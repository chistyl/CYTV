# R2 研究紀錄：VPN UID routing 移除與 Network teardown 順序

日期：2026-11-06  
分支：investigation/toshiba-vpn-ethernet-r2  
來源：Android 9.0.0_r45 AOSP 靜態檢視  
性質：研究紀錄；不是已確認的 Toshiba 故障根因，也不是 runtime 修正。

## 1. 本輪追查範圍

檢視：
- frameworks/base/services/core/java/com/android/server/ConnectivityService.java
- frameworks/base/services/core/java/com/android/server/connectivity/Vpn.java

## 2. 已確認的 UID range 清理順序

Android 9.0.0_r45 的 ConnectivityService.updateUids() 會比較舊、新 NetworkCapabilities 的 UID ranges：
- 新增範圍：呼叫 mNetd.addVpnUidRanges(netId, ranges)。
- 移除範圍：呼叫 mNetd.removeVpnUidRanges(netId, ranges)。

VPN NetworkAgent 收到 DISCONNECTED 時，updateNetworkInfo() 先呼叫 updateUids(networkAgent, networkCapabilities, null)，因此先要求 netd 移除該 VPN network 的 UID ranges，之後才呼叫 disconnectAndDestroyNetwork()。

## 3. 已確認的 Network teardown 順序

disconnectAndDestroyNetwork() 的主要順序：
1. 將 NetworkInfo 狀態設為 DISCONNECTED（若仍 Connected）。
2. 通知 CALLBACK_LOST、停止 keepalive、通知 NetworkMonitor disconnected。
3. 從 ConnectivityService 的 NetworkAgent / netId 對照表移除該 network。
4. 清除該 network 已滿足的 NetworkRequest。
5. 呼叫 rematchAllNetworksAndRequests()，讓其他仍存在的 network 有機會接手 requests / default network。
6. 若 network.created，呼叫 mNetd.removeNetwork(netId)，清理該 netId 的 routing rules、DNS 等網路設定。
7. 呼叫 mDnsManager.removeNetwork(network)，最後釋放 netId。

這表示 UID ranges 移除與 removeNetwork(netId) 是兩個不同層次的清理步驟：前者移除 VPN UID 導流範圍；後者清理整個 netId 的網路設定。

## 4. 對 Ethernet 根因調查的意義

目前 AOSP 程式碼可證明：
- VPN disconnect 會先移除 VPN UID ranges。
- 隨後 ConnectivityService 會移除 VPN NetworkAgent、重新匹配 requests，再要求 netd 移除該 VPN netId 的網路設定。
- removeNetwork() 的註解範圍是該 netId 的 routing rules、DNS 等設定。

目前 AOSP 程式碼不能證明：
- removeVpnUidRanges() 或 removeNetwork(vpnNetId) 會呼叫 Ethernet driver 的 carrier-off。
- VPN netId 清理會刪除 Ethernet 實體介面。
- ConnectivityService rematch 本身會使 Ethernet carrier 變成 down。

因此不可把「VPN netId teardown」直接等同於「Ethernet 實體 Link Down」。要成立因果關係，仍需找到 netd/vendor framework 對實體 Ethernet interface 的具體操作，或取得 Toshiba 的 kernel/netd/ethernet log 時序。

## 5. 下一個精確調查點

下一輪應查 Android 9 netd 實作：
- NetdNativeService / NetworkController 對 addVpnUidRanges()、removeVpnUidRanges() 的實作。
- NetworkController::removeNetwork() 的實際清理範圍。
- RouteController 的 VPN UID rule 操作是否只作用於 VPN netId / fwmark / UID ranges。
- 是否存在從上述清理路徑呼叫 InterfaceController 或 netlink link-state 操作的直接呼叫鏈。

另需繼續對照 EthernetTracker / EthernetNetworkFactory 的 interface removal、link-state callback 與 NetworkAgent teardown，判斷 Ethernet NetworkAgent DISCONNECTED 是先於 VPN teardown 還是後於 VPN teardown。

## 6. 安全邊界

- 保留 Toshiba + Android 9 + Ethernet R1 guard；不得呼叫 VPN Builder.establish()。
- 不在 Toshiba 電視重新開啟阻擋廣告。
- 不產生 VPN 實驗 APK；使用者沒有備用 Android 9 Ethernet 測試設備。
- 不修改 runtime code，不改動已驗收功能。
- PR #47 維持 Draft、open、未合併。
- 本輪只有 AOSP 靜態分析，沒有 CI、APK 或實機測試。

## 7. 本輪結論

已確認 Android 9 VPN teardown 會依序移除 UID ranges、重配 NetworkRequests，再呼叫 netd.removeNetwork(vpnNetId) 清理該 VPN netId 的路由與 DNS。尚無證據顯示這些操作會關閉 Ethernet 實體介面或觸發 carrier-off。下一步應深入 netd 的實作，確認 VPN UID routing / network cleanup 的操作邊界。
