package dev.jason.gboardpatches.patches.gboard.features.keyboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.invokeRegisters
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val MOTION_EVENT = "Landroid/view/MotionEvent;"
private const val ACTION_DEF =
    "Lcom/google/android/libraries/inputmethod/metadata/ActionDef;"

internal val gboardSpacebarLanguageCycleBytecodePatch = bytecodePatch(
    description = "Переключает на следующий язык Gboard горизонтальным свайпом по пробелу, не меняя штатные tap, long-press и cursor gestures.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val down = findMutableMethodOrThrow(GboardVersionBindings.qilPointerDown)
        val move = findMutableMethodOrThrow(GboardVersionBindings.qilPointerMove)
        val up = findMutableMethodOrThrow(GboardVersionBindings.qilActionUp)
        val cancel = findMutableMethodOrThrow(GboardVersionBindings.qilPointerCancel)
        val reset = findMutableMethodOrThrow(GboardVersionBindings.qilPointerReset)
        val close = findMutableMethodOrThrow(GboardVersionBindings.qilPointerClose)
        val clearPointer = findMutableMethodOrThrow(GboardVersionBindings.qinPointerClear)

        down.injectAfterInvokeOnce(
            GboardVersionBindings.qinPointerHitTest,
            RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_BEGIN_FROM_DOWN,
        ) { registers ->
            "v${registers[0]}"
        }

        move.injectAfterInvokeOnce(
            GboardVersionBindings.qinPointerMove,
            RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_OBSERVE_POINTER,
        ) { registers ->
            "v${registers[0]}, v${registers[1]}, v${registers[2]}"
        }

        up.injectSpacebarActionResolver()

        cancel.injectEntryDelegateOnce(
            RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_CLEAR_ON_CANCEL,
            "p1",
        )
        reset.injectEntryDelegateOnce(
            RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_CLEAR_ALL,
            "",
        )
        close.injectEntryDelegateOnce(
            RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_CLEAR_ALL,
            "",
        )
        clearPointer.injectEntryDelegateOnce(
            RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_CLEAR,
            "p0",
        )
    }
}

private data class InvokeSite(
    val index: Int,
    val registers: List<Int>,
)

private fun MutableMethod.uniqueInvoke(target: GboardMethodTarget): InvokeSite {
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val matches = instructions.indices.filter { index ->
        instructions[index].isMethodReference(target.reference)
    }
    check(matches.size == 1) {
        "Expected exactly one ${target.reference} call in $definingClass->$name, found ${matches.size}"
    }
    val index = matches.single()
    val registers = instructions[index].invokeRegisters(target.reference)
        ?: error("${target.reference} is not an invoke instruction in $definingClass->$name")
    return InvokeSite(index, registers)
}

private fun MutableMethod.injectAfterInvokeOnce(
    target: GboardMethodTarget,
    runtimeCall: RuntimeCallId,
    registers: (List<Int>) -> String,
) {
    val site = uniqueInvoke(target)
    val reference = RuntimeAbiCatalog.abi(runtimeCall).reference
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.indices.filter { index ->
        instructions[index].isMethodReference(reference)
    }
    if (existing.isNotEmpty()) {
        check(existing.size == 1 && existing.single() == site.index + 1) {
            "Malformed $reference delegate in $definingClass->$name"
        }
        return
    }

    addInstructions(
        site.index + 1,
        RuntimeCallEmitter.invoke(runtimeCall, registers(site.registers)),
    )
}

private fun MutableMethod.injectSpacebarActionResolver() {
    check(
        definingClass == GboardVersionBindings.qilActionUp.ownerDescriptor &&
            name == GboardVersionBindings.qilActionUp.name &&
            parameterTypes == listOf(
                "Lqin;",
                MOTION_EVENT,
                "I",
            ) &&
            returnType == "V",
    ) {
        "Refusing unexpected action-up method $definingClass->$name"
    }
    check((accessFlags and 0x8) != 0) {
        "Expected static action-up method for $definingClass->$name"
    }

    val target = GboardVersionBindings.qinPointerDispatchAction
    val site = uniqueInvoke(target)
    check(site.registers.size == 10) {
        "Unexpected register shape for ${target.reference}: ${site.registers}"
    }
    val actionRegister = site.registers[1]
    val runtimeCall = RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_RESOLVE_ACTION
    val runtimeReference = RuntimeAbiCatalog.abi(runtimeCall).reference
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.indices.filter { index ->
        instructions[index].isMethodReference(runtimeReference)
    }

    if (existing.isNotEmpty()) {
        check(existing.size == 1 && existing.single() + 3 == site.index) {
            "Malformed spacebar action resolver in $definingClass->$name"
        }
        check(instructions[existing.single() + 1].opcode.name == "MOVE_RESULT_OBJECT")
        check(instructions[existing.single() + 2].opcode.name == "CHECK_CAST")
        return
    }

    addInstructions(
        site.index,
        """
            ${RuntimeCallEmitter.invoke(
                runtimeCall,
                "v${site.registers[0]}, v$actionRegister, p1, p2",
            )}

            move-result-object v$actionRegister
            check-cast v$actionRegister, $ACTION_DEF
        """.trimIndent(),
    )
}

private fun MutableMethod.injectEntryDelegateOnce(
    runtimeCall: RuntimeCallId,
    registers: String,
) {
    val reference = RuntimeAbiCatalog.abi(runtimeCall).reference
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.indices.filter { index ->
        instructions[index].isMethodReference(reference)
    }
    if (existing.isNotEmpty()) {
        check(existing.size == 1 && existing.single() == 0) {
            "Malformed entry delegate $reference in $definingClass->$name"
        }
        return
    }
    addInstructions(0, RuntimeCallEmitter.invoke(runtimeCall, registers))
}
