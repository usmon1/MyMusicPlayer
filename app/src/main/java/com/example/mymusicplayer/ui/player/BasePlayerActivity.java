package com.example.mymusicplayer.ui.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Playlist;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.data.repository.MusicRepository;
import com.example.mymusicplayer.playback.MusicService;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BasePlayerActivity extends AppCompatActivity
        implements PlayerBottomSheetFragment.PlayerControlListener {

    private static final long PLAYER_PROGRESS_UPDATE_DELAY_MS = 500L;

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

    private MusicRepository repository;
    private PlayerBottomSheetFragment playerBottomSheetFragment;
    private MusicService musicService;
    private boolean isServiceBound;
    private Track currentTrack;
    private Track pendingTrackToPlay;
    private boolean isPlaying;

    private View miniPlayerView;
    private TextView miniPlayerTitleView;
    private TextView miniPlayerArtistView;
    private Button miniPlayerActionButton;
    private ImageView miniPlayerCoverView;
    private SeekBar miniPlayerProgressView;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isServiceBound = true;

            musicService.getCurrentTrack().observe(BasePlayerActivity.this, track -> {
                currentTrack = track;
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

            if (pendingTrackToPlay != null) {
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
        super.onCreate(savedInstanceState);
        repository = new MusicRepository(this);
        repository.getAllPlaylists().observe(this, items -> {
            playlists.clear();
            if (items != null) {
                playlists.addAll(items);
            }
        });
    }

    protected void setupPlayerUi(View miniPlayerView,
                                 TextView miniPlayerTitleView,
                                 Button miniPlayerActionButton) {
        this.miniPlayerView = miniPlayerView;
        this.miniPlayerTitleView = miniPlayerTitleView;
        this.miniPlayerActionButton = miniPlayerActionButton;
        this.miniPlayerArtistView = miniPlayerView.findViewById(R.id.textMiniPlayerArtist);
        this.miniPlayerCoverView = miniPlayerView.findViewById(R.id.imageMiniPlayerCover);
        this.miniPlayerProgressView = miniPlayerView.findViewById(R.id.seekMiniPlayerProgress);

        miniPlayerView.setOnClickListener(view -> showPlayerBottomSheet());
        miniPlayerActionButton.setEnabled(true);
        miniPlayerActionButton.setOnClickListener(view -> togglePlayback());
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
        if (track == null) {
            return;
        }

        repository.saveTrack(track);
        repository.updateLastPlayed(track.getId());
        currentTrack = track;
        isPlaying = true;

        if (isServiceBound && musicService != null) {
            musicService.playTrack(track);
        } else {
            pendingTrackToPlay = track;
        }

        renderPlayerState();
        showPlayerBottomSheet();
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

    private void showPlayerBottomSheet() {
        if (playerBottomSheetFragment == null) {
            playerBottomSheetFragment = PlayerBottomSheetFragment.newInstance();
        }

        if (!playerBottomSheetFragment.isAdded()) {
            playerBottomSheetFragment.show(getSupportFragmentManager(), PlayerBottomSheetFragment.TAG);
        }

        playerBottomSheetFragment.updatePlayerState(currentTrack, isPlaying);
        notifyPlayerBottomSheetProgressChanged();
    }

    private void renderPlayerState() {
        if (miniPlayerTitleView == null || miniPlayerActionButton == null) {
            return;
        }

        if (currentTrack == null) {
            miniPlayerTitleView.setText(R.string.mini_player_idle);
            if (miniPlayerArtistView != null) {
                miniPlayerArtistView.setText(R.string.no_artist);
            }
            if (miniPlayerCoverView != null) {
                miniPlayerCoverView.setTag(null);
                miniPlayerCoverView.setImageResource(R.drawable.ic_player_placeholder);
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

        miniPlayerActionButton.setText(isPlaying ? R.string.pause : R.string.play);
        updateMiniPlayerProgress();

        if (playerBottomSheetFragment != null) {
            playerBottomSheetFragment.updatePlayerState(currentTrack, isPlaying);
            notifyPlayerBottomSheetProgressChanged();
        }

        onPlayerStateChanged(currentTrack, isPlaying);
    }

    protected void onPlayerStateChanged(Track track, boolean isPlaying) {
    }

    protected void onPlaybackCompleted() {
    }

    @Override
    public void onPlayPauseClicked() {
        togglePlayback();
    }

    @Override
    public void onFavoriteClicked() {
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
        if (currentTrack == null) {
            Toast.makeText(this, R.string.message_select_track_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentTrack.isDownloaded()) {
            Toast.makeText(this, R.string.message_already_downloaded, Toast.LENGTH_SHORT).show();
            return;
        }

        repository.downloadTrack(currentTrack, new MusicRepository.DownloadCallback() {
            @Override
            public void onSuccess(String localUri) {
                currentTrack.setDownloaded(true);
                currentTrack.setDataUri(localUri);
                repository.saveTrack(currentTrack);
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
        Track previousTrack = getAdjacentTrack(-1);
        if (previousTrack != null) {
            playTrack(previousTrack);
        }
    }

    @Override
    public void onNextClicked() {
        Track nextTrack = getAdjacentTrack(1);
        if (nextTrack != null) {
            playTrack(nextTrack);
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

    private void notifyPlayerBottomSheetProgressChanged() {
        if (playerBottomSheetFragment != null) {
            playerBottomSheetFragment.updatePlaybackProgress(
                    getPlaybackPosition(),
                    getPlaybackDuration()
            );
        }
    }

    private void loadMiniPlayerCover(Track track) {
        if (miniPlayerCoverView == null) {
            return;
        }

        miniPlayerCoverView.setImageResource(R.drawable.ic_player_placeholder);
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
                    miniPlayerCoverView.setImageResource(R.drawable.ic_player_placeholder);
                }
            });
        }).start();
    }
}
