# R2 研究紀錄：VPN 建立、UID 路由與斷線清理順序

日期：2026-11-06  
分支：investigation/toshiba-vpn-ethernet-r2  
來源：Android 9.0.0_r45 AOSP 原始碼靜態檢視  
性質：研究紀錄；不是已確認的 Toshiba 故障根因，也不是 runtime 修正。

## 1. 本輪新增的原始碼觀察

### 1.1 VPN NetworkAgent CONNECTED 時

在 `ConnectivityService.updateNetworkInfo()` 的首次 CONNECTED 路徑中，Android 9.0.0_r45 會：

1. 將 VPN network 標記為 foreground。
2. 對 VPN 呼叫 `mNetd.createVirtualNetwork(netId, hasDns, allowBypass)`；其中 allowBypass 取決於 NetworkMisc。
3. 更新 LinkProperties，並啟動 NetworkMonitor。
4. 對 VPN 呼叫 `updateAllVpnsCapabilities()`。
5. 呼叫 `rematchNetworkAndRequests()`，把新 VPN network 納入 NetworkRequest 匹配。
6. 發送相關 network callbacks。

這證明 VPN CONNECTED 不只是 TUN 介面存在，也會觸發 ConnectivityService 與 netd 的網路管理路徑。

### 1.2 VPN NetworkAgent DISCONNECTED 時

同一個 `updateNetworkInfo()` 的 DISCONNECTED 路徑會：

1. 斷開 NetworkAgent async channel。
2. 恢復預設 proxy 狀態（若先前因 VPN 暫時停用）。
3. 對 VPN 呼叫 `updateUids(networkAgent, oldCapabilities, null)`。
4. 呼叫 `disconnectAndDestroyNetwork(networkAgent)`。

這顯示 VPN 結束時，系統會清理與該 VPN network 關聯的 UID 狀態並銷毀 network agent。若裝置廠商修改了這些流程或 netd 實作，可能產生與 AOSP 不同的行為；目前沒有 Toshiba log 可證實此點。

### 1.3 underlying networks 與 NetworkStats

Android 9 的 `Vpn.updateCapabilities(defaultNetwork)` 中，若 `mConfig.underlyingNetworks == null` 且有 default network，會以 default network 作為 underlying network。之後會從 underlying network 的 NetworkCapabilities 更新 VPN 的 transport、頻寬、metered、roaming、congested 等能力。

ConnectivityService 的 `getDefaultNetworks()` 則會回傳：
- 當前 default network；
- 所有已 everConnected 的 VPN networks。

這個集合用於通知 NetworkStatsService 更新 active interfaces / VPN 統計狀態。它不等於「所有 VPN 都成為 default network」，也不代表 Ethernet 實體 link 被停用。

### 1.4 Builder.establish() 與 agent 建立的順序

Android 9 `Vpn.establish()` 的主要順序為：
- 建立 TUN；
- 設定 TUN addresses；
- bind VPN service；
- 更新 VPN config；
- 嘗試 seamless handover；若無法原地更新，呼叫 `agentConnect()`；
- 之後才設定 TUN FD blocking。

因此，VPN agent 註冊和相關系統網路操作發生在 CYTV 收到成功建立的 TUN FD 之前。若 `establish()` 過程拋出 RuntimeException，framework 會關閉 TUN、呼叫 agentDisconnect，並恢復舊 config / UID 狀態。

## 2. 對 CYTV 問題的技術含意

目前 CYTV 的 DNS-only TUN 即使沒有 IPv4 default route，仍可能經過：
- netd virtual network 建立；
- VPN NetworkAgent CONNECTED；
- VPN capabilities 更新；
- NetworkRequest rematch；
- UID 相關網路狀態設定；
- NetworkStats active network / VPN interface 更新。

因此「只設 DNS /32 route」並不能排除系統層 VPN lifecycle 對網路管理造成影響。

但以上 AOSP 路徑本身沒有證明會把 Ethernet carrier 關閉。使用者實機看到的 Ethernet「網路線已拔除／未連線」仍需區分：
- 實體 link/carrier 真的掉線；
- Ethernet NetworkAgent 被移除或狀態轉為 disconnected；
- 廠商 UI 將邏輯網路狀態顯示為拔線；
- netd / UID routing 失常導致實際無法通網。

沒有 Toshiba logcat、Ethernet service log 與 kernel link state 時，不應將上述任一項定為根因。

## 3. 研究優先順序更新

下一輪應聚焦兩條可驗證的證據鏈：

### A. ConnectivityService / netd
- `createVirtualNetwork` 對應的 netd 實作與參數語意。
- VPN UID ranges 由 `NetworkCapabilities.getUids()` 如何傳遞到 `updateUids()`、netd 與 routing rules。
- `disconnectAndDestroyNetwork()` 如何清除 virtual network、UID ranges、routes 與 DNS。
- VPN rematch 是否可能造成 Ethernet NetworkAgent 被錯誤 teardown；必須找到具體呼叫鏈才可成立。

### B. Ethernet service / link state
- Ethernet NetworkAgent 何時收到 DISCONNECTED。
- Android 9 EthernetTracker / EthernetNetworkFactory 如何處理 interface state。
- 廠商 Ethernet service 是否會因 VPN virtual network 或 netd callback 重新設定 Ethernet interface。
- kernel netlink / carrier state 與 ConnectivityService log 的時間關係。

## 4. R1 安全邊界與測試限制

- Toshiba + Android 9 + Ethernet 必須保留 R1 guard；不得呼叫 VPN Builder.establish()。
- 不要求使用者在 Toshiba 電視重新開啟阻擋廣告。
- 使用者沒有備用 Android 9 Ethernet 測試設備；因此不產生 VPN 實驗 APK。
- 其他 Android 9 與 Android 10+ 不套用未驗證的 underlay / route 行為。
- 本輪只有 AOSP 靜態分析；沒有 runtime code 修改、APK 或 CI。
- 不合併 PR；PR #47 仍維持 Draft、open、未合併。

## 5. 本輪結論

- 已確認 AOSP VPN CONNECTED 會建立 netd virtual network、更新 VPN capabilities 並 rematch requests。
- 已確認 VPN DISCONNECTED 會更新 UID 狀態並銷毀 network agent。
- 已確認 underlyingNetworks null 會跟隨 default network 的語意，且 VPN networks 也會納入 NetworkStatsService 的 active network 集合。
- 尚未確認上述流程會導致 Toshiba Ethernet carrier 掉線。
- 下一個必要證據是 netd UID routing 清理呼叫鏈與 EthernetTracker / EthernetNetworkFactory 狀態變更來源。
