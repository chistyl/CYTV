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


## Android 9 AOSP VPN 建立流程追查（2026-11-06）

### 來源與範圍
- 對照 Android 9.0.0_r45 AOSP framework：
  - `frameworks/base/services/core/java/com/android/server/connectivity/Vpn.java`
  - `frameworks/base/services/core/java/com/android/server/ConnectivityService.java`
- 這是 AOSP 參考實作，不代表 Toshiba 廠商韌體完全相同。

### 已確認的建立順序
Android 9 AOSP `Vpn.establish(VpnConfig)` 依序執行：
1. `jniCreate(config.mtu)` 建立 TUN fd。
2. `jniGetName()` 取得 TUN 介面名稱。
3. `jniSetAddresses()` 設定 TUN 位址。
4. 綁定 VPN Service。
5. 寫入 `mConfig`。
6. 呼叫 `agentConnect()` 建立 VPN NetworkAgent，並發布 CONNECTED 狀態。
7. `agentConnect()` 透過 `makeLinkProperties()` 建立 VPN LinkProperties，並依 routes / DNS / address 判斷 INTERNET capability。

因此，應用程式呼叫 `Builder.establish()` 不只是建立 TUN fd；framework 還會建立並發布 VPN NetworkAgent，讓 ConnectivityService 重新評估網路與 UID 路由。這是 Toshiba 故障最值得追查的系統交界，但目前沒有證據指出其中哪一步是根因。

### Underlying network 在 Android 9 的語意
- `VpnConfig.underlyingNetworks == null` 代表跟隨系統 default network。
- `Vpn.updateCapabilities(defaultNetwork)` 在 underlyingNetworks 為 null 時，會以當下 default network 作為 VPN underlying network capability 資訊。
- `Vpn.setUnderlyingNetworks(Network[])` 更新 `mConfig.underlyingNetworks`；AOSP 註解指出它本身不更新 capabilities，需由 ConnectivityService thread 再呼叫 `updateCapabilities()`。
- 因此 `Builder.setUnderlyingNetworks(new Network[]{selected})` 不是「綁定 DNS socket」的同義詞，而是影響 VPN 向系統宣告的 upstream/underlying network 資訊，不能視為已知安全修復。

### 對 CYTV 現況的具體意義
- CYTV 的 TUN 配置 `198.18.0.1/32`，並只將 `198.18.0.2/32` 路由進 VPN，沒有配置 IPv4 default route。
- AOSP `Vpn.makeLinkProperties()` 對未允許的 address family 會加入 unreachable default route；CYTV 已透過 IPv4 address / route / DNS 開啟 IPv4 family，因此不能由此推論它建立了全域 IPv4 default route。
- 即使只有 DNS /32 路由，VPN NetworkAgent 仍會註冊，並觸發 ConnectivityService 網路匹配與 UID routing 更新；所以「沒有 0.0.0.0/0」不能排除 VPN 建立流程影響實體網路狀態。
- R1 guard 在 Toshiba + Android 9 + Ethernet 時於 `Builder.establish()` 前停止流程，與實機觀察到的安全結果一致。

### 下一輪研究方向（仍不改 runtime code）
1. 追查 Android 9 `ConnectivityService` 收到 VPN NetworkAgent CONNECTED 後，如何 rematch default network、NetworkRequest 與 UID ranges。
2. 追查 `Vpn.java` 對 VPN routes / DNS 的 netd 套用順序，以及 underlying network capability 更新時機。
3. 比較 AOSP Android 9 與較新 Android 的差異，避免把新版 framework 行為套用到 API 28。
4. 只有取得可安全測試的非 Toshiba Android 9 Ethernet 設備後，才考慮 R2 runtime 實驗；保留 R1 Toshiba guard，不提供可讓 Toshiba 再次啟動 VPN 的測試 APK。

### 本輪結論
目前調查重點由「DNS socket 綁定是否正確」提升為「VPN NetworkAgent 註冊後 ConnectivityService 的網路重選與 UID 路由更新」。這是優先研究方向，不是已確認的故障根因。沒有修改 runtime code、沒有建立 APK、沒有改動 R1 安全保護。
