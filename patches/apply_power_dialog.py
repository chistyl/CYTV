from pathlib import Path

path = Path("launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java")
text = path.read_text(encoding="utf-8")
old = '''        if (!HomeKeyService.canOpenPowerMenu()) {
            new AlertDialog.Builder(this)
                    .setTitle("尚未啟用重新啟動功能")
                    .setMessage("需要先啟用 CleanYourTV 的「主畫面鍵」輔助使用服務，才能開啟電視的系統電源選單。啟用後返回桌面，再選擇「重新啟動」。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("前往輔助使用設定", (dialog, which) ->
                            open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .show();
            return;
        }

        // The service is connected, but this firmware declined GLOBAL_ACTION_POWER_DIALOG.
        new AlertDialog.Builder(this)
                .setTitle("無法開啟系統電源選單")
                .setMessage("電視系統沒有接受電源選單請求。請使用電視遙控器的電源鍵，或前往 Android 系統設定重新啟動電視。")
                .setPositiveButton("開啟系統設定", (dialog, which) ->
                        open(new Intent(Settings.ACTION_SETTINGS)))
                .setNegativeButton("取消", null)
                .show();
    }
'''
new = '''        if (!HomeKeyService.canOpenPowerMenu()) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("尚未啟用重新開啟功能")
                    .setMessage("需要先啟用 LauncherTV 的「主畫面鍵」輔助使用服務，才能開啟電視的系統電源選單。啟用後返回桌面，再選擇「重新開啟」。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("前往輔助使用設定", (d, which) ->
                            open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .create();
            dialog.show();
            styleLauncherPowerDialog(dialog, true);
            return;
        }

        // The service is connected, but this firmware declined GLOBAL_ACTION_POWER_DIALOG.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("無法開啟系統電源選單")
                .setMessage("電視系統沒有接受電源選單請求。請使用電視遙控器的電源鍵，或前往 Android 系統設定重新開啟電視。")
                .setPositiveButton("開啟系統設定", (d, which) ->
                        open(new Intent(Settings.ACTION_SETTINGS)))
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        styleLauncherPowerDialog(dialog, true);
    }

    /** Charcoal rounded dialog matching the Settings popup; focus uses the same light pill as menu rows. */
    private void styleLauncherPowerDialog(AlertDialog dialog, boolean focusPrimary) {
        Window window = dialog.getWindow();
        if (window != null) {
            GradientDrawable surface = new GradientDrawable();
            surface.setColor(0xFF242426);
            surface.setCornerRadius(24 * density);
            surface.setStroke(Math.max(1, (int) (1.2f * density)), 0xFF505054);
            window.setBackgroundDrawable(surface);
            window.setDimAmount(0.62f);
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId != 0 ? dialog.findViewById(titleId) : null;
        if (title != null) title.setTextColor(Color.WHITE);
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(0xFFE7E7E7);
            message.setLineSpacing(3 * density, 1.0f);
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button[] buttons = new Button[]{negative, positive};
        for (Button button : buttons) {
            if (button == null) continue;
            button.setAllCaps(false);
            button.setFocusable(true);
            button.setFocusableInTouchMode(true);
            button.setMinHeight((int) (52 * density));
            button.setMinWidth((int) (112 * density));
            button.setDefaultFocusHighlightEnabled(false);
            GradientDrawable normal = new GradientDrawable();
            normal.setColor(0xFF343436);
            normal.setCornerRadius(16 * density);
            GradientDrawable focused = new GradientDrawable();
            focused.setColor(0xFFF4F4F4);
            focused.setCornerRadius(16 * density);
            focused.setStroke(Math.max(1, (int) (1 * density)), 0xFFFFFFFF);
            button.setBackground(normal);
            button.setTextColor(0xFFE8E8E8);
            button.setOnFocusChangeListener((v, hasFocus) -> {
                v.setBackground(hasFocus ? focused : normal);
                ((Button) v).setTextColor(hasFocus ? 0xFF202124 : 0xFFE8E8E8);
                v.setElevation(hasFocus ? 3 * density : 0f);
                v.setScaleX(hasFocus ? 1.03f : 1f);
                v.setScaleY(hasFocus ? 1.03f : 1f);
            });
        }
        if (negative != null && positive != null) {
            negative.setNextFocusRightId(positive.getId());
            positive.setNextFocusLeftId(negative.getId());
        }
        Button first = focusPrimary && positive != null ? positive : negative;
        if (first != null) first.post(first::requestFocus);
    }
'''
if old not in text:
    raise SystemExit("Expected original power dialog code not found; refusing to edit.")
path.write_text(text.replace(old, new), encoding="utf-8")
