package com.gathin.tvhome;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/** Offline RSA-SHA256 activation verifier. Never embed the signing key in the APK. */
final class ActivationGate {
    private static final String PREFS = "cytv_activation_v1";
    private static final String TOKEN_KEY = "signed_token";
    private static final String PUBLIC_KEY = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAk7sfu6nXCp3IbYAYL2XV5GrgO3TatzipcqAfMjMh+voSgPrpVHdXjQDHtbet4atChvo2BMnYB31pkx4J+CQIrfDeBNb9s4stYF1eHhu0TsM18AXDk+xI2DuStpnzpIowm34c4dX5X06yt4tRp/gfj7h6BlW80XGv2Z5lv7IO3bh/Xh+ScZHrr7egktlYxp2PcDb3ADiwNg1mFuOeRKPNJUifoHbbbHvz86UxpmfHs/k64t3qNt1csZdg9rHxhKru0bJyr8DQtzC2FLeLrFYZixSgejb6sjcTIciOY7AtWxvplVZ9/7gtoaivT1jmekYL18Sh5WkesKYNGHASyZvlMJJBSdqniDCQOkVQW3Yz8aOcHxccP5bjachE+Wkw6aeyhkzfri1hHrlPKUAlAeUyUDBB9P9hI/APNE6FvIgGWUbjxsFn874aubJiRkqTRnPBfJ78KtHrUKafwQhjQfre7kjv2XHujld1i77JgNS7MQKw3ttewrE1Bjs7WLmaqi2lAgMBAAE=";
    private static final String PREFIX = "CYTV-ACTIVATION-V1|";
    private ActivationGate() { }

    static boolean isActivated(Context context) {
        String token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TOKEN_KEY, "");
        return verify(token);
    }

    static boolean activate(Context context, String token) {
        if (!verify(token)) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(TOKEN_KEY, token.trim()).commit();
    }

    static boolean verify(String token) {
        if (token == null) return false;
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
