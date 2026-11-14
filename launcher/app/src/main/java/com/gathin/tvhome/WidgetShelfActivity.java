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
    private LinearLayout widgetList;
    private TextView emptyState;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
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
        add.setOnClickListener(v -> pickWidget());
        header.addView(add, new LinearLayout.LayoutParams(dp(210), dp(48)));

        Button back = button("返回桌面");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(170), dp(48));
        backLp.leftMargin = dp(12);
        header.addView(back, backLp);
        page.addView(header);

        TextView hint = text("選擇電視已安裝 APP 提供的小工具。小工具內容由原 APP 提供，CYTV 不會修改第三方 APP。", 14, 0xFF9AA6B5);
        hint.setPadding(0, dp(4), 0, dp(16));
        page.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        widgetList = new LinearLayout(this);
        widgetList.setOrientation(LinearLayout.VERTICAL);
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

    private void pickWidget() {
        final List<AppWidgetProviderInfo> providers = widgetManager.getInstalledProviders();
        final ArrayList<AppWidgetProviderInfo> thirdParty = new ArrayList<>();
        final ArrayList<String> labels = new ArrayList<>();
        for (AppWidgetProviderInfo info : providers) {
            if (info == null || info.provider == null
                    || getPackageName().equals(info.provider.getPackageName())) continue;
            thirdParty.add(info);
            CharSequence label = info.loadLabel(getPackageManager());
            String name = label == null ? info.provider.getClassName() : label.toString();
            labels.add(name);
        }
        if (thirdParty.isEmpty()) {
            Toast.makeText(this, "目前沒有找到已安裝的第三方小工具提供者", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("選擇第三方小工具")
                .setItems(labels.toArray(new String[0]), (dialog, which) ->
                        bindProvider(thirdParty.get(which)))
                .setNegativeButton("取消", null)
                .show();
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
        List<Integer> ids = readIds();
        if (!ids.contains(id)) ids.add(id);
        saveIds(ids);
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        renderWidgets();
        Toast.makeText(this, "已新增：" + info.loadLabel(getPackageManager()), Toast.LENGTH_SHORT).show();
    }

    private void deletePending(int id) {
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try { widgetHost.deleteAppWidgetId(id); } catch (RuntimeException ignored) { }
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    }

    private List<Integer> readIds() {
        ArrayList<Integer> ids = new ArrayList<>();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_IDS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                int id = array.optInt(i, AppWidgetManager.INVALID_APPWIDGET_ID);
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID && !ids.contains(id)) ids.add(id);
            }
        } catch (Exception ignored) {
            // Corrupt preference data is treated as an empty widget shelf.
        }
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
        List<Integer> ids = readIds();
        ArrayList<Integer> valid = new ArrayList<>();
        for (int id : ids) {
            AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
            if (info == null) {
                try { widgetHost.deleteAppWidgetId(id); } catch (RuntimeException ignored) { }
                continue;
            }
            valid.add(id);
            addWidgetCard(id, info);
        }
        if (valid.size() != ids.size()) saveIds(valid);
        emptyState.setVisibility(valid.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addWidgetCard(int id, AppWidgetProviderInfo info) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF202936);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), 0x334CA6AD);
        card.setBackground(background);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(String.valueOf(info.loadLabel(getPackageManager())), 16, Color.WHITE);
        bar.addView(label, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button remove = button("移除");
        remove.setOnClickListener(v -> confirmRemove(id, info));
        bar.addView(remove, new LinearLayout.LayoutParams(dp(100), dp(40)));
        card.addView(bar);

        AppWidgetHostView hostView = widgetHost.createView(this, id, info);
        hostView.setFocusable(true);
        hostView.setFocusableInTouchMode(false);
        int requestedHeight = info.minHeight > 0 ? Math.round(info.minHeight * density) : dp(180);
        int height = Math.max(dp(120), Math.min(dp(360), requestedHeight));
        LinearLayout.LayoutParams widgetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        widgetLp.topMargin = dp(8);
        card.addView(hostView, widgetLp);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(14);
        widgetList.addView(card, cardLp);
    }

    private void confirmRemove(int id, AppWidgetProviderInfo info) {
        new AlertDialog.Builder(this)
                .setTitle("移除小工具")
                .setMessage("要從 CYTV 桌面移除「" + info.loadLabel(getPackageManager()) + "」嗎？不會解除安裝原 APP。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> {
                    List<Integer> ids = readIds();
                    ids.remove(Integer.valueOf(id));
                    saveIds(ids);
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
