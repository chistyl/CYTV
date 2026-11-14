# CYTV R163 — Fixed Widget Grid Zones

## Baseline
- Parent: `feature/widget-layout-live-preview-r162`
- Keep R162 live column/row preview and save/cancel behavior.
- Keep R161/R162 third-party AppWidgetHostView native content and interaction.

## Required behavior
1. Render the actual widget workspace as a fixed grid using the saved desktop widget columns (1–4) and rows (1–3). The visible grid must show every configured zone, including empty zones.
2. Each empty zone has its own add action. Selecting a third-party widget provider binds/configures it and places the widget into the exact zone from which Add was invoked. The page-level Add action may choose the first available empty zone, but must not place widgets in an unassigned vertical list.
3. Every zone has a stable grid coordinate and fixed equal cell bounds for the current columns/rows. Empty zones remain visible and retain their dimensions. One widget must not expand to fill the entire workspace; adding/removing a widget must not move or resize widgets in other zones.
4. Persist widget ID to zone index mapping. On restart, restore the same zone assignments. If the configured grid shrinks and a saved zone falls outside the new grid, place that widget in the first available zone without overwriting another widget.
5. Keep remove confirmation and release the AppWidgetHost ID when removed. Preserve provider RemoteViews and native widget interactions.

## Acceptance
- Test 1x1, 2x1, 3x2, and 4x3.
- Add one widget to a non-first zone; confirm it stays there after restart.
- Add a second widget; confirm the first does not move or stretch.
- Remove one widget; confirm its zone becomes an empty fixed zone and other widgets remain in place.
- Verify D-pad navigation and 720p/1080p layouts.
- Confirm cancel/save semantics for column/row settings.
