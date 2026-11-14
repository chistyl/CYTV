# CYTV R159 — Desktop-integrated third-party widgets

## Baseline
- Repository: chistyl/CYTV
- R158 base: feature/android-widget-host-r158 at f5ff671f0dbf5bc743ce4f05607dfe889103c9b3
- R159 branch: feature/desktop-widgets-r159
- Draft PR stacked on R158; do not merge before TV acceptance.

## R159 changes
1. Render configured Android AppWidgetHostView instances in a dedicated section on the existing CYTV Home desktop, above the app grid.
2. Keep desktop controls visible even before the first widget is added; provide visible add/manage launch actions.
3. Provider picker rows show the provider application's icon and name, with separate「啟動 APP」and「新增」actions.
4. Add user-configurable widget grid columns (1–4) and visible rows (1–3); persist preferences.
5. Use CYTV dark surfaces, teal accent, compact typography, and focusable remote controls.
6. Keep the weather card anchored at one fixed position below the clock for both「一天」and「一週」modes; adapt only its content width and minimum height.
7. Keep the weather overlay outside the app-grid measurement so enabling/disabling weather never changes the clock or weather anchor. Place the widget shelf below the app grid so it cannot cover the weather card.
8. Keep the weather mode's daily/week layout responsive to available screen width, with a compact one-day card and a wider seven-day forecast.
6. Preserve native bind/configure flow, host IDs, saved widget IDs, and provider RemoteViews.

## Acceptance checks
- Desktop visibly displays the widget section and its add/manage controls.
- Toggling weather enabled/disabled does not move the clock or weather card anchor.
- Switching「一天／一週」keeps the weather card's top-left anchor fixed while its content width/height adapts.
- Widget shelf is below the app grid and does not overlap the weather overlay.
- After adding a widget, its RemoteViews render on Home without opening a separate screen.
- Provider list displays app icons and has an app launch action.
- Column/row settings persist after leaving and reopening the widget screen.
- D-pad can reach add/manage/provider actions and widget content.

## Explicit remaining validation
- Build/CI can validate compilation and unit tests, not TV firmware widget bind permission UI, individual provider RemoteViews, or every widget's D-pad behavior.
- Drag-and-drop positioning and per-widget resizing are not included in R159; widgets are arranged in a configurable grid section above the existing app tiles.

## Preservation
R158 remains the base. Do not alter activation/login behavior or existing launcher functions: app grid, folders, HDMI, network, ad filtering, wallpaper, weather, and remote navigation.
