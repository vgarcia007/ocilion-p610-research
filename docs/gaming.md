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

**Controller support:** the BT HID host is present (verified) and the R601
remote works as a BT device; any standard BT/USB gamepad (XInput/DualShock/
3rd-party) should pair and be recognized by RetroArch/Lemuroid. Gamepad
input works via Android's standard `Input` system.

## Game streaming (the best "modern gaming" path)

The box is a strong **client** for streaming your own PC/console's output:

- **Moonlight** (NVIDIA GameStream / Sunshine) – Android TV build available;
  excellent latency over a wired or Wi-Fi 6 link. The box's HEVC/AV1 decode
  + 4K HDMI 2.1 output make it a good Moonlight *display* for a
  high-bitrate 4K60 HEVC stream from a PC running Sunshine.
- **Steam Link** (official) – Android TV app, works as a Steam streaming
  client.
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

- Pair a standard BT gamepad and benchmark Lemuroid across PS1/N64/NDS cores
  (install approval needed).
- Moonlight client latency/quality over Wi-Fi 6 vs. Ethernet for a 4K60 HEVC
  stream (needs a Sunshine host).
- Confirm whether any `arm64-v8a`-only games that would otherwise be
  desirable actually fail on this 32-bit build (to document the real-world
  impact of the ABI choice).
