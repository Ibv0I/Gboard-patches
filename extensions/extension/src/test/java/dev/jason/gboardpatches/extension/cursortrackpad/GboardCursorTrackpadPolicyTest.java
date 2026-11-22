package dev.jason.gboardpatches.extension.cursortrackpad;

import org.junit.Assert;
import org.junit.Test;

public final class GboardCursorTrackpadPolicyTest {
    @Test
    public void cursorTrackpadIsEnabledByDefault() {
        Assert.assertTrue(GboardCursorTrackpadSettings.DEFAULT_ENABLED);
    }

    @Test
    public void enabledForcesBothFlags() {
        Assert.assertEquals(Boolean.TRUE, GboardCursorTrackpadPolicy.maybeForceFlag(
                "free_cursor", Boolean.FALSE, true));
        Assert.assertEquals(Boolean.TRUE, GboardCursorTrackpadPolicy.maybeForceFlag(
                "free_cursor_lock_mode", Boolean.FALSE, true));
    }

    @Test
    public void missingPreferencesUseTheEnabledDefaultForNativeFlags() {
        Assert.assertEquals(Boolean.TRUE, GboardCursorTrackpadRuntime.applyOverriddenFlagValue(
                "free_cursor", Boolean.FALSE, null));
        Assert.assertEquals(Boolean.TRUE, GboardCursorTrackpadRuntime.applyOverriddenFlagValue(
                "free_cursor_lock_mode", Boolean.FALSE, null));
        Assert.assertSame(Boolean.FALSE, GboardCursorTrackpadRuntime.applyOverriddenFlagValue(
                "unrelated", Boolean.FALSE, null));
    }

    @Test
    public void disabledOrUnrelatedPreservesStock() {
        Assert.assertSame(Boolean.FALSE, GboardCursorTrackpadPolicy.maybeForceFlag(
                "free_cursor", Boolean.FALSE, false));
    }
}
