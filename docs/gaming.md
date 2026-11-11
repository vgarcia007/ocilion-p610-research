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

### Bluetooth HID: broken for gamepads on this firmware (tested 2026-11-11)

**Test:** Xbox Wireless Controller (modern, BLE-capable) paired against the
box through the Settings UI (*Einstellungen → Systemeinstellungen →
Bluetooth → Zubehör hinzufügen*).

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

**Interpretation:** the platform BT stack (Realtek, SDIO, shared with the
RTL8852BS Wi-Fi chip) implements only the *simple* HID/keyboard profile
enough for the R601 remote (which shows up as "Ocilion BLE Keyboard"), but
the **full HID Report-Descriptor path used by gamepads fails immediately**.
This is a known pattern on cheap Android TV boxes: the R601/keyboard works,
gamepads don't. The failure is on the box side, not the controller.

**Practical conclusion: do not rely on Bluetooth for a gamepad on this box.**

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
- **Second BT gamepad (long-shot):** try a PS4/PS5 or 8BitDo pad via
  Bluetooth to check whether the HID profile failure is Xbox-specific or
  general (the log evidence points to general).
- Benchmark Lemuroid across PS1/N64/NDS cores with a working gamepad
  (install approval needed).
- Moonlight client latency/quality over Wi-Fi 6 vs. Ethernet for a 4K60
  HEVC stream (needs a Sunshine host).
- Confirm whether any `arm64-v8a`-only games that would otherwise be
  desirable actually fail on this 32-bit build (to document the real-world
  impact of the ABI choice).
