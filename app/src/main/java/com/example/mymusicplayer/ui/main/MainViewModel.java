package com.example.mymusicplayer.ui.main;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mymusicplayer.data.local.entity.Playlist;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.data.repository.MusicRepository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Stores main screen state and delegates data work to the repository.
 */
public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";
    private static final int WAVE_BATCH_SIZE = 50;
    private static final int WAVE_BATCH_REQUEST_COUNT = 5;
    private static final int MAX_WAVE_BATCHES = 5;
    private static final int WAVE_PREFETCH_THRESHOLD = 5;
    private static final int RECENT_WAVE_TRACK_WINDOW = 100;
    private static final int MAX_WAVE_FETCH_ATTEMPTS = 3;

    private final MusicRepository repository;
    private final MediatorLiveData<List<Track>> randomTracks = new MediatorLiveData<>();
    private final MediatorLiveData<List<Track>> recentTracks = new MediatorLiveData<>();
    private final MediatorLiveData<List<Track>> localTracks = new MediatorLiveData<>();
    private final MediatorLiveData<List<Track>> downloadedTracks = new MediatorLiveData<>();
    private final MediatorLiveData<List<Track>> favoriteTracks = new MediatorLiveData<>();
    private final MediatorLiveData<List<Playlist>> playlists = new MediatorLiveData<>();
    private final MediatorLiveData<List<String>> playlistTrackIds = new MediatorLiveData<>();
    private final MediatorLiveData<List<Track>> selectedPlaylistTracks = new MediatorLiveData<>();
    private final MutableLiveData<Track> currentPlayingTrack = new MutableLiveData<>();
    private final MutableLiveData<Playlist> selectedPlaylist = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isWaveLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final List<Track> waveQueue = new ArrayList<>();
    private final ArrayDeque<Integer> waveBatchSizes = new ArrayDeque<>();
    private final ArrayDeque<String> recentWaveTrackIds = new ArrayDeque<>();
    private final Set<String> recentWaveTrackIdSet = new HashSet<>();
    private final Set<String> protectedWaveTrackIds = new HashSet<>();
    private final Random random = new Random();

    private LiveData<List<Track>> randomTracksSource;
    private LiveData<List<Track>> recentTracksSource;
    private LiveData<List<Track>> localTracksSource;
    private LiveData<List<Track>> selectedPlaylistTracksSource;
    private int currentWaveIndex = -1;
    private boolean isLoadingWaveBatch;
    private boolean isNetworkAvailable = true;

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new MusicRepository(application);
        downloadedTracks.addSource(repository.getDownloadedTracks(), tracks -> {
            downloadedTracks.setValue(tracks);
            refreshProtectedWaveTrackIds();
        });
        favoriteTracks.addSource(repository.getFavoriteTracks(), tracks -> {
            favoriteTracks.setValue(tracks);
            refreshProtectedWaveTrackIds();
        });
        playlistTrackIds.addSource(repository.getAllPlaylistTrackIds(), ids -> {
            playlistTrackIds.setValue(ids);
            refreshProtectedWaveTrackIds();
        });
        playlists.addSource(repository.getAllPlaylists(), items -> {
            playlists.setValue(items);

            Playlist currentPlaylist = selectedPlaylist.getValue();
            if ((currentPlaylist == null || currentPlaylist.getPlaylistId() == 0)
                    && items != null
                    && !items.isEmpty()) {
                selectPlaylist(items.get(0));
            }
        });
    }

    public LiveData<List<Track>> getRandomTracks() {
        return randomTracks;
    }

    public LiveData<List<Track>> getRecentTracks() {
        return recentTracks;
    }

    public LiveData<List<Track>> getLocalTracks() {
        return localTracks;
    }

    public LiveData<List<Track>> getDownloadedTracks() {
        return downloadedTracks;
    }

    public LiveData<List<Track>> getFavoriteTracks() {
        return favoriteTracks;
    }

    public LiveData<List<Playlist>> getPlaylists() {
        return playlists;
    }

    public LiveData<Playlist> getSelectedPlaylist() {
        return selectedPlaylist;
    }

    public LiveData<List<Track>> getSelectedPlaylistTracks() {
        return selectedPlaylistTracks;
    }

    public LiveData<Track> getCurrentPlayingTrack() {
        return currentPlayingTrack;
    }

    public LiveData<Boolean> getIsWaveLoading() {
        return isWaveLoading;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public void loadRandomTracks() {
        Log.d(TAG, "loadRandomTracks: queueSize=" + waveQueue.size()
                + ", isLoadingWaveBatch=" + isLoadingWaveBatch);
        if (!isNetworkAvailable) {
            isLoadingWaveBatch = false;
            isWaveLoading.setValue(false);
            publishWaveQueue();
            return;
        }

        if (!waveQueue.isEmpty() || isLoadingWaveBatch) {
            publishWaveQueue();
            return;
        }

        requestNextWaveBatch(0);
    }

    public void loadRecentTracks() {
        if (recentTracksSource != null) {
            recentTracks.removeSource(recentTracksSource);
        }

        recentTracksSource = repository.getRecentTracks();
        recentTracks.addSource(recentTracksSource, recentTracks::setValue);
    }

    public void loadLocalTracks() {
        if (localTracksSource != null) {
            localTracks.removeSource(localTracksSource);
        }

        localTracksSource = repository.getLocalTracksFromMediaStore();
        localTracks.addSource(localTracksSource, localTracks::setValue);
    }

    public void onTrackClicked(Track track) {
        if (track == null) {
            return;
        }

        repository.saveTrack(track);
        currentPlayingTrack.setValue(track);
        isPlaying.setValue(true);
        repository.updateLastPlayed(track);
    }

    public void toggleFavorite(Track track) {
        if (track == null) {
            return;
        }

        boolean newFavoriteState = !track.isFavorite();
        track.setFavorite(newFavoriteState);
        repository.saveTrack(track);
        repository.addToFavorites(track.getId(), newFavoriteState);
        currentPlayingTrack.setValue(track);
    }

    public void createPlaylist(String name) {
        repository.createPlaylist(name);
    }

    public void selectPlaylist(Playlist playlist) {
        selectedPlaylist.setValue(playlist);

        if (selectedPlaylistTracksSource != null) {
            selectedPlaylistTracks.removeSource(selectedPlaylistTracksSource);
            selectedPlaylistTracksSource = null;
        }

        if (playlist == null) {
            selectedPlaylistTracks.setValue(null);
            return;
        }

        selectedPlaylistTracksSource = repository.getTracksFromPlaylist(playlist.getPlaylistId());
        selectedPlaylistTracks.addSource(selectedPlaylistTracksSource, selectedPlaylistTracks::setValue);
    }

    public void addTrackToPlaylist(int playlistId, Track track) {
        if (track == null) {
            return;
        }

        repository.saveTrack(track);
        repository.addTrackToPlaylist(playlistId, track.getId());
    }

    public void downloadTrack(Track track, MusicRepository.DownloadCallback callback) {
        if (track == null) {
            if (callback != null) {
                callback.onError("Track is empty.");
            }
            return;
        }

        repository.saveTrack(track);
        repository.downloadTrack(track, new MusicRepository.DownloadCallback() {
            @Override
            public void onSuccess(String localUri) {
                track.setDownloaded(true);
                track.setDataUri(localUri);
                repository.saveTrack(track);
                currentPlayingTrack.postValue(track);

                if (callback != null) {
                    callback.onSuccess(localUri);
                }
            }

            @Override
            public void onError(String message) {
                if (callback != null) {
                    callback.onError(message);
                }
            }
        });
    }

    public void updatePlaybackState(boolean playing) {
        isPlaying.setValue(playing);
    }

    public void updateCurrentTrack(Track track) {
        currentPlayingTrack.setValue(track);
    }

    public void setNetworkAvailable(boolean networkAvailable) {
        isNetworkAvailable = networkAvailable;
        if (!networkAvailable) {
            isLoadingWaveBatch = false;
            isWaveLoading.setValue(false);
        }
    }

    public Track getCurrentWaveTrack() {
        if (currentWaveIndex < 0 || currentWaveIndex >= waveQueue.size()) {
            return null;
        }

        return waveQueue.get(currentWaveIndex);
    }

    public Track getWaveTrackForPlayback() {
        Log.d(TAG, "getWaveTrackForPlayback: queueSize=" + waveQueue.size()
                + ", currentWaveIndex=" + currentWaveIndex);
        if (waveQueue.isEmpty()) {
            loadRandomTracks();
            return null;
        }

        if (currentWaveIndex < 0 || currentWaveIndex >= waveQueue.size()) {
            currentWaveIndex = 0;
        }

        maybePrefetchNextWaveBatch();
        return waveQueue.get(currentWaveIndex);
    }

    public boolean hasWaveTracks() {
        return !waveQueue.isEmpty();
    }

    public Track moveToNextWaveTrack() {
        if (waveQueue.isEmpty()) {
            loadRandomTracks();
            return null;
        }

        if (currentWaveIndex < 0) {
            currentWaveIndex = 0;
            maybePrefetchNextWaveBatch();
            return waveQueue.get(currentWaveIndex);
        }

        if (currentWaveIndex + 1 >= waveQueue.size()) {
            maybePrefetchNextWaveBatch();
            return null;
        }

        currentWaveIndex++;
        maybePrefetchNextWaveBatch();
        return waveQueue.get(currentWaveIndex);
    }

    public Track moveToPreviousWaveTrack() {
        if (waveQueue.isEmpty()) {
            return null;
        }

        if (currentWaveIndex < 0 || currentWaveIndex >= waveQueue.size()) {
            currentWaveIndex = 0;
            return waveQueue.get(currentWaveIndex);
        }

        if (currentWaveIndex == 0) {
            return waveQueue.get(currentWaveIndex);
        }

        currentWaveIndex--;
        return waveQueue.get(currentWaveIndex);
    }

    public boolean isCurrentWaveTrack(Track track) {
        Track currentWaveTrack = getCurrentWaveTrack();
        return currentWaveTrack != null
                && track != null
                && currentWaveTrack.getId().equals(track.getId());
    }

    public void onWaveTrackStarted(Track track) {
        if (track == null) {
            return;
        }

        for (int index = 0; index < waveQueue.size(); index++) {
            if (track.getId().equals(waveQueue.get(index).getId())) {
                currentWaveIndex = index;
                maybePrefetchNextWaveBatch();
                return;
            }
        }
    }

    private void requestNextWaveBatch(int attempt) {
        Log.d(TAG, "requestNextWaveBatch: attempt=" + attempt
                + ", isLoadingWaveBatch=" + isLoadingWaveBatch);
        if (isLoadingWaveBatch || !isNetworkAvailable) {
            return;
        }

        isLoadingWaveBatch = true;
        isWaveLoading.setValue(true);

        randomTracksSource = repository.getRandomWaveTracks(
                WAVE_BATCH_SIZE,
                WAVE_BATCH_REQUEST_COUNT
        );
        randomTracks.addSource(randomTracksSource, tracks -> {
            randomTracks.removeSource(randomTracksSource);
            randomTracksSource = null;
            isLoadingWaveBatch = false;
            isWaveLoading.setValue(false);

            List<Track> filteredTracks = filterWaveBatch(tracks);
            if (filteredTracks.isEmpty() && waveQueue.isEmpty()) {
                filteredTracks = buildInitialWaveFallbackBatch(tracks);
                Log.w(TAG, "Wave fallback batch applied: size=" + filteredTracks.size());
            }

            Log.d(TAG, "Wave batch received: raw=" + (tracks == null ? -1 : tracks.size())
                    + ", filtered=" + filteredTracks.size()
                    + ", attempt=" + attempt);
            if (filteredTracks.isEmpty() && attempt < MAX_WAVE_FETCH_ATTEMPTS - 1) {
                requestNextWaveBatch(attempt + 1);
                return;
            }

            if (!filteredTracks.isEmpty()) {
                java.util.Collections.shuffle(filteredTracks, random);
                waveQueue.addAll(filteredTracks);
                waveBatchSizes.addLast(filteredTracks.size());
                rememberWaveTrackIds(filteredTracks);
                trimWaveBatchesIfNeeded();

                if (currentWaveIndex < 0 && !waveQueue.isEmpty()) {
                    currentWaveIndex = 0;
                }
            }

            publishWaveQueue();
        });
    }

    private List<Track> filterWaveBatch(List<Track> tracks) {
        List<Track> result = new ArrayList<>();
        Set<String> batchIds = new HashSet<>();

        if (tracks == null) {
            return result;
        }

        for (Track track : tracks) {
            if (track == null || track.getId() == null || track.getId().trim().isEmpty()) {
                continue;
            }

            if (!batchIds.add(track.getId())) {
                continue;
            }

            if (recentWaveTrackIdSet.contains(track.getId())) {
                continue;
            }

            result.add(track);
        }

        return result;
    }

    private List<Track> buildInitialWaveFallbackBatch(List<Track> tracks) {
        List<Track> result = new ArrayList<>();
        Set<String> batchIds = new HashSet<>();

        if (tracks == null) {
            return result;
        }

        for (Track track : tracks) {
            if (track == null || track.getId() == null || track.getId().trim().isEmpty()) {
                continue;
            }

            if (track.getDataUri() == null || track.getDataUri().trim().isEmpty()) {
                continue;
            }

            if (!batchIds.add(track.getId())) {
                continue;
            }

            result.add(track);
        }

        return result;
    }

    private void rememberWaveTrackIds(List<Track> tracks) {
        for (Track track : tracks) {
            if (track == null || track.getId() == null || track.getId().trim().isEmpty()) {
                continue;
            }

            recentWaveTrackIds.addLast(track.getId());
            recentWaveTrackIdSet.add(track.getId());

            while (recentWaveTrackIds.size() > RECENT_WAVE_TRACK_WINDOW) {
                String removedId = recentWaveTrackIds.removeFirst();
                if (!recentWaveTrackIds.contains(removedId)) {
                    recentWaveTrackIdSet.remove(removedId);
                }
            }
        }
    }

    private void trimWaveBatchesIfNeeded() {
        while (waveBatchSizes.size() > MAX_WAVE_BATCHES) {
            removeOldestWaveBatch();
        }
    }

    private void removeOldestWaveBatch() {
        Integer oldestBatchSize = waveBatchSizes.pollFirst();
        if (oldestBatchSize == null || oldestBatchSize <= 0 || waveQueue.isEmpty()) {
            return;
        }

        int removableCount = Math.min(oldestBatchSize, waveQueue.size());
        for (int index = removableCount - 1; index >= 0; index--) {
            Track track = waveQueue.get(index);
            if (track != null && protectedWaveTrackIds.contains(track.getId())) {
                continue;
            }

            waveQueue.remove(index);
            if (currentWaveIndex > index) {
                currentWaveIndex--;
            } else if (currentWaveIndex == index) {
                currentWaveIndex = Math.min(index, waveQueue.size() - 1);
            }
        }

        if (waveQueue.isEmpty()) {
            currentWaveIndex = -1;
        }
    }

    private void maybePrefetchNextWaveBatch() {
        if (isLoadingWaveBatch || waveQueue.isEmpty() || currentWaveIndex < 0) {
            return;
        }

        int tracksLeft = waveQueue.size() - currentWaveIndex - 1;
        if (tracksLeft <= WAVE_PREFETCH_THRESHOLD) {
            requestNextWaveBatch(0);
        }
    }

    private void publishWaveQueue() {
        Log.d(TAG, "publishWaveQueue: queueSize=" + waveQueue.size()
                + ", currentWaveIndex=" + currentWaveIndex);
        randomTracks.setValue(new ArrayList<>(waveQueue));
    }

    private void refreshProtectedWaveTrackIds() {
        protectedWaveTrackIds.clear();
        addProtectedTrackIds(favoriteTracks.getValue());
        addProtectedTrackIds(downloadedTracks.getValue());

        List<String> playlistIds = playlistTrackIds.getValue();
        if (playlistIds != null) {
            protectedWaveTrackIds.addAll(playlistIds);
        }
    }

    private void addProtectedTrackIds(List<Track> tracks) {
        if (tracks == null) {
            return;
        }

        for (Track track : tracks) {
            if (track != null && track.getId() != null && !track.getId().trim().isEmpty()) {
                protectedWaveTrackIds.add(track.getId());
            }
        }
    }
}
