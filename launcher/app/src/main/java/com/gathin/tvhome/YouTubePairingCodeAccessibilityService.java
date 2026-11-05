package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Optional helper that reads accessible text from the YouTube TV app only.
 * It never captures pixels, uploads screen content, or submits a pairing code.
 */
public class YouTubePairingCodeAccessibilityService extends AccessibilityService {
    static final String ACTION_CODE_DETECTED =
            "com.gathin.tvhome.action.YOUTUBE_PAIRING_CODE_DETECTED";
    static final String EXTRA_CODE = "pairing_code";
    static final String PREFS = "youtube_pairing_detection";
    private static final String YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv";
    private static final String PREF_CODE = "detected_code";
    private static final String PREF_TIME = "detected_at";
    private static final int MAX_NODES = 320;
    private static final int MAX_DEPTH = 16;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastBroadcastCode = "";
    private long lastBroadcastAt;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("service_connected", true)
                .putLong("service_connected_at", System.currentTimeMillis())
                .apply();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !YOUTUBE_TV_PACKAGE.contentEquals(event.getPackageName())) return;
        int type = event.getEventType();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("last_event_at", System.currentTimeMillis())
                .putInt("event_count", getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getInt("event_count", 0) + 1)
                .putInt("last_event_type", type)
                .apply();

        // The code may be attached after the initial window event or arrive as text changes.
        handler.removeCallbacks(scanRunnable);
        handler.postDelayed(scanRunnable, 180L);
        handler.postDelayed(scanRunnable, 550L);
        handler.postDelayed(scanRunnable, 1100L);
        handler.postDelayed(scanRunnable, 2200L);
    }

    private final Runnable scanRunnable = this::scanActiveWindow;

    private void scanActiveWindow() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int scans = prefs.getInt("scan_count", 0) + 1;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            prefs.edit().putInt("scan_count", scans)
                    .putInt("root_null_count", prefs.getInt("root_null_count", 0) + 1)
                    .putLong("last_scan_at", System.currentTimeMillis()).apply();
            return;
        }
        try {
            StringBuilder separated = new StringBuilder();
            StringBuilder compact = new StringBuilder();
            int[] visited = {0};
            collectAccessibleText(root, 0, visited, separated, compact);
            prefs.edit().putInt("scan_count", scans)
                    .putInt("last_node_count", visited[0])
                    .putInt("last_text_length", separated.length())
                    .putLong("last_scan_at", System.currentTimeMillis()).apply();
            String code = YouTubePairingCodeParser.findCode(separated);
            if (code.isEmpty()) code = YouTubePairingCodeParser.findCode(compact);
            if (!code.isEmpty()) publishCode(code);
        } finally {
            root.recycle();
        }
    }

    private void collectAccessibleText(AccessibilityNodeInfo node, int depth, int[] visited,
                                       StringBuilder separated, StringBuilder compact) {
        if (node == null || depth > MAX_DEPTH || visited[0] >= MAX_NODES) return;
        visited[0]++;
        appendText(node.getText(), separated, compact);
        appendText(node.getContentDescription(), separated, compact);
        for (int i = 0; i < node.getChildCount() && visited[0] < MAX_NODES; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                collectAccessibleText(child, depth + 1, visited, separated, compact);
            } finally {
                child.recycle();
            }
        }
    }

    private void appendText(CharSequence value, StringBuilder separated, StringBuilder compact) {
        if (value == null || value.length() == 0) return;
        if (separated.length() > 0) separated.append(' ');
        separated.append(value);
        compact.append(value);
    }

    private void publishCode(String code) {
        long now = SystemClock.elapsedRealtime();
        if (code.equals(lastBroadcastCode) && now - lastBroadcastAt < 2500L) return;
        lastBroadcastCode = code;
        lastBroadcastAt = now;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_CODE, code)
                .putLong(PREF_TIME, System.currentTimeMillis())
                .putLong("last_detected_at", System.currentTimeMillis())
                .apply();
        sendBroadcast(new Intent(ACTION_CODE_DETECTED)
                .setPackage(getPackageName())
                .putExtra(EXTRA_CODE, code));
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("service_connected", false).apply();
        super.onDestroy();
    }
}
