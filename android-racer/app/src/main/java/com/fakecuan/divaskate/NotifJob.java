package com.fakecuan.divaskate;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Cek notifikasi akun (pesan, ajakan main, teman baru) di latar belakang, kira-kira tiap 15 menit. */
public class NotifJob extends JobService {
    static final int ID = 7101;
    static final String URL_RPC = "https://yngyyoqmftslhcuqmgrn.supabase.co/rest/v1/rpc/skate_notifications_list";
    static final String KEY = "sb_publishable_kz05d2dAOlpD-hQdcxnCHg_7QVMQm6L";

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == ID) return;
        js.schedule(new JobInfo.Builder(ID, new ComponentName(c, NotifJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true)
            .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> { try { check(getApplicationContext()); } catch (Exception e) { } jobFinished(params, false); }).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }

    static void check(Context c) throws Exception {
        SharedPreferences p = Notif.prefs(c);
        String name = p.getString("name", ""), token = p.getString("token", "");
        if (name.isEmpty() || token.isEmpty()) return;
        HttpURLConnection h = (HttpURLConnection) new URL(URL_RPC).openConnection();
        h.setConnectTimeout(10000); h.setReadTimeout(10000);
        h.setRequestMethod("POST"); h.setDoOutput(true);
        h.setRequestProperty("apikey", KEY);
        h.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject(); body.put("p_name", name); body.put("p_token", token);
        try (OutputStream o = h.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        if (h.getResponseCode() != 200) return;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), StandardCharsets.UTF_8))) { String l; while ((l = r.readLine()) != null) sb.append(l); }
        JSONObject res = new JSONObject(sb.toString());
        if (!res.optBoolean("ok")) return;
        JSONArray list = res.optJSONArray("list");
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            JSONObject n = list.getJSONObject(i);
            String kind = n.optString("kind", ""), sender = n.optString("sender", "");
            String title = n.optString("title", "Diva Skate"), prev = n.optString("preview", "");
            Notif.show(c, "n" + n.optLong("id"), title, prev.isEmpty() ? "Buka Diva Skate untuk melihat" : prev, kind, sender);
        }
    }
}
