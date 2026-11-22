package dev.jason.gboardpatches.extension.spacebarlanguageswitch;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/** User preference controlling whether the spacebar language gestures are active. */
public final class GboardSpacebarLanguageSwitchSettings {
    public static final String PREF_KEY_ENABLED = "pref_spacebar_language_switch_enabled";
    public static final boolean DEFAULT_ENABLED = true;

    private GboardSpacebarLanguageSwitchSettings() {
    }

    /** Runtime-safe read: locked or unavailable settings preserve the patched default. */
    public static boolean readEnabled(Context context) {
        return GboardPatchesSettings.readRuntimeSetting(
                context,
                GboardSpacebarLanguageSwitchSettings::readEnabled,
                DEFAULT_ENABLED);
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_ENABLED;
        }
        Object raw = preferences.getAll().get(PREF_KEY_ENABLED);
        if (raw instanceof Boolean value) {
            return value.booleanValue();
        }
        if (raw instanceof String value) {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }
        return DEFAULT_ENABLED;
    }

    public static void ensureDefault(SharedPreferences preferences) {
        if (preferences != null && !preferences.contains(PREF_KEY_ENABLED)) {
            preferences.edit().putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED).commit();
        }
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
        return writeEnabled(preferences, enabled);
    }

    static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        try {
            return preferences.edit().putBoolean(PREF_KEY_ENABLED, enabled).commit();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
