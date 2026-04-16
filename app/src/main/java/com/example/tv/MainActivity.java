package com.example.tv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChannelAdapter adapter;
    private List<Channel> channelList;
    private List<Channel> fullChannelList;
    private SharedPreferences sharedPreferences;
    private RelativeLayout toolbar;
    private long downloadID;
    private android.widget.TextView tvVisitorCount;
    private android.widget.TextView[] categoryTabs;
    private int[] categoryColors = {
        Color.parseColor("#FF8C00"), // Deep Orange (All)
        Color.parseColor("#1E88E5"), // Bright Blue (Sports)
        Color.parseColor("#E53935"), // Red (News)
        Color.parseColor("#43A047"), // Green (Bangla)
        Color.parseColor("#FB8C00"), // Orange (Movies)
        Color.parseColor("#8E24AA")  // Purple (Update)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        tvVisitorCount = findViewById(R.id.tvVisitorCount);
        
        channelList = new ArrayList<>();
        fullChannelList = new ArrayList<>();

        setupCategoryTabs();
        setupCategoryClickListeners();
        updateVisitorCount();

        // startRGBAnimation(); // Removed RGB animation as requested

        // PLAYLIST বাটন - প্লেলিস্ট স্ক্রিনে নিয়ে যাবে
        findViewById(R.id.btnPlaylist).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, PlaylistActivity.class));
        });

        // URL LINK বাটন - কাস্টম লিঙ্ক প্লে করার জন্য
        findViewById(R.id.btnNetwork).setOnClickListener(v -> showNetworkDialog());

        // Left About Button Click
        findViewById(R.id.btnInfo).setOnClickListener(v -> {
            // Custom Title
            android.widget.TextView titleView = new android.widget.TextView(this);
            titleView.setText("About App");
            titleView.setPadding(20, 40, 20, 10);
            titleView.setTextSize(24);
            titleView.setTextColor(Color.parseColor("#FF8C00")); // Premium Orange
            titleView.setGravity(android.view.Gravity.CENTER);
            titleView.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.BOLD));

            // Custom Message with Styled Name and Contact
            android.text.SpannableString spannableMessage = new android.text.SpannableString(
                "Shamim Live TV\nVersion: 1.0\n\nDeveloped by\nShamimul Haque (Samin)\n\nContact: 📞 01638073621\n\nEnjoy premium live TV channels for free.");
            
            // Stylish styling for the name
            int nameStart = spannableMessage.toString().indexOf("Shamimul Haque (Samin)");
            int nameEnd = nameStart + "Shamimul Haque (Samin)".length();
            spannableMessage.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#FF8C00")), nameStart, nameEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableMessage.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC), nameStart, nameEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableMessage.setSpan(new android.text.style.RelativeSizeSpan(1.2f), nameStart, nameEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Styling for the Phone Number
            int phoneStart = spannableMessage.toString().indexOf("01638073621");
            int phoneEnd = phoneStart + "01638073621".length();
            spannableMessage.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#00E5FF")), phoneStart, phoneEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableMessage.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), phoneStart, phoneEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setMessage(spannableMessage)
                .setPositiveButton("CLOSE", null)
                .create();

            dialog.show();

            // Customizing Dialog Appearance
            if (dialog.getWindow() != null) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(Color.parseColor("#121212")); // Deeper Dark Background
                gd.setCornerRadius(50f);
                gd.setStroke(3, Color.parseColor("#FF8C00")); // Thinner Elegant Orange Border
                dialog.getWindow().setBackgroundDrawable(gd);
            }

            // Styling Message Text
            android.widget.TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setTextColor(Color.WHITE);
                messageView.setGravity(android.view.Gravity.CENTER);
                messageView.setLineSpacing(0, 1.2f);
                messageView.setTextSize(15);
                messageView.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL));
            }

            // Styling Button
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(Color.parseColor("#FF8C00"));
                positiveButton.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
            }
        });

        // New Toolbar Update Button Click
        findViewById(R.id.btnToolbarUpdate).setOnClickListener(v -> {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
            checkUpdate(true);
        });

        // আপডেট চেক বাটন - ম্যানুয়ালি চেক করার জন্য
        findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
            checkUpdate(true);
        });

        // অ্যাপ ওপেন হওয়ার সাথে সাথে অটোমেটিক আপডেট চেক করবে (Silent)
        checkUpdate(false);
    }

    private void showNetworkDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_m3u, null);
        EditText nameInput = dialogView.findViewById(R.id.et_playlist_name);
        EditText urlInput = dialogView.findViewById(R.id.et_playlist_url);
        android.widget.Button btnPlay = dialogView.findViewById(R.id.btn_add);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        nameInput.setHint("Stream Name (Optional)");
        urlInput.setHint("Enter M3U/Video URL");
        btnPlay.setText("PLAY");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnPlay.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) {
                playCustomUrl(url);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentM3U();
    }

    private void updateVisitorCount() {
        // Visitor counting safe implementation
        new Thread(() -> {
            try {
                // Using a fallback mechanism if the API is down
                String apiKey = "shamim_live_tv_v1"; 
                java.net.URL url = new java.net.URL("https://api.countapi.xyz/hit/" + apiKey + "/visits");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000); // Faster timeout
                connection.setReadTimeout(3000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()));
                    String line = reader.readLine();
                    if (line != null && line.contains("\"value\":")) {
                        String count = line.substring(line.indexOf("\"value\":") + 8, line.lastIndexOf("}"));
                        runOnUiThread(() -> {
                            if (tvVisitorCount != null) tvVisitorCount.setText("Visitors: " + count);
                        });
                    }
                    reader.close();
                } else {
                    runOnUiThread(() -> {
                        if (tvVisitorCount != null) tvVisitorCount.setText("Visitors: Live");
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (tvVisitorCount != null) tvVisitorCount.setText("Visitors: Online");
                });
            }
        }).start();
    }

    private void setupCategoryTabs() {
        categoryTabs = new android.widget.TextView[]{
                findViewById(R.id.catAll),
                findViewById(R.id.catSports),
                findViewById(R.id.catNews),
                findViewById(R.id.catBangla),
                findViewById(R.id.catMovies),
                findViewById(R.id.btnCheckUpdate)
        };

        for (int i = 0; i < categoryTabs.length; i++) {
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(10f); 
            
            int color = categoryColors[i];
            drawable.setColor(color); 
            drawable.setStroke(2, Color.parseColor("#40FFFFFF")); 
            
            categoryTabs[i].setBackground(drawable);
            categoryTabs[i].setTextColor(Color.WHITE);
            categoryTabs[i].setPadding(25, 12, 25, 12); // Reduced padding to fit better
            categoryTabs[i].setAllCaps(false);
            categoryTabs[i].setTextSize(12); // Slightly smaller text to ensure fit
            categoryTabs[i].setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            
            // Ensure single line and handle overflow
            categoryTabs[i].setSingleLine(true);
            categoryTabs[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
    }

    private void setupCategoryClickListeners() {
        // All
        findViewById(R.id.catAll).setOnClickListener(v -> filterChannels("All"));
        // Sports
        findViewById(R.id.catSports).setOnClickListener(v -> filterChannels("Sports"));
        // BD News
        findViewById(R.id.catNews).setOnClickListener(v -> filterChannels("News"));
        // Bangla
        findViewById(R.id.catBangla).setOnClickListener(v -> filterChannels("Bangla"));
        // Movies
        findViewById(R.id.catMovies).setOnClickListener(v -> filterChannels("Movie"));
    }

    private void filterChannels(String category) {
        List<Channel> filteredList = new ArrayList<>();
        String categoryLower = category.toLowerCase();
        
        if (category.equalsIgnoreCase("All")) {
            filteredList.addAll(fullChannelList);
        } else {
            for (Channel channel : fullChannelList) {
                String nameLower = channel.getName().toLowerCase();
                
                if (category.equalsIgnoreCase("News")) {
                    // BD News এর জন্য বিশেষ কি-ওয়ার্ড (বাংলাদেশের জনপ্রিয় নিউজ চ্যানেলসমূহ)
                    if (nameLower.contains("news") || nameLower.contains("somoy") || 
                        nameLower.contains("ekattor") || nameLower.contains("jamuna") || 
                        nameLower.contains("independent") || nameLower.contains("dbc") || 
                        nameLower.contains("channel 24") || nameLower.contains("atn news") || 
                        nameLower.contains("news24") || nameLower.contains("71") ||
                        nameLower.contains("ekattor tv") || nameLower.contains("ekattor.tv") ||
                        nameLower.contains("desh tv") || nameLower.contains("rtv") || 
                        nameLower.contains("ntv") || nameLower.contains("boishakhi")) {
                        filteredList.add(channel);
                    }
                } else if (category.equalsIgnoreCase("Sports")) {
                    if (nameLower.contains("sports") || nameLower.contains("t sports") || 
                        nameLower.contains("gtv") || nameLower.contains("star sports") || 
                        nameLower.contains("sony") || nameLower.contains("ptv sports")) {
                        filteredList.add(channel);
                    }
                } else if (category.equalsIgnoreCase("Movie")) {
                    if (nameLower.contains("movie") || nameLower.contains("cinema") || 
                        nameLower.contains("star jalsha movies") || nameLower.contains("sony max") || 
                        nameLower.contains("zee cinema")) {
                        filteredList.add(channel);
                    }
                } else {
                    // সাধারণ সার্চ (Bangla বা অন্য ক্যাটাগরির জন্য)
                    if (nameLower.contains(categoryLower)) {
                        filteredList.add(channel);
                    }
                }
            }
        }
        
        channelList.clear();
        channelList.addAll(filteredList);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No channels found in " + category, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCurrentM3U() {
        // হোম স্ক্রিনের জন্য আপনার নির্দিষ্ট ফিক্সড লিঙ্ক
        String url = "https://raw.githubusercontent.com/shamimpipon/Shamim-live-tv/main/Channel.m3u";
        
        new Thread(() -> {
            try {
                InputStream inputStream;
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new URL(url).openConnection();
                // সব ধরণের লিঙ্ক (Toffee, ISP) সাপোর্ট করার জন্য User-Agent
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                inputStream = connection.getInputStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                List<Channel> channels = new ArrayList<>();
                String line, name = "", logo = "";
                
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#EXTINF")) {
                        name = line.substring(line.lastIndexOf(",") + 1).trim();
                        if (line.contains("tvg-logo=\"")) {
                            logo = line.substring(line.indexOf("tvg-logo=\"") + 10);
                            logo = logo.substring(0, logo.indexOf("\""));
                        }
                    } else if (line.startsWith("http") || line.startsWith("rtmp") || line.startsWith("rtsp")) {
                        if (name.isEmpty()) name = "Unknown Channel";
                        channels.add(new Channel(name, line.trim(), logo));
                        name = ""; logo = "";
                    }
                }
                reader.close();

                runOnUiThread(() -> {
                    fullChannelList.clear();
                    fullChannelList.addAll(channels);

                    channelList.clear();
                    channelList.addAll(channels);
                    if (adapter == null) {
                        adapter = new ChannelAdapter(channelList, channel -> {
                            int position = channelList.indexOf(channel);
                            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                            intent.putExtra("position", position);
                            intent.putExtra("channelList", new ArrayList<>(channelList));
                            startActivity(intent);
                        });
                        recyclerView.setAdapter(adapter);
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                    // সুন্দরভাবে লোড হওয়ার পর ছোট একটি মেসেজ (ঐচ্ছিক)
                    if (!channels.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Channels Updated", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void playCustomUrl(String url) {
        Channel customChannel = new Channel("Network Stream", url, "");
        ArrayList<Channel> tempPlaylist = new ArrayList<>();
        tempPlaylist.add(customChannel);
        
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        intent.putExtra("position", 0);
        intent.putExtra("channelList", tempPlaylist);
        startActivity(intent);
    }

    private static boolean isUpdateChecked = false;

    private void checkUpdate(boolean isManual) {
        if (!isManual && isUpdateChecked) return;
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/shamimpipon/Shamim-Live-TV-Update/main/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        UpdateService service = retrofit.create(UpdateService.class);
        service.checkUpdate("update.json").enqueue(new Callback<UpdateResponse>() {
            @Override
            public void onResponse(Call<UpdateResponse> call, retrofit2.Response<UpdateResponse> response) {
                isUpdateChecked = true;
                if (response.isSuccessful() && response.body() != null) {
                    UpdateResponse update = response.body();
                    try {
                        long currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                        String currentVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                        
                        if (update.getVersionCode() > currentVersionCode) {
                            showUpdateDialog(update);
                        } else {
                            if (isManual) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("App Up to Date")
                                        .setMessage("You are running the latest version.\n\n" +
                                                   "Running Version: " + currentVersionName + " (" + currentVersionCode + ")\n" +
                                                   "Latest Version: " + update.getVersionName())
                                        .setPositiveButton("OK", null)
                                        .show();
                            }
                        }
                    } catch (Exception e) {
                        if (isManual) Toast.makeText(MainActivity.this, "Error checking version", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override
            public void onFailure(Call<UpdateResponse> call, Throwable t) {
                isUpdateChecked = true;
                if (isManual) Toast.makeText(MainActivity.this, "Check failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startRGBAnimation() {
        int colorFrom = Color.parseColor("#050A30"); // Dark Navy
        int colorTo = Color.parseColor("#D81B60");   // Pink Neon
        int colorCyan = Color.parseColor("#00E5FF"); // Cyan Neon

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo, colorCyan, colorFrom);
        colorAnimation.setDuration(5000); // 5 seconds for one cycle
        colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimation.addUpdateListener(animator -> toolbar.setBackgroundColor((int) animator.getAnimatedValue()));
        colorAnimation.start();
    }

    private void showUpdateDialog(UpdateResponse update) {
        String currentVersionName = "";
        try {
            currentVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {}

        new AlertDialog.Builder(this)
                .setTitle("New Update Available")
                .setMessage("Current Version: " + currentVersionName + "\n" +
                           "New Version: " + update.getVersionName() + "\n\n" +
                           update.getUpdateMessage())
                .setCancelable(false) // আপডেট বাধ্যতামূলক করার জন্য false রাখা ভালো
                .setPositiveButton("Update Now", (dialog, which) -> {
                    startDownload(update.getApkUrl());
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void startDownload(String url) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Downloading Update...");
        progressDialog.setMessage("Starting download...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        String fileName = "ShamimLiveTV_v" + System.currentTimeMillis() + ".apk";
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("Shamim Live TV Update");
        request.setDescription("Downloading new version...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        downloadID = manager.enqueue(request);

        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadID == id) {
                    progressDialog.dismiss();
                    installApk(fileName);
                    unregisterReceiver(this);
                }
            }
        };
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        new Thread(() -> {
            boolean downloading = true;
            while (downloading) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadID);
                Cursor cursor = manager.query(query);
                if (cursor.moveToFirst()) {
                    int bytes_downloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytes_total = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false;
                    }

                    if (bytes_total > 0) {
                        final int progress = (int) ((bytes_downloaded * 100L) / bytes_total);
                        runOnUiThread(() -> {
                            progressDialog.setProgress(progress);
                            progressDialog.setMessage("Downloaded: " + progress + "%");
                        });
                    }
                }
                cursor.close();
                try { Thread.sleep(500); } catch (InterruptedException e) {}
            }
        }).start();
    }

    private void installApk(String fileName) {
        try {
            java.io.File file = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
            if (file.exists()) {
                Uri path = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(path, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Auto-install failed. Please install from Downloads folder.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}
