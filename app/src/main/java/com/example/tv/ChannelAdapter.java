package com.example.tv;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
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

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = channelList.get(position);
        holder.name.setText(channel.getName());
        
        Glide.with(holder.itemView.getContext())
                .load(channel.getLogoUrl())
                .placeholder(R.drawable.tvlogo)
                .error(R.drawable.tvlogo)
                .into(holder.logo);

        // ছবিতে যেমন দেখা যাচ্ছে, ডার্ক প্রিমিয়াম লুক রাখার জন্য 
        // বর্ডার এবং ব্যাকগ্রাউন্ড ফিক্সড করা হলো।
        holder.container.setBackgroundResource(R.drawable.channel_card_bg);
        holder.name.setTextColor(Color.WHITE);
        holder.name.setBackgroundColor(Color.TRANSPARENT);

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

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.channel_name);
            logo = itemView.findViewById(R.id.channel_logo);
            container = itemView.findViewById(R.id.channel_container);
        }
    }
}
