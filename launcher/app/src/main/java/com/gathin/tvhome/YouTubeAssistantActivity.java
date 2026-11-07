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
    // Match the Ad Block settings palette: deep navy background, slate panels and teal focus.
    private static final int BG = Color.rgb(16, 25, 40);
    private static final int PANEL = Color.rgb(32, 44, 60);
    private static final int FOCUS = Color.rgb(23, 79, 88);
    private static final int ACCENT = Color.rgb(0, 207, 198);
    private static final int TEXT = Color.rgb(245, 246, 250);
    private static final int MUTED = Color.rgb(172, 184, 193);

    private TextView pairingStatus;
    private TextView featureStatus;
    private TextView pairingAction;
    private TextView featureAction;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        int screenWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density);
        int horizontalInset = Math.max(24, Math.min(72, Math.round(screenWidthDp * 0.055f)));
        int verticalInset = Math.max(18, Math.min(42, Math.round(screenHeightDp * 0.035f)));
        int contentWidth = Math.min(920, Math.max(280, screenWidthDp - horizontalInset * 2));
        int headingSize = Math.max(24, Math.min(32, Math.round(contentWidth * 0.032f)));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setPadding(horizontalInset, verticalInset, horizontalInset, verticalInset);
        root.setBackgroundColor(BG);
        scroll.addView(root, new android.widget.ScrollView.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        root.addView(content, new LinearLayout.LayoutParams(contentWidth, -2));

        TextView title = new TextView(this);
        title.setText("YouTube 贊助片段略過");
        title.setTextColor(TEXT);
        title.setTextSize(headingSize);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        View accentLine = new View(this);
        GradientDrawable accentDrawable = round(ACCENT, dp(4));
        accentDrawable.setCornerRadius(dp(4));
        accentLine.setBackground(accentDrawable);
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(dp(88), dp(5));
        accentLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        accentLp.topMargin = dp(10);
        content.addView(accentLine, accentLp);

        TextView subtitle = text("連接 YouTube TV，即可管理自動略過功能", 16, false, MUTED);
        subtitle.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.topMargin = dp(12);
        subtitleLp.bottomMargin = dp(22);
        content.addView(subtitle, subtitleLp);

        LinearLayout pairingCard = card();
        TextView pairingTitle = text("YouTube TV 配對", 20, true, TEXT);
        pairingCard.addView(pairingTitle);
        pairingStatus = text("", 15, false, MUTED);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(8);
        pairingCard.addView(pairingStatus, statusLp);
        pairingAction = actionButton("");
        pairingAction.setOnClickListener(v -> showPairingDialog());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, dp(54));
        actionLp.topMargin = dp(16);
        pairingCard.addView(pairingAction, actionLp);
        content.addView(pairingCard, fullWidth());

        LinearLayout featureCard = card();
        TextView featureTitle = text("自動略過贊助片段", 20, true, TEXT);
        featureCard.addView(featureTitle);
        featureStatus = text("", 15, false, MUTED);
        featureStatus.setGravity(android.view.Gravity.CENTER_VERTICAL);
        featureStatus.setMinHeight(dp(48));
        featureStatus.setPadding(dp(2), dp(8), dp(2), dp(8));
        featureCard.addView(featureStatus);
        featureAction = actionButton("");
        featureAction.setBackgroundColor(Color.TRANSPARENT);
        featureAction.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable state = round(hasFocus ? FOCUS : Color.TRANSPARENT, dp(12));
            if (hasFocus) state.setStroke(dp(2), ACCENT);
            v.setBackground(state);
            v.setElevation(hasFocus ? dp(5) : 0f);
        });
        featureAction.setOnClickListener(v -> {
            if (!YouTubePairingClient.hasSavedCredentials(this)) {
                showMessageDialog("尚未連接電視", "請先連接 YouTube TV，再開啟自動略過。");
                return;
            }
            toggleAutoSkip();
        });
        LinearLayout.LayoutParams featureActionLp = new LinearLayout.LayoutParams(-1, dp(54));
        featureActionLp.topMargin = dp(8);
        featureCard.addView(featureAction, featureActionLp);
        content.addView(featureCard, fullWidth());

        TextView footer = text("配對資料會使用 Android Keystore 加密保存在本機。", 13, false, MUTED);
        footer.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = dp(8);
        content.addView(footer, footerLp);

        pairingAction.setNextFocusDownId(featureAction.getId());
        featureAction.setNextFocusUpId(pairingAction.getId());
        setContentView(scroll);
        refreshUi();
        pairingAction.requestFocus();
    }

    private TextView actionButton(String label) {
        TextView button = text(label, 16, true, TEXT);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable buttonBackground = round(FOCUS, dp(18));
        buttonBackground.setStroke(dp(2), ACCENT);
        button.setBackground(buttonBackground);
        button.setId(View.generateViewId());
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setDefaultFocusHighlightEnabled(false);
        button.setOnFocusChangeListener((v, hasFocus) -> applyFocusState(v, hasFocus));
        return button;
    }

    private void applyFocusState(View view, boolean hasFocus) {
        GradientDrawable background = round(
                hasFocus ? Color.rgb(18, 104, 108) : FOCUS, dp(18));
        background.setStroke(dp(2), ACCENT);
        view.setBackground(background);
        view.setElevation(hasFocus ? dp(5) : 0f);
    }

    @Override protected void onResume() {
        super.onResume();
        if (pairingStatus != null) refreshUi();
    }

    private void refreshUi() {
        boolean paired = YouTubePairingClient.hasSavedCredentials(this);
        pairingStatus.setText(paired
                ? "狀態：已完成配對，可重新配對其他電視"
                : "狀態：尚未連接電視，請先完成配對");
        pairingAction.setText(paired ? "重新配對 YouTube TV" : "連接 YouTube TV");
        boolean enabled = getSharedPreferences(
                YouTubeSponsorSkipService.PREFS, MODE_PRIVATE)
                .getBoolean(YouTubeSponsorSkipService.PREF_ENABLED, false);
        String status = getSharedPreferences(
                YouTubeSponsorSkipService.PREFS, MODE_PRIVATE)
                .getString(YouTubeSponsorSkipService.PREF_STATUS, "");
        featureStatus.setText(!paired ? "目前尚未配對電視，完成配對後即可使用此功能。"
                : enabled ? "目前狀態：已開啟\n" + (status.isEmpty() ? "正在啟動監控" : status)
                : "目前狀態：已關閉");
        featureAction.setText(!paired ? "請先連接 YouTube TV"
                : enabled ? "關閉自動略過" : "開啟自動略過");
        // Keep the action reachable by D-pad even before pairing so it can explain the prerequisite.
        featureAction.setEnabled(true);
        featureAction.setAlpha(1f);
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
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(20), dp(24), dp(20));
        GradientDrawable panelBackground = round(Color.rgb(29, 33, 43), dp(22));
        panelBackground.setStroke(Math.max(1, dp(1)), 0x28FFFFFF);
        panel.setBackground(panelBackground);

        TextView title = text("連接 YouTube TV", 20, true, TEXT);
        title.setGravity(android.view.Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView help = text("請在 YouTube TV 設定中取得配對代碼。", 14, false, MUTED);
        help.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(-1, -2);
        helpLp.topMargin = dp(8);
        panel.addView(help, helpLp);

        EditText input = new EditText(this);
        input.setId(View.generateViewId());
        input.setSingleLine(true);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setHint("輸入電視上的配對代碼");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(18);
        input.setGravity(android.view.Gravity.CENTER_VERTICAL);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(round(PANEL, dp(10)));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(68));
        inputLp.topMargin = dp(12);
        panel.addView(input, inputLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, dp(56));
        actionsLp.topMargin = dp(16);
        panel.addView(actions, actionsLp);

        TextView cancel = text("取消", 16, true, TEXT);
        TextView connect = text("連接", 16, true, TEXT);
        TextView[] buttons = {cancel, connect};
        for (TextView button : buttons) {
            button.setGravity(android.view.Gravity.CENTER);
            button.setFocusable(true);
            button.setFocusableInTouchMode(true);
            button.setClickable(true);
            button.setDefaultFocusHighlightEnabled(false);
            button.setBackground(round(PANEL, dp(16)));
            button.setMinWidth(dp(112));
            button.setId(View.generateViewId());
            button.setOnFocusChangeListener((view, hasFocus) -> {
                GradientDrawable state = round(hasFocus ? FOCUS : PANEL, dp(16));
                if (hasFocus) state.setStroke(dp(2), ACCENT);
                view.setBackground(state);
                view.setElevation(hasFocus ? dp(4) : 0f);
            });
            LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(dp(112), dp(52));
            buttonLp.leftMargin = dp(10);
            buttonLp.rightMargin = dp(10);
            actions.addView(button, buttonLp);
        }

        input.setNextFocusDownId(cancel.getId());
        cancel.setNextFocusUpId(input.getId());
        connect.setNextFocusUpId(input.getId());
        cancel.setNextFocusRightId(connect.getId());
        connect.setNextFocusLeftId(cancel.getId());
        input.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                cancel.requestFocus();
                return true;
            }
            return false;
        });
        cancel.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                input.requestFocus();
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                connect.requestFocus();
                return true;
            }
            return false;
        });
        connect.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                cancel.requestFocus();
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                input.requestFocus();
                return true;
            }
            return false;
        });

        cancel.setOnClickListener(v -> dialog.dismiss());
        connect.setOnClickListener(v -> {
            String code = input.getText().toString().trim();
            if (code.isEmpty()) {
                input.setError("請輸入配對代碼");
                input.requestFocus();
                return;
            }
            dialog.dismiss();
            pair(code);
        });

        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        final android.widget.ImageView[] backdropBlur = new android.widget.ImageView[1];
        dialog.setOnDismissListener(ignored -> {
            android.widget.ImageView overlay = backdropBlur[0];
            if (overlay == null) return;
            android.view.ViewParent parent = overlay.getParent();
            if (parent instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) parent).removeView(overlay);
            android.graphics.drawable.Drawable drawable = overlay.getDrawable();
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                android.graphics.Bitmap bitmap =
                        ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
        });
        dialog.show();
        // Capture the fully laid-out activity behind the dialog, then add the blurred
        // snapshot above the activity decor. The dialog window stays crisp on top.
        backdropBlur[0] = installPairingBackdropBlur();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.6f);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxWidth = Math.min(dp(680), screenWidth - dp(48));
            window.setLayout(maxWidth, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(android.view.Gravity.CENTER);
            window.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        input.post(() -> {
            input.requestFocus();
            input.setSelection(input.getText().length());
        });
    }

    /**
     * Matches the folder dialog backdrop: a 25px Gaussian blur of the launcher,
     * with the dialog window applying the same 60% dark scrim. The dialog itself
     * is drawn afterwards and remains crisp.
     */
    private android.widget.ImageView installPairingBackdropBlur() {
        final View decor = getWindow() != null ? getWindow().getDecorView() : null;
        if (!(decor instanceof android.view.ViewGroup)
                || decor.getWidth() <= 0 || decor.getHeight() <= 0) return null;
        android.graphics.Bitmap source = null;
        android.graphics.Bitmap small = null;
        android.graphics.Bitmap blurred = null;
        android.renderscript.RenderScript rs = null;
        android.renderscript.Allocation input = null;
        android.renderscript.Allocation output = null;
        android.renderscript.ScriptIntrinsicBlur blur = null;
        try {
            int width = decor.getWidth();
            int height = decor.getHeight();
            source = android.graphics.Bitmap.createBitmap(
                    width, height, android.graphics.Bitmap.Config.ARGB_8888);
            decor.draw(new android.graphics.Canvas(source));
            int smallWidth = Math.max(1, width / 4);
            int smallHeight = Math.max(1, height / 4);
            small = android.graphics.Bitmap.createScaledBitmap(
                    source, smallWidth, smallHeight, true);
            source.recycle();
            source = null;
            rs = android.renderscript.RenderScript.create(this);
            input = android.renderscript.Allocation.createFromBitmap(rs, small);
            output = android.renderscript.Allocation.createTyped(rs, input.getType());
            blur = android.renderscript.ScriptIntrinsicBlur.create(
                    rs, android.renderscript.Element.U8_4(rs));
            blur.setRadius(6.25f); // 6.25px at quarter scale = 25px on screen.
            blur.setInput(input);
            blur.forEach(output);
            output.copyTo(small);
            blurred = android.graphics.Bitmap.createScaledBitmap(small, width, height, true);
            android.widget.ImageView overlay = new android.widget.ImageView(this);
            overlay.setImageBitmap(blurred);
            overlay.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            overlay.setClickable(false);
            overlay.setFocusable(false);
            overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ((android.view.ViewGroup) decor).addView(
                    overlay, new android.view.ViewGroup.LayoutParams(-1, -1));
            return overlay;
        } catch (Throwable unavailable) {
            if (blurred != null && !blurred.isRecycled()) blurred.recycle();
            return null;
        } finally {
            if (source != null && !source.isRecycled()) source.recycle();
            if (small != null && !small.isRecycled()) small.recycle();
            try { if (blur != null) blur.destroy(); } catch (Exception ignored) { }
            try { if (input != null) input.destroy(); } catch (Exception ignored) { }
            try { if (output != null) output.destroy(); } catch (Exception ignored) { }
            try { if (rs != null) rs.destroy(); } catch (Exception ignored) { }
        }
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
    // Keep this dialog's confirmation action independently styled for TV focus.
    private void showMessageDialog(String title, String message) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(28), dp(22), dp(28), dp(22));
        GradientDrawable surface = round(Color.rgb(29, 33, 43), dp(22));
        surface.setStroke(Math.max(1, dp(1)), 0x28FFFFFF);
        panel.setBackground(surface);

        TextView heading = text(title, 20, true, TEXT);
        heading.setGravity(android.view.Gravity.CENTER);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView body = text(message, 16, false, 0xFFE7EAF0);
        body.setGravity(android.view.Gravity.CENTER);
        body.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = dp(10);
        panel.addView(body, bodyLp);

        TextView confirm = text("確定", 16, true, Color.WHITE);
        confirm.setGravity(android.view.Gravity.CENTER);
        confirm.setFocusable(true);
        confirm.setFocusableInTouchMode(true);
        confirm.setClickable(true);
        confirm.setDefaultFocusHighlightEnabled(false);
        confirm.setId(View.generateViewId());
        confirm.setBackground(round(FOCUS, dp(16)));
        confirm.setOnFocusChangeListener((view, hasFocus) -> {
            GradientDrawable state = round(hasFocus ? FOCUS : PANEL, dp(16));
            if (hasFocus) state.setStroke(dp(2), ACCENT);
            view.setBackground(state);
            view.setElevation(hasFocus ? dp(4) : 0f);
            ((TextView) view).setTextColor(Color.WHITE);
        });
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(dp(112), dp(52));
        confirmLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        confirmLp.topMargin = dp(22);
        panel.addView(confirm, confirmLp);
        confirm.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        final android.widget.ImageView[] backdropBlur = new android.widget.ImageView[1];
        dialog.setOnDismissListener(ignored -> {
            android.widget.ImageView overlay = backdropBlur[0];
            if (overlay == null) return;
            android.view.ViewParent parent = overlay.getParent();
            if (parent instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) parent).removeView(overlay);
            android.graphics.drawable.Drawable drawable = overlay.getDrawable();
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                android.graphics.Bitmap bitmap =
                        ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
        });
        dialog.show();
        // Capture the fully laid-out activity behind the dialog, then add the blurred
        // snapshot above the activity decor. The dialog window stays crisp on top.
        backdropBlur[0] = installPairingBackdropBlur();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.6f);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(dp(680), screenWidth - dp(48)),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(android.view.Gravity.CENTER);
        }
        confirm.post(confirm::requestFocus);
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
        GradientDrawable surface = round(PANEL, dp(24));
        surface.setStroke(dp(1), 0x553D526A);
        card.setBackground(surface);
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
