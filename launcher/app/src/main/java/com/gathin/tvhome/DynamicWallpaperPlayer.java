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

/** Media3 wallpaper layer with per-video AVC -> HEVC -> UHD source fallback and detailed diagnostics. */
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
    private final Runnable firstFrameTimeout = () -> {
        if (released || player == null || firstFrameRendered) return;
        String detail = "No first frame after " + FIRST_FRAME_TIMEOUT_MS + "ms"
                + ", theme=" + activeThemeId + ", video=" + currentVideoId()
                + ", source=" + currentSourceUrl();
        Log.e(TAG, detail);
        tryNextSource("FIRST_FRAME_TIMEOUT", detail);
    };

    private ExoPlayer player;
    private boolean released;
    private boolean firstFrameRendered;
    private String activeThemeId = "";
    private List<DynamicWallpaperCatalog.Video> activeVideos = new ArrayList<>();
    private int videoIndex;
    private int variantIndex;
    private int failedSourceCount;

    DynamicWallpaperPlayer(Context context, FrameLayout root, Listener listener) {
        appContext = context.getApplicationContext();
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
        root.addView(videoLayer, Math.min(1, root.getChildCount()),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    boolean playTheme(DynamicWallpaperCatalog catalog, DynamicWallpaperCatalog.Theme theme) {
        if (released || catalog == null || theme == null) return false;
        List<DynamicWallpaperCatalog.Video> videos = catalog.videosFor(theme);
        if (videos.isEmpty()) {
            Log.e(TAG, "Theme has no videos: " + theme.id);
            return false;
        }
        try {
            ensurePlayer();
            activeThemeId = theme.id;
            activeVideos = new ArrayList<>(videos);
            videoIndex = 0;
            variantIndex = 0;
            failedSourceCount = 0;
            firstFrameRendered = false;
            videoLayer.setVisibility(View.VISIBLE);
            videoLayer.setAlpha(0f);
            Log.i(TAG, "Starting theme=" + theme.id + ", videos=" + videos.size());
            startCurrentSource("theme-start");
            return true;
        } catch (Exception error) {
            Log.e(TAG, "Unable to start theme " + theme.id, error);
            revealStillWallpaper();
            notifyError("動態桌布啟動失敗：" + error.getClass().getSimpleName()
                    + "，詳細原因已寫入診斷紀錄");
            return false;
        }
    }

    void start() {
        if (!released && player != null) player.setPlayWhenReady(true);
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

    String activeThemeId() { return activeThemeId; }

    private void ensurePlayer() {
        if (player != null) return;
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 9; CYTV) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(java.util.Collections.singletonMap(
                        "Accept", "video/quicktime,video/mp4,application/octet-stream,*/*"));
        CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                .setCache(cache())
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        player = new ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(cacheFactory)).build();
        player.setVolume(0f);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.setVideoTextureView(textureView);
        player.addListener(new Player.Listener() {
            @Override public void onVideoSizeChanged(VideoSize size) {
                if (released || size.height <= 0 || size.width <= 0) return;
                float ratio = size.width * size.pixelWidthHeightRatio / (float) size.height;
                if (ratio > 0f && Float.isFinite(ratio)) aspectFrame.setAspectRatio(ratio);
            }
            @Override public void onRenderedFirstFrame() {
                if (released) return;
                firstFrameRendered = true;
                mainHandler.removeCallbacks(firstFrameTimeout);
                videoLayer.setVisibility(View.VISIBLE);
                videoLayer.animate().alpha(1f).setDuration(220L).start();
                Log.i(TAG, "First frame rendered: theme=" + activeThemeId + ", video="
                        + currentVideoId() + ", format=" + variantName() + ", url=" + currentSourceUrl());
                if (listener != null) listener.onFirstFrame();
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (!released && state == Player.STATE_ENDED) advanceToNextVideo();
            }
            @Override public void onPlayerError(PlaybackException error) {
                String detail = "code=" + error.errorCode + "/" + error.getErrorCodeName()
                        + ", theme=" + activeThemeId + ", video=" + currentVideoId()
                        + ", format=" + variantName() + ", url=" + currentSourceUrl()
                        + ", cause=" + causeSummary(error);
                Log.e(TAG, "Aerial source failed: " + detail, error);
                tryNextSource(error.getErrorCodeName(), detail);
            }
        });
    }

    private void startCurrentSource(String reason) {
        if (released || player == null || activeVideos.isEmpty()) return;
        String url = currentSourceUrl();
        if ("none".equals(url)) {
            advanceToNextVideo();
            return;
        }
        firstFrameRendered = false;
        mainHandler.removeCallbacks(firstFrameTimeout);
        Log.i(TAG, "Try source: reason=" + reason + ", theme=" + activeThemeId
                + ", video=" + currentVideoId() + ", format=" + variantName() + ", url=" + url);
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.setPlayWhenReady(true);
        mainHandler.postDelayed(firstFrameTimeout, FIRST_FRAME_TIMEOUT_MS);
    }

    private void tryNextSource(String reason, String detail) {
        if (released || activeVideos.isEmpty()) return;
        mainHandler.removeCallbacks(firstFrameTimeout);
        failedSourceCount++;
        List<String> urls = variants(activeVideos.get(videoIndex));
        if (variantIndex + 1 < urls.size()) {
            variantIndex++;
            Log.w(TAG, "Fallback after " + reason + ": " + detail);
            startCurrentSource("fallback-after-" + reason);
            return;
        }
        if (videoIndex + 1 < activeVideos.size()) {
            videoIndex++;
            variantIndex = 0;
            Log.w(TAG, "Formats exhausted; advancing to video=" + currentVideoId());
            startCurrentSource("next-video-after-" + reason);
            return;
        }
        revealStillWallpaper();
        String summary = "所有影片來源均無法播放；已嘗試 " + failedSourceCount
                + " 個來源，主題：" + activeThemeId + "。最後錯誤：" + reason;
        Log.e(TAG, summary + "；" + detail);
        notifyError(summary + "\n\n" + detail);
    }

    private void advanceToNextVideo() {
        if (released || activeVideos.isEmpty()) return;
        mainHandler.removeCallbacks(firstFrameTimeout);
        videoIndex = (videoIndex + 1) % activeVideos.size();
        variantIndex = 0;
        startCurrentSource("playlist-next-video");
    }

    private List<String> variants(DynamicWallpaperCatalog.Video video) {
        List<String> result = new ArrayList<>(3);
        addUrl(result, video.streamUrl);
        addUrl(result, video.hevcUrl);
        addUrl(result, video.uhdUrl);
        return result;
    }
    private void addUrl(List<String> list, String url) {
        if (url != null && !url.trim().isEmpty() && !list.contains(url)) list.add(url);
    }
    private String currentVideoId() {
        if (activeVideos.isEmpty() || videoIndex < 0 || videoIndex >= activeVideos.size()) return "none";
        return activeVideos.get(videoIndex).id;
    }
    private String currentSourceUrl() {
        if (activeVideos.isEmpty() || videoIndex < 0 || videoIndex >= activeVideos.size()) return "none";
        List<String> urls = variants(activeVideos.get(videoIndex));
        return variantIndex >= 0 && variantIndex < urls.size() ? urls.get(variantIndex) : "none";
    }
    private String variantName() {
        switch (variantIndex) {
            case 0: return "2K-AVC";
            case 1: return "2K-HEVC";
            case 2: return "4K-HEVC";
            default: return "unknown";
        }
    }
    private String causeSummary(Throwable error) {
        StringBuilder result = new StringBuilder();
        Throwable current = error;
        for (int depth = 0; current != null && depth < 5; depth++, current = current.getCause()) {
            if (depth > 0) result.append(" <- ");
            result.append(current.getClass().getSimpleName()).append(": ")
                    .append(String.valueOf(current.getMessage()));
        }
        return result.toString();
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
