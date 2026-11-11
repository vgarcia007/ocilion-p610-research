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
