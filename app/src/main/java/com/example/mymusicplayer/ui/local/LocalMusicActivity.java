package com.example.mymusicplayer.ui.local;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.databinding.ActivityLocalMusicBinding;
import com.example.mymusicplayer.playback.MusicService;
import com.example.mymusicplayer.ui.library.LibraryViewModel;
import com.example.mymusicplayer.ui.main.adapter.TrackAdapter;
import com.example.mymusicplayer.ui.player.BasePlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class LocalMusicActivity extends BasePlayerActivity {

    private ActivityLocalMusicBinding binding;
    private LibraryViewModel viewModel;
    private TrackAdapter trackAdapter;
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    viewModel.loadLocalTracks();
                } else {
                    binding.textEmpty.setVisibility(android.view.View.VISIBLE);
                    binding.textEmpty.setText(R.string.local_music_permission_needed);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocalMusicBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        trackAdapter = new TrackAdapter(this::playTrack);

        binding.recyclerTracks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTracks.setAdapter(trackAdapter);
        setupSwipeRefresh(binding.swipeRefresh);
        setupPlayerUi(binding.miniPlayer, binding.textMiniPlayerTitle, binding.buttonMiniPlayerAction);
        refreshNetworkState(false);
        binding.buttonBack.setOnClickListener(view -> finish());

        viewModel.getLocalTracks().observe(this, tracks -> {
            trackAdapter.submitList(tracks);
            binding.textEmpty.setVisibility(
                    tracks == null || tracks.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE
            );
            notifyPlaybackQueueChanged();
        });

        ensureLocalMusicPermissionAndLoad();
    }

    @Override
    protected List<com.example.mymusicplayer.data.local.entity.Track> getPlaybackQueue() {
        return trackAdapter == null ? new ArrayList<>() : trackAdapter.getItems();
    }

    @Override
    protected String resolvePlaybackSource(@Nullable com.example.mymusicplayer.data.local.entity.Track track) {
        return MusicService.PLAYBACK_SOURCE_LOCAL;
    }

    @Override
    protected String resolvePlaybackSourceLabel(@Nullable com.example.mymusicplayer.data.local.entity.Track track) {
        return getString(com.example.mymusicplayer.R.string.player_source_local_music);
    }

    @Override
    protected boolean shouldNavigateToMainOnOfflineRefresh() {
        return false;
    }

    private void ensureLocalMusicPermissionAndLoad() {
        String permission = resolveAudioPermission();
        if (permission == null
                || ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadLocalTracks();
            return;
        }

        binding.textEmpty.setVisibility(android.view.View.VISIBLE);
        binding.textEmpty.setText(R.string.local_music_permission_needed);
        permissionLauncher.launch(permission);
    }

    @Nullable
    private String resolveAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_AUDIO;
        }

        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }
}
