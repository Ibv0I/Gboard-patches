# Spacebar language-cycle gesture — implementation notes

Target Gboard: `com.google.android.inputmethod.latin`
Verified target APK: `18.5.4.993608717-release-arm64-v8a`

## Requested behavior

- A horizontal swipe that starts on the space bar advances to the next enabled Gboard language/layout.
- Swipe direction is ignored; either direction invokes the same next-language action.
- A normal short tap still inserts a space.
- Existing long-press spacebar cursor/trackpad behavior must remain available.
- Only a horizontal gesture past a movement threshold is consumed.

## Current status

The supplied JADX `sources.zip` has been extracted and inspected. The correct built-in Gboard language-switch path has been identified. The gesture interception point is narrowed to the `qin` pointer session, but no runtime patch has been implemented or built yet.

Do not ship this as a working patch until the exact DEX transformation is implemented, bundle validation passes, and the target APK is tested on-device.

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

## Next implementation checks

1. Inspect the complete `qil` touch dispatch sequence and the exact `qin` pointer-up/cancel lifecycle.
2. Confirm how the `SoftKeyView` ID is preserved when the pointer moves outside the spacebar.
3. Determine whether horizontal cursor movement on the spacebar uses the same `qsl` session, and ensure the new gesture never takes ownership after that session starts.
4. Inspect the patch framework's smali transformation conventions and version-binding schema.
5. Implement the smallest bytecode patch for the exact 18.5.4 target. Avoid patching `pad.java`; it is the Smart Compose swipe extension, not the language switch handler.
6. Build and validate the patch bundle, then test tap, left/right swipe, long-press cursor movement, pointer cancellation, and layouts with more than two languages on-device.

## Gesture policy

Track the initial down point for a spacebar-owned pointer. Arm only after horizontal displacement exceeds a configurable or conservative threshold and clearly dominates vertical displacement. Direction is ignored. Do not trigger on a tap, on vertical movement, or after cursor/trackpad mode has claimed the pointer. Dispatch one next-language action per gesture. Clear all state on finish, cancel, reset, pointer replacement, and keyboard teardown.
