package com.cyberhayat.tazkiah;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Keyword + domain matching. Text is normalised (case, Arabic/Urdu letter forms, leetspeak) before matching. */
final class Blocklist {
    private Blocklist() {}

    static final class Term {
        final String t; final boolean prefix; final int pts;
        Term(String t, boolean prefix, int pts) { this.t = t; this.prefix = prefix; this.pts = pts; }
    }

    private static volatile List<Term> terms;
    private static volatile List<Term> custom = new ArrayList<>();
    private static volatile Set<String> domains;

    // substrings that are unambiguous inside a hostname
    private static final String[] HOST_PARTS = {
            "porn", "xvideos", "xnxx", "xhamster", "hentai", "redtube", "youporn", "brazzers", "chaturbate",
            "stripchat", "bongacams", "livejasmin", "spankbang", "nsfw", "xxx", "onlyfans", "sexcam", "sexchat",
            "camgirl", "jerkmate", "rule34", "thothub", "fapello", "eporner", "hqporner", "tnaflix"
    };

    static synchronized void init(Context c) {
        if (terms != null) return;
        List<Term> t = new ArrayList<>();
        Set<String> d = new HashSet<>();
        try {
            for (String line : readLines(c, "blocklist/keywords.txt")) {
                if (line.length() < 3 || line.charAt(0) == '#' || line.charAt(1) != '|') continue;
                int pts = line.charAt(0) == 'S' ? 4 : 1;
                String raw = line.substring(2).trim();
                boolean prefix = raw.endsWith("*");
                if (prefix) raw = raw.substring(0, raw.length() - 1);
                String n = norm(raw, false).trim();
                if (!n.isEmpty()) t.add(new Term(n, prefix, pts));
            }
            for (String line : readLines(c, "blocklist/domains.txt")) {
                line = line.trim().toLowerCase(Locale.ROOT);
                if (!line.isEmpty() && line.charAt(0) != '#') d.add(line);
            }
        } catch (Exception ignored) { }
        domains = d;
        terms = t;
        setCustom(Prefs.customWords(c));
    }

    private static List<String> readLines(Context c, String path) throws Exception {
        List<String> out = new ArrayList<>();
        InputStream is = c.getAssets().open(path);
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String l;
        while ((l = r.readLine()) != null) out.add(l);
        r.close();
        return out;
    }

    /** User's own words, one per line (each counts as a strong term). */
    static void setCustom(String text) {
        List<Term> t = new ArrayList<>();
        if (text != null) {
            for (String line : text.split("\\r?\\n")) {
                String n = norm(line, false).trim();
                if (n.length() >= 2) t.add(new Term(n, false, 4));
            }
        }
        custom = t;
    }

    /** Lower-cases, unifies Arabic/Urdu letter forms, turns punctuation into spaces. Result is padded with spaces. */
    static String norm(String in, boolean leet) {
        if (in == null) return " ";
        StringBuilder sb = new StringBuilder(in.length() + 2);
        sb.append(' ');
        boolean lastSpace = true;
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            if ((ch >= 0x064B && ch <= 0x065F) || ch == 0x0670 || ch == 0x0640
                    || (ch >= 0x200B && ch <= 0x200F) || ch == 0xFEFF) continue;
            switch (ch) {
                case 'أ': case 'إ': case 'آ': case 'ٱ': ch = 'ا'; break; // alef forms
                case 'ى': case 'ی': case 'ې': ch = 'ي'; break;               // ya forms
                case 'ة': case 'ہ': case 'ھ': case 'ە': ch = 'ه'; break; // ta marbuta / heh forms
                case 'ک': ch = 'ك'; break;                                             // keheh -> kaf
                default: break;
            }
            if (leet) {
                switch (ch) {
                    case '0': ch = 'o'; break;
                    case '1': case '!': ch = 'i'; break;
                    case '3': ch = 'e'; break;
                    case '4': case '@': ch = 'a'; break;
                    case '5': case '$': ch = 's'; break;
                    case '7': ch = 't'; break;
                    default: break;
                }
            }
            boolean keep = Character.isLetterOrDigit(ch);
            if (!keep) {
                int ty = Character.getType(ch);
                keep = ty == Character.NON_SPACING_MARK || ty == Character.COMBINING_SPACING_MARK;
            }
            if (keep) { sb.append(Character.toLowerCase(ch)); lastSpace = false; }
            else if (!lastSpace) { sb.append(' '); lastSpace = true; }
        }
        if (!lastSpace) sb.append(' ');
        return sb.toString();
    }

    private static boolean matches(String padded, Term t) {
        String needle = t.prefix ? " " + t.t : " " + t.t + " ";
        return padded.contains(needle);
    }

    /** Score of distinct matching terms. 4 = one strong term. */
    static int score(String text) {
        if (terms == null || text == null || text.isEmpty()) return 0;
        String a = norm(text, false);
        String b = norm(text, true);
        int s = 0;
        for (Term t : terms) if (matches(a, t) || matches(b, t)) s += t.pts;
        for (Term t : custom) if (matches(a, t) || matches(b, t)) s += t.pts;
        return s;
    }

    static boolean hostBlocked(String host) {
        if (host == null || domains == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.isEmpty()) return false;
        String cand = h;
        while (true) {
            if (domains.contains(cand)) return true;
            int i = cand.indexOf('.');
            if (i < 0) break;
            cand = cand.substring(i + 1);
        }
        for (String p : HOST_PARTS) if (h.contains(p)) return true;
        return false;
    }

    /** "https://www.example.com/path?q=1" -> "www.example.com"; returns null for anything that is not a bare host/URL. */
    static String extractHost(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.indexOf(' ') >= 0) return null;
        int i = s.indexOf("://");
        if (i >= 0) s = s.substring(i + 3);
        int end = s.length();
        for (int k = 0; k < s.length(); k++) {
            char ch = s.charAt(k);
            if (ch == '/' || ch == '?' || ch == '#' || ch == ':') { end = k; break; }
        }
        s = s.substring(0, end);
        return s.indexOf('.') > 0 ? s : null;
    }
}
