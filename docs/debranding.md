# Debranding: Using the P610 as a Normal Android/Google TV Box

Status: **analysis only – no changes made** (read-only ADB only, per the
project's safety constraints). This document documents what exists, what is
possible, and a concrete, reversible test plan that can be executed after
explicit approval.

## The goal

Use the box like a regular Android TV / Google TV device: a normal home
screen, Play Store sign-in, casting, YouTube, apps – without the operator
branding (UI6, operator OTA UI, remote-control branding).

## Current state (verified)

### Launcher

- Default HOME app: **`com.ocilion.p6`** (Ocilion UI6), v1.8.0, a
  **Flutter** app (Sentry + Flutter plugins in its manifest) installed in
  `/data` (not a system priv-app).
- Its `MainActivity` registers:
  `MAIN` + `category.HOME` + `category.LAUNCHER` + `action.ALL_APPS` →
  it *is* the launcher in the Android sense, and `resolve-activity` reports
  it as `isDefault=true`.
- Only other HOME registrant: `com.android.tv.settings` (the standard
  fallback home).
- **No stock Android TV Home** (`com.google.android.tv`/`launcherx`) and no
  Google TV Home (`com.google.android.apps.tv`) exists on the device.

### Google stack

- Full GMS/GTV component set is present (GMS core, TV settings, TV provider,
  YouTube TV, recommendations, Play Games, setup wizard `setupwraith`, LatinIME
  TV, TalkBack, TTS) – see [android.md](android.md).
- **Play Store** (`com.android.vending`) is installed (user-space, from the
  operator's FTI). No Google account is signed in.
- `mdnsd` runs → Google Cast **receiver** is available.

### Operator lock-in

| Constraint | Severity | Effect on debranding |
|------------|----------|----------------------|
| Verified boot / dm-verity / locked boot flash | – | System partitions (`/system`, `/product`, …) are read-only and signed. Ocilion apps living *in /data* are unaffected; system-resident OEM apps cannot be modified. |
| `com.sdt.ocilion.oemservice` (SYSTEM_FIXED permissions) | High | Operator backbone (provisioning, R601 lifecycle, likely boot-critical). **Must not be touched.** |
| Operator OTA pipeline (`com.sdt.ota`, `update_engine`) | Medium | OTA installs are operator-driven; a future OTA could **reinstall/re-enable** UI6 or restore its default state. PAI config (`android.autoinstalls.config.Ocilion.hp4c06`) can re-push the app list. |
| PAI/FTI auto-install | Low | Only runs during first-time setup. |
| `com.ocilion.p6` in `/data` | Low | A normal app: it can be uninstalled, disabled, or data-wiped like any user app. |
| Branding overlays (`com.sdt.*`, `com.droidlogic.*` resource overlays) | Low (cosmetic) | Settings/SystemUI keep an operator-ish look; harmless, reversible if they are user-disable-able (they are system apps – not disable-able without root). |
| No stock GTV/ATV home app | Medium | "Switch back to stock" is impossible; we must *install* a third-party launcher. |

## Verdict

**Debranding is feasible and low-risk for the core use case.** The reasons:

1. The launcher is a **user-space Flutter app** in `/data`, not a system
   priv-app → `pm disable-user`, `pm clear`, or a full uninstall all work
   without root.
2. All Google TV plumbing (GMS, TV provider, settings, YouTube, cast,
   Play Store) is **stock and present**, so a normal launcher + apps will
   behave like on any GTV box.
3. The only things that *cannot* be removed are the privileged OEM services
   in system partitions (oemservice, OTAs) – and for the "normal TV box"
   goal, that is acceptable: they run silently in the background.
4. Everything we would change is **reversible** (re-enable / reinstall /
   factory reset), and none of it touches the verified-boot chain.

What debranding does **not** fix: the operator look of SystemUI/Settings
(resource overlays, no root), the operator OTA channel, and the missing stock
Google home.

## Candidate launchers

Because there is no stock GTV home to fall back to, we need an alternative
launcher APK. Options, in order of fit:

1. **LtvLauncher** (open source, Flutter, leanback) – designed exactly for
   this: minimal, D-pad-first, has a built-in **"Home Button Fix"**
   accessibility service that intercepts HOME and forces the launcher even
   when the OS blocks default-launcher changes. Ships a **32-bit
   `armeabi-v7a`** build (required here – see 32-bit userspace in
   [android.md](android.md)). GPL-3.0.
   <https://github.com/leanbitlab-org/LtvLauncher>
2. **FLauncher** (same lineage, also 32-bit) – alternative if LtvLauncher has
   issues.
3. **Google TV Home APK from another device** (`com.google.android.apps.tv`) –
   *not recommended*: GTV Home expects specific GTV system components/privileges
   and may fight the operator overlays; also licensing/gray-area. Kept as a
   documented experiment only.
4. **ATV Home (`com.google.android.tv`) from another device** – same caveats.

**Recommendation: LtvLauncher (armeabi-v7a build).**

## Reversible debranding procedure (test plan – requires approval)

All steps are reversible; each has an undo. Nothing touches verified boot.

> Prerequisites: ADB over Wi-Fi (already working), Play Store signed in with a
> Google account (optional but recommended), a remote that works with the
> launcher (the R601 or any BT gamepad/D-pad).

### Phase 0 – Baseline & safety net

1. Record current state (for the write-up):
   ```bash
   adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
   adb shell pm list packages > baseline_packages.txt
   ```
2. Note that UI6 lives in `/data` → a **factory reset restores everything**
   (including operator provisioning; the box would need re-provisioning by the
   operator). Keep this in mind before any step.
3. Screenshot the home screen for before/after.

### Phase 1 – Install the alternative launcher

1. Obtain `LtvLauncher-armeabi-v7a-release.apk` (release page).
2. Install:
   ```bash
   adb install LtvLauncher-armeabi-v7a-release.apk
   ```
3. Verify it registered HOME:
   ```bash
   adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME
   ```
   → expect both `com.ocilion.p6` and the new launcher listed.
4. **Do not disable UI6 yet.** First just launch the new launcher manually
   (`adb shell am start -n <launcher>/<activity>` or via the app switcher)
   and verify: D-pad navigation, Play Store launch, YouTube launch, casting.

### Phase 2 – Make the new launcher the default (reversible)

Option A (no root, in-OS):
- In the new launcher's settings: *Set as default launcher*, or
  Settings → Apps → Default apps → Home app → choose the new launcher.

Option B (ADB, reversible):
```bash
# disable the UI6 launcher for the current user
adb shell pm disable-user --user 0 com.ocilion.p6
# press Home / reboot → new launcher takes over
```
Option C (if the OS keeps forcing UI6 back, e.g. via its service manager):
- Enable LtvLauncher's **Home Button Fix** accessibility service (this is the
  path LtvLauncher documents for Google TV boxes where the stock launcher
  keeps priority).

**Undo (any time):**
```bash
adb shell pm enable com.ocilion.p6
```
→ UI6 becomes the default home again.

### Phase 3 – Cosmetics & hardening (optional, all reversible)

- Sign in to the Google account in the Play Store → normal app ecosystem.
- Disable UI6's data so it doesn't pollute storage (reversible):
  `adb shell pm clear com.ocilion.p6` (it will re-initialize if re-enabled).
- Remove forced PAI apps you don't want (see
  [android.md → Forced / pre-installed apps](android.md#forced--pre-installed-apps-pai-and-their-removability)
  for the two classes):
  ```sh
  # Class 2 (broadcaster apps): full uninstall
  adb shell pm uninstall de.tagesschau
  # Class 1 (Netflix/Disney+/Prime/Play Store/UI6): per-user removal, no root
  adb shell pm uninstall -k --user 0 com.disney.disneyplus
  # restore any of them later:
  adb shell pm install-existing com.disney.disneyplus
  ```
  Expect operator OTAs to potentially re-assert the PAI list.
- Leave `com.sdt.ocilion.oemservice` and all system-resident operator apps
  **untouched** (boot-critical, no benefit to disabling).
- Do **not** attempt to uninstall system overlay packages.

### Phase 4 – Acceptance criteria

- Home button opens the new launcher, consistently, across reboots.
- Play Store, YouTube TV, and casting all work.
- The R601 remote (or a BT gamepad) navigates the launcher.
- `adb shell pm enable com.ocilion.p6` restores the original state within a
  reboot (proves reversibility).

### Risks & mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| UI6/service manager re-asserts itself as home | Medium (it has an `OciServiceManagerService`) | Home Button Fix (Option C); re-apply `pm disable-user`; document behavior |
| An operator OTA re-enables UI6 or reinstalls PAI apps | Medium over months | Disable-user is per-user and per-package; an OTA may not touch it, but monitor after OTAs |
| New launcher incompatible with 32-bit ABI | Low (LtvLauncher ships v7a) | Fallback to FLauncher v7a |
| Losing the operator UI6 permanently | Very low (it's in /data, restored by factory reset or reinstall from the operator) | Factory reset as last resort |
| Bricking | **~None** – no system partitions are modified | Verified boot chain untouched |

## Open questions & planned experiments

- Does `com.ocilion.p6`'s `OciServiceManagerService` actively *manage*
  (restart/re-enable) other apps? Observe logs after disable (`logcat`).
- Does the R601 remote's Google Voice Control button work with an
  unmodified Google stack + no Google account? (Voice control needs a Google
  account.)
- How much of the "operator look" (settings, systemui) comes from disableable
  user-space overlays vs. system partitions? (Map each overlay's source
  dir; system-resident overlays are fixed without root.)
- Whether Play Store sign-in triggers any operator-side reaction
  (passive observation of operator app logs only).
