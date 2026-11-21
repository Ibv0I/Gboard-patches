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

## Lifecycle path audit (matching source archive)

The lifecycle source audit adds the following exact cleanup points to the earlier method map:

- `qil.i(MotionEvent)` resolves the pointer owner by pointer ID. If `qin.O(...)` rejects the pointer-up/cancel event, it calls `qin.A(...)` instead of the ordinary `t(qin,...)` completion path. The touch-dispatch patch must clear the swipe session on this branch; otherwise a cancelled gesture could remain armed.
- `qil.j(qin)` removes an owner from the active-owner collection, calls `qin.close()`, and returns the owner to the pool. Any extension state keyed by that owner must be cleared before it can be reused.
- `qin.C()` calls `qin.s(0L)` and then `ac()`; `qin.s(long)` resets the native cursor/trackpad controller and the trackpad-active flag. The extension clear must be attached to the reset lifecycle, not to `qin.B`.
- `qin.ab()` calls `B(null,...)`, and `B` runs `ac()`. This confirms why `B` cannot serve as a begin hook: it is also part of reset/retarget behavior and can receive a null view.

For the down hook, capture the actual `qin.m` view only after `qin.F(MotionEvent,int)` has completed hit testing, and only if `qin.e() == 62`. Keep that captured reference for the later finish call; do not substitute the potentially retargeted `qin.m` at pointer-up.

For move, insert the observer only in the normal pointer-owned branch after the complete condition
`qin.m != previousView || qin.q == null || !qsl.c(x,y,false)`
has been evaluated. The extension's `cursorOwnsPointer` argument must represent the negation of that complete condition's normal-ownership decision, not merely whether `qin.q` is non-null.

For up, keep the `qin.G(...)` trackpad decision untouched. Only after it returns false may the extension finish run. If finish returns true, branch over exactly the ordinary `qin.u(...)` invocation while retaining the following stock action bookkeeping, delayed-action handling, trace-finally path, and final owner cleanup. The cancel path in `qil.i` must clear rather than finish.

These are source-confirmed lifecycle requirements. They do not yet constitute verified DEX instruction indices/register operands, so the feature remains disabled pending instruction-level decoding and candidate verification.

## Clarified control-flow semantics from the complete decompiled methods

The full source bodies were re-read against the APK-derived source archive. The following details are important when translating the source map into instruction-level hooks:

- `qil.c(MotionEvent, boolean)` invokes `qin.F(event, pointerIndex)`, then obtains the action through `qin.l()`, and then invokes `qin.n(...)`. The spacebar check must therefore use the resolved `qin.e()` key code after `F`; the original `qin.m` view must be captured before subsequent move retargeting.
- `qil.h(MotionEvent)` first invokes `qin.v(event)`, resolves the pointer index, invokes `qin.t(event,index)`, and then checks `qin.M()`. Only inside that gate does it evaluate the cursor/retarget condition. The observer must not run on the early-return paths from `qin.t`, nor when `qin.M()` is false.
- The condition body is entered when `qin.m != previousSoftKeyView || qin.q == null || !qin.q.c(x,y,false)`. Therefore the extension observer belongs only on the taken/body path. The fall-through path is the path where the same view remains under the pointer and the cursor controller reports that it owns the pointer. Pass the runtime a cursor-owned flag consistent with that branch outcome.
- `qil.i(MotionEvent)` distinguishes a valid pointer completion using `qin.O(event,index)`. When it returns false, the method calls `qin.A(eventTime,deviceId)` directly and never enters the normal `t(qin,...)` completion. This branch must clear the session; it must not attempt a language switch.
- `qil.j(qin)` closes and pools the owner only when it is removed from the active set. Clear extension state before `qin.close()`/pool return, so a pooled `qin` object cannot inherit a stale gesture.
- `qil.m()` resets each active owner through `qin.C()` and then removes it. It is an additional owner-wide teardown path; cleanup must be idempotent because it can overlap the per-owner reset path.

No bytecode was changed in this audit. It narrows the control-flow predicates that the upcoming instruction-level composer must verify before mutating a candidate class.

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

## APK DEX code-item inventory (18.5.4 arm64 target)

The supplied APK was unpacked and the DEX header/class-data/method-id tables were parsed locally. This establishes concrete code-item locations and register/insn counts for the target methods; it is not yet a full instruction disassembly or a mutation anchor.

APK SHA-256: `0bbc448e570f4042692c850f8b5a19458e009d245c5f099bff3c0415574c2402`.

| DEX | Class/method | Prototype | Code item offset | Registers | Insn code units |
|---|---|---|---:|---:|---:|
| classes.dex | `Lqil;->c` | `(MotionEvent,Z)qin` | `0x5209b4` | 12 | 135 |
| classes.dex | `Lqil;->h` | `(MotionEvent)V` | `0x520c54` | 12 | 347 |
| classes.dex | `Lqil;->i` | `(MotionEvent)V` | `0x520f30` | 7 | 48 |
| classes.dex | `Lqil;->j` | `(qin)V` | `0x520ff0` | 3 | 19 |
| classes.dex | `Lqil;->t` | `(qin,MotionEvent,I)V` | `0x521088` | 16 | 275 |
| classes.dex | `Lqil;->m` | `()V` | `0x34966c` | 7 | 43 |
| classes2.dex | `Lqin;->A` | `(J,I)V` | `0x5eb01c` | 5 | 28 |
| classes2.dex | `Lqin;->C` | `()V` | `0x5eb164` | 3 | 9 |
| classes2.dex | `Lqin;->close` | `()V` | `0x5ea8f4` | 3 | 20 |
| classes2.dex | `Lqin;->s` | `(J)V` | `0x5eaa08` | 4 | 16 |
| classes2.dex | `Lqin;->t` | `(MotionEvent,I)V` | `0x5eaa38` | 13 | 235 |
| classes2.dex | `Lqin;->v` | `(MotionEvent)V` | `0x5eaca4` | 6 | 55 |

Important: offsets above are absolute offsets of DEX code_item structures, not instruction offsets. The table confirms that `qil` and `qin` are in different DEX files and prevents accidentally resolving the right obfuscated class from the wrong DEX. Register counts and code-unit lengths are verified from the code_item headers. Before mutation, the composer still needs decoded instruction operands, branch targets, try/catch boundaries, and exact method-reference checks from these code items.


## Decoded DEX invoke anchors and register words

The instruction streams in the code items above were decoded to identify invoke instructions and their argument registers. Offsets below are code-unit offsets relative to the method's instruction array (not byte offsets in the APK). Register lists are in invoke argument-word order, including the receiver for virtual invokes.

### `Lqil;` in `classes.dex`

| Method | Offset | Invoke target | Registers |
|---|---:|---|---|
| `c(MotionEvent,Z)` | 17 | `Lqip;->b(MotionEvent,I)qin` | v9, v10, v0 |
| | 44 | `Lqin;->F(MotionEvent,I)V` | v1, v10, v0 |
| | 47 | `Lqin;->l()ActionDef` | v1 |
| | 120 | `Lqil;->t(qin,MotionEvent,I)V` | v2, v10, v0 |
| `h(MotionEvent)` | 26 | `Lqin;->v(MotionEvent)V` | v1, v11 |
| | 43 | `Lqin;->t(MotionEvent,I)V` | v1, v11, v0 |
| | 46 | `Lqin;->M()Z` | v1 |
| | 100 | `Lqsl;->c(FFZ)Z` | v2, v3, v4, v9 |
| `i(MotionEvent)` | 14 | `Lqip;->a(I)qin` | v5, v2 |
| | 20 | `Lqin;->O(MotionEvent,I)Z` | v2, v6, v0 |
| | 26 | `Lqil;->t(qin,MotionEvent,I)V` | v2, v6, v0 |
| | 38 | `Lqin;->A(J,I)V` | v2, v3, v4, v6 |
| `j(qin)` | 10 | `Lqin;->close()V` | v2 |
| `t(qin,MotionEvent,I)` | 50 | `Lqin;->m()SoftKeyDef` | v13 |
| | 56 | `Lqin;->G(MotionEvent,SoftKeyDef,I,I)Z` | v13, v14, v1, v0, v15 |
| | 115 | `Lqin;->u(ActionDef,SoftKeyDef,J, Z,Z,J,I)V` | v3..v12 |
| `m()` | 32 | `Lqin;->C()V` | v1 |

### `Lqin;` in `classes2.dex`

| Method | Offset | Invoke target | Registers |
|---|---:|---|---|
| `C()` | 2 | `Lqin;->s(J)V` | v0, v1, v2 |
| | 5 | `Lqin;->ac()V` | v0 |
| `close()` | 0 | `Lqin;->C()V` | v0 |
| `s(J)` | 4 | `Lqsl;->a(J)V` | v0, v1, v2 |
| | 9 | `Lqio;->k()V` | v0 |
| `A(J,I)` | 2 | `Lqio;->p(qin)V` | v1, v0 |
| | 5 | `Lqin;->r(J,I)V` | v0..v3 |
| | 8 | `Lqin;->ac()V` | v0 |

These anchors show that the UP completion call at `qil.t` is at instruction offset 56, and the normal action dispatch is at offset 115. They also show the reset and pooling call sites. They do **not** by themselves authorize insertion: the implementation must still decode and validate all branch targets and try/catch ranges, preserve the stock trackpad branch, and reserve scratch registers safely. In particular, the invoke at `qil.t:56` is the native `G` gate, not the action dispatch; the extension's finish decision must be placed only on its false-return path, before the `qin.u` dispatch at offset 115.



## Verified control-flow and exception ranges (18.5.4 APK)

The following offsets are code-unit offsets in the original instruction array. Conditional and unconditional branch targets below were decoded from the DEX instruction operands, not inferred from decompiled Java.

### Completion and cancellation

- `qil.i(MotionEvent)`:
  - offset 18 branches to 41 when the pointer owner lookup returns null.
  - offset 20 calls `qin.O(MotionEvent,int)`.
  - offset 24 branches to 30 when `O` is false.
  - offset 26 calls `qil.t(qin,MotionEvent,int)` only when `O` is true.
  - offset 29 jumps to 41 after normal completion.
  - offset 30 onward is the alternate `qin.A(long,int)` path; this is the cancellation/rejection path and must clear, never finish, a swipe session.
  - offset 42 branches to 47 for the action-masked check; offset 44 calls `qip.c()` on the matching branch.
  - No try items are present in this method.
- `qil.j(qin)`:
  - offset 8 branches to 18 when the owner is not found in the active collection.
  - offset 10 calls `qin.close()` on the removal path.
  - No try items are present in this method.

### UP handler: `qil.t(qin,MotionEvent,int)`

- offset 5 begins the owner's event update; the method has one try item covering code units [5,254), with a catch-all handler at 269.
- offset 14 branches to 19 if the pointer index is valid; offset 17 otherwise jumps to 254, the trace-finally path.
- offset 50 reads the current `SoftKeyDef`; offset 54 branches back to 16 when it is null.
- offset 56 invokes `qin.G(MotionEvent,SoftKeyDef,int,int)`.
- offset 60 branches back to 16 when `G` returns true. This is the native trackpad/cursor-owned path and must remain untouched.
- When `G` returns false, execution falls through at 62 into the normal action-resolution path. The extension finish decision belongs on this false-return path, before the normal `qin.u(...)` action dispatch at offset 115.
- A successful extension finish may skip only the stock `qin.u(...)` dispatch. It must rejoin the existing bookkeeping/delayed-action/trace cleanup flow; it must not bypass the catch-all handler or trace end.
- The final trace end is at offset 254; the normal return is at 268. The catch-all handler begins at 269 and rethrows after ending the trace.

### DOWN and MOVE handler exception ranges

- `qil.c(MotionEvent,boolean)` has one try item covering [26,86), with catch-all handler at 129.
- `qil.h(MotionEvent)` has two try items: [26,35) and [41,338), both using the catch-all handler at 340.
- `qil.i` and `qil.j` have no try items.
- Do not insert instructions inside protected ranges without updating their try ranges and handler offsets. Any insertion must also preserve branch targets and the original handler/finally structure.

### Reset and owner reuse anchors

- `qil.m()` invokes `qin.C()` at offset 32.
- `qin.close()` invokes `qin.C()` at offset 0.
- `qin.C()` invokes `qin.s(long)` at offset 2 and `qin.ac()` at offset 5.
- `qin.s(long)` invokes `qsl.a(long)` at offset 4 and `qio.k()` at offset 9.
- `qin.A(long,int)` invokes `qio.p(qin)` at offset 2, `qin.r(long,int)` at offset 5, and `qin.ac()` at offset 8.

These facts establish semantic insertion regions but are not yet a transformation specification: register read/write liveness, exact branch conditions, try-handler relocation, and post-transform structural fingerprints must still be validated before bytecode mutation is enabled.
