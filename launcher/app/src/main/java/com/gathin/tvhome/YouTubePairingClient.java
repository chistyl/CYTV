package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Exchanges a YouTube TV pairing code for screenId/loungeToken.
 * The returned credentials are encrypted with an Android Keystore AES-GCM key.
 * This class does not claim that the Lounge control channel is connected.
 */
final class YouTubePairingClient {
    private static final String API = "https://www.youtube.com/api/lounge";
    private static final String KEY_ALIAS = "CYTV_YouTube_Pairing_Key";
    private static final String PREFS = "youtube_pairing_secure";
    private static final String PREF_DATA = "credential_blob";

    static final class PairingResult {
        final String screenId;
        final String loungeToken;
        final String deviceName;
        final String deviceId;

        PairingResult(String screenId, String loungeToken, String deviceName) {
            this(screenId, loungeToken, deviceName, UUID.randomUUID().toString());
        }

        PairingResult(String screenId, String loungeToken, String deviceName, String deviceId) {
            this.screenId = screenId;
            this.loungeToken = loungeToken;
            this.deviceName = deviceName;
            this.deviceId = deviceId;
        }
    }

    static PairingResult pair(String rawCode) throws Exception {
        String code = rawCode == null ? "" : rawCode.replace("-", "").replace(" ", "").trim();
        if (code.isEmpty() || !code.matches("[A-Za-z0-9]{4,32}")) {
            throw new IllegalArgumentException("裝置代碼格式不正確，請重新確認 YouTube TV 顯示的代碼。");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(API + "/pairing/get_screen");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] body = ("pairing_code=" + java.net.URLEncoder.encode(code, "UTF-8"))
                    .getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            if (status != 200) {
                throw new Exception(status == 404 || status == 400
                        ? "YouTube 未接受此配對代碼，請確認代碼仍有效。"
                        : "YouTube 配對服務回應錯誤（HTTP " + status + "）。");
            }

            JSONObject root = new JSONObject(response);
            JSONObject screen = root.optJSONObject("screen");
            if (screen == null) throw new Exception("YouTube 回應中沒有裝置資料，請重新取得配對代碼。");
            String screenId = screen.optString("screenId", "");
            String loungeToken = screen.optString("loungeToken", "");
            String name = screen.optString("name", "YouTube TV");
            if (screenId.isEmpty() || loungeToken.isEmpty()) {
                throw new Exception("YouTube 配對回應缺少必要憑證，未保存資料。");
            }
            return new PairingResult(screenId, loungeToken, name);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static void save(Context context, PairingResult result) throws Exception {
        JSONObject json = new JSONObject();
        json.put("screenId", result.screenId);
        json.put("loungeToken", result.loungeToken);
        json.put("name", result.deviceName);
        json.put("deviceId", result.deviceId);
        byte[] iv;
        byte[] encrypted;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        iv = cipher.getIV();
        encrypted = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
        String blob = Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.edit().putString(PREF_DATA, blob).commit()) {
            throw new Exception("配對成功，但無法安全保存裝置憑證。");
        }
    }

    static PairingResult load(Context context) throws Exception {
        String blob = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_DATA, "");
        if (blob.isEmpty()) throw new Exception("尚未完成 YouTube TV 配對。");
        String[] parts = blob.split(":", 2);
        if (parts.length != 2) throw new Exception("配對憑證格式錯誤，請重新配對。");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        JSONObject json = new JSONObject(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
        return new PairingResult(json.getString("screenId"), json.getString("loungeToken"),
                json.optString("name", "YouTube TV"),
                json.optString("deviceId", UUID.randomUUID().toString()));
    }

    static PairingResult refreshLoungeToken(Context context) throws Exception {
        PairingResult old = load(context);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API + "/pairing/get_lounge_token_batch");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] body = ("screen_ids=" + java.net.URLEncoder.encode(old.screenId, "UTF-8"))
                    .getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            String response = readAll(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (status != 200) throw new Exception("YouTube 憑證更新失敗（HTTP " + status + "）。請重新配對。");
            JSONObject root = new JSONObject(response);
            org.json.JSONArray screens = root.optJSONArray("screens");
            if (screens == null || screens.length() == 0)
                throw new Exception("YouTube 未回傳可更新的裝置憑證，請重新配對。");
            JSONObject screen = screens.getJSONObject(0);
            String screenId = screen.optString("screenId", old.screenId);
            String token = screen.optString("loungeToken", "");
            if (token.isEmpty()) throw new Exception("YouTube 未回傳新的 Lounge Token，請重新配對。");
            PairingResult updated = new PairingResult(screenId, token, old.deviceName, old.deviceId);
            save(context, updated);
            return updated;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static boolean checkAvailability(Context context) throws Exception {
        PairingResult credentials = load(context);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API + "/pairing/get_screen_availability");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] body = ("lounge_token=" + java.net.URLEncoder.encode(credentials.loungeToken, "UTF-8"))
                    .getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            String response = readAll(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (status != 200) throw new Exception("YouTube 在線狀態查詢失敗（HTTP " + status + "）。");
            JSONObject root = new JSONObject(response);
            org.json.JSONArray screens = root.optJSONArray("screens");
            return screens != null && screens.length() > 0
                    && "online".equalsIgnoreCase(screens.getJSONObject(0).optString("status", ""));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static boolean hasSavedCredentials(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(PREF_DATA);
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PREF_DATA).apply();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
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

    private YouTubePairingClient() {}
}
