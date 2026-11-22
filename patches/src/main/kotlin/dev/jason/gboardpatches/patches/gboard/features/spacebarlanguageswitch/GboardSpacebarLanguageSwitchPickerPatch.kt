package dev.jason.gboardpatches.patches.gboard.features.spacebarlanguageswitch

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private val SPACEBAR_PICKER_EVENT_CALL =
    RuntimeCallId.SPACEBAR_LANGUAGE_SWITCH_RUNTIME_MAYBE_CONSUME_SPACE_IME_PICKER
private val SPACEBAR_PICKER_EVENT_DESCRIPTOR =
    RuntimeAbiCatalog.abi(SPACEBAR_PICKER_EVENT_CALL).reference

private val SPACEBAR_PICKER_EVENT_TARGET =
    GboardMethodTarget("Lpvd;", "m", listOf("Lohe;"), "Z")

internal val gboardSpacebarLanguageSwitchPickerPatch = bytecodePatch(
    description = "Suppress the long-press language picker on space while preserving cursor control.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val method = SPACEBAR_PICKER_EVENT_TARGET.resolve(this)
        check(
            method.accessFlags ==
                (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value),
        ) { "Unexpected pvd.m picker event handler access flags" }

        val implementation = method.implementation
            ?: error("Spacebar picker event handler has no implementation")
        check(implementation.registerCount >= 3) {
            "Spacebar picker event handler has no local register for its guard"
        }

        val matchingCalls = implementation.instructions.count { instruction ->
            instruction.isMethodReference(SPACEBAR_PICKER_EVENT_DESCRIPTOR)
        }
        when (matchingCalls) {
            0 -> method.addInstructions(
                0,
                """
                    ${RuntimeCallEmitter.invoke(
                        SPACEBAR_PICKER_EVENT_CALL,
                        "p1",
                    )}

                    move-result v0

                    if-eqz v0, :jasondev_spacebar_picker_continue

                    const/4 v0, 0x1

                    return v0

                    :jasondev_spacebar_picker_continue
                """.trimIndent(),
            )

            1 -> check(
                implementation.instructions.size >= 5 &&
                    implementation.instructions[0].isMethodReference(
                        SPACEBAR_PICKER_EVENT_DESCRIPTOR,
                    ),
            ) { "Malformed existing spacebar picker suppression guard" }

            else -> error("Duplicate spacebar picker suppression runtime calls")
        }
    }
}
