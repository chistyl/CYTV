package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Minimal notification-listener service used to isolate Android 9 notification-access binding.
 *
 * Keep the service callback free of launcher, accessibility, receiver-registration, and secure-
 * settings side effects. MediaSessionManager uses this component solely as the authorization token.
 */
public class GuardRevival extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        // Intentionally empty: verify the TV can retain notification-listener authorization
        // without running GuardKeep or any other app-specific work during the system bind.
    }
}
