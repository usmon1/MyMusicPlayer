package com.example.mymusicplayer.ui.main.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.databinding.ItemManageTrackBinding;

import java.util.ArrayList;
import java.util.List;

public class ManageTrackAdapter extends RecyclerView.Adapter<ManageTrackAdapter.ManageTrackViewHolder> {

    private final List<Track> items = new ArrayList<>();
    private final String actionLabel;
    private final OnTrackActionListener onTrackActionListener;

    public ManageTrackAdapter(String actionLabel, OnTrackActionListener onTrackActionListener) {
        this.actionLabel = actionLabel;
        this.onTrackActionListener = onTrackActionListener;
    }

    public void submitList(List<Track> tracks) {
        items.clear();
        if (tracks != null) {
            items.addAll(tracks);
        }
        notifyDataSetChanged();
    }

    public List<Track> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public ManageTrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemManageTrackBinding binding = ItemManageTrackBinding.inflate(inflater, parent, false);
        return new ManageTrackViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ManageTrackViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ManageTrackViewHolder extends RecyclerView.ViewHolder {

        private final ItemManageTrackBinding binding;

        ManageTrackViewHolder(ItemManageTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Track track) {
            binding.textTrackTitle.setText(track.getTitle());
            binding.textTrackArtist.setText(track.getArtist());
            binding.buttonTrackAction.setText(actionLabel);
            binding.getRoot().setOnClickListener(view -> onTrackActionListener.onTrackClick(track));
            binding.buttonTrackAction.setOnClickListener(view -> onTrackActionListener.onTrackAction(track));
        }
    }

    public interface OnTrackActionListener {
        void onTrackClick(Track track);

        void onTrackAction(Track track);
    }
}
