package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * CYTV's native Android App Widget host workspace.
 *
 * Third-party providers supply their own RemoteViews; CYTV hosts those views without
 * modifying provider APKs or their original widget layouts.
 */
public final class WidgetShelfActivity extends Activity {
    private static final int HOST_ID = 0x43595456;
    private static final int REQUEST_PICK_WIDGET = 0x435;
    private static final int REQUEST_CONFIGURE_WIDGET = 0x436;
    private static final String PREFS = "cytv_desktop_widgets";
    private static final String KEY_IDS = "widget_ids";

    private AppWidgetManager widgetManager;
    private AppWidgetHost widgetHost;
    private GridLayout widgetList;
    private TextView emptyState;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int pendingWidgetSlotIndex = -1;
    private float density;

    private int dp(int value) {
        return Math.round(value * density);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, HOST_ID);
        buildUi();
        renderWidgets();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            widgetHost.startListening();
        } catch (RuntimeException ignored) {
            // Some vendor launchers tear down the host service during a rapid HOME transition.
        }
        renderWidgets();
    }

    @Override
    protected void onStop() {
        try {
            widgetHost.stopListening();
        } catch (RuntimeException ignored) {
            // The host may already have been stopped by the system.
        }
        super.onStop();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(40), dp(24), dp(40), dp(24));
        page.setBackgroundColor(0xFF10141C);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("桌面小工具", 27, Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));

        Button add = button("新增小工具 +");
        add.setOnClickListener(v -> pickWidget(firstEmptySlot()));
        header.addView(add, new LinearLayout.LayoutParams(dp(190), dp(48)));

        Button layout = button("欄列設定");
        layout.setOnClickListener(v -> showLayoutSettings());
        LinearLayout.LayoutParams layoutLp = new LinearLayout.LayoutParams(dp(140), dp(48));
        layoutLp.leftMargin = dp(10);
        header.addView(layout, layoutLp);

        Button back = button("返回桌面");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(150), dp(48));
        backLp.leftMargin = dp(10);
        header.addView(back, backLp);
        page.addView(header);

        TextView hint = text("選擇電視已安裝 APP 提供的小工具。小工具內容由原 APP 提供，CYTV 不會修改第三方 APP。", 14, 0xFF9AA6B5);
        hint.setPadding(0, dp(4), 0, dp(16));
        page.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        widgetList = new GridLayout(this);
        widgetList.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        widgetList.setUseDefaultMargins(false);
        widgetList.setPadding(dp(4), dp(4), dp(4), dp(20));
        scroll.addView(widgetList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        emptyState = text("尚未新增小工具\n請按右上方「新增小工具 +」選擇第三方 APP 提供的 Widget。", 18, 0xFFADB8C6);
        emptyState.setGravity(Gravity.CENTER);
        page.addView(emptyState, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));

        setContentView(page);
    }

    private int firstEmptySlot() {
        List<Integer> slots = readSlots();
        int columns = Math.max(1, Math.min(4, getSharedPreferences(PREFS, MODE_PRIVATE).getInt("desktop_widget_columns", 2)));
        int rows = Math.max(1, Math.min(3, getSharedPreferences(PREFS, MODE_PRIVATE).getInt("desktop_widget_rows", 1)));
        for (int i = 0; i < columns * rows; i++) {
            if (i >= slots.size() || slots.get(i) == 0) return i;
        }
        return -1;
    }

    private void pickWidget(int targetSlot) {
        if (targetSlot < 0) {
            Toast.makeText(this, "目前配置已沒有空白分區，請先調整欄列或移除小工具", Toast.LENGTH_LONG).show();
            return;
        }
        pendingWidgetSlotIndex = targetSlot;
        final List<AppWidgetProviderInfo> providers = widgetManager.getInstalledProviders();
        final ArrayList<AppWidgetProviderInfo> thirdParty = new ArrayList<>();
        for (AppWidgetProviderInfo info : providers) {
            if (info == null || info.provider == null
                    || getPackageName().equals(info.provider.getPackageName())) continue;
            thirdParty.add(info);
        }
        if (thirdParty.isEmpty()) {
            pendingWidgetSlotIndex = -1;
            Toast.makeText(this, "目前沒有找到已安裝的第三方小工具提供者", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(list);
        for (AppWidgetProviderInfo info : thirdParty) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10), dp(8), dp(10), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF202936);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), 0x334CA6AD);
            item.setBackground(bg);

            ImageView icon = new ImageView(this);
            try {
                icon.setImageDrawable(getPackageManager().getApplicationIcon(info.provider.getPackageName()));
            } catch (Exception ignored) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            item.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(String.valueOf(info.loadLabel(getPackageManager())), 16, Color.WHITE);
            TextView packageName = text(info.provider.getPackageName(), 11, 0xFF9AA6B5);
            labels.addView(name);
            labels.addView(packageName);
            LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, dp(56), 1);
            labelsLp.leftMargin = dp(12);
            item.addView(labels, labelsLp);

            Button launch = button("啟動 APP");
            launch.setTextSize(12);
            launch.setOnClickListener(v -> {
                Intent app = getPackageManager().getLaunchIntentForPackage(info.provider.getPackageName());
                if (app != null) startActivity(app);
                else Toast.makeText(this, "此 APP 沒有可啟動的主畫面", Toast.LENGTH_SHORT).show();
            });
            item.addView(launch, new LinearLayout.LayoutParams(dp(105), dp(42)));

            Button add = button("新增");
            add.setTextSize(12);
            LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(82), dp(42));
            addLp.leftMargin = dp(8);
            item.addView(add, addLp);
            add.setOnClickListener(v -> {
                dialogDismiss(list);
                bindProvider(info);
            });

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
            itemLp.bottomMargin = dp(8);
            list.addView(item, itemLp);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("選擇第三方小工具")
                .setView(scroll)
                .setNegativeButton("關閉", null)
                .create();
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            if (row instanceof LinearLayout) {
                View add = ((LinearLayout) row).getChildAt(3);
                if (add != null) add.setOnClickListener(v -> {
                    int position = list.indexOfChild(row);
                    dialog.dismiss();
                    bindProvider(thirdParty.get(position));
                });
            }
        }
        dialog.show();
    }

    private void dialogDismiss(View ignored) {
        // The provider list is a transient chooser; binding starts after the chooser is dismissed.
    }

    private void showLayoutSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int initialColumns = Math.max(1, Math.min(4, prefs.getInt("desktop_widget_columns", 2)));
        int initialRows = Math.max(1, Math.min(3, prefs.getInt("desktop_widget_rows", 1)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(12));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        NumberPicker columns = new NumberPicker(this);
        columns.setMinValue(1);
        columns.setMaxValue(4);
        columns.setWrapSelectorWheel(false);
        columns.setValue(initialColumns);
        NumberPicker rows = new NumberPicker(this);
        rows.setMinValue(1);
        rows.setMaxValue(3);
        rows.setWrapSelectorWheel(false);
        rows.setValue(initialRows);

        LinearLayout colBox = new LinearLayout(this);
        colBox.setOrientation(LinearLayout.VERTICAL);
        colBox.setGravity(Gravity.CENTER);
        colBox.addView(text("每列欄數", 14, Color.WHITE));
        colBox.addView(columns, new LinearLayout.LayoutParams(dp(112), dp(104)));
        LinearLayout rowBox = new LinearLayout(this);
        rowBox.setOrientation(LinearLayout.VERTICAL);
        rowBox.setGravity(Gravity.CENTER);
        rowBox.addView(text("顯示列數", 14, Color.WHITE));
        rowBox.addView(rows, new LinearLayout.LayoutParams(dp(112), dp(104)));
        controls.addView(colBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        controls.addView(rowBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(controls);

        TextView summary = text("", 13, 0xFFB9C7D5);
        summary.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        summaryLp.topMargin = dp(4);
        content.addView(summary, summaryLp);

        GridLayout preview = new GridLayout(this);
        preview.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        preview.setUseDefaultMargins(false);
        preview.setPadding(dp(4), dp(4), dp(4), dp(4));
        preview.setBackground(makePreviewPanel());
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        previewLp.topMargin = dp(6);
        content.addView(preview, previewLp);

        TextView note = text("預覽會依欄數與列數即時排列；儲存後套用至桌面。", 12, 0xFF8F9BAA);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        content.addView(note, noteLp);

        Runnable refreshPreview = () -> {
            int cols = columns.getValue();
            int rowCount = rows.getValue();
            summary.setText("桌面配置：" + cols + " 欄 × " + rowCount + " 列");
            preview.removeAllViews();
            preview.setColumnCount(cols);
            preview.setRowCount(rowCount);
            int total = cols * rowCount;
            for (int i = 0; i < total; i++) {
                LinearLayout tile = new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                GradientDrawable tileBg = new GradientDrawable();
                tileBg.setColor(0x332D4654);
                tileBg.setCornerRadius(dp(9));
                tileBg.setStroke(dp(1), 0x664CA6AD);
                tile.setBackground(tileBg);
                TextView icon = text("◫", 22, 0xFFB8D7DC);
                icon.setGravity(Gravity.CENTER);
                tile.addView(icon, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
                TextView label = text("小工具 " + (i + 1), 10, 0xFFB9C7D5);
                label.setGravity(Gravity.CENTER);
                tile.addView(label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
                GridLayout.LayoutParams cell = new GridLayout.LayoutParams(
                        GridLayout.spec(i / cols, 1f), GridLayout.spec(i % cols, 1f));
                cell.width = 0;
                cell.height = 0;
                cell.setMargins(dp(4), dp(4), dp(4), dp(4));
                preview.addView(tile, cell);
            }
            preview.requestLayout();
        };
        columns.setOnValueChangedListener((picker, oldValue, newValue) -> refreshPreview.run());
        rows.setOnValueChangedListener((picker, oldValue, newValue) -> refreshPreview.run());
        refreshPreview.run();

        new AlertDialog.Builder(this)
                .setTitle("桌面小工具欄列設定")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (d, w) -> {
                    prefs.edit()
                            .putInt("desktop_widget_columns", columns.getValue())
                            .putInt("desktop_widget_rows", rows.getValue())
                            .apply();
                    normalizeSlotsToGrid();
                    renderWidgets();
                    Toast.makeText(this, "桌面欄列設定已儲存", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private android.graphics.drawable.Drawable makePreviewPanel() {
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xFF171D27);
        panel.setCornerRadius(dp(12));
        panel.setStroke(dp(1), 0x334CA6AD);
        return panel;
    }

    private void bindProvider(AppWidgetProviderInfo info) {
        pendingWidgetId = widgetHost.allocateAppWidgetId();
        boolean bound;
        try {
            bound = widgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId, info.provider);
        } catch (RuntimeException e) {
            bound = false;
        }
        if (bound) {
            configureOrCommit(pendingWidgetId);
            return;
        }
        Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
        try {
            startActivityForResult(bind, REQUEST_PICK_WIDGET);
        } catch (Exception e) {
            deletePending(pendingWidgetId);
            Toast.makeText(this, "系統無法授權此小工具", Toast.LENGTH_LONG).show();
        }
    }

    private void configureOrCommit(int id) {
        AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
        if (info == null) {
            deletePending(id);
            Toast.makeText(this, "無法取得所選小工具資訊", Toast.LENGTH_SHORT).show();
            return;
        }
        if (info.configure != null) {
            Intent configure = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            configure.setComponent(info.configure);
            configure.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            try {
                startActivityForResult(configure, REQUEST_CONFIGURE_WIDGET);
            } catch (Exception e) {
                deletePending(id);
                Toast.makeText(this, "無法開啟小工具設定畫面", Toast.LENGTH_SHORT).show();
            }
        } else {
            commitWidget(id);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_WIDGET) {
            int id = pendingWidgetId;
            if (resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                configureOrCommit(id);
            } else {
                deletePending(id);
            }
            return;
        }
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            int id = pendingWidgetId;
            if (resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                commitWidget(id);
            } else {
                deletePending(id);
            }
        }
    }

    private void commitWidget(int id) {
        AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
        if (info == null) {
            deletePending(id);
            return;
        }
        List<Integer> slots = readSlots();
        int slotCount = configuredSlotCount();
        while (slots.size() < slotCount) slots.add(0);
        int target = pendingWidgetSlotIndex;
        if (target < 0 || target >= slotCount || slots.get(target) != 0) target = firstEmptySlot();
        if (target < 0) {
            deletePending(id);
            Toast.makeText(this, "沒有可用的固定分區", Toast.LENGTH_LONG).show();
            return;
        }
        slots.set(target, id);
        saveSlots(slots);
        pendingWidgetSlotIndex = -1;
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        renderWidgets();
        Toast.makeText(this, "已新增：" + info.loadLabel(getPackageManager()), Toast.LENGTH_SHORT).show();
    }

    private void deletePending(int id) {
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try { widgetHost.deleteAppWidgetId(id); } catch (RuntimeException ignored) { }
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        pendingWidgetSlotIndex = -1;
    }

    private int configuredSlotCount() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int columns = Math.max(1, Math.min(4, prefs.getInt("desktop_widget_columns", 2)));
        int rows = Math.max(1, Math.min(3, prefs.getInt("desktop_widget_rows", 1)));
        return columns * rows;
    }

    private List<Integer> readSlots() {
        ArrayList<Integer> slots = new ArrayList<>();
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString("widget_slots", null);
        if (raw != null) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) slots.add(array.optInt(i, 0));
                return slots;
            } catch (Exception ignored) { }
        }
        for (int id : readLegacyIds()) slots.add(id);
        return slots;
    }

    private List<Integer> readLegacyIds() {
        ArrayList<Integer> ids = new ArrayList<>();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_IDS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                int id = array.optInt(i, AppWidgetManager.INVALID_APPWIDGET_ID);
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID && id > 0 && !ids.contains(id)) ids.add(id);
            }
        } catch (Exception ignored) { }
        return ids;
    }

    private void saveSlots(List<Integer> slots) {
        JSONArray slotArray = new JSONArray();
        JSONArray idsArray = new JSONArray();
        for (Integer id : slots) {
            slotArray.put(id == null ? 0 : id);
            if (id != null && id > 0) idsArray.put(id);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("widget_slots", slotArray.toString())
                .putString(KEY_IDS, idsArray.toString()).apply();
    }

    private void normalizeSlotsToGrid() {
        List<Integer> slots = readSlots();
        int count = configuredSlotCount();
        ArrayList<Integer> normalized = new ArrayList<>();
        for (int i = 0; i < count; i++) normalized.add(0);
        ArrayList<Integer> overflow = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            int id = slots.get(i);
            if (id <= 0) continue;
            if (i < count && normalized.get(i) == 0) normalized.set(i, id);
            else overflow.add(id);
        }
        for (int id : overflow) {
            int target = normalized.indexOf(0);
            if (target < 0) normalized.add(id);
            else normalized.set(target, id);
        }
        saveSlots(normalized);
    }

    private List<Integer> readIds() {
        ArrayList<Integer> ids = new ArrayList<>();
        for (int id : readSlots()) if (id > 0 && !ids.contains(id)) ids.add(id);
        return ids;
    }

    private void saveIds(List<Integer> ids) {
        JSONArray array = new JSONArray();
        for (Integer id : ids) array.put(id);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_IDS, array.toString()).apply();
    }

    private void renderWidgets() {
        if (widgetList == null) return;
        widgetList.removeAllViews();
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int columns = Math.max(1, Math.min(4, prefs.getInt("desktop_widget_columns", 2)));
        int rows = Math.max(1, Math.min(3, prefs.getInt("desktop_widget_rows", 1)));
        int count = columns * rows;
        widgetList.setColumnCount(columns);
        widgetList.setRowCount(rows);
        List<Integer> slots = readSlots();
        while (slots.size() < count) slots.add(0);
        for (int i = 0; i < count; i++) {
            int id = slots.get(i);
            AppWidgetProviderInfo info = id > 0 ? widgetManager.getAppWidgetInfo(id) : null;
            if (id > 0 && info == null) {
                try { widgetHost.deleteAppWidgetId(id); } catch (RuntimeException ignored) { }
                slots.set(i, 0);
                id = 0;
            }
            if (id > 0) addWidgetCard(id, info, i, columns);
            else addEmptyZone(i, columns);
        }
        saveSlots(slots);
        emptyState.setVisibility(View.GONE);
        widgetList.requestLayout();
    }

    private GridLayout.LayoutParams cellParams(int index, int columns) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(index / columns, 1f), GridLayout.spec(index % columns, 1f));
        lp.width = 0;
        lp.height = dp(240);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        return lp;
    }

    private void addEmptyZone(int index, int columns) {
        LinearLayout zone = new LinearLayout(this);
        zone.setOrientation(LinearLayout.VERTICAL);
        zone.setGravity(Gravity.CENTER);
        zone.setFocusable(true);
        zone.setClickable(true);
        zone.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x332D4654);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x664CA6AD);
        zone.setBackground(bg);
        TextView symbol = text("＋", 32, 0xFF78BFC5);
        symbol.setGravity(Gravity.CENTER);
        zone.addView(symbol);
        TextView label = text("分區 " + (index + 1) + "\n新增小工具", 13, 0xFFB9C7D5);
        label.setGravity(Gravity.CENTER);
        zone.addView(label);
        zone.setOnClickListener(v -> pickWidget(index));
        widgetList.addView(zone, cellParams(index, columns));
    }

    private void addWidgetCard(int id, AppWidgetProviderInfo info, int index, int columns) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF202936);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0x664CA6AD);
        card.setBackground(background);

        TextView label = text(String.valueOf(info.loadLabel(getPackageManager())), 14, Color.WHITE);
        label.setGravity(Gravity.CENTER);
        card.addView(label, new LinearLayout.LayoutParams(-1, dp(30)));

        AppWidgetHostView hostView = widgetHost.createView(this, id, info);
        hostView.setFocusable(true);
        hostView.setFocusableInTouchMode(false);
        int requestedHeight = info.minHeight > 0 ? Math.round(info.minHeight * density) : dp(120);
        int height = Math.max(dp(72), Math.min(dp(150), requestedHeight));
        card.addView(hostView, new LinearLayout.LayoutParams(-1, height));

        Button remove = button("移除");
        remove.setOnClickListener(v -> confirmRemove(id, info));
        card.addView(remove, new LinearLayout.LayoutParams(dp(100), dp(36)));
        widgetList.addView(card, cellParams(index, columns));
    }

    private void confirmRemove(int id, AppWidgetProviderInfo info) {
        new AlertDialog.Builder(this)
                .setTitle("移除小工具")
                .setMessage("要從 CYTV 桌面移除「" + info.loadLabel(getPackageManager()) + "」嗎？不會解除安裝原 APP。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> {
                    List<Integer> slots = readSlots();
                    for (int i = 0; i < slots.size(); i++) {
                        if (slots.get(i) == id) slots.set(i, 0);
                    }
                    saveSlots(slots);
                    try { widgetHost.deleteAppWidgetId(id); } catch (RuntimeException ignored) { }
                    renderWidgets();
                }).show();
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF16434B));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
