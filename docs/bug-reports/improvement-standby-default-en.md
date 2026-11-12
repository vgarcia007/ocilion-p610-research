# Improvement ticket: "Keep screen on while plugged in" defaults to USB on an AC-powered P610

*Ready-to-send English improvement suggestion for Ocilion.
Deliberately phrased as a suggestion, not a defect. Before sending:
add contact details, optionally the unit serial.*

---

**Subject:** P610 – Suggestion: default the "stay awake while plugged in" setting to AC (headless operation)

Dear Ocilion support team,

we run a P610 as a **permanently on headless test unit** (no
monitor, under a desk) and noticed a small configuration detail we
would like to pass on as an **improvement suggestion** –
explicitly not as a defect.

## Observation

- The Android setting *"Stay awake while plugged in"*
  (`stay_on_while_plugged_in`, bitmask: 1=AC, 2=USB, 4=wireless)
  defaults to **USB (value 2)** on this box.
- The P610 is **powered exclusively via the supplied AC adapter** –
  the USB port is never used for power.
- Consequence: in normal operation the option **never applies**,
  since no USB power is present. Anyone who wants to keep the box
  awake in headless/24-7 operation (e.g. as a streaming client,
  server, or test device) has to change the setting to AC
  manually (or via ADB: `adb shell svc power stayon ac`).

## Suggestion

For a future firmware revision it would make sense to default this
setting to **AC (1)** – or AC+USB (3) – since the AC adapter is
the only supplied and only sensible power path. That way the option
would work out of the box for headless / continuous operation.

## Context (not a defect)

In regular TV use the behavior is neutral: the screen turns off
after the inactivity timeout and is instantly woken by the remote.
This is therefore purely an ergonomics / configuration matter that
mostly matters for headless operation.

We are happy to make the test unit available for any follow-up
questions.

Best regards
*[Name, e-mail, optionally the serial number]*

---

*Internal reference (do not send): full analysis in
[../firmware.md](../firmware.md) (section "Keep-awake for the
headless test box"). German version:
[improvement-standby-default.md](improvement-standby-default.md).*
