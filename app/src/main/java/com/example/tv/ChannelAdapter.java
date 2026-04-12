package com.example.tv;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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
                .placeholder(R.drawable.tvlogo)
                .error(R.drawable.tvlogo)
                .into(holder.logo);

        // ছবিতে যেমন দেখা যাচ্ছে, ডাইনামিক বর্ডার এবং নাম এর ব্যাকগ্রাউন্ড সেট করা হচ্ছে
        int color = colors[position % colors.length];

        // ১. পুরো বক্সের বর্ডার সেট করা
        GradientDrawable border = new GradientDrawable();
        border.setShape(GradientDrawable.RECTANGLE);
        border.setCornerRadius(25); // ছবির মতো রাউন্ডেড কর্নার
        border.setStroke(4, color); // বর্ডার কালার
        border.setColor(Color.parseColor("#050A30")); // ভেতরের ডার্ক কালার
        holder.container.setBackground(border);

        // ২. নিচের নামের অংশের ব্যাকগ্রাউন্ড কালার সেট করা
        GradientDrawable nameBg = new GradientDrawable();
        nameBg.setShape(GradientDrawable.RECTANGLE);
        // শুধুমাত্র নিচের দুই কোণা রাউন্ড করার জন্য (TopLeft, TopRight, BottomRight, BottomLeft)
        float radius = 22f;
        nameBg.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        nameBg.setColor(color);
        holder.nameBgArea.setBackground(nameBg);

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
