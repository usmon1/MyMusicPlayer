package com.example.mymusicplayer.ui.playlist;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.data.repository.MusicRepository;

import java.util.List;

public class PlaylistDetailsViewModel extends AndroidViewModel {

    private final MusicRepository repository;

    public PlaylistDetailsViewModel(@NonNull Application application) {
        super(application);
        repository = new MusicRepository(application);
    }

    public LiveData<List<Track>> getFavoriteTracks() {
        return repository.getFavoriteTracks();
    }

    public LiveData<List<Track>> getTracksFromPlaylist(int playlistId) {
        return repository.getTracksFromPlaylist(playlistId);
    }

    public void removeFromFavorites(Track track) {
        if (track == null) {
            return;
        }

        track.setFavorite(false);
        repository.saveTrack(track);
        repository.addToFavorites(track.getId(), false);
    }

    public void removeFromPlaylist(int playlistId, Track track) {
        if (track == null) {
            return;
        }

        repository.removeTrackFromPlaylist(playlistId, track.getId());
    }
}
