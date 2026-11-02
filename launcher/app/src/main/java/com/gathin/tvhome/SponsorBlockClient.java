package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * SponsorBlock lookup using the public K-anonymity endpoint.
 * Only returns segments for the exact requested YouTube video ID.
 * This class does not perform playback seeking or skipping.
 *
 * SponsorBlock API attribution: https://github.com/ajayyy/SponsorBlock/wiki/API-Docs
 */
final class SponsorBlockClient {
    private static final String API = "https://sponsor.ajay.app/api/skipSegments/";

    static final class Segment {
        final double startSeconds;
        final double endSeconds;
        final String category;
        final String uuid;

        Segment(double startSeconds, double endSeconds, String category, String uuid) {
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.category = category;
            this.uuid = uuid;
        }
    }

    static List<Segment> lookupSponsorSegments(String videoId) throws Exception {
        String id = videoId == null ? "" : videoId.trim();
        if (!id.matches("[A-Za-z0-9_-]{11}")) {
            throw new IllegalArgumentException("YouTube 影片 ID 格式不正確。");
        }

        String hash = sha256Hex(id);
        String prefix = hash.substring(0, 4);
        String categories = URLEncoder.encode("[\\"sponsor\\"]", "UTF-8");
        URL url = new URL(API + prefix + "?categories=" + categories);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "CYTV-YouTube-Assistant/1.0");

            int status = connection.getResponseCode();
            if (status == 404) return new ArrayList<>();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            if (status != 200) {
                throw new Exception("SponsorBlock 查詢失敗（HTTP " + status + "）。");
            }

            JSONArray candidates = new JSONArray(response);
            List<Segment> matches = new ArrayList<>();
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject video = candidates.optJSONObject(i);
                if (video == null || !id.equals(video.optString("videoID", ""))) continue;
                JSONArray segments = video.optJSONArray("segments");
                if (segments == null) continue;
                for (int j = 0; j < segments.length(); j++) {
                    JSONObject item = segments.optJSONObject(j);
                    if (item == null || !"sponsor".equals(item.optString("category", ""))) continue;
                    JSONArray range = item.optJSONArray("segment");
                    if (range == null || range.length() < 2) continue;
                    double start = range.optDouble(0, -1);
                    double end = range.optDouble(1, -1);
                    if (!Double.isFinite(start) || !Double.isFinite(end)
                            || start < 0 || end <= start) continue;
                    matches.add(new Segment(start, end, "sponsor", item.optString("UUID", "")));
                }
            }
            java.util.Collections.sort(matches,
                    (left, right) -> Double.compare(left.startSeconds, right.startSeconds));
            return matches;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }

    private SponsorBlockClient() {}
}
