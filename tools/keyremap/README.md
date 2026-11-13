# P610 Key Remap (keyremap)

Minimal Android app that remaps the R601 remote's "global" buttons
(category A – see [../../docs/remote.md](../../docs/remote.md) §5)
to arbitrary apps, **without root**.

## How it works (verified on the live unit, 2026-11-13)

The R601's shortcut buttons (Netflix 265 / YouTube 266 / Prime 138 /
TV-Guide 267) are intercepted by the system before any user layer can
see the key event:

- the `android.intent.action.GLOBAL_BUTTON` broadcast is
  permission-filtered to system uid (`com.google.android.permission.
  GLOBAL_BUTTON` is not even defined on-device → only uid 1000),
  - no window dispatch, no accessibility `onKeyEvent`,
    no `onAccessibilityEvent` for the key itself.

**But the system does start the mapped app** – and the resulting
**window state change** *is* visible. So `KeyRemapAccessibilityService`
implements a **window hijack**:

1. Netflix button pressed → `com.sdt.globalkey` starts
   `com.netflix.ninja`
2. `com.netflix.ninja` window appears → service detects it
3. service immediately starts `org.jellyfin.androidtv`
   (launch intent + `NEW_TASK | CLEAR_TASK | NO_ANIMATION`)

Result: the original app flashes for at most one frame, the target
opens. Toggle: pressing the button again while inside the target
goes Home (decided by the actually observed previous window).
A 2.5 s self-check relaunches the target if the first
`startActivity` was swallowed on cold start (otherwise the first
press fails silently – "have to press twice").

Status: **Netflix → Jellyfin installed & verified on the box**
(1 press from cold start, toggle, and Netflix-off behaviour all
checked). The old broadcast receiver in `GlobalKeyRemap.java` is
kept for reference; it does *not* receive the protected broadcast
on an unrooted box.

## Requirements

- The trigger app (e.g. `com.netflix.ninja`) must be **enabled and
  launchable** – the system has to actually start it for the
  hijack to have something to hijack.
  (Netflix note: the `/data` update 13.2.2 was built for Android 16
  and failed with `CLASS_NOT_FOUND` on this Android 14 box;
  `pm uninstall com.netflix.ninja` reverts to the working
  `/product` 11.0.12.)
- Manifest `<queries>` for trigger + target packages (Android 11+
  package visibility) – already in `AndroidManifest.xml`.
- Service config: `accessibilityEventTypes=typeWindowStateChanged`,
  `canRetrieveWindowContent=true`.

## Install

```sh
adb install keyremap-signed.apk
adb shell settings put secure enabled_accessibility_services \
    de.pi.p610.keyremap/.KeyRemapAccessibilityService
adb shell settings put secure accessibility_enabled 1
# open the app once (MainActivity) so it is not-stopped,
# then the service can be switched off/on in Settings
```

## Undo (full revert)

```sh
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall de.pi.p610.keyremap
```

## Changing the mapping

Edit the `MAP` array in `KeyRemapAccessibilityService.java`
(`[triggerPackage, targetPackage]` pairs) and the `<queries>` block
in `AndroidManifest.xml`, then rebuild. Known R601 keyCodes and
their system targets: 265 Netflix → `com.netflix.ninja`, 266
YouTube → `com.google.android.youtube.tv`, 138 Prime →
`com.amazon.amazonvideo.livingroom`, 267 TV-Guide →
`com.ocilion.p6` (see [../../docs/remote.md](../../docs/remote.md)
§3b).

The color keys (category B, keyCodes 398–401) do NOT need this app –
they reach the foreground app directly; any remapper (e.g. Button
Mapper from Play Store) handles them.

## Build

Requires: JDK 11, Android `build-tools` 34 (`aapt2`, `d8`,
`apksigner`) and `platforms/android-34` (`android.jar`).

```sh
BT=build-tools/android-14        # or equivalent
PLAT=platforms/android-34/android.jar

aapt2 compile --dir . -o compile.zip
aapt2 link -o keyremap.apk -I $PLAT --manifest AndroidManifest.xml compile.zip
javac -source 8 -target 8 -bootclasspath $PLAT -d classes \
    GlobalKeyRemap.java KeyRemapAccessibilityService.java MainActivity.java
$BT/d8 --release --min-api 30 classes/de/pi/p610/keyremap/*.class --output dex
zip keyremap.apk dex/classes.dex            # NOTE: top-level, not dex/ subdir
keytool -genkeypair -keystore key.jks -alias p610 -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass android -keypass android \
        -dname "CN=P610 Research, OU=Test, O=Pi, L=Test, S=Test, C=DE"
$BT/apksigner sign --ks key.jks --ks-pass pass:android \
        --key-pass pass:android --out keyremap-signed.apk keyremap.apk
```

## Notes / gotchas (all hit during the 2026-11-13 build)

- `classes.dex` must be at the APK **root**, not under `dex/`
  (`INSTALL_FAILED_INVALID_APK: code is missing` otherwise).
- javac: `-source 8 -target 8 -bootclasspath android.jar`;
  avoid lambdas (`LambdaMetafactory` missing from android.jar).
- `Intent.ACTION_GLOBAL_BUTTON` and `AccessibilityEvent` key
  constants are `@hide` – use the string / numeric literals.
- `AccessibilityEvent.getPackageName()` returns `CharSequence`
  (not `byte[]` as in newer SDKs).
- Toggle must compare against the *observed previous window*, not an
  internal "last target" flag – stale state caused false Home jumps.
- Without the delayed relaunch check, cold starts swallow the first
  `startActivity` (background-activity-launch restriction while the
  trigger app is still coming up) → "press twice" behaviour.
- Re-enabling the accessibility service after a config change:
  clear `enabled_accessibility_services`, wait, re-set it.
