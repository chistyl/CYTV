# Toshiba 65U7000VS STR / Ethernet investigation R15 — M7322 kernel identity cross-check

Date: 2026-11-06

## Scope

Continue from R14 without treating generic M7322 / MB170 evidence as Toshiba-specific. This pass searched public GitHub code for the exact product suffix, M7322 kernel configuration, EMAC driver identifiers, and STR configuration markers.

## New public leads

### 1. A separate Toshiba/Hisense A4 product suffix exists in public device-specific work

A public repository, `cbkii/tvRAMguard`, identifies its target as:
- Android 9 / API 28
- `ro.product.device=xiaoyushan`
- `ro.product.name=xiaoyushan_emT`
- `ro.hardware=m7322`
- `ro.board.platform=m7322`

Its README describes this as a Toshiba/Hisense A4 device. This is an independently visible device-family lead, but it is specifically `xiaoyushan_emT`, not the previously observed Toshiba `xiaoyushan_etT` suffix. It does not establish that the user's 65U7000VS has the same product identity, firmware, kernel, board revision, or Ethernet implementation. Do not transfer any module/configuration or root conclusions from that repository to the user's TV.

Reference:
https://github.com/cbkii/tvRAMguard/blob/ca5b95f9ef03fb4dd8fa235d9621ebc93d1d6d5c/README.md

### 2. M7322 kernel configuration lead is Amazon Clara, not Toshiba

A public kernel repository contains `arch/arm64/configs/t22_defconfig` with:
- `CONFIG_MSTAR_CHIP_NAME="m7322"`
- MStar T22 platform configuration

The repository is named `android_kernel_amazon_clara`. This confirms that a public T22/M7322 kernel configuration exists, but the repository identity is Amazon Clara. It is not evidence of Toshiba 65U7000VS source or its actual kernel config. GitHub code search did not locate an exact Toshiba `xiaoyushan_etT` kernel config or `mdrv_emac` match in this pass.

Reference:
https://github.com/R0rt1z2/android_kernel_amazon_clara/blob/24339f284ec3863e3503561cb24fb198fc91af72/arch/arm64/configs/t22_defconfig

### 3. No exact Toshiba EMAC / STR source match surfaced

Searches for `xiaoyushan_etT`, `xiaoyushan Toshiba 65U7000VS`, `mdrv_emac M7322`, and `MSTAR_UTOPIA2K_STR M7322 kernel` did not return a matching Toshiba 65U7000VS kernel tree, vendor EMAC implementation, or Toshiba STR callback. Results that mention M7322 are generic, unrelated platforms, or other product variants.

## Assessment

- The M7322 platform family remains plausible and consistent with prior public clues.
- The `emT` and `etT` product suffixes must remain distinct. No source found proves they share an identical system image or Ethernet/STR implementation.
- The Amazon Clara T22 defconfig is useful as a platform comparison only, not as Toshiba evidence.
- No newly found source changes the established diagnosis boundary: prior device logs showed Android Ethernet teardown after the STR/VPN transition, but the Toshiba-side initiating caller and exact vendor/kernel path remain unidentified.
- This pass does not prove a specific EMAC defect, STR callback ordering defect, Android Ethernet framework defect, or applicability of the AOSP refCount candidate patch.

## Next decisive evidence

1. Full build fingerprint and build ID from the exact Toshiba 65U7000VS.
2. Matching Toshiba OTA/system image or OEM source manifest.
3. Matching kernel `/proc/config.gz` or extracted kernel config and exact EMAC driver/module identity.
4. Toshiba vendor STR DTS/module registration and resume callback ordering.

No device commands, diagnostic telemetry, APK, firmware build, or real-device test were performed in this research pass. No safety guard or accepted baseline was changed.
