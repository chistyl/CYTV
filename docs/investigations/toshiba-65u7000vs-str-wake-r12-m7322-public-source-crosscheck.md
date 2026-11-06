# Toshiba 65U7000VS STR wake R12 — M7322 public-source cross-check

Date: 2026-11-06

## Purpose

Continue the Toshiba 65U7000VS Ethernet-loss investigation from the R11 boundary. This pass checks whether additional public MStar/M7322 kernel and device-tree material can narrow the STR resume path. It does not change CYTV runtime behavior, add diagnostics, or alter the Toshiba safety guard.

## New public-source findings

### 1. TCL OpenSource MT9221 kernel exposes M7322 STR device-tree material

Repository: https://github.com/TCLOpenSource/mt9221

A public GitHub code search found device-tree files including:

- `arch/arm/boot/dts/m7322_an.dts`
- `arch/arm/boot/dts/m7322_303A.dts`
- `arch/arm/boot/dts/m7332.dtsi`
- `arch/arm/boot/dts/m7332_nontee.dtsi`

Search excerpts show the `Mstar-utopia2k-str` compatible node and resume-stage declarations in the M7322-family DTS files. The excerpts returned in this pass specifically expose an `mhl` resume stage; they do not expose an Ethernet/EMAC STR stage or a Toshiba-specific DTS configuration.

This is useful as a source-navigation lead only. The repository is a TCL TV kernel, not a verified Toshiba 65U7000VS kernel. Its board DTS and STR stage order must not be attributed to Toshiba.

Relevant source references:
- https://github.com/TCLOpenSource/mt9221/blob/098aa173622fa59a5531b9b01d8f9fc3b0e539f8/arch/arm/boot/dts/m7322_an.dts
- https://github.com/TCLOpenSource/mt9221/blob/098aa173622fa59a5531b9b01d8f9fc3b0e539f8/arch/arm/boot/dts/m7322_303A.dts
- https://github.com/TCLOpenSource/mt9221/blob/098aa173622fa59a5531b9b01d8f9fc3b0e539f8/arch/arm/boot/dts/m7332.dtsi

### 2. Independent M7322 kernel configuration confirms Utopia2K STR is a real platform build option

An Amazon OSS kernel source publishes a configuration named `.config_m7322_SMP_arm64_emmc_nand_utopia2k_fusion`. Its configuration enables both:

- `CONFIG_MSTAR_M7322=y`
- `CONFIG_MSTAR_UTOPIA2K_STR=y`

Source:
https://github.com/Amazon-OSS-Kernels/android_kernel_mstar_t31/blob/insignia_4k_uhd/amazonfiretvedition_4k_uhd-7.6.4.6-20230601/kernel/mstar/t31/4.9/.config_m7322_SMP_arm64_MI_emmc_nand_utopia2k_fusion

This independently confirms that M7322-family builds can combine the M7322 platform with Utopia2K STR. It does not identify the Toshiba kernel version, exact board configuration, Ethernet driver variant, or vendor STR callback ordering.

### 3. EMAC V3 resume implementation remains a candidate, not a Toshiba finding

The publicly indexed MStar EMAC V3 source contains the `EP_FLAG_SUSPENDING_OPEND` path and a resume-side conditional reopen. This is consistent with R11's generic driver analysis, but no evidence in this pass ties this exact source revision or V3 implementation to the Toshiba 65U7000VS.

Source:
https://github.com/TCLOpenSource/mt9221/blob/098aa173622fa59a5531b9b01d8f9fc3b0e539f8/drivers/mstar2/drv/emac/mdrv_emac_v3.c

## What this pass does and does not establish

Established:
- Public M7322-family source material contains Utopia2K STR DTS declarations.
- At least one independently published M7322 kernel configuration enables Utopia2K STR.
- MStar EMAC V3 has a suspend/reopen state path that merits comparison if Toshiba's actual driver is identified.

Not established:
- Toshiba 65U7000VS uses the TCL MT9221 DTS or the Amazon kernel configuration.
- Toshiba uses EMAC V3 rather than V1 or a vendor-modified driver.
- Toshiba's actual DTS registers EMAC in a particular STR resume stage.
- The Ethernet loss is caused by EMAC, STR callback ordering, EthernetService, or a particular AOSP refCount defect.

## Next decisive evidence

The highest-value missing evidence remains the TV's complete Android build fingerprint/build ID and a matching Toshiba OTA/system image or OEM kernel/vendor source manifest. With that exact identity, the next pass can test whether any public M7322 source is actually a valid match, then compare the loaded EMAC implementation and DTS STR registration. Until then, no framework or kernel patch should be proposed as a Toshiba fix.

## Safety and project boundaries

- Keep PR #47's Toshiba Android 9 + Ethernet VPN safety guard unchanged.
- Do not enable VPN ad filtering on the Toshiba 65U7000VS.
- Do not add diagnostic telemetry or ask the user to repeat completed tests.
- Keep PR #48 open/draft/unmerged; this document is research only.
- No APK, firmware image, CI build, or real-device verification is produced by this document.
