package com.example.mymusicplayer.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mymusicplayer.data.local.entity.Track;

import java.util.List;

@Dao
public interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Track track);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Track> tracks);

    @Update
    void update(Track track);

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    LiveData<Track> getTrackById(String trackId);

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    Track getTrackByIdSync(String trackId);

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY title ASC")
    LiveData<List<Track>> getDownloadedTracks();

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title ASC")
    LiveData<List<Track>> getFavoriteTracks();

    @Query("SELECT * FROM tracks WHERE sourceType = :sourceType ORDER BY title ASC")
    LiveData<List<Track>> getTracksBySource(String sourceType);

    @Query("SELECT * FROM tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT 10")
    LiveData<List<Track>> getRecentTracks();

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    void updateFavoriteState(String trackId, boolean isFavorite);

    @Query("UPDATE tracks SET isDownloaded = :isDownloaded WHERE id = :trackId")
    void updateDownloadedState(String trackId, boolean isDownloaded);

    @Query("UPDATE tracks SET isDownloaded = :isDownloaded, dataUri = :dataUri WHERE id = :trackId")
    void updateDownloadedTrack(String trackId, boolean isDownloaded, String dataUri);

    @Query("DELETE FROM tracks WHERE id = :trackId")
    void deleteById(String trackId);

    @Query("UPDATE tracks SET lastPlayed = :lastPlayed WHERE id = :trackId")
    void updateLastPlayed(String trackId, long lastPlayed);
}
