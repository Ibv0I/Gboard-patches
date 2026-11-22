package dev.jason.gboardpatches.extension.spacebarlanguageswitch;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardSpacebarLanguageSwitchSettingsFeatureTest {
    @Test
    public void settingsScreenExposesOneMasterToggleAndMarksRestartRequired() {
        GboardSpacebarLanguageSwitchSettingsFeature feature =
                new GboardSpacebarLanguageSwitchSettingsFeature(null);
        AtomicBoolean changedValue = new AtomicBoolean(true);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                true, changedValue::set);

        Assert.assertEquals(1, screen.getRows().size());
        GboardPatchesSettingsContract.Row row = screen.getRows().get(0);
        Assert.assertTrue(row instanceof GboardPatchesSettingsContract.ToggleRow);
        GboardPatchesSettingsContract.ToggleRow toggle =
                (GboardPatchesSettingsContract.ToggleRow) row;
        Assert.assertTrue(toggle.isChecked());
        Assert.assertTrue(toggle.isMasterToggle());
        Assert.assertTrue(toggle.isRestartRequired());
        Assert.assertEquals(
                GboardSpacebarLanguageSwitchSettings.PREF_KEY_ENABLED,
                toggle.getRestartKey());

        toggle.getToggleAction().accept(false);
        Assert.assertFalse(changedValue.get());
    }
}
