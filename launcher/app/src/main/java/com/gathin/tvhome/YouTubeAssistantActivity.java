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

    private void subscribeLoungeOnce() {
        if (!YouTubePairingClient.hasSavedCredentials(this)) {
            showInfo("請先完成 YouTube TV 配對並取得裝置憑證。");
            return;
        }
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("測試事件訂閱")
                .setMessage("正在建立工作階段並等待 YouTube TV 事件，最多約 12 秒…")
                .setCancelable(false).create();
        progress.show();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            String message;
            boolean success = false;
            try {
                YouTubeLoungeClient.Session session = YouTubeLoungeClient.connect(this);
                YouTubeLoungeClient.SubscriptionResult result =
                        YouTubeLoungeClient.subscribeOnce(this, session);
                StringBuilder report = new StringBuilder();
                report.append(result.httpAccepted
                        ? "YouTube 已接受 Lounge 訂閱 HTTP 請求。"
                        : "尚未確認 YouTube 接受 Lounge 訂閱請求。");
                report.append("\\n收到事件數：").append(result.eventCount)
                        .append("\\n最後事件編號 AID：").append(result.lastEventId)
                        .append("\\n事件：").append(result.eventSummary);
                if (!result.videoId.isEmpty()) {
                    report.append("\\n目前影片 ID：").append(result.videoId);
                    if (result.currentTime >= 0) {
                        report.append("\\n目前播放位置：").append(String.format(java.util.Locale.US, "%.1f 秒", result.currentTime));
                    }
                    if (result.duration >= 0) {
                        report.append("\\n影片長度：").append(String.format(java.util.Locale.US, "%.1f 秒", result.duration));
                    }
                    try {
                        java.util.List<SponsorBlockClient.Segment> segments =
                                SponsorBlockClient.lookupSponsorSegments(result.videoId);
                        report.append("\\nSponsorBlock 贊助片段：").append(segments.size()).append(" 段");
                        for (SponsorBlockClient.Segment segment : segments) {
                            report.append("\\n").append(String.format(java.util.Locale.US,
                                    "%.1f–%.1f 秒", segment.startSeconds, segment.endSeconds));
                        }
                    } catch (Exception lookupError) {
                        report.append("\\nSponsorBlock 查詢失敗：")
                                .append(lookupError.getMessage() == null ? "未知錯誤" : lookupError.getMessage());
                    }
                } else {
                    report.append("\\n目前尚未取得影片 ID，暫不查詢 SponsorBlock。");
                }
                if (result.timedOut) report.append("\\n\\n診斷結果：等待逾時。");
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
