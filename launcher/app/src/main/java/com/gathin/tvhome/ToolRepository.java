package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent, isolated state for CYTV's third-party Tool Center.
 *
 * Stores only launchable app component identifiers and presentation preferences. It never
 * installs, copies, or modifies third-party APKs. All methods are synchronous and intended
 * for the small preference payload used by the TV launcher.
 */
final class ToolRepository {
    private static final String PREFS = "cytv_tool_center";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ITEMS_BACKUP = "items_backup";
    private static final String KEY_COLUMNS = "columns";
    private static final String KEY_ROWS = "rows";
    private static final String KEY_ICON_SIZE = "icon_size";
    private static final String KEY_SHOW_NAMES = "show_names";
    private static final String KEY_ICON_MODE = "icon_mode";
    private static final String KEY_LAST_FOCUSED_TOOL = "last_focused_tool";
    private static final String KEY_CURRENT_PAGE = "current_page";

    static final int MIN_COLUMNS = 3;
    static final int MAX_COLUMNS = 8;
    static final int MIN_ROWS = 1;
    static final int MAX_ROWS = 5;
    static final int DEFAULT_COLUMNS = 5;
    static final int DEFAULT_ROWS = 2;
    static final int DEFAULT_ICON_SIZE = 100;

    enum IconMode {
        CYTV_SMART, ORIGINAL, CYTV_MINIMAL
    }

    static final class Tool {
        final String packageName;
        final String activityName;
        final String label;
        final int position;
        final String cachedIconPath;
        final String category;

        Tool(String packageName, String activityName, String label, int position,
                String cachedIconPath, String category) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.label = label;
            this.position = position;
            this.cachedIconPath = cachedIconPath;
            this.category = category == null || category.trim().isEmpty() ? "未分類" : category;
        }

        String key() {
            return packageName + "/" + activityName;
        }
    }

    private final SharedPreferences preferences;

    ToolRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized List<Tool> getTools() {
        String raw = readString(KEY_ITEMS, "[]");
        ArrayList<Tool> parsed = parseTools(raw);
        if (parsed != null) return parsed;

        // Recover the last known-good list if the primary preference was truncated or malformed.
        String backup = readString(KEY_ITEMS_BACKUP, "[]");
        ArrayList<Tool> recovered = parseTools(backup);
        if (recovered != null) {
            preferences.edit().putString(KEY_ITEMS, backup).commit();
            return recovered;
        }

        // Keep the launcher usable even if both copies are damaged. Do not overwrite the
        // backup here; the next explicit user edit will establish a fresh valid snapshot.
        return new ArrayList<>();
    }

    private String readString(String key, String fallback) {
        try {
            return preferences.getString(key, fallback);
        } catch (ClassCastException wrongType) {
            return null;
        }
    }

    private ArrayList<Tool> parseTools(String raw) {
        if (raw == null) return null;
        ArrayList<Tool> result = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String pkg = item.optString("package", "");
                String activity = item.optString("activity", "");
                if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(activity)) continue;
                String key = pkg + "/" + activity;
                if (!seen.add(key)) continue;
                result.add(new Tool(pkg, activity, item.optString("label", pkg),
                        item.optInt("position", i), item.optString("icon", ""),
                        item.optString("category", "未分類")));
            }
        } catch (JSONException malformed) {
            return null;
        }
        Collections.sort(result, (a, b) -> Integer.compare(a.position, b.position));
        return result;
    }

    synchronized void refreshInstalledEntries(PackageManager packageManager) {
        ArrayList<Tool> refreshed = new ArrayList<>();
        boolean changed = false;
        for (Tool tool : getTools()) {
            try {
                android.content.pm.ActivityInfo info = packageManager.getActivityInfo(
                        new ComponentName(tool.packageName, tool.activityName), 0);
                if (!info.enabled || !info.exported) {
                    changed = true;
                    continue;
                }
                String label = info.loadLabel(packageManager).toString();
                if (!TextUtils.equals(label, tool.label)) changed = true;
                refreshed.add(new Tool(tool.packageName, tool.activityName, label,
                        refreshed.size(), tool.cachedIconPath, tool.category));
            } catch (PackageManager.NameNotFoundException | RuntimeException missing) {
                // The app was removed or its launcher activity is no longer available.
                changed = true;
            }
        }
        if (changed) saveTools(refreshed);
    }

    synchronized boolean contains(String packageName, String activityName) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(activityName)) return false;
        String key = packageName + "/" + activityName;
        for (Tool tool : getTools()) if (key.equals(tool.key())) return true;
        return false;
    }

    synchronized boolean add(String packageName, String activityName, String label) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(activityName)
                || contains(packageName, activityName)) return false;
        ArrayList<Tool> tools = new ArrayList<>(getTools());
        tools.add(new Tool(packageName, activityName,
                TextUtils.isEmpty(label) ? packageName : label, tools.size(), "", "未分類"));
        return saveTools(tools);
    }

    synchronized boolean remove(String packageName, String activityName) {
        ArrayList<Tool> tools = new ArrayList<>(getTools());
        String key = packageName + "/" + activityName;
        boolean removed = false;
        ArrayList<Tool> kept = new ArrayList<>();
        for (Tool tool : tools) {
            if (key.equals(tool.key())) removed = true;
            else kept.add(tool);
        }
        if (!removed) return false;
        return saveTools(kept);
    }

    synchronized boolean move(String packageName, String activityName, int offset) {
        ArrayList<Tool> tools = new ArrayList<>(getTools());
        int from = -1;
        for (int i = 0; i < tools.size(); i++) {
            if ((packageName + "/" + activityName).equals(tools.get(i).key())) {
                from = i;
                break;
            }
        }
        int to = from + offset;
        if (from < 0 || to < 0 || to >= tools.size()) return false;
        Collections.swap(tools, from, to);
        return saveTools(tools);
    }

    synchronized int getColumns() {
        return clamp(preferences.getInt(KEY_COLUMNS, DEFAULT_COLUMNS), MIN_COLUMNS, MAX_COLUMNS);
    }

    synchronized boolean setColumns(int columns) {
        if (columns < MIN_COLUMNS || columns > MAX_COLUMNS) return false;
        return preferences.edit().putInt(KEY_COLUMNS, columns).commit();
    }

    synchronized int getRows() {
        return clamp(preferences.getInt(KEY_ROWS, DEFAULT_ROWS), MIN_ROWS, MAX_ROWS);
    }

    synchronized boolean setRows(int rows) {
        if (rows < MIN_ROWS || rows > MAX_ROWS) return false;
        return preferences.edit().putInt(KEY_ROWS, rows).commit();
    }

    synchronized int getIconSize() {
        return clamp(preferences.getInt(KEY_ICON_SIZE, DEFAULT_ICON_SIZE), 60, 160);
    }

    synchronized boolean setIconSize(int percent) {
        if (percent < 60 || percent > 160) return false;
        return preferences.edit().putInt(KEY_ICON_SIZE, percent).commit();
    }

    synchronized boolean isShowNames() {
        return preferences.getBoolean(KEY_SHOW_NAMES, true);
    }

    synchronized boolean setShowNames(boolean show) {
        return preferences.edit().putBoolean(KEY_SHOW_NAMES, show).commit();
    }

    synchronized IconMode getIconMode() {
        String value = preferences.getString(KEY_ICON_MODE, IconMode.CYTV_SMART.name());
        try {
            return IconMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return IconMode.CYTV_SMART;
        }
    }

    synchronized boolean setIconMode(IconMode mode) {
        if (mode == null) return false;
        return preferences.edit().putString(KEY_ICON_MODE, mode.name()).commit();
    }

    synchronized String getLastFocusedTool() {
        return preferences.getString(KEY_LAST_FOCUSED_TOOL, "");
    }

    synchronized void setLastFocusedTool(String key) {
        preferences.edit().putString(KEY_LAST_FOCUSED_TOOL, key == null ? "" : key).apply();
    }

    synchronized int getCurrentPage() {
        return Math.max(0, preferences.getInt(KEY_CURRENT_PAGE, 0));
    }

    synchronized void setCurrentPage(int page) {
        preferences.edit().putInt(KEY_CURRENT_PAGE, Math.max(0, page)).apply();
    }

    synchronized boolean setCategory(String packageName, String activityName, String category) {
        if (TextUtils.isEmpty(category)) return false;
        ArrayList<Tool> tools = new ArrayList<>(getTools());
        boolean found = false;
        for (int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);
            if ((packageName + "/" + activityName).equals(tool.key())) {
                tools.set(i, new Tool(tool.packageName, tool.activityName, tool.label,
                        tool.position, tool.cachedIconPath, category.trim()));
                found = true;
                break;
            }
        }
        return found && saveTools(tools);
    }

    synchronized List<String> getCategories() {
        ArrayList<String> categories = new ArrayList<>();
        categories.add("未分類");
        for (Tool tool : getTools()) {
            if (!categories.contains(tool.category)) categories.add(tool.category);
        }
        Collections.sort(categories);
        if (categories.remove("未分類")) categories.add(0, "未分類");
        return categories;
    }

    private boolean saveTools(List<Tool> tools) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);
            JSONObject item = new JSONObject();
            try {
                item.put("package", tool.packageName);
                item.put("activity", tool.activityName);
                item.put("label", tool.label);
                item.put("position", i);
                item.put("icon", tool.cachedIconPath);
                item.put("category", tool.category);
            } catch (JSONException ignored) {
                continue;
            }
            array.put(item);
        }
        String next = array.toString();
        String current = readString(KEY_ITEMS, null);
        SharedPreferences.Editor editor = preferences.edit();
        if (parseTools(current) != null) {
            editor.putString(KEY_ITEMS_BACKUP, current);
        }
        return editor.putString(KEY_ITEMS, next).commit();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
