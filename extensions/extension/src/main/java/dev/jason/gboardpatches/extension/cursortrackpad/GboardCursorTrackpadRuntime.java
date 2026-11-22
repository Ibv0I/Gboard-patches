package dev.jason.gboardpatches.extension.cursortrackpad;

import android.content.SharedPreferences;
import android.util.Log;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardCursorTrackpadRuntime {
    private static final String TAG = "GboardSpacebarLang";
    private static final AtomicInteger DEBUG_LOG_COUNT = new AtomicInteger();

    private GboardCursorTrackpadRuntime() {
    }

    public static boolean applyBooleanPreference(String key, boolean stock) {
        if (!"enable_scrub_move".equals(key)) return stock;
        boolean result = stock || isEnabledOrStock(stock);
        logFlag("Boolean preference " + key + ": stock=" + stock + ", result=" + result);
        return result;
    }

    public static boolean applyResourceBooleanPreference(int key, boolean stock) {
        if (key != 0x7f14096b) return stock;
        boolean result = stock || isEnabledOrStock(stock);
        logFlag("Resource boolean 0x" + Integer.toHexString(key)
                + ": stock=" + stock + ", result=" + result);
        return result;
    }

    private static void logFlag(String message) {
        try {
            if (DEBUG_LOG_COUNT.getAndIncrement() < 40) {
                Log.i(TAG, "Cursor flag diagnostic: " + message);
            }
        } catch (Throwable ignored) {
            // Diagnostics must not affect the keyboard path.
        }
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
            Object result = applyOverriddenFlagValue(flagName, stockResult, preferences);
            logFlag("Feature flag " + flagName + ": stock=" + stockResult
                    + ", prefsAvailable=" + (preferences != null) + ", result=" + result);
            return result;
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
