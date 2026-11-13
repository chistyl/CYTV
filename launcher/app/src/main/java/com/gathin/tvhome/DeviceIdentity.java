package com.gathin.tvhome;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.MessageDigest;
import java.util.Locale;

/** Device-bound, non-exportable signing identity backed by Android Keystore. */
final class DeviceIdentity {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "com.gathin.tvhome.offline-license.v1";

    private DeviceIdentity() {}

    static synchronized KeyPair getOrCreate() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(ALIAS)) {
            Certificate cert = store.getCertificate(ALIAS);
            PrivateKey key = (PrivateKey) store.getKey(ALIAS, null);
            if (cert != null && key != null) return new KeyPair(cert.getPublicKey(), key);
            store.deleteEntry(ALIAS);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE);
        generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKeyPair();
    }

    /** Returns null rather than silently creating a replacement identity during startup validation. */
    static synchronized KeyPair getExisting() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (!store.containsAlias(ALIAS)) return null;
        Certificate cert = store.getCertificate(ALIAS);
        PrivateKey key = (PrivateKey) store.getKey(ALIAS, null);
        return cert == null || key == null ? null : new KeyPair(cert.getPublicKey(), key);
    }

    static byte[] sign(byte[] message) throws Exception {
        KeyPair pair = getExisting();
        if (pair == null) throw new IllegalStateException("Device identity is missing");
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(message);
        return signer.sign();
    }

    static boolean provePossession() {
        try {
            KeyPair pair = getExisting();
            if (pair == null) return false;
            byte[] challenge = ("CYTV-device-check:" + System.currentTimeMillis())
                    .getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(pair.getPrivate());
            signer.update(challenge);
            byte[] signed = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(pair.getPublic());
            verifier.update(challenge);
            return verifier.verify(signed);
        } catch (Exception e) {
            return false;
        }
    }

    static String publicKeyFingerprint() throws Exception {
        KeyPair pair = getExisting();
        if (pair == null) throw new IllegalStateException("Device identity is missing");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(pair.getPublic().getEncoded());
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format(Locale.US, "%02X", b & 0xff));
        return out.toString();
    }
}
