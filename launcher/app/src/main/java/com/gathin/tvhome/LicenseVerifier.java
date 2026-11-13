package com.gathin.tvhome;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

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
        VALID, EMPTY, MALFORMED, BAD_SIGNATURE, WRONG_PRODUCT, UNSUPPORTED_VERSION, EXPIRED, INVALID_FIELDS
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
        return verify(activationCode, System.currentTimeMillis());
    }

    static Result verify(String activationCode, long nowMillis) {
        if (activationCode == null || activationCode.trim().isEmpty()) {
            return result(Status.EMPTY);
        }
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

            JSONObject json = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            if (json.optInt("v", -1) != FORMAT_VERSION) return result(Status.UNSUPPORTED_VERSION);
            if (!PRODUCT_ID.equals(json.optString("product", ""))) return result(Status.WRONG_PRODUCT);

            String licenseId = json.optString("license_id", "").trim();
            long issuedAt = json.optLong("issued_at", -1L);
            long expiresAt = json.optLong("expires_at", 0L); // zero means perpetual
            if (licenseId.isEmpty() || issuedAt <= 0 || expiresAt < 0 || issuedAt > nowMillis + 300_000L) {
                return new Result(Status.INVALID_FIELDS, "", 0L, 0L);
            }
            if (expiresAt != 0L && nowMillis >= expiresAt) {
                return new Result(Status.EXPIRED, licenseId, issuedAt, expiresAt);
            }
            return new Result(Status.VALID, licenseId, issuedAt, expiresAt);
        } catch (IllegalArgumentException | JSONException e) {
            return result(Status.MALFORMED);
        } catch (Exception e) {
            return result(Status.BAD_SIGNATURE);
        }
    }

    private static byte[] decodeUrl(String value) {
        // Android Base64 URL_SAFE accepts padded and unpadded Base64URL.
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    private static PublicKey publicKey() throws Exception {
        String encoded = PUBLIC_KEY_PEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.decode(encoded, Base64.DEFAULT);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static Result result(Status status) {
        return new Result(status, "", 0L, 0L);
    }
}
