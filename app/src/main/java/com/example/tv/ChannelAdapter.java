package com.example.tv;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private List<Channel> channelList;
    private OnChannelClickListener listener;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        holder.container.setBackgroundColor(Color.WHITE);
        holder.nameBgArea.setBackgroundColor(Color.parseColor("#050A30"));
        holder.name.setTextColor(Color.WHITE);

        // Update Status UI
        updateStatusUI(holder, channel);

        // Start checking if not checked yet
        if (channel.isChecking()) {
            checkChannelStatus(holder, channel);
        }

        holder.itemView.setOnClickListener(v -> listener.onChannelClick(channel));
    }

    private void updateStatusUI(ViewHolder holder, Channel channel) {
        if (channel.isChecking()) {
            holder.tvLiveStatus.setText("CHECK");
            holder.tvLiveStatus.getBackground().setTint(Color.GRAY);
        } else if (channel.isOnline()) {
            holder.tvLiveStatus.setText("LIVE");
            holder.tvLiveStatus.getBackground().setTint(Color.parseColor("#4CAF50")); // Green
        } else {
            holder.tvLiveStatus.setText("OFF");
            holder.tvLiveStatus.getBackground().setTint(Color.RED);
        }
    }

    private void checkChannelStatus(ViewHolder holder, Channel channel) {
        executorService.execute(() -> {
            boolean isOnline = false;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(channel.getUrl()).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                
                // শুধুমাত্র কানেকশন চেক করার জন্য প্রথম কয়েক বাইট পড়ার চেষ্টা করবে
                int responseCode = connection.getResponseCode();
                isOnline = (responseCode >= 200 && responseCode < 400);
                connection.disconnect();
            } catch (Exception e) {
                isOnline = false;
            }

            final boolean finalStatus = isOnline;
            mainHandler.post(() -> {
                channel.setOnline(finalStatus);
                // Only update if the holder is still showing the same channel
                if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                    Channel currentChannel = channelList.get(holder.getAdapterPosition());
                    if (currentChannel.getUrl().equals(channel.getUrl())) {
                        updateStatusUI(holder, channel);
                    }
                }
            });
        });
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
        TextView tvLiveStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.channel_name);
            logo = itemView.findViewById(R.id.channel_logo);
            container = itemView.findViewById(R.id.channel_container);
            nameBgArea = itemView.findViewById(R.id.name_bg_area);
            tvLiveStatus = itemView.findViewById(R.id.tvLiveStatus);
        }
    }
}
