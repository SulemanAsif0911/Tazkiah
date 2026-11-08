package com.cyberhayat.tazkiah;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Layer 1. Notices adult searches / URLs / on-screen text and covers the screen with an ayah or hadith.
 * Only counts are kept; the text it reads is scored in memory and thrown away. Password fields are skipped.
 */
public class TazkiahAccessibilityService extends AccessibilityService {
    static volatile TazkiahAccessibilityService instance;

    // Performance: scans run on a background thread, are debounced, and back off when the screen is heavy.
    private static final long SCAN_DELAY_MS = 900, MIN_GAP_MS = 1500;
    private static final int MAX_NODES = 450, MAX_DEPTH = 30, MAX_CHARS = 7000;

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View overlay;
    private boolean overlayGuard;
    private volatile boolean scanPending, guardPending;
    private volatile long cooldownUntil, guardGraceUntil, nextScanAt;
    private volatile String lastPkg = "";
    private volatile boolean cfgVisual, visualOn, visualBroken;
    private int textStrikes, visualStrikes;
    private long visualInterval = 2500;
    private VisualGuard vg;
    private HandlerThread bgThread;
    private Handler bg;
    private int lastHash;
    private String lastHashPkg = "";
    // cached settings (refreshed every 3 s, so events never hit SharedPreferences)
    private volatile boolean cfgProtection = true, cfgGuard;
    private volatile String cfgSens = "balanced";
    private long cfgAt;
    private String homePkg = "";
    private String imePkg = "";
    private volatile Set<String> trusted = new HashSet<>();
    private long trustedLoadedAt;

    private static final Set<String> ALWAYS_IGNORED = new HashSet<>();
    private static final Set<String> SETTINGS_PKGS = new HashSet<>();
    private static final Map<String, String[]> URL_BARS = new HashMap<>();
    static {
        ALWAYS_IGNORED.add("com.android.systemui");
        ALWAYS_IGNORED.add("android");
        ALWAYS_IGNORED.add("com.android.settings");
        ALWAYS_IGNORED.add("com.google.android.packageinstaller");
        ALWAYS_IGNORED.add("com.android.packageinstaller");
        ALWAYS_IGNORED.add("com.samsung.android.packageinstaller");
        ALWAYS_IGNORED.add("com.miui.packageinstaller");
        SETTINGS_PKGS.addAll(ALWAYS_IGNORED);
        URL_BARS.put("com.android.chrome", new String[]{"com.android.chrome:id/url_bar"});
        URL_BARS.put("com.chrome.beta", new String[]{"com.chrome.beta:id/url_bar"});
        URL_BARS.put("org.mozilla.firefox", new String[]{"org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/url_bar_title"});
        URL_BARS.put("com.sec.android.app.sbrowser", new String[]{"com.sec.android.app.sbrowser:id/location_bar_edit_text"});
        URL_BARS.put("com.microsoft.emmx", new String[]{"com.microsoft.emmx:id/url_bar"});
        URL_BARS.put("com.brave.browser", new String[]{"com.brave.browser:id/url_bar"});
        URL_BARS.put("com.opera.browser", new String[]{"com.opera.browser:id/url_field"});
        URL_BARS.put("com.opera.mini.native", new String[]{"com.opera.mini.native:id/url_field"});
        URL_BARS.put("com.duckduckgo.mobile.android", new String[]{"com.duckduckgo.mobile.android:id/omnibarTextInput"});
        URL_BARS.put("com.kiwibrowser.browser", new String[]{"com.kiwibrowser.browser:id/url_bar"});
        URL_BARS.put("com.vivaldi.browser", new String[]{"com.vivaldi.browser:id/url_bar"});
        URL_BARS.put("com.UCMobile.intl", new String[]{"com.UCMobile.intl:id/url_bar"});
        URL_BARS.put("com.mi.globalbrowser", new String[]{"com.mi.globalbrowser:id/url"});
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override public void run() {
            scanPending = false;
            long t0 = SystemClock.elapsedRealtime();
            try { scanScreen(); } catch (Throwable ignored) { }
            long took = SystemClock.elapsedRealtime() - t0;
            nextScanAt = SystemClock.elapsedRealtime() + Math.max(MIN_GAP_MS, took * 10); // heavy screen -> scan less
        }
    };
    private final Runnable guardRunnable = new Runnable() {
        @Override public void run() { guardPending = false; try { checkGuard(); } catch (Throwable ignored) { } }
    };
    private final Runnable autoClose = new Runnable() { @Override public void run() { dismiss(true); } };

    // ------------------------------------------------------------------ lifecycle
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        bgThread = new HandlerThread("tazkiah-scan", Process.THREAD_PRIORITY_BACKGROUND);
        bgThread.start();
        bg = new Handler(bgThread.getLooper());
        refreshCfg(true);
        Blocklist.init(this);
        resolveSystemPackages();
        reloadTrusted();
    }

    @Override public void onInterrupt() { }

    private void stopBg() {
        if (bg != null) { bg.removeCallbacksAndMessages(null); }
        visualOn = false;
        if (vg != null) { vg.close(); vg = null; }
        if (bgThread != null) { bgThread.quitSafely(); bgThread = null; bg = null; }
    }

    @Override public boolean onUnbind(Intent intent) {
        stopBg();
        removeOverlay();
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        stopBg();
        removeOverlay();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void resolveSystemPackages() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) homePkg = ri.activityInfo.packageName;
            String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.contains("/")) imePkg = ime.substring(0, ime.indexOf('/'));
        } catch (Exception ignored) { }
    }

    private void refreshCfg(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - cfgAt < 3000) return;
        cfgAt = now;
        cfgProtection = Prefs.protection(this);
        cfgGuard = Prefs.guard(this);
        cfgSens = Prefs.sensitivity(this);
        cfgVisual = Prefs.visual(this);
    }

    private void reloadTrusted() {
        trusted = Prefs.trusted(this);
        trustedLoadedAt = SystemClock.elapsedRealtime();
    }

    private boolean isIgnored(String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        if (SystemClock.elapsedRealtime() - trustedLoadedAt > 15000) reloadTrusted();
        return ALWAYS_IGNORED.contains(pkg) || pkg.equals(homePkg) || pkg.equals(imePkg)
                || pkg.equals(getPackageName()) || trusted.contains(pkg);
    }

    // ------------------------------------------------------------------ events (main thread: keep it tiny)
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || bg == null) return;
        CharSequence cp = e.getPackageName();
        if (cp == null) return;
        String pkg = cp.toString();
        if (overlay != null || pkg.equals(getPackageName())) return;
        int type = e.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) lastPkg = pkg;
        refreshCfg(false);

        if (cfgGuard && SETTINGS_PKGS.contains(pkg)) {
            if ((type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    && !guardPending && SystemClock.elapsedRealtime() >= guardGraceUntil) {
                guardPending = true;
                bg.postDelayed(guardRunnable, 500);
            }
            return;
        }
        if (!cfgProtection || isIgnored(pkg)) return;
        ensureVisual();

        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (!e.isPassword()) handleTyped(e);
            return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (!scanPending) {
                scanPending = true;
                long wait = Math.max(SCAN_DELAY_MS, nextScanAt - SystemClock.elapsedRealtime());
                bg.postDelayed(scanRunnable, wait);
            }
        }
    }

    private int typedThreshold() { return "strict".equals(cfgSens) ? 2 : 4; }

    private int screenThreshold(boolean browser) {
        String s = cfgSens;
        if ("gentle".equals(s)) return 16;
        if ("strict".equals(s)) return browser ? 4 : 8;
        return browser ? 8 : 12;
    }

    private void handleTyped(AccessibilityEvent e) {
        List<CharSequence> parts = e.getText();
        if (parts == null || parts.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (CharSequence cs : parts) if (cs != null) sb.append(cs).append(' ');
        String text = sb.toString();
        if (text.length() < 3) return;
        if (text.length() > 300) text = text.substring(text.length() - 300);
        String host = Blocklist.extractHost(text);
        if (host != null && Blocklist.hostBlocked(host)) { trigger("browser"); return; }
        if (Blocklist.score(text) >= typedThreshold()) trigger("search");
    }

    // ------------------------------------------------------------------ screen scan
    /** Runs on the background thread. */
    private void scanScreen() {
        if (overlay != null || SystemClock.elapsedRealtime() < cooldownUntil || !cfgProtection) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            CharSequence p = root.getPackageName();
            String pkg = p == null ? "" : p.toString();
            if (isIgnored(pkg)) return;
            boolean browser = URL_BARS.containsKey(pkg);

            if (browser) {
                for (String id : URL_BARS.get(pkg)) {
                    List<AccessibilityNodeInfo> bars = root.findAccessibilityNodeInfosByViewId(id);
                    if (bars == null) continue;
                    for (AccessibilityNodeInfo bar : bars) {
                        CharSequence t = bar.getText();
                        if (t != null) {
                            String host = Blocklist.extractHost(t.toString());
                            if (host != null && Blocklist.hostBlocked(host)) { trigger("browser"); return; }
                        }
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            collect(root, sb, new int[]{0}, 0);
            String text = sb.toString();
            int hash = text.hashCode();
            if (hash == lastHash && pkg.equals(lastHashPkg) && textStrikes == 0) return; // nothing new since the last scan
            lastHash = hash; lastHashPkg = pkg;
            int thr = screenThreshold(browser), sc = Blocklist.score(text, false, false);
            if (sc >= thr) {
                // one odd frame is not enough: ordinary apps must show it in two scans in a row
                if (browser || sc >= thr + 8 || ++textStrikes >= 2) { textStrikes = 0; trigger(browser ? "browser" : "screen"); }
            } else {
                textStrikes = 0;
            }
        } finally {
            try { root.recycle(); } catch (Exception ignored) { }
        }
    }

    private void collect(AccessibilityNodeInfo n, StringBuilder sb, int[] count, int depth) {
        if (n == null || depth > MAX_DEPTH || count[0] > MAX_NODES || sb.length() > MAX_CHARS) return;
        count[0]++;
        if (n.isPassword() || !n.isVisibleToUser()) return;
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) sb.append(t).append(' ');
        CharSequence d = n.getContentDescription();
        if (d != null && d.length() > 0) sb.append(d).append(' ');
        int kids = n.getChildCount();
        for (int i = 0; i < kids; i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null) continue;
            collect(c, sb, count, depth + 1);
            try { c.recycle(); } catch (Exception ignored) { }
        }
    }

    // ------------------------------------------------------------------ guard (cooling-off before removing protection)
    private static final String[] GUARD_WORDS = {"uninstall", "deactivate", "force stop", "turn off", "disable",
            "stop cyber", "remove", "clear data", "clear storage", "delete"};

    private void checkGuard() {
        if (overlay != null || !cfgGuard || SystemClock.elapsedRealtime() < guardGraceUntil) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            collect(root, sb, new int[]{0}, 0);
            String t = sb.toString().toLowerCase(Locale.ROOT);
            if (!t.contains("cyber tazkiah")) return;
            for (String w : GUARD_WORDS) {
                if (t.contains(w)) { guardAlert(); return; }
            }
        } finally {
            try { root.recycle(); } catch (Exception ignored) { }
        }
    }

    // ------------------------------------------------------------------ triggers
    private void guardAlert() {
        h.post(new Runnable() {
            @Override public void run() {
                if (overlay != null) return;
                Prefs.recordBlock(TazkiahAccessibilityService.this, "guard");
                showOverlay(true, Reminder.pick(TazkiahAccessibilityService.this, false));
            }
        });
    }

    /** Safe from any thread; the overlay is always shown on the main thread. */
    private void trigger(final String cat) {
        h.post(new Runnable() {
            @Override public void run() {
                if (overlay != null || SystemClock.elapsedRealtime() < cooldownUntil) return;
                Prefs.recordBlock(TazkiahAccessibilityService.this, cat);
                showOverlay(false, Reminder.pick(TazkiahAccessibilityService.this, false));
            }
        });
    }

    // ------------------------------------------------------------------ image guard (Android 11+)
    private final Executor bgExec = new Executor() {
        @Override public void execute(Runnable r) { Handler x = bg; if (x != null) x.post(r); }
    };
    private final Runnable visualRunnable = new Runnable() { @Override public void run() { visualTick(); } };

    private boolean visualEligible() {
        if (Build.VERSION.SDK_INT < 30 || visualBroken || !cfgVisual || !cfgProtection || overlay != null) return false;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return false;
        return !isIgnored(lastPkg);
    }

    private void ensureVisual() {
        if (visualOn || bg == null || Build.VERSION.SDK_INT < 30 || visualBroken || !cfgVisual) return;
        visualOn = true;
        bg.postDelayed(visualRunnable, 1500);
    }

    private void nextVisual(long delay) {
        Handler x = bg;
        if (x == null) { visualOn = false; return; }
        x.postDelayed(visualRunnable, delay);
    }

    /** Runs on the background thread: one screenshot -> small model -> maybe block. */
    private void visualTick() {
        if (!visualEligible()) { visualOn = false; return; }
        if (SystemClock.elapsedRealtime() < cooldownUntil) { nextVisual(1500); return; }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, bgExec, new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult r) {
                    long t0 = SystemClock.elapsedRealtime();
                    Bitmap sw = null;
                    try {
                        HardwareBuffer hb = r.getHardwareBuffer();
                        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                        if (hw != null) { sw = hw.copy(Bitmap.Config.ARGB_8888, false); hw.recycle(); }
                        hb.close();
                    } catch (Throwable ignored) { }
                    if (sw != null) {
                        try { if (overlay == null) evaluateVisual(sw); } catch (Throwable ignored) { }
                        sw.recycle();
                    }
                    long took = SystemClock.elapsedRealtime() - t0;
                    visualInterval = Math.max(2500, took * 8); // heavy device -> look less often
                    nextVisual(visualInterval);
                }
                @Override public void onFailure(int errorCode) { nextVisual(4000); }
            });
        } catch (Throwable t) {
            nextVisual(6000);
        }
    }

    private void evaluateVisual(Bitmap sw) {
        if (vg == null) vg = new VisualGuard();
        if (!vg.load(this)) { visualBroken = true; return; }
        boolean strict = "strict".equals(cfgSens), gentle = "gentle".equals(cfgSens);
        float sc = vg.analyze(sw, strict);
        if (sc < 0) return; // unchanged screen
        float hi = gentle ? 0.75f : strict ? 0.55f : 0.65f;
        float lo = gentle ? 0.75f : strict ? 0.35f : 0.45f;
        if (sc >= hi) { visualStrikes = 0; trigger("visual"); }
        else if (sc >= lo) { if (++visualStrikes >= 2) { visualStrikes = 0; trigger("visual"); } }
        else visualStrikes = 0;
    }

    // ------------------------------------------------------------------ overlay
    /** Used by the in-app preview button. Returns false when the service is not enabled. */
    static boolean preview(Context ctx, String reminderId) {
        final TazkiahAccessibilityService s = instance;
        if (s == null) return false;
        final Reminder r = reminderId == null ? null : Reminder.byId(ctx, reminderId);
        s.h.post(new Runnable() { @Override public void run() { s.showOverlay(false, r != null ? r : Reminder.pick(s, false)); } });
        return true;
    }

    private void showOverlay(final boolean guard, final Reminder r) {
        if (wm == null) return;
        if (overlay != null) removeOverlay();
        overlayGuard = guard;
        final View v = OverlayUi.build(this, r, Prefs.lang(this), guard, guard ? 20 : 7, new OverlayUi.Callbacks() {
            @Override public void onTurnBack() {
                if (!overlayGuard) Prefs.addIstighfar(TazkiahAccessibilityService.this);
                dismiss(true);
            }
            @Override public void onContinue() {
                guardGraceUntil = SystemClock.elapsedRealtime() + 3 * 60 * 1000L;
                dismiss(false);
            }
            @Override public void onNext() {
                showOverlay(false, Reminder.pick(TazkiahAccessibilityService.this, false));
            }
        });
        v.setFocusableInTouchMode(true);
        v.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View view, int keyCode, KeyEvent ev) { return keyCode == KeyEvent.KEYCODE_BACK; }
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE);
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try {
            wm.addView(v, lp);
            overlay = v;
            v.requestFocus();
            h.removeCallbacks(autoClose);
            h.postDelayed(autoClose, 120000);
        } catch (Exception ex) {
            overlay = null;
        }
    }

    private void removeOverlay() {
        h.removeCallbacks(autoClose);
        if (overlay != null) {
            OverlayUi.cancel(overlay);
            try { wm.removeView(overlay); } catch (Exception ignored) { }
            overlay = null;
        }
    }

    /** goBack=true: leave the page (Back, then Home). */
    private void dismiss(boolean goBack) {
        removeOverlay();
        cooldownUntil = SystemClock.elapsedRealtime() + 4000;
        lastHash = 0;
        if (goBack) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            h.postDelayed(new Runnable() { @Override public void run() { performGlobalAction(GLOBAL_ACTION_HOME); } }, 350);
        }
    }
}
