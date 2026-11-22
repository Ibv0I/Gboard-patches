package dev.jason.gboardpatches.patches.gboard.features.keyboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.invokeRegisters
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val ACTION_DEF_TYPE = "Lcom/google/android/libraries/inputmethod/metadata/ActionDef;"
private const val QIL = "Lqil;"
private const val QIN = "Lqin;"
private val BEGIN = RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_BEGIN_FROM_DOWN
private val OBSERVE = RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_OBSERVE_POINTER
private val RESOLVE = RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_RESOLVE_ACTION
private val CLEAR = RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_CLEAR
private val CLEAR_ALL = RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_CLEAR_ALL
private val CLEAR_ON_CANCEL = RuntimeCallId.SPACEBAR_LANGUAGE_SWIPE_CLEAR_ON_CANCEL

internal val gboardSpacebarLanguageCycleBytecodePatch = bytecodePatch(
    description = "Detect horizontal spacebar swipes and rotate Gboard's configured input layout.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    execute {
        applySpacebarLanguageCycle(
            GboardVersionBindings.qilPointerDown.ownerClass(this),
            GboardVersionBindings.qinPointerClear.ownerClass(this),
        )
    }
}

internal fun applySpacebarLanguageCycle(qilClass: MutableClass, qinClass: MutableClass) {
    check(GboardVersionBindings.targetVersion == "18.5.4") {
        "Spacebar Language Cycle has reviewed DEX anchors only for Gboard 18.5.4"
    }
    check(qilClass.type == QIL && qinClass.type == QIN)

    val live = Methods.resolve(qilClass, qinClass)
    when (live.state()) {
        State.PATCHED -> return
        State.MALFORMED -> error("Refusing partially patched Gboard spacebar lifecycle")
        State.STOCK -> Unit
    }
    live.validateStock()

    val candidateQil = MutableClass(qilClass)
    val candidateQin = MutableClass(qinClass)
    val candidate = Methods.resolve(candidateQil, candidateQin)
    candidate.inject()
    candidate.validatePatched()

    qilClass.methods.clear()
    qilClass.methods.addAll(candidateQil.methods)
    qinClass.methods.clear()
    qinClass.methods.addAll(candidateQin.methods)
}

private enum class State { STOCK, PATCHED, MALFORMED }
private data class CallSite(val index: Int, val registers: List<Int>)

private data class Methods(
    val down: MutableMethod,
    val move: MutableMethod,
    val up: MutableMethod,
    val reset: MutableMethod,
    val close: MutableMethod,
    val cancel: MutableMethod,
    val hitTest: MutableMethod,
    val pointerMove: MutableMethod,
    val pointerClear: MutableMethod,
    val actionDispatch: MutableMethod,
) {
    fun state(): State {
        val expected = listOf(
            down to listOf(CLEAR_ALL, BEGIN),
            move to listOf(OBSERVE),
            up to listOf(RESOLVE),
            reset to listOf(CLEAR_ALL),
            close to listOf(CLEAR_ALL),
            cancel to listOf(CLEAR_ON_CANCEL),
            pointerClear to listOf(CLEAR),
        )
        var seen = 0
        var total = 0
        for ((method, calls) in expected) {
            val insns = method.implementation?.instructions ?: return State.MALFORMED
            for (call in calls) {
                val n = insns.count { it.isMethodReference(call.encodedAbi) }
                if (n > 1) return State.MALFORMED
                if (n == 1) seen++
                total++
            }
        }
        if (seen == 0) return State.STOCK
        if (seen != total) return State.MALFORMED
        return if (runCatching { validatePatched() }.isSuccess) State.PATCHED else State.MALFORMED
    }

    fun validateStock() {
        for (m in listOf(down, move, up, reset, close, cancel, hitTest, pointerMove, pointerClear, actionDispatch)) {
            check(m.implementation != null && (m.accessFlags and AccessFlags.ABSTRACT.value) == 0) {
                "Missing or abstract DEX method ${m.definingClass}->${m.name}"
            }
        }
        check((up.accessFlags and AccessFlags.STATIC.value) != 0)
        check(up.parameterTypes == listOf(QIN, "Landroid/view/MotionEvent;", "I"))
        check(actionDispatch.parameterTypes.firstOrNull() == ACTION_DEF_TYPE)
        callSite(down, GboardVersionBindings.qinPointerHitTest.reference)
        callSite(move, GboardVersionBindings.qinPointerMove.reference)
        check(callSite(up, GboardVersionBindings.qinPointerDispatchAction.reference).registers.size == 10)
        check(cancel.parameterTypes == listOf("Landroid/view/MotionEvent;")) {
            "Unexpected qil.i(MotionEvent) cancel signature"
        }
        check(pointerClear.parameterTypes == listOf("J", "I"))
    }

    fun inject() {
        val downSite = callSite(down, GboardVersionBindings.qinPointerHitTest.reference)
        down.addInstructions(downSite.index + 1, RuntimeCallEmitter.invoke(BEGIN, "v${downSite.registers[0]}"))
        down.addInstructions(0, RuntimeCallEmitter.invoke(CLEAR_ALL, ""))

        val moveSite = callSite(move, GboardVersionBindings.qinPointerMove.reference)
        check(moveSite.registers.size == 3) {
            "Unexpected qin.t(MotionEvent,int) MOVE register shape: ${moveSite.registers}"
        }
        move.addInstructions(
            moveSite.index + 1,
            RuntimeCallEmitter.invoke(
                OBSERVE,
                "v${moveSite.registers[0]}, v${moveSite.registers[1]}, v${moveSite.registers[2]}",
            ),
        )

        val upSite = callSite(up, GboardVersionBindings.qinPointerDispatchAction.reference)
        val ownerReg = "v${upSite.registers[0]}"
        val actionReg = "v${upSite.registers[1]}"
        up.addInstructions(upSite.index, """
            ${RuntimeCallEmitter.invoke(RESOLVE, "$ownerReg, $actionReg, p1, p2")}
            move-result-object $actionReg
            check-cast $actionReg, $ACTION_DEF_TYPE
        """.trimIndent())

        pointerClear.addInstructions(0, RuntimeCallEmitter.invoke(CLEAR, "p0"))
        reset.addInstructions(0, RuntimeCallEmitter.invoke(CLEAR_ALL, ""))
        close.addInstructions(0, RuntimeCallEmitter.invoke(CLEAR_ALL, ""))
        cancel.addInstructions(0, RuntimeCallEmitter.invoke(CLEAR_ON_CANCEL, "p1"))
    }

    fun validatePatched() {
        check(count(down, CLEAR_ALL) == 1 && count(down, BEGIN) == 1)
        check(count(move, OBSERVE) == 1 && count(up, RESOLVE) == 1)
        check(count(pointerClear, CLEAR) == 1)
        check(count(reset, CLEAR_ALL) == 1 && count(close, CLEAR_ALL) == 1)
        check(count(cancel, CLEAR_ON_CANCEL) == 1)

        check(cancel.implementation!!.instructions.first().isMethodReference(CLEAR_ON_CANCEL.encodedAbi))

        val di = down.implementation!!.instructions
        val dc = di.indexOfFirst { it.isMethodReference(CLEAR_ALL.encodedAbi) }
        val dh = di.indexOfFirst { it.isMethodReference(GboardVersionBindings.qinPointerHitTest.reference) }
        val db = di.indexOfFirst { it.isMethodReference(BEGIN.encodedAbi) }
        check(dc == 0 && dh >= 0 && db == dh + 1)

        val mi = move.implementation!!.instructions
        val ma = mi.indexOfFirst { it.isMethodReference(GboardVersionBindings.qinPointerMove.reference) }
        val mo = mi.indexOfFirst { it.isMethodReference(OBSERVE.encodedAbi) }
        check(ma >= 0 && mo == ma + 1)

        val ui = up.implementation!!.instructions
        val ua = ui.indexOfFirst { it.isMethodReference(GboardVersionBindings.qinPointerDispatchAction.reference) }
        val ur = ui.indexOfFirst { it.isMethodReference(RESOLVE.encodedAbi) }
        check(ur >= 0 && ur + 3 == ua)
        check(ui[ur + 1].opcode.name == "MOVE_RESULT_OBJECT")
        check(ui[ur + 2].opcode.name == "CHECK_CAST")

        for (m in listOf(pointerClear, reset, close)) {
            val expected = if (m === pointerClear) CLEAR else CLEAR_ALL
            check(m.implementation!!.instructions.first().isMethodReference(expected.encodedAbi))
        }
    }

    companion object {
        fun resolve(qil: MutableClass, qin: MutableClass): Methods {
            fun q(binding: GboardMethodTarget) = binding.resolve(qil)
            fun n(binding: GboardMethodTarget) = binding.resolve(qin)
            return Methods(
                q(GboardVersionBindings.qilPointerDown),
                q(GboardVersionBindings.qilPointerMove),
                q(GboardVersionBindings.qilActionUp),
                q(GboardVersionBindings.qilPointerReset),
                q(GboardVersionBindings.qilPointerClose),
                q(GboardVersionBindings.qilPointerCancel),
                n(GboardVersionBindings.qinPointerHitTest),
                n(GboardVersionBindings.qinPointerMove),
                n(GboardVersionBindings.qinPointerClear),
                n(GboardVersionBindings.qinPointerDispatchAction),
            )
        }
    }
}

private fun callSite(method: MutableMethod, descriptor: String): CallSite {
    val insns = method.implementation?.instructions
        ?: error("Missing implementation for ${method.definingClass}->${method.name}")
    val matches = insns.indices.filter { insns[it].isMethodReference(descriptor) }
    check(matches.size == 1) {
        "Expected one $descriptor invoke in ${method.definingClass}->${method.name}, found ${matches.size}"
    }
    val index = matches.single()
    return CallSite(index, insns[index].invokeRegisters(descriptor)
        ?: error("Unsupported invoke encoding for $descriptor"))
}

private fun count(method: MutableMethod, id: RuntimeCallId): Int =
    method.implementation?.instructions?.count { it.isMethodReference(id.encodedAbi) } ?: 0
