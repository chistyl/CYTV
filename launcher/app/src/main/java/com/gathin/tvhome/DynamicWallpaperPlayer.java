package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Independent Media3 layer for remote dynamic wallpapers.
 *
 * It is inserted directly above WallpaperPlayer's still-image view and below Home UI.
 * The still wallpaper remains visible until the first video frame is rendered, and is
 * revealed again automatically on playback failure or when the dynamic layer is stopped.
 */
final class DynamicWallpaperPlayer {
    private static final String TAG = "CYTV.DynamicPlayer";
    private static final long CACHE_BYTES = 256L * 1024L * 1024L;
    private static final long FIRST_FRAME_TIMEOUT_MS = 20_000L;
    private static volatile Cache sharedCache;

    interface Listener {
        void onPlaybackError(String message);
        void onFirstFrame();
    }

    private final Context appContext;
    private final FrameLayout root;
    private final PlayerView playerView;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable firstFrameTimeout = () -> {
        if (released || player == null || player.getPlaybackState() == Player.STATE_READY) return;
        Log.w(TAG, "No first video frame within timeout; revealing still wallpaper");
        revealStillWallpaper();
        notifyError("動態圖片載入逾時，已切回靜態桌布");
    };
    private ExoPlayer player;
    private boolean released;
    private String activeThemeId = "";

    DynamicWallpaperPlayer(Context context, FrameLayout root, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.root = root;
        this.listener = listener;
        playerView = new PlayerView(context);
        playerView.setUseController(false);
        playerView.setUseArtwork(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setFocusable(false);
        playerView.setFocusableInTouchMode(false);
        playerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        playerView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        playerView.setAlpha(0f);
        playerView.setVisibility(View.GONE);

        // WallpaperPlayer inserts its still-image ImageView at index 0. Index 1 places
        // video above that image while preserving the already-inflated Home UI above it.
        root.addView(playerView, Math.min(1, root.getChildCount()),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    boolean playTheme(DynamicWallpaperCatalog catalog, DynamicWallpaperCatalog.Theme theme) {
        if (released || catalog == null || theme == null) return false;
        List<DynamicWallpaperCatalog.Video> videos = catalog.videosFor(theme);
        if (videos.isEmpty()) return false;

        try {
            ensurePlayer();
            List<MediaItem> items = new ArrayList<>();
            for (DynamicWallpaperCatalog.Video video : videos) {
                // 2K AVC is the conservative baseline for Android TV decoder compatibility.
                if (video.streamUrl != null && !video.streamUrl.isEmpty()) {
                    items.add(MediaItem.fromUri(video.streamUrl));
                }
            }
            if (items.isEmpty()) return false;

            activeThemeId = theme.id;
            playerView.setVisibility(View.VISIBLE);
            playerView.setAlpha(0f);
            player.setMediaItems(items, true);
            player.prepare();
            player.setPlayWhenReady(true);
            mainHandler.removeCallbacks(firstFrameTimeout);
            mainHandler.postDelayed(firstFrameTimeout, FIRST_FRAME_TIMEOUT_MS);
            return true;
        } catch (Exception error) {
            Log.e(TAG, "Unable to start theme " + theme.id, error);
            revealStillWallpaper();
            notifyError("動態圖片無法啟動，已切回靜態桌布");
            return false;
        }
    }

    void start() {
        if (released || player == null) return;
        player.setPlayWhenReady(true);
    }

    void stop() {
        mainHandler.removeCallbacks(firstFrameTimeout);
        if (player != null) {
            player.setPlayWhenReady(false);
            playerView.setVisibility(View.GONE);
            playerView.setAlpha(0f);
        }
    }

    void release() {
        if (released) return;
        released = true;
        mainHandler.removeCallbacks(firstFrameTimeout);
        if (player != null) {
            player.release();
            player = null;
        }
        root.removeView(playerView);
    }

    String activeThemeId() {
        return activeThemeId;
    }

    private void ensurePlayer() {
        if (player != null) return;
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true);
        CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                .setCache(cache())
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        player = new ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(cacheFactory))
                .build();
        player.setVolume(0f);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                if (released) return;
                mainHandler.removeCallbacks(firstFrameTimeout);
                playerView.setVisibility(View.VISIBLE);
                playerView.animate().alpha(1f).setDuration(220L).start();
                if (listener != null) listener.onFirstFrame();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                mainHandler.removeCallbacks(firstFrameTimeout);
                Log.w(TAG, "Aerial playback failed; revealing still wallpaper", error);
                revealStillWallpaper();
                notifyError("動態圖片連線或播放失敗，已切回靜態桌布");
            }
        });
        playerView.setPlayer(player);
    }

    private Cache cache() {
        Cache value = sharedCache;
        if (value != null) return value;
        synchronized (DynamicWallpaperPlayer.class) {
            value = sharedCache;
            if (value == null) {
                File directory = new File(appContext.getCacheDir(), "dynamic-wallpaper-cache");
                value = new SimpleCache(directory, new LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
                        new StandaloneDatabaseProvider(appContext));
                sharedCache = value;
            }
        }
        return value;
    }

    private void revealStillWallpaper() {
        mainHandler.removeCallbacks(firstFrameTimeout);
        if (player != null) player.setPlayWhenReady(false);
        playerView.animate().cancel();
        playerView.setAlpha(0f);
        playerView.setVisibility(View.GONE);
    }

    private void notifyError(String message) {
        if (listener != null) listener.onPlaybackError(message);
    }
}
