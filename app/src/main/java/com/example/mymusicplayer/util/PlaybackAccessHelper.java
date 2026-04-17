package com.example.mymusicplayer.util;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.mymusicplayer.data.local.entity.Track;

import java.io.File;

public final class PlaybackAccessHelper {

    private static final String SOURCE_TYPE_LOCAL = "LOCAL";

    private PlaybackAccessHelper() {
    }

    public static boolean canPlayTrack(Context context, @Nullable Track track) {
        return isOfflinePlayable(context, track)
                || NetworkMonitor.getInstance(context).isCurrentlyConnected();
    }

    public static boolean isOfflinePlayable(@Nullable Track track) {
        if (track == null) {
            return false;
        }

        if (SOURCE_TYPE_LOCAL.equalsIgnoreCase(track.getSourceType())) {
            return true;
        }

        String dataUri = track.getDataUri();
        return isLocalUri(dataUri);
    }

    public static boolean isOfflinePlayable(Context context, @Nullable Track track) {
        if (track == null) {
            return false;
        }

        if (track.isDownloaded() && resolveDownloadedTrackUri(context, track) != null) {
            return true;
        }

        if (SOURCE_TYPE_LOCAL.equalsIgnoreCase(track.getSourceType())) {
            return true;
        }

        String dataUri = track.getDataUri();
        if (dataUri == null || dataUri.trim().isEmpty()) {
            return false;
        }

        String scheme = Uri.parse(dataUri).getScheme();
        return "file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme);
    }

    @Nullable
    public static String resolveDownloadedTrackUri(Context context, @Nullable Track track) {
        if (track == null || !track.isDownloaded()) {
            return null;
        }

        String dataUri = track.getDataUri();
        if (isLocalUri(dataUri)) {
            return dataUri;
        }

        if (track.getId() == null || track.getId().trim().isEmpty()) {
            return null;
        }

        File downloadsDirectory = new File(context.getApplicationContext().getFilesDir(), "downloads");
        File[] files = downloadsDirectory.listFiles((dir, name) ->
                name != null && name.startsWith("track_" + track.getId())
        );
        if (files == null || files.length == 0) {
            return null;
        }

        return Uri.fromFile(files[0]).toString();
    }

    public static void prepareTrackForPlayback(Context context, @Nullable Track track) {
        if (track == null) {
            return;
        }

        if (track.isDownloaded()) {
            String localUri = resolveDownloadedTrackUri(context, track);
            if (localUri != null) {
                track.setDataUri(localUri);
            }
        }
    }

    private static boolean isLocalUri(@Nullable String dataUri) {
        if (dataUri == null || dataUri.trim().isEmpty()) {
            return false;
        }

        String scheme = Uri.parse(dataUri).getScheme();
        return "file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme);
    }
}
