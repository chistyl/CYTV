package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Simple user-facing YouTube SponsorBlock screen.
 * Diagnostic controls are intentionally kept out of the normal UI.
 */
public class YouTubeAssistantActivity extends Activity {
    private static final int BG = Color.rgb(12, 14, 19);
    private static final int PANEL = Color.rgb(28, 31, 39);
    private static final int FOCUS = Color.rgb(245, 247, 250);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(157, 164, 177);

    private TextView pairingStatus;
    private TextView featureStatus;
    private TextView pairingAction;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(56), dp(34), dp(56), dp(30));

        TextView title = new TextView(this);
        title.setText("YouTube 贊助片段略過");
        title.setTextColor(TEXT);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("連接 YouTube TV，即可管理自動略過功能");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(15);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.topMargin = dp(8);
        subtitleLp.bottomMargin = dp(28);
        root.addView(subtitle, subtitleLp);

        LinearLayout pairingCard = card();
        TextView pairingTitle = text("YouTube TV", 19, true, TEXT);
        pairingCard.addView(pairingTitle);
        pairingStatus = text("", 14, false, MUTED);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(6);
        pairingCard.addView(pairingStatus, statusLp);
        pairingAction = text("", 15, true, TEXT);
        pairingAction.setGravity(android.view.Gravity.CENTER);
        pairingAction.setPadding(dp(16), dp(12), dp(16), dp(12));
        pairingAction.setBackground(round(PANEL, dp(10)));
        pairingAction.setFocusable(true);
        pairingAction.setClickable(true);
        pairingAction.setOnClickListener(v -> showPairingDialog());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, -2);
        actionLp.topMargin = dp(16);
        pairingCard.addView(pairingAction, actionLp);
        root.addView(pairingCard, fullWidth());

        LinearLayout featureCard = card();
        TextView featureTitle = text("自動略過贊助片段", 19, true, TEXT);
        featureCard.addView(featureTitle);
        featureStatus = text("", 15, true, TEXT);
        featureStatus.setGravity(android.view.Gravity.CENTER_VERTICAL);
        featureStatus.setPadding(dp(14), dp(13), dp(14), dp(13));
        featureStatus.setBackground(round(Color.rgb(38, 43, 53), dp(10)));
        featureStatus.setFocusable(true);
        featureStatus.setClickable(true);
        featureStatus.setOnClickListener(v -> toggleAutoSkip());
        LinearLayout.LayoutParams featureStatusLp = new LinearLayout.LayoutParams(-1, -2);
        featureStatusLp.topMargin = dp(12);
        featureCard.addView(featureStatus, featureStatusLp);
        root.addView(featureCard, fullWidth());

        TextView footer = text("配對資料會安全保存在本機。", 13, false, MUTED);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = dp(18);
        root.addView(footer, footerLp);

        setContentView(root);
        refreshUi();
        pairingAction.requestFocus();
    }

    private void refreshUi() {
        boolean paired = YouTubePairingClient.hasSavedCredentials(this);
        pairingStatus.setText(paired ? "已完成電視配對" : "尚未連接電視");
        pairingAction.setText(paired ? "重新配對電視" : "連接 YouTube TV");
        boolean enabled = getSharedPreferences(
                YouTubeSponsorSkipService.PREFS, MODE_PRIVATE)
                .getBoolean(YouTubeSponsorSkipService.PREF_ENABLED, false);
        String status = getSharedPreferences(
                YouTubeSponsorSkipService.PREFS, MODE_PRIVATE)
                .getString(YouTubeSponsorSkipService.PREF_STATUS, "");
        featureStatus.setText(!paired ? "請先連接 YouTube TV。"
                : enabled ? "自動略過：已開啟\n" + (status.isEmpty() ? "正在啟動監控" : status)
                        + "\n點選此處關閉"
                : "自動略過：已關閉\n點選此處開啟");
        featureStatus.setFocusable(paired);
        featureStatus.setClickable(paired);
        featureStatus.setAlpha(paired ? 1f : 0.55f);
    }

    private void toggleAutoSkip() {
        if (!YouTubePairingClient.hasSavedCredentials(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("尚未連接電視")
                    .setMessage("請先連接 YouTube TV，再開啟自動略過。")
                    .setPositiveButton("確定", (d, w) -> d.dismiss())
                    .show();
            return;
        }
        android.content.SharedPreferences prefs =
                getSharedPreferences(YouTubeSponsorSkipService.PREFS, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(YouTubeSponsorSkipService.PREF_ENABLED, false);
        if (enabled) {
            prefs.edit().putBoolean(YouTubeSponsorSkipService.PREF_ENABLED, false)
                    .putString(YouTubeSponsorSkipService.PREF_STATUS, "功能已關閉").apply();
            stopService(new android.content.Intent(this, YouTubeSponsorSkipService.class));
        } else {
            prefs.edit().putBoolean(YouTubeSponsorSkipService.PREF_ENABLED, true)
                    .putString(YouTubeSponsorSkipService.PREF_STATUS, "正在連接 YouTube TV").apply();
            android.content.Intent service =
                    new android.content.Intent(this, YouTubeSponsorSkipService.class)
                            .setAction(YouTubeSponsorSkipService.ACTION_ENABLE);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        }
        refreshUi();
    }

    private void showPairingDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("輸入電視上的配對代碼");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(18);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(round(PANEL, dp(10)));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(4));
        TextView help = text("請在 YouTube TV 設定中取得配對代碼。", 14, false, MUTED);
        box.addView(help);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.topMargin = dp(14);
        box.addView(input, inputLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("連接 YouTube TV")
                .setView(box)
                .setPositiveButton("連接", null)
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String code = input.getText().toString().trim();
                if (code.isEmpty()) {
                    input.setError("請輸入配對代碼");
                    return;
                }
                dialog.dismiss();
                pair(code);
            });
            input.requestFocus();
        });
        dialog.show();
    }

    private void pair(String code) {
        new Thread(() -> {
            String message;
            boolean success = false;
            try {
                YouTubePairingClient.PairingResult result = YouTubePairingClient.pair(code);
                YouTubePairingClient.save(this, result);
                message = "YouTube TV 已成功連接。";
                success = true;
            } catch (Exception error) {
                message = error.getMessage() == null ? "連接失敗，請確認配對代碼。" : error.getMessage();
            }
            final String resultMessage = message;
            final boolean paired = success;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                refreshUi();
                new AlertDialog.Builder(this)
                        .setTitle(paired ? "連接成功" : "連接失敗")
                        .setMessage(resultMessage)
                        .setPositiveButton("確定", (d, w) -> d.dismiss())
                        .show();
            });
        }, "CYTV-YouTube-Pairing").start();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setBackground(round(PANEL, dp(16)));
        return card;
    }

    private LinearLayout.LayoutParams fullWidth() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        return lp;
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
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
