package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolated Media3 wallpaper layer. Uses TextureView rather than SurfaceView:
 * SurfaceView is a separate system surface and can cover the launcher UI or render
 * black on some Android TV firmwares even when its parent view is transparent.
 */
final class DynamicWallpaperPlayer {
    private static final String TAG = "CYTV.DynamicPlayer";
    private static final long CACHE_BYTES = 256L * 1024L * 1024L;
    private static final long FIRST_FRAME_TIMEOUT_MS = 45_000L;
    private static volatile Cache sharedCache;

    interface Listener {
        void onPlaybackError(String message);
        void onFirstFrame();
    }

    private final Context appContext;
    private final FrameLayout root;
    private final FrameLayout videoLayer;
    private final AspectRatioFrameLayout aspectFrame;
    private final TextureView textureView;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private boolean released;
    private boolean firstFrameRendered;
    private String activeThemeId = "";

    private final Runnable firstFrameTimeout = () -> {
        if (released || player == null || firstFrameRendered) return;
        Log.w(TAG, "No first video frame within timeout; revealing still wallpaper");
        revealStillWallpaper();
        notifyError("動態圖片載入逾時（45 秒無影格），已切回靜態桌布");
    };

    DynamicWallpaperPlayer(Context context, FrameLayout root, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.root = root;
        this.listener = listener;

        videoLayer = new FrameLayout(context);
        videoLayer.setBackgroundColor(Color.TRANSPARENT);
        videoLayer.setAlpha(0f);
        videoLayer.setVisibility(View.GONE);
        videoLayer.setFocusable(false);
        videoLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        aspectFrame = new AspectRatioFrameLayout(context);
        aspectFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        textureView = new TextureView(context);
        textureView.setOpaque(false);
        aspectFrame.addView(textureView, new AspectRatioFrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoLayer.addView(aspectFrame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // WallpaperPlayer's still image is inserted at index 0. Keep this layer directly
        // above it and below all launcher UI children. TextureView obeys this View ordering.
        root.addView(videoLayer, Math.min(1, root.getChildCount()),
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
                if (video.streamUrl != null && !video.streamUrl.isEmpty())
                    items.add(MediaItem.fromUri(video.streamUrl));
            }
            if (items.isEmpty()) return false;

            activeThemeId = theme.id;
            firstFrameRendered = false;
            videoLayer.setVisibility(View.VISIBLE);
            videoLayer.setAlpha(0f);
            Log.i(TAG, "Starting theme=" + theme.id + ", videos=" + items.size()
                    + ", firstUrl=" + items.get(0).localConfiguration.uri);
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
        if (player != null) player.setPlayWhenReady(false);
        videoLayer.animate().cancel();
        videoLayer.setAlpha(0f);
        videoLayer.setVisibility(View.GONE);
    }

    void release() {
        if (released) return;
        released = true;
        mainHandler.removeCallbacks(firstFrameTimeout);
        if (player != null) {
            player.clearVideoTextureView(textureView);
            player.release();
            player = null;
        }
        root.removeView(videoLayer);
    }

    String activeThemeId() {
        return activeThemeId;
    }

    private void ensurePlayer() {
        if (player != null) return;
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 9; CYTV) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(new java.util.HashMap<String, String>() {{
                    put("Accept", "video/quicktime,video/mp4,application/octet-stream,*/*");
                    put("Connection", "keep-alive");
                }});
        CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                .setCache(cache())
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        player = new ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(cacheFactory))
                .build();
        player.setVolume(0f);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setVideoTextureView(textureView);
        player.addListener(new Player.Listener() {
            @Override public void onVideoSizeChanged(VideoSize videoSize) {
                if (released || videoSize.height <= 0 || videoSize.width <= 0) return;
                float videoAspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio
                        / (float) videoSize.height;
                if (videoAspectRatio > 0f && Float.isFinite(videoAspectRatio)) {
                    aspectFrame.setAspectRatio(videoAspectRatio);
                    Log.i(TAG, "Video aspect ratio updated: " + videoAspectRatio);
                }
            }

            @Override public void onRenderedFirstFrame() {
                if (released) return;
                firstFrameRendered = true;
                mainHandler.removeCallbacks(firstFrameTimeout);
                videoLayer.setVisibility(View.VISIBLE);
                videoLayer.animate().alpha(1f).setDuration(220L).start();
                if (listener != null) listener.onFirstFrame();
            }

            @Override public void onPlayerError(PlaybackException error) {
                mainHandler.removeCallbacks(firstFrameTimeout);
                String detail = "code=" + error.errorCode + "/" + error.getErrorCodeName()
                        + ", cause=" + (error.getCause() == null ? "none"
                        : error.getCause().getClass().getSimpleName() + ": " + error.getCause().getMessage());
                Log.e(TAG, "Aerial playback failed: " + detail, error);
                revealStillWallpaper();
                notifyError("動態圖片播放失敗：" + error.getErrorCodeName() + "，已切回靜態桌布");
            }
        });
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
        videoLayer.animate().cancel();
        videoLayer.setAlpha(0f);
        videoLayer.setVisibility(View.GONE);
    }

    private void notifyError(String message) {
        if (listener != null) listener.onPlaybackError(message);
    }
}
