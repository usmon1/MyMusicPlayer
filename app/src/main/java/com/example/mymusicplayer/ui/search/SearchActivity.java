package com.example.mymusicplayer.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.databinding.ActivitySearchBinding;
import com.example.mymusicplayer.playback.MusicService;
import com.example.mymusicplayer.ui.main.adapter.TrackAdapter;
import com.example.mymusicplayer.ui.player.BasePlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends BasePlayerActivity {

    private ActivitySearchBinding binding;
    private SearchViewModel viewModel;
    private TrackAdapter trackAdapter;
    private Track selectedTrack;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        trackAdapter = new TrackAdapter(this::playTrack);

        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerResults.setAdapter(trackAdapter);
        setupPlayerUi(binding.miniPlayer, binding.textMiniPlayerTitle, binding.buttonMiniPlayerAction);
        refreshNetworkState(false);

        ImageButton backButton = binding.buttonBack;
        applyPressMotion(backButton);
        backButton.setOnClickListener(view -> finishScreen());

        binding.inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.searchTracks(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        viewModel.getSearchResults().observe(this, tracks -> {
            trackAdapter.submitList(tracks);
            updateTrackHighlight();
            boolean isEmpty = tracks == null || tracks.isEmpty();
            binding.textEmpty.setText(
                    binding.inputSearch.getText() == null || binding.inputSearch.getText().toString().trim().isEmpty()
                            ? R.string.search_hint
                            : R.string.search_empty
            );
            binding.textEmpty.setVisibility(isEmpty ? android.view.View.VISIBLE : android.view.View.GONE);
            notifyPlaybackQueueChanged();
        });
    }

    @Override
    protected List<Track> getPlaybackQueue() {
        List<Track> queue = new ArrayList<>();
        if (selectedTrack != null) {
            queue.add(selectedTrack);
        }
        return queue;
    }

    @Override
    protected String resolvePlaybackSource(@Nullable Track track) {
        return MusicService.PLAYBACK_SOURCE_SEARCH;
    }

    @Override
    protected String resolvePlaybackSourceLabel(@Nullable Track track) {
        return "";
    }

    @Override
    protected void playTrack(Track track) {
        selectedTrack = track;
        playTrackWithSource(track, MusicService.PLAYBACK_SOURCE_SEARCH, "");
    }

    @Override
    public void onPreviousClicked() {
    }

    @Override
    public void onNextClicked() {
    }

    @Override
    protected void onNetworkAvailabilityChanged(boolean isNetworkAvailable) {
        forceNetworkAvailability(isNetworkAvailable);
        if (binding == null) {
            return;
        }

        if (!isNetworkAvailable) {
            finishScreen();
        }
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
                MusicService.PLAYBACK_SOURCE_SEARCH.equals(getCurrentPlaybackSource())
        );
    }
}
