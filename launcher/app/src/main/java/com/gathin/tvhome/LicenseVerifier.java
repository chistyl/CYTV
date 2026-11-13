package com.gathin.tvhome;

import java.util.Base64;


import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * Offline verifier for version-1 CYTV activation codes.
 *
 * Code format: BASE64URL(payload UTF-8) "." BASE64URL(RSA-SHA256 signature).
 * The signature covers the exact UTF-8 payload bytes. The private signing key must never ship in the APK.
 */
final class LicenseVerifier {
    static final String PRODUCT_ID = "CYTV";
    private static final int FORMAT_VERSION = 1;
    // Public key only. Matching private key is held offline by the license issuer.
    private static final String PUBLIC_KEY_PEM =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAtTRGWjG3M686jnrtNIrk\n" +
            "Dv3LWT5pV/5PrcJMycV3V2LrsEn7tFVbbRvzPdNg2exRLvD93/cxz1am8BozupAj\n" +
            "zGGlsg29gBdimvUqD/Gye4FylJN6OWA9aTgcxHvFFkTaBDVZGF7dNI06hI69RjB/\n" +
            "tF1u6oRmufQCyYEY5e0tBcb+kiGl7pJPf5eR65R3/BUOeVnmvXfqdzY1D0cCk58l\n" +
            "EjbjSyNDBYWZrXPrBek+3zAMGFaUmCIYL4WgT0p+RvyYHi2KlO/BJT/MbnUz14NH\n" +
            "ig8fDNHS0MKyGYX9mXT3DyU0FAPw2IqWHAM6QfNZQ4r07kM/4ovmqfcz+nzLgf+M\n" +
            "tLXzWBlrPnp6IS7eSqQvqCia+Fju69vcGSldLz+Zn/VyRLB0jDbb7VHmRzAGFfd5\n" +
            "cQmp5MADphc/KL4GI8cCDtqb3XEplqOPpiOZMDVXglvYf7r5Et4rxngIfzvLiZcZ\n" +
            "udENwEBuTR1LttKhcIziYbeoPHISadFawU/rr6qbfDi/AgMBAAE=\n" +
            "-----END PUBLIC KEY-----";

    enum Status {
        VALID, EMPTY, MALFORMED, BAD_SIGNATURE, WRONG_PRODUCT, DEVICE_MISMATCH, UNSUPPORTED_VERSION, EXPIRED, INVALID_FIELDS
    }

    static final class Result {
        final Status status;
        final String licenseId;
        final long issuedAt;
        final long expiresAt;

        Result(Status status, String licenseId, long issuedAt, long expiresAt) {
            this.status = status;
            this.licenseId = licenseId;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }

        boolean isValid() { return status == Status.VALID; }
    }

    private LicenseVerifier() {}

    static Result verify(String activationCode) {
        return verify(activationCode, "", System.currentTimeMillis());
    }

    static Result verify(String activationCode, long nowMillis) {
        return verify(activationCode, "", nowMillis);
    }

    static Result verify(String activationCode, String expectedDeviceFingerprint) {
        return verify(activationCode, expectedDeviceFingerprint, System.currentTimeMillis());
    }

    static Result verify(String activationCode, String expectedDeviceFingerprint, long nowMillis) {
        if (activationCode == null || activationCode.trim().isEmpty()) {
            return result(Status.EMPTY);
        }
        // All activation codes must carry a valid RSA-SHA256 signature.
        try {
            String[] parts = activationCode.trim().split("\\.", -1);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return result(Status.MALFORMED);
            }

            byte[] payload = decodeUrl(parts[0]);
            byte[] signatureBytes = decodeUrl(parts[1]);
            if (payload.length == 0 || signatureBytes.length == 0 || payload.length > 4096) {
                return result(Status.MALFORMED);
            }

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey());
            verifier.update(payload);
            if (!verifier.verify(signatureBytes)) return result(Status.BAD_SIGNATURE);

            String json = new String(payload, StandardCharsets.UTF_8);
            if (jsonLong(json, "v", -1L) != FORMAT_VERSION) return result(Status.UNSUPPORTED_VERSION);
            if (!PRODUCT_ID.equals(jsonString(json, "product", ""))) return result(Status.WRONG_PRODUCT);

            String licenseId = jsonString(json, "license_id", "").trim();
            String boundFingerprint = jsonString(json, "device_fingerprint", "").trim().toUpperCase(java.util.Locale.US);
            String expectedFingerprint = expectedDeviceFingerprint == null ? "" : expectedDeviceFingerprint.trim().toUpperCase(java.util.Locale.US);
            if (boundFingerprint.isEmpty() || !boundFingerprint.matches("[0-9A-F]{64}") || expectedFingerprint.isEmpty() || !boundFingerprint.equals(expectedFingerprint)) {
                return result(Status.DEVICE_MISMATCH);
            }
            long issuedAt = jsonLong(json, "issued_at", -1L);
            long expiresAt = jsonLong(json, "expires_at", 0L); // zero means perpetual
            if (licenseId.isEmpty() || issuedAt <= 0 || expiresAt < 0 || issuedAt > nowMillis + 300_000L) {
                return new Result(Status.INVALID_FIELDS, "", 0L, 0L);
            }
            if (expiresAt != 0L && nowMillis >= expiresAt) {
                return new Result(Status.EXPIRED, licenseId, issuedAt, expiresAt);
            }
            return new Result(Status.VALID, licenseId, issuedAt, expiresAt);
        } catch (IllegalArgumentException e) {
            return result(Status.MALFORMED);
        } catch (Exception e) {
            return result(Status.BAD_SIGNATURE);
        }
    }


    // Parse the small canonical payload without Android's org.json unit-test stubs.
    private static String jsonString(String json, String key, String fallback) {
        String marker = "\"" + key + "\"";
        int keyAt = json.indexOf(marker);
        if (keyAt < 0) return fallback;
        int colon = json.indexOf(':', keyAt + marker.length());
        if (colon < 0) return fallback;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return fallback;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return fallback;
        return json.substring(start + 1, end);
    }

    private static long jsonLong(String json, String key, long fallback) {
        String marker = "\"" + key + "\"";
        int keyAt = json.indexOf(marker);
        if (keyAt < 0) return fallback;
        int colon = json.indexOf(':', keyAt + marker.length());
        if (colon < 0) return fallback;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        if (end < json.length() && json.charAt(end) == '-') end++;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return fallback;
        try { return Long.parseLong(json.substring(start, end)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static byte[] decodeUrl(String value) {
        // Android Base64 URL_SAFE accepts padded and unpadded Base64URL.
        return Base64.getUrlDecoder().decode(value);
    }

    private static PublicKey publicKey() throws Exception {
        String encoded = PUBLIC_KEY_PEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(encoded);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static Result result(Status status) {
        return new Result(status, "", 0L, 0L);
    }
}
