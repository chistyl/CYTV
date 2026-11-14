# CYTV R164 — Widget Long-Press Management, Resize and Edit

Base: CYTV R163 fixed widget grid zones.

## Requirements
- Remove widget name/title labels from management cards.
- Remove the always-visible remove button.
- Long-press a widget in the management page or desktop to open its management actions.
- Offer Edit only when the provider declares a configuration activity.
- Offer resize controls for horizontal and vertical display scale; persist per-widget settings.
- Offer removal from the long-press menu with confirmation.
- Preserve R163 fixed grid zones, slot coordinates, weather avoidance and column/row configuration.

## Implementation notes
- Third-party widget content remains owned by its provider.
- Edit launches the provider's declared AppWidget configure activity; widgets without one do not show an Edit action.
- Size controls adjust the hosted view's X/Y display scale and save the values in the CYTV widget preferences.
- Physical TV validation is required for long-press gesture behavior, focus navigation, clipping and provider-specific resize compatibility.

## Weather isolation — mandatory regression guard
- The existing CYTV weather forecast is a protected, independent desktop feature.
- Preserve the existing Today and Weekly forecast modes, appearance, position, dimensions, switching behavior, refresh behavior and visibility rules exactly as implemented in the R163 base.
- Third-party widget operations (add/remove, fixed-zone layout, row/column changes, per-widget X/Y scaling, provider configuration) must never resize, move, cover, scale, restyle, replace or otherwise alter the weather forecast.
- Widget-zone placement must continue to respect the existing weather avoidance logic and spacing from the R163 base. Do not redesign the weather card or introduce a new weather position.
- Any change that affects the weather view or its layout is a regression and must be corrected before R164 can be considered build-ready.
- Validate both Today and Weekly weather modes alongside widget layout changes on the TV device.
