package dev.jason.gboardpatches.patches.gboard.features.spacebarlanguageswitch

import dev.jason.gboardpatches.patches.gboard.registry.GboardPublishedPatchCatalog
import dev.jason.gboardpatches.patches.gboard.registry.gboardSpacebarLanguageSwitchPatch
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardSpacebarLanguageSwitchPatchShapeTest {
    @Test
    fun `public patch is enabled by default and registered`() {
        assertTrue(gboardSpacebarLanguageSwitchPatch.default)
        assertTrue(
            GboardPublishedPatchCatalog.morpheRegistrations.contains(
                gboardSpacebarLanguageSwitchPatch,
            ),
        )
    }
}
