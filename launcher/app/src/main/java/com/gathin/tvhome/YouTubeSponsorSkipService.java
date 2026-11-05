package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * User-enabled foreground monitor for SponsorBlock on the paired YouTube TV.
 * It only seeks when a fresh Lounge playback-position AND playing-state event
 * has been received for the current video.
 */
public final class YouTubeSponsorSkipService extends Service {
    static final String ACTION_ENABLE = "com.gathin.tvhome.youtube.ENABLE_SPONSOR_SKIP";
    static final String ACTION_DISABLE = "com.gathin.tvhome.youtube.DISABLE_SPONSOR_SKIP";
    static final String PREFS = "youtube_sponsor_skip_state";
    static final String PREF_ENABLED = "enabled";
    static final String PREF_STATUS = "status";

    private static final String CHANNEL_ID = "youtube_sponsor_skip";
    private static final int NOTIFICATION_ID = 7318;
    private volatile boolean stopping;
    private volatile YouTubeLoungeClient.MonitorHandle activeMonitor;
    private Thread worker;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_ENABLE : intent.getAction();
        if (ACTION_DISABLE.equals(action)) {
            stopping = true;
            YouTubeLoungeClient.MonitorHandle monitor = activeMonitor;
            if (monitor != null) monitor.stop();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_ENABLED, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!YouTubePairingClient.hasSavedCredentials(this)) {
            setStatus("請先連接 YouTube TV");
            stopSelf();
            return START_NOT_STICKY;
        }

        stopping = false;
        startAsForeground();
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(this::monitorLoop, "CYTV-SponsorBlock-Monitor");
            worker.start();
        }
        return START_STICKY;
    }

    private void monitorLoop() {
        while (!stopping) {
            YouTubeLoungeClient.MonitorHandle monitor = new YouTubeLoungeClient.MonitorHandle();
            activeMonitor = monitor;
            try {
                YouTubeLoungeClient.Session session = YouTubeLoungeClient.connect(this, monitor);
                setStatus("已連接電視，正在監控播放");
                String videoId = session.initialVideoId;
                String segmentsVideoId = "";
                // Playback progress and state may arrive in separate Lounge polls.
                // Keep the latest values for this video only; reset on video change.
                PlaybackEventAccumulator playback = new PlaybackEventAccumulator();
                playback.recordPosition(session.initialCurrentTime, session.initialDuration);
                playback.recordPlayerState(session.initialPlayerState);
                List<SponsorBlockClient.Segment> segments = java.util.Collections.emptyList();
                Set<String> skipped = new HashSet<>();
                long handledAdEventVersion = session.adEventVersion;
                long handledAdStartVersion = session.adStartVersion;
                boolean adActive = session.adActive;
                boolean skipAttemptedForCurrentAd = false;

                if (isValidVideoId(videoId) && session.initialCurrentTime >= 0
                        && "1".equals(session.initialPlayerState)) {
                    segments = loadSegments(videoId, monitor);
                    segmentsVideoId = videoId;
                    skipIfNeeded(session, videoId, segments, skipped,
                            session.initialCurrentTime, session.initialPlayerState,
                            session.initialDuration, monitor);
                }

                while (!stopping && !monitor.isStopped()) {
                    YouTubeLoungeClient.SubscriptionResult event =
                            YouTubeLoungeClient.subscribeOnce(this, session, monitor);
                    if (!event.httpAccepted) {
                        throw new IllegalStateException("YouTube Lounge 未接受播放事件訂閱。");
                    }
                    // A clean EOF with no events can otherwise create a tight reconnect loop.
                    if (event.eventCount == 0 && !event.timedOut) {
                        try { Thread.sleep(900); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            stopping = true;
                            break;
                        }
                    }

                    // Process ad events before content playback updates. Ad positions belong
                    // to a separate timeline and must never reach SponsorBlock's clock.
                    if (session.adEventVersion != handledAdEventVersion) {
                        if (session.adStartVersion != handledAdStartVersion) {
                            playback.reset();
                            skipAttemptedForCurrentAd = false;
                        }
                        handledAdEventVersion = session.adEventVersion;
                        handledAdStartVersion = session.adStartVersion;
                        adActive = session.adActive;
                    }

                    // A player-state event newer than the last ad event is required before
                    // considering the ad finished. HTTP 200 from skipAd is not proof of success.
                    if (adActive && session.lastPlaybackEventId > session.adLastEventId
                            && event.hasFreshPlayerState) {
                        boolean adSkipped = "1082".equals(event.playerState);
                        String expectedContentVideoId = session.adContentVideoId.isEmpty()
                                ? videoId : session.adContentVideoId;
                        boolean contentResumed = "1".equals(event.playerState)
                                && event.hasFreshVideoId
                                && isValidVideoId(event.videoId)
                                && event.videoId.equals(expectedContentVideoId);
                        if (adSkipped || contentResumed) {
                            session.clearAdState();
                            adActive = false;
                            skipAttemptedForCurrentAd = false;
                            handledAdEventVersion = session.adEventVersion;
                            handledAdStartVersion = session.adStartVersion;
                            android.util.Log.i("CYTV-YouTubeAdSkip",
                                    adSkipped ? "Lounge confirmed AdSkipped" :
                                            "Lounge confirmed content playback resumed");
                        }
                    }

                    if (adActive) {
                        if (!skipAttemptedForCurrentAd
                                && Boolean.TRUE.equals(session.adSkippable)
                                && Boolean.TRUE.equals(session.adSkipEnabled)
                                && !Boolean.TRUE.equals(session.adBumper)) {
                            // Lock before sending: duplicate/replayed events cannot issue a
                            // second skipAd for the same ad, even if the request fails.
                            skipAttemptedForCurrentAd = true;
                            try {
                                String response = YouTubeLoungeClient.skipAd(this, session);
                                android.util.Log.i("CYTV-YouTubeAdSkip",
                                        "skipAd command accepted by Lounge; awaiting TV confirmation: "
                                                + response);
                            } catch (Exception commandError) {
                                android.util.Log.w("CYTV-YouTubeAdSkip",
                                        "skipAd command failed; will not retry this ad",
                                        commandError);
                            }
                        }
                        // SponsorBlock seekTo must stay idle while the ad timeline is active.
                        continue;
                    }

                    if (event.hasFreshVideoId && isValidVideoId(event.videoId)
                            && !event.videoId.equals(videoId)) {
                        videoId = event.videoId;
                        segmentsVideoId = "";
                        segments = java.util.Collections.emptyList();
                        skipped.clear();
                        playback.reset();
                        setStatus("偵測到新影片，正在載入贊助片段");
                    }

                    if (event.hasFreshPosition) {
                        playback.recordPosition(event.currentTime, event.duration);
                    }
                    if (event.hasFreshPlayerState) {
                        playback.recordPlayerState(event.playerState);
                    }
                    // Ignore unrelated Lounge notifications when both cached values are old;
                    // only a new progress or player-state update should trigger evaluation.
                    if ((event.hasFreshPosition || event.hasFreshPlayerState)
                            && playback.isUsableForAutoSkip() && isValidVideoId(videoId)) {
                        if (!videoId.equals(segmentsVideoId)) {
                            segments = loadSegments(videoId, monitor);
                            segmentsVideoId = videoId;
                        }
                        skipIfNeeded(session, videoId, segments, skipped,
                                playback.currentTime, playback.playerState,
                                playback.duration, monitor);
                    }
                }
            } catch (CancellationException cancelled) {
                if (!stopping) setStatus("監控已中斷，準備重新連線");
            } catch (Exception error) {
                if (!stopping) {
                    String message = error.getMessage();
                    setStatus("連線中斷，正在重新連線");
                    android.util.Log.w("CYTV-SponsorBlock",
                            message == null ? "SponsorBlock monitor error" : message);
                }
            } finally {
                monitor.stop();
                if (activeMonitor == monitor) activeMonitor = null;
            }

            if (!stopping) {
                try { Thread.sleep(3500); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    stopping = true;
                }
            }
        }
    }

    private List<SponsorBlockClient.Segment> loadSegments(
            String videoId, YouTubeLoungeClient.MonitorHandle monitor) throws Exception {
        SponsorBlockClient.LookupResult result =
                SponsorBlockClient.lookupSponsorSegmentsDetailed(videoId, monitor);
        return result.segments;
    }

    private void skipIfNeeded(YouTubeLoungeClient.Session session, String videoId,
                              List<SponsorBlockClient.Segment> segments, Set<String> skipped,
                              double currentTime, String playerState, double duration,
                              YouTubeLoungeClient.MonitorHandle monitor) throws Exception {
        if (stopping || monitor.isStopped() || !"1".equals(playerState)
                || !Double.isFinite(currentTime) || currentTime < 0) return;

        SponsorBlockClient.Segment segment =
                SponsorBlockClient.findSegmentAtPosition(segments, currentTime);
        if (segment == null) return;

        String segmentKey = videoId + ":" + (segment.uuid.isEmpty()
                ? segment.startSeconds + "-" + segment.endSeconds : segment.uuid);
        if (skipped.contains(segmentKey)) return;

        double target = segment.endSeconds + 0.35d;
        if (Double.isFinite(duration) && duration > 0) {
            target = Math.min(target, Math.max(0d, duration - 0.15d));
        }
        if (!Double.isFinite(target) || target <= currentTime + 0.05d) {
            skipped.add(segmentKey);
            return;
        }

        // Mark only after YouTube accepts the command; a failed seek can be retried.
        String response = YouTubeLoungeClient.seekTo(this, session, target);
        skipped.add(segmentKey);
        setStatus("已自動略過贊助片段");
        android.util.Log.i("CYTV-SponsorBlock",
                "Skipped video=" + videoId + " segment=" + segmentKey + " " + response);
    }

    private boolean isValidVideoId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{11}");
    }

    private void setStatus(String status) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_STATUS, status).apply();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(status));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "YouTube 贊助片段略過", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("監控已配對的 YouTube TV 播放狀態並略過贊助片段");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String status) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("YouTube 贊助片段略過")
                    .setContentText(status)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build();
        }
        return new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("YouTube 贊助片段略過")
                .setContentText(status)
                .setOngoing(true)
                .build();
    }

    private void startAsForeground() {
        Notification notification = buildNotification("正在連接 YouTube TV");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override public void onDestroy() {
        stopping = true;
        YouTubeLoungeClient.MonitorHandle monitor = activeMonitor;
        if (monitor != null) monitor.stop();
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
