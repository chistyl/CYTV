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
            showLauncherPowerDialog(
                    "尚未啟用重新啟動功能",
                    "需要先啟用 LauncherTV 的「主畫面鍵」輔助使用服務，才能開啟電視的系統電源選單。啟用後返回桌面，再選擇「重新啟動」。",
                    "前往輔助使用設定",
                    "取消",
                    () -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            return;
        }

        // The service is connected, but this firmware declined GLOBAL_ACTION_POWER_DIALOG.
        showLauncherPowerDialog(
                "無法開啟系統電源選單",
                "電視系統沒有接受電源選單請求。請使用電視遙控器的電源鍵，或前往 Android 系統設定重新啟動電視。",
                "開啟系統設定",
                "取消",
                () -> open(new Intent(Settings.ACTION_SETTINGS)));
    }

    /** Custom spacious TV dialog: balanced typography, separated rounded buttons, D-pad navigation. */
    private void showLauncherPowerDialog(String titleText, String messageText,
                                         String positiveText, String negativeText,
                                         Runnable positiveAction) {
        android.widget.LinearLayout panel = new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setPadding((int) (36 * density), (int) (30 * density),
                (int) (36 * density), (int) (26 * density));
        panel.setBackground(new GradientDrawable() {
            setColor(0xFF242426);
            setCornerRadius(26 * density);
            setStroke(Math.max(1, (int) (1.2f * density)), 0xFF505054);
        });

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setIncludeFontPadding(true);
        panel.addView(title, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        message.setText(messageText);
        message.setTextColor(0xFFE7E7E7);
        message.setTextSize(20);
        message.setLineSpacing(7 * density, 1.0f);
        message.setIncludeFontPadding(true);
        android.widget.LinearLayout.LayoutParams messageParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = (int) (13 * density);
        panel.addView(message, messageParams);

        android.widget.LinearLayout buttonRow = new android.widget.LinearLayout(this);
        buttonRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        android.widget.LinearLayout.LayoutParams rowParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (25 * density);
        panel.addView(buttonRow, rowParams);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setDimAmount(0.62f);
                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                int width = Math.min((int) (metrics.widthPixels * 0.86f),
                        (int) (960 * density));
                window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        Button negative = new Button(this);
        negative.setText(negativeText);
        Button positive = new Button(this);
        positive.setText(positiveText);
        Button[] buttons = new Button[]{negative, positive};
        for (Button button : buttons) {
            button.setAllCaps(false);
            button.setTextSize(18);
            button.setMinHeight((int) (64 * density));
            button.setPadding((int) (25 * density), 0, (int) (25 * density), 0);
            button.setFocusable(true);
            button.setFocusableInTouchMode(true);
            button.setDefaultFocusHighlightEnabled(false);
            GradientDrawable normal = new GradientDrawable();
            normal.setColor(0xFF343436);
            normal.setCornerRadius(18 * density);
            GradientDrawable focused = new GradientDrawable();
            focused.setColor(0xFFF4F4F4);
            focused.setCornerRadius(18 * density);
            focused.setStroke(Math.max(1, (int) (1 * density)), 0xFFFFFFFF);
            button.setBackground(normal);
            button.setTextColor(0xFFE8E8E8);
            button.setOnFocusChangeListener((v, hasFocus) -> {
                v.setBackground(hasFocus ? focused : normal);
                ((Button) v).setTextColor(hasFocus ? 0xFF202124 : 0xFFE8E8E8);
                v.setElevation(hasFocus ? 3 * density : 0f);
            });
        }

        android.widget.LinearLayout.LayoutParams negativeParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        (int) (64 * density));
        negativeParams.leftMargin = (int) (14 * density);
        android.widget.LinearLayout.LayoutParams positiveParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        (int) (64 * density));
        positiveParams.leftMargin = (int) (14 * density);
        buttonRow.addView(negative, negativeParams);
        buttonRow.addView(positive, positiveParams);

        negative.setOnClickListener(v -> dialog.dismiss());
        positive.setOnClickListener(v -> {
            dialog.dismiss();
            if (positiveAction != null) positiveAction.run();
        });
        negative.setId(View.generateViewId());
        positive.setId(View.generateViewId());
        negative.setNextFocusRightId(positive.getId());
        positive.setNextFocusLeftId(negative.getId());

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.62f);
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = Math.min((int) (metrics.widthPixels * 0.86f),
                    (int) (960 * density));
            window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
'''
if old not in text:
    raise SystemExit("Expected original power dialog code not found; refusing to edit.")
path.write_text(text.replace(old, new), encoding="utf-8")
