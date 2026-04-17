package com.example.mymusicplayer.ui.playlist;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Playlist;
import com.example.mymusicplayer.databinding.ActivityPlaylistListBinding;
import com.example.mymusicplayer.ui.library.LibraryViewModel;
import com.example.mymusicplayer.ui.main.adapter.PlaylistAdapter;
import com.example.mymusicplayer.ui.player.BasePlayerActivity;

public class PlaylistListActivity extends BasePlayerActivity {

    public static final String EXTRA_PLAYLIST_ID = "playlist_id";
    public static final String EXTRA_PLAYLIST_NAME = "playlist_name";
    public static final String EXTRA_IS_FAVORITES = "is_favorites";

    private ActivityPlaylistListBinding binding;
    private LibraryViewModel viewModel;
    private PlaylistAdapter playlistAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaylistListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        playlistAdapter = new PlaylistAdapter(new PlaylistAdapter.OnPlaylistActionListener() {
            @Override
            public void onPlaylistClick(Playlist playlist) {
                openPlaylistDetails(playlist);
            }

            @Override
            public void onPlaylistDelete(Playlist playlist) {
                showDeletePlaylistDialog(playlist);
            }
        });

        binding.recyclerPlaylists.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPlaylists.setAdapter(playlistAdapter);
        setupSwipeRefresh(binding.swipeRefresh);
        setupPlayerUi(binding.miniPlayer, binding.textMiniPlayerTitle, binding.buttonMiniPlayerAction);
        refreshNetworkState(false);
        binding.buttonBack.setOnClickListener(view -> finish());

        binding.buttonOpenFavorites.setOnClickListener(view -> {
            if (requireOnlineFeature()) {
                openFavorites();
            }
        });
        binding.buttonCreatePlaylist.setOnClickListener(view -> {
            if (requireOnlineFeature()) {
                showCreatePlaylistDialog();
            }
        });

        viewModel.getPlaylists().observe(this, playlists -> {
            if (!isNetworkAvailable()) {
                playlistAdapter.submitList(null);
                binding.textEmpty.setVisibility(android.view.View.VISIBLE);
                binding.textEmpty.setText(R.string.message_feature_online_only);
                return;
            }

            playlistAdapter.submitList(playlists);
            binding.textEmpty.setVisibility(
                    playlists == null || playlists.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE
            );
        });
    }

    private void openFavorites() {
        Intent intent = new Intent(this, PlaylistDetailsActivity.class);
        intent.putExtra(EXTRA_IS_FAVORITES, true);
        intent.putExtra(EXTRA_PLAYLIST_NAME, getString(R.string.title_favorites));
        startActivity(intent);
    }

    private void openPlaylistDetails(Playlist playlist) {
        if (!requireOnlineFeature()) {
            return;
        }

        Intent intent = new Intent(this, PlaylistDetailsActivity.class);
        intent.putExtra(EXTRA_PLAYLIST_ID, playlist.getPlaylistId());
        intent.putExtra(EXTRA_PLAYLIST_NAME, playlist.getName());
        startActivity(intent);
    }

    private void showCreatePlaylistDialog() {
        EditText input = new EditText(this);
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding);
        input.setPadding(padding, padding, padding, padding);
        input.setHint(R.string.hint_playlist_name);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_create_playlist_title)
                .setView(input)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    viewModel.createPlaylist(name);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showDeletePlaylistDialog(Playlist playlist) {
        if (!requireOnlineFeature()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_playlist_title)
                .setMessage(getString(R.string.dialog_delete_playlist_message, playlist.getName()))
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        viewModel.deletePlaylist(playlist.getPlaylistId())
                )
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    protected void onNetworkAvailabilityChanged(boolean isNetworkAvailable) {
        if (binding == null) {
            return;
        }

        binding.buttonOpenFavorites.setEnabled(isNetworkAvailable);
        binding.buttonCreatePlaylist.setEnabled(isNetworkAvailable);
        binding.recyclerPlaylists.setAlpha(isNetworkAvailable ? 1f : 0.5f);
        if (!isNetworkAvailable) {
            playlistAdapter.submitList(null);
            binding.textEmpty.setVisibility(android.view.View.VISIBLE);
            binding.textEmpty.setText(R.string.message_feature_online_only);
            binding.recyclerPlaylists.setEnabled(false);
            binding.buttonOpenFavorites.setClickable(false);
            binding.buttonCreatePlaylist.setClickable(false);
            return;
        }

        binding.recyclerPlaylists.setEnabled(true);
        binding.buttonOpenFavorites.setClickable(true);
        binding.buttonCreatePlaylist.setClickable(true);
    }

    private boolean requireOnlineFeature() {
        if (isNetworkAvailable()) {
            return true;
        }

        Toast.makeText(this, R.string.message_feature_online_only, Toast.LENGTH_SHORT).show();
        return false;
    }
}
