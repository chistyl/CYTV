package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Optional, user-enabled helper that reads accessible text from the YouTube TV app only.
 * It never captures pixels, uploads screen content, or submits a pairing code.
 */
public class YouTubePairingCodeAccessibilityService extends AccessibilityService {
    static final String ACTION_CODE_DETECTED =
            "com.gathin.tvhome.action.YOUTUBE_PAIRING_CODE_DETECTED";
    static final String EXTRA_CODE = "pairing_code";
    private static final String YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv";
    private static final String PREFS = "youtube_pairing_detection";
    private static final String PREF_CODE = "detected_code";
    private static final String PREF_TIME = "detected_at";
    private static final int MAX_NODES = 240;
    private static final int MAX_DEPTH = 12;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastBroadcastCode = "";
    private long lastBroadcastAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !YOUTUBE_TV_PACKAGE.contentEquals(event.getPackageName())) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;

        // YouTube TV may emit the window event before its pairing-code nodes are attached.
        // Retry briefly after the UI has had time to populate its accessibility tree.
        handler.removeCallbacks(scanRunnable);
        handler.postDelayed(scanRunnable, 180L);
        handler.postDelayed(scanRunnable, 550L);
        handler.postDelayed(scanRunnable, 1100L);
    }

    private final Runnable scanRunnable = this::scanActiveWindow;

    private void scanActiveWindow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            StringBuilder separated = new StringBuilder();
            StringBuilder compact = new StringBuilder();
            int[] visited = {0};
            collectAccessibleText(root, 0, visited, separated, compact);
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
        super.onDestroy();
    }
}
