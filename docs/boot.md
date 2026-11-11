# Boot Process & Bootloader

Deep dive into how the P610 boots, what the bootloader state is, and what that
means for flashing / recovery / alternative images. Based on live ADB
inspection (read-only) on 2026-11-11, firmware `C4.0.7_20260605`.

## Boot chain

Amlogic S905X5M (and the `ross` platform generally) boots through several
stages before Android even starts. What we can confirm on this device:

```
ROM (eFUSE-booted, in SoC)
  └─ XBL (Amlogic "XBOOT", first-stage loader, from eMMC)
       └─ U-Boot ("bootloader" – stored in `bootloader_a`/`bootloader_b`)
            │   reads the Amlogic `env` partition (boot options, slot selection)
            │   applies verified-boot checks on the boot images
            └─ init_boot_a + boot_a  (kernel + ramdisk / AVB hand-off)
                 └─ vendor_boot_a (vendor dtb/modules) + dtbo_a
                      └─ Android init → verified partitions (system/product/
                         vendor/odm via dm-verity) → zygote32 → UI6 launcher
```

Evidence from the device:

| Item | Value | Meaning |
|------|-------|---------|
| `ro.bootloader` / `ro.boot.bootloader` | `01.01.260605.182929` | Amlogic U-Boot build string (matches the firmware date, June 2026) |
| `ro.boot.hardware` | `amlogic` | Amlogic XBL/ATF boot |
| `ro.boot.boot_devices` | `soc/fe08c000.mmc` | eMMC is the boot device |
| `ro.boot.slot_suffix` | `_a` | A/B slot currently active |
| `ro.boot.vbmeta.device` / `.digest` | `/dev/block/vbmeta`, `f25edd…` (sha256, 11648 bytes) | AVB chain head verified by U-Boot |
| `ro.boot.verifiedbootstate` | `green` | full chain verified |
| `ro.boot.flash.locked` | `1` | **bootloader locked** |
| `ro.boot.securebootstate` | `1` | secure boot enabled |
| `ro.boot.dynamic_partitions` | `true` | super/`dm-*` logical partitions |
| `odsign.verification.success` | `1` | **Skyworth "odsign" extra partition signing check** ran and passed |
| `ro.boot.rpmb_state` | `1x1` | RPMB binding (rollback protection) |

## Partition map (from `/dev/block/by-name` symlinks)

Full GPT of the 32 GB eMMC, in partition order:

| # | Name | Role |
|---|------|------|
| p1 | `reserved` | Amlogic reserved area (XBL/ATF/BL31 blobs, per-partition sign data) |
| p2 | **`env`** | **Amlogic U-Boot environment** – boot options, slot/chain control (the "gpt_misc" equivalent) |
| p3 | `frp` | Factory reset protection (FRP) data |
| p4 | `factory` | factory calibration data (vfat, mounted at `/mnt/vendor/factory`) |
| p5/p6 | `vendor_boot_a/b` | vendor ramdisk/dtb/modules (A/B) |
| p7/p8 | **`bootloader_a/b`** | **U-Boot proper**, in A/B slots (A/B even at bootloader level) |
| p9 | `tee` | TEE (OP-TEE) image (mounted at `/mnt/vendor/tee`) |
| p10 | `logo` | boot logo (bootanimation assets also on `/mnt/skyinfo`: `bootup.bmp`, `bootanimation.zip`) |
| p11 | `misc` | Android `bootctl` data (A/B slot metadata, "recovery" commands) |
| p12/p13 | `dtbo_a/b` | dynamic DTBO |
| p14 | `cri_data` | CRI (component repair / calibration info) data |
| p15 | `param` | Amlogic params (mounted at `/mnt/vendor/param`) |
| p16/p17 | `odm_ext_a/b` | ODM ext (A/B) |
| p18/p19 | `boot_a/b` | kernel + AVB boot image (A/B) |
| p20/p21 | `init_boot_a/b` | init ramdisk (A/B) |
| p22 | `metadata` | AVB metadata (f2fs) |
| p23/p24 | `vbmeta_a/b` | AVB descriptors (A/B) |
| p25/p26 | `vbmeta_system_a/b` | system AVB chain |
| p27 | `super` | logical partitions: system, system_ext, product, vendor, odm, vendor_dlkm, odm_dlkm (erofs, dm-verity) |
| p28 | `skyinfo` | Skyworth service/info partition (boot logo assets, service data) |
| p29 | `rsv` | reserved #2 |
| p30 | `userdata` | `/data` (f2fs, 25 GiB) |

Notable: **no `recovery` partition** (Android 11+ "no-recovery" design – the
update path uses `init_boot`/`boot` + `update_verifier`), and the `logo`
partition is the Amlogic/Skyworth customization slot for the boot screen.

## Bootloader state: locked, and how it locks

- `ro.boot.flash.locked=1` → the **Amlogic flash lock** is engaged. With the
  lock set, U-Boot only boots **signed** images and rejects writes to the
  signed areas; the lock is tied to the SoC's eFuse/RPMB
  (`ro.boot.rpmb_state=1x1`).
- Verified boot is **green**: ROM → XBL → U-Boot → kernel → every logical
  partition (system/product/system_ext/vendor/odm/vendor_dlkm/odm_dlkm) is
  verified (sha256 root digests per partition, `partition.*.verified=2`).
- On top of AVB there is a second, vendor-specific signature layer:
  `odsign.verification.*` with per-partition root digests. So even a validly
  AVB-signed image would fail if it is not also signed by the OEM key.
- **No public unlock path.** `sys.oem_unlock_allowed=1` is a property the
  OEM's U-Boot exposes (some Amlogic boxes implement an unlock via
  fastboot `oem unlock` against an OEM key), but on this locked,
  production-secured unit unlocking would require the Skyworth/Ocilion OEM
  key flow. There is no Magisk-style or `fastboot flashing unlock` route
  (the box does not even expose standard AOSP fastboot – see below).

### Is there a fastboot / recovery mode at all?

- **No standard AOSP fastboot from `adb reboot bootloader`**: there is no
  `fastboot` partition, and the USB gadget configuration
  (`init.amlogic.usb.rc`) offers `adb`, `mtp`, `ptp`, `mass_storage`,
  `accessory`, `diag`, `dun` – but no `ffs.fastboot` function. Entering
  "fastboot" on Amlogic boxes happens in **U-Boot** (env-triggered or
  key-triggered), where U-Boot implements the Amlogic fastboot/upgrade
  protocol – not the Android `fastbootd` protocol.
- **No recovery mode in the classic sense**: no recovery partition, no
  `/system/bin/recovery`, no recovery image. The only recovery-related
  artifacts are the `misc` bootctl partition, `/cache/recovery` bookkeeping
  in `init.rc`, and the A11 `update_verifier_nonencrypted` init service
  (stopped). "Recovery"-style operations (apply update, wipe data) are
  done by the A/B **update_engine** flow, not by a standalone recovery.

## Update mechanism (how images *do* get written)

- Android-level: standard **A/B via `update_engine`** (Google payload),
  verified through `update_verifier` + AVB. Boot reason history shows real
  OTA reboots (`reboot,ota,…`). The A/B partition list is:
  `boot,bootloader,dtbo,init_boot,logo,odm,odm_dlkm,odm_ext,product,system,
  system_dlkm,system_ext,vbmeta,vbmeta_system,vendor,vendor_boot,vendor_dlkm`
  – note `bootloader` and `logo` are in the A/B OTA list, i.e. the OEM's
  OTA can update U-Boot and the boot logo through the normal signed payload.
- Vendor-level: Amlogic OTA tooling is present on the box:
  `/vendor/bin/ot-ctl-aml` (the "OT" = OTA controller) plus
  `update_verifier`. Amlogic's production pipeline burns full
  `upgrade_*.img` packages (containing GPT + all images, incl. XBL and
  U-Boot) via the **USB download (DNL) mode**, which is what Ocilion/Skyworth
  use for provisioning/RMA. DNL is entered pre-U-Boot (test-point or
  key-combo), speaks the Amlogic burning protocol over USB, and can write
  everything on the eMMC – **but it requires the board-specific entry
  sequence and, because of the flash lock + secure boot, the written images
  must be OEM-signed** (or the lock would have to be released first, which
  is an eFuse-level operation).

## Can we flash a recovery image / custom image? – realistic paths

| Path | Feasibility | Notes |
|------|-------------|-------|
| `adb reboot recovery` | ❌ | no recovery partition/image exists |
| `adb reboot bootloader` + `fastboot flash` | ❌ (as-is) | no AOSP fastbootd; Amlogic U-Boot fastboot protocol would be needed and the flash is locked |
| Signed OTA (operator) | ✅ (happens normally) | only operator-signed payloads are accepted |
| Amlogic USB DNL burn | ⚠️ theoretically yes | board DNL entry sequence unknown; images must pass flash-lock + secure boot (OEM-signed) – effectively only OEM/provisioning can use this safely |
| Unlock bootloader | ❌ (no public method) | `sys.oem_unlock_allowed=1` exists but needs the OEM key flow; eFuse/RPMB-backed |
| Boot unsigned custom kernel/recovery from `env` | ❌ while locked | U-Boot verifies the boot chain; `env` (p2) is not writable from Android (SELinux) and changing boot targets would fail the verified boot anyway |
| CoreELEC-style boot (see linux.md) | ⚠️ needs DNL or unlock | same blocker: a third-party boot image is not OEM-signed |

**Bottom line:** the P610 is a *fully closed* boot chain in the strongest
sense for this SoC generation: locked Amlogic flash, AVB green, an extra
OEM signature layer (odsign), RPMB rollback protection, and no AOSP-style
recovery/fastboot surface. The only write path that exists is the
**OEM-signed A/B OTA** and the **OEM burn path** (DNL), both controlled by
Ocilion/Skyworth. Anything else (custom recovery, custom kernel, CoreELEC on
eMMC) would require either (a) an OEM unlock/burn collaboration or (b)
opening the case and finding the DNL test point – and even then the
secure-boot signatures constrain what can actually boot.

This is why the debranding and Termux paths in the other docs (which stay in
`/data`, unsigned user space) are the only realistic modifications today.

## Open questions & planned experiments

1. Does U-Boot expose an Amlogic fastboot/upgrade mode on this board, and how
   is it triggered (env variable in `env` p2, button combo, or test point)?
   – would need the board schematics/service manual.
2. What is the exact DNL entry sequence for the P610 (12 V pin / test point /
   IR command)? Likely documented only in the Skyworth service manual.
3. Can `sys.oem_unlock_allowed=1` be exercised with a standard
   `fastboot oem unlock` against the Amlogic U-Boot fastboot protocol?
   (Would require first reaching that mode; expected to fail without the OEM
   key.)
4. Is the `env` partition (p2) format the standard Amlogic `env` (with a
   `bootcmd` chain)? Reading it requires root (`dd` is blocked by SELinux for
   the shell user) – a candidate experiment *if* root/DNL becomes available.
5. Compare the GPT/partition sizes against the Odroid C5 and X96 M300 (same
   SoC) to sanity-check the layout assumptions.
