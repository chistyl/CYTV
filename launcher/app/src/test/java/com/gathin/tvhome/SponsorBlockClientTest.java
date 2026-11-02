package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SponsorBlockClientTest {
    @Test public void segmentStartIsInclusive() {
        SponsorBlockClient.Segment segment =
                new SponsorBlockClient.Segment(12.5, 28.0, "sponsor", "uuid-1");
        assertSame(segment, SponsorBlockClient.findSegmentAtPosition(
                Collections.singletonList(segment), 12.5));
    }

    @Test public void segmentEndIsExclusive() {
        SponsorBlockClient.Segment segment =
                new SponsorBlockClient.Segment(12.5, 28.0, "sponsor", "uuid-1");
        assertNull(SponsorBlockClient.findSegmentAtPosition(
                Collections.singletonList(segment), 28.0));
    }

    @Test public void sortedSegmentsReturnTheMatchingRange() {
        SponsorBlockClient.Segment first =
                new SponsorBlockClient.Segment(10.0, 15.0, "sponsor", "uuid-1");
        SponsorBlockClient.Segment second =
                new SponsorBlockClient.Segment(30.0, 38.0, "sponsor", "uuid-2");
        List<SponsorBlockClient.Segment> segments = Arrays.asList(first, second);
        assertSame(first, SponsorBlockClient.findSegmentAtPosition(segments, 14.9));
        assertSame(second, SponsorBlockClient.findSegmentAtPosition(segments, 33.0));
    }

    @Test public void invalidPositionsAndMissingSegmentsNeverMatch() {
        SponsorBlockClient.Segment segment =
                new SponsorBlockClient.Segment(1.0, 5.0, "sponsor", "uuid-1");
        assertNull(SponsorBlockClient.findSegmentAtPosition(null, 2.0));
        assertNull(SponsorBlockClient.findSegmentAtPosition(
                Collections.singletonList(segment), -1.0));
        assertNull(SponsorBlockClient.findSegmentAtPosition(
                Collections.singletonList(segment), Double.NaN));
        assertNull(SponsorBlockClient.findSegmentAtPosition(
                Collections.singletonList(segment), Double.POSITIVE_INFINITY));
    }
}
