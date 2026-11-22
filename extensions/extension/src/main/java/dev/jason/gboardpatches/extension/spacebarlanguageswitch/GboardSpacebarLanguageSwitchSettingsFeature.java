package dev.jason.gboardpatches.extension.spacebarlanguageswitch;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Settings screen for enabling or disabling language switching with a spacebar swipe. */
public final class GboardSpacebarLanguageSwitchSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";

    private final String title;
    private final String summary;
    private final String badge;
    private final String section;
    private final String enabledTitle;
    private final String enabledSummary;
    private final String errorTitle;
    private final String errorSummary;

    public GboardSpacebarLanguageSwitchSettingsFeature(Context context) {
        title = text(context, R.string.gboard_patches_spacebar_language_switch_title);
        summary = text(context, R.string.gboard_patches_spacebar_language_switch_summary);
        badge = text(context, R.string.gboard_patches_header_badge);
        section = text(context, R.string.gboard_patches_spacebar_language_switch_section);
        enabledTitle = text(context,
                R.string.gboard_patches_spacebar_language_switch_enabled_title);
        enabledSummary = text(context,
                R.string.gboard_patches_spacebar_language_switch_enabled_summary);
        errorTitle = text(context,
                R.string.gboard_patches_spacebar_language_switch_error_title);
        errorSummary = text(context,
                R.string.gboard_patches_spacebar_language_switch_error_summary);
    }

    @Override
    public String getEntryTitle() {
        return title;
    }

    @Override
    public String getEntrySummary() {
        return summary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_SPACEBAR_LANGUAGE_SWITCH);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            Context context = host == null ? null : host.getContext();
            SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
            if (context == null || preferences == null) {
                return errorScreen();
            }
            GboardSpacebarLanguageSwitchSettings.ensureDefault(preferences);
            boolean enabled = GboardSpacebarLanguageSwitchSettings.readEnabled(preferences);
            return buildScreenForState(enabled, value -> {
                if (GboardSpacebarLanguageSwitchSettings.writeEnabled(context, value)) {
                    GboardPatchesSettingsContract.refresh(host);
                } else {
                    Log.w(TAG, "Failed to save spacebar language switch setting");
                    GboardPatchesSettingsContract.refresh(host);
                }
            });
        } catch (Throwable failure) {
            Log.w(TAG, "Failed to render spacebar language switch settings", failure);
            return errorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(
            boolean enabled,
            GboardPatchesSettingsContract.ToggleAction toggleAction) {
        return new GboardPatchesSettingsContract.Screen(
                title,
                badge,
                title,
                summary,
                Collections.emptyList(),
                Collections.singletonList(new GboardPatchesSettingsContract.Section(
                        section,
                        Collections.singletonList(
                                new GboardPatchesSettingsContract.ToggleRow(
                                        enabledTitle,
                                        enabledSummary,
                                        true,
                                        enabled,
                                        toggleAction)
                                        .setMasterToggle(true)
                                        .setRestartRequired(
                                                GboardSpacebarLanguageSwitchSettings
                                                        .PREF_KEY_ENABLED)))),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);(context, resourceId);
    }
}
