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


## 10. 第十六階段：EthernetNetworkFactory.setInterfaceDown() 的觸發條件

### 10.1 找到的呼叫情境

在 AOSP frameworks/opt/net/ethernet 的 EthernetNetworkFactory.java（可取得的 oreo-dev / 相關 Android 版本提交，非已確認 android-9.0.0_r45）中，setInterfaceDown() 出現在 setInterfaceUp(iface) 的異常競態分支：

1. maybeTrackInterface(iface) 發現符合 config_ethernet_iface_regex 的介面，且目前尚未追蹤介面。
2. 呼叫 setInterfaceUp(iface)，先要求 NetworkManagementService 將該新介面設為 UP。
3. 讀取介面設定與 hardware address。
4. 再次檢查 isTrackingInterface()。
5. 若此時已經有另一個介面被追蹤，記錄「Interface unexpectedly changed from ... to ...」，並對剛才傳入的 iface 呼叫 setInterfaceDown(iface)。

因此該 setInterfaceDown() 是處理「兩個介面競爭成為被追蹤介面」的防護：將多出來、未被採用的介面設回 DOWN。它不是 interfaceLinkStateChanged(up=false) 的處理函式，也不是 VPN disconnect 的正常清理步驟。

### 10.2 對 Toshiba Ethernet 故障的意義

- 正常 link-state callback 只更新 mLinkUp / NetworkInfo / IP provisioning / NetworkAgent。
- setInterfaceDown() 的已知 EthernetNetworkFactory 呼叫情境，發生在介面加入及追蹤競態，而非 VPN UID range 清除或 VPN netId teardown。
- 若 Toshiba log 出現「Interface unexpectedly changed ...」或相鄰的 setcfg down 訊息，才值得沿著這條支線調查。
- 若沒有介面新增事件或追蹤競態，這個 AOSP 呼叫點本身不足以解釋 VPN 啟停後 Ethernet 斷線。

### 10.3 精確版本限制

本次在可用的 android-9.0.0_r45 GitHub 鏡像中，未能找到 frameworks/opt/net/ethernet 的 EthernetNetworkFactory.java；官方 Gitiles 對 android-9.0.0_r45 指定檔案路徑也無法由目前檢索工具讀取。因此本節觸發條件是 Android EthernetNetworkFactory 的 AOSP 實作線索，不能標成 Toshiba r45 精確證據。

### 10.4 階段性結論

目前已知 EthernetNetworkFactory 內部 setInterfaceDown() 的 AOSP 用途，是處理介面追蹤競態時關閉未被採用的額外介面。沒有找到 VPN teardown 會製造此競態、或直接呼叫該分支的證據。這進一步降低了「AOSP 標準 VPN UID/netId 清理直接關閉 Ethernet」的可能性，但仍無法排除 Toshiba vendor framework、netd、Ethernet HAL 或 driver 的客製行為。

### 10.5 下一步

1. 尋找 Toshiba Android 9 vendor framework / Ethernet service log 中的 interface add/remove、tracking race、setcfg down 訊息。
2. 追查 Ethernet interface 被移除與重新加入時，EthernetNetworkFactory / EthernetTracker 如何選擇追蹤介面。
3. 追查 Android 9 r45 tethering state machine setInterfaceDown() 的實際 iface 來源，避免與實體 Ethernet WAN 介面混淆。
4. 繼續搜尋 r45 netd 與 framework 精確來源；不可用其他 Android tag 代替版本證據。

本階段仍為靜態研究，未修改 runtime、未產生 APK、未執行 Toshiba 實機 VPN 測試。R1 guard 保留，PR #47 維持 Draft/open/未合併。


## 11. 第十七階段：TetherInterfaceStateMachine 的 setInterfaceDown 目標介面

### 11.1 精確來源與介面身份

在 android_9.0.0_r45 鏡像的 frameworks/base/services/core/java/com/android/server/connectivity/tethering/TetherInterfaceStateMachine.java 確認：
- mIfaceName 是建構子傳入的 tethering interface 名稱。
- mInterfaceType 是該介面的 TETHERING_USB / TETHERING_WIFI / 其他類型。
- configureIPv4(enabled) 只有 USB 與 Wi-Fi 類型會配置 IPv4 位址與 prefix。
- 其他類型（程式註解指出 Bluetooth 由其他地方處理）直接 return true，不執行這裡的 interface config down/up。
- USB 類型在 enabled=true 時 setInterfaceUp()，enabled=false 時 setInterfaceDown()。
- Wi-Fi 類型註解明確指出由 Wi-Fi stack 管理介面 up/down，因此使用 ignoreInterfaceUpDownStatus()。
- BaseServingState.exit() 呼叫 stopIPv4()，而 stopIPv4() 會 configureIPv4(false)，再清除 IPv4 address。

### 11.2 呼叫時機

TetherInterfaceStateMachine 的 BaseServingState 在退出分享狀態時會停止 IPv6 / Router Advertisement、呼叫 untetherInterface(mIfaceName)，再呼叫 stopIPv4()。USB tethering 的 configureIPv4(false) 會將其 mIfaceName 設為 DOWN；Wi-Fi 忽略 up/down 狀態；其他類型不在此函式執行 setInterfaceDown。

### 11.3 對 Ethernet WAN 的意義

此處 setInterfaceDown() 的目標不是 VPN netId，也不是上游 upstreamIface；而是 TetherInterfaceStateMachine 自己管理的下游分享介面 mIfaceName。上游介面僅在 NAT / forwarding cleanup 中被使用。

在這份 r45 鏡像的 configureIPv4() 中，沒有針對 TETHERING_ETHERNET 類型執行 setInterfaceDown() 的分支。這降低了「標準 TetherInterfaceStateMachine 在停止分享時直接關閉 Ethernet WAN 上游介面」的可能性。

但這不代表所有 OEM tethering / Ethernet service 實作都相同，也不能排除 Toshiba 客製化狀態機或其他服務把 Ethernet interface 當作 downstream tethering interface 的情況。

### 11.4 階段結論

在目前檢視的 Android 9.0.0_r45 TetherInterfaceStateMachine 中，setInterfaceDown 只會在 USB tethering 的 IPv4 stop/configure false 路徑中由此函式觸發。Wi-Fi 的 up/down 由 Wi-Fi stack 管理；Bluetooth / 其他類型不在這個 configureIPv4() 分支中執行 setInterfaceDown。這裡的 iface 是 tethering downstream interface，不是 upstream Ethernet iface。沒有找到 VPN teardown 到此 stopIPv4() 的呼叫鏈。

### 11.5 下一步

1. 追查 Tethering master state machine 如何建立 TetherInterfaceStateMachine，並將 interfaceType 與 ifaceName 指派給 USB / Wi-Fi / Ethernet。
2. 確認 TETHERING_ETHERNET 是否存在於此 Android 9 branch，以及它是否使用獨立 Ethernet tethering 流程。
3. 比對 stopInterfaceForwarding / disableNat 對 upstream interface 的操作，確認它們只清除 forwarding / NAT，而非 set upstream interface down。
4. 保留 Toshiba vendor customization 未排除的限制。

本階段為 r45 鏡像靜態分析；沒有修改 runtime、沒有 APK/CI/實機測試。R1 guard 保留，PR #47 維持 Draft/open/未合併。


## 12. 第十八階段：Tethering 介面辨識與 upstream NAT 清理

### 12.1 Android 9 r45 的 tetherable interface 範圍

在 frameworks/base/services/core/java/com/android/server/connectivity/Tethering.java：
- ifaceNameToType() 依序檢查 TetheringConfiguration.isWifi(iface)、isUsb(iface)、isBluetooth(iface)。
- 其餘介面回傳 TETHERING_INVALID。
- maybeTrackNewInterfaceLocked(iface) 對 TETHERING_INVALID 直接記錄 not a tetherable iface 並 return，不會建立 TetherInterfaceStateMachine。
- interfaceStatusChanged() 對一般未知介面不會建立 TISM；只有 Wi-Fi、USB、Bluetooth 名稱符合設定才會辨識。
- 本分支的 untetherAll() 也只停止 Wi-Fi、USB、Bluetooth 三類。

因此在此 AOSP r45 實作中，標準 Tethering 不會把一般 Ethernet WAN 介面自動納入 downstream TetherInterfaceStateMachine。若 Toshiba 有 Ethernet tethering，必須是另外的 OEM 擴充或其他服務路徑，不能由這份標準程式碼推定。

### 12.2 Upstream NAT / forwarding 清理操作

在同一 r45 鏡像的 NetworkManagementService.java：
- startInterfaceForwarding(fromIface, toIface) 透過 NativeDaemonConnector 執行 ipfwd add。
- stopInterfaceForwarding(fromIface, toIface) 執行 ipfwd remove。
- enableNat(internalInterface, externalInterface) 透過 netd nat enable，並附帶 internal interface 的非 link-local prefix 資訊。
- disableNat(internalInterface, externalInterface) 執行 netd nat disable。

TetherInterfaceStateMachine.cleanupUpstreamInterface(upstreamIface) 使用 stopInterfaceForwarding(mIfaceName, upstreamIface) 與 disableNat(mIfaceName, upstreamIface)。這兩者針對 forwarding / NAT 規則，不是 setInterfaceDown(upstreamIface)，也沒有在 NetworkManagementService 這些方法中呼叫 setInterfaceDown。

所以標準 Tethering 清除 upstream NAT / forwarding，不等於關閉上游 Ethernet 介面。若要證明 Ethernet 被 administrative down，仍需找到對該 Ethernet iface 的 setInterfaceDown / interface setcfg down 或 vendor netlink 操作。

### 12.3 本階段結論

- AOSP Android 9 r45 標準 Tethering 只識別 Wi-Fi、USB、Bluetooth downstream interfaces。
- 一般 Ethernet WAN 不符合 ifaceNameToType()，不會進入此標準 TISM 建立流程。
- 停止 Tethering 時，upstream cleanup 只移除 forwarding 與 NAT 設定；沒有在此路徑關閉 upstream interface 的操作。
- 因此目前標準 AOSP Tethering 路徑也沒有解釋 VPN 停啟造成 Ethernet 介面 DOWN 的直接證據。
- Toshiba vendor Ethernet tethering / framework customization 仍未排除。

### 12.4 下一步

1. 追查 TetheringConfiguration 的 USB/Wi-Fi/Bluetooth iface pattern 來源，確認 OEM overlay 是否可能將 Ethernet 名稱納入既有類型。
2. 搜尋 Toshiba/vendor Ethernet service、Ethernet HAL 或 init rc 是否有 link down / setcfg down / interface down 的獨立管理路徑。
3. 將 VPN lifecycle、netd network teardown、Ethernet link event、Tethering downstream cleanup 的目前證據整合成因果矩陣，標示已證實、未證實與需 Toshiba log 的項目。

本階段為 android_9.0.0_r45 鏡像靜態分析；未修改 runtime、未產生 APK、未執行 Toshiba 實機 VPN 測試。R1 guard 保留，PR #47 維持 Draft/open/未合併。


## 13. 第十九階段：TetheringConfiguration regex 與 OEM overlay 邊界

### 13.1 r45 鏡像中已核實的分類來源

在 Android 9.0.0_r45 鏡像的 `TetheringConfiguration.java`：

- 建構時分別讀取 `config_tether_usb_regexs`、`config_tether_wifi_regexs`、`config_tether_bluetooth_regexs` 三組資源陣列。
- `isUsb(iface)`、`isWifi(iface)`、`isBluetooth(iface)` 分別以對應 regex 陣列比對介面名稱。
- 沒有獨立的 `config_tether_ethernet_regexs` 欄位或 `isEthernet()` 分類函式。

因此 OEM overlay 理論上可以改變既有 USB/Wi-Fi/Bluetooth regex 的匹配範圍；若把 Ethernet 命名介面塞進其中一組 regex，它會被錯誤/特殊地分類成該既有類型，而不是新增 Ethernet tethering type。後續實際行為仍由該 type 的狀態機分支決定。這是資源配置可能性，不代表 Toshiba 一定如此設定。

### 13.2 ConnectivityManager 類型 API

在同一鏡像的 `ConnectivityManager.java`，公開 tetherable regex getter 僅見 USB、Wi-Fi、Bluetooth 三組：
- `getTetherableUsbRegexs()`
- `getTetherableWifiRegexs()`
- `getTetherableBluetoothRegexs()`

本次搜尋未找到 `TETHERING_ETHERNET` 的命中。由於 GitHub code search 對此鏡像的索引與完整性有限，這只能記為「目前取得檔案與搜尋範圍未見」，不能當成整個 Toshiba firmware 不存在此常數的完整證明。

### 13.3 本階段限制

取得的 TetheringConfiguration 與 ConnectivityManager 是名為 android_9.0.0_r45 的 GitHub 鏡像內容；未能核實該鏡像與 Google Gitiles 官方 tag 的每個檔案完全一致。OEM overlay 資源檔及 Toshiba vendor framework/HAL/init scripts 未取得，因此無法確認 Toshiba 是否自訂 regex 或另有 Ethernet 管理服務。

## 14. 故障因果矩陣（截至第十九階段）

| 路徑 | 靜態證據狀態 | 能否解釋 Ethernet 實體介面 DOWN |
|---|---|---|
| VPN UID range remove | Android 9 ConnectivityService teardown 順序已確認 | 未找到直接操作實體介面的證據 |
| VPN netId remove | 會清理該 VPN netId 的 routing / DNS 等網路設定 | 未找到直接關閉 Ethernet 的證據 |
| netd route / policy cleanup | 與 VPN UID routing / netId 清理屬不同層次 | 未找到到 interface setcfg down 的直接呼叫鏈 |
| LinkState notification | 是 kernel/netlink link event 的通知及狀態傳遞 | 本身不是關閉介面的命令；可能反映已發生的 link down |
| Ethernet NetworkAgent DISCONNECTED | EthernetTracker/NetworkFactory 會反映 link/provisioning 狀態 | 單憑 NetworkAgent 狀態不能證明 carrier 或 IFF_UP 被關閉 |
| setInterfaceDown / interface setcfg down | Framework API 會將介面 flags 設 DOWN；r33 netd 參考路徑可到 ifc_down | 若目標真是 Ethernet iface，可能造成 administrative DOWN；尚無 Toshiba caller 證據 |
| Tethering downstream cleanup | r45 標準 Tethering 分類 USB/Wi-Fi/Bluetooth；USB downstream stop 可設 DOWN | 標準 upstream NAT/forwarding cleanup 不會 set upstream DOWN |
| OEM overlay regex | 三組 regex 可由資源讀取；OEM 可能改變匹配範圍 | 未取得 Toshiba overlay，不能排除特殊分類 |
| Toshiba vendor service / HAL / init | 未取得 Toshiba vendor source | 未知，需 Toshiba 專屬 log/source |

## 15. 對實際修正的決策

目前靜態證據沒有指向一個可安全修改的 runtime 根因。直接改 VPN route、DNS、netd teardown 或 Ethernet 狀態監控，可能掩蓋症狀、破壞已驗收 R1 guard，甚至造成使用者再次失去有線網路。因此本分支不提交猜測性的 runtime patch。

已知安全措施維持不變：
- Toshiba + Android 9 + Ethernet 在 VPN Builder.establish() 前阻止 VPN 啟動。
- 不在 Toshiba TV 重新啟用廣告阻擋測試。
- 不產生 VPN 實驗 APK。
- 本分支只提交研究文件；不修改 runtime。
- PR #47 維持 Draft、open、未合併。

要完成根因修正，下一個必要證據是 Toshiba 裝置在「VPN 未啟動」狀態下的 Ethernet 基線，以及既有故障紀錄中的 kernel link/carrier、netd interface config、Ethernet service、ConnectivityService 時序。不得為收集紀錄而重新觸發已知會斷線的 VPN 情境。


## 16. 第二十階段：依補充截圖追查 Android Ethernet framework

### 16.1 正確原始碼位置

依補充截圖指定的方向，Ethernet framework 應從以下目錄追查，而不是只停留在 ConnectivityService / netd：

`frameworks/opt/net/ethernet/java/com/android/server/ethernet/`

重點檔案：
- `EthernetTracker.java`：介面發現、interface observer、link state 通知與介面追蹤。
- `EthernetNetworkFactory.java`：Ethernet NetworkAgent、IP provisioning、link down 後的網路撤銷。
- `EthernetServiceImpl.java`：Ethernet service API 與管理入口。

### 16.2 可取得的 AOSP Android 9 參考實作（版本限制）

目前可直接核對的 Google Gitiles 官方來源為 `android-9.0.0_r1`，不是本研究目標的 r45；因此以下只作 Android 9 架構參考，不宣稱與 r45/Toshiba firmware 完全一致。

在該版 `EthernetTracker.java`：
- `addInterface()` 呼叫 `mNMService.setInterfaceUp(iface)`，目的是啟用介面並取得 link status。
- `InterfaceObserver.interfaceLinkStateChanged()` 將收到的 link state 事件交由 handler 更新。
- `updateInterfaceState()` 只把狀態交給 Factory，並通知 Ethernet listener。
- `interfaceRemoved()` 會走 `removeInterface()`，停止追蹤該介面。

在可取得的 Android EthernetNetworkFactory 參考實作：
- link state 變成 down 時，NetworkInfo 設成 DISCONNECTED、停止 IP provisioning、更新 NetworkAgent 與 score。
- 該 link-down 分支本身沒有呼叫 `setInterfaceDown()`；它是在反映收到的介面狀態。
- 另一條「已經追蹤一個介面，卻遇到不同介面」的保護分支會對新遇到的介面呼叫 `mNMService.setInterfaceDown(iface)`。這表示必須確認 Toshiba 裝置是否有多個匹配 Ethernet regex 的介面，以及實際被設 DOWN 的 iface 名稱；目前沒有證據顯示這就是本案故障原因。

官方參考來源：
- `EthernetTracker.java`，android-9.0.0_r1： https://android.googlesource.com/platform/frameworks/opt/net/ethernet/+/refs/tags/android-9.0.0_r1/java/com/android/server/ethernet/EthernetTracker.java
- `EthernetNetworkFactory.java`，AOSP Gitiles 參考檔： https://android.googlesource.com/platform/frameworks/opt/net/ethernet/+/c84dd94/java/com/android/server/ethernet/EthernetNetworkFactory.java

### 16.3 本階段能排除與不能排除的事項

- Ethernet NetworkAgent 變成 DISCONNECTED 可能只是收到 link-down event 後的狀態反映，不能單獨證明 framework 主動把實體介面關閉。
- 真正要證明 administrative DOWN，必須取得該次事件中的 interface name、介面 flags/carrier，以及呼叫 `setInterfaceDown()` 或 netd `interface setcfg` 的 caller。
- r1 / c84dd94 參考碼不能代替 Android 9 r45 的精確原始碼，更不能代替 Toshiba OEM overlay、vendor service、HAL、init script。
- 本次未找到可將 CYTV VPN teardown 直接連到 EthernetTracker 主動 setInterfaceDown 的呼叫鏈。

### 16.4 下一個安全除錯步驟

不重新啟動 Toshiba 上的 VPN 廣告阻擋。只使用既有故障紀錄或一般網路狀態紀錄，按時間對齊：
1. kernel link event（介面名稱、IFF_UP、LOWER_UP/carrier）。
2. NetworkManagementService observer 收到的 interfaceLinkStateChanged / interfaceRemoved。
3. EthernetTracker / EthernetNetworkFactory 對同一 iface 的處理。
4. 是否有 setInterfaceDown / interface setcfg down，以及 caller。
5. ConnectivityService / netd VPN netId teardown 的時間點。

若 kernel carrier/LOWER_UP 先消失，方向應轉向 driver、HAL、vendor service 或實體 link；若 IFF_UP 被清除，才追查發出 administrative down 的 framework/vendor caller；若只有 NetworkAgent DISCONNECTED 而 link flags 仍 up，則應查 provisioning/NetworkAgent 路徑，而非先修改網卡控制。

### 16.5 修改決策

本階段新增的是可追查的 Ethernet framework 呼叫路徑與證據需求，尚未證明 runtime 根因。保留 Toshiba Android 9 Ethernet R1 guard，不修改 VPN runtime、不產生實驗 APK、不在 Toshiba TV 重測危險情境。PR #47 維持 Draft/open/未合併。


## 17. 第二十一階段：VPN 虛擬網路建立、UID routing 與 teardown 分層

### 17.1 ConnectivityService.updateUids() 的責任邊界

本次可讀取的 AOSP ConnectivityService 版本中，`updateUids()`：
- 從前後 NetworkCapabilities 取出 UID ranges，計算新增與移除集合。
- 新增範圍呼叫 `mNMS.addVpnUidRanges(nai.network.netId, ...)`。
- 移除範圍呼叫 `mNMS.removeVpnUidRanges(nai.network.netId, ...)`。
- 若符合 app VPN isolation 條件，另經 PermissionMonitor 更新 VPN UID/interface filtering。

這段程式管理的是「哪些 UID 使用 VPN netId」及 VPN ingress filtering，不是 Ethernet interface administrative state。該份官方 Gitiles 檔案的 commit 為 `e4de9d2c56b758588c7a365e0f33d88753413c9a`；目前不能僅憑搜尋結果將它標為 Android 9 r45 精確版本。需與 r45 tag source 對照後才可作版本鎖定證據。

### 17.2 netd networkCreateVpn() → createVirtualNetwork()

可直接核對的 AOSP Android 9 r33：
- `NetdNativeService::networkCreateVpn(netId, hasDns, secure)` 將請求交給 `NetworkController::createVirtualNetwork()`。
- `createVirtualNetwork()` 驗證 netId、確認無重複 ID、建立 VPN fallthrough 設定，並建立 `VirtualNetwork(netId, hasDns, secure)`。
- 此函式沒有呼叫 Ethernet interface up/down API，也沒有把實體 Ethernet iface 設為 VPN virtual network 的必要步驟。

### 17.3 netd networkDestroy() → destroyNetwork()

同一 r33 參考版本：
- `NetdNativeService::networkDestroy(netId)` 呼叫 `NetworkController::destroyNetwork(netId)`，再清除該 netId 的 DNS server 設定。
- `destroyNetwork()` 對指定 Network 呼叫 `clearInterfaces()`；對 VirtualNetwork 移除 VPN fallthrough；從 netId map 刪除物件並清理該 netId resolver cache。
- `clearInterfaces()` 是清除該 Network 物件管理的介面關聯；這不等於對 Ethernet 實體介面執行 `interface setcfg down`。
- 這條 VPN virtual network destroy 路徑未見直接呼叫 `InterfaceController::setInterfaceDown` 或 `NetworkManagementService.setInterfaceDown`。

### 17.4 UID ranges 的 netd 端

r33 `NetdNativeService` 將 `networkAddUidRanges` / `networkRemoveUidRanges` 分別交給 `NetworkController::addUsersToNetwork` / `removeUsersFromNetwork`。其對象是 VirtualNetwork UID routing ranges，而非 Ethernet link state。

### 17.5 目前因果結論

截至本階段，可將下列推論提升為有原始碼支持的「責任邊界」：
- `createVirtualNetwork()` 建立 VPN virtual netId 與 fallthrough routing state。
- `updateUids()` 更新 VPN UID routing / isolation。
- `destroyNetwork()` 清除指定 netId 的 network membership、fallthrough 與 DNS resolver cache。
- 上述 API 本身不是 Ethernet 實體介面 link-down 命令；目前未找到從這三條路徑直接呼叫 Ethernet interface-down 的呼叫鏈。

但這仍不是 Toshiba 斷線根因的完整排除證明：r33 只能作 Android 9 相鄰 tag 的 netd 參考；ConnectivityService 搜尋結果也需核對 r45 tag。Toshiba vendor framework、HAL、driver 與 init 路徑仍未被排除。

### 17.6 下一步與安全決策

下一步應在可核對的 Android 9 r45 tag 上鎖定同一組檔案，並將 `interface setcfg down`、`setInterfaceDown` 的所有 caller 與 EthernetTracker link-state 路徑交叉比對。若尚未找到 caller，不能提交 runtime patch。Toshiba + Android 9 + Ethernet 的 R1 guard 保持不變；不重新啟動該設備的 VPN 廣告阻擋、不產生實驗 APK、不合併 PR #47。
