# R2 — Android 9 VPN underlay API findings

## Confirmed API facts

- `VpnService.Builder.setUnderlyingNetworks(Network[])` was added in API 22, so it exists on Android 9 / API 28.
- It declares the network(s) used by the VPN for upstream connections.
- `VpnService.setUnderlyingNetworks(Network[])` is a different API and only succeeds after the VPN is established.
- The current service binds its protected DNS UDP socket to the selected physical Ethernet Network, but leaves the VPN Builder underlay unspecified.
- Current code comments avoid calling the post-establishment service method on Android 9 TV firmware because of observed Ethernet recovery problems.

## Important correction to the investigation plan

The candidate experiment is specifically `Builder.setUnderlyingNetworks(new Network[]{selected})` before `establish()`, not the post-establishment `VpnService.setUnderlyingNetworks()` call. These are distinct APIs with different timing/semantics. Do not conflate them.

## Hypothesis and risk

Declaring the selected Ethernet Network in the Builder may make the VPN's upstream topology explicit to Android. However, the API documentation does not promise that this changes the vendor's routing behavior or prevents Ethernet loss. It could also alter how the Android 9 vendor framework configures the VPN network. Treat as an unproven, potentially risky experiment.

## Safe next experiment design

1. Keep the Toshiba/Android 9/Ethernet safety guard intact in normal builds.
2. Do not enable this experiment on the owner's Toshiba TV.
3. If a spare Android 9 Ethernet test device is available, make a diagnostic-only build with one isolated change: call Builder.setUnderlyingNetworks(new Network[]{selected}) immediately before establish(), only when the explicit experiment flag is enabled.
4. Add before/after diagnostics for selected Network, its transport capabilities, LinkProperties DNS list, ActiveNetwork, and Internet capability.
5. If no spare device exists, stop before producing a build that could tempt the owner to test on the protected Toshiba TV. Continue static AOSP/vendor-source analysis instead.
6. Do not reintroduce the post-establishment `VpnService.setUnderlyingNetworks()` call.

## Current status

Research documentation only. No runtime code change, no APK, and no root-cause claim.
