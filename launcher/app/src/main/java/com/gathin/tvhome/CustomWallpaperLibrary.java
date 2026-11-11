package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Persistent, app-private gallery for user-imported wallpaper images. Importing never selects a wallpaper. */
final class CustomWallpaperLibrary {
    private static final String PREFS = "wallpaper_rotation";
    private static final String KEY_ITEMS = "custom_wallpaper_library";
    private static final String DIRECTORY = "wallpaper-library";
    private CustomWallpaperLibrary() {}

    static synchronized List<File> list(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<File> result = new ArrayList<>();
        String raw = prefs.getString(KEY_ITEMS, "[]");
        try {
            JSONArray items = new JSONArray(raw);
            for (int i = 0; i < items.length(); i++) {
                File file = new File(items.getString(i));
                if (file.isFile() && file.length() > 0) result.add(file);
            }
        } catch (Exception ignored) { }
        return result;
    }

    static synchronized File importUri(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            return input == null ? null : importStream(context, input);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    static synchronized File importFile(Context context, File source) {
        if (source == null || !source.isFile()) return null;
        try (InputStream input = new FileInputStream(source)) {
            return importStream(context, input);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private static File importStream(Context context, InputStream input) throws Exception {
        File dir = new File(context.getFilesDir(), DIRECTORY);
        if (!dir.exists() && !dir.mkdirs()) return null;
        File target = new File(dir, UUID.randomUUID().toString().replace("-", "") + ".img");
        File temp = new File(dir, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
        }
        if (temp.length() <= 0 || !temp.renameTo(target)) {
            temp.delete();
            return null;
        }
        List<File> items = list(context);
        items.add(target);
        save(context, items);
        return target;
    }

    static synchronized boolean delete(Context context, File file) {
        if (file == null) return false;
        List<File> items = list(context);
        boolean found = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).equals(file)) {
                items.remove(i);
                found = true;
            }
        }
        if (!found) return false;
        save(context, items);
        return !file.exists() || file.delete();
    }

    static synchronized List<File> selected(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<File> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("custom_wallpaper_selected", "[]"));
            for (int i = 0; i < array.length(); i++) {
                File file = new File(array.getString(i));
                if (file.isFile() && file.length() > 0) result.add(file);
            }
        } catch (Exception ignored) { }
        return result;
    }

    static synchronized void setSelected(Context context, List<File> files, String mode) {
        JSONArray array = new JSONArray();
        for (File file : files) if (file != null && file.isFile()) array.put(file.getAbsolutePath());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("custom_wallpaper_selected", array.toString())
                .putString("custom_wallpaper_mode", mode)
                .apply();
    }

    static String mode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("custom_wallpaper_mode", "single");
    }

    private static void save(Context context, List<File> files) {
        JSONArray array = new JSONArray();
        for (File file : files) array.put(file.getAbsolutePath());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ITEMS, array.toString()).apply();
    }
}
