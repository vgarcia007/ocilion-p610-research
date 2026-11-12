# Firmware & Software

Everything below was read from the running device via ADB on 2026-11-11.

## Build identification

```
ro.build.fingerprint   = Ocilion-WL/HP4C06-Ocilion/HP4CD-Ocilion:14/UKG3.250826.001/C4.0.7_20260605:user/release-keys
ro.build.description   = HP4CAX5M_4K-user 14 UKG3.250826.001 C4.0.7_20260605 release-keys
ro.build.type          = user
ro.build.tags          = release-keys
ro.build.host          = sky17188        (Skyworth build host)
ro.build.version.incremental = C4.0.7_20260605
ro.build.version.security_patch = 2026-04-05
```

- Android **14**, API level 34.
- `user`/`release-keys` build → production image, no `test-keys`, no
  test-type build features. ADB is available only because the operator
  enabled it (developer mode), not because of build type.
- `ro.boot.build.id = UKG3.250826.001` is a Google platform build ID
  (UKG3 = Android 14 U-base release line), so the base is close to the
  August 2025 AOSP TV release.
- Reference fingerprint: `Amlogic/ross/ross:14/UKG3.250826.001/13993950:user/release-keys`
  – i.e. the firmware is a Skyworth build of an **Amlogic `ross` reference
  AOSP tree**, with a Google TV "product" partition stacked on top.

## Kernel

```
Linux version 5.15.196-android14-11-g3d26c188ef7e-ab14657224
  (Android clang 17.0.2, +pgo +bolt +lto)
  SMP PREEMPT, built Sat Jan 3 21:52:40 UTC 2026
```

- Mainline-derived **5.15** LTS base with Android patches, same family CoreELEC
  uses for this SoC generation (CoreELEC 22 kernel = 5.15.196).
- `ro.boot.boot_devices = soc/fe08c000.mmc` – the eMMC is the boot/storage
  device.
- Kernel exposes the SoC as 64-bit ARMv8; the userspace is deliberately 32-bit
  (see [hardware.md](hardware.md) → CPU).

## Boot, A/B, Verified Boot

> For the full boot-chain analysis (XBL → U-Boot → Android), the complete
> partition map, and the flashing/recovery feasibility assessment, see
> [boot.md](boot.md).

| Property | Value | Meaning |
|----------|-------|---------|
| `ro.boot.verifiedbootstate` | **green** | fully verified boot chain |
| `ro.boot.flash.locked` | **1** | **bootloader is locked** |
| `ro.boot.vbmeta.device_state` | locked | AVB state locked |
| `ro.boot.veritymode` | enforcing (+ `managed=yes`) | dm-verity active on all partitions |
| `ro.boot.selinux` | enforcing | SELinux enforcing |
| `ro.boot.slot_suffix` | `_a` | A/B slots; currently on slot A |
| `ro.boot.avb_version` | 1.2 | AVB 1.2 |
| `ro.boot.rollback` | 0 | rollback protection level |
| `ro.boot.rpmb_state` | 1x1 | RPMB binding (rollback security) |
| `odsign.verification.*` | success | **ODM signing verification** (Skyworth "odsign" root-digest check per partition, sha256) |
| `ro.secure` | 1 | secure mode (no unsigned shells) |

All major partitions (`system`, `product`, `system_ext`, `vendor`, `vendor_dlkm`,
`odm`, `odm_dlkm`) carry a `partition.<name>.verified` flag with a SHA-256
root digest – the SoC boot ROM verifies them in hardware before handing off to
Android. This means:

- **No custom kernels, no Magisk-style patching, no system modifications** are
  possible without either an unlocked bootloader (not available here – the
  boot flash is locked and `sys.oem_unlock_allowed=1` is only a *capability*
  flag, not a working unlock) or the proprietary Amlogic burning path.
- A/B slots + `env` partition = standard Android A/B update flow.

### Bootloader

- `ro.bootloader = 01.01.260605.182929` (Amlogic U-Boot style build string,
  June 2026).
- Amlogic boxes of this generation normally boot: `XBL → U-Boot (bl1/bl2) →
  env/misc → Android boot_a`. The **`env`** (GPT/params) and **`misc`**
  partitions are the Amlogic-specific boot control surface (this is also what
  the `coreelec-android-dualboot-twilight` project manipulates on other
  Amlogic boxes).

## Security model summary

- Verified boot green + locked + dm-verity + SELinux + user build = a
  **hardened operator appliance**, not a developer device.
- ADB: `ro.adb.secure=1`, connected as `shell` (uid 2000), **no `su`** binary,
  no root. `ro.oem_unlock_allowed` exists but unlocking requires an
  Amlogic/Skyworth-specific flow that is not exposed in this firmware.
- DRM: Widevine APEX present (`/apex/com.google.android.widevine`),
  `init.svc.drm` + `mediadrm` running, TEE + `tee_comm_hdcp` nodes present,
  protected buffers enabled → **Widevine L1** (see open question in
  [android.md](android.md)).

## Update mechanism

- **Standard Android A/B OTA via `update_engine`** (`init.svc.update_engine =
  running`), with `ro.build.ab_update=true`. The boot-reason history shows
  real OTAs: `reboot,ota,...` entries.
- OTAs are delivered by the **operator** (Ocilion provisioning) and, on a
  provisioned device, through their backend. Because the box is provisioned,
  firmware updates arrive via the operator pipeline, not directly from Google.
- GMS updates: the `gms` partition is part of the OTA (`gmsversion =
  S_amlogic`), i.e. Google's SafetyNet-style "GMS system update" is folded
  into the OEM image; the box is not a direct "Google system updater" device.
- There is no user-visible "check for updates" outside the operator flow;
  Settings shows operator-controlled update UI.

## Power / standby behavior

The box aggressively enters standby when idle, which is relevant for any
long-running ADB work (in standby, Wi-Fi goes dormant and `adbd` stops
listening, so the ADB-over-Wi-Fi connection drops and must be re-established).

Controls observed on the device (2026-11-11):

| Setting | Default on this device | Effect |
|---------|------------------------|--------|
| `settings secure sleep_timeout` | `10800000` (3 h); `-1` = off | Idle timer before the box goes to standby. UI path: **Einstellungen → Systemeinstellungen → Geräteeinstellungen → "Timer zum Ausschalten"** |
| `persist.sys.standby.mode` | `1`; `0` = off | Amlogic standby mode, enforced by the vendor daemons (`systemcontrol` / `system_suspend` / `magic_wake_daemon`). Writable via the UI (system privileges) but not by the ADB shell (SELinux) |
| `persist.sys.hdmi.keep_awake` | `false` | Amlogic keep-awake flag for the HDMI/display path |
| `settings secure stay_on_while_plugged_in` | was `2` (USB) on this AC-powered box → ineffective; now `1` (AC) | standard Android "stay awake while plugged in" bitmask (1=AC, 2=USB, 4=wireless, 8=dock); survives reboot (stored in `/data`) |

To keep the box awake for research sessions (reversible, user-space only):

```sh
adb shell settings put secure sleep_timeout -1            # disable idle power-off timer
adb shell setprop persist.sys.standby.mode 0              # (needs system privileges / UI)
adb shell setprop persist.sys.hdmi.keep_awake true        # (needs system privileges / UI)
```

Or, the cleaner route: use the operator Settings UI —
**Einstellungen → Systemeinstellungen → Geräteeinstellungen → "Timer zum
Ausschalten"** — and set all timers to off. This writes both
`sleep_timeout` (to `-1`) and `persist.sys.standby.mode` (to `0`).

Restore with: `sleep_timeout 10800000` (or via the UI), and
`persist.sys.standby.mode 1` via the UI. (The `persist.*` props survive
reboot; the `settings` value is stored in `/data`.)

> **Tested 2026-11-11:** `settings put secure sleep_timeout …` succeeds from
> the ADB shell. The two `persist.*` properties are **SELinux-protected and
> cannot be written by the shell user** (`setprop` → "Failed to set
> property"), but they **can** be changed through the operator Settings UI,
> which runs with system privileges.
>
> **Result of the UI path (applied, verified):** the operator Settings at
> *Einstellungen → Systemeinstellungen → Geräteeinstellungen → Timer zum
> Ausschalten* ("Timer zum Ausschalten" = power-off timer) map to the
> `sleep_timeout` setting and the vendor standby mode. After setting all
> timers to "off" in the UI, the device reports:
> `settings secure sleep_timeout = -1` (disabled; the UI uses `-1`, not a
> large value) and `persist.sys.standby.mode = 0`. The box no longer enters
> standby automatically, and the ADB-over-Wi-Fi connection stays stable.
> Note: the UI overrode the ADB-set `sleep_timeout 2147483647` with the
> cleaner `-1` — both mean "disabled".

### Keep-awake for the headless test box (applied, 2026-11-12)

The P610 in this repo's testing setup runs **headless under a desk**
(no display), so it must never drop the display into sleep. Two
mechanisms were identified:

- **Display screen-off timeout** – standard Android
  (`mScreenOffTimeoutSetting=900000`, 15 min inactivity → display
  off, box stays up; `standbyMode=sleep` log entries are the
  *effect* of screen-off, not a separate vendor idle timer – none
  found; `xt_idletimer` exists but is empty/inactive).
- **`svc power stayon` / `settings secure stay_on_while_plugged_in`**
  – "keep awake while plugged in" (bitmask: 1=AC, 2=USB, 4=wireless,
  8=dock).

**Bug found:** the setting was **`2` (USB)**, but this box is
**AC-powered** (`dumpsys battery`: AC powered: true, USB powered:
false) – the stay-on therefore never applied. Fixed with:

```sh
adb shell svc power stayon ac        # writes settings secure stay_on_while_plugged_in=1
```

**Status (active, intended permanent):** `stay_on_while_plugged_in=1`
(AC), runtime `mStayOn=true`. The setting is stored in `/data`
(`settings secure`), so it **survives reboots**. Combined with the
UI-disabled idle timers (`sleep_timeout=-1`,
`persist.sys.standby.mode=0`), the headless box stays fully awake
indefinitely as long as it is on AC power.

Restore (if ever wanted): `adb shell svc power stayon false`
(→ `stay_on_while_plugged_in=0`).

Note: a "trigger input every N minutes" keep-awake script was
considered and rejected as the primary mechanism – it is clunky
(real input events wake apps) and would not survive reboot without
a boot autostart, which the locked build does not expose.
`svc power stayon ac` is the clean, official keep-awake path.

## Wake / "WOL" capability

"Can the box be woken remotely?" – the answer depends on what "WOL" means:

| Wake path | Supported? | Evidence |
|-----------|-----------|----------|
| **CEC / HDMI-ARC wake (TV "system standby" / routing control)** | ✅ yes | `ro.hdmi.cec.source.playback_device_action_on_routing_control = wake_up_and_send_active_source` – when the TV sends a CEC routing-control request (e.g. source switch or TV wake), the box **wakes up and announces itself as active source**. CEC is the practical "WOL" for this class of device: it keeps a low-power state with the CEC line alive (HDMI HPD/CEC pins, SoC `dhp` block). |
| **CEC "send standby on sleep"** | ✅ yes | `ro.hdmi.cec.source.send_standby_on_sleep = broadcast` – the box tells the TV to go to standby too when it sleeps (coordinated standby). |
| **Network WOL / magic packet on Ethernet** | ❌ no (not detectable) | `eth0` (Amlogic internal MAC) has **no `wake_on_lan` sysfs attribute**, and no `ethtool` wake flags are exposed. A 100 Mbit internal MAC without a WoL sysfs interface does not implement classic magic-packet wake. |
| **WoWLAN (wake via Wi-Fi packet)** | ⚠️ unverified / likely not exposed | Wi-Fi is a **Realtek RTL8852BS** (SDIO) chip (`/sys/class/net/wlan0/device/driver → rtl8852bs`). The driver parameters directory is empty/not readable from the shell, and in deep standby the box drops Wi-Fi (which is why ADB-over-Wi-Fi disconnects) – there is no user-visible WoWLAN toggle. |
| **IR / remote wake** | ✅ yes (expected) | Standard for the platform: the CEC/IR receiver path plus the running `magic_wake_daemon` (binary is SELinux-protected, not analyzable from the shell) indicate wake-on-remote support. The R601 remote can wake the box. |
| **BT wake** | ⚠️ unverified | `magic_wake_daemon` may include BT HID wake, but not confirmable without the binary. |

**Practical conclusion:** to wake the P610 remotely, use **CEC** (from the
TV's HDMI-CEC menu / source selection, or an HDMI-CEC USB adapter + a
controller like `cEC-client`), or the **IR/remote path**. There is no
Ethernet magic-packet WOL and no exposed WoWLAN setting.

## Running init services (47 at boot)

Notable services (full list obtainable via `scripts/adb-info.sh`):

- Standard Android: `zygote`, `surfaceflinger`, `audioserver`, `media*`,
  `vold`, `netd`, `installd`, `keystore2`, `gatekeeperd`, `statsd`, `storaged`,
  `update_engine`, `logd`/`logpersist`, `tombstoned`, `traced`, `cameraserver`
  (no camera hardware, but service runs), `credstore`, `prng_seeder`, `incidentd`.
- Amlogic vendor: `gpu`, `dhp_daemon` (Amlogic DHP = display/HDMI pipeline),
  `magic_wake_daemon`, `mdnsd` (Chromecast/mDNS discovery for casting),
  `droidaudio` (Skyworth audio daemon), `boardinfo`, `system_control`,
  `system_suspend`, `hidl_memory`, `android-hardware-media-c2-hal-1-2`.
- `bi_client` – Biometric/Id client (part of the Google partner setup stack).

`mdnsd` being present is what enables **Chromecast-style casting** discovery
on the box.

## Device Tree

`/proc/device-tree` is partially restricted for the shell user (SELinux), but
the SoC node listing confirms the platform: `amhdmitx` (HDMI TX),
`aml_wifi` (integrated Wi-Fi/BT), `amvenc_avc` (hardware video encoder),
`aml_bt`, `aucpu` (audio SoC), `amlogic-dt-id`, and 2 VPU nodes
(`amlvideo2_0/1`) consistent with an S905X5-class media pipeline.
The full DTB itself is not readable from the shell (normal for secure
bootloader images).

## Open questions & planned experiments

- **Exact Widevine level**: L1 is strongly implied (TEE + protected buffers +
  L1 APEX), but confirming it requires a DRM probe app (e.g. the Widevine
  License Checker / a Netflix 4K test), which needs installation approval.
- **HDMI 2.1 / eARC / 4K60+DV output**: verify with an HDMI 2.1 sink and a
  DV 4K60 stream (e.g. a known DV profile-5 file on local storage).
- **Ethernet link speed**: confirm 100 Mbit with a connected link.
- **OTA content**: whether operator OTAs carry Google mainline module updates
  (APEX) or only full images – observe over time.
