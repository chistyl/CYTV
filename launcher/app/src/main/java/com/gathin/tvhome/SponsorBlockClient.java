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

    static final class LookupResult {
        final int httpStatus;
        final int responseBytes;
        final int candidateCount;
        final int matchingVideoCount;
        final List<Segment> segments;

        LookupResult(int httpStatus, int responseBytes, int candidateCount,
                     int matchingVideoCount, List<Segment> segments) {
            this.httpStatus = httpStatus;
            this.responseBytes = responseBytes;
            this.candidateCount = candidateCount;
            this.matchingVideoCount = matchingVideoCount;
            this.segments = segments;
        }
    }

    static List<Segment> lookupSponsorSegments(String videoId) throws Exception {
        return lookupSponsorSegmentsDetailed(videoId, null).segments;
    }

    static List<Segment> lookupSponsorSegments(String videoId,
                                                YouTubeLoungeClient.MonitorHandle monitor) throws Exception {
        return lookupSponsorSegmentsDetailed(videoId, monitor).segments;
    }

    static LookupResult lookupSponsorSegmentsDetailed(String videoId) throws Exception {
        return lookupSponsorSegmentsDetailed(videoId, null);
    }

    static LookupResult lookupSponsorSegmentsDetailed(String videoId,
                                                        YouTubeLoungeClient.MonitorHandle monitor) throws Exception {
        if (monitor != null && monitor.isStopped()) {
            throw new java.util.concurrent.CancellationException("SponsorBlock 查詢已取消。");
        }
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
            if (monitor != null) monitor.attach(connection);

            int status = connection.getResponseCode();
            if (monitor != null && monitor.isStopped()) {
                throw new java.util.concurrent.CancellationException("SponsorBlock 查詢已取消。");
            }
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            byte[] response = readBytes(stream);
            if (status == 404) {
                return new LookupResult(status, response.length, 0, 0, new ArrayList<>());
            }
            if (status != 200) {
                throw new Exception("SponsorBlock 查詢失敗（HTTP " + status + "）；回應 "
                        + response.length + " bytes。");
            }

            JSONArray candidates;
            try {
                candidates = new JSONArray(new String(response, StandardCharsets.UTF_8));
            } catch (Exception parseError) {
                throw new Exception("SponsorBlock 回應不是有效 JSON（HTTP 200，"
                        + response.length + " bytes）。", parseError);
            }
            List<Segment> matches = new ArrayList<>();
            int matchingVideos = 0;
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject video = candidates.optJSONObject(i);
                if (video == null || !id.equals(video.optString("videoID", ""))) continue;
                matchingVideos++;
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
            return new LookupResult(status, response.length, candidates.length(),
                    matchingVideos, matches);
        } finally {
            if (monitor != null && connection != null) monitor.detach(connection);
            if (connection != null) connection.disconnect();
        }
    }

    static Segment findSegmentAtPosition(List<Segment> segments, double positionSeconds) {
        if (!Double.isFinite(positionSeconds) || positionSeconds < 0 || segments == null) {
            return null;
        }
        for (Segment segment : segments) {
            if (positionSeconds >= segment.startSeconds
                    && positionSeconds < segment.endSeconds) return segment;
            if (segment.startSeconds > positionSeconds) break;
        }
        return null;
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }

    private static byte[] readBytes(InputStream stream) throws Exception {
        if (stream == null) return new byte[0];
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private SponsorBlockClient() {}
}
