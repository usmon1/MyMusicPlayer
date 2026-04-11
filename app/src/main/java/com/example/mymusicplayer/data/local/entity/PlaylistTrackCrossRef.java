package com.example.mymusicplayer.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * Связующая таблица между плейлистами и треками.
 */
@Entity(
        tableName = "playlist_track_cross_ref",
        primaryKeys = {"playlistId", "trackId"},
        foreignKeys = {
                @ForeignKey(
                        entity = Playlist.class,
                        parentColumns = "playlistId",
                        childColumns = "playlistId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Track.class,
                        parentColumns = "id",
                        childColumns = "trackId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("playlistId"),
                @Index("trackId")
        }
)
public class PlaylistTrackCrossRef {

    private int playlistId;
    @NonNull
    private String trackId;

    public PlaylistTrackCrossRef(int playlistId, @NonNull String trackId) {
        this.playlistId = playlistId;
        this.trackId = trackId;
    }

    public int getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(int playlistId) {
        this.playlistId = playlistId;
    }

    @NonNull
    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(@NonNull String trackId) {
        this.trackId = trackId;
    }
}
