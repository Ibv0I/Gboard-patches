package dev.jason.gboardpatches.extension.cursortrackpad;

import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettings;

public final class GboardCursorTrackpadSettings {
    public static final String PREF_KEY_ENABLED = "pref_force_cursor_trackpad_mode";
    // The spacebar long-press gesture must enter Gboard's native cursor trackpad
    // mode without requiring users to discover and enable a separate setting.
    public static final boolean DEFAULT_ENABLED = true;

    private GboardCursorTrackpadSettings() {
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        return GboardBooleanFlagSettings.readEnabled(
                preferences, PREF_KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static void ensureDefault(SharedPreferences preferences) {
        GboardBooleanFlagSettings.ensureDefault(
                preferences, PREF_KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        return GboardBooleanFlagSettings.writeEnabled(preferences, PREF_KEY_ENABLED, enabled);
    }
}
