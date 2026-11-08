package com.cyberhayat.tazkiah;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Daily ayah / hadith notification. */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "tazkiah_daily";
    private static final int REQ = 7701;

    static void schedule(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ, new Intent(ctx, ReminderReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
            if (!Prefs.daily(ctx)) return;
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, Prefs.dailyHour(ctx));
            c.set(Calendar.MINUTE, Prefs.dailyMin(ctx));
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) { }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Prefs.daily(ctx)) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT));
            }
            Reminder r = Reminder.pick(ctx, false);
            String lang = Prefs.lang(ctx);
            boolean ur = !"en".equals(lang);
            String title = ur ? r.hookUr : r.hookEn;
            String body = r.ar + "\n" + (ur ? r.ur : r.en) + "\n— " + r.ref;
            PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL) : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_tazkiah)
                    .setContentTitle(title)
                    .setContentText(r.ref)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(open)
                    .setAutoCancel(true);
            nm.notify(REQ, b.build());
        } catch (Exception ignored) { }
    }
}
