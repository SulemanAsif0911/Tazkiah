package com.cyberhayat.tazkiah;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_VPN = 21, REQ_NOTIF = 22, REQ_ADMIN = 23;

    private WebView web;
    private WebViewAssetLoader loader;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#FFF7F3"));
        setContentView(web);

        loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/", new WebViewAssetLoader.PathHandler() {
                    @Override
                    public WebResourceResponse handle(String path) {
                        try {
                            if (path.isEmpty() || path.endsWith("/")) path += "index.html";
                            InputStream is = getAssets().open("www/" + path);
                            return new WebResourceResponse(mime(path), isText(path) ? "utf-8" : null, is);
                        } catch (IOException e) {
                            return null;
                        }
                    }
                }).build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        keepLight(s);

        web.addJavascriptInterface(new Bridge(), "AndroidNative");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) { // the UI never talks to the internet
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return loader.shouldInterceptRequest(u);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !HOST.equals(r.getUrl().getHost());
            }
        });

        if (b == null) web.loadUrl("https://" + HOST + "/index.html");
        else web.restoreState(b);
    }

    @SuppressWarnings("deprecation")
    private void keepLight(WebSettings s) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false);
        } catch (Throwable ignored) { }
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK))
                WebSettingsCompat.setForceDark(s, WebSettingsCompat.FORCE_DARK_OFF);
        } catch (Throwable ignored) { }
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.TZ&&TZ.back&&TZ.back())?'1':'0'", new ValueCallback<String>() {
            @Override public void onReceiveValue(String v) {
                if (!"\"1\"".equals(v)) MainActivity.super.onBackPressed();
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        web.onResume();
        refreshUi(0);
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); }

    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }

    private void refreshUi(long delay) {
        main.postDelayed(new Runnable() {
            @Override public void run() { if (web != null) web.evaluateJavascript("window.TZ&&TZ.refresh&&TZ.refresh()", null); }
        }, delay);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_VPN) {
            if (res == RESULT_OK) startShield();
            refreshUi(500);
        } else if (req == REQ_ADMIN) {
            refreshUi(300);
        } else super.onActivityResult(req, res, data);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req == REQ_NOTIF) { ReminderReceiver.schedule(this); refreshUi(300); }
    }

    // ------------------------------------------------------------------ helpers
    private void startShield() {
        Prefs.setDnsWanted(this, true);
        startService(new Intent(this, DnsShieldService.class).setAction(DnsShieldService.ACTION_START));
    }

    private boolean accessibilityOn() {
        if (TazkiahAccessibilityService.instance != null) return true;
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            ComponentName cn = new ComponentName(this, TazkiahAccessibilityService.class);
            for (String s : enabled.split(":")) {
                if (s.equalsIgnoreCase(cn.flattenToString()) || s.equalsIgnoreCase(cn.flattenToShortString())) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private ComponentName adminCn() { return new ComponentName(this, TazkiahAdminReceiver.class); }

    private void openSafely(Intent i) {
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "Could not open that screen on this phone.", Toast.LENGTH_SHORT).show(); }
    }

    private static boolean isText(String p) {
        return p.endsWith(".html") || p.endsWith(".js") || p.endsWith(".css") || p.endsWith(".json") || p.endsWith(".svg") || p.endsWith(".txt");
    }

    private static String mime(String p) {
        String l = p.toLowerCase();
        if (l.endsWith(".js") || l.endsWith(".mjs")) return "application/javascript";
        if (l.endsWith(".json")) return "application/json";
        if (l.endsWith(".woff2")) return "font/woff2";
        if (l.endsWith(".html")) return "text/html";
        if (l.endsWith(".css")) return "text/css";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(l));
        return m == null ? "application/octet-stream" : m;
    }

    private String readAsset(String path) {
        try {
            InputStream is = getAssets().open(path);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            is.close();
            return bo.toString("UTF-8");
        } catch (Exception e) { return "{\"items\":[]}"; }
    }

    // ------------------------------------------------------------------ bridge (window.AndroidNative)
    public class Bridge {
        @JavascriptInterface public String getStatus() {
            try {
                JSONObject o = new JSONObject();
                o.put("accessibility", accessibilityOn());
                o.put("dns", DnsShieldService.running);
                o.put("notifications", ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled());
                o.put("admin", ((DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE)).isAdminActive(adminCn()));
                o.put("battery", ((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()));
                o.put("sdk", Build.VERSION.SDK_INT);
                String v = "1.0";
                try { PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0); v = pi.versionName; } catch (Exception ignored) { }
                o.put("version", v);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface public String getSettings() {
            try {
                JSONObject o = new JSONObject();
                o.put("protection", Prefs.protection(MainActivity.this));
                o.put("sensitivity", Prefs.sensitivity(MainActivity.this));
                o.put("lang", Prefs.lang(MainActivity.this));
                o.put("guard", Prefs.guard(MainActivity.this));
                o.put("visual", Prefs.visual(MainActivity.this));
                o.put("daily", Prefs.daily(MainActivity.this));
                o.put("dailyHour", Prefs.dailyHour(MainActivity.this));
                o.put("dailyMin", Prefs.dailyMin(MainActivity.this));
                o.put("custom", Prefs.customWords(MainActivity.this));
                o.put("trusted", new JSONArray(Prefs.trusted(MainActivity.this)));
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        /** key: protection | sensitivity | lang | guard | daily | dailyTime (HH:mm) | custom | trusted (JSON array) */
        @JavascriptInterface public void setSetting(String key, String value) {
            Context c = MainActivity.this;
            try {
                switch (key) {
                    case "protection": Prefs.setProtection(c, "1".equals(value)); break;
                    case "sensitivity": Prefs.setSensitivity(c, value); break;
                    case "lang": Prefs.setLang(c, value); break;
                    case "guard": Prefs.setGuard(c, "1".equals(value)); break;
                    case "visual": Prefs.setVisual(c, "1".equals(value)); break;
                    case "daily": Prefs.setDaily(c, "1".equals(value)); ReminderReceiver.schedule(c); break;
                    case "dailyTime": {
                        String[] p = value.split(":");
                        Prefs.setDailyTime(c, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                        ReminderReceiver.schedule(c);
                        break;
                    }
                    case "custom": Prefs.setCustomWords(c, value); Blocklist.setCustom(value); break;
                    case "trusted": {
                        JSONArray a = new JSONArray(value);
                        Set<String> s = new HashSet<>();
                        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                        Prefs.setTrusted(c, s);
                        break;
                    }
                    default: break;
                }
            } catch (Exception ignored) { }
        }

        @JavascriptInterface public String getDiag() { return Diag.json(MainActivity.this); }

        /** Blocking (runs the model once); the page calls it and shows the result when it returns. */
        @JavascriptInterface public String selfTest() { return Diag.selfTest(MainActivity.this); }

        @JavascriptInterface public String getStats() { return Prefs.statsJson(MainActivity.this); }
        @JavascriptInterface public void resetStats() { Prefs.resetStats(MainActivity.this); }
        @JavascriptInterface public void istighfar() { Prefs.addIstighfar(MainActivity.this); }
        @JavascriptInterface public String getReminders() { return readAsset("data/reminders.json"); }

        @JavascriptInterface public String getApps() {
            final PackageManager pm = getPackageManager();
            List<ResolveInfo> l = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            final List<String[]> apps = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : l) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || !seen.add(p)) continue;
                apps.add(new String[]{p, String.valueOf(ri.loadLabel(pm))});
            }
            Collections.sort(apps, new Comparator<String[]>() {
                @Override public int compare(String[] a, String[] b) { return a[1].compareToIgnoreCase(b[1]); }
            });
            JSONArray out = new JSONArray();
            try {
                for (String[] a : apps) out.put(new JSONObject().put("p", a[0]).put("n", a[1]));
            } catch (Exception ignored) { }
            return out.toString();
        }

        @JavascriptInterface public void openAccessibility() {
            runOnUiThread(new Runnable() { @Override public void run() { openSafely(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } });
        }

        @JavascriptInterface public void startDns() {
            runOnUiThread(new Runnable() { @Override public void run() {
                Intent i = VpnService.prepare(MainActivity.this);
                if (i != null) startActivityForResult(i, REQ_VPN);
                else { startShield(); refreshUi(500); }
            } });
        }

        @JavascriptInterface public void stopDns() {
            runOnUiThread(new Runnable() { @Override public void run() {
                Prefs.setDnsWanted(MainActivity.this, false);
                startService(new Intent(MainActivity.this, DnsShieldService.class).setAction(DnsShieldService.ACTION_STOP));
                refreshUi(500);
            } });
        }

        @JavascriptInterface public void requestNotifications() {
            runOnUiThread(new Runnable() { @Override public void run() {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                } else {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    openSafely(i);
                }
            } });
        }

        @JavascriptInterface public void requestAdmin() {
            runOnUiThread(new Runnable() { @Override public void run() {
                DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                if (dpm.isAdminActive(adminCn())) {
                    openSafely(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    return;
                }
                Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminCn())
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation));
                try { startActivityForResult(i, REQ_ADMIN); } catch (Exception e) { openSafely(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
            } });
        }

        @JavascriptInterface public void requestBattery() {
            runOnUiThread(new Runnable() { @Override public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    openSafely(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            } });
        }

        @JavascriptInterface public void openAppSettings() {
            runOnUiThread(new Runnable() { @Override public void run() {
                openSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } });
        }

        @JavascriptInterface public void openVpnSettings() {
            runOnUiThread(new Runnable() { @Override public void run() { openSafely(new Intent(Settings.ACTION_VPN_SETTINGS)); } });
        }

        /** true if the real block screen was shown (needs the accessibility service). */
        @JavascriptInterface public boolean previewOverlay(String id) {
            return TazkiahAccessibilityService.preview(MainActivity.this, id);
        }

        @JavascriptInterface public void copy(final String text) {
            runOnUiThread(new Runnable() { @Override public void run() {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Cyber Tazkiah", text));
                Toast.makeText(MainActivity.this, "Copied", Toast.LENGTH_SHORT).show();
            } });
        }

        @JavascriptInterface public void share(final String text) {
            runOnUiThread(new Runnable() { @Override public void run() {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
                openSafely(Intent.createChooser(i, null));
            } });
        }
    }
}
