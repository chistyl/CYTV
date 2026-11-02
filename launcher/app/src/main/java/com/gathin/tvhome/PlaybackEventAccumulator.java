package com.gathin.tvhome;

/**
 * Combines the latest playback position and player state observed during one
 * Lounge subscription response. YouTube can deliver these in separate events.
 */
final class PlaybackEventAccumulator {
    double currentTime = -1;
    double duration = -1;
    String playerState = "";

    void recordPosition(double positionSeconds, double durationSeconds) {
        if (!Double.isFinite(positionSeconds) || positionSeconds < 0) return;
        currentTime = positionSeconds;
        if (Double.isFinite(durationSeconds) && durationSeconds > 0) {
            duration = durationSeconds;
        }
    }

    void recordPlayerState(String state) {
        if (state != null && !state.trim().isEmpty()) {
            playerState = state.trim();
        }
    }

    boolean hasPosition() {
        return Double.isFinite(currentTime) && currentTime >= 0;
    }

    boolean hasPlayerState() {
        return !playerState.isEmpty();
    }

    boolean isPlaying() {
        return "1".equals(playerState);
    }

    boolean isUsableForAutoSkip() {
        return hasPosition() && hasPlayerState() && isPlaying();
    }
}
