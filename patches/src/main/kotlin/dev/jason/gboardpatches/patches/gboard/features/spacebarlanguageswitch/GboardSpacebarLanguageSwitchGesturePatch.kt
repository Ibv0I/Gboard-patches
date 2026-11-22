package dev.jason.gboardpatches.patches.gboard.features.spacebarlanguageswitch

import dev.jason.gboardpatches.patches.gboard.shared.GboardGestureFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.gboardGestureFamilyFeaturePatch

internal val gboardSpacebarLanguageSwitchGesturePatch = gboardGestureFamilyFeaturePatch(
    description = "Handle horizontal spacebar swipes through Gboard's enabled-language rotation list.",
    feature = GboardGestureFamilyFeature.SPACEBAR_LANGUAGE_SWITCH,
)
