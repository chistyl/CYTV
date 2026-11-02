package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Initial YouTube Lounge bind handshake with length-prefixed event-frame parsing.
 * This does not yet subscribe to the backchannel or send remote-control commands.
 */
final class YouTubeLoungeClient {
    private static final String BIND_URL =
            "https://www.youtube.com/api/lounge/bc/bind?RID=1&VER=8&CVER=1&TYPE=xmlhttp&auth_failure_option=send_error";

    static final class Session {
        final String sid;
        final String gsessionId;
        final String screenId;
        final String deviceId;
        final long lastEventId;
        final String initialVideoId;
        final double initialCurrentTime;
        final double initialDuration;
        final String initialPlayerState;
        final String initialEventSummary;

        Session(String sid, String gsessionId, String screenId, String deviceId, long lastEventId,
                String initialVideoId, double initialCurrentTime, double initialDuration,
                String initialPlayerState, String initialEventSummary) {
            this.sid = sid;
            this.gsessionId = gsessionId;
            this.screenId = screenId;
            this.deviceId = deviceId;
            this.lastEventId = lastEventId;
            this.initialVideoId = initialVideoId;
            this.initialCurrentTime = initialCurrentTime;
            this.initialDuration = initialDuration;
            this.initialPlayerState = initialPlayerState;
            this.initialEventSummary = initialEventSummary;
        }
    }

    static final class SubscriptionResult {
        final int eventCount;
        final long lastEventId;
        final String eventSummary;
        final boolean timedOut;
        final boolean httpAccepted;
        final String videoId;
        final double currentTime;
        final double duration;
        final String playerState;

        SubscriptionResult(int eventCount, long lastEventId, String eventSummary,
                           boolean timedOut, boolean httpAccepted, String videoId,
                           double currentTime, double duration, String playerState) {
            this.eventCount = eventCount;
            this.lastEventId = lastEventId;
            this.eventSummary = eventSummary;
            this.timedOut = timedOut;
            this.httpAccepted = httpAccepted;
            this.videoId = videoId;
            this.currentTime = currentTime;
            this.duration = duration;
            this.playerState = playerState;
        }
    }

    /**
     * Cancellation token for a bounded Lounge subscription. stop() disconnects
     * the active HTTP request so a monitor can stop without waiting for read timeout.
     */
    static final class MonitorHandle {
        private final java.util.concurrent.atomic.AtomicBoolean stopped =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        private volatile HttpURLConnection activeConnection;

        boolean isStopped() { return stopped.get(); }

        void stop() {
            stopped.set(true);
            HttpURLConnection connection = activeConnection;
            if (connection != null) connection.disconnect();
        }

        void attach(HttpURLConnection connection) throws Exception {
            if (stopped.get()) {
                connection.disconnect();
                throw new java.util.concurrent.CancellationException("Lounge 監測已停止。");
            }
            activeConnection = connection;
            if (stopped.get()) {
                activeConnection = null;
                connection.disconnect();
                throw new java.util.concurrent.CancellationException("Lounge 監測已停止。");
            }
        }

        void detach(HttpURLConnection connection) {
            if (activeConnection == connection) activeConnection = null;
        }
    }

    private static final class ParsedEvents {
        String sid = "";
        String gsessionId = "";
        long lastEventId = -1;
        int eventCount = 0;
        final java.util.LinkedHashSet<String> eventNames = new java.util.LinkedHashSet<>();
        final java.util.LinkedHashSet<String> eventDetails = new java.util.LinkedHashSet<>();
        String videoId = "";
        double currentTime = -1;
        double duration = -1;
        String playerState = "";
        boolean timedOut = false;
    }

    static Session connect(Context context) throws Exception {
        return connect(context, null);
    }

    static Session connect(Context context, MonitorHandle monitor) throws Exception {
        if (monitor != null && monitor.isStopped()) {
            throw new java.util.concurrent.CancellationException("Lounge 監測已停止。");
        }
        // Lounge tokens expire; refresh from the paired screen ID before opening a new session.
        YouTubePairingClient.PairingResult credentials =
                YouTubePairingClient.refreshLoungeToken(context);
        if (monitor != null && monitor.isStopped()) {
            throw new java.util.concurrent.CancellationException("Lounge 監測已停止。");
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BIND_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Origin", "https://www.youtube.com");
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            if (monitor != null) monitor.attach(connection);

            String deviceContext = "user_agent=dunno&window_width_points=&window_height_points="
                    + "&os_name=android&ms=";
            String body = form("app", "web")
                    + "&" + form("mdx-version", "3")
                    + "&" + form("name", "CYTV")
                    + "&" + form("id", credentials.deviceId)
                    + "&" + form("device", "REMOTE_CONTROL")
                    + "&" + form("capabilities", "que,dsdtr,atp,vsp")
                    + "&" + form("magnaKey", "cloudPairedDevice")
                    + "&" + form("ui", "false")
                    + "&" + form("deviceContext", deviceContext)
                    + "&" + form("theme", "cl")
                    + "&" + form("loungeIdToken", credentials.loungeToken);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
                out.flush();
            }

            int status = connection.getResponseCode();
            if (monitor != null && monitor.isStopped()) {
                throw new java.util.concurrent.CancellationException("Lounge 監測已停止。");
            }
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            if (status == 401) {
                throw new Exception("YouTube Lounge 憑證已失效（HTTP 401），請重新配對電視。");
            }
            if (status != 200) {
                throw new Exception("YouTube Lounge bind 連線失敗（HTTP " + status + "）。");
            }

            ParsedEvents events = parseEventStream(new java.io.ByteArrayInputStream(
                    response.getBytes(StandardCharsets.UTF_8)));
            if (events.sid.isEmpty() || events.gsessionId.isEmpty() || events.lastEventId < 0) {
                throw new Exception("YouTube 已回應，但封包中缺少 SID、gsessionid 或事件編號；"
                        + "尚未建立可用的控制工作階段。");
            }
            if (monitor != null && monitor.isStopped()) {
                throw new java.util.concurrent.CancellationException("Lounge 監測已停止。");
            }
            String initialSummary = events.eventNames.isEmpty()
                    ? "初始 bind 未附帶播放事件。"
                    : android.text.TextUtils.join("、", events.eventNames);
            if (!events.eventDetails.isEmpty()) {
                initialSummary += "；初始事件資料：" +
                        android.text.TextUtils.join(" | ", events.eventDetails);
            }
            return new Session(events.sid, events.gsessionId, credentials.screenId,
                    credentials.deviceId, events.lastEventId, events.videoId,
                    events.currentTime, events.duration, events.playerState, initialSummary);
        } finally {
            if (monitor != null && connection != null) monitor.detach(connection);
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Opens one bounded backchannel subscription request for diagnostics.
     * It deliberately does not keep a permanent listener or issue control commands.
     */
    static SubscriptionResult subscribeOnce(Context context, Session session) throws Exception {
        return subscribeOnce(context, session, null);
    }

    /**
     * One bounded subscription that can be cancelled by a monitor handle.
     * The existing one-shot diagnostic keeps its original behavior.
     */
    static SubscriptionResult subscribeOnce(Context context, Session session,
                                            MonitorHandle monitor) throws Exception {
        if (monitor != null && monitor.isStopped()) {
            throw new java.util.concurrent.CancellationException("Lounge 監測已停止。");
        }
        YouTubePairingClient.PairingResult credentials = YouTubePairingClient.load(context);
        String query = "name=" + enc("CYTV")
                + "&loungeIdToken=" + enc(credentials.loungeToken)
                + "&SID=" + enc(session.sid)
                + "&AID=" + enc(String.valueOf(session.lastEventId))
                + "&gsessionid=" + enc(session.gsessionId)
                + "&device=REMOTE_CONTROL&app=youtube-desktop&VER=8&v=2&RID=rpc&CI=0&TYPE=xmlhttp";
        HttpURLConnection connection = null;
        boolean httpAccepted = false;
        try {
            URL url = new URL("https://www.youtube.com/api/lounge/bc/bind?" + query);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Origin", "https://www.youtube.com");
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            if (monitor != null) monitor.attach(connection);

            final int status;
            try {
                status = connection.getResponseCode();
            } catch (SocketTimeoutException timeout) {
                return new SubscriptionResult(0, session.lastEventId,
                        "等待 YouTube Lounge HTTP 回應逾時；尚未確認伺服器接受訂閱。",
                        true, false, "", -1, -1, "");
            }
            if (status == 401) throw new Exception("YouTube Lounge 訂閱憑證失效（HTTP 401），請重新配對。");
            if (status == 400 || status == 410) {
                throw new Exception("YouTube Lounge 工作階段已失效（HTTP " + status + "），請重新建立控制連線。");
            }
            if (status != 200) throw new Exception("YouTube Lounge 事件訂閱失敗（HTTP " + status + "）。");
            httpAccepted = true;

            ParsedEvents parsed = parseEventStream(connection.getInputStream());
            long aid = parsed.lastEventId >= 0 ? parsed.lastEventId : session.lastEventId;
            String summary = parsed.eventNames.isEmpty()
                    ? "訂閱回應中沒有可辨識的事件。"
                    : android.text.TextUtils.join("、", parsed.eventNames);
            if (!parsed.eventDetails.isEmpty()) {
                summary += "；事件資料：" + android.text.TextUtils.join(" | ", parsed.eventDetails);
            }
            String videoId = parsed.videoId.isEmpty() ? session.initialVideoId : parsed.videoId;
            double currentTime = parsed.currentTime >= 0
                    ? parsed.currentTime : session.initialCurrentTime;
            double duration = parsed.duration > 0 ? parsed.duration : session.initialDuration;
            String playerState = parsed.playerState.isEmpty()
                    ? session.initialPlayerState : parsed.playerState;
            summary += "；" + session.initialEventSummary;
            if (videoId.isEmpty()) {
                summary += "；尚未取得目前播放影片 ID。";
            } else if (parsed.videoId.isEmpty()) {
                summary += "；影片 ID 取自初始 bind 回應。";
            }
            return new SubscriptionResult(parsed.eventCount, aid, summary,
                    parsed.timedOut, true, videoId, currentTime, duration, playerState);
        } catch (SocketTimeoutException timeout) {
            return new SubscriptionResult(0, session.lastEventId,
                    "連線等待逾時；尚未確認伺服器接受訂閱。",
                    true, httpAccepted, "", -1, -1, "");
        } finally {
            if (monitor != null && connection != null) monitor.detach(connection);
            if (connection != null) connection.disconnect();
        }
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    /**
     * YouTube Lounge frames are: decimal character count + newline + JSON event array.
     * Each event is [eventId, [eventName, ...arguments]].
     */
    /**
     * Reads length-prefixed event frames incrementally. A read timeout preserves
     * any complete events already received instead of discarding the partial session.
     */
    private static ParsedEvents parseEventStream(InputStream stream) throws Exception {
        ParsedEvents parsed = new ParsedEvents();
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))) {
            while (true) {
                String lengthLine;
                try {
                    lengthLine = reader.readLine();
                } catch (SocketTimeoutException timeout) {
                    parsed.timedOut = true;
                    break;
                }
                if (lengthLine == null) break;
                if (lengthLine.trim().isEmpty()) continue;
                final int frameLength;
                try {
                    frameLength = Integer.parseInt(lengthLine.trim());
                } catch (NumberFormatException e) {
                    throw new Exception("YouTube Lounge 回應格式錯誤：封包長度不是數字。", e);
                }
                if (frameLength < 0 || frameLength > 2_000_000) {
                    throw new Exception("YouTube Lounge 回應格式錯誤：封包長度超出範圍。");
                }
                StringBuilder frame = new StringBuilder(frameLength);
                int remaining = frameLength;
                while (remaining > 0) {
                    final String line;
                    try {
                        line = reader.readLine();
                    } catch (SocketTimeoutException timeout) {
                        parsed.timedOut = true;
                        return parsed;
                    }
                    if (line == null) {
                        throw new Exception("YouTube Lounge 回應不完整：事件封包提前結束。");
                    }
                    frame.append(line);
                    remaining -= line.length() + 1;
                    if (remaining < 0) {
                        throw new Exception("YouTube Lounge 回應格式錯誤：封包長度不符。");
                    }
                }
                JSONArray events;
                try {
                    events = new JSONArray(frame.toString());
                } catch (JSONException e) {
                    throw new Exception("YouTube Lounge 事件封包不是有效 JSON。", e);
                }
                for (int i = 0; i < events.length(); i++) {
                    JSONArray envelope = events.optJSONArray(i);
                    if (envelope == null || envelope.length() < 2) continue;
                    long eventId = envelope.optLong(0, -1);
                    JSONArray payload = envelope.optJSONArray(1);
                    if (payload == null || payload.length() == 0) continue;
                    String eventName = payload.optString(0, "");
                    // Keep a bounded sample of every event, including unknown event types.
                    // Lounge playback payloads can be nested differently across TV clients.
                    String detail = payload.toString();
                    if (detail.length() > 1400) detail = detail.substring(0, 1400) + "…";
                    if (!"c".equals(eventName) && !"S".equals(eventName)) {
                        parsed.eventDetails.add("AID=" + eventId + " " + eventName + " " + detail);
                    }
                    if ("c".equals(eventName) && payload.length() > 1) {
                        parsed.sid = payload.optString(1, "");
                    } else if ("S".equals(eventName) && payload.length() > 1) {
                        parsed.gsessionId = payload.optString(1, "");
                    } else {
                        if (!eventName.isEmpty()) parsed.eventNames.add(eventName);
                        if (payload.length() > 1) {
                            Object eventData = payload.opt(1);
                            JSONObject stateData = eventData instanceof JSONObject
                                    ? (JSONObject) eventData : null;
                            if (stateData != null) {
                                String id = findString(stateData, "videoId");
                                if (!id.matches("[A-Za-z0-9_-]{11}")) {
                                    id = findString(stateData, "firstVideoId");
                                }
                                if (id.matches("[A-Za-z0-9_-]{11}")) parsed.videoId = id;
                                String state = findString(stateData, "state");
                                if (!state.isEmpty()) parsed.playerState = state;
                            }
                            // Search nested objects, arrays and JSON-encoded strings. Some
                            // Lounge clients wrap playback data instead of exposing it at root.
                            double position = findNumber(eventData, "currentTime");
                            double length = findNumber(eventData, "duration");
                            if (position >= 0) parsed.currentTime = position;
                            if (length > 0) parsed.duration = length;
                        }
                    }
                    parsed.eventCount++;
                    if (eventId >= 0) parsed.lastEventId = Math.max(parsed.lastEventId, eventId);
                }
            }
        }
        return parsed;
    }

    private static String findString(Object value, String key) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String found = object.optString(key, "");
            if (!found.isEmpty() && !"null".equals(found)) return found;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String childKey = keys.next();
                String nested = findString(object.opt(childKey), key);
                if (!nested.isEmpty()) return nested;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String nested = findString(array.opt(i), key);
                if (!nested.isEmpty()) return nested;
            }
        } else if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.startsWith("{") || raw.startsWith("[")) {
                try {
                    Object nested = raw.startsWith("{") ? new JSONObject(raw) : new JSONArray(raw);
                    return findString(nested, key);
                } catch (JSONException ignored) { }
            }
        }
        return "";
    }

    private static double findNumber(Object value, String key) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has(key)) {
                Object raw = object.opt(key);
                if (raw instanceof Number) return ((Number) raw).doubleValue();
                if (raw instanceof String) {
                    try { return Double.parseDouble((String) raw); }
                    catch (NumberFormatException ignored) { }
                }
            }
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                double nested = findNumber(object.opt(keys.next()), key);
                if (nested >= 0) return nested;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                double nested = findNumber(array.opt(i), key);
                if (nested >= 0) return nested;
            }
        } else if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.startsWith("{") || raw.startsWith("[")) {
                try {
                    Object nested = raw.startsWith("{") ? new JSONObject(raw) : new JSONArray(raw);
                    return findNumber(nested, key);
                } catch (JSONException ignored) { }
            }
        }
        return -1;
    }

    private static String form(String key, String value) throws Exception {
        return URLEncoder.encode(key, "UTF-8") + "="
                + URLEncoder.encode(value == null ? "" : value, "UTF-8");
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

    private YouTubeLoungeClient() {}
}
