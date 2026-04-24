package com.example.mymusicplayer.ui.main.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.databinding.ItemTrackBinding;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple adapter for displaying track title and artist.
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    private final List<Track> items = new ArrayList<>();
    private final OnTrackClickListener onTrackClickListener;
    private String activeTrackId;
    private boolean highlightActiveTrack;

    public TrackAdapter(OnTrackClickListener onTrackClickListener) {
        this.onTrackClickListener = onTrackClickListener;
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

    public void updatePlaybackState(@Nullable String trackId, boolean shouldHighlight) {
        boolean stateChanged = highlightActiveTrack != shouldHighlight
                || (activeTrackId == null ? trackId != null : !activeTrackId.equals(trackId));
        if (!stateChanged) {
            return;
        }

        activeTrackId = trackId;
        highlightActiveTrack = shouldHighlight;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemTrackBinding binding = ItemTrackBinding.inflate(inflater, parent, false);
        return new TrackViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class TrackViewHolder extends RecyclerView.ViewHolder {

        private final ItemTrackBinding binding;

        TrackViewHolder(ItemTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Track track) {
            binding.textTrackTitle.setText(track.getTitle());
            binding.textTrackArtist.setText(track.getArtist());
            boolean isActive = highlightActiveTrack
                    && track != null
                    && track.getId() != null
                    && track.getId().equals(activeTrackId);
            int primaryColor = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
            int surfaceVariantColor = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorSurfaceVariant);

            binding.getRoot().setStrokeWidth(isActive ? 4 : 0);
            binding.getRoot().setStrokeColor(isActive ? primaryColor : surfaceVariantColor);
            binding.viewPlayingIndicator.setAlpha(isActive ? 1f : 0f);
            binding.textTrackTitle.setTextColor(isActive ? primaryColor : MaterialColors.getColor(binding.textTrackTitle, android.R.attr.textColorPrimary));
            binding.textTrackArtist.setTextColor(MaterialColors.getColor(
                    binding.textTrackArtist,
                    isActive ? androidx.appcompat.R.attr.colorPrimary : android.R.attr.textColorSecondary
            ));
            binding.textNowPlaying.setVisibility(isActive ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.getRoot().setOnClickListener(view -> onTrackClickListener.onTrackClick(track));
        }
    }

    public interface OnTrackClickListener {
        void onTrackClick(Track track);
    }
}
