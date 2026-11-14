package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Independent, full-screen tool shelf. It deliberately does not reuse the Home app grid:
 * the desktop weather overlay remains on the underlying MainActivity and cannot cover this screen.
 */
public final class ToolCenterActivity extends Activity {
    private ToolRepository repository;
    private LinearLayout root;
    private GridLayout grid;
    private TextView empty;
    private TextView pageInfo;
    private Button previousPageButton;
    private Button nextPageButton;
    private int currentPage;
    private EditText searchInput;
    private String searchQuery = "";
    private String focusedToolKey = "";
    private String selectedCategory = "全部";
    private boolean restoringFocus;
    private ToolIconProvider iconProvider;
    private boolean packageReceiverRegistered;
    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (repository == null || root == null) return;
            repository.refreshInstalledEntries(getPackageManager());
            renderTools();
        }
    };
    private final float density() { return getResources().getDisplayMetrics().density; }
    private int dp(int value) { return Math.round(value * density()); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new ToolRepository(this);
        currentPage = repository.getCurrentPage();
        iconProvider = new ToolIconProvider(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        registerPackageChangeReceiver();
        if (repository != null && root != null) {
            repository.refreshInstalledEntries(getPackageManager());
            renderTools();
        }
    }

    @Override protected void onPause() {
        unregisterPackageChangeReceiver();
        super.onPause();
    }

    private void registerPackageChangeReceiver() {
        if (packageReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageChanges, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageChanges, filter);
        }
        packageReceiverRegistered = true;
    }

    private void unregisterPackageChangeReceiver() {
        if (!packageReceiverRegistered) return;
        try {
            unregisterReceiver(packageChanges);
        } catch (IllegalArgumentException ignored) {
            // Defensive: lifecycle callbacks can race with receiver teardown on some TV firmware.
        }
        packageReceiverRegistered = false;
    }

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(20), dp(28), dp(24));
        root.setBackgroundColor(0xFF10141C);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("工具中心", 27, Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button add = actionButton("新增工具 +");
        add.setOnClickListener(v -> showInstalledApps());
        header.addView(add, new LinearLayout.LayoutParams(dp(190), dp(48)));
        Button settings = actionButton("欄列設定");
        settings.setOnClickListener(v -> showLayoutSettings());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(150), dp(48));
        settingsLp.leftMargin = dp(12);
        header.addView(settings, settingsLp);
        root.addView(header);

        TextView hint = text("只加入本機已安裝的應用程式；移除工具捷徑不會解除安裝 APP。", 13, 0xFF9AA6B5);
        hint.setPadding(0, dp(2), 0, dp(10));
        root.addView(hint);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(0xFF8995A4);
        searchInput.setHint("搜尋工具名稱");
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable searchBackground = new GradientDrawable();
        searchBackground.setColor(0xFF202936);
        searchBackground.setCornerRadius(dp(12));
        searchBackground.setStroke(dp(1), 0x334CA6AD);
        searchInput.setBackground(searchBackground);
        root.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout.LayoutParams searchMargin = (LinearLayout.LayoutParams) searchInput.getLayoutParams();
        searchMargin.bottomMargin = dp(12);
        searchInput.setLayoutParams(searchMargin);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim();
                currentPage = 0;
                repository.setCurrentPage(0);
                renderTools();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        Button categoryButton = actionButton("分類：全部");
        categoryButton.setOnClickListener(v -> showCategoryFilter(categoryButton));
        LinearLayout.LayoutParams categoryLp = new LinearLayout.LayoutParams(dp(190), dp(44));
        categoryLp.bottomMargin = dp(10);
        root.addView(categoryButton, categoryLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        grid = new GridLayout(this);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout pager = new LinearLayout(this);
        pager.setGravity(Gravity.CENTER);
        pager.setOrientation(LinearLayout.HORIZONTAL);
        previousPageButton = actionButton("上一頁");
        previousPageButton.setOnClickListener(v -> changePage(-1));
        pager.addView(previousPageButton, new LinearLayout.LayoutParams(dp(150), dp(44)));
        pageInfo = text("1 / 1", 15, Color.WHITE);
        pageInfo.setGravity(Gravity.CENTER);
        pager.addView(pageInfo, new LinearLayout.LayoutParams(dp(130), dp(44)));
        nextPageButton = actionButton("下一頁");
        nextPageButton.setOnClickListener(v -> changePage(1));
        pager.addView(nextPageButton, new LinearLayout.LayoutParams(dp(150), dp(44)));
        LinearLayout.LayoutParams pagerLp = new LinearLayout.LayoutParams(-1, dp(50));
        pagerLp.topMargin = dp(6);
        root.addView(pager, pagerLp);

        empty = text("目前尚未新增工具\n請選擇右上方「新增工具 +」", 18, 0xFFADB8C6);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new LinearLayout.LayoutParams(-1, dp(110)));
        setContentView(root);
        renderTools();
    }

    private void renderTools() {
        if (grid == null) return;
        grid.removeAllViews();
        int columns = repository.getColumns();
        grid.setColumnCount(columns);
        List<ToolRepository.Tool> allTools = repository.getTools();
        List<ToolRepository.Tool> tools = new ArrayList<>();
        String query = searchQuery.toLowerCase(Locale.ROOT);
        for (ToolRepository.Tool tool : allTools) {
            if ((selectedCategory.equals("全部") || selectedCategory.equals(tool.category))
                    && (query.isEmpty() || tool.label.toLowerCase(Locale.ROOT).contains(query)
                    || tool.packageName.toLowerCase(Locale.ROOT).contains(query))) {
                tools.add(tool);
            }
        }
        empty.setVisibility(tools.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(allTools.isEmpty()
                ? "目前尚未新增工具\n請選擇右上方「新增工具 +」"
                : "找不到符合的工具");
        int rows = repository.getRows();
        int pageSize = Math.max(1, columns * rows);
        int pageCount = Math.max(1, (tools.size() + pageSize - 1) / pageSize);
        currentPage = Math.max(0, Math.min(currentPage, pageCount - 1));
        repository.setCurrentPage(currentPage);
        int fromIndex = Math.min(currentPage * pageSize, tools.size());
        int toIndex = Math.min(fromIndex + pageSize, tools.size());
        List<ToolRepository.Tool> pageTools = new ArrayList<>(tools.subList(fromIndex, toIndex));
        if (pageInfo != null) pageInfo.setText((currentPage + 1) + " / " + pageCount);
        if (previousPageButton != null) previousPageButton.setEnabled(currentPage > 0);
        if (nextPageButton != null) nextPageButton.setEnabled(currentPage < pageCount - 1);
        int screenWidth = getResources().getDisplayMetrics().widthPixels - dp(56);
        int cellWidth = Math.max(dp(64), (screenWidth - dp(12 * columns)) / columns);
        // Keep every configured row inside the available TV viewport, including 720p panels.
        // The ScrollView remains a safety net for unusually large fonts or accessibility scaling.
        int availableHeight = Math.max(dp(320),
                getResources().getDisplayMetrics().heightPixels - dp(300));
        int cellHeight = Math.max(dp(64), availableHeight / rows - dp(12));
        for (ToolRepository.Tool tool : pageTools) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setPadding(dp(8), dp(10), dp(8), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF202936);
            bg.setCornerRadius(dp(16));
            bg.setStroke(dp(1), 0x22FFFFFF);
            tile.setBackground(bg);
            tile.setOnFocusChangeListener((view, focused) -> {
                if (focused) {
                    focusedToolKey = tool.key();
                    repository.setLastFocusedTool(focusedToolKey);
                }
                GradientDrawable state = new GradientDrawable();
                state.setColor(focused ? 0xFF263D49 : 0xFF202936);
                state.setCornerRadius(dp(16));
                state.setStroke(dp(focused ? 2 : 1), focused ? 0xFF4CA6AD : 0x22FFFFFF);
                view.setBackground(state);
            });

            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageDrawable(iconProvider.load(tool, repository.getIconMode()));
            int iconSize = dp(54 * repository.getIconSize() / 100);
            tile.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
            if (repository.isShowNames()) {
                TextView label = text(tool.label, 14, Color.WHITE);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setPadding(0, dp(8), 0, 0);
                tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
            }
            tile.setTag(tool.key());
            tile.setOnClickListener(v -> launch(tool));
            tile.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle(tool.label)
                        .setItems(new String[]{"移除工具捷徑", "上移", "下移", "變更分類"}, (dialog, which) -> {
                            if (which == 0) repository.remove(tool.packageName, tool.activityName);
                            else if (which == 1) repository.move(tool.packageName, tool.activityName, -1);
                            else if (which == 2) repository.move(tool.packageName, tool.activityName, 1);
                            else changeToolCategory(tool);
                            renderTools();
                        }).show();
                return true;
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cellWidth;
            lp.height = cellHeight;
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            grid.addView(tile, lp);
        }
        String restoreKey = !focusedToolKey.isEmpty()
                ? focusedToolKey : repository.getLastFocusedTool();
        if (!restoringFocus && !pageTools.isEmpty()) {
            restoringFocus = true;
            grid.post(() -> {
                View target = null;
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View child = grid.getChildAt(i);
                    if (restoreKey.equals(child.getTag())) {
                        target = child;
                        break;
                    }
                }
                if (target == null && grid.getChildCount() > 0) target = grid.getChildAt(0);
                if (target != null) target.requestFocus();
                restoringFocus = false;
            });
        }
    }

    private void changePage(int offset) {
        int columns = repository.getColumns();
        int rows = repository.getRows();
        int pageSize = Math.max(1, columns * rows);
        int filteredCount = 0;
        String query = searchQuery.toLowerCase(Locale.ROOT);
        for (ToolRepository.Tool tool : repository.getTools()) {
            if ((selectedCategory.equals("全部") || selectedCategory.equals(tool.category))
                    && (query.isEmpty() || tool.label.toLowerCase(Locale.ROOT).contains(query)
                    || tool.packageName.toLowerCase(Locale.ROOT).contains(query))) {
                filteredCount++;
            }
        }
        int pageCount = Math.max(1, (filteredCount + pageSize - 1) / pageSize);
        int next = Math.max(0, Math.min(currentPage + offset, pageCount - 1));
        if (next == currentPage) return;
        currentPage = next;
        repository.setCurrentPage(currentPage);
        focusedToolKey = "";
        renderTools();
    }

    private void showCategoryFilter(Button button) {
        List<String> categories = repository.getCategories();
        ArrayList<String> options = new ArrayList<>();
        options.add("全部");
        options.addAll(categories);
        String[] labels = options.toArray(new String[0]);
        int selected = Math.max(0, options.indexOf(selectedCategory));
        new AlertDialog.Builder(this).setTitle("工具分類篩選")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    selectedCategory = labels[which];
                    button.setText("分類：" + selectedCategory);
                    dialog.dismiss();
                    renderTools();
                }).setNegativeButton("取消", null).show();
    }

    private void changeToolCategory(ToolRepository.Tool tool) {
        List<String> categories = repository.getCategories();
        ArrayList<String> options = new ArrayList<>(categories);
        options.add("＋ 新增自訂分類");
        String[] labels = options.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("設定工具分類")
                .setItems(labels, (dialog, which) -> {
                    if (which == labels.length - 1) {
                        EditText input = new EditText(this);
                        input.setSingleLine(true);
                        input.setHint("輸入分類名稱");
                        new AlertDialog.Builder(this).setTitle("新增自訂分類")
                                .setView(input)
                                .setPositiveButton("儲存", (d, w) -> {
                                    String value = input.getText().toString().trim();
                                    if (!value.isEmpty()) {
                                        repository.setCategory(tool.packageName, tool.activityName, value);
                                        renderTools();
                                    }
                                }).setNegativeButton("取消", null).show();
                    } else {
                        repository.setCategory(tool.packageName, tool.activityName, labels[which]);
                        renderTools();
                    }
                }).show();
    }

    private void showInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>();
        addUnique(found, pm.queryIntentActivities(leanback, 0));
        addUnique(found, pm.queryIntentActivities(launcher, 0));
        final List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo info : found) {
            if (info.activityInfo == null || !info.activityInfo.exported
                    || getPackageName().equals(info.activityInfo.packageName)) continue;
            apps.add(info);
        }
        Collections.sort(apps, (a, b) -> String.valueOf(a.loadLabel(pm))
                .compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        if (apps.isEmpty()) {
            Toast.makeText(this, "沒有找到可加入的應用程式", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            ResolveInfo info = apps.get(i);
            labels[i] = String.valueOf(info.loadLabel(pm));
            checked[i] = repository.contains(info.activityInfo.packageName, info.activityInfo.name);
        }
        boolean[] original = checked.clone();
        new AlertDialog.Builder(this).setTitle("新增工具")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("完成", (dialog, which) -> {
                    for (int i = 0; i < apps.size(); i++) {
                        ResolveInfo info = apps.get(i);
                        if (checked[i] && !original[i]) {
                            repository.add(info.activityInfo.packageName, info.activityInfo.name, labels[i]);
                        } else if (!checked[i] && original[i]) {
                            repository.remove(info.activityInfo.packageName, info.activityInfo.name);
                        }
                    }
                    renderTools();
                })
                .setNegativeButton("取消", (dialog, which) -> { })
                .show();
    }

    private void addUnique(List<ResolveInfo> target, List<ResolveInfo> source) {
        for (ResolveInfo item : source) {
            if (item.activityInfo == null) continue;
            boolean exists = false;
            for (ResolveInfo old : target) {
                if (old.activityInfo.packageName.equals(item.activityInfo.packageName)
                        && old.activityInfo.name.equals(item.activityInfo.name)) { exists = true; break; }
            }
            if (!exists) target.add(item);
        }
    }

    private void showLayoutSettings() {
        new AlertDialog.Builder(this).setTitle("工具中心設定")
                .setItems(new String[]{"欄數：" + repository.getColumns(), "列數：" + repository.getRows(),
                        "圖示風格：" + iconModeLabel(repository.getIconMode()),
                        "圖示大小：" + repository.getIconSize() + "%",
                        "顯示工具名稱：" + (repository.isShowNames() ? "開啟" : "關閉")}, (dialog, which) -> {
                    if (which == 0) chooseColumns();
                    else if (which == 1) chooseRows();
                    else if (which == 2) chooseIconMode();
                    else if (which == 3) chooseIconSize();
                    else toggleNames();
                }).show();
    }

    private String iconModeLabel(ToolRepository.IconMode mode) {
        if (mode == ToolRepository.IconMode.ORIGINAL) return "原始圖示";
        if (mode == ToolRepository.IconMode.CYTV_MINIMAL) return "CYTV 簡約風格";
        return "CYTV 智慧風格";
    }

    private void chooseIconMode() {
        String[] labels = {"CYTV 智慧風格", "原始圖示", "CYTV 簡約風格"};
        ToolRepository.IconMode[] modes = {ToolRepository.IconMode.CYTV_SMART,
                ToolRepository.IconMode.ORIGINAL, ToolRepository.IconMode.CYTV_MINIMAL};
        new AlertDialog.Builder(this).setTitle("選擇工具圖示風格")
                .setSingleChoiceItems(labels, repository.getIconMode().ordinal(), (dialog, which) -> {
                    repository.setIconMode(modes[which]);
                    dialog.dismiss();
                    renderTools();
                }).setNegativeButton("取消", null).show();
    }

    private void chooseIconSize() {
        String[] values = {"60%", "80%", "100%", "120%", "140%", "160%"};
        int[] sizes = {60, 80, 100, 120, 140, 160};
        int selected = 2;
        for (int i = 0; i < sizes.length; i++) if (sizes[i] == repository.getIconSize()) selected = i;
        new AlertDialog.Builder(this).setTitle("選擇圖示大小")
                .setSingleChoiceItems(values, selected, (dialog, which) -> {
                    repository.setIconSize(sizes[which]);
                    dialog.dismiss();
                    renderTools();
                }).setNegativeButton("取消", null).show();
    }

    private void toggleNames() {
        repository.setShowNames(!repository.isShowNames());
        renderTools();
    }

    private void chooseColumns() {
        String[] values = {"3 欄", "4 欄", "5 欄", "6 欄", "7 欄", "8 欄"};
        new AlertDialog.Builder(this).setTitle("選擇欄數")
                .setSingleChoiceItems(values, repository.getColumns() - 3, (d, which) -> {
                    repository.setColumns(which + 3); d.dismiss(); renderTools();
                }).setNegativeButton("取消", null).show();
    }

    private void chooseRows() {
        String[] values = {"1 列", "2 列", "3 列", "4 列", "5 列"};
        new AlertDialog.Builder(this).setTitle("選擇列數")
                .setSingleChoiceItems(values, repository.getRows() - 1, (d, which) -> {
                    repository.setRows(which + 1); d.dismiss(); renderTools();
                }).setNegativeButton("取消", null).show();
    }

    private void launch(ToolRepository.Tool tool) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(tool.packageName, tool.activityName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { startActivity(intent); }
        catch (Exception e) { Toast.makeText(this, "無法開啟此工具，請確認 APP 是否仍已安裝", Toast.LENGTH_SHORT).show(); }
    }

    private Button actionButton(String value) {
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
}
