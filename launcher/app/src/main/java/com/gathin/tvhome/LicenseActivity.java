package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Offline activation screen. It intentionally has no network dependency. */
public final class LicenseActivity extends Activity {
    private EditText codeInput;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setBackgroundDrawableResource(android.R.color.black);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(56), dp(28), dp(56), dp(28));
        root.setBackgroundColor(Color.rgb(12, 14, 18));

        TextView title = new TextView(this);
        title.setText("啟用 CYTV");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView hint = new TextView(this);
        hint.setText("請輸入產品授權碼。此程序不需要連接網路。");
        hint.setTextColor(Color.LTGRAY);
        hint.setTextSize(17);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(48));
        hp.bottomMargin = dp(18);
        root.addView(hint, hp);

        codeInput = new EditText(this);
        codeInput.setSingleLine(true);
        codeInput.setTextSize(17);
        codeInput.setTextColor(Color.WHITE);
        codeInput.setHintTextColor(Color.GRAY);
        codeInput.setHint("貼上或輸入授權碼");
        codeInput.setSelectAllOnFocus(false);
        codeInput.setPadding(dp(16), dp(10), dp(16), dp(10));
        codeInput.setBackgroundColor(Color.rgb(38, 42, 50));
        root.addView(codeInput, new LinearLayout.LayoutParams(-1, dp(58)));

        Button activate = new Button(this);
        activate.setText("啟用");
        activate.setTextSize(18);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(240), dp(54));
        bp.gravity = Gravity.CENTER;
        bp.topMargin = dp(22);
        root.addView(activate, bp);
        activate.setOnClickListener(v -> activate());

        TextView foot = new TextView(this);
        foot.setText("授權成功後，本設備即可離線使用。清除應用程式資料或重新安裝後需重新啟用。");
        foot.setTextColor(Color.GRAY);
        foot.setTextSize(13);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(54));
        fp.topMargin = dp(18);
        root.addView(foot, fp);

        setContentView(root);
        codeInput.requestFocus();
    }

    private void activate() {
        LicenseVerifier.Result result = LicenseManager.activate(this, codeInput.getText().toString());
        if (result.isValid()) {
            Toast.makeText(this, "啟用成功", Toast.LENGTH_SHORT).show();
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            finish();
        } else {
            String message = result.status == LicenseVerifier.Status.EXPIRED
                    ? "授權碼已到期，請確認授權期限。"
                    : "授權碼無效，請檢查內容後再試一次。";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            codeInput.requestFocus();
        }
    }

    @Override public void onBackPressed() {
        // Do not allow Back to bypass the startup gate.
        Toast.makeText(this, "請先完成產品啟用", Toast.LENGTH_SHORT).show();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}