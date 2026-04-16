package com.example.tv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PlaylistActivity extends AppCompatActivity {

    private RelativeLayout toolbar;
    private View borderView;
    private RecyclerView rvPlaylists;
    private TextView tvEmpty;
    private List<PlaylistModel> playlistList;
    private PlaylistAdapter adapter;
    private SharedPreferences sharedPreferences;
    private Gson gson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        rvPlaylists = findViewById(R.id.rvPlaylists);
        tvEmpty = findViewById(R.id.tvEmpty);
        toolbar = findViewById(R.id.toolbar_container);
        
        // Find the border view
        LinearLayout topSection = findViewById(R.id.topSection);
        RelativeLayout headerRow = topSection.findViewById(R.id.headerRow);
        borderView = headerRow.getChildAt(1); // The second child is the View with orange_border_bg

        rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
        sharedPreferences = getSharedPreferences("Playlists", Context.MODE_PRIVATE);
        gson = new Gson();
        
        loadPlaylists();
        startBorderAnimation();

        // About Button Click
        findViewById(R.id.btnInfo).setOnClickListener(v -> showAboutDialog());

        // Toolbar Update Button Click
        findViewById(R.id.btnToolbarUpdate).setOnClickListener(v -> {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
            checkUpdate(true);
        });

        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddOptionsDialog());
    }

    private void showAboutDialog() {
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

        AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
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
                        String currentVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;

                        if (update.getVersionCode() > currentVersionCode) {
                            showUpdateDialog(update);
                        } else {
                            if (isManual) {
                                new androidx.appcompat.app.AlertDialog.Builder(PlaylistActivity.this)
                                        .setTitle("App Up to Date")
                                        .setMessage("You are running the latest version.\n\n" +
                                                "Running Version: " + currentVersionName + " (" + currentVersionCode + ")\n" +
                                                "Latest Version: " + update.getVersionName())
                                        .setPositiveButton("OK", null)
                                        .show();
                            }
                        }
                    } catch (Exception e) {
                        if (isManual) Toast.makeText(PlaylistActivity.this, "Error checking version", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override
            public void onFailure(retrofit2.Call<UpdateResponse> call, Throwable t) {
                if (isManual) Toast.makeText(PlaylistActivity.this, "Check failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showUpdateDialog(UpdateResponse update) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("New Update Available")
                .setMessage("New Version: " + update.getVersionName() + "\n\n" + update.getUpdateMessage())
                .setPositiveButton("Update Now", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(update.getApkUrl()));
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void startBorderAnimation() {
        if (borderView == null) return;
        
        int color1 = Color.parseColor("#FF8C00"); // Orange
        int color2 = Color.parseColor("#00FF00"); // Green
        int color3 = Color.parseColor("#00E5FF"); // Cyan
        int color4 = Color.parseColor("#D81B60"); // Pink

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), color1, color2, color3, color4, color1);
        colorAnimation.setDuration(4000); 
        colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimation.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            android.graphics.drawable.GradientDrawable drawable = (android.graphics.drawable.GradientDrawable) borderView.getBackground();
            drawable.setStroke(4, color);
        });
        colorAnimation.start();
    }

    private void setupAdapter() {
        adapter = new PlaylistAdapter(playlistList, new PlaylistAdapter.OnPlaylistClickListener() {
            @Override
            public void onPlaylistClick(PlaylistModel playlist) {
                Intent intent = new Intent(PlaylistActivity.this, PlaylistChannelsActivity.class);
                intent.putExtra("playlist_url", playlist.getUrl());
                intent.putExtra("playlist_name", playlist.getName());
                startActivity(intent);
            }

            @Override
            public void onPlaylistDelete(int position) {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_confirm, null);
                Button btnYes = dialogView.findViewById(R.id.btn_yes);
                Button btnNo = dialogView.findViewById(R.id.btn_no);

                AlertDialog dialog = new AlertDialog.Builder(PlaylistActivity.this)
                        .setView(dialogView)
                        .create();

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                btnYes.setOnClickListener(v -> {
                    playlistList.remove(position);
                    savePlaylists();
                    adapter.notifyItemRemoved(position);
                    adapter.notifyItemRangeChanged(position, playlistList.size());
                    dialog.dismiss();
                });

                btnNo.setOnClickListener(v -> dialog.dismiss());
                dialog.show();
            }

            @Override
            public void onPlaylistEdit(int position, PlaylistModel playlist) {
                showEditDialog(position, playlist);
            }
        });
        rvPlaylists.setAdapter(adapter);
    }

    private void showEditDialog(int position, PlaylistModel playlist) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_playlist, null);
        EditText nameInput = dialogView.findViewById(R.id.et_playlist_name);
        EditText urlInput = dialogView.findViewById(R.id.et_playlist_url);
        Button btnUpdate = dialogView.findViewById(R.id.btn_update);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        nameInput.setText(playlist.getName());
        urlInput.setText(playlist.getUrl());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnUpdate.setOnClickListener(v -> {
            String newName = nameInput.getText().toString().trim();
            String newUrl = urlInput.getText().toString().trim();
            if (!newName.isEmpty() && !newUrl.isEmpty()) {
                playlistList.set(position, new PlaylistModel(newName, newUrl));
                savePlaylists();
                adapter.notifyItemChanged(position);
                Toast.makeText(this, "Playlist Updated", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void loadPlaylists() {
        String json = sharedPreferences.getString("playlist_list", null);
        Type type = new TypeToken<ArrayList<PlaylistModel>>() {}.getType();
        playlistList = gson.fromJson(json, type);
        if (playlistList == null) {
            playlistList = new ArrayList<>();
        }
        setupAdapter();
        checkEmpty();
    }

    private void savePlaylists() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String json = gson.toJson(playlistList);
        editor.putString("playlist_list", json);
        editor.apply();
        checkEmpty();
    }

    private void checkEmpty() {
        if (playlistList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvPlaylists.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvPlaylists.setVisibility(View.VISIBLE);
        }
    }

    private void showAddOptionsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_options, null);
        Button btnM3u = dialogView.findViewById(R.id.btn_add_m3u);
        Button btnLocal = dialogView.findViewById(R.id.btn_local_file);
        Button btnXtream = dialogView.findViewById(R.id.btn_xtream);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnM3u.setOnClickListener(v -> {
            dialog.dismiss();
            showAddM3uDialog();
        });

        btnLocal.setOnClickListener(v -> {
            dialog.dismiss();
            openFilePicker();
        });

        btnXtream.setOnClickListener(v -> {
            dialog.dismiss();
            showXtreamLoginDialog();
        });

        dialog.show();
    }

    private void showAddM3uDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_m3u, null);
        EditText nameInput = dialogView.findViewById(R.id.et_playlist_name);
        EditText urlInput = dialogView.findViewById(R.id.et_playlist_url);
        Button btnAdd = dialogView.findViewById(R.id.btn_add);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnAdd.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            if (!name.isEmpty() && !url.isEmpty()) {
                playlistList.add(new PlaylistModel(name, url));
                savePlaylists();
                adapter.notifyDataSetChanged();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                playlistList.add(new PlaylistModel("Local: " + uri.getLastPathSegment(), uri.toString()));
                savePlaylists();
                adapter.notifyDataSetChanged();
            }
        }
    }

    private void showXtreamLoginDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_xtream_login, null);
        EditText serverInput = dialogView.findViewById(R.id.et_server_url);
        EditText userInput = dialogView.findViewById(R.id.et_username);
        EditText passInput = dialogView.findViewById(R.id.et_password);
        Button btnLogin = dialogView.findViewById(R.id.btn_login);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnLogin.setOnClickListener(v -> {
            String server = serverInput.getText().toString().trim();
            String user = userInput.getText().toString().trim();
            String pass = passInput.getText().toString().trim();
            if (!server.isEmpty() && !user.isEmpty() && !pass.isEmpty()) {
                String xtreamUrl = server + "/get.php?username=" + user + "&password=" + pass + "&type=m3u_plus&output=ts";
                playlistList.add(new PlaylistModel("Xtream: " + user, xtreamUrl));
                savePlaylists();
                adapter.notifyDataSetChanged();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
