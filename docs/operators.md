# Operators & Providers using the Ocilion P610

The P610 is a white-label product: the same Skyworth/Ocilion hardware ships
under many operators' brands, with market- and operator-specific branding,
PAI app lists, and provisioning backends. This list tracks which operators
are known to deploy the **P610** specifically.

**Important:** the P610 (launched June 2025) is the newest Ocilion
generation, while many published Ocilion customer references date from the
P400/P500 era. The table therefore distinguishes three evidence levels:

- ✅ **Confirmed P610** – the operator is listed for the P610 model.
- 🔶 **Likely P610 / migrating** – announced for the P600 generation or
  actively receiving P610 rollouts.
- ⚪ **Ocilion customer (generation unverified)** – known Ocilion STB
  customer; whether they run P610 or the older P400/P500 series is not
  confirmed.

## P610 deployments

| Operator | Market / region | Service | Evidence |
|----------|-----------------|---------|----------|
| **komro GmbH** | Germany, Rosenheim (cable) | komroTV+ / Kabel TV | ✅ **First-hand** (the unit researched in this repository is a komro-provisioned P610; German-market app set incl. ORF/ARD/ZDF) |
| **Deutsche GigaNetz (DGN)** | Germany (broadband) | "MyTV" | ✅ listed on Android TV Guide's P610 operator page; Ocilion case study |
| **Kabelfernsehen Bödeli** | Switzerland (cable) | – | ✅ listed on Android TV Guide's P610 operator page; Ocilion reference |
| **Telenec** | Switzerland | – | ✅ listed on Android TV Guide's P610 operator page |
| **Gemeindewerke Nümbrecht** | Germany (municipal) | – | ✅ listed on Android TV Guide's P610 operator page |

## Ocilion STB customers (generation unverified)

From Ocilion's public reference list
(<https://ocilion.com/referenzen-case-studies>), these operators run
Ocilion platform hardware (UI/UI6, IPTV), but the references predate or do
not specify the P610 generation:

| Operator | Market | Service |
|----------|--------|---------|
| EWE | Germany (Oldenburg) | "ZuhauseTV" |
| willy.tel | Germany (Hamburg) | "willy.tv" |
| M-Net | Germany (Munich) | "TVplus" |
| Eltrona | Austria (cable) | – |
| Tele Data | Austria/Germany | – |
| Stadtwerke Neubrandenburg | Germany (municipal) | – |
| RFT Kabel | Germany (cable) | "RFT Choice" |
| spusu | Germany (broadband) | IP-TV |
| wilhelm.tel | Germany | – |
| Salzburg AG | Austria | – |
| envia TEL | Germany | – |

(Inhouse projects, e.g. OptimaMed, RheinEnergie, are Ocilion customers but
not relevant for retail STB deployments.)

## Context & caveats

- Ocilion announced (Nov 2025) that the P610 had already reached "die
  ersten Netzbetreiber" (the first operators) – rollouts are therefore
  still in progress, and this list will grow.
- The **P610A** variant (same platform, listed in the official manual)
  may target different markets/operators.
- Because provisioning (branding, PAI list, OTA backend) is per operator,
  **findings in this repo that depend on provisioning – especially the
  forced-app policy in
  [android.md](android.md#forced--pre-installed-apps-pai-and-their-removability)
  – apply in detail to komro / the German market**, while the
  hardware/firmware/boot/security findings apply to all operators.

### Sources

- Android TV Guide, P610 entry (operators: Deutsche GigaNetz,
  Kabelfernsehen Bödeli, Telenec, Gemeindewerke Nümbrecht):
  <https://www.androidtv-guide.com/pay-tv-provider/ocilion-p610/>
- Ocilion references & case studies:
  <https://ocilion.com/referenzen-case-studies>
- Ocilion P610 launch news (P600 series, June 2025):
  <https://ocilion.com/news/p600-serie>
- UI6/P610 launch (Nov 2025):
  <https://ocilion.com/en/news/launch-ui6-und-p610>
- komro GmbH (operator of the researched unit): <https://www.komro.net>

**If you can confirm additional operators (from your own box or the
operator's docs), please add them to the table – first-hand reports are
marked with ✅.**
