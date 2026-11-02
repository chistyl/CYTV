package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
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

    private static final class ParsedEvents {
        String sid = "";
        String gsessionId = "";
        long lastEventId = -1;
    }

    static Session connect(Context context) throws Exception {
        // Lounge tokens expire; refresh from the paired screen ID before opening a new session.
        YouTubePairingClient.PairingResult credentials =
                YouTubePairingClient.refreshLoungeToken(context);
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
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            if (status == 401) {
                throw new Exception("YouTube Lounge 憑證已失效（HTTP 401），請重新配對電視。");
            }
            if (status != 200) {
                throw new Exception("YouTube Lounge bind 連線失敗（HTTP " + status + "）。");
            }

            ParsedEvents events = parseEventFrames(response);
            if (events.sid.isEmpty() || events.gsessionId.isEmpty() || events.lastEventId < 0) {
                throw new Exception("YouTube 已回應，但封包中缺少 SID、gsessionid 或事件編號；"
                        + "尚未建立可用的控制工作階段。");
            }
            return new Session(events.sid, events.gsessionId, credentials.screenId,
                    credentials.deviceId, events.lastEventId);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * YouTube Lounge frames are: decimal character count + newline + JSON event array.
     * Each event is [eventId, [eventName, ...arguments]].
     */
    private static ParsedEvents parseEventFrames(String response) throws Exception {
        ParsedEvents parsed = new ParsedEvents();
        try (BufferedReader reader = new BufferedReader(new StringReader(response))) {
            String lengthLine;
            while ((lengthLine = reader.readLine()) != null) {
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
                    String line = reader.readLine();
                    if (line == null) {
                        throw new Exception("YouTube Lounge 回應不完整：事件封包提前結束。");
                    }
                    frame.append(line);
                    remaining -= line.length() + 1;
                    if (remaining < 0) {
                        throw new Exception("YouTube Lounge 回應格式錯誤：事件封包長度不符。");
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
                    if ("c".equals(eventName) && payload.length() > 1) {
                        parsed.sid = payload.optString(1, "");
                    } else if ("S".equals(eventName) && payload.length() > 1) {
                        parsed.gsessionId = payload.optString(1, "");
                    }
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
