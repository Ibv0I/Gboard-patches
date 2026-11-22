package dev.jason.gboardpatches.extension.cursortrackpad;

import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettings;

public final class GboardCursorTrackpadSettings {
    public static final String PREF_KEY_ENABLED = "pref_force_cursor_trackpad_mode";
    static final String PREF_KEY_DEFAULT_MIGRATION =
            "pref_force_cursor_trackpad_mode_default_migrated_v1";
    // The spacebar long-press gesture must enter Gboard's native cursor trackpad
    // mode without requiring users to discover and enable a separate setting.
    public static final boolean DEFAULT_ENABLED = true;

    private GboardCursorTrackpadSettings() {
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        migratePreviousDisabledDefault(preferences);
        return GboardBooleanFlagSettings.readEnabled(
                preferences, PREF_KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static void ensureDefault(SharedPreferences preferences) {
        migratePreviousDisabledDefault(preferences);
        GboardBooleanFlagSettings.ensureDefault(
                preferences, PREF_KEY_ENABLED, DEFAULT_ENABLED);
    }

    private static void migratePreviousDisabledDefault(SharedPreferences preferences) {
        if (preferences == null || preferences.contains(PREF_KEY_DEFAULT_MIGRATION)) {
            return;
        }

        // Older builds stored false as the default when the settings screen was
        // first opened. Upgrade that stale default once; later explicit user
        // changes (including turning the feature off) are preserved.
        preferences.edit()
                .putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED)
                .putBoolean(PREF_KEY_DEFAULT_MIGRATION, true)
                .commit();
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        return GboardBooleanFlagSettings.writeEnabled(preferences, PREF_KEY_ENABLED, enabled);
    }
}
