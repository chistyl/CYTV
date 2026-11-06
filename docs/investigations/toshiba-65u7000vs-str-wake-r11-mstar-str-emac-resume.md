# Toshiba 65U7000VS STR wake investigation R11 — MStar STR orchestration and EMAC resume path

Date: 2026-11-06

## New source-level finding

A public MStar kernel tree contains both:
- `drivers/mstar2/drv/utopia2k_str/mdrv_utopia2k_str.c`
- `drivers/mstar2/drv/emac/mdrv_emac.c` and `mdrv_emac_v3.c`

Source repository: https://github.com/jockyw2001/kernel
Commit: `ce2d9f19ed0f66a0398646f795bdc6735357022f`

This is a generic MStar kernel source reference, not a verified Toshiba 65U7000VS source revision.

## STR orchestration behavior found

The Utopia2K STR driver:
- registers platform suspend and resume callbacks;
- iterates registered modules and schedules each module's STR callback asynchronously;
- waits for the scheduled callback domain to complete before finishing the suspend or resume phase;
- clears the DTS post-condition list after each phase;
- exposes DTS-driven stage coordination through `pre-condition` and `post-condition` properties under `/Mstar-utopia2k-str/<module>/<suspend|resume>/stageN`.

Relevant source:
https://github.com/jockyw2001/kernel/blob/ce2d9f19ed0f66a0398646f795bdc6735357022f/drivers/mstar2/drv/utopia2k_str/mdrv_utopia2k_str.c

This means module callback ordering and stage dependencies can be platform/device-tree-specific. A generic statement that Ethernet always resumes before or after another module would be unjustified without the matching Toshiba DTS.

## EMAC suspend/resume behavior found

The MStar EMAC V1 implementation's suspend callback:
- sets `EP_FLAG_SUSPENDING`;
- stops the network TX queue;
- disables the device IRQ and link timer;
- disables MAC receiver/transmitter bits;
- disables PHY interrupts and MAC interrupts;
- performs a software reset.

Its resume callback:
- clears the suspending flag;
- powers on the EMAC clock;
- scans the PHY address and applies PHY patching;
- restores EMAC hardware initialization and MAC address;
- enables MDI and IRQ;
- reopens the device if its open flag is set.

Relevant source:
https://github.com/jockyw2001/kernel/blob/ce2d9f19ed0f66a0398646f795bdc6735357022f/drivers/mstar2/drv/emac/mdrv_emac.c

The V3 implementation differs materially:
- suspend also powers off the EMAC clock;
- if the interface was open, it calls `MDev_EMAC_close()` and sets `EP_FLAG_SUSPENDING_OPEND`;
- resume reinitializes hardware registers, enables IRQ, and conditionally calls `MDev_EMAC_open()`, then clears the reopen flag.

Relevant source:
https://github.com/jockyw2001/kernel/blob/ce2d9f19ed0f66a0398646f795bdc6735357022f/drivers/mstar2/drv/emac/mdrv_emac_v3.c

## Why this matters to the observed Toshiba symptom

Existing Toshiba logs establish that after STR resume Android clears IP configuration on `eth0`, the Ethernet NetworkAgent becomes DISCONNECTED, and DHCP recovery follows.

The MStar reference code shows a plausible lower-layer recovery boundary worth checking in the exact OEM build: EMAC/PHY hardware reinitialization and whether the network device is reopened during the resume callback. It also shows that STR module sequencing can be controlled by device-tree pre/post conditions.

However, these sources do NOT establish that:
- the user's Toshiba build uses this exact EMAC V1 or V3 driver;
- its Ethernet PHY or board configuration matches this reference tree;
- its Toshiba DTS registers the same STR modules/stages;
- the EMAC resume callback failed on the user's TV;
- EMAC teardown caused Android's observed IP clear, rather than being a parallel/recovery consequence.

## Revised investigation priority

1. Obtain exact Toshiba build fingerprint and matching kernel/vendor source manifest or system image.
2. Identify the actual loaded EMAC driver variant and platform configuration.
3. Inspect the matching Toshiba device tree for `Mstar-utopia2k-str` module registrations and suspend/resume stage dependencies.
4. Compare the exact Toshiba EMAC suspend/resume callback with the observed sequence: EMAC resume, PHY link, netdev carrier, EthernetService/IpClient, NetworkAgent DISCONNECTED, and DHCP recovery.
5. Only then decide whether the failure is in EMAC hardware recovery, STR callback ordering, Android Ethernet framework, or their interaction.

No patch is proposed from the generic reference code.

## Safety and project boundaries

- Keep PR #47's Toshiba Android 9 + Ethernet VPN safety guard unchanged.
- Keep PR #48 open/draft/unmerged; this is investigation documentation only.
- Do not change PR #35 or the accepted CYTV R4 baseline.
- Do not add CYTV network diagnostics/telemetry.
- Do not enable VPN ad filtering on Toshiba 65U7000VS.
- No APK, firmware image, CI build, or real-device verification is claimed by this documentation update.
