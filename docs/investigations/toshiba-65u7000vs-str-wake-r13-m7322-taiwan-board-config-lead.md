# Toshiba 65U7000VS STR wake R13 — M7322 Taiwan-board boot configuration lead

Date: 2026-11-06

## Purpose

Continue the Toshiba 65U7000VS Ethernet-loss investigation by checking whether publicly indexed MStar M7322 board configurations provide a closer Taiwan-region hardware lead. This remains source research only; no CYTV runtime behavior, diagnostics, or safety settings are changed.

## New finding: a Taiwan-region M7322 MB170 boot configuration exists publicly

Repository:
https://github.com/R0rt1z2/android_bootable_mstar

Exact file:
`uboot-mstar/t22/sboot/configs/M7322/.config.m7322.android.MB170.rom_emmc_64bit.optee.kdrv.asymetric_UMA_Amazon`

Pinned source:
https://github.com/R0rt1z2/android_bootable_mstar/blob/25fbfedaad370ab6b8fe4e22cae1a49c43b91d21/uboot-mstar/t22/sboot/configs/M7322/.config.m7322.android.MB170.rom_emmc_64bit.optee.kdrv.asymetric_UMA_Amazon

The fetched configuration explicitly contains:

- `CONFIG_MSTAR_M7322=y`
- `CONFIG_MSTAR_BD_M7322_MB170=y`
- `CONFIG_MSTAR_BD_BOARDNAME="MST148B_10NQAT_18115_M7322"`
- `CONFIG_HASH0_PATH_STRING="TWN"`
- `CONFIG_USE_EMAC=y` and `CONFIG_USE_GMAC` disabled
- `CONFIG_MSTAR_STR_ENABLE=y`

This is a materially closer research lead than a generic M7322 reference because it is a Taiwan-region (`TWN`) M7322 bootloader configuration and it explicitly selects EMAC and STR. It is still only a bootloader/SBoot configuration for board MB170 / board name MST148B_10NQAT_18115_M7322.

## Critical identity boundary

This file does **not** identify Toshiba 65U7000VS, does not mention Toshiba or `xiaoyushan_etT`, and does not prove the television uses MB170, MST148B_10NQAT_18115_M7322, this bootloader, or the same Ethernet PHY wiring.

It is not the Linux kernel .config, Linux EMAC V1/V3 selection, Android Ethernet framework source, or Toshiba vendor STR DTS. The fact that a configuration is marked TWN is not enough to attribute it to this Toshiba model.

## What it narrows

If the TV's actual build fingerprint, bootloader version string, board identifier, or firmware image later matches `M7322` + `MB170` / `MST148B_10NQAT_18115_M7322`, this source becomes a strong candidate lineage for deeper comparison. If it does not match, it must remain an unrelated M7322 example.

The next comparison targets, conditional on an exact identity match, are:
1. corresponding Linux kernel config and exact `CONFIG_MSTAR_EMAC` / `CONFIG_MSTAR_EMAC_V3` selection;
2. matching M7322 board DTS and Ethernet PHY/MDIO settings;
3. matching `Mstar-utopia2k-str` resume registration and callback ordering;
4. vendor EthernetService / NetworkManagement integration on the Android 9 image.

## Current conclusion

The public-source search has found a Taiwan-region M7322 + MB170 + EMAC + STR bootloader configuration, but still has not found a verified Toshiba 65U7000VS firmware image, complete build fingerprint, or OEM source manifest. It is a lead to validate against the TV's exact identity, not a confirmed match and not a basis for a patch.

## Safety and project boundaries

- Keep PR #47's Toshiba Android 9 + Ethernet VPN safety guard unchanged.
- Do not enable VPN ad filtering on the Toshiba 65U7000VS.
- Do not add diagnostic telemetry or ask the user to repeat completed tests.
- Keep PR #48 open/draft/unmerged; this document is research only.
- No APK, firmware image, CI build of firmware, or real-device verification is produced by this document.
