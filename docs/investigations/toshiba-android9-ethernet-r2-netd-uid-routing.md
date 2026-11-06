# R2 研究紀錄：netd Virtual Network、UID 更新與 network teardown

日期：2026-11-06  
分支：investigation/toshiba-vpn-ethernet-r2  
來源：Android 9.0.0_r45 AOSP `ConnectivityService.java`、`Vpn.java` 靜態檢視  
性質：研究紀錄；不是已確認的 Toshiba 故障根因，也不是 runtime 修正。

## 1. 本輪確認的呼叫鏈

### 1.1 NetworkAgent 註冊

ConnectivityService 收到 NetworkAgent 註冊事件後：
- 將 NetworkAgentInfo 放入 agent / netId 對照表。
- 建立 async channel。
- 呼叫 `updateNetworkInfo()` 處理目前 NetworkInfo 狀態。
- 接著呼叫 `updateUids(nai, null, nai.networkCapabilities)`，讓系統更新此 network 的 UID 適用範圍。

這表示 UID 範圍更新不只發生在 VPN 關閉時；NetworkAgent 註冊時也會執行初始 UID 更新。

### 1.2 VPN Network 建立

VPN 進入 CONNECTING / CONNECTED 時，ConnectivityService 對 VPN 呼叫：
`mNetd.createVirtualNetwork(netId, hasDns, allowBypass)`

其中：
- `hasDns` 取決於 VPN LinkProperties 是否有 DNS server。
- `allowBypass` 取決於 NetworkMisc.allowBypass。
- VPN NetworkCapabilities 會加入 FOREGROUND capability。
- VPN 首次 CONNECTED 時會更新其他 VPN capabilities，再執行 `rematchNetworkAndRequests()`。

對 CYTV 而言，DNS-only TUN 仍會走進 virtual network 建立路徑；是否有 default route 並不會跳過這個 lifecycle。

### 1.3 VPN UID 範圍來源

Android 9 `Vpn.agentConnect()` 會將 `createUserAndRestrictedProfilesRanges(...)` 計算出的 UID ranges 放入 VPN NetworkCapabilities：
`mNetworkCapabilities.setUids(...)`

同時設定 establishing VPN app UID。ConnectivityService 在 NetworkAgent 註冊時把新舊 capabilities 傳給 `updateUids()`。因此 VPN UID 路由範圍由 framework VPN capabilities 傳遞到 ConnectivityService/netd 管理流程，而不是由 CYTV 自行建立每個 UID 的 routing rule。

### 1.4 VPN 斷線與 netd 清理順序

ConnectivityService 處理 VPN DISCONNECTED 時：
- 呼叫 `updateUids(networkAgent, oldCapabilities, null)`，撤銷此 network 的 UID 範圍。
- 呼叫 `disconnectAndDestroyNetwork()`。
- teardown 會先 rematch network requests，讓 fallback network 有機會成為 default / 被 request 使用。
- 若 network 已在 netd 建立（`nai.created`），再呼叫 `mNetd.removeNetwork(netId)`，清除該 network 的 routing rules、DNS 等設定，並呼叫 DNS manager 移除 network。

這個順序很重要：AOSP 註解明確指出 netd network 清理可能耗時，因此安排在 request rematch 之後，避免 fallback network 接手前造成不必要的流量中斷。

### 1.5 underlying network 與 UID routing 是不同層

`Vpn.setUnderlyingNetworks()` 更新 VPN config 的 underlying network 列表；ConnectivityService 之後更新 VPN capabilities。這主要描述 VPN 的上游網路能力資訊，不等於設定 VPN UID ranges。

`NetworkCapabilities.setUids()` 則定義 VPN network 適用的 UID 範圍。兩者不能混為一談，也不能假設呼叫 Builder.setUnderlyingNetworks() 就能修復 UID routing 或實體 Ethernet link。

## 2. 對 Toshiba 故障的意義

本輪將可疑系統路徑具體化為：

`Vpn.establish()` → VPN NetworkAgent 註冊 → `createVirtualNetwork()` → `updateUids()` → VPN CONNECTED / rematch → 後續 DISCONNECTED 時 `updateUids(old, null)` → rematch → `removeNetwork()`

這條鏈已由 AOSP 原始碼確認。

但它仍沒有證明 Ethernet 實體 carrier 被關閉。AOSP 的 `removeNetwork()` 是針對 VPN virtual network netId 的清理，不能直接解讀為刪除 Ethernet physical network。若 Toshiba 實機 Ethernet 真的掉 carrier，仍需要 vendor Ethernet service、netd vendor extension 或 kernel link-state log 才能定位。

## 3. Ethernet source 路徑限制

目前使用的 Android 9.0.0_r45 repository 鏡像可讀到 ConnectivityService 與 Vpn.java，但預期的下列 Ethernet source 路徑在此鏡像中回傳 404：
- `frameworks/base/services/core/java/com/android/server/ethernet/EthernetNetworkFactory.java`
- `frameworks/base/services/core/java/com/android/server/ethernet/EthernetTracker.java`

因此本輪不對 EthernetTracker / EthernetNetworkFactory 行為作推測，也不將缺少檔案誤判為 Android 9 沒有 Ethernet framework。下一步需先確認該鏡像的實際目錄布局，或改用對應 Android 9 AOSP tag 的官方原始碼。

## 4. 下一輪研究重點

1. 取得與 Android 9.0.0_r45 完全對應的 netd 原始碼，追查 `createVirtualNetwork`、`removeNetwork`、UID range 更新對應的 netd service / NetworkController / RouteController 實作。
2. 確認 `updateUids()` 的完整實作與呼叫 netd 的接口，辨別 UID routing policy 和實體介面狀態是否有直接耦合。
3. 找到正確 Android 9 Ethernet framework source 路徑，檢視 Ethernet interface link-state 事件如何轉成 NetworkAgent DISCONNECTED。
4. 將 AOSP 可證實的 framework 行為與 Toshiba 廠商客製行為分開標示。

## 5. 安全與驗收限制

- Toshiba + Android 9 + Ethernet 必須保留 R1 guard，不得呼叫 VPN Builder.establish()。
- 不要求使用者在 Toshiba 電視重新開啟阻擋廣告。
- 沒有備用 Android 9 Ethernet 測試設備，不產生 VPN 實驗 APK。
- 本輪只有靜態原始碼研究；沒有 runtime code 修改、APK 或 CI。
- PR #47 保持 Draft、open、未合併；未經使用者授權不合併。

## 6. 本輪結論

- 已確認 VPN NetworkAgent 註冊時 ConnectivityService 會更新 UID network 範圍。
- 已確認 VPN 建立時會呼叫 netd 建立 virtual network。
- 已確認 VPN teardown 時先更新 UID 範圍、rematch requests，再清理 netd network。
- 尚未找到 AOSP 證據顯示此流程會關閉 Ethernet physical link。
- 根因仍未確認；下一步應直接追查 netd 的 virtual network / UID routing 實作，並修正 Ethernet source 的來源定位。
