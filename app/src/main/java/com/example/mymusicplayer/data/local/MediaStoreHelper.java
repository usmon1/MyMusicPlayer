package com.example.mymusicplayer.data.local;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.example.mymusicplayer.data.local.entity.Track;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads local audio files from MediaStore.
 */
public class MediaStoreHelper {

    private static final String SOURCE_TYPE_LOCAL = "LOCAL";

    private final ContentResolver contentResolver;

    public MediaStoreHelper(Context context) {
        this.contentResolver = context.getApplicationContext().getContentResolver();
    }

    public List<Track> getAudioTracks() {
        List<Track> tracks = new ArrayList<>();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
        )) {
            if (cursor == null) {
                return tracks;
            }

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

            while (cursor.moveToNext()) {
                long mediaId = cursor.getLong(idColumn);
                Uri contentUri = Uri.withAppendedPath(collection, String.valueOf(mediaId));

                Track track = new Track(
                        String.valueOf(mediaId),
                        cursor.getString(titleColumn),
                        cursor.getString(artistColumn),
                        contentUri.toString(),
                        null,
                        cursor.getLong(durationColumn),
                        SOURCE_TYPE_LOCAL,
                        false,
                        false,
                        0L
                );

                tracks.add(track);
            }
        }

        return tracks;
    }
}
