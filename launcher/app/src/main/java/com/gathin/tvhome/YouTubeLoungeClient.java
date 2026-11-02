package com.gathin.tvhome;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First-stage YouTube Lounge bind handshake.
 *
 * This class only opens the HTTP bind session and extracts the session identifiers.
 * It does not subscribe to events or send remote-control commands yet.
 */
final class YouTubeLoungeClient {
    private static final String BIND_URL =
            "https://www.youtube.com/api/lounge/bc/bind?RID=1&VER=8&CVER=1&auth_failure_option=send_error";

    static final class Session {
        final String sid;
        final String gsessionId;
        final String screenId;
        final String deviceId;

        Session(String sid, String gsessionId, String screenId, String deviceId) {
            this.sid = sid;
            this.gsessionId = gsessionId;
            this.screenId = screenId;
            this.deviceId = deviceId;
        }
    }

    static Session connect(Context context) throws Exception {
        YouTubePairingClient.PairingResult credentials = YouTubePairingClient.load(context);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BIND_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
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
            if (status != 200) {
                throw new Exception("YouTube Lounge bind 連線失敗（HTTP " + status + "）。");
            }

            String sid = findValue(response, "c");
            String gsessionId = findValue(response, "S");
            if (sid.isEmpty() || gsessionId.isEmpty()) {
                throw new Exception("YouTube 已回應，但未取得 Lounge 工作階段識別碼。"
                        + "目前尚未建立可用的控制連線。");
            }
            return new Session(sid, gsessionId, credentials.screenId, credentials.deviceId);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String findValue(String source, String key) {
        Pattern pattern = Pattern.compile("\\[\\s*\\\"" + Pattern.quote(key)
                + "\\\"\\s*,\\s*\\\"([^\\\"]+)\\\"\\s*\\]");
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : "";
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
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return out.toString("UTF-8");
        }
    }

    private YouTubeLoungeClient() {}
}
