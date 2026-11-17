package com.fakecuan.divaskinsuit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Membagikan file sementara (video, gambar) ke aplikasi lain, misalnya Instagram. */
public class ShareProvider extends ContentProvider {
    static final String AUTH = "com.fakecuan.divaskinsuit.share";

    static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTH + "/" + Uri.encode(f.getName()));
    }

    File fileFor(Uri u) throws FileNotFoundException {
        String n = u.getLastPathSegment();
        if (n == null || n.contains("/") || n.contains("..")) throw new FileNotFoundException();
        File f = new File(new File(getContext().getCacheDir(), "share"), n);
        if (!f.exists()) throw new FileNotFoundException(n);
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try {
            File f = fileFor(uri);
            c.addRow(new Object[]{f.getName(), f.length()});
        } catch (Exception ignored) {
        }
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        if (n == null) return "application/octet-stream";
        n = n.toLowerCase();
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        return 0;
    }
}
