package com.gathin.tvhome;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Persistent, deduplicated storage for user-imported wallpaper images. */
final class CustomWallpaperLibrary {
    private static final String DIRECTORY = "custom_wallpapers";
    private static final long MAX_IMAGE_BYTES = 20L * 1024L * 1024L;

    private CustomWallpaperLibrary() { }

    static File directory(Context context) {
        File dir = new File(context.getFilesDir(), DIRECTORY);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static boolean importImage(Context context, Uri uri) {
        if (uri == null) return false;
        File dir = directory(context);
        File temporary = new File(dir, "incoming-" + System.nanoTime() + ".tmp");
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(temporary)) {
            if (input == null) return false;
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_IMAGE_BYTES) {
                    temporary.delete();
                    return false;
                }
                output.write(buffer, 0, count);
            }
            output.flush();
            if (total <= 0) return false;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;

            String hash = sha256(temporary);
            String extension = extension(context, uri);
            File[] existing = dir.listFiles(file -> file.isFile() && file.getName().startsWith(hash + "."));
            if (existing != null && existing.length > 0) {
                temporary.delete();
                return true;
            }
            File target = new File(dir, hash + extension);
            if (!temporary.renameTo(target)) {
                try (FileInputStream in = new FileInputStream(temporary);
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] copy = new byte[16 * 1024];
                    while ((count = in.read(copy)) != -1) out.write(copy, 0, count);
                    out.flush();
                }
                temporary.delete();
            }
            return target.isFile() && target.length() == total;
        } catch (Exception | OutOfMemoryError failure) {
            temporary.delete();
            return false;
        }
    }

    static List<File> list(Context context) {
        File[] files = directory(context).listFiles(file -> file.isFile()
                && (file.getName().endsWith(".jpg") || file.getName().endsWith(".jpeg")
                || file.getName().endsWith(".png") || file.getName().endsWith(".webp")));
        List<File> result = new ArrayList<>();
        if (files != null) Collections.addAll(result, files);
        result.sort(Comparator.comparing(File::getName));
        return result;
    }

    static boolean delete(File file, Context context) {
        if (file == null) return false;
        try {
            String root = directory(context).getCanonicalPath() + File.separator;
            return file.getCanonicalPath().startsWith(root) && file.isFile() && file.delete();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String extension(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        if (mime != null) {
            mime = mime.toLowerCase(Locale.ROOT);
            if (mime.contains("png")) return ".png";
            if (mime.contains("webp")) return ".webp";
        }
        return ".jpg";
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value));
        return hex.toString();
    }
}
