package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.icu.text.Transliterator;

/** Converts launcher-owned and installed-app display labels to the selected Chinese script. */
public final class TraditionalText {
    private static final String PREFS = "display_preferences";
    private static final String KEY_ENABLED = "simplified_to_traditional";
    private static final String KEY_LANGUAGE = "ui_language";
    private static final String TRADITIONAL = "zh-TW";
    private static final String SIMPLIFIED = "zh-CN";
    private static final Transliterator TO_TRADITIONAL =
            Transliterator.getInstance("Simplified-Traditional");
    private static final Transliterator TO_SIMPLIFIED =
            Transliterator.getInstance("Traditional-Simplified");

    private TraditionalText() {}

    public static String language(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, TRADITIONAL);
    }

    public static boolean isTraditional(Context context) {
        return !SIMPLIFIED.equals(language(context));
    }

    public static void setLanguage(Context context, boolean traditional) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LANGUAGE, traditional ? TRADITIONAL : SIMPLIFIED)
                .putBoolean(KEY_ENABLED, traditional).apply();
    }

    /** Compatibility for older callers; enabled now means the complete Traditional Chinese mode. */
    public static boolean isEnabled(Context context) {
        return isTraditional(context);
    }

    public static void setEnabled(Context context, boolean enabled) {
        setLanguage(context, enabled);
    }

    public static String display(Context context, CharSequence value) {
        if (value == null) return "";
        return isTraditional(context) ? toTraditional(value) : toSimplified(value);
    }

    public static String toTraditional(CharSequence value) {
        if (value == null) return "";
        synchronized (TO_TRADITIONAL) {
            return TO_TRADITIONAL.transliterate(value.toString());
        }
    }

    public static String toSimplified(CharSequence value) {
        if (value == null) return "";
        synchronized (TO_SIMPLIFIED) {
            return TO_SIMPLIFIED.transliterate(value.toString());
        }
    }
}
