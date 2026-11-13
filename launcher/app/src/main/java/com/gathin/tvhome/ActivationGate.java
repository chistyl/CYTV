package com.gathin.tvhome;

import android.content.Context;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

/** Offline RSA-SHA256 verifier with device-local Android Keystore protection. */
final class ActivationGate {
    private static final String PREFS = "cytv_activation_v1";
    private static final String TOKEN_KEY = "encrypted_signed_token";
    private static final String KEY_ALIAS = "cytv_activation_aes_v1";
    private static final String PUBLIC_KEY = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAk7sfu6nXCp3IbYAYL2XV5GrgO3TatzipcqAfMjMh+voSgPrpVHdXjQDHtbet4atChvo2BMnYB31pkx4J+CQIrfDeBNb9s4stYF1eHhu0TsM18AXDk+xI2DuStpnzpIowm34c4dX5X06yt4tRp/gfj7h6BlW80XGv2Z5lv7IO3bh/Xh+ScZHrr7egktlYxp2PcDb3ADiwNg1mFuOeRKPNJUifoHbbbHvz86UxpmfHs/k64t3qNt1csZdg9rHxhKru0bJyr8DQtzC2FLeLrFYZixSgejb6sjcTIciOY7AtWxvplVZ9/7gtoaivT1jmekYL18Sh5WkesKYNGHASyZvlMJJBSdqniDCQOkVQW3Yz8aOcHxccP5bjachE+Wkw6aeyhkzfri1hHrlPKUAlAeUyUDBB9P9hI/APNE6FvIgGWUbjxsFn874aubJiRkqTRnPBfJ78KtHrUKafwQhjQfre7kjv2XHujld1i77JgNS7MQKw3ttewrE1Bjs7WLmaqi2lAgMBAAE=";
    private static final String PREFIX = "CYTV-ACTIVATION-V1|";
    // Convenience activation password; this is a shared fallback, not a cryptographic secret.
    private static final String UNIVERSAL_PASSWORD = "0822";

    private ActivationGate() { }

    static boolean isActivated(Context context) {
        try {
            String encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(TOKEN_KEY, "");
            return verify(decrypt(encrypted));
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean activate(Context context, String token) {
        if (!verify(token)) return false;
        try {
            String encrypted = encrypt(token.trim());
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(TOKEN_KEY, encrypted).commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static SecretKey getOrCreateDeviceKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return (SecretKey) store.getKey(KEY_ALIAS, null);
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

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateDeviceKey());
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)
                + "." + Base64.encodeToString(ciphertext, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String decrypt(String value) throws Exception {
        if (value == null) return "";
        String[] parts = value.split("\\.", -1);
        if (parts.length != 2) return "";
        byte[] iv = Base64.decode(parts[0], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        byte[] ciphertext = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateDeviceKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    static boolean verify(String token) {
        if (token == null) return false;
        if (UNIVERSAL_PASSWORD.equals(token.trim())) return true;
        String[] parts = token.trim().split("\\.", -1);
        if (parts.length != 2 || parts[0].length() > 256 || parts[1].length() > 1024) return false;
        try {
            byte[] payload = Base64.decode(parts[0], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            byte[] signature = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            String text = new String(payload, StandardCharsets.UTF_8);
            if (!text.startsWith(PREFIX) || text.length() > 160) return false;
            byte[] keyBytes = Base64.decode(PUBLIC_KEY, Base64.DEFAULT);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes)));
            verifier.update(payload);
            return verifier.verify(signature);
        } catch (Exception ignored) {
            return false;
        }
    }
}
