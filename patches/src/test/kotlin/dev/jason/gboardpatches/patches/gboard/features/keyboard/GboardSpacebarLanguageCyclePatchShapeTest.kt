package dev.jason.gboardpatches.patches.gboard.features.keyboard

import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardSpacebarLanguageCyclePatchShapeTest {
    @Test
    fun runtimeBridgeMethodsMatchRegisteredAbi() {
        assertTrue(RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_BEGIN_FROM_DOWN.encodedAbi.endsWith("beginFromSpacebar(Ljava/lang/Object;)V"))
        assertTrue(RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_OBSERVE_POINTER.encodedAbi.endsWith("observePointer(Ljava/lang/Object;)V"))
        assertTrue(RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_RESOLVE_ACTION.encodedAbi.endsWith("resolveAction(Ljava/lang/Object;Ljava/lang/Object;Landroid/view/MotionEvent;I)Ljava/lang/Object;"))
        assertTrue(RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_CLEAR_ALL.encodedAbi.endsWith("clearAll()V"))
    }

    @Test
    fun actionResolverUsesAStaticFourRegisterInvocation() {
        val code = RuntimeCallEmitter.invoke(
            RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_RESOLVE_ACTION,
            "v3, v4, p1, p2",
        )
        assertTrue(code.startsWith("invoke-static {"))
        assertTrue(code.endsWith(RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_RESOLVE_ACTION.encodedAbi))
    }
}
