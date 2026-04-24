package com.example.mymusicplayer.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.MediaStoreHelper;
import com.example.mymusicplayer.data.local.dao.CrossRefDao;
import com.example.mymusicplayer.data.local.dao.PlaylistDao;
import com.example.mymusicplayer.data.local.dao.TrackDao;
import com.example.mymusicplayer.data.local.db.AppDatabase;
import com.example.mymusicplayer.data.local.entity.Playlist;
import com.example.mymusicplayer.data.local.entity.PlaylistTrackCrossRef;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.data.remote.ApiConfig;
import com.example.mymusicplayer.data.remote.JamendoApi;
import com.example.mymusicplayer.data.remote.JamendoResponse;
import com.example.mymusicplayer.data.remote.RetrofitClient;
import com.example.mymusicplayer.util.DownloadSessionManager;
import com.example.mymusicplayer.util.NetworkMonitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Repository for Jamendo, MediaStore and Room data.
 */
public class MusicRepository {

    private static final String TAG = "MusicRepository";
    private static final String SOURCE_TYPE_JAMENDO = "JAMENDO";
    private static final int JAMENDO_LIMIT = 10;
    private static final int JAMENDO_SEARCH_LIMIT = 50;
    private static final String JAMENDO_RESPONSE_FORMAT = "json";
    private static final String JAMENDO_WAVE_ORDER = "popularity_total";
    private static final String JAMENDO_AUDIO_FORMAT = "mp32";
    private static final int JAMENDO_RANDOM_OFFSET_PAGES = 20;
    private final Context appContext;
    private final TrackDao trackDao;
    private final PlaylistDao playlistDao;
    private final CrossRefDao crossRefDao;
    private final JamendoApi jamendoApi;
    private final MediaStoreHelper mediaStoreHelper;
    private final OkHttpClient okHttpClient;
    private final ExecutorService executorService;
    private final File downloadsDirectory;
    private final Random random;

    public MusicRepository(Context context) {
        this.appContext = context.getApplicationContext();
        AppDatabase appDatabase = AppDatabase.getInstance(appContext);

        this.trackDao = appDatabase.trackDao();
        this.playlistDao = appDatabase.playlistDao();
        this.crossRefDao = appDatabase.crossRefDao();
        this.jamendoApi = RetrofitClient.getInstance().create(JamendoApi.class);
        this.mediaStoreHelper = new MediaStoreHelper(appContext);
        this.okHttpClient = new OkHttpClient();
        this.executorService = Executors.newSingleThreadExecutor();
        this.downloadsDirectory = new File(appContext.getFilesDir(), "downloads");
        this.random = new Random();
    }

    public LiveData<List<Track>> getRandomTracksFromJamendo() {
        return getWaveTracks(JAMENDO_LIMIT, 0);
    }

    public LiveData<List<Track>> getWaveTracks(int limit, int offset) {
        MutableLiveData<List<Track>> liveData = new MutableLiveData<>();

        if (!NetworkMonitor.getInstance(appContext).isCurrentlyConnected()) {
            liveData.setValue(new ArrayList<>());
            return liveData;
        }

        jamendoApi.getWaveTracks(
                        ApiConfig.CLIENT_ID,
                        JAMENDO_RESPONSE_FORMAT,
                        JAMENDO_WAVE_ORDER,
                        limit,
                        offset,
                        JAMENDO_AUDIO_FORMAT
                )
                .enqueue(new retrofit2.Callback<JamendoResponse>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<JamendoResponse> call,
                                           @NonNull retrofit2.Response<JamendoResponse> response) {
                        if (!response.isSuccessful() || response.body() == null || response.body().getResults() == null) {
                            Log.e(TAG, "Jamendo response failed: code=" + response.code());
                            liveData.postValue(new ArrayList<>());
                            return;
                        }

                        List<Track> tracks = normalizeJamendoTracks(response.body().getResults());
                        Log.d(TAG, "Jamendo tracks loaded: " + tracks.size());
                        liveData.postValue(tracks);
                        saveTracks(tracks);
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<JamendoResponse> call,
                                          @NonNull Throwable throwable) {
                        Log.e(TAG, "Jamendo request error", throwable);
                        liveData.postValue(new ArrayList<>());
                    }
                });

        return liveData;
    }

    public LiveData<List<Track>> getRandomWaveTracks(int totalLimit, int requestCount) {
        MutableLiveData<List<Track>> liveData = new MutableLiveData<>();

        if (!NetworkMonitor.getInstance(appContext).isCurrentlyConnected()) {
            liveData.setValue(new ArrayList<>());
            return liveData;
        }

        int safeRequestCount = Math.max(1, requestCount);
        int requestLimit = Math.max(1, totalLimit / safeRequestCount);
        AtomicInteger completedRequests = new AtomicInteger(0);
        List<Track> mergedTracks = new ArrayList<>();
        Set<String> mergedIds = new HashSet<>();

        for (int index = 0; index < safeRequestCount; index++) {
            int randomOffset = getRandomWaveOffset();
            jamendoApi.getWaveTracks(
                            ApiConfig.CLIENT_ID,
                            JAMENDO_RESPONSE_FORMAT,
                            JAMENDO_WAVE_ORDER,
                            requestLimit,
                            randomOffset,
                            JAMENDO_AUDIO_FORMAT
                    )
                    .enqueue(new retrofit2.Callback<JamendoResponse>() {
                        @Override
                        public void onResponse(@NonNull retrofit2.Call<JamendoResponse> call,
                                               @NonNull retrofit2.Response<JamendoResponse> response) {
                            if (response.isSuccessful()
                                    && response.body() != null
                                    && response.body().getResults() != null) {
                                List<Track> normalizedTracks =
                                        normalizeJamendoTracks(response.body().getResults());
                                synchronized (mergedTracks) {
                                    for (Track track : normalizedTracks) {
                                        if (track == null || track.getId() == null) {
                                            continue;
                                        }

                                        if (mergedIds.add(track.getId())) {
                                            mergedTracks.add(track);
                                        }
                                    }
                                }
                            } else {
                                Log.e(TAG, "Random Jamendo response failed: code=" + response.code());
                            }

                            postRandomWaveResultIfReady(
                                    liveData,
                                    completedRequests.incrementAndGet(),
                                    safeRequestCount,
                                    mergedTracks
                            );
                        }

                        @Override
                        public void onFailure(@NonNull retrofit2.Call<JamendoResponse> call,
                                              @NonNull Throwable throwable) {
                            Log.e(TAG, "Random Jamendo request error", throwable);
                            postRandomWaveResultIfReady(
                                    liveData,
                                    completedRequests.incrementAndGet(),
                                    safeRequestCount,
                                    mergedTracks
                            );
                        }
                    });
        }

        return liveData;
    }

    public LiveData<List<Track>> getLocalTracksFromMediaStore() {
        MutableLiveData<List<Track>> liveData = new MutableLiveData<>(new ArrayList<>());

        executorService.execute(() -> {
            List<Track> tracks = mediaStoreHelper.getAudioTracks();
            liveData.postValue(tracks);
            saveTracksSync(tracks);
        });

        return liveData;
    }

    public LiveData<List<Track>> getDownloadedTracks() {
        return trackDao.getDownloadedTracks();
    }

    public LiveData<List<Track>> getFavoriteTracks() {
        return trackDao.getFavoriteTracks();
    }

    public LiveData<List<Track>> getRecentTracks() {
        return trackDao.getRecentTracks();
    }

    public LiveData<List<Track>> searchTracks(String query) {
        if (query == null) {
            query = "";
        }

        return trackDao.searchTracks(query.trim());
    }

    public LiveData<List<Track>> searchTracksOnline(String query) {
        MutableLiveData<List<Track>> liveData = new MutableLiveData<>(new ArrayList<>());

        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty()) {
            return liveData;
        }

        if (!NetworkMonitor.getInstance(appContext).isCurrentlyConnected()) {
            return liveData;
        }

        jamendoApi.searchTracks(
                        ApiConfig.CLIENT_ID,
                        JAMENDO_RESPONSE_FORMAT,
                        trimmedQuery,
                        JAMENDO_SEARCH_LIMIT,
                        JAMENDO_AUDIO_FORMAT
                )
                .enqueue(new retrofit2.Callback<JamendoResponse>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<JamendoResponse> call,
                                           @NonNull retrofit2.Response<JamendoResponse> response) {
                        if (!response.isSuccessful()
                                || response.body() == null
                                || response.body().getResults() == null) {
                            liveData.postValue(new ArrayList<>());
                            return;
                        }

                        liveData.postValue(normalizeJamendoTracks(response.body().getResults()));
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<JamendoResponse> call,
                                          @NonNull Throwable throwable) {
                        Log.e(TAG, "Jamendo search request error", throwable);
                        liveData.postValue(new ArrayList<>());
                    }
                });

        return liveData;
    }

    public Track getTrackByIdSync(String trackId) {
        if (trackId == null || trackId.trim().isEmpty()) {
            return null;
        }

        return trackDao.getTrackByIdSync(trackId);
    }

    public void updateLastPlayed(Track track) {
        if (track == null || track.getId() == null || track.getId().trim().isEmpty()) {
            return;
        }

        if (!NetworkMonitor.getInstance(appContext).isCurrentlyConnected()) {
            return;
        }

        executorService.execute(() ->
                trackDao.updateLastPlayed(track.getId(), System.currentTimeMillis())
        );
    }

    public void addToFavorites(String trackId, boolean favorite) {
        executorService.execute(() -> trackDao.updateFavoriteState(trackId, favorite));
    }

    public LiveData<List<Playlist>> getAllPlaylists() {
        return playlistDao.getAllPlaylists();
    }

    public LiveData<List<String>> getAllPlaylistTrackIds() {
        return crossRefDao.getAllTrackIds();
    }

    public void createPlaylist(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        executorService.execute(() -> playlistDao.insert(
                new Playlist(name.trim(), System.currentTimeMillis())
        ));
    }

    public void addTrackToPlaylist(int playlistId, String trackId) {
        if (playlistId <= 0 || trackId == null || trackId.trim().isEmpty()) {
            return;
        }

        executorService.execute(() -> crossRefDao.insert(
                new PlaylistTrackCrossRef(playlistId, trackId)
        ));
    }

    public LiveData<List<Track>> getTracksFromPlaylist(int playlistId) {
        return crossRefDao.getTracksForPlaylist(playlistId);
    }

    public void deletePlaylist(int playlistId) {
        if (playlistId <= 0) {
            return;
        }

        executorService.execute(() -> playlistDao.deleteById(playlistId));
    }

    public void removeTrackFromPlaylist(int playlistId, String trackId) {
        if (playlistId <= 0 || trackId == null || trackId.trim().isEmpty()) {
            return;
        }

        executorService.execute(() -> crossRefDao.deleteByIds(playlistId, trackId));
    }

    public void removeFromDownloads(Track track) {
        if (track == null || track.getId() == null || track.getId().trim().isEmpty()) {
            return;
        }

        executorService.execute(() -> {
            if (track.getDataUri() != null && track.getDataUri().startsWith("file:")) {
                File localFile = new File(Uri.parse(track.getDataUri()).getPath());
                if (localFile.exists()) {
                    localFile.delete();
                }
            }

            trackDao.updateDownloadedState(track.getId(), false);
        });
    }

    public void saveTrack(Track track) {
        if (track == null || track.getId() == null || track.getId().trim().isEmpty()) {
            return;
        }

        executorService.execute(() -> saveTrackSync(track));
    }

    public void downloadTrack(Track track, DownloadCallback callback) {
        if (track == null || track.getDataUri() == null || track.getDataUri().isEmpty()) {
            if (callback != null) {
                callback.onError("Track url is empty.");
            }
            return;
        }

        if (!NetworkMonitor.getInstance(appContext).isCurrentlyConnected()) {
            if (callback != null) {
                callback.onError("No internet connection.");
            }
            return;
        }

        if (!downloadsDirectory.exists() && !downloadsDirectory.mkdirs()) {
            if (callback != null) {
                callback.onError("Could not create downloads directory.");
            }
            return;
        }

        Request request = new Request.Builder()
                .url(track.getDataUri())
                .build();
        DownloadSessionManager downloadSessionManager =
                DownloadSessionManager.getInstance(appContext);
        downloadSessionManager.onDownloadStarted(track.getId(), track.getTitle());

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException exception) {
                downloadSessionManager.onDownloadFailed(track.getId());
                if (callback != null) {
                    callback.onError(exception.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    downloadSessionManager.onDownloadFailed(track.getId());
                    if (callback != null) {
                        callback.onError("Download failed.");
                    }
                    return;
                }

                File outputFile = new File(downloadsDirectory, "track_" + track.getId() + resolveExtension(track.getDataUri()));
                long totalBytes = response.body().contentLength();
                long downloadedBytes = 0L;
                int lastProgress = -1;

                try (InputStream inputStream = response.body().byteStream();
                     FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;

                        if (totalBytes > 0L) {
                            int progress = (int) ((downloadedBytes * 100L) / totalBytes);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                downloadSessionManager.onProgress(track.getId(), progress);
                            }
                        }
                    }
                } catch (IOException exception) {
                    downloadSessionManager.onDownloadFailed(track.getId());
                    throw exception;
                }

                String localUri = Uri.fromFile(outputFile).toString();

                executorService.execute(() -> {
                    Track savedTrack = trackDao.getTrackByIdSync(track.getId());
                    if (savedTrack == null) {
                        track.setSourceType(SOURCE_TYPE_JAMENDO);
                        track.setDownloaded(true);
                        track.setDataUri(localUri);
                        saveTrackSync(track);
                    } else {
                        savedTrack.setSourceType(SOURCE_TYPE_JAMENDO);
                        savedTrack.setDownloaded(true);
                        savedTrack.setDataUri(localUri);
                        mergeTrack(savedTrack, track);
                        trackDao.update(savedTrack);
                    }
                });
                downloadSessionManager.onDownloadSuccess(track.getId());

                if (callback != null) {
                    callback.onSuccess(localUri);
                }
            }
        });
    }

    private List<Track> normalizeJamendoTracks(List<Track> apiTracks) {
        List<Track> result = new ArrayList<>();

        for (Track track : apiTracks) {
            if (track == null || track.getId() == null || track.getId().trim().isEmpty()) {
                continue;
            }

            if (track.getDataUri() == null || track.getDataUri().trim().isEmpty()) {
                continue;
            }

            track.setSourceType(SOURCE_TYPE_JAMENDO);
            if (track.getTitle() == null || track.getTitle().trim().isEmpty()) {
                track.setTitle(appContext.getString(R.string.unknown_title));
            }
            if (track.getArtist() == null || track.getArtist().trim().isEmpty()) {
                track.setArtist("Unknown artist");
            }

            result.add(track);
        }

        return result;
    }

    private String resolveExtension(String url) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension == null || extension.isEmpty()) {
            return ".mp3";
        }

        return "." + extension;
    }

    private int getRandomWaveOffset() {
        return random.nextInt(JAMENDO_RANDOM_OFFSET_PAGES) * JAMENDO_LIMIT;
    }

    private void postRandomWaveResultIfReady(MutableLiveData<List<Track>> liveData,
                                             int completedRequests,
                                             int requestCount,
                                             List<Track> mergedTracks) {
        if (completedRequests < requestCount) {
            return;
        }

        List<Track> result;
        synchronized (mergedTracks) {
            result = new ArrayList<>(mergedTracks);
        }

        Log.d(TAG, "Random Jamendo tracks loaded: " + result.size());
        liveData.postValue(result);
        saveTracks(result);
    }

    private void saveTracks(List<Track> tracks) {
        executorService.execute(() -> saveTracksSync(tracks));
    }

    private void saveTracksSync(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }

        for (Track track : tracks) {
            saveTrackSync(track);
        }
    }

    private void saveTrackSync(Track track) {
        if (track == null || track.getId() == null || track.getId().trim().isEmpty()) {
            return;
        }

        Track existingTrack = trackDao.getTrackByIdSync(track.getId());
        if (existingTrack == null) {
            trackDao.insert(track);
            return;
        }

        mergeTrack(existingTrack, track);
        trackDao.update(existingTrack);
    }

    private void mergeTrack(Track target, Track source) {
        if (target == null || source == null) {
            return;
        }

        boolean targetHasLocalDownloadedUri = target.isDownloaded()
                && target.getDataUri() != null
                && (target.getDataUri().startsWith("file:")
                || target.getDataUri().startsWith("content:"));
        boolean sourceHasLocalDownloadedUri = source.isDownloaded()
                && source.getDataUri() != null
                && (source.getDataUri().startsWith("file:")
                || source.getDataUri().startsWith("content:"));

        if (source.getTitle() != null && !source.getTitle().trim().isEmpty()) {
            target.setTitle(source.getTitle());
        }

        if (source.getArtist() != null && !source.getArtist().trim().isEmpty()) {
            target.setArtist(source.getArtist());
        }

        if (sourceHasLocalDownloadedUri) {
            target.setDataUri(source.getDataUri());
        } else if (!targetHasLocalDownloadedUri
                && source.getDataUri() != null
                && !source.getDataUri().trim().isEmpty()) {
            target.setDataUri(source.getDataUri());
        }

        if (source.getImageUrl() != null && !source.getImageUrl().trim().isEmpty()) {
            target.setImageUrl(source.getImageUrl());
        }

        if (source.getDuration() > 0L) {
            target.setDuration(source.getDuration());
        }

        if (source.getSourceType() != null && !source.getSourceType().trim().isEmpty()) {
            target.setSourceType(source.getSourceType());
        }

        target.setFavorite(source.isFavorite() || target.isFavorite());
        target.setDownloaded(source.isDownloaded() || target.isDownloaded());

        if (source.getLastPlayed() > 0L) {
            target.setLastPlayed(source.getLastPlayed());
        }
    }

    public interface DownloadCallback {
        void onSuccess(String localUri);

        void onError(String message);
    }
}
