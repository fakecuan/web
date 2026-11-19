package com.fakecuan.divaskinsuit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Pembungkus Android untuk https://fakecuan.github.io/web/sseditor/
 * Halaman web memakai jembatan "DivaNative" untuk buka/simpan file langsung
 * (simpan menimpa file yang sama) dan berbagi video.
 */
public class MainActivity extends Activity {
    static final String HOST = "fakecuan.github.io";
    static final String BASE = "https://fakecuan.github.io/web/sseditor/";
    static final String FILE_PREFIX = "/__diva/f/";
    static final int RC_CHOOSER = 11;
    static final int RC_PICK = 12;
    static final int RC_TREE = 13;
    String authPending;

    WebView web;
    ValueCallback<Uri[]> chooserCb;
    String pickReq;
    boolean pickSave;

    /** token -> target file (Uri dari Android atau file sementara) */
    static class Target {
        Uri uri;
        File file;
        String name;
        String mime;
        OutputStream out;
    }

    final Map<String, Target> targets = new HashMap<>();
    String incomingToken;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString(s.getUserAgentString() + " DivaSkinsuitApp/" + versionName());

        web.addJavascriptInterface(new Bridge(), "DivaNative");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, cd, mime, len) ->
                web.evaluateJavascript("window.toast&&toast('Gunakan tombol Simpan / Unduh di aplikasi')", null));

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

    // ---------- file yang dibuka dari aplikasi lain ("Buka dengan") ----------
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (handleIntent(i) && web != null) {
            web.evaluateJavascript("window.__divaIncoming&&__divaIncoming()", null);
        }
    }

    boolean handleIntent(Intent i) {
        if (i == null) return false;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null && "divaskinsuit".equals(i.getData().getScheme())) {
            String q = i.getData().getEncodedQuery();
            authPending = q == null ? "" : q;
            if (web != null) js("window.__gdAuth&&__gdAuth()");
            return false;
        }
        if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u == null) return false;
        Target t = new Target();
        t.uri = u;
        t.name = displayName(u);
        incomingToken = register(t);
        return true;
    }

    String register(Target t) {
        String k = UUID.randomUUID().toString().replace("-", "");
        synchronized (targets) {
            targets.put(k, t);
        }
        return k;
    }

    Target get(String k) {
        synchronized (targets) {
            return targets.get(k);
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
        return n == null ? "file" : n;
    }

    long size(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    String info(String token, Target t) {
        try {
            JSONObject o = new JSONObject();
            o.put("token", token);
            o.put("name", t.name);
            o.put("size", t.uri != null ? size(t.uri) : (t.file != null ? t.file.length() : -1));
            return o.toString();
        } catch (Exception e) {
            return "null";
        }
    }

    void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    // ---------- jembatan untuk halaman web ----------
    class Bridge {
        @JavascriptInterface
        public String version() {
            return versionName();
        }

        /** mode "open" = pilih file untuk dibuka (tetap bisa ditimpa), "save" = buat file baru */
        @JavascriptInterface
        public void pick(String req, String mode, String name, String mime) {
            runOnUiThread(() -> {
                pickReq = req;
                pickSave = "save".equals(mode);
                Intent i;
                if (pickSave) {
                    i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                    i.putExtra(Intent.EXTRA_TITLE, name == null ? "file" : name);
                } else {
                    i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                }
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    startActivityForResult(i, RC_PICK);
                } catch (Exception e) {
                    js("window.__divaPick&&__divaPick(" + JSONObject.quote(req) + ",null)");
                }
            });
        }

        /** isi papan klip: gambar (token file) atau teks */
        @JavascriptInterface
        public String clipImage() {
            final String[] out = {""};
            final CountDownLatch done = new CountDownLatch(1);
            runOnUiThread(() -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData c = cm == null ? null : cm.getPrimaryClip();
                    if (c != null) {
                        for (int i = 0; i < c.getItemCount() && out[0].isEmpty(); i++) {
                            ClipData.Item it = c.getItemAt(i);
                            Uri u = it.getUri();
                            if (u != null) {
                                String mime = null;
                                try { mime = getContentResolver().getType(u); } catch (Exception ignored) { }
                                if (mime == null || mime.startsWith("image/")) {
                                    Target t = new Target();
                                    t.uri = u;
                                    t.name = displayName(u);
                                    String k = register(t);
                                    JSONObject o = new JSONObject(info(k, t));
                                    o.put("mime", mime == null ? "" : mime);
                                    out[0] = o.toString();
                                }
                            }
                        }
                        if (out[0].isEmpty() && c.getItemCount() > 0) {
                            CharSequence tx = c.getItemAt(0).coerceToText(MainActivity.this);
                            if (tx != null && tx.length() > 0) {
                                JSONObject o = new JSONObject();
                                o.put("text", tx.toString());
                                out[0] = o.toString();
                            }
                        }
                    }
                } catch (Exception e) {
                    out[0] = "";
                }
                done.countDown();
            });
            try { done.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            return out[0];
        }

        @JavascriptInterface
        public String incoming() {
            String k = incomingToken;
            incomingToken = null;
            if (k == null) return "";
            Target t = get(k);
            return t == null ? "" : info(k, t);
        }

        /** file sementara (untuk dibagikan) */
        @JavascriptInterface
        public String temp(String name) {
            File d = new File(getCacheDir(), "share");
            d.mkdirs();
            String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            Target t = new Target();
            t.file = new File(d, safe);
            t.name = safe;
            return register(t);
        }

        @JavascriptInterface
        public String begin(String token) {
            Target t = get(token);
            if (t == null) return "File tidak dikenal";
            try {
                if (t.out != null) t.out.close();
                if (t.file != null) t.out = new FileOutputStream(t.file);
                else {
                    ContentResolver cr = getContentResolver();
                    OutputStream o;
                    try {
                        o = cr.openOutputStream(t.uri, "wt");
                    } catch (Exception e) {
                        o = cr.openOutputStream(t.uri, "w");
                    }
                    if (o == null) return "Tidak bisa menulis ke file ini";
                    t.out = o;
                }
                return "ok";
            } catch (Exception e) {
                return "Tidak bisa menulis: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String chunk(String token, String b64) {
            Target t = get(token);
            if (t == null || t.out == null) return "File belum dibuka untuk ditulis";
            try {
                t.out.write(Base64.decode(b64, Base64.DEFAULT));
                return "ok";
            } catch (Exception e) {
                return "Gagal menulis: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String end(String token) {
            Target t = get(token);
            if (t == null || t.out == null) return "ok";
            try {
                t.out.flush();
                t.out.close();
                t.out = null;
                return "ok";
            } catch (Exception e) {
                t.out = null;
                return "Gagal menutup file: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void share(String token, String mime, String title) {
            Target t = get(token);
            if (t == null) return;
            runOnUiThread(() -> {
                Uri u = t.file != null ? ShareProvider.uriFor(t.file) : t.uri;
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                i.putExtra(Intent.EXTRA_STREAM, u);
                i.setClipData(ClipData.newRawUri(t.name, u));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, title == null || title.isEmpty() ? "Bagikan" : title));
            });
        }

        /* ---------- login Google Drive (lewat browser, kembali ke aplikasi) ---------- */
        @JavascriptInterface
        public String authResult() {
            String a = authPending;
            authPending = null;
            return a == null ? "" : a;
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
            });
        }

        /* ---------- folder di HP (izin folder sekali, lalu bisa dijelajah) ---------- */
        @JavascriptInterface
        public void treeAdd(String req) {
            runOnUiThread(() -> {
                pickReq = req;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                try {
                    startActivityForResult(i, RC_TREE);
                } catch (Exception e) {
                    js("window.__divaTree&&__divaTree(" + JSONObject.quote(req) + ",null)");
                }
            });
        }

        @JavascriptInterface
        public String treeList(String tree, String docId) {
            try {
                Uri t = Uri.parse(tree);
                String id = docId == null || docId.isEmpty() ? DocumentsContract.getTreeDocumentId(t) : docId;
                Uri ch = DocumentsContract.buildChildDocumentsUriUsingTree(t, id);
                String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED};
                JSONArray a = new JSONArray();
                try (Cursor c = getContentResolver().query(ch, cols, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        JSONObject o = new JSONObject();
                        String mime = c.getString(2);
                        o.put("id", c.getString(0));
                        o.put("name", c.getString(1));
                        o.put("dir", DocumentsContract.Document.MIME_TYPE_DIR.equals(mime));
                        o.put("mime", mime == null ? "" : mime);
                        o.put("size", c.isNull(3) ? -1 : c.getLong(3));
                        o.put("mod", c.isNull(4) ? 0 : c.getLong(4));
                        a.put(o);
                    }
                }
                return a.toString();
            } catch (Exception e) {
                return "{\"error\":" + JSONObject.quote(String.valueOf(e.getMessage())) + "}";
            }
        }

        @JavascriptInterface
        public String treeOpen(String tree, String docId, String name) {
            try {
                Target t = new Target();
                t.uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(tree), docId);
                t.name = name == null || name.isEmpty() ? displayName(t.uri) : name;
                return info(register(t), t);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String treeCreate(String tree, String parentDocId, String name, String mime) {
            try {
                Uri t = Uri.parse(tree);
                String pid = parentDocId == null || parentDocId.isEmpty() ? DocumentsContract.getTreeDocumentId(t) : parentDocId;
                Uri p = DocumentsContract.buildDocumentUriUsingTree(t, pid);
                Uri n = DocumentsContract.createDocument(getContentResolver(), p,
                        mime == null || mime.isEmpty() ? "application/octet-stream" : mime, name);
                if (n == null) return "";
                Target tg = new Target();
                tg.uri = n;
                tg.name = displayName(n);
                JSONObject o = new JSONObject(info(register(tg), tg));
                o.put("id", DocumentsContract.getDocumentId(n));
                return o.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void treeDrop(String tree) {
            try {
                getContentResolver().releasePersistableUriPermission(Uri.parse(tree),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }

        /* ---------- akses semua file di HP (seperti file manager) ---------- */
        @JavascriptInterface
        public boolean fsAllowed() {
            if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void fsRequest() {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
                        try {
                            startActivity(i);
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    } else {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 21);
                    }
                } catch (Exception ignored) {
                }
            });
        }

        @JavascriptInterface
        public String fsRoot() {
            return Environment.getExternalStorageDirectory().getAbsolutePath();
        }

        JSONObject fo(File f) throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", f.getAbsolutePath());
            o.put("name", f.getName());
            o.put("dir", f.isDirectory());
            o.put("size", f.isDirectory() ? -1 : f.length());
            o.put("mod", f.lastModified());
            o.put("parent", f.getParent());
            return o;
        }

        @JavascriptInterface
        public String fsList(String path) {
            try {
                File d = new File(path);
                File[] l = d.listFiles();
                if (l == null) return "{\"error\":\"Folder tidak bisa dibaca\"}";
                JSONArray a = new JSONArray();
                for (File f : l) if (!f.getName().startsWith(".")) a.put(fo(f));
                return a.toString();
            } catch (Exception e) {
                return "{\"error\":" + JSONObject.quote(String.valueOf(e.getMessage())) + "}";
            }
        }

        /** cari file desain terbaru (berjalan di latar, hasil lewat __divaRecent) */
        @JavascriptInterface
        public void fsRecent(String req, String exts, int max) {
            new Thread(() -> {
                String out = "[]";
                try {
                    Pattern pt = Pattern.compile("\\.(" + exts + ")$", Pattern.CASE_INSENSITIVE);
                    List<File> found = new ArrayList<>();
                    int[] seen = {0};
                    walk(Environment.getExternalStorageDirectory(), 0, pt, found, seen);
                    found.sort((x, y) -> Long.compare(y.lastModified(), x.lastModified()));
                    JSONArray a = new JSONArray();
                    for (int i = 0; i < found.size() && i < max; i++) a.put(fo(found.get(i)));
                    out = a.toString();
                } catch (Exception ignored) {
                }
                js("window.__divaRecent&&__divaRecent(" + JSONObject.quote(req) + "," + out + ")");
            }).start();
        }

        void walk(File d, int depth, Pattern pt, List<File> found, int[] seen) {
            if (depth > 6 || seen[0] > 40000) return;
            File[] l = d.listFiles();
            if (l == null) return;
            for (File f : l) {
                seen[0]++;
                String n = f.getName();
                if (n.startsWith(".")) continue;
                if (f.isDirectory()) {
                    if (depth == 0 && n.equals("Android")) continue;
                    walk(f, depth + 1, pt, found, seen);
                } else if (pt.matcher(n).find()) found.add(f);
            }
        }

        @JavascriptInterface
        public String fsOpen(String path) {
            try {
                Target t = new Target();
                t.file = new File(path);
                t.name = t.file.getName();
                return info(register(t), t);
            } catch (Exception e) {
                return "";
            }
        }

        /** file baru (atau menimpa) di folder; ditulis lewat begin/chunk/end */
        @JavascriptInterface
        public String fsCreate(String dir, String name) {
            try {
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                Target t = new Target();
                t.file = new File(dir, safe);
                t.name = safe;
                JSONObject o = new JSONObject(info(register(t), t));
                o.put("id", t.file.getAbsolutePath());
                return o.toString();
            } catch (Exception e) {
                return "";
            }
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
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.__divaResume&&__divaResume()", null);
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        if (web != null) web.evaluateJavascript("window.__divaResume&&__divaResume()", null);
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
        if (rc == RC_TREE) {
            String req = pickReq;
            pickReq = null;
            if (res != RESULT_OK || data == null || data.getData() == null) {
                js("window.__divaTree&&__divaTree(" + JSONObject.quote(req) + ",null)");
                return;
            }
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            String id = DocumentsContract.getTreeDocumentId(u);
            String name = displayName(DocumentsContract.buildDocumentUriUsingTree(u, id));
            try {
                JSONObject o = new JSONObject();
                o.put("uri", u.toString());
                o.put("id", id);
                o.put("name", name);
                js("window.__divaTree&&__divaTree(" + JSONObject.quote(req) + "," + o + ")");
            } catch (Exception e) {
                js("window.__divaTree&&__divaTree(" + JSONObject.quote(req) + ",null)");
            }
            return;
        }
        if (rc == RC_PICK) {
            String req = pickReq;
            pickReq = null;
            if (res != RESULT_OK || data == null || data.getData() == null) {
                js("window.__divaPick&&__divaPick(" + JSONObject.quote(req) + ",null)");
                return;
            }
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) {
                try {
                    getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
            }
            Target t = new Target();
            t.uri = u;
            t.name = displayName(u);
            String k = register(t);
            js("window.__divaPick&&__divaPick(" + JSONObject.quote(req) + "," + info(k, t) + ")");
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
                Target t = get(k);
                Map<String, String> h = new HashMap<>();
                h.put("Cache-Control", "no-store");
                h.put("Access-Control-Allow-Origin", "*");
                try {
                    InputStream in = t == null ? null
                            : (t.file != null ? new java.io.FileInputStream(t.file) : getContentResolver().openInputStream(t.uri));
                    if (in == null) throw new Exception("not found");
                    return new WebResourceResponse("application/octet-stream", null, 200, "OK", h, in);
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", h,
                            new java.io.ByteArrayInputStream(new byte[0]));
                }
            }
            return null;
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r.isForMainFrame()) {
                String html = "<html><head><meta name=viewport content='width=device-width,initial-scale=1'></head>"
                        + "<body style='font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:90vh;text-align:center;color:#333'>"
                        + "<h3>Tidak ada koneksi internet</h3><p>Aplikasi perlu internet saat pertama dibuka.</p>"
                        + "<button onclick='DivaNative.reload()' style='padding:12px 22px;border:0;border-radius:24px;background:#e8661c;color:#fff;font-size:16px'>Coba lagi</button></body></html>";
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
        web.evaluateJavascript("window.__divaBack?__divaBack():'exit'", r -> {
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

    @SuppressWarnings("unused")
    void hideSystemUi() {
        web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
