package com.gathin.tvhome;

import android.os.Build;

import java.util.Locale;

/** Device-specific safety policy for TV models with unstable Ethernet under local DNS VPN. */
final class AdFilterDevicePolicy {
    private AdFilterDevicePolicy() {}

    static boolean isToshiba65U7000Vs() {
        // R39: no model-specific DNS VPN disable policy; the reported issue is firmware-side.
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }
}
