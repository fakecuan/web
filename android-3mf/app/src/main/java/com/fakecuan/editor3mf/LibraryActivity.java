package com.fakecuan.editor3mf;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Library online: membuka MakerWorld di dalam aplikasi. Saat pengguna menekan
 * Download (3MF / STL) atau "Buka di Bambu Studio", file diunduh ke cache lalu
 * dikirim kembali ke editor (MainActivity) supaya langsung terbuka.
 */
public class LibraryActivity extends Activity {
    static final String EXTRA_URL = "url";
    static final String RESULT_PATH = "path";
    static final String RESULT_NAME = "name";

    WebView web;
    ProgressBar bar;
    View busy;
    TextView busyTxt;
    ValueCallback<Uri[]> chooserCb;
    OutputStream blobOut;
    File blobFile;
    volatile boolean downloading;

    int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    TextView btn(String t) {
        TextView b = new TextView(this);
        b.setText(t);
        b.setTextColor(Color.parseColor("#E9ECEF"));
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setClickable(true);
        b.setBackgroundResource(android.R.drawable.list_selector_background);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121417"));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(Color.parseColor("#1C1F23"));
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = btn("‹");
        back.setTextSize(26);
        TextView title = new TextView(this);
        title.setText("Library online · MakerWorld");
        title.setTextColor(Color.parseColor("#E9ECEF"));
        title.setTextSize(15);
        title.setSingleLine(true);
        TextView close = btn("Tutup");
        close.setTextColor(Color.parseColor("#0BD35A"));
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        root.addView(top);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        FrameLayout frame = new FrameLayout(this);
        web = new WebView(this);
        frame.addView(web);

        LinearLayout ov = new LinearLayout(this);
        ov.setOrientation(LinearLayout.VERTICAL);
        ov.setGravity(Gravity.CENTER);
        ov.setBackgroundColor(Color.parseColor("#CC121417"));
        ov.setClickable(true);
        ov.addView(new ProgressBar(this));
        busyTxt = new TextView(this);
        busyTxt.setTextColor(Color.WHITE);
        busyTxt.setTextSize(15);
        busyTxt.setGravity(Gravity.CENTER);
        busyTxt.setPadding(dp(24), dp(12), dp(24), 0);
        ov.addView(busyTxt);
        ov.setVisibility(View.GONE);
        busy = ov;
        frame.addView(ov, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        back.setOnClickListener(v -> onBackPressed());
        close.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // tampil seperti Chrome biasa (bukan "wv") supaya situs & login berjalan normal
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replaceAll("Version/[0-9.]+ ", ""));

        web.addJavascriptInterface(new BlobBridge(), "LibNative");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            String name = URLUtil.guessFileName(url, cd, mime);
            if (url.startsWith("blob:")) grabBlob(url, name);
            else if (url.startsWith("data:")) grabData(url, name);
            else download(url, name);
        });

        String start = getIntent().getStringExtra(EXTRA_URL);
        web.loadUrl(start == null ? "https://makerworld.com/" : start);
    }

    void showBusy(String t) {
        runOnUiThread(() -> {
            busyTxt.setText(t);
            busy.setVisibility(t == null ? View.GONE : View.VISIBLE);
        });
    }

    void toastJs(String t) {
        runOnUiThread(() -> new android.app.AlertDialog.Builder(this).setMessage(t).setPositiveButton("OK", null).show());
    }

    File outFile(String name) {
        File d = new File(getCacheDir(), "library");
        d.mkdirs();
        File[] old = d.listFiles();
        if (old != null) for (File f : old) f.delete();
        String safe = (name == null || name.isEmpty() ? "model.3mf" : name).replaceAll("[\\\\/:*?\"<>|]", "_");
        return new File(d, safe);
    }

    void deliver(File f) {
        runOnUiThread(() -> {
            Intent r = new Intent();
            r.putExtra(RESULT_PATH, f.getAbsolutePath());
            r.putExtra(RESULT_NAME, f.getName());
            setResult(RESULT_OK, r);
            finish();
        });
    }

    static boolean modelName(String n) {
        String l = n == null ? "" : n.toLowerCase();
        return l.endsWith(".3mf") || l.endsWith(".stl");
    }

    // ---------- unduh lewat jaringan (pakai cookie login MakerWorld) ----------
    void download(String url, String guess) {
        if (downloading) return;
        downloading = true;
        showBusy("Mengunduh model…");
        final String ua = web.getSettings().getUserAgentString();
        final String ref = web.getUrl();
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                String u = url;
                for (int hop = 0; hop < 6; hop++) {
                    c = (HttpURLConnection) new URL(u).openConnection();
                    c.setInstanceFollowRedirects(false);
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(60000);
                    c.setRequestProperty("User-Agent", ua);
                    if (ref != null) c.setRequestProperty("Referer", ref);
                    String ck = CookieManager.getInstance().getCookie(u);
                    if (ck != null) c.setRequestProperty("Cookie", ck);
                    int code = c.getResponseCode();
                    if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                        u = new URL(new URL(u), c.getHeaderField("Location")).toString();
                        c.disconnect();
                        continue;
                    }
                    if (code != 200) throw new Exception("HTTP " + code);
                    break;
                }
                String name = URLUtil.guessFileName(u, c.getHeaderField("Content-Disposition"), c.getContentType());
                if (!modelName(name) && modelName(guess)) name = guess;
                if (!modelName(name)) name = name.replaceAll("\\.(bin|zip)$", "") + ".3mf";
                File f = outFile(name);
                long total = c.getContentLengthLong(), got = 0;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        got += n;
                        if (total > 0) showBusy("Mengunduh model… " + (got * 100 / total) + "%");
                        else showBusy("Mengunduh model… " + (got / 1024) + " KB");
                    }
                }
                deliver(f);
            } catch (Exception e) {
                showBusy(null);
                toastJs("Gagal mengunduh: " + e.getMessage());
            } finally {
                if (c != null) c.disconnect();
                downloading = false;
            }
        }).start();
    }

    void grabData(String url, String name) {
        try {
            int i = url.indexOf(',');
            byte[] data = url.substring(0, i).contains(";base64")
                    ? Base64.decode(url.substring(i + 1), Base64.DEFAULT)
                    : Uri.decode(url.substring(i + 1)).getBytes("UTF-8");
            File f = outFile(modelName(name) ? name : "model.3mf");
            try (OutputStream o = new FileOutputStream(f)) {
                o.write(data);
            }
            deliver(f);
        } catch (Exception e) {
            toastJs("Gagal membaca file: " + e.getMessage());
        }
    }

    // ---------- file dari blob: (dibuat oleh halaman) ----------
    void grabBlob(String url, String name) {
        showBusy("Menyiapkan file…");
        String js = "(async()=>{try{const b=await (await fetch(" + JSONObject.quote(url) + ")).blob();"
                + "const u=new Uint8Array(await b.arrayBuffer());LibNative.begin(" + JSONObject.quote(name) + ");"
                + "for(let i=0;i<u.length;i+=196608){let s='';const p=u.subarray(i,i+196608);"
                + "for(let j=0;j<p.length;j+=8192)s+=String.fromCharCode.apply(null,p.subarray(j,j+8192));LibNative.chunk(btoa(s));}"
                + "LibNative.end('');}catch(e){LibNative.end(String(e&&e.message||e));}})()";
        web.evaluateJavascript(js, null);
    }

    class BlobBridge {
        @JavascriptInterface
        public void begin(String name) {
            try {
                blobFile = outFile(modelName(name) ? name : "model.3mf");
                blobOut = new FileOutputStream(blobFile);
            } catch (Exception e) {
                blobOut = null;
            }
        }

        @JavascriptInterface
        public void chunk(String b64) {
            try {
                if (blobOut != null) blobOut.write(Base64.decode(b64, Base64.DEFAULT));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void end(String err) {
            try {
                if (blobOut != null) blobOut.close();
            } catch (Exception ignored) {
            }
            blobOut = null;
            if (err == null || err.isEmpty()) {
                if (blobFile != null) deliver(blobFile);
            } else {
                showBusy(null);
                toastJs("Gagal membaca file: " + err);
            }
        }
    }

    // ---------- navigasi ----------
    class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            String sc = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
            // "Buka di Bambu Studio / Orca" → ambil link file-nya
            if (sc.startsWith("bambustudio") || sc.startsWith("orcaslicer") || sc.startsWith("orca-slicer")) {
                String f = u.getQueryParameter("file");
                if (f == null) {
                    String s = u.toString();
                    int i = s.indexOf("http");
                    if (i >= 0) f = Uri.decode(s.substring(i));
                }
                if (f != null && f.startsWith("http")) {
                    download(f, URLUtil.guessFileName(f, null, null));
                } else toastJs("Link ini tidak berisi file. Pakai tombol Download → 3MF.");
                return true;
            }
            if (sc.equals("http") || sc.equals("https")) {
                String p = u.getPath() == null ? "" : u.getPath().toLowerCase();
                if (p.endsWith(".3mf") || p.endsWith(".stl")) {
                    download(u.toString(), u.getLastPathSegment());
                    return true;
                }
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception ignored) {
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            bar.setVisibility(View.GONE);
        }
    }

    class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView v, int p) {
            bar.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            bar.setProgress(p);
        }

        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (chooserCb != null) chooserCb.onReceiveValue(null);
            chooserCb = cb;
            try {
                startActivityForResult(p.createIntent(), 21);
            } catch (Exception e) {
                chooserCb = null;
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == 21 && chooserCb != null) {
            chooserCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            chooserCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (busy.getVisibility() == View.VISIBLE) return;
        if (web.canGoBack()) web.goBack();
        else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
