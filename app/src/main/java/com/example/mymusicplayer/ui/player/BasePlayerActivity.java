package com.example.mymusicplayer.ui.player;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Playlist;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.data.repository.MusicRepository;
import com.example.mymusicplayer.playback.MusicService;
import com.example.mymusicplayer.ui.main.MainActivity;
import com.example.mymusicplayer.util.NetworkMonitor;
import com.example.mymusicplayer.util.PlaybackAccessHelper;
import com.example.mymusicplayer.util.ThemeManager;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BasePlayerActivity extends AppCompatActivity
        implements PlayerBottomSheetFragment.PlayerControlListener {

    private static final long PLAYER_PROGRESS_UPDATE_DELAY_MS = 500L;
    private static final long MOTION_SHORT_DURATION_MS = 140L;
    private static final long MOTION_MEDIUM_DURATION_MS = 220L;
    private static final float PRESSED_SCALE = 0.97f;
    public static final String EXTRA_FORCE_OFFLINE_MODE = "force_offline_mode";

    private final List<Playlist> playlists = new ArrayList<>();
    private final Handler miniPlayerProgressHandler = new Handler(Looper.getMainLooper());
    private final Runnable miniPlayerProgressRunnable = new Runnable() {
        @Override
        public void run() {
            updateMiniPlayerProgress();
            if (!isFinishing() && !isDestroyed()) {
                miniPlayerProgressHandler.postDelayed(this, PLAYER_PROGRESS_UPDATE_DELAY_MS);
            }
        }
    };
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(
                            this,
                            R.string.message_notifications_permission_needed,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

    private MusicRepository repository;
    private NetworkMonitor networkMonitor;
    private PlayerBottomSheetFragment playerBottomSheetFragment;
    private MusicService musicService;
    private boolean isServiceBound;
    private boolean isNetworkAvailable;
    private Boolean lastNetworkAvailable;
    private Track currentTrack;
    private Track pendingTrackToPlay;
    private List<Track> pendingPlaybackQueue;
    private String pendingQueueTrackId;
    private String pendingPlaybackSource;
    private String pendingPlaybackSourceLabel;
    private String pendingLaunchPlaybackSource;
    private String pendingLaunchPlaybackSourceLabel;
    private boolean isPlaying;

    private View miniPlayerView;
    private TextView miniPlayerTitleView;
    private TextView miniPlayerArtistView;
    private MaterialButton miniPlayerActionButton;
    private ImageView miniPlayerCoverView;
    private SeekBar miniPlayerProgressView;
    private String lastRenderedMiniPlayerTrackId;
    private Boolean lastRenderedMiniPlayerPlayState;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isServiceBound = true;

            musicService.getCurrentTrack().observe(BasePlayerActivity.this, track -> {
                currentTrack = track;
                onCurrentTrackChanged(track);
                renderPlayerState();
            });

            musicService.getIsPlaying().observe(BasePlayerActivity.this, playingState -> {
                isPlaying = Boolean.TRUE.equals(playingState);
                renderPlayerState();
            });

            musicService.getPlaybackCompletedEvent().observe(BasePlayerActivity.this, eventCount -> {
                if (eventCount != null && eventCount > 0) {
                    onPlaybackCompleted();
                }
            });

            syncPlaybackQueueWithService();

            if (pendingTrackToPlay != null) {
                musicService.setPlaybackContext(
                        pendingPlaybackQueue,
                        pendingTrackToPlay.getId(),
                        pendingLaunchPlaybackSource,
                        pendingLaunchPlaybackSourceLabel
                );
                musicService.playTrack(pendingTrackToPlay);
                pendingTrackToPlay = null;
            }

            startMiniPlayerProgressUpdates();
            notifyPlayerBottomSheetProgressChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            musicService = null;
            stopMiniPlayerProgressUpdates();
            notifyPlayerBottomSheetProgressChanged();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(ThemeManager.getSavedNightMode(this));
        super.onCreate(savedInstanceState);
        repository = new MusicRepository(this);
        networkMonitor = NetworkMonitor.getInstance(this);
        isNetworkAvailable = networkMonitor.isCurrentlyConnected();
        lastNetworkAvailable = isNetworkAvailable;
        repository.getAllPlaylists().observe(this, items -> {
            playlists.clear();
            if (items != null) {
                playlists.addAll(items);
            }
        });
        requestNotificationPermissionIfNeeded();
    }

    protected void setupPlayerUi(View miniPlayerView,
                                 TextView miniPlayerTitleView,
                                 MaterialButton miniPlayerActionButton) {
        this.miniPlayerView = miniPlayerView;
        this.miniPlayerTitleView = miniPlayerTitleView;
        this.miniPlayerActionButton = miniPlayerActionButton;
        this.miniPlayerArtistView = miniPlayerView.findViewById(R.id.textMiniPlayerArtist);
        this.miniPlayerCoverView = miniPlayerView.findViewById(R.id.imageMiniPlayerCover);
        this.miniPlayerProgressView = miniPlayerView.findViewById(R.id.seekMiniPlayerProgress);

        miniPlayerView.setOnClickListener(view -> showPlayerBottomSheet());
        miniPlayerActionButton.setEnabled(true);
        miniPlayerActionButton.setOnClickListener(view -> togglePlayback());
        applyPressMotion(miniPlayerView);
        applyPressMotion(miniPlayerActionButton);
        if (miniPlayerProgressView != null) {
            miniPlayerProgressView.setOnTouchListener((view, event) -> true);
        }
        renderPlayerState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent = new Intent(this, MusicService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        stopMiniPlayerProgressUpdates();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        super.onStop();
    }

    protected void playTrack(Track track) {
        playTrackWithSource(
                track,
                resolvePlaybackSource(track),
                resolvePlaybackSourceLabel(track)
        );
    }

    protected void playTrackWithSource(Track track,
                                       @Nullable String playbackSource,
                                       @Nullable String playbackSourceLabel) {
        if (track == null) {
            return;
        }

        PlaybackAccessHelper.prepareTrackForPlayback(this, track);
        if (!PlaybackAccessHelper.canPlayTrack(this, track)) {
            Toast.makeText(this, R.string.message_track_unavailable_offline, Toast.LENGTH_SHORT).show();
            return;
        }

        ensurePlaybackServiceStarted();
        repository.saveTrack(track);
        repository.updateLastPlayed(track);
        currentTrack = track;
        isPlaying = true;
        pendingLaunchPlaybackSource = playbackSource;
        pendingLaunchPlaybackSourceLabel = playbackSourceLabel;
        syncPlaybackQueueSnapshot(track, playbackSource, playbackSourceLabel);

        if (isServiceBound && musicService != null) {
            musicService.setPlaybackContext(
                    getPlaybackQueue(),
                    track.getId(),
                    playbackSource,
                    playbackSourceLabel
            );
            musicService.playTrack(track);
        } else {
            pendingTrackToPlay = track;
        }

        renderPlayerState();
    }

    protected Track getCurrentTrack() {
        return currentTrack;
    }

    protected boolean isPlaying() {
        return isPlaying;
    }

    protected List<Track> getPlaybackQueue() {
        return Collections.emptyList();
    }

    protected void notifyPlaybackQueueChanged() {
        syncPlaybackQueueSnapshot(
                currentTrack,
                getCurrentPlaybackSource(),
                getCurrentPlaybackSourceLabel()
        );
        syncPlaybackQueueWithService();
    }

    protected String getCurrentPlaybackSourceLabel() {
        if (musicService != null && musicService.getCurrentPlaybackSourceLabel() != null) {
            return musicService.getCurrentPlaybackSourceLabel();
        }

        return pendingPlaybackSourceLabel;
    }

    protected String getCurrentPlaybackSource() {
        if (musicService != null && musicService.getCurrentPlaybackSource() != null) {
            return musicService.getCurrentPlaybackSource();
        }

        return pendingPlaybackSource;
    }

    protected String resolvePlaybackSource(@Nullable Track track) {
        return null;
    }

    protected String resolvePlaybackSourceLabel(@Nullable Track track) {
        return null;
    }

    protected boolean isNetworkAvailable() {
        return isNetworkAvailable;
    }

    protected void forceNetworkAvailability(boolean isNetworkAvailable) {
        this.isNetworkAvailable = isNetworkAvailable;
        this.lastNetworkAvailable = isNetworkAvailable;
    }

    protected void onNetworkAvailabilityChanged(boolean isNetworkAvailable) {
    }

    protected boolean shouldNavigateToMainOnOfflineRefresh() {
        return true;
    }

    protected void setupSwipeRefresh(@Nullable SwipeRefreshLayout swipeRefreshLayout) {
        if (swipeRefreshLayout == null) {
            return;
        }

        swipeRefreshLayout.setOnRefreshListener(() -> {
            refreshNetworkState(true);
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    protected void refreshNetworkState(boolean fromUserRefresh) {
        boolean currentState = networkMonitor.refresh();

        isNetworkAvailable = currentState;
        lastNetworkAvailable = currentState;
        onNetworkAvailabilityChanged(currentState);

        if (fromUserRefresh && !currentState) {
            handleNetworkLost();
        } else if (fromUserRefresh) {
            Toast.makeText(this, R.string.message_network_available, Toast.LENGTH_SHORT).show();
        }
    }

    protected boolean ownsPlaybackSource(@Nullable String playbackSource) {
        String screenPlaybackSource = resolvePlaybackSource(currentTrack);
        return screenPlaybackSource != null && screenPlaybackSource.equals(playbackSource);
    }

    protected long getPlaybackPosition() {
        if (!isServiceBound || musicService == null) {
            return 0L;
        }

        return musicService.getCurrentPosition();
    }

    protected long getPlaybackDuration() {
        if (!isServiceBound || musicService == null) {
            return 0L;
        }

        return musicService.getDuration();
    }

    protected boolean canSkipToPrevious() {
        if (isServiceBound && musicService != null) {
            return musicService.canPlayPrevious();
        }

        return getAdjacentTrackPreview(-1) != null;
    }

    protected boolean canSkipToNext() {
        if (isServiceBound && musicService != null) {
            return musicService.canPlayNext();
        }

        return getAdjacentTrackPreview(1) != null;
    }

    protected void startScreen(Intent intent) {
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    protected void finishScreen() {
        finish();
        overridePendingTransition(0, 0);
    }

    protected void applyPressMotion(@Nullable View view) {
        if (view == null) {
            return;
        }

        view.setOnTouchListener((pressedView, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressedView.animate()
                            .scaleX(PRESSED_SCALE)
                            .scaleY(PRESSED_SCALE)
                            .setDuration(MOTION_SHORT_DURATION_MS)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pressedView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(MOTION_SHORT_DURATION_MS)
                            .start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void togglePlayback() {
        if (!isServiceBound || musicService == null) {
            return;
        }

        if (musicService.isPlayingNow()) {
            musicService.pause();
        } else if (musicService.getCurrentTrackValue() != null) {
            musicService.resume();
        }

        notifyPlayerBottomSheetProgressChanged();
    }

    private void handleNetworkLost() {
        boolean canContinueOffline = PlaybackAccessHelper.isOfflinePlayable(this, currentTrack);
        if (!canContinueOffline && musicService != null && currentTrack != null) {
            musicService.stopPlayback();
            Toast.makeText(this, R.string.message_playback_stopped_offline, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.message_offline_mode_applied, Toast.LENGTH_SHORT).show();
        }

        if (!(this instanceof MainActivity) && shouldNavigateToMainOnOfflineRefresh()) {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            mainIntent.putExtra(EXTRA_FORCE_OFFLINE_MODE, true);
            startScreen(mainIntent);
            finishScreen();
        }
    }

    private void showPlayerBottomSheet() {
        if (playerBottomSheetFragment == null) {
            playerBottomSheetFragment = PlayerBottomSheetFragment.newInstance();
        }

        if (!playerBottomSheetFragment.isAdded()) {
            playerBottomSheetFragment.show(getSupportFragmentManager(), PlayerBottomSheetFragment.TAG);
        }

        playerBottomSheetFragment.updatePlayerState(
                currentTrack,
                isPlaying,
                getCurrentPlaybackSourceLabel()
        );
        notifyPlayerBottomSheetProgressChanged();
    }

    private void ensurePlaybackServiceStarted() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void renderPlayerState() {
        if (miniPlayerTitleView == null || miniPlayerActionButton == null) {
            return;
        }

        syncPlaybackQueueWithService();
        String currentTrackId = currentTrack == null ? null : currentTrack.getId();
        boolean hasRenderedMiniPlayerBefore = lastRenderedMiniPlayerTrackId != null;
        boolean trackChanged = hasRenderedMiniPlayerBefore
                && currentTrackId != null
                && !currentTrackId.equals(lastRenderedMiniPlayerTrackId);
        boolean playStateChanged = hasRenderedMiniPlayerBefore
                && lastRenderedMiniPlayerPlayState != null
                && lastRenderedMiniPlayerPlayState != isPlaying;

        if (currentTrack == null) {
            miniPlayerTitleView.setText(R.string.mini_player_idle);
            if (miniPlayerArtistView != null) {
                miniPlayerArtistView.setText(R.string.no_artist);
            }
            if (miniPlayerCoverView != null) {
                miniPlayerCoverView.setTag(null);
                miniPlayerCoverView.setImageResource(R.drawable.img);
            }
        } else {
            miniPlayerTitleView.setText(currentTrack.getTitle());
            if (miniPlayerArtistView != null) {
                String artist = currentTrack.getArtist();
                miniPlayerArtistView.setText(
                        artist == null || artist.trim().isEmpty()
                                ? getString(R.string.no_artist)
                                : artist
                );
            }
            loadMiniPlayerCover(currentTrack);
        }

        updatePlayPauseButton(miniPlayerActionButton, isPlaying);
        miniPlayerActionButton.setEnabled(currentTrack != null);
        miniPlayerActionButton.setAlpha(currentTrack == null ? 0.5f : 1f);
        updateMiniPlayerProgress();
        animateMiniPlayerTrackChange(trackChanged);
        animatePlaybackButtonState(miniPlayerActionButton, playStateChanged && currentTrack != null);

        if (playerBottomSheetFragment != null) {
            playerBottomSheetFragment.updatePlayerState(
                    currentTrack,
                    isPlaying,
                    getCurrentPlaybackSourceLabel()
            );
            notifyPlayerBottomSheetProgressChanged();
        }

        lastRenderedMiniPlayerTrackId = currentTrackId;
        lastRenderedMiniPlayerPlayState = isPlaying;
        onPlayerStateChanged(currentTrack, isPlaying);
    }

    protected void onPlayerStateChanged(Track track, boolean isPlaying) {
    }

    protected void onCurrentTrackChanged(Track track) {
    }

    protected void onPlaybackCompleted() {
        String playbackSource = getCurrentPlaybackSource();
        if (playbackSource == null
                || MusicService.PLAYBACK_SOURCE_SEARCH.equals(playbackSource)
                || MusicService.PLAYBACK_SOURCE_RECENT.equals(playbackSource)) {
            return;
        }

        if (!ownsPlaybackSource(playbackSource)) {
            return;
        }

        Track nextTrack = getAdjacentTrack(1);
        if (nextTrack != null) {
            playTrack(nextTrack);
        }
    }

    @Override
    public void onPlayPauseClicked() {
        togglePlayback();
    }

    @Override
    public void onFavoriteClicked() {
        if (!isNetworkAvailable) {
            Toast.makeText(this, R.string.message_feature_online_only, Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentTrack == null) {
            return;
        }

        boolean newFavoriteState = !currentTrack.isFavorite();
        currentTrack.setFavorite(newFavoriteState);
        repository.saveTrack(currentTrack);
        repository.addToFavorites(currentTrack.getId(), newFavoriteState);
        renderPlayerState();
    }

    @Override
    public void onAddToPlaylistClicked() {
        if (!isNetworkAvailable) {
            Toast.makeText(this, R.string.message_feature_online_only, Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentTrack == null) {
            Toast.makeText(this, R.string.message_select_track_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (playlists.isEmpty()) {
            Toast.makeText(this, R.string.message_no_playlists, Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] names = new CharSequence[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            names[i] = playlists.get(i).getName();
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_add_to_playlist_title)
                .setItems(names, (dialog, which) -> {
                    Playlist playlist = playlists.get(which);
                    repository.saveTrack(currentTrack);
                    repository.addTrackToPlaylist(playlist.getPlaylistId(), currentTrack.getId());
                    Toast.makeText(
                            this,
                            getString(R.string.message_added_to_playlist, playlist.getName()),
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    @Override
    public void onDownloadClicked() {
        if (!isNetworkAvailable) {
            Toast.makeText(this, R.string.message_feature_online_only, Toast.LENGTH_SHORT).show();
            return;
        }

        if (MusicService.PLAYBACK_SOURCE_LOCAL.equals(getCurrentPlaybackSource())) {
            return;
        }

        Track trackToDownload = currentTrack;
        if (trackToDownload == null) {
            Toast.makeText(this, R.string.message_select_track_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (trackToDownload.isDownloaded()) {
            Toast.makeText(this, R.string.message_already_downloaded, Toast.LENGTH_SHORT).show();
            return;
        }

        repository.downloadTrack(trackToDownload, new MusicRepository.DownloadCallback() {
            @Override
            public void onSuccess(String localUri) {
                trackToDownload.setDownloaded(true);
                trackToDownload.setDataUri(localUri);
                repository.saveTrack(trackToDownload);
                runOnUiThread(() -> {
                    renderPlayerState();
                    Toast.makeText(
                            BasePlayerActivity.this,
                            R.string.message_download_success,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(
                        BasePlayerActivity.this,
                        getString(R.string.message_download_error, message == null ? "" : message),
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    @Override
    public void onPreviousClicked() {
        if (isServiceBound && musicService != null) {
            musicService.playPrevious();
        }
    }

    @Override
    public void onNextClicked() {
        if (isServiceBound && musicService != null) {
            musicService.playNext();
        }
    }

    @Override
    public void onSeekTo(long positionMs) {
        if (!isServiceBound || musicService == null) {
            return;
        }

        musicService.seekTo(positionMs);
        updateMiniPlayerProgress();
        notifyPlayerBottomSheetProgressChanged();
    }

    private Track getAdjacentTrack(int direction) {
        return getAdjacentTrackPreview(direction);
    }

    @Nullable
    private Track getAdjacentTrackPreview(int direction) {
        List<Track> queue = getPlaybackQueue();
        if (queue == null || queue.isEmpty() || currentTrack == null) {
            return null;
        }

        int currentIndex = -1;
        for (int index = 0; index < queue.size(); index++) {
            Track track = queue.get(index);
            if (track != null && currentTrack.getId().equals(track.getId())) {
                currentIndex = index;
                break;
            }
        }

        if (currentIndex < 0) {
            return null;
        }

        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= queue.size()) {
            return null;
        }

        return queue.get(targetIndex);
    }

    private void startMiniPlayerProgressUpdates() {
        miniPlayerProgressHandler.removeCallbacks(miniPlayerProgressRunnable);
        miniPlayerProgressHandler.post(miniPlayerProgressRunnable);
    }

    private void stopMiniPlayerProgressUpdates() {
        miniPlayerProgressHandler.removeCallbacks(miniPlayerProgressRunnable);
    }

    private void updateMiniPlayerProgress() {
        if (miniPlayerProgressView == null) {
            return;
        }

        long duration = getPlaybackDuration();
        long currentPosition = getPlaybackPosition();
        if (duration <= 0L) {
            miniPlayerProgressView.setMax(100);
            miniPlayerProgressView.setProgress(0);
            return;
        }

        miniPlayerProgressView.setMax((int) duration);
        miniPlayerProgressView.setProgress((int) Math.min(currentPosition, duration));
    }

    private void animateMiniPlayerTrackChange(boolean trackChanged) {
        if (!trackChanged || miniPlayerView == null) {
            return;
        }

        miniPlayerView.setPivotY(miniPlayerView.getHeight());
        miniPlayerView.setTranslationY(18f);
        miniPlayerView.setAlpha(0.92f);
        miniPlayerView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(MOTION_MEDIUM_DURATION_MS)
                .start();

        animateTrackInfoView(miniPlayerTitleView);
        animateTrackInfoView(miniPlayerArtistView);
        animateTrackInfoView(miniPlayerCoverView);
    }

    private void animateTrackInfoView(@Nullable View view) {
        if (view == null) {
            return;
        }

        view.setAlpha(0.4f);
        view.setTranslationY(10f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(MOTION_MEDIUM_DURATION_MS)
                .start();
    }

    private void animatePlaybackButtonState(@Nullable View button, boolean shouldAnimate) {
        if (button == null || !shouldAnimate) {
            return;
        }

        button.animate().cancel();
        button.setScaleX(0.9f);
        button.setScaleY(0.9f);
        button.setAlpha(0.7f);
        button.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(MOTION_MEDIUM_DURATION_MS)
                .start();
    }

    protected void updatePlayPauseButton(@Nullable MaterialButton button, boolean playing) {
        if (button == null) {
            return;
        }

        button.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        button.setContentDescription(getString(playing ? R.string.pause : R.string.play));
    }

    private void notifyPlayerBottomSheetProgressChanged() {
        if (playerBottomSheetFragment != null) {
            playerBottomSheetFragment.updatePlaybackProgress(
                    getPlaybackPosition(),
                    getPlaybackDuration()
            );
        }
    }

    private void syncPlaybackQueueSnapshot(@Nullable Track track,
                                           @Nullable String playbackSource,
                                           @Nullable String playbackSourceLabel) {
        pendingPlaybackQueue = new ArrayList<>(getPlaybackQueue());
        pendingQueueTrackId = track == null ? null : track.getId();
        pendingPlaybackSource = playbackSource;
        pendingPlaybackSourceLabel = playbackSourceLabel;
    }

    private void syncPlaybackQueueWithService() {
        if (!isServiceBound || musicService == null) {
            return;
        }

        String activePlaybackSource = getCurrentPlaybackSource();
        if (activePlaybackSource != null && !ownsPlaybackSource(activePlaybackSource)) {
            return;
        }

        List<Track> queue = new ArrayList<>(getPlaybackQueue());
        if (queue.isEmpty() && pendingPlaybackQueue != null) {
            queue = pendingPlaybackQueue;
        }
        String trackId = currentTrack != null ? currentTrack.getId() : pendingQueueTrackId;
        if (trackId == null && pendingTrackToPlay != null) {
            trackId = pendingTrackToPlay.getId();
        }

        musicService.updatePlaybackQueue(queue, trackId);
    }

    private void loadMiniPlayerCover(Track track) {
        if (miniPlayerCoverView == null) {
            return;
        }

        miniPlayerCoverView.setImageResource(R.drawable.img);
        if (track == null) {
            miniPlayerCoverView.setTag(null);
            return;
        }

        String imageUrl = track.getImageUrl();
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            miniPlayerCoverView.setTag(null);
            return;
        }

        miniPlayerCoverView.setTag(imageUrl);
        Uri uri = Uri.parse(imageUrl);
        String scheme = uri.getScheme();
        if (scheme != null
                && ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme))) {
            miniPlayerCoverView.setImageURI(uri);
            return;
        }

        new Thread(() -> {
            Bitmap bitmap = null;
            try (InputStream inputStream = new URL(imageUrl).openStream()) {
                bitmap = BitmapFactory.decodeStream(inputStream);
            } catch (Exception ignored) {
            }

            Bitmap finalBitmap = bitmap;
            if (isFinishing() || isDestroyed()) {
                return;
            }

            runOnUiThread(() -> {
                if (miniPlayerCoverView == null || !imageUrl.equals(miniPlayerCoverView.getTag())) {
                    return;
                }

                if (finalBitmap != null) {
                    miniPlayerCoverView.setImageBitmap(finalBitmap);
                } else {
                    miniPlayerCoverView.setImageResource(R.drawable.img);
                }
            });
        }).start();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
