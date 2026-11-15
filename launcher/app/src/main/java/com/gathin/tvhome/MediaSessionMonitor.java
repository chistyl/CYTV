package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads active Android media sessions for the optional Home-screen media surface.
 * Notification access is supplied by the launcher's existing GuardRevival listener.
 */
final class MediaSessionMonitor {
    interface Listener {
        void onMediaSessionChanged(MediaController controller, MediaMetadata metadata,
                                   PlaybackState playbackState);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionManager manager;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    private final List<MediaController> observed = new ArrayList<>();
    private boolean running;

    MediaSessionMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (running) return;
        running = true;
        manager = context.getSystemService(MediaSessionManager.class);
        if (manager == null) {
            publish(null);
            return;
        }
        sessionsListener = controllers -> {
            observe(controllers);
            publishBest(controllers);
        };
        try {
            ComponentName notificationListener = new ComponentName(context, GuardRevival.class);
            manager.addOnActiveSessionsChangedListener(sessionsListener, notificationListener, main);
            observe(manager.getActiveSessions(notificationListener));
            publishBest(manager.getActiveSessions(notificationListener));
        } catch (SecurityException | IllegalStateException denied) {
            // Notification access may not be enabled on this TV yet. Settings UI handles guidance.
            publish(null);
        }
    }

    void stop() {
        if (!running) return;
        running = false;
        if (manager != null && sessionsListener != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (RuntimeException ignored) { }
        }
        for (MediaController controller : observed) {
            try { controller.unregisterCallback(controllerCallback); }
            catch (RuntimeException ignored) { }
        }
        observed.clear();
        sessionsListener = null;
        manager = null;
    }

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) {
            refresh();
        }
        @Override public void onPlaybackStateChanged(PlaybackState state) {
            refresh();
        }
        @Override public void onSessionDestroyed() {
            refresh();
        }
    };

    private void observe(List<MediaController> controllers) {
        List<MediaController> next = controllers == null ? new ArrayList<>() : controllers;
        for (MediaController old : new ArrayList<>(observed)) {
            if (!next.contains(old)) {
                try { old.unregisterCallback(controllerCallback); } catch (RuntimeException ignored) { }
                observed.remove(old);
            }
        }
        for (MediaController controller : next) {
            if (!observed.contains(controller)) {
                observed.add(controller);
                try { controller.registerCallback(controllerCallback, main); }
                catch (RuntimeException ignored) { }
            }
        }
    }

    private void refresh() {
        if (!running || manager == null) return;
        try {
            ComponentName component = new ComponentName(context, GuardRevival.class);
            List<MediaController> active = manager.getActiveSessions(component);
            observe(active);
            publishBest(active);
        } catch (SecurityException | IllegalStateException denied) {
            publish(null);
        }
    }

    private void publishBest(List<MediaController> controllers) {
        MediaController best = null;
        PlaybackState bestState = null;
        MediaMetadata bestMetadata = null;
        long bestScore = Long.MIN_VALUE;
        if (controllers != null) for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();
            if (state == null && metadata == null) continue;
            int playback = state == null ? PlaybackState.STATE_NONE : state.getState();
            long priority = playback == PlaybackState.STATE_PLAYING ? 3L
                    : playback == PlaybackState.STATE_BUFFERING ? 2L
                    : playback == PlaybackState.STATE_PAUSED ? 1L : 0L;
            long recency = state == null ? 0L : Math.max(0L, state.getLastPositionUpdateTime());
            long score = priority * 1_000_000_000_000_000L + recency;
            if (score > bestScore) {
                bestScore = score;
                best = controller;
                bestState = state;
                bestMetadata = metadata;
            }
        }
        publish(best, bestMetadata, bestState);
    }

    private void publish(MediaController controller) {
        publish(controller, controller == null ? null : controller.getMetadata(),
                controller == null ? null : controller.getPlaybackState());
    }

    private void publish(MediaController controller, MediaMetadata metadata, PlaybackState state) {
        if (listener != null) listener.onMediaSessionChanged(controller, metadata, state);
    }
}
