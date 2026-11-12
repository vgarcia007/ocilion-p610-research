# Ocilion P610 – Research & Documentation

> **Unofficial project.** This repository is an independent research effort. It is
> not affiliated with, endorsed by, or sponsored by Ocilion IPTV Technologies GmbH,
> Skyworth, or Google. All trademarks belong to their respective owners.

## What is the Ocilion P610?

The Ocilion P610 is an IPTV set-top box sold to European cable/IPTV operators
under their own branding (white label). It ships with Android TV 14 "Operator Tier"
and Ocilion's **UI6** launcher as the default user interface.

The box is manufactured by **Skyworth Digital** on top of an
**Amlogic S905X5M** reference design (internal codename `ross`). A full AOSP-based
Android TV system with the standard Google TV component set is present underneath
the Ocilion skin – the box behaves much more like a stock Google TV device than
most operator boxes.

> **Market / operator context.** The unit researched here is provisioned for the
> **German market** and branded for the operator **komro GmbH**. Findings that
> depend on operator provisioning – the PAI app list (German broadcasters:
> ARD/ZDF/RTL+/tagesschau/3sat/arte/toggo/heute/seventv), launcher branding,
> OTA behavior, and some Settings-UI wording – are **market- and
> operator-specific**. Boxes in other markets or with other operators are
> based on the same Skyworth/Ocilion hardware and firmware family, but may
> ship different pre-installed app sets, language defaults, and provisioning
> backends. Hardware, boot/security, and debranding conclusions in this repo
> are not market-dependent; the app-policy findings are. Known operators
> using the P610 are tracked in [docs/operators.md](docs/operators.md).

## Key specifications (verified via ADB where noted)

| Item | Spec |
|------|------|
| SoC | Amlogic S905X5M (`ro.boot.hardware=amlogic`, ref. design `Amlogic/ross/ross`) |
| CPU | 4× ARM Cortex-A55, 2.508 GHz max (`cpuinfo_max_freq=2508000`, 4 cores online) |
| GPU | ARM Mali-G310 (EGL: `ro.hardware.egl=mali`, OpenGLES 3.2) |
| RAM | 4 GB DDR4 (`ro.boot.ddr_size=3584MB`, `MemTotal` ≈ 3.76 GiB) |
| Storage | 32 GB eMMC (`/data` = 25 GiB f2fs, ~23 GiB free) |
| OS | Android 14 (SDK 34), build `UKG3.250826.001.C4.0.7_20260605` |
| Security patch | 2026-04-05 |
| Kernel | 5.15.196-android14 (64-bit) – but the **userspace is 32-bit** (see below) |
| Video decode | H.264, HEVC, VP9, AV1, Dolby Vision; secure variants available |
| Audio | AC3, E-AC3, DTS family, TrueHD, AC4, MP3/AAC/FLAC/Opus via FFmpeg path |
| Wi-Fi | Wi-Fi 6 (5 GHz verified, 866 Mbps PHY) |
| Bluetooth | 5.x, A2DP/AVRCP/HID profiles |
| Ethernet | 1× RJ45 100 Mbit (`eth0`) |
| Display out | HDMI 2.1b w/ HDCP 2.3, 4K, HDMI-CEC (device type: playback) |
| Other | USB-A, S/PDIF, 12 V power |
| DRM | Widevine L1 (APEX), protected-buffers support, `odsign` ODM verification |
| Boot | A/B slots, verified boot **green/locked**, dm-verity enforcing, no root |

### ⚠️ Important: 32-bit userspace

Despite the 64-bit kernel (`armv8l`, `arm64` capable SoC), the Android userspace
runs as **32-bit only**:

```
ro.product.cpu.abilist = armeabi-v7a,armeabi
ro.zygote              = zygote32
getconf LONG_BIT       = 32
```

Consequences:
- Only `armeabi-v7a`/`armeabi` native libraries run. `arm64-v8a`-only APKs are
  not installable/launchable.
- Termux must be used in its `v7a` build; some arm64-only prebuilt packages
  (e.g. some `proot-distro` images) need the 32-bit variant or will fail.
- Benchmarks and app performance are bound to 32-bit codegen on a 64-bit kernel.

This is a deliberate choice by the OEM/operator firmware, not a hardware limit.

## Research goals

1. **Characterize the hardware and firmware** accurately, using the live device
   (ADB) rather than marketing material.
2. **Understand the Android/operator layer**: what Ocilion ships, what stock
   Google TV pieces are present, and how locked down the system is.
3. **Debranding**: evaluate how far the box can be turned into a "normal"
   Android/Google TV box (launcher replacement, hiding operator components),
   and what is reversible.
4. **Linux**: assess the feasibility of running CoreELEC / Debian / Armbian on
   the S905X5M, including boot options on this exact box.
5. **Gaming**: what the box can do for retro, emulation, game streaming, and
   ARM64 compatibility layers.

## Current state of findings (summary)

- The box is **Skyworth-built on Amlogic `ross`**, running a recent **AOSP 14 TV
  image with the full Google TV component set** (GMS, YouTube TV, Play Games,
  settings, TV framework) – but **without stock Android TV Home**. The Ocilion
  **UI6** app (`com.ocilion.p6`, a Flutter app) is the launcher.
- Security is **fully locked**: verified boot green, boot flash locked, dm-verity
  enforcing, SELinux enforcing, no root. ADB is available at `shell` level only.
- The operator enforces a **two-class app policy**: Netflix/Prime Video/
  Disney+/Play Store/UI6 are **system apps** (not uninstallable, but
  removable per user via `pm uninstall -k --user 0` without root), while
  the German broadcaster PAI apps are **plain user apps** (fully
  uninstallable). An OTA can re-assert the PAI list. See
  [docs/android.md](docs/android.md).
- The **Play Store** is present and enabled, and its UI6 app-list tile is
  **account-gated** (verified: hidden without a Google account, appears
  after login) – launchable without the tile via
  `am start -n com.android.vending/com.google.android.finsky.tvmainactivity.TvMainActivity`.
  The ADB shell holds `INSTALL_PACKAGES`, so APKs can be sideloaded with
  `pm install` (32-bit `armeabi-v7a` only). Full pre-installed app catalog
  (25 apps) in [docs/android.md](docs/android.md).
- **Debranding looks very promising** for the "use it as a normal TV box" goal:
  the launcher is a regular user-level app in `/data`, Google TV pieces are all
  present, and the standard, *reversible* `pm disable-user` path applies. No
  root is needed to swap the default home app (see [docs/debranding.md](docs/debranding.md)).
- **Linux is feasible**: CoreELEC officially supports the S905X5M
  (`s7d_s905x5m_*` device trees). The hard parts on this box are the **locked
  bootloader** and the **absence of an SD card slot** – see
  [docs/linux.md](docs/linux.md) for the full assessment.
- **Gaming** is solid for retro/up to mid-gen console emulation and game
  streaming; native Steam-on-ARM is not realistic on a 32-bit userspace.
  The BT-gamepad pairing bug (GATT discovery fails while the bundled
  remote is connected) is **root-caused and has a verified workaround**
  (pair with the remote off). See
  [docs/gaming.md](docs/gaming.md) and
  [docs/bug-reports/](docs/bug-reports/).

## Documentation

| Document | Contents |
|----------|----------|
| [docs/hardware.md](docs/hardware.md) | SoC, CPU, GPU, memory, storage, video/audio codecs, connectivity, display, verified via ADB |
| [docs/firmware.md](docs/firmware.md) | Build, kernel, partitions, verified boot, security, update mechanism, services |
| [docs/boot.md](docs/boot.md) | Boot chain (XBL → U-Boot → Android), full partition map, bootloader lock state, flashing/recovery feasibility |
| [docs/android.md](docs/android.md) | The Android layer: installed components, Ocilion apps, Google services, 32-bit ABI |
| [docs/debranding.md](docs/debranding.md) | Launcher analysis, what's missing/extra vs. stock, reversible debranding plan, test plan |
| [docs/linux.md](docs/linux.md) | CoreELEC / Debian / Armbian / Termux feasibility, boot methods, open risks |
| [docs/gaming.md](docs/gaming.md) | Retro emulation, gamepads, game streaming, ARM64 layers |
| [docs/bug-reports/](docs/bug-reports/) | BT gamepad GATT bug: root cause, verified workaround, vendor report, ready-to-send tickets; plus the keep-awake default improvement suggestion |
| [docs/operators.md](docs/operators.md) | Known operators/providers deploying the P610 (evidence-graded) |
| [docs/rooting.md](docs/rooting.md) | Rooting: current state, realistic paths, watch list |
| [docs/links.md](docs/links.md) | Official sources, datasheets, and related projects |

## Tooling

- [scripts/adb-info.sh](scripts/adb-info.sh) – a single, dependency-free script
  that collects the relevant technical information from a connected device.

## Safety / constraints of this research

- Only **read-only ADB commands** were used against the device so far.
- Nothing has been flashed, rooted, unlocked, reset, or modified.
- No serial numbers, MAC addresses, tokens, credentials, or operator-internal
  URLs are published in this repository. Device-specific identifiers collected
  during research were stripped before documentation.

## Open questions / next experiments

See the "Open questions & planned experiments" section at the end of each
document. Highlights:

- Confirm the exact Widevine security level (L1) with a DRM probe app.
- Validate the reversible launcher swap end-to-end (requires explicit approval).
- Map the Amlogic U-Boot `env` partition (p2) format and the DNL entry
  sequence for the P610 – see [docs/boot.md](docs/boot.md) for the boot-chain
  analysis.
