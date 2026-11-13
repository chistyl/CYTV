package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;

/** Private local activation record. A copied record is unusable without its original Keystore key. */
final class LicenseStorage {
    private static final String PREFS = "cytv_offline_license_v1";
    private static final String RECORD = "activation_record";

    private LicenseStorage() {}

    static boolean save(Context context, LicenseVerifier.Result license) {
        try {
            KeyPair device = DeviceIdentity.getOrCreate();
            JSONObject body = new JSONObject();
            body.put("v", 1);
            body.put("license_id", license.licenseId);
            body.put("issued_at", license.issuedAt);
            body.put("expires_at", license.expiresAt);
            body.put("device_public_key", Base64.getEncoder().encodeToString(device.getPublic().getEncoded()));

            // Sign and persist the exact same canonical UTF-8 string; do not depend on JSON key ordering.
            String canonicalBody = body.toString();
            byte[] canonical = canonicalBody.getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(device.getPrivate());
            signer.update(canonical);

            JSONObject envelope = new JSONObject();
            envelope.put("body", canonicalBody);
            envelope.put("device_signature", Base64.getEncoder().encodeToString(signer.sign()));
            return prefs(context).edit().putString(RECORD, envelope.toString()).commit();
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isValid(Context context, long nowMillis) {
        String raw = prefs(context).getString(RECORD, null);
        if (raw == null || raw.isEmpty()) return false;
        try {
            KeyPair device = DeviceIdentity.getExisting();
            if (device == null || !DeviceIdentity.provePossession()) return false;

            JSONObject envelope = new JSONObject(raw);
            String canonicalBody = envelope.getString("body");
            JSONObject body = new JSONObject(canonicalBody);
            if (body.optInt("v", -1) != 1) return false;

            byte[] storedPublic = Base64.getDecoder().decode(body.getString("device_public_key"));
            if (!java.security.MessageDigest.isEqual(storedPublic, device.getPublic().getEncoded())) return false;

            String id = body.optString("license_id", "").trim();
            long issued = body.optLong("issued_at", -1L);
            long expires = body.optLong("expires_at", -1L);
            if (id.isEmpty() || issued <= 0 || expires < 0 || issued > nowMillis + 300_000L) return false;
            if (expires != 0L && nowMillis >= expires) return false;

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(device.getPublic());
            verifier.update(canonicalBody.getBytes(StandardCharsets.UTF_8));
            byte[] signature = Base64.getDecoder().decode(envelope.getString("device_signature"));
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    static void clear(Context context) {
        prefs(context).edit().remove(RECORD).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
