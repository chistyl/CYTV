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
            "https://www.youtube.com/api/lounge/bc/bind?RID=1&VER=8&CVER=1&auth_failure_option=send_error";

    static final class Session {
        final String sid;
        final String gsessionId;
        final String screenId;
        final String deviceId;
        final long lastEventId;

        Session(String sid, String gsessionId, String screenId, String deviceId, long lastEventId) {
            this.sid = sid;
            this.gsessionId = gsessionId;
            this.screenId = screenId;
            this.deviceId = deviceId;
            this.lastEventId = lastEventId;
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
            return new Session(events.sid, events.gsessionId, credentials.screenId,
                    credentials.deviceId, events.lastEventId);
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
            if (parsed.videoId.isEmpty()) {
                summary += "；尚未取得目前播放影片 ID。";
            }
            return new SubscriptionResult(parsed.eventCount, aid, summary,
                    parsed.timedOut, true, parsed.videoId, parsed.currentTime, parsed.duration, parsed.playerState);
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
                    // Keep a bounded, token-free sample of the actual event payload for protocol diagnostics.
                    String detail = payload.toString();
                    if (detail.length() > 420) detail = detail.substring(0, 420) + "…";
                    if (!"c".equals(eventName) && !"S".equals(eventName)) {
                        parsed.eventDetails.add(eventName + " " + detail);
                    }
                    if ("c".equals(eventName) && payload.length() > 1) {
                        parsed.sid = payload.optString(1, "");
                    } else if ("S".equals(eventName) && payload.length() > 1) {
                        parsed.gsessionId = payload.optString(1, "");
                    } else {
                        if (!eventName.isEmpty()) parsed.eventNames.add(eventName);
                        if (payload.length() > 1) {
                            JSONObject stateData = payload.optJSONObject(1);
                            if (stateData != null && "nowPlaying".equals(eventName)) {
                                String id = stateData.optString("videoId", "");
                                if (id.matches("[A-Za-z0-9_-]{11}")) {
                                    parsed.videoId = id;
                                }
                                parsed.currentTime = stateData.optDouble("currentTime", -1);
                                parsed.duration = stateData.optDouble("duration", -1);
                            } else if (stateData != null && "onStateChange".equals(eventName)) {
                                parsed.playerState = stateData.optString("state", "");
                                double position = stateData.optDouble("currentTime", -1);
                                double length = stateData.optDouble("duration", -1);
                                if (position >= 0) parsed.currentTime = position;
                                if (length > 0) parsed.duration = length;
                            }
                        }
                    }
                    parsed.eventCount++;
                    if (eventId >= 0) parsed.lastEventId = Math.max(parsed.lastEventId, eventId);
                }
            }
        }
        return parsed;
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
