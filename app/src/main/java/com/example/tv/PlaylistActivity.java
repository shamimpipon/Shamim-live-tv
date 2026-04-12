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

    private RecyclerView rvPlaylists;
    private TextView tvEmpty;
    private List<PlaylistModel> playlistList;
    private PlaylistAdapter adapter;
    private SharedPreferences sharedPreferences;
    private Gson gson;
    private RelativeLayout toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        rvPlaylists = findViewById(R.id.rvPlaylists);
        tvEmpty = findViewById(R.id.tvEmpty);
        toolbar = findViewById(R.id.toolbar);
        
        rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
        sharedPreferences = getSharedPreferences("Playlists", Context.MODE_PRIVATE);
        gson = new Gson();
        
        loadPlaylists();
        startRGBAnimation();

        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddOptionsDialog());
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
                new AlertDialog.Builder(PlaylistActivity.this)
                        .setTitle("Delete Playlist")
                        .setMessage("Are you sure you want to delete this?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            playlistList.remove(position);
                            savePlaylists();
                            adapter.notifyItemRemoved(position);
                            adapter.notifyItemRangeChanged(position, playlistList.size());
                        })
                        .setNegativeButton("No", null)
                        .show();
            }

            @Override
            public void onPlaylistEdit(int position, PlaylistModel playlist) {
                showEditDialog(position, playlist);
            }
        });
        rvPlaylists.setAdapter(adapter);
    }

    private void showEditDialog(int position, PlaylistModel playlist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Playlist");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);
        
        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        nameInput.setText(playlist.getName());
        layout.addView(nameInput);
        
        final EditText urlInput = new EditText(this);
        urlInput.setHint("URL");
        urlInput.setText(playlist.getUrl());
        layout.addView(urlInput);
        
        builder.setView(layout);
        builder.setPositiveButton("Update", (dialog, which) -> {
            String newName = nameInput.getText().toString().trim();
            String newUrl = urlInput.getText().toString().trim();
            if (!newName.isEmpty() && !newUrl.isEmpty()) {
                playlistList.set(position, new PlaylistModel(newName, newUrl));
                savePlaylists();
                adapter.notifyItemChanged(position);
                Toast.makeText(this, "Playlist Updated", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void startRGBAnimation() {
        if (toolbar == null) return;
        int colorFrom = Color.parseColor("#050A30"); // Dark Navy
        int colorTo = Color.parseColor("#D81B60");   // Pink Neon
        int colorCyan = Color.parseColor("#00E5FF"); // Cyan Neon

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo, colorCyan, colorFrom);
        colorAnimation.setDuration(5000); 
        colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimation.addUpdateListener(animator -> toolbar.setBackgroundColor((int) animator.getAnimatedValue()));
        colorAnimation.start();
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
        String[] options = {"Add M3U URL", "Select Local File", "Xtream Codes"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Option");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) showAddM3uDialog();
            else if (which == 1) openFilePicker();
            else if (which == 2) showXtreamLoginDialog();
        });
        builder.show();
    }

    private void showAddM3uDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add M3U Playlist");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);
        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        layout.addView(nameInput);
        final EditText urlInput = new EditText(this);
        urlInput.setHint("URL (http://...)");
        layout.addView(urlInput);
        builder.setView(layout);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            if (!name.isEmpty() && !url.isEmpty()) {
                playlistList.add(new PlaylistModel(name, url));
                savePlaylists();
                adapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Xtream Codes Login");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);
        final EditText serverInput = new EditText(this); serverInput.setHint("Server URL (http://...)");
        layout.addView(serverInput);
        final EditText userInput = new EditText(this); userInput.setHint("Username");
        layout.addView(userInput);
        final EditText passInput = new EditText(this); passInput.setHint("Password");
        layout.addView(passInput);
        builder.setView(layout);
        builder.setPositiveButton("Login", (dialog, which) -> {
            String server = serverInput.getText().toString().trim();
            String user = userInput.getText().toString().trim();
            String pass = passInput.getText().toString().trim();
            if (!server.isEmpty() && !user.isEmpty() && !pass.isEmpty()) {
                String xtreamUrl = server + "/get.php?username=" + user + "&password=" + pass + "&type=m3u_plus&output=ts";
                playlistList.add(new PlaylistModel("Xtream: " + user, xtreamUrl));
                savePlaylists();
                adapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
