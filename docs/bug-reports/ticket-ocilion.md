# Ticket: Bluetooth-Gamepad lässt sich nicht mit der P610 koppeln

*Fertiger Text für den Support-Ticket-Channel von Ocilion (via
komro). Vor dem Absenden: eigene Kontaktdaten ergänzen, Gerät
optional identifizieren (Seriennummer etc. – bewusst nicht
vorbelegt, da der Report kundendatenneutral gehalten ist).*

---

**Betreff:** P610 – Bluetooth-Gamepad-Kopplung schlägt fehl, solange die mitgelieferte Fernbedienung verbunden ist (Repro + Analyse + Workaround)

Sehr geehrtes Ocilion-Support-Team,

an unserer **Ocilion P610** (Amlogic S905X5M, Android 14,
Fingerprint
`Ocilion-WL/HP4C06-Ocilion/HP4CD-Ocilion:14/UKG3.250826.001/C4.0.7_20260605`)
lässt sich **kein Bluetooth-Gamepad koppeln**, solange die
mitgelieferte R601-Fernbedienung wie im Auslieferungszustand
verbunden ist. Wir haben das Problem ausführlich analysiert und
können Ihnen eine vollständige Diagnose mit Reproduktionspfad und
Workaround liefern.

## Symptom

1. Gamepad (getestet: 2× Xbox Wireless Controller, moderne
   BLE-Variante) in Pairing-Modus, auf der Box
   *Einstellungen → Bluetooth → Zubehör hinzufügen*.
2. Die **Kopplung (Bond) und die Verschlüsselung schlagen immer
   erfolgreich** zu – das Gerät wird kurz als verbunden angezeigt.
3. Dann bricht die **GATT-Service-Discovery** ab. Aus dem Logcat:

   ```
   bta_gattc_process_api_open: No resources to open a new connection.
   bta_dm_search_cmpl: GATT active but no BLE connection, likely disconnected midway
   btif_dm_search_services_evt: No well known GATT services discovered
   ```

4. Das HID-Profil aktiviert sich daher nie, der Controller wird
   nicht als Eingabegerät angezeigt, und die Box **löscht den Bond
   selbst** nach ca. 70 Sekunden (UI: „Kopplung nicht möglich").
5. Reproduziert 4× identisch an zwei verschiedenen Controllern,
   auch nach Factory-Reset des Controllers.

## Ursache (durch Isolationsversuch bestätigt)

Die R601-Fernbedienung hält **seit dem Boot eine permanente
LE-Verbindung** zur Box offen. Solange diese aktiv ist, verweigert
der Stack die zusätzlichen GATT-Clients, die die
Pairing-Discovery des Gamepads benötigt („No resources to open a
new connection"). Wir vermuten ein **zu kleines Budget für
LE-Verbindungen/GATT-Clients** in der BT-Controller-Firmware bzw.
der `cqhci`-Konfiguration (Realtek RTL8852BS-Combo-Chip, Kernel-
HCI-Treiber `cqhci`).

**Isolationsversuch (am selben Gerät durchgeführt):**

- Fernbedienung ausgeschaltet (Batterien entfernt, LE-Link
  trennte sich) → zweiter Xbox-Controller gepaired:
  **erfolgreich beim ersten Versuch.** GATT-Discovery lief sauber
  durch (HID-Service 0x1812 + Battery-Service 0x180f gefunden),
  Controller funktioniert sofort.
- Fernbedienung wieder eingeschaltet → sie verbindet sich
  automatisch, **und beide (später 2× Controller + Fernbedienung)
  arbeiten gleichzeitig fehlerfrei.**
- Bestätigung: ca. 3 Minuten nach Wiedereinschalten der
  Fernbedienung löste die Box eine erneute GATT-Discovery für den
  ersten Controller aus – und scheiterte sofort wieder mit
  „No resources to open a new connection". Der Fehler tritt also
  reproduzierbar genau dann auf, wenn die Fernbedienung verbunden
  ist.
- Die Kopplung **übersteht einen Neustart**: alle gekoppelten
  Geräte verbinden sich nach dem Reboot automatisch neu.

## Auswirkung

Im Auslieferungszustand ist **kein Bluetooth-Gamepad einsetzbar** –
betroffen: Steam Link, Moonlight, jede Controller-Anwendung im
drahtlosen Setup. Das ist der für die P610 beworbene
Gaming-Alltag.

## Workaround (verifiziert, aber nicht akzeptabel für den Kunden)

Fernbedienung kurz ausschalten (Batterien raus) → Gamepad pairen →
Fernbedienung wieder einschalten. Funktioniert, ist beliebig
wiederholbar (wir betreiben inzwischen 2× Xbox-Controller +
Fernbedienung gleichzeitig) und übersteht Neustarts – aber ein
Endkunde, der einfach sein Gamepad koppeln will, stolpert exakt
auf diesen Bug.

## Bitte um Maßnahmen

1. **Prüfung der BT-Controller-Firmware / `cqhci`-Konfiguration:**
   `max_num_le_connections` und die GATT-Client-Vergabe in der
   BT-Stack-Konfiguration für diesen Combo-Chip – das Budget muss
   eine permanente Fernbedienungsverbindung **plus** ein
   gleichzeitiges Gamepad-Pairing (-Discovery) erlauben.
2. **Reparatur des Pairing-Flows**, damit sich ein Gamepad koppeln
   lässt, *während* die Fernbedienung verbunden ist
   (Auslieferungszustand). Der Isolationsversuch oben reproduziert
   sowohl den Fehler- als auch den Erfolgsfall sauber.
3. Alternativ: falls es eine Hardware-/Firmware-Limitierung ist –
   das **dokumentieren** (Unterstützte Eingabewege / Pairing-Anleitung
   mit ausgeschalteter Fernbedienung) in der Kunden-Dokumentation.
4. Wir stehen für eine **Repro-Session unter ADB** (read-only
   Diagnostik) an dem betroffenen Gerät zur Verfügung; vollständige
   ungekürzte Logs (Bond-Events, GATT-Discovery, ACL-Lebenszyklen)
   können wir auf Anfrage liefern.

Viele Grüße
*[Name, E-Mail, ggf. Serielle/Nr. der P610]*

---

*Interne Referenz (nicht mit abschicken): vollständiger
vendor-ready Report mit allen Log-Auszügen,
[bt-hid-gatt-discovery.md](bt-hid-gatt-discovery.md); Workaround-
Doku, [bt-hid-gatt-discovery-workaround.md](bt-hid-gatt-discovery-workaround.md).*
