# Toshiba 65U7000VS STR wake investigation R10 — M7322 kernel-source cross-check

Date: 2026-11-06

## New public source lead

A public Amazon OSS kernel repository was located:

- Repository: https://github.com/Amazon-OSS-Kernels/android_kernel_mstar_t31
- Branch: `insignia_4k_uhd/amazonfiretvedition_4k_uhd-7.6.4.6-20230601`
- The repository describes itself as an Amazon Fire TV T31 kernel source tree and lists several Amazon Fire TV Toshiba product families. It is not identified as the Toshiba 65U7000VS firmware/source tree.
- Kernel config: `kernel/mstar/t31/4.9/.config_m7322_SMP_arm64_emmc_nand_utopia2k`
- A second M7322 config variant is `.config_m7322_SMP_arm64_MI_emmc_nand_utopia2k_fusion`.

## What the M7322 configs establish

The public M7322 kernel configuration contains these platform options:

- `CONFIG_MSTAR_M7322=y`
- `CONFIG_MSTAR_EMAC=y` and `CONFIG_MSTAR_EMAC_V3=y`
- `CONFIG_ETHERNET_ALBANY=y`
- `CONFIG_DISCONNECT_DELAY_S=1`
- In the alternate M7322 config: `CONFIG_MSTAR_UTOPIA2K_STR=y`, `CONFIG_MSTAR_PM=y`, and `CONFIG_MP_PLATFORM_PM=y`.

This is useful because it shows a real M7322-based TV kernel family has distinct MStar Ethernet MAC/PHY and STR/power-management integration. It supports keeping the investigation at the vendor kernel / STR / Ethernet-driver boundary rather than treating the symptom as a DNS configuration defect.

## What it does NOT establish

- The repository is an Amazon Fire TV kernel source tree; its listed Toshiba products are Fire TV models, not the user's Toshiba 65U7000VS Android TV 9 build.
- The kernel config is a configuration, not proof that the Toshiba 65U7000VS uses the same source revision, board config, Ethernet PHY, driver patches, or STR callbacks.
- The config does not identify which Android framework/vendor service calls Ethernet teardown during STR resume.
- It does not prove that `CONFIG_DISCONNECT_DELAY_S=1` causes the observed failure, nor that changing it would fix the TV.
- No matching Toshiba 65U7000VS OTA/system image, complete build fingerprint, vendor framework source, or exact kernel source manifest has been found.

## Current causal boundary

Existing device logs still establish the observed sequence on the user's TV: Ethernet IP configuration is cleared after STR resume, the Ethernet NetworkAgent becomes DISCONNECTED, and DHCP recovery follows. The initiating Toshiba vendor callback remains unidentified.

The new M7322 source lead narrows the next source-level search to the MStar EMAC/Albany driver and the MStar STR/PM integration, but only after an exact Toshiba firmware/source match is found. It is not sufficient evidence to propose a kernel patch or flash a replacement image.

## Safety and project boundaries

- Keep PR #47's Toshiba Android 9 + Ethernet VPN safety guard unchanged.
- Keep PR #48 open/draft/unmerged; this document is investigation only.
- Do not change PR #35 or the accepted CYTV R4 baseline.
- Do not add CYTV network diagnostics/telemetry.
- Do not enable VPN ad filtering on the Toshiba 65U7000VS.
- No APK, firmware image, CI build, or real-device verification is claimed by this documentation update.

## Sources

- Amazon OSS MStar T31 kernel repository and supported-device list: https://github.com/Amazon-OSS-Kernels/android_kernel_mstar_t31
- M7322 kernel config (Ethernet MAC/Albany and disconnect-delay settings): https://github.com/Amazon-OSS-Kernels/android_kernel_mstar_t31/blob/insignia_4k_uhd/amazonfiretvedition_4k_uhd-7.6.4.6-20230601/kernel/mstar/t31/4.9/.config_m7322_SMP_arm64_emmc_nand_utopia2k
- Alternate M7322 config (MStar STR/PM integration): https://github.com/Amazon-OSS-Kernels/android_kernel_mstar_t31/blob/insignia_4k_uhd/amazonfiretvedition_4k_uhd-7.6.4.6-20230601/kernel/mstar/t31/4.9/.config_m7322_SMP_arm64_MI_emmc_nand_utopia2k_fusion
