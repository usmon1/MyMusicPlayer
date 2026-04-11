package com.example.mymusicplayer.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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
    private static final String JAMENDO_RESPONSE_FORMAT = "json";
    private static final String JAMENDO_WAVE_ORDER = "popularity_total";
    private static final String JAMENDO_AUDIO_FORMAT = "mp32";
    private static final int JAMENDO_RANDOM_OFFSET_PAGES = 20;

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
        Context appContext = context.getApplicationContext();
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
                        executorService.execute(() -> trackDao.insertAll(tracks));
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
            trackDao.insertAll(tracks);
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

    public void updateLastPlayed(String trackId) {
        executorService.execute(() -> trackDao.updateLastPlayed(trackId, System.currentTimeMillis()));
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

        executorService.execute(() -> trackDao.insert(track));
    }

    public void downloadTrack(Track track, DownloadCallback callback) {
        if (track == null || track.getDataUri() == null || track.getDataUri().isEmpty()) {
            if (callback != null) {
                callback.onError("Track url is empty.");
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

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException exception) {
                if (callback != null) {
                    callback.onError(exception.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    if (callback != null) {
                        callback.onError("Download failed.");
                    }
                    return;
                }

                File outputFile = new File(downloadsDirectory, "track_" + track.getId() + resolveExtension(track.getDataUri()));

                try (InputStream inputStream = response.body().byteStream();
                     FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                String localUri = Uri.fromFile(outputFile).toString();

                executorService.execute(() -> {
                    Track savedTrack = trackDao.getTrackByIdSync(track.getId());
                    if (savedTrack == null) {
                        track.setSourceType(SOURCE_TYPE_JAMENDO);
                        track.setDownloaded(true);
                        track.setDataUri(localUri);
                        trackDao.insert(track);
                    } else {
                        trackDao.updateDownloadedTrack(track.getId(), true, localUri);
                    }
                });

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
                track.setTitle("Unknown title");
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
        executorService.execute(() -> trackDao.insertAll(result));
    }

    public interface DownloadCallback {
        void onSuccess(String localUri);

        void onError(String message);
    }
}
