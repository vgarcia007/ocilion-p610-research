# P610 Key Remap (keyremap)

Minimal Android app that remaps R601 remote "global" buttons
(category A – see [../../docs/remote.md](../../docs/remote.md) §5)
to arbitrary apps. Built & tested on 2026-11-13 against
`C4.0.7_20260605`.

## Status on the current (unrooted) box

**Does not work for category A buttons.** The
`android.intent.action.GLOBAL_BUTTON` broadcast is permission-filtered
to system uid (the `com.google.android.permission.GLOBAL_BUTTON`
receiver permission is undefined on the device, so AOSP only grants
it to `SYSTEM_UID`), and the key events never reach window dispatch or
accessibility. Both a manifest receiver and an accessibility service
(with `flagRequestFilterKeyEvents`) were tested: zero deliveries for
keyCodes 265/266/138/267.

**Use case:** this is the ready implementation for a *rooted* P610 –
after root, either (a) grant the app `android.uid.system` via the
manifest `sharedUserId` + platform signature, or (b) simply replace
the targets in the decompiled
`/system/priv-app/SdtGlobalKeyHandler/SdtGlobalKeyHandler.apk`
(`com.sdt.globalkey`) with the packages you want. See
[../../docs/rooting.md](../../docs/rooting.md).

The color keys (category B, keyCodes 398–401) do NOT need this app –
any app/accessibility remapper (e.g. Button Mapper from Play Store)
handles them today.

## Build

Requires: JDK 11, Android `build-tools` 34 (`aapt2`, `d8`,
`apksigner`) and `platforms/android-34` (`android.jar`).

```sh
BT=build-tools/android-14        # or equivalent
PLAT=platforms/android-34/android.jar

$BT/aapt2 compile --dir . -o compile.zip
$BT/aapt2 link -o keyremap.apk -I $PLAT --manifest AndroidManifest.xml compile.zip
javac -source 8 -target 8 -bootclasspath $PLAT -d classes \
    GlobalKeyRemap.java KeyRemapAccessibilityService.java MainActivity.java
$BT/d8 --release --min-api 30 classes/de/pi/p610/keyremap/*.class --output dex
zip keyremap.apk dex/classes.dex            # NOTE: top-level, not dex/ subdir
keytool -genkeypair -keystore key.jks -alias p610 -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass android -keypass android \
        -dname "CN=P610 Research, OU=Test, O=Pi, L=Test, S=Test, C=DE"
$BT/apksigner sign --ks key.jks --ks-pass pass:android \
        --key-pass pass:android --out keyremap-signed.apk keyremap.apk
adb install keyremap-signed.apk
```

Activate the accessibility service (shell-level, no UI needed):

```sh
adb shell settings put secure enabled_accessibility_services \
    de.pi.p610.keyremap/.KeyRemapAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

## Changing the mapping

Edit the `KEYCODES`/`TARGETS` arrays in
`GlobalKeyRemap.java` and/or `KeyRemapAccessibilityService.java`.
Known R601 keyCodes: 265 Netflix, 266 YouTube, 138 Prime Video,
267 TV-Guide, 398–401 red/green/yellow/blue (see
[../../docs/remote.md](../../docs/remote.md) §3b).

## Notes / gotchas (all hit during the 2026-11-13 build)

- `classes.dex` must be at the APK **root**, not under `dex/`
  (`INSTALL_FAILED_INVALID_APK: code is missing` otherwise).
- `aapt2 link` needs `--manifest` pointing at the *source* manifest;
  resource IDs resolve fine without an `R` class because the app has
  no resource references in code.
- javac 11: use `-source 8 -target 8 -bootclasspath android.jar`
  (11 targets refuse `--boot-class-path`); avoid lambdas
  (`LambdaMetafactory` missing from android.jar).
- `Intent.ACTION_GLOBAL_BUTTON` and `AccessibilityEvent` key
  constants are `@hide` – use the string / numeric literals.
- The app must be *not-stopped* for implicit broadcasts (open the
  tiny MainActivity once).
