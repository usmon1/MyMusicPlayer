package com.example.mymusicplayer.ui.playlist;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.databinding.ActivityPlaylistDetailsBinding;
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
        setupPlayerUi(binding.miniPlayer, binding.textMiniPlayerTitle, binding.buttonMiniPlayerAction);

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
            trackAdapter.submitList(tracks);
            binding.textEmpty.setVisibility(
                    tracks == null || tracks.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE
            );
        });
    }

    private void removeTrack(Track track) {
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
}
