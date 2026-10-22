package com.legalguide.app.core.util;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.legalguide.app.R;
import com.legalguide.app.data.model.DownloadLink;

/** Downloads a server-signed URL (no auth header needed) into the public Downloads folder. */
public final class Downloads {
    private Downloads() {}

    public static void enqueue(Context ctx, DownloadLink link, String fallbackName) {
        if (link == null || Texts.blank(link.url)) {
            Toast.makeText(ctx, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        String name = Texts.blank(link.fileName) ? fallbackName : link.fileName;
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(link.url))
                    .setTitle(name)
                    .setDescription(ctx.getString(R.string.app_name))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Sand/" + name);
            if (link.mimeType != null) req.setMimeType(link.mimeType);
            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(req);
                Toast.makeText(ctx, R.string.download_started, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (RuntimeException ignored) {
            // Fall back to the browser
        }
        Intents.openUrl(ctx, link.url);
    }
}
