package dev.jason.gboardpatches.extension.cursortrackpad;

import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class GboardCursorTrackpadPreferenceTest {
    @Test public void previousDisabledDefaultIsMigratedOnce() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("cursor1853_migration", 0);
        prefs.edit().clear().commit();
        prefs.edit()
                .putBoolean(GboardCursorTrackpadSettings.PREF_KEY_ENABLED, false)
                .commit();

        assertTrue(GboardCursorTrackpadSettings.readEnabled(prefs));
        assertTrue(prefs.getBoolean(GboardCursorTrackpadSettings.PREF_KEY_ENABLED, false));
        assertTrue(prefs.getBoolean(
                GboardCursorTrackpadSettings.PREF_KEY_DEFAULT_MIGRATION, false));

        // Once migrated, a deliberate user change is no longer overwritten.
        assertTrue(GboardCursorTrackpadSettings.writeEnabled(prefs, false));
        assertFalse(GboardCursorTrackpadSettings.readEnabled(prefs));
    }

    @Test public void localSwitchPromotesFalseWithoutPersistingOfficialPreference() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("cursor1853", 0);
        prefs.edit().clear().commit();

        // A fresh install now enables cursor trackpad automatically.
        assertTrue(GboardCursorTrackpadRuntime.applyPreference(false, prefs));
        assertTrue(prefs.getBoolean(GboardCursorTrackpadSettings.PREF_KEY_ENABLED, false));

        assertTrue(GboardCursorTrackpadSettings.writeEnabled(prefs, true));
        assertTrue(GboardCursorTrackpadRuntime.applyPreference(false, prefs));
        assertTrue(GboardCursorTrackpadRuntime.applyPreference(true, prefs));
        assertFalse(prefs.contains("enable_scrub_move"));

        // The separate switch can still disable the feature after migration.
        assertTrue(GboardCursorTrackpadSettings.writeEnabled(prefs, false));
        assertFalse(GboardCursorTrackpadRuntime.applyPreference(false, prefs));
    }

    @Test public void wrongStringAndWrongResourcePreserveStockButMissingStorageUsesDefault() {
        assertFalse(GboardCursorTrackpadRuntime.applyBooleanPreference("unrelated", false));
        assertFalse(GboardCursorTrackpadRuntime.applyResourceBooleanPreference(0x7f140996, false));
        assertTrue(GboardCursorTrackpadRuntime.applyBooleanPreference("unrelated", true));
        assertTrue(GboardCursorTrackpadRuntime.applyResourceBooleanPreference(0, true));
        // Early Gboard flag/resource reads may happen before an Application
        // context is available. Missing preferences therefore use the declared
        // enabled-by-default value, rather than silently disabling cursor mode.
        assertTrue(GboardCursorTrackpadRuntime.applyPreference(false, null));
        assertTrue(GboardCursorTrackpadRuntime.applyPreference(true, null));
    }
}
