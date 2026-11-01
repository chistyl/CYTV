# CYTV-R140 — Ethernet / DNS filtering recovery patch

## Scope
This patch is limited to the existing ad-filter VPN service and its settings note. It does not add SponsorBlock or change the launcher layout, saved allow/block rules, or DNS rule precedence.

## Changes
1. Ethernet is preferred over Wi-Fi when selecting the physical network.
2. Automatic DNS mode tries DHCP-provided resolvers, then Cloudflare (1.1.1.1), Google (8.8.8.8), and Quad9 (9.9.9.9). Manual mode remains pinned to its configured resolver.
3. DNS forwarding timeout is reduced to 1.2 seconds.
4. If upstream DNS fails three times within 30 seconds, filtering is automatically disabled and the VPN tunnel is closed (fail-open). A successful upstream response resets the counter.
5. On physical network loss or unexpected DNS tunnel worker exit, filtering is stopped to restore ordinary networking.

## Validation status
- ZIP integrity and static XML/source checks passed locally.
- APK compilation and Android TV Ethernet runtime tests have not yet been performed.
- Do not consider the disconnection issue resolved until the R3 APK passes sustained Ethernet testing on the target TV.
