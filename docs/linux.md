# Running Linux on the P610

**Summary:** CoreELEC is *feasible and officially supported for this SoC*;
Debian/Armbian are *not* officially supported for the S905X5M yet; Termux
works *inside* Android. The main obstacles specific to **this** box are the
**locked bootloader** and the **missing SD card slot**. Nothing destructive has
been attempted.

## What the hardware gives us

- SoC: Amlogic S905X5M (`s7d` platform family in Amlogic/CoreELEC naming).
- 4 GB RAM, 32 GB eMMC, internal Wi-Fi/BT (`aml_wifi`), 100 Mbit Ethernet,
  HDMI 2.1, USB host.
- **No SD card slot** (manual + datasheet). This rules out the "just boot an
  SD card image" path that is the default for most Amlogic boxes.
- Kernel 5.15.196 (Android) already on the SoC; CoreELEC 22 uses kernel
  5.15.196 for this generation — same kernel base, good sign.

## Option 1: CoreELEC (Kodi-based media OS) — ✅ supported SoC

- CoreELEC officially supports the S905X5M. From the CoreELEC device-tree
  table: `Devices with S905X5M → s7d_s905x5m_2g / _4g / _1gbit` device trees,
  and the download helper lists a "Devices with S905X5M" image. The closest
  commercial devices in the same family: **Odroid C5** (S905X5M, 4 GB) and
  **X96 M300 / Ugoos X5M Pro** (S905X5M, 4 GB, 1 Gbit).
- Our box is 4 GB RAM, 100 Mbit Ethernet → the
  **`s7d_s905x5m_4g` (100 MBit)** device tree is the likely candidate
  *(untested)*.
- Features you'd get: hardware video decode (H.264/HEVC/VP9/AV1), Dolby Vision
  passthrough (DV2/3 on Amlogic), AC3/E-AC3/DTS passthrough, Kodi 21/22.

### Boot problem on this specific box

CoreELEC normally boots from **SD card** (U-Boot detects the card and loads
`dtb.img` + the CE image). Without an SD slot, the options are:

1. **USB boot** – many Amlogic U-Boots can also chain-load from USB mass
   storage. Whether the P610's locked U-Boot permits booting from a USB
   drive is **unknown/untested**. The box has USB-A; a USB flash drive with
   the CoreELEC image is the lowest-risk experiment. (Requires the box to
   actually attempt USB boot in recovery/CFE mode — see below.)
2. **eMMC dual-boot** (the `coreelec-android-dualboot-twilight` approach) –
   this method (Xiaomi S905X4 boxes) rewrites the GPT of the internal
   eMMC, writes a small CE image + storage image, and manipulates the
   Amlogic **`env`/`misc`** partitions to gate boot between Android and
   CoreELEC. It **requires**: an unlocked bootloader + root (Magisk) to
   write the eMMC from Android, plus Amlogic `env` tooling. On the P610 the
   boot flash is **locked** (`ro.boot.flash.locked=1`, verified boot green),
   so the prerequisite (unlock) is **not currently satisfiable** → this path
   is **blocked for now**, documented for completeness.
3. **Amlogic USB DNL / burning mode** – every Amlogic SoC has a USB download
   (DNL) mode (often entered via a specific key/combo or the 12 V pin
   sequence) that allows flashing the eMMC with the Amlogic burning tools
   (`upgrade_tool`). This is the same path Skyworth/Ocilion uses for factory
   provisioning. It can write a full custom image (Android + CoreELEC
   partitions). This is the *realistic* "Linux on eMMC" path, but:
   - it requires the DNL entry sequence for this exact board (unknown,
     typically a test-point short or a special remote/IR command on
     operator boxes), and
   - it **flashes the device** → outside the current safety constraints.
   Documented here as the expected route, **not yet attempted**.

**Assessment:** CoreELEC on this box = *SoC-supported, board-path unknown*.
USB-boot experiment (read-mostly risk: worst case it just doesn't boot) is
the first candidate; DNL burning is the definitive solution and needs
approval.

## Option 2: Debian / Ubuntu / Armbian — ⚠️ not supported for S905X5M

- Armbian does **not** list the S905X5M (its Odroid C5 page does not exist;
  supported Amlogic boards stop at older SoCs, e.g. aml-c400-plus).
- No mainline-kernel distro currently ships an S905X5M board image.
- A from-scratch build (mainline 6.x kernel + Amlogic `s7d` dts + U-Boot +
  rootfs) is possible in principle — Amlogic's own SDK ships the full boot
  chain and DTBs for `s7d` — but that's a project, not an evening task.
  **Assessment: not feasible short-term; track CoreELEC's `Amlogic-ng`
  kernel (5.15) which is the de-facto community base for these SoCs.**

## Option 3: Termux inside Android — ✅ works today (no booting needed)

- Termux `armv7` packages can be installed from F-Droid/Izzy (`termux.apk`,
  `armeabi-v7a` variant) — the 32-bit userspace constraint applies: use the
  v7a build. *(install not yet performed — requires approval)*
- `proot-distro` then provides a chroot-like Debian/Ubuntu userspace on top
  of the Android kernel: no root needed, pure `/data` installation,
  reversible by uninstalling Termux.
  - Caveats: 32-bit userspace inside (unless a 64-bit proot image runs under
    a 64-bit kernel — proot can emulate, but performance and some binaries
    are limited); kernel is 5.15 (no issues for userspace distros);
    no hardware acceleration passthrough; Wi-Fi/Ethernet are available
    normally since it's the Android network stack.
- **Termux:X11** (the `termux-x11` package + X11 server for Android) gives a
  desktop experience (X11 apps, and with an X11 client on a PC, remote X).
  This works *regardless of the 32-bit userspace* as long as the v7a
  variant is used. *(untested on this box)*
- This is the **only Linux-ish option that can be tried immediately** inside
  the current read-only-safety model (it only installs user apps).

## Option 4: Alternative boot paths / bootloader

- **Bootloader:** Amlogic XBL/U-Boot, locked, verified. No public unlock
  method for this board/OEM exists (unlike e.g. some Xiaomi boxes). The
  `sys.oem_unlock_allowed=1` property is present but `ro.boot.flash.locked=1`
  and verified-boot green mean it is not usable from fastboot without an
  OEM key.
- **DNL mode:** present on the SoC (USB download). Entry method unknown for
  this board; flashing from DNL is the standard provisioning path (Ocilion
  themselves use it for RMA/provisioning). Detailed boot-chain and
  bootloader analysis: [boot.md](boot.md).
- **SD slot:** none → no standard CoreELEC/Armbian SD-boot.

## Feasibility matrix

| Option | Feasible today? | Blocker | Risk |
|--------|----------------|---------|------|
| CoreELEC via USB boot | Unknown (SoC supported) | U-Boot USB-boot behavior unknown; no board-specific DTB test | Low (worst case: no boot, back to Android) |
| CoreELEC on eMMC (dual-boot) | **No, currently** | Locked bootloader + no root for eMMC writes | High (if attempted without unlock) |
| CoreELEC/Android custom image via DNL | Possible with approval | DNL entry sequence unknown; flashing | High (brick possible) |
| Debian/Armbian | **No** | No S905X5M distro support | – |
| Termux (+proot-distro, +Termux:X11) | **Yes** (needs APK install approval) | 32-bit userspace limits some packages | Very low (user-space only, reversible) |

## Open questions & planned experiments

1. Does the U-Boot chain-load from USB? (Experiment: boot with a prepared
   USB drive; observe serial/console output if available.)
2. What is the DNL entry sequence for the P610? (Likely a 12 V pin test
   point; would need opening the case — *needs approval*.)
3. Can CoreELEC's `s7d_s905x5m_4g` DTB be confirmed to match this board's
   Wi-Fi/BT and eMMC layout? (Diff against the `HP4CAX5M` vendor DTB if a
   DTB can be legally obtained; otherwise trial.)
4. Termux v7a + proot-distro (Debian 12 32-bit) + Termux:X11: does it give a
   usable desktop? (Install approval needed.)
5. Is there an Odroid C5 community that has already cracked eMMC dual-boot
   for S905X5M? (Research task; the C5 is the closest public S905X5M board.)
