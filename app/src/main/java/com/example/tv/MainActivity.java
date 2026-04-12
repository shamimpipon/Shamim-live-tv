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
    private SharedPreferences sharedPreferences;
    private RelativeLayout toolbar;
    private long downloadID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        toolbar = findViewById(R.id.toolbar);
        
        channelList = new ArrayList<>();

        startRGBAnimation();

        // PLAYLIST বাটন - প্লেলিস্ট স্ক্রিনে নিয়ে যাবে
        findViewById(R.id.btnPlaylist).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, PlaylistActivity.class));
        });

        // URL LINK বাটন - কাস্টম লিঙ্ক প্লে করার জন্য
        findViewById(R.id.btnNetwork).setOnClickListener(v -> showNetworkDialog());

        // আপডেট চেক বাটন - ম্যানুয়ালি চেক করার জন্য
        findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
            checkUpdate(true);
        });

        // অ্যাপ ওপেন হওয়ার সাথে সাথে অটোমেটিক আপডেট চেক করবে (Silent)
        checkUpdate(false);
    }

    private void showNetworkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Network Stream");
        
        final EditText input = new EditText(this);
        input.setHint("Enter M3U/Video URL");
        input.setPadding(50, 20, 50, 20);
        builder.setView(input);

        builder.setPositiveButton("PLAY", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                playCustomUrl(url);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentM3U();
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
                        long currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                        if (update.getVersionCode() > currentVersion) {
                            showUpdateDialog(update);
                        } else {
                            if (isManual) {
                                Toast.makeText(MainActivity.this, "App is up to date!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (Exception e) {
                        if (isManual) Toast.makeText(MainActivity.this, "Error checking update", Toast.LENGTH_SHORT).show();
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
