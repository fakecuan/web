package com.fakecuan.ordernotify;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/** Dipanggil dari MainActivity: minta izin notifikasi, minta bebas penghemat baterai (sekali), nyalakan layanan. */
public final class OrderNotifySetup {
    private OrderNotifySetup() {
    }

    public static void run(Activity a) {
        OrderWatchService.createChannels(a);
        if (Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7101);
        }
        SharedPreferences sp = a.getSharedPreferences("order_notify", Activity.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 23 && !sp.getBoolean("askedBattery", false)) {
            PowerManager pm = (PowerManager) a.getSystemService(Activity.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(a.getPackageName())) {
                sp.edit().putBoolean("askedBattery", true).apply();
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + a.getPackageName()));
                    a.startActivity(i);
                } catch (Exception ignored) {
                }
            }
        }
        OrderWatchService.start(a);
    }
}
