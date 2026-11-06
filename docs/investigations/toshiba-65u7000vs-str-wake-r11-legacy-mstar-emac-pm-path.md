# Toshiba 65U7000VS STR wake investigation R11 — legacy MStar EMAC PM path

Date: 2026-11-06

## New source-level reference

A public MStar GPL source tree was located:

- Repository: https://github.com/Benjamin-Dobell/kogan-tv-gpl
- File: `Kernel_updated/mstar/mstar/emac/mdrv_emac.c`
- Source path: legacy MStar EMAC driver (`Mstar-emac` platform driver).
- This is a Kogan TV GPL source tree, not Toshiba 65U7000VS source. It is used only as an architectural reference for the MStar EMAC family.

## Observed suspend path in the reference driver

The source's `mstar_emac_drv_suspend()` performs these operations:

1. Sets `EP_FLAG_SUSPENDING`.
2. Stops the netdev TX queue with `netif_stop_queue()`.
3. Disables the network IRQ and deletes the link timer.
4. Powers on the EMAC clock temporarily, disables MAC receiver/transmitter bits (`EMAC_TE | EMAC_RE`), disables PHY interrupt and MAC interrupts.
5. Powers off the EMAC clock and returns.

Notably, this legacy path does not visibly call `netif_device_detach()` or `netif_carrier_off()` in the suspend function.

## Observed resume path in the reference driver

The source's `mstar_emac_drv_resume()`:

1. Clears `EP_FLAG_SUSPENDING`.
2. Powers on the EMAC clock.
3. Reinitializes a MAC register and scans for PHY address.
4. Reapplies PHY patching and memory write protection.
5. Reinitializes EMAC hardware and programs MAC address.
6. Enables MDIO access and reads PHY state.
7. Re-enables network IRQ.
8. If the interface was open, calls `MDev_EMAC_open()`.

This demonstrates that at least one legacy MStar EMAC implementation explicitly performs substantial MAC/PHY reinitialization on resume. It provides a concrete code path to compare against the Toshiba implementation once its exact source is available.

## Important distinction from the 2025 MediaTek STAR EMAC fix

The separate upstream MediaTek STAR EMAC driver issue is not the same driver family or proof of the Toshiba cause. Its fix adds `netif_device_detach()` during suspend and `netif_device_attach()` during resume to avoid a linkwatch_work() suspend/resume hang. That is a useful contrast, not a patch recommendation for the legacy MStar Albany driver.

## What this does and does not prove

Established from this reference source:
- Legacy MStar EMAC has explicit platform suspend/resume callbacks.
- Resume reinitializes MAC/PHY hardware and reopens the interface if it had been open.
- Its suspend path stops queues, disables IRQ/timers and powers down EMAC clock.

Not established:
- The Toshiba 65U7000VS uses this exact driver revision or the same EMAC/PHY board wiring.
- Toshiba's Android 9 EthernetService or NetworkManagement calls these callbacks in the observed failure sequence.
- The reference code has the same bug as the Toshiba TV.
- Adding detach/attach, changing refCount, or modifying driver PM callbacks would fix the Toshiba TV.

## Updated investigation direction

The strongest missing evidence remains the exact Toshiba build fingerprint and matching vendor kernel/framework source. If a matching source tree becomes available, compare:
- MStar EMAC suspend/resume callback implementation;
- PHY IRQ and link timer handling;
- EMAC clock and MAC/PHY reinitialization ordering;
- Android EthernetService / EthernetNetworkFactory interactions;
- STR resume vendor callbacks that may clear `eth0` IP configuration.

Existing user-device logs continue to show IP configuration being cleared on `eth0` after STR resume, followed by Ethernet NetworkAgent DISCONNECTED and DHCP recovery. This reference source narrows what to inspect, but does not identify the Toshiba-side initiator.

## Safety / project boundaries

- PR #47 Toshiba Android 9 + Ethernet VPN safety guard remains unchanged.
- PR #48 remains investigation-only, open/draft/unmerged.
- PR #35 and accepted CYTV R4 baseline remain unchanged.
- No additional CYTV diagnostics or telemetry.
- No VPN ad filtering on Toshiba 65U7000VS.
- No APK, firmware build, CI pass, or real-device verification is claimed by this documentation update.

## Sources

- Legacy MStar EMAC driver source: https://github.com/Benjamin-Dobell/kogan-tv-gpl/blob/master/Kernel_updated/mstar/mstar/emac/mdrv_emac.c
- MediaTek STAR EMAC suspend/resume contrast: https://lists.openwall.net/netdev/2025/05/28/134
- Linux device PM suspend/resume lifecycle documentation: https://www.kernel.org/doc/html/latest/driver-api/pm/devices.html
