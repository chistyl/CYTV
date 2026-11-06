# Toshiba 65U7000VS STR wake investigation — R7 firmware identity and alias boundary

## New identity cross-check

Public Android device-name catalogs list both `xiaoyushan_etT` and `xiaoyushan_emT` under Toshiba, with device codename `xiaoyushan` and product family HiSmartTV A4. This supports treating the recorded `Toshiba/xiaoyushan_etT/xiaoyushan:9/...` fingerprint as a Toshiba-branded A4-family variant rather than assuming it is the India-market Hisense unit.

Public Android TV 9 references also associate the build string `PTMR.190127.037` with HiSmartTV A4. A device-detector fixture labels a HiSmartTV A4 / PTMR.190127.037 user agent as Toshiba, while community device inventories document a Hisense India A4 using the same firmware string and a specific build `01.00.00.04`.

## Important limits

- `xiaoyushan_etT` and `xiaoyushan_emT` are distinct product suffixes; the public catalog does not prove their system images or vendor patches are byte-identical.
- The shared `PTMR.190127.037` string does not uniquely identify the Toshiba system image. It may be a platform/firmware family label reused across branding variants.
- The Hisense India build number `01.00.00.04`, security patch date, package list, and source behavior must not be assigned to the Toshiba 65U7000VS without a matching full fingerprint.
- No public source found in this pass contains Toshiba `xiaoyushan_etT` vendor framework source, exact EthernetService implementation, or STR resume callback.

## Implication for Ethernet investigation

The target is now narrowed to the Toshiba HiSmartTV A4 / M7322 Android 9 product variant. The decisive missing identifier is the complete Toshiba build fingerprint/build ID and matching OEM image/source manifest. Until that is available, the AOSP refCount backport remains a candidate only, not a confirmed applicable patch.

Continue investigating exact Toshiba variant firmware and vendor service boundaries. Do not use the Hisense sibling image as a patch source.

## Safety and scope

- Preserve accepted Toshiba/API 28/Ethernet R1 VPN safety guard.
- Keep PR #47 and PR #48 open/draft/unmerged.
- No app-level DNS retry, telemetry, or new diagnostics.
- No change to accepted launcher/APK baselines.
- Any true repair needs matching Toshiba vendor framework/system image and target-device regression validation.
