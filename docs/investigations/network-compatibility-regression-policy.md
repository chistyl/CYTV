# CYTV 網路相容性修正策略與防退化驗收規範

日期：2026-11-06  
適用專案：chistyl/CYTV  
研究分支：investigation/toshiba-vpn-ethernet-r2

## 1. 核心決策

本次工作定位為「Toshiba Android 9 Ethernet 相容性修正」，不得將未驗證的修正直接套用到所有 Android 裝置。

最高優先順序：實體網路連線穩定性高於廣告阻擋功能。

## 2. 裝置分流政策

### Toshiba + Android 9 + Ethernet
- 必須保留 R1 安全保護。
- 在根因尚未獲得安全驗證前，不得呼叫 VPN Builder.establish()。
- 自動關閉廣告阻擋，確保實體網路優先。
- 不得提供會繞過此 guard 的測試 APK，也不得要求使用者在此設備重新啟用阻擋廣告。

### 其他 Android 9 裝置
- 維持既有正式行為。
- 不因 Toshiba 個案而自動套用新的 VPN underlay 或路由行為。
- 只有在有明確技術證據及安全測試設備時，才可針對特定設備加入獨立條件。

### Android 10 及以上
- 維持既有 VPN 建立與網路選擇流程。
- Toshiba Android 9 專案修正不得改變這些版本的預設行為。
- 不得把 Android 新版本 framework 行為直接假設為 Android 9 行為，反之亦然。

## 3. R2 實驗規範

- 所有候選修正先做靜態分析與差異審查。
- 如需 runtime 實驗，必須使用獨立實驗開關，預設關閉。
- 實驗開關不得覆蓋 Toshiba Android 9 Ethernet R1 guard。
- 不得以全裝置強制指定 Builder.setUnderlyingNetworks() 作為未經驗證的通用修正。
- 在沒有非 Toshiba Android 9 Ethernet 測試設備前，不產生可能誘導使用者在 Toshiba 電視上測試 VPN 的 APK。
- 不得合併 PR，除非使用者明確授權；實機驗收前維持 Draft / open。

## 4. 防退化驗收矩陣

任何未來 runtime 修改至少要評估下列情境：

| 測試族群 | Ethernet | Wi-Fi | 網路切換 | VPN 停止後恢復 |
|---|---|---|---|---|
| Toshiba Android 9 | R1 必須阻止 VPN 啟動，實體網路保持可用 | 不得因 Ethernet guard 誤傷 Wi-Fi | 不得因切換觸發不安全 VPN 啟動 | 原始 Ethernet 連線必須維持 |
| 其他 Android 9 | 原有連線不可退化 | 原有連線不可退化 | Ethernet/Wi-Fi 切換正常 | 停止過濾後實體網路恢復 |
| Android 10+ | 原有連線不可退化 | 原有連線不可退化 | Ethernet/Wi-Fi 切換正常 | 停止過濾後實體網路恢復 |

若沒有相應實體設備，該族群標記為「未驗證」，不可宣稱通過。CI 成功只代表建置與自動化測試成功，不等於實體網路驗收成功。

## 5. 修改與交付規則

1. 以已驗收基準為起點開獨立分支，不覆蓋已驗收功能。
2. 保留 Toshiba R1 guard，任何修改都必須明確審查其控制流程。
3. 逐一檢查 Android API 相容性、NetworkCallback、VPN NetworkAgent、routes、DNS 與 underlying network 行為。
4. 對其他 Android 版本做程式碼差異審查及可用的 CI 測試。
5. APK 只有在確認不會繞過安全 guard、Artifact 真實存在且完成必要測試後才可交付。
6. PR 預設 Draft、open、未合併；實機驗收與使用者授權前不得合併。

## 6. 目前狀態

- Toshiba Android 9 Ethernet R1 安全保護：實機驗收通過。
- R2 根因：仍在 AOSP 靜態分析，尚未確認。
- Runtime code：本策略文件不修改 runtime code。
- APK：本階段不產生。
- 其他 Android 版本：維持原有行為，尚未宣稱經本次修正重新驗收。
