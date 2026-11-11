# Bug report: Bluetooth gamepad pairing fails at GATT service discovery

*Prepared for escalation to Ocilion / Skyworth (OEM). Device details are
redacted where they identify the customer; the log excerpts use the
stack's own `XX:XX:XX:XX` masking.*

**Status:** open · **Reproduced:** 3× on one unit (incl. after controller
factory reset) · **Severity:** functional – all BT gamepads unusable

## 1. Summary

Pairing any Bluetooth **gamepad** to the P610 fails inside the system
pairing flow. The SMP bond and LE encryption succeed, but **GATT service
discovery aborts** with `No resources to open a new connection` and
reports `No well known GATT services discovered`. The HID profile can
therefore never activate; the box deletes the bond ~70 s later and the
UI shows "Kopplung nicht möglich" (pairing failed).

Bluetooth input **works for the shipped R601 remote** (BLE), so the
issue is specific to the gamepad-class HID/GATT path, not to BT in
general.

## 2. Environment

| Item | Value |
|------|-------|
| Model / build | Ocilion P610 (Skyworth HP4C06/HP4CD, Amlogic S905X5M `ross`) |
| Fingerprint | `Ocilion-WL/HP4C06-Ocilion/HP4CD-Ocilion:14/UKG3.250826.001/C4.0.7_20260605:user/release-keys` |
| Kernel | 5.15.196-android14 |
| BT/Wi-Fi combo | Realtek RTL8852BS (SDIO), kernel HCI driver `cqhci` (Amlogic) |
| BT adapter | on at boot, adapter MAC efuse, name "TV Box", `bluetooth.profile.hid.host.enabled=true` |
| Android BT stack | Android 14 "shim" (GATT + `shim::legacy::hid` BTHH path) |
| Repro date | 2026-11-11 |

## 3. Reproduction steps

1. Box in stock state (R601 remote already bonded – as shipped; it
   holds a **permanent LE connection** since boot).
2. Put an **Xbox Wireless Controller** (modern, BLE-capable) into
   pairing mode (pairing button ~3 s).
3. On the box: *Einstellungen → Systemeinstellungen → Bluetooth →
   Zubehör hinzufügen* (or the system "connect a controller" prompt
   after installing e.g. Steam Link; same flow,
   `com.google.android.intent.action.CONNECT_INPUT` →
   `AddAccessoryActivity`).
4. Select the controller → bond succeeds → "Verbinden" → fails.
5. Observed 3× identical outcome, including once after a full
   controller factory reset and removal of its previous host bond.

## 4. Evidence (device logs, read-only)

**Bonding succeeds, discovery fails:**

```
19:36:42  CBACK  Bond state changed  a7:e2  bond_state:1  reason:HCI_SUCCESS
19:36:43  CBACK  Bond state changed  a7:e2  bond_state:2  reason:HCI_SUCCESS   (BONDED)
19:41:01  SEC    Encryption success  a7:e2  transport:BT_TRANSPORT_LE
19:40:01  GATTC_Discover  conn_id=0x0101  disc_type=2, s_handle=0x0001, e_handle=0xffff
19:40:01  bta_gattc_process_api_open: No resources to open a new connection.
19:40:01  bta_dm_search_cmpl: GATT active but no BLE connection, likely disconnected midway
19:40:01  btif_dm_search_services_evt: No well known GATT services discovered
```

**Profile layer gives up, box deletes the bond itself:**

```
19:40:04  E BluetoothHidHostService: Hid Device not disconnected: a7:e2
          (earlier runs: 'Failed to connect HID device', HID conn state 0->1->0 in ~6 ms)
19:42:08  API  Remove bond  a7:e2   /  CONNECTION_TERMINATED_BY_LOCAL_HOST
              HID_HOST connection policy: 100 -> -1
```

**Working baseline (same stack path):** the shipped R601 remote is a
**BLE** device, bonds `[LE]`, connects via `BT_TRANSPORT_LE` and keeps a
persistent HID connection (`shim::legacy::hid … BTHH_CONN_STATE_CONNECTED
ready:T`, ACL handle 0x0010 since 16:09:57 boot). Simple BLE HID
therefore works on this unit.

## 5. Suspected root cause (for vendor triage)

- **LE connection / GATT client resource budget:** with the R601's
  permanent connection (1 LE ACL + its GATT clients) already open, the
  stack refuses the second GATT client needed for pairing discovery
  (`No resources to open a new connection`). The Realtek combo /
  `cqhci` driver or the BT shim may be configured with a too-small
  max-connected / max-GATT-clients budget for a host that always keeps
  a remote bonded.
- Secondary: even when the second GATT client is opened, discovery
  shows the LE link dropping "midway" – a possible controller/firmware
  scheduling issue on the shared combo chip.

**Open isolation test (not yet performed, needs customer action):**
pair the gamepad while the R601 is temporarily out of the equation
(batteries out / BT toggle). If discovery then completes, the resource
budget is confirmed as the cause.

## 6. Impact

- No Bluetooth gamepad can be used: blocks Steam Link / Moonlight /
  any controller-based app in the standard (wireless) setup.
- Workaround exists (USB tether / 2.4 GHz dongle) but is not the
  intended UX and needs extra cables.

## 7. Requested vendor action

1. Check the BT controller firmware / `cqhci` configuration:
   `max_num_le_connections`, GATT client allocation in the Android BT
   shim for this combo chip.
2. Verify the pairing flow with the R601 **and without** it
   (isolation of the resource hypothesis).
3. Test a second gamepad class (PS4/PS5, 8BitDo) to confirm the
   failure is device-class general, not Xbox-specific.
4. Fix or, if a hardware/firmware limitation, document the supported
   gamepad input paths in the operator manual.

## 8. Contact / data availability

Full unredacted logs (bond events, GATT discovery, ACL lifecycle) are
available from the researcher; the box can be made available for a
repro session under ADB (read-only diagnostics).
