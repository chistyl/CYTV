package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads small preview frames for the dynamic-wallpaper theme picker.
 * Network extraction is always off the UI thread and bounded to two workers.
 */
final class DynamicWallpaperThumbnailLoader {
    private static final int MAX_CACHE_KB = 12 * 1024;
    private static final int PREVIEW_WIDTH = 480;
    private static final int PREVIEW_HEIGHT = 270;

    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache =
            new LruCache<String, Bitmap>(MAX_CACHE_KB) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return Math.max(1, value.getByteCount() / 1024);
                }
            };
    private volatile boolean closed;

    void load(DynamicWallpaperCatalog.Video video, ImageView target) {
        if (video == null || target == null || closed) return;
        target.setTag(video.id);
        Bitmap cached = cache.get(video.id);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        workers.execute(() -> {
            Bitmap frame = extractFrame(video.streamUrl);
            if (frame == null || closed) return;
            cache.put(video.id, frame);
            main.post(() -> {
                if (!closed && video.id.equals(target.getTag())) {
                    target.setImageBitmap(frame);
                }
            });
        });
    }

    private Bitmap extractFrame(String url) {
        if (url == null || url.isEmpty()) return null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(url, Collections.emptyMap());
            Bitmap frame = retriever.getFrameAtTime(1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) return null;
            int width = frame.getWidth();
            int height = frame.getHeight();
            if (width <= 0 || height <= 0) {
                frame.recycle();
                return null;
            }
            float scale = Math.min((float) PREVIEW_WIDTH / width,
                    (float) PREVIEW_HEIGHT / height);
            if (scale >= 1f) return frame;
            Bitmap scaled = Bitmap.createScaledBitmap(frame,
                    Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)), true);
            if (scaled != frame) frame.recycle();
            return scaled;
        } catch (RuntimeException | java.io.IOException error) {
            return null;
        } finally {
            try { retriever.release(); } catch (RuntimeException ignored) { }
        }
    }

    void close() {
        closed = true;
        workers.shutdownNow();
        main.removeCallbacksAndMessages(null);
        cache.evictAll();
    }
}
