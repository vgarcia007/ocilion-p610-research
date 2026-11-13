# Rooting the P610 – status and realistic paths

Question: can this box be rooted? Short answer: **not with any known
public method today**, but there is an active ecosystem for the *chip*
and several concrete avenues to track. This page tracks the state of
the art (researched 2026-11-11) and what would actually have to happen.

## Where this box stands (verified)

| Fact | Value | Consequence |
|------|-------|-------------|
| `ro.boot.flash.locked` | `1` | Amlogic flash lock engaged; U-Boot only boots OEM-signed images |
| `ro.boot.verifiedbootstate` | `green` | full AVB chain OK, all partitions verified |
| `ro.boot.rpmb_state` | set | rollback protection active |
| `odsign.verification.*` | per-partition digests | **second, OEM-specific signature layer** on top of AVB |
| `sys.oem_unlock_allowed` | `1` | property exists, but **no public unlock command works** (no AOSP fastboot on the box at all) |
| `adb root` | `adbd cannot run as root in production builds` | no debug build |
| setuid binaries / su / Magisk | none present | no existing root surface |
| SELinux | `Enforcing` | shell is sandboxed |

So this is a **locked operator box** (komro provisioning), which is the
harder case than consumer Amlogic boxes.

## The ecosystem for this exact chip (S905X5M)

- **slimBOXtv** (slimboxtv.ru, active, XDA thread "project slimBOXtv")
  ships **rooted (Magisk) Android 11/14 builds for the S905X5M/S905A
  family** – X96 M300, X96 M200, Vontar X5, Tanix TX5 Plus 2025, M11
  Max, Ugoos AM9/AM9 Pro. These are **consumer boxes that ship with
  unlockable/unsigned flash** – on them the flow is "ADB on, unlock via
  PC/USB, flash slimBOXtv (pre-rooted Magisk)".
- Notably, the Vontar X5's stock base is named
  `ross_009_VONTAR_X5_…_14` – **`ross` is the same Amlogic reference
  codename this P610 runs** (`ro.product.device`/reference `Amlogic/ross/ross`).
  So the P610 is essentially an *operator-locked variant of the same
  reference design* that slimBOXtv already supports in unlocked form.
- The **Xiaomi s7d/twilight** project (CoreELEC dual-boot research)
  documents what a *rooted, bootloader-unlocked* s905x5m-class device
  enables: writing `env`, `boot_a/b`, GPT surgery, Magisk modules, etc.
  It is the best public reference for "what root unlocks on this SoC".

## Realistic paths, ordered by feasibility

1. **Exploit the running system (most promising, no hardware).**
   The box runs a large vendor attack surface (`systemcontrol`,
   `ot-ctl-aml`, `magic_wake_daemon`, `rcupair`, update_engine, ADB
   shell in an SELinux `shell` domain). A local privilege-escalation in
   any vendor daemon, or a kernel 5.15.196-android14 LPE (e.g. from a
   future AOSP/CVE batch), would give root without touching the boot
   chain. Nothing public is known for this exact build yet. Tracking
   Amlogic/s905x5 CVEs and the XDA/4PDA threads is the standing task.
2. **slimBOXtv / community build compatibility.**
   Because the P610 is the `ross` reference, a slimBOXtv S905X5M image
   *might* boot on this hardware **if** the flash lock could be
   bypassed – which it cannot (see 3). So this path is currently
   blocked by the lock, not by hardware compatibility. If an unlocked
   P610A/other-operator P610 variant ever appears in the community,
   the answer changes immediately.
3. **Amlogic DNL (USB burn) – hardware path, likely dead end.**
   Entry (test point / key combo / 12 V short) could be found by
   opening the case, but with `flash.locked=1` + RPMB + odsign, DNL can
   only write **OEM-signed** images. Releasing the lock is eFuse-level
   and requires the OEM key. Effectively: OEM/RMA-only.
4. **OEM unlock flow via `sys.oem_unlock_allowed=1`.**
   The property hints that Skyworth's U-Boot *has* an unlock routine,
   but it requires the OEM key handshake and a fastboot surface that
   this build does not expose. Not user-reachable.

   **Follow-up (2026-11-13):** the user found an
   **"OEM-Entsperrung / Bootloader-Entsperrung zulassen"** toggle in
   the box's developer settings. Read-only analysis:
   - `sys.oem_unlock_allowed=1` is **already set** – the toggle just
     mirrors this property; flipping it cannot change a state that is
     already "allowed".
   - The other "OEM lock" surface on the box (`oem_lock` /
     `android.hardware.oemlock.IOemLock` service) is Android's
     **hardware oemlock** (carrier feature locks, e.g. USIM
     restrictions) – **unrelated to the bootloader**.
   - Actual lock state unchanged: `ro.boot.flash.locked=1`,
     `ro.boot.vbmeta.device_state=locked`,
     `verifiedbootstate=green` (user build, release-keys).
   - No fastboot/fastbootd binary on the box, so there is no bootable
     surface the toggle could act on anyway.
   - **Assessment:** the toggle is almost certainly a no-op (or only
     sets the already-set property) – the eFuse-level flash lock would
     still require the OEM key. *Untested on purpose* (write action
     unknown). If it is ever toggled, re-check `getprop` for
     `ro.boot.flash.locked` / `ro.boot.vbmeta.device_state` after
     reboot and note the result here.
5. **Wait for the generation to mature.**
   The P610 launched mid-2025; S905X4/X3 boxes took 1–2 years before
   stable root/Custom firmware arrived. Given the `ross` shared base,
   a P610-specific build (or a confirmed DNL+unlock sequence) is the
   most likely future outcome.

## What root would unlock (so the effort is measurable)

- Read `env` (p2) and the U-Boot `bootcmd` (boot.md open question 4).
- Write `persist.*` properties and vendor configs (standby/WOL tuning).
- Magisk: module-level debranding (hide PAI re-assertion, patch
  `ociserviceapplication`), Zygisk hooks, the BT-HID `cqhci` issue
  could be debugged properly.
- Partition surgery → CoreELEC/dual-boot (twilight project proves it
  on the SoC).
- Full firmware extraction for this operator build (currently only
  possible via ADB read of individual files).

## Standing watch list

- XDA: **project slimBOXtv** (4152049) – S905X5M sub-thread / posts
  mentioning "ross", "P610", "HP4C06", "Ocilion".
- 4PDA: slimBOXtv thread (topic 993324) – Russian-speaking community,
  often ahead on box-specific unlock sequences.
- slimboxtv.ru S905X5M category for new device pages.
- Amlogic S905X5M kernel 5.15.196-android14 security bulletins.
- Other operators' P610 units: an **unlocked or debug variant**
  (e.g. an Austrian P610 `HP4CA` per androidtv-guide, or a developer
  unit) would change the whole picture – worth asking in the
  operator thread of this repo.

## Decision for this unit

**Do not attempt DNL/pinning on the daily komro unit** – wrong entry
sequence + locked flash risks an unusable (but RMA-able) box, with no
realistic write path anyway. Keep the read-only research posture here;
re-evaluate if (a) a public LPE for this build appears, or
(b) a community P610 unlock materializes.
