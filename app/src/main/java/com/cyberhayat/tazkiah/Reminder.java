package com.cyberhayat.tazkiah;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** One ayah / hadith reminder, loaded from assets/data/reminders.json. */
final class Reminder {
    String id, type, ar, ur, en, ref, hookUr, hookEn;
    boolean hope; // tawbah / mercy item

    private static volatile List<Reminder> all;
    private static String lastId = "";
    private static final Random RND = new Random();

    static synchronized List<Reminder> all(Context c) {
        if (all != null) return all;
        List<Reminder> out = new ArrayList<>();
        try {
            InputStream is = c.getAssets().open("data/reminders.json");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            is.close();
            JSONArray arr = new JSONObject(bo.toString("UTF-8")).getJSONArray("items");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Reminder r = new Reminder();
                r.id = o.optString("id"); r.type = o.optString("type");
                r.ar = o.optString("ar"); r.ur = o.optString("ur"); r.en = o.optString("en");
                r.ref = o.optString("ref"); r.hookUr = o.optString("hook_ur"); r.hookEn = o.optString("hook_en");
                JSONArray cats = o.optJSONArray("cats");
                for (int k = 0; cats != null && k < cats.length(); k++) if ("tawbah".equals(cats.getString(k))) r.hope = true;
                out.add(r);
            }
        } catch (Exception ignored) { }
        all = out;
        return out;
    }

    static Reminder byId(Context c, String id) {
        for (Reminder r : all(c)) if (r.id.equals(id)) return r;
        return null;
    }

    /** hope=false -> deterrent reminders (gaze, Allah sees, hereafter...). hope=true -> tawbah / mercy ones. */
    static synchronized Reminder pick(Context c, boolean hope) {
        List<Reminder> pool = new ArrayList<>();
        for (Reminder r : all(c)) if (r.hope == hope && !r.id.equals(lastId)) pool.add(r);
        if (pool.isEmpty()) pool = all(c);
        if (pool.isEmpty()) return fallback();
        Reminder r = pool.get(RND.nextInt(pool.size()));
        lastId = r.id;
        return r;
    }

    private static Reminder fallback() {
        Reminder r = new Reminder();
        r.id = "fallback"; r.type = "ayah";
        r.ar = "أَلَمۡ يَعۡلَم بِأَنَّ ٱللَّهَ يَرَىٰ";
        r.ur = "کیا وہ نہیں جانتا کہ اللہ دیکھ رہا ہے؟";
        r.en = "Does he not know that Allah sees?";
        r.ref = "Surah Al-Alaq 96:14";
        r.hookUr = "ایک لمحے کو سوچئے: اللہ آپ کو دیکھ رہا ہے۔";
        r.hookEn = "For a moment, think: Allah is looking at you.";
        return r;
    }
}
