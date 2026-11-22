package dev.jason.gboardpatches.patches.gboard.features.spacebarlanguageswitch

import dev.jason.gboardpatches.patches.gboard.shared.GboardSoftKeyFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.gboardSoftKeyFamilyFeaturePatch

internal val gboardSpacebarLanguageSwitchSoftKeyPatch = gboardSoftKeyFamilyFeaturePatch(
    description = "Add horizontal swipe actions to the spacebar without changing its normal press action.",
    feature = GboardSoftKeyFamilyFeature.SPACEBAR_LANGUAGE_SWITCH,
)
