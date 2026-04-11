package com.example.mymusicplayer.ui.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.databinding.FragmentPlayerBottomSheetBinding;
import com.example.mymusicplayer.ui.downloaded.DownloadedActivity;
import com.example.mymusicplayer.ui.local.LocalMusicActivity;
import com.example.mymusicplayer.ui.main.MainActivity;
import com.example.mymusicplayer.ui.playlist.PlaylistDetailsActivity;
import com.example.mymusicplayer.ui.playlist.PlaylistListActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

/**
 * Simple player bottom sheet with collapsed and expanded content.
 */
public class PlayerBottomSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "PlayerBottomSheet";
    private static final long PROGRESS_UPDATE_DELAY_MS = 500L;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgressFromActivity();
            if (isAdded() && binding != null) {
                progressHandler.postDelayed(this, PROGRESS_UPDATE_DELAY_MS);
            }
        }
    };

    private FragmentPlayerBottomSheetBinding binding;
    private PlayerControlListener playerControlListener;
    private Track currentTrack;
    private boolean isPlaying;
    private boolean isSeekInProgress;

    public static PlayerBottomSheetFragment newInstance() {
        return new PlayerBottomSheetFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof PlayerControlListener) {
            playerControlListener = (PlayerControlListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlayerBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupUi();
        renderState();
    }

    @Override
    public void onStart() {
        super.onStart();

        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog == null) {
            return;
        }

        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        bottomSheet.setLayoutParams(layoutParams);

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheet.post(() -> {
            behavior.setSkipCollapsed(true);
            behavior.setFitToContents(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            updateExpandedVisibility(true);
        });

        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                updateExpandedVisibility(true);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                updateExpandedVisibility(true);
            }
        });

        startProgressUpdates();
    }

    @Override
    public void onStop() {
        stopProgressUpdates();
        super.onStop();
    }

    private void setupUi() {
        binding.buttonCollapsedPlayPause.setOnClickListener(view -> notifyPlayPauseClick());
        binding.buttonExpandedPlayPause.setOnClickListener(view -> notifyPlayPauseClick());
        binding.buttonFavorite.setOnClickListener(view -> notifyFavoriteClick());
        binding.buttonAddToPlaylist.setOnClickListener(view -> notifyAddToPlaylistClick());
        binding.buttonDownload.setOnClickListener(view -> notifyDownloadClick());
        binding.buttonPrevious.setOnClickListener(view -> notifyPreviousClick());
        binding.buttonNext.setOnClickListener(view -> notifyNextClick());
        binding.seekBarProgress.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    binding.textCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                isSeekInProgress = true;
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                isSeekInProgress = false;
                if (playerControlListener != null) {
                    playerControlListener.onSeekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void notifyPlayPauseClick() {
        if (playerControlListener != null) {
            playerControlListener.onPlayPauseClicked();
        }
    }

    private void notifyFavoriteClick() {
        if (playerControlListener != null) {
            playerControlListener.onFavoriteClicked();
        }
    }

    private void notifyAddToPlaylistClick() {
        if (playerControlListener != null) {
            playerControlListener.onAddToPlaylistClicked();
        }
    }

    private void notifyDownloadClick() {
        if (playerControlListener != null) {
            playerControlListener.onDownloadClicked();
        }
    }

    private void notifyPreviousClick() {
        if (playerControlListener != null) {
            playerControlListener.onPreviousClicked();
        }
    }

    private void notifyNextClick() {
        if (playerControlListener != null) {
            playerControlListener.onNextClicked();
        }
    }

    private void updateExpandedVisibility(boolean expanded) {
        if (binding != null) {
            binding.layoutExpanded.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
    }

    private void renderState() {
        if (binding == null) {
            return;
        }

        if (currentTrack == null) {
            binding.textCollapsedTitle.setText(R.string.mini_player_idle);
            binding.textExpandedTitle.setText(R.string.mini_player_idle);
            binding.textExpandedArtist.setText(R.string.no_artist);
            binding.textPlaybackSource.setText("");
            binding.textCurrentTime.setText(R.string.player_time_zero);
            binding.textTotalDuration.setText(R.string.player_time_zero);
            binding.seekBarProgress.setMax(100);
            binding.seekBarProgress.setProgress(0);
            binding.seekBarProgress.setEnabled(false);
            binding.imageAlbumCover.setTag(null);
            binding.imageAlbumCover.setImageResource(R.drawable.ic_player_placeholder);
        } else {
            binding.textCollapsedTitle.setText(currentTrack.getTitle());
            binding.textExpandedTitle.setText(currentTrack.getTitle());

            String artist = currentTrack.getArtist();
            binding.textExpandedArtist.setText(
                    artist == null || artist.trim().isEmpty()
                            ? getString(R.string.no_artist)
                            : artist
            );

            binding.textPlaybackSource.setText(resolvePlaybackSource());
            binding.seekBarProgress.setEnabled(true);
            loadCoverImage(currentTrack);
        }

        int playPauseText = isPlaying ? R.string.pause : R.string.play;
        binding.buttonCollapsedPlayPause.setText(playPauseText);
        binding.buttonExpandedPlayPause.setText(playPauseText);
        binding.buttonFavorite.setText(
                currentTrack != null && currentTrack.isFavorite()
                        ? R.string.player_action_favorite_on_symbol
                        : R.string.player_action_favorite_off_symbol
        );

        binding.buttonDownload.setEnabled(currentTrack != null && !currentTrack.isDownloaded());
        if (currentTrack != null && currentTrack.isDownloaded()) {
            binding.buttonDownload.setText(R.string.player_action_downloaded_symbol);
        } else {
            binding.buttonDownload.setText(R.string.player_action_download_symbol);
        }

        updateProgressFromActivity();
    }

    public void updatePlayerState(Track track, boolean isPlaying) {
        this.currentTrack = track;
        this.isPlaying = isPlaying;
        renderState();
    }

    public void updatePlaybackProgress(long positionMs, long durationMs) {
        if (binding == null || isSeekInProgress) {
            return;
        }

        int safeDuration = (int) Math.max(durationMs, 1L);
        int safePosition = (int) Math.max(0L, Math.min(positionMs, durationMs));
        binding.seekBarProgress.setMax(safeDuration);
        binding.seekBarProgress.setProgress(safePosition);
        binding.textCurrentTime.setText(formatTime(positionMs));
        binding.textTotalDuration.setText(formatTime(durationMs));
    }

    private void startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private void updateProgressFromActivity() {
        if (!(getActivity() instanceof BasePlayerActivity)) {
            return;
        }

        BasePlayerActivity activity = (BasePlayerActivity) getActivity();
        updatePlaybackProgress(activity.getPlaybackPosition(), activity.getPlaybackDuration());
    }

    private String resolvePlaybackSource() {
        if (getActivity() instanceof PlaylistDetailsActivity) {
            boolean isFavorites = requireActivity()
                    .getIntent()
                    .getBooleanExtra(PlaylistListActivity.EXTRA_IS_FAVORITES, false);
            if (isFavorites) {
                return getString(R.string.player_source_favorites);
            }

            String playlistName = requireActivity()
                    .getIntent()
                    .getStringExtra(PlaylistListActivity.EXTRA_PLAYLIST_NAME);
            if (playlistName != null && !playlistName.trim().isEmpty()) {
                return getString(R.string.player_source_playlist, playlistName);
            }
        }

        if (getActivity() instanceof DownloadedActivity) {
            return getString(R.string.player_source_downloaded);
        }

        if (getActivity() instanceof LocalMusicActivity) {
            return getString(R.string.player_source_local_music);
        }

        if (getActivity() instanceof MainActivity
                && currentTrack != null
                && "JAMENDO".equals(currentTrack.getSourceType())) {
            return getString(R.string.player_source_wave);
        }

        return "";
    }

    private void loadCoverImage(Track track) {
        binding.imageAlbumCover.setImageResource(R.drawable.ic_player_placeholder);

        if (track == null) {
            binding.imageAlbumCover.setTag(null);
            return;
        }

        String imageUrl = track.getImageUrl();
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            binding.imageAlbumCover.setTag(null);
            return;
        }

        binding.imageAlbumCover.setTag(imageUrl);

        Uri uri = Uri.parse(imageUrl);
        String scheme = uri.getScheme();
        if (scheme != null
                && ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme))) {
            binding.imageAlbumCover.setImageURI(uri);
            return;
        }

        new Thread(() -> {
            Bitmap bitmap = null;
            try (InputStream inputStream = new URL(imageUrl).openStream()) {
                bitmap = BitmapFactory.decodeStream(inputStream);
            } catch (Exception ignored) {
            }

            Bitmap finalBitmap = bitmap;
            if (!isAdded()) {
                return;
            }

            binding.imageAlbumCover.post(() -> {
                if (binding == null || !imageUrl.equals(binding.imageAlbumCover.getTag())) {
                    return;
                }

                if (finalBitmap != null) {
                    binding.imageAlbumCover.setImageBitmap(finalBitmap);
                } else {
                    binding.imageAlbumCover.setImageResource(R.drawable.ic_player_placeholder);
                }
            });
        }).start();
    }

    private String formatTime(long durationMs) {
        long safeDurationMs = Math.max(durationMs, 0L);
        long totalSeconds = safeDurationMs / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    public void onDestroyView() {
        stopProgressUpdates();
        binding = null;
        super.onDestroyView();
    }

    public interface PlayerControlListener {
        void onPlayPauseClicked();

        void onFavoriteClicked();

        void onAddToPlaylistClicked();

        void onDownloadClicked();

        void onPreviousClicked();

        void onNextClicked();

        void onSeekTo(long positionMs);
    }
}
