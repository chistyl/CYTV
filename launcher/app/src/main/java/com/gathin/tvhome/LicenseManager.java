package com.gathin.tvhome;

import android.content.Context;

/** Coordinates signed-code validation and the local device-bound activation record. */
final class LicenseManager {
    private LicenseManager() {}

    static LicenseVerifier.Result activate(Context context, String code) {
        LicenseVerifier.Result result = LicenseVerifier.verify(code);
        if (result.isValid() && !LicenseStorage.save(context, result)) {
            return new LicenseVerifier.Result(LicenseVerifier.Status.INVALID_FIELDS, "", 0L, 0L);
        }
        return result;
    }

    static boolean isActivated(Context context) {
        return LicenseStorage.isValid(context, System.currentTimeMillis());
    }

    static void deactivate(Context context) {
        LicenseStorage.clear(context);
    }
}
