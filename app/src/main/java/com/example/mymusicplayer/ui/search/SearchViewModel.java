package com.example.mymusicplayer.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.data.repository.MusicRepository;

import java.util.ArrayList;
import java.util.List;

public class SearchViewModel extends AndroidViewModel {

    private final MusicRepository repository;
    private final MediatorLiveData<List<Track>> searchResults = new MediatorLiveData<>();

    private LiveData<List<Track>> searchSource;

    public SearchViewModel(@NonNull Application application) {
        super(application);
        repository = new MusicRepository(application);
        searchResults.setValue(new ArrayList<>());
    }

    public LiveData<List<Track>> getSearchResults() {
        return searchResults;
    }

    public void searchTracks(String query) {
        if (searchSource != null) {
            searchResults.removeSource(searchSource);
            searchSource = null;
        }

        if (query == null || query.trim().isEmpty()) {
            searchResults.setValue(new ArrayList<>());
            return;
        }

        searchSource = repository.searchTracksOnline(query);
        searchResults.addSource(searchSource, tracks ->
                searchResults.setValue(tracks == null ? new ArrayList<>() : tracks)
        );
    }
}
