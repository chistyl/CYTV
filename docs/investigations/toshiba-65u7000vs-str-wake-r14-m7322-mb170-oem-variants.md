# Toshiba 65U7000VS STR wake R14 — M7322 MB170 board-name ambiguity and OEM variants

Date: 2026-11-06

## Purpose

Validate the R13 Taiwan M7322 / MB170 lead more carefully before treating it as relevant to Toshiba 65U7000VS. This pass checks the bootloader's board-selection definitions and OEM-specific variants.

## New finding: MB170 and MST148B board-name are not a unique product identity

In the public MStar bootloader repository:

- `Board.h` defines `BD_M7322_MB170` as a distinct board selector.
- The selected header `BD_M7322_MB170.h` sets `BOARD_NAME` to `MST148B_10NQAT_18115_M7322`.
- Separate board selectors also exist for `MST148B_10NQAT_18115_M7322_ARMANI`, `_TOPAZ`, and `_CHIEF`.
- The bootloader's `Config.in` exposes these as separate M7322 board options.
- A separate T31 bootloader configuration index includes an explicit `MST148B_10NQAT_18115_M7322_VESTEL_MB170` option, showing that the same general M7322/MB170 board lineage can have OEM-specific variants.

Pinned sources:
- Board selector/header: https://github.com/R0rt1z2/android_bootable_mstar/blob/25fbfedaad370ab6b8fe4e22cae1a49c43b91d21/uboot-mstar/t22/sboot/inc/M7322/board/Board.h
- Generic MB170 header: https://github.com/R0rt1z2/android_bootable_mstar/blob/25fbfedaad370ab6b8fe4e22cae1a49c43b91d21/uboot-mstar/t22/sboot/inc/M7322/board/BD_M7322_MB170.h
- Board option definitions: https://github.com/R0rt1z2/android_bootable_mstar/blob/25fbfedaad370ab6b8fe4e22cae1a49c43b91d21/uboot-mstar/t22/sboot/Config.in
- T31 OEM-variant references: https://github.com/davift/FireOS_FireTV_and_FireStick/blob/224f8510bd734acafb1a5bc9d4e2de5dfd0686f0/platform/bootable/bootloader/uboot-mstar/t31/sboot/Config.in

## Interpretation and correction to R13

R13 correctly identified a Taiwan-region M7322 MB170 SBoot configuration with EMAC and STR enabled. R14 adds an important limit: the generic MB170 board name is not a unique TV-brand or model identifier. The same broad board family has separate OEM-specific configurations, including a Vestel-labelled MB170 variant in another MStar bootloader tree.

Therefore:
- `TWN` is a region marker in that configuration, not proof of Toshiba identity.
- `MST148B_10NQAT_18115_M7322` is a board-name string, not a Toshiba 65U7000VS model mapping.
- The existence of a Vestel-specific variant does not prove the Toshiba uses it or any sibling variant.
- No Toshiba-specific mapping from `65U7000VS` / `xiaoyushan_etT` to MB170 or MST148B was found.

## What this means for the investigation

The R13 lead is now classified as a generic M7322 board-family reference, not a Toshiba match. It can guide source navigation only. It must not be used to select an EMAC V1/V3 implementation, DTS stage order, PHY configuration, or propose a firmware patch for Toshiba.

The decisive evidence remains a complete Toshiba build fingerprint/build ID plus matching bootloader/kernel image or OEM source manifest. A board selector extracted from the actual TV image would be especially useful; absent that, no board-level conclusion is justified.

## Safety and project boundaries

- Keep PR #47's Toshiba Android 9 + Ethernet VPN safety guard unchanged.
- Do not enable VPN ad filtering on the Toshiba 65U7000VS.
- Do not add diagnostic telemetry or ask the user to repeat completed tests.
- Keep PR #48 open/draft/unmerged; this document is research only.
- No APK, firmware image, CI build of firmware, or real-device verification is produced by this document.
