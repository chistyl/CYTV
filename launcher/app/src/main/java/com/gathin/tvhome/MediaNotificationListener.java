package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Dedicated notification-listener component used only to authorize media-session discovery.
 * It intentionally has no lifecycle side effects so Android 9 TV firmware can bind it
 * independently from GuardRevival's launcher-recovery behavior.
 */
public final class MediaNotificationListener extends NotificationListenerService {
}
