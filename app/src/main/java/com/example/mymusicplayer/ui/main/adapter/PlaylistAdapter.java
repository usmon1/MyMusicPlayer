package com.example.mymusicplayer.ui.main.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymusicplayer.data.local.entity.Playlist;
import com.example.mymusicplayer.databinding.ItemPlaylistBinding;

import java.util.ArrayList;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    private final List<Playlist> items = new ArrayList<>();
    private final OnPlaylistActionListener onPlaylistActionListener;

    public PlaylistAdapter(OnPlaylistActionListener onPlaylistActionListener) {
        this.onPlaylistActionListener = onPlaylistActionListener;
    }

    public void submitList(List<Playlist> playlists) {
        items.clear();
        if (playlists != null) {
            items.addAll(playlists);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemPlaylistBinding binding = ItemPlaylistBinding.inflate(inflater, parent, false);
        return new PlaylistViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {

        private final ItemPlaylistBinding binding;

        PlaylistViewHolder(ItemPlaylistBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Playlist playlist) {
            binding.textPlaylistName.setText(playlist.getName());
            binding.textPlaylistMeta.setText(binding.getRoot().getContext().getString(
                    com.example.mymusicplayer.R.string.playlist_item_meta
            ));
            binding.getRoot().setOnClickListener(view -> onPlaylistActionListener.onPlaylistClick(playlist));
            binding.buttonDeletePlaylist.setOnClickListener(view -> onPlaylistActionListener.onPlaylistDelete(playlist));
        }
    }

    public interface OnPlaylistActionListener {
        void onPlaylistClick(Playlist playlist);

        void onPlaylistDelete(Playlist playlist);
    }
}
