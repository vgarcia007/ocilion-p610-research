# Bug report: Bluetooth gamepad pairing fails at GATT service discovery

*Prepared for escalation to Ocilion / Skyworth (OEM). Device details are
redacted where they identify the customer; the log excerpts use the
stack's own `XX:XX:XX:XX` masking.*

**Status:** open · **Reproduced:** 3× on one unit (incl. after controller
factory reset) · **Root cause:** confirmed by isolation test 2026-11-11
(see §5) · **Workaround:** found (pair with the remote powered off, see
§6) · **Severity:** functional – BT gamepad pairing blocked while the
bundled remote is connected

## 1. Summary

Pairing a Bluetooth **gamepad** to the P610 **fails while the bundled
R601 remote is connected** (the out-of-the-box state): the SMP bond and
LE encryption succeed, but **GATT service discovery aborts** with
`No resources to open a new connection` and reports `No well known GATT
services discovered`. The HID profile never activates; the box deletes
the bond ~70 s later and the UI shows "Kopplung nicht möglich"
(pairing failed).

**Confirmed by isolation test:** with the R601 powered off, the same
controller pairs successfully on the first attempt, and after restoring
the remote both operate simultaneously (see §5). Root cause: the
LE-connection / GATT-client resource budget is exhausted by the
remote's permanent connection.

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

## 5. Root cause (confirmed by isolation test, 2026-11-11)

**LE connection / GATT client resource budget.** The R601 remote holds
a **permanent LE connection** (ACL + GATT clients) from boot. While it
is connected, the stack refuses the additional GATT clients needed for
gamepad pairing discovery (`No resources to open a new connection`),
discovery aborts, the HID profile never activates, and the box deletes
the bond. The Realtek RTL8852BS combo / `cqhci` driver or the BT shim
is evidently configured with a too-small max-connected /
max-GATT-clients budget for a host that always keeps its remote
connected.

**Isolation test (performed on the same unit):**
- R601 batteries removed → its LE ACL dropped (CONNECTION_TIMEOUT).
- Second Xbox controller (`ad:1c`) put into pairing mode and paired
  via the stock *Zubehör hinzufügen* picker (launched via ADB, so no
  remote was needed).
- **Pairing succeeded on the first attempt:** GATT discovery returned
  the HID (0x1812) and Battery (0x180f) services,
  `BTHH_CONN_STATE_CONNECTED ready:T`, the controller appeared as an
  input device and works flawlessly. No "No resources" / "No well
  known GATT services" events during the successful pairing (vs.
  every previous attempt).
- R601 restored → it re-bonds automatically → **remote and gamepad
  operate simultaneously.**
- Additional confirmation: ~3 min after the R601 came back, the box
  re-triggered GATT discovery for the *first* Xbox (`a7:e2`) and
  immediately failed again with `No resources to open a new
  connection` – i.e. the error recurs exactly when the remote is
  connected and is not a one-off state.

Secondary observation: during the failed attempts the LE link was
reported to drop "midway" during discovery – a possible controller/
firmware scheduling issue on the shared combo chip once the resource
limit is hit.

## 6. Impact

- In the out-of-the-box state (remote bonded, as shipped) **no
  Bluetooth gamepad can be paired**: blocks Steam Link / Moonlight /
  any controller-based app in the standard wireless setup.
- Workarounds, **verified on the affected unit**:
  - (a) Power off the R601 (batteries out), pair the gamepad,
    restore the remote – works. Repeated a second time with a
    *second* gamepad: end state **2× gamepad + remote, all
    connected and working simultaneously**.
  - (b) A full **reboot does not break the workaround**: all bonded
    devices (remote + both gamepads) remain bonded after reboot and
    the controllers reconnect automatically (user-verified; ADB
    cross-check: both Xbox controllers received re-initiated LE
    connections from the box within ~1 min of BT startup). Only
    the initial pairing needs the remote powered off.
  - (c) USB tether / 2.4 GHz dongle – works, but needs extra cables.

## 7. Requested vendor action

1. Check the BT controller firmware / `cqhci` configuration:
   `max_num_le_connections`, GATT client allocation in the Android BT
   shim for this combo chip; the budget must allow a persistent remote
   connection **plus** one concurrent gamepad pairing/discovery.
2. Fix the pairing flow so a gamepad can be paired **while the R601
   is connected** (the current out-of-the-box state) – the isolation
   test in §5 reproduces both the failure and the successful path.
3. Test a second gamepad class (PS4/PS5, 8BitDo) to confirm the
   failure is device-class general, not Xbox-specific.
4. Until a firmware fix ships: document the working pairing procedure
   (remote powered off during pairing) in the operator manual /
   customer FAQ.

## 8. Contact / data availability

Full unredacted logs (bond events, GATT discovery, ACL lifecycle) are
available from the researcher; the box can be made available for a
repro session under ADB (read-only diagnostics).
