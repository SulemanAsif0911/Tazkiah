package com.cyberhayat.tazkiah;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONObject;

/** Live health numbers + a self-test, shown in Shield > Live status. Counts only, no content. */
final class Diag {
    private Diag() {}

    static volatile long events, eventsAt, scans, scansAt, visScans, visAt, triggers;
    static volatile int lastScanScore = -1, lastShotError = 0;
    static volatile float lastVisScore = -1f;
    static volatile String visState = "idle", lastScanPkg = "";

    static String json(Context c) {
        try {
            long now = SystemClock.elapsedRealtime();
            TazkiahAccessibilityService s = TazkiahAccessibilityService.instance;
            JSONObject o = new JSONObject();
            o.put("connected", s != null);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("events", events);
            o.put("eventAgo", eventsAt == 0 ? -1 : (now - eventsAt) / 1000);
            o.put("scans", scans);
            o.put("scanAgo", scansAt == 0 ? -1 : (now - scansAt) / 1000);
            o.put("scanScore", lastScanScore);
            o.put("scanPkg", lastScanPkg);
            o.put("visScans", visScans);
            o.put("visAgo", visAt == 0 ? -1 : (now - visAt) / 1000);
            o.put("visScore", Math.round(lastVisScore * 100));
            o.put("visState", visState);
            o.put("shotError", lastShotError);
            o.put("modelError", VisualGuard.lastError);
            o.put("inferMs", VisualGuard.lastInferMs);
            o.put("triggers", triggers);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    /** Runs on a background thread (called from the WebView bridge). */
    static String selfTest(Context c) {
        try {
            JSONObject o = new JSONObject();
            Blocklist.init(c);
            o.put("textTyped", Blocklist.score("porn video") >= 4);
            o.put("textScreen", Blocklist.score("watch porn and xxx videos", false, false) >= 4);
            o.put("textClean", Blocklist.score("weather in Lahore today and cricket scores", false, false) == 0);
            o.put("sdk", Build.VERSION.SDK_INT);
            VisualGuard g = new VisualGuard();
            long t0 = SystemClock.elapsedRealtime();
            boolean ok = g.load(c);
            o.put("modelLoaded", ok);
            o.put("loadMs", SystemClock.elapsedRealtime() - t0);
            if (ok) {
                Bitmap b = Bitmap.createBitmap(540, 1200, Bitmap.Config.ARGB_8888);
                b.eraseColor(Color.rgb(120, 130, 140));
                float r = g.analyze(b, true, 3);
                b.recycle();
                o.put("inferMs", VisualGuard.lastInferMs);
                o.put("blankScore", Math.round(r * 100));
                g.close();
            } else {
                o.put("error", VisualGuard.lastError);
            }
            o.put("serviceConnected", TazkiahAccessibilityService.instance != null);
            return o.toString();
        } catch (Throwable t) {
            return "{\"error\":\"" + String.valueOf(t.getMessage()).replace('"', '\'') + "\"}";
        }
    }
}
