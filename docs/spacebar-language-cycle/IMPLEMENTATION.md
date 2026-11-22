# Spacebar language-cycle gesture — implementation notes

Target Gboard: `com.google.android.inputmethod.latin`
Verified target APK: `18.5.4.993608717-release-arm64-v8a`

## Requested behavior

- A horizontal swipe that starts on the space bar advances to the next enabled Gboard language/layout.
- Swipe direction is ignored; either direction invokes the same next-language action.
- A normal short tap still inserts a space.
- Existing long-press spacebar cursor/trackpad behavior must remain available.
- Only a horizontal gesture past a movement threshold is consumed.

## Current status — 22 November 2026

The standalone patch is implemented on branch `feature/spacebar-language-cycle`.

Implementation files:
- `patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/keyboard/GboardSpacebarLanguageCycleBytecodePatch.kt`
- `extensions/extension/src/main/java/dev/jason/gboardpatches/extension/keyboard/GboardSpacebarLanguageSwipeRuntime.java`

The patch uses the reviewed 18.5.4 bindings already present in the target profile and the existing `spacebar_language_cycle.touch` admission. Its lifecycle hooks are:

- **DOWN:** immediately after `qin.F(MotionEvent,int)`, recognize the original `SoftKeyView` only when its exact `PRESS` action has keycode 62.
- **MOVE:** immediately after `qin.t(MotionEvent,int)`, observe the active pointer and abandon the custom gesture when the cursor controller's side-effect-free `qsl.b()` eligibility predicate is true. This is deliberately conservative: it may decline a swipe while cursor UI is available rather than risk taking over cursor behavior.
- **UP:** immediately before the existing `qin.u(...)` call in private static `qil.t(...)`, resolve the matching pointer session. The runtime consumes only an `ACTION_UP` / `ACTION_POINTER_UP` for the same pointer, a final horizontal displacement that dominates vertical movement, a `PRESS` action, and a state that is neither `LONG_PRESS` nor `DOUBLE_TAP`. A successful native Gboard rotation returns a null `ActionDef`; `qin.u(null,...)` is a stock no-op, so the original bookkeeping and cleanup continue unchanged.
- **Cleanup:** cancel, pointer reset, keyboard teardown, and `qin.A(...)` clear session state.

Language switching uses Gboard's own `pga.F(context).t(true,pfi.d)` operation. The Android system-level IME switch API is not used.

Runtime and patch-shape tests have been added, including a regression test that later vertical movement revokes a previously qualified horizontal movement. **The Gradle build and tests have not been run in this environment, the patched bundle has not been inspected, and the APK has not been tested on the OnePlus 13T.** Treat the branch as an implementation candidate, not as a verified installable build.

## Confirmed next-language implementation

### `pvd` — built-in SwitchLanguage action

The Gboard module `defpackage/pvd.java` handles the internal action:

- Action code `-10011`: `SWITCH_TO_NEXT_LANGUAGE`.
- Action code `-10197`: `SWITCH_TO_NEXT_LANGUAGE_ALLOWING_OTHER_IMES`.

For `-10011`, `pvd.m(ohe)` calls:

```java
pga pgaVarF = pga.F(ac());
if (pgaVarF.t(true, pfi.d)) {
    return;
}
```

The second action calls `t(false, pfi.d)` and may allow switching to another IME. The desired action is therefore `-10011` / `t(true, pfi.d)`, not `switchToNextInputMethod()`.

### `pga` — language entry rotation

`pga.t(boolean, pfi)` is `InputMethodEntryManager.switchToNextInputMethodEntry`. It obtains the current `pev` entry, asks the dynamic rotation list `this.Y` for the next entry, and applies it through `l(pev, pfi)`.

The `pev` entry exposes a language tag through `i()` and a layout-related identifier through `q()`. The static table in `pga` includes `ru -> russian`. This supports that the manager rotates language/layout entries, not merely external IMEs.

### Invocation options

- `pvd` is the existing action handler and is the authoritative behavior for action `-10011`.
- `pvi.a()` also calls `pga.t(true, pfi.e)`, but is a shortcut registration path and uses a different `pfi` source.
- For a patch inside the pointer handler, direct invocation of `pga.F(context).t(true, pfi.d)` is a candidate, but must be checked against the module action's metrics/side effects and lifecycle. Prefer reusing the existing action dispatch if it can be reached safely.

## Confirmed pointer / spacebar path

### `qip` and `qin`

- `qip.b(MotionEvent, int)` creates a `qin` pointer session and stores initial pointer coordinates.
- `qin.B(SoftKeyView, ...)` assigns the active key view to `qin.m`.
- `qin.d()` returns `this.m.getId()` for the active key.
- The spacebar view is identified in the UI by `R.id.key_pos_space`.
- `qin.m()` returns the active `SoftKeyDef`.
- `qin.t(MotionEvent, int)` is the move handler. It measures normalized horizontal/vertical displacement from the initial down point, consults the active key definition, and has a cursor/trackpad guard involving `qsl`.
- `qil` dispatches move events to `qin.t` and on pointer-up classifies the gesture, selects an `ActionDef`, and calls `qin.u(...)`.

### Cursor behavior guard

The move handler checks `this.q` (a `qsl` cursor/trackpad session). If that session is active and its state says the gesture should remain owned by the cursor controller, `qin.t` returns early. This guard must not be bypassed by the new gesture.

The `qin` class also tracks the active key and initial coordinates. A safe implementation should only arm for a pointer whose active view ID is `R.id.key_pos_space`, only after a horizontal threshold is crossed, and only when no cursor/trackpad session has claimed the pointer. It must consume exactly once and reset its state on finish/cancel/reset.

## Existing binding catalog

The current `gboard-version-bindings.json` contains these Gboard 18.5.4 bindings:

- `pointer_owner`: `Lqin;->B(Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;F F J I)V`
- `pointer_finish`: `Lqin;->r(J I)V`
- `pointer_cancel`: `Lqin;->s(J)V`
- `pointer_reset`: `Lqin;->C()V`
- `pointer_pre_reset`: `Lqin;->ac()V`

These identify the pointer lifecycle but are not themselves a completed patch.

## Remaining verification

1. Run the extension and patch-module test suites and fix any compile or test failures.
2. Build the Morphe patch bundle and inspect the emitted DEX to confirm that the new calls are present with valid register operands and preserved exception handlers.
3. Install on the target Gboard build and test: tap, left/right swipe, vertical movement, long press, double tap, cursor/trackpad movement, cancel, additional pointers, and more than two configured languages.

The code currently resolves method targets from generated bindings, extracts registers from the exact invoke instructions, and fails closed when expected call anchors are missing or duplicated. It does **not yet provide a full post-mutation control-flow fingerprint or copied-class transactional verifier**; that remains part of the pre-release verification work.

## Gesture policy

Track the initial down point for a spacebar-owned pointer. Arm only after horizontal displacement exceeds a configurable or conservative threshold and clearly dominates vertical displacement. Direction is ignored. Do not trigger on a tap, on vertical movement, or after cursor/trackpad mode has claimed the pointer. Dispatch one next-language action per gesture. Clear all state on finish, cancel, reset, pointer replacement, and keyboard teardown.


## Detailed touch-dispatch findings (18.5.4)

### `qil` dispatch

- `qil.h(MotionEvent)` is the move dispatcher. For every active pointer owner in `qip.b`, it calls `qin.t(event, pointerIndex)` first.
- After `qin.t`, it checks `qin.M()` and updates coordinates/history. If the active key view changed, or the cursor session does not claim the current coordinates, it resolves a gesture action with `qin.h(...)` and dispatches it through `qin.n(...)`.
- `qil.i(MotionEvent)` handles pointer-up/pointer-index-up. It finds the matching `qin`; if `qin.O(...)` is true it calls the private `qil.t(qin, event, index)` completion routine. Otherwise it calls `qin.A(...)` to end the session.
- The completion routine `qil.t(...)` obtains the active `SoftKeyDef`, classifies the final gesture, resolves an `ActionDef`, and calls `qin.u(...)`. This is where a short space tap remains a normal key action.
- `qil` teardown loops active sessions, calls `qin.r(...)`, then `qin.C()`; pointer cancellation/reset must clear any extension state too.

### `qin.t(MotionEvent, int)` movement behavior

- It starts only when `M()` says there is an active enabled soft key.
- It preserves existing early exits for a key with `g` when `this.t` is false, and for an active cursor session `this.q` whose controller reports that it still owns the gesture.
- It computes normalized displacement from the initial down point: `abs(dx) / keyBounds.width` and `abs(dy) / keyBounds.height`.
- Horizontal and vertical directions are recognized separately through the current `SoftKeyDef` actions. A direction is considered only when its displacement dominates the other axis.
- Once a direction is recognized, `this.H` is set and `F(event,index)` may retarget the active key view. This means the current `qin.m` is not necessarily the original spacebar after the pointer leaves its bounds.

### Implication for a safe spacebar gesture

Do not merely test `qin.d() == R.id.key_pos_space` at pointer-up: after movement, `qin.m` can be retargeted to a neighboring key. The extension must capture the original down/owner view at the start of the pointer session and retain that identity independently.

Do not suppress the stock move handler before it gets a chance to service `this.q`. A safe runtime should:
1. Capture that the pointer began on `R.id.key_pos_space` using the original `SoftKeyView` argument to `qin.B(...)`.
2. Leave all stock cursor/trackpad guards intact.
3. Arm only after a horizontal displacement threshold is exceeded and dominates vertical displacement.
4. Ensure the cursor controller has not claimed the session; if it has, abandon the custom gesture.
5. On pointer finish, dispatch the next-language action once only if armed; otherwise allow the stock completion path (short tap / normal key behavior).
6. Clear state on pointer cancel and reset.

The selected hook is a separate `qil` dispatcher transformation rather than the shared pointer-owner composer: DOWN follows `qin.F`, MOVE follows `qin.t`, and UP is resolved immediately before `qin.u`. This preserves the original spacebar object even if stock movement retargets `qin.m`.

## Patch-framework patterns found in this fork

- Existing pointer-owner transformations are composed through `GboardPointerOwnerTransformationAdapter` and `GboardPointerOwnerFeatureSpec`.
- The top-row swipe patch inserts a runtime delegate into the pointer-owner method and hooks finish/cancel/reset for session cleanup.
- Long-press quick actions also delegates from pointer-owner and finish/cancel/reset lifecycle methods.
- The pointer-owner stock contract and register mapping are centrally validated. A new feature must participate in this composition rather than patching the same method independently.
- Runtime calls are emitted through `RuntimeCallEmitter` and registered in the runtime ABI catalog/inventory. A patch cannot safely call arbitrary Kotlin code unless its runtime artifact is packaged and its ABI entry is declared.

## Current conclusion

The DEX patch and runtime are now committed on the feature branch. The original spacebar identity is retained, pointer IDs are checked on release, and a successful gesture suppresses only the normal action by passing `null` to Gboard's existing `qin.u(...)` method. Long-press and double-tap states are explicitly excluded. Build, bundle inspection, and device validation remain outstanding.

## Extension module integration findings (21 Nov 2026)

The runtime extension is packaged from the Gradle subproject `extensions:extension` (not a top-level `extension` module). Its Android Java sources live under:

`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/`

The existing top-row runtime is:

`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/toprowswipe/GboardTopRowSwipeRuntime.java`

The patch-side ABI declarations are centralized in:

`patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/runtimeabi/RuntimeAbi.kt`

The `RuntimeCallId` enum and `RuntimeAbiCatalog` are in that same file. Runtime ABI packaging is verified against the extension artifact produced by `project(":extensions:extension").tasks.named("syncExtension")`; adding an ABI enum entry alone is insufficient.

The existing pointer-owner delegate pattern is confirmed in:

`patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/toprowswipe/GboardTopRowSwipePointerPatch.kt`

It inserts a runtime call into the shared pointer-owner method and adds finish/cancel/reset cleanup calls. The composer validates the exact method descriptors, runtime ABI signatures, helper calls, lifecycle placement, and normalized transformation state.

The extension runtime uses weak-keyed session maps and defensive exception handling for reflective access. This is a useful implementation convention, but the top-row swipe's owner-retargeting strategy is not directly suitable for the spacebar feature: the spacebar gesture must retain the original pointer-down view while stock cursor movement remains authoritative.

### Resolved implementation decision

The implementation uses a dedicated `qil` patch because the shared pointer-owner composer does not expose all required dispatcher phases. The feature remains independent of the top-row swipe toggle. MOVE observation is conservative around cursor UI; UP interception is anchored at the stock `qin.u(...)` invocation so the native `qin.G(...)` cursor guard and all later cleanup remain in place.

## Exact 18.5.4 move and finish code (JADX confirmation)

The decompiled qil source confirms this ordering:

1. Save the previous active SoftKeyView from qin.m.
2. Call qin.t(event, pointerIndex), which performs stock movement and cursor ownership checks.
3. If qin.M() remains true, update coordinates/history.
4. Continue gesture dispatch only if qin.m changed, qin.q is null, or qin.q.c(x, y, false) says the cursor controller does not own the current coordinates.

This establishes a useful distinction: a non-null qin.q alone does not mean the cursor owns the gesture. The extension must respect the same qsl.c(x, y, false) result used by stock dispatch.

The pointer-up completion routine is private static qil.t(qin, MotionEvent, int). Its relevant order is:

1. Update final pointer coordinates and history.
2. Read the current SoftKeyDef.
3. Call qin.G(event, softKeyDef, pointerIndex, actionIndex); if it claims the event, normal action dispatch is skipped.
4. Otherwise resolve the final gesture and ActionDef.
5. Call qin.u(actionDef, ...), which dispatches the stock key action.
6. Clear gesture flags and finish the pointer session.

Therefore the most precise consume point, if the runtime can be safely attached to this method, is immediately before qin.u(...): a true runtime result should branch around the stock action and continue through the same cleanup path. This preserves normal tap behavior when the runtime returns false. The move-side observer must be placed after the stock cursor ownership decision and must be able to abandon a session if the cursor controller owns it.

### Important correction to the previous hook proposal

The existing pointer-owner lifecycle composer does not expose the move dispatcher or private qil.t(qin, MotionEvent, int) completion method. A correct implementation likely requires a second, separately preflighted transformation for the 18.5.4 qil dispatcher, composed with the existing pointer-owner transformation. Do not force this into the pointer-owner adapter by guessing register positions.

### Patch-side file discovery

The previously missed files do exist; their exact paths are:

- patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/toprowswipe/GboardTopRowSwipePointerPatch.kt
- patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/zhuyinslide/GboardZhuyinSlidePointerAnchorPatch.kt
- patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/GboardPointerOwnerTransformation.kt
- patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/runtimeabi/RuntimeAbi.kt
- patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/runtimeabi/RuntimeCallEmitter.kt

The Zhuyin slide patch is a useful example of capturing the original pointer anchor before stock retargeting. Its behavior suppresses retargeting and is not suitable for this feature as-is.

## Further exact findings from the supplied JADX source

### Move dispatcher `qil.h(MotionEvent)`

The actual 18.5.4 decompilation confirms the cursor decision point more precisely:

- `qin.v(event)` first adjusts coordinates for raw offset changes.
- `qin.t(event, pointerIndex)` runs stock key movement. Inside it, if `qin.q != null`, the stock code returns early only when `qsl.d()` is true and the cursor controller's `tkd.h()` is false.
- After that, `qil.h` updates coordinates/history.
- Before resolving and dispatching the normal gesture action, `qil.h` evaluates `qin.m != previousSoftKeyView || qin.q == null || !qin.q.c(x, y, false)`.
- Only when that condition is true does it call `qin.h(...)`, `qin.j(...)`, and `qin.n(...)`.

Thus a safe observer should be placed after the condition's cursor-ownership test, not before `qin.t`, and should abandon the candidate gesture whenever the cursor owns the pointer. The check must use the same qsl.c(x, y, false) outcome, rather than simply testing qin.q for null.

### Pointer-up completion `qil.t(qin, MotionEvent, int)`

The full method confirms that `qin.G(event, softKeyDef, pointerIndex, actionIndex)` executes before normal action dispatch. If it returns true, the normal action path is skipped. Otherwise, the method calculates the final gesture, resolves an ActionDef, and calls `qin.u(...)`. Subsequent long-press/double-tap cleanup and `qin.A(...)` run in the same completion routine.

A robust consume hook can therefore be inserted immediately before the `qin.u(...)` invocation, with a conditional branch to the existing post-action cleanup block. The runtime must return false for non-spacebar sessions, taps, vertical gestures, and cursor-owned sessions. It should return true exactly once for an armed horizontal spacebar swipe, invoke Gboard's internal next-language action, and let the original cleanup continue.

### Original key identity

`qin.B(SoftKeyView, float, float, long, int)` receives the newly assigned view before any movement retargeting. It also calls `r(...)` and `ac()` to finish/reset prior session state before storing the new view in `qin.m`. This is the correct place to capture the original view identity for a pointer session. The runtime must not rely on `qin.m` at pointer-up because `qin.F(...)` can retarget it during movement.

### Implementation boundary

The current patch is anchored to generated method bindings and resolves argument registers from the exact `qin.F`, `qin.t`, and `qin.u` invoke instructions rather than relying on hard-coded register numbers. It leaves the original `qil.t` try/catch and post-action control flow intact because it replaces the ActionDef argument instead of inserting a branch around the action call. The patch does not yet perform a full post-mutation control-flow fingerprint or copied-class transactional verification; those checks, plus a real build and device test, remain outstanding.



## Historical 18.5.4 method-target research (pre-implementation notes)

The supplied decompilation identifies these exact source-level signatures (DEX access flags and code-item shapes still need to be verified from the APK):

| Role | Source-level target | Required verification |
|---|---|---|
| Capture original spacebar view | `Lqin;->B(Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;F F J I)V` | Confirm the existing pointer-owner binding and insertion point before stock retargeting |
| Observe movement | `Lqil;->h(Landroid/view/MotionEvent;)V` | Public/final status, register count, exact qsl ownership branch, and insertion point after that branch |
| Consume completion | `Lqil;->t(Lqin;Landroid/view/MotionEvent;I)V` | Private/static status, register count, exact `qin.u(...)` call site, branch target after action dispatch, and try/catch coverage |

The move method is a dispatcher over active `qin` sessions, so its loop registers cannot be assumed to match the pointer-owner method's register contract. The completion method is private static and is not exposed by the current pointer-owner transformation context. Both require independent shape verifiers and transactional preflight before mutation.

### Verification status for the implementation above

The method bindings, runtime ABI entries, dedicated `qil` patch, and patch-shape tests are now present in the feature branch. The original `qin.u(...)` call is retained and receives a null ActionDef only when the runtime confirms the gesture and native language rotation succeeds. The next verification steps are to compile/test the modules, inspect the resulting DEX, and test on-device; no successful build or device result is claimed yet.


## Target APK DEX inspection (directly read from supplied APK)

The APK was inspected as a DEX container rather than relying only on JADX's Java reconstruction. The relevant definitions are present in these DEX files:

| Method | DEX | Access flags | Registers | Incoming words | Outgoing words | Try blocks |
|---|---|---:|---:|---:|---:|---:|
| `qin.B(SoftKeyView,float,float,long,int)` | `classes2.dex` | `0x10` (final) | 21 | 7 | 14 | 0 |
| `qil.h(MotionEvent)` | `classes.dex` | `0x11` (public, final) | 12 | 2 | 8 | 2 |
| `qil.t(qin,MotionEvent,int)` | `classes.dex` | `0x0a` (private, static) | 16 | 3 | 10 | 1 |

### Verified DEX call sites

- `qil.h` calls `qin.t(MotionEvent,int)` at code-unit offset `0x2b`.
- The same method calls `qsl.c(float,float,boolean)` at `0x64`; this is the stock cursor-ownership predicate. The custom move observer must be inserted only after the associated result/branch has been evaluated, not immediately after the invocation.
- `qil.t` calls `qin.G(...)` before resolving the normal action. The normal action dispatch is the `invoke-virtual/range` to `qin.u(...)` at code-unit offset `0x73`. It uses 10 register words beginning at `v3` (including the receiver). The next instruction begins at `0x76`, where the stock completion path continues.
- `qin.B` calls `qin.r(J,I)` at `0x0c` and `qin.ac()` at `0x0f` before storing the new key view. The view argument is still available as the incoming parameter when the original pointer owner is established.

### What this establishes — and what it does not

The target APK confirms the descriptors, access flags, code-item register counts, try-block counts, and the key call-site offsets above. In particular, it confirms that the completion hook can be anchored immediately before the stock `qin.u` invocation and that the move observer has to account for the stock cursor predicate.

This is not yet a safe executable transformation. Before emitting instructions, the patch must still resolve the exact field/method references and all branch targets around the cursor predicate and the `qin.u` call, including the `qil.t` try/catch region. The current project binding catalog does not yet declare the `qil.h` and `qil.t` targets. The next code change should add these exact bindings and fail-closed semantic shape checks, then test the candidate mutation against a copied class before enabling runtime calls.
