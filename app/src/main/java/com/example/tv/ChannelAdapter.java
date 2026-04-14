package com.example.tv;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private List<Channel> channelList;
    private OnChannelClickListener listener;

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    public ChannelAdapter(List<Channel> channelList, OnChannelClickListener listener) {
        this.channelList = channelList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.channel_item, parent, false);
        return new ViewHolder(view);
    }

    private int[] colors = {
            Color.parseColor("#D81B60"), // Pink
            Color.parseColor("#00E5FF"), // Cyan
            Color.parseColor("#4CAF50"), // Green
            Color.parseColor("#FFC107"), // Yellow
            Color.parseColor("#2196F3"), // Blue
            Color.parseColor("#9C27B0")  // Purple
    };

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = channelList.get(position);
        holder.name.setText(channel.getName());

        Glide.with(holder.itemView.getContext())
                .load(channel.getLogoUrl())
                .override(200, 200)
                .placeholder(R.drawable.app_icon)
                .error(R.drawable.app_icon)
                .into(holder.logo);

        // লোগো এরিয়া শুধুমাত্র সাদা ব্যাকগ্রাউন্ড (কোনো বর্ডার থাকবে না)
        holder.container.setBackgroundColor(Color.WHITE);

        // চ্যানেলের নামের অংশের ব্যাকগ্রাউন্ড কালার (Premium Deep Navy Blue)
        holder.nameBgArea.setBackgroundColor(Color.parseColor("#050A30"));

        holder.name.setTextColor(Color.WHITE);

        holder.itemView.setOnClickListener(v -> listener.onChannelClick(channel));
    }

    @Override
    public int getItemCount() {
        return channelList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        ImageView logo;
        View container;
        RelativeLayout nameBgArea;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.channel_name);
            logo = itemView.findViewById(R.id.channel_logo);
            container = itemView.findViewById(R.id.channel_container);
            nameBgArea = itemView.findViewById(R.id.name_bg_area);
        }
    }
}
