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

        // Preserve the device-specific Ethernet safety policy.
        if (AdFilterDevicePolicy.isToshiba65U7000Vs()) {
            prefs.edit().putBoolean("enabled", false).apply();
            Log.i(TAG, "DNS VPN auto-start skipped by Toshiba Ethernet safety policy");
            return;
        }

        // VPN consent is normally retained across reboot. If Android revoked it, do not
        // leave the UI claiming the filter is enabled when no VPN can be established.
        if (VpnService.prepare(context) != null) {
            prefs.edit().putBoolean("enabled", false).apply();
            Log.w(TAG, "DNS VPN auto-start skipped: Android VPN consent is not available");
            return;
        }

        try {
            Intent service = new Intent(context, AdsFilterVpnService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
            Log.i(TAG, "Restoring previously enabled DNS VPN after boot");
        } catch (RuntimeException error) {
            prefs.edit().putBoolean("enabled", false).apply();
            Log.e(TAG, "Unable to restore DNS VPN after boot", error);
        }
    }
}
