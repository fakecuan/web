package com.fakecuan.ordernotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Menyalakan lagi pemantau order setelah HP restart atau aplikasi diperbarui. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            OrderWatchService.start(context);
        }
    }
}
