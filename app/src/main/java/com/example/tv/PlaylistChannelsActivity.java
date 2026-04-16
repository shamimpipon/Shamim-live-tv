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

        // About Button Click
        if (findViewById(R.id.btnInfo) != null) {
            findViewById(R.id.btnInfo).setOnClickListener(v -> showAboutDialog());
        }

        // Toolbar Update Button Click
        if (findViewById(R.id.btnToolbarUpdate) != null) {
            findViewById(R.id.btnToolbarUpdate).setOnClickListener(v -> {
                Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
                checkUpdate(true);
            });
        }
        
        startRGBAnimation();
        loadChannels();
    }

    private void showAboutDialog() {
        android.widget.TextView titleView = new android.widget.TextView(this);
        titleView.setText("About App");
        titleView.setPadding(20, 40, 20, 10);
        titleView.setTextSize(24);
        titleView.setTextColor(Color.parseColor("#FF8C00"));
        titleView.setGravity(android.view.Gravity.CENTER);
        titleView.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.BOLD));

        android.text.SpannableString spannableMessage = new android.text.SpannableString(
                "Shamim Live TV\nVersion: 1.0\n\nDeveloped by\nShamimul Haque (Samin)\n\nContact: 📞 01638073621\n\nEnjoy premium live TV channels for free.");

        int nameStart = spannableMessage.toString().indexOf("Shamimul Haque (Samin)");
        int nameEnd = nameStart + "Shamimul Haque (Samin)".length();
        spannableMessage.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#FF8C00")), nameStart, nameEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableMessage.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC), nameStart, nameEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableMessage.setSpan(new android.text.style.RelativeSizeSpan(1.2f), nameStart, nameEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int phoneStart = spannableMessage.toString().indexOf("01638073621");
        int phoneEnd = phoneStart + "01638073621".length();
        spannableMessage.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#00E5FF")), phoneStart, phoneEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableMessage.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), phoneStart, phoneEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setMessage(spannableMessage)
                .setPositiveButton("CLOSE", null)
                .create();
        dialog.show();

        if (dialog.getWindow() != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(Color.parseColor("#121212"));
            gd.setCornerRadius(50f);
            gd.setStroke(3, Color.parseColor("#FF8C00"));
            dialog.getWindow().setBackgroundDrawable(gd);
        }

        android.widget.TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextColor(Color.WHITE);
            messageView.setGravity(android.view.Gravity.CENTER);
            messageView.setLineSpacing(0, 1.2f);
            messageView.setTextSize(15);
        }
    }

    private void checkUpdate(boolean isManual) {
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/shamimpipon/Shamim-Live-TV-Update/main/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        UpdateService service = retrofit.create(UpdateService.class);
        service.checkUpdate("update.json").enqueue(new retrofit2.Callback<UpdateResponse>() {
            @Override
            public void onResponse(retrofit2.Call<UpdateResponse> call, retrofit2.Response<UpdateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UpdateResponse update = response.body();
                    try {
                        long currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                        if (update.getVersionCode() > currentVersionCode) {
                            showUpdateDialog(update);
                        } else if (isManual) {
                            Toast.makeText(PlaylistChannelsActivity.this, "App is up to date", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {}
                }
            }
            @Override
            public void onFailure(retrofit2.Call<UpdateResponse> call, Throwable t) {}
        });
    }

    private void showUpdateDialog(UpdateResponse update) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("New Update Available")
                .setMessage(update.getUpdateMessage())
                .setPositiveButton("Update Now", (dialog, which) -> {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(update.getApkUrl())));
                })
                .setNegativeButton("Later", null)
                .show();
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
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                String content = sb.toString().trim();
                reader.close();

                channelList.clear();
                if (content.startsWith("[") || content.startsWith("{")) {
                    // It's JSON
                    com.google.gson.JsonArray jsonArray = com.google.gson.JsonParser.parseString(content).getAsJsonArray();
                    for (int i = 0; i < jsonArray.size(); i++) {
                        com.google.gson.JsonObject obj = jsonArray.get(i).getAsJsonObject();
                        Channel channel = new Channel();
                        channel.setName(obj.has("name") ? obj.get("name").getAsString() : "Unknown");
                        channel.setUrl(obj.has("link") ? obj.get("link").getAsString() : "");
                        channel.setLogoUrl(obj.has("logo") ? obj.get("logo").getAsString() : "");
                        
                        // যদি কুকি থাকে তবে সেটা ইউআরএল এর সাথে বা অন্যভাবে হ্যান্ডেল করা যেতে পারে
                        // আপাতত শুধু চ্যানেল অ্যাড করছি
                        if (!channel.getUrl().isEmpty()) {
                            channelList.add(channel);
                        }
                    }
                } else {
                    // It's M3U
                    String[] lines = content.split("\n");
                    Channel currentChannel = null;
                    java.util.List<String> currentHeaders = new java.util.ArrayList<>();
                    for (String m3uLine : lines) {
                        m3uLine = m3uLine.trim();
                        if (m3uLine.isEmpty()) continue;

                        if (m3uLine.startsWith("#EXTINF:")) {
                            currentChannel = new Channel();
                            currentHeaders.clear();
                            String name = "";
                            if (m3uLine.contains(",")) {
                                name = m3uLine.substring(m3uLine.lastIndexOf(",") + 1).trim();
                            } else {
                                name = "Unknown Channel";
                            }
                            currentChannel.setName(name);

                            if (m3uLine.contains("tvg-logo=\"")) {
                                String logo = m3uLine.substring(m3uLine.indexOf("tvg-logo=\"") + 10);
                                logo = logo.substring(0, logo.indexOf("\""));
                                currentChannel.setLogoUrl(logo);
                            }
                        } else if (m3uLine.startsWith("#EXTVLCOPT:")) {
                            String opt = m3uLine.substring(11).trim();
                            if (opt.startsWith("http-user-agent=")) {
                                currentHeaders.add("User-Agent=" + opt.substring(16));
                            } else if (opt.startsWith("http-referrer=")) {
                                currentHeaders.add("Referer=" + opt.substring(14));
                            }
                        } else if (!m3uLine.startsWith("#") && currentChannel != null) {
                            String channelUrl = m3uLine;
                            if (!currentHeaders.isEmpty()) {
                                StringBuilder sbUrl = new StringBuilder(channelUrl);
                                for (String h : currentHeaders) {
                                    sbUrl.append("|").append(h);
                                }
                                channelUrl = sbUrl.toString();
                            }
                            currentChannel.setUrl(channelUrl);
                            channelList.add(currentChannel);
                            currentChannel = null;
                        }
                    }
                }

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
