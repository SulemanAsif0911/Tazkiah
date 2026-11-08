package com.cyberhayat.tazkiah;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Device-admin hook used only to make accidental / impulsive uninstalls harder. It requests no policies. */
public class TazkiahAdminReceiver extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return context.getString(R.string.admin_disable_warning);
    }
}
