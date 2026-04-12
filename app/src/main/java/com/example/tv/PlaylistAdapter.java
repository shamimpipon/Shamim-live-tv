package com.example.tv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {

    private List<PlaylistModel> playlistList;
    private OnPlaylistClickListener listener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(PlaylistModel playlist);
        void onPlaylistDelete(int position);
        void onPlaylistEdit(int position, PlaylistModel playlist);
    }

    public PlaylistAdapter(List<PlaylistModel> playlistList, OnPlaylistClickListener listener) {
        this.playlistList = playlistList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.playlist_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaylistModel playlist = playlistList.get(position);
        holder.name.setText(playlist.getName());
        holder.url.setText(playlist.getUrl());

        holder.itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
        
        holder.btnEdit.setOnClickListener(v -> listener.onPlaylistEdit(position, playlist));

        holder.btnDelete.setOnClickListener(v -> listener.onPlaylistDelete(position));
    }

    @Override
    public int getItemCount() {
        return playlistList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, url;
        ImageView btnDelete, btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvPlaylistName);
            url = itemView.findViewById(R.id.tvPlaylistUrl);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnEdit = itemView.findViewById(R.id.btnEdit);
        }
    }
}
