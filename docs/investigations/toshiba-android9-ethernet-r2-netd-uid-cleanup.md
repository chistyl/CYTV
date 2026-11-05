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


## 8. 第十三至十四階段：Ethernet Link State 與 setInterfaceDown 呼叫路徑

### 8.1 Link State 通知鏈

目前取得的 Android 9 相關來源顯示，下列路徑負責把底層介面 Link State 事件轉交給 Ethernet 網路管理層：

1. netd NetlinkHandler 收到 link up/down event，呼叫介面 Link State 通知。
2. NetworkManagementService 的 notifyInterfaceLinkStateChanged(iface, up) 透過 Observer 廣播 interfaceLinkStateChanged()。
3. EthernetTracker / EthernetNetworkFactory 的 InterfaceObserver 接收 callback。
4. EthernetNetworkFactory.updateInterfaceState() 更新 mLinkUp、NetworkInfo availability / DetailedState、IP provisioning 與 NetworkAgent。

這條鏈的角色是事件通知及狀態反映；收到 up=false 不等於 EthernetNetworkFactory 自己先將實體介面關閉。

### 8.2 NetworkManagementService.setInterfaceDown() 的實際效果

在 Android 9.0 原始碼鏡像 lulululbj/android_9.0.0_r45 的：

frameworks/base/services/core/java/com/android/server/NetworkManagementService.java

setInterfaceDown(iface) 的程式順序為：

1. enforce CONNECTIVITY_INTERNAL permission。
2. getInterfaceConfig(iface)。
3. ifcg.setInterfaceDown()，移除 UP flag 並加入 DOWN flag。
4. setInterfaceConfig(iface, ifcg)。
5. setInterfaceConfig() 透過 NativeDaemonConnector 對 netd 執行 interface setcfg，附帶介面位址、prefix 與 flags。

因此，若該 API 確實被呼叫到 Ethernet 實體介面，它不是單純 NetworkAgent 狀態更新，而是要求 netd 將該介面設為 DOWN。這是有別於 VPN UID range / netId routing cleanup 的操作。

### 8.3 已找到的 setInterfaceDown 使用點與限制

對該 Android 9.0.0_r45 鏡像進行 GitHub code search，目前找到的使用點包括：

- NetworkManagementService.setInterfaceDown() 本身。
- InterfaceConfiguration.setInterfaceDown() 的旗標操作。
- TetherInterfaceStateMachine 對 tethering interface 的狀態設定。

另在 AOSP frameworks/opt/net/ethernet 的 EthernetNetworkFactory 版本中，setInterfaceDown() 出現在「tracking interface unexpectedly changed」的防護分支；正常 link-state callback 則是更新 Ethernet NetworkAgent 狀態。該 EthernetNetworkFactory 搜尋來源是 AOSP Gitiles 的其他提交，尚未核實為 android-9.0.0_r45，故不能當作 r45 的精確呼叫點證據。

目前在可搜尋的 r45 鏡像中，尚未找到 VPN teardown 直接呼叫 NetworkManagementService.setInterfaceDown(EthernetIface) 的證據。此搜尋結果不等於對所有 vendor code、反射呼叫或動態命令的完整排除。

### 8.4 本階段結論

- 若 Ethernet 介面收到 kernel / driver Link Down event，Android 可透過 Observer 通知鏈將 Ethernet NetworkAgent 標示為 DISCONNECTED。
- 若 NetworkManagementService.setInterfaceDown(EthernetIface) 被呼叫，則會透過 netd interface setcfg 設定介面 DOWN。
- 目前未找到 VPN UID routing 移除、VPN netId teardown 到 Ethernet setInterfaceDown() 的直接呼叫鏈。
- 因此仍不能判定 Toshiba 的故障根因；需有 Toshiba 廠商 framework/netd、kernel log 或完整呼叫堆疊才能確認。

### 8.5 下一個調查點

1. 追查 netd 收到 interface setcfg 後的 InterfaceController / ioctl / rtnetlink 實作，確認 Android 9 netd 如何設定 IFF_UP。
2. 對照 Android 9 r45 的 NetworkManagementService event parser，區分 interface status changed 與 interface link state changed。
3. 確認 TetherInterfaceStateMachine 的 setInterfaceDown 僅作用於 tethering interface，避免把它誤認為 VPN cleanup。
4. 若無法取得 r45 netd source，明確保留版本限制，不以其他 tag 的程式碼推論 Toshiba r45 行為。

### 8.6 安全邊界

本階段僅靜態研究；未修改 runtime、未產生 APK、未執行 Toshiba 實機 VPN 測試。R1 guard 必須保留，PR #47 維持 Draft / open / 未合併。


## 9. 第十五階段：netd interface setcfg 到 Linux IFF_UP

### 9.1 已確認的 Android 9 netd 舊式命令路徑（r33）

在 AOSP netd tag android-9.0.0_r33 的 server/CommandListener.cpp，interface setcfg 解析 flags：
- 遇到 "up" 呼叫 ifc_up(iface)。
- 遇到 "down" 呼叫 ifc_down(iface)。
- 失敗時回報 OperationFailed；成功後回報 Interface configuration set。

這是 netd legacy CommandListener 路徑。此來源為 r33，不是 r45，因此僅能作 Android 9 同期實作參考。

### 9.2 Linux 介面旗標效果

在這條 legacy 路徑中，ifc_down() 是 netutils 的介面控制 helper，負責將介面管理旗標設為 down；其作用不是刪除某條 VPN route，而是把指定 interface 的 administrative UP 狀態關閉。這與 kernel/driver 回報的物理 carrier/link state 是不同概念：
- IFF_UP：介面 administrative state。
- IFF_RUNNING / carrier：反映 driver/介面運作或連線狀態，語意不同。
- Android Ethernet NetworkAgent 可能因 link-state callback 而 DISCONNECTED；不能只靠 UI 字樣推斷 IFF_UP 被清除。

本輪找到的 AOSP netd r33 CommandListener 直接顯示 ifc_down() 呼叫，但未取得與該 tag 完全配套的 netutils ifc_down() 實作行號，因此不把 ioctl 細節寫成 r45 已核實事實。

### 9.3 與 VPN UID routing / netId cleanup 的區隔

目前已確認的 Android 9 ConnectivityService VPN teardown 路徑，涉及 removeVpnUidRanges()、rematch network requests 及 removeNetwork(vpnNetId)。已查到的 netd NetworkController / RouteController 實作線索，屬於 UID routing、policy rules、network routing/DNS cleanup。

本輪尚未找到從這些 VPN cleanup 函式直接呼叫 interface setcfg、ifc_down() 或 InterfaceController 設定 Ethernet IFF_UP 的呼叫鏈。兩條路徑目前應視為不同子系統，除非後續找到 vendor hook 或明確 caller 才能建立因果關係。

### 9.4 版本限制與結論

- NetworkManagementService setInterfaceDown() 的 r45 鏡像證據：已確認它會組出 interface setcfg 命令，flags 含 down。
- netd CommandListener 的 ifc_down() 證據：目前取得的是 AOSP android-9.0.0_r33，不是 r45。
- r45 netd InterfaceController / CommandListener 精確來源：尚未成功取得。
- 因此不能宣稱已完成 r45 netd 層的逐行核對，也不能據此判定 Toshiba 廠商版本的實際行為。

階段性結論：若 Android Framework 對 Ethernet iface 呼叫 setInterfaceDown，netd 的對應 interface configuration 路徑確實可能將該介面的 administrative UP 狀態關閉；但目前沒有證據顯示 VPN UID range / VPN netId cleanup 會呼叫這條介面管理路徑。Toshiba 故障根因仍未確定。

### 9.5 下一步

1. 取得 android-9.0.0_r45 的 netd CommandListener / InterfaceController / netutils 精確原始碼，核實是否沿用 r33 legacy path 或已轉為 binder INetd path。
2. 追查 EthernetNetworkFactory 的 setInterfaceDown 呼叫點及其 caller，確認該分支觸發條件。
3. 追查 tethering state machine 的介面 down 操作是否限定於 tethering iface。
4. 若 r45 精確原始碼無法取得，保留版本差異，不以 r33 推斷 Toshiba 的 r45/vendor 行為。

本階段仍為靜態研究；沒有修改 runtime、沒有 APK/CI/實機測試。R1 guard 保留，PR #47 維持 Draft/open/未合併。
