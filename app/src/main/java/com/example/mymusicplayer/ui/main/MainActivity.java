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
import com.example.mymusicplayer.ui.downloaded.DownloadedActivity;
import com.example.mymusicplayer.ui.local.LocalMusicActivity;
import com.example.mymusicplayer.ui.main.adapter.TrackAdapter;
import com.example.mymusicplayer.ui.player.BasePlayerActivity;
import com.example.mymusicplayer.ui.playlist.PlaylistListActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BasePlayerActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private TrackAdapter recentAdapter;
    private Track currentWaveTrack;
    private boolean isWavePlaybackPending;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupInsets();
        setupViewModel();
        setupNavigationCards();
        setupWaveBlock();
        setupRecyclerViews();
        setupPlayerUi(binding.miniPlayer, binding.textMiniPlayerTitle, binding.buttonMiniPlayerAction);
        observeViewModel();
        loadData();
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
    }

    private void setupNavigationCards() {
        binding.cardPlaylists.setOnClickListener(view ->
                startActivity(new Intent(this, PlaylistListActivity.class))
        );

        binding.cardLocalMusic.setOnClickListener(view ->
                startActivity(new Intent(this, LocalMusicActivity.class))
        );

        binding.cardDownloaded.setOnClickListener(view ->
                startActivity(new Intent(this, DownloadedActivity.class))
        );
    }

    private void setupRecyclerViews() {
        recentAdapter = new TrackAdapter(this::playTrack);
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
        viewModel.getRecentTracks().observe(this, tracks -> recentAdapter.submitList(tracks));
    }

    private void loadData() {
        viewModel.loadRandomTracks();
        viewModel.loadRecentTracks();
    }

    private void handleWaveAction() {
        Log.d(TAG, "Wave action clicked: loading=" + viewModel.getIsWaveLoading().getValue()
                + ", hasWaveTracks=" + viewModel.hasWaveTracks());
        Track playingTrack = getCurrentTrack();
        currentWaveTrack = viewModel.getCurrentWaveTrack();
        boolean isCurrentWaveTrack = viewModel.isCurrentWaveTrack(playingTrack);

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
        playTrack(currentWaveTrack);
        updateWaveUi();
    }

    private void updateWaveUi() {
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
        currentWaveTrack = viewModel.getCurrentWaveTrack();
        boolean isCurrentWaveTrack = viewModel.isCurrentWaveTrack(playingTrack);

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
    protected List<Track> getPlaybackQueue() {
        Track currentTrack = getCurrentTrack();
        if (viewModel.isCurrentWaveTrack(currentTrack)) {
            List<Track> waveTracks = viewModel.getRandomTracks().getValue();
            return waveTracks == null ? new ArrayList<>() : new ArrayList<>(waveTracks);
        }

        return recentAdapter == null ? new ArrayList<>() : recentAdapter.getItems();
    }

    @Override
    protected void onPlaybackCompleted() {
        Track currentTrack = getCurrentTrack();
        if (!viewModel.isCurrentWaveTrack(currentTrack)) {
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
        viewModel.onWaveTrackStarted(track);
        currentWaveTrack = viewModel.getCurrentWaveTrack();
        viewModel.onTrackClicked(track);
        super.playTrack(track);
        updateWaveUi();
    }

    @Override
    public void onPreviousClicked() {
        Track currentTrack = getCurrentTrack();
        if (viewModel.isCurrentWaveTrack(currentTrack)) {
            Track previousWaveTrack = viewModel.moveToPreviousWaveTrack();
            if (previousWaveTrack != null) {
                currentWaveTrack = previousWaveTrack;
                playTrack(previousWaveTrack);
            }
            return;
        }

        super.onPreviousClicked();
    }

    @Override
    public void onNextClicked() {
        Track currentTrack = getCurrentTrack();
        if (viewModel.isCurrentWaveTrack(currentTrack)) {
            Track nextWaveTrack = viewModel.moveToNextWaveTrack();
            if (nextWaveTrack != null) {
                currentWaveTrack = nextWaveTrack;
                playTrack(nextWaveTrack);
            }
            return;
        }

        super.onNextClicked();
    }
}
