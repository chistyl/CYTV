# Toshiba 65U7000VS STR / Ethernet investigation — R9 Taiwan owner reports

Date: 2026-11-06

## Purpose

Record independently published Taiwan owner reports relevant to standby/resume and firmware maintenance. These reports are supporting context only; they do not replace the previously collected device logs or prove the VPN-triggered Ethernet teardown root cause.

## Finding A — exact 65U7000VS standby / YouTube reconnect report

Mobile01 thread: https://www.mobile01.com/topicdetail.php?f=347&t=6752036

On 2023-03-07, an owner of Toshiba 65U7000VS reported:
- Before standby, YouTube had been used.
- After standby using the remote power button (without unplugging mains), YouTube could not connect/start normally after wake, or took a very long time.
- A full power disconnect/restart of Android allowed YouTube to start normally each time.

A reply in the same thread reported a different experience: that owner's set and a friend's set did not show the issue, and they mainly used 5 GHz Wi-Fi. This is therefore an intermittent / configuration-dependent community report, not a universal model defect.

Relevance: the symptom boundary (standby resume vs full Android reboot) is consistent with a state-recovery problem after STR on this model family. It strengthens the decision to investigate the TV's Android/vendor resume path rather than repeatedly changing CYTV DNS logic. It does not identify EthernetService, VPN, DHCP, or any particular vendor callback.

## Finding B — user report of no firmware update availability

Mobile01 thread: https://www.mobile01.com/topicdetail.php?f=347&p=1&t=6666376

On 2022-09-23, a 65U7000VS owner asked whether firmware could be updated, noting that the TV's system updater reported the latest version while the Android security patch remained at 2020-05. A reply said the set had never been updated. This is anecdotal and does not establish the complete firmware fingerprint or prove that every region/serial batch stopped receiving updates.

The Toshiba Taiwan public downloads page found in this pass exposes a generic download selector for drivers/manuals; the page content retrieved did not show a 65U7000VS TV firmware package. It is not sufficient evidence that Toshiba Taiwan hosts Android TV firmware for this model.

## Engineering interpretation

1. The exact-model standby report is independent public evidence that at least one 65U7000VS experienced a YouTube connectivity/recovery problem after standby and recovered after a full OS restart.
2. The counter-report means we must not describe this as affecting every 65U7000VS.
3. Combined with our existing device logs showing Ethernet IP configuration cleared and Ethernet NetworkAgent disconnected after STR resume, the best next target remains the vendor/system resume-to-Ethernet recovery path.
4. The public owner reports do not prove that the CYTV VPN is the root cause, nor do they prove that the AOSP refCount candidate is the correct Toshiba patch.
5. Exact Toshiba firmware fingerprint and matching vendor source/image are still missing.

## Boundaries retained

- Keep PR #47's accepted Toshiba Android 9 + Ethernet safety guard unchanged.
- Keep PR #48 open / Draft / unmerged.
- Do not change CYTV DNS, VPN, retry, or diagnostic behavior based on forum anecdotes.
- Do not request another round of already-completed device tests.
- Do not flash a Hisense or other non-matching image.
- No APK, firmware build, CI run, or real-device verification is claimed.

## References

- Mobile01: Toshiba 65U7000VS YouTube fails / slow to connect after standby, 2023-03-07: https://www.mobile01.com/topicdetail.php?f=347&t=6752036
- Mobile01: Toshiba 65U7000VS firmware update question, 2022-09-23: https://www.mobile01.com/topicdetail.php?f=347&p=1&t=6666376
- Toshiba Taiwan downloads page (generic download selector; no matching TV firmware surfaced in retrieved page): https://www.sdd.toshiba.com.tw/chinese/support/download.aspx
