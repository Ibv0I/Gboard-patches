package dev.jason.gboardpatches.extension.cursortrackpad;

import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardCursorTrackpadRuntime {
    private GboardCursorTrackpadRuntime() {
    }

    public static boolean applyBooleanPreference(String key, boolean stock) {
        if (!"enable_scrub_move".equals(key) || stock) return stock;
        return isEnabledOrStock(stock);
    }

    public static boolean applyResourceBooleanPreference(int key, boolean stock) {
        if (key != 0x7f14096b || stock) return stock;
        return isEnabledOrStock(stock);
    }

    private static boolean isEnabledOrStock(boolean stock) {
        try {
            SharedPreferences prefs = GboardFlagRuntimeContext.preferencesOrNull();
            return applyPreference(stock, prefs);
        } catch (Throwable ignored) { return stock; }
    }

    static boolean applyPreference(boolean stock, SharedPreferences prefs) {
        // Flag/resource getters can run before ActivityThread exposes the application
        // context. A missing preference store must use the feature's declared default,
        // otherwise free_cursor and enable_scrub_move remain false on a cold start.
        boolean enabled = prefs == null
                ? GboardCursorTrackpadSettings.DEFAULT_ENABLED
                : GboardCursorTrackpadSettings.readEnabled(prefs);
        return stock || enabled;
    }

    public static Object applyOverriddenFlagValue(String flagName, Object stockResult) {
        try {
            if (!(GboardCursorTrackpadPolicy.FREE_CURSOR_FLAG.equals(flagName)
                    || GboardCursorTrackpadPolicy.FREE_CURSOR_LOCK_MODE_FLAG.equals(flagName))
                    || !Boolean.FALSE.equals(stockResult)) {
                return stockResult;
            }
            SharedPreferences preferences = GboardFlagRuntimeContext.preferencesOrNull();
            return applyOverriddenFlagValue(flagName, stockResult, preferences);
        } catch (Throwable ignored) {
            return stockResult;
        }
    }

    static Object applyOverriddenFlagValue(
            String flagName,
            Object stockResult,
            SharedPreferences preferences) {
        boolean enabled = preferences == null
                ? GboardCursorTrackpadSettings.DEFAULT_ENABLED
                : GboardCursorTrackpadSettings.readEnabled(preferences);
        return GboardCursorTrackpadPolicy.maybeForceFlag(flagName, stockResult, enabled);
    }

}
