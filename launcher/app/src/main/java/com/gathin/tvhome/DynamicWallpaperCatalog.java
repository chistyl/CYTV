package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads CYTV's curated nine-theme aerial video catalog.
 *
 * This class only parses metadata. Playback is deliberately kept out of WallpaperPlayer
 * so the existing still-image wallpaper lifecycle remains independent.
 */
final class DynamicWallpaperCatalog {
    private static final String TAG = "CYTV.DynamicWall";
    private static final String ASSET_NAME = "dynamic_wallpaper_themes.json";

    static final class Video {
        final String id;
        final String label;
        final String sourceCategory;
        final String streamUrl;
        final String hevcUrl;
        final String uhdUrl;
        final String dolbyVisionUrl;
        final List<String> themeIds;

        Video(JSONObject json) {
            id = json.optString("id", "");
            label = json.optString("label", "");
            sourceCategory = json.optString("sourceCategory", "");
            streamUrl = json.optString("streamUrl", "");
            hevcUrl = nullable(json, "hevcUrl");
            uhdUrl = nullable(json, "uhdUrl");
            dolbyVisionUrl = nullable(json, "dolbyVisionUrl");
            themeIds = readStrings(json.optJSONArray("themeIds"));
        }

        private static String nullable(JSONObject json, String key) {
            String value = json.optString(key, "");
            return value.isEmpty() || "null".equals(value) ? null : value;
        }
    }

    static final class Theme {
        final String id;
        final String name;
        final String description;
        final List<String> videoIds;

        Theme(JSONObject json) {
            id = json.optString("id", "");
            name = json.optString("name", "");
            description = json.optString("description", "");
            videoIds = readStrings(json.optJSONArray("videoIds"));
        }

        boolean isPlayable() {
            return !videoIds.isEmpty();
        }
    }

    private final Map<String, Video> videosById;
    private final List<Theme> themes;

    private DynamicWallpaperCatalog(Map<String, Video> videosById, List<Theme> themes) {
        this.videosById = Collections.unmodifiableMap(videosById);
        this.themes = Collections.unmodifiableList(themes);
    }

    static DynamicWallpaperCatalog load(Context context) {
        try (InputStream input = context.getAssets().open(ASSET_NAME);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            JSONObject root = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));

            Map<String, Video> videos = new LinkedHashMap<>();
            JSONArray videoArray = root.optJSONArray("videos");
            if (videoArray != null) {
                for (int i = 0; i < videoArray.length(); i++) {
                    JSONObject item = videoArray.optJSONObject(i);
                    if (item == null) continue;
                    Video video = new Video(item);
                    if (!video.id.isEmpty() && !video.streamUrl.isEmpty()) videos.put(video.id, video);
                }
            }

            List<Theme> themes = new ArrayList<>();
            JSONArray themeArray = root.optJSONArray("themes");
            if (themeArray != null) {
                for (int i = 0; i < themeArray.length(); i++) {
                    JSONObject item = themeArray.optJSONObject(i);
                    if (item == null) continue;
                    Theme theme = new Theme(item);
                    // Do not expose a theme whose manifest references no known playable videos.
                    List<String> validIds = new ArrayList<>();
                    for (String id : theme.videoIds) if (videos.containsKey(id)) validIds.add(id);
                    if (!theme.id.isEmpty() && !theme.name.isEmpty() && !validIds.isEmpty()) {
                        themes.add(new Theme(new JSONObject()
                                .put("id", theme.id)
                                .put("name", theme.name)
                                .put("description", theme.description)
                                .put("videoIds", new JSONArray(validIds))));
                    }
                }
            }
            if (themes.size() != 9) {
                Log.w(TAG, "Expected 9 non-empty themes, loaded " + themes.size());
            }
            return new DynamicWallpaperCatalog(videos, themes);
        } catch (Exception error) {
            Log.e(TAG, "Unable to load dynamic wallpaper catalog", error);
            return new DynamicWallpaperCatalog(new LinkedHashMap<>(), new ArrayList<>());
        }
    }

    List<Theme> themes() {
        return themes;
    }

    Video video(String id) {
        return videosById.get(id);
    }

    List<Video> videosFor(Theme theme) {
        if (theme == null) return Collections.emptyList();
        List<Video> result = new ArrayList<>();
        for (String id : theme.videoIds) {
            Video video = videosById.get(id);
            if (video != null) result.add(video);
        }
        return result;
    }

    private static List<String> readStrings(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }
}
