package com.example.tv;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SlappScreen extends AppCompatActivity {

    private static final String UPDATE_JSON_URL = "https://raw.githubusercontent.com/shamimpipon/Shamim-Live-TV-Update/main/update.json";

    private android.widget.ProgressBar progressBar;
    private android.widget.TextView tvPercentage;
    private android.widget.TextView tvLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_slapp_screen);
            progressBar = findViewById(R.id.progressBar);
            tvPercentage = findViewById(R.id.tvPercentage);
            tvLoading = findViewById(R.id.tvLoading);
            
            // ভার্সন নাম্বার সেট করা
            if (tvLoading != null) {
                tvLoading.setText("Version " + BuildConfig.VERSION_NAME + " - Processing...");
            }
        } catch (Exception e) {
            startMainActivity();
            return;
        }

        startLoadingAnimation();
    }

    private void startLoadingAnimation() {
        final int totalTime = 2000; // 2 seconds
        final int interval = 20;   // Update every 20ms
        final Handler handler = new Handler();
        
        new Thread(() -> {
            for (int i = 0; i <= 100; i++) {
                final int progress = i;
                handler.post(() -> {
                    if (tvPercentage != null) tvPercentage.setText(progress + "%");
                    if (progressBar != null) {
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(progress);
                    }
                });
                try {
                    Thread.sleep(totalTime / 100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // লোডিং শেষ হলে আপডেট চেক শুরু হবে (Get & Process flow)
            handler.post(this::checkUpdate);
        }).start();
    }

    private void checkUpdate() {
        runOnUiThread(() -> {
            if (tvLoading != null) tvLoading.setText("Checking for updates...");
        });

        // GitHub Raw URL থেকে আপডেট চেক করার সঠিক পদ্ধতি
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/shamimpipon/Shamim-Live-TV-Update/main/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        UpdateService service = retrofit.create(UpdateService.class);
        service.checkUpdate("update.json").enqueue(new Callback<UpdateResponse>() {
            @Override
            public void onResponse(Call<UpdateResponse> call, retrofit2.Response<UpdateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UpdateResponse update = response.body();
                    int currentVersion = BuildConfig.VERSION_CODE;
                    int latestVersion = update.getVersionCode();

                    if (latestVersion > currentVersion) {
                        showUpdateDialog(update);
                    } else {
                        startMainActivity();
                    }
                } else {
                    startMainActivity();
                }
            }

            @Override
            public void onFailure(Call<UpdateResponse> call, Throwable t) {
                startMainActivity();
            }
        });
    }

    private void showUpdateDialog(UpdateResponse update) {
        new AlertDialog.Builder(this)
                .setTitle("New Update Available")
                .setMessage("A new version is available. Do you want to download Shamim Live TV v" + update.getVersionCode() + "?")
                .setCancelable(false)
                .setPositiveButton("Update Now", (dialog, which) -> downloadAndInstallApk(update.getApkUrl(), String.valueOf(update.getVersionCode())))
                .setNegativeButton("Later", (dialog, which) -> startMainActivity())
                .show();
    }

    private void downloadAndInstallApk(String url, String version) {
        runOnUiThread(() -> {
            if (tvLoading != null) tvLoading.setText("Getting Shamim Live TV v" + version + "...");
            if (tvPercentage != null) tvPercentage.setText("Connecting...");
        });

        // পুরনো সেই DownloadManager সিস্টেম যা আপনার রিপোজিটরির সাথে কাজ করত
        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
        request.setTitle("Shamim Live TV v" + version);
        request.setDescription("Getting update from GitHub...");
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        
        String fileName = "Shamim_Live_TV_v" + version + ".apk";
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);

        android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
        long downloadID = manager.enqueue(request);

        new Thread(() -> {
            boolean downloading = true;
            while (downloading) {
                android.app.DownloadManager.Query query = new android.app.DownloadManager.Query();
                query.setFilterById(downloadID);
                android.database.Cursor cursor = manager.query(query);
                if (cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS));
                    int bytes_downloaded = cursor.getInt(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytes_total = cursor.getInt(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false;
                        runOnUiThread(() -> {
                            if (tvLoading != null) tvLoading.setText("Posting/Updating to v" + version + "...");
                            installApkFromFile(fileName);
                        });
                    }

                    if (bytes_total > 0) {
                        final int progress = (int) ((bytes_downloaded * 100L) / bytes_total);
                        runOnUiThread(() -> {
                            if (tvPercentage != null) tvPercentage.setText(progress + "%");
                            if (progressBar != null) progressBar.setProgress(progress);
                        });
                    }
                }
                cursor.close();
                try { Thread.sleep(500); } catch (InterruptedException e) {}
            }
        }).start();
    }

    private void installApkFromFile(String fileName) {
        try {
            java.io.File file = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName);
            if (file.exists()) {
                android.net.Uri path = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(path, "application/vnd.android.package-archive");
                intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        } catch (Exception e) {
            startMainActivity();
        }
    }

    private void installApk(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = Uri.fromFile(file);
        }
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startMainActivityDelayed() {
        new Handler().postDelayed(this::startMainActivity, 2000);
    }

    private void startMainActivity() {
        Intent intent = new Intent(SlappScreen.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}

