package com.fakecuan.divaskate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pembungkus Android untuk game Diva Skate di https://fakecuan.github.io/web/racer/
 * Game selalu dimuat dari internet, jadi pembaruan index.html langsung sampai tanpa instal ulang.
 */
public class MainActivity extends Activity {
    static final String HOST = "fakecuan.github.io";
    static final String BASE = "https://fakecuan.github.io/web/racer/";
    static final String REL_API = "https://api.github.com/repos/fakecuan/web/releases/tags/diva-skate-latest";
    static final String APK_URL = "https://github.com/fakecuan/web/releases/download/diva-skate-latest/DivaSkate.apk";

    WebView web;
    long lastBack = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B2A6A);
        setContentView(web);
        immersive();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        int vc = 1; try { vc = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { }
        s.setUserAgentString(s.getUserAgentString() + " DivaSkateApp/" + vc);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/web/racer")) return false;
                // link luar (WhatsApp, Instagram, dll.) dibuka di aplikasi lain
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadData("<html><body style='margin:0;background:#0b2a6a;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center'>"
                        + "<div><h2>Tidak ada koneksi internet</h2><p>Sambungkan internet lalu tap tombol di bawah.</p>"
                        + "<button onclick=\"location.href='" + BASE + "'\" style='font-size:18px;padding:12px 26px;border:0;border-radius:24px;background:#e8302a;color:#fff'>COBA LAGI</button></div></body></html>",
                        "text/html", "utf-8");
                }
            }
        });
        if (b != null) web.restoreState(b); else web.loadUrl(BASE);
        cekUpdate();
    }

    /** Cek versi APK terbaru di GitHub Releases; kalau lebih baru, tawarkan update. */
    void cekUpdate() {
        new Thread(() -> {
            try {
                @SuppressWarnings("deprecation") int mine = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                HttpURLConnection c = (HttpURLConnection) new URL(REL_API).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) { String l; while ((l = r.readLine()) != null) sb.append(l); }
                Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"Diva Skate APK 1\\.(\\d+)\"").matcher(sb);
                if (!m.find()) return;
                int latest = Integer.parseInt(m.group(1));
                if (latest <= mine) return;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    new AlertDialog.Builder(this)
                        .setTitle("Versi baru tersedia")
                        .setMessage("Aplikasi Diva Skate versi 1." + latest + " sudah ada (kamu memakai 1." + mine + "). Unduh lalu pasang di atas versi lama, data tidak hilang.")
                        .setPositiveButton("UPDATE", (d, w) -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))); } catch (Exception e) { } })
                        .setNegativeButton("NANTI", null)
                        .show();
                });
            } catch (Exception e) { }
        }).start();
    }

    void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }

    @Override
    public void onBackPressed() {
        // biarkan game menangani tombol kembali (tutup pop up / kembali ke menu sebelumnya)
        web.evaluateJavascript("(window.__divaBack&&window.__divaBack())?1:0", res -> {
            if ("1".equals(res)) return;
            long now = System.currentTimeMillis();
            if (now - lastBack < 2000) { finish(); return; }
            lastBack = now;
            Toast.makeText(this, "Tekan kembali sekali lagi untuk keluar", Toast.LENGTH_SHORT).show();
        });
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); web.evaluateJavascript("window.dispatchEvent(new Event('blur'))", null); }
    @Override protected void onResume() { super.onResume(); web.onResume(); immersive(); }
    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }
    @Override protected void onDestroy() { if (web != null) web.destroy(); super.onDestroy(); }
}
