# Toshiba Android 9 Ethernet stop-path analysis (R4)

## New source-level finding

The public Android Pie maintenance source at `digi-embedded/android_platform_frameworks_opt_net_ethernet` commit `9586f802aa802b11649af5021ddc7da1bd667c2d` exposes a second stop path that is independent of the `releaseNetworkFor()` refCount defect:

1. `EthernetTracker.InterfaceObserver.interfaceLinkStateChanged(iface, up)` posts `updateInterfaceState(iface, up)`.
2. `EthernetTracker.updateInterfaceState()` calls `EthernetNetworkFactory.updateInterfaceLinkState(iface, up)`.
3. `EthernetNetworkFactory.NetworkInterfaceState.updateLinkState(false)` calls `stop()`.
4. `stop()` shuts down `IpClient`, sends a DISCONNECTED `NetworkInfo` to the `NetworkAgent`, and clears link properties.

This matches the *type* of effects observed in the Toshiba wake snapshot (`IpClient CMD_STOP eth0`, IP clearing, then Ethernet NetworkAgent DISCONNECTED), but does not prove that Toshiba firmware emitted `interfaceLinkStateChanged(eth0, false)`: the captured log has no surviving observer callback because the EthernetService log window was truncated/expired.

## Other framework-triggered stop paths

The same source also has these independent stop triggers:

- `EthernetNetworkFactory.NetworkAgent.unwanted()` calls `stop()` for the current agent.
- `EthernetNetworkFactory.NetworkInterfaceState.setIpConfig()` calls `stop()` then `start()`.
- `EthernetTracker.removeInterface()` calls factory `removeInterface()`, which calls `stop()`.
- Digi's `EthernetServiceImpl.setEnabled(iface, false)` explicitly calls `mTracker.updateInterfaceState(iface, false)`, then clears interface addresses and sets the interface down.
- `updateLinkState(false)` calls `stop()` even when `mLinkUp` was already false.

The observed ordering in the Toshiba log (`Setting iface cfg` at 02:42:08.564, `Clearing all IP addresses on eth0` at 02:42:08.637, then Ethernet DISCONNECTED at 02:42:08.679) is consistent with an interface-down / Ethernet-stop sequence. It is not sufficient to identify which vendor component initiated it. USB/OTG resume messages are temporally adjacent but are not established as the cause.

## Relationship to the existing refCount patch

The existing Android 9 patch corrects the independent `releaseNetworkFor()` condition from `--network.refCount == 1` to `== 0`. It prevents one class of premature Ethernet stop when multiple requests share an interface. It does not prevent a genuine link-down callback, explicit Ethernet disable/reset, interface removal, or `NetworkAgent.unwanted()` from stopping IpClient.

Therefore this patch must not be represented as a confirmed fix for the Toshiba STR/wake disconnect. The Toshiba OEM `system_server` / Ethernet framework source and the missing observer/vendor event evidence are still unavailable. Keep PR #48 Draft/open/unmerged and retain PR #47's R1 runtime safety guard. Do not generate a bypass APK or ask the user to re-enable VPN ad filtering on the Toshiba Android 9 Ethernet device.

## Next source-level checks

1. Compare Toshiba OEM `EthernetTracker`, `EthernetNetworkFactory`, `EthernetServiceImpl`, and vendor STR/power integration against this Android Pie source when an OEM image/source becomes available.
2. In any future full system log, preserve `EthernetTracker`, `EthernetNetworkFactory`, `EthernetServiceImpl`, `NetworkManagement`, `CommandListener`, and `IpClient` lines around STR resume. The existing uploaded files are the complete evidence currently available; no additional capture is being requested here.
3. Treat the refCount patch as a validated upstream-derived candidate only, not a firmware-level repair.
