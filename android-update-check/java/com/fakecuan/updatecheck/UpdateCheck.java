package com.fakecuan.updatecheck;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cek APK baru di GitHub Releases (fakecuan/web). Versi aplikasi = "1.N", judul rilis berisi "1.N".
 * Konfigurasi lewat meta-data di AndroidManifest:
 *   updatecheck.tag    = tag rilis, mis. "diva-skate-latest"
 *   updatecheck.apk    = nama file APK di rilis, mis. "DivaSkate.apk"
 *   updatecheck.dialog = "false" untuk mematikan dialog di dalam aplikasi (notifikasi tetap)
 */
public final class UpdateCheck {
    static final String CHANNEL = "app_update_v1";
    static final int JOB_ID = 9301;
    static final int NOTIF_ID = 9302;
    private static final String PREFS = "update_check";
    private static boolean dialogShownThisRun;

    private UpdateCheck() {
    }

    /** Panggil di onCreate() MainActivity. */
    public static void run(final Activity a) {
        schedule(a);
        if (Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            try {
                a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9303);
            } catch (Exception ignored) {
            }
        }
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            final int latest = check(app, true);
            if (latest <= 0 || dialogShownThisRun || !"true".equals(metaOr(app, "updatecheck.dialog", "true"))) return;
            new Handler(Looper.getMainLooper()).post(() -> showDialog(a, latest));
        }, "update-check").start();
    }

    static String metaOr(Context c, String key, String def) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(c.getPackageName(), PackageManager.GET_META_DATA);
            Bundle b = ai.metaData;
            Object v = b == null ? null : b.get(key);
            return v == null ? def : String.valueOf(v);
        } catch (Exception e) {
            return def;
        }
    }

    static int num(String s) {
        Matcher m = Pattern.compile("1\\.(\\d+)").matcher(s == null ? "" : s);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    static int installed(Context c) {
        try {
            return num(c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName);
        } catch (Exception e) {
            return -1;
        }
    }

    static String apkUrl(Context c) {
        return "https://github.com/fakecuan/web/releases/download/" + metaOr(c, "updatecheck.tag", "")
                + "/" + metaOr(c, "updatecheck.apk", "");
    }

    static String appName(Context c) {
        try {
            return String.valueOf(c.getPackageManager().getApplicationLabel(c.getApplicationInfo()));
        } catch (Exception e) {
            return "Aplikasi";
        }
    }

    /**
     * Cek rilis terbaru (jalankan di thread latar). Mengembalikan nomor versi baru, atau -1 kalau sudah terbaru.
     * Notifikasi sistem dikirim sekali untuk setiap versi baru.
     */
    static int check(Context c, boolean fromApp) {
        String tag = metaOr(c, "updatecheck.tag", "");
        int mine = installed(c);
        if (tag.isEmpty() || mine < 0) return -1;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://api.github.com/repos/fakecuan/web/releases/tags/" + tag).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "fakecuan-update-check");
            if (conn.getResponseCode() != 200) return -1;
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            int latest = num(new JSONObject(bo.toString("UTF-8")).optString("name"));
            if (latest <= mine) return -1;
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (sp.getInt("notified", -1) != latest) {
                sp.edit().putInt("notified", latest).apply();
                notifyUpdate(c, mine, latest);
            }
            return latest;
        } catch (Exception e) {
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static void schedule(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null) return;
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(6L * 60 * 60 * 1000)
                    .setPersisted(true)
                    .build());
        } catch (Exception ignored) {
        }
    }

    static Intent downloadIntent(Context c) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl(c)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    @SuppressWarnings("deprecation")
    static void notifyUpdate(Context c, int mine, int latest) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Update aplikasi", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Pemberitahuan saat ada versi APK baru");
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(c, 9304, downloadIntent(c),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = "Versi 1." + latest + " tersedia (kamu memakai 1." + mine + "). Ketuk untuk mengunduh, lalu pasang menimpa yang lama.";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Update " + appName(c) + " tersedia")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            nm.notify(NOTIF_ID, b.build());
        } catch (SecurityException ignored) {
        }
    }

    static void showDialog(final Activity a, int latest) {
        if (a.isFinishing() || dialogShownThisRun) return;
        if (Build.VERSION.SDK_INT >= 17 && a.isDestroyed()) return;
        dialogShownThisRun = true;
        try {
            new AlertDialog.Builder(a)
                    .setTitle("Versi baru tersedia")
                    .setMessage(appName(a) + " versi 1." + latest + " sudah tersedia (kamu memakai 1." + installed(a)
                            + "). Unduh lalu pasang menimpa aplikasi lama — data tidak hilang.")
                    .setPositiveButton("Unduh", (d, w) -> {
                        try {
                            a.startActivity(downloadIntent(a));
                        } catch (Exception ignored) {
                        }
                    })
                    .setNegativeButton("Nanti", null)
                    .show();
        } catch (Exception ignored) {
        }
    }
}
