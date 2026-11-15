# CYTV R163 動態圖片整合規格

## 基準與範圍

- 基準：CYTV R162 / PR #319
- 基準分支：`fix/weather-rain-layout-degree-r162`
- 基準 commit：`93e2af9b38a2904e13eb3f5696bf3c706e2e6ee1`
- R163 工作分支：`feature/dynamic-wallpaper-themes-r163`
- 本功能只新增「影像設定 > 動態圖片」，不改寫 R162，也不改變既有「預設圖片」與「自訂圖片」行為。

## 影像設定項目順序

1. 預設圖片
2. 動態圖片
3. 自訂圖片

「動態圖片」沿用「預設圖片」的九宮格卡片框架，3 欄、16:9 預覽、Android TV 遙控器焦點與選取外框一致。動態圖片區塊提供九個主題。

## 九個主題（介面名稱草案）

1. 城市天際
2. 海洋島嶼
3. 山岳森林
4. 湖泊河流
5. 自然地景
6. 海岸風光
7. 世界建築
8. 冰雪世界
9. 夢幻航拍

以上是 CYTV 的九個 UI 主題，不是 Aerial 原始資料的分類名稱。必須先完成 114 支影片的人工/規則映射，並驗證每個主題至少有可播放項目，才可顯示為可選。

## 影片資料來源調查

參考資料庫：`pengchujin/aerial-tv` 的 `app/src/main/assets/aerials.json`。

- 影片總數：114
- 原始分類：`underwater` 21、`space` 22、`landscapes` 41、`cities` 30
- 可用來源欄位包含 2K AVC URL、4K HEVC URL（另有部分 HEVC / Dolby Vision 版本）。
- CYTV 必須建立自己的九主題映射，不可把原始四分類假裝成九分類。

## CYTV 現況與相容性

- CYTV Launcher 為原生 Java View 架構，並非 Aerial 專案的 Kotlin/Compose 架構，不能直接搬入其 UI。
- CYTV `launcher/app/build.gradle` 的 minSdk 為 26。
- 目前 CYTV 沒有 Media3/ExoPlayer 依賴；加入前須先確認 TV 裝置支援、網路串流格式與 R8 規則。
- 現有 `WallpaperPlayer` 負責靜態圖片輪播，必須保留原本圖片載入、快取、輪播與生命週期，不可直接替換成影片播放器。
- 動態播放應獨立管理播放器與 Surface，Home 不在前景時暫停/釋放，網路失敗時回退至既有靜態桌布。
- 首輪相容性以 2K AVC/H.264 為優先；4K HEVC 只能在確認硬體 decoder/display 能力後選用。

## 授權與資料治理

- Aerial 程式碼專案採 MIT License，不代表 Apple 航拍影片素材也採 MIT 授權。
- 不將 Apple 影片下載或打包進 APK；保留來源歸屬與來源 URL，並在實作前確認外部串流的使用條款及可用性。
- 必須處理來源 URL 失效、連線逾時、無網路、影片格式不支援等情況。

## 實作驗收門檻

- 三個區塊順序固定：預設圖片、動態圖片、自訂圖片。
- 動態圖片九主題皆有非空影片清單，且縮圖與主題對應。
- 遙控器上下左右/確認/返回可完整操作，焦點不遺失。
- 動態播放靜音、循環；離開 Home 或進入待機時停止播放。
- 播放失敗時不黑屏，回退既有靜態桌布。
- 原有預設圖片、自訂圖片、QR 匯入、圖片輪播與儲存草稿行為全部回歸測試。
- 建置 APK、跑測試並提供完整原始碼 ZIP；CI 成功不取代實機驗收。
