package com.legalguide.app.core.net;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/** Streams a content:// Uri into a multipart request without copying it to disk. */
public final class UriRequestBody extends RequestBody {

    /** Metadata of a picked file. */
    public static final class FileInfo {
        public final Uri uri;
        public final String name;
        public final String mimeType;
        public final long size;

        FileInfo(Uri uri, String name, String mimeType, long size) {
            this.uri = uri;
            this.name = name;
            this.mimeType = mimeType;
            this.size = size;
        }
    }

    private final ContentResolver resolver;
    private final FileInfo info;

    private UriRequestBody(ContentResolver resolver, FileInfo info) {
        this.resolver = resolver;
        this.info = info;
    }

    public static FileInfo inspect(Context ctx, Uri uri) {
        ContentResolver cr = ctx.getContentResolver();
        String name = "file";
        long size = -1;
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int s = c.getColumnIndex(OpenableColumns.SIZE);
                if (n >= 0 && !c.isNull(n)) name = c.getString(n);
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s);
            }
        } catch (RuntimeException ignored) {
        }
        String mime = cr.getType(uri);
        if (mime == null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(name);
            mime = ext != null ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase()) : null;
        }
        if (mime == null) mime = "application/octet-stream";
        return new FileInfo(uri, name, mime, size);
    }

    public static MultipartBody.Part part(Context ctx, String field, FileInfo info) {
        return MultipartBody.Part.createFormData(field, info.name, new UriRequestBody(ctx.getContentResolver(), info));
    }

    public static RequestBody text(@Nullable String value) {
        return RequestBody.create(value == null ? "" : value, MediaType.parse("text/plain; charset=utf-8"));
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return MediaType.parse(info.mimeType);
    }

    @Override
    public long contentLength() {
        return info.size;
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        InputStream in = resolver.openInputStream(info.uri);
        if (in == null) throw new IOException("Cannot open " + info.uri);
        try (Source src = Okio.source(in)) {
            sink.writeAll(src);
        }
    }
}
