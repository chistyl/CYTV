package com.gathin.tvhome;

/**
 * Bounds skipAd retries to fresh Lounge ad-state events. Transport acceptance
 * stops retries, but the caller must still wait for TV playback confirmation.
 */
final class YouTubeAdSkipRetryPolicy {
    private static final int MAX_ATTEMPTS_PER_AD = 3;
    private int attempts;
    private long lastAttemptEventVersion = Long.MIN_VALUE;
    private boolean commandAccepted;

    boolean shouldAttempt(long eventVersion) {
        return !commandAccepted
                && attempts < MAX_ATTEMPTS_PER_AD
                && eventVersion != lastAttemptEventVersion;
    }

    void recordAttempt(long eventVersion) {
        if (!shouldAttempt(eventVersion)) {
            throw new IllegalStateException("No skipAd retry is available for this event.");
        }
        attempts++;
        lastAttemptEventVersion = eventVersion;
    }

    void recordAccepted() {
        commandAccepted = true;
    }

    void reset() {
        attempts = 0;
        lastAttemptEventVersion = Long.MIN_VALUE;
        commandAccepted = false;
    }
}
