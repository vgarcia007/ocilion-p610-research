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
| Shortcut keys | **per-app shortcut keys** (the Google TV row of app buttons) | *(datasheet)* – mapping on this box untested (see §5) |
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

## 3. What the remote does today (observed behavior)

- Navigates UI6 / any Android TV app: D-pad, OK, back, home, volume,
  power, mute. *(verified)*
- Navigates the proposed debranding launchers (LtvLauncher etc.) the
  same way. *(verified – debranding acceptance criteria)*
- Volume events are visible to the operator API live
  (`UI/Volume` 204 → 255 after 5× volume-up, verified 2026-11-12, see
  [firmware.md](firmware.md) → A/B test).
- Power button: box enters standby after the inactivity timeout and
  the remote wakes it instantly. *(verified behaviorally)*

**Not yet observed / untested:**

- The **per-app shortcut keys** (Google TV app-button row) – which apps
  they map to on *this* provisioning, and whether the mapping is
  configurable. (Open, §5.)
- The **Google Voice Control button** – voice control needs a Google
  account; untested (open question from [debranding.md](debranding.md)).
- Which of the 39 buttons are actually used on the P610 firmware vs.
  mapped to no-ops. (Open, §5.)

## 4. Button remapping: goal & constraints

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

### Where a remapping could live (hypotheses, ordered by likelihood)

1. **`com.sdt.globalkey`** – "Global key handling (shortcut keys on
   the remote)". Most likely home of the button→action mapping.
   Check: does it expose `android.provider.Settings` entries,
   `SharedPreferences`, or a settings Activity we can read/change?
2. **`com.ruwido.atv_ir` / `com.ruwido.rci_service`** – the remote
   companion may carry the per-app shortcut configuration (ruwido
   remotes are configurable remotes by design). Check its data dir and
   settings surface.
3. **Android keylayout layer** – the R601 arrives as a standard HID
   input device; the base keycode map may live in
   `/system/usr/keylayout/*.kl`. If the special buttons show up as
   generic keycodes (e.g. `KEYCODE_BUTTON_*`), a *user*-level
   interception (accessibility service, input interceptor app from
   Play Store) could remap them without touching system files.
4. **Google TV `AtvRemoteService`** – the stock remote-service
   component handles shortcut keys on Google TV; on this box it may be
   shadowed by `com.sdt.globalkey` instead.
5. **Remote firmware itself** – last resort: the R601 firmware (OTA'd
   by the operator) defines what the buttons *send*. Not user-
   accessible.

## 5. Investigation plan (read-only first)

All steps are read-only ADB; nothing is changed until a mapping
surface is confirmed *and* a safe reversible path exists.

**Step 0 – Identify the input device & keycodes (5 min):**

```sh
adb shell dumpsys input | grep -A6 -i "remote\|ruwido\|r601"
adb shell input getevents -l      # then press each special button
```

→ Map: which of the 39 buttons the box even sees as input events, and
with which keycodes. Buttons that produce no event at all are handled
out-of-band (e.g. direct GATT notifications to `com.ruwido.rci_service`)
and cannot be remapped at the input layer.

**Step 1 – Watch who reacts (10 min):**

```sh
adb shell logcat -v time | grep -iE "globalkey|ruwido|rci|shortcut|keyevent"
```

→ Press each special button; note which component logs a reaction.

**Step 2 – Inspect `com.sdt.globalkey` (20 min):**

```sh
adb shell dumpsys package com.sdt.globalkey
adb shell run-as com.sdt.globalkey 2>&1 | head -1   # expect "not allowed" (system app)
adb shell pm dump com.sdt.globalkey | grep -iE "permission|receiver|service"
```

→ Extract the APK (`pm path` + `adb pull`) and decompile locally
(offline): look for key→action tables, settings screens, config files.
APK stays out of this public repo (proprietary firmware material).

**Step 3 – Inspect `com.ruwido.*` (20 min):** same approach – APK
inspection + `dumpsys package` + any `settings put`-style keys in the
dex.

**Step 4 – Test a user-level interceptor (if steps 0–3 find a plain
keycode):** with the base keycodes known, evaluate an accessibility
service / input-remapping app (Play Store, `armeabi-v7a`) that can
reassign `KEYCODE_*` per-app. This is the most likely *clean* answer
to "can we remap buttons" – no root, reversible, survives firmware
updates only while the app is installed.

**Step 5 – Decide & document:** whichever path works (or the
conclusion "not possible without root / remote firmware change"),
document it here with reproducible commands.

## 6. Open questions

- Which apps do the per-app shortcut keys map to on this (komro)
  provisioning, and is the mapping per-operator or per-remote?
- Does the Google Voice Control button do anything useful without a
  Google account?
- Is the `com.sdt.globalkey` mapping configurable via a
  `settings put` / Settings UI entry? (Step 2 answers this.)
- Do any of the 39 buttons have no in-box function at all (e.g.
  buttons meant for TV-input/TV-radio that don't exist on the P610)?
- Does the remote OTA (`com.sdt.remotecontrolota`) ever change button
  behavior in a newer remote firmware? (Unobservable from the box –
  only the operator knows.)
