package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;

/** Restores the user-enabled local DNS VPN after Android has completed booting. */
public final class AdsFilterBootReceiver extends BroadcastReceiver {
    private static final String TAG = "AdsFilterBoot";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences("ad_filter", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("enabled", false)) return;

        // Keep the user's enabled preference as the source of truth.
        // A boot-time consent/network condition must not silently clear that preference.
        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "DNS VPN restore deferred: Android VPN consent is not available");
            return;
        }

        try {
            Intent service = new Intent(context, AdsFilterVpnService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
            Log.i(TAG, "Restoring previously enabled DNS VPN after boot");
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to restore DNS VPN after boot; preserving user preference", error);
        }
    }
}
