package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
import java.util.regex.Pattern;

/** Applies the launcher's persisted full-interface language to every launcher activity. */
final class LauncherLocale {
    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{1,8})*");
    private static final String PREFS = "display_preferences";
    private static final String KEY_LANGUAGE = "ui_language";

    private LauncherLocale() {}

    static Locale parse(String tag) {
        if (tag == null || !TAG.matcher(tag.trim()).matches()) return null;
        return Locale.forLanguageTag(tag.trim());
    }

    /** New installations default to Traditional Chinese; the user's choice overrides TV firmware language. */
    static Context wrap(Context base) {
        String tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "zh-TW");
        Locale locale = parse(tag);
        if (locale == null) locale = Locale.forLanguageTag("zh-TW");
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        return base.createConfigurationContext(configuration);
    }
}
