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
        // Give the help text and code field balanced left/right insets inside the fixed dialog.
        // Balanced horizontal inset for both helper text and pairing code field.
        // Keep these insets independent from the fixed outer dialog dimensions.
        box.setPadding(dp(20), dp(6), dp(20), dp(8));
        TextView help = text("請在 YouTube TV 設定中取得配對代碼。", 14, false, MUTED);
        box.addView(help);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.topMargin = dp(8);
        inputLp.height = dp(68);
        box.addView(input, inputLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("連接 YouTube TV")
                .setView(box)
                .setPositiveButton("連接", null)
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .create();
        dialog.setOnShowListener(ignored -> {
            styleDialog(dialog, false);
            // Preserve the platform-measured outer dialog width exactly; only centre its position.
            // Internal content and action spacing are adjusted without resizing the outer surface.
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                // Lock the already-measured outer dimensions. Subsequent inner spacing changes
                // must not resize the dialog surface.
                View decor = window.getDecorView();
                int fixedWidth = decor.getWidth();
                int fixedHeight = decor.getHeight();
                if (fixedWidth > 0 && fixedHeight > 0) {
                    window.setLayout(fixedWidth, fixedHeight);
                }
                window.setGravity(android.view.Gravity.CENTER);
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }

            android.widget.Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            android.widget.Button connectButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            // Separate the action pills; AlertDialog otherwise places them edge-to-edge.
            if (cancelButton != null && connectButton != null) {
                View buttons = (View) cancelButton.getParent();
                if (buttons instanceof LinearLayout) {
                    LinearLayout.LayoutParams cancelLp = (LinearLayout.LayoutParams) cancelButton.getLayoutParams();
                    cancelLp.rightMargin = dp(14);
                    cancelButton.setLayoutParams(cancelLp);
                    LinearLayout.LayoutParams connectLp = (LinearLayout.LayoutParams) connectButton.getLayoutParams();
                    connectLp.leftMargin = dp(14);
                    connectButton.setLayoutParams(connectLp);
                    // Align the Connect button's right edge with the code field's right edge.
                    // Move the complete action row slightly lower while keeping the fixed dialog frame.
                    buttons.setPadding(buttons.getPaddingLeft(), buttons.getPaddingTop(),
                            dp(20), 0);
                    ((LinearLayout) buttons).setClipChildren(false);
                    ((LinearLayout) buttons).setClipToPadding(false);
                    buttons.setTranslationY(dp(10));
                }
                // Explicit D-pad graph: input -> Cancel -> Connect, with reverse navigation.
                input.setNextFocusDownId(cancelButton.getId());
                cancelButton.setNextFocusUpId(input.getId());
                connectButton.setNextFocusUpId(input.getId());
                cancelButton.setNextFocusRightId(connectButton.getId());
                connectButton.setNextFocusLeftId(cancelButton.getId());
                input.setOnKeyListener((view, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        cancelButton.requestFocus();
                        return true;
                    }
                    return false;
                });
                cancelButton.setOnKeyListener((view, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        input.requestFocus();
                        return true;
                    }
                    return false;
                });
            }
            // Keep both action labels white in normal and focused states.
            android.widget.Button[] actionButtons = {cancelButton, connectButton};
            for (android.widget.Button button : actionButtons) {
                if (button == null) continue;
                button.setTextColor(Color.WHITE);
                button.setOnFocusChangeListener((view, hasFocus) -> {
                    view.setBackground(round(hasFocus ? FOCUS : PANEL, dp(16)));
                    android.graphics.drawable.Drawable bg = view.getBackground();
                    if (hasFocus && bg instanceof GradientDrawable) {
                        ((GradientDrawable) bg).setStroke(dp(2), 0xFF80C9C2);
                    }
                    ((android.widget.Button) view).setTextColor(Color.WHITE);
                    view.setElevation(hasFocus ? dp(4) : 0f);
                });
            }
            cancelButton.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    connectButton.requestFocus();
                    return true;
                }
                return false;
            });
            connectButton.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    cancelButton.requestFocus();
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    input.requestFocus();
                    return true;
                }
                return false;
            });
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
            // Focus the editable field after the window and action row have completed layout.
            input.post(() -> {
                input.requestFocus();
                input.setSelection(input.getText().length());
            });
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
