package com.fakecuan.ordernotify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Layanan latar yang terus tersambung ke topik ntfy.sh milik toko.
 * Setiap order baru (dikirim oleh form/orders-db.js) langsung muncul sebagai notifikasi bersuara,
 * walau aplikasi admin sedang ditutup. Pesan yang terlewat saat HP offline diambil ulang lewat since=<id>.
 */
public class OrderWatchService extends Service {
    static final String CH_ORDERS = "orders_v1";
    static final String CH_WATCH = "watch_v1";
    static final int WATCH_ID = 7001;
    private static final String PREFS = "order_notify";

    private volatile boolean running;
    private Thread worker;
    private HttpURLConnection conn;
    private PowerManager.WakeLock wake;

    /** Dipanggil dari MainActivity dan BootReceiver. */
    public static void start(Context ctx) {
        Intent i = new Intent(ctx, OrderWatchService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) {
        }
    }

    static String meta(Context ctx, String key) {
        try {
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA);
            Bundle b = ai.metaData;
            return b == null ? null : b.getString(key);
        } catch (Exception e) {
            return null;
        }
    }

    static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel orders = new NotificationChannel(CH_ORDERS, "Order masuk", NotificationManager.IMPORTANCE_HIGH);
        orders.setDescription("Bunyi dan notifikasi setiap ada pesanan baru");
        orders.enableVibration(true);
        orders.setVibrationPattern(new long[]{0, 400, 200, 400});
        orders.setSound(soundUri(ctx), new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        nm.createNotificationChannel(orders);
        NotificationChannel watch = new NotificationChannel(CH_WATCH, "Pemantau order (latar)", NotificationManager.IMPORTANCE_MIN);
        watch.setDescription("Notifikasi tetap agar pemantau order terus berjalan. Boleh disembunyikan.");
        watch.setShowBadge(false);
        nm.createNotificationChannel(watch);
    }

    static Uri soundUri(Context ctx) {
        int id = ctx.getResources().getIdentifier("order_chime", "raw", ctx.getPackageName());
        return Uri.parse("android.resource://" + ctx.getPackageName() + "/" + id);
    }

    private PendingIntent openApp(int req) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch == null) launch = new Intent();
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, req, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    private Notification.Builder builder(String channel) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
    }

    private int smallIcon() {
        int id = getResources().getIdentifier("ic_stat_order", "drawable", getPackageName());
        return id != 0 ? id : getApplicationInfo().icon;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = builder(CH_WATCH)
                .setSmallIcon(smallIcon())
                .setContentTitle("Memantau order masuk")
                .setContentText("Notifikasi akan berbunyi saat ada pesanan baru")
                .setOngoing(true)
                .setContentIntent(openApp(1))
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(WATCH_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(WATCH_ID, n);
        }
        if (!running) {
            running = true;
            worker = new Thread(this::loop, "order-watch");
            worker.start();
        }
        return START_STICKY;
    }

    private void loop() {
        String topic = meta(this, "ordernotify.topic");
        if (topic == null || topic.isEmpty()) return;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        long backoff = 3000;
        while (running) {
            try {
                String since = sp.getString("lastId", null);
                if (since == null) since = String.valueOf(System.currentTimeMillis() / 1000);
                URL url = new URL("https://ntfy.sh/" + URLEncoder.encode(topic, "UTF-8") + "/json?since=" + URLEncoder.encode(since, "UTF-8"));
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(120000); // ntfy mengirim keepalive tiap ~45 detik
                conn.setRequestProperty("User-Agent", "order-notify-android");
                if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (running && (line = r.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    backoff = 3000;
                    JSONObject m = new JSONObject(line);
                    if (!"message".equals(m.optString("event"))) continue;
                    String id = m.optString("id");
                    if (id.equals(sp.getString("lastId", ""))) continue;
                    sp.edit().putString("lastId", id).apply();
                    showOrder(m.optString("title", "Order baru"), m.optString("message", "Ada pesanan baru masuk"), id.hashCode());
                }
            } catch (Exception e) {
                // Sinyal putus / server sibuk: coba lagi dengan jeda yang makin panjang.
            } finally {
                if (conn != null) conn.disconnect();
            }
            if (!running) break;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException ie) {
                break;
            }
            backoff = Math.min(backoff * 2, 60000);
        }
    }

    private void showOrder(String title, String text, int id) {
        if (wake == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ordernotify:alert");
        }
        wake.acquire(5000);
        Notification.Builder b = builder(CH_ORDERS)
                .setSmallIcon(smallIcon())
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(openApp(2));
        if (Build.VERSION.SDK_INT < 26) {
            b.setSound(soundUri(this));
            b.setVibrate(new long[]{0, 400, 200, 400});
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        try {
            nm.notify(id, b.build());
        } catch (SecurityException ignored) {
            // Izin notifikasi belum diberikan.
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (conn != null) conn.disconnect();
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
