package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/**
 * Offline icon presentation for Tool Center. The two CYTV modes are deterministic local
 * treatments, not a claim of on-device generative AI. Original package assets are never changed.
 */
final class ToolIconProvider {
    private final Context context;
    private final File cacheDir;

    ToolIconProvider(Context context) {
        this.context = context.getApplicationContext();
        cacheDir = new File(this.context.getCacheDir(), "cytv-tool-icons");
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    Drawable load(ToolRepository.Tool tool, ToolRepository.IconMode mode) {
        File cached = new File(cacheDir, key(tool, mode) + ".png");
        if (cached.isFile()) {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(cached.getAbsolutePath());
            if (bitmap != null) return new BitmapDrawable(context.getResources(), bitmap);
        }

        Drawable original;
        try {
            original = context.getPackageManager().getActivityIcon(
                    new ComponentName(tool.packageName, tool.activityName));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            try {
                original = context.getPackageManager().getApplicationIcon(tool.packageName);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                return context.getDrawable(android.R.drawable.sym_def_app_icon);
            }
        }
        if (mode == ToolRepository.IconMode.ORIGINAL) return original;

        Bitmap rendered = render(original, mode);
        if (rendered == null) return original;
        try (FileOutputStream stream = new FileOutputStream(cached)) {
            rendered.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.flush();
        } catch (Exception ignored) {
            // Cache is an optimization only; the generated bitmap remains usable in memory.
        }
        return new BitmapDrawable(context.getResources(), rendered);
    }

    private Bitmap render(Drawable source, ToolRepository.IconMode mode) {
        final int size = 192;
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        int background = mode == ToolRepository.IconMode.CYTV_MINIMAL ? 0xFF17232D : 0xFF16434B;
        GradientDrawable plate = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                mode == ToolRepository.IconMode.CYTV_MINIMAL
                        ? new int[]{0xFF25313B, 0xFF101820}
                        : new int[]{0xFF24616A, 0xFF102B35});
        plate.setCornerRadius(42f);
        plate.setBounds(0, 0, size, size);
        plate.draw(canvas);

        int inset = mode == ToolRepository.IconMode.CYTV_MINIMAL ? 38 : 28;
        Rect bounds = new Rect(inset, inset, size - inset, size - inset);
        source.setBounds(bounds);
        if (mode == ToolRepository.IconMode.CYTV_MINIMAL) {
            source.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
        }
        source.draw(canvas);
        source.clearColorFilter();

        if (mode == ToolRepository.IconMode.CYTV_SMART) {
            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setColor(0x44FFFFFF);
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeWidth(3f);
            canvas.drawRoundRect(new RectF(5, 5, size - 5, size - 5), 38f, 38f, glow);
        } else {
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setColor(0x6678C6C9);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2f);
            canvas.drawRoundRect(new RectF(8, 8, size - 8, size - 8), 36f, 36f, line);
        }
        return output;
    }

    private String key(ToolRepository.Tool tool, ToolRepository.IconMode mode) {
        String raw = tool.packageName + "|" + tool.activityName + "|" + mode.name();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes("UTF-8"));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format(java.util.Locale.US, "%02x", value));
            return result.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
