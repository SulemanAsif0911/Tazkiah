package com.cyberhayat.tazkiah;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** All settings + counters. Only counts are stored: never URLs, words or page text. */
final class Prefs {
    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("tazkiah", Context.MODE_PRIVATE);
    }

    // ---- settings ----
    static boolean protection(Context c) { return sp(c).getBoolean("protection", true); }
    static void setProtection(Context c, boolean v) { sp(c).edit().putBoolean("protection", v).apply(); }

    /** gentle | balanced | strict */
    static String sensitivity(Context c) { return sp(c).getString("sensitivity", "balanced"); }
    static void setSensitivity(Context c, String v) { sp(c).edit().putString("sensitivity", v).apply(); }

    /** both | ur | en */
    static String lang(Context c) { return sp(c).getString("lang", "both"); }
    static void setLang(Context c, String v) { sp(c).edit().putString("lang", v).apply(); }

    static boolean guard(Context c) { return sp(c).getBoolean("guard", false); }
    static void setGuard(Context c, boolean v) { sp(c).edit().putBoolean("guard", v).apply(); }

    static boolean dnsWanted(Context c) { return sp(c).getBoolean("dns_on", false); }
    static void setDnsWanted(Context c, boolean v) { sp(c).edit().putBoolean("dns_on", v).apply(); }

    static boolean daily(Context c) { return sp(c).getBoolean("daily", true); }
    static void setDaily(Context c, boolean v) { sp(c).edit().putBoolean("daily", v).apply(); }
    static int dailyHour(Context c) { return sp(c).getInt("daily_h", 21); }
    static int dailyMin(Context c) { return sp(c).getInt("daily_m", 0); }
    static void setDailyTime(Context c, int h, int m) { sp(c).edit().putInt("daily_h", h).putInt("daily_m", m).apply(); }

    static Set<String> trusted(Context c) { return new HashSet<>(sp(c).getStringSet("trusted", new HashSet<String>())); }
    static void setTrusted(Context c, Set<String> s) { sp(c).edit().putStringSet("trusted", s).apply(); }

    static String customWords(Context c) { return sp(c).getString("custom", ""); }
    static void setCustomWords(Context c, String s) { sp(c).edit().putString("custom", s == null ? "" : s).apply(); }

    // ---- counters ----
    private static String day(long ms) { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(ms)); }

    private static long startOfDay(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long installMs(Context c) {
        SharedPreferences p = sp(c);
        long t = p.getLong("install_ms", 0);
        if (t == 0) { t = System.currentTimeMillis(); p.edit().putLong("install_ms", t).apply(); }
        return t;
    }

    /** cat: browser | search | screen | dns | guard */
    static synchronized void recordBlock(Context c, String cat) {
        SharedPreferences p = sp(c);
        long now = System.currentTimeMillis();
        String k = "n_" + day(now);
        p.edit()
                .putInt(k, p.getInt(k, 0) + 1)
                .putInt("c_" + cat, p.getInt("c_" + cat, 0) + 1)
                .putInt("total", p.getInt("total", 0) + 1)
                .putLong("last_block_ms", now)
                .apply();
    }

    static synchronized void addIstighfar(Context c) {
        SharedPreferences p = sp(c);
        p.edit().putInt("istighfar", p.getInt("istighfar", 0) + 1).apply();
    }

    static String statsJson(Context c) {
        SharedPreferences p = sp(c);
        long now = System.currentTimeMillis();
        long today0 = startOfDay(now);
        long lastBlock = p.getLong("last_block_ms", 0);
        long from = lastBlock == 0 ? startOfDay(installMs(c)) : startOfDay(lastBlock);
        long streak = Math.round((today0 - from) / 86400000.0);
        try {
            JSONObject o = new JSONObject();
            o.put("today", p.getInt("n_" + day(now), 0));
            o.put("total", p.getInt("total", 0));
            o.put("streak", streak);
            o.put("istighfar", p.getInt("istighfar", 0));
            JSONArray week = new JSONArray();
            Calendar cal = Calendar.getInstance();
            for (int i = 6; i >= 0; i--) {
                cal.setTimeInMillis(now);
                cal.add(Calendar.DAY_OF_YEAR, -i);
                JSONObject d = new JSONObject();
                d.put("d", new SimpleDateFormat("EEE", Locale.ENGLISH).format(cal.getTime()));
                d.put("n", p.getInt("n_" + day(cal.getTimeInMillis()), 0));
                week.put(d);
            }
            o.put("week", week);
            JSONObject cats = new JSONObject();
            for (String k : new String[]{"browser", "search", "screen", "dns", "guard"}) cats.put(k, p.getInt("c_" + k, 0));
            o.put("cats", cats);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    static void resetStats(Context c) {
        SharedPreferences p = sp(c);
        SharedPreferences.Editor e = p.edit();
        for (String k : p.getAll().keySet())
            if (k.startsWith("n_") || k.startsWith("c_") || k.equals("total") || k.equals("last_block_ms") || k.equals("istighfar")) e.remove(k);
        e.putLong("install_ms", System.currentTimeMillis());
        e.apply();
    }
}
