# The Android Layer

What the operator image actually contains, and how it differs from a stock
Google TV box. Everything was read via ADB (`pm list packages`, `dumpsys
package`, `getprop`) on 2026-11-11.

## Component overview

146 packages total:

| Location | Count | Role |
|----------|------:|------|
| `/system` | 49 | AOSP + Google TV system apps, Ocilion OEM apps |
| `/product` | 43 | Google TV product partition (the "stock" layer) |
| `/system_ext` | 12 | Google TV extensions (settings, feedback, network) |
| `/apex` | 23 | APEX modules (media, art, webview, widevine, …) |
| `/vendor` | 4 | Amlogic vendor apps |
| `/data` (user) | 15 | Sideloader-free PAI apps + the launcher + Play Store |

### The Ocilion operator stack

| Package | Version | Role |
|---------|---------|------|
| `com.ocilion.p6` | 1.8.0 (26093000) | **The UI6 launcher** (Flutter app, `/data`, see debranding.md) |
| `com.ocilion.ociserviceapplication` | 1.0.0 | Ocilion service app (operator APIs, provisioning glue) |
| `com.sdt.ocilion.oemservice` | 2.3 | **OEM service** (Skyworth/SDT): has `SYSTEM_FIXED` location, BT, phone-state, contacts, audio permissions – the privileged operator backbone |
| `com.sdt.vendor` | 14 | Skyworth vendor app |
| `com.sdt.customization` | 14 | SDT/Atv partner config (branding overlays) |
| `com.sdt.ota` / `com.sdt.rcuotaActivity` / `com.sdt.remotecontrolota` | – | Operator OTA + **remote control OTA** (R601 remote updates) |
| `com.sdt.rcupair` | – | Remote pairing service |
| `com.sdt.globalkey` (+overlay) | – | Global key handling (shortcut keys on the remote) |
| `com.sdt.frontpanelledsservice` | – | Front-panel LED control |
| `com.sdt.producttest` | – | Factory test app |
| `com.droidlogic.tv.settings` | 1.0 | SDT's Android TV settings app (replaces/augments stock) |
| `com.droidlogic` (+ overlays: `overlay`, `systemui.overlay`, `tvprovider.overlay`, `wifitetheringoverlay`, `com.android.networkstack.tethering.droidlogic`) | – | Branding/resource overlays across system apps |
| `com.sdt.*.overlay` (many) | – | Resource overlays: `com.sdt.android.overlay`, `.ce`, settings overlays, etc. |
| `com.ruwido.atv_ir` | 3.0.15 | R601 remote companion (ruwido) |
| `com.ruwido.rci_service` | – | ruwido remote-control-interface service |
| `android.autoinstalls.config.Ocilion.hp4c06` | 1 | **PAI (Play Auto Install) config**: the FTI app list (ARD, ZDF, RTL+, wow, DAZN, DYN, joyn, Pluto, Paramount+, Spotify, Twitch, Rakuten, Apple TV+, Arte, 3sat, HBO Max) |
| `com.amlogic.tv.overlay.matchcontent` | – | Amlogic "match content" TV overlay (IPTV guide UI pieces) |

**Observations**

- The Ocilion stack is layered **on top of** a standard AOSP TV image via
  resource overlays (`com.sdt.*`, `com.droidlogic.*`), the usual white-label
  technique. The operator skin mostly replaces *resources* (strings, icons,
  layouts), not whole system apps.
- `com.sdt.ocilion.oemservice` is the most privileged non-Google app
  (`SYSTEM_FIXED` permissions, granted by default, exemption from revocation
  policies). It is the anchor for operator provisioning, remote management
  and the R601 remote lifecycle. It cannot be disabled without root.
- There is **no TV-input/IPTV tuning app in the package list** – live TV is
  delivered by the Ocilion player stack inside `com.ocilion.p6` (its
  `OciMediaService` is a `androidx.media3` MediaSessionService) rather than a
  separate IPTV player. This means live-TV playback is coupled to the launcher
  app (relevant for debranding: removing UI6 removes the operator TV UI, but
  the underlying Google TV layer remains fully functional for apps).
- The R601 remote is supported via `com.ruwido.*` + `com.sdt.rcupair` +
  `com.sdt.remotecontrolota` (the remote can be OTA-updated by the operator).

### Stock Google TV components present

The `/product` + `/system` + `/system_ext` partitions carry the full standard
set:

- **GMS**: `com.google.android.gms` (PrebuiltGmsCorePano), `gsf`
  (GoogleServicesFramework), `katniss` (setup assistant), `backdrop`
  (TV home background/recommendations), `ext.services`, `permissioncontroller`,
  `safetycenter.resources`, `modulemetadata`, `tts`, `webview`.
- **TV framework**: `com.android.providers.tv` (TvProvider),
  `com.android.systemui` (TvSystemUI), `com.android.tv.settings` (TvSettings),
  `com.android.tv.feedbackconsent`, `TvFrameworkPackageStubs`,
  `AtvRemoteService` (Google TV remote app service).
- **YouTube**: `com.google.android.youtube.tv` (YouTube Leanback) and
  `com.google.android.youtube.tvmusic`.
- **Recommendations/Cast**: `com.google.android.tvrecommendations`,
  `com.google.android.apps.mediashell` (media shell for Play), `mdnsd`
  (cast discovery), `com.google.android.play.games`.
- **Setup**: `com.google.android.tungsten.setupwraith` (SetupWraith – the
  Android TV setup wizard) + `GooglePartnerSetup` + `GoogleOneTimeInitializer`.
- **Input**: `com.google.android.inputmethod.latin` (LatinIME TV),
  TalkBack, Google TTS, GoogleFeedback.
- **Play Store**: `com.android.vending` **is installed** (sideloaded into
  `/data` at provisioning, i.e. the operator chose to enable the Play Store on
  this device) – Google account sign-in was skipped at FTI, so it is present
  but unauthenticated.

**What is NOT present**

- **No stock Android TV Home** (`com.google.android.tv` / `launcherx`): the
  Google TV launcher app itself is *not* on the box. This is a deliberate
  operator-tier decision: they ship their own launcher (UI6) and keep the
  rest of the GTV stack. Consequence: a "just switch back to stock home"
  debranding path is **not** available – an alternative launcher must be
  installed (see [debranding.md](debranding.md)).
- No Play Books/Maps etc. (normal for TV).

## 32-bit userspace (repeated, because it drives everything)

```
ro.product.cpu.abilist        = armeabi-v7a,armeabi
ro.product.cpu.abilist64      = (empty)
ro.zygote                     = zygote32
app_process -> app_process32  (ELF32 arm)
```

Practical consequences:

- Only 32-bit APKs run. Many modern apps ship only `arm64-v8a` splits; those
  will fail to install or launch. Apps with an `armeabi-v7a` split (most
  mainstream apps still do) work fine.
- Termux: use the **v7a** package set; 64-bit-only prebuilts won't run.
- Benchmarks (e.g. Geekbench) run 32-bit; expect lower-than-64-bit scores for
  the same silicon.
- The `abi` is a firmware choice, not a silicon limit: the kernel and SoC are
  64-bit. A rebuilt 64-bit image would likely run arm64 userspace, but that
  requires an unlocked/rebuilt firmware (out of scope, see linux.md).

## Forced / pre-installed apps ("PAI") and their removability

The operator enforces a set of apps (per the PAI/FTI docs: Netflix, Prime
Video, Disney+ are "fix vorhanden und nicht deinstallierbar"). **The exact
forced-app set is market- and operator-specific**: this unit is provisioned
for **Germany / komro GmbH** (the operator identity is bound at
provisioning time, visible on-device as the operator key `ro.oem.key1` and
`ro.boot.product.hardware.sku`; no operator-specific packages are present
– branding and app lists are pushed from the provisioning backend). Other
markets/operators on the same hardware will have different app sets.
On *this* device it shows up as **two distinct classes**, which behave
very differently:

### Class 1 – System apps (cannot be uninstalled, only disabled)

These exist as **base APKs in signed system partitions** (A/B `super`),
updated at `/data` via the normal Play Store / operator update channel
(`flags = SYSTEM + UPDATED_SYSTEM_APP`):

| App | Base APK (read-only partition) | Note |
|-----|-------------------------------|------|
| Netflix (`com.netflix.ninja`) | `/product/app/Netflix/Netflix.apk` (~93 MB) | plus a dedicated system service `com.netflix.tokenmanager` (`/system_ext/app/NfxTokenManager`) – Netflix is deeply integrated (token/session offload) |
| Disney+ (`com.disney.disneyplus`) | `/system/priv-app/Disney/Disney.apk` (~39 MB) | **priv-app** → holds elevated privileges |
| Prime Video (`com.amazon.amazonvideo.livingroom`) | `/system/app/PrimeVideo/PrimeVideo.apk` (~25 MB) | |
| Play Store (`com.android.vending`) | SYSTEM app | the operator's chosen app channel |
| UI6 launcher (`com.ocilion.p6`) | SYSTEM app in /data | see [debranding.md](debranding.md) |

Consequences:

- The GUI (and `pm uninstall`) **cannot remove** them – Android forbids
  uninstalling `SYSTEM` apps. "Deaktivieren" (disable for user 0) is the
  maximum the UI offers.
- **Without root, `pm uninstall` fails** ("not a user app"). The standard
  no-root equivalent is a *per-user* uninstall, which **does** work for
  system apps:
  ```sh
  # remove for the current user (reversible!)
  adb shell pm uninstall -k --user 0 com.netflix.ninja
  # restore at any time
  adb shell pm install-existing com.netflix.ninja
  ```
  This removes the app for user 0 (incl. data), leaving the read-only base
  APK in place. Functionally equivalent to "disable" but cleaner.
- **Reinstallation pressure:** the PAI mechanism
  (`android.autoinstalls.config.Ocilion.hp4c06` auto-install config +
  `com.ocilion.ociserviceapplication`) can re-assert the app list after
  first-time setup or **operator OTA updates** – expect removed/disabled
  PAI apps to come back after an OTA. This has not been triggered in this
  research (read-only so far), it is documented from the PAI design.

### Class 2 – PAI user apps (fully uninstallable)

The German broadcaster apps installed during the FTI are **plain user apps**
(no `SYSTEM` flag), installed through the Play Store
(`installerPackageName = com.android.vending`):

`de.zdf.mediathek.tivi`, `com.zdf.android.mediathek`, `de.swr.avp.ard.tv`,
`com.mediathek_3sat.android`, `de.tagesschau`, `de.rtli.tvnow`,
`tv.arte.plus7`, `de.toggo`, `de.heute.mobile`,
`de.prosiebensat1digital.seventv`

These can be **fully uninstalled** without root:
```sh
adb shell pm uninstall de.tagesschau        # complete removal
# reinstall later via Play Store / `pm install-existing` does NOT work
# for these (no system base) – just reinstall from Play
```
Note: the same PAI re-assertion pressure applies after OTAs (the FTI app
list is operator-managed and dynamic, per the official PAI documentation).

### Complete pre-installed app catalog (this device, 2026-11-11)

All 25 non-system-resident (`/data`) apps with version, install class and
removal path. `firstInstallTime = 2009-01-01` is the Android placeholder
for "installed with the system image", i.e. before FTI.

| Package | Version | Class | Removal (no root) |
|---------|---------|-------|-------------------|
| `com.ocilion.p6` (UI6 launcher) | 1.8.0 (26093000) | 1 – system | per-user only; do not remove |
| `com.android.vending` (Play Store) | 53.4.17-34 | 1 – system* | per-user; see Play Store note below |
| `com.netflix.ninja` (Netflix) | 13.2.2 build 27249 | 1 – system | `pm uninstall -k --user 0` |
| `com.disney.disneyplus` (Disney+) | 26.20.0+rc3-2026.11.02 | 1 – system | `pm uninstall -k --user 0` |
| `com.amazon.amazonvideo.livingroom` (Prime Video) | 6.24.13 | 1 – system | `pm uninstall -k --user 0` |
| `com.google.android.gms` (Google Play services) | 26.43.30 | 1 – system | **do not remove** (breaks Play ecosystem) |
| `com.google.android.tvrecommendations` | 7.4.0 | 1 – system | do not remove (Google TV core) |
| `com.google.android.backdrop` (backdrops) | 2.3.5 | 1 – system | do not remove |
| `com.google.android.katniss` | 7.55.87 | 1 – system | optional per-user removal |
| `com.google.android.play.games` | 2026.10.70836 | 1 – system | per-user if not wanted |
| `com.google.android.tv.remote.service` | 7.00.999 | 1 – system | optional (remote control) |
| `com.google.android.tungsten.setupwraith` (setup helper) | 1.0.993403918 | 1 – system | optional (FTI helper) |
| `com.google.android.inputmethod.latin` (Gboard TV) | 17.3.11 v7a | 1 – system | keep (only keyboard) |
| `com.google.android.tts` (Google TTS) | 20261014 p0 | 1 – system | keep (voice/search) |
| `com.google.android.webview` | 156.0.8078.47 | 1 – system | keep (many apps need it) |
| `de.swr.avp.ard.tv` (ARD Mediathek) | 12.5.1 | 2 – PAI | `pm uninstall` |
| `de.zdf.mediathek.tivi` (ZDFtivi) | 2.0 | 2 – PAI | `pm uninstall` |
| `com.zdf.android.mediathek` (ZDF Mediathek) | 6.10.1 | 2 – PAI | `pm uninstall` |
| `com.mediathek_3sat.android` (3sat) | 3.0.2 | 2 – PAI | `pm uninstall` |
| `de.tagesschau` (tagesschau) | 1.0.15 | 2 – PAI | `pm uninstall` |
| `de.rtli.tvnow` (RTL+) | 7.17.0 | 2 – PAI | `pm uninstall` |
| `tv.arte.plus7` (ARTE) | 3.12.1 | 2 – PAI | `pm uninstall` |
| `de.toggo` (TOGGO) | 1.73.0.0-A | 2 – PAI | `pm uninstall` |
| `de.heute.mobile` (heute) | 2.2.0 | 2 – PAI | `pm uninstall` |
| `de.prosiebensat1digital.seventv` (7TV) | 6.5.2-ATV | 2 – PAI | `pm uninstall` |

Notes:

- The 10 Class-2 apps all carry `firstInstallTime = 2026-11-11 15:23–15:26`
  (the FTI/PAI window of the factory setup), confirming they were
  installed at first boot, not present in the image.
- `pm list packages -3` (third-party) reports exactly these 10.
- *The Play Store's base APK lives **only in `/data`** (no copy in any
  read-only partition on this build) – see the Play Store section below.

### The Play Store: presence, GUI access, ADB installation

**Is there a Play Store? Yes.** `com.android.vending`
(Google Play Store, TV build) is present and **enabled** (not in
`pm list packages -d`).

**How to reach it via the GUI:**

- Technically it registers a Leanback launcher activity
  (`com.google.android.finsky.tvmainactivity.TvMainActivity`,
  categories `LAUNCHER` + `LEANBACK_LAUNCHER` + `APP_MARKET`).
- **However, on this unit it is NOT shown in the UI6 app list**
  (observed 2026-11-11): the `LEANBACK_LAUNCHER` query returns the
  Play Store alongside YouTube, 7TV, ARTE etc., but the UI6
  launcher only displays a curated subset – the Play Store tile is
  missing from the on-screen app drawer while the box has **no
  Google account signed in**. The launcher's own app-list storage
  is not readable from the shell (`run-as` unavailable, package not
  debuggable), so the exact hiding rule (account-gated vs.
  operator config) could not be inspected.
- It can be launched explicitly from ADB (verified – it comes up
  on `UnauthenticatedMainActivity`, the no-account screen):
  ```sh
  adb shell am start -n com.android.vending/com.google.android.finsky.tvmainactivity.TvMainActivity
  ```
  or generically:
  `adb shell am start -a android.intent.action.MAIN -c android.intent.category.APP_MARKET`
- In-app, it also responds to the standard `VIEW`/`market:` deep
  links (e.g. `am start -a android.intent.action.VIEW -d market://details?id=<pkg>`).

> **To verify next:** sign in with a Google account and check whether
> the Play Store tile then appears in the UI6 app list (likely
> account-gated; would confirm the launcher hides it without an
> account).

**Important nuance – no read-only base APK on this build:**
`pm path com.android.vending` resolves **only** to `/data/app/…`
(base + `split_config.armeabi_v7a` + `split_config.de`). Unlike Netflix
or Disney+, there is **no** Play Store APK in `/system`, `/product` or
`/system_ext`. Consequences:

- `pm install-existing com.android.vending` **cannot** restore it if it
  was per-user-uninstalled – it would have to be re-sideloaded.
- It is flagged `SYSTEM + UPDATED_SYSTEM_APP` (the base install
  predates FTI, timestamp `2009-01-01`), but the on-disk copy is
  user-writable. **Recommendation: do not remove it** – it is the app
  channel for both the operator's PAI mechanism and any future
  debranding installs; the Play Store itself is also needed to restore
  Class-2 PAI apps.

**Can apps be installed from ADB? Yes** – the `shell` user holds
`android.permission.INSTALL_PACKAGES = granted=true`, so:

```sh
adb push ./app-armeabi-v7a.apk /data/local/tmp/
adb shell pm install -r /data/local/tmp/app-armeabi-v7a.apk
```

Constraints to keep in mind:

- **32-bit only:** the box runs a 32-bit userspace (see the ABI
  section) – only APKs with an `armeabi-v7a` (or pure-Java) native
  library will run. Play Store downloads for this device are already
  filtered to `armeabi-v7a` (note the `split_config.armeabi_v7a.apk`
  of the Play Store itself).
- **No root** → `pm install` works for normal apps, but not for system
  updates to `SYSTEM` apps and not into privileged partitions.
- ADB-installed apps show up as user-installed (`pm list packages -3`)
  and are fully removable with `pm uninstall <pkg>`.
- Alternative: launch the Play Store on the TV and search there –
  identical result, but requires a **Google account** signed in
  (currently none is present; `dumpsys account` shows only the ARTE
  anonymous session).

### Why the distinction exists

Class 1 is the **licensing/qualification** layer: Netflix, Prime Video and
Disney+ are "vorzertifizierte Apps" that the operator must guarantee are
present (and Netflix's token manager suggests a device-level DRM/session
offload for certification). Class 2 is the **FTI convenience** layer (public
PAI list, reinstallable from the Play Store), which the PAI doc itself
describes as an adjustable, operator-managed list.

## Services of note

- `mdnsd` – Chromecast/Chromecast-to-TV discovery (Google Cast receiver is
  available; casting **to** the box works).
- `droidaudio` – Skyworth audio pipeline daemon (multi-channel/Atmos routing).
- `dhp_daemon` – Amlogic display/HDMI pipeline (CEC, ARC, HPD).
- `update_engine` – A/B OTA engine (operator pipeline).
- No `tvinput` hardware tuner: live TV is pure IP (IPTV over the operator
  network), consistent with the "IPTV STB" product class.

## Open questions & planned experiments

- Does `com.ocilion.p6` contain the full IPTV player (EPG, catch-up, VOD
  store) or does it proxy to a separate service? (Static APK inspection –
  read-only, no install.)
- What exactly does `com.ocilion.ociserviceapplication` poll (operator
  backend endpoints)? Passive network observation only, no endpoint
  disclosure in public docs.
- Can `com.sdt.ocilion.oemservice` be **disabled** (`pm disable-user`)
  without bricking the boot? (Requires explicit approval; risk: boot-loop if
  init depends on it – it holds system-fixed permissions, so likely
  boot-critical.)
- **Market/operator dependence:** the PAI list, forced apps, and branding
  are provisioned per market/operator (this unit: Germany / komro GmbH).
  Cross-check findings against the official PAI doc for *other* markets
  before generalizing.
- **PAI re-assertion:** does an operator OTA actually reinstall Class-1 /
  Class-2 apps that were per-user-uninstalled or disabled? Observe across an
  OTA. (Requires explicit approval to trigger/observe.)
- What exactly does `com.netflix.tokenmanager` (NfxTokenManager) offload –
  is it a device-level DRM/session credential store for Netflix
  certification? (Static APK inspection, read-only.)
