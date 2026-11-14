# CYTV R158 — Native Third-Party Android App Widget Host

## Baseline
- Project: chistyl/CYTV
- Base: R156 commit 297d8d11775ed362261ae971f5a26ecd27f57f70
- Branch: feature/android-widget-host-r158
- Draft PR; do not merge before real-device acceptance.

## Goal
Add genuine Android App Widget hosting. This is not an app-shortcut catalog and does not install APKs or alter third-party providers.

## First implementation slice
- Add a desktop Settings entry named「第三方小工具」.
- Open a TV-oriented widget workspace from the desktop.
- Discover installed third-party providers through AppWidgetManager.getInstalledProviders().
- Allocate host IDs and use bindAppWidgetIdIfAllowed; when user approval is required, invoke ACTION_APPWIDGET_BIND.
- Launch the provider's configure Activity when declared.
- Render provider RemoteViews through AppWidgetHost/AppWidgetHostView.
- Persist host widget IDs locally; remove stale IDs and allow removing a widget without uninstalling its provider app.
- Keep the native widget view interactive and focusable for TV remote navigation.
- Android API 26+; base minSdk remains 26, covering Android 9 and later.

## Explicit limitations for this first slice
- The widget workspace is opened from the desktop settings menu; widgets are not yet freely positioned as floating cards among the app tiles.
- Per-widget resize/drag placement and host-side custom grid placement are follow-up work.
- Actual provider compatibility and D-pad behavior require TV hardware validation; CI cannot prove vendor firmware behavior.

## Preservation
R156 remains the sole base. Existing launcher grid, folders, HDMI, network, ad filtering, wallpaper, weather and remote behavior must remain unchanged.
