package com.gathin.tvhome;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Full-screen, muted YouTube iframe background. User wallpaper preferences are never changed. */
final class YouTubeVideoCarousel {
    private static final String PREFS = "youtube_video_carousel";
    private static final String DEFAULT_ONE = "https://youtu.be/FaoCaZSzkOw?list=PLrXb4NDfS6KNOGMvv3RadaX317LQjm2X8";
    private static final String DEFAULT_TWO = "https://youtu.be/Um2JFPX-uH0";
    private final Context context;
    private final FrameLayout root;
    private final View[] backgroundLayers;
    private final int[] oldVisibility;
    private final SharedPreferences prefs;
    private final List<String> urls = new ArrayList<>();
    private final Random random = new Random();
    private WebView webView;
    private boolean active;
    private boolean pageReady;
    private boolean backgroundHidden;
    private int currentIndex;

    YouTubeVideoCarousel(Context context, FrameLayout root) {
        this.context = context;
        this.root = root;
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        backgroundLayers = new View[] {
                root.findViewById(R.id.sky), root.findViewById(R.id.heroArt),
                root.findViewById(R.id.heroScrim), root.findViewById(R.id.ambient)
        };
        oldVisibility = new int[backgroundLayers.length];
        loadUrls();
    }

    boolean isEnabled() { return prefs.getBoolean("enabled", false); }

    void start() {
        loadUrls();
        if (!isEnabled()) return;
        if (urls.isEmpty()) {
            prefs.edit().putBoolean("enabled", false).apply();
            active = false;
            restoreBackgroundLayers();
            if (webView != null) webView.setVisibility(View.GONE);
            return;
        }
        active = true;
        ensureWebView();
        webView.setVisibility(View.VISIBLE);
        hideBackgroundLayers();
        if (!pageReady) loadPlayer();
        else playAt(currentIndex);
        webView.onResume();
    }

    void pause() {
        active = false;
        if (webView != null) {
            webView.evaluateJavascript("if(window.ytPlayer&&ytPlayer.pauseVideo)ytPlayer.pauseVideo();", null);
            webView.onPause();
        }
    }

    void close() {
        active = false;
        if (webView != null) {
            root.removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
    }

    void showSettingsDialog() {
        loadUrls();
        final List<String> draftUrls = new ArrayList<>(urls);
        while (draftUrls.size() < 5) draftUrls.add("");
        final int[] draftTiming = {prefs.getInt("timing_seconds", 15)};
        final String[] draftMode = {prefs.getString("playback_mode", "sequential")};

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10), dp(2), dp(10), dp(3));
        content.setClipChildren(false);
        content.setClipToPadding(false);

        // First row: three radio playback modes on the left and enable switch on the right.
        LinearLayout topRow = new LinearLayout(context);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.RadioGroup modes = new android.widget.RadioGroup(context);
        modes.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        modes.setGravity(Gravity.CENTER_VERTICAL);
        String[] modeLabels = {"單一播放", "依順序播放", "隨機播放"};
        String[] modeValues = {"single", "sequential", "random"};
        android.widget.RadioButton[] modeRadios = new android.widget.RadioButton[3];
        int[] modeIds = new int[3];
        for (int i = 0; i < modeLabels.length; i++) {
            final String value = modeValues[i];
            android.widget.RadioButton radio = new android.widget.RadioButton(context);
            radio.setText(modeLabels[i]);
            radio.setTextColor(Color.WHITE);
            radio.setTextSize(14);
            radio.setButtonTintList(new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{0xFF80C9C2, 0xFFACB8C1}));
            radio.setPadding(0, 0, dp(3), 0);
            radio.setFocusable(true);
            radio.setFocusableInTouchMode(true);
            radio.setBackgroundColor(Color.TRANSPARENT);
            radio.setDefaultFocusHighlightEnabled(false);
            radio.setId(View.generateViewId());
            modeRadios[i] = radio;
            modeIds[i] = radio.getId();
            modes.addView(radio, new android.widget.RadioGroup.LayoutParams(-2, dp(48)));
            radio.setOnClickListener(v -> {
                draftMode[0] = value;
                radio.setChecked(true);
            });
            radio.setOnFocusChangeListener((v, focused) -> {
                GradientDrawable focusBg = new GradientDrawable();
                focusBg.setColor(Color.TRANSPARENT);
                focusBg.setCornerRadius(dp(10));
                if (focused) focusBg.setStroke(Math.max(2, dp(2)), 0xFF80C9C2);
                v.setBackground(focusBg);
            });
            radio.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    radio.performClick();
                    return true;
                }
                return false;
            });
        }
        int selectedMode = "single".equals(draftMode[0]) ? 0
                : "random".equals(draftMode[0]) ? 2 : 1;
        modeRadios[selectedMode].setChecked(true);
        topRow.addView(modes, new LinearLayout.LayoutParams(0, dp(52), 1f));

        android.widget.Switch enabled = new android.widget.Switch(context);
        enabled.setText("啟用視頻輪播");
        enabled.setTextColor(Color.WHITE);
        enabled.setTextSize(14);
        enabled.setId(View.generateViewId());
        enabled.setFocusable(true);
        enabled.setFocusableInTouchMode(true);
        enabled.setChecked(isEnabled());
        enabled.setBackgroundColor(Color.TRANSPARENT);
        enabled.setDefaultFocusHighlightEnabled(false);
        enabled.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF80C9C2));
        enabled.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable focusBg = new GradientDrawable();
            focusBg.setColor(Color.TRANSPARENT);
            focusBg.setCornerRadius(dp(10));
            if (focused) focusBg.setStroke(Math.max(2, dp(2)), 0xFF80C9C2);
            v.setBackground(focusBg);
        });
        enabled.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_SPACE)) {
                enabled.performClick();
                return true;
            }
            return false;
        });
        topRow.addView(enabled, new LinearLayout.LayoutParams(-2, dp(52)));
        content.addView(topRow, matchWrap());

        LinearLayout timingRow = new LinearLayout(context);
        timingRow.setGravity(Gravity.CENTER_VERTICAL);
        timingRow.addView(text("開始後／結束前", 14, Color.WHITE),
                new LinearLayout.LayoutParams(dp(112), dp(48)));
        String[] timingLabels = {"5秒", "10秒", "15秒", "30秒"};
        int[] timingValues = {5, 10, 15, 30};
        Button[] timingButtons = new Button[timingLabels.length];
        int[] timingIds = new int[timingLabels.length];
        Runnable refreshTiming = () -> {
            for (int i = 0; i < timingButtons.length; i++)
                styleChoice(timingButtons[i], timingValues[i] == draftTiming[0]);
        };
        for (int i = 0; i < timingLabels.length; i++) {
            final int value = timingValues[i];
            Button choice = smallButton(timingLabels[i]);
            choice.setId(View.generateViewId());
            timingButtons[i] = choice;
            timingIds[i] = choice.getId();
            choice.setOnClickListener(v -> { draftTiming[0] = value; refreshTiming.run(); });
            choice.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_SPACE)) {
                    choice.performClick();
                    return true;
                }
                return false;
            });
            timingRow.addView(choice, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        refreshTiming.run();
        wireChoiceFocus(timingRow);
        LinearLayout.LayoutParams timingLp = matchWrap();
        timingLp.topMargin = dp(2);
        timingLp.bottomMargin = dp(6);
        content.addView(timingRow, timingLp);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        List<EditText> fields = new ArrayList<>();
        EditText[] urlFields = new EditText[5];
        Button[] upButtons = new Button[5];
        Button[] downButtons = new Button[5];
        for (int i = 0; i < 5; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            EditText edit = new EditText(context);
            edit.setSingleLine(true);
            edit.setText(draftUrls.get(i));
            edit.setTextColor(Color.BLACK);
            edit.setHintTextColor(0xFF6F7782);
            edit.setHint("YouTube 影片連結");
            edit.setTextSize(14);
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            edit.setSelectAllOnFocus(true);
            edit.setPadding(dp(14), 0, dp(12), 0);
            edit.setBackground(roundSurface(0xFFE4E7EC, 12));
            edit.setFocusable(true);
            edit.setFocusableInTouchMode(true);
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            editLp.rightMargin = dp(12);
            row.addView(edit, editLp);
            fields.add(edit);
            urlFields[index] = edit;
            edit.setId(View.generateViewId());
            edit.setOnFocusChangeListener((view, focused) -> {
                GradientDrawable bg = roundSurface(0xFFE4E7EC, 12);
                if (focused) bg.setStroke(Math.max(2, dp(2)), 0xFF80C9C2);
                view.setBackground(bg);
            });

            Button up = smallButton("↑");
            up.setId(View.generateViewId());
            upButtons[index] = up;
            up.setTextSize(19);
            up.setFocusable(true);
            up.setFocusableInTouchMode(true);
            up.setClickable(true);
            up.setDefaultFocusHighlightEnabled(false);
            up.setBackground(roundSurface(0xFF2D323B, 8));
            up.setEnabled(true);
            up.setAlpha(index == 0 ? 0.48f : 1f);
            up.setOnClickListener(v -> {
                if (index == 0) {
                    Toast.makeText(context, "已經是第一筆影片", Toast.LENGTH_SHORT).show();
                    return;
                }
                syncDraftFields(fields, draftUrls);
                Collections.swap(draftUrls, index, index - 1);
                for (int n = 0; n < fields.size(); n++) fields.get(n).setText(draftUrls.get(n));
                upButtons[index].requestFocus();
            });
            row.addView(up, new LinearLayout.LayoutParams(dp(78), dp(44)));

            Button down = smallButton("↓");
            down.setId(View.generateViewId());
            downButtons[index] = down;
            down.setTextSize(19);
            down.setFocusable(true);
            down.setFocusableInTouchMode(true);
            down.setClickable(true);
            down.setDefaultFocusHighlightEnabled(false);
            down.setBackground(roundSurface(0xFF55575B, 8));
            down.setEnabled(true);
            down.setAlpha(index == 4 ? 0.48f : 1f);
            LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(dp(78), dp(44));
            downLp.leftMargin = dp(8);
            downLp.rightMargin = dp(2);
            down.setOnClickListener(v -> {
                if (index == 4) {
                    Toast.makeText(context, "已經是最後一筆影片", Toast.LENGTH_SHORT).show();
                    return;
                }
                syncDraftFields(fields, draftUrls);
                Collections.swap(draftUrls, index, index + 1);
                for (int n = 0; n < fields.size(); n++) fields.get(n).setText(draftUrls.get(n));
                downButtons[index].requestFocus();
            });
            row.addView(down, downLp);
            LinearLayout.LayoutParams rowLp = matchWrap();
            rowLp.bottomMargin = dp(2);
            list.addView(row, rowLp);
        }
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(Color.rgb(29, 33, 43));
        surface.setCornerRadius(dp(22));
        surface.setStroke(Math.max(1, dp(1)), 0x28FFFFFF);
        outer.setBackground(surface);

        TextView heading = text("視頻輪播", 20, Color.WHITE);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(dp(8), dp(4), 0, dp(12));
        outer.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        outer.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(5), 0, dp(2));
        Button reset = dialogActionButton("恢復預設");
        Button cancel = dialogActionButton("取消");
        Button apply = dialogActionButton("套用");
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(dp(112), dp(46));
        resetLp.rightMargin = dp(8);
        footer.addView(reset, resetLp);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(dp(82), dp(46));
        actionLp.leftMargin = dp(4);
        actionLp.rightMargin = dp(4);
        footer.addView(cancel, actionLp);
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(dp(82), dp(46));
        applyLp.leftMargin = dp(4);
        footer.addView(apply, applyLp);
        outer.addView(footer, new LinearLayout.LayoutParams(-1, dp(53)));

        // Deterministic Android TV DPAD map: every visible control is focusable
        // and every direction has an explicit destination.
        for (int i = 0; i < 3; i++) {
            final int index = i;
            android.widget.RadioButton radio = modeRadios[i];
            radio.setFocusable(true);
            radio.setFocusableInTouchMode(true);
            radio.setNextFocusLeftId(i == 0 ? modeIds[0] : modeIds[i - 1]);
            radio.setNextFocusRightId(i == 2 ? enabled.getId() : modeIds[i + 1]);
            radio.setNextFocusUpId(apply.getId());
            radio.setNextFocusDownId(timingIds[Math.min(i, 2)]);
            radio.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && index < 2) {
                    modeRadios[index + 1].requestFocus(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && index > 0) {
                    modeRadios[index - 1].requestFocus(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    timingButtons[index].requestFocus(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    apply.requestFocus(); return true;
                }
                return false;
            });
        }
        enabled.setFocusable(true);
        enabled.setFocusableInTouchMode(true);
        enabled.setNextFocusLeftId(modeIds[2]);
        enabled.setNextFocusRightId(enabled.getId());
        enabled.setNextFocusUpId(apply.getId());
        enabled.setNextFocusDownId(timingIds[3]);

        for (int i = 0; i < 4; i++) {
            final int index = i;
            Button button = timingButtons[i];
            button.setFocusable(true);
            button.setFocusableInTouchMode(true);
            button.setNextFocusLeftId(timingIds[Math.max(0, i - 1)]);
            button.setNextFocusRightId(timingIds[Math.min(3, i + 1)]);
            button.setNextFocusUpId(i == 3 ? enabled.getId() : modeIds[Math.min(i, 2)]);
            button.setNextFocusDownId(urlFields[0].getId());
            button.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && index > 0) {
                    timingButtons[index - 1].requestFocus(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && index < 3) {
                    timingButtons[index + 1].requestFocus(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    if (index == 3) enabled.requestFocus();
                    else modeRadios[Math.min(index, 2)].requestFocus();
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    urlFields[0].requestFocus(); return true;
                }
                return false;
            });
        }

        for (int i = 0; i < 5; i++) {
            final int rowIndex = i;
            EditText field = urlFields[i];
            Button up = upButtons[i];
            Button down = downButtons[i];
            field.setFocusable(true);
            field.setFocusableInTouchMode(true);
            field.setShowSoftInputOnFocus(false);
            field.setNextFocusLeftId(field.getId());
            field.setNextFocusRightId(up.getId());
            field.setNextFocusUpId(i == 0 ? timingIds[0] : urlFields[i - 1].getId());
            field.setNextFocusDownId(i == 4 ? reset.getId() : urlFields[i + 1].getId());
            field.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    field.setShowSoftInputOnFocus(true);
                    field.requestFocus();
                    field.setSelection(field.length());
                    InputMethodManager imm = (InputMethodManager)
                            context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    (rowIndex == 0 ? timingButtons[0] : urlFields[rowIndex - 1]).requestFocus();
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    (rowIndex == 4 ? reset : urlFields[rowIndex + 1]).requestFocus();
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    up.requestFocus(); return true;
                }
                return false;
            });
            field.setOnFocusChangeListener((view, focused) -> {
                GradientDrawable bg = roundSurface(0xFFE4E7EC, 12);
                if (focused) bg.setStroke(Math.max(2, dp(2)), 0xFF80C9C2);
                field.setBackground(bg);
                if (!focused) field.setShowSoftInputOnFocus(false);
            });
            up.setFocusable(true);
            up.setFocusableInTouchMode(true);
            up.setNextFocusLeftId(field.getId());
            up.setNextFocusRightId(down.getId());
            up.setNextFocusUpId(i == 0 ? timingIds[0] : upButtons[i - 1].getId());
            up.setNextFocusDownId(i == 4 ? reset.getId() : upButtons[i + 1].getId());
            up.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    up.performClick(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    field.requestFocus(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    down.requestFocus(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    (rowIndex == 0 ? timingButtons[0] : upButtons[rowIndex - 1]).requestFocus();
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    (rowIndex == 4 ? reset : upButtons[rowIndex + 1]).requestFocus();
                    return true;
                }
                return false;
            });
            down.setFocusable(true);
            down.setFocusableInTouchMode(true);
            down.setNextFocusLeftId(up.getId());
            down.setNextFocusRightId(down.getId());
            down.setNextFocusUpId(i == 0 ? timingIds[3] : downButtons[i - 1].getId());
            down.setNextFocusDownId(i == 4 ? apply.getId() : downButtons[i + 1].getId());
            down.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    down.performClick(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    up.requestFocus(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    field.requestFocus(); return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    (rowIndex == 0 ? timingButtons[3] : downButtons[rowIndex - 1]).requestFocus();
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    (rowIndex == 4 ? apply : downButtons[rowIndex + 1]).requestFocus();
                    return true;
                }
                return false;
            });
            up.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(roundSurface(focused ? 0xFF465366 : 0xFF2D323B, 8));
                ((Button) v).setTextColor(Color.WHITE);
            });
            down.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(roundSurface(focused ? 0xFF68717D : 0xFF55575B, 8));
                ((Button) v).setTextColor(Color.WHITE);
            });
        }

        reset.setFocusable(true);
        cancel.setFocusable(true);
        apply.setFocusable(true);
        reset.setNextFocusLeftId(reset.getId());
        reset.setNextFocusRightId(cancel.getId());
        reset.setNextFocusUpId(urlFields[4].getId());
        reset.setNextFocusDownId(modeIds[selectedMode]);
        cancel.setNextFocusLeftId(reset.getId());
        cancel.setNextFocusRightId(apply.getId());
        cancel.setNextFocusUpId(urlFields[4].getId());
        cancel.setNextFocusDownId(modeIds[selectedMode]);
        apply.setNextFocusLeftId(cancel.getId());
        apply.setNextFocusRightId(apply.getId());
        apply.setNextFocusUpId(downButtons[4].getId());
        apply.setNextFocusDownId(modeIds[selectedMode]);

        Dialog dialog = new Dialog(context);
        dialog.setContentView(outer);
        dialog.setCanceledOnTouchOutside(false);
        reset.setOnClickListener(v -> {
            draftTiming[0] = 15;
            draftMode[0] = "sequential";
            modeRadios[1].setChecked(true);
            refreshTiming.run();
            Toast.makeText(context, "已恢復預設設定", Toast.LENGTH_SHORT).show();
        });
        wireRemoteClick(reset);
        wireRemoteClick(cancel);
        wireRemoteClick(apply);
        cancel.setOnClickListener(v -> dialog.dismiss());
        apply.setOnClickListener(v -> {
            syncDraftFields(fields, draftUrls);
            List<String> valid = new ArrayList<>();
            for (String url : draftUrls) {
                String trimmed = url == null ? "" : url.trim();
                if (trimmed.isEmpty()) continue;
                if (extractVideoId(trimmed) == null) {
                    Toast.makeText(context, "請輸入有效的 YouTube 影片連結", Toast.LENGTH_SHORT).show();
                    return;
                }
                valid.add(trimmed);
            }
            if (valid.size() > 5) return;
            if (enabled.isChecked() && valid.isEmpty()) {
                Toast.makeText(context, "啟用視頻輪播前，請至少保留一部有效影片", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONArray array = new JSONArray();
            for (String url : valid) array.put(url);
            prefs.edit().putString("urls", array.toString())
                    .putBoolean("enabled", enabled.isChecked())
                    .putInt("timing_seconds", draftTiming[0])
                    .putString("playback_mode", draftMode[0])
                    .apply();
            urls.clear();
            urls.addAll(valid);
            dialog.dismiss();
            pageReady = false;
            if (isEnabled()) start();
            else { pause(); restoreBackgroundLayers(); if (webView != null) webView.setVisibility(View.GONE); }
        });

        final android.widget.ImageView backdropBlur = context instanceof MainActivity
                ? ((MainActivity) context).installFolderBackdropBlur() : null;
        dialog.setOnDismissListener(ignored -> {
            if (backdropBlur != null) {
                android.view.ViewParent parent = backdropBlur.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(backdropBlur);
                android.graphics.drawable.Drawable drawable = backdropBlur.getDrawable();
                if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                    android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                }
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.6f);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.CENTER);
            dialog.getWindow().setLayout(
                    Math.min(dp(680), context.getResources().getDisplayMetrics().widthPixels - dp(48)),
                    Math.min(dp(520), context.getResources().getDisplayMetrics().heightPixels - dp(48)));
        }
        modeRadios[selectedMode].requestFocus();
    }

    // Keep draft URL edits synchronized before applying reorder operations.
    private void syncDraftFields(List<EditText> fields, List<String> target) {
        for (int i = 0; i < fields.size() && i < target.size(); i++)
            target.set(i, fields.get(i).getText().toString());
    }

    private void wireRemoteClick(Button button) {
        button.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_SPACE)) {
                button.performClick();
                return true;
            }
            return false;
        });
    }

    private Button dialogActionButton(String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setDefaultFocusHighlightEnabled(false);
        button.setTextColor(0xFFE8EBF0);
        button.setBackground(roundSurface(0xFF343A46, 12));
        button.setId(View.generateViewId());
        button.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable bg = roundSurface(focused ? 0xFF465366 : 0xFF343A46, 12);
            if (focused) bg.setStroke(Math.max(2, dp(2)), 0xFF80C9C2);
            v.setBackground(bg);
            ((Button) v).setTextColor(focused ? Color.WHITE : 0xFFE8EBF0);
        });
        return button;
    }

    private LinearLayout optionRow(String label, String[] labels, int[] values, int selected, String key) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView caption = text(label, 14, Color.WHITE);
        row.addView(caption, new LinearLayout.LayoutParams(dp(112), dp(48)));
        for (int i=0;i<labels.length;i++) {
            Button choice = smallButton(labels[i]);
            choice.setTag(values[i]);
            styleChoice(choice, values[i] == selected);
            choice.setOnClickListener(v -> {
                for (int j=1;j<row.getChildCount();j++) styleChoice((Button)row.getChildAt(j), row.getChildAt(j)==v);
                prefs.edit().putInt(key,(Integer)v.getTag()).apply();
            });
            row.addView(choice,new LinearLayout.LayoutParams(0,dp(44),1f));
        }
        wireChoiceFocus(row);
        return row;
    }

    private LinearLayout modeOptionRow(String selected) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("影片播放方式",14,Color.WHITE),new LinearLayout.LayoutParams(dp(112),dp(48)));
        String[] labels={"單一播放","依順序播放","隨機播放"};
        String[] values={"single","sequential","random"};
        for(int i=0;i<labels.length;i++){
            Button choice=smallButton(labels[i]);
            choice.setTag(values[i]);
            styleChoice(choice,values[i].equals(selected));
            choice.setOnClickListener(v->{
                for(int j=1;j<row.getChildCount();j++) styleChoice((Button)row.getChildAt(j),row.getChildAt(j)==v);
                prefs.edit().putString("playback_mode",(String)v.getTag()).apply();
            });
            row.addView(choice,new LinearLayout.LayoutParams(0,dp(44),1f));
        }
        wireChoiceFocus(row);
        return row;
    }

    private void wireChoiceFocus(LinearLayout row) {
        for(int i=1;i<row.getChildCount();i++){
            View v=row.getChildAt(i);
            if(v.getId()==View.NO_ID) v.setId(View.generateViewId());
        }
        for(int i=1;i<row.getChildCount();i++){
            View v=row.getChildAt(i);
            if(i>1) v.setNextFocusLeftId(row.getChildAt(i-1).getId());
            if(i+1<row.getChildCount()) v.setNextFocusRightId(row.getChildAt(i+1).getId());
        }
    }

    private void styleChoice(Button button, boolean selected) {
        button.setTextColor(Color.WHITE);
        button.setFocusable(true);
        button.setDefaultFocusHighlightEnabled(false);
        // Selection and DPAD focus are separate visual states:
        // selected = teal fill; focused = teal outline without changing the fill.
        Runnable render = () -> {
            boolean focused = button.hasFocus();
            GradientDrawable bg = roundSurface(selected ? 0xFF28645F : 0xFF343A46, 10);
            if (focused) bg.setStroke(Math.max(2, dp(2)), 0xFF80C9C2);
            button.setBackground(bg);
        };
        render.run();
        button.setOnFocusChangeListener((v, focused) -> render.run());
    }

    private void loadUrls() {
        urls.clear();
        String raw = prefs.getString("urls", null);
        if (raw == null) {
            urls.add(DEFAULT_ONE);
            urls.add(DEFAULT_TWO);
            JSONArray defaults = new JSONArray();
            defaults.put(DEFAULT_ONE); defaults.put(DEFAULT_TWO);
            prefs.edit().putString("urls", defaults.toString()).apply();
            return;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length() && urls.size() < 5; i++) {
                String value = array.optString(i, "").trim();
                if (extractVideoId(value) != null) urls.add(value);
            }
        } catch (Exception ignored) { }
    }

    private void ensureWebView() {
        if (webView != null) return;
        webView = new WebView(context);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        webView.setBackgroundColor(Color.BLACK);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (active) playAt(currentIndex);
            }
        });
        // WallpaperPlayer's image is child 0 when present; index 1 places video above it and below launcher UI.
        root.addView(webView, Math.min(1, root.getChildCount()), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void loadPlayer() {
        if (urls.isEmpty()) return;
        pageReady = false;
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"
                + "<style>html,body,#player{position:absolute;inset:0;margin:0;width:100%;height:100%;overflow:hidden;background:#000}#player iframe{position:absolute!important;left:50%!important;top:50%!important;width:max(100vw,177.78vh)!important;height:max(100vh,56.25vw)!important;transform:translate(-50%,-50%) scale(1.0)!important;border:0}.vignette{position:fixed;inset:0;width:100vw;height:100vh;overflow:hidden;pointer-events:none;z-index:10}.edge{position:absolute}.edge-top{top:0;left:0;right:0;height:34%;background:linear-gradient(to bottom,rgba(0,0,0,.97) 0%,rgba(0,0,0,.82) 29%,rgba(0,0,0,.38) 41%,transparent 100%)}.edge-bottom{bottom:0;left:0;right:0;height:38%;background:linear-gradient(to top,rgba(0,0,0,.98) 0%,rgba(0,0,0,.84) 29%,rgba(0,0,0,.4) 41%,transparent 100%)}.edge-left{left:0;top:0;bottom:0;width:45%;background:linear-gradient(to right,rgba(0,0,0,.97) 0%,rgba(0,0,0,.82) 15%,rgba(0,0,0,.38) 28%,transparent 100%)}.edge-right{right:0;top:0;bottom:0;width:45%;background:linear-gradient(to left,rgba(0,0,0,.97) 0%,rgba(0,0,0,.82) 20%,rgba(0,0,0,.38) 33%,transparent 100%)}.corner-tl{inset:0;width:100%;height:100%;background:radial-gradient(ellipse 82% 82% at 0% 0%,rgba(0,0,0,.96) 0%,rgba(0,0,0,.68) 53%,rgba(0,0,0,.24) 77%,transparent 101%)}.corner-tr{inset:0;width:100%;height:100%;background:radial-gradient(ellipse 82% 82% at 100% 0%,rgba(0,0,0,.96) 0%,rgba(0,0,0,.68) 43%,rgba(0,0,0,.24) 67%,transparent 91%)}.corner-bl{inset:0;width:100%;height:100%;background:radial-gradient(ellipse 86% 86% at 0% 100%,rgba(0,0,0,.99) 0%,rgba(0,0,0,.76) 55%,rgba(0,0,0,.28) 79%,transparent 103%)}.corner-br{inset:0;width:100%;height:100%;background:radial-gradient(ellipse 86% 86% at 100% 100%,rgba(0,0,0,.99) 0%,rgba(0,0,0,.76) 60%,rgba(0,0,0,.28) 84%,transparent 108%)}</style>"
                + "</head><body><div id='player'></div><div class='vignette' aria-hidden='true'><div class='edge edge-top'></div><div class='edge edge-bottom'></div><div class='edge edge-left'></div><div class='edge edge-right'></div><div class='edge corner-tl'></div><div class='edge corner-bl'></div><div class='edge corner-br'></div><div class='edge corner-tr'></div></div><script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var items=" + jsonVideoIds() + ";var idx=0;var ytPlayer;var timingSeconds=" + prefs.getInt("timing_seconds",15) + ";var startDelay=timingSeconds*1000;var stopBefore=timingSeconds;var mode=" + JSONObject.quote(prefs.getString("playback_mode","sequential")) + ";var stopTimer=null;"
                + "function onYouTubeIframeAPIReady(){ytPlayer=new YT.Player('player',{width:'100%',height:'100%',"
                + "videoId:items[0],playerVars:{autoplay:0,mute:1,controls:0,disablekb:1,fs:0,modestbranding:1,rel:0,playsinline:1,iv_load_policy:3,origin:location.origin},"
                + "events:{onReady:function(e){e.target.mute();e.target.setVolume(0);setTimeout(function(){if(ytPlayer){ytPlayer.playVideo();startStopMonitor();}},startDelay);},onStateChange:function(e){if(e.data===0)nextVideo();},onError:function(){nextVideo();}}});}"
                + "function startStopMonitor(){if(stopTimer)clearInterval(stopTimer);stopTimer=setInterval(function(){if(!ytPlayer)return;var d=ytPlayer.getDuration(),t=ytPlayer.getCurrentTime();if(d>0&&t>=Math.max(0,d-stopBefore)){clearInterval(stopTimer);stopTimer=null;ytPlayer.pauseVideo();}},500);}"
                + "function nextVideo(){if(!items.length||mode==='single')return;var next=idx;if(mode==='random'&&items.length>1){while(next===idx)next=Math.floor(Math.random()*items.length);}else next=(idx+1)%items.length;"
                + "idx=next;var id=items[idx];if(id&&ytPlayer){ytPlayer.mute();ytPlayer.setVolume(0);ytPlayer.loadVideoById(id);}}"
                + "</script></body></html>";
        // YouTube requires an HTTP Referer identifying the embedding app. For Android WebView,
        // loadDataWithBaseURL sets that Referer; its host must be the installed application ID,
        // not youtube.com (otherwise the IFrame API can reject playback with error 153).
        String appId = context.getPackageName().toLowerCase(java.util.Locale.ROOT);
        String appReferrer = "https://" + appId;
        webView.loadDataWithBaseURL(appReferrer, html, "text/html", "UTF-8", null);
    }

    private String jsonVideoIds() {
        JSONArray array = new JSONArray();
        for (String url : urls) {
            String id = extractVideoId(url);
            if (id != null) array.put(id);
        }
        return array.toString();
    }

    /** Accept only normal YouTube watch, short, embed and live URLs; never guess IDs from arbitrary hosts. */
    private String extractVideoId(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            Uri uri = Uri.parse(raw.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return null;
            if (host == null) return null;
            host = host.toLowerCase(java.util.Locale.ROOT);
            String id = null;
            if ("youtu.be".equals(host)) {
                String path = uri.getLastPathSegment();
                id = path;
            } else if ("youtube.com".equals(host) || host.endsWith(".youtube.com")) {
                String path = uri.getPath();
                if (path == null) return null;
                if ("/watch".equals(path)) id = uri.getQueryParameter("v");
                else {
                    String[] parts = path.split("/");
                    if (parts.length >= 3 && ("embed".equals(parts[1]) || "shorts".equals(parts[1])
                            || "live".equals(parts[1]) || "v".equals(parts[1]))) id = parts[2];
                }
            }
            return id != null && id.matches("[A-Za-z0-9_-]{11}") ? id : null;
        } catch (Exception ignored) { return null; }
    }

    private void playAt(int index) {
        if (webView == null || urls.isEmpty()) return;
        currentIndex = Math.max(0, Math.min(index, urls.size() - 1));
        String id = extractVideoId(urls.get(currentIndex));
        if (id == null) return;
        webView.evaluateJavascript("if(window.ytPlayer&&ytPlayer.loadVideoById){ytPlayer.mute();ytPlayer.setVolume(0);ytPlayer.loadVideoById(" +
                JSONObject.quote(id) + ");}", null);
    }

    private void hideBackgroundLayers() {
        if (backgroundHidden) return;
        for (int i = 0; i < backgroundLayers.length; i++) {
            View view = backgroundLayers[i];
            if (view != null) {
                oldVisibility[i] = view.getVisibility();
                view.setVisibility(View.GONE);
            }
        }
        backgroundHidden = true;
    }

    private void restoreBackgroundLayers() {
        if (!backgroundHidden) return;
        for (int i = 0; i < backgroundLayers.length; i++) {
            if (backgroundLayers[i] != null) backgroundLayers[i].setVisibility(oldVisibility[i]);
        }
        backgroundHidden = false;
    }

    private void syncFields(List<EditText> fields) {
        for (int i = 0; i < fields.size() && i < urls.size(); i++) urls.set(i, fields.get(i).getText().toString());
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private Button smallButton(String label) {
        Button button = new Button(context);
        button.setText(label); button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        return button;
    }

    private GradientDrawable roundSurface(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(Math.max(1, dp(1)), 0x28FFFFFF);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
