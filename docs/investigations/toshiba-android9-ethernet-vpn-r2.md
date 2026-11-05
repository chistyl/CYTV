# Toshiba Android 9 Ethernet VPN Investigation — R2

## Accepted safety baseline

- Base: `safety/toshiba-android9-ethernet-disable-vpn-r1`
- Base commit: `002a6b4964ea2fb6b2639e41947b6e73690cc2fa`
- Device evidence: Manufacturer=Hisense, Brand=Toshiba, Android API=28, Ethernet=true.
- R1 safety branch was observed in the DNS log; VPN=false, Internet=true, and YouTube playback remained normal for five minutes.
- The R1 Toshiba/API 28/Ethernet guard must remain in every R2 experiment. Do not ask the owner to enable ad filtering on this Toshiba device.

## Code facts confirmed

1. The VPN builder configures a TUN address `198.18.0.1/32`, DNS server `198.18.0.2`, and a single route `198.18.0.2/32`. It does not intentionally add a default route such as `0.0.0.0/0`.
2. DNS forwarding sockets call `VpnService.protect()`; when possible, the router DNS socket is additionally bound to the selected physical Ethernet `Network`.
3. The service currently does not call `Builder.setUnderlyingNetworks()` before `establish()`, nor `VpnService.setUnderlyingNetworks()` after establishment.
4. The service has prior comments explicitly avoiding the post-establishment `setUnderlyingNetworks()` call on Android 9 TV firmware because it was suspected of disrupting Ethernet restoration. Do not reintroduce that call as an unguarded change.

## Working hypothesis (not yet proven)

The failure is associated with creation of the Android VPN/TUN network on this vendor Android 9 firmware, rather than an intentional full-tunnel route in CYTV. The fact that Ethernet and Internet recover when VPN is not established, while the safety guard prevents the failure, supports this correlation but does not identify the framework/vendor root cause.

## Candidate investigation

- Compare Android 9 framework behavior for a VPN whose upstream is an explicitly selected Ethernet `Network` versus the default network.
- Evaluate `Builder.setUnderlyingNetworks(new Network[]{selected})` as a narrowly scoped experiment. Android API documentation says this declares the network(s) carrying VPN upstream traffic; it is available from API 22. This is only a candidate, not an approved fix.
- Do not combine this with route, MTU, DNS, callback, or socket-binding changes in the same experiment. Keep each variable isolated.
- Before any device test, require an explicit opt-in diagnostic build and a recoverable test setup. Never remove or bypass the Toshiba safety guard for normal use.
- If a Toshiba-specific experiment would require bypassing the guard, do not run it on the owner's primary TV. Use a spare/test device or stop and report that the root cause cannot be safely reproduced.

## Required acceptance gates

- Unit tests and debug APK build pass.
- Toshiba safety guard remains intact and is covered by a regression check.
- No claim of root-cause repair based on CI alone.
- Real-device network stability must be verified before any proposed fix is accepted.
- PRs remain Draft and unmerged until explicit owner approval.

## Current status

Research only. No R2 code behavior has been changed yet.
