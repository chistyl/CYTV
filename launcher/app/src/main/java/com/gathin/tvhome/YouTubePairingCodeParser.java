package com.gathin.tvhome;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser for an eight-character YouTube TV link code. */
final class YouTubePairingCodeParser {
    private static final Pattern CODE = Pattern.compile(
            "(?i)(?<![A-Z0-9])([A-Z0-9]{4})[-\\s]?([A-Z0-9]{4})(?![A-Z0-9])");

    static String findCode(CharSequence text) {
        if (text == null) return "";
        Matcher matcher = CODE.matcher(text);
        if (!matcher.find()) return "";
        return (matcher.group(1) + matcher.group(2)).toUpperCase(Locale.US);
    }

    private YouTubePairingCodeParser() {}
}
