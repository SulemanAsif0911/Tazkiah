package com.cyberhayat.tazkiah;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

/** Restores the DNS shield and the daily reminder after a reboot or an app update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            if (Prefs.dnsWanted(ctx) && VpnService.prepare(ctx) == null) {
                ctx.startService(new Intent(ctx, DnsShieldService.class).setAction(DnsShieldService.ACTION_START));
            }
        } catch (Exception ignored) { }
        ReminderReceiver.schedule(ctx);
    }
}
