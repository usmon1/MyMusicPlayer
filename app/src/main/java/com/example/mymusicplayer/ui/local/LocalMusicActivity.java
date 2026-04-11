package com.example.mymusicplayer.ui.local;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mymusicplayer.databinding.ActivityLocalMusicBinding;
import com.example.mymusicplayer.ui.library.LibraryViewModel;
import com.example.mymusicplayer.ui.main.adapter.TrackAdapter;
import com.example.mymusicplayer.ui.player.BasePlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class LocalMusicActivity extends BasePlayerActivity {

    private ActivityLocalMusicBinding binding;
    private LibraryViewModel viewModel;
    private TrackAdapter trackAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocalMusicBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        trackAdapter = new TrackAdapter(this::playTrack);

        binding.recyclerTracks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTracks.setAdapter(trackAdapter);
        setupPlayerUi(binding.miniPlayer, binding.textMiniPlayerTitle, binding.buttonMiniPlayerAction);

        viewModel.getLocalTracks().observe(this, tracks -> {
            trackAdapter.submitList(tracks);
            binding.textEmpty.setVisibility(
                    tracks == null || tracks.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE
            );
        });

        viewModel.loadLocalTracks();
    }

    @Override
    protected List<com.example.mymusicplayer.data.local.entity.Track> getPlaybackQueue() {
        return trackAdapter == null ? new ArrayList<>() : trackAdapter.getItems();
    }
}
