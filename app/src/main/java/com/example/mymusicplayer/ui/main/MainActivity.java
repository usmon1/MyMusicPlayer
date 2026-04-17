package com.example.mymusicplayer.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.databinding.ActivityMainBinding;
import com.example.mymusicplayer.playback.MusicService;
import com.example.mymusicplayer.ui.downloaded.DownloadedActivity;
import com.example.mymusicplayer.ui.local.LocalMusicActivity;
import com.example.mymusicplayer.ui.main.adapter.TrackAdapter;
import com.example.mymusicplayer.ui.player.BasePlayerActivity;
import com.example.mymusicplayer.ui.playlist.PlaylistListActivity;
import com.example.mymusicplayer.ui.search.SearchActivity;
import com.example.mymusicplayer.util.PlaybackAccessHelper;
import com.example.mymusicplayer.util.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BasePlayerActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private TrackAdapter recentAdapter;
    private List<Track> recentTracksCache = new ArrayList<>();
    private Track currentWaveTrack;
    private boolean isWavePlaybackPending;
    private boolean shouldForceOfflineMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        shouldForceOfflineMode = getIntent().getBooleanExtra(EXTRA_FORCE_OFFLINE_MODE, false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupInsets();
        setupViewModel();
        setupTopBar();
        setupNavigationCards();
        setupWaveBlock();
        setupRecyclerViews();
        setupSwipeRefresh(binding.swipeRefresh);
        setupPlayerUi(binding.miniPlayer, binding.textMiniPlayerTitle, binding.buttonMiniPlayerAction);
        observeViewModel();
        applyInitialNetworkState();
        loadData();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        shouldForceOfflineMode = intent != null
                && intent.getBooleanExtra(EXTRA_FORCE_OFFLINE_MODE, false);
        if (binding != null && viewModel != null) {
            applyInitialNetworkState();
        }
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.setNetworkAvailable(isNetworkAvailable());
    }

    private void setupTopBar() {
        binding.buttonTheme.setOnClickListener(view -> {
            ThemeManager.toggleTheme(this);
            recreate();
        });
        binding.buttonSearch.setOnClickListener(view -> {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, R.string.message_feature_online_only, Toast.LENGTH_SHORT).show();
                return;
            }

            startActivity(new Intent(this, SearchActivity.class));
        });
        updateThemeButton();
    }

    private void setupNavigationCards() {
        binding.cardPlaylists.setOnClickListener(view -> {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, R.string.message_feature_online_only, Toast.LENGTH_SHORT).show();
                return;
            }

            startActivity(new Intent(this, PlaylistListActivity.class));
        });

        binding.cardLocalMusic.setOnClickListener(view ->
                startActivity(new Intent(this, LocalMusicActivity.class))
        );

        binding.cardDownloaded.setOnClickListener(view ->
                startActivity(new Intent(this, DownloadedActivity.class))
        );
    }

    private void setupRecyclerViews() {
        recentAdapter = new TrackAdapter(track -> playTrackWithSource(
                track,
                MusicService.PLAYBACK_SOURCE_RECENT,
                getString(R.string.player_source_recent)
        ));
        binding.recyclerRecent.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRecent.setAdapter(recentAdapter);
        binding.recyclerRecent.setNestedScrollingEnabled(false);
    }

    private void setupWaveBlock() {
        binding.buttonWaveAction.setOnClickListener(view -> handleWaveAction());
    }

    private void observeViewModel() {
        viewModel.getRandomTracks().observe(this, tracks -> {
            Log.d(TAG, "Wave observer update: tracks=" + (tracks == null ? -1 : tracks.size())
                    + ", pending=" + isWavePlaybackPending);
            if (currentWaveTrack == null) {
                currentWaveTrack = viewModel.getCurrentWaveTrack();
            }

            if (isWavePlaybackPending && tracks != null && !tracks.isEmpty()) {
                Track waveTrackToPlay = viewModel.getWaveTrackForPlayback();
                if (waveTrackToPlay != null) {
                    isWavePlaybackPending = false;
                    currentWaveTrack = waveTrackToPlay;
                    playTrack(waveTrackToPlay);
                    return;
                }
            }

            updateWaveUi();
            notifyPlaybackQueueChanged();
        });

        viewModel.getIsWaveLoading().observe(this, loading -> {
            Log.d(TAG, "Wave loading state changed: loading=" + loading
                    + ", hasWaveTracks=" + viewModel.hasWaveTracks()
                    + ", pending=" + isWavePlaybackPending);
            if (Boolean.FALSE.equals(loading)
                    && isWavePlaybackPending
                    && !viewModel.hasWaveTracks()) {
                isWavePlaybackPending = false;
                Toast.makeText(this, R.string.message_wave_empty, Toast.LENGTH_SHORT).show();
            }

            updateWaveUi();
        });
        viewModel.getRecentTracks().observe(this, tracks -> {
            recentTracksCache = tracks == null ? new ArrayList<>() : new ArrayList<>(tracks);
            renderRecentTracks();
            notifyPlaybackQueueChanged();
        });
    }

    private void loadData() {
        viewModel.loadRandomTracks();
        viewModel.loadRecentTracks();
    }

    private void handleWaveAction() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, R.string.message_feature_online_only, Toast.LENGTH_SHORT).show();
            updateWaveUi();
            return;
        }

        Log.d(TAG, "Wave action clicked: loading=" + viewModel.getIsWaveLoading().getValue()
                + ", hasWaveTracks=" + viewModel.hasWaveTracks());
        Track playingTrack = getCurrentTrack();
        currentWaveTrack = getWaveTrackForUi();
        boolean isCurrentWaveTrack = isWavePlaybackContext()
                && currentWaveTrack != null
                && playingTrack != null
                && currentWaveTrack.getId().equals(playingTrack.getId());

        if (isCurrentWaveTrack && playingTrack != null) {
            onPlayPauseClicked();
            updateWaveUi();
            return;
        }

        Track waveTrackToPlay = viewModel.getWaveTrackForPlayback();
        Log.d(TAG, "Wave track for playback result="
                + (waveTrackToPlay == null ? "null" : waveTrackToPlay.getId()));
        if (waveTrackToPlay == null) {
            if (Boolean.TRUE.equals(viewModel.getIsWaveLoading().getValue())) {
                isWavePlaybackPending = true;
                Log.d(TAG, "Wave playback marked as pending");
            } else {
                Toast.makeText(this, R.string.message_wave_empty, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Wave empty toast shown");
            }
            return;
        }

        isWavePlaybackPending = false;
        currentWaveTrack = waveTrackToPlay;
        playTrackWithSource(
                currentWaveTrack,
                MusicService.PLAYBACK_SOURCE_WAVE,
                getString(R.string.player_source_wave)
        );
        updateWaveUi();
    }

    private void updateWaveUi() {
        if (!isNetworkAvailable()) {
            binding.textWaveSubtitle.setText(R.string.wave_subtitle_offline);
            binding.buttonWaveAction.setEnabled(false);
            binding.buttonWaveAction.setText(R.string.offline_mode);
            binding.cardPlaylists.setAlpha(0.5f);
            return;
        }

        binding.cardPlaylists.setAlpha(1f);
        List<Track> availableWaveTracks = viewModel.getRandomTracks().getValue();
        boolean hasWaveTracks = availableWaveTracks != null && !availableWaveTracks.isEmpty();
        boolean isLoadingWave = Boolean.TRUE.equals(viewModel.getIsWaveLoading().getValue());

        if (!hasWaveTracks) {
            binding.textWaveSubtitle.setText(
                    isLoadingWave ? R.string.wave_subtitle_loading : R.string.wave_subtitle_idle
            );
            binding.buttonWaveAction.setEnabled(!isLoadingWave);
            binding.buttonWaveAction.setText(R.string.play);
            return;
        }

        binding.buttonWaveAction.setEnabled(true);

        Track playingTrack = getCurrentTrack();
        currentWaveTrack = getWaveTrackForUi();
        boolean isCurrentWaveTrack = isWavePlaybackContext()
                && currentWaveTrack != null
                && playingTrack != null
                && currentWaveTrack.getId().equals(playingTrack.getId());

        if (isCurrentWaveTrack && currentWaveTrack != null) {
            binding.textWaveSubtitle.setText(getString(
                    R.string.wave_subtitle_playing,
                    currentWaveTrack.getTitle()
            ));
            binding.buttonWaveAction.setText(isPlaying() ? R.string.pause : R.string.play);
        } else {
            binding.textWaveSubtitle.setText(R.string.wave_subtitle_idle);
            binding.buttonWaveAction.setText(R.string.play);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWaveUi();
    }

    @Override
    protected void onPlayerStateChanged(Track track, boolean isPlaying) {
        updateWaveUi();
    }

    @Override
    protected void onNetworkAvailabilityChanged(boolean isNetworkAvailable) {
        if (viewModel == null || binding == null) {
            return;
        }

        forceNetworkAvailability(isNetworkAvailable);
        viewModel.setNetworkAvailable(isNetworkAvailable);
        if (isNetworkAvailable && !viewModel.hasWaveTracks()) {
            viewModel.loadRandomTracks();
        }
        updateWaveUi();
        renderRecentTracks();
        binding.buttonSearch.setEnabled(isNetworkAvailable);
        binding.buttonSearch.setAlpha(isNetworkAvailable ? 1f : 0.5f);
    }

    private void applyInitialNetworkState() {
        if (shouldForceOfflineMode) {
            shouldForceOfflineMode = false;
            forceNetworkAvailability(false);
            onNetworkAvailabilityChanged(false);
            return;
        }

        refreshNetworkState(false);
    }

    @Override
    protected void onCurrentTrackChanged(Track track) {
        if (isWavePlaybackContext()) {
            viewModel.onWaveTrackStarted(track);
        }
        currentWaveTrack = getWaveTrackForUi();
    }

    @Override
    protected List<Track> getPlaybackQueue() {
        if (isWavePlaybackContext()) {
            List<Track> waveTracks = viewModel.getRandomTracks().getValue();
            return waveTracks == null ? new ArrayList<>() : new ArrayList<>(waveTracks);
        }

        return recentAdapter == null ? new ArrayList<>() : recentAdapter.getItems();
    }

    @Override
    protected String resolvePlaybackSource(@Nullable Track track) {
        String activePlaybackSource = getCurrentPlaybackSource();
        if (MusicService.PLAYBACK_SOURCE_WAVE.equals(activePlaybackSource)) {
            return MusicService.PLAYBACK_SOURCE_WAVE;
        }
        if (MusicService.PLAYBACK_SOURCE_RECENT.equals(activePlaybackSource)) {
            return MusicService.PLAYBACK_SOURCE_RECENT;
        }

        return viewModel.isCurrentWaveTrack(track)
                ? MusicService.PLAYBACK_SOURCE_WAVE
                : MusicService.PLAYBACK_SOURCE_RECENT;
    }

    @Override
    protected String resolvePlaybackSourceLabel(@Nullable Track track) {
        return getString(MusicService.PLAYBACK_SOURCE_WAVE.equals(resolvePlaybackSource(track))
                ? R.string.player_source_wave
                : R.string.player_source_recent);
    }

    @Override
    protected void onPlaybackCompleted() {
        if (!isWavePlaybackContext()) {
            return;
        }

        Track nextWaveTrack = viewModel.moveToNextWaveTrack();
        if (nextWaveTrack != null) {
            currentWaveTrack = nextWaveTrack;
            playTrack(nextWaveTrack);
        } else {
            updateWaveUi();
        }
    }

    @Override
    protected void playTrack(Track track) {
        if (MusicService.PLAYBACK_SOURCE_WAVE.equals(resolvePlaybackSource(track))) {
            viewModel.onWaveTrackStarted(track);
            currentWaveTrack = viewModel.getCurrentWaveTrack();
        }
        viewModel.onTrackClicked(track);
        super.playTrack(track);
        updateWaveUi();
    }

    @Override
    public void onPreviousClicked() {
        super.onPreviousClicked();
    }

    @Override
    public void onNextClicked() {
        super.onNextClicked();
    }

    private boolean isWavePlaybackContext() {
        return MusicService.PLAYBACK_SOURCE_WAVE.equals(getCurrentPlaybackSource());
    }

    private void renderRecentTracks() {
        if (recentAdapter == null) {
            return;
        }

        if (isNetworkAvailable()) {
            recentAdapter.submitList(new ArrayList<>(recentTracksCache));
            return;
        }

        List<Track> offlineRecentTracks = new ArrayList<>();
        for (Track track : recentTracksCache) {
            if (PlaybackAccessHelper.isOfflinePlayable(this, track)) {
                offlineRecentTracks.add(track);
            }
        }
        recentAdapter.submitList(offlineRecentTracks);
    }

    private void updateThemeButton() {
        binding.buttonTheme.setText(ThemeManager.isDarkTheme(this)
                ? R.string.theme_dark
                : R.string.theme_light);
    }

    @Nullable
    private Track getWaveTrackForUi() {
        if (!isWavePlaybackContext()) {
            return currentWaveTrack;
        }

        Track waveTrack = viewModel.getCurrentWaveTrack();
        return waveTrack != null ? waveTrack : currentWaveTrack;
    }
}
