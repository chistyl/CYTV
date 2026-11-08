package com.gathin.tvhome;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Full-screen, muted YouTube iframe background. User wallpaper preferences are never changed. */
final class YouTubeVideoCarousel {
    private static final String PREFS = "youtube_video_carousel";
    private static final String DEFAULT_ONE = "https://youtu.be/FaoCaZSzkOw?list=PLrXb4NDfS6KNOGMvv3RadaX317LQjm2X8";
    private static final String DEFAULT_TWO = "https://youtu.be/Um2JFPX-uH0";
    private final Context context;
    private final FrameLayout root;
    private final View[] backgroundLayers;
    private final int[] oldVisibility;
    private final SharedPreferences prefs;
    private final List<String> urls = new ArrayList<>();
    private final Random random = new Random();
    private WebView webView;
    private boolean active;
    private boolean pageReady;
    private boolean backgroundHidden;
    private int currentIndex;

    YouTubeVideoCarousel(Context context, FrameLayout root) {
        this.context = context;
        this.root = root;
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        backgroundLayers = new View[] {
                root.findViewById(R.id.sky), root.findViewById(R.id.heroArt),
                root.findViewById(R.id.heroScrim), root.findViewById(R.id.ambient)
        };
        oldVisibility = new int[backgroundLayers.length];
        loadUrls();
    }

    boolean isEnabled() { return prefs.getBoolean("enabled", false); }

    void start() {
        loadUrls();
        if (!isEnabled()) return;
        if (urls.isEmpty()) {
            prefs.edit().putBoolean("enabled", false).apply();
            active = false;
            restoreBackgroundLayers();
            if (webView != null) webView.setVisibility(View.GONE);
            return;
        }
        active = true;
        ensureWebView();
        webView.setVisibility(View.VISIBLE);
        hideBackgroundLayers();
        if (!pageReady) loadPlayer();
        else playAt(currentIndex);
        webView.onResume();
    }

    void pause() {
        active = false;
        if (webView != null) {
            webView.evaluateJavascript("if(window.ytPlayer&&ytPlayer.pauseVideo)ytPlayer.pauseVideo();", null);
            webView.onPause();
        }
    }

    void close() {
        active = false;
        if (webView != null) {
            root.removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
    }

    void showSettingsDialog() {
        loadUrls();
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(16));
        panel.setBackground(roundSurface(0xB32F333C, 20));
        TextView title = text("視頻輪播", 21, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, matchWrap());
        CheckBox enabled = new CheckBox(context);
        enabled.setText("啟用視頻輪播");
        enabled.setTextColor(Color.WHITE);
        enabled.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF80C9C2));
        enabled.setChecked(isEnabled());
        panel.addView(enabled, matchWrap());
        CheckBox randomPlay = new CheckBox(context);
        randomPlay.setText("隨機播放");
        randomPlay.setTextColor(Color.WHITE);
        randomPlay.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF80C9C2));
        randomPlay.setChecked(prefs.getBoolean("random", false));
        panel.addView(randomPlay, matchWrap());

        TextView hint = text("最多 5 筆 YouTube 影片連結，可修改並調整播放順序", 13, 0xFFCBD2DC);
        hint.setPadding(0, dp(8), 0, dp(8));
        panel.addView(hint, matchWrap());
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));

        List<EditText> fields = new ArrayList<>();
        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.removeAllViews();
            fields.clear();
            for (int i = 0; i < urls.size(); i++) {
                final int index = i;
                LinearLayout row = new LinearLayout(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                EditText edit = new EditText(context);
                edit.setSingleLine(true);
                edit.setText(urls.get(i));
                edit.setTextColor(Color.WHITE);
                edit.setHintTextColor(0xFF9BA6B5);
                edit.setHint("https://youtu.be/...");
                edit.setTextSize(13);
                edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                edit.setSelectAllOnFocus(true);
                row.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 1f));
                fields.add(edit);
                Button up = smallButton("↑");
                up.setEnabled(i > 0);
                up.setOnClickListener(v -> { syncFields(fields); Collections.swap(urls, index, index - 1); rebuild[0].run(); });
                row.addView(up);
                Button down = smallButton("↓");
                down.setEnabled(i < urls.size() - 1);
                down.setOnClickListener(v -> { syncFields(fields); Collections.swap(urls, index, index + 1); rebuild[0].run(); });
                row.addView(down);
                Button remove = smallButton("×");
                remove.setOnClickListener(v -> { syncFields(fields); urls.remove(index); rebuild[0].run(); });
                row.addView(remove);
                list.addView(row);
            }
        };
        rebuild[0].run();

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button add = smallButton("新增影片");
        add.setEnabled(urls.size() < 5);
        add.setOnClickListener(v -> {
            syncFields(fields);
            if (urls.size() < 5) urls.add("");
            rebuild[0].run();
        });
        actions.addView(add);
        panel.addView(actions, matchWrap());

        Dialog dialog = new Dialog(context);
        LinearLayout.LayoutParams buttonsParams = matchWrap();
        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.END);
        Button cancel = smallButton("取消");
        cancel.setOnClickListener(v -> dialog.dismiss());
        Button save = smallButton("儲存");
        save.setOnClickListener(v -> {
            syncFields(fields);
            List<String> valid = new ArrayList<>();
            for (String url : urls) {
                String trimmed = url == null ? "" : url.trim();
                if (trimmed.isEmpty()) continue;
                if (extractVideoId(trimmed) == null) {
                    Toast.makeText(context, "請輸入有效的 YouTube 影片連結", Toast.LENGTH_SHORT).show();
                    return;
                }
                valid.add(trimmed);
            }
            if (valid.size() > 5) return;
            if (enabled.isChecked() && valid.isEmpty()) {
                Toast.makeText(context, "啟用視頻輪播前，請至少新增一部有效影片", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONArray array = new JSONArray();
            for (String url : valid) array.put(url);
            prefs.edit().putString("urls", array.toString())
                    .putBoolean("enabled", enabled.isChecked())
                    .putBoolean("random", randomPlay.isChecked()).apply();
            urls.clear(); urls.addAll(valid);
            dialog.dismiss();
            pageReady = false; // Rebuild the iframe page so edited links and random mode take effect immediately.
            if (isEnabled()) start();
            else { pause(); restoreBackgroundLayers(); if (webView != null) webView.setVisibility(View.GONE); }
        });
        footer.addView(cancel);
        footer.addView(save);
        panel.addView(footer, buttonsParams);
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(80), dp(760)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(80), dp(760)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        cancel.requestFocus();
    }

    private void loadUrls() {
        urls.clear();
        String raw = prefs.getString("urls", null);
        if (raw == null) {
            urls.add(DEFAULT_ONE);
            urls.add(DEFAULT_TWO);
            JSONArray defaults = new JSONArray();
            defaults.put(DEFAULT_ONE); defaults.put(DEFAULT_TWO);
            prefs.edit().putString("urls", defaults.toString()).apply();
            return;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length() && urls.size() < 5; i++) {
                String value = array.optString(i, "").trim();
                if (extractVideoId(value) != null) urls.add(value);
            }
        } catch (Exception ignored) { }
    }

    private void ensureWebView() {
        if (webView != null) return;
        webView = new WebView(context);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        webView.setBackgroundColor(Color.BLACK);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (active) playAt(currentIndex);
            }
        });
        // WallpaperPlayer's image is child 0 when present; index 1 places video above it and below launcher UI.
        root.addView(webView, Math.min(1, root.getChildCount()), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void loadPlayer() {
        if (urls.isEmpty()) return;
        pageReady = false;
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"
                + "<style>html,body,#player{margin:0;width:100%;height:100%;overflow:hidden;background:#000}iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style>"
                + "</head><body><div id='player'></div><script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var items=" + jsonVideoIds() + ";var idx=0;var ytPlayer;"
                + "function onYouTubeIframeAPIReady(){ytPlayer=new YT.Player('player',{width:'100%',height:'100%',"
                + "videoId:items[0],playerVars:{autoplay:1,mute:1,controls:0,disablekb:1,fs:0,modestbranding:1,rel:0,playsinline:1,iv_load_policy:3,origin:location.origin},"
                + "events:{onReady:function(e){e.target.mute();e.target.setVolume(0);e.target.playVideo();},onStateChange:function(e){if(e.data===0)nextVideo();},onError:function(){nextVideo();}}});}"
                + "function nextVideo(){if(!items.length)return;var random=" + prefs.getBoolean("random", false) + ";"
                + "var next=idx;if(random&&items.length>1){while(next===idx)next=Math.floor(Math.random()*items.length);}else next=(idx+1)%items.length;"
                + "idx=next;var id=videoId(items[idx]);if(id&&ytPlayer)ytPlayer.loadVideoById(id);}"
                + "</script></body></html>";
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    private String jsonVideoIds() {
        JSONArray array = new JSONArray();
        for (String url : urls) {
            String id = extractVideoId(url);
            if (id != null) array.put(id);
        }
        return array.toString();
    }

    /** Accept only normal YouTube watch, short, embed and live URLs; never guess IDs from arbitrary hosts. */
    private String extractVideoId(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            Uri uri = Uri.parse(raw.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return null;
            if (host == null) return null;
            host = host.toLowerCase(java.util.Locale.ROOT);
            String id = null;
            if ("youtu.be".equals(host)) {
                String path = uri.getLastPathSegment();
                id = path;
            } else if ("youtube.com".equals(host) || host.endsWith(".youtube.com")) {
                String path = uri.getPath();
                if (path == null) return null;
                if ("/watch".equals(path)) id = uri.getQueryParameter("v");
                else {
                    String[] parts = path.split("/");
                    if (parts.length >= 3 && ("embed".equals(parts[1]) || "shorts".equals(parts[1])
                            || "live".equals(parts[1]) || "v".equals(parts[1]))) id = parts[2];
                }
            }
            return id != null && id.matches("[A-Za-z0-9_-]{11}") ? id : null;
        } catch (Exception ignored) { return null; }
    }

    private void playAt(int index) {
        if (webView == null || urls.isEmpty()) return;
        currentIndex = Math.max(0, Math.min(index, urls.size() - 1));
        String id = extractVideoId(urls.get(currentIndex));
        if (id == null) return;
        webView.evaluateJavascript("if(window.ytPlayer&&ytPlayer.loadVideoById){ytPlayer.mute();ytPlayer.setVolume(0);ytPlayer.loadVideoById(" +
                JSONObject.quote(id) + ");}", null);
    }

    private void hideBackgroundLayers() {
        if (backgroundHidden) return;
        for (int i = 0; i < backgroundLayers.length; i++) {
            View view = backgroundLayers[i];
            if (view != null) {
                oldVisibility[i] = view.getVisibility();
                view.setVisibility(View.GONE);
            }
        }
        backgroundHidden = true;
    }

    private void restoreBackgroundLayers() {
        if (!backgroundHidden) return;
        for (int i = 0; i < backgroundLayers.length; i++) {
            if (backgroundLayers[i] != null) backgroundLayers[i].setVisibility(oldVisibility[i]);
        }
        backgroundHidden = false;
    }

    private void syncFields(List<EditText> fields) {
        for (int i = 0; i < fields.size() && i < urls.size(); i++) urls.set(i, fields.get(i).getText().toString());
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private Button smallButton(String label) {
        Button button = new Button(context);
        button.setText(label); button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        return button;
    }

    private GradientDrawable roundSurface(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(Math.max(1, dp(1)), 0x28FFFFFF);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
