package com.example.mymusicplayer.ui.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.databinding.FragmentPlayerBottomSheetBinding;
import com.example.mymusicplayer.playback.MusicService;
import com.google.android.material.button.MaterialButton;
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
    private static final long MOTION_SHORT_DURATION_MS = 140L;
    private static final long MOTION_MEDIUM_DURATION_MS = 220L;
    private static final float PRESSED_SCALE = 0.96f;

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
    private String playbackSource;
    private boolean isSeekInProgress;
    private String lastRenderedTrackId;
    private Boolean lastRenderedPlayState;

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
            bottomSheet.setAlpha(0f);
            bottomSheet.setTranslationY(36f);
            bottomSheet.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(MOTION_MEDIUM_DURATION_MS)
                    .start();
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
        binding.textExpandedTitle.setSelected(true);
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
        applyPressMotion(binding.buttonCollapsedPlayPause);
        applyPressMotion(binding.buttonExpandedPlayPause);
        applyPressMotion(binding.buttonFavorite);
        applyPressMotion(binding.buttonAddToPlaylist);
        applyPressMotion(binding.buttonDownload);
        applyPressMotion(binding.buttonPrevious);
        applyPressMotion(binding.buttonNext);
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

        String currentTrackId = currentTrack == null ? null : currentTrack.getId();
        boolean trackChanged = currentTrackId != null && !currentTrackId.equals(lastRenderedTrackId);
        boolean playStateChanged = lastRenderedPlayState != null && lastRenderedPlayState != isPlaying;

        if (currentTrack == null) {
            binding.textCollapsedTitle.setText(R.string.mini_player_idle);
            binding.textExpandedTitle.setText(R.string.mini_player_idle);
            binding.textExpandedTitle.setSelected(true);
            binding.textExpandedArtist.setText(R.string.no_artist);
            binding.textPlaybackSource.setText("");
            binding.textPlaybackSource.setVisibility(View.GONE);
            binding.textCurrentTime.setText(R.string.player_time_zero);
            binding.textTotalDuration.setText(R.string.player_time_zero);
            binding.seekBarProgress.setMax(100);
            binding.seekBarProgress.setProgress(0);
            binding.seekBarProgress.setEnabled(false);
            binding.imageAlbumCover.setTag(null);
            binding.imageAlbumCover.setImageResource(R.drawable.img);
        } else {
            binding.textCollapsedTitle.setText(currentTrack.getTitle());
            binding.textExpandedTitle.setText(currentTrack.getTitle());
            binding.textExpandedTitle.setSelected(true);

            String artist = currentTrack.getArtist();
            binding.textExpandedArtist.setText(
                    artist == null || artist.trim().isEmpty()
                            ? getString(R.string.no_artist)
                            : artist
            );

            binding.textPlaybackSource.setText(playbackSource == null ? "" : playbackSource);
            binding.textPlaybackSource.setVisibility(
                    TextUtils.isEmpty(playbackSource) ? View.GONE : View.VISIBLE
            );
            binding.seekBarProgress.setEnabled(true);
            loadCoverImage(currentTrack);
        }

        updatePlayPauseButton(binding.buttonCollapsedPlayPause, isPlaying);
        updatePlayPauseButton(binding.buttonExpandedPlayPause, isPlaying);
        binding.buttonCollapsedPlayPause.setEnabled(currentTrack != null);
        binding.buttonExpandedPlayPause.setEnabled(currentTrack != null);
        binding.buttonCollapsedPlayPause.setAlpha(currentTrack == null ? 0.5f : 1f);
        binding.buttonExpandedPlayPause.setAlpha(currentTrack == null ? 0.5f : 1f);
        animatePlaybackButton(binding.buttonCollapsedPlayPause, playStateChanged && currentTrack != null);
        animatePlaybackButton(binding.buttonExpandedPlayPause, playStateChanged && currentTrack != null);

        boolean isFavorite = currentTrack != null && currentTrack.isFavorite();
        binding.buttonFavorite.setIconResource(
                isFavorite ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline
        );
        binding.buttonFavorite.setContentDescription(getString(
                isFavorite ? R.string.action_favorite_remove : R.string.action_favorite_add
        ));
        binding.buttonFavorite.setEnabled(currentTrack != null);
        binding.buttonFavorite.setSelected(isFavorite);
        binding.buttonFavorite.setAlpha(currentTrack == null ? 0.5f : 1f);

        boolean isDownloaded = currentTrack != null && currentTrack.isDownloaded();
        boolean isLocalPlayback = MusicService.PLAYBACK_SOURCE_LOCAL.equals(playbackSource);
        binding.buttonDownload.setVisibility(isLocalPlayback ? View.GONE : View.VISIBLE);
        binding.buttonDownload.setEnabled(currentTrack != null && !isDownloaded && !isLocalPlayback);
        binding.buttonDownload.setSelected(isDownloaded);
        binding.buttonDownload.setAlpha(currentTrack == null ? 0.5f : (isDownloaded ? 0.7f : 1f));
        binding.buttonDownload.setIconResource(
                isDownloaded ? R.drawable.ic_download_done : R.drawable.ic_download_simple
        );
        binding.buttonDownload.setContentDescription(getString(
                isDownloaded ? R.string.action_downloaded : R.string.action_download
        ));

        updateNavigationState();

        updateProgressFromActivity();
        animateTrackState(trackChanged);
        lastRenderedTrackId = currentTrackId;
        lastRenderedPlayState = isPlaying;
    }

    public void updatePlayerState(Track track, boolean isPlaying, @Nullable String playbackSource) {
        this.currentTrack = track;
        this.isPlaying = isPlaying;
        this.playbackSource = playbackSource;
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
        updateNavigationState();
        updatePlaybackProgress(activity.getPlaybackPosition(), activity.getPlaybackDuration());
    }

    private void updateNavigationState() {
        if (binding == null) {
            return;
        }

        boolean canGoPrevious = false;
        boolean canGoNext = false;
        if (getActivity() instanceof BasePlayerActivity) {
            BasePlayerActivity activity = (BasePlayerActivity) getActivity();
            canGoPrevious = activity.canSkipToPrevious();
            canGoNext = activity.canSkipToNext();
        }

        binding.buttonPrevious.setEnabled(canGoPrevious);
        binding.buttonNext.setEnabled(canGoNext);
        binding.buttonPrevious.setAlpha(canGoPrevious ? 1f : 0.4f);
        binding.buttonNext.setAlpha(canGoNext ? 1f : 0.4f);
    }

    private void loadCoverImage(Track track) {
        binding.imageAlbumCover.setImageResource(R.drawable.img);

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
                    binding.imageAlbumCover.setImageResource(R.drawable.img);
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

    private void applyPressMotion(@Nullable View view) {
        if (view == null) {
            return;
        }

        view.setOnTouchListener((pressedView, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    pressedView.animate()
                            .scaleX(PRESSED_SCALE)
                            .scaleY(PRESSED_SCALE)
                            .setDuration(MOTION_SHORT_DURATION_MS)
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
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

    private void animateTrackState(boolean trackChanged) {
        if (!trackChanged || binding == null) {
            return;
        }

        animateTrackView(binding.textPlaybackSource);
        animateTrackView(binding.textExpandedTitle);
        animateTrackView(binding.textExpandedArtist);

        binding.imageAlbumCover.setScaleX(0.94f);
        binding.imageAlbumCover.setScaleY(0.94f);
        binding.imageAlbumCover.setAlpha(0.55f);
        binding.imageAlbumCover.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(MOTION_MEDIUM_DURATION_MS)
                .start();
    }

    private void animateTrackView(@Nullable View view) {
        if (view == null) {
            return;
        }

        view.setAlpha(0.35f);
        view.setTranslationY(12f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(MOTION_MEDIUM_DURATION_MS)
                .start();
    }

    private void animatePlaybackButton(@Nullable View button, boolean shouldAnimate) {
        if (button == null || !shouldAnimate) {
            return;
        }

        button.animate().cancel();
        button.setScaleX(0.9f);
        button.setScaleY(0.9f);
        button.setAlpha(0.72f);
        button.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(MOTION_MEDIUM_DURATION_MS)
                .start();
    }

    private void updatePlayPauseButton(@Nullable MaterialButton button, boolean playing) {
        if (button == null) {
            return;
        }

        button.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        button.setContentDescription(getString(playing ? R.string.pause : R.string.play));
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
