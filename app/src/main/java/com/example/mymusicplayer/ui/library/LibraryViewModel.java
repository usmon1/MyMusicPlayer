package com.example.mymusicplayer.ui.library;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.mymusicplayer.data.local.entity.Playlist;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.data.repository.MusicRepository;

import java.util.List;

public class LibraryViewModel extends AndroidViewModel {

    private final MusicRepository repository;
    private final MediatorLiveData<List<Track>> localTracks = new MediatorLiveData<>();

    private LiveData<List<Track>> localTracksSource;

    public LibraryViewModel(@NonNull Application application) {
        super(application);
        repository = new MusicRepository(application);
    }

    public LiveData<List<Playlist>> getPlaylists() {
        return repository.getAllPlaylists();
    }

    public LiveData<List<Track>> getFavoriteTracks() {
        return repository.getFavoriteTracks();
    }

    public LiveData<List<Track>> getDownloadedTracks() {
        return repository.getDownloadedTracks();
    }

    public LiveData<List<Track>> getLocalTracks() {
        return localTracks;
    }

    public void loadLocalTracks() {
        if (localTracksSource != null) {
            localTracks.removeSource(localTracksSource);
        }

        localTracksSource = repository.getLocalTracksFromMediaStore();
        localTracks.addSource(localTracksSource, localTracks::setValue);
    }

    public void createPlaylist(String name) {
        repository.createPlaylist(name);
    }

    public void deletePlaylist(int playlistId) {
        repository.deletePlaylist(playlistId);
    }

    public void removeDownload(Track track) {
        if (track == null) {
            return;
        }

        track.setDownloaded(false);
        repository.removeFromDownloads(track);
    }
}
