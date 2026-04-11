package com.example.mymusicplayer.data.local.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.mymusicplayer.data.local.dao.CrossRefDao;
import com.example.mymusicplayer.data.local.dao.PlaylistDao;
import com.example.mymusicplayer.data.local.dao.TrackDao;
import com.example.mymusicplayer.data.local.entity.Playlist;
import com.example.mymusicplayer.data.local.entity.PlaylistTrackCrossRef;
import com.example.mymusicplayer.data.local.entity.Track;

/**
 * Единая точка доступа к локальной базе приложения.
 */
@Database(
        entities = {Track.class, Playlist.class, PlaylistTrackCrossRef.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "music_player_db";
    private static volatile AppDatabase instance;

    public abstract TrackDao trackDao();

    public abstract PlaylistDao playlistDao();

    public abstract CrossRefDao crossRefDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    ).build();
                }
            }
        }

        return instance;
    }
}
