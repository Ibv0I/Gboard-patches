package dev.jason.gboardpatches.patches.gboard.features.spacebarlanguageswitch

import dev.jason.gboardpatches.patches.gboard.registry.GboardPublishedPatchCatalog
import dev.jason.gboardpatches.patches.gboard.registry.gboardSpacebarLanguageSwitchPatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class GboardSpacebarLanguageSwitchPatchShapeTest {
    @Test
    fun `public patch is enabled by default and registered`() {
        assertTrue(gboardSpacebarLanguageSwitchPatch.default)
        assertTrue(GboardPublishedPatchCatalog.morpheRegistrations.contains(
            gboardSpacebarLanguageSwitchPatch
        ))
    }

    @Test
    fun `runtime preserves normal space press and recognizes only horizontal slide actions`() {
        val source = readSource(
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                "spacebarlanguageswitch/GboardSpacebarLanguageSwitchRuntime.java"
        )
        assertTrue(source.contains("view.getId() != SPACEBAR_VIEW_ID"))
        assertTrue(source.contains("actionName"))
        assertTrue(source.contains("\"SLIDE_LEFT\""))
        assertTrue(source.contains("\"SLIDE_RIGHT\""))
        assertTrue(source.contains("actionDataKeycodeField.getInt(actionData) == SPACE_KEYCODE"))
        assertFalse(source.contains("performHapticFeedback"))
        assertFalse(source.contains("sendDownUpKeyEvents"))
    }

    @Test
    fun `runtime uses Gboard language rotation manager rather than Android IME switching`() {
        val source = readSource(
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                "spacebarlanguageswitch/GboardSpacebarLanguageSwitchRuntime.java"
        )
        assertTrue(source.contains("Class.forName(\"pga\""))
        assertTrue(source.contains("Class.forName(\"pfi\""))
        assertTrue(source.contains("rotationClass.getDeclaredMethod(\"j\""))
        assertTrue(source.contains("managerClass.getDeclaredMethod(\"l\""))
        assertTrue(source.contains("\"GLOBE_KEY\""))
        assertFalse(source.contains("switchToNextInputMethod"))
        assertFalse(source.contains("InputMethodManager"))
    }

    private fun readSource(path: String): String =
        Files.readString(Path.of(path)).replace("\r\n", "\n")
}
