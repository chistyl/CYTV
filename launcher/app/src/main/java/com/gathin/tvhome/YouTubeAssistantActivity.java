package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV-first YouTube helper diagnostics. Pairing, Lounge bind and bounded event subscription
 * are diagnostic stages only; SponsorBlock automation and remote commands are not enabled.
 */
public class YouTubeAssistantActivity extends Activity {
    private static final int BG = Color.rgb(12, 14, 19);
    private static final int PANEL = Color.rgb(28, 31, 39);
    private static final int FOCUS = Color.rgb(245, 247, 250);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(157, 164, 177);

    private volatile boolean monitoring = false;
    private volatile YouTubeLoungeClient.MonitorHandle monitorHandle;
    private volatile Thread monitorThread;
    private TextView monitorStatusView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(52), dp(30), dp(52), dp(26));

        TextView back = new TextView(this);
        back.setText("‹  返回");
        back.setTextColor(MUTED);
        back.setTextSize(16);
        back.setFocusable(true);
        back.setFocusableInTouchMode(true);
        back.setPadding(dp(12), dp(8), dp(18), dp(8));
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-2, -2));

        TextView title = new TextView(this);
        title.setText("YouTube 廣告助手");
        title.setTextColor(TEXT);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(8);
        root.addView(title, titleLp);

        TextView intro = new TextView(this);
        intro.setText("管理 YouTube TV 連線與 SponsorBlock 片段略過");
        intro.setTextColor(MUTED);
        intro.setTextSize(16);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2);
        introLp.topMargin = dp(8);
        introLp.bottomMargin = dp(24);
        root.addView(intro, introLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        addPairingRow(rows);
        addAvailabilityRow(rows);
        addLoungeConnectRow(rows);
        addLoungeSubscribeRow(rows);
        addMonitorRows(rows);
        addRow(rows, "SponsorBlock 片段略過", "尚未啟用", "尚未連接 SponsorBlock API，目前不會自動略過影片片段。");
        addRow(rows, "自動略過贊助片段", "功能準備中", "待配對與片段偵測完成後加入。");
        addRow(rows, "連線狀態與診斷", "尚無連線資料", "配對功能完成後，這裡會顯示裝置、連線與錯誤資訊。");

        TextView note = new TextView(this);
        note.setText("目前可測試 YouTube TV 配對、Lounge 握手與單次事件訂閱；尚未啟用遙控命令或自動略過。");
        note.setTextColor(MUTED);
        note.setTextSize(13);
        note.setPadding(dp(4), dp(16), dp(4), 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        back.requestFocus();
    }


    private void addPairingRow(LinearLayout parent) {
        String savedCode = getPreferences(MODE_PRIVATE).getString("youtube_tv_device_code", "");
        String stateText = YouTubePairingClient.hasSavedCredentials(this) ? "已取得配對憑證（尚未建立控制連線）" : (savedCode.isEmpty() ? "尚未輸入裝置代碼" : "裝置代碼已保存（尚未配對）");
        LinearLayout card = createCard(parent, "YouTube TV 配對", stateText);
        card.setOnClickListener(v -> showDeviceCodeDialog());
    }

    private LinearLayout createCard(LinearLayout parent, String heading, String status) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setPadding(dp(20), dp(15), dp(20), dp(15));
        card.setBackground(background(PANEL, dp(14)));
        card.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(background(focused ? FOCUS : PANEL, dp(14)));
            setTextColorRecursive((ViewGroup) v, focused ? Color.rgb(20, 23, 29) : TEXT,
                    focused ? Color.rgb(70, 76, 88) : MUTED);
            v.setScaleX(focused ? 1.015f : 1f);
            v.setScaleY(focused ? 1.015f : 1f);
        });
        TextView name = new TextView(this);
        name.setText(heading);
        name.setTextColor(TEXT);
        name.setTextSize(19);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(name);
        TextView state = new TextView(this);
        state.setText(status);
        state.setTextColor(MUTED);
        state.setTextSize(14);
        LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(-1, -2);
        stateLp.topMargin = dp(5);
        card.addView(state, stateLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(card, lp);
        return card;
    }

    private void showDeviceCodeDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(getPreferences(MODE_PRIVATE).getString("youtube_tv_device_code", ""));
        input.setHint("輸入 YouTube TV 裝置代碼");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(18);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(background(PANEL, dp(10)));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(4));
        TextView help = new TextView(this);
        help.setText("請先在 YouTube TV App 的設定中查看裝置代碼，再使用遙控器輸入。");
        help.setTextColor(MUTED);
        help.setTextSize(14);
        box.addView(help);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.topMargin = dp(14);
        box.addView(input, inputLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("輸入 YouTube TV 裝置代碼")
                .setView(box)
                .setPositiveButton("保存代碼", (d, which) -> {
                    String code = input.getText().toString().trim();
                    if (code.isEmpty()) {
                        getPreferences(MODE_PRIVATE).edit().remove("youtube_tv_device_code").apply();
                        YouTubePairingClient.clear(this);
                        showInfo("已清除裝置代碼與配對憑證");
                    } else {
                        getPreferences(MODE_PRIVATE).edit().putString("youtube_tv_device_code", code).apply();
                        startPairing(code);
                    }
                    recreate();
                })
                .setNegativeButton("取消", (d, which) -> d.dismiss())
                .create();
        dialog.getWindow();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void addAvailabilityRow(LinearLayout parent) {
        LinearLayout card = createCard(parent, "檢查 YouTube TV 在線狀態",
                "確認電視是否仍可透過 YouTube 配對服務連線");
        card.setOnClickListener(v -> checkDeviceAvailability());
    }

    private void addLoungeConnectRow(LinearLayout parent) {
        LinearLayout card = createCard(parent, "建立 YouTube Lounge 控制連線",
                "測試 YouTube TV 控制通道的第一階段握手");
        card.setOnClickListener(v -> connectLounge());
    }

    private void addLoungeSubscribeRow(LinearLayout parent) {
        LinearLayout card = createCard(parent, "測試 Lounge 事件訂閱",
                "建立工作階段後，短時間接收 YouTube TV 事件");
        card.setOnClickListener(v -> subscribeLoungeOnce());
    }

    private void addMonitorRows(LinearLayout parent) {
        LinearLayout start = createCard(parent, "開始持續監測",
                "持續讀取播放事件與 SponsorBlock 片段狀態；不會自動跳轉");
        start.setOnClickListener(v -> startPlaybackMonitor());
        LinearLayout stop = createCard(parent, "停止持續監測",
                "中斷目前 Lounge 訂閱並停止背景監測");
        stop.setOnClickListener(v -> stopPlaybackMonitor());
        LinearLayout status = createCard(parent, "即時播放監測狀態",
                "尚未開始監測");
        monitorStatusView = (TextView) status.getChildAt(1);
    }

    private void startPlaybackMonitor() {
        if (monitoring) {
            showInfo("播放監測已在執行中。");
            return;
        }
        if (!YouTubePairingClient.hasSavedCredentials(this)) {
            showInfo("請先完成 YouTube TV 配對並取得裝置憑證。");
            return;
        }
        monitoring = true;
        monitorHandle = new YouTubeLoungeClient.MonitorHandle();
        setMonitorStatus("正在建立 YouTube Lounge 工作階段…");
        final YouTubeLoungeClient.MonitorHandle handle = monitorHandle;
        monitorThread = new Thread(() -> {
            String cachedVideoId = "";
            double latestPosition = -1;
            double latestDuration = -1;
            String latestPlayerState = "";
            java.util.List<SponsorBlockClient.Segment> cachedSegments =
                    new java.util.ArrayList<>();
            String previousSegmentKey = "";
            String terminalStatus = null;
            String sponsorLookupError = "";
            try {
                YouTubeLoungeClient.Session session = YouTubeLoungeClient.connect(this, handle);
                if (handle.isStopped()) return;
                setMonitorStatus("已連線，等待 YouTube 播放事件…");
                while (!handle.isStopped() && monitoring) {
                    YouTubeLoungeClient.SubscriptionResult result =
                            YouTubeLoungeClient.subscribeOnce(this, session, handle);
                    if (handle.isStopped() || !monitoring) break;

                    if (result.lastEventId > session.lastEventId) {
                        session = new YouTubeLoungeClient.Session(session.sid,
                                session.gsessionId, session.screenId, session.deviceId,
                                result.lastEventId, session.initialVideoId,
                                session.initialCurrentTime, session.initialDuration,
                                session.initialPlayerState, session.initialEventSummary);
                    }

                    if (result.videoId.matches("[A-Za-z0-9_-]{11}")
                            && !result.videoId.equals(cachedVideoId)) {
                        cachedVideoId = result.videoId;
                        try {
                            cachedSegments = SponsorBlockClient.lookupSponsorSegments(cachedVideoId, handle);
                            sponsorLookupError = "";
                        } catch (java.util.concurrent.CancellationException cancelled) {
                            throw cancelled;
                        } catch (Exception lookupError) {
                            cachedSegments = new java.util.ArrayList<>();
                            sponsorLookupError = lookupError.getMessage() == null
                                    ? "未知錯誤" : lookupError.getMessage();
                        }
                        previousSegmentKey = "";
                        latestPosition = -1;
                        latestDuration = -1;
                    }
                    if (result.currentTime >= 0) latestPosition = result.currentTime;
                    if (result.duration > 0) latestDuration = result.duration;
                    if (!result.playerState.isEmpty()) latestPlayerState = result.playerState;

                    StringBuilder status = new StringBuilder();
                    status.append(result.httpAccepted ? "Lounge：已接受訂閱" : "Lounge：尚未確認訂閱");
                    status.append("\n事件數：").append(result.eventCount)
                            .append("｜AID：").append(result.lastEventId);
                    if (!latestPlayerState.isEmpty()) {
                        status.append("\n播放狀態：").append(latestPlayerState);
                    }

                    if (!cachedVideoId.isEmpty()) {
                        status.append("\n影片 ID：").append(cachedVideoId);
                        if (latestPosition >= 0) {
                            status.append("\n播放位置：").append(String.format(
                                    java.util.Locale.US, "%.1f 秒", latestPosition));
                            if (latestDuration > 0) {
                                status.append(" / ").append(String.format(
                                        java.util.Locale.US, "%.1f 秒", latestDuration));
                            }
                            SponsorBlockClient.Segment active =
                                    SponsorBlockClient.findSegmentAtPosition(cachedSegments,
                                            latestPosition);
                            String activeKey = active == null ? "" : active.uuid;
                            if (!activeKey.equals(previousSegmentKey)) {
                                if (!activeKey.isEmpty()) {
                                    status.append("\n狀態變化：進入贊助片段（")
                                            .append(String.format(java.util.Locale.US, "%.1f–%.1f 秒",
                                                    active.startSeconds, active.endSeconds)).append("）");
                                } else if (!previousSegmentKey.isEmpty()) {
                                    status.append("\n狀態變化：離開贊助片段");
                                }
                                previousSegmentKey = activeKey;
                            }
                            status.append(active == null
                                    ? "\n目前位置：不在已知贊助片段內"
                                    : "\n目前位置：位於贊助片段");
                        } else {
                            status.append("\n尚未取得有效播放位置");
                        }
                        if (!sponsorLookupError.isEmpty()) {
                            status.append("\nSponsorBlock 查詢失敗：").append(sponsorLookupError);
                            status.append("\nLounge 監測仍會持續執行");
                        } else {
                            status.append("\nSponsorBlock 贊助片段：")
                                    .append(cachedSegments.size()).append(" 段（已快取）");
                        }
                    } else {
                        status.append("\n尚未取得有效影片 ID");
                    }
                    if (result.timedOut) status.append("\n等待逾時，將重新等待事件");
                    setMonitorStatus(status.toString());

                    if (result.eventCount == 0 && !handle.isStopped()) {
                        try { Thread.sleep(700); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (java.util.concurrent.CancellationException cancelled) {
                // Expected when the user stops monitoring.
            } catch (Exception error) {
                if (!handle.isStopped()) {
                    terminalStatus = "監測發生錯誤："
                            + (error.getMessage() == null ? "未知錯誤" : error.getMessage());
                }
            } finally {
                monitoring = false;
                if (monitorHandle == handle) monitorHandle = null;
                monitorThread = null;
                if (!isFinishing() && !isDestroyed()) {
                    if (terminalStatus != null) {
                        setMonitorStatus(terminalStatus);
                    } else {
                        setMonitorStatus(handle.isStopped() ? "監測已停止" : "監測已結束");
                    }
                }
            }
        }, "CYTV-YouTube-Monitor");
        monitorThread.start();
    }

    private void stopPlaybackMonitor() {
        monitoring = false;
        YouTubeLoungeClient.MonitorHandle handle = monitorHandle;
        if (handle != null) handle.stop();
        Thread thread = monitorThread;
        if (thread != null) thread.interrupt();
        setMonitorStatus("正在停止監測並中斷 Lounge 連線…");
    }

    private void setMonitorStatus(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (monitorStatusView != null) monitorStatusView.setText(message);
        });
    }

    @Override protected void onDestroy() {
        stopPlaybackMonitor();
        super.onDestroy();
    }

    private void subscribeLoungeOnce() {
        if (!YouTubePairingClient.hasSavedCredentials(this)) {
            showInfo("請先完成 YouTube TV 配對並取得裝置憑證。");
            return;
        }
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("測試事件訂閱")
                .setMessage("正在建立工作階段並連續接收 YouTube TV 事件，最多約 60 秒…")
                .setCancelable(false).create();
        progress.show();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        final ExecutorService eventExecutor = executor;
        Handler main = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            String message;
            boolean success = false;
            try {
                YouTubeLoungeClient.Session session = YouTubeLoungeClient.connect(this);
                // Keep the first event subscription open while getNowPlaying is sent.
                // Lounge playback updates are delivered on the backchannel, not necessarily
                // in the command acknowledgement response.
                final YouTubeLoungeClient.Session initialSession = session;
                java.util.concurrent.Future<YouTubeLoungeClient.SubscriptionResult> firstWindow =
                        eventExecutor.submit(() -> YouTubeLoungeClient.subscribeOnce(this, initialSession, 0));
                try { Thread.sleep(500); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CancellationException("診斷已中斷。");
                }
                String nowPlayingRequestStatus = YouTubeLoungeClient.requestNowPlaying(this, session);
                // Alternate CI=0 and CI=1 on the same Lounge session to compare
                // backchannel behavior without issuing playback-control commands.
                int totalEvents = 0;
                long latestAid = session.lastEventId;
                boolean httpAccepted = false;
                boolean timedOut = false;
                StringBuilder eventTrace = new StringBuilder();
                String videoId = session.initialVideoId;
                double currentTime = session.initialCurrentTime;
                double duration = session.initialDuration;
                String playerState = session.initialPlayerState;
                for (int window = 1; window <= 4; window++) {
                    int clientIndex = (window % 2 == 1) ? 0 : 1;
                    YouTubeLoungeClient.SubscriptionResult windowResult;
                    if (window == 1) {
                        windowResult = firstWindow.get(15, java.util.concurrent.TimeUnit.SECONDS);
                    } else {
                        windowResult = YouTubeLoungeClient.subscribeOnce(this, session, clientIndex);
                    }
                    httpAccepted |= windowResult.httpAccepted;
                    totalEvents += windowResult.eventCount;
                    latestAid = Math.max(latestAid, windowResult.lastEventId);
                    timedOut |= windowResult.timedOut;
                    if (windowResult.videoId.matches("[A-Za-z0-9_-]{11}")) {
                        videoId = windowResult.videoId;
                    }
                    if (windowResult.currentTime >= 0) currentTime = windowResult.currentTime;
                    if (windowResult.duration > 0) duration = windowResult.duration;
                    if (!windowResult.playerState.isEmpty()) playerState = windowResult.playerState;
                    if (eventTrace.length() > 0) eventTrace.append(" || ");
                    eventTrace.append("第").append(window).append("輪（CI=")
                            .append(clientIndex).append("）：")
                            .append(windowResult.eventSummary);
                    session = new YouTubeLoungeClient.Session(session.sid,
                            session.gsessionId, session.screenId, session.deviceId,
                            latestAid, videoId, currentTime, duration, playerState,
                            session.initialEventSummary);
                }
                final boolean anyHttpAccepted = httpAccepted;
                final boolean anyTimedOut = timedOut;
                final int collectedEvents = totalEvents;
                final long collectedAid = latestAid;
                final String collectedEventTrace = eventTrace.toString();
                final String collectedVideoId = videoId;
                final double collectedCurrentTime = currentTime;
                final double collectedDuration = duration;
                final String collectedPlayerState = playerState;
                YouTubeLoungeClient.SubscriptionResult result =
                        new YouTubeLoungeClient.SubscriptionResult(collectedEvents,
                                collectedAid, collectedEventTrace, anyTimedOut,
                                anyHttpAccepted, collectedVideoId, collectedCurrentTime,
                                collectedDuration, collectedPlayerState);
                StringBuilder report = new StringBuilder();
                report.append(result.httpAccepted
                        ? "YouTube 已接受 Lounge 訂閱 HTTP 請求。"
                        : "尚未確認 YouTube 接受 Lounge 訂閱請求。");
                report.append("\n唯讀播放狀態請求：").append(nowPlayingRequestStatus);
                report.append("\n訂閱輪數：4（CI=0、CI=1 各測 2 輪；每輪最多等待 12 秒）");
                report.append("\n收到事件數：").append(result.eventCount)
                        .append("\n最後事件編號 AID：").append(result.lastEventId)
                        .append("\n事件：").append(result.eventSummary);
                if (!result.playerState.isEmpty()) {
                    report.append("\n播放狀態：").append(result.playerState);
                }
                if (!result.videoId.isEmpty()) {
                    report.append("\n目前影片 ID：").append(result.videoId);
                    if (result.currentTime >= 0) {
                        report.append("\n目前播放位置：").append(String.format(java.util.Locale.US, "%.1f 秒", result.currentTime));
                    }
                    if (result.duration >= 0) {
                        report.append("\n影片長度：").append(String.format(java.util.Locale.US, "%.1f 秒", result.duration));
                    }
                    try {
                        SponsorBlockClient.LookupResult lookup =
                                SponsorBlockClient.lookupSponsorSegmentsDetailed(result.videoId);
                        java.util.List<SponsorBlockClient.Segment> segments = lookup.segments;
                                                report.append("\nSponsorBlock API：HTTP ").append(lookup.httpStatus)
                                .append("｜回應大小：").append(lookup.responseBytes).append(" bytes")
                                .append("｜SHA-256 前綴：").append(lookup.hashPrefix)
                                .append("｜候選影片：").append(lookup.candidateCount)
                                .append("｜符合影片 ID：").append(lookup.matchingVideoCount)
                                .append("\n候選影片 ID 樣本：")
                                .append(lookup.candidateIdSamples.isEmpty()
                                        ? "無可讀取的 videoID 欄位"
                                        : android.text.TextUtils.join("、", lookup.candidateIdSamples))
                                .append("\nSponsorBlock 贊助片段：").append(segments.size()).append(" 段");
                        if (lookup.httpStatus == 404) {
                            report.append("\n查詢判讀：API 回覆 404，該雜湊前綴目前沒有可回傳資料");
                        } else if (lookup.candidateCount > 0 && lookup.matchingVideoCount == 0) {
                            report.append("\n查詢判讀：有候選資料，但沒有符合目前影片 ID");
                        } else if (lookup.matchingVideoCount > 0 && segments.isEmpty()) {
                            report.append("\n查詢判讀：找到影片資料，但沒有 sponsor 類別片段");
                        } else if (segments.isEmpty()) {
                            report.append("\n查詢判讀：API 回覆成功，但沒有符合的影片／贊助片段");
                        } else {
                            report.append("\n查詢判讀：已取得贊助片段資料");
                        }
                        if (result.currentTime >= 0) {
                            SponsorBlockClient.Segment activeSegment =
                                    SponsorBlockClient.findSegmentAtPosition(segments, result.currentTime);
                            if (activeSegment != null) {
                                report.append("\n目前位置判定：位於贊助片段（")
                                        .append(String.format(java.util.Locale.US, "%.1f–%.1f 秒",
                                                activeSegment.startSeconds, activeSegment.endSeconds))
                                        .append("）");
                            } else {
                                report.append("\n目前位置判定：不在已知贊助片段內");
                            }
                        } else {
                            report.append("\n目前位置判定：尚未取得有效播放位置");
                        }
                        for (SponsorBlockClient.Segment segment : segments) {
                            report.append("\n").append(String.format(java.util.Locale.US,
                                    "%.1f–%.1f 秒", segment.startSeconds, segment.endSeconds));
                        }
                    } catch (Exception lookupError) {
                        report.append("\nSponsorBlock 查詢失敗：")
                                .append(lookupError.getMessage() == null ? "未知錯誤" : lookupError.getMessage());
                    }
                } else {
                    report.append("\n目前尚未取得影片 ID，暫不查詢 SponsorBlock。");
                }
                if (result.timedOut) report.append("\n\n診斷結果：等待逾時。");
                message = report.toString();
                success = true;
            } catch (Exception e) {
                message = e.getMessage() == null ? "事件訂閱失敗，請稍後重試。" : e.getMessage();
            }
            final String resultMessage = message;
            final boolean completed = success;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                new AlertDialog.Builder(this)
                        .setTitle(completed ? "事件訂閱診斷完成" : "事件訂閱失敗")
                        .setMessage(resultMessage)
                        .setNeutralButton("複製診斷資料", (d, w) -> {
                            android.content.ClipboardManager clipboard =
                                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                                        "CYTV Lounge 診斷資料", resultMessage));
                                showInfo("診斷資料已複製，請回到對話中貼上。");
                            } else {
                                showInfo("無法存取剪貼簿，請改用截圖回傳。");
                            }
                        })
                        .setPositiveButton("知道了", (d, w) -> d.dismiss()).show();
            });
            executor.shutdown();
        });
    }

    private void connectLounge() {
        if (!YouTubePairingClient.hasSavedCredentials(this)) {
            showInfo("請先完成 YouTube TV 配對並取得裝置憑證。");
            return;
        }
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("建立控制連線")
                .setMessage("正在建立 YouTube Lounge 工作階段…")
                .setCancelable(false).create();
        progress.show();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            String message;
            boolean connected = false;
            try {
                YouTubeLoungeClient.Session session = YouTubeLoungeClient.connect(this);
                message = "已取得 YouTube Lounge 工作階段識別碼。"
                        + "\n目前只完成 bind handshake，尚未訂閱事件，也尚未啟用遙控命令。"
                        + "\n最後事件編號 AID：" + session.lastEventId + "。";
                connected = true;
            } catch (Exception e) {
                message = e.getMessage() == null ? "Lounge 連線失敗，請稍後重試。" : e.getMessage();
            }
            final String result = message;
            final boolean success = connected;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                new AlertDialog.Builder(this)
                        .setTitle(success ? "Lounge 握手成功" : "Lounge 連線失敗")
                        .setMessage(result)
                        .setPositiveButton("知道了", (d, w) -> d.dismiss()).show();
            });
            executor.shutdown();
        });
    }

    private void checkDeviceAvailability() {
        if (!YouTubePairingClient.hasSavedCredentials(this)) {
            showInfo("請先完成 YouTube TV 配對並取得裝置憑證。");
            return;
        }
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("檢查裝置狀態")
                .setMessage("正在查詢 YouTube TV 在線狀態…")
                .setCancelable(false).create();
        progress.show();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            String message;
            try {
                boolean online = YouTubePairingClient.checkAvailability(this);
                message = online
                        ? "YouTube 配對服務回報裝置在線。\n這只代表裝置可用，尚未建立 Lounge 控制連線。"
                        : "YouTube 配對服務目前回報裝置離線。請確認電視已開啟 YouTube，並與本機網路連線正常。";
            } catch (Exception e) {
                message = e.getMessage() == null ? "查詢失敗，請稍後重試。" : e.getMessage();
            }
            final String result = message;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                showInfo(result);
            });
            executor.shutdown();
        });
    }

    private void startPairing(String code) {
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("正在配對")
                .setMessage("正在向 YouTube 驗證裝置代碼，請稍候…")
                .setCancelable(false).create();
        progress.show();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            String message;
            boolean success = false;
            try {
                YouTubePairingClient.PairingResult result = YouTubePairingClient.pair(code);
                YouTubePairingClient.save(this, result);
                message = "已取得 YouTube TV 配對憑證：" + result.deviceName
                        + "\n\n裝置憑證已使用 Android Keystore 加密保存。"
                        + "\n目前尚未建立 Lounge 控制連線，因此不代表已可控制電視。";
                success = true;
            } catch (Exception e) {
                message = e.getMessage() == null ? "配對失敗，請稍後重試。" : e.getMessage();
            }
            final String resultMessage = message;
            final boolean paired = success;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                new AlertDialog.Builder(this).setTitle(paired ? "取得配對憑證" : "配對失敗")
                        .setMessage(resultMessage)
                        .setPositiveButton("知道了", (d, w) -> recreate()).show();
            });
            executor.shutdown();
        });
    }
    private void showInfo(String message) {
        new AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton("知道了", (d, w) -> d.dismiss()).show();
    }

    private void addRow(LinearLayout parent, String heading, String status, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setPadding(dp(20), dp(15), dp(20), dp(15));
        card.setBackground(background(PANEL, dp(14)));
        card.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(background(focused ? FOCUS : PANEL, dp(14)));
            setTextColorRecursive((ViewGroup) v, focused ? Color.rgb(20, 23, 29) : TEXT,
                    focused ? Color.rgb(70, 76, 88) : MUTED);
            v.setScaleX(focused ? 1.015f : 1f);
            v.setScaleY(focused ? 1.015f : 1f);
        });
        card.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(heading)
                .setMessage(detail)
                .setPositiveButton("知道了", (d, w) -> d.dismiss())
                .show());

        TextView name = new TextView(this);
        name.setText(heading);
        name.setTextColor(TEXT);
        name.setTextSize(19);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(name);

        TextView state = new TextView(this);
        state.setText(status);
        state.setTextColor(MUTED);
        state.setTextSize(14);
        LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(-1, -2);
        stateLp.topMargin = dp(5);
        card.addView(state, stateLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(card, lp);
    }

    private void setTextColorRecursive(ViewGroup group, int primary, int secondary) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                tv.setTextColor(tv.getTextSize() > dp(16) ? primary : secondary);
            }
        }
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (color == FOCUS) d.setStroke(dp(2), Color.WHITE);
        return d;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
