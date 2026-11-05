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
        Deque<NodeDepth> pending = new ArrayDeque<>();
        pending.add(new NodeDepth(root, 0));
        int visited = 0;
        while (!pending.isEmpty() && visited < 160) {
            NodeDepth item = pending.removeFirst();
            AccessibilityNodeInfo node = item.node;
            visited++;
            CharSequence text = node.getText();
            String code = YouTubePairingCodeParser.findCode(text);
            if (code.isEmpty()) code = YouTubePairingCodeParser.findCode(node.getContentDescription());
            if (!code.isEmpty()) return code;
            if (item.depth >= 8) continue;
            for (int i = 0; i < node.getChildCount() && visited + pending.size() < 160; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) pending.addLast(new NodeDepth(child, item.depth + 1));
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
