package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** First-launch activation screen. No diagnostic details or secret material are displayed. */
public final class ActivationActivity extends Activity {
    private EditText codeInput;
    private TextView message;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(12, 19, 25));
        getWindow().setNavigationBarColor(Color.rgb(12, 19, 25));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(64), dp(36), dp(64), dp(36));
        root.setBackgroundColor(Color.rgb(12, 19, 25));

        TextView title = new TextView(this);
        title.setText("CYTV 啟用驗證");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText("首次使用前，請輸入啟用驗證碼\n驗證成功後即可使用本設備");
        hint.setTextColor(Color.LTGRAY);
        hint.setTextSize(17);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(18);
        root.addView(hint, hp);

        codeInput = new EditText(this);
        codeInput.setSingleLine(true);
        codeInput.setTextSize(16);
        codeInput.setTextColor(Color.WHITE);
        codeInput.setHintTextColor(Color.GRAY);
        codeInput.setHint("請輸入啟用驗證碼");
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeInput.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(560), dp(62));
        ip.topMargin = dp(28);
        root.addView(codeInput, ip);

        message = new TextView(this);
        message.setTextColor(Color.rgb(255, 130, 120));
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(32));
        mp.topMargin = dp(8);
        root.addView(message, mp);

        Button activate = new Button(this);
        activate.setText("啟用並進入桌面");
        activate.setTextSize(17);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(300), dp(58));
        bp.topMargin = dp(12);
        root.addView(activate, bp);
        activate.setOnClickListener(v -> submit());
        codeInput.setOnEditorActionListener((v, actionId, event) -> { submit(); return true; });
        setContentView(root);
        codeInput.requestFocus();
    }

    private void submit() {
        String token = codeInput.getText() == null ? "" : codeInput.getText().toString().trim();
        if (token.isEmpty() || !ActivationGate.activate(this, token)) {
            message.setText("驗證碼不正確，請重新輸入");
            codeInput.requestFocus();
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
