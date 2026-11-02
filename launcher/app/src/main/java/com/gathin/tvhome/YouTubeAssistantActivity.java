package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * TV-first landing page for the YouTube helper. Pairing and SponsorBlock are intentionally
 * presented as unavailable until their services are implemented and tested.
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

        addRow(rows, "YouTube TV 配對", "尚未連線", "配對功能尚在開發中，完成通訊協定與實機測試後才會開放。");
        addRow(rows, "SponsorBlock 片段略過", "尚未啟用", "尚未連接 SponsorBlock API，目前不會自動略過影片片段。");
        addRow(rows, "自動略過贊助片段", "功能準備中", "待配對與片段偵測完成後加入。");
        addRow(rows, "連線狀態與診斷", "尚無連線資料", "配對功能完成後，這裡會顯示裝置、連線與錯誤資訊。");

        TextView note = new TextView(this);
        note.setText("注意：此頁目前為設定介面第一階段，尚未執行配對、靜音或自動略過。");
        note.setTextColor(MUTED);
        note.setTextSize(13);
        note.setPadding(dp(4), dp(16), dp(4), 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        back.requestFocus();
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
