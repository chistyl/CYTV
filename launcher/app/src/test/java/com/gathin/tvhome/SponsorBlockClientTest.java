package com.gathin.tvhome;

import org.junit.Test;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                new SponsorBlockClient.Segment(30.0, 38.0, "intro", "uuid-2");
        List<SponsorBlockClient.Segment> segments = Arrays.asList(first, second);
        assertSame(first, SponsorBlockClient.findSegmentAtPosition(segments, 14.9));
        assertSame(second, SponsorBlockClient.findSegmentAtPosition(segments, 33.0));
    }

    @Test public void defaultAutoSkipCategoriesCoverCommonSegmentsButExcludeAggressiveAndSpecialTypes() {
        assertTrue(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("sponsor"));
        assertTrue(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("selfpromo"));
        assertTrue(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("interaction"));
        assertTrue(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("intro"));
        assertTrue(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("outro"));
        assertTrue(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("preview"));
        assertTrue(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("hook"));
        assertTrue(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("music_offtopic"));
        assertFalse(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("filler"));
        assertFalse(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("poi_highlight"));
        assertFalse(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("exclusive_access"));
        assertFalse(SponsorBlockClient.AUTO_SKIP_CATEGORIES.contains("chapter"));
    }

    @Test public void categoryFilterMatchesOnlySelectedCategories() {
        assertTrue(SponsorBlockClient.isCategorySelected(
                "sponsor", SponsorBlockClient.AUTO_SKIP_CATEGORIES));
        assertTrue(SponsorBlockClient.isCategorySelected(
                "intro", SponsorBlockClient.AUTO_SKIP_CATEGORIES));
        assertFalse(SponsorBlockClient.isCategorySelected(
                "filler", SponsorBlockClient.AUTO_SKIP_CATEGORIES));
        assertFalse(SponsorBlockClient.isCategorySelected(
                "unknown", SponsorBlockClient.AUTO_SKIP_CATEGORIES));
        assertFalse(SponsorBlockClient.isCategorySelected(
                null, SponsorBlockClient.AUTO_SKIP_CATEGORIES));
    }

    @Test public void duplicateCategoriesAreRemovedAndUnknownCategoriesRejected() {
        List<String> normalized = SponsorBlockClient.normalizeCategories(
                Arrays.asList("sponsor", "intro", "sponsor"));
        assertEquals(Arrays.asList("sponsor", "intro"), normalized);
        try {
            SponsorBlockClient.normalizeCategories(Collections.singletonList("unknown"));
            fail("Unknown categories must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("不支援"));
        }
        try {
            SponsorBlockClient.normalizeCategories(Collections.<String>emptyList());
            fail("An empty category list must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("至少"));
        }
    }

    @Test public void apiQueryEncodesMultipleCategoriesAndOnlySkipActionType() throws Exception {
        String query = SponsorBlockClient.buildLookupQueryParameters(
                Arrays.asList("sponsor", "intro", "outro"));
        String[] fields = query.split("&");
        assertEquals(2, fields.length);
        assertEquals("[\"sponsor\",\"intro\",\"outro\"]",
                URLDecoder.decode(fields[0].substring("categories=".length()), "UTF-8"));
        assertEquals("[\"skip\"]",
                URLDecoder.decode(fields[1].substring("actionTypes=".length()), "UTF-8"));
    }

    @Test public void alreadySkippedFirstOverlappingSegmentDoesNotHideSecondCategory() {
        SponsorBlockClient.Segment sponsor =
                new SponsorBlockClient.Segment(10.0, 20.0, "sponsor", "uuid-sponsor");
        SponsorBlockClient.Segment intro =
                new SponsorBlockClient.Segment(12.0, 22.0, "intro", "uuid-intro");
        List<SponsorBlockClient.Segment> segments = Arrays.asList(sponsor, intro);
        Set<String> skipped = new HashSet<>();
        skipped.add(SponsorBlockClient.segmentKey("video123456", sponsor));

        assertSame(intro, SponsorBlockClient.findNextUnskippedSegmentAtPosition(
                segments, 15.0, skipped, "video123456"));
    }

    @Test public void sameSegmentCannotTriggerAgainAfterItIsRecordedAsSkipped() {
        SponsorBlockClient.Segment segment =
                new SponsorBlockClient.Segment(12.0, 22.0, "sponsor", "uuid-1");
        List<SponsorBlockClient.Segment> segments = Collections.singletonList(segment);
        Set<String> skipped = new HashSet<>();
        skipped.add(SponsorBlockClient.segmentKey("video123456", segment));

        assertNull(SponsorBlockClient.findNextUnskippedSegmentAtPosition(
                segments, 15.0, skipped, "video123456"));
    }

    @Test public void fallbackSegmentIdentityIncludesCategoryAndRange() {
        SponsorBlockClient.Segment sponsor =
                new SponsorBlockClient.Segment(10.0, 20.0, "sponsor", "");
        SponsorBlockClient.Segment intro =
                new SponsorBlockClient.Segment(10.0, 20.0, "intro", "");
        assertNotEquals(SponsorBlockClient.segmentKey("video123456", sponsor),
                SponsorBlockClient.segmentKey("video123456", intro));
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
