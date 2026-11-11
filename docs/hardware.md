# Hardware

All values marked *(verified)* were read from the running device via ADB on
2026-11-11 (firmware `C4.0.7_20260605`). Values from the official datasheet are
marked *(datasheet)*. Where the two disagree, the ADB value wins.

## System-on-Chip

| Property | Value | Source |
|----------|-------|--------|
| SoC | Amlogic **S905X5M** | datasheet; `ro.boot.hardware=amlogic` *(verified)* |
| Reference design | `Amlogic/ross/ross` (`ro.build.reference.fingerprint`) | *(verified)* |
| Product board | `HP4CA` / `HP4CAX5M` (`ro.product.board`, `ro.build.flavor=HP4CAX5M_4K-user`) | *(verified)* |
| Manufacturer | Skyworth Digital (`ro.product.manufacturer`) | *(verified)* |
| Device codename | `HP4CD-Ocilion` / `HP4C06-Ocilion` | *(verified)* |
| SKU property | `ro.boot.product.hardware.sku=_OM_6010714` | *(verified)* |

The `HP4CAX5M` build flavor and the "X5M" in the board name are the strongest
on-device confirmation of the S905X5M. The `ross` codename is Amlogic's internal
name for this generation of 4K60 + AV1 reference platforms.

## CPU

- **4× ARM Cortex-A55** (datasheet: "4x A55 2.5 GHz").
- `scaling_max_freq` = **2508000 kHz** on all 4 cores, 4 cores online *(verified)*.
- `model name: ARMv8 Processor rev 0 (v8l)` – note `v8l`, i.e. the kernel exposes
  an ARMv8 (64-bit) CPU.
- The kernel is 64-bit (`armv8l`), **but the Android userspace is 32-bit**
  (`ro.zygote=zygote32`, `ro.product.cpu.abilist=armeabi-v7a,armeabi`,
  `ro.product.cpu.abilist64=` empty) *(verified)*.
- NEON, AES, PMULL, SHA1/SHA2, CRC32 present in `cpuinfo` feature list.
- Governor: `schedutil`.

```bash
adb shell 'ls /sys/devices/system/cpu/ | grep -E "^cpu[0-9]+$"'   # cpu0..cpu3
adb shell 'cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq'  # 2508000
```

## GPU

- **ARM Mali-G310** (datasheet) *(verified: `ro.hardware.egl=mali`, `mali0`
  device node, `init.svc.gpu=running`)*.
- OpenGLES version reported: `ro.opengles.version=196610` = **OpenGL ES 3.2**.
- Driver stack is the ARM GPU driver (`/vendor/lib/egl/libGLES_mali.so`);
  `/dev/dri/card0` + `renderD128` exist.
- The datasheet does not state the exact G310 variant (e.g. MP count);
  androidtv-guide lists it as "Mali-G310 V2". *(unverified on device)*
- Max SurfaceFlinger graphics: 1920×1080 (`ro.surface_flinger.max_graphics_*`)
  – UI rendering is capped at FHD; 4K is a direct video pipeline concern, not a
  UI one.

```bash
adb shell 'ls /dev/dri/; getprop ro.hardware.egl; getprop ro.opengles.version'
```

## Memory & storage

- **RAM**: `ro.boot.ddr_size=3584MB`, `MemTotal = 3763232 kB` (≈3.59 GiB usable
  out of the 4 GB DDR4) *(verified)*.
- **eMMC**: 32 GB (datasheet) *(capacity not readable by shell user, but the
  partition layout and a 25 GiB `/data` f2fs partition are consistent with 32 GB)*.

| Mount | Size | FS | Notes |
|-------|------|----|-------|
| `/` (system) | 523 MB | erofs (dm-16) | read-only, verified |
| `/product` | 403 MB | erofs (dm-20) | Google TV product partition |
| `/system_ext` | 56 MB | erofs (dm-17) | |
| `/vendor` | 92 MB | erofs (dm-19) | Amlogic vendor + media HALs |
| `/odm` | 4.4 MB | erofs (dm-21) | |
| `/vendor_dlkm`, `/odm_dlkm` | 24 MB / 276 KB | erofs | loadable kernel modules |
| `/data` | 25 GiB | f2fs (dm-57) | user data, apps |
| `/metadata` | 62 MB | f2fs | AVB/metadata |
| `/mnt/skyinfo` | 97 MB | ext4 | Skyworth service/info partition |
| `/mnt/vendor/factory` | vfat | – | factory calibration |
| `/mnt/vendor/param`, `/mnt/vendor/tee` | ext4 | – | vendor params / TEE |

AB (A/B) partitions are present: `boot_a/b`, `vendor_boot_a/b`, `vbmeta_a/b`,
`super`, plus `env` (slot management) and `misc` (boot control).

## Video decode / encode (hardware codecs)

From `/vendor/etc/media_codecs*.xml` *(verified)*. All are Codec2
(`c2.amlogic.*`) implementations; `max` sizes 4096×4096 and high
`blocks-per-second` limits indicate 4K60-class headroom.

| Codec | Decoder | Encoder |
|-------|:-------:|:-------:|
| H.264/AVC | ✅ (incl. **secure** variant) | ✅ |
| HEVC (H.265) | ✅ (incl. secure) | ✅ |
| VP9 | ✅ (4K) | – |
| AV1 | ✅ incl. **secure** variant (3840×2160) | – |
| AVS / AVS2 | ✅ (`c2.amlogic.avs*.decoder`) | – |
| Dolby Vision | ✅ (incl. DV-over-AV1 secure path `dolby-vision.dav1.decoder.secure`) | – |

Audio:

| Codec | Decoder |
|-------|:-------:|
| AC3, E-AC3 (Dolby Digital / Plus) | ✅ (incl. secure `c2.amlogic.audio.decoder.ac*`) |
| DTS, DTS-E, DTS-HD, DTS Ultra HD (Atmos) | ✅ |
| AC4 | ✅ |
| MP3, AAC, FLAC, Opus, Vorbis, WMA etc. | ✅ via `c2.amlogic.audio.decoder.ffmpeg` + `mp*`/`aac` |

Protected content: `FLAG_SUPPORTS_PROTECTED_BUFFERS` is set on the display and
the SoC exposes a TEE partition (`/mnt/vendor/tee`, `tee_comm_hdcp` misc node),
consistent with **Widevine L1** (see [firmware.md](firmware.md) for the DRM
service).

## Connectivity

### Wi-Fi

- Wi-Fi 6 *(datasheet)*. On device: **Realtek RTL8852BS** (SDIO)
  (`/sys/class/net/wlan0/device/driver → rtl8852bs`), connected on **5 GHz**,
  "Wi-Fi standard: 5", **866 Mbps** PHY (5 GHz, 2×2-class),
  MLO-aware supplicant code present *(verified via `dumpsys wifi`)*.
- `wlan0` interface; `p2p0` (Wi-Fi Direct) and `ap0` (soft AP) present.

### Ethernet

- `eth0` present, Amlogic internal MAC at `fe368000.ethernet` *(verified)*.
- Datasheet: **100 Mbit/s** (no GbE PHY on this board). *(link speed not
  measurable – port idle during testing)*
- No wake-on-LAN interface (`wake_on_lan` sysfs absent) – see
  [firmware.md](firmware.md) → Wake / "WOL" capability.

### Bluetooth

- BT 5.2 *(datasheet)*. Adapter reports **BT 5.x** features; classic + BLE,
  A2DP source/sink, AVRCP, HFP, HID host, PAN *(verified via
  `dumpsys bluetooth_manager`)*. HID host is what makes gamepads and the R601
  remote work.

### USB

- 4 USB controller nodes (`usb1..usb4`), at least one exposed as USB-A on the
  enclosure. USB host mode supports keyboard/mouse/gamepads; no storage was
  attached during testing.

### Audio out

- S/PDIF (optical) on the rear panel (datasheet + manual), plus HDMI audio.

## Display / HDMI

- HDMI **2.1b with HDCP 2.3** (datasheet) – AV1 decode + DV + 4K60 fits the
  48 Gbps budget.
- HDMI-CEC fully present: `/sys/class/cec/*` controls, `ro.hdmi.device_type=4`
  (playback device), ARC port detected, wake-on-CEC and
  `standby-on-sleep` support *(verified)*.
- Output modes seen in `dumpsys display`: 1080p @ 59.94/60/50/30 Hz and
  720p; the connected sink (an LG monitor) was limiting the EDID to 1080p
  during testing, so 4K mode could not be observed from ADB. 4K60 output is
  expected from the SoC and datasheet.
- UI is FHD-only (`max_graphics_*` 1920×1080); 4K is handled by the direct
  video path (VPU → HDMI), which is why UI quality is 1080p even in 4K mode.

## Enclosure / power (datasheet & manual)

- 100 × 100 × 21 mm; 12 V DC power.
- Power: ~0.05 W deep standby, ~0.5 W standby, ~2.5 W in 4K operation.
- Rear: HDMI, LAN, S/PDIF, power. Side: USB, reset button.
- **No SD card slot** (confirmed by the manual's connector list). This matters
  for Linux boot options – see [linux.md](linux.md).

## Remote (R601)

- 39 buttons, BLE + Google Voice Control support, per-app shortcut keys, 2×AAA.
- Companion app `com.ruwido.atv_ir` v3.0.15 is installed on the box
  (ruwido is the remote's developer).
