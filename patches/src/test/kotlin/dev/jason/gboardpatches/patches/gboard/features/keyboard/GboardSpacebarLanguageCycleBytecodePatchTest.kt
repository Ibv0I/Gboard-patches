package dev.jason.gboardpatches.patches.gboard.features.keyboard

import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardSpacebarLanguageCycleBytecodePatchTest {
    @Test
    fun `all lifecycle bindings point at the reviewed qil and qin methods`() {
        assertEquals("Lqil;->c(Landroid/view/MotionEvent;Z)Lqin;", GboardVersionBindings.qilPointerDown.reference)
        assertEquals("Lqil;->h(Landroid/view/MotionEvent;)V", GboardVersionBindings.qilPointerMove.reference)
        assertEquals("Lqil;->t(Lqin;Landroid/view/MotionEvent;I)V", GboardVersionBindings.qilActionUp.reference)
        assertEquals("Lqil;->i(Landroid/view/MotionEvent;)V", GboardVersionBindings.qilPointerCancel.reference)
        assertEquals("Lqil;->m()V", GboardVersionBindings.qilPointerReset.reference)
        assertEquals("Lqil;->close()V", GboardVersionBindings.qilPointerClose.reference)
        assertEquals("Lqin;->F(Landroid/view/MotionEvent;I)V", GboardVersionBindings.qinPointerHitTest.reference)
        assertEquals("Lqin;->t(Landroid/view/MotionEvent;I)V", GboardVersionBindings.qinPointerMove.reference)
        assertEquals(
            "Lqin;->u(Lcom/google/android/libraries/inputmethod/metadata/ActionDef;" +
                "Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;JZZJI)V",
            GboardVersionBindings.qinPointerDispatchAction.reference,
        )
        assertEquals("Lqin;->A(JI)V", GboardVersionBindings.qinPointerClear.reference)
    }

    @Test
    fun `patch delegates each lifecycle phase to the matching runtime method`() {
        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/keyboard/" +
                "GboardSpacebarLanguageCycleBytecodePatch.kt",
        )
        assertTrue(source.contains("SPACEBAR_LANGUAGE_SWIPE_BEGIN_FROM_DOWN"))
        assertTrue(source.contains("SPACEBAR_LANGUAGE_SWIPE_OBSERVE_POINTER"))
        assertTrue(source.contains("SPACEBAR_LANGUAGE_SWIPE_RESOLVE_ACTION"))
        assertTrue(source.contains("SPACEBAR_LANGUAGE_SWIPE_CLEAR_ON_CANCEL"))
        assertTrue(source.contains("SPACEBAR_LANGUAGE_SWIPE_CLEAR_ALL"))
        assertTrue(source.contains("SPACEBAR_LANGUAGE_SWIPE_CLEAR"))
        assertTrue(source.contains("qinPointerDispatchAction"))
    }

    @Test
    fun `spacebar language cycle remains an independent registered contribution`() {
        val wiring = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardContributionWiring.kt",
        )
        val registry = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardPatchRegistry.kt",
        )
        assertTrue(wiring.contains("\"spacebar_language_cycle\""))
        assertTrue(wiring.contains("gboardSpacebarLanguageCycleBytecodePatch"))
        assertTrue(registry.contains("gboardSpacebarLanguageCyclePatch"))
        assertTrue(registry.contains("featureId = \"spacebar_language_cycle\""))
    }

    private fun readSource(path: String): String =
        Files.readString(Path.of(path)).replace("\r\n", "\n")
}
