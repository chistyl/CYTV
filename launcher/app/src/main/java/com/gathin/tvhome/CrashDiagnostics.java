package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Persists the last Java crash and the last wallpaper-picker lifecycle stage.
 * This is a best-effort diagnostic aid; it cannot capture native process aborts or every OOM.
 */
final class CrashDiagnostics {
    private static final String PREFS = "cytv_crash_diagnostics";
    private static final String KEY_CRASH = "last_crash";
    private static final String KEY_STAGE = "last_stage";
    private static final String KEY_TIME = "last_stage_time";
    private static boolean installed;

    private CrashDiagnostics() {}

    static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter writer = new StringWriter();
                error.printStackTrace(new PrintWriter(writer));
                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_CRASH,
                        "Thread: " + thread.getName() + "\n" + writer.toString()).commit();
            } catch (Throwable ignored) {
                // Never interfere with Android's normal crash handling.
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static void stage(Context context, String stage) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_STAGE, stage)
                    .putString(KEY_TIME, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(new Date()))
                    .commit();
        } catch (Throwable ignored) { }
    }

    static void showPreviousCrashIfAny(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final String crash = prefs.getString(KEY_CRASH, null);
        if (TextUtils.isEmpty(crash) || activity.isFinishing()) return;
        final String stage = prefs.getString(KEY_STAGE, "unknown");
        final String time = prefs.getString(KEY_TIME, "unknown");
        final String report = "上次崩潰階段：" + stage + "\n時間：" + time + "\n\n" + crash;
        TextView body = new TextView(activity);
        body.setText(report);
        body.setTextColor(0xFFE8EAF0);
        body.setTextSize(12);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(20, 16, 20, 16);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("CYTV 上次錯誤診斷")
                .setView(scroll)
                .setNegativeButton("關閉", null)
                .setNeutralButton("複製錯誤內容", (d, which) -> {
                    try {
                        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("CYTV crash report", report));
                    } catch (Throwable ignored) { }
                })
                .setPositiveButton("清除紀錄", (d, which) ->
                        prefs.edit().remove(KEY_CRASH).remove(KEY_STAGE).remove(KEY_TIME).apply())
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        Math.min((int)(620 * activity.getResources().getDisplayMetrics().density),
                                activity.getResources().getDisplayMetrics().widthPixels - 32),
                        Math.min((int)(460 * activity.getResources().getDisplayMetrics().density),
                                activity.getResources().getDisplayMetrics().heightPixels - 48));
            }
        });
        dialog.show();
    }
}
