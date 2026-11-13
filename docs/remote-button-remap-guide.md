# R601 special button → custom app: how-to (reproducible)

*Created 2026-11-13. Step-by-step guide to remap one of the R601's
"global" buttons (Netflix / YouTube / Prime Video / TV-Guide row)
to launch an arbitrary app, **without root**, on the Ocilion P610
(firmware `C4.0.7_20260605`, Android 14, API 34). Everything here
was executed and verified on the live unit; the complete reasoning
and the failed paths are in [remote.md](remote.md) §5. The app
source lives in [../tools/keyremap/](../tools/keyremap/).*

**Result achieved on this box:** pressing the **Netflix** button
(keyCode 265) opens **Jellyfin** (`org.jellyfin.androidtv`)
instead of Netflix. One press, cold start, no root.

## 0. The trick in one paragraph

The button's key event is **not** catchable by any user app (the
`GLOBAL_BUTTON` broadcast is system-uid-only, and no key/window
accessibility event is generated – proven by experiment, see
[remote.md](remote.md) §5). But the system *does* launch the
hardcoded target app (`com.sdt.globalkey` → `com.netflix.ninja`),
and **the appearance of that window is observable** by any enabled
accessibility service. So: let the system launch Netflix, and
redirect *from there* – an always-on accessibility service
(`KeyRemapAccessibilityService`) watches for the trigger app's
window and immediately starts the target app. The original app
flashes for at most one frame. No system partition is touched;
`com.sdt.globalkey` and the operator backbone are untouched and
keep working.

## 1. Prerequisites

- P610 reachable via **ADB over Wi-Fi** (`adb connect
  <ip>:5555`). Note: the box **drops ADB in standby** – press any
  remote key to wake it, then reconnect.
- The **trigger app must be installed and enabled** (for the
  Netflix button: `com.netflix.ninja`). The system has to actually
  launch it. Check: `adb shell pm list packages | grep <pkg>` and
  `dumpsys package <pkg> | grep 'enabled='`.
  - ⚠️ **Netflix gotcha found on this unit:** the `/data` update
    `com.netflix.ninja` 13.2.2 was built for **Android 16**
    (`platformBuildVersionCode=36`) and failed to launch with
    `CLASS_NOT_FOUND` (result −92) on this Android 14 box, even
    though enabled. Fix: `adb shell pm uninstall com.netflix.ninja`
    (removes only the `/data` update → reverts to the working
    `/product` 11.0.12). If a future OTA re-breaks the launch,
    check this first.
- The **target app** installed and launchable (here:
  `org.jellyfin.androidtv`). Verify:
  `adb shell am start -n <target>/<launcher activity>`.
- A build host with **JDK 11** and `unzip`.
- Android build tools (download once, ~60 MB total):
  ```sh
  # Linux; for macOS/Windows use the _darwin.zip / _win.zip names
  curl -LO https://dl.google.com/android/repository/build-tools_r34-linux.zip
  curl -LO https://dl.google.com/android/repository/platform-34_r02.zip
  unzip build-tools_r34-linux.zip -d android-build/
  unzip platform-34_r02.zip -d android-build/
  # gives: android-build/build-tools/34.0.0/{aapt2,d8,apksigner}
  #        android-build/android-34/android.jar
  ```
- The app source: copy the repo's [../tools/keyremap/](../tools/keyremap/)
  directory to your build machine (it is plain `javac`-compatible
  Java 8, no Gradle, no Android SDK project).

## 2. Adapt the mapping (optional)

Default mapping: **Netflix button (`com.netflix.ninja`) →
`org.jellyfin.androidtv`**. To remap a different button or target:

1. In `KeyRemapAccessibilityService.java`, edit the `MAP` array
   (pairs `[triggerPackage, targetPackage]`):

   | Button | keyCode | Trigger package (the system's hardcoded target) |
   |--------|---------|--------------------------------------------------|
   | Netflix | 265 | `com.netflix.ninja` |
   | YouTube | 266 | `com.google.android.youtube.tv` |
   | Prime Video | 138 | `com.amazon.amazonvideo.livingroom` |
   | TV-Guide | 267 | `com.ocilion.p6` |

2. Add **both** packages to the `<queries>` block in
   `AndroidManifest.xml` (Android 11+ package visibility –
   without it `getLaunchIntentForPackage()` returns null and the
   hijack silently does nothing).

The package name (e.g. `de.pi.p610.keyremap`) can stay as is; it
does not matter.

## 3. Build

From the directory containing `AndroidManifest.xml`, `src/`-equivalent
`.java` files and `res/` (the repo layout is flat:
`AndroidManifest.xml` + `*.java` + `res/` at top level):

```sh
BT=.../android-build/build-tools/34.0.0
PLAT=.../android-build/android-34/android.jar

# 3a. resources (manifest + res must be compiled for aapt2 link)
$BT/aapt2 compile --dir . -o compile.zip
$BT/aapt2 link -o keyremap.apk -I $PLAT --manifest AndroidManifest.xml compile.zip

# 3b. java -> dex (Java 8 target! no lambdas – LambdaMetafactory
#     is missing from android.jar)
mkdir -p classes dex
javac -source 8 -target 8 -bootclasspath $PLAT -d classes \
    GlobalKeyRemap.java KeyRemapAccessibilityService.java MainActivity.java
$BT/d8 --release --min-api 30 $(find classes -name '*.class') --output dex

# 3c. dex goes at the APK ROOT (not under dex/ – that yields
#     INSTALL_FAILED_INVALID_APK "code is missing")
cp dex/classes.dex .
zip -q keyremap.apk classes.dex
rm classes.dex

# 3d. sign (generate the keystore once; store/key pass "android",
#     alias "p610" as in tools/keyremap/README.md)
$BT/apksigner sign --ks key.jks --ks-pass pass:android \
    --key-pass pass:android --out keyremap-signed.apk keyremap.apk
```

Build gotchas (all actually hit):

- `aapt2 link` needs `--manifest` pointing at the *source*
  manifest; no `R` class is needed (the app references no
  resources from code).
- `javac 11` rejects `--boot-class-path` for non-8 targets –
  use `-source 8 -target 8 -bootclasspath android.jar`.
- `Intent.ACTION_GLOBAL_BUTTON` and `AccessibilityEvent` key
  constants are `@hide` in the public SDK – the code uses the
  string/numeric literals already.
- `AccessibilityEvent.getPackageName()` returns `CharSequence` on
  this SDK (not `byte[]`) – call `.toString()`.

## 4. Install & enable

```sh
adb install -r keyremap-signed.apk            # "Success"
# open the tiny MainActivity once so the app is "not stopped"
adb shell am start -n de.pi.p610.keyremap/.MainActivity
# enable the accessibility service (shell-level, no UI needed)
adb shell settings put secure enabled_accessibility_services \
    de.pi.p610.keyremap/.KeyRemapAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Verify the service is really connected:

```sh
adb logcat -d | grep P610KeyRemap
# expected: "hijack service connected"
```

> After any rebuild that changes `res/xml/accessibility_service_
> config.xml`, the service must be cycled: clear
> `enabled_accessibility_services`, wait ~2 s, set it again.

## 5. Test

Simulate the physical button with its keycode (265 = Netflix):

```sh
adb shell input keyevent 265
adb logcat -d | grep P610KeyRemap
adb shell "dumpsys window | grep -m1 mCurrentFocus"
```

Expected log and focus:

```
D P610KeyRemap: window: com.netflix.ninja
I P610KeyRemap: hijack: com.netflix.ninja -> org.jellyfin.androidtv
D P610KeyRemap: window: org.jellyfin.androidtv
mCurrentFocus=Window{... org.jellyfin.androidtv/...MainActivity}
```

Then test the **toggle**: with Jellyfin in front,
`adb shell input keyevent 265` again → expect
`toggle: was in org.jellyfin.androidtv -> Home` and focus back at
the launcher (`com.ocilion.p6`).

Finally: press the **real Netflix button** on the R601 from
home/cold start. One press → Jellyfin (possibly with a one-frame
Netflix flash).

## 6. Undo (full revert, no root)

```sh
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall de.pi.p610.keyremap
```

This restores the stock behaviour exactly (button → Netflix).
Nothing on `/system` or the operator stack was ever modified.

## 7. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `hijack: ... -> ...` but "target not installed" in log | missing `<queries>` entry for the target package → rebuild |
| first press does nothing, second works | cold start swallowed the first `startActivity`; the 2.5 s self-check relaunch fixes this – make sure you're on the build *with* `launchVerified()` |
| `toggle: ... -> Home` when you expected a launch | the user really *was* in the target app previously (by design); the check uses the observed previous window, so it cannot false-positive otherwise |
| Netflix never starts ("Cannot find intent" in `SdtGlobalKey` log) | trigger app disabled → enable it; or the `/data`-update/API-level mismatch from §1 → `pm uninstall com.netflix.ninja` |
| `INSTALL_FAILED_INVALID_APK: code is missing` | `classes.dex` must be at the APK root (see 3c) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | a differently signed `de.pi.p610.keyremap` is still installed → `adb uninstall` first |
| service not "connected" after install | app must be started once (MainActivity); service must be re-enabled after config changes (see §4 note) |
| ADB "Connection refused" | box is in standby (drops ADB) → press any remote key, reconnect |

## 8. What this is *not*

- It does not change the button's definition anywhere; the system
  still "thinks" the button opens Netflix. Any other system
  component that reacts to the `GLOBAL_BUTTON` broadcast (operator
  telemetry, settings) is unaffected.
- It does not work for category-B (color) buttons – those reach
  the foreground app directly and can be handled by any remapper
  (e.g. "Button Mapper – TV Quick Actions" from the Play Store).
- While the service runs, the *real* target of a remapped button
  is only reachable by opening it from the app grid (the one-frame
  flash is the intended behaviour).
