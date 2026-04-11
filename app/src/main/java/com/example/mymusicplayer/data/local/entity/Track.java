package com.example.mymusicplayer.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Shared track model for Room and Jamendo responses.
 */
@Entity(tableName = "tracks")
public class Track implements Serializable {

    @PrimaryKey
    @SerializedName("id")
    @NonNull
    private String id = "";

    @SerializedName(value = "name", alternate = {"title"})
    private String title;

    @SerializedName("artist_name")
    private String artist;

    @SerializedName("audio")
    private String dataUri;

    @SerializedName("image")
    private String imageUrl;

    @SerializedName("duration")
    private long duration;

    private String sourceType;
    private boolean isFavorite;
    private boolean isDownloaded;
    private long lastPlayed;

    public Track() {
    }

    @Ignore
    public Track(@NonNull String id,
                 String title,
                 String artist,
                 String dataUri,
                 String imageUrl,
                 long duration,
                 String sourceType,
                 boolean isFavorite,
                 boolean isDownloaded,
                 long lastPlayed) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.dataUri = dataUri;
        this.imageUrl = imageUrl;
        this.duration = duration;
        this.sourceType = sourceType;
        this.isFavorite = isFavorite;
        this.isDownloaded = isDownloaded;
        this.lastPlayed = lastPlayed;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getDataUri() {
        return dataUri;
    }

    public void setDataUri(String dataUri) {
        this.dataUri = dataUri;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }

    public void setDownloaded(boolean downloaded) {
        isDownloaded = downloaded;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }
}
