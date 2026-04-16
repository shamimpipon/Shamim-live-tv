package com.example.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    private StyledPlayerView playerView;
    private ExoPlayer player;
    private ProgressBar progressBar;
    private TextView tvChannelName, controlText, tvSpeed;
    private ImageButton btnNext, btnPrev, btnInfo;
    private ImageView controlIcon, ivChannelLogo;
    private LinearLayout volBrightLayout, controlsLayout;
    private final Handler hideHandler = new Handler();
    private final Runnable hideRunnable = () -> {
        controlsLayout.setVisibility(View.GONE);
    };
    private final Handler speedHandler = new Handler();
    private final Runnable speedRunnable = new Runnable() {
        @Override
        public void run() {
            updateSpeed();
            speedHandler.postDelayed(this, 1000);
        }
    };
    private long lastBytes = 0;

    private List<Channel> channelList;
    private int currentPosition;

    private AudioManager audioManager;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        progressBar = findViewById(R.id.progressBar);
        tvChannelName = findViewById(R.id.tv_channel_name);
        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);
        btnInfo = findViewById(R.id.btn_info);
        controlsLayout = findViewById(R.id.controls_layout);
        ivChannelLogo = findViewById(R.id.iv_channel_logo);
        
        volBrightLayout = findViewById(R.id.volume_brightness_layout);
        controlIcon = findViewById(R.id.control_icon);
        controlText = findViewById(R.id.control_text);
        tvSpeed = findViewById(R.id.tv_speed);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        channelList = (ArrayList<Channel>) getIntent().getSerializableExtra("channelList");
        currentPosition = getIntent().getIntExtra("position", 0);

        if (channelList != null && !channelList.isEmpty()) {
            playChannel(currentPosition);
        } else {
            // যদি লিস্ট না থাকে, তবে সিঙ্গেল ইউআরএল প্লে করার চেষ্টা করবে
            String singleUrl = getIntent().getStringExtra("url");
            String singleName = getIntent().getStringExtra("name");
            if (singleUrl != null) {
                channelList = new ArrayList<>();
                channelList.add(new Channel(singleName, singleUrl, ""));
                playChannel(0);
            }
        }

        btnNext.setOnClickListener(v -> playNextChannel());
        btnPrev.setOnClickListener(v -> playPreviousChannel());
        btnInfo.setOnClickListener(v -> showChannelInfo());

        setupGestures();
    }

    private void showChannelInfo() {
        if (channelList == null || currentPosition < 0 || currentPosition >= channelList.size()) return;
        Channel currentChannel = channelList.get(currentPosition);

        View dialogView = getLayoutInflater().inflate(R.layout.custom_info_dialog, null);
        TextView tvName = dialogView.findViewById(R.id.dialog_channel_name);
        TextView tvUrl = dialogView.findViewById(R.id.dialog_channel_url);
        TextView tvLogo = dialogView.findViewById(R.id.dialog_logo_url);
        android.widget.Button btnCopyUrl = dialogView.findViewById(R.id.btn_copy_url);
        android.widget.Button btnCopyLogo = dialogView.findViewById(R.id.btn_copy_logo);
        android.widget.Button btnClose = dialogView.findViewById(R.id.btn_close);

        tvName.setText("Name: " + currentChannel.getName());
        tvUrl.setText("URL: " + currentChannel.getUrl());
        tvLogo.setText("Logo URL: " + currentChannel.getLogoUrl());

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCopyUrl.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Channel URL", currentChannel.getUrl());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(PlayerActivity.this, "URL Copied", Toast.LENGTH_SHORT).show();
        });

        btnCopyLogo.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Logo URL", currentChannel.getLogoUrl());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(PlayerActivity.this, "Logo URL Copied", Toast.LENGTH_SHORT).show();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void playNextChannel() {
        if (channelList != null && currentPosition < channelList.size() - 1) {
            currentPosition++;
            playChannel(currentPosition);
        } else {
            Toast.makeText(this, "Last Channel", Toast.LENGTH_SHORT).show();
        }
    }

    private void playPreviousChannel() {
        if (channelList != null && currentPosition > 0) {
            currentPosition--;
            playChannel(currentPosition);
        } else {
            Toast.makeText(this, "First Channel", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSpeed() {
        long currentBytes = android.net.TrafficStats.getTotalRxBytes();
        if (lastBytes != 0) {
            long bytesPerSecond = currentBytes - lastBytes;
            String speedText;
            if (bytesPerSecond < 1024) {
                speedText = bytesPerSecond + " B/s";
            } else if (bytesPerSecond < 1024 * 1024) {
                speedText = (bytesPerSecond / 1024) + " KB/s";
            } else {
                speedText = String.format("%.2f MB/s", (float) bytesPerSecond / (1024 * 1024));
            }
            runOnUiThread(() -> tvSpeed.setText(speedText));
        }
        lastBytes = currentBytes;
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (Math.abs(distanceY) > Math.abs(distanceX)) {
                    float deltaY = e1.getY() - e2.getY();
                    float width = playerView.getWidth();
                    if (e1.getX() < width / 2) {
                        adjustBrightness(deltaY);
                    } else {
                        adjustVolume(deltaY);
                    }
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    if (e1.getX() - e2.getX() > 100) {
                        playNextChannel();
                        return true;
                    } else if (e2.getX() - e1.getX() > 100) {
                        playPreviousChannel();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (controlsLayout.getVisibility() == View.VISIBLE) {
                    controlsLayout.setVisibility(View.GONE);
                } else {
                    controlsLayout.setVisibility(View.VISIBLE);
                    hideHandler.removeCallbacks(hideRunnable);
                    hideHandler.postDelayed(hideRunnable, 3000);
                }
                return true;
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                volBrightLayout.setVisibility(View.GONE);
            }
            return true;
        });
    }

    private void adjustVolume(float deltaY) {
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int change = (int) (deltaY / 50);
        int newVol = Math.max(0, Math.min(maxVol, currentVol + change));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
        volBrightLayout.setVisibility(View.VISIBLE);
        controlIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        int percent = (newVol * 100) / maxVol;
        controlText.setText("Volume: " + percent + "%");
    }

    private void adjustBrightness(float deltaY) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float brightness = lp.screenBrightness;
        if (brightness < 0) brightness = 0.5f;
        float change = deltaY / 1000;
        lp.screenBrightness = Math.max(0.01f, Math.min(1.0f, brightness + change));
        getWindow().setAttributes(lp);
        volBrightLayout.setVisibility(View.VISIBLE);
        controlIcon.setImageResource(android.R.drawable.ic_menu_compass);
        int percent = (int) (lp.screenBrightness * 100);
        controlText.setText("Brightness: " + percent + "%");
    }

    private void playChannel(int position) {
        if (channelList == null || position < 0 || position >= channelList.size()) return;
        
        Channel channel = channelList.get(position);
        tvChannelName.setText(channel.getName());
        tvChannelName.setVisibility(View.VISIBLE);

        if (channel.getLogoUrl() != null && !channel.getLogoUrl().isEmpty()) {
            Glide.with(this)
                    .load(channel.getLogoUrl())
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .into(ivChannelLogo);
        } else {
            ivChannelLogo.setImageResource(R.mipmap.ic_launcher);
        }

        controlsLayout.setVisibility(View.VISIBLE);
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, 3000);
        
        speedHandler.removeCallbacks(speedRunnable);
        speedHandler.post(speedRunnable);

        if (player != null) {
            player.release();
        }

        // ডিফল্ট হেডার সেট করা যা অনেক সার্ভারে প্রয়োজনীয়
        java.util.Map<String, String> defaultRequestProperties = new java.util.HashMap<>();
        String url = channel.getUrl();

        // Toffee বা অন্যান্য লিঙ্কের জন্য স্পেশাল ইউজার এজেন্ট
        String userAgent = "VLC/3.0.18 LibVLC/3.0.18";
        
        if (url != null && url.contains("toffeelive.com")) {
            userAgent = "Toffee (Android; 10; SM-G975F)";
            defaultRequestProperties.put("X-VIDEO-TOKEN", ""); // অনেক সময় খালি টোকেন কাজ করে
            defaultRequestProperties.put("Origin", "https://toffeelive.com");
        }

        // যদি URL-এর সাথে হেডার থাকে (যেমন: http://link.m3u8|User-Agent=VLC)
        if (url != null && url.contains("|")) {
            String[] parts = url.split("\\|");
            url = parts[0];
            for (int i = 1; i < parts.length; i++) {
                String[] headerPart = parts[i].split("=");
                if (headerPart.length == 2) {
                    defaultRequestProperties.put(headerPart[0], headerPart[1]);
                    if (headerPart[0].equalsIgnoreCase("User-Agent")) {
                        userAgent = headerPart[1];
                    }
                }
            }
        }

        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(defaultRequestProperties);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(httpDataSourceFactory))
                .build();

        playerView.setPlayer(player);

        MediaItem mediaItem = MediaItem.fromUri(url);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    progressBar.setVisibility(View.VISIBLE);
                } else if (playbackState == Player.STATE_READY) {
                    progressBar.setVisibility(View.GONE);
                } else if (playbackState == Player.STATE_ENDED) {
                    Toast.makeText(PlayerActivity.this, "Stream Ended", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                progressBar.setVisibility(View.GONE);
                String errorMsg = "Playback Error: ";
                if (error.errorCode == com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    errorMsg += "Server returned 403/404.";
                } else if (error.errorCode == com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                    errorMsg += "Network Connection Failed";
                } else {
                    errorMsg += error.getMessage();
                }
                Toast.makeText(PlayerActivity.this, errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Window window = getWindow();
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        speedHandler.removeCallbacks(speedRunnable);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
