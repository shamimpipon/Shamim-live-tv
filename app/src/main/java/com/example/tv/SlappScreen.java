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
            handler.post(this::startMainActivity);
        }).start();
    }

    private void checkUpdate() {
        // GitHub Raw URL থেকে আপডেট চেক করার সঠিক পদ্ধতি
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/shamimpipon/Shamim-Live-TV-Update/main/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        UpdateService service = retrofit.create(UpdateService.class);
        // update.json ফাইলটি চেক করা হচ্ছে
        service.checkUpdate("update.json").enqueue(new Callback<UpdateResponse>() {
            @Override
            public void onResponse(Call<UpdateResponse> call, retrofit2.Response<UpdateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UpdateResponse update = response.body();
                    int currentVersion = BuildConfig.VERSION_CODE;
                    int latestVersion = update.getVersionCode();

                    // যদি GitHub এর ভার্সন অ্যাপের ভার্সন এর চেয়ে বেশি হয়, তবেই আপডেট দেখাবে
                    if (latestVersion > currentVersion) {
                        showUpdateDialog(update);
                    } else {
                        startMainActivityDelayed();
                    }
                } else {
                    startMainActivityDelayed();
                }
            }

            @Override
            public void onFailure(Call<UpdateResponse> call, Throwable t) {
                startMainActivityDelayed();
            }
        });
    }

    private void showUpdateDialog(UpdateResponse update) {
        new AlertDialog.Builder(this)
                .setTitle("New Update Available")
                .setMessage(update.getUpdateMessage())
                .setCancelable(false)
                .setPositiveButton("Update Now", (dialog, which) -> downloadAndInstallApk(update.getApkUrl()))
                .setNegativeButton("Later", (dialog, which) -> {
                    // Later এ ক্লিক করলে ওই ভার্সনের জন্য আর নোটিফিকেশন দেবে না
                    SharedPreferences prefs = getSharedPreferences("UpdatePrefs", MODE_PRIVATE);
                    prefs.edit().putInt("lastPromptedVersion", update.getVersionCode()).apply();
                    startMainActivity();
                })
                .show();
    }

    private void downloadAndInstallApk(String url) {
        // এপিকে ডাউনলোডের জন্য একটি সহজ থ্রেড
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                
                if (response.isSuccessful() && response.body() != null) {
                    // ডাউনলোড করা ফাইলটির নাম ভার্সনসহ সেট করা হয়েছে
                    String fileName = "Shamim Live TV v" + BuildConfig.VERSION_NAME + ".apk";
                    File apkFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
                    InputStream is = response.body().byteStream();
                    FileOutputStream fos = new FileOutputStream(apkFile);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();
                    
                    runOnUiThread(() -> installApk(apkFile));
                }
            } catch (Exception e) {
                runOnUiThread(() -> startMainActivity());
            }
        }).start();
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

