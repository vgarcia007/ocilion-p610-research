# Gaming on the P610

What the box can realistically do for gaming. All assessments assume the
**32-bit userspace** constraint (see [android.md](android.md)) and the locked
security model (no root, no custom firmware).

## Short assessment

- **Retro emulation (up to PS1/N64/GameCube-light, SNES, GBA, NDS):** ✅ good.
- **Modern console emulation (PS2, Switch, Wii U):** ⚠️ mixed-to-poor,
  limited by the 32-bit ABI and 4×A55 clock.
- **Game streaming (Moonlight/Steam Link, GeForce Now cloud):** ✅ good over
  Wi-Fi 6 / Ethernet.
- **Native Steam / Windows via ARM64 layers (Winlator/Mobox):** ⚠️ limited –
  these need a **64-bit** userspace for the x86 emulation core + ARM64
  libs; the 32-bit Android userspace is a hard constraint.

## Retro emulation

Recommended frontends (all have `armeabi-v7a` builds):

- **Lemuroid** (RetroArch fork, Android TV native, D-pad friendly) – the
  default choice on a TV box. Supports cores up to PS1/N64/GBA/NDS/SNES
  etc.
- **RetroArch** standalone (has a TV/leanback build).

Expected performance on 4×Cortex-A55 @2.5 GHz (32-bit):

| System | Verdict | Notes |
|--------|---------|-------|
| NES/SNES/GBA/GBA/NGP | ✅ | trivial |
| PS1 | ✅ | most games at 1× with shaders |
| N64 | ✅–⚠️ | most games OK; heavy ones (e.g. some with on-screen FX) may drop |
| NDS | ✅ | DeSmuME/mergeNDS, 32-bit OK |
| Dreamcast | ⚠️ | often playable; some heavy titles stutter |
| GameCube/Wii | ⚠️ | partial; 32-bit Dolphin cores work but performance is the limit |
| PS2 | ⚠️–❌ | PCSX2 32-bit cores exist but 4×A55 is below the comfortable line for most PS2 titles |
| Switch | ❌ | not realistic at usable frame rates here |

The 32-bit ABI is the *binding* constraint for the heavier cores, more than
raw CPU count. A 64-bit firmware would help, but that's out of scope.

**Controller support:** the BT HID host profile is enabled and the R601
remote works as a BT device – **but the full HID gamepad path is broken on
this firmware** (Xbox Wireless Controller tested, pairing succeeds but the
HID profile connection fails in ~6 ms; see the dedicated section below).
Gamepad input works via Android's standard `Input` system; the reliable
path on this box is **USB** (wired or 2.4 GHz dongle).

## Gamepad / controller input (important for streaming & emulation)

### Bluetooth gamepad pairing: Xbox fails at the HID profile step (tested 2026-11-11)

*Note (2026-11-11, same day): the R601 remote is also a **BLE** device and
works on the same HID path, so BT HID is not categorically broken –
see the interpretation below for the refined diagnosis and the fix
candidates.*

**Test:** Xbox Wireless Controller (modern, BLE-capable) paired against the
box through the Settings UI (*Einstellungen → Systemeinstellungen →
Bluetooth → Zubehör hinzufügen*).

**The "connect a controller" system flow:** after installing a
controller-using app (e.g. Steam Link from the Play Store), Android TV
shows a system prompt "connect a controller". Pressing continue fires
the intent `com.google.android.intent.action.CONNECT_INPUT`, which
resolves to **`com.android.tv.settings/.accessories.AddAccessoryActivity`**
– the standard Bluetooth device picker. That activity **is present** on
this build (verified via `cmd package resolve-activity`), so the
"missing assistant" is not missing; it leads into the same BT stack
that fails at the HID profile step below. It also has the `LAUNCH`
action (`android.bluetooth.devicepicker.action.LAUNCH`), so it can be
launched directly:

```sh
adb shell am start -a android.bluetooth.devicepicker.action.LAUNCH \
  -n com.android.tv.settings/.accessories.BluetoothDevicePickerActivity
```

**What works:**

- Bluetooth adapter is on at boot (`state: ON`, adapter name "TV Box",
  brought up by `com.sdt.rcupair` right after boot – that's the R601 remote).
- The **HID Host profile is enabled**: `bluetooth.profile.hid.host.enabled = true`.
- The device is discoverable and the pairing *bond* succeeds – the
  controller was found in the scan, `SMP Pairing success` in 2 s,
  `bond_state_changed → BOND_STATE_BONDED`.

**What fails:** the HID *profile* connection, which is what actually makes
the gamepad usable:

```
17:41:05.409  HID_HOST connection policy → 100         # box: "connect"
17:41:05.413  Connection state: 0 -> 1                 # starts
17:41:05.419  Connection state: 1 -> 0                 # drops ~6 ms later
17:41:05.449  "Failed to connect HID device"           # x3 in logcat
```

The box then **terminates the ACL itself** (`CONNECTION_TERMINATED_BY_LOCAL_HOST`)
and **deletes the bond ~70 s later** (`Remove bond`,
`HID_HOST connection policy → -1`), which is why the GUI finally reports
"Kopplung nicht möglich" (pairing not possible).

**Interpretation & what this rules out:**

- The kernel HCI driver is **`cqhci`** (Amlogic SDIO HCI module,
  `cqhci` in `lsmod`) – a platform driver for the Realtek combo chip
  shared with the RTL8852BS Wi-Fi, not the generic `btusb` path.
- **The R601 remote is also a BLE device** (bond type `[LE]`,
  `transport:BT_TRANSPORT_LE`) and it **works** through the very same
  `shim::legacy::hid` (BTHH) path that the Xbox controller fails on.
  So "BT HID is broken on this box" is **too strong**: simple BLE HID
  devices (remote/keyboard) do complete the full handshake.
- The Xbox bond itself succeeds repeatedly (`SMP Pairing success`,
  `Encryption success`, `bond_state:1`), so SMP bonding is fine.
- **Refined root cause (3rd attempt, clean controller after factory
  reset, 2026-11-11 19:40):** the failure is inside **GATT service
  discovery**, not (only) in the profile layer:
  ```
  GATTC_Discover ... disc_type=2/4              # discovery starts
  bta_gattc_process_api_open: No resources to open a new connection
  bta_dm_search_cmpl: GATT active but no BLE connection,
                      likely disconnected midway through
  btif_dm_search_services_evt: No well known GATT services discovered
  ```
  i.e. the LE link drops (or the stack refuses a second GATT client
  with "no resources") **during** service discovery, so the **HID
  over GATT service is never found**, and HID profile activation can
  never start. The earlier "drops in ~6 ms" observation was the
  profile layer giving up because discovery had already failed.
- The Xbox controller also **keeps trying to reconnect to its previous
  host** (PC/console) after the box deletes the bond – its LED state
  reflects that, not a box connection. This is standard Xbox behavior
  and can interfere with re-pairing (it prefers the older bond).

**Where a fix could live (hypotheses, ordered):**

1. **Connection-resource contention with the R601 remote (new,
   testable):** the R601 holds a permanent LE/HID connection
   (`BTHH_CONN_STATE_CONNECTED`, its own GATT client). The failing
   error is literally **"No resources to open a new connection"**
   while the stack tries to run GATT discovery for the Xbox. If the
   Realtek controller/`cqhci` driver has a small LE-connection or
   GATT-client budget, the always-connected remote could be
   starving the pairing process. **Test:** temporarily disconnect /
   take out the R601 (or switch the box's BT off+on and pair the
   Xbox *before* the remote reconnects), then pair the Xbox.
2. **Controller side:** remove the old bond on any previous host
   (PC: Settings → Bluetooth → remove device) and factory-reset the
   controller (Xbox + Pairing button ~10 s). **Done 2026-11-11, did
   not change the outcome** (3rd attempt failed the same way, with
   the refined root cause above).
3. **Different BLE gamepad** (PS4/PS5/8BitDo): another device might
   survive the GATT-discovery phase – worth testing, since the R601
   proof shows the LE HID path can work on this box.
4. **Stack/firmware level:** if all BT gamepads fail at the same
   step, the bug is in the `cqhci`/Realtek combo or the Android BT
   shim's GATT-client/HID-over-GATT handling – only fixable via a
   firmware update (operator OTA) or root; not user-fixable.
5. **USB remains the guaranteed path** (see alternatives below).

**Practical status: BT for a gamepad is unproven on this box and the
current evidence (Xbox) is negative; USB is the reliable path.
Next test to run (documented under Open questions): the
R601-contention test – pair the Xbox while the always-connected
R601 remote is out of the equation. Second candidate: a different
BLE gamepad (PS4/PS5/8BitDo).**

### The reliable alternatives (in order of preference)

1. **USB wired** – the box has **4 USB controllers** in the kernel
   (`usb1`–`usb4`, `sys.usb.configfs` mode).
   - **Xbox One (2013 original):** has a **USB 2.0 Micro-B port** on the
     controller – plug in directly, no pairing, recognized as a standard
     HID gamepad.
   - **Xbox One S / One X / Series:** Bluetooth only, **but** with a
     Micro-USB / USB-C **tether cable** straight to the box they behave
     like USB controllers (the data pins are present on the controller
     port). This is the cleanest path for a modern Xbox pad.
   - **PS4 / PS5:** USB-C wired.
   - USB HID on Android boxes is very reliable because it uses a
     completely different (and mature) stack path than BT HID.
2. **2.4 GHz USB dongle gamepad** (e.g. 8BitDo, eSwitcher, generic) –
   plug the dongle into USB, pair the pad to the dongle (not to the box).
   Cheapest and most robust option.
3. **USB Bluetooth adapter (5.x with HID/gamepad support)** – the box may
   pick it up as a second BT adapter if the Bluetooth USB-HCI driver is
   present; not guaranteed on this image, but common on Amlogic boxes.
   Worth trying if BT specifically is wanted.
4. **Different BT gamepad** – a PS4/PS5/8BitDo pad has a different HID
   report structure and *might* succeed where the Xbox pad fails, but this
   is unverified and the Xbox result suggests the stack limitation is
   general, not Xbox-specific. Treat as long-shot.
5. **Microsoft 2.4 GHz Xbox Wireless Dongle: will NOT work.** The dongle
   is a proprietary 2.4 GHz RF transceiver, not a USB HID device – the
   "driver" lives in the dongle's own firmware, and only Windows (and
   niche Linux `xboxdrv` efforts) speak that protocol. The box's kernel
   does have generic gamepad drivers (`xpad`, `usbhid` – verified loaded),
   which is exactly why the *wired* USB tether in point 1 works, but no
   kernel module exists for the proprietary Microsoft RF link.
   *(Don't bother.)*

**For Steam Link / Moonlight:** pair the gamepad to the **box** (via USB
or dongle, not to the PC) – the streaming traffic is Wi-Fi/Ethernet, the
input is USB/BT; the two run on separate radios and simultaneously.

### Verifying a connected gamepad

```sh
# bonded BT devices (should show the pad when paired)
adb shell dumpsys bluetooth_manager | grep -A5 "Bonded devices:"
# input devices (a gamepad shows up as a new eventN with its name)
adb shell getevent -i | grep -A2 "name:"
```

## Game streaming (the best "modern gaming" path)

The box is a strong **client** for streaming your own PC/console's output:

- **Moonlight** (NVIDIA GameStream / Sunshine) – Android TV build available;
  excellent latency over a wired or Wi-Fi 6 link. The box's HEVC/AV1 decode
  + 4K HDMI 2.1 output make it a good Moonlight *display* for a
  high-bitrate 4K60 HEVC stream from a PC running Sunshine.
- **Steam Link** (official) – Android TV app; **installed on this unit
  (2026-11-11, `com.valvesoftware.steamlink`)** and installs cleanly. It
  needs a gamepad – see the controller section above (use USB/dongle,
  not Bluetooth).
- **GeForce Now / Xbox Cloud / PlayStation Plus cloud** – cloud gaming
  clients; Google Play has GeForce Now for Android TV. These depend on the
  Play Store being signed in (see [debranding.md](debranding.md)).

This is by far the most "modern game" experience achievable on the box: you
get full PC/console performance, the P610 is just a low-latency 4K display +
gamepad. **Recommendation for anyone wanting to play current games.**

Requirements for good streaming: wired Ethernet preferred (100 Mbit is a
bit low for 4K60 high-bitrate HEVC; Wi-Fi 6 on 5 GHz at 866 Mbps PHY is the
better path here), a gamepad, and a host (PC with Sunshine, or an
NVIDIA/Steam machine).

## Native Steam / Windows-on-ARM (compatibility layers)

Projects like **Winlator**, **Mobox**, **ExaGear** (legacy) run x86/x86_64
Windows games on ARM via Box86/Box64 + Wine. Constraints on *this* box:

- **They require a 64-bit (arm64-v8a) Android userspace** for the Box86/Box64
  emulators and the Wine ARM64 libs. The P610 runs **32-bit only**
  (`ro.product.cpu.abilist64` empty, `zygote32`) → the current Winlator/Mobox
  builds **cannot run**.
- Even on a 64-bit build, the 4×A55 @2.5 GHz is in the "light game" tier for
  x86 emulation – indie/2D/older 3D games, not modern AAA.

**Verdict:** not currently possible on this firmware. Would require a
64-bit userspace image (i.e. a rebuilt/unlocked firmware – out of scope).
Documented so the limitation is explicit.

## Performance context

- CPU: 4× Cortex-A55 @ 2.508 GHz, **32-bit** – roughly a mid-2020 mobile
  tier in 32-bit code.
- GPU: Mali-G310 (GL ES 3.2) – fine for 2D/3D up to ~720p-1080p moderate
  titles; not a high-end GPU.
- The 32-bit userspace caps both CPU and GPU shader/code paths.

## Open questions & planned experiments

- **USB gamepad test (next):** plug the Xbox Wireless Controller in via
  Micro-USB/USB-C tether (or a USB 2.4 GHz dongle) and verify it appears
  in `getevent -i` and is accepted by Steam Link / Lemuroid. If it works,
  close the BT-HID gap with a documented working path.
- **Second BT gamepad (important, no longer long-shot):** the R601
  remote proves the BLE HID path can work on this box, so a PS4/PS5 or
  8BitDo pad is a real candidate to close the gap. If it also fails at
  the same ~6 ms point, the bug is in the platform stack/firmware
  (`cqhci`/Realtek combo) and only an operator OTA could fix it.
- **Xbox retest with clean state:** DONE 2026-11-11 – factory reset
  + fresh pair; same GATT-discovery failure (no well-known services
  found, "no resources to open a new connection"). Stale-bond
  interference ruled out.
- **⏳ R601-contention test (NOT YET PERFORMED – most promising open
  fix, do this next):** with the R601 remote **not bonded**, pair
  the Xbox and watch the GATT-discovery log.
  1. Take the R601 out of the equation: either remove its batteries,
     or toggle Bluetooth off/on and act fast (the R601 re-bonds
     within seconds), or pair via ADB-driven picker
     (`am start -a android.bluetooth.devicepicker.action.LAUNCH` +
     `input keyevent` for DPAD/OK) so the remote is not needed.
  2. Put the Xbox in pairing mode and pair it through the picker.
  3. Check `logcat` for the discovery result: success = "well known
     GATT services" found + `HID_HOST` connected + a new device in
     `getevent -i`; failure = same "No resources to open a new
     connection" / "No well known GATT services discovered".
  4. Put the R601 back; verify it still bonds on its own.
  Expected outcome: if the Realtek/`cqhci` LE-connection budget is
  the limit, discovery completes without the remote and the Xbox
  works – a real fix for BT gamepads on this box.
- Benchmark Lemuroid across PS1/N64/NDS cores with a working gamepad
  (install approval needed).
- Moonlight client latency/quality over Wi-Fi 6 vs. Ethernet for a 4K60
  HEVC stream (needs a Sunshine host).
- Confirm whether any `arm64-v8a`-only games that would otherwise be
  desirable actually fail on this 32-bit build (to document the real-world
  impact of the ABI choice).
