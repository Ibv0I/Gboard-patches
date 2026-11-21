# Spacebar language-cycle gesture — implementation notes

Target Gboard: `com.google.android.inputmethod.latin`
Verified target APK: `18.5.4.993608717-release-arm64-v8a`

## Requested behavior

- A horizontal swipe that starts on the space bar advances to the next enabled keyboard language/layout.
- The swipe direction does not select a fixed language. It always invokes the next-language action, cycling through the configured layouts.
- A normal short tap still inserts a space.
- Existing long-press spacebar cursor/trackpad behavior must remain available.
- Only a horizontal gesture past a movement threshold is consumed.

## Current status

This branch is an implementation work branch. The gesture behavior is specified, but the APK-specific bytecode target and the actual Gboard next-language method still need to be identified from the supplied APK before a runtime patch is safe to implement.

Do not merge or ship a no-op/placeholder implementation as a working patch.

## Investigation checklist

1. Decompile all DEX files for the exact target APK and inspect the spacebar soft-key / pointer-owner / gesture dispatch paths.
2. Find Gboard's internal action for advancing to the next input subtype/layout (not a hard-coded RU or EN target).
3. Trace how the existing cursor trackpad gesture claims and cancels a spacebar pointer session; make sure horizontal swipes can be distinguished without breaking long-press editing.
4. Add version bindings and an exact, verified bytecode transformation.
5. Add a runtime extension that recognizes a horizontal swipe on the spacebar, calls Gboard's next-layout action once per completed swipe, and safely resets/cancels the session.
6. Add patch metadata, settings (enable/disable and threshold if practical), and source-contract/runtime tests.
7. Build the patch bundle against the target APK and test on-device.

## Gesture policy

The runtime should track the initial down coordinates and elapsed time for a spacebar-owned pointer session. It should arm only after horizontal displacement exceeds the configured threshold and dominates vertical displacement. It must not claim a simple tap. It must not arm a long-press cursor-trackpad session. Upon completion, it should dispatch exactly one "next input language/layout" action irrespective of swipe direction, then clear all session state. Cancellation, pointer reset, window detach, and keyboard teardown must also clear state.


## Findings from the repository's 18.5.4 binding catalog

The current `gboard-version-bindings.json` already contains these verified pointer-owner targets for Gboard 18.5.4:

- `pointer_owner`: `Lqin;->B(Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;F F J I)V`
- `pointer_finish`: `Lqin;->r(J I)V`
- `pointer_cancel`: `Lqin;->s(J)V`
- `pointer_reset`: `Lqin;->C()V`
- `pointer_pre_reset`: `Lqin;->ac()V`

The catalog also has `latin_globe_factory` (`LatinIme->N()`) but does not currently expose a verified spacebar-specific next-layout action. These pointer methods are promising interception points, not proof that a completed horizontal swipe can safely consume the spacebar session.

## APK analysis status

The supplied APK was inspected as an archive and contains `classes.dex` through `classes4.dex`. The current working environment has Java 21 but no JADX, apktool, baksmali, or dexdump executable, and cannot download release binaries. Therefore the DEX methods have not yet been decompiled here. Do not infer the next-language method from the pointer bindings alone.

For local analysis on Windows, download the JADX command-line ZIP from the official releases page:
https://github.com/skylot/jadx/releases/latest

Then run (adjust the JADX folder path if needed):

```powershell
& "C:\Tools\jadx\bin\jadx.bat" -d "$env:USERPROFILE\Desktop\gboard-src" "C:\path\to\Google+Gboard+18.5.4.993608717-release-arm64-v8a.apk"
```

After decompilation, search the output for `switchToNextInputMethod`, `setInputMethodAndSubtype`, `InputMethodManager`, `spacebar`, `pointer_owner`, `cursor`, and `LatinIme`. The most useful output to provide is the decompiled classes around `Lqin` (pointer owner) and the class that handles globe/language switching. If JADX cannot decompile a method, provide its corresponding smali instead.
