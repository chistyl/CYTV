package com.gathin.tvhome;

import android.os.Build;

import java.util.Locale;

/** Device-specific safety policy for TV models with unstable Ethernet under local DNS VPN. */
final class AdFilterDevicePolicy {
    private AdFilterDevicePolicy() {}

    static boolean isToshiba65U7000Vs() {
        String model = normalize(Build.MODEL);
        String manufacturer = normalize(Build.MANUFACTURER);
        String brand = normalize(Build.BRAND);
        boolean targetModel = model.contains("65U7000VS");
        boolean toshibaBrand = manufacturer.contains("TOSHIBA")
                || brand.contains("TOSHIBA")
                || model.startsWith("TOSHIBA");
        return targetModel && toshibaBrand;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }
}
