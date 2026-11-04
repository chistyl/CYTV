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
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Simple user-facing YouTube SponsorBlock screen.
 * Diagnostic controls are intentionally kept out of the normal UI.
 */
public class YouTubeAssistantActivity extends Activity {
    private static final int BG = Color.rgb(29, 33, 43);
    private static final int PANEL = Color.rgb(52, 58, 70);
    private static final int FOCUS = Color.rgb(70, 83, 102);
    private static final int TEXT = Color.rgb(245, 246, 250);
    private static final int MUTED = Color.rgb(172, 184, 193);

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
        pairingAction.setBackground(round(0xFF343A46, dp(12)));
        pairingAction.setId(View.generateViewId());
        pairingAction.setFocusable(true);
        pairingAction.setFocusableInTouchMode(true);
        pairingAction.setClickable(true);
        pairingAction.setDefaultFocusHighlightEnabled(false);
        pairingAction.setOnFocusChangeListener((v, hasFocus) -> applyFocusState(v, hasFocus));
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
        featureStatus.setBackground(round(Color.rgb(52, 58, 70), dp(10)));
        featureStatus.setId(View.generateViewId());
        // 即使尚未配對也保留焦點，讓 Android TV 遙控器方向鍵可以移到此功能區；
        // 未配對時按確認鍵會提示先連接電視，不會執行開關。
        featureStatus.setFocusable(true);
        featureStatus.setFocusableInTouchMode(true);
        featureStatus.setClickable(true);
        featureStatus.setDefaultFocusHighlightEnabled(false);
        featureStatus.setOnFocusChangeListener((v, hasFocus) -> applyFocusState(v, hasFocus));
        featureStatus.setOnClickListener(v -> {
            if (!YouTubePairingClient.hasSavedCredentials(this)) {
                showMessageDialog("尚未連接電視", "請先連接 YouTube TV，再開啟自動略過。");
                return;
            }
            toggleAutoSkip();
        });
        // 明確指定上下焦點順序，避免 Android TV 不同遙控器／系統版本自動搜尋失敗。
        pairingAction.setNextFocusDownId(featureStatus.getId());
        featureStatus.setNextFocusUpId(pairingAction.getId());
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

    private void applyFocusState(View view, boolean hasFocus) {
        GradientDrawable background = round(
                hasFocus ? 0xFF465366 : 0xFF343A46, dp(12));
        if (hasFocus) background.setStroke(dp(2), 0xFF80C9C2);
        view.setBackground(background);
        view.setElevation(hasFocus ? dp(5) : 0f);
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
        featureStatus.setFocusable(true);
        featureStatus.setFocusableInTouchMode(true);
        featureStatus.setClickable(true);
        featureStatus.setAlpha(paired ? 1f : 0.55f);
    }

    private void toggleAutoSkip() {
        if (!YouTubePairingClient.hasSavedCredentials(this)) {
            showMessageDialog("尚未連接電視", "請先連接 YouTube TV，再開啟自動略過。");
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
        input.setId(View.generateViewId());
        input.setSingleLine(true);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setShowSoftInputOnFocus(true);
        input.setHint("輸入電視上的配對代碼");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(18);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(16), dp(13), dp(16), dp(13));
        input.setBackground(round(PANEL, dp(12)));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        box.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        box.setPadding(dp(8), dp(6), dp(8), dp(8));

        TextView help = text("請在 YouTube TV 設定中取得配對代碼。", 14, false, MUTED);
        help.setGravity(android.view.Gravity.CENTER_VERTICAL);
        box.addView(help, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(68));
        inputLp.topMargin = dp(16);
        box.addView(input, inputLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, dp(60));
        actionsLp.topMargin = dp(14);
        box.addView(actions, actionsLp);

        android.widget.Button cancelButton = pairingDialogButton("取消");
        android.widget.Button connectButton = pairingDialogButton("連接");
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, -1, 1f);
        cancelLp.rightMargin = dp(8);
        LinearLayout.LayoutParams connectLp = new LinearLayout.LayoutParams(0, -1, 1f);
        connectLp.leftMargin = dp(8);
        actions.addView(cancelButton, cancelLp);
        actions.addView(connectButton, connectLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("連接 YouTube TV")
                .setView(box)
                .create();
        dialog.setOnShowListener(ignored -> {
            styleDialog(dialog, false);
            input.setNextFocusDownId(cancelButton.getId());
            cancelButton.setNextFocusUpId(input.getId());
            connectButton.setNextFocusUpId(input.getId());
            cancelButton.setNextFocusRightId(connectButton.getId());
            connectButton.setNextFocusLeftId(cancelButton.getId());
            cancelButton.setOnClickListener(v -> dialog.dismiss());
            connectButton.setOnClickListener(v -> {
                String code = input.getText().toString().trim();
                if (code.isEmpty()) {
                    input.setError("請輸入配對代碼");
                    input.requestFocus();
                    return;
                }
                dialog.dismiss();
                pair(code);
            });
            // Explicitly start D-pad focus in the code field. The custom action row avoids
            // AlertDialog's tightly packed system button panel and its competing focus request.
            input.post(() -> {
                input.requestFocus();
                input.setSelection(input.length());
            });
            if (dialog.getWindow() != null) {
                android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
                params.gravity = android.view.Gravity.CENTER;
                params.y = -dp(8);
                dialog.getWindow().setAttributes(params);
            }
        });
        dialog.show();
    }

    private android.widget.Button pairingDialogButton(String label) {
        android.widget.Button button = new android.widget.Button(this);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(0xFFE8EBF0);
        button.setGravity(android.view.Gravity.CENTER);
        button.setMinHeight(dp(56));
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setDefaultFocusHighlightEnabled(false);
        GradientDrawable normal = round(0xFF343A46, dp(18));
        GradientDrawable focused = round(FOCUS, dp(18));
        focused.setStroke(dp(2), 0xFF80C9C2);
        button.setBackground(normal);
        button.setOnFocusChangeListener((view, hasFocus) -> {
            view.setBackground(hasFocus
                    ? focused.getConstantState().newDrawable().mutate()
                    : normal.getConstantState().newDrawable().mutate());
            view.setTextColor(hasFocus ? 0xFF202124 : 0xFFE8EBF0);
            view.setElevation(hasFocus ? dp(3) : 0f);
        });
        return button;
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
                showMessageDialog(paired ? "連接成功" : "連接失敗", resultMessage);
            });
        }, "CYTV-YouTube-Pairing").start();
    }

    /**
     * Shared TV dialog styling: charcoal rounded surface, rounded action pills,
     * and a high-contrast white focus state that is easy to see with a D-pad remote.
     */
    private void showMessageDialog(String title, String message) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("確定", (d, w) -> d.dismiss())
                .create();
        dialog.show();
        styleDialog(dialog, true);
    }

    private void styleDialog(AlertDialog dialog, boolean focusPrimary) {
        if (dialog.getWindow() != null) {
            GradientDrawable surface = round(Color.rgb(29, 33, 43), dp(24));
            surface.setStroke(dp(1), 0x28FFFFFF);
            dialog.getWindow().setBackgroundDrawable(surface);
            dialog.getWindow().getDecorView().setClipToOutline(true);
            dialog.getWindow().setDimAmount(0.62f);
        }

        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(0xFFE7EAF0);
            message.setTextSize(16);
            message.setLineSpacing(dp(3), 1.0f);
        }

        android.widget.Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        android.widget.Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        android.widget.Button[] buttons = {negative, neutral, positive};

        GradientDrawable normal = round(0xFF343A46, dp(16));
        GradientDrawable focused = round(FOCUS, dp(16));
        focused.setStroke(dp(2), 0xFF80C9C2);

        for (android.widget.Button button : buttons) {
            if (button == null) continue;
            button.setAllCaps(false);
            button.setTextSize(16);
            button.setMinHeight(dp(52));
            button.setMinWidth(dp(112));
            button.setPadding(dp(18), dp(10), dp(18), dp(10));
            button.setFocusable(true);
            button.setFocusableInTouchMode(true);
            button.setDefaultFocusHighlightEnabled(false);
            button.setBackground(normal.getConstantState().newDrawable().mutate());
            button.setTextColor(0xFFE8EBF0);
            button.setOnFocusChangeListener((view, hasFocus) -> {
                view.setBackground(hasFocus
                        ? focused.getConstantState().newDrawable().mutate()
                        : normal.getConstantState().newDrawable().mutate());
                ((android.widget.Button) view).setTextColor(hasFocus ? 0xFF202124 : 0xFFE8EBF0);
                view.setElevation(hasFocus ? dp(3) : 0f);
            });
        }

        if (negative != null && positive != null) {
            negative.setNextFocusRightId(positive.getId());
            positive.setNextFocusLeftId(negative.getId());
        }
        android.widget.Button first = focusPrimary && positive != null ? positive : negative;
        if (first == null) first = positive != null ? positive : neutral;
        if (first != null) first.post(first::requestFocus);
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
