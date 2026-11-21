# Spacebar language cycle — 18.5.4 DEX preflight

Target branch: `feature/spacebar-language-cycle`  
Target APK: `Google Gboard 18.5.4.993608717-release-arm64-v8a`

This document records the bytecode facts verified against the supplied APK. It is a preflight artifact, **not an executable patch**.

## Exact method bindings

| Role | DEX method | Access flags | Registers | Incoming words | Outgoing words | Try blocks |
|---|---|---:|---:|---:|---:|---:|
| Begin pointer dispatch | `Lqil;->c(Landroid/view/MotionEvent;Z)Lqin;` | public final | verified from matching source |
| Observe move | `Lqil;->h(Landroid/view/MotionEvent;)V` | `0x11` public final | 12 | 2 | 8 | 2 |
| Consume pointer-up action | `Lqil;->t(Lqin;Landroid/view/MotionEvent;I)V` | `0x0a` private static | 16 | 3 | 10 | 1 |

## Verified call-site anchors

- `qil.c(MotionEvent,boolean)` is the down-dispatch method in `classes.dex`. It obtains the `qin` pointer owner, writes the initial coordinates, calls `qin.F(MotionEvent,int)` for hit testing, and then arms the initial action through `qin.n(...)`. The safe begin hook is after `qin.F` and before `qin.n`, guarded by `qin.e() == 62`; do not use `qin.B` as a down hook because it is also reached with a null view during reset.
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
- [ ] Add generated version bindings for `qil.c`, `qil.h`, and `qil.t`.
- [ ] Implement fail-closed semantic shape verifiers and candidate-class preflight.
- [ ] Add runtime gesture state and ABI calls.
- [ ] Build and inspect the patched bundle.
- [ ] Test on the OnePlus 13T (not yet tested).

No working patch is claimed until the remaining steps are complete.


## APK verification: 18.5.4.993608717 arm64

Verified against the uploaded APK (SHA-256
`0bbc448e570f4042692c850f8b5a19458e009d245c5f099bff3c0415574c2402`).
The archive contains four DEX files; the relevant `qil` and `qin` classes
are in `classes.dex`.

Source-level dispatch boundaries confirmed against the matching decompiled
sources supplied with the APK:

- `qil.c(MotionEvent, boolean): qin` is the touch-down path. It obtains the
  pointer owner, records current coordinates/pressure, calls
  `qin.F(MotionEvent, int)` to resolve the target `SoftKeyView`, then calls
  `qin.n(...)` to arm the initial action.
- `qin.F` calls `qin.B(SoftKeyView, float, float, long, int)` after hit
  testing. `B` is therefore a target-retarget/reset routine, not a reliable
  "new pointer down" signal: it is also reached from `qin.ab()` with a null
  view during lifecycle reset. A swipe session must not be armed unconditionally
  from `B`.
- `qin.e()` returns the current primary action keycode; space is keycode 62.
  This is a stronger semantic discriminator than matching a view label.
- `qil.h(MotionEvent)` is the move path. It calls `qin.t(MotionEvent,int)`
  before updating the current coordinates. Its cursor ownership gate is the
  combined condition: active view unchanged, no `qsl` session, or
  `qsl.c(x,y,false)` returns false. Movement observation must occur only on
  the normal pointer-owned side of that gate.
- `qil.t(qin, MotionEvent, int)` is the action-up path. It first checks
  `qin.m()` and then `qin.G(...)`; only when the trackpad has not consumed
  the event does it calculate and dispatch the normal action through
  `qin.u(...)`. A successful spacebar swipe must suppress only that normal
  action dispatch and retain the remainder of the cleanup and final
  `qin.A(...)` call.
- `qin.C()` is a reset path; `qin.s(long)` resets cursor/trackpad state.
  Session cleanup must cover cancellation, reset, pointer replacement and
  owner teardown, not only the ordinary up path.

Important implementation constraint: the source-level findings above identify
semantic insertion points, but do not by themselves authorize guessed DEX
register numbers or instruction offsets. The patch must resolve the exact
method prototypes and register operands from the mutable DEX model, then
preflight the complete transformed methods before committing any mutation.
The feature must remain unregistered/unpublished until down, move, up and
lifecycle paths are wired and the structural verification tests pass.

## Generated target bindings and admission gate

The repository's binding pipeline was inspected before adding any target
entries. The source of truth is not the generated Kotlin file alone:

1. `patches/src/main/resources/gboard/gboard-product-catalog.json`
   declares the complete feature/contribution inventory and the required
   binding keys for each contribution.
2. `gboard-port-bundle.json` (when supplied to the build) admits a reviewed
   subset of those contributions and exports evidence for every required
   binding key.
3. `gboard-version-bindings.json` must contain exactly the binding-key set
   required by the selected admission. Extra keys are rejected just like
   missing keys.
4. `TargetBindingCompiler` verifies the profile and computes its canonical
   source digest. `GboardTargetBuildCompiler` verifies the PortBundle digest,
   catalog digest, profile/projection linkage, target version and profile
   version before generating `GboardVersionBindings.kt` and
   `GboardTargetAdmission.kt`.

Consequently, adding `qil.c`, `qil.h`, and `qil.t` as arbitrary JSON
properties is not a safe intermediate step. The product catalog, admitted
contribution definitions, PortBundle binding exports, reviewed binding
profile, and their linked SHA-256 values must be updated as one coherent
reviewed target change. The profile's `projection_sha256` is checked against
the compiler's canonical projection, while `profile_sha256` and
`port_bundle_sha256` pin the external review artifacts.

The implementation should therefore add a distinct touch-dispatch contribution
for the spacebar gesture, with explicit required bindings for the three
`qil` methods and the exact dependencies/runtime calls it needs. Its
admission must remain pending until the matching APK evidence has been
reviewed. The generated admission should not expose the feature until the
down, move, up and lifecycle transformations are all present and validated.

This is a build-pipeline constraint, not a claim that the bindings have been
added. No binding profile, catalog, PortBundle or generated source was changed
as part of this inspection.
