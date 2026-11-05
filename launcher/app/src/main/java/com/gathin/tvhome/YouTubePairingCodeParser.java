package com.gathin.tvhome;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative parser for the eight-character YouTube TV link code. */
final class YouTubePairingCodeParser {
    private static final Pattern CODE = Pattern.compile(
            "(?i)(?<![A-Z0-9])([A-Z0-9]{4})[-\\s]?([A-Z0-9]{4})(?![A-Z0-9])");

    static String findCode(CharSequence text) {
        if (text == null) return "";
        Matcher matcher = CODE.matcher(text);
        while (matcher.find()) {
            String candidate = (matcher.group(1) + matcher.group(2)).toUpperCase(Locale.US);
            boolean hasLetter = false;
            boolean hasDigit = false;
            for (int i = 0; i < candidate.length(); i++) {
                char c = candidate.charAt(i);
                hasLetter |= c >= 'A' && c <= 'Z';
                hasDigit |= c >= '0' && c <= '9';
            }
            if (hasLetter && hasDigit) return candidate;
        }
        return "";
    }

    private YouTubePairingCodeParser() {}
}
