package com.example.mymusicplayer.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Сущность пользовательского плейлиста.
 */
@Entity(tableName = "playlists")
public class Playlist {

    @PrimaryKey(autoGenerate = true)
    private int playlistId;
    private String name;
    private long createdAt;

    public Playlist(String name, long createdAt) {
        this.name = name;
        this.createdAt = createdAt;
    }

    public int getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(int playlistId) {
        this.playlistId = playlistId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
