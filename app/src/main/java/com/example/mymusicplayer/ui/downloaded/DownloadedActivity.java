package com.example.mymusicplayer.ui.downloaded;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.databinding.ActivityDownloadedBinding;
import com.example.mymusicplayer.playback.MusicService;
import com.example.mymusicplayer.ui.library.LibraryViewModel;
import com.example.mymusicplayer.ui.main.adapter.ManageTrackAdapter;
import com.example.mymusicplayer.ui.player.BasePlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class DownloadedActivity extends BasePlayerActivity {

    private ActivityDownloadedBinding binding;
    private LibraryViewModel viewModel;
    private ManageTrackAdapter trackAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDownloadedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        trackAdapter = new ManageTrackAdapter(
                getString(R.string.action_remove_download),
                new ManageTrackAdapter.OnTrackActionListener() {
                    @Override
                    public void onTrackClick(com.example.mymusicplayer.data.local.entity.Track track) {
                        playTrack(track);
                    }

                    @Override
                    public void onTrackAction(com.example.mymusicplayer.data.local.entity.Track track) {
                        viewModel.removeDownload(track);
                    }
                }
        );

        binding.recyclerTracks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTracks.setAdapter(trackAdapter);
        setupSwipeRefresh(binding.swipeRefresh);
        setupPlayerUi(binding.miniPlayer, binding.textMiniPlayerTitle, binding.buttonMiniPlayerAction);
        refreshNetworkState(false);
        binding.buttonBack.setOnClickListener(view -> finish());

        viewModel.getDownloadedTracks().observe(this, tracks -> {
            trackAdapter.submitList(tracks);
            binding.textEmpty.setVisibility(
                    tracks == null || tracks.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE
            );
            notifyPlaybackQueueChanged();
        });
    }

    @Override
    protected List<com.example.mymusicplayer.data.local.entity.Track> getPlaybackQueue() {
        return trackAdapter == null ? new ArrayList<>() : trackAdapter.getItems();
    }

    @Override
    protected String resolvePlaybackSource(@Nullable com.example.mymusicplayer.data.local.entity.Track track) {
        return MusicService.PLAYBACK_SOURCE_DOWNLOADED;
    }

    @Override
    protected String resolvePlaybackSourceLabel(@Nullable com.example.mymusicplayer.data.local.entity.Track track) {
        return getString(R.string.player_source_downloaded);
    }

    @Override
    protected boolean shouldNavigateToMainOnOfflineRefresh() {
        return false;
    }
}
