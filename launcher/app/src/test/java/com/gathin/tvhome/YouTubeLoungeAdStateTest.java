package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YouTubeLoungeAdStateTest {

    private YouTubeLoungeClient.Session newSession() {
        return new YouTubeLoungeClient.Session(
                "sid", "gsession", "screen", "device", 0,
                "abcdefghijk", 0d, 60d, "1", "");
    }

    @Test
    public void adStateUpdatesDoNotCreateAnotherAdForTheSameAdVideo() {
        YouTubeLoungeClient.Session session = newSession();

        session.recordAdEvent(1, "adPlaying", true, false, false,
                "-1", "abcdefghijk", "12345678901");
        assertTrue(session.adActive);
        assertEquals(1L, session.adStartVersion);

        session.recordAdEvent(2, "onAdStateChange", null, true, null,
                "1", "abcdefghijk", "");
        assertEquals(1L, session.adStartVersion);
        assertEquals(Boolean.TRUE, session.adSkipEnabled);

        session.recordAdEvent(3, "adPlaying", true, true, false,
                "1", "abcdefghijk", "12345678901");
        assertEquals("Repeated event for the same ad must not re-arm skipAd",
                1L, session.adStartVersion);

        session.recordAdEvent(4, "adPlaying", true, false, false,
                "-1", "abcdefghijk", "zyxwvutsrqp");
        assertEquals("A different ad video starts a new one-shot window",
                2L, session.adStartVersion);
    }

    @Test
    public void unknownBumperAndSkipFlagsRemainUnknown() {
        YouTubeLoungeClient.Session session = newSession();
        session.recordAdEvent(1, "adPlaying", true, true, null,
                "-1", "abcdefghijk", "12345678901");

        assertEquals(Boolean.TRUE, session.adSkippable);
        assertEquals(Boolean.TRUE, session.adSkipEnabled);
        assertNull(session.adBumper);
    }

    @Test
    public void duplicateOrOlderLoungeEventIdsAreIgnored() {
        YouTubeLoungeClient.Session session = newSession();

        assertTrue(session.noteEvent(1));
        assertFalse(session.noteEvent(1));
        assertFalse(session.noteEvent(0));
        assertTrue(session.noteEvent(2));
        assertEquals(2L, session.lastEventId);
    }

    @Test
    public void clearingAdStateResetsAllAdFlags() {
        YouTubeLoungeClient.Session session = newSession();
        session.recordAdEvent(1, "adPlaying", true, true, false,
                "-1", "abcdefghijk", "12345678901");

        session.clearAdState();

        assertFalse(session.adActive);
        assertNull(session.adSkippable);
        assertNull(session.adSkipEnabled);
        assertNull(session.adBumper);
        assertEquals("", session.adContentVideoId);
        assertEquals("", session.adVideoId);
    }
}
