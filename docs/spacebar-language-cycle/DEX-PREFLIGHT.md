# Spacebar language cycle — 18.5.4 DEX preflight

Target branch: `feature/spacebar-language-cycle`  
Target APK: `Google Gboard 18.5.4.993608717-release-arm64-v8a`

This document records the bytecode facts verified against the supplied APK. It is a preflight artifact, **not an executable patch**.

## Exact method bindings

| Role | DEX method | Access flags | Registers | Incoming words | Outgoing words | Try blocks |
|---|---|---:|---:|---:|---:|---:|
| Capture original down-view | `Lqin;->B(Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;FFJI)V` | `0x10` final | 21 | 7 | 14 | 0 |
| Observe move | `Lqil;->h(Landroid/view/MotionEvent;)V` | `0x11` public final | 12 | 2 | 8 | 2 |
| Consume pointer-up action | `Lqil;->t(Lqin;Landroid/view/MotionEvent;I)V` | `0x0a` private static | 16 | 3 | 10 | 1 |

## Verified call-site anchors

- `qin.B` is in `classes2.dex`. It calls `qin.r(JI)` at code-unit offset `0x0c`, then `qin.ac()` at `0x0f`, before the new SoftKeyView is stored. The original view parameter is therefore available at pointer-session creation, before stock retargeting.
- `qil.h` is in `classes.dex`. It calls `qin.t(MotionEvent,int)` at `0x2b`. The stock cursor ownership predicate `qsl.c(float,float,boolean)` is called at `0x64`. A custom observer must be placed after the predicate's branch has been evaluated, not merely after the invoke.
- `qil.t` is in `classes.dex`. The normal action call `qin.u(...)` is an `invoke-virtual/range` at code-unit offset `0x73`, using ten register words beginning at `v3`. The next instruction starts at `0x76` and continues the stock completion path.

## Source archive confirmation and native dispatch path

The uploaded `sources.zip` contains the complete `sources/defpackage/qil.java`
(18,940 bytes). The relevant Java control flow confirms:

- `qil.h(MotionEvent)` first runs `qin.t(MotionEvent,index)`, then evaluates
  `qin.m != previousSoftKeyView || qin.q == null || !qsl.c(x,y,false)`.
  The custom observer belongs only inside the body guarded by that condition.
- `qil.t(qin,MotionEvent,index)` leaves `qin.G(...)` intact and invokes the
  ordinary action via `qin.u(...)`; only that action dispatch may be skipped.
- `qin.r` is the `qio` dispatcher (`qil`), and `qil.b` is its application
  `Context`.

The source archive also confirms the native language-rotation operation:
`pga.F(Context).t(true,pfi.d)`. This is the same manager operation reached by
`pvd.m(ohe)` for action code `-10011`. It is preferable to Android's
system-level IME switching. The runtime now attempts this native operation
reflectively and returns false on failure, preserving the ordinary space action.

## Behavior contract

1. Capture the original pointer-down SoftKeyView. Do not infer the origin from `qin.m` at pointer-up: stock movement can retarget it.
2. Leave `qin.t` and its cursor/trackpad checks intact.
3. Observe movement only after the stock cursor ownership decision. If the cursor controller owns the pointer, abandon the custom gesture.
4. Arm only when horizontal displacement exceeds the threshold and dominates vertical displacement.
5. Either horizontal direction advances to the next Gboard language/layout.
6. A short tap remains a normal space action.
7. On a qualifying release, dispatch exactly one Gboard internal next-language action and skip only the stock `qin.u` action. Continue the original cleanup path.
8. Cancel, reset, pointer replacement, and teardown clear all extension state.

## Internal language-switch action

The APK's internal action dispatcher `pvd.m(ohe)` recognizes action code `-10011` as `SWITCH_TO_NEXT_LANGUAGE`. It reaches `pga.t(true, pfi.d)`, which rotates Gboard's active language/layout list. This is the intended operation; using Android's system-level next-input-method API would not implement the requested Gboard layout cycle.

## Required safeguards before enabling a bytecode mutation

The offsets above are code-unit anchors, not permission to insert instructions blindly. The implementation must still resolve and assert the exact instruction objects, branch targets, field/method references, and try/catch boundaries in the target class. It must mutate a copied candidate class, verify the candidate, and commit only after all checks pass.

In particular, the `qil.h` observer must be inserted after the actual cursor-ownership branch, and the `qil.t` consume branch must jump to the existing post-`qin.u` cleanup block without crossing or changing try/catch coverage.

## Current status

- [x] Target method descriptors and DEX locations recorded.
- [x] Access flags, register counts, incoming/outgoing words, and try-block counts verified.
- [x] Cursor predicate and stock action call-site offsets recorded.
- [x] Gboard's internal next-language action identified.
- [ ] Add generated version bindings for `qil.h` and `qil.t`.
- [ ] Implement fail-closed semantic shape verifiers and candidate-class preflight.
- [ ] Add runtime gesture state and ABI calls.
- [ ] Build and inspect the patched bundle.
- [ ] Test on the OnePlus 13T (not yet tested).

No working patch is claimed until the remaining steps are complete.
