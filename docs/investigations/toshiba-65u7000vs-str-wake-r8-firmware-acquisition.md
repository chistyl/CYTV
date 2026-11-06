# Toshiba 65U7000VS STR / Ethernet investigation — R8 firmware acquisition cross-check

Date: 2026-11-06

## Scope

This is a documentation-only continuation of the Toshiba 65U7000VS investigation. It does not change CYTV networking, DNS, VPN, retry, or diagnostic behavior. The previously accepted Toshiba Android 9 + Ethernet safety guard remains the protective behavior. No device retest is requested.

## New public-source cross-check

### 1. The Toshiba product suffix is not resolved by the shared device codename

Public Google Play device-catalog results list both `xiaoyushan_emT` and `xiaoyushan_etT` as Toshiba / HiSmartTV A4 entries under the shared `xiaoyushan` codename. This corroborates the product-family mapping, but does not establish that the two suffix variants share the same board revision, system image, Ethernet framework, or STR implementation.

### 2. Android 9 firmware string is not a unique Toshiba image identifier

The public CleanYourTV inventory documents a Hisense India HiSmartTV A4 using:
- device: `xiaoyushan`
- product family string: `xiaoyushan-em`
- platform: MStar/MediaTek `m7322`
- Android TV 9
- firmware: `PTMR.190127.037`
- build: `01.00.00.04`
- security patch: `2020-05-05`

That source explicitly identifies the tested set as Hisense India. It is not evidence that Toshiba 65U7000VS / `xiaoyushan_etT` runs the same full fingerprint or vendor image. The same PTMR string also appears in unrelated device-family records, further reducing its value as a unique identifier.

### 3. Firmware acquisition search result

Searches for the exact model string `65U7000VS` combined with firmware, OTA, recovery, and update-package terms did not surface a verifiable Toshiba 65U7000VS firmware image, OTA payload, recovery package, or matching vendor source tree. Searches for `xiaoyushan_etT` in indexed public GitHub code likewise did not surface a matching framework/vendor source tree.

The public Toshiba support/download material found in this pass did not expose a matching 65U7000VS Android TV firmware package. Generic Toshiba TV software-upgrade manuals describe on-device/USB upgrade workflows but do not provide this model's package or source.

## What this changes

The device-family identification is better corroborated, but the decisive evidence is still missing:
1. the complete build fingerprint/build ID from the Toshiba 65U7000VS itself (not just the shared `PTMR.190127.037` string);
2. an OTA/recovery/system image demonstrably matching that complete fingerprint and `xiaoyushan_etT`;
3. the matching vendor framework/source manifest or extracted framework implementation, especially EthernetService, NetworkManagement, and STR resume integration.

Until those are matched, the AOSP Ethernet refCount change remains a candidate patch only. It must not be presented as a verified Toshiba fix. The exact vendor caller that initiates Ethernet teardown after STR resume remains unidentified.

## Safety and project boundaries

- Keep PR #47's Toshiba Android 9 + Ethernet VPN safety guard unchanged.
- Keep PR #48 as an investigation branch; do not merge.
- Do not alter the accepted CYTV R4 baseline or unrelated YouTube PRs.
- Do not add network diagnostic collection or telemetry.
- Do not attempt a firmware flash or recommend a non-matching Hisense image.
- No APK, firmware build, CI run, or real-device verification is claimed by this documentation update.

## Public references checked

- CleanYourTV Hisense HiSmartTV A4 Android 9 inventory: https://cleanyourtv.site/tv/hisense-hismarttv-a4/
- Toshiba Google Play compatibility catalog showing both `xiaoyushan_emT` and `xiaoyushan_etT`: https://playgist.com/en/tools/google-play-compatibility/toshiba/
- Toshiba Taiwan public downloads portal: https://www.sdd.toshiba.com.tw/chinese/support/download.aspx
- Generic Toshiba software-upgrade instructions (not a 65U7000VS firmware package): https://toshiba-tv.com/media/24w1753db_289D52661342.pdf
