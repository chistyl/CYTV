package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.Deque;

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
    private String lastBroadcastCode = "";
    private long lastBroadcastAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !YOUTUBE_TV_PACKAGE.contentEquals(event.getPackageName())) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            String code = findCode(root);
            if (!code.isEmpty()) publishCode(code);
        } finally {
            root.recycle();
        }
    }

    private String findCode(AccessibilityNodeInfo root) {
        return findCodeInNode(root, 0, new int[]{0});
    }

    private String findCodeInNode(AccessibilityNodeInfo node, int depth, int[] visited) {
        if (node == null || depth > 8 || visited[0] >= 160) return "";
        visited[0]++;
        String code = YouTubePairingCodeParser.findCode(node.getText());
        if (code.isEmpty()) code = YouTubePairingCodeParser.findCode(node.getContentDescription());
        if (!code.isEmpty()) return code;
        for (int i = 0; i < node.getChildCount() && visited[0] < 160; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                code = findCodeInNode(child, depth + 1, visited);
                if (!code.isEmpty()) return code;
            } finally {
                child.recycle();
            }
        }
        return "";
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
        sendBroadcast(new android.content.Intent(ACTION_CODE_DETECTED)
                .setPackage(getPackageName())
                .putExtra(EXTRA_CODE, code));
    }

    @Override public void onInterrupt() { }

    private static final class NodeDepth {
        final AccessibilityNodeInfo node;
        final int depth;
        NodeDepth(AccessibilityNodeInfo node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}
