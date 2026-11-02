package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.*;

public class PlaybackEventAccumulatorTest {
    @Test public void combinesPositionAndStateFromSeparateEvents() {
        PlaybackEventAccumulator playback = new PlaybackEventAccumulator();

        playback.recordPosition(18.25, 420.0);
        assertTrue(playback.hasPosition());
        assertFalse(playback.hasPlayerState());
        assertFalse(playback.isUsableForAutoSkip());

        playback.recordPlayerState("1");
        assertTrue(playback.hasPlayerState());
        assertTrue(playback.isUsableForAutoSkip());
        assertEquals(18.25, playback.currentTime, 0.001);
        assertEquals(420.0, playback.duration, 0.001);
    }

    @Test public void latestPausedStateOverridesEarlierPlayingState() {
        PlaybackEventAccumulator playback = new PlaybackEventAccumulator();
        playback.recordPlayerState("1");
        playback.recordPosition(25.0, 100.0);
        assertTrue(playback.isUsableForAutoSkip());

        playback.recordPlayerState("2");
        assertFalse(playback.isUsableForAutoSkip());
    }

    @Test public void latestPositionWinsAndInvalidPositionIsIgnored() {
        PlaybackEventAccumulator playback = new PlaybackEventAccumulator();
        playback.recordPosition(12.0, 90.0);
        playback.recordPosition(14.5, 90.0);
        playback.recordPosition(Double.NaN, 90.0);
        playback.recordPosition(-1.0, 90.0);
        playback.recordPlayerState("1");

        assertEquals(14.5, playback.currentTime, 0.001);
        assertTrue(playback.isUsableForAutoSkip());
    }

    @Test public void missingStateNeverAllowsAutoSkip() {
        PlaybackEventAccumulator playback = new PlaybackEventAccumulator();
        playback.recordPosition(12.0, 90.0);
        assertFalse(playback.isUsableForAutoSkip());
    }
}
