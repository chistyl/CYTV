package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YouTubeAdSkipRetryPolicyTest {
    @Test public void retriesOnlyOnFreshEventsAndStopsAfterThreeFailures() {
        YouTubeAdSkipRetryPolicy policy = new YouTubeAdSkipRetryPolicy();

        assertTrue(policy.shouldAttempt(10));
        policy.recordAttempt(10);
        assertFalse(policy.shouldAttempt(10));
        assertTrue(policy.shouldAttempt(11));
        policy.recordAttempt(11);
        assertTrue(policy.shouldAttempt(12));
        policy.recordAttempt(12);
        assertFalse(policy.shouldAttempt(13));
    }

    @Test public void acceptedCommandStopsFurtherRetriesUntilNextAd() {
        YouTubeAdSkipRetryPolicy policy = new YouTubeAdSkipRetryPolicy();
        policy.recordAttempt(1);
        policy.recordAccepted();

        assertFalse(policy.shouldAttempt(2));
        policy.reset();
        assertTrue(policy.shouldAttempt(1));
    }
}
