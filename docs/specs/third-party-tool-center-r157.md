# CYTV R157：第三方工具中心規格

## 開發基準
- Repository: chistyl/CYTV
- 唯一基礎 Commit: 297d8d11775ed362261ae971f5a26ecd27f57f70 (R156)
- Base branch for PR: feature/disable-login-verification-r156
- Target: Android 9 (API 28) and above
- Keep R156 login-verification-disabled behavior and all existing features.

## Confirmed requirements
1. Add a desktop entry named「工具中心」.
2. In the existing app-management area, add「新增工具 +」above「應用分類 +」.
3. Only add apps already installed on the TV. No APK import/install, arbitrary URL, or external code loading.
4. Tool-center grid columns and rows are user-configurable, independent of the existing desktop app grid.
5. Add/remove shortcuts without uninstalling the underlying app.
6. Read app label and launcher icon from Android; prevent duplicates.
7. Persist tool list, order, grid settings, and icon presentation mode across restart.
8. Support TV remote D-pad navigation, select, back, long-press management, and focus restoration.
9. Refresh label/icon after app update; clean stale entries when an app is uninstalled or no longer launchable.
10. Provide search, custom ordering, optional custom categories, and safe confirmation before removal.

## Tool icon presentation
Provide three modes:
- CYTV Smart Style: generate a consistent CYTV-style icon from app label and available app icon/context.
- Original: display the app's original launcher icon.
- CYTV Minimal: apply a consistent dark/teal visual treatment.

Generated icons are presentation assets only. Never modify the third-party app package or its original icon. Generated assets must be cached locally and remain available offline. If generation is unavailable or fails, fall back to the original icon or a bundled deterministic style treatment. Do not require a cloud connection at app launch. Do not claim on-device generative AI unless a compatible model is actually implemented.

## Android compatibility architecture
- Keep minSdk 26 unless repository-wide constraints require otherwise; Android 9 is API 28.
- Use PackageManager/LauncherApps and explicitly handle launcher activities from CATEGORY_LEANBACK_LAUNCHER and CATEGORY_LAUNCHER.
- Respect Android 11+ package visibility. Review existing QUERY_ALL_PACKAGES and <queries> policy before adding permissions; request the narrowest visibility that still supports the product requirement.
- Use defensive null checks for missing launch intents, labels, icons, and activities.
- Keep Android-version-specific behavior behind small compatibility helpers.
- Do not rely on newer APIs without guarded fallbacks.

## Suggested components
- ToolCenterActivity: independent tool-center screen.
- InstalledAppScanner: enumerate launchable installed apps.
- ToolRepository: persist selected package/activity IDs, ordering, categories, and layout preferences.
- ToolLauncher: launch selected apps and report unavailable apps safely.
- ToolIconProvider: resolve original icons and cached presentation assets.
- ToolGridAdapter / focus controller: render configurable grid and manage TV focus.

## UI and interaction
- Match current CYTV dark visual language, background treatment, card proportions, typography, and focus colors.
- Add「新增工具 +」above「應用分類 +」in the relevant existing app-management UI.
- Grid layout settings affect only Tool Center, never the existing desktop app grid.
- Recommended initial layout: 5 columns x 2 rows; user can adjust columns and rows.
- Provide immediate preview of grid changes.
- Preserve focused item when returning from a launched tool or management dialog where possible.

## Non-regression requirements
- Do not alter R156 activation/login behavior.
- Preserve R155 CWA weather fallback inherited by R156.
- Do not regress desktop app list, folders, HDMI/input, remote navigation, network, ad filtering, wallpaper, or settings.
- Do not merge the PR before CI and real-device acceptance.

## Delivery and validation
- Deliver debug APK and complete source ZIP.
- Run unit tests and Android build.
- Test Android 9 and representative Android 10-15+ devices/TV firmware.
- Test D-pad navigation, app add/remove, duplicates, uninstall/update refresh, offline icon cache, restart persistence, and layout changes.
- This document records the agreed scope; it does not claim that application code or APK has already been implemented.


## Weather-aware desktop layout and grid behavior (追加需求)
The Tool Center and the existing desktop must remain visually stable when weather information is displayed in either one-day or one-week mode.

### Weather display states to account for
- Compact / collapsed weather card.
- One-day weather information expanded.
- Seven-day weather information expanded or opened in its forecast dialog.

### Layout rules
1. Do not hard-code a fixed grid height based only on the default 5 x 2 setting. Measure the actual safe content viewport after the weather presentation state and screen dimensions are known.
2. Keep the Tool Center's saved columns/rows as the user's preferred layout. Compute the number of currently visible rows from available height; allow vertical scrolling only when the configured tools exceed the visible area.
3. When the one-day weather card is visible on the desktop, reserve a non-overlapping safe region for it. Tool cards must not be covered by the weather card; preserve their size and focus geometry where possible. If space is insufficient, reflow the tool grid within the remaining safe viewport rather than drawing underneath the weather information.
4. When the seven-day forecast is opened as a modal/dialog, do not leave an interactive Tool Center grid visible or focusable behind it. The forecast dialog owns focus until dismissed. On return, restore the previous screen, grid scroll position, selected tool, and D-pad focus.
5. When switching between collapsed, one-day, and one-week weather states, recompute layout after the final measured dimensions are available. Avoid layout thrashing, clipped cards, overlapping focus/glass highlights, and unexpected jumps in the focused item.
6. The weather panel's own seven-day columns are independent from Tool Center columns. Never reuse the Tool Center's configurable column count to lay out weather forecast days.
7. Keep the existing desktop weather overlay behavior and the R156 weather rendering/fallback logic unchanged. This R157 work only needs to respect the weather panel's occupied area and focus/modal state.
8. Test with weather disabled, compact card, one-day display, seven-day card, and seven-day forecast dialog, including 720p and 1080p TV resolutions and D-pad navigation.

### Acceptance criteria
- No tool tile or focus highlight is obscured by the one-day weather card.
- Seven-day weather forecast remains its own seven-day layout and does not distort Tool Center grid settings.
- Opening and closing the forecast does not reset the user's selected tool, order, configured columns/rows, or focus.
- No overlap, inaccessible bottom row, or focus trap occurs when weather display mode changes.
