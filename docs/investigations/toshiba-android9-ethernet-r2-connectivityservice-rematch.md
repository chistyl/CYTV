# R2 研究紀錄：VPN NetworkAgent 註冊、NetworkRequest 重配與 UID 範圍

日期：2026-11-06  
分支：investigation/toshiba-vpn-ethernet-r2  
範圍：Android 9 VPN 建立流程與 ConnectivityService 靜態分析  
性質：研究紀錄；不是已確認的 Toshiba 故障根因，也不是 runtime 修正。

## 1. 本輪結論

研究焦點應從「VPN 是否建立 default route」擴展到「VPN NetworkAgent 註冊後，系統如何依 NetworkCapabilities、NetworkRequest 與 UID 範圍重新配置網路」。

即使 CYTV 的 TUN 只宣告 DNS /32 route，VPN 建立流程仍可能建立一個系統可見的 VPN NetworkAgent。該 agent 的註冊及 CONNECTED 狀態會進入 ConnectivityService 的網路匹配流程。故「沒有 0.0.0.0/0 route」不足以證明 VPN 建立不會影響系統網路選擇或 UID 路由。

目前證據支持這是值得優先追查的系統層路徑，但尚不足以證明它就是 Toshiba Ethernet 斷線的直接原因。

## 2. NetworkAgent 與能力資訊

AOSP VPN 建立流程的 agentConnect 路徑會建立 VPN NetworkAgent，並將 VPN 的 NetworkCapabilities、LinkProperties、VPN owner UID、VPN UID ranges 及 underlying networks 等資訊交給 ConnectivityService。

重要含意：
- VPN 不只是 TUN file descriptor；它同時是 ConnectivityService 管理的一個 Network。
- NetworkCapabilities 的 UID 範圍決定哪些 UID 由該 VPN network 提供服務。
- underlying network 是 VPN 向系統宣告的上游網路資訊；它與應用程式自行對 DNS socket 呼叫 Network.bindSocket() 是不同層次的機制。
- underlyingNetworks 為 null 時，Android framework 的 VPN 實作會依系統 default network 的語意處理；不能將 null 解讀成「不使用實體網路」。

參考：
- Android 9.0.0_r45 ConnectivityService 原始碼鏡像：
  https://github.com/lulululbj/android_9.0.0_r45/blob/master/frameworks/base/services/core/java/com/android/server/ConnectivityService.java
- AOSP Vpn.java（用於核對 agentConnect、UID ranges、underlying networks 等實作概念；引用時須注意不同提交版本可能與 Android 9 有差異）：
  https://android.googlesource.com/platform/frameworks/base/+/afe0d0e3c7b4/services/core/java/com/android/server/connectivity/Vpn.java

## 3. ConnectivityService 網路重配的意義

ConnectivityService 的 rematchNetworkAndRequests / rematchAllNetworksAndRequests 類流程，會把已註冊 Network 與現存 NetworkRequest 重新比對，並依網路能力及分數決定各 request 由哪個 network 滿足。

這帶來幾個需要驗證的可能影響：
1. VPN NetworkAgent CONNECTED 後，系統會把新 VPN network 納入網路請求匹配。
2. 對應 UID 的網路請求可能轉由 VPN network 滿足；實際結果取決於 VPN capabilities、UID ranges、score、request 與裝置廠商 framework 實作。
3. VPN 的出現或消失可能改變應用程式所見的 default/bound network 與路由規則。
4. VPN teardown 時，系統還需要移除 agent、重新匹配 requests，並清理該 network 的路由與 DNS 設定。

注意：不能只因 ConnectivityService 有 rematch 流程，就推斷實體 Ethernet link carrier 會被關閉。真正的 Ethernet「已拔除」狀態可能還涉及 vendor Ethernet service、netd、驅動程式或廠商客製化 framework。必須把 Android 邏輯網路選擇與實體 link 狀態分開調查。

參考：
- Android 9.0.0_r45 ConnectivityService 原始碼鏡像（完整檔案）：
  https://github.com/lulululbj/android_9.0.0_r45/blob/master/frameworks/base/services/core/java/com/android/server/ConnectivityService.java
- AOSP ConnectivityService rematch 流程參考片段：
  https://android.googlesource.com/platform/frameworks/base/%2B/8aefdb4881caefed5cb10c2fe6d7bb1281ef3c9e/services/core/java/com/android/server/ConnectivityService.java

## 4. 與 CYTV 現況的關聯

目前 CYTV AdsFilterVpnService：
- 使用 TUN address 198.18.0.1/32。
- DNS server 為 198.18.0.2。
- 僅將 198.18.0.2/32 加入 VPN route，並未配置 IPv4 default route。
- DNS UDP socket 使用 protect(socket)，並嘗試透過選定 Network.bindSocket(socket) 綁定實體上游。
- Builder 尚未明確設定 underlying networks。
- Toshiba + Android API 28 + Ethernet 的 R1 guard 會在 Builder.establish() 前中止 VPN 啟動，並自動停用阻擋廣告。

因此，R1 保護的關鍵仍是避免進入整個 VPN establish / agent registration 流程，而不只是避免安裝 default route。

## 5. 下一步應追查的精確問題

A. Android 9.0.0_r45 ConnectivityService：
- VPN NetworkAgent 註冊事件的處理入口。
- agent CONNECTED 後呼叫 rematchNetworkAndRequests 的實際位置。
- VPN UID ranges 如何由 NetworkCapabilities 傳遞到 netd / UID routing。
- VPN agent disconnect 時，UID rules、default network、routes、DNS 的清理順序。

B. Android 9.0.0_r45 Vpn.java / netd：
- establish() 中何時建立 TUN、設定 address/routes/DNS。
- agentConnect() 前後的 netd 設定與 agent 註冊順序。
- underlyingNetworks null 與明確指定 Ethernet 時，VPN capabilities 有何差異。
- VPN 的 route / DNS 設定是否可能與廠商 Ethernet 管理服務互相觸發。

C. Toshiba vendor layer：
- 實體 Ethernet carrier/link 狀態變成 disconnected 的直接來源。
- 是否存在 ConnectivityService 與 vendor Ethernet service 間的客製化 callback。
- logcat 中 EthernetTracker、EthernetNetworkFactory、ConnectivityService、Vpn、netd 的時間序列。

## 6. 安全與驗收限制

- 不在 Toshiba 電視上重新啟用阻擋廣告或測試 VPN。
- 不移除、不繞過 R1 Toshiba Android 9 Ethernet guard。
- 使用者沒有備用 Android 9 Ethernet 設備，因此本階段只做靜態研究。
- 不修改 runtime code、不產生 APK、不宣稱根因已確認。
- 未經使用者明確同意，不合併任何 PR。

## 7. 本輪狀態

- 新增研究方向：NetworkAgent registration → ConnectivityService rematch → UID/network routing。
- 根因狀態：未確認。
- 實體 Ethernet carrier 變成 disconnected 的原因：仍待 vendor 層證據。
- R1 安全保護：不變。
