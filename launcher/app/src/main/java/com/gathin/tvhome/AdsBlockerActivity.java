package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AdsBlockerActivity extends Activity {
    private static final int VPN_REQUEST = 7412;
    private static final String PREFS = "ad_filter";
    private static final String BLOCK = "block_domains";
    private static final String ALLOW = "allow_domains";
    private SharedPreferences prefs;
    private LinearLayout content;
    private boolean showingDnsLogs = false;
    private TextView status;
    private Button toggle;
    private Button autoDnsChoice;
    private Button manualDnsChoice;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRefresh = new Runnable() {
        @Override public void run() {
            if (content == null) return;
            refreshVpnStatus();
            statusHandler.postDelayed(this, 1000L);
        }
    };

    // Bundled starter rules. Allow rules take precedence over block rules.
    private static final String DEFAULT_BLOCK =
            "doubleclick.net\ngooglesyndication.com\ngoogleadservices.com\nadservice.google.com\n" +
            "pagead2.googlesyndication.com\napp-measurement.com\nadcolony.com\nunityads.unity3d.com\n" +
            "applovin.com\nmoatads.com\nadsrvr.org\n" +
            "googleads.g.doubleclick.net\nsecurepubads.g.doubleclick.net\npubads.g.doubleclick.net\n" +
            "av1.xdrig.com\nme.xdrig.com\nwww.xdrig.com\n*.xdrig.com\n" +
            "footprints-pa.googleapis.com\nwww.googleadservices.com\n" +
            "p-app.dhub.hinet.net\nads.aralego.com\nadsv.svc.litv.tv\nacs2.svc.litv.tv\n" +
            "*.svc.litv.tv\n";
    private static final String DEFAULT_ALLOW =
            "vid.dbokutv.com\nfirebaselogging-pa.googleapis.com\npic.dbokutv.com\nevents.ads.vungle.com\np-cdnstatic.svc.litv.tv\n";
    private static final String DEFAULT_RULES_VERSION = "6";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (AdFilterDevicePolicy.isToshiba65U7000Vs()) {
            prefs.edit().putBoolean("enabled", false).apply();
            // Ensure a previously running local DNS VPN is torn down when this screen opens.
            stopService(new Intent(this, AdsFilterVpnService.class));
        }
        installBundledRules();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.rgb(20, 21, 27));
        getWindow().setNavigationBarColor(Color.rgb(20, 21, 27));
        buildUi();
    }

    private void installBundledRules() {
        if (DEFAULT_RULES_VERSION.equals(prefs.getString("bundled_rules_version", ""))) return;
        LinkedHashSet<String> block = new LinkedHashSet<>(rules(BLOCK));
        // These shared Google API/video hosts also carry playback manifests, player APIs and media.
        // DNS-only filtering cannot distinguish ad URLs from video URLs on the same host, unlike uBlock.
        // Remove the broad rules from previously saved defaults to prevent breaking video playback.
        block.remove("googlevideo.com");
        block.remove("rr2---sn-un57sne7.googlevideo.com");
        block.remove("eligibility-panelresearch.googlevideo.com");
        block.remove("www.googlevideo.com");
        block.remove("googleapis.com");
        block.remove("www.googleapis.com");
        block.remove("youtubei.googleapis.com");
        // This playback CDN host must be explicitly allowed even though *.svc.litv.tv is blocked.
        block.remove("p-cdnstatic.svc.litv.tv");
        LinkedHashSet<String> allow = new LinkedHashSet<>(rules(ALLOW));
        allow.add("p-cdnstatic.svc.litv.tv");
        for (String item : DEFAULT_BLOCK.split("\\s+")) {
            String domain = normalize(item);
            if (!domain.isEmpty()) block.add(domain);
        }
        for (String item : DEFAULT_ALLOW.split("\\s+")) {
            String domain = normalize(item);
            if (!domain.isEmpty()) allow.add(domain);
        }
        saveRules(BLOCK, block);
        saveRules(ALLOW, allow);
        prefs.edit().putString("bundled_rules_version", DEFAULT_RULES_VERSION).apply();
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) {
            refresh();
            statusHandler.removeCallbacks(statusRefresh);
            statusHandler.post(statusRefresh);
        }
    }

    @Override protected void onPause() {
        statusHandler.removeCallbacks(statusRefresh);
        super.onPause();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable bg(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(18));
        if (stroke != 0) d.setStroke(dp(2), stroke);
        return d;
    }
    private android.graphics.drawable.Drawable focusBg(int normal, int focused) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, bg(focused, Color.rgb(0, 210, 205)));
        states.addState(new int[]{android.R.attr.state_selected}, bg(Color.rgb(22, 75, 87), Color.rgb(0, 210, 205)));
        states.addState(new int[]{android.R.attr.state_pressed}, bg(focused, Color.WHITE));
        states.addState(new int[]{}, bg(normal, Color.TRANSPARENT));
        return states;
    }
    private void keepFocusedVisible(View view) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) v.post(() -> v.requestRectangleOnScreen(
                    new android.graphics.Rect(0, 0, v.getWidth(), v.getHeight()), true));
        });
    }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextColor(Color.WHITE);
        v.setTextSize(size); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(18);
        b.setAllCaps(false); b.setFocusable(true); b.setMinHeight(dp(54));
        b.setBackground(focusBg(Color.rgb(38, 50, 68), Color.rgb(53, 69, 89)));
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        keepFocusedVisible(b);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void buildUi() {
        showingDnsLogs = false;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(15, 24, 39));
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(84), dp(22), dp(84), dp(30));
        scroll.addView(content);
        setContentView(scroll);
        TextView title = label("阻擋廣告", 30); title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.CENTER);
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView desc = label("透過本機 DNS 過濾常見廣告網域。首次啟用時，Android 會詢問是否允許 VPN 連線。", 17);
        desc.setTextColor(Color.LTGRAY); desc.setGravity(Gravity.CENTER);
        content.addView(desc, new LinearLayout.LayoutParams(-1, dp(68)));
        status = label("", 19); status.setPadding(dp(16), 0, dp(16), 0); status.setBackground(bg(Color.rgb(32, 43, 59), 0));
        content.addView(status, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView networkModeTitle = label("網路連線模式", 19);
        networkModeTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams modeTitleParams = new LinearLayout.LayoutParams(-1, dp(42));
        modeTitleParams.topMargin = dp(12);
        content.addView(networkModeTitle, modeTitleParams);
        TextView modeHint = label("建議使用自動 DNS，由目前連線提供 DNS。", 15);
        modeHint.setTextColor(Color.LTGRAY);
        content.addView(modeHint, new LinearLayout.LayoutParams(-1, dp(34)));
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.VERTICAL);
        autoDnsChoice = button("自動（使用目前網路 DNS）", () -> setDnsMode("auto"));
        manualDnsChoice = button("手動（固定 DNS：1.1.1.1）", () -> setDnsMode("manual"));
        LinearLayout.LayoutParams modeButtonParams = new LinearLayout.LayoutParams(-1, dp(60));
        modeButtonParams.bottomMargin = dp(8);
        modeRow.addView(autoDnsChoice, modeButtonParams);
        modeRow.addView(manualDnsChoice, new LinearLayout.LayoutParams(-1, dp(60)));
        content.addView(modeRow, new LinearLayout.LayoutParams(-1, dp(130)));
        toggle = button("", this::toggleFilter);
        toggle.setTextSize(21);
        toggle.setTypeface(null, Typeface.BOLD);
        StateListDrawable primaryStates = new StateListDrawable();
        GradientDrawable primaryFocused = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(0, 190, 190), Color.rgb(0, 145, 155)});
        primaryFocused.setCornerRadius(dp(18));
        primaryFocused.setStroke(dp(2), Color.rgb(100, 240, 230));
        GradientDrawable primaryNormal = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(0, 150, 160), Color.rgb(0, 115, 130)});
        primaryNormal.setCornerRadius(dp(18));
        primaryNormal.setStroke(dp(1), Color.rgb(0, 190, 190));
        primaryStates.addState(new int[]{android.R.attr.state_focused}, primaryFocused);
        primaryStates.addState(new int[]{}, primaryNormal);
        toggle.setBackground(primaryStates);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(70)); tp.topMargin = dp(14);
        content.addView(toggle, tp);
        Button dnsHistory = button("查看 DNS 紀錄", this::showDnsLogs);
        LinearLayout.LayoutParams logButtonParams = new LinearLayout.LayoutParams(-1, dp(56));
        logButtonParams.topMargin = dp(8);
        content.addView(dnsHistory, logButtonParams);
        TextView note = label("注意：YouTube 影片廣告、加密 DNS／DoH，以及與影片共用網域的廣告，可能無法由 DNS 規則攔截。", 15);
        note.setTextColor(Color.rgb(255, 205, 120)); note.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(58)); np.topMargin = dp(10);
        content.addView(note, np);
        addSection("封鎖規則", "", BLOCK);
        addSection("允許規則", "", ALLOW);
        Button back = button("返回", this::finish);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(58)); bp.topMargin = dp(20);
        content.addView(back, bp);
        refresh();
    }
    private void addSection(String heading, String hint, String key) {
        TextView h = label(heading, 22); h.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(46)); hp.topMargin = dp(20);
        content.addView(h, hp);
        if (hint != null && !hint.trim().isEmpty()) {
            TextView sub = label(hint, 15); sub.setTextColor(Color.LTGRAY);
            content.addView(sub, new LinearLayout.LayoutParams(-1, dp(32)));
        }
        // Both block and allow rules use the same live list, backed by persisted SharedPreferences.
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setTag("list_" + key);
        content.addView(list);
        Button add = button("＋ 新增網域", () -> promptDomain(key));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(52)); ap.topMargin = dp(8);
        content.addView(add, ap);
    }
    private boolean enabled() { return prefs.getBoolean("enabled", false); }
    private void refreshVpnStatus() {
        if (status == null || toggle == null) return;
        if (AdFilterDevicePolicy.isToshiba65U7000Vs()) {
            prefs.edit().putBoolean("enabled", false).apply();
            status.setText("●  TOSHIBA 65U7000VS：已停用 DNS VPN（保護有線網路）");
            toggle.setText("此機型已停用 DNS VPN");
            toggle.setEnabled(false);
            autoDnsChoice.setEnabled(false);
            manualDnsChoice.setEnabled(false);
            return;
        }
        boolean requested = enabled();
        boolean active = AdsFilterVpnService.isTunnelActive();
        if (active) {
            status.setText("●  過濾狀態：已啟用");
            toggle.setText("停用廣告阻擋");
        } else if (requested) {
            status.setText("◌  過濾狀態：啟動中／等待網路");
            toggle.setText("取消啟用廣告阻擋");
        } else {
            status.setText("○  過濾狀態：已關閉");
            toggle.setText("啟用廣告阻擋");
        }
        toggle.setEnabled(true);
        autoDnsChoice.setEnabled(true);
        manualDnsChoice.setEnabled(true);
    }
    private void refresh() {
        if (status == null) return;
        refreshVpnStatus();
        boolean auto = "auto".equals(prefs.getString("dns_mode", "auto"));
        autoDnsChoice.setText(auto ? "✓ 自動（Auto）" : "自動（Auto）");
        manualDnsChoice.setText(auto ? "手動（固定 DNS）" : "✓ 手動（固定 DNS）");
        autoDnsChoice.setSelected(auto);
        manualDnsChoice.setSelected(!auto);
        renderRules(BLOCK); renderRules(ALLOW);
    }
    private void setDnsMode(String mode) {
        if (AdFilterDevicePolicy.isToshiba65U7000Vs()) {
            Toast.makeText(this, "此機型已停用 DNS VPN，不會變更 DNS 或路由", Toast.LENGTH_SHORT).show();
            return;
        }
        String current = prefs.getString("dns_mode", "auto");
        if (mode.equals(current)) return;
        prefs.edit().putString("dns_mode", mode).apply();
        if (enabled()) {
            Intent refreshIntent = new Intent(this, AdsFilterVpnService.class);
            refreshIntent.setAction(AdsFilterVpnService.ACTION_REFRESH_NETWORK);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(refreshIntent); else startService(refreshIntent);
        }
        refresh();
        Toast.makeText(this, mode.equals("auto")
                ? "已切換自動 DNS 協商" : "已切換固定 DNS（1.1.1.1）", Toast.LENGTH_SHORT).show();
    }

    private void renderRules(String key) {
        LinearLayout list = content.findViewWithTag("list_" + key);
        if (list == null) return;
        list.removeAllViews();
        for (String domain : rules(key)) {
            // Bundled defaults are active internally but intentionally hidden from this editable page.
            if (isBundledRule(key, domain)) continue;
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(8), 0); row.setBackground(bg(Color.rgb(32, 43, 59), 0));
            TextView name = label(domain, 17); row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button del = new Button(this); del.setText("刪除"); del.setTextSize(14); del.setAllCaps(false);
            del.setTextColor(Color.WHITE); del.setFocusable(true); del.setMinHeight(dp(48));
            del.setBackground(focusBg(Color.rgb(85,47,49), Color.rgb(126,58,62)));
            keepFocusedVisible(del);
            del.setOnClickListener(v -> { LinkedHashSet<String> set = new LinkedHashSet<>(rules(key)); set.remove(domain); saveRules(key, set); refresh(); });
            row.addView(del, new LinearLayout.LayoutParams(dp(76), dp(42)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(50)); rp.topMargin = dp(5);
            list.addView(row, rp);
        }
        if (list.getChildCount() == 0) {
            TextView empty = label("尚未新增自訂網域", 15); empty.setTextColor(Color.LTGRAY);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(40)));
        }
    }
    private boolean isBundledRule(String key, String domain) {
        String defaults = BLOCK.equals(key) ? DEFAULT_BLOCK : DEFAULT_ALLOW;
        for (String item : defaults.split("\\s+")) {
            String bundled = normalize(item);
            if (!bundled.isEmpty() && bundled.equals(domain)) return true;
        }
        return false;
    }

    private boolean isLockedRule(String key, String domain) {
        if (BLOCK.equals(key)) {
            return "googleads.g.doubleclick.net".equals(domain)
                    || "securepubads.g.doubleclick.net".equals(domain);
        }
        if (ALLOW.equals(key)) {
            return "vid.dbokutv.com".equals(domain)
                    || "firebaselogging-pa.googleapis.com".equals(domain)
                    || "pic.dbokutv.com".equals(domain)
                     || "events.ads.vungle.com".equals(domain)
                    || "p-cdnstatic.svc.litv.tv".equals(domain);
        }
        return false;
    }

    private Set<String> rules(String key) {
        String raw = prefs.getString(key, "");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) for (String s : raw.split("\\s+")) {
            String d = normalize(s); if (!d.isEmpty()) out.add(d);
        }
        return out;
    }
    private void saveRules(String key, Set<String> set) {
        StringBuilder b = new StringBuilder(); for (String d : set) b.append(d).append('\n');
        prefs.edit().putString(key, b.toString()).apply();
    }
    private String normalize(String value) {
        String d = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        while (d.startsWith(".")) d = d.substring(1);
        while (d.endsWith(".")) d = d.substring(0, d.length()-1);
        if (d.startsWith("http://")) d = d.substring(7);
        if (d.startsWith("https://")) d = d.substring(8);
        int slash = d.indexOf('/'); if (slash >= 0) d = d.substring(0, slash);
        if (d.startsWith("*.") ) d = d.substring(2);
        if (d.length() > 253 || d.isEmpty() || d.contains(" ") || d.contains(":")) return "";
        for (String part : d.split("\\.")) {
            if (part.isEmpty() || part.length() > 63 || !part.matches("[a-z0-9_-]+")) return "";
        }
        return d;
    }
    private void promptDomain(String key) {
        EditText input = new EditText(this);
        input.setId(View.generateViewId());
        input.setSingleLine(true);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setHint("例如 ads.example.com");
        input.setTextSize(18);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setBackground(focusBg(Color.rgb(39, 42, 50), Color.rgb(54, 59, 70)));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        // Match the folder window: navy-gray rounded surface with a subtle light border.
        panel.setPadding(dp(18), dp(12), dp(18), dp(8));
        GradientDrawable panelSurface = new GradientDrawable();
        panelSurface.setColor(Color.rgb(29, 33, 43));
        panelSurface.setCornerRadius(dp(22));
        panelSurface.setStroke(Math.max(1, dp(1)), 0x28FFFFFF);
        panel.setBackground(panelSurface);

        TextView heading = label(key.equals(BLOCK) ? "新增封鎖網域" : "新增允許網域", 22);
        heading.setTextColor(Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(dp(8), dp(2), dp(8), dp(14));
        panel.addView(heading, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(-1, dp(58));
        inputParams.leftMargin = dp(4);
        inputParams.rightMargin = dp(4);
        panel.addView(input, inputParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams =
                new LinearLayout.LayoutParams(-1, dp(64));
        actionsParams.topMargin = dp(8);
        panel.addView(actions, actionsParams);

        Button cancel = new Button(this);
        cancel.setId(View.generateViewId());
        cancel.setText("取消");
        Button add = new Button(this);
        add.setId(View.generateViewId());
        add.setText("新增");
        Button[] buttons = {cancel, add};
        for (Button button : buttons) {
            button.setAllCaps(false);
            button.setTextSize(17);
            button.setTextColor(Color.rgb(128, 201, 194));
            button.setMinHeight(dp(50));
            button.setMinWidth(dp(88));
            button.setPadding(dp(14), dp(8), dp(14), dp(8));
            button.setFocusable(true);
            button.setFocusableInTouchMode(true);
            button.setBackground(focusBg(Color.rgb(29, 33, 43), Color.rgb(54, 64, 77)));
        }
        LinearLayout.LayoutParams cancelParams =
                new LinearLayout.LayoutParams(dp(112), dp(54));
        cancelParams.rightMargin = dp(10);
        actions.addView(cancel, cancelParams);
        actions.addView(add, new LinearLayout.LayoutParams(dp(112), dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(panel)
                .create();

        cancel.setOnClickListener(v -> dialog.dismiss());
        add.setOnClickListener(v -> {
            String domain = normalize(input.getText().toString());
            if (domain.isEmpty()) {
                Toast.makeText(this, "網域格式不正確", Toast.LENGTH_LONG).show();
                input.requestFocus();
                return;
            }
            LinkedHashSet<String> set = new LinkedHashSet<>(rules(key));
            set.add(domain);
            saveRules(key, set);
            refresh();
            dialog.dismiss();
        });

        // Explicit D-pad routing: DOWN from the text field enters the button row;
        // UP returns to the field; LEFT/RIGHT moves between the two action buttons.
        input.setNextFocusDownId(cancel.getId());
        cancel.setNextFocusUpId(input.getId());
        add.setNextFocusUpId(input.getId());
        cancel.setNextFocusRightId(add.getId());
        add.setNextFocusLeftId(cancel.getId());
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                cancel.requestFocus();
                return true;
            }
            return false;
        });
        cancel.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                input.requestFocus();
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                add.requestFocus();
                return true;
            }
            return false;
        });
        add.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                input.requestFocus();
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                cancel.requestFocus();
                return true;
            }
            return false;
        });

        final android.widget.ImageView backdropBlur = installDnsBackdropBlur();
        dialog.setOnDismissListener(ignored -> {
            if (backdropBlur != null) {
                android.view.ViewParent parent = backdropBlur.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(backdropBlur);
                android.graphics.drawable.Drawable drawable = backdropBlur.getDrawable();
                if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                    android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                }
            }
        });
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.60f);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.88f),
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
            input.post(input::requestFocus);
        });
        dialog.show();
    }

    private void showDnsLogs() {
        // DNS 紀錄改為同一 Activity 的完整頁面，不再建立外層 AlertDialog。
        showingDnsLogs = true;
        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(false);
        pageScroll.setBackgroundColor(Color.rgb(15, 24, 39));
        pageScroll.setFocusable(false);
        pageScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(84), dp(22), dp(84), dp(30));
        pageScroll.addView(page);
        setContentView(pageScroll);

        TextView title = label("DNS 紀錄", 30);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        page.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView summary = label("", 19);
        summary.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, dp(46));
        summaryParams.topMargin = dp(8);
        page.addView(summary, summaryParams);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, dp(60));
        filterParams.bottomMargin = dp(10);
        page.addView(filters, filterParams);

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setTag("dns_log_rows");
        page.addView(rows, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

        final int[] selected = {0}; // 0 all, 1 blocked, 2 allowed
        Button[] filterButtons = new Button[3];
        String[] filterNames = {"全部紀錄", "只看阻擋", "只看允許"};
        for (int i = 0; i < filterNames.length; i++) {
            final int index = i;
            Button b = button(filterNames[i], () -> {
                selected[0] = index;
                for (int j = 0; j < filterButtons.length; j++) filterButtons[j].setSelected(j == index);
                updateDnsLogRows(summary, rows, selected[0]);
            });
            b.setSelected(i == 0);
            filterButtons[i] = b;
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(60), 1f);
            fp.rightMargin = dp(5);
            filters.addView(b, fp);
        }

        // 操作列放在紀錄清單之後，隨整頁內容自然往下排列，不固定、不遮擋紀錄。
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(62));
        actionsParams.topMargin = dp(12);
        Button clear = button("清除紀錄", () -> new AlertDialog.Builder(this)
                .setTitle("清除 DNS 紀錄")
                .setMessage("確定要清除所有 DNS 紀錄嗎？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> {
                    prefs.edit().remove("dns_logs").apply();
                    updateDnsLogRows(summary, rows, selected[0]);
                }).show());
        Button close = button("關閉", this::buildUi);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(58), 1f);
        ap.rightMargin = dp(8);
        actions.addView(clear, ap);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(58), 1f);
        cp.leftMargin = dp(8);
        actions.addView(close, cp);
        page.addView(actions, actionsParams);

        updateDnsLogRows(summary, rows, selected[0]);
        filterButtons[0].requestFocus();
    }

    /** Same blurred desktop backdrop and 60% dim layer used by the folder window. */
    private android.widget.ImageView installDnsBackdropBlur() {
        final android.view.View decor = getWindow() != null ? getWindow().getDecorView() : null;
        if (!(decor instanceof ViewGroup) || decor.getWidth() <= 0 || decor.getHeight() <= 0) return null;
        android.graphics.Bitmap source = null;
        android.graphics.Bitmap small = null;
        android.graphics.Bitmap blurred = null;
        android.renderscript.RenderScript rs = null;
        android.renderscript.Allocation input = null;
        android.renderscript.Allocation output = null;
        android.renderscript.ScriptIntrinsicBlur blur = null;
        try {
            int width = decor.getWidth();
            int height = decor.getHeight();
            source = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(source);
            decor.draw(canvas);
            int smallWidth = Math.max(1, width / 4);
            int smallHeight = Math.max(1, height / 4);
            small = android.graphics.Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true);
            source.recycle(); source = null;
            rs = android.renderscript.RenderScript.create(this);
            input = android.renderscript.Allocation.createFromBitmap(rs, small);
            output = android.renderscript.Allocation.createTyped(rs, input.getType());
            blur = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs));
            blur.setRadius(6.25f);
            blur.setInput(input);
            blur.forEach(output);
            output.copyTo(small);
            blurred = android.graphics.Bitmap.createScaledBitmap(small, width, height, true);
            android.widget.ImageView overlay = new android.widget.ImageView(this);
            overlay.setImageBitmap(blurred);
            overlay.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            overlay.setClickable(false);
            overlay.setFocusable(false);
            overlay.setFocusableInTouchMode(false);
            overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ((ViewGroup) decor).addView(overlay, new ViewGroup.LayoutParams(-1, -1));
            return overlay;
        } catch (Throwable unavailable) {
            if (blurred != null && !blurred.isRecycled()) blurred.recycle();
            return null;
        } finally {
            if (source != null && !source.isRecycled()) source.recycle();
            if (small != null && !small.isRecycled()) small.recycle();
            try { if (blur != null) blur.destroy(); } catch (Exception ignored) { }
            try { if (input != null) input.destroy(); } catch (Exception ignored) { }
            try { if (output != null) output.destroy(); } catch (Exception ignored) { }
            try { if (rs != null) rs.destroy(); } catch (Exception ignored) { }
        }
    }

    private void updateDnsLogRows(TextView summary, LinearLayout rows, int filter) {
        rows.removeAllViews();
        JSONArray logs;
        try { logs = new JSONArray(prefs.getString("dns_logs", "[]")); }
        catch (Exception e) { logs = new JSONArray(); }
        // 僅顯示最新 50 筆；較早紀錄仍保留在本機儲存中。
        // 先取最新 50 筆，再依所選類型篩選，避免篩選模式意外回溯舊紀錄。
        int startIndex = Math.max(0, logs.length() - 50);
        int visibleCount = logs.length() - startIndex;
        int blocked = 0, allowed = 0, failed = 0;
        for (int i = startIndex; i < logs.length(); i++) {
            JSONObject item = logs.optJSONObject(i);
            if (item == null) continue;
            String result = item.optString("result", "");
            if ("已阻擋".equals(result)) blocked++;
            else if ("允許".equals(result)) allowed++;
            else if ("查詢失敗".equals(result)) failed++;
        }
        summary.setText("顯示最新 " + visibleCount + " / " + logs.length() + " 筆　允許 " + allowed + "　阻擋 " + blocked + "　異常 " + failed);
        if (logs.length() == 0) {
            TextView empty = label("目前沒有 DNS 紀錄。啟用廣告阻擋並讓 APP 產生網路請求後，紀錄會顯示在這裡。", 17);
            empty.setTextColor(Color.LTGRAY);
            empty.setGravity(Gravity.CENTER);
            rows.addView(empty, new LinearLayout.LayoutParams(-1, dp(130)));
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.TAIWAN);
        for (int i = logs.length() - 1; i >= startIndex; i--) {
            JSONObject item = logs.optJSONObject(i);
            if (item == null) continue;
            String result = item.optString("result", "一般通過");
            if (filter == 1 && !"已阻擋".equals(result)) continue;
            if (filter == 2 && !"允許".equals(result)) continue;
            int color = "已阻擋".equals(result) ? Color.rgb(255, 105, 105)
                    : ("允許".equals(result) ? Color.rgb(90, 220, 145)
                    : ("查詢失敗".equals(result) ? Color.rgb(255, 195, 75) : Color.rgb(125, 190, 255)));
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(9), dp(14), dp(9));
            card.setBackground(bg(Color.rgb(39, 42, 50), 0));
            long time = item.optLong("time", 0);
            TextView head = label((time > 0 ? format.format(new Date(time)) : "--") + "    " + result, 16);
            head.setTypeface(null, Typeface.BOLD); head.setTextColor(color);
            card.addView(head, new LinearLayout.LayoutParams(-1, dp(30)));
            String domainValue = normalize(item.optString("domain", ""));
            LinearLayout domainRow = new LinearLayout(this);
            domainRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView domain = label(domainValue.isEmpty() ? "未知網域" : domainValue, 17);
            domain.setTypeface(null, Typeface.BOLD);
            domainRow.addView(domain, new LinearLayout.LayoutParams(0, dp(42), 1f));
            if (!domainValue.isEmpty()) {
                boolean blockedRule = rules(BLOCK).contains(domainValue);
                boolean allowedRule = rules(ALLOW).contains(domainValue);

                Button blockButton = button(blockedRule ? "已封鎖" : "加入封鎖",
                        () -> addDnsRuleFromLog(domainValue, BLOCK, summary, rows, filter));
                blockButton.setTextSize(13);
                blockButton.setPadding(dp(7), dp(3), dp(7), dp(3));
                blockButton.setBackground(focusBg(blockedRule ? Color.rgb(75, 65, 65) : Color.rgb(105, 48, 52),
                        Color.rgb(145, 65, 70)));
                domainRow.addView(blockButton, new LinearLayout.LayoutParams(-2, dp(40)));

                Button allowRuleButton = button(allowedRule ? "已允許" : "加入允許",
                        () -> addDnsRuleFromLog(domainValue, ALLOW, summary, rows, filter));
                allowRuleButton.setTextSize(13);
                allowRuleButton.setPadding(dp(7), dp(3), dp(7), dp(3));
                allowRuleButton.setBackground(focusBg(allowedRule ? Color.rgb(55, 75, 65) : Color.rgb(38, 105, 78),
                        Color.rgb(55, 145, 105)));
                LinearLayout.LayoutParams allowRuleParams = new LinearLayout.LayoutParams(-2, dp(40));
                allowRuleParams.leftMargin = dp(5);
                domainRow.addView(allowRuleButton, allowRuleParams);

                if ("已阻擋".equals(result)) {
                    boolean tempAllowed = temporaryAllowedDomains().contains(domainValue);
                    Button tempButton = button(tempAllowed ? "取消暫時允許" : "暫時允許",
                            () -> toggleTemporaryAllow(domainValue, summary, rows, filter));
                    tempButton.setTextSize(13);
                    tempButton.setPadding(dp(7), dp(3), dp(7), dp(3));
                    tempButton.setBackground(focusBg(tempAllowed ? Color.rgb(38, 105, 78) : Color.rgb(48, 83, 112),
                            tempAllowed ? Color.rgb(55, 145, 105) : Color.rgb(70, 120, 165)));
                    LinearLayout.LayoutParams tempParams = new LinearLayout.LayoutParams(-2, dp(40));
                    tempParams.leftMargin = dp(5);
                    domainRow.addView(tempButton, tempParams);
                }
            }
            card.addView(domainRow, new LinearLayout.LayoutParams(-1, dp(42)));
            TextView response = label("DNS 回應：" + item.optString("response", "無資料"), 14);
            response.setTextColor(Color.LTGRAY);
            card.addView(response, new LinearLayout.LayoutParams(-1, dp(28)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(7);
            rows.addView(card, rp);
        }
    }

    private void addDnsRuleFromLog(String domain, String targetKey, TextView summary, LinearLayout rows, int filter) {
        String oppositeKey = BLOCK.equals(targetKey) ? ALLOW : BLOCK;
        if (isLockedRule(targetKey, domain) || isLockedRule(oppositeKey, domain)) {
            Toast.makeText(this, "這是內建固定網域規則，無法從紀錄變更", Toast.LENGTH_SHORT).show();
            return;
        }
        LinkedHashSet<String> target = new LinkedHashSet<>(rules(targetKey));
        LinkedHashSet<String> opposite = new LinkedHashSet<>(rules(oppositeKey));
        boolean alreadyExists = target.contains(domain);
        target.add(domain);
        opposite.remove(domain);
        saveRules(targetKey, target);
        saveRules(oppositeKey, opposite);
        String action = BLOCK.equals(targetKey) ? "已加入封鎖" : "已加入允許";
        Toast.makeText(this, alreadyExists ? "此網域已在" + (BLOCK.equals(targetKey) ? "封鎖" : "允許") + "清單" : action + "：" + domain,
                Toast.LENGTH_SHORT).show();
        updateDnsLogRows(summary, rows, filter);
        if (enabled()) {
            Intent refreshIntent = new Intent(this, AdsFilterVpnService.class);
            refreshIntent.setAction(AdsFilterVpnService.ACTION_REFRESH_NETWORK);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(refreshIntent); else startService(refreshIntent);
        }
    }

    private Set<String> temporaryAllowedDomains() {
        String raw = prefs.getString("temporary_allow_domains", "");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) for (String item : raw.split("\\s+")) {
            String domain = normalize(item);
            if (!domain.isEmpty()) out.add(domain);
        }
        return out;
    }

    private void toggleTemporaryAllow(String domain, TextView summary, LinearLayout rows, int filter) {
        LinkedHashSet<String> set = new LinkedHashSet<>(temporaryAllowedDomains());
        boolean enabled = set.contains(domain);
        if (enabled) set.remove(domain); else set.add(domain);
        saveRules("temporary_allow_domains", set);
        Toast.makeText(this, enabled ? "已取消暫時允許：" + domain : "已暫時允許：" + domain,
                Toast.LENGTH_SHORT).show();
        updateDnsLogRows(summary, rows, filter);
    }

    private void toggleFilter() {
        if (AdFilterDevicePolicy.isToshiba65U7000Vs()) {
            prefs.edit().putBoolean("enabled", false).apply();
            refresh();
            return;
        }
        if (enabled()) {
            Intent stop = new Intent(this, AdsFilterVpnService.class);
            stop.setAction(AdsFilterVpnService.ACTION_STOP);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(stop); else startService(stop);
            prefs.edit().putBoolean("enabled", false).apply();
            refresh();
            return;
        }
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) startActivityForResult(prepare, VPN_REQUEST);
        else startFilter();
    }
    @Override public void onBackPressed() {
        if (showingDnsLogs) {
            buildUi();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VPN_REQUEST && result == RESULT_OK) startFilter();
    }
    private void startFilter() {
        if (AdFilterDevicePolicy.isToshiba65U7000Vs()) {
            prefs.edit().putBoolean("enabled", false).apply();
            refresh();
            return;
        }
        prefs.edit().putBoolean("enabled", true).apply();
        Intent service = new Intent(this, AdsFilterVpnService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        refresh();
    }
}
