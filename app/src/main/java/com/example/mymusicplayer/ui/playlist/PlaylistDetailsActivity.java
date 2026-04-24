package com.example.mymusicplayer.ui.playlist;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.databinding.ActivityPlaylistDetailsBinding;
import com.example.mymusicplayer.playback.MusicService;
import com.example.mymusicplayer.ui.main.adapter.ManageTrackAdapter;
import com.example.mymusicplayer.ui.player.BasePlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class PlaylistDetailsActivity extends BasePlayerActivity {

    private ActivityPlaylistDetailsBinding binding;
    private PlaylistDetailsViewModel viewModel;
    private ManageTrackAdapter trackAdapter;
    private LiveData<List<Track>> tracksSource;
    private boolean isFavorites;
    private int playlistId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaylistDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(PlaylistDetailsViewModel.class);

        isFavorites = getIntent().getBooleanExtra(PlaylistListActivity.EXTRA_IS_FAVORITES, false);
        playlistId = getIntent().getIntExtra(PlaylistListActivity.EXTRA_PLAYLIST_ID, 0);
        String title = getIntent().getStringExtra(PlaylistListActivity.EXTRA_PLAYLIST_NAME);

        binding.textTitle.setText(title == null || title.trim().isEmpty()
                ? getString(R.string.title_playlists)
                : title);

        trackAdapter = new ManageTrackAdapter(
                getString(R.string.action_remove),
                new ManageTrackAdapter.OnTrackActionListener() {
                    @Override
                    public void onTrackClick(Track track) {
                        playTrack(track);
                    }

                    @Override
                    public void onTrackAction(Track track) {
                        removeTrack(track);
                    }
                }
        );

        binding.recyclerTracks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTracks.setAdapter(trackAdapter);
        setupSwipeRefresh(binding.swipeRefresh);
        setupPlayerUi(binding.miniPlayer, binding.textMiniPlayerTitle, binding.buttonMiniPlayerAction);
        refreshNetworkState(false);
        applyPressMotion(binding.buttonBack);
        binding.buttonBack.setOnClickListener(view -> finishScreen());

        observeTracks();
    }

    private void observeTracks() {
        if (tracksSource != null) {
            tracksSource.removeObservers(this);
        }

        if (isFavorites) {
            tracksSource = viewModel.getFavoriteTracks();
            binding.textEmpty.setText(R.string.empty_favorites);
        } else {
            tracksSource = viewModel.getTracksFromPlaylist(playlistId);
            binding.textEmpty.setText(R.string.empty_playlist_tracks);
        }

        tracksSource.observe(this, tracks -> {
            if (!isNetworkAvailable()) {
                trackAdapter.submitList(new ArrayList<>());
                updateTrackHighlight();
                binding.textEmpty.setVisibility(android.view.View.VISIBLE);
                binding.textEmpty.setText(R.string.message_feature_online_only);
                return;
            }

            trackAdapter.submitList(tracks);
            updateTrackHighlight();
            binding.textEmpty.setVisibility(
                    tracks == null || tracks.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE
            );
            notifyPlaybackQueueChanged();
        });
    }

    private void removeTrack(Track track) {
        if (!isNetworkAvailable()) {
            return;
        }

        if (isFavorites) {
            viewModel.removeFromFavorites(track);
        } else {
            viewModel.removeFromPlaylist(playlistId, track);
        }
    }

    @Override
    protected List<Track> getPlaybackQueue() {
        return trackAdapter == null ? new ArrayList<>() : trackAdapter.getItems();
    }

    @Override
    protected String resolvePlaybackSource(@Nullable Track track) {
        return isFavorites
                ? MusicService.PLAYBACK_SOURCE_FAVORITES
                : MusicService.PLAYBACK_SOURCE_PLAYLIST;
    }

    @Override
    protected String resolvePlaybackSourceLabel(@Nullable Track track) {
        if (isFavorites) {
            return getString(R.string.player_source_favorites);
        }

        String playlistName = getIntent().getStringExtra(PlaylistListActivity.EXTRA_PLAYLIST_NAME);
        if (playlistName == null || playlistName.trim().isEmpty()) {
            return getString(R.string.title_playlists);
        }

        return getString(R.string.player_source_playlist, playlistName);
    }

    @Override
    protected boolean ownsPlaybackSource(@Nullable String playbackSource) {
        String screenPlaybackSource = resolvePlaybackSource(getCurrentTrack());
        if (screenPlaybackSource == null || !screenPlaybackSource.equals(playbackSource)) {
            return false;
        }

        String activeLabel = getCurrentPlaybackSourceLabel();
        String screenLabel = resolvePlaybackSourceLabel(getCurrentTrack());
        if (activeLabel == null || screenLabel == null) {
            return false;
        }

        return activeLabel.equals(screenLabel);
    }

    @Override
    protected void onNetworkAvailabilityChanged(boolean isNetworkAvailable) {
        if (binding == null || trackAdapter == null) {
            return;
        }

        if (!isNetworkAvailable) {
            trackAdapter.submitList(new ArrayList<>());
            updateTrackHighlight();
            binding.textEmpty.setVisibility(android.view.View.VISIBLE);
            binding.textEmpty.setText(R.string.message_feature_online_only);
            return;
        }

        observeTracks();
    }

    @Override
    protected void onPlayerStateChanged(Track track, boolean isPlaying) {
        updateTrackHighlight();
    }

    private void updateTrackHighlight() {
        if (trackAdapter == null) {
            return;
        }

        Track currentTrack = getCurrentTrack();
        trackAdapter.updatePlaybackState(
                currentTrack == null ? null : currentTrack.getId(),
                ownsPlaybackSource(getCurrentPlaybackSource())
        );
    }
}
