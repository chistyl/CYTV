# Toshiba 65U7000VS STR wake investigation — R6 platform cross-check

## New public platform reference

A public, community-maintained Android TV device inventory identifies an Android TV 9 HiSmartTV A4 variant using device codename `xiaoyushan` and MStar/MediaTek M7322. Its Android 9 example records firmware `PTMR.190127.037`, build `01.00.00.04`, security patch 2020-05-05, and a 32-bit userspace. The same inventory separately lists an Android 11 A4 variant on the same codename/platform.

The model-name mapping also lists `xiaoyushan_etT` as Toshiba / HiSmartTV A4. Together, these sources strengthen the device-family and SoC match for the recorded Toshiba fingerprint.

## Critical variant boundary

The public Android 9 example is an India-market HiSmartTV A4 firmware. Its firmware/build identifiers are not the recorded Toshiba 65U7000VS identifiers. It is therefore a sibling-platform reference only, not proof that the Toshiba OEM image contains the same EthernetService, NetworkManagement, STR service, kernel driver, or vendor patches.

No public source located in this pass exposes the Toshiba `xiaoyushan_etT` Android 9 vendor framework source or the STR resume caller. Do not transfer the sibling firmware's behavior or source assumptions to the Toshiba device without an exact build match.

## Consequence for the refCount candidate

The AOSP `EthernetNetworkFactory.releaseNetworkFor()` refCount candidate remains unverified for the target firmware. A matching M7322 board and Android 9 release are insufficient: the OEM framework source must be checked for the exact method implementation and surrounding vendor changes before treating the patch as applicable.

## Next source target

Prioritize obtaining or locating the exact Toshiba `xiaoyushan_etT` firmware package / build manifest and its vendor framework integration. Inspect, in order:

1. Ethernet service implementation and its `IpClient` lifecycle.
2. STR suspend/resume service or vendor callback path.
3. NetworkManagement / interface configuration calls around resume.
4. Kernel Ethernet driver link-state handling and DHCP restart interaction.

The existing captured timeline still establishes Ethernet IP configuration teardown after resume, but does not identify which of these callers initiated it. No new user capture is requested.

## Safety and scope

- Keep Toshiba/API 28/Ethernet R1 VPN safety guard active.
- Keep PR #47 and PR #48 open/draft/unmerged.
- Do not change accepted launcher/APK baselines.
- Do not add telemetry or diagnostic collection.
- Do not describe the sibling A4 firmware or refCount candidate as a confirmed Toshiba fix.
- A system-level repair still requires exact OEM source or a matching system image build and target-device regression testing.
