# EthernetNetworkFactory 停止路徑與 log 對照（R3）

日期：2026-11-06  
專案：chistyl/CYTV  
分支：investigation/toshiba-vpn-ethernet-r2  
性質：AOSP 靜態比對；不是 Toshiba OEM source proof，也不是實機修復。

## 1. 修正 source 定位

Android Ethernet framework 不在 frameworks/base/services/core。正確的 AOSP 專案是：

- platform/frameworks/opt/net/ethernet
- Java source path：java/com/android/server/ethernet/EthernetNetworkFactory.java

因此先前在 frameworks/base 鏡像查不到 EthernetNetworkFactory.java，不能解讀成 Android 9 沒有這個元件。

## 2. AOSP refCount bug 是已被上游明確承認的錯誤

AOSP 官方修正：
- Commit：64ba6b016cff7aae69da0af9430bf77eaeeefd30
- Bug：146089778
- 標題：Fix refCount check at releaseNetworkFor
- 日期：2019-12-17

官方 commit 說明：當 releaseNetworkFor() 執行時，若 refCount 原為 2，舊條件會錯誤呼叫 network.stop()，即使仍有另一個 request 使用 Ethernet。

錯誤邏輯：if (--network.refCount == 1) network.stop();
修正邏輯：if (--network.refCount == 0) network.stop();

來源：
- AOSP 官方修正 diff：https://android.googlesource.com/platform/frameworks/opt/net/ethernet/+/64ba6b016cff7aae69da0af9430bf77eaeeefd30%5E%21/
- 公開 Android Pie maintenance source 仍可見舊條件：https://github.com/digi-embedded/android_platform_frameworks_opt_net_ethernet/blob/pie/maint/java/com/android/server/ethernet/EthernetNetworkFactory.java
- AOSP 修正後 source 可見新條件：https://android.googlesource.com/platform/frameworks/opt/net/ethernet/+/bad3d2ddaec0468fedd8067a86385abd7c7173e5/java/com/android/server/ethernet/EthernetNetworkFactory.java

這證明 Android Pie 時期公開 source 曾存在此 bug，且 AOSP 有明確修正；仍不能證明 Toshiba firmware fingerprint Toshiba/xiaoyushan_etT/.../01.03.00.01 使用該舊版本。

## 3. 與 Toshiba log 的對照

現有 log 在 STR resume 後出現：
1. IpClient CMD_STOP eth0
2. CommandListener: Setting iface cfg
3. Netd: Destroyed ... sockets on 192.168.1.106
4. CommandListener: Clearing all IP addresses on eth0
5. Ethernet NetworkAgent 由 CONNECTED 轉 DISCONNECTED
6. 隨後 IpClient CMD_START、DHCP 成功、network validation 通過

此序列與「Ethernet framework 停止既有 IpClient / NetworkAgent，接著重新 provision」相容。它不是 carrier 物理拔線的直接證據。

重要區分：
- releaseNetworkFor() refCount bug 可呼叫 network.stop()。
- Ethernet NetworkAgent 的 unwanted() 亦可呼叫 network.stop()。
- 介面 link state 真正變成 down 也會停止 IpClient。
- 所以 CMD_STOP / 清 IP 是停止路徑的結果，不能單靠它區分上述起因。

## 4. 目前根因排序

| 候選 | 證據強度 | 限制 |
|---|---|---|
| EthernetNetworkFactory refCount 舊條件 | 高優先候選 | 未取得 Toshiba OEM framework source |
| NetworkAgent unwanted / request rematch | 次要候選 | Toshiba log 缺少 unwanted/refCount/request callback 關鍵行 |
| Vendor STR resume / Ethernet service 操作 | 仍未排除 | 缺 Toshiba vendor source 與完整 vendor log |

AOSP 官方 refCount 修正的 commit message 特別描述 refCount=2 時 release 一個 request 卻錯誤 stop Ethernet，與本案 VPN NetworkAgent 出現時可能增加/改變 request 配對、Ethernet 隨後 stop 的機制相符；但這只是可解釋的候選，不是已證明的觸發鏈。

## 5. 對 CYTV patch 的處置

目前 PR #48 的 patch 路徑已對準正確 module：
frameworks/opt/net/ethernet/java/com/android/server/ethernet/EthernetNetworkFactory.java

Patch 保持最小範圍，只把 stop 條件由 == 1 改為 == 0，並修正錯誤 log tag。不得把它套到 CYTV APK；這是 system framework 修正，不是一般應用程式可自行替換的程式碼。

套用前必須先在 Toshiba OEM source 確認舊條件存在，並對該 OEM source 執行 patch check、完整 framework/system image build。沒有 Toshiba source / build environment 時，不得宣稱系統層已修好。

## 6. 安全限制與交付狀態

- Toshiba + Android 9 + Ethernet R1 guard 必須保留。
- 不在使用者的 Toshiba 電視上啟用 VPN 廣告阻擋重現問題。
- 不產生繞過 guard 的測試 APK。
- PR #48 保持 Draft / open / unmerged。
- 本文件是研究紀錄，不修改 runtime code，不代表實機驗收。

## 7. 下一個真正可完成的修復步驟

取得 Toshiba 對應 firmware 的 frameworks/opt/net/ethernet source revision，核對 releaseNetworkFor() 舊條件。若存在，套用 PR #48 的最小 patch 並在該 OEM source 建置 framework/system image；若不存在，轉查 vendor STR resume 與 Ethernet NetworkAgent unwanted 的實際觸發者。
