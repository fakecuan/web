package com.fakecuan.divaskate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Notifikasi sistem Android untuk pesan, ajakan main, dan pemberitahuan lain. */
final class Notif {
    static final String CH = "diva_pesan";
    static volatile boolean foreground = false;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("diva", Context.MODE_PRIVATE); }

    static void channel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "Pesan & ajakan main", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Pesan dari teman, ajakan main, dan pemberitahuan Diva Skate");
            nm.createNotificationChannel(ch);
        }
    }

    /** true bila key ini belum pernah ditandai; sekaligus menandainya. */
    static synchronized boolean markNew(Context c, String key) {
        SharedPreferences p = prefs(c);
        Set<String> s = new HashSet<>(p.getStringSet("seen", new HashSet<>()));
        if (s.contains(key)) return false;
        if (s.size() > 400) s.clear();
        s.add(key);
        p.edit().putStringSet("seen", s).apply();
        return true;
    }

    /** Tampilkan notifikasi bila key baru. Saat aplikasi sedang dibuka, hanya ditandai (tidak muncul). */
    static void show(Context c, String key, String title, String body, String kind, String sender) {
        if (!markNew(c, key)) return;
        if (foreground) return;
        channel(c);
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("nkind", kind == null ? "" : kind);
        i.putExtra("nsender", sender == null ? "" : sender);
        PendingIntent pi = PendingIntent.getActivity(c, key.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF1B5CC4)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build();
        try { c.getSystemService(NotificationManager.class).notify(key.hashCode(), n); } catch (SecurityException e) { }
    }
}
