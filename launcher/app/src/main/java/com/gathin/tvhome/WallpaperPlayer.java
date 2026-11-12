package com.gathin.tvhome;

import android.content.Context;
import android.app.AlertDialog;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final Handler rotationHandler = new Handler(Looper.getMainLooper());
    private static final long BUILTIN_RANDOM_INTERVAL_MS = 30 * 60_000L;
    /** One shared one-minute clock checks both built-in and custom wallpaper rotation deadlines. */
    private static final long ROTATION_POLL_INTERVAL_MS = 60_000L;
    /** Custom gallery slideshow cadence: sequential and random modes both advance once per minute. */
    private static final long CUSTOM_WALLPAPER_INTERVAL_MS = 60_000L;
    /** Selection is persistent gallery state; this flag alone decides whether custom wallpapers are active. */
    private boolean customWallpaperEnabled() {
        return preferences.getBoolean("custom_wallpaper_enabled",
                preferences.getString("custom_wallpaper_file", null) != null
                        || preferences.getString("custom_wallpaper_uri", null) != null);
    }
    private final Runnable builtinRandomTick = new Runnable() {
        @Override public void run() {
            if (!active || closed) return;
            rotateBuiltinWallpaper(false);
            rotationHandler.postDelayed(this, ROTATION_POLL_INTERVAL_MS);
        }
    };

    /** Uses a persisted deadline so standby/Home lifecycle restarts do not reset the 30-minute interval. */
    private boolean rotateBuiltinWallpaper(boolean force) {
        if (!preferences.getBoolean("builtin_random_play", false)
                || preferences.getString("custom_wallpaper_uri", null) != null
                || preferences.getString("custom_wallpaper_file", null) != null
                || customWallpaperEnabled()) {
            recordDiagnostic(root.getContext(), "內建輪播略過：未啟用或自訂桌布正在使用");
            return false;
        }
        int currentTheme = preferences.getInt("builtin_wallpaper", 0);
        if (currentTheme < 1 || currentTheme > 9) {
            recordDiagnostic(root.getContext(), "內建輪播略過：目前預設圖片編號無效");
            return false;
        }
        long now = System.currentTimeMillis();
        long changed = preferences.getLong("builtin_wallpaper_changed", 0);
        if (!force && !WallpaperRotation.due(changed, now, BUILTIN_RANDOM_INTERVAL_MS)) {
            return false;
        }
        int nextTheme = 1 + random.nextInt(8);
        if (nextTheme >= currentTheme) nextTheme++;
        int[] themes = {R.drawable.wallpaper_theme_01, R.drawable.wallpaper_theme_02,
                R.drawable.wallpaper_theme_03, R.drawable.wallpaper_theme_04,
                R.drawable.wallpaper_theme_05, R.drawable.wallpaper_theme_06,
                R.drawable.wallpaper_theme_07, R.drawable.wallpaper_theme_08,
                R.drawable.wallpaper_theme_09};
        image.setImageResource(themes[nextTheme - 1]);
        displayed = "builtin_" + nextTheme;
        preferences.edit().putInt("builtin_wallpaper", nextTheme)
                .putLong("builtin_wallpaper_changed", now).apply();
        recordDiagnostic(root.getContext(), "內建圖片切換成功：builtin_" + nextTheme
                + (force ? "（手動測試）" : "（到期自動切換）"));
        return true;
    }
    /** Dedicated custom-gallery clock; independent of Home's general minute refresh. */
    private final Runnable customRotationTick = new Runnable() {
        @Override public void run() {
            if (!active || closed) {
                Log.w("TvHome.Wallpaper", "R102 custom tick skipped: active=" + active + ", closed=" + closed);
                recordDiagnostic(root.getContext(), "計時器略過：active=" + active + "，closed=" + closed);
                return;
            }
            Log.i("TvHome.Wallpaper", "R102 custom 60s tick; displayed=" + displayed);
            recordDiagnostic(root.getContext(), "計時器觸發（60 秒）；目前=" + shortName(displayed));
            update(true);
            if (active && !closed) {
                rotationHandler.postDelayed(this, CUSTOM_WALLPAPER_INTERVAL_MS);
            }
        }
    };
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        backgroundColor = config.backgroundColor;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        if ((customWallpaperEnabled() && !CustomWallpaperLibrary.selected(context).isEmpty())
                || preferences.getInt("builtin_wallpaper", 0) > 0
                || preferences.getString("custom_wallpaper_uri", null) != null
                || preferences.getString("custom_wallpaper_file", null) != null
                || (config.wallpaperEnabled && !files.isEmpty())) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
        // Post after MainActivity finishes adding its overlays, then put the diagnostic entry above the Home UI.
        root.post(() -> installDiagnostics(context));
    }

    private static final String DIAGNOSTIC_KEY = "custom_wallpaper_diagnostic_events";

    private static synchronized void recordDiagnostic(Context context, String message) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
            String old = prefs.getString(DIAGNOSTIC_KEY, "");
            String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String[] lines = old.isEmpty() ? new String[0] : old.split("\\n");
            StringBuilder next = new StringBuilder();
            int first = Math.max(0, lines.length - 17);
            for (int i = first; i < lines.length; i++) next.append(lines[i]).append('\n');
            next.append(stamp).append("  ").append(message);
            prefs.edit().putString(DIAGNOSTIC_KEY, next.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static String shortName(String path) {
        if (path == null || path.isEmpty()) return "（尚無）";
        return new File(path).getName();
    }

    /** On-device diagnostic entry; no ADB or computer is needed to inspect the last rotation attempts. */
    private void installDiagnostics(Context context) {
        // Always expose diagnostics in this diagnostic build, even if the selected-image list is unexpectedly empty.
        float density = context.getResources().getDisplayMetrics().density;
        TextView action = new TextView(context);
        action.setText("圖片診斷");
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        action.setTextColor(0xFFFFFFFF);
        action.setGravity(Gravity.CENTER);
        action.setPadding(Math.round(14 * density), 0, Math.round(14 * density), 0);
        action.setBackground(roundDiagnosticSurface());
        action.setFocusable(true);
        action.setFocusableInTouchMode(true);
        action.setClickable(true);
        action.setContentDescription("查看自訂圖片輪播診斷");
        action.setOnClickListener(v -> showDiagnostics());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(38 * density),
                Gravity.TOP | Gravity.START);
        params.topMargin = Math.round(112 * density);
        params.leftMargin = Math.round(24 * density);
        root.addView(action, params);
        action.setElevation(24 * density);
        action.setTranslationZ(24 * density);
        action.bringToFront();
    }

    private static android.graphics.drawable.GradientDrawable roundDiagnosticSurface() {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(0xE6222B38);
        shape.setCornerRadius(10);
        shape.setStroke(1, 0xFF80CBC4);
        return shape;
    }

    private void showDiagnostics() {
        Context context = root.getContext();
        List<File> selected = CustomWallpaperLibrary.selected(context);
        String mode = CustomWallpaperLibrary.mode(context);
        SharedPreferences prefs = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        String current = prefs.getString("custom_wallpaper_current", displayed);
        String events = prefs.getString(DIAGNOSTIC_KEY, "尚無執行紀錄");
        StringBuilder report = new StringBuilder();
        List<File> library = CustomWallpaperLibrary.list(context);
        boolean builtinRandom = prefs.getBoolean("builtin_random_play", false);
        int builtinTheme = prefs.getInt("builtin_wallpaper", 0);
        long builtinChanged = prefs.getLong("builtin_wallpaper_changed", 0);
        report.append("播放模式：").append(modeLabel(mode)).append('\n');
        report.append("自訂圖片庫：").append(library.size()).append(" 張；已選播放：").append(selected.size()).append(" 張\n");
        report.append("內建隨機播放：").append(builtinRandom ? "開啟" : "關閉")
                .append("；目前編號：").append(builtinTheme).append('\n');
        report.append("內建上次切換：").append(builtinChanged == 0 ? "尚無紀錄"
                : new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(new Date(builtinChanged))).append('\n');
        report.append("目前圖片：").append(shortName(current)).append('\n');
        report.append("播放器：").append(active ? "執行中" : "未啟動")
                .append("；解碼：").append(loading ? "進行中" : "閒置").append('\n');
        report.append("上次切換時間：");
        long changed = prefs.getLong("custom_wallpaper_changed", 0);
        report.append(changed == 0 ? "無紀錄" : new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(new Date(changed)));
        report.append("\n\n圖片清單：\n");
        for (int i = 0; i < selected.size(); i++) {
            File file = selected.get(i);
            report.append(i + 1).append(". ").append(file.getName())
                    .append(file.isFile() && file.length() > 0 ? "（可讀取）" : "（檔案異常）").append('\n');
        }
        report.append("\n最近執行紀錄：\n").append(events);
        new AlertDialog.Builder(context)
                .setTitle("自訂圖片輪播診斷")
                .setMessage(report.toString())
                .setPositiveButton("手動測試切換", (dialog, which) -> {
                    recordDiagnostic(context, "使用者手動測試切換");
                    if (!customWallpaperEnabled()
                            && preferences.getBoolean("builtin_random_play", false)) {
                        rotateBuiltinWallpaper(true);
                    } else {
                        update(true);
                    }
                    root.postDelayed(this::showDiagnostics, 1200);
                })
                .setNeutralButton("清除紀錄", (dialog, which) -> {
                    prefs.edit().remove(DIAGNOSTIC_KEY).apply();
                    recordDiagnostic(context, "診斷紀錄已清除");
                    showDiagnostics();
                })
                .setNegativeButton("關閉", null)
                .show();
    }

    private static String modeLabel(String mode) {
        if ("sequential".equals(mode)) return "依序播放";
        if ("random".equals(mode)) return "隨機播放";
        return "單一圖片";
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        List<File> customSelection = CustomWallpaperLibrary.selected(root.getContext());
        if (customWallpaperEnabled() && !customSelection.isEmpty()) {
            String mode = CustomWallpaperLibrary.mode(root.getContext());
            Log.i("TvHome.Wallpaper", "R102 initial custom selection; count=" + customSelection.size()
                    + ", mode=" + mode + ", displayed=" + displayed);
            recordDiagnostic(root.getContext(), "啟動讀取圖片清單：" + customSelection.size()
                    + " 張，模式=" + modeLabel(mode));
            String previousCustom = preferences.getString("custom_wallpaper_current", "");
            File selected = WallpaperRotation.find(customSelection, previousCustom);
            if (selected == null) selected = customSelection.get(0);
            if ("random".equals(mode) && customSelection.size() > 1) {
                selected = WallpaperRotation.pick(customSelection, "", random);
            }
            Bitmap customBitmap = decode(selected);
            if (customBitmap != null) {
                show(customBitmap);
                displayed = selected.getAbsolutePath();
                recordDiagnostic(root.getContext(), "初始圖片顯示成功：" + selected.getName());
                preferences.edit().putString("custom_wallpaper_current", displayed)
                        .putLong("custom_wallpaper_changed", System.currentTimeMillis()).apply();
                showCaption(false);
                return;
            }
        }
        int builtIn = preferences.getInt("builtin_wallpaper", 0);
        if (builtIn >= 1 && builtIn <= 9) {
            int[] themes = {R.drawable.wallpaper_theme_01, R.drawable.wallpaper_theme_02,
                    R.drawable.wallpaper_theme_03, R.drawable.wallpaper_theme_04,
                    R.drawable.wallpaper_theme_05, R.drawable.wallpaper_theme_06,
                    R.drawable.wallpaper_theme_07, R.drawable.wallpaper_theme_08,
                    R.drawable.wallpaper_theme_09};
            image.setImageResource(themes[builtIn - 1]);
            root.setBackground(null);
            displayed = "builtin_" + builtIn;
            if (preferences.getLong("builtin_wallpaper_changed", 0) == 0) {
                preferences.edit().putLong("builtin_wallpaper_changed", System.currentTimeMillis()).apply();
            }
            showCaption(false);
            return;
        }
        String customFile = preferences.getString("custom_wallpaper_file", null);
        if (customFile != null) {
            File selected = new File(customFile);
            Bitmap custom = selected.isFile() ? decode(selected) : null;
            if (custom != null) {
                show(custom);
                displayed = "custom";
                showCaption(false);
                return;
            }
            preferences.edit().remove("custom_wallpaper_file").apply();
        }
        String customUri = preferences.getString("custom_wallpaper_uri", null);
        if (customUri != null) {
            Bitmap custom = decodeUri(Uri.parse(customUri));
            if (custom != null) {
                show(custom);
                displayed = "custom";
                showCaption(false);
                return;
            }
            preferences.edit().remove("custom_wallpaper_uri").apply();
        }
        String previous = preferences.getString("image", "");
        List<File> playable = playable();
        File first = WallpaperRotation.find(playable, previous);
        if (first == null) first = WallpaperRotation.pick(playable, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                displayed = first.getName();
                showCaption(false);
                if (!displayed.equals(previous)) preferences.edit().putString("image", displayed)
                        .putLong("changed", System.currentTimeMillis()).apply();
                return;
            }
            files.remove(first);
            playable = playable();
            first = WallpaperRotation.pick(playable, previous, order, random);
        }
    }

    private Bitmap decodeUri(Uri uri) {
        try (java.io.InputStream in = root.getContext().getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
            int sample = WallpaperSize.sampleSize(opts.outWidth, opts.outHeight, width, height);
            try (java.io.InputStream imageIn = root.getContext().getContentResolver().openInputStream(uri)) {
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                opts.inPreferredConfig = bitmapConfig;
                Bitmap decoded = BitmapFactory.decodeStream(imageIn, null, opts);
                if (decoded == null) return null;
                if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
                Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
                if (gpu == null) return decoded;
                decoded.recycle();
                return gpu;
            }
        } catch (Exception | OutOfMemoryError failure) { return null; }
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (current == null) return;
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our references and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        displayed = "";
        root.setBackgroundColor(backgroundColor);
    }

    void start() {
        active = true;
        Log.i("TvHome.Wallpaper", "R102 start; selected=" + CustomWallpaperLibrary.selected(root.getContext()).size()
                + ", mode=" + CustomWallpaperLibrary.mode(root.getContext()) + ", displayed=" + displayed);
        recordDiagnostic(root.getContext(), "播放器啟動；選取 "
                + CustomWallpaperLibrary.selected(root.getContext()).size() + " 張，模式="
                + modeLabel(CustomWallpaperLibrary.mode(root.getContext())));
        rotationHandler.removeCallbacks(builtinRandomTick);
        // Poll a shared persisted deadline: returning from standby no longer restarts a fresh 30-minute wait.
        rotationHandler.postDelayed(builtinRandomTick, ROTATION_POLL_INTERVAL_MS);
        rotationHandler.removeCallbacks(customRotationTick);
        rotationHandler.postDelayed(customRotationTick, CUSTOM_WALLPAPER_INTERVAL_MS);
        update();
    }

    void stop() {
        Log.i("TvHome.Wallpaper", "R102 stop; displayed=" + displayed);
        recordDiagnostic(root.getContext(), "播放器停止；目前=" + shortName(displayed));
        active = false;
        rotationHandler.removeCallbacks(builtinRandomTick);
        rotationHandler.removeCallbacks(customRotationTick);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        if (moods.isEmpty()) return files;
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    void close() {
        active = false;
        closed = true;
        rotationHandler.removeCallbacks(builtinRandomTick);
        rotationHandler.removeCallbacks(customRotationTick);
        decoder.shutdownNow();
    }

    void update() {
        update(false);
    }

    /** A dedicated slideshow tick has already waited the full interval; do not gate it a second time. */
    private void update(boolean forceCustomRotation) {
        if (!active || closed) {
            Log.w("TvHome.Wallpaper", "R102 update skipped: active=" + active + ", closed=" + closed);
            recordDiagnostic(root.getContext(), "更新略過：播放器未啟動或已關閉");
            return;
        }
        if (loading) {
            Log.w("TvHome.Wallpaper", "R102 update skipped: image decode still loading; force=" + forceCustomRotation);
            recordDiagnostic(root.getContext(), "更新略過：上一張圖片仍在解碼");
            return;
        }
        List<File> customSelection = customWallpaperEnabled()
                ? CustomWallpaperLibrary.selected(root.getContext()) : Collections.emptyList();
        // If the last selected custom image was deleted, there is no custom item to rotate to.
        // Restore the normal wallpaper fallback immediately instead of leaving the deleted bitmap visible.
        if (customSelection.isEmpty() && displayed != null
                && displayed.startsWith(File.separator) && !new File(displayed).isFile()) {
            preferences.edit().remove("custom_wallpaper_current")
                    .remove("custom_wallpaper_changed").apply();
            showFirst();
            return;
        }
        if (!customSelection.isEmpty()) {
            String mode = CustomWallpaperLibrary.mode(root.getContext());
            Log.i("TvHome.Wallpaper", "R102 custom update; count=" + customSelection.size()
                    + ", mode=" + mode + ", force=" + forceCustomRotation + ", displayed=" + displayed);
            recordDiagnostic(root.getContext(), "執行切換檢查：清單 " + customSelection.size()
                    + " 張，模式=" + modeLabel(mode) + "，強制=" + forceCustomRotation);
            String previousCustom = preferences.getString("custom_wallpaper_current", "");
            // The current-image preference stores an absolute path, while WallpaperRotation.find()
            // compares basenames. Compare absolute paths here, and fall back to the displayed path
            // because the gallery delete action clears the preference before the player is updated.
            String currentCustomPath = previousCustom.isEmpty() ? displayed : previousCustom;
            File currentCustom = null;
            for (File candidate : customSelection) {
                if (candidate.getAbsolutePath().equals(currentCustomPath)) {
                    currentCustom = candidate;
                    break;
                }
            }
            boolean currentWasDeleted = currentCustom == null
                    && currentCustomPath != null && currentCustomPath.startsWith(File.separator);

            // A deleted current image must be replaced immediately, including single-image mode.
            // Otherwise the old bitmap remains on screen until Home is recreated.
            if (!currentWasDeleted && ("single".equals(mode) || customSelection.size() < 2)) {
                recordDiagnostic(root.getContext(), "未切換：單張模式或圖片不足兩張");
                return;
            }
            long changed = preferences.getLong("custom_wallpaper_changed", 0);
            if (!currentWasDeleted && !forceCustomRotation
                    && !WallpaperRotation.due(changed, System.currentTimeMillis(), CUSTOM_WALLPAPER_INTERVAL_MS)) return;
            File nextCustom;
            if (currentWasDeleted) {
                nextCustom = customSelection.get(0);
            } else {
                nextCustom = "sequential".equals(mode)
                        ? WallpaperRotation.next(customSelection, previousCustom)
                        : WallpaperRotation.pick(customSelection, previousCustom, random);
            }
            if (nextCustom == null || nextCustom.getAbsolutePath().equals(displayed)) {
                Log.w("TvHome.Wallpaper", "R102 no next custom image; next=" + nextCustom + ", displayed=" + displayed);
                recordDiagnostic(root.getContext(), "未取得下一張圖片；目前=" + shortName(displayed));
                return;
            }
            File targetCustom = nextCustom;
            Log.i("TvHome.Wallpaper", "R102 decoding next custom image=" + targetCustom.getAbsolutePath());
            recordDiagnostic(root.getContext(), "開始解碼下一張：" + targetCustom.getName());
            loading = true;
            decoder.execute(() -> {
                Bitmap bitmap = decode(targetCustom);
                root.post(() -> {
                    loading = false;
                    if (closed || !active) { if (bitmap != null) bitmap.recycle(); return; }
                    if (bitmap == null) {
                        Log.e("TvHome.Wallpaper", "R102 custom image decode failed: " + targetCustom);
                        recordDiagnostic(root.getContext(), "圖片解碼失敗：" + targetCustom.getName());
                        return;
                    }
                    Log.i("TvHome.Wallpaper", "R102 custom image decoded and displayed: " + targetCustom);
                    recordDiagnostic(root.getContext(), "下一張圖片顯示成功：" + targetCustom.getName());
                    show(bitmap);
                    displayed = targetCustom.getAbsolutePath();
                    preferences.edit().putString("custom_wallpaper_current", displayed)
                            .putLong("custom_wallpaper_changed", System.currentTimeMillis()).apply();
                    showCaption(true);
                });
            });
            return;
        }
        if (files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        // The wallpaper covers the whole root; avoid drawing the fallback colour under it.
        root.setBackground(null);
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
