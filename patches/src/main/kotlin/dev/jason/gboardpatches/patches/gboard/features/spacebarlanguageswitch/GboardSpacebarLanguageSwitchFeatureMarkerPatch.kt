package dev.jason.gboardpatches.patches.gboard.features.spacebarlanguageswitch

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardSpacebarLanguageSwitchFeatureMarkerPatch = resourcePatch(
    description = "Mark the spacebar language-switch feature as installed."
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(SPACEBAR_LANGUAGE_SWITCH_FEATURE_MARKER_NAME)
    }
}

private const val SPACEBAR_LANGUAGE_SWITCH_FEATURE_MARKER_NAME =
    "dev.jason.gboardpatches.feature.spacebar_language_switch"
