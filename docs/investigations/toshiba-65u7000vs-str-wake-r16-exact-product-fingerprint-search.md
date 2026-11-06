# Toshiba 65U7000VS STR / Ethernet investigation R16 — exact product fingerprint search

Date: 2026-11-06

## Scope

Continue R15 by searching public code and device inventories for the exact Toshiba product suffix `xiaoyushan_etT`, a matching Android build fingerprint, firmware/build.prop, and any Toshiba-specific vendor/kernel tree. The purpose is to determine whether public artifacts can identify the exact image behind the previously observed Android 9 / M7322 device family.

## Search results

### 1. Exact Toshiba product suffix did not produce a public source match

GitHub code searches for:
- `ro.product.name xiaoyushan_etT`
- `xiaoyushan_etT build.prop`
- `xiaoyushan_etT fingerprint`
- `xiaoyushan_etT vendor`
- `65U7000VS firmware Android 9 build`

returned no matching files in this pass.

This is a bounded search result, not proof that no such firmware exists privately or on other distribution channels.

### 2. Public A4 Android 9 inventory points to a separate Hisense India device entry

A public device inventory lists:
- Hisense HiSmartTV A4 (`xiaoyushan`, MStar M7322), India, Android TV 9
- A separate Android TV 11 update entry for the same broad device family

That inventory is not a Toshiba 65U7000VS firmware manifest and does not identify the exact product suffix `xiaoyushan_etT`, Toshiba build fingerprint, or the user's installed image. It cannot be used to transfer vendor packages, kernel config, STR callback order, or Ethernet driver assumptions.

Reference:
https://github.com/FJRG2007/polaris/blob/485e3fbc39fa8860e83cfcba14bacf5109d7f3a3/tv/examples/README.md

### 3. No exact Toshiba kernel/vendor tree surfaced

The searches did not identify a public source tree containing a Toshiba 65U7000VS-specific:
- Linux kernel defconfig or extracted `IKCONFIG`
- M7322 EMAC V1/V3 selection for the exact Toshiba product
- STR DTS stage registration or resume callback
- Ethernet vendor service / NetworkManagement integration

Generic M7322 and other manufacturer T22 trees remain comparison material only.

## Technical conclusion

R15/R16 narrow the blocker: the public evidence supports the broad M7322 / HiSmart A4 family, but does not identify the exact Toshiba product image. The exact product suffix and complete build fingerprint remain unverified. It would be unsafe to select an EMAC driver variant or propose a kernel/framework patch based on a sibling Hisense, Amazon, or generic MStar source tree.

The established prior device-side finding remains unchanged: the observed transition involved Android Ethernet state teardown around the STR/VPN transition. This research does not identify the initiating Toshiba vendor caller and does not reclassify the issue as a CYTV DNS configuration problem.

## Evidence needed to advance beyond public-source inference

The next decisive evidence is the exact set's full Android build fingerprint/build ID and, ideally, the matching OTA/system image or OEM source manifest. With that identity, the investigation can validate whether any public kernel/vendor source actually matches the user's Toshiba variant.

No request to repeat prior network tests is made. No new diagnostic telemetry, device commands, APK, firmware build, or real-device test was performed in this pass. No app code, safety guard, or accepted baseline was changed.
