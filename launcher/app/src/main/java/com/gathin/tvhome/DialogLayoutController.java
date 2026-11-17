package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Keeps launcher-owned dialogs inside the actually visible display area.
 *
 * The visible frame is used instead of raw display dimensions so system bars,
 * overscan and device-specific safe areas are respected where the firmware reports them.
 */
final class DialogLayoutController {
    private static final float MAX_WIDTH_FRACTION = 0.92f;
    private static final float MAX_HEIGHT_FRACTION = 0.90f;
    private static final int MIN_EDGE_DP = 24;

    private DialogLayoutController() { }

    static void apply(Dialog dialog, Activity activity) {
        if (dialog == null || activity == null || activity.isFinishing()) return;
        final Window window = dialog.getWindow();
        if (window == null) return;

        final View decor = window.getDecorView();
        decor.post(() -> {
            if (!dialog.isShowing() || activity.isFinishing()) return;

            Rect visible = new Rect();
            decor.getWindowVisibleDisplayFrame(visible);
            if (visible.width() <= 0 || visible.height() <= 0) {
                activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
            }
            if (visible.width() <= 0 || visible.height() <= 0) return;

            float density = activity.getResources().getDisplayMetrics().density;
            int edge = Math.max(1, Math.round(MIN_EDGE_DP * density));
            int maxWidth = Math.max(1, Math.min(
                    Math.round(visible.width() * MAX_WIDTH_FRACTION),
                    visible.width() - edge * 2));
            int maxHeight = Math.max(1, Math.min(
                    Math.round(visible.height() * MAX_HEIGHT_FRACTION),
                    visible.height() - edge * 2));

            WindowManager.LayoutParams params = window.getAttributes();
            int width = params.width;
            int height = params.height;

            if (width == WindowManager.LayoutParams.MATCH_PARENT || width > maxWidth) {
                width = maxWidth;
            } else if (width <= 0) {
                width = Math.min(decor.getMeasuredWidth(), maxWidth);
            }
            if (height == WindowManager.LayoutParams.MATCH_PARENT || height > maxHeight) {
                height = maxHeight;
            } else if (height <= 0 && decor.getMeasuredHeight() > maxHeight) {
                height = maxHeight;
            }

            window.setLayout(width, height);
            window.setGravity(android.view.Gravity.CENTER);
        });
    }
}
