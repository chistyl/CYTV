# Toshiba 65U7000VS STR wake investigation — R5

## Additional device-family evidence

The recorded firmware identity is `Toshiba/xiaoyushan_etT/xiaoyushan:9/...` on the M7322 platform. A public Android device-model mapping lists `xiaoyushan_etT` under Toshiba / `HiSmartTV A4`; this is useful as a family/codename clue, but it is not an OEM source revision and does not identify the exact Ethernet implementation.

Public owner reports for the 65U7000VS also describe occasional automatic wake at night, including reports that network/Wi-Fi wake settings did not prevent it. This is consistent with the device having a recurring standby/wake behavior, but it does not prove that STR resume is the cause of the Ethernet IP reset seen in this user's logs. It must not be used as root-cause evidence.

## Review of the unmerged app-level link-recovery experiment

The branch `fix/ethernet-link-drop-recovery-toshiba-65u7000vs` contains an experimental `ethernetWasUnderlying` preference and a `findEthernetNetwork()` retry path. This is not a fix for the observed physical interface stop:

- It can only select a Network object that Android currently publishes; it cannot raise `eth0`, restore a vendor link state, or undo a framework `IpClient` shutdown after the Ethernet service has cleared interface addresses.
- `ethernetWasUnderlying` is set from the currently selected Network's capabilities in `updateUnderlyingNetwork()`. If Wi-Fi is selected first while Ethernet is also present, the flag is false; the experiment therefore does not guarantee Ethernet-specific recovery in that case.
- Retrying DNS sockets or preferring an Ethernet NetworkAgent cannot repair a lower-layer link-down or a vendor STR service operation.

Do not cherry-pick this experimental recovery logic into the accepted baseline as a claimed Toshiba fix. Keep the R1 guard active for Toshiba/API 28/Ethernet.

## Current strongest causal boundary

The captured wake timeline proves that Android's Ethernet IP configuration is torn down shortly after STR resume: `Setting iface cfg` precedes `Clearing all IP addresses on eth0`, followed by Ethernet NetworkAgent DISCONNECTED and then DHCP recovery. The public Android Pie Ethernet source shows multiple ways to reach `IpClient.stop()`, including interface-link-down notification, NetworkAgent unwanted, IP config replacement, interface removal, and explicit Ethernet disable. The supplied logs do not distinguish these initiators because the relevant EthernetService/vendor log segment expired or is absent.

The next meaningful source-level target is the vendor integration that handles STR resume and calls into Ethernet service / NetworkManagement, not another VPN DNS retry. No source or reliable public symbol mapping currently identifies that vendor caller. No additional user capture is being requested; existing uploaded files remain the evidence set.

## Safety and release status

- Preserve the accepted R1 guard and its tested APK build.
- Keep PR #47 and PR #48 open/draft/unmerged.
- Do not enable VPN ad filtering on the Toshiba Android 9 Ethernet device.
- Do not label the refCount backport or app-level recovery experiment as the Toshiba STR root-cause fix.
- A genuine firmware-level repair requires the exact Toshiba vendor framework / STR source, a system image build, and regression testing on the target device. A launcher APK cannot directly patch `system_server` or the vendor Ethernet service.
