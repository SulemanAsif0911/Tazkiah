package com.cyberhayat.tazkiah;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Builds the full-screen reminder in code (always light, even in dark mode). */
final class OverlayUi {
    private OverlayUi() {}

    interface Callbacks {
        void onTurnBack();   // primary button
        void onContinue();   // guard mode only: user insists after the cooling-off
        void onNext();       // show another reminder
    }

    static final int CREAM = 0xFFFFF7F3, CORAL = 0xFFE8553A, CORAL_DEEP = 0xFFC4402A, INK = 0xFF231F1E,
            MUTED = 0xFF7A6D68, LINE = 0xFFF3D9CF, SOFT = 0xFFFDE4DB, WHITE = 0xFFFFFFFF;

    private static Typeface amiri, naskh;
    private static final Handler H = new Handler(Looper.getMainLooper());

    private static synchronized void loadFonts(Context c) {
        if (amiri != null) return;
        try { amiri = Typeface.createFromAsset(c.getAssets(), "fonts/amiri.ttf"); } catch (Exception e) { amiri = Typeface.SERIF; }
        try { naskh = Typeface.createFromAsset(c.getAssets(), "fonts/naskh.ttf"); } catch (Exception e) { naskh = Typeface.SANS_SERIF; }
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    private static GradientDrawable box(int fill, int stroke, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1.5f), stroke);
        return g;
    }

    private static TextView tv(Context c, String text, float sp, int color, Typeface tf, int gravity) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        t.setGravity(gravity);
        t.setIncludeFontPadding(true);
        return t;
    }

    private static void rtl(TextView t) {
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    private static LinearLayout.LayoutParams lp(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        return p;
    }

    static View build(final Context c, Reminder r, String lang, final boolean guard, final int waitSec, final Callbacks cb) {
        loadFonts(c);
        final Handler h = H;
        final boolean showUr = !"en".equals(lang), showEn = !"ur".equals(lang);
        final int pad = dp(c, 22);

        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(CREAM);

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(pad, dp(c, 48), pad, dp(c, 32));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.logo);
        col.addView(logo, lp(dp(c, 78), dp(c, 78), 0));

        String titleEn = guard ? "Wait — think before you remove protection" : "Pause — Allah is watching";
        String titleUr = guard ? "ٹھہریے — حفاظت ہٹانے سے پہلے سوچیے" : "رک جائیے — اللہ دیکھ رہا ہے";
        TextView tEn = tv(c, titleEn, 20, CORAL, Typeface.DEFAULT_BOLD, Gravity.CENTER);
        col.addView(tEn, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 14)));
        if (showUr) {
            TextView tUr = tv(c, titleUr, 22, INK, naskh, Gravity.CENTER);
            rtl(tUr);
            col.addView(tUr, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 4)));
        }

        // card
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(box(WHITE, LINE, 22, c));
        card.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        col.addView(card, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 18)));

        String hook = showUr ? r.hookUr : r.hookEn;
        TextView hookTv = tv(c, hook, showUr ? 19 : 17, CORAL_DEEP, showUr ? naskh : Typeface.DEFAULT_BOLD, Gravity.CENTER);
        if (showUr) rtl(hookTv); else hookTv.setTypeface(Typeface.DEFAULT_BOLD);
        hookTv.setLineSpacing(0, 1.25f);
        card.addView(hookTv, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));
        if (showUr && showEn) {
            TextView hookEn = tv(c, r.hookEn, 13, MUTED, null, Gravity.CENTER);
            card.addView(hookEn, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 6)));
        }

        View sep = new View(c);
        sep.setBackgroundColor(LINE);
        card.addView(sep, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1), dp(c, 14)));

        TextView badge = tv(c, "hadith".equals(r.type) ? "HADITH" : "QUR'AN", 11, CORAL_DEEP, Typeface.DEFAULT_BOLD, Gravity.CENTER);
        badge.setBackground(box(SOFT, 0, 20, c));
        badge.setPadding(dp(c, 12), dp(c, 3), dp(c, 12), dp(c, 3));
        badge.setLetterSpacing(0.08f);
        card.addView(badge, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 14)));

        TextView ar = tv(c, r.ar, 24, INK, amiri, Gravity.CENTER);
        rtl(ar);
        ar.setLineSpacing(0, 1.45f);
        card.addView(ar, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 12)));

        if (showUr) {
            TextView ur = tv(c, r.ur, 17, INK, naskh, Gravity.CENTER);
            rtl(ur);
            ur.setLineSpacing(0, 1.35f);
            card.addView(ur, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 12)));
        }
        if (showEn) {
            TextView en = tv(c, r.en, 14, MUTED, null, Gravity.CENTER);
            en.setLineSpacing(0, 1.2f);
            card.addView(en, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 10)));
        }
        TextView ref = tv(c, r.ref, 12, CORAL, Typeface.DEFAULT_BOLD, Gravity.CENTER);
        card.addView(ref, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 12)));

        // buttons
        final TextView primary = tv(c, "", 16, WHITE, Typeface.DEFAULT_BOLD, Gravity.CENTER);
        primary.setPadding(dp(c, 16), dp(c, 15), dp(c, 16), dp(c, 15));
        col.addView(primary, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 20)));

        final TextView second = tv(c, "", 14, MUTED, Typeface.DEFAULT_BOLD, Gravity.CENTER);
        second.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        col.addView(second, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 6)));
        if (guard) {
            second.setText("Continue anyway");
            second.setEnabled(false);
            second.setAlpha(0.45f);
            second.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { cb.onContinue(); } });
        } else {
            second.setText("Show another reminder");
            second.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { cb.onNext(); } });
        }

        final String label = guard ? "Keep my protection" : "I'm turning back — Astaghfirullah";
        primary.setEnabled(false);
        primary.setBackground(box(0xFFE9C9BF, 0, 16, c));
        primary.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { cb.onTurnBack(); } });

        // the guard screen lets "Keep my protection" work at once; the risky choice is the one that waits
        final int[] left = {waitSec};
        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (left[0] > 0) {
                    if (guard) {
                        primary.setText(label);
                        second.setText("Continue anyway (" + left[0] + "s)");
                    } else {
                        primary.setText("Take a breath… " + left[0] + "s");
                    }
                    left[0]--;
                    h.postDelayed(this, 1000);
                } else {
                    if (!guard) {
                        primary.setEnabled(true);
                        primary.setText(label);
                        primary.setBackground(box(CORAL, 0, 16, c));
                    } else {
                        second.setEnabled(true);
                        second.setAlpha(1f);
                        second.setText("Continue anyway");
                    }
                }
            }
        };
        if (guard) {
            primary.setEnabled(true);
            primary.setBackground(box(CORAL, 0, 16, c));
            primary.setText(label);
        }
        h.post(tick);
        root.setTag(tick);
        return root;
    }

    /** Stops the countdown when the view is removed. */
    static void cancel(View v) {
        if (v == null) return;
        Object t = v.getTag();
        if (t instanceof Runnable) H.removeCallbacks((Runnable) t);
    }
}
