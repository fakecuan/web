package com.fakecuan.divaskate;

import android.content.Context;
import android.webkit.JavascriptInterface;

/** Dipanggil dari game (window.DivaApp) untuk sesi akun & notifikasi. */
public class Bridge {
    final Context c;
    Bridge(Context c) { this.c = c.getApplicationContext(); }

    @JavascriptInterface public void setSession(String name, String token) {
        Notif.prefs(c).edit().putString("name", name).putString("token", token).apply();
        NotifJob.schedule(c);
    }
    @JavascriptInterface public void clearSession() {
        Notif.prefs(c).edit().remove("name").remove("token").apply();
    }
    @JavascriptInterface public void notify(String key, String title, String body, String kind, String sender) {
        Notif.show(c, key, title, body, kind, sender);
    }
    @JavascriptInterface public int version() { return 2; }
}
