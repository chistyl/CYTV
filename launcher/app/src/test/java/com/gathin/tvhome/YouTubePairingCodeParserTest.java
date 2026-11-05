package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class YouTubePairingCodeParserTest {
    @Test public void parsesCodeWithHyphen() {
        assertEquals("AB12CD34", YouTubePairingCodeParser.findCode("Enter this code: AB12-CD34"));
    }

    @Test public void parsesCodeWithSpace() {
        assertEquals("AB12CD34", YouTubePairingCodeParser.findCode("AB12 CD34"));
    }

    @Test public void parsesEmbeddedCodeFromAccessibleText() {
        assertEquals("A1B2C3D4", YouTubePairingCodeParser.findCode(
                "Visit youtube.com/tv and enter code A1B2-C3D4 to continue."));
    }

    @Test public void rejectsOrdinaryWordsWithoutDigits() {
        assertEquals("", YouTubePairingCodeParser.findCode("CONTINUE WATCHING"));
    }

    @Test public void rejectsShortAndLongTokens() {
        assertEquals("", YouTubePairingCodeParser.findCode("AB12-CD3"));
        assertEquals("", YouTubePairingCodeParser.findCode("AB12CD34EF56"));
    }

    @Test public void rejectsNullAndEmptyText() {
        assertEquals("", YouTubePairingCodeParser.findCode(null));
        assertEquals("", YouTubePairingCodeParser.findCode(""));
    }
}
