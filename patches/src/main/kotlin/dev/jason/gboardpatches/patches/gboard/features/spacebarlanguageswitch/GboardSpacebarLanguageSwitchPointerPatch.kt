package dev.jason.gboardpatches.patches.gboard.features.spacebarlanguageswitch

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.GboardPointerOwnerFeature
import dev.jason.gboardpatches.patches.gboard.shared.GboardPointerOwnerFeatureSpec
import dev.jason.gboardpatches.patches.gboard.shared.GboardPointerOwnerTransformationAdapter
import dev.jason.gboardpatches.patches.gboard.shared.gboardPointerOwnerFeaturePatch
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private val SPACEBAR_POINTER_MOVE_METHOD_PARAMETERS =
    listOf("Landroid/view/MotionEvent;", "I")

private val gboardSpacebarLanguageSwitchPointerTransformation =
    GboardPointerOwnerTransformationAdapter { context ->
        val candidates = context.ownerClass.methods.filter { method ->
            method.name == "t" &&
                method.parameterTypes == SPACEBAR_POINTER_MOVE_METHOD_PARAMETERS &&
                method.returnType == "V"
        }
        check(candidates.size == 1) {
            "Could not uniquely resolve qin.t(Landroid/view/MotionEvent;I)V"
        }
        val moveMethod = candidates.single()
        check(
            moveMethod.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) &&
                (moveMethod.implementation?.registerCount ?: 0) > 3
        ) { "Unexpected qin.t pointer-move method shape" }

        val popupCandidates = context.ownerClass.methods.filter { method ->
            method.name == "D" &&
                method.parameterTypes == listOf("J") &&
                method.returnType == "V"
        }
        check(popupCandidates.size == 1) {
            "Could not uniquely resolve qin.D(J)V long-press popup method"
        }
        val popupMethod = popupCandidates.single()
        check(
            popupMethod.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) &&
                (popupMethod.implementation?.registerCount ?: 0) > 3
        ) { "Unexpected qin.D long-press popup method shape" }

        moveMethod.applySpacebarSwipePromotionDelegate()
        popupMethod.applySpacebarPopupSuppressionDelegate()
    }

private val gboardSpacebarLanguageSwitchPointerSpec = GboardPointerOwnerFeatureSpec(
    feature = GboardPointerOwnerFeature.SPACEBAR_LANGUAGE_SWITCH,
    transformation = gboardSpacebarLanguageSwitchPointerTransformation,
)

internal val gboardSpacebarLanguageSwitchPointerPatch = gboardPointerOwnerFeaturePatch(
    description = "Promote horizontal movement within the spacebar to Gboard slide actions.",
    spec = gboardSpacebarLanguageSwitchPointerSpec,
)

private fun MutableMethod.applySpacebarPopupSuppressionDelegate() {
    val delegate = """
        ${RuntimeCallEmitter.invoke(
            RuntimeCallId.SPACEBAR_LANGUAGE_SWITCH_RUNTIME_MAYBE_SUPPRESS_LONG_PRESS_POPUP,
            "p0",
        )}

        move-result v0

        if-eqz v0, :jasondev_spacebar_popup_continue

        return-void

        :jasondev_spacebar_popup_continue
    """.trimIndent()
    addInstructions(0, delegate)
}

private fun MutableMethod.applySpacebarSwipePromotionDelegate() {
    val delegate = """
        ${RuntimeCallEmitter.invoke(
            RuntimeCallId.SPACEBAR_LANGUAGE_SWITCH_RUNTIME_MAYBE_PROMOTE_POINTER_SWIPE,
            "p0, p1, p2",
        )}

        move-result v0

        if-eqz v0, :jasondev_spacebar_swipe_continue

        return-void

        :jasondev_spacebar_swipe_continue
    """.trimIndent()
    addInstructions(0, delegate)
}
