# Workaround: pairing a Bluetooth gamepad on the P610

**Scope:** Ocilion P610 with the stock firmware and the bundled R601
remote. Verified 2026-11-11 on the affected unit.

## The problem

With the R601 remote connected (the out-of-the-box state), pairing a
BT gamepad always fails: the bond succeeds, but GATT service
discovery aborts (`No resources to open a new connection`), the HID
profile never activates, and the box deletes the bond. Root cause:
the remote's permanent LE connection exhausts the
LE/GATT-client resource budget (see
[bt-hid-gatt-discovery.md](bt-hid-gatt-discovery.md)).

## The workaround (verified)

Pairing works when the remote is temporarily out of the equation.

### One-off procedure

1. **(Optional, recommended)** attach a USB keyboard so the box
   stays controllable without the remote.
2. **Power off the R601 remote** – simplest: remove its batteries.
   Wait a few seconds (its LE connection drops with a
   CONNECTION_TIMEOUT).
3. **(If an old gamepad is already bonded but disconnected)**
   power it off as well – not required, but keeps the test clean.
4. **Put the gamepad in pairing mode** (Xbox: pairing button ~3 s,
   fast LED blink).
5. **On the box:** *Einstellungen → Systemeinstellungen → Bluetooth →
   Zubehör hinzufügen* and select the controller. (The system
   "connect a controller" prompt from Steam Link & co. opens the
   same screen. ADB:
   `am start -n com.android.tv.settings/.accessories.AddAccessoryActivity`.)
6. **Pairing now succeeds** – the controller appears as an input
   device and works immediately.
7. **Restore the R601** (batteries in). It re-bonds automatically
   and coexists with the gamepad(s).
8. **(Optional)** switch any previously-off gamepads back on; they
   reconnect to their existing bonds.

### Repeatable & multi-gamepad

- The procedure is **repeatable**: it was used twice in one session
  to bring up a *second* gamepad the same way (first gamepad off,
  remote off, second paired, everything back on).
- End state achieved: **2× Xbox Wireless Controller + R601, all
  connected and working simultaneously.**
- Which device is paired "into which slot" does not matter.

### Persistence / reboot

- Bonds are **persistent across reboots**: after a full box reboot
  all bonded devices (remote + both gamepads) reconnect on their
  own; the box actively re-initiates the LE connections right after
  Bluetooth starts up (~1 min). No re-pairing needed ever again.
- Note: **Wi-Fi/USB debugging is switched off again after every
  reboot** and must be re-enabled in the settings UI (see
  [android.md](../android.md)) – relevant only for ADB workflows,
  not for the gamepad pairing itself.

### What does *not* help (tried)

- Controller factory reset (Xbox + pairing ~10 s) – no change.
- Deleting/re-creating the bond on the box – no change.
- Waiting for the R601 to disconnect on its own – it doesn't; its
  LE link stays up.

## When you do not want to touch the remote

- **USB tether** (Xbox One S/X/Series via Micro-USB/USB-C cable,
  Xbox One 2013 via the port on the pad) – zero pairing, always
  works.
- **2.4 GHz USB dongle gamepad** (8BitDo etc.) – pair to the dongle,
  not to the box.

## Status

Permanent fix requires a firmware change (increased
LE-connection / GATT-client budget, or pairing that does not need an
extra GATT client while a device is connected). A vendor-ready bug
report is at [bt-hid-gatt-discovery.md](bt-hid-gatt-discovery.md);
the escalation ticket is at [ticket-ocilion.md](ticket-ocilion.md).
