# Ticket: Bluetooth gamepad cannot be paired on the P610 while the bundled remote is connected

*Ready-to-send English text for the Ocilion support ticket channel
(via komro). Before sending: add your contact details, optionally
identify the unit (serial number, etc. – deliberately left out, the
report is kept customer-neutral).*

---

**Subject:** P610 – Bluetooth gamepad pairing fails while the bundled remote is connected (repro + root-cause analysis + workaround)

Dear Ocilion support team,

on our **Ocilion P610** (Amlogic S905X5M, Android 14, fingerprint
`Ocilion-WL/HP4C06-Ocilion/HP4CD-Ocilion:14/UKG3.250826.001/C4.0.7_20260605`)
**no Bluetooth gamepad can be paired** while the bundled R601 remote
is connected, i.e. in the out-of-the-box state. We have analyzed the
problem in depth and can provide a full diagnosis with a
reproduction path and a verified workaround.

## Symptom

1. Put a gamepad (tested: 2× Xbox Wireless Controller, modern
   BLE-capable model) into pairing mode; on the box:
   *Settings → System settings → Bluetooth → Add accessory*.
2. The **SMP bond and LE encryption always succeed** – the device
   briefly shows as connected.
3. Then **GATT service discovery aborts**. From logcat:

   ```
   bta_gattc_process_api_open: No resources to open a new connection.
   bta_dm_search_cmpl: GATT active but no BLE connection, likely disconnected midway
   btif_dm_search_services_evt: No well known GATT services discovered
   ```

4. The HID profile therefore never activates, no input device is
   registered, and the box **deletes the bond itself** after ~70 s
   (UI shows "pairing failed").
5. Reproduced 4× identically with **two different controllers**,
   including after a full controller factory reset.

## Root cause (confirmed by isolation test)

The R601 remote holds a **permanent LE connection** to the box
since boot. While it is active, the stack refuses the additional
GATT clients that the gamepad pairing discovery requires ("No
resources to open a new connection"). We suspect a **too-small
LE-connection / GATT-client budget** in the BT controller firmware
or the `cqhci` configuration (Realtek RTL8852BS combo chip, kernel
HCI driver `cqhci`).

**Isolation test (performed on the same unit):**

- Remote powered off (batteries removed, its LE link dropped) →
  paired a second Xbox controller: **succeeded on the first
  attempt.** GATT discovery completed cleanly (HID service 0x1812
  + Battery service 0x180f found), the controller works
  immediately.
- Remote powered back on → it re-bonds automatically, and **both
  (later 2× controllers + remote) work simultaneously without
  errors.**
- Confirmation: ~3 minutes after the remote came back, the box
  re-triggered GATT discovery for the first controller and failed
  again immediately with "No resources to open a new connection" –
  the error recurs exactly when the remote is connected.
- The pairing **survives a reboot**: all bonded devices reconnect
  automatically; the box actively re-initiates the LE connections
  shortly after Bluetooth starts up.

## Impact

In the out-of-the-box state **no Bluetooth gamepad can be used** –
affected: Steam Link, Moonlight, any controller-based app in the
standard wireless setup. This is exactly the gaming use case the
P610 is marketed for.

## Workaround (verified, but not acceptable for end customers)

Briefly power off the remote (batteries out) → pair the gamepad →
power the remote back on. It works, is repeatable (we now run 2×
Xbox controllers + the remote simultaneously), and survives
reboots – but an end customer who simply wants to pair their
gamepad walks right into this bug.

## Requested actions

1. **Check the BT controller firmware / `cqhci` configuration:**
   `max_num_le_connections` and GATT-client allocation for this
   combo chip – the budget must allow a persistent remote
   connection **plus** a concurrent gamepad pairing/discovery.
2. **Fix the pairing flow** so a gamepad can be paired *while the
   remote is connected* (the out-of-the-box state). The isolation
   test above reproduces both the failing and the working case
   cleanly.
3. Alternatively, if this is a hardware/firmware limitation –
   **document it** (supported input paths / pairing instructions
   with the remote powered off) in the customer documentation.
4. **Firmware status:** our unit runs build `C4.0.7_20260605`.
   Is this GATT-discovery failure known, and is there a newer
   firmware that already fixes it? If so: how is it rolled out to
   provisioned devices?
5. We are available for an **ADB repro session** (read-only
   diagnostics) on the affected unit; full unredacted logs (bond
   events, GATT discovery, ACL lifecycles) can be provided on
   request.

Best regards
*[Name, e-mail, optionally the P610 serial number]*

---

*Internal reference (do not send): full vendor-ready report with
all log excerpts,
[bt-hid-gatt-discovery.md](bt-hid-gatt-discovery.md); workaround
doc,
[bt-hid-gatt-discovery-workaround.md](bt-hid-gatt-discovery-workaround.md);
German version, [ticket-ocilion.md](ticket-ocilion.md).*
