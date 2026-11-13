# Steam Link on the P610 – complete settings (working)

Tested 2026-11-13 with GTA V, host PC over Wi-Fi 5 GHz (5180 MHz, RSSI ~-41 dBm,
866 Mbps PHY). Result: **smooth, no drops, no UI freezes** (user-verified).

*Note: the app-settings files themselves are not readable unrooted
(package not debuggable, no `run-as`); this document is the effective
configuration as applied in the app UI, verified against live codec
metrics from `dumpsys media.metrics`.*

## Video settings (app: Steam Link → Einstellungen → Video)

| Setting | Value |
|---|---|
| Video (quality) | **Schön** ("Beautiful") |
| Auflösungsbegrenzung | **Automatisch (1920 × 1080)** |
| Frameratebegrenzung | **60 FPS** |
| HDR | Automatisch |
| Anzeigestil | Briefkasten ("Thumbnail") |
| Hardwaredekodierung | **Aktiviert** |
| HEVC-Video | **Deaktiviert** |
| AV1-Video | **Aktiviert** |
| PyroWave-Video | Deaktiviert |
| YUV 4:4:4 | **Aktiviert** |
| PyroWave-Bitrate | Automatisch (empfohlen) |
| Video mit niedriger Latenz | **Aktiviert** |

## Network settings (app: Steam Link → Einstellungen → Netzwerk)

| Setting | Value |
|---|---|
| Bandbreitenbegrenzung | **50 Mbit/s** |
| Netzbetrieb mit niedriger Latenz | **Aktiviert** |
| Direktverbindung erlauben | **Automatisch (aktiviert)** |

## Why this combination works (measured failure modes)

The S905X5M decode/composite pipeline saturates at unbounded game-bitrates.
Measured via `dumpsys media.metrics` (same box, same host, same Wi-Fi):

| Config | Result |
|---|---|
| HEVC 1080p60, default bitrate | `c2.amlogic.hevc.decoder`: **24–36 % frame drops**, freeze rate up to ~40 %; load > 4 → **system-wide UI freeze** (black screen, only ADB alive), required `adb reboot` |
| HEVC 1080p**30** | *Worse*: still ~30 % drops → the fps cap is not the bottleneck |
| AV1 1080p60, **uncapped** | `c2.amlogic.av1.decoder`: 36–54 % drops, freeze rate ~54–59 % |
| **AV1 1080p60 + 50 Mbit cap + 4:4:4** | **Works** – decoder holds 60 fps, no sustained drops |

Key points:

- **YUV 4:4:4** costs ~50 % more bandwidth than the usual 4:2:0 (full
  chroma resolution → no edge/text blur). It only works here because AV1's
  compression ratio keeps full-chroma 1080p60 under the 50 Mbit cap.
  HEVC + 4:4:4 would have broken the pipeline.
- The **50 Mbit/s bandwidth cap is the decisive lever** – it keeps the
  stream below the point where the box saturates.
- Cross-check: a Fire TV Stick on the same Wi-Fi streamed the same host
  fine at lower throughput → the Wi-Fi link was never the bottleneck.

## In-app navigation / exit

- **Xbox guide button** (gamepad): opens the **Steam overlay** (like on a
  PC) – note: in Cannoli the same button opens the emulator pause/exit
  menu, but in Steam Link it is the Steam overlay. Close overlay with
  Esc (top-left face button) or guide again.
- Quit game: guide → *Beenden* → back to the Steam Link home screen.
- **Home key** on the remote: returns to the Android home screen.

## Related

- Context and other streaming options: [gaming.md](gaming.md)
- Wi-Fi/ADB state notes: [android.md](android.md)
