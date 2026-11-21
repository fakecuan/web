package com.fakecuan.editor3mf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pembungkus Android untuk https://fakecuan.github.io/web/3MF/ (3MF Editor HP).
 * Halaman web memakai jembatan "Editor3MF" untuk menyimpan file .3mf lewat
 * penyimpanan Android dan membuka file yang dikirim dari aplikasi lain.
 */
public class MainActivity extends Activity {
    static final String HOST = "fakecuan.github.io";
    static final String BASE = "https://fakecuan.github.io/web/3MF/";
    static final String FILE_PREFIX = "/__3mf/f/";
    static final int RC_CHOOSER = 11;
    static final int RC_SAVE = 12;
    static final int RC_LIB = 13;
    static final int RC_OPEN = 14;

    WebView web;
    ValueCallback<Uri[]> chooserCb;
    OutputStream saveOut;
    Uri lastSave;
    final Map<String, Uri> incoming = new HashMap<>();
    final Map<String, OutputStream> outs = new HashMap<>();
    String incomingToken;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        com.fakecuan.updatecheck.UpdateCheck.run(this);
        web = new WebView(this);
        setContentView(web);
        WebView.setWebContentsDebuggingEnabled(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setUserAgentString(s.getUserAgentString() + " Editor3MFApp/" + versionName());

        web.addJavascriptInterface(new Bridge(), "Editor3MF");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, cd, mime, len) ->
                web.evaluateJavascript("window.toast&&toast('Pakai tombol Simpan di aplikasi')", null));

        handleIntent(getIntent());
        loadApp();
    }

    String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1";
        }
    }

    boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    void loadApp() {
        // Online: selalu ambil versi terbaru. Offline: pakai salinan terakhir.
        web.getSettings().setCacheMode(online() ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        web.loadUrl(BASE + "?apk=1");
    }

    void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    // ---------- file dari aplikasi lain ("Buka dengan" / Bagikan) ----------
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (handleIntent(i) && web != null) js("window.__apkIncoming&&__apkIncoming()");
    }

    boolean handleIntent(Intent i) {
        if (i == null) return false;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u == null) return false;
        String k = UUID.randomUUID().toString().replace("-", "");
        synchronized (incoming) {
            incoming.put(k, u);
        }
        incomingToken = k;
        return true;
    }

    String register(Uri u) {
        String k = UUID.randomUUID().toString().replace("-", "");
        synchronized (incoming) {
            incoming.put(k, u);
        }
        return k;
    }

    Uri uriOf(String k) {
        synchronized (incoming) {
            return k == null ? null : incoming.get(k);
        }
    }

    String infoJson(String k) {
        Uri u = uriOf(k);
        if (u == null) return "";
        try {
            JSONObject o = new JSONObject();
            o.put("token", k);
            o.put("name", displayName(u));
            o.put("writable", !"file".equals(u.getScheme()));
            return o.toString();
        } catch (Exception e) {
            return "";
        }
    }

    void takePerm(Uri u) {
        try {
            getContentResolver().takePersistableUriPermission(u,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            try {
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
    }

    String displayName(Uri u) {
        String n = null;
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) n = c.getString(i);
            }
        } catch (Exception ignored) {
        }
        if (n == null) n = u.getLastPathSegment();
        return n == null ? "model.3mf" : n;
    }

    // ---------- jembatan untuk halaman web ----------
    class Bridge {
        @JavascriptInterface
        public String version() {
            return versionName();
        }

        /** buka dialog "simpan sebagai"; hasilnya dikirim ke window.__apkWrite(ok) */
        @JavascriptInterface
        public void pickSave(String name) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/octet-stream");
                i.putExtra(Intent.EXTRA_TITLE, name == null || name.isEmpty() ? "model.3mf" : name);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    startActivityForResult(i, RC_SAVE);
                } catch (Exception e) {
                    js("window.__apkWrite&&__apkWrite(false)");
                }
            });
        }

        @JavascriptInterface
        public String chunk(String b64) {
            if (saveOut == null && lastSave != null) {
                try {
                    saveOut = getContentResolver().openOutputStream(lastSave, "wt");
                } catch (Exception e) {
                    return "Tidak bisa menulis: " + e.getMessage();
                }
                lastSave = null;
            }
            if (saveOut == null) return "File belum dibuka";
            try {
                saveOut.write(Base64.decode(b64, Base64.DEFAULT));
                return "ok";
            } catch (Exception e) {
                return "Gagal menulis: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String end() {
            if (saveOut == null) return "ok";
            try {
                saveOut.flush();
                saveOut.close();
                return "ok";
            } catch (Exception e) {
                return "Gagal menutup file: " + e.getMessage();
            } finally {
                saveOut = null;
            }
        }

        /** file yang dikirim dari aplikasi lain: {"token","name","writable"} atau "" */
        @JavascriptInterface
        public String incoming() {
            String k = incomingToken;
            incomingToken = null;
            return k == null ? "" : infoJson(k);
        }

        @JavascriptInterface
        public String info(String token) {
            return infoJson(token);
        }

        /** pilih file untuk dibuka (izin baca + tulis, supaya Simpan bisa menimpa) */
        @JavascriptInterface
        public void pickOpen() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    startActivityForResult(i, RC_OPEN);
                } catch (Exception e) {
                    js("window.__apkOpened&&__apkOpened(null)");
                }
            });
        }

        /** tulis ulang (menimpa) file token */
        @JavascriptInterface
        public String begin(String token) {
            Uri u = uriOf(token);
            if (u == null) return "file tidak dikenal";
            try {
                OutputStream o = getContentResolver().openOutputStream(u, "wt");
                if (o == null) return "tidak bisa menulis ke file ini";
                synchronized (outs) {
                    OutputStream old = outs.put(token, o);
                    if (old != null) try { old.close(); } catch (Exception ignored) { }
                }
                return "ok";
            } catch (Exception e) {
                return "tidak ada izin menulis";
            }
        }

        @JavascriptInterface
        public String chunkTo(String token, String b64) {
            OutputStream o;
            synchronized (outs) {
                o = outs.get(token);
            }
            if (o == null) return "file belum dibuka untuk ditulis";
            try {
                o.write(Base64.decode(b64, Base64.DEFAULT));
                return "ok";
            } catch (Exception e) {
                return "gagal menulis: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String endTo(String token) {
            OutputStream o;
            synchronized (outs) {
                o = outs.remove(token);
            }
            if (o == null) return "ok";
            try {
                o.flush();
                o.close();
                return "ok";
            } catch (Exception e) {
                return "gagal menutup file: " + e.getMessage();
            }
        }

        /** library online (MakerWorld): file yang diunduh langsung dibuka di editor */
        @JavascriptInterface
        public void openLibrary(String url) {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, LibraryActivity.class);
                i.putExtra(LibraryActivity.EXTRA_URL, url);
                startActivityForResult(i, RC_LIB);
            });
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(MainActivity.this::finish);
        }

        @JavascriptInterface
        public void reload() {
            runOnUiThread(MainActivity.this::loadApp);
        }
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == RC_CHOOSER) {
            if (chooserCb == null) return;
            Uri[] r = null;
            if (res == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    r = new Uri[cd.getItemCount()];
                    for (int i = 0; i < r.length; i++) r[i] = cd.getItemAt(i).getUri();
                } else if (data.getData() != null) r = new Uri[]{data.getData()};
            }
            chooserCb.onReceiveValue(r);
            chooserCb = null;
            return;
        }
        if (rc == RC_LIB) {
            if (res == RESULT_OK && data != null && data.getStringExtra(LibraryActivity.RESULT_PATH) != null) {
                Uri u = Uri.fromFile(new java.io.File(data.getStringExtra(LibraryActivity.RESULT_PATH)));
                incomingToken = register(u);
                js("window.__apkIncoming&&__apkIncoming()");
            }
            return;
        }
        if (rc == RC_OPEN) {
            if (res != RESULT_OK || data == null || data.getData() == null) {
                js("window.__apkOpened&&__apkOpened(null)");
                return;
            }
            Uri u = data.getData();
            takePerm(u);
            String info = infoJson(register(u));
            js("window.__apkOpened&&__apkOpened(" + (info.isEmpty() ? "null" : info) + ")");
            return;
        }
        if (rc == RC_SAVE) {
            if (res != RESULT_OK || data == null || data.getData() == null) {
                js("window.__apkWrite&&__apkWrite(false)");
                return;
            }
            Uri u = data.getData();
            takePerm(u);
            String k = register(u);
            lastSave = u;
            // cara lama (chunk/end) tetap didukung
            try {
                if (saveOut != null) saveOut.close();
            } catch (Exception ignored) {
            }
            saveOut = null;
            js("window.__apkWrite&&__apkWrite(true," + JSONObject.quote(k) + ")");
        }
    }

    // ---------- WebView ----------
    class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            if (HOST.equals(u.getHost())) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception ignored) {
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            String p = u.getPath();
            if (HOST.equals(u.getHost()) && p != null && p.contains(FILE_PREFIX)) {
                String k = p.substring(p.indexOf(FILE_PREFIX) + FILE_PREFIX.length());
                Uri src;
                synchronized (incoming) {
                    src = incoming.get(k);
                }
                Map<String, String> h = new HashMap<>();
                h.put("Cache-Control", "no-store");
                h.put("Access-Control-Allow-Origin", "*");
                try {
                    InputStream in = src == null ? null : getContentResolver().openInputStream(src);
                    if (in == null) throw new Exception("not found");
                    return new WebResourceResponse("application/octet-stream", null, 200, "OK", h, in);
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", h, new ByteArrayInputStream(new byte[0]));
                }
            }
            return null;
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r.isForMainFrame()) {
                String html = "<html><head><meta name=viewport content='width=device-width,initial-scale=1'></head>"
                        + "<body style='background:#121417;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:90vh;text-align:center;color:#e9ecef'>"
                        + "<h3>Tidak ada koneksi internet</h3><p>Aplikasi perlu internet saat pertama dibuka.</p>"
                        + "<button onclick='Editor3MF.reload()' style='padding:12px 22px;border:0;border-radius:24px;background:#00ae42;color:#fff;font-size:16px'>Coba lagi</button>"
                        + "<p style='color:#99a1a9;font-size:12px'>APK " + versionName() + "</p></body></html>";
                v.loadDataWithBaseURL(BASE, html, "text/html", "utf-8", null);
            }
        }
    }

    class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (chooserCb != null) chooserCb.onReceiveValue(null);
            chooserCb = cb;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            String[] acc = p.getAcceptTypes();
            String type = "*/*";
            if (acc != null && acc.length > 0 && acc[0] != null && acc[0].startsWith("image")) type = "image/*";
            i.setType(type);
            if (p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                startActivityForResult(Intent.createChooser(i, "Pilih file"), RC_CHOOSER);
            } catch (Exception e) {
                chooserCb = null;
                return false;
            }
            return true;
        }
    }

    // tombol kembali: tutup jendela di aplikasi dulu, baru keluar
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__apkBack?__apkBack():'exit'", r -> {
            String v = r == null ? "" : r.replace("\"", "");
            if ("handled".equals(v)) return;
            if ("dirty".equals(v)) {
                new AlertDialog.Builder(this)
                        .setMessage("Ada perubahan yang belum disimpan. Keluar dari aplikasi?")
                        .setPositiveButton("Keluar", (d, w) -> finish())
                        .setNegativeButton("Batal", null)
                        .show();
                return;
            }
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
