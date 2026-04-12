package com.example.tv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PlaylistChannelsActivity extends AppCompatActivity {

    private RecyclerView rvChannels;
    private ChannelAdapter adapter;
    private List<Channel> channelList = new ArrayList<>();
    private ProgressBar loader;
    private String playlistUrl;
    private String playlistName;
    private RelativeLayout toolbar;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_channels);

        playlistUrl = getIntent().getStringExtra("playlist_url");
        playlistName = getIntent().getStringExtra("playlist_name");

        rvChannels = findViewById(R.id.rvChannels);
        loader = findViewById(R.id.loader);
        toolbar = findViewById(R.id.toolbar);
        tvTitle = findViewById(R.id.tvPlaylistTitle);
        
        if (playlistName != null) tvTitle.setText(playlistName);
        
        rvChannels.setLayoutManager(new GridLayoutManager(this, 3));
        
        // ChannelAdapter এর সঠিক কন্স্ট্রাক্টর ব্যবহার করা হচ্ছে
        adapter = new ChannelAdapter(channelList, channel -> {
            int position = channelList.indexOf(channel);
            Intent intent = new Intent(PlaylistChannelsActivity.this, PlayerActivity.class);
            intent.putExtra("position", position);
            intent.putExtra("channelList", new ArrayList<>(channelList));
            startActivity(intent);
        });
        
        rvChannels.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        startRGBAnimation();
        loadChannels();
    }

    private void startRGBAnimation() {
        int colorFrom = Color.parseColor("#050A30");
        int colorTo = Color.parseColor("#D81B60");
        int colorCyan = Color.parseColor("#00E5FF");

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo, colorCyan, colorFrom);
        colorAnimation.setDuration(5000);
        colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimation.addUpdateListener(animator -> toolbar.setBackgroundColor((int) animator.getAnimatedValue()));
        colorAnimation.start();
    }

    private void loadChannels() {
        loader.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                InputStream inputStream;
                if (playlistUrl.startsWith("http")) {
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new URL(playlistUrl).openConnection();
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = getContentResolver().openInputStream(Uri.parse(playlistUrl));
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                Channel currentChannel = null;

                channelList.clear();
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#EXTINF:")) {
                        currentChannel = new Channel();
                        String name = line.substring(line.lastIndexOf(",") + 1).trim();
                        currentChannel.setName(name);

                        if (line.contains("tvg-logo=\"")) {
                            String logo = line.substring(line.indexOf("tvg-logo=\"") + 10);
                            logo = logo.substring(0, logo.indexOf("\""));
                            currentChannel.setLogoUrl(logo);
                        }
                    } else if (line.startsWith("http") && currentChannel != null) {
                        currentChannel.setUrl(line.trim());
                        channelList.add(currentChannel);
                        currentChannel = null;
                    }
                }
                reader.close();

                runOnUiThread(() -> {
                    loader.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                    if (channelList.isEmpty()) {
                        Toast.makeText(this, "No channels found", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}
