package com.example.mymusicplayer.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.mymusicplayer.data.local.entity.PlaylistTrackCrossRef;
import com.example.mymusicplayer.data.local.entity.Track;

import java.util.List;

@Dao
public interface CrossRefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PlaylistTrackCrossRef crossRef);

    @Delete
    void delete(PlaylistTrackCrossRef crossRef);

    @Query("SELECT t.* FROM tracks t " +
            "INNER JOIN playlist_track_cross_ref pcr ON t.id = pcr.trackId " +
            "WHERE pcr.playlistId = :playlistId " +
            "ORDER BY t.title ASC")
    LiveData<List<Track>> getTracksForPlaylist(int playlistId);

    @Query("SELECT DISTINCT trackId FROM playlist_track_cross_ref")
    LiveData<List<String>> getAllTrackIds();

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    void deleteByIds(int playlistId, String trackId);
}
