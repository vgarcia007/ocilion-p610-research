# R601 Remote Control

*Created 2026-11-13. Dedicated area for everything we know about the
bundled R601 remote: hardware, the box-side software stack, observed
behavior, the known GATT bug, and the investigation of button
remapping.*

Status legend as in the other docs: *(verified)* = observed on the live
unit via read-only ADB; *(datasheet/manual)* = from Ocilion's published
documents; *(expected)* = platform-standard, not yet observed.

## 1. Hardware

| Item | Value | Source |
|------|-------|--------|
| Model | R601 (bundled with P610) | *(datasheet)* |
| Buttons | **39** | *(datasheet)* |
| Transport | **BLE** (Bluetooth LE), Google-TV-style remote | *(verified)* – bond type `[LE]`, `transport:BT_TRANSPORT_LE` |
| BT version | box adapter is BT 5.2 (Realtek combo, `cqhci` driver) | *(verified)* |
| Voice Control | **Google Voice Control** button | *(datasheet)* – function on this box untested (see open questions) |
| Shortcut keys | **per-app shortcut keys** (the Google TV row of app buttons) | *(datasheet)* – mapping verified (see §3b) |
| Power | 2× AAA | *(datasheet)* |

- Official datasheet (P610 + R601, contains the button overview):
  <https://ocilion.com/assets/files/ocilion_P610_R601.pdf> (see
  [links.md](links.md)).
- User manual (de/en/fr/it, contains remote usage + pairing instructions):
  <https://ocilion.com/assets/files/P610_ocilion_V1.8_de_en_fr_it.pdf>

## 2. The box-side remote stack

The R601 is not "just" a BT HID device – it is supported by a whole
dedicated stack of system apps. All of these are part of the locked
operator backbone and must **not** be disabled (see
[debranding.md](debranding.md)):

| Package | Role | Notes |
|---------|------|-------|
| `com.ruwido.atv_ir` (v3.0.15) | **R601 remote companion app** | *(verified)* – ruwido is the remote's developer; this is the app that speaks to the R601 |
| `com.ruwido.rci_service` | **ruwido remote-control-interface service** | *(verified installed)* – the RCU interface layer |
| `com.sdt.rcupair` | **Remote pairing service** | *(verified)* – brings up the BT adapter right after boot ("adapter name TV Box" appears because of it) |
| `com.sdt.remotecontrolota` (v1.2.3, `com.skyworthdigital.remotecontrolota`) | **OTA channel for the remote itself** | *(verified)* – the R601 firmware is updated over the air, independently of box firmware. Explains the remote's own `last_active` / firmware-version fields |
| `com.sdt.globalkey` (+ overlay) | **Global key handling** – intercepts the remote's shortcut/special keys | *(verified installed)* – **primary candidate for button-remapping** (see §5) |
| `com.sdt.ocilion.oemservice` (v2.3) | Operator telemetry incl. remote state | *(verified)* – via `IBluetoothDeviceService` (RCU info): reports the bonded remote's MAC, type `RemoteControl`, connected state, **battery**, and **remote firmware version** to the operator backend |

### Key facts

- The R601 holds a **permanent LE connection since boot** and
  **re-bonds automatically** after power loss / battery removal.
  *(verified)* – see [bug-reports/bt-hid-gatt-discovery.md](bug-reports/bt-hid-gatt-discovery.md)
  for the full logcat evidence.
- Input path: BLE HID over the standard `shim::legacy::hid` (BTHH)
  path; the box runs with `bluetooth.profile.hid.host.enabled = true`.
  The remote works flawlessly through it. *(verified)*
- **Wake:** the R601 can wake the box from standby (standard platform
  path: CEC/IR receiver + `magic_wake_daemon`; standby is "instantly
  woken by the remote"). *(expected/verified behaviorally)*
- The remote's own firmware can be OTA-updated by the operator via
  `com.sdt.remotecontrolota`. There is no user-visible "update remote"
  action. *(verified – app + version present)*

### Known issue: GATT-discovery contention (root-caused)

While the R601 is connected (out-of-the-box state), **no other
Bluetooth gamepad can be paired** – the remote's permanent GATT client
starves the pairing flow ("No resources to open a new connection").
Root-caused 2026-11-11; verified workaround: pair with the remote
powered off. Full docs:

- [bug-reports/bt-hid-gatt-discovery.md](bug-reports/bt-hid-gatt-discovery.md) (analysis)
- [bug-reports/bt-hid-gatt-discovery-workaround.md](bug-reports/bt-hid-gatt-discovery-workaround.md) (how-to)
- [bug-reports/ticket-ocilion.md](bug-reports/ticket-ocilion.md) /
  [ticket-ocilion-en.md](bug-reports/ticket-ocilion-en.md) (vendor tickets)

This also means: the R601 **must keep working in any scenario** – it is
the box's primary (and only guaranteed BT) input device.

## 3. Verified button → keycode → action map (2026-11-13)

**Method:** the R601 presents as a **BLE HID keyboard** (uhid,
vendor `0x119b`, product `0x21d0`, shown as "Ocilion BLE Keyboard",
`/dev/input/event10`). Its keymap is
`/vendor/usr/keylayout/Vendor_119b_Product_21d0.kl` *(verified)*. Raw
button presses were captured with `getevent -l`; app reactions with
`logcat` (tag `SdtGlobalKey`). HID usages (`MSC_SCAN 0x0c:xxxx`) are
the Consumer Page button codes.

### 3a. The two categories

This is the key architectural finding for remapping:

| Category | Buttons | Path | Who acts | Remappable? |
|----------|---------|------|----------|-------------|
| **A – "Global buttons"** | Netflix, YouTube, Prime Video, TV-Guide, TV, Apps (Home-row), Settings, RCU-pair, Reset | keycode → broadcast `android.intent.action.GLOBAL_BUTTON` → 4 receivers (`sdt.globalkey`, `tv.settings`, `droidlogic`, `sdt.vendor`) | `com.sdt.globalkey.GlobalKeyReceiver` has a **hardcoded** keyCode→app table | ❌ needs root / OTA (privileged `/system/priv-app`) |
| **B – "Color buttons"** | **Red, Green, Yellow, Blue** | standard Android input event, **not** intercepted by any receiver | delivered to the **foreground app** | ✅ **yes** – any app / accessibility service can catch them |

**Why B is the remap target:** the color keys emit plain
`KEY_RED/GREEN/YELLOW/BLUE` keycodes on the shared input device,
produce **no** `SdtGlobalKey` log line, and reach the top app. UI6
answers them with *"Diese Funktion ist derzeit nicht verfügbar"*
(feature not available) because it does not implement them. Since they
flow through the normal Android input pipeline, a user-installed app or
accessibility service can intercept `KEYCODE_RED…BLUE` (398–401) and
reassign them – **no root, reversible**.

### 3b. Full table (verified where marked)

"Keymap source" is exactly what `Vendor_119b_Product_21d0.kl` maps the
button from: either a HID Consumer-Page usage (`usage 0x0c:xxxx`, what
`getevent` shows as `MSC_SCAN`) or a raw kernel keycode (`key N`).

"Keymap source" is exactly what `Vendor_119b_Product_21d0.kl` maps
from: either a HID Consumer-Page usage (`usage 0x0c:xxxx`, what
`getevent` shows as `MSC_SCAN`) or a raw kernel keycode (`key N`).
"Android keycode" is what the receiver/app receives afterwards.

| Physical button | Keymap source (raw) | Android keycode | Category | Action on this box |
|-----------------|---------------------|-----------------|----------|--------------------|
| **Netflix** | `usage 0x0c:0x78` (STEM_1) | **265** | A | `launchNetflix` → `com.netflix.ninja` *(verified live)* |
| **YouTube** | `usage 0x0c:0x77` (STEM_2) | **266** | A | `launchYoutube` → `com.google.android.youtube.tv` *(verified live: launched)* |
| **Prime Video** | `key 230` (F8) | **138** | A | `launchPrimeVideo` → `com.amazon.amazonvideo.livingroom` *(verified live)* |
| **TV-Guide** | `key 422` (STEM_3) | **267** | A | `launchOcilionTvGuide` → `com.ocilion.p6` *(verified live)* |
| **TV** | `key 377` (TV) | **170** | A | `launchOcilionTV` → `com.ocilion.p6` "tv" *(receiver match 0xaa)* |
| **Apps / Home-row** | `key 580` (ALL_APPS) | **284** (ALL_APPS) | A | `launchOcilionApps` → `com.ocilion.p6` "apps" *(receiver match 0x11c)* |
| **Settings** | `key 384` (SETTINGS) | **176** | A | `launchTvSettings` → `com.android.tv.settings` *(receiver match 0xb0)* |
| **RCU pair / Assist** | `key 63` (F5) | **135** (F5) | A | `handleRcuPairButton` (re-pair the remote) *(receiver match 0x87)* |
| **Reset** | (not in keymap) | **201** | A | `handleResetButton` – long-press = factory-reset flow *(receiver match 0xc9)* |
| **Red** | `key 0x18e` (MSC_SCAN `0x0c0069`) | **398** (KEY_RED) | **B** | → foreground app; UI6: "nicht verfügbar" *(verified raw)* |
| **Green** | `key 0x18f` (`0x0c006a`) | **399** (KEY_GREEN) | **B** | → foreground app *(verified raw)* |
| **Yellow** | `key 0x190` (`0x0c006c`) | **400** (KEY_YELLOW) | **B** | → foreground app *(verified raw)* |
| **Blue** | `key 0x191` (`0x0c006b`) | **401** (KEY_BLUE) | **B** | → foreground app *(verified raw)* |
| Power | `key 116` | 116 (KEY_POWER) | – | standby/wake |
| OK / D-pad | `key 353` / `103,105,106,108` | 23 / 19,21,22,20 (CENTER/UP/LEFT/RIGHT/DOWN) | – | navigation |
| Volume +/− / Mute | `key 115/114/113` | 115/114/113 | – | audio |
| Back / Home | `key 158` / `key 172` | 158 (BACK) / 3 (HOME) | – | navigation |
| Channel up/down | `key 402` / `key 403` | 166 / 167 | – | (no receiver handler) |
| Media play/rew/ff | `key 164/168/208` | 164/168/208 | – | transport |

Notes on the receiver matches: the `sdt.globalkey` switch (from the
decompiled smali) compares exactly these values: `0xc9`=201 reset,
`0x87`=135 pair, `0x86`=134 → `launchGooglePlayOrDisney`, `0x88`=136
→ `launchDisneyPlus`, `0x8a`=138 → Prime, `0xaa`=170 → TV,
`0xb0`=176 → Settings, `0x11c`=284 → Apps, packed-switch 265/266/267
→ Netflix/YouTube/TV-Guide. The 134/136 (F4/F6) entries have **no
matching button in the current keymap** – legacy/vestigial. Rows
marked only "receiver match" (TV, Apps, Settings, RCU-pair, Reset)
were not individually pressed in the live capture, but the keymap
symbol → Android keycode mapping is standard AOSP.

> **Reading note for remapping:** the app-shortcut row reaches the
> system as Android **keycodes 265/266/138/267** (intercepted at
> broadcast level, §5). The color keys reach apps as **keycodes
> 398–401** (`KEYCODE_RED/GREEN/YELLOW/BLUE`) – the §3a category-B
> finding.

**Repro for the raw capture:**

```sh
adb shell getevent -l          # watch /dev/input/event10 lines
# press Red/Green/Yellow/Blue -> KEY_RED/GREEN/YELLOW/BLUE appear
```

**Repro for the app reaction (category A):**

```sh
adb logcat -v time | grep SdtGlobalKey
# press Netflix -> "onReceive:keyAction<1> keyCode: 265"
# press YouTube -> "keyCode: 266" ... "GlobalButtonLaunch pkg: com.google.android.youtube.tv"
```

> **Note on the Netflix button:** on this unit `com.netflix.ninja` is
> per-user *disabled*, so `launchNetflix` logs
> `Cannot find intent for Netlix package: com.netflix.ninja` and does
> nothing. The button's *logic* is intact; it only fails because the
> target app is not launchable. *(observed 2026-11-13)*

## 4. What the remote does today (observed behavior)

- Navigates UI6 / any Android TV app: D-pad, OK, back, home, volume,
  power, mute. *(verified)*
- Navigates the proposed debranding launchers (LtvLauncher etc.) the
  same way. *(verified – debranding acceptance criteria)*
- Volume events are visible to the operator API live
  (`UI/Volume` 204 → 255 after 5× volume-up, verified 2026-11-12, see
  [firmware.md](firmware.md) → A/B test).
- Power button: box enters standby after the inactivity timeout and
  the remote wakes it instantly. *(verified behaviorally)*

**Verified 2026-11-13 (see §3):**

- The app-shortcut row launches Netflix/YouTube/Prime Video/TV-Guide
  (hardcoded in `com.sdt.globalkey`; the Netflix target fails while
  `com.netflix.ninja` is per-user disabled).
- The four **color keys** reach the foreground app as `KEY_RED/GREEN/
  YELLOW/BLUE`; UI6 answers them with "feature not available" on the
  home screen.

**Still untested:**

- The **Google Voice Control button** – voice control needs a Google
  account; untested (open question from [debranding.md](debranding.md)).
- Which of the remaining buttons (number pad, channel up/down, media
  keys, TV button) do something beyond the standard Android mapping.
  (Open, §7.)

## 5. Button remapping: goal & constraints

**Goal:** find out whether individual R601 buttons can be
reassigned (e.g. turn an app shortcut into "open app X", or make a
button do nothing / do something else) – without root, using the
existing shell-level ADB access.

**Hard constraints:**

- No root; read-only ADB shell. Anything that only works by writing to
  `/system` is out (would need root/OTA).
- The operator backbone (`com.sdt.ocilion.oemservice`, `com.sdt.*`)
  must stay functional – breaking the remote lifecycle or OTA would be
  bad for a customer-visible box.
- Reversible: anything we change must have a documented undo.

### Where a remapping could live – resolved 2026-11-13

**Category A (app shortcut row) – mapped, and NOT user-remappable
(proven by experiment 2026-11-13):**
`com.sdt.globalkey.GlobalKeyReceiver` (decompiled from
`/system/priv-app/SdtGlobalKeyHandler/SdtGlobalKeyHandler.apk`, 2
classes) contains a **hardcoded** keyCode→launch-method table,
compiled into a privileged `/system/priv-app`. There is **no**
`settings` / SharedPreferences configuration surface for it
(verified: no related `settings global/system/secure` entries exist on
the device).

The interception is fully system-level, proven three ways:

1. **Accessibility apps do not see the key.** "Button Mapper – TV
   Quick Actions" (Play Store, accessibility-based remapper) detects
   the four color keys but **not** the Netflix/YouTube/Prime/TV-Guide
   keys *(tested on this box)*.
2. **Custom broadcast receiver: blocked.** `GLOBAL_BUTTON` is a
   protected broadcast (AOSP `core/res/AndroidManifest.xml`) that any
   app can *declare* a receiver for, but the sender attaches
   `com.google.android.permission.GLOBAL_BUTTON` as receiver
   permission. That permission is **not even defined on the device**
   (absent from `dumpsys package` permission table); AOSP grants
   undefined permissions only to `SYSTEM_UID` (1000) – all five
   registered receivers (`sdt.globalkey`, `tv.settings`, `droidlogic`,
   `sdt.vendor`, + test app) are system apps, our test app
   (uid 10120) is registered in the resolver table but **never
   invoked** *(proven: APK with manifest receiver, declared
   permission, app de-stopped – zero deliveries)*.
3. **Accessibility `onKeyEvent` / `onAccessibilityEvent`: also empty**
   (custom service with `flagRequestFilterKeyEvents` +
   `canRetrieveWindowContent` – no events for keyCode 265) *(proven)*.

   i.e. the key event is consumed at InputDispatcher level and only
   re-emitted as the permission-filtered broadcast. No user-space
   layer sees it.

**Conclusion:** changing a Netflix/YouTube/Prime/TV-Guide target
requires writing to `/system` → **root or a new operator firmware**.
A ready-made remap app skeleton (broadcast receiver + accessibility
service) is in `tools/keyremap/` in this repo – it is the
implementation to use if a rooted path ever materializes (see
[rooting.md](rooting.md)); on the current box it is a no-op for
category A.

**Category B (color buttons) – remappable without root:**
the four color keys are plain input events (see §3a) with no system
handler. Remap options, ordered by preference:
1. **In-app handling** – the foreground app *is* the handler. Apps
   that implement `KEYCODE_RED/GREEN/YELLOW/BLUE` (398–401) just work;
   UI6 currently answers "not available".
2. **Accessibility-service interceptor** (user-level app from Play
   Store or sideloaded, `armeabi-v7a`): remaps the key events
   per-app. No system partition touched, uninstall = revert.
3. **Custom launcher** (relevant if we swap UI6, see
   [debranding.md](debranding.md)): our own home app could define what
   the four colors do on the home screen.

**Not a viable layer:** the remote firmware (OTA'd by the operator via
`com.sdt.remotecontrolota`) defines the *raw* HID usages – not
user-accessible.

## 6. Completed investigation steps (2026-11-13)

All steps were read-only ADB; nothing on the device was modified.

1. **Input device & keycodes – DONE.** `dumpsys input`: the R601 is
   "Ocilion BLE Keyboard" (`/dev/input/event10`, uhid, vendor 0x119b).
   `getevent -l` + `Vendor_119b_Product_21d0.kl` give the full
   button→keycode table (see §3b).
2. **Who reacts – DONE.** `logcat` (tag `SdtGlobalKey`) proves the
   GLOBAL_BUTTON path for the app-shortcut row (keyCode 265/266/138/
   267 observed live). The color keys produce **no** receiver log
   lines → window-app delivery (verified).
3. **`com.sdt.globalkey` decompiled – DONE.** 2 smali classes;
   complete keyCode→action table extracted; no config surface.
4. **Color-key capture – DONE.** Raw `getevent` shows
   `KEY_RED/GREEN/YELLOW/BLUE` (HID usages 0x0c0069/6a/6c/6b) on
   event10 – the §3a category-B finding.

**Remaining open items** are in §7.

## 7. Open questions

**Answered 2026-11-13** (kept for reference):
- *Which apps do the shortcut keys map to?* Hardcoded in
  `com.sdt.globalkey` (Netflix/YouTube/Prime/TV-Guide/UI6 variants),
  not per-remote configurable – same for all boxes on this firmware
  (see §3b/§5).
- *Is the mapping configurable via `settings`/UI?* No – no
  configuration surface exists (see §5).
- *Can the Netflix/YouTube/… buttons be remapped to other apps
  (e.g. Jellyfin) without root?* **No** – proven by experiment
  (custom broadcast receiver + accessibility service both blocked,
  Button Mapper from Play Store does not see the keys). Requires
  root/OTA. See §5 and [../tools/keyremap/](../tools/keyremap/) for
  the ready implementation to use once rooted.

**Still open:**
- Does the Google Voice Control button do anything useful without a
  Google account? (The `virtual-remote` input device has `HasMic:
  true`; the physical voice key's keycode is not in the keymap yet –
  capture it via `getevent`.)
- What does the **TV** button (keyCode 377 / TV_INPUT, `#Access`
  comment in the legacy keymap) do on this firmware? Not in the
  `sdt.globalkey` table – likely window-app or droidlogic/vendor
  receiver.
- What consumes the color keys in the **Ocilion TV app** (UI6
  playback screens)? The "feature not available" toast on the home
  screen suggests some screens *do* implement them (operator-TV
  convention: red=menu, green=info/EPG, yellow=subtitle, blue=
  recording/extra) – verify in UI6 live-TV playback.
- Are the `com.droidlogic` / `com.sdt.vendor` GLOBAL_BUTTON receivers
  no-ops on this firmware or do they handle any key? (Decompile the
  two APKs, offline.)
- Does the remote OTA (`com.sdt.remotecontrolota`) ever change button
  behavior in a newer remote firmware? (Unobservable from the box –
  only the operator knows.)
- Which of the remaining 39 buttons (number pad, info, channel
  up/down, media keys) do something beyond the standard Android
  mapping? (Cheap to check with the same `getevent` + logcat method.)
