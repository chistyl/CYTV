package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * Restores the user-enabled YouTube SponsorBlock monitor after the TV finishes booting.
 * The foreground service owns network retry/reconnect behavior; this receiver only
 * restores the service when the user had explicitly enabled it before shutdown.
 */
public final class YouTubeAssistantBootReceiver extends BroadcastReceiver {
    private static final String TAG = "CYTV-YouTubeBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(
                YouTubeSponsorSkipService.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(YouTubeSponsorSkipService.PREF_ENABLED, false)) {
            return;
        }

        // A reboot must not silently turn on an assistant that has lost its saved TV pairing.
        if (!YouTubePairingClient.hasSavedCredentials(context)) {
            prefs.edit()
                    .putBoolean(YouTubeSponsorSkipService.PREF_ENABLED, false)
                    .putString(YouTubeSponsorSkipService.PREF_STATUS, "配對資料不存在，請重新連接 YouTube TV")
                    .apply();
            return;
        }

        prefs.edit()
                .putString(YouTubeSponsorSkipService.PREF_STATUS, "開機後正在恢復 YouTube 監控")
                .apply();

        Intent service = new Intent(context, YouTubeSponsorSkipService.class)
                .setAction(YouTubeSponsorSkipService.ACTION_ENABLE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
            Log.i(TAG, "Restored user-enabled YouTube SponsorBlock monitor after boot.");
        } catch (RuntimeException error) {
            // Leave the UI truthful and allow the user to enable the feature manually.
            prefs.edit()
                    .putBoolean(YouTubeSponsorSkipService.PREF_ENABLED, false)
                    .putString(YouTubeSponsorSkipService.PREF_STATUS, "開機恢復失敗，請手動開啟 YouTube 助手")
                    .apply();
            Log.e(TAG, "Unable to restore YouTube SponsorBlock monitor after boot.", error);
        }
    }
}
