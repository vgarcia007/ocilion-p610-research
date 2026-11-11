# Links

## Official Ocilion sources

- Datasheet (P610 / R601):
  <https://ocilion.com/assets/files/ocilion_P610_R601.pdf>
- User manual (P610 / P610A, de/en/fr/it):
  <https://ocilion.com/assets/files/P610_ocilion_V1.8_de_en_fr_it.pdf>
- Pre-installed / PAI apps (DE):
  <https://ocilion.com/assets/files/ocilion_PAI_UI6_DE.pdf>
- Compliance & open-source page: <https://ocilion.com/compliance>
- Product news (P600 series): <https://ocilion.com/news/p600-serie>
- P610 EU declaration of conformity:
  <https://ocilion.com/assets/files/P610_DoC_20260402.pdf>

## Operators

- Known P610 operators (komro, Deutsche GigaNetz, Kabelfernsehen Bödeli,
  Telenec, Gemeindewerke Nümbrecht, + Ocilion customer list) – maintained in
  [operators.md](operators.md).
- Ocilion references & case studies: <https://ocilion.com/referenzen-case-studies>

## Community / third-party

- Android TV Guide – Ocilion P610 entry (Skyworth HP4C06/HP4CD, S905X5M,
  Widevine L1, certified devices list). Also lists other P610 operators
  (Deutsche GigaNetz, Kabelfernsehen Bödeli, Telenec, Gemeindewerke
  Nümbrecht) – useful evidence that the same hardware ships under
  different operator brands/markets:
  <https://www.androidtv-guide.com/pay-tv-provider/ocilion-p610/>
- Geekbench entries under the Skyworth/Ocilion-p610 fingerprint
  (useful for 32-bit vs 64-bit performance comparison):
  <https://browser.geekbench.com/v5/cpu/search?utf8=%E2%9C%93&q=SkyworthDigital+Ocilion-p610>

## Related projects

- **LtvLauncher** – open-source Android TV launcher (Flutter), 32-bit build
  available, "Home Button Fix" for forcing a non-default launcher:
  <https://github.com/leanbitlab-org/LtvLauncher>
- **coreelec-android-dualboot-twilight** – CoreELEC + Android dual-boot on
  eMMC for Amlogic (Xiaomi S905X4 boxes); documents the Amlogic
  `env`/`misc`/GPT boot-gate technique:
  <https://github.com/samtheruby/coreelec-android-dualboot-twilight>
- **CoreELEC device-tree table** (S905X5M = `s7d_s905x5m_*`):
  <https://coreelec.org/dtb/>
- **CoreELEC** (project, Amlogic-ng, kernel 5.15.196):
  <https://coreelec.org> · <https://github.com/CoreELEC/CoreELEC>
- **Termux** – terminal emulator / Linux env on Android:
  <https://github.com/termux/termux-x11>
- **proot-distro** – Debian/Ubuntu userspaces inside Termux:
  <https://github.com/termux/proot-distro>
- **Armbian** (checked: no S905X5M board support at time of writing):
  <https://www.armbian.com/boards>

## Amlogic / platform references

- Amlogic S905X5M reference (`ross`): the `ro.build.reference.fingerprint`
  on the device points to the `Amlogic/ross/ross` AOSP tree.
- CoreELEC "Amlogic-ng" branch: the community kernel/base for
  S905X4/X5/X5M-class SoCs (Kodi 21/22, kernel 5.15).
- Odroid C5 (closest public S905X5M board, 4 GB):
  <https://www.hardkernel.com/shop/odroid-c5/> – useful cross-reference for
  S905X5M-specific Linux work.
- X96 M300 / Ugoos X5M Pro (S905X5M, 4 GB, in the CoreELEC DTB table) –
  similar consumer boxes for cross-reference.

## Notes on what is *not* linked

- No operator-internal URLs, provisioning endpoints, or OEM build-server
  references are published here (see README safety section).
- No proprietary firmware images are linked or hosted.
