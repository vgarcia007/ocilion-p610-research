# Vorschlag-Ticket: "Bildschirm an halten" defaultet auf USB, obwohl die P610 AC-powered ist

*Ready-to-send English/German improvement suggestion for Ocilion.
Deliberately phrased as a suggestion, not a defect. Before sending:
add contact details, optionally the unit serial.*

---

**Betreff:** P610 – Vorschlag: "Bildschirm an halten während angeschlossen" sollte standardmäßig AC berücksichtigen (Headless-Betrieb)

Sehr geehrtes Ocilion-Support-Team,

wir betreiben eine P610 im **Dauertestbetrieb ohne Monitor**
(Headless, unter einem Schreibtisch) und haben dabei ein kleines
Konfigurationsdetail entdeckt, das wir als **Verbesserungsvorschlag**
– ausdrücklich nicht als Fehler – an Sie weitergeben möchten.

## Beobachtung

- Die Android-Einstellung *"Bildschirm an halten, während
  angeschlossen"* (`stay_on_while_plugged_in`, Bitmaske: 1=AC,
  2=USB, 4=drahtlos) ist auf dieser Box standardmäßig auf **USB
  (Wert 2)** gesetzt.
- Die P610 wird jedoch **ausschließlich über den mitgelieferten
  AC-Netzadapter betrieben** – der USB-Port dient nicht der
  Stromversorgung.
- Konsequenz: Die Option greift im Normalbetrieb **nie**, da kein
  USB-Strom anliegt. Wer die Box im Headless-Betrieb dauerhaft
  wach halten möchte (z. B. als Streaming-Client, Server oder
  Testgerät), muss die Einstellung manuell auf AC umstellen
  (bzw. per ADB: `adb shell svc power stayon ac`).

## Vorschlag

Für eine künftige Firmware-Revision würde es Sinn ergeben, den
Default-Wert dieser Einstellung auf **AC (1)** zu setzen – oder
AC+USB (3) – da der AC-Netzadapter der einzige mitgelieferte und
einzige sinnvolle Strompfad ist. Damit würde die Option im
Headless-/Dauerbetrieb ohne zusätzliche Konfiguration greifen.

## Kontext (kein Defekt)

Im üblichen TV-Betrieb ist das Verhalten neutral: Der Bildschirm
geht nach der Inaktivitätszeit aus und wird per Fernbedienung
sofort wieder wach. Es handelt sich daher um eine reine
Ergonomie-/Konfigurationsfrage, die v. a. im Headless-Betrieb
relevant wird.

Gerne stellen wir das Testgerät für Rückfragen zur Verfügung.

Viele Grüße
*[Name, E-Mail, ggf. Seriennummer]*

---

*Interne Referenz (nicht mit abschicken): vollständige Analyse in
[../firmware.md](../firmware.md) (Abschnitt "Keep-awake for the
headless test box"). Englische Version:
[improvement-standby-default-en.md](improvement-standby-default-en.md).*
