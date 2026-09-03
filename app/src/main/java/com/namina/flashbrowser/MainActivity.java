package com.namina.flashbrowser;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.content.Intent;
import android.content.ClipboardManager;
import android.database.Cursor;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Message;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "FlashBrowser";
    private static final String DEFAULT_URL = "https://webbrowsertools.com/test-flash-player/";
    private static final String IE_USER_AGENT =
            "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; Trident/6.0)";
    private static final String IE_ACCEPT =
            "text/html, application/xhtml+xml, */*";
    private static final String IE_ACCEPT_LANGUAGE =
            "zh-CN,zh;q=0.9,en;q=0.8";
    private static final int ORIENTATION_LANDSCAPE = 0;
    private static final int ORIENTATION_PORTRAIT = 1;
    private static final int ORIENTATION_SYSTEM = 2;
    private static final int FONT_MODE_CHINESE_SANS = 0;
    private static final int FONT_MODE_CHINESE_SERIF = 1;
    private static final int FONT_MODE_EMBEDDED = 2;
    private static final String RUFFLE_PATH_PREFIX = "/__ruffle__/";
    private static final String PROXY_PATH_PREFIX = "/__proxy__/";
    private static final String BOOTSTRAP_SCRIPT = "bootstrap.js";
    private static final long HOVER_HOLD_MS = 450L;
    private static final long MENU_HOLD_MS = 700L;
    private static final float TOUCH_HOLD_MOVE_TOLERANCE_DP = 18f;
    private static final String DEFAULT_MANUAL_TEST_SWF = "http://pvzol.org/youkia/main.swf";
    private static final String MANUAL_TEST_PROXY_BASE_URL = "https://webbrowsertools.com/__manual_test__/index.html";
    private WebView wrapper;
    private EditText urlInput;
    private ProgressBar progressBar;
    private View topBar;
    private View browserChrome;
    private FrameLayout fullscreenContainer;
    private ImageButton fullscreenRotateButton;
    private ImageButton fullscreenExitButton;
    private ImageButton fullscreenFeaturePanelButton;
    private Button legacyYoukiaRedirectButton;
    private BrowserPreferenceStore preferenceStore;
    private LocalMappingManager localMappingManager;
    private CookieProfileManager cookieProfileManager;
    private ClipboardCookieImportController clipboardCookieImportController;
    private BrowserSettingsController browserSettingsController;
    private BrowserRequestController browserRequestController;
    private BrowserFullscreenController browserFullscreenController;
    private BrowserNavigationController browserNavigationController;
    private BrowserTouchController browserTouchController;
    private FeaturePanelDialogController featurePanelDialogController;
    private final DutyRequestQueue dutyRequestQueue = new DutyRequestQueue();
    private Uri pendingExternalCookieImportUri;
    private String pendingExternalCookieImportName;
    private String pendingExternalCookieImportMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        preferenceStore = new BrowserPreferenceStore(this);
        applySavedOrientation();
        setContentView(R.layout.activity_main);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                MainActivity.this.handleBackNavigation();
            }
        });

        localMappingManager = new LocalMappingManager(this);
        localMappingManager.initialize();
        cookieProfileManager = new CookieProfileManager(this);
        clipboardCookieImportController = new ClipboardCookieImportController(
                this,
                cookieProfileManager,
                this::requestAllFilesAccessPermission
        );
        browserRequestController = new BrowserRequestController(
                this,
                localMappingManager,
                this::getSavedFontMode
        );
        browserSettingsController = new BrowserSettingsController(
                this,
                cookieProfileManager,
                localMappingManager,
                new BrowserSettingsController.Host() {
                    @Override
                    public void requestAllFilesAccessPermission() {
                        MainActivity.this.requestAllFilesAccessPermission();
                    }

                    @Override
                    public void updateUrlInput(String url) {
                        if (browserNavigationController != null) {
                            browserNavigationController.updateUrlInput(url);
                        }
                    }

                    @Override
                    public void loadUrl(String url) {
                        if (browserNavigationController != null) {
                            browserNavigationController.loadUrl(url);
                        }
                    }

                    @Override
                    public void onOrientationSelected(int mode) {
                        saveOrientation(mode);
                        applyOrientation(mode);
                    }

                    @Override
                    public int getSelectedFontMode() {
                        return getSavedFontMode();
                    }

                    @Override
                    public void onFontModeSelected(int mode) {
                        saveFontMode(mode);
                        reloadCurrentPageForFontMode();
                    }

                    @Override
                    public String getCurrentPageUrl() {
                        return wrapper == null ? null : wrapper.getUrl();
                    }

                    @Override
                    public String getCurrentPageTitle() {
                        return wrapper == null ? null : wrapper.getTitle();
                    }
                }
        );
        cookieProfileManager.ensureInitialized();

        wrapper = findViewById(R.id.web_view);
        urlInput = findViewById(R.id.input_url);
        progressBar = findViewById(R.id.progress_bar);
        topBar = findViewById(R.id.top_bar);
        browserChrome = findViewById(R.id.browser_chrome);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        fullscreenRotateButton = findViewById(R.id.btn_rotate_fullscreen);
        fullscreenExitButton = findViewById(R.id.btn_exit_fullscreen);
        fullscreenFeaturePanelButton = findViewById(R.id.btn_feature_panel_fullscreen);
        legacyYoukiaRedirectButton = findViewById(R.id.btn_legacy_youkia_redirect);
        browserNavigationController = new BrowserNavigationController(
                this,
                wrapper,
                urlInput,
                progressBar,
                legacyYoukiaRedirectButton
        );
        browserFullscreenController = new BrowserFullscreenController(
                this,
                wrapper,
                progressBar,
                topBar,
                browserChrome,
                fullscreenContainer,
                fullscreenRotateButton,
                fullscreenExitButton,
                fullscreenFeaturePanelButton,
                this::applySavedOrientation
        );
        browserFullscreenController.initializeWindowInsets();
        browserTouchController = new BrowserTouchController(this, wrapper);
        featurePanelDialogController = new FeaturePanelDialogController(
                this,
                wrapper,
                preferenceStore,
                cookieProfileManager,
                new WarehouseRecordManager(this),
                dutyRequestQueue,
                this::requestAllFilesAccessPermission
        );
        dutyRequestQueue.setListener(snapshot -> runOnUiThread(() -> featurePanelDialogController.renderQueueState(snapshot)));

        findViewById(R.id.btn_back).setOnClickListener(v -> browserNavigationController.goBackIfPossible());

        findViewById(R.id.btn_forward).setOnClickListener(v -> browserNavigationController.goForwardIfPossible());

        findViewById(R.id.btn_refresh).setOnClickListener(v -> browserNavigationController.reload());
        findViewById(R.id.btn_go).setOnClickListener(v -> featurePanelDialogController.show());
        findViewById(R.id.btn_fullscreen).setOnClickListener(v -> browserFullscreenController.toggleRuffleFullscreenCompat());
        findViewById(R.id.btn_save_cookie).setOnClickListener(v -> browserSettingsController.saveCurrentCookieProfile());
        fullscreenFeaturePanelButton.setOnClickListener(v -> featurePanelDialogController.toggle());
        legacyYoukiaRedirectButton.setOnClickListener(v -> browserNavigationController.performPendingLegacyYoukiaRedirect());
        fullscreenRotateButton.setOnClickListener(v -> browserFullscreenController.rotateFullscreenOrientation());
        fullscreenExitButton.setOnClickListener(v -> browserFullscreenController.handleExitButtonClick());
        findViewById(R.id.btn_settings).setOnClickListener(browserSettingsController::showSettingsMenu);

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                browserNavigationController.loadFromInput();
                return true;
            }
            return false;
        });

        setupWebView();
        if (savedInstanceState != null && wrapper.restoreState(savedInstanceState) != null) {
            String restoredUrl = wrapper.getUrl();
            browserNavigationController.updateUrlInput(TextUtils.isEmpty(restoredUrl) ? DEFAULT_URL : restoredUrl);
        } else {
            browserNavigationController.updateUrlInput(DEFAULT_URL);
            browserNavigationController.loadUrl(DEFAULT_URL);
        }
        wrapper.setOnTouchListener((v, event) -> browserTouchController.handleTouch(event));
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        featurePanelDialogController.onResume();
        resumePendingExternalCookieImportIfPossible();
        if (clipboardCookieImportController != null) {
            clipboardCookieImportController.onResume();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (clipboardCookieImportController != null) {
            clipboardCookieImportController.onWindowFocusChanged(hasFocus);
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (browserFullscreenController != null) {
            browserFullscreenController.ensureStateMatchesPage();
        }
        if (browserRequestController != null && wrapper != null) {
            browserRequestController.refreshPageCompatLayout(wrapper);
        }
    }

    private void setupWebView() {
        WebSettings webSettings = wrapper.getSettings();
        wrapper.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setUserAgentString(IE_USER_AGENT);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setBlockNetworkImage(false);
        webSettings.setBlockNetworkLoads(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setSupportMultipleWindows(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webSettings.setAllowFileAccessFromFileURLs(false);
            webSettings.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webSettings.setOffscreenPreRaster(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(false);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(wrapper, true);
        }

        wrapper.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return browserRequestController.shouldInterceptRequest(request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                browserFullscreenController.resetForNavigation();
                browserTouchController.resetBridgeAvailability();
                browserNavigationController.onPageStarted(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                browserFullscreenController.ensureStateMatchesPage();
                browserRequestController.refreshPageCompatLayout(view);
                browserTouchController.refreshBridgeAvailability();
                wrapper.postDelayed(() -> browserRequestController.refreshPageCompatLayout(view), 250L);
                wrapper.postDelayed(browserTouchController::refreshBridgeAvailability, 600L);
                browserNavigationController.onPageFinished(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.e(TAG, "WebView error: " + error.getDescription());
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceResponse errorResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse);
                Log.e(TAG, "HTTP error for " + request.getUrl() + ": " + errorResponse.getStatusCode());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
                Log.e(TAG, "SSL error: " + error);
            }
        });

        wrapper.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                browserNavigationController.onProgressChanged(view.getUrl(), newProgress);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                browserFullscreenController.showCustomView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                browserFullscreenController.hideCustomView();
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                result.cancel();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                result.cancel();
                return true;
            }

            @Override
            public boolean onJsPrompt(
                    WebView view,
                    String url,
                    String message,
                    String defaultValue,
                    android.webkit.JsPromptResult result
            ) {
                result.cancel();
                return true;
            }

            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean isDialog,
                    boolean isUserGesture,
                    Message resultMsg
            ) {
                return false;
            }
        });
    }

    private void openManualRuffleTest(boolean useProxy) {
        String sourceUrl = resolveManualTestSwfUrl();
        String loadTarget = useProxy ? buildProxyUrl(sourceUrl) : sourceUrl;
        if (TextUtils.isEmpty(loadTarget)) {
            Toast.makeText(this, "Unable to build test SWF URL", Toast.LENGTH_SHORT).show();
            return;
        }

        String baseUrl = useProxy ? MANUAL_TEST_PROXY_BASE_URL : buildManualDirectBaseUrl(sourceUrl);
        String html = buildManualRuffleTestHtml(sourceUrl, loadTarget, useProxy);

        browserNavigationController.hideKeyboard();
        browserNavigationController.focusWebView();
        wrapper.loadDataWithBaseURL(baseUrl, html, "text/html", StandardCharsets.UTF_8.name(), null);
        browserNavigationController.setUrlInputText((useProxy ? "manual-proxy: " : "manual-direct: ") + sourceUrl);
        Toast.makeText(this, useProxy ? "Opened manual proxy test" : "Opened manual direct test", Toast.LENGTH_SHORT).show();
    }

    private String resolveManualTestSwfUrl() {
        String rawInput = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
        if (isLikelySwfUrl(rawInput)) {
            return browserNavigationController.normalizeInputToUrl(rawInput);
        }

        String currentUrl = wrapper == null ? null : wrapper.getUrl();
        if (isLikelySwfUrl(currentUrl)) {
            return currentUrl;
        }

        return DEFAULT_MANUAL_TEST_SWF;
    }

    private boolean isLikelySwfUrl(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.US);
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        return normalized.endsWith(".swf");
    }

    private String buildProxyUrl(String sourceUrl) {
        try {
            Uri uri = Uri.parse(sourceUrl);
            String scheme = uri.getScheme();
            String authority = uri.getEncodedAuthority();
            if (TextUtils.isEmpty(scheme) || TextUtils.isEmpty(authority)) {
                return null;
            }

            Uri baseUri = Uri.parse(MANUAL_TEST_PROXY_BASE_URL);
            String origin = baseUri.getScheme() + "://" + baseUri.getEncodedAuthority();
            String path = uri.getEncodedPath();
            if (TextUtils.isEmpty(path)) {
                path = "/";
            }

            StringBuilder builder = new StringBuilder();
            builder.append(origin)
                    .append(PROXY_PATH_PREFIX)
                    .append(scheme)
                    .append("/")
                    .append(authority)
                    .append(path);

            String query = uri.getEncodedQuery();
            if (!TextUtils.isEmpty(query)) {
                builder.append("?").append(query);
            }
            return builder.toString();
        } catch (Exception e) {
            Log.e(TAG, "Unable to build proxy URL for " + sourceUrl, e);
            return null;
        }
    }

    private String buildManualDirectBaseUrl(String sourceUrl) {
        try {
            Uri uri = Uri.parse(sourceUrl);
            String scheme = uri.getScheme();
            String authority = uri.getEncodedAuthority();
            if (TextUtils.isEmpty(scheme) || TextUtils.isEmpty(authority)) {
                return sourceUrl;
            }

            String path = uri.getEncodedPath();
            if (TextUtils.isEmpty(path)) {
                return scheme + "://" + authority + "/";
            }

            int slashIndex = path.lastIndexOf('/');
            String directory = slashIndex >= 0 ? path.substring(0, slashIndex + 1) : "/";
            if (TextUtils.isEmpty(directory)) {
                directory = "/";
            }

            return scheme + "://" + authority + directory;
        } catch (Exception e) {
            Log.e(TAG, "Unable to build direct test base URL for " + sourceUrl, e);
            return sourceUrl;
        }
    }

    private String buildManualRuffleTestHtml(String sourceUrl, String loadTarget, boolean useProxy) {
        String modeLabel = useProxy ? "manual proxy load" : "manual direct load";
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5, user-scalable=yes'>");
        html.append("<title>Ruffle Manual Test</title>");
        html.append("<style>");
        html.append("html,body{margin:0;padding:0;background:#111;color:#f3f4f6;font-family:sans-serif;height:100%;overflow:hidden;}");
        html.append("body{display:flex;flex-direction:column;}");
        html.append(".meta{background:#1f2937;padding:10px 12px;font-size:12px;line-height:1.5;word-break:break-all;}");
        html.append(".status{color:#93c5fd;}");
        html.append(".host{flex:1;min-height:0;display:flex;align-items:center;justify-content:center;background:#000;}");
        html.append("#player-host,#player-host ruffle-player{width:100%;height:100%;}");
        html.append("ruffle-player,ruffle-embed,ruffle-object{width:100%!important;height:100%!important;max-width:100%!important;max-height:100%!important;}");
        html.append("</style>");
        html.append("<script>").append(browserRequestController.buildRuffleConfigScript()).append("</script>");
        html.append("<script src='").append(RUFFLE_PATH_PREFIX).append("ruffle.js'></script>");
        html.append("</head><body>");
        html.append("<div class='meta'>");
        html.append("<div>Mode: ").append(escapeHtml(modeLabel)).append("</div>");
        html.append("<div>Source SWF: ").append(escapeHtml(sourceUrl)).append("</div>");
        html.append("<div>Load URL: ").append(escapeHtml(loadTarget)).append("</div>");
        html.append("<div class='status' id='status'>Status: waiting</div>");
        html.append("</div>");
        html.append("<div class='host'><div id='player-host'></div></div>");
        html.append("<script>");
        html.append("(function(){");
        html.append("function setStatus(message){");
        html.append("var node=document.getElementById('status');");
        html.append("if(node){node.textContent='Status: '+message;}");
        html.append("}");
        html.append("function boot(){");
        html.append("try{");
        html.append("if(!window.RufflePlayer||typeof window.RufflePlayer.newest!=='function'){setStatus('Ruffle runtime unavailable');return;}");
        html.append("var factory=window.RufflePlayer.newest();");
        html.append("if(!factory||typeof factory.createPlayer!=='function'){setStatus('Ruffle factory unavailable');return;}");
        html.append("var player=factory.createPlayer();");
        html.append("player.id='manual-ruffle-player';");
        html.append("player.style.width='100%';");
        html.append("player.style.height='100%';");
        html.append("player.style.maxWidth='100%';");
        html.append("player.style.maxHeight='100%';");
        html.append("var host=document.getElementById('player-host');");
        html.append("host.innerHTML='';");
        html.append("host.appendChild(player);");
        html.append("window.__manualRufflePlayer=player;");
        html.append("setStatus('loading');");
        html.append("var result=player.load('").append(browserRequestController.escapeJsString(loadTarget)).append("');");
        html.append("if(result&&typeof result.then==='function'){");
        html.append("result.then(function(){setStatus('loaded');}).catch(function(error){setStatus('load failed: '+(error&&error.message?error.message:String(error)));});");
        html.append("}else{setStatus('load dispatched');}");
        html.append("}catch(error){setStatus('boot failed: '+(error&&error.message?error.message:String(error)));}}");
        html.append("if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded', boot, {once:true});}else{boot();}");
        html.append("})();");
        html.append("</script>");
        html.append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void logRenderCapabilities() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        String script =
                "(function(){" +
                        "var c=document.createElement('canvas');" +
                        "var w2=!!window.WebGL2RenderingContext;" +
                        "var gl2=null;" +
                        "var gl1=null;" +
                        "try{gl2=c.getContext('webgl2');}catch(e){}" +
                        "try{gl1=c.getContext('webgl')||c.getContext('experimental-webgl');}catch(e){}" +
                        "return JSON.stringify({" +
                        "webgl2Ctor:w2," +
                        "webgl2:!!gl2," +
                        "webgl:!!gl1," +
                        "ua:navigator.userAgent" +
                        "});" +
                        "})();";
        wrapper.evaluateJavascript(script, value -> { });
    }

    private void scheduleRuffleDebugDump() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || wrapper == null) {
            return;
        }

        wrapper.postDelayed(() -> evaluateRuffleDebugDump(false, null), 1200);
        wrapper.postDelayed(() -> evaluateRuffleDebugDump(false, null), 3500);
    }

    private void showRuffleDebugDumpDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || wrapper == null) {
            Toast.makeText(this, "Ruffle debug dump requires Android 4.4+", Toast.LENGTH_SHORT).show();
            return;
        }

        evaluateRuffleDebugDump(true, dump -> {
            ScrollView scrollView = new ScrollView(this);
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            TextView textView = new TextView(this);
            textView.setTextIsSelectable(true);
            textView.setPadding(padding, padding, padding, padding);
            textView.setText(TextUtils.isEmpty(dump) ? "No debug info available" : dump);
            scrollView.addView(textView);

            new AlertDialog.Builder(this)
                    .setTitle("Ruffle Debug Dump")
                    .setView(scrollView)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }

    private interface DebugDumpCallback {
        void onDump(String dump);
    }

    private void evaluateRuffleDebugDump(boolean showToastOnFailure, DebugDumpCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || wrapper == null) {
            if (callback != null) {
                callback.onDump("");
            }
            return;
        }

        wrapper.evaluateJavascript(buildRuffleDebugDumpScript(), value -> {
            String decoded = decodeEvaluateJavascriptString(value);
            if (TextUtils.isEmpty(decoded)) {
                decoded = "No debug info returned";
                if (showToastOnFailure) {
                    Toast.makeText(this, decoded, Toast.LENGTH_SHORT).show();
                }
            }
            if (callback != null) {
                callback.onDump(decoded);
            }
        });
    }

    private String buildRuffleDebugDumpScript() {
        return "(function(){"
                + "function safe(value){try{return value===undefined?null:value;}catch(e){return String(e);}}"
                + "function text(value){if(value===null||value===undefined){return 'null';}if(typeof value==='string'){return value;}try{return JSON.stringify(value,null,2);}catch(e){return String(value);}}"
                + "var c=document.createElement('canvas');"
                + "var webgl=null,webgl2=null,gl1ok=false,gl2ok=false;"
                + "try{webgl=c.getContext('webgl')||c.getContext('experimental-webgl');gl1ok=!!webgl;}catch(e){webgl='error:'+e.message;}"
                + "try{webgl2=c.getContext('webgl2');gl2ok=!!webgl2;}catch(e){webgl2='error:'+e.message;}"
                + "var element=document.querySelector('ruffle-player, ruffle-embed, ruffle-object');"
                + "if(!element&&window.__manualRufflePlayer){element=window.__manualRufflePlayer;}"
                + "var api=null;var apiError=null;"
                + "try{if(element&&typeof element.ruffle==='function'){api=element.ruffle(1);}}catch(e){apiError=String(e);}"
                + "var rect=element&&element.getBoundingClientRect?element.getBoundingClientRect():null;"
                + "var shadow=element&&element.shadowRoot?element.shadowRoot:null;"
                + "var canvasNode=shadow?shadow.querySelector('canvas'):null;"
                + "var dump={"
                + "url:safe(location.href),"
                + "title:safe(document.title),"
                + "userAgent:safe(navigator.userAgent),"
                + "webglCtor:!!window.WebGLRenderingContext,"
                + "webgl2Ctor:!!window.WebGL2RenderingContext,"
                + "webglContext:gl1ok,"
                + "webgl2Context:gl2ok,"
                + "ruffleConfig:safe(window.RufflePlayer&&window.RufflePlayer.config?window.RufflePlayer.config:null),"
                + "element:element?{tag:element.tagName,id:element.id||null,widthAttr:element.getAttribute&&element.getAttribute('width'),heightAttr:element.getAttribute&&element.getAttribute('height'),style:element.getAttribute&&element.getAttribute('style'),clientWidth:element.clientWidth,clientHeight:element.clientHeight,rect:rect?{width:rect.width,height:rect.height,left:rect.left,top:rect.top}:null}:null,"
                + "apiError:apiError,"
                + "player:api?{readyState:safe(api.readyState),metadata:safe(api.metadata),loadedConfig:safe(api.loadedConfig),rendererName:safe(typeof api.rendererName==='function'?api.rendererName():null),rendererDebugInfo:safe(typeof api.rendererDebugInfo==='function'?api.rendererDebugInfo():null),fullscreenEnabled:safe(api.fullscreenEnabled),isFullscreen:safe(api.isFullscreen),volume:safe(api.volume),suspended:safe(api.suspended)}:null,"
                + "canvas:canvasNode?{width:canvasNode.width,height:canvasNode.height,clientWidth:canvasNode.clientWidth,clientHeight:canvasNode.clientHeight}:null"
                + "};"
                + "return ["
                + "'URL: '+text(dump.url),"
                + "'Title: '+text(dump.title),"
                + "'UA: '+text(dump.userAgent),"
                + "'WebGL ctor: '+text(dump.webglCtor)+' / context: '+text(dump.webglContext),"
                + "'WebGL2 ctor: '+text(dump.webgl2Ctor)+' / context: '+text(dump.webgl2Context),"
                + "'Ruffle config: '+text(dump.ruffleConfig),"
                + "'Element: '+text(dump.element),"
                + "'API error: '+text(dump.apiError),"
                + "'Player: '+text(dump.player),"
                + "'Canvas: '+text(dump.canvas)"
                + "].join('\\n\\n');"
                + "})();";
    }

    private String decodeEvaluateJavascriptString(String value) {
        if (value == null || "null".equals(value)) {
            return null;
        }

        try {
            return new JSONArray("[" + value + "]").getString(0);
        } catch (Exception e) {
            Log.e(TAG, "Unable to decode evaluateJavascript result", e);
            return value;
        }
    }

    private void requestAllFilesAccessPermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        }
        Toast.makeText(this, R.string.cookie_storage_permission_granted_tip, Toast.LENGTH_LONG).show();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        Uri targetUri = null;
        String mimeType = intent.getType();
        String fileNameHint = null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            targetUri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) {
                targetUri = (Uri) stream;
            }
        }

        if (targetUri == null) {
            return;
        }

        fileNameHint = getDisplayNameForUri(targetUri);
        if (TextUtils.isEmpty(fileNameHint)) {
            fileNameHint = targetUri.getLastPathSegment();
        }

        if (!isSupportedCookieImport(mimeType, fileNameHint)) {
            return;
        }

        if (!cookieProfileManager.canAccessRootDirectory()) {
            pendingExternalCookieImportUri = targetUri;
            pendingExternalCookieImportName = fileNameHint;
            pendingExternalCookieImportMimeType = mimeType;
            Toast.makeText(this, R.string.cookie_import_need_permission, Toast.LENGTH_LONG).show();
            requestAllFilesAccessPermission();
            return;
        }

        importExternalCookieUri(targetUri, mimeType, fileNameHint);
    }

    private void resumePendingExternalCookieImportIfPossible() {
        if (pendingExternalCookieImportUri == null || !cookieProfileManager.canAccessRootDirectory()) {
            return;
        }
        importExternalCookieUri(
                pendingExternalCookieImportUri,
                pendingExternalCookieImportMimeType,
                pendingExternalCookieImportName
        );
    }

    private void importExternalCookieUri(Uri uri, String mimeType, String fileNameHint) {
        if (uri == null) {
            return;
        }

        CookieProfileManager.ImportResult result;
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                result = CookieProfileManager.ImportResult.failure("no_input");
            } else if (isZipImport(mimeType, fileNameHint)) {
                result = cookieProfileManager.importExternalZip(inputStream);
            } else if (isXmlImport(mimeType, fileNameHint)) {
                result = cookieProfileManager.importExternalXml(inputStream, fileNameHint);
            } else {
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to import external cookie file", e);
            result = CookieProfileManager.ImportResult.failure("import_exception");
        }

        pendingExternalCookieImportUri = null;
        pendingExternalCookieImportName = null;
        pendingExternalCookieImportMimeType = null;

        if (result.success) {
            if (result.importedCount <= 1 && !TextUtils.isEmpty(result.primaryName)) {
                Toast.makeText(
                        this,
                        getString(R.string.cookie_import_success_single, result.primaryName),
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Toast.makeText(
                        this,
                        getString(R.string.cookie_import_success_multiple, result.importedCount),
                        Toast.LENGTH_LONG
                ).show();
            }
            return;
        }

        Toast.makeText(this, R.string.cookie_import_failed, Toast.LENGTH_LONG).show();
    }

    private String getDisplayNameForUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (columnIndex >= 0) {
                        return cursor.getString(columnIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to resolve display name for import uri: " + uri, e);
            }
        }
        return null;
    }

    private boolean isSupportedCookieImport(String mimeType, String fileNameHint) {
        return isXmlImport(mimeType, fileNameHint) || isZipImport(mimeType, fileNameHint);
    }

    private boolean isXmlImport(String mimeType, String fileNameHint) {
        String normalizedMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        String normalizedName = fileNameHint == null ? "" : fileNameHint.toLowerCase(Locale.US);
        return normalizedMime.contains("xml")
                || normalizedName.endsWith(".xml");
    }

    private boolean isZipImport(String mimeType, String fileNameHint) {
        String normalizedMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        String normalizedName = fileNameHint == null ? "" : fileNameHint.toLowerCase(Locale.US);
        return normalizedMime.contains("zip")
                || normalizedName.endsWith(".zip");
    }

    private void applySavedOrientation() {
        int mode = preferenceStore.getOrientationMode(ORIENTATION_PORTRAIT);
        applyOrientation(mode);
    }

    private void saveOrientation(int mode) {
        preferenceStore.setOrientationMode(mode);
    }

    private int getSavedFontMode() {
        return preferenceStore.getFontMode(FONT_MODE_CHINESE_SANS);
    }

    private void saveFontMode(int mode) {
        preferenceStore.setFontMode(mode);
    }

    private void reloadCurrentPageForFontMode() {
        if (wrapper == null || TextUtils.isEmpty(wrapper.getUrl())) {
            return;
        }
        wrapper.reload();
        Toast.makeText(this, "已重新加载页面以应用字体设置", Toast.LENGTH_SHORT).show();
    }

    private void applyOrientation(int mode) {
        switch (mode) {
            case ORIENTATION_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                break;
            case ORIENTATION_SYSTEM:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
            case ORIENTATION_LANDSCAPE:
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (wrapper != null) {
            wrapper.saveState(outState);
        }
    }

    private void handleBackNavigation() {
        if (browserFullscreenController.handleBackPressed()) {
            return;
        }
        if (browserNavigationController.canGoBack()) {
            browserNavigationController.goBackIfPossible();
        } else {
            finish();
        }
    }

}

final class BrowserNavigationController {
    private static final String TAG = "FlashBrowser";

    private final AppCompatActivity activity;
    private final WebView webView;
    private final EditText urlInput;
    private final ProgressBar progressBar;
    private final Button legacyYoukiaRedirectButton;
    private String pendingLegacyYoukiaSourceUrl;
    private String pendingLegacyYoukiaTargetUrl;

    BrowserNavigationController(
            AppCompatActivity activity,
            WebView webView,
            EditText urlInput,
            ProgressBar progressBar,
            Button legacyYoukiaRedirectButton
    ) {
        this.activity = activity;
        this.webView = webView;
        this.urlInput = urlInput;
        this.progressBar = progressBar;
        this.legacyYoukiaRedirectButton = legacyYoukiaRedirectButton;
    }

    void goBackIfPossible() {
        if (webView.canGoBack()) {
            webView.goBack();
        }
    }

    void goForwardIfPossible() {
        if (webView.canGoForward()) {
            webView.goForward();
        }
    }

    void reload() {
        webView.reload();
    }

    boolean canGoBack() {
        return webView.canGoBack();
    }

    void loadFromInput() {
        String rawInput = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
        if (rawInput.isEmpty()) {
            return;
        }

        String targetUrl = normalizeInputToUrl(rawInput);
        updateUrlInput(targetUrl);
        hideKeyboard();
        focusWebView();
        loadUrl(targetUrl);
    }

    void loadUrl(String url) {
        webView.loadUrl(url);
    }

    void onPageStarted(String url) {
        hideLegacyYoukiaRedirectPrompt();
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        updateUrlInput(url);
    }

    void onPageFinished(String url) {
        updateLegacyYoukiaRedirectPrompt(url);
        updateUrlInput(url);
        if (progressBar != null && progressBar.getProgress() >= 100) {
            progressBar.setVisibility(View.GONE);
        }
    }

    void onProgressChanged(String url, int newProgress) {
        if (progressBar != null) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }
        if (newProgress >= 100) {
            updateUrlInput(url);
        }
    }

    void performPendingLegacyYoukiaRedirect() {
        String sourceUrl = pendingLegacyYoukiaSourceUrl;
        String targetUrl = pendingLegacyYoukiaTargetUrl;
        hideLegacyYoukiaRedirectPrompt();
        if (TextUtils.isEmpty(sourceUrl) || TextUtils.isEmpty(targetUrl)) {
            return;
        }
        performRedirectWithPreferredCookies(sourceUrl, targetUrl);
    }

    String normalizeInputToUrl(String rawInput) {
        if (Patterns.WEB_URL.matcher(rawInput).matches()) {
            return ensureScheme(rawInput);
        }

        Uri parsed = Uri.parse(rawInput);
        if (parsed.getScheme() != null) {
            return rawInput;
        }

        if (rawInput.contains(".") && !rawInput.contains(" ")) {
            return "https://" + rawInput;
        }

        return "https://www.google.com/search?q=" + Uri.encode(rawInput);
    }

    void updateUrlInput(String url) {
        if (TextUtils.isEmpty(url) || urlInput.hasFocus()) {
            return;
        }
        urlInput.setText(url);
        urlInput.setSelection(urlInput.getText().length());
    }

    void setUrlInputText(String value) {
        if (value == null) {
            value = "";
        }
        urlInput.setText(value);
        urlInput.setSelection(urlInput.getText().length());
    }

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        }
    }

    void focusWebView() {
        webView.requestFocus();
    }

    private void performRedirectWithPreferredCookies(String sourceUrl, String fallbackTargetUrl) {
        Uri sourceUri = Uri.parse(sourceUrl);
        Uri fallbackTargetUri = Uri.parse(fallbackTargetUrl);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        String chosenTargetUrl = fallbackTargetUrl;
        String selectedCookies = null;
        try {
            for (String candidateRootUrl : CookieProfileManager.buildCandidateGameRootUrlsForLegacyPage(sourceUri)) {
                Uri candidateRootUri = Uri.parse(candidateRootUrl);
                String candidateMainUrl = CookieProfileManager.buildMainTargetUrlForPage(candidateRootUri);
                String candidateCookies = null;
                if (!TextUtils.isEmpty(candidateMainUrl)) {
                    candidateCookies = cookieManager.getCookie(candidateMainUrl);
                }
                if (TextUtils.isEmpty(candidateCookies)) {
                    candidateCookies = cookieManager.getCookie(candidateRootUrl);
                }
                candidateCookies = CookieProfileManager.selectPersistedCookiesForPage(candidateRootUri, candidateCookies);
                if (!TextUtils.isEmpty(candidateCookies)) {
                    selectedCookies = candidateCookies;
                    if (!TextUtils.isEmpty(candidateMainUrl)) {
                        chosenTargetUrl = candidateMainUrl;
                    } else {
                        chosenTargetUrl = candidateRootUrl;
                    }
                    break;
                }
            }

            if (TextUtils.isEmpty(selectedCookies)) {
                String sourceCookies = cookieManager.getCookie(sourceUrl);
                selectedCookies = CookieProfileManager.selectPersistedCookiesForPage(fallbackTargetUri, sourceCookies);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to inspect cookies for redirect: " + sourceUrl, e);
        }

        final String finalTargetUrl = chosenTargetUrl;
        final String finalCookies = selectedCookies;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(value -> {
                applyRedirectCookies(cookieManager, finalTargetUrl, finalCookies);
                loadUrl(finalTargetUrl);
            });
            cookieManager.flush();
        } else {
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            applyRedirectCookies(cookieManager, finalTargetUrl, finalCookies);
            loadUrl(finalTargetUrl);
        }
    }

    private void applyRedirectCookies(CookieManager cookieManager, String targetUrl, String cookies) {
        if (cookieManager == null || TextUtils.isEmpty(targetUrl) || TextUtils.isEmpty(cookies)) {
            return;
        }
        Uri targetUri = Uri.parse(targetUrl);
        String targetRootUrl = CookieProfileManager.buildRootUrl(targetUri);
        for (String cookieEntry : CookieProfileManager.buildCookieApplicationList(cookies)) {
            if (!TextUtils.isEmpty(targetRootUrl)) {
                cookieManager.setCookie(targetRootUrl, cookieEntry);
            }
            cookieManager.setCookie(targetUrl, cookieEntry);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
        }
    }

    private void updateLegacyYoukiaRedirectPrompt(String currentUrl) {
        String targetUrl = resolveSpecialYoukiaRedirectTarget(currentUrl);
        if (TextUtils.isEmpty(targetUrl) || TextUtils.equals(currentUrl, targetUrl)) {
            hideLegacyYoukiaRedirectPrompt();
            return;
        }

        pendingLegacyYoukiaSourceUrl = currentUrl;
        pendingLegacyYoukiaTargetUrl = targetUrl;
        if (legacyYoukiaRedirectButton != null) {
            legacyYoukiaRedirectButton.setVisibility(View.VISIBLE);
        }
    }

    private void hideLegacyYoukiaRedirectPrompt() {
        pendingLegacyYoukiaSourceUrl = null;
        pendingLegacyYoukiaTargetUrl = null;
        if (legacyYoukiaRedirectButton != null) {
            legacyYoukiaRedirectButton.setVisibility(View.GONE);
        }
    }

    private String resolveSpecialYoukiaRedirectTarget(String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) {
            return null;
        }

        try {
            Uri uri = Uri.parse(rawUrl);
            if (!CookieProfileManager.isLegacyYoukiaLandingPage(uri)) {
                return null;
            }

            String subdomain = CookieProfileManager.extractLegacyYoukiaSubdomain(uri);
            if (TextUtils.isEmpty(subdomain)) {
                return null;
            }

            return CookieProfileManager.buildMainTargetUrlForPage(uri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to rewrite youkia url: " + rawUrl, e);
            return null;
        }
    }

    private String ensureScheme(String value) {
        Uri uri = Uri.parse(value);
        if (uri.getScheme() != null) {
            return value;
        }
        return "https://" + value;
    }
}

final class FeaturePanelDialogController {
    private static final int TAB_COOKIE = 0;
    private static final int TAB_BASIC = 1;
    private static final int TAB_REPOSITORY = 2;
    private static final int TAB_LOG = 3;

    private final AppCompatActivity activity;
    private final WebView webView;
    private final BrowserPreferenceStore preferenceStore;
    private final DutyRequestQueue dutyRequestQueue;
    private final FeaturePanelUiController uiController;
    private final FeaturePanelCookieController cookieController;
    private final FeaturePanelTaskController taskController;
    private final FeaturePanelRepositoryController repositoryController;
    private final Runnable requestAllFilesAccessAction;
    private final ArrayList<FeatureCookieChoice> featureCookieChoices = new ArrayList<>();

    private AlertDialog dialog;
    private Button tabCookieButton;
    private Button tabBasicButton;
    private Button tabRepositoryButton;
    private Button tabLogButton;
    private View cookiePage;
    private View basicPage;
    private View repositoryPage;
    private View logPage;
    private LinearLayout cookieContainer;
    private TextView cookieHintText;
    private Button selectAllCookiesButton;
    private EditText concurrencyInput;
    private EditText requestIntervalInput;
    private EditText frequentRetryIntervalInput;
    private Button pauseResumeButton;
    private Button cancelButton;
    private CheckBox dailyDutyCheckBox;
    private CheckBox fullSweepCheckBox;
    private CheckBox fubenProgressCheckBox;
    private View fubenProgressSettingsLayout;
    private Button fubenProgressSettingsButton;
    private Button fubenProgressRunButton;
    private CheckBox fubenProgressStage1CheckBox;
    private CheckBox fubenProgressStage2CheckBox;
    private CheckBox fubenProgressStage3CheckBox;
    private CheckBox fubenProgressStage4CheckBox;
    private CheckBox fubenProgressStage5CheckBox;
    private CheckBox medalRepeatCheckBox;
    private CheckBox medalRepeatInfiniteCheckBox;
    private View medalRepeatSettingsLayout;
    private Button medalRepeatSettingsButton;
    private Button medalRepeatRunButton;
    private CheckBox medalBlock1CheckBox;
    private CheckBox medalBlock2CheckBox;
    private CheckBox medalBlock3CheckBox;
    private CheckBox medalBlock4CheckBox;
    private CheckBox medalBlock5CheckBox;
    private Spinner medalBlock1RangeSpinner;
    private Spinner medalBlock2RangeSpinner;
    private Spinner medalBlock3RangeSpinner;
    private Spinner medalBlock4RangeSpinner;
    private Spinner medalBlock5RangeSpinner;
    private Button dailyDutyRunButton;
    private Button fullSweepRunButton;
    private Button fubenResetRunButton;
    private Button startSelectedButton;
    private Button storageAccessButton;
    private TextView repositoryHintText;
    private TextView repositorySelectedTargetsText;
    private Button repositoryPickTargetsButton;
    private TextView repositoryViewCookieText;
    private Button repositoryPickViewCookieButton;
    private Button repositoryRefreshButton;
    private Button repositoryRecordButton;
    private Button repositoryCompareButton;
    private Button repositoryIgnoreSelectedButton;
    private Button repositoryRemoveIgnoredButton;
    private LinearLayout repositoryCurrentContainer;
    private LinearLayout repositoryDeltaContainer;
    private LinearLayout repositoryIgnoredContainer;
    private TextView queueStatusText;
    private TextView queueLogText;

    FeaturePanelDialogController(
            AppCompatActivity activity,
            WebView webView,
            BrowserPreferenceStore preferenceStore,
            CookieProfileManager cookieProfileManager,
            WarehouseRecordManager warehouseRecordManager,
            DutyRequestQueue dutyRequestQueue,
            Runnable requestAllFilesAccessAction
    ) {
        this.activity = activity;
        this.webView = webView;
        this.preferenceStore = preferenceStore;
        this.dutyRequestQueue = dutyRequestQueue;
        this.requestAllFilesAccessAction = requestAllFilesAccessAction;
        this.uiController = new FeaturePanelUiController();
        this.cookieController = new FeaturePanelCookieController(activity, preferenceStore, cookieProfileManager);
        this.taskController = new FeaturePanelTaskController(preferenceStore);
        this.repositoryController = new FeaturePanelRepositoryController(
                activity,
                preferenceStore,
                warehouseRecordManager
        );
    }

    void onResume() {
        if (dialog != null && dialog.isShowing()) {
            refreshCookieChoices();
            repositoryController.render(featureCookieChoices);
            renderQueueState(dutyRequestQueue.snapshot());
        }
    }

    void show() {
        if (dialog != null && dialog.isShowing()) {
            return;
        }

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_feature_panel, null);
        bindViews(dialogView);
        bindControllers();
        bindActions();

        dialog = new AlertDialog.Builder(activity)
                .setTitle("Feature Panel")
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnDismissListener(ignored -> clearReferences());
        dialog.show();
        if (dialog.getWindow() != null) {
            int width = Math.min(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    dpToPx(920)
            );
            int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.88f);
            dialog.getWindow().setLayout(width, height);
        }

        refreshCookieChoices();
        repositoryController.render(featureCookieChoices);
        switchTab(TAB_COOKIE);
        renderQueueState(dutyRequestQueue.snapshot());
    }

    void toggle() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            return;
        }
        show();
    }

    void renderQueueState(DutyRequestQueue.StateSnapshot snapshot) {
        uiController.renderQueueState(snapshot);
    }

    private void bindViews(View dialogView) {
        tabCookieButton = dialogView.findViewById(R.id.btn_panel_tab_cookie);
        tabBasicButton = dialogView.findViewById(R.id.btn_panel_tab_basic);
        tabRepositoryButton = dialogView.findViewById(R.id.btn_panel_tab_repository);
        tabLogButton = dialogView.findViewById(R.id.btn_panel_tab_log);
        cookiePage = dialogView.findViewById(R.id.panel_page_cookie);
        basicPage = dialogView.findViewById(R.id.panel_page_basic);
        repositoryPage = dialogView.findViewById(R.id.panel_page_repository);
        logPage = dialogView.findViewById(R.id.panel_page_log);
        cookieContainer = dialogView.findViewById(R.id.panel_cookie_container);
        cookieHintText = dialogView.findViewById(R.id.text_cookie_selection_hint);
        selectAllCookiesButton = dialogView.findViewById(R.id.btn_panel_select_all_cookies);
        concurrencyInput = dialogView.findViewById(R.id.input_panel_concurrency);
        requestIntervalInput = dialogView.findViewById(R.id.input_panel_request_interval);
        frequentRetryIntervalInput = dialogView.findViewById(R.id.input_panel_frequent_retry_interval);
        pauseResumeButton = dialogView.findViewById(R.id.btn_panel_pause_resume);
        cancelButton = dialogView.findViewById(R.id.btn_panel_cancel);
        dailyDutyCheckBox = dialogView.findViewById(R.id.check_panel_daily_duty);
        fullSweepCheckBox = dialogView.findViewById(R.id.check_panel_duty_full_sweep);
        fubenProgressCheckBox = dialogView.findViewById(R.id.check_panel_fuben_progress);
        fubenProgressSettingsLayout = dialogView.findViewById(R.id.layout_panel_fuben_progress_settings);
        fubenProgressSettingsButton = dialogView.findViewById(R.id.btn_panel_fuben_progress_settings);
        fubenProgressRunButton = dialogView.findViewById(R.id.btn_panel_fuben_progress_run);
        fubenProgressStage1CheckBox = dialogView.findViewById(R.id.check_panel_fuben_progress_stage_1);
        fubenProgressStage2CheckBox = dialogView.findViewById(R.id.check_panel_fuben_progress_stage_2);
        fubenProgressStage3CheckBox = dialogView.findViewById(R.id.check_panel_fuben_progress_stage_3);
        fubenProgressStage4CheckBox = dialogView.findViewById(R.id.check_panel_fuben_progress_stage_4);
        fubenProgressStage5CheckBox = dialogView.findViewById(R.id.check_panel_fuben_progress_stage_5);
        medalRepeatCheckBox = dialogView.findViewById(R.id.check_panel_medal_repeat);
        medalRepeatInfiniteCheckBox = dialogView.findViewById(R.id.check_panel_medal_repeat_infinite);
        medalRepeatSettingsLayout = dialogView.findViewById(R.id.layout_panel_medal_repeat_settings);
        medalRepeatSettingsButton = dialogView.findViewById(R.id.btn_panel_medal_repeat_settings);
        medalRepeatRunButton = dialogView.findViewById(R.id.btn_panel_medal_repeat_run);
        fubenResetRunButton = dialogView.findViewById(R.id.btn_panel_fuben_reset);
        medalBlock1CheckBox = dialogView.findViewById(R.id.check_panel_medal_block_1);
        medalBlock2CheckBox = dialogView.findViewById(R.id.check_panel_medal_block_2);
        medalBlock3CheckBox = dialogView.findViewById(R.id.check_panel_medal_block_3);
        medalBlock4CheckBox = dialogView.findViewById(R.id.check_panel_medal_block_4);
        medalBlock5CheckBox = dialogView.findViewById(R.id.check_panel_medal_block_5);
        medalBlock1RangeSpinner = dialogView.findViewById(R.id.spinner_panel_medal_block_1_range);
        medalBlock2RangeSpinner = dialogView.findViewById(R.id.spinner_panel_medal_block_2_range);
        medalBlock3RangeSpinner = dialogView.findViewById(R.id.spinner_panel_medal_block_3_range);
        medalBlock4RangeSpinner = dialogView.findViewById(R.id.spinner_panel_medal_block_4_range);
        medalBlock5RangeSpinner = dialogView.findViewById(R.id.spinner_panel_medal_block_5_range);
        dailyDutyRunButton = dialogView.findViewById(R.id.btn_panel_daily_duty_run);
        fullSweepRunButton = dialogView.findViewById(R.id.btn_panel_duty_full_sweep_run);
        startSelectedButton = dialogView.findViewById(R.id.btn_panel_start_selected);
        storageAccessButton = dialogView.findViewById(R.id.btn_panel_request_storage_access);
        repositoryHintText = dialogView.findViewById(R.id.text_panel_repository_hint);
        repositorySelectedTargetsText = dialogView.findViewById(R.id.text_panel_repository_selected_targets);
        repositoryPickTargetsButton = dialogView.findViewById(R.id.btn_panel_repository_pick_targets);
        repositoryViewCookieText = dialogView.findViewById(R.id.text_panel_repository_view_cookie);
        repositoryPickViewCookieButton = dialogView.findViewById(R.id.btn_panel_repository_pick_view_cookie);
        repositoryRefreshButton = dialogView.findViewById(R.id.btn_panel_repository_refresh);
        repositoryRecordButton = dialogView.findViewById(R.id.btn_panel_repository_record);
        repositoryCompareButton = dialogView.findViewById(R.id.btn_panel_repository_compare);
        repositoryIgnoreSelectedButton = dialogView.findViewById(R.id.btn_panel_repository_ignore_selected);
        repositoryRemoveIgnoredButton = dialogView.findViewById(R.id.btn_panel_repository_remove_ignored);
        repositoryCurrentContainer = dialogView.findViewById(R.id.panel_repository_current_container);
        repositoryDeltaContainer = dialogView.findViewById(R.id.panel_repository_delta_container);
        repositoryIgnoredContainer = dialogView.findViewById(R.id.panel_repository_ignored_container);
        queueStatusText = dialogView.findViewById(R.id.text_panel_queue_status);
        queueLogText = dialogView.findViewById(R.id.text_panel_queue_log);
    }

    private void bindControllers() {
        uiController.bind(
                tabCookieButton,
                tabBasicButton,
                tabRepositoryButton,
                tabLogButton,
                cookiePage,
                basicPage,
                repositoryPage,
                logPage,
                queueStatusText,
                queueLogText,
                pauseResumeButton,
                cancelButton,
                dailyDutyRunButton,
                fullSweepRunButton,
                fubenProgressRunButton,
                fubenResetRunButton,
                medalRepeatRunButton,
                startSelectedButton
        );
        repositoryController.bind(
                repositoryHintText,
                repositorySelectedTargetsText,
                repositoryPickTargetsButton,
                repositoryViewCookieText,
                repositoryPickViewCookieButton,
                repositoryRefreshButton,
                repositoryRecordButton,
                repositoryCompareButton,
                repositoryIgnoreSelectedButton,
                repositoryRemoveIgnoredButton,
                repositoryCurrentContainer,
                repositoryDeltaContainer,
                repositoryIgnoredContainer
        );

        concurrencyInput.setText(String.valueOf(taskController.getSavedConcurrency()));
        requestIntervalInput.setText(String.valueOf(taskController.getSavedRequestInterval()));
        frequentRetryIntervalInput.setText(String.valueOf(taskController.getSavedFrequentRetryInterval()));
        dailyDutyCheckBox.setChecked(preferenceStore.isPanelDailyDutyEnabled());
        fullSweepCheckBox.setChecked(preferenceStore.isPanelDutyFullSweepEnabled());
        fubenProgressCheckBox.setChecked(preferenceStore.isPanelFubenProgressEnabled());
        medalRepeatCheckBox.setChecked(preferenceStore.isPanelMedalRepeatEnabled());
        medalRepeatInfiniteCheckBox.setChecked(preferenceStore.isMedalRepeatInfiniteEnabled());
        fubenProgressStage1CheckBox.setChecked(preferenceStore.isFubenProgressStageSelected(1));
        fubenProgressStage2CheckBox.setChecked(preferenceStore.isFubenProgressStageSelected(2));
        fubenProgressStage3CheckBox.setChecked(preferenceStore.isFubenProgressStageSelected(3));
        fubenProgressStage4CheckBox.setChecked(preferenceStore.isFubenProgressStageSelected(4));
        fubenProgressStage5CheckBox.setChecked(preferenceStore.isFubenProgressStageSelected(5));
        medalBlock1CheckBox.setChecked(preferenceStore.isMedalBlockEnabled(1));
        medalBlock2CheckBox.setChecked(preferenceStore.isMedalBlockEnabled(2));
        medalBlock3CheckBox.setChecked(preferenceStore.isMedalBlockEnabled(3));
        medalBlock4CheckBox.setChecked(preferenceStore.isMedalBlockEnabled(4));
        medalBlock5CheckBox.setChecked(preferenceStore.isMedalBlockEnabled(5));
        configureMedalRangeSpinner(medalBlock1RangeSpinner);
        configureMedalRangeSpinner(medalBlock2RangeSpinner);
        configureMedalRangeSpinner(medalBlock3RangeSpinner);
        configureMedalRangeSpinner(medalBlock4RangeSpinner);
        configureMedalRangeSpinner(medalBlock5RangeSpinner);
        setSpinnerSelection(medalBlock1RangeSpinner, preferenceStore.getMedalBlockRange(1));
        setSpinnerSelection(medalBlock2RangeSpinner, preferenceStore.getMedalBlockRange(2));
        setSpinnerSelection(medalBlock3RangeSpinner, preferenceStore.getMedalBlockRange(3));
        setSpinnerSelection(medalBlock4RangeSpinner, preferenceStore.getMedalBlockRange(4));
        setSpinnerSelection(medalBlock5RangeSpinner, preferenceStore.getMedalBlockRange(5));
        fubenProgressSettingsLayout.setVisibility(preferenceStore.isFubenSettingsExpanded() ? View.VISIBLE : View.GONE);
        medalRepeatSettingsLayout.setVisibility(preferenceStore.isMedalSettingsExpanded() ? View.VISIBLE : View.GONE);
    }

    private void bindActions() {
        dailyDutyCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setPanelDailyDutyEnabled(isChecked));
        fullSweepCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setPanelDutyFullSweepEnabled(isChecked));
        fubenProgressCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setPanelFubenProgressEnabled(isChecked));
        medalRepeatCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setPanelMedalRepeatEnabled(isChecked));
        medalRepeatInfiniteCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setMedalRepeatInfiniteEnabled(isChecked));
        fubenProgressStage1CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setFubenProgressStageSelected(1, isChecked));
        fubenProgressStage2CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setFubenProgressStageSelected(2, isChecked));
        fubenProgressStage3CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setFubenProgressStageSelected(3, isChecked));
        fubenProgressStage4CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setFubenProgressStageSelected(4, isChecked));
        fubenProgressStage5CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setFubenProgressStageSelected(5, isChecked));
        medalBlock1CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setMedalBlockEnabled(1, isChecked));
        medalBlock2CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setMedalBlockEnabled(2, isChecked));
        medalBlock3CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setMedalBlockEnabled(3, isChecked));
        medalBlock4CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setMedalBlockEnabled(4, isChecked));
        medalBlock5CheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setMedalBlockEnabled(5, isChecked));
        bindMedalRangeSpinner(medalBlock1RangeSpinner, 1);
        bindMedalRangeSpinner(medalBlock2RangeSpinner, 2);
        bindMedalRangeSpinner(medalBlock3RangeSpinner, 3);
        bindMedalRangeSpinner(medalBlock4RangeSpinner, 4);
        bindMedalRangeSpinner(medalBlock5RangeSpinner, 5);
        fubenProgressSettingsButton.setOnClickListener(v -> toggleSettingsVisibility(
                fubenProgressSettingsLayout,
                true
        ));
        medalRepeatSettingsButton.setOnClickListener(v -> toggleSettingsVisibility(
                medalRepeatSettingsLayout,
                false
        ));
        tabCookieButton.setOnClickListener(v -> switchTab(TAB_COOKIE));
        tabBasicButton.setOnClickListener(v -> switchTab(TAB_BASIC));
        tabRepositoryButton.setOnClickListener(v -> switchTab(TAB_REPOSITORY));
        tabLogButton.setOnClickListener(v -> switchTab(TAB_LOG));
        selectAllCookiesButton.setOnClickListener(v -> selectAllCookies());
        repositoryPickTargetsButton.setOnClickListener(
                v -> repositoryController.showTargetPickerDialog(featureCookieChoices)
        );
        repositoryPickViewCookieButton.setOnClickListener(
                v -> repositoryController.showViewPickerDialog(featureCookieChoices)
        );
        repositoryRefreshButton.setOnClickListener(
                v -> repositoryController.refreshSelectedTargets(featureCookieChoices)
        );
        repositoryRecordButton.setOnClickListener(
                v -> repositoryController.recordSelectedTargets(featureCookieChoices)
        );
        repositoryCompareButton.setOnClickListener(
                v -> repositoryController.compareSelectedTargets(featureCookieChoices)
        );
        pauseResumeButton.setOnClickListener(v -> {
            DutyRequestQueue.StateSnapshot snapshot = dutyRequestQueue.snapshot();
            if (snapshot.running && !snapshot.paused) {
                dutyRequestQueue.pause();
            } else if (snapshot.running) {
                dutyRequestQueue.resume();
            }
        });
        cancelButton.setOnClickListener(v -> dutyRequestQueue.cancel());
        dailyDutyRunButton.setOnClickListener(v ->
                startTaskRequests(false, FeaturePanelTaskController.TaskMode.DAILY_DUTY));
        fullSweepRunButton.setOnClickListener(v ->
                startTaskRequests(false, FeaturePanelTaskController.TaskMode.FULL_SWEEP));
        fubenProgressRunButton.setOnClickListener(v ->
                startTaskRequests(false, FeaturePanelTaskController.TaskMode.FUBEN_PROGRESS));
        medalRepeatRunButton.setOnClickListener(v ->
                startTaskRequests(false, FeaturePanelTaskController.TaskMode.MEDAL_REPEAT));
        fubenResetRunButton.setOnClickListener(v -> startFubenResetRequests());
        startSelectedButton.setOnClickListener(v -> startTaskRequests(true, null));
        storageAccessButton.setOnClickListener(v -> requestAllFilesAccessAction.run());
    }

    private void refreshCookieChoices() {
        cookieController.renderChoices(
                cookieContainer,
                cookieHintText,
                storageAccessButton,
                featureCookieChoices,
                buildCurrentPageChoice(),
                this::onCookieChoicesChanged
        );
    }

    private void onCookieChoicesChanged() {
        repositoryController.render(featureCookieChoices);
    }

    private void selectAllCookies() {
        cookieController.selectAll(featureCookieChoices);
        refreshCookieChoices();
        repositoryController.render(featureCookieChoices);
    }

    private FeatureCookieChoice buildCurrentPageChoice() {
        String currentUrl = webView == null ? null : webView.getUrl();
        if (TextUtils.isEmpty(currentUrl)) {
            return null;
        }
        Uri currentUri = Uri.parse(currentUrl);
        if (!CookieProfileManager.isSupportedSavePage(currentUri)) {
            return null;
        }

        String cookies = CookieProfileManager.selectPersistedCookiesForPage(
                currentUri,
                CookieManager.getInstance().getCookie(currentUrl)
        );
        String baseUrl = CookieProfileManager.buildRootUrl(currentUri);
        if (TextUtils.isEmpty(cookies) || TextUtils.isEmpty(baseUrl)) {
            return null;
        }

        FeatureCookieChoice choice = new FeatureCookieChoice();
        String pageTitle = webView == null ? null : webView.getTitle();
        choice.label = TextUtils.isEmpty(pageTitle) ? currentUri.getHost() : pageTitle;
        choice.pageUrl = currentUrl;
        choice.subtitle = currentUrl;
        choice.baseUrl = baseUrl;
        choice.cookies = cookies;
        choice.currentPage = true;
        choice.selected = preferenceStore.isCurrentPageCookieSelectedByDefault();
        return choice;
    }

    private void switchTab(int tab) {
        uiController.switchTab(tab, TAB_COOKIE, TAB_BASIC, TAB_REPOSITORY, TAB_LOG);
    }

    private void startTaskRequests(
            boolean startCheckedItemsOnly,
            FeaturePanelTaskController.TaskMode forcedTaskMode
    ) {
        if (concurrencyInput == null
                || requestIntervalInput == null
                || frequentRetryIntervalInput == null) {
            return;
        }
        if (dutyRequestQueue.isBusy()) {
            Toast.makeText(activity, "Request queue is already running", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean dailyDutyChecked = dailyDutyCheckBox != null && dailyDutyCheckBox.isChecked();
        boolean fullSweepChecked = fullSweepCheckBox != null && fullSweepCheckBox.isChecked();
        boolean fubenProgressChecked = fubenProgressCheckBox != null && fubenProgressCheckBox.isChecked();
        boolean medalRepeatChecked = medalRepeatCheckBox != null && medalRepeatCheckBox.isChecked();
        boolean medalRepeatInfinite = medalRepeatInfiniteCheckBox != null
                && medalRepeatInfiniteCheckBox.isChecked();
        boolean[] fubenProgressSelections = collectFubenProgressSelections();
        boolean[] medalBlockSelections = collectMedalBlockSelections();
        String[] medalBlockRangeValues = collectMedalBlockRangeValues();
        if (forcedTaskMode == FeaturePanelTaskController.TaskMode.DAILY_DUTY) {
            dailyDutyChecked = true;
            fullSweepChecked = false;
            fubenProgressChecked = false;
            medalRepeatChecked = false;
        } else if (forcedTaskMode == FeaturePanelTaskController.TaskMode.FULL_SWEEP) {
            dailyDutyChecked = false;
            fullSweepChecked = true;
            fubenProgressChecked = false;
            medalRepeatChecked = false;
        } else if (forcedTaskMode == FeaturePanelTaskController.TaskMode.FUBEN_PROGRESS) {
            dailyDutyChecked = false;
            fullSweepChecked = false;
            fubenProgressChecked = true;
            medalRepeatChecked = false;
            medalRepeatInfinite = false;
        } else if (forcedTaskMode == FeaturePanelTaskController.TaskMode.MEDAL_REPEAT) {
            dailyDutyChecked = false;
            fullSweepChecked = false;
            fubenProgressChecked = false;
            medalRepeatChecked = true;
        }
        FeaturePanelTaskController.BuildResult buildResult = taskController.buildStartRequest(
                featureCookieChoices,
                concurrencyInput.getText().toString(),
                requestIntervalInput.getText().toString(),
                frequentRetryIntervalInput.getText().toString(),
                dailyDutyChecked,
                fullSweepChecked,
                fubenProgressChecked,
                fubenProgressSelections,
                medalRepeatChecked,
                medalBlockSelections,
                medalBlockRangeValues,
                medalRepeatInfinite,
                startCheckedItemsOnly
        );
        concurrencyInput.setText(String.valueOf(taskController.getSavedConcurrency()));
        requestIntervalInput.setText(String.valueOf(taskController.getSavedRequestInterval()));
        frequentRetryIntervalInput.setText(String.valueOf(taskController.getSavedFrequentRetryInterval()));
        if (buildResult.errorMessage != null) {
            Toast.makeText(activity, buildResult.errorMessage, Toast.LENGTH_SHORT).show();
            return;
        }

        FeaturePanelTaskController.StartRequest request = buildResult.request;
        prepareDailyMedalIntegralState(request);
        dutyRequestQueue.startRequests(request);
    }

    private void startFubenResetRequests() {
        if (concurrencyInput == null
                || requestIntervalInput == null
                || frequentRetryIntervalInput == null) {
            return;
        }
        if (dutyRequestQueue.isBusy()) {
            Toast.makeText(activity, "Request queue is already running", Toast.LENGTH_SHORT).show();
            return;
        }

        int concurrency = parsePositiveInt(concurrencyInput.getText().toString(), 1);
        int requestIntervalMs = parseNonNegativeInt(requestIntervalInput.getText().toString(), 700);
        int frequentRetryIntervalMs = parseNonNegativeInt(
                frequentRetryIntervalInput.getText().toString(),
                14000
        );

        LinkedHashMap<String, DutyRequestQueue.CookieTarget> deduplicatedTargets = new LinkedHashMap<>();
        for (FeatureCookieChoice choice : featureCookieChoices) {
            if (!choice.selected || TextUtils.isEmpty(choice.baseUrl) || TextUtils.isEmpty(choice.cookies)) {
                continue;
            }
            String key = choice.baseUrl + "\n" + choice.cookies;
            if (!deduplicatedTargets.containsKey(key)) {
                deduplicatedTargets.put(key, new DutyRequestQueue.CookieTarget(
                        choice.currentPage ? "当前页面 Cookie" : choice.label,
                        choice.baseUrl,
                        choice.cookies
                ));
            }
        }

        if (deduplicatedTargets.isEmpty()) {
            Toast.makeText(activity, "请先勾选至少一个可用 Cookie。", Toast.LENGTH_SHORT).show();
            return;
        }

        dutyRequestQueue.startFubenResetRequests(
                new ArrayList<>(deduplicatedTargets.values()),
                concurrency,
                requestIntervalMs,
                frequentRetryIntervalMs
        );
    }

    private int parsePositiveInt(String value, int fallback) {
        if (TextUtils.isEmpty(value)) {
            return Math.max(1, fallback);
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (Exception e) {
            return Math.max(1, fallback);
        }
    }

    private int parseNonNegativeInt(String value, int fallback) {
        if (TextUtils.isEmpty(value)) {
            return Math.max(0, fallback);
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Exception e) {
            return Math.max(0, fallback);
        }
    }

    private void clearReferences() {
        dialog = null;
        tabCookieButton = null;
        tabBasicButton = null;
        tabRepositoryButton = null;
        tabLogButton = null;
        cookiePage = null;
        basicPage = null;
        repositoryPage = null;
        logPage = null;
        cookieContainer = null;
        cookieHintText = null;
        selectAllCookiesButton = null;
        concurrencyInput = null;
        requestIntervalInput = null;
        frequentRetryIntervalInput = null;
        pauseResumeButton = null;
        cancelButton = null;
        dailyDutyCheckBox = null;
        fullSweepCheckBox = null;
        fubenProgressCheckBox = null;
        fubenProgressSettingsLayout = null;
        fubenProgressSettingsButton = null;
        fubenProgressRunButton = null;
        fubenProgressStage1CheckBox = null;
        fubenProgressStage2CheckBox = null;
        fubenProgressStage3CheckBox = null;
        fubenProgressStage4CheckBox = null;
        fubenProgressStage5CheckBox = null;
        medalRepeatCheckBox = null;
        medalRepeatInfiniteCheckBox = null;
        medalRepeatSettingsLayout = null;
        medalRepeatSettingsButton = null;
        medalRepeatRunButton = null;
        medalBlock1CheckBox = null;
        medalBlock2CheckBox = null;
        medalBlock3CheckBox = null;
        medalBlock4CheckBox = null;
        medalBlock5CheckBox = null;
        medalBlock1RangeSpinner = null;
        medalBlock2RangeSpinner = null;
        medalBlock3RangeSpinner = null;
        medalBlock4RangeSpinner = null;
        medalBlock5RangeSpinner = null;
        dailyDutyRunButton = null;
        fullSweepRunButton = null;
        fubenResetRunButton = null;
        startSelectedButton = null;
        storageAccessButton = null;
        repositoryHintText = null;
        repositorySelectedTargetsText = null;
        repositoryPickTargetsButton = null;
        repositoryViewCookieText = null;
        repositoryPickViewCookieButton = null;
        repositoryRefreshButton = null;
        repositoryRecordButton = null;
        repositoryCompareButton = null;
        repositoryIgnoreSelectedButton = null;
        repositoryRemoveIgnoredButton = null;
        repositoryCurrentContainer = null;
        repositoryDeltaContainer = null;
        repositoryIgnoredContainer = null;
        queueStatusText = null;
        queueLogText = null;
        uiController.clear();
        repositoryController.clear();
        featureCookieChoices.clear();
    }

    private void toggleSettingsVisibility(View targetView, boolean fubenSettings) {
        if (targetView == null) {
            return;
        }
        boolean expanded = targetView.getVisibility() != View.VISIBLE;
        targetView.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (fubenSettings) {
            preferenceStore.setFubenSettingsExpanded(expanded);
        } else {
            preferenceStore.setMedalSettingsExpanded(expanded);
        }
    }

    private void configureMedalRangeSpinner(Spinner spinner) {
        if (spinner == null) {
            return;
        }
        ArrayList<String> values = new ArrayList<>();
        values.add("1");
        values.add("2");
        values.add("3");
        values.add("4");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_item,
                values
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(3, false);
    }

    private void setSpinnerSelection(Spinner spinner, int value) {
        if (spinner == null) {
            return;
        }
        int index = value - 1;
        if (index < 0) {
            index = 0;
        }
        if (index > 3) {
            index = 3;
        }
        spinner.setSelection(index, false);
    }

    private void bindMedalRangeSpinner(Spinner spinner, int blockNumber) {
        if (spinner == null) {
            return;
        }
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                preferenceStore.setMedalBlockRange(blockNumber, position + 1);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private boolean[] collectFubenProgressSelections() {
        return new boolean[] {
                fubenProgressStage1CheckBox != null && fubenProgressStage1CheckBox.isChecked(),
                fubenProgressStage2CheckBox != null && fubenProgressStage2CheckBox.isChecked(),
                fubenProgressStage3CheckBox != null && fubenProgressStage3CheckBox.isChecked(),
                fubenProgressStage4CheckBox != null && fubenProgressStage4CheckBox.isChecked(),
                fubenProgressStage5CheckBox != null && fubenProgressStage5CheckBox.isChecked()
        };
    }

    private boolean[] collectMedalBlockSelections() {
        return new boolean[] {
                medalBlock1CheckBox != null && medalBlock1CheckBox.isChecked(),
                medalBlock2CheckBox != null && medalBlock2CheckBox.isChecked(),
                medalBlock3CheckBox != null && medalBlock3CheckBox.isChecked(),
                medalBlock4CheckBox != null && medalBlock4CheckBox.isChecked(),
                medalBlock5CheckBox != null && medalBlock5CheckBox.isChecked()
        };
    }

    private String[] collectMedalBlockRangeValues() {
        return new String[] {
                getSpinnerValue(medalBlock1RangeSpinner),
                getSpinnerValue(medalBlock2RangeSpinner),
                getSpinnerValue(medalBlock3RangeSpinner),
                getSpinnerValue(medalBlock4RangeSpinner),
                getSpinnerValue(medalBlock5RangeSpinner)
        };
    }

    private String getSpinnerValue(Spinner spinner) {
        if (spinner == null || spinner.getSelectedItem() == null) {
            return "4";
        }
        return String.valueOf(spinner.getSelectedItem());
    }

    private void prepareDailyMedalIntegralState(FeaturePanelTaskController.StartRequest request) {
        if (request == null || request.targets == null || request.medalBlockRequests == null) {
            return;
        }
        request.medalDailyIntegralKeys.clear();
        for (DutyRequestQueue.CookieTarget target : request.targets) {
            if (target == null) {
                continue;
            }
            String targetCookieKey = target.uniqueKey();
            for (FeaturePanelTaskController.MedalBlockRequest block : request.medalBlockRequests) {
                if (block == null) {
                    continue;
                }
                if (preferenceStore.shouldRunDailyMedalIntegral(targetCookieKey, block.blockNumber)) {
                    request.medalDailyIntegralKeys.add(buildMedalDailyIntegralRequestKey(target, block.blockNumber));
                    preferenceStore.markDailyMedalIntegralDone(targetCookieKey, block.blockNumber);
                }
            }
        }
    }

    private String buildMedalDailyIntegralRequestKey(DutyRequestQueue.CookieTarget target, int blockNumber) {
        String targetKey = target == null ? "" : target.uniqueKey();
        return targetKey + "#medalIntegral#" + blockNumber;
    }

    private int dpToPx(int dp) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

final class BrowserFullscreenController {
    private final AppCompatActivity activity;
    private final WebView webView;
    private final ProgressBar progressBar;
    private final View topBar;
    private final View browserChrome;
    private final FrameLayout fullscreenContainer;
    private final ImageButton fullscreenRotateButton;
    private final ImageButton fullscreenExitButton;
    private final ImageButton fullscreenFeaturePanelButton;
    private final Runnable restoreOrientationAction;
    private int topBarBasePaddingLeft;
    private int topBarBasePaddingTop;
    private int topBarBasePaddingRight;
    private int topBarBasePaddingBottom;
    private int browserChromeBasePaddingLeft;
    private int browserChromeBasePaddingTop;
    private int browserChromeBasePaddingRight;
    private int browserChromeBasePaddingBottom;
    private int fullscreenRotateBaseTopMargin;
    private int fullscreenRotateBaseRightMargin;
    private int fullscreenExitBaseTopMargin;
    private int fullscreenExitBaseRightMargin;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean inAppRuffleFullscreen;

    BrowserFullscreenController(
            AppCompatActivity activity,
            WebView webView,
            ProgressBar progressBar,
            View topBar,
            View browserChrome,
            FrameLayout fullscreenContainer,
            ImageButton fullscreenRotateButton,
            ImageButton fullscreenExitButton,
            ImageButton fullscreenFeaturePanelButton,
            Runnable restoreOrientationAction
    ) {
        this.activity = activity;
        this.webView = webView;
        this.progressBar = progressBar;
        this.topBar = topBar;
        this.browserChrome = browserChrome;
        this.fullscreenContainer = fullscreenContainer;
        this.fullscreenRotateButton = fullscreenRotateButton;
        this.fullscreenExitButton = fullscreenExitButton;
        this.fullscreenFeaturePanelButton = fullscreenFeaturePanelButton;
        this.restoreOrientationAction = restoreOrientationAction;
    }

    void initializeWindowInsets() {
        captureInsetAwareBaseValues();
        installWindowInsetHandlers();
    }

    void toggleRuffleFullscreenCompat() {
        if (customView != null) {
            hideCustomView();
            return;
        }

        String script =
                "(function(){"
                        + "window.__androidRuffleFullscreen=window.__androidRuffleFullscreen||{};"
                        + "var state=window.__androidRuffleFullscreen;"
                        + "if(state.active&&state.target){"
                        + "if(state.originalStyle===null){state.target.removeAttribute('style');}"
                        + "else{state.target.setAttribute('style',state.originalStyle);}"
                        + "document.documentElement.style.overflow=state.htmlOverflow||'';"
                        + "if(document.body){document.body.style.overflow=state.bodyOverflow||'';}"
                        + "state.active=false;"
                        + "state.target=null;"
                        + "state.originalStyle=null;"
                        + "return 'exit';"
                        + "}"
                        + "var target=document.querySelector('ruffle-player, ruffle-embed, ruffle-object');"
                        + "if(!target){"
                        + "target=document.querySelector('embed[type*=\\\"shockwave\\\"], object[type*=\\\"shockwave\\\"], embed[src*=\\\".swf\\\"], object[data*=\\\".swf\\\"]');"
                        + "}"
                        + "if(!target){return 'not-found';}"
                        + "state.target=target;"
                        + "state.originalStyle=target.getAttribute('style');"
                        + "state.htmlOverflow=document.documentElement.style.overflow||'';"
                        + "state.bodyOverflow=document.body?document.body.style.overflow||'':'';"
                        + "document.documentElement.style.overflow='hidden';"
                        + "if(document.body){document.body.style.overflow='hidden';}"
                        + "target.style.position='fixed';"
                        + "target.style.left='0';"
                        + "target.style.top='0';"
                        + "target.style.width='100vw';"
                        + "target.style.height='100vh';"
                        + "target.style.maxWidth='100vw';"
                        + "target.style.maxHeight='100vh';"
                        + "target.style.margin='0';"
                        + "target.style.padding='0';"
                        + "target.style.zIndex='2147483647';"
                        + "target.style.background='#000';"
                        + "state.active=true;"
                        + "return 'enter';"
                        + "})();";

        webView.evaluateJavascript(script, value -> {
            if (value == null) {
                Toast.makeText(activity, "Unable to toggle Ruffle fullscreen", Toast.LENGTH_SHORT).show();
                return;
            }

            String normalized = value.replace("\"", "");
            if ("enter".equals(normalized)) {
                enterInAppRuffleFullscreenMode();
            } else if ("exit".equals(normalized)) {
                exitInAppRuffleFullscreenMode();
            } else if ("not-found".equals(normalized)) {
                Toast.makeText(activity, "Current page has no Ruffle player", Toast.LENGTH_SHORT).show();
            }
        });
    }

    void showCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        if (view == null) {
            if (callback != null) {
                callback.onCustomViewHidden();
            }
            return;
        }
        if (customView != null) {
            if (callback != null) {
                callback.onCustomViewHidden();
            }
            return;
        }

        customView = view;
        customViewCallback = callback;
        if (browserChrome != null) {
            browserChrome.setVisibility(View.GONE);
        }
        showFullscreenControls();
        fullscreenContainer.setVisibility(View.VISIBLE);
        fullscreenContainer.removeAllViews();
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setSystemBarsHidden(true);
        requestInsetRefresh();
    }

    void hideCustomView() {
        if (customView == null) {
            return;
        }

        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        if (browserChrome != null) {
            browserChrome.setVisibility(View.VISIBLE);
        }
        hideFullscreenControls();
        setSystemBarsHidden(false);
        restoreOrientationAction.run();
        requestInsetRefresh();
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }
        customView = null;
        customViewCallback = null;
    }

    void rotateFullscreenOrientation() {
        if (customView == null && !inAppRuffleFullscreen) {
            return;
        }

        int orientation = activity.getResources().getConfiguration().orientation;
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    void handleExitButtonClick() {
        if (customView != null) {
            hideCustomView();
        } else if (inAppRuffleFullscreen) {
            toggleRuffleFullscreenCompat();
        }
    }

    void resetForNavigation() {
        if (customView != null) {
            hideCustomView();
        }
        if (inAppRuffleFullscreen) {
            exitInAppRuffleFullscreenMode();
        }
    }

    void ensureStateMatchesPage() {
        if (!inAppRuffleFullscreen) {
            return;
        }

        String script =
                "(function(){"
                        + "var state=window.__androidRuffleFullscreen||null;"
                        + "var target=document.querySelector('ruffle-player, ruffle-embed, ruffle-object');"
                        + "if(!target){target=document.querySelector('embed[type*=\\\"shockwave\\\"], object[type*=\\\"shockwave\\\"], embed[src*=\\\".swf\\\"], object[data*=\\\".swf\\\"]');}"
                        + "return !!(state&&state.active&&state.target&&target);"
                        + "})();";
        webView.evaluateJavascript(script, value -> {
            boolean stillFullscreen = "true".equalsIgnoreCase(value == null ? "" : value.replace("\"", ""));
            if (!stillFullscreen && inAppRuffleFullscreen) {
                exitInAppRuffleFullscreenMode();
            }
        });
    }

    boolean handleBackPressed() {
        if (customView != null) {
            hideCustomView();
            return true;
        }
        if (inAppRuffleFullscreen) {
            toggleRuffleFullscreenCompat();
            return true;
        }
        return false;
    }

    boolean isFullscreenActive() {
        return customView != null || inAppRuffleFullscreen;
    }

    private void enterInAppRuffleFullscreenMode() {
        if (inAppRuffleFullscreen) {
            return;
        }

        inAppRuffleFullscreen = true;
        if (topBar != null) {
            topBar.setVisibility(View.GONE);
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        showFullscreenControls();
        setSystemBarsHidden(true);
        requestInsetRefresh();
    }

    private void exitInAppRuffleFullscreenMode() {
        if (!inAppRuffleFullscreen) {
            return;
        }

        inAppRuffleFullscreen = false;
        if (topBar != null) {
            topBar.setVisibility(View.VISIBLE);
        }
        if (progressBar != null) {
            progressBar.setVisibility(progressBar.getProgress() >= 100 ? View.GONE : View.VISIBLE);
        }
        hideFullscreenControls();
        setSystemBarsHidden(false);
        restoreOrientationAction.run();
        requestInsetRefresh();
    }

    private void showFullscreenControls() {
        if (fullscreenRotateButton != null) {
            fullscreenRotateButton.setVisibility(View.VISIBLE);
        }
        if (fullscreenExitButton != null) {
            fullscreenExitButton.setVisibility(View.VISIBLE);
        }
        if (fullscreenFeaturePanelButton != null) {
            fullscreenFeaturePanelButton.setVisibility(View.VISIBLE);
        }
    }

    private void hideFullscreenControls() {
        if (fullscreenRotateButton != null) {
            fullscreenRotateButton.setVisibility(View.GONE);
        }
        if (fullscreenExitButton != null) {
            fullscreenExitButton.setVisibility(View.GONE);
        }
        if (fullscreenFeaturePanelButton != null) {
            fullscreenFeaturePanelButton.setVisibility(View.GONE);
        }
    }

    private void captureInsetAwareBaseValues() {
        if (topBar != null) {
            topBarBasePaddingLeft = topBar.getPaddingLeft();
            topBarBasePaddingTop = topBar.getPaddingTop();
            topBarBasePaddingRight = topBar.getPaddingRight();
            topBarBasePaddingBottom = topBar.getPaddingBottom();
        }
        if (browserChrome != null) {
            browserChromeBasePaddingLeft = browserChrome.getPaddingLeft();
            browserChromeBasePaddingTop = browserChrome.getPaddingTop();
            browserChromeBasePaddingRight = browserChrome.getPaddingRight();
            browserChromeBasePaddingBottom = browserChrome.getPaddingBottom();
        }
        if (fullscreenRotateButton != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fullscreenRotateButton.getLayoutParams();
            fullscreenRotateBaseTopMargin = params.topMargin;
            fullscreenRotateBaseRightMargin = params.rightMargin;
        }
        if (fullscreenExitButton != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fullscreenExitButton.getLayoutParams();
            fullscreenExitBaseTopMargin = params.topMargin;
            fullscreenExitBaseRightMargin = params.rightMargin;
        }
    }

    private void installWindowInsetHandlers() {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            boolean fullscreenActive = isFullscreenActive();
            if (browserChrome != null) {
                int left = fullscreenActive ? 0 : browserChromeBasePaddingLeft + systemBars.left;
                int top = fullscreenActive ? 0 : browserChromeBasePaddingTop;
                int right = fullscreenActive ? 0 : browserChromeBasePaddingRight + systemBars.right;
                int bottom = fullscreenActive ? 0 : browserChromeBasePaddingBottom + systemBars.bottom;
                browserChrome.setPadding(left, top, right, bottom);
            }
            if (topBar != null) {
                topBar.setPadding(
                        topBarBasePaddingLeft + systemBars.left,
                        topBarBasePaddingTop + systemBars.top,
                        topBarBasePaddingRight + systemBars.right,
                        topBarBasePaddingBottom
                );
            }
            applyFullscreenButtonInsets(systemBars);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void applyFullscreenButtonInsets(Insets systemBars) {
        if (fullscreenRotateButton != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fullscreenRotateButton.getLayoutParams();
            params.topMargin = fullscreenRotateBaseTopMargin + systemBars.top;
            params.rightMargin = fullscreenRotateBaseRightMargin + systemBars.right;
            fullscreenRotateButton.setLayoutParams(params);
        }
        if (fullscreenExitButton != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fullscreenExitButton.getLayoutParams();
            params.topMargin = fullscreenExitBaseTopMargin + systemBars.top;
            params.rightMargin = fullscreenExitBaseRightMargin + systemBars.right;
            fullscreenExitButton.setLayoutParams(params);
        }
    }

    private void requestInsetRefresh() {
        View root = activity.findViewById(android.R.id.content);
        if (root != null) {
            ViewCompat.requestApplyInsets(root);
        }
    }

    private void setSystemBarsHidden(boolean hidden) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller == null) {
            return;
        }
        if (hidden) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }
}

final class BrowserTouchController {
    private static final long HOVER_HOLD_MS = 450L;
    private static final long MENU_HOLD_MS = 700L;
    private static final float TOUCH_HOLD_MOVE_TOLERANCE_DP = 18f;

    private final AppCompatActivity activity;
    private final WebView webView;
    private final ScaleGestureDetector scaleGestureDetector;
    private final float touchHoldMoveTolerancePx;

    private float gestureAnchorX;
    private float gestureAnchorY;
    private boolean simulatedHoverActive;
    private boolean consumeTouchUntilGestureEnd;
    private boolean hoverModeArmedForClick;
    private boolean hoverEnteredByHold;
    private boolean flashTouchBridgeAvailable;
    private boolean syntheticMouseHoverActive;
    private boolean nativeScrollPreferred;

    private final Runnable hoverHoldRunnable = () -> {
        dispatchTouchBridgeBooleanCall("setNativeTouchBlocked", true);
        if (dispatchSyntheticMouseHover(gestureAnchorX, gestureAnchorY, true)) {
            simulatedHoverActive = true;
            consumeTouchUntilGestureEnd = true;
            hoverModeArmedForClick = true;
            hoverEnteredByHold = true;
        }
    };
    private final Runnable menuHoldRunnable = () -> {
        if (dispatchTouchBridgeCall("contextMenuAt", gestureAnchorX, gestureAnchorY)) {
            consumeTouchUntilGestureEnd = true;
        }
    };

    BrowserTouchController(AppCompatActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.touchHoldMoveTolerancePx =
                activity.getResources().getDisplayMetrics().density * TOUCH_HOLD_MOVE_TOLERANCE_DP;
        this.scaleGestureDetector =
                new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float factor = detector.getScaleFactor();
                        if (Float.isNaN(factor) || Float.isInfinite(factor)) {
                            return false;
                        }

                        factor = Math.max(0.75f, Math.min(1.25f, factor));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            BrowserTouchController.this.webView.zoomBy(factor);
                        } else if (factor > 1.02f) {
                            BrowserTouchController.this.webView.zoomIn();
                        } else if (factor < 0.98f) {
                            BrowserTouchController.this.webView.zoomOut();
                        }
                        return true;
                    }
                });
        this.webView.setHapticFeedbackEnabled(false);
        this.webView.setOnLongClickListener(v -> true);
    }

    boolean handleTouch(MotionEvent event) {
        if (event == null) {
            return false;
        }

        if (isSyntheticMouseEvent(event)) {
            return false;
        }

        scaleGestureDetector.onTouchEvent(event);
        boolean shouldConsume = scaleGestureDetector.isInProgress() || consumeTouchUntilGestureEnd;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelPendingTouchGestures();
                dispatchTouchBridgeBooleanCall("setNativeTouchBlocked", false);
                simulatedHoverActive = false;
                consumeTouchUntilGestureEnd = false;
                hoverModeArmedForClick = false;
                hoverEnteredByHold = false;
                syntheticMouseHoverActive = false;
                nativeScrollPreferred = false;
                gestureAnchorX = event.getX();
                gestureAnchorY = event.getY();
                webView.postDelayed(hoverHoldRunnable, HOVER_HOLD_MS);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                cancelPendingTouchGestures();
                if (simulatedHoverActive) {
                    dispatchSyntheticMouseExit(event.getX(), event.getY());
                    dispatchTouchBridgeBooleanCall("setNativeTouchBlocked", false);
                    simulatedHoverActive = false;
                    hoverModeArmedForClick = false;
                    hoverEnteredByHold = false;
                    syntheticMouseHoverActive = false;
                }
                if (event.getPointerCount() >= 2) {
                    gestureAnchorX = averageTouchX(event);
                    gestureAnchorY = averageTouchY(event);
                    webView.postDelayed(menuHoldRunnable, MENU_HOLD_MS);
                }
                shouldConsume = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (scaleGestureDetector.isInProgress()) {
                    cancelPendingTouchGestures();
                    if (simulatedHoverActive) {
                        dispatchSyntheticMouseExit(event.getX(), event.getY());
                        dispatchTouchBridgeBooleanCall("setNativeTouchBlocked", false);
                        simulatedHoverActive = false;
                        syntheticMouseHoverActive = false;
                    }
                    hoverModeArmedForClick = false;
                    hoverEnteredByHold = false;
                    shouldConsume = true;
                    break;
                }

                if (simulatedHoverActive && event.getPointerCount() == 1) {
                    dispatchSyntheticMouseHover(event.getX(), event.getY(), false);
                    hoverModeArmedForClick = true;
                    shouldConsume = true;
                    break;
                }

                if (movedBeyondTouchTolerance(event)) {
                    cancelPendingTouchGestures();
                    if (event.getPointerCount() == 1 && !simulatedHoverActive) {
                        float dx = event.getX() - gestureAnchorX;
                        float dy = event.getY() - gestureAnchorY;
                        boolean mostlyVerticalMove = Math.abs(dy) > Math.abs(dx) * 1.15f;
                        if (mostlyVerticalMove) {
                            nativeScrollPreferred = true;
                        } else {
                            dispatchTouchBridgeBooleanCall("setNativeTouchBlocked", true);
                            dispatchSyntheticMouseHover(event.getX(), event.getY(), true);
                            simulatedHoverActive = true;
                            consumeTouchUntilGestureEnd = true;
                            hoverModeArmedForClick = true;
                            hoverEnteredByHold = false;
                            syntheticMouseHoverActive = true;
                            shouldConsume = true;
                        }
                    }
                }
                shouldConsume = shouldConsume || (!nativeScrollPreferred && event.getPointerCount() >= 2);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                cancelPendingTouchGestures();
                hoverModeArmedForClick = false;
                hoverEnteredByHold = false;
                shouldConsume = true;
                break;
            case MotionEvent.ACTION_UP:
                cancelPendingTouchGestures();
                if (simulatedHoverActive) {
                    if (hoverModeArmedForClick && !hoverEnteredByHold) {
                        dispatchSyntheticMouseHover(event.getX(), event.getY(), false);
                        dispatchSyntheticMouseClick(event.getX(), event.getY());
                        dispatchSyntheticMouseExit(event.getX(), event.getY());
                        dispatchTouchBridgeBooleanCall("setNativeTouchBlocked", false);
                        simulatedHoverActive = false;
                    } else if (!hoverEnteredByHold) {
                        dispatchSyntheticMouseExit(event.getX(), event.getY());
                        dispatchTouchBridgeBooleanCall("setNativeTouchBlocked", false);
                        simulatedHoverActive = false;
                    } else {
                        dispatchTouchBridgeBooleanCall("setNativeTouchBlocked", false);
                    }
                }
                shouldConsume = shouldConsume || consumeTouchUntilGestureEnd || hoverModeArmedForClick;
                consumeTouchUntilGestureEnd = false;
                hoverModeArmedForClick = false;
                hoverEnteredByHold = false;
                syntheticMouseHoverActive = false;
                nativeScrollPreferred = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelPendingTouchGestures();
                if (simulatedHoverActive) {
                    dispatchSyntheticMouseExit(event.getX(), event.getY());
                    dispatchTouchBridgeBooleanCall("setNativeTouchBlocked", false);
                    simulatedHoverActive = false;
                    syntheticMouseHoverActive = false;
                }
                shouldConsume = shouldConsume || consumeTouchUntilGestureEnd;
                consumeTouchUntilGestureEnd = false;
                hoverModeArmedForClick = false;
                hoverEnteredByHold = false;
                nativeScrollPreferred = false;
                break;
            default:
                shouldConsume = shouldConsume || (!nativeScrollPreferred && event.getPointerCount() >= 2);
                break;
        }

        return shouldConsume || (!nativeScrollPreferred && event.getPointerCount() >= 2);
    }

    void resetBridgeAvailability() {
        flashTouchBridgeAvailable = false;
    }

    void refreshBridgeAvailability() {
        String script =
                "(function(){"
                        + "return !!document.querySelector("
                        + "'ruffle-player,ruffle-embed,ruffle-object,"
                        + "embed[type*=\\\"shockwave\\\"],object[type*=\\\"shockwave\\\"],"
                        + "embed[src*=\\\".swf\\\"],object[data*=\\\".swf\\\"]'"
                        + ");"
                        + "})();";
        webView.evaluateJavascript(script, value -> {
            String normalized = value == null ? "" : value.replace("\"", "").trim();
            flashTouchBridgeAvailable = "true".equalsIgnoreCase(normalized);
        });
    }

    private void cancelPendingTouchGestures() {
        webView.removeCallbacks(hoverHoldRunnable);
        webView.removeCallbacks(menuHoldRunnable);
    }

    private boolean isSyntheticMouseEvent(MotionEvent event) {
        if (event.getPointerCount() <= 0) {
            return false;
        }
        return ((event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE)
                || event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE;
    }

    private boolean dispatchSyntheticMouseHover(float x, float y, boolean entering) {
        long now = SystemClock.uptimeMillis();
        if (entering || !syntheticMouseHoverActive) {
            MotionEvent enterEvent = obtainMouseMotionEvent(
                    now,
                    now,
                    MotionEvent.ACTION_HOVER_ENTER,
                    x,
                    y,
                    0
            );
            webView.dispatchGenericMotionEvent(enterEvent);
            enterEvent.recycle();
            syntheticMouseHoverActive = true;
        }

        MotionEvent hoverEvent = obtainMouseMotionEvent(
                now,
                now,
                MotionEvent.ACTION_HOVER_MOVE,
                x,
                y,
                0
        );
        webView.dispatchGenericMotionEvent(hoverEvent);
        hoverEvent.recycle();
        return true;
    }

    private boolean dispatchSyntheticMouseClick(float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        long upTime = downTime + 16L;

        MotionEvent downEvent = obtainMouseMotionEvent(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                MotionEvent.BUTTON_PRIMARY
        );
        webView.dispatchTouchEvent(downEvent);
        downEvent.recycle();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MotionEvent buttonPressEvent = obtainMouseMotionEvent(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_BUTTON_PRESS,
                    x,
                    y,
                    MotionEvent.BUTTON_PRIMARY
            );
            webView.dispatchGenericMotionEvent(buttonPressEvent);
            buttonPressEvent.recycle();
        }

        MotionEvent upEvent = obtainMouseMotionEvent(
                downTime,
                upTime,
                MotionEvent.ACTION_UP,
                x,
                y,
                0
        );
        webView.dispatchTouchEvent(upEvent);
        upEvent.recycle();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MotionEvent buttonReleaseEvent = obtainMouseMotionEvent(
                    downTime,
                    upTime,
                    MotionEvent.ACTION_BUTTON_RELEASE,
                    x,
                    y,
                    MotionEvent.BUTTON_PRIMARY
            );
            webView.dispatchGenericMotionEvent(buttonReleaseEvent);
            buttonReleaseEvent.recycle();
        }
        return true;
    }

    private boolean dispatchSyntheticMouseExit(float x, float y) {
        if (!syntheticMouseHoverActive) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent exitEvent = obtainMouseMotionEvent(
                now,
                now,
                MotionEvent.ACTION_HOVER_EXIT,
                x,
                y,
                0
        );
        webView.dispatchGenericMotionEvent(exitEvent);
        exitEvent.recycle();
        syntheticMouseHoverActive = false;
        return true;
    }

    private MotionEvent obtainMouseMotionEvent(
            long downTime,
            long eventTime,
            int action,
            float x,
            float y,
            int buttonState
    ) {
        MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.id = 0;
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.x = x;
        pointerCoords.y = y;
        pointerCoords.pressure = buttonState == 0 ? 0f : 1f;
        pointerCoords.size = 1f;

        return MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1,
                new MotionEvent.PointerProperties[]{pointerProperties},
                new MotionEvent.PointerCoords[]{pointerCoords},
                0,
                buttonState,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_MOUSE,
                0
        );
    }

    private boolean movedBeyondTouchTolerance(MotionEvent event) {
        float x = event.getPointerCount() >= 2 ? averageTouchX(event) : event.getX();
        float y = event.getPointerCount() >= 2 ? averageTouchY(event) : event.getY();
        float dx = x - gestureAnchorX;
        float dy = y - gestureAnchorY;
        return (dx * dx) + (dy * dy) > touchHoldMoveTolerancePx * touchHoldMoveTolerancePx;
    }

    private float averageTouchX(MotionEvent event) {
        float sum = 0f;
        int count = event.getPointerCount();
        for (int i = 0; i < count; i += 1) {
            sum += event.getX(i);
        }
        return count <= 0 ? 0f : sum / count;
    }

    private float averageTouchY(MotionEvent event) {
        float sum = 0f;
        int count = event.getPointerCount();
        for (int i = 0; i < count; i += 1) {
            sum += event.getY(i);
        }
        return count <= 0 ? 0f : sum / count;
    }

    private boolean dispatchTouchBridgeCall(String method, float x, float y) {
        if (TextUtils.isEmpty(method)) {
            return false;
        }
        String script = "(function(){var bridge=window.__ruffleWrapperTouchBridge;"
                + "return !!(bridge&&bridge." + method + "&&bridge." + method + "(" + x + "," + y + "));})();";
        webView.evaluateJavascript(script, value -> { });
        return true;
    }

    private boolean dispatchTouchBridgeBooleanCall(String method, boolean value) {
        if (TextUtils.isEmpty(method)) {
            return false;
        }
        String script = "(function(){var bridge=window.__ruffleWrapperTouchBridge;"
                + "return !!(bridge&&bridge." + method + "&&bridge." + method + "(" + value + "));})();";
        webView.evaluateJavascript(script, result -> { });
        return true;
    }
}

final class BrowserRequestController {
    interface Host {
        int getSelectedFontMode();
    }

    private static final String TAG = "FlashBrowser";
    private static final String IE_USER_AGENT =
            "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; Trident/6.0)";
    private static final String IE_ACCEPT =
            "text/html, application/xhtml+xml, */*";
    private static final String IE_ACCEPT_LANGUAGE =
            "zh-CN,zh;q=0.9,en;q=0.8";
    private static final String RUFFLE_PATH_PREFIX = "/__ruffle__/";
    private static final String PROXY_PATH_PREFIX = "/__proxy__/";
    private static final String BOOTSTRAP_SCRIPT = "bootstrap.js";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int FONT_MODE_CHINESE_SANS = 0;
    private static final int FONT_MODE_CHINESE_SERIF = 1;
    private static final int FONT_MODE_EMBEDDED = 2;
    private static final Pattern CHARSET_PATTERN =
            Pattern.compile("charset=([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSP_META_PATTERN =
            Pattern.compile(
                    "(?is)<meta[^>]+http-equiv\\s*=\\s*['\\\"]Content-Security-Policy['\\\"][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );
    private static final Pattern VIEWPORT_META_PATTERN =
            Pattern.compile(
                    "(?is)<meta[^>]+name\\s*=\\s*['\\\"]viewport['\\\"][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );
    private static final int LEGACY_PAGE_MIN_VIEWPORT_WIDTH = 1000;
    private static final int LEGACY_PAGE_MAX_VIEWPORT_WIDTH = 4096;
    private static final Set<String> HOP_BY_HOP_HEADERS = new HashSet<>(Arrays.asList(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade"
    ));
    private static final Set<String> SKIPPED_REQUEST_HEADERS = new HashSet<>(Arrays.asList(
            "host",
            "connection",
            "accept-encoding",
            "cookie"
    ));
    private static final Set<String> OVERRIDDEN_IE_REQUEST_HEADERS = new HashSet<>(Arrays.asList(
            "user-agent",
            "accept",
            "accept-language",
            "cache-control",
            "pragma"
    ));
    private static final Set<String> STRIPPED_RESPONSE_HEADERS = new HashSet<>(Arrays.asList(
            "content-encoding",
            "content-length",
            "content-security-policy",
            "content-security-policy-report-only",
            "x-frame-options"
    ));

    private final AppCompatActivity activity;
    private final LocalMappingManager localMappingManager;
    private final Host host;

    BrowserRequestController(
            AppCompatActivity activity,
            LocalMappingManager localMappingManager,
            Host host
    ) {
        this.activity = activity;
        this.localMappingManager = localMappingManager;
        this.host = host;
    }

    WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
        if (request == null) {
            return null;
        }

        Uri url = request.getUrl();
        if (url == null) {
            return null;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return createEmptyCorsResponse();
        }

        if (isRuffleAssetRequest(url)) {
            return serveRuffleAsset(url);
        }

        if (isProxyAssetRequest(url)) {
            Uri targetUrl = resolveProxyTarget(url);
            WebResourceResponse mappedResponse = tryServeMappedResource(targetUrl);
            if (mappedResponse != null) {
                logLocalIntercept("proxy-local-hit", url, targetUrl);
                return mappedResponse;
            }
            if (targetUrl != null && isProxyableRequest(targetUrl, request.getMethod())) {
                logLocalIntercept("proxy-forward", url, targetUrl);
                return proxyRequest(targetUrl, request);
            }
            return null;
        }

        WebResourceResponse mappedResponse = tryServeMappedResource(url);
        if (mappedResponse != null) {
            logLocalIntercept("direct-local-hit", url, url);
            return mappedResponse;
        }

        if (shouldProxyMainFrameRequest(url, request)) {
            logLocalIntercept("main-frame-forward", url, url);
            return proxyRequest(url, request);
        }

        return null;
    }

    String buildRuffleConfigScript() {
        int fontMode = host.getSelectedFontMode();
        StringBuilder script = new StringBuilder();
        script.append("(function(){");
        script.append("var ieUa='").append(escapeJsString(IE_USER_AGENT)).append("';");
        script.append("try{Object.defineProperty(navigator,'userAgent',{get:function(){return ieUa;},configurable:true});}catch(e){}");
        script.append("try{Object.defineProperty(navigator,'appVersion',{get:function(){return ieUa;},configurable:true});}catch(e){}");
        script.append("try{Object.defineProperty(navigator,'appName',{get:function(){return 'Microsoft Internet Explorer';},configurable:true});}catch(e){}");
        script.append("try{Object.defineProperty(navigator,'platform',{get:function(){return 'Win32';},configurable:true});}catch(e){}");
        script.append("try{Object.defineProperty(navigator,'vendor',{get:function(){return '';},configurable:true});}catch(e){}");
        script.append("try{Object.defineProperty(document,'documentMode',{get:function(){return 10;},configurable:true});}catch(e){}");
        script.append("window.RufflePlayer=window.RufflePlayer||{};");
        script.append("window.RufflePlayer.config=window.RufflePlayer.config||{};");
        script.append("var c=window.RufflePlayer.config;");
        script.append("c.allowScriptAccess=true;");
        script.append("c.allowNetworking='all';");
        script.append("c.openUrlMode='allow';");
        script.append("c.logLevel='error';");
        script.append("if(window.navigator&&('gpu' in navigator)){c.preferredRenderer='webgpu';}");
        script.append("else if(window.WebGLRenderingContext||window.WebGL2RenderingContext){c.preferredRenderer='wgpu-webgl';}");

        if (fontMode == FONT_MODE_EMBEDDED) {
            script.append("c.deviceFontRenderer='embedded';");
            script.append("c.defaultFonts={};");
        } else if (fontMode == FONT_MODE_CHINESE_SERIF) {
            script.append("c.deviceFontRenderer='canvas';");
            script.append("c.defaultFonts={");
            script.append("sans:['Noto Serif CJK SC','Noto Serif SC','Source Han Serif SC','serif'],");
            script.append("serif:['Noto Serif CJK SC','Noto Serif SC','Source Han Serif SC','serif'],");
            script.append("typewriter:['monospace'],");
            script.append("japaneseGothic:['Noto Sans CJK SC','Noto Sans SC','Source Han Sans SC','Droid Sans Fallback','sans-serif'],");
            script.append("japaneseGothicMono:['monospace'],");
            script.append("japaneseMincho:['Noto Serif CJK SC','Noto Serif SC','Source Han Serif SC','serif']");
            script.append("};");
        } else {
            script.append("c.deviceFontRenderer='canvas';");
            script.append("c.defaultFonts={");
            script.append("sans:['Noto Sans CJK SC','Noto Sans SC','Source Han Sans SC','Droid Sans Fallback','sans-serif'],");
            script.append("serif:['Noto Serif CJK SC','Noto Serif SC','Source Han Serif SC','serif'],");
            script.append("typewriter:['monospace'],");
            script.append("japaneseGothic:['Noto Sans CJK SC','Noto Sans SC','Source Han Sans SC','Droid Sans Fallback','sans-serif'],");
            script.append("japaneseGothicMono:['monospace'],");
            script.append("japaneseMincho:['Noto Serif CJK SC','Noto Serif SC','Source Han Serif SC','serif']");
            script.append("};");
        }

        script.append("})();");
        return script.toString();
    }

    String buildPageCompatScript(boolean legacyViewportMode) {
        StringBuilder script = new StringBuilder();
        script.append("(function(){");
        script.append("var legacyMode=").append(legacyViewportMode ? "true" : "false").append(";");
        script.append("function preparePage(){");
        script.append("try{document.documentElement.style.maxWidth='100%';document.documentElement.style.overflowX='auto';document.documentElement.style.overflowY='auto';}catch(e){}");
        script.append("try{document.documentElement.style.visibility='visible';}catch(e){}");
        script.append("try{if(document.body){document.body.style.maxWidth='100%';document.body.style.overflowX='auto';document.body.style.overflowY='auto';document.body.style.webkitOverflowScrolling='touch';document.body.style.visibility='visible';document.body.style.opacity='1';}}catch(e){}");
        script.append("}");
        script.append("function ensureViewportTag(){");
        script.append("var meta=document.querySelector('meta[name=\"viewport\"]');");
        script.append("if(meta){return meta;}");
        script.append("meta=document.createElement('meta');");
        script.append("meta.setAttribute('name','viewport');");
        script.append("var head=document.head||document.getElementsByTagName('head')[0]||document.documentElement;");
        script.append("if(head.firstChild){head.insertBefore(meta,head.firstChild);}else{head.appendChild(meta);}");
        script.append("return meta;");
        script.append("}");
        script.append("function measureContentWidth(){");
        script.append("var width=").append(LEGACY_PAGE_MIN_VIEWPORT_WIDTH).append(";");
        script.append("try{if(window.innerWidth){width=Math.max(width,Math.ceil(window.innerWidth));}}catch(e){}");
        script.append("try{if(document.documentElement){width=Math.max(width,Math.ceil(document.documentElement.scrollWidth||0));width=Math.max(width,Math.ceil(document.documentElement.getBoundingClientRect().width||0));}}catch(e){}");
        script.append("try{if(document.body){width=Math.max(width,Math.ceil(document.body.scrollWidth||0));width=Math.max(width,Math.ceil(document.body.getBoundingClientRect().width||0));}}catch(e){}");
        script.append("try{var nodes=document.querySelectorAll('table,img,object,embed,iframe,canvas,svg,div,section,article,form,body>*');");
        script.append("var limit=Math.min(nodes.length,200);");
        script.append("for(var i=0;i<limit;i++){var node=nodes[i];if(!node||!node.getBoundingClientRect){continue;}var rect=node.getBoundingClientRect();if(!rect){continue;}width=Math.max(width,Math.ceil(rect.left+rect.width));}}catch(e){}");
        script.append("width=Math.max(").append(LEGACY_PAGE_MIN_VIEWPORT_WIDTH).append(",Math.min(").append(LEGACY_PAGE_MAX_VIEWPORT_WIDTH).append(",width));");
        script.append("return width;");
        script.append("}");
        script.append("function applyLegacyViewport(){");
        script.append("preparePage();");
        script.append("try{var width=measureContentWidth();var meta=ensureViewportTag();meta.setAttribute('content','width='+width+', initial-scale=1, minimum-scale=0.25, maximum-scale=5, user-scalable=yes');window.__flashBrowserLegacyViewportWidth=width;}catch(e){}");
        script.append("preparePage();");
        script.append("}");
        script.append("window.__flashBrowserRefreshPageCompat=function(){if(legacyMode){applyLegacyViewport();}else{preparePage();}};");
        script.append("if(!window.__flashBrowserPageCompatBound){");
        script.append("window.__flashBrowserPageCompatBound=true;");
        script.append("window.addEventListener('resize',window.__flashBrowserRefreshPageCompat,{passive:true});");
        script.append("window.addEventListener('orientationchange',window.__flashBrowserRefreshPageCompat,{passive:true});");
        script.append("document.addEventListener('DOMContentLoaded',window.__flashBrowserRefreshPageCompat,{passive:true});");
        script.append("window.addEventListener('load',window.__flashBrowserRefreshPageCompat,{passive:true});");
        script.append("setTimeout(window.__flashBrowserRefreshPageCompat,0);");
        script.append("setTimeout(window.__flashBrowserRefreshPageCompat,50);");
        script.append("setTimeout(window.__flashBrowserRefreshPageCompat,250);");
        script.append("setTimeout(window.__flashBrowserRefreshPageCompat,800);");
        script.append("}");
        script.append("if(legacyMode){try{document.documentElement.style.visibility='hidden';if(document.body){document.body.style.visibility='hidden';document.body.style.opacity='0';}}catch(e){}}");
        script.append("window.__flashBrowserRefreshPageCompat();");
        script.append("})();");
        return script.toString();
    }

    void refreshPageCompatLayout(WebView webView) {
        if (webView == null) {
            return;
        }
        webView.evaluateJavascript(
                "(function(){if(window.__flashBrowserRefreshPageCompat){window.__flashBrowserRefreshPageCompat();return true;}return false;})();",
                value -> { }
        );
    }

    String escapeJsString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }

    private WebResourceResponse proxyRequest(Uri targetUrl, WebResourceRequest request) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(targetUrl.toString());
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod(request.getMethod());
            connection.setDoInput(true);
            connection.setRequestProperty("Accept-Encoding", "identity");

            copyRequestHeaders(connection, request);
            syncRequestCookies(connection, targetUrl);

            int statusCode = connection.getResponseCode();
            Map<String, String> responseHeaders = flattenHeaders(connection.getHeaderFields());
            syncResponseCookies(targetUrl, connection);

            String mimeType = getMimeType(connection, targetUrl);
            String encoding = getEncoding(connection.getContentType());
            InputStream responseStream = openResponseStream(connection, statusCode);

            if (responseStream == null) {
                responseStream = new ByteArrayInputStream(new byte[0]);
            }

            if (shouldInjectHtml(statusCode, mimeType)) {
                responseHeaders = new HashMap<>(responseHeaders);
                stripHtmlSecurityHeaders(responseHeaders);

                Charset charset = resolveCharset(encoding);
                String html = readStream(responseStream, charset);
                String injectedHtml = injectBootstrapIntoHtml(html);
                byte[] body = injectedHtml.getBytes(charset);
                responseHeaders.put("Content-Type", "text/html; charset=" + charset.name().toLowerCase(Locale.US));
                responseHeaders.put("Content-Length", String.valueOf(body.length));
                responseHeaders = addCorsHeaders(responseHeaders);

                return buildResponse(
                        "text/html",
                        charset.name(),
                        statusCode,
                        connection.getResponseMessage(),
                        responseHeaders,
                        new ByteArrayInputStream(body)
                );
            }

            responseHeaders = addCorsHeaders(responseHeaders);
            responseHeaders.put("Cross-Origin-Resource-Policy", "cross-origin");
            return buildResponse(
                    mimeType,
                    encoding,
                    statusCode,
                    connection.getResponseMessage(),
                    responseHeaders,
                    responseStream
            );
        } catch (Exception e) {
            Log.e(TAG, "Proxy failed for " + targetUrl, e);
            return null;
        }
    }

    private void copyRequestHeaders(HttpURLConnection connection, WebResourceRequest request) {
        Map<String, String> headers = request.getRequestHeaders();
        if (headers == null) {
            applyIeRequestHeaders(connection);
            return;
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }

            String normalizedKey = key.toLowerCase(Locale.US);
            if (SKIPPED_REQUEST_HEADERS.contains(normalizedKey)
                    || OVERRIDDEN_IE_REQUEST_HEADERS.contains(normalizedKey)) {
                continue;
            }

            connection.setRequestProperty(key, value);
        }

        applyIeRequestHeaders(connection);
    }

    private void applyIeRequestHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("User-Agent", IE_USER_AGENT);
        connection.setRequestProperty("Accept", IE_ACCEPT);
        connection.setRequestProperty("Accept-Language", IE_ACCEPT_LANGUAGE);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Pragma", "no-cache");
    }

    private void syncRequestCookies(HttpURLConnection connection, Uri uri) {
        String cookies = CookieManager.getInstance().getCookie(uri.toString());
        if (cookies != null && !cookies.isEmpty()) {
            connection.setRequestProperty("Cookie", cookies);
        }
    }

    private void syncResponseCookies(Uri uri, HttpURLConnection connection) {
        Map<String, List<String>> headers = connection.getHeaderFields();
        if (headers == null) {
            return;
        }

        List<String> cookies = null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && "set-cookie".equalsIgnoreCase(entry.getKey())) {
                cookies = entry.getValue();
                break;
            }
        }
        if (cookies == null || cookies.isEmpty()) {
            return;
        }

        CookieManager cookieManager = CookieManager.getInstance();
        String url = uri.toString();
        for (String cookie : cookies) {
            cookieManager.setCookie(url, cookie);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
        }
    }

    private WebResourceResponse serveRuffleAsset(Uri uri) {
        String assetName = uri.getLastPathSegment();
        if (assetName == null || assetName.isEmpty()) {
            assetName = "ruffle.js";
        }

        String assetPath = "ruffle/" + assetName;
        try {
            InputStream inputStream = activity.getAssets().open(assetPath);
            String mimeType = guessMimeType(assetName);
            String encoding = mimeType.startsWith("text/") || mimeType.contains("javascript")
                    ? StandardCharsets.UTF_8.name()
                    : null;

            Map<String, String> headers = addCorsHeaders(new HashMap<>());
            headers.put("Cache-Control", "no-cache");
            headers.put("Cross-Origin-Resource-Policy", "cross-origin");

            return buildResponse(mimeType, encoding, 200, "OK", headers, inputStream);
        } catch (IOException e) {
            Log.e(TAG, "Unable to serve asset " + assetPath, e);
            return buildResponse(
                    "text/plain",
                    StandardCharsets.UTF_8.name(),
                    404,
                    "Not Found",
                    addCorsHeaders(new HashMap<>()),
                    new ByteArrayInputStream("Missing asset".getBytes(StandardCharsets.UTF_8))
            );
        }
    }

    private WebResourceResponse createEmptyCorsResponse() {
        return buildResponse(
                "text/plain",
                StandardCharsets.UTF_8.name(),
                204,
                "No Content",
                addCorsHeaders(new HashMap<>()),
                new ByteArrayInputStream(new byte[0])
        );
    }

    private WebResourceResponse tryServeMappedResource(Uri uri) {
        if (uri == null) {
            return null;
        }

        LocalMappingManager.MappedResource resource = localMappingManager.resolve(uri);
        if (resource == null) {
            return null;
        }

        try {
            Map<String, String> headers = addCorsHeaders(new HashMap<>());
            headers.put("Cache-Control", "no-cache");
            headers.put("Cross-Origin-Resource-Policy", "cross-origin");

            if (isHtmlMimeType(resource.mimeType)) {
                Charset charset = resolveCharset(resource.encoding);
                String html = readStream(resource.inputStream, charset);
                String injectedHtml = injectBootstrapIntoHtml(html);
                byte[] body = injectedHtml.getBytes(charset);
                headers.put("Content-Type", "text/html; charset=" + charset.name().toLowerCase(Locale.US));
                headers.put("Content-Length", String.valueOf(body.length));
                return buildResponse(
                        "text/html",
                        charset.name(),
                        200,
                        "OK",
                        headers,
                        new ByteArrayInputStream(body)
                );
            }

            return buildResponse(
                    resource.mimeType,
                    resource.encoding,
                    200,
                    "OK",
                    headers,
                    resource.inputStream
            );
        } catch (IOException e) {
            Log.e(TAG, "Unable to serve mapped resource for " + uri, e);
            return null;
        }
    }

    private void logLocalIntercept(String stage, Uri originalUri, Uri resolvedUri) {
        String hostValue = resolvedUri == null ? "" : String.valueOf(resolvedUri.getHost());
        String pathValue = resolvedUri == null ? "" : String.valueOf(resolvedUri.getPath());
        if (hostValue.endsWith("pvzol.org") || pathValue.startsWith("/pvz/") || pathValue.startsWith("/youkia/")) {
            return;
        }
    }

    private WebResourceResponse buildResponse(
            String mimeType,
            String encoding,
            int statusCode,
            String reasonPhrase,
            Map<String, String> headers,
            InputStream inputStream
    ) {
        return new WebResourceResponse(
                mimeType,
                encoding,
                statusCode,
                sanitizeReason(reasonPhrase),
                headers,
                inputStream
        );
    }

    private Map<String, String> flattenHeaders(Map<String, List<String>> headerFields) {
        Map<String, String> headers = new HashMap<>();
        if (headerFields == null) {
            return headers;
        }

        for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }

            String normalizedKey = key.toLowerCase(Locale.US);
            if (HOP_BY_HOP_HEADERS.contains(normalizedKey) || STRIPPED_RESPONSE_HEADERS.contains(normalizedKey)) {
                continue;
            }

            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }

            if ("set-cookie".equals(normalizedKey)) {
                continue;
            }

            headers.put(key, values.get(0));
        }
        return headers;
    }

    private Map<String, String> addCorsHeaders(Map<String, String> headers) {
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "*");
        headers.put("Access-Control-Allow-Credentials", "false");
        return headers;
    }

    private void stripHtmlSecurityHeaders(Map<String, String> headers) {
        headers.remove("Content-Security-Policy");
        headers.remove("Content-Security-Policy-Report-Only");
        headers.remove("content-security-policy");
        headers.remove("content-security-policy-report-only");
        headers.remove("X-Frame-Options");
        headers.remove("x-frame-options");
    }

    private InputStream openResponseStream(HttpURLConnection connection, int statusCode) throws IOException {
        if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
            return connection.getErrorStream();
        }
        return connection.getInputStream();
    }

    private boolean shouldInjectHtml(int statusCode, String mimeType) {
        if (statusCode < 200 || statusCode >= 300) {
            return false;
        }

        if (mimeType == null) {
            return false;
        }

        return mimeType.toLowerCase(Locale.US).contains("text/html");
    }

    private String injectBootstrapIntoHtml(String html) {
        String cleanedHtml = CSP_META_PATTERN.matcher(html).replaceAll("");
        boolean likelyFlashPage = containsFlashMarkup(cleanedHtml);
        String normalizedHtml = cleanedHtml;
        boolean injectLegacyViewport = !likelyFlashPage;
        if (injectLegacyViewport) {
            normalizedHtml = VIEWPORT_META_PATTERN.matcher(normalizedHtml).replaceAll("");
        }
        String scriptTag = buildRuffleInjectionTag(injectLegacyViewport);
        if (normalizedHtml.contains(scriptTag)) {
            return normalizedHtml;
        }

        if (normalizedHtml.matches("(?is).*?</head>.*")) {
            return normalizedHtml.replaceFirst("(?is)</head>", Matcher.quoteReplacement(scriptTag + "</head>"));
        }

        if (normalizedHtml.matches("(?is).*?<html[^>]*>.*")) {
            return normalizedHtml.replaceFirst("(?is)<html[^>]*>", "$0" + Matcher.quoteReplacement(scriptTag));
        }

        return scriptTag + normalizedHtml;
    }

    private String buildRuffleInjectionTag(boolean injectLegacyViewport) {
        String viewportTag = "";
        if (injectLegacyViewport) {
            viewportTag = "<meta name=\"viewport\" content=\"width=" + LEGACY_PAGE_MIN_VIEWPORT_WIDTH + ", initial-scale=1, minimum-scale=0.25, maximum-scale=5, user-scalable=yes\">"
                    + "<style>html,body{max-width:100%;overflow-x:auto;overflow-y:auto;-webkit-overflow-scrolling:touch;}</style>";
        }
        return viewportTag
                + "<script>" + buildRuffleConfigScript() + "</script>"
                + "<script>" + buildPageCompatScript(injectLegacyViewport) + "</script>"
                + "<script src=\"" + RUFFLE_PATH_PREFIX + BOOTSTRAP_SCRIPT + "\"></script>";
    }

    private boolean containsFlashMarkup(String html) {
        if (html == null) {
            return false;
        }
        String lower = html.toLowerCase(Locale.US);
        return lower.contains(".swf")
                || lower.contains("shockwave")
                || lower.contains("ruffle-embed")
                || lower.contains("ruffle-object")
                || lower.contains("ruffle-player");
    }

    private String getMimeType(HttpURLConnection connection, Uri uri) {
        String contentType = connection.getContentType();
        if (contentType != null && !contentType.isEmpty()) {
            int separator = contentType.indexOf(';');
            return separator >= 0 ? contentType.substring(0, separator).trim() : contentType.trim();
        }

        return guessMimeType(uri.getLastPathSegment());
    }

    private String guessMimeType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }

        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript";
        }
        if (lower.endsWith(".css")) {
            return "text/css";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".wasm")) {
            return "application/wasm";
        }
        if (lower.endsWith(".swf")) {
            return "application/x-shockwave-flash";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private String getEncoding(String contentType) {
        if (contentType == null) {
            return null;
        }

        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (matcher.find()) {
            return matcher.group(1).trim().replace("\"", "");
        }
        return null;
    }

    private Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isEmpty()) {
            return StandardCharsets.UTF_8;
        }

        try {
            return Charset.forName(encoding);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private String readStream(InputStream inputStream, Charset charset) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, count);
        }
        return outputStream.toString(charset.name());
    }

    private String sanitizeReason(String reasonPhrase) {
        if (reasonPhrase == null || reasonPhrase.trim().isEmpty()) {
            return "OK";
        }
        return reasonPhrase;
    }

    private boolean isHtmlMimeType(String mimeType) {
        return mimeType != null && mimeType.toLowerCase(Locale.US).contains("text/html");
    }

    private boolean isProxyableRequest(Uri uri, String method) {
        String scheme = uri.getScheme();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method));
    }

    private boolean shouldProxyMainFrameRequest(Uri uri, WebResourceRequest request) {
        return request.isForMainFrame() && isProxyableRequest(uri, request.getMethod());
    }

    private boolean isRuffleAssetRequest(Uri uri) {
        return uri.getPath() != null && uri.getPath().startsWith(RUFFLE_PATH_PREFIX);
    }

    private boolean isProxyAssetRequest(Uri uri) {
        return uri.getPath() != null && uri.getPath().startsWith(PROXY_PATH_PREFIX);
    }

    private Uri resolveProxyTarget(Uri proxyUri) {
        String path = proxyUri.getPath();
        if (path == null || !path.startsWith(PROXY_PATH_PREFIX)) {
            return null;
        }

        String remainder = path.substring(PROXY_PATH_PREFIX.length());
        int separator = remainder.indexOf('/');
        if (separator <= 0 || separator == remainder.length() - 1) {
            return null;
        }

        String scheme = remainder.substring(0, separator);
        String authorityAndPath = remainder.substring(separator + 1);
        if (authorityAndPath.isEmpty()) {
            return null;
        }

        Uri.Builder builder = new Uri.Builder()
                .scheme(scheme)
                .encodedAuthority(extractAuthority(authorityAndPath))
                .encodedPath(extractPath(authorityAndPath));

        String encodedQuery = proxyUri.getEncodedQuery();
        if (encodedQuery != null && !encodedQuery.isEmpty()) {
            builder.encodedQuery(encodedQuery);
        }

        return builder.build();
    }

    private String extractAuthority(String authorityAndPath) {
        int slashIndex = authorityAndPath.indexOf('/');
        if (slashIndex < 0) {
            return authorityAndPath;
        }
        return authorityAndPath.substring(0, slashIndex);
    }

    private String extractPath(String authorityAndPath) {
        int slashIndex = authorityAndPath.indexOf('/');
        if (slashIndex < 0) {
            return "/";
        }
        return authorityAndPath.substring(slashIndex);
    }
}

final class FeaturePanelRepositoryController {
    private final AppCompatActivity activity;
    private final BrowserPreferenceStore preferenceStore;
    private final WarehouseRecordManager warehouseRecordManager;

    private final LinkedHashSet<String> sessionRecordChoiceKeys = new LinkedHashSet<>();
    private final HashMap<String, WarehouseRecordManager.RepositorySnapshot> currentSnapshots = new HashMap<>();
    private final HashMap<String, LinkedHashSet<Integer>> selectedDeltaIds = new HashMap<>();
    private final HashMap<String, LinkedHashSet<Integer>> selectedIgnoredIds = new HashMap<>();

    private TextView repositoryHintText;
    private TextView repositorySelectedTargetsText;
    private Button repositoryPickTargetsButton;
    private TextView repositoryViewCookieText;
    private Button repositoryPickViewCookieButton;
    private Button repositoryRefreshButton;
    private Button repositoryRecordButton;
    private Button repositoryCompareButton;
    private Button repositoryIgnoreSelectedButton;
    private Button repositoryRemoveIgnoredButton;
    private LinearLayout repositoryCurrentContainer;
    private LinearLayout repositoryDeltaContainer;
    private LinearLayout repositoryIgnoredContainer;

    private String sessionViewChoiceKey;
    private boolean sessionRecordSelectionInitialized;
    private boolean currentExpanded;
    private boolean deltaExpanded;

    FeaturePanelRepositoryController(
            AppCompatActivity activity,
            BrowserPreferenceStore preferenceStore,
            WarehouseRecordManager warehouseRecordManager
    ) {
        this.activity = activity;
        this.preferenceStore = preferenceStore;
        this.warehouseRecordManager = warehouseRecordManager;
    }

    void bind(
            TextView repositoryHintText,
            TextView repositorySelectedTargetsText,
            Button repositoryPickTargetsButton,
            TextView repositoryViewCookieText,
            Button repositoryPickViewCookieButton,
            Button repositoryRefreshButton,
            Button repositoryRecordButton,
            Button repositoryCompareButton,
            Button repositoryIgnoreSelectedButton,
            Button repositoryRemoveIgnoredButton,
            LinearLayout repositoryCurrentContainer,
            LinearLayout repositoryDeltaContainer,
            LinearLayout repositoryIgnoredContainer
    ) {
        this.repositoryHintText = repositoryHintText;
        this.repositorySelectedTargetsText = repositorySelectedTargetsText;
        this.repositoryPickTargetsButton = repositoryPickTargetsButton;
        this.repositoryViewCookieText = repositoryViewCookieText;
        this.repositoryPickViewCookieButton = repositoryPickViewCookieButton;
        this.repositoryRefreshButton = repositoryRefreshButton;
        this.repositoryRecordButton = repositoryRecordButton;
        this.repositoryCompareButton = repositoryCompareButton;
        this.repositoryIgnoreSelectedButton = repositoryIgnoreSelectedButton;
        this.repositoryRemoveIgnoredButton = repositoryRemoveIgnoredButton;
        this.repositoryCurrentContainer = repositoryCurrentContainer;
        this.repositoryDeltaContainer = repositoryDeltaContainer;
        this.repositoryIgnoredContainer = repositoryIgnoredContainer;
        hideUnusedRepositoryViews();
    }

    void clear() {
        bind(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    void render(List<FeatureCookieChoice> choices) {
        if (repositorySelectedTargetsText == null
                || repositoryViewCookieText == null
                || repositoryCurrentContainer == null
                || repositoryDeltaContainer == null) {
            return;
        }

        refreshSelectionState(choices);
        List<FeatureCookieChoice> selectedChoices = getSelectedChoices(choices);
        if (selectedChoices.isEmpty()) {
            currentExpanded = false;
            deltaExpanded = false;
            repositorySelectedTargetsText.setText("当前未选择记录目标");
            repositoryViewCookieText.setText("当前未选择查看目标");
            if (repositoryHintText != null) {
                repositoryHintText.setText("仓库记录按 Cookie 分开保存。请先选择记录 Cookie。");
            }
            renderMessage(repositoryCurrentContainer, "当前未选择记录目标");
            renderMessage(repositoryDeltaContainer, "当前未选择记录目标");
            updateButtonsEnabled(false);
            return;
        }

        ArrayList<String> labels = new ArrayList<>();
        for (FeatureCookieChoice choice : selectedChoices) {
            labels.add(getChoiceLabel(choice));
        }
        repositorySelectedTargetsText.setText("记录目标：" + TextUtils.join("，", labels));

        FeatureCookieChoice viewedChoice = getViewedChoice(choices);
        if (viewedChoice == null) {
            viewedChoice = selectedChoices.get(0);
            sessionViewChoiceKey = getChoiceKey(viewedChoice);
        }
        repositoryViewCookieText.setText("当前查看：" + getChoiceLabel(viewedChoice));
        if (repositoryHintText != null) {
            repositoryHintText.setText("记录、对比和屏蔽都按各自 Cookie 分开保存。");
        }
        updateButtonsEnabled(true);
        renderChoiceData(viewedChoice);
    }

    void showTargetPickerDialog(List<FeatureCookieChoice> choices) {
        List<FeatureCookieChoice> availableChoices = new ArrayList<>(choices);
        if (availableChoices.isEmpty()) {
            Toast.makeText(activity, "当前没有可用的 Cookie。", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[availableChoices.size()];
        boolean[] checked = new boolean[availableChoices.size()];
        for (int i = 0; i < availableChoices.size(); i += 1) {
            FeatureCookieChoice choice = availableChoices.get(i);
            labels[i] = getChoiceLabel(choice);
            checked[i] = sessionRecordChoiceKeys.contains(getChoiceKey(choice));
        }

        new AlertDialog.Builder(activity)
                .setTitle("选择记录 Cookie")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    String key = getChoiceKey(availableChoices.get(which));
                    if (isChecked) {
                        sessionRecordChoiceKeys.add(key);
                    } else {
                        sessionRecordChoiceKeys.remove(key);
                    }
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    refreshSelectionState(choices);
                    currentExpanded = false;
                    deltaExpanded = false;
                    render(choices);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    void showViewPickerDialog(List<FeatureCookieChoice> choices) {
        List<FeatureCookieChoice> selectedChoices = getSelectedChoices(choices);
        if (selectedChoices.isEmpty()) {
            Toast.makeText(activity, "请先选择记录 Cookie。", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[selectedChoices.size()];
        int checkedIndex = 0;
        for (int i = 0; i < selectedChoices.size(); i += 1) {
            FeatureCookieChoice choice = selectedChoices.get(i);
            labels[i] = getChoiceLabel(choice);
            if (TextUtils.equals(sessionViewChoiceKey, getChoiceKey(choice))) {
                checkedIndex = i;
            }
        }

        final int[] selectedIndex = new int[] {checkedIndex};
        new AlertDialog.Builder(activity)
                .setTitle("选择查看 Cookie")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    sessionViewChoiceKey = getChoiceKey(selectedChoices.get(selectedIndex[0]));
                    currentExpanded = false;
                    deltaExpanded = false;
                    render(choices);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    void refreshSelectedTargets(List<FeatureCookieChoice> choices) {
        executeBatchAction("refresh", choices);
    }

    void recordSelectedTargets(List<FeatureCookieChoice> choices) {
        executeBatchAction("record", choices);
    }

    void compareSelectedTargets(List<FeatureCookieChoice> choices) {
        executeBatchAction("compare", choices);
    }

    void addSelectedDeltasToIgnored(List<FeatureCookieChoice> choices) {
        FeatureCookieChoice viewedChoice = getViewedChoice(choices);
        if (viewedChoice == null) {
            return;
        }
        String key = getChoiceKey(viewedChoice);
        LinkedHashSet<Integer> ids = selectedDeltaIds.get(key);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LinkedHashSet<Integer> ignoredIds = loadIgnoredIds(key);
        ignoredIds.addAll(ids);
        saveIgnoredIds(key, ignoredIds);
        ids.clear();
        render(choices);
    }

    void removeSelectedIgnoredItems(List<FeatureCookieChoice> choices) {
        FeatureCookieChoice viewedChoice = getViewedChoice(choices);
        if (viewedChoice == null) {
            return;
        }
        String key = getChoiceKey(viewedChoice);
        LinkedHashSet<Integer> ids = selectedIgnoredIds.get(key);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LinkedHashSet<Integer> ignoredIds = loadIgnoredIds(key);
        ignoredIds.removeAll(ids);
        saveIgnoredIds(key, ignoredIds);
        ids.clear();
        render(choices);
    }

    private void executeBatchAction(String action, List<FeatureCookieChoice> choices) {
        List<FeatureCookieChoice> targets = getSelectedChoices(choices);
        if (targets.isEmpty()) {
            Toast.makeText(activity, "请先选择记录 Cookie。", Toast.LENGTH_SHORT).show();
            return;
        }

        setActionButtonsEnabled(false);
        new Thread(() -> {
            ArrayList<String> messages = new ArrayList<>();
            for (FeatureCookieChoice choice : targets) {
                String key = getChoiceKey(choice);
                String label = getChoiceLabel(choice);
                try {
                    WarehouseRecordManager.RepositorySnapshot snapshot =
                            warehouseRecordManager.fetchRepository(choice.baseUrl, choice.cookies);
                    currentSnapshots.put(key, snapshot);
                    if ("record".equals(action)) {
                        saveRecordedSnapshot(key, snapshot);
                        messages.add(label + "：已记录");
                    } else if ("compare".equals(action)) {
                        messages.add(label + "：已对比");
                    } else {
                        messages.add(label + "：已刷新");
                    }
                } catch (Exception e) {
                    messages.add(label + "：失败，" + e.getMessage());
                }
            }

            activity.runOnUiThread(() -> {
                setActionButtonsEnabled(true);
                if (repositoryHintText != null && !messages.isEmpty()) {
                    repositoryHintText.setText(TextUtils.join(" | ", messages));
                }
                render(choices);
            });
        }).start();
    }

    private void refreshSelectionState(List<FeatureCookieChoice> choices) {
        List<FeatureCookieChoice> availableChoices = new ArrayList<>(choices);
        LinkedHashSet<String> availableKeys = new LinkedHashSet<>();
        for (FeatureCookieChoice choice : availableChoices) {
            availableKeys.add(getChoiceKey(choice));
        }
        sessionRecordChoiceKeys.retainAll(availableKeys);
        if (!sessionRecordSelectionInitialized) {
            sessionRecordSelectionInitialized = true;
            for (FeatureCookieChoice choice : availableChoices) {
                if (choice.currentPage) {
                    sessionRecordChoiceKeys.add(getChoiceKey(choice));
                    break;
                }
            }
            if (sessionRecordChoiceKeys.isEmpty() && !availableChoices.isEmpty()) {
                sessionRecordChoiceKeys.add(getChoiceKey(availableChoices.get(0)));
            }
        }

        if (TextUtils.isEmpty(sessionViewChoiceKey)
                || !sessionRecordChoiceKeys.contains(sessionViewChoiceKey)) {
            sessionViewChoiceKey = sessionRecordChoiceKeys.isEmpty()
                    ? null
                    : sessionRecordChoiceKeys.iterator().next();
        }
    }

    private List<FeatureCookieChoice> getSelectedChoices(List<FeatureCookieChoice> choices) {
        ArrayList<FeatureCookieChoice> result = new ArrayList<>();
        for (FeatureCookieChoice choice : choices) {
            if (sessionRecordChoiceKeys.contains(getChoiceKey(choice))) {
                result.add(choice);
            }
        }
        return result;
    }

    private FeatureCookieChoice getViewedChoice(List<FeatureCookieChoice> choices) {
        if (TextUtils.isEmpty(sessionViewChoiceKey)) {
            return null;
        }
        for (FeatureCookieChoice choice : choices) {
            if (sessionViewChoiceKey.equals(getChoiceKey(choice))) {
                return choice;
            }
        }
        return null;
    }

    private String getChoiceKey(FeatureCookieChoice choice) {
        if (choice == null) {
            return "";
        }
        if (!TextUtils.isEmpty(choice.selectionKey)) {
            return "profile:" + choice.selectionKey;
        }
        return "current:" + choice.baseUrl + ":" + Integer.toHexString(String.valueOf(choice.cookies).hashCode());
    }

    private String getChoiceLabel(FeatureCookieChoice choice) {
        if (choice == null) {
            return "";
        }
        return choice.currentPage ? "当前页面 Cookie" : choice.label;
    }

    private void renderChoiceData(FeatureCookieChoice choice) {
        String key = getChoiceKey(choice);
        WarehouseRecordManager.RepositorySnapshot currentSnapshot = currentSnapshots.get(key);
        if (currentSnapshot == null) {
            renderMessage(repositoryCurrentContainer, "尚未刷新当前仓库。");
        } else {
            renderCurrentSnapshot(currentSnapshot);
        }

        WarehouseRecordManager.RepositorySnapshot recordedSnapshot = loadRecordedSnapshot(key);
        if (recordedSnapshot == null || currentSnapshot == null) {
            renderMessage(repositoryDeltaContainer, "请先刷新仓库并至少记录一次。");
        } else {
            renderDeltas(warehouseRecordManager.compare(currentSnapshot, recordedSnapshot, null));
        }
    }

    private void renderCurrentSnapshot(WarehouseRecordManager.RepositorySnapshot snapshot) {
        repositoryCurrentContainer.removeAllViews();
        if (snapshot == null || snapshot.toolEntries.isEmpty()) {
            renderMessage(repositoryCurrentContainer, "当前仓库为空。");
            return;
        }

        final int collapsedLineCount = 8;
        String fullText = buildSnapshotText(snapshot);
        String[] lines = fullText.split("\n");
        boolean canCollapse = lines.length > collapsedLineCount;
        String shownText = fullText;
        if (!currentExpanded && canCollapse) {
            shownText = TextUtils.join("\n", Arrays.asList(lines).subList(0, collapsedLineCount));
        }

        TextView textView = new TextView(activity);
        textView.setText(shownText);
        textView.setTextColor(0xFF1F2937);
        textView.setBackgroundColor(0xFFFFFFFF);
        textView.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        repositoryCurrentContainer.addView(textView);

        if (canCollapse) {
            Button toggleButton = new Button(activity);
            toggleButton.setAllCaps(false);
            toggleButton.setText(currentExpanded ? "收起" : "展开全部");
            toggleButton.setOnClickListener(v -> {
                currentExpanded = !currentExpanded;
                renderCurrentSnapshot(snapshot);
            });
            repositoryCurrentContainer.addView(toggleButton);
        }
    }

    private String buildSnapshotText(WarehouseRecordManager.RepositorySnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        for (WarehouseRecordManager.ToolEntry entry : snapshot.toolEntries) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry.displayName).append(" x ").append(entry.amount);
        }
        return builder.toString();
    }

    private void renderDeltas(List<WarehouseRecordManager.RepositoryDelta> deltas) {
        repositoryDeltaContainer.removeAllViews();
        if (deltas == null || deltas.isEmpty()) {
            renderMessage(repositoryDeltaContainer, "暂无变化。");
            return;
        }

        final int collapsedLineCount = 8;
        String fullText = buildDeltaText(deltas);
        String[] lines = fullText.split("\n");
        boolean canCollapse = lines.length > collapsedLineCount;
        String shownText = fullText;
        if (!deltaExpanded && canCollapse) {
            shownText = TextUtils.join("\n", Arrays.asList(lines).subList(0, collapsedLineCount));
        }

        TextView textView = new TextView(activity);
        textView.setText(shownText);
        textView.setTextColor(0xFF1F2937);
        textView.setBackgroundColor(0xFFFFFFFF);
        textView.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        repositoryDeltaContainer.addView(textView);

        if (canCollapse) {
            Button toggleButton = new Button(activity);
            toggleButton.setAllCaps(false);
            toggleButton.setText(deltaExpanded ? "收起" : "展开全部");
            toggleButton.setOnClickListener(v -> {
                deltaExpanded = !deltaExpanded;
                renderDeltas(deltas);
            });
            repositoryDeltaContainer.addView(toggleButton);
        }
    }

    private String buildDeltaText(List<WarehouseRecordManager.RepositoryDelta> deltas) {
        StringBuilder builder = new StringBuilder();
        for (WarehouseRecordManager.RepositoryDelta delta : deltas) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(delta.displayName)
                    .append(" | ")
                    .append(delta.deltaAmount >= 0 ? "+" : "")
                    .append(delta.deltaAmount)
                    .append(" | 当前 ")
                    .append(delta.currentAmount);
        }
        return builder.toString();
    }

    private void renderDeltas(String key, List<WarehouseRecordManager.RepositoryDelta> deltas) {
        repositoryDeltaContainer.removeAllViews();
        LinkedHashSet<Integer> ids = selectedDeltaIds.get(key);
        if (ids == null) {
            ids = new LinkedHashSet<>();
            selectedDeltaIds.put(key, ids);
        }
        if (deltas == null || deltas.isEmpty()) {
            renderMessage(repositoryDeltaContainer, "暂无变化。");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (WarehouseRecordManager.RepositoryDelta delta : deltas) {
            View itemView = inflater.inflate(R.layout.item_panel_cookie_option, repositoryDeltaContainer, false);
            CheckBox checkBox = itemView.findViewById(R.id.check_cookie_option);
            TextView subtitle = itemView.findViewById(R.id.text_cookie_option_subtitle);
            checkBox.setText(delta.displayName);
            checkBox.setChecked(ids.contains(Integer.valueOf(delta.toolId)));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                LinkedHashSet<Integer> selectedIds = selectedDeltaIds.get(key);
                if (selectedIds == null) {
                    selectedIds = new LinkedHashSet<>();
                    selectedDeltaIds.put(key, selectedIds);
                }
                if (isChecked) {
                    selectedIds.add(Integer.valueOf(delta.toolId));
                } else {
                    selectedIds.remove(Integer.valueOf(delta.toolId));
                }
            });
            subtitle.setText("变化：" + delta.deltaAmount + "，当前数量：" + delta.currentAmount);
            repositoryDeltaContainer.addView(itemView);
        }
    }

    private void renderIgnoredList(String key, LinkedHashSet<Integer> ignoredIds) {
        repositoryIgnoredContainer.removeAllViews();
        LinkedHashSet<Integer> ids = selectedIgnoredIds.get(key);
        if (ids == null) {
            ids = new LinkedHashSet<>();
            selectedIgnoredIds.put(key, ids);
        }
        if (ignoredIds == null || ignoredIds.isEmpty()) {
            renderMessage(repositoryIgnoredContainer, "暂无屏蔽物品。");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (Integer toolId : ignoredIds) {
            View itemView = inflater.inflate(R.layout.item_panel_cookie_option, repositoryIgnoredContainer, false);
            CheckBox checkBox = itemView.findViewById(R.id.check_cookie_option);
            TextView subtitle = itemView.findViewById(R.id.text_cookie_option_subtitle);
            checkBox.setText(warehouseRecordManager.nameOf(toolId.intValue()));
            checkBox.setChecked(ids.contains(toolId));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                LinkedHashSet<Integer> selectedIds = selectedIgnoredIds.get(key);
                if (selectedIds == null) {
                    selectedIds = new LinkedHashSet<>();
                    selectedIgnoredIds.put(key, selectedIds);
                }
                if (isChecked) {
                    selectedIds.add(toolId);
                } else {
                    selectedIds.remove(toolId);
                }
            });
            subtitle.setText("物品 ID：" + toolId);
            repositoryIgnoredContainer.addView(itemView);
        }
    }

    private void renderMessage(LinearLayout container, String message) {
        container.removeAllViews();
        TextView textView = new TextView(activity);
        textView.setText(message);
        textView.setTextColor(0xFF4B5563);
        textView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        container.addView(textView);
    }

    private void updateButtonsEnabled(boolean enabled) {
        if (repositoryPickViewCookieButton != null) {
            repositoryPickViewCookieButton.setEnabled(enabled);
        }
        if (repositoryRefreshButton != null) {
            repositoryRefreshButton.setEnabled(enabled);
        }
        if (repositoryRecordButton != null) {
            repositoryRecordButton.setEnabled(enabled);
        }
        if (repositoryCompareButton != null) {
            repositoryCompareButton.setEnabled(enabled);
        }
        if (repositoryIgnoreSelectedButton != null) {
            repositoryIgnoreSelectedButton.setEnabled(enabled);
        }
        if (repositoryRemoveIgnoredButton != null) {
            repositoryRemoveIgnoredButton.setEnabled(enabled);
        }
        setContentEnabled(repositoryCurrentContainer, enabled);
        setContentEnabled(repositoryDeltaContainer, enabled);
        setContentEnabled(repositoryIgnoredContainer, enabled);
    }

    private void setActionButtonsEnabled(boolean enabled) {
        if (repositoryRefreshButton != null) {
            repositoryRefreshButton.setEnabled(enabled);
        }
        if (repositoryRecordButton != null) {
            repositoryRecordButton.setEnabled(enabled);
        }
        if (repositoryCompareButton != null) {
            repositoryCompareButton.setEnabled(enabled);
        }
    }

    private void setContentEnabled(View view, boolean enabled) {
        if (view == null) {
            return;
        }
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1.0f : 0.42f);
    }

    private WarehouseRecordManager.RepositorySnapshot loadRecordedSnapshot(String key) {
        try {
            JSONObject root = new JSONObject(preferenceStore.getRepositoryRecordsJson());
            JSONObject item = root.optJSONObject(key);
            if (item == null) {
                return null;
            }
            JSONObject snapshotObject = item.optJSONObject("snapshot");
            if (snapshotObject == null) {
                return null;
            }
            LinkedHashMap<Integer, Integer> toolAmounts = new LinkedHashMap<>();
            java.util.Iterator<String> keys = snapshotObject.keys();
            while (keys.hasNext()) {
                String toolId = keys.next();
                toolAmounts.put(
                        Integer.valueOf(Integer.parseInt(toolId)),
                        Integer.valueOf(snapshotObject.optInt(toolId, 0))
                );
            }
            ArrayList<WarehouseRecordManager.ToolEntry> entries = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : toolAmounts.entrySet()) {
                entries.add(new WarehouseRecordManager.ToolEntry(
                        entry.getKey().intValue(),
                        entry.getValue().intValue(),
                        warehouseRecordManager.nameOf(entry.getKey().intValue())
                ));
            }
            return new WarehouseRecordManager.RepositorySnapshot(toolAmounts, entries);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveRecordedSnapshot(String key, WarehouseRecordManager.RepositorySnapshot snapshot) {
        if (TextUtils.isEmpty(key) || snapshot == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject(preferenceStore.getRepositoryRecordsJson());
            JSONObject item = root.optJSONObject(key);
            if (item == null) {
                item = new JSONObject();
                root.put(key, item);
            }
            JSONObject snapshotObject = new JSONObject();
            for (Map.Entry<Integer, Integer> entry : snapshot.toolAmounts.entrySet()) {
                snapshotObject.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            item.put("snapshot", snapshotObject);
            if (!item.has("ignored")) {
                item.put("ignored", new JSONArray());
            }
            preferenceStore.setRepositoryRecordsJson(root.toString());
        } catch (Exception ignored) {
        }
    }

    private LinkedHashSet<Integer> loadIgnoredIds(String key) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        try {
            JSONObject root = new JSONObject(preferenceStore.getRepositoryRecordsJson());
            JSONObject item = root.optJSONObject(key);
            if (item == null) {
                return result;
            }
            JSONArray ignored = item.optJSONArray("ignored");
            if (ignored == null) {
                return result;
            }
            for (int i = 0; i < ignored.length(); i += 1) {
                result.add(Integer.valueOf(ignored.optInt(i)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void saveIgnoredIds(String key, LinkedHashSet<Integer> ignoredIds) {
        try {
            JSONObject root = new JSONObject(preferenceStore.getRepositoryRecordsJson());
            JSONObject item = root.optJSONObject(key);
            if (item == null) {
                item = new JSONObject();
                root.put(key, item);
            }
            JSONArray ignored = new JSONArray();
            for (Integer toolId : ignoredIds) {
                ignored.put(toolId);
            }
            item.put("ignored", ignored);
            if (!item.has("snapshot")) {
                item.put("snapshot", new JSONObject());
            }
            preferenceStore.setRepositoryRecordsJson(root.toString());
        } catch (Exception ignored) {
        }
    }

    private void hideUnusedRepositoryViews() {
        hideView(repositoryIgnoreSelectedButton);
        hideView(repositoryRemoveIgnoredButton);
        hideView(repositoryIgnoredContainer);
        hidePreviousLabel(repositoryRemoveIgnoredButton);
    }

    private void hidePreviousLabel(View view) {
        if (view == null) {
            return;
        }
        android.view.ViewParent parent = view.getParent();
        if (!(parent instanceof LinearLayout)) {
            return;
        }
        LinearLayout layout = (LinearLayout) parent;
        int index = layout.indexOfChild(view);
        if (index > 0) {
            View previous = layout.getChildAt(index - 1);
            if (previous instanceof TextView) {
                previous.setVisibility(View.GONE);
            }
        }
    }

    private void hideView(View view) {
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    private int dpToPx(int dp) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

final class BrowserSettingsController {
    interface Host {
        void requestAllFilesAccessPermission();

        void updateUrlInput(String url);

        void loadUrl(String url);

        void onOrientationSelected(int mode);

        int getSelectedFontMode();

        void onFontModeSelected(int mode);

        String getCurrentPageUrl();

        String getCurrentPageTitle();
    }

    private static final int MENU_COOKIE_PROFILES = 100;
    private static final int MENU_MAPPING_CONFIG = 101;
    private static final int MENU_FONT_MODE_SANS = 102;
    private static final int MENU_FONT_MODE_SERIF = 103;
    private static final int MENU_FONT_MODE_EMBEDDED = 104;
    private static final int MENU_NAV_PVZ_YOUKIA = 108;

    private static final int ORIENTATION_LANDSCAPE = 0;
    private static final int ORIENTATION_PORTRAIT = 1;
    private static final int ORIENTATION_SYSTEM = 2;

    private static final int FONT_MODE_CHINESE_SANS = 0;
    private static final int FONT_MODE_CHINESE_SERIF = 1;
    private static final int FONT_MODE_EMBEDDED = 2;

    private static final String PVZ_YOUKIA_ENTRY_URL = "http://pvz.youkia.com/index.php";

    private final AppCompatActivity activity;
    private final CookieProfileManager cookieProfileManager;
    private final LocalMappingManager localMappingManager;
    private final Host host;

    BrowserSettingsController(
            AppCompatActivity activity,
            CookieProfileManager cookieProfileManager,
            LocalMappingManager localMappingManager,
            Host host
    ) {
        this.activity = activity;
        this.cookieProfileManager = cookieProfileManager;
        this.localMappingManager = localMappingManager;
        this.host = host;
    }

    void saveCurrentCookieProfile() {
        if (!cookieProfileManager.canAccessRootDirectory()) {
            Toast.makeText(activity, R.string.cookie_storage_permission_needed, Toast.LENGTH_LONG).show();
            host.requestAllFilesAccessPermission();
            return;
        }

        String currentUrl = host.getCurrentPageUrl();
        if (TextUtils.isEmpty(currentUrl)) {
            Toast.makeText(activity, "No page to save cookie from", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri currentUri = Uri.parse(currentUrl);
        if (!CookieProfileManager.isSupportedSavePage(currentUri)) {
            Toast.makeText(activity, "Current page cannot be saved as a cookie profile", Toast.LENGTH_SHORT).show();
            return;
        }

        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(currentUrl);
        if (CookieProfileManager.isLegacyYoukiaLandingPage(currentUri)) {
            String targetMainUrl = CookieProfileManager.buildMainTargetUrlForPage(currentUri);
            String targetRootUrl = CookieProfileManager.buildRootUrl(currentUri);
            String targetCookies = TextUtils.isEmpty(targetMainUrl) ? null : cookieManager.getCookie(targetMainUrl);
            if (TextUtils.isEmpty(targetCookies) && !TextUtils.isEmpty(targetRootUrl)) {
                targetCookies = cookieManager.getCookie(targetRootUrl);
            }
            if (!TextUtils.isEmpty(targetCookies)) {
                cookies = targetCookies;
            }
        }
        if (TextUtils.isEmpty(cookies)) {
            Toast.makeText(activity, "Current page has no cookies", Toast.LENGTH_SHORT).show();
            return;
        }

        File savedFile = cookieProfileManager.saveProfileFromPage(
                currentUri,
                cookies,
                host.getCurrentPageTitle()
        );
        if (savedFile == null) {
            Toast.makeText(activity, "Failed to save cookie", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(activity, "Saved cookie: " + savedFile.getName(), Toast.LENGTH_SHORT).show();
    }

    void showSettingsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        int fontMode = host.getSelectedFontMode();
        menu.getMenu().add(0, MENU_COOKIE_PROFILES, 0, activity.getString(R.string.cookie_profiles));
        menu.getMenu().add(0, MENU_FONT_MODE_SANS, 1, buildFontMenuTitle(fontMode, FONT_MODE_CHINESE_SANS, "Ruffle 字体: 中文无衬线"));
        menu.getMenu().add(0, MENU_FONT_MODE_SERIF, 2, buildFontMenuTitle(fontMode, FONT_MODE_CHINESE_SERIF, "Ruffle 字体: 中文衬线"));
        menu.getMenu().add(0, MENU_FONT_MODE_EMBEDDED, 3, buildFontMenuTitle(fontMode, FONT_MODE_EMBEDDED, "Ruffle 字体: 关闭设备字体"));
        menu.getMenu().add(0, ORIENTATION_LANDSCAPE, 4, activity.getString(R.string.orientation_landscape));
        menu.getMenu().add(0, ORIENTATION_PORTRAIT, 5, activity.getString(R.string.orientation_portrait));
        menu.getMenu().add(0, ORIENTATION_SYSTEM, 6, activity.getString(R.string.orientation_system));
        menu.getMenu().add(0, MENU_MAPPING_CONFIG, 7, activity.getString(R.string.mapping_config_path));
        menu.getMenu().add(0, MENU_NAV_PVZ_YOUKIA, 8, "导航到 pvz.youkia.com");
        menu.setOnMenuItemClickListener(item -> handleSettingsMenuItem(item.getItemId()));
        menu.show();
    }

    private boolean handleSettingsMenuItem(int itemId) {
        if (itemId == MENU_COOKIE_PROFILES) {
            showCookieProfilesDialog();
            return true;
        }
        if (itemId == MENU_FONT_MODE_SANS) {
            host.onFontModeSelected(FONT_MODE_CHINESE_SANS);
            return true;
        }
        if (itemId == MENU_FONT_MODE_SERIF) {
            host.onFontModeSelected(FONT_MODE_CHINESE_SERIF);
            return true;
        }
        if (itemId == MENU_FONT_MODE_EMBEDDED) {
            host.onFontModeSelected(FONT_MODE_EMBEDDED);
            return true;
        }
        if (itemId == MENU_MAPPING_CONFIG) {
            Toast.makeText(activity, localMappingManager.getConfigFile().getAbsolutePath(), Toast.LENGTH_LONG).show();
            return true;
        }
        if (itemId == MENU_NAV_PVZ_YOUKIA) {
            host.loadUrl(PVZ_YOUKIA_ENTRY_URL);
            return true;
        }
        host.onOrientationSelected(itemId);
        return true;
    }

    private void showCookieProfilesDialog() {
        if (!cookieProfileManager.canAccessRootDirectory()) {
            Toast.makeText(activity, R.string.cookie_storage_permission_needed, Toast.LENGTH_LONG).show();
            host.requestAllFilesAccessPermission();
            return;
        }

        if (!cookieProfileManager.ensureInitialized()) {
            Toast.makeText(activity, R.string.cookie_copy_failed, Toast.LENGTH_LONG).show();
            return;
        }

        List<CookieProfileGroup> groups = buildCookieProfileGroups(cookieProfileManager.loadProfiles());
        if (groups.isEmpty()) {
            Toast.makeText(activity, R.string.cookie_no_profiles, Toast.LENGTH_LONG).show();
            return;
        }

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_cookie_profiles, null);
        LinearLayout container = dialogView.findViewById(R.id.cookie_profile_container);
        Button cleanupAllButton = dialogView.findViewById(R.id.btn_cleanup_all_cookie_duplicates);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.cookie_profile_title)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        Runnable refreshProfiles = () -> renderCookieProfiles(
                container,
                buildCookieProfileGroups(cookieProfileManager.loadProfiles()),
                dialog
        );
        cleanupAllButton.setOnClickListener(v -> {
            List<CookieProfileGroup> currentGroups = buildCookieProfileGroups(cookieProfileManager.loadProfiles());
            ArrayList<List<CookieProfileManager.CookieProfile>> duplicateGroups = new ArrayList<>();
            for (CookieProfileGroup group : currentGroups) {
                if (group != null && group.profiles.size() > 1) {
                    duplicateGroups.add(group.profiles);
                }
            }
            int cleanedGroups = cookieProfileManager.cleanupAllDuplicateProfiles(duplicateGroups);
            Toast.makeText(
                    activity,
                    cleanedGroups > 0 ? R.string.cookie_cleanup_all_done : R.string.cookie_cleanup_none,
                    Toast.LENGTH_SHORT
            ).show();
            refreshProfiles.run();
        });

        refreshProfiles.run();

        File cookieDir = cookieProfileManager.getRootDirectory();
        FileObserver observer = new FileObserver(cookieDir.getAbsolutePath(),
                FileObserver.CREATE
                        | FileObserver.CLOSE_WRITE
                        | FileObserver.MOVED_TO
                        | FileObserver.DELETE
                        | FileObserver.MOVED_FROM) {
            @Override
            public void onEvent(int event, String path) {
                activity.runOnUiThread(() -> {
                    if (!dialog.isShowing()) {
                        return;
                    }
                    renderCookieProfiles(
                            container,
                            buildCookieProfileGroups(cookieProfileManager.loadProfiles()),
                            dialog
                    );
                });
            }
        };
        dialog.setOnShowListener(dialogInterface -> observer.startWatching());
        dialog.setOnDismissListener(dialogInterface -> observer.stopWatching());
        dialog.show();
    }

    private void renderCookieProfiles(
            LinearLayout container,
            List<CookieProfileGroup> groups,
            AlertDialog dialog
    ) {
        container.removeAllViews();
        if (groups.isEmpty()) {
            TextView emptyView = new TextView(activity);
            emptyView.setText(R.string.cookie_no_profiles);
            emptyView.setTextColor(0xFF374151);
            emptyView.setPadding(8, 8, 8, 8);
            container.addView(emptyView);
            return;
        }

        for (CookieProfileGroup group : groups) {
            View itemView = LayoutInflater.from(activity).inflate(R.layout.item_cookie_profile, container, false);
            TextView fileName = itemView.findViewById(R.id.text_file_name);
            TextView userName = itemView.findViewById(R.id.text_user_name);
            View cleanupButton = itemView.findViewById(R.id.btn_cleanup_cookie_duplicates);
            fileName.setText(activity.getString(R.string.cookie_file_name, group.displayFileNames));
            userName.setText(activity.getString(R.string.cookie_user_name, group.displayUserNames));
            itemView.findViewById(R.id.btn_edit_cookie).setOnClickListener(v ->
                    showEditCookieProfileDialog(group, () ->
                            renderCookieProfiles(
                                    container,
                                    buildCookieProfileGroups(cookieProfileManager.loadProfiles()),
                                    dialog
                            )
                    )
            );
            cleanupButton.setEnabled(group.profiles.size() > 1);
            cleanupButton.setAlpha(group.profiles.size() > 1 ? 1f : 0.45f);
            cleanupButton.setOnClickListener(v -> {
                boolean cleaned = cookieProfileManager.cleanupDuplicateProfiles(group.profiles);
                Toast.makeText(
                        activity,
                        cleaned ? R.string.cookie_cleanup_done : R.string.cookie_cleanup_none,
                        Toast.LENGTH_SHORT
                ).show();
                renderCookieProfiles(
                        container,
                        buildCookieProfileGroups(cookieProfileManager.loadProfiles()),
                        dialog
                );
            });
            itemView.findViewById(R.id.btn_delete_cookie).setOnClickListener(v ->
                    showDeleteCookieConfirmationDialog(group, () ->
                            renderCookieProfiles(
                                    container,
                                    buildCookieProfileGroups(cookieProfileManager.loadProfiles()),
                                    dialog
                            )
                    )
            );
            itemView.findViewById(R.id.btn_apply_cookie).setOnClickListener(v -> {
                dialog.dismiss();
                applyCookieProfile(group.primaryProfile);
            });
            container.addView(itemView);
        }
    }

    private void showDeleteCookieConfirmationDialog(CookieProfileGroup group, Runnable onDeleted) {
        if (group == null || group.profiles.isEmpty()) {
            return;
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int horizontalPadding = Math.round(20f * density);
        int verticalPadding = Math.round(12f * density);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        TextView messageView = new TextView(activity);
        messageView.setText(R.string.cookie_delete_message);
        messageView.setTextColor(0xFF374151);
        layout.addView(messageView);

        EditText confirmEdit = new EditText(activity);
        confirmEdit.setHint(R.string.cookie_delete_confirm_hint);
        confirmEdit.setSingleLine(true);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputParams.topMargin = Math.round(12f * density);
        confirmEdit.setLayoutParams(inputParams);
        layout.addView(confirmEdit);

        AlertDialog confirmDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.cookie_delete_title)
                .setView(layout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.cookie_delete, null)
                .create();
        confirmDialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                if (!"我已知晓".contentEquals(confirmEdit.getText())) {
                    confirmEdit.setError("请输入“我已知晓”");
                    return;
                }
                boolean deleted = cookieProfileManager.deleteProfiles(group.profiles);
                Toast.makeText(
                        activity,
                        deleted ? R.string.cookie_deleted : R.string.cookie_delete_failed,
                        Toast.LENGTH_SHORT
                ).show();
                if (deleted && onDeleted != null) {
                    onDeleted.run();
                }
                if (deleted) {
                    confirmDialog.dismiss();
                }
            });
        });
        confirmDialog.show();
    }

    private List<CookieProfileGroup> buildCookieProfileGroups(List<CookieProfileManager.CookieProfile> profiles) {
        LinkedHashMap<String, CookieProfileGroup> grouped = new LinkedHashMap<>();
        ArrayList<CookieProfileGroup> fallbackGroups = new ArrayList<>();
        int fallbackIndex = 1;
        for (CookieProfileManager.CookieProfile profile : profiles) {
            if (profile == null) {
                continue;
            }
            String key = CookieProfileManager.buildCookieIdentityKey(profile.userCookies);
            if (TextUtils.isEmpty(key)) {
                fallbackGroups.add(new CookieProfileGroup("fallback_" + fallbackIndex, profile));
                fallbackIndex += 1;
                continue;
            }
            CookieProfileGroup group = grouped.get(key);
            if (group == null) {
                group = new CookieProfileGroup(key, profile);
                grouped.put(key, group);
            } else {
                group.add(profile);
            }
        }
        ArrayList<CookieProfileGroup> result = new ArrayList<>(grouped.values());
        result.addAll(fallbackGroups);
        return result;
    }

    private void showEditCookieProfileDialog(CookieProfileGroup group, Runnable onUpdated) {
        if (group == null || group.primaryProfile == null) {
            return;
        }
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_cookie_profile_editor, null);
        TextView messageView = dialogView.findViewById(R.id.text_cookie_editor_message);
        EditText fileNameEdit = dialogView.findViewById(R.id.edit_cookie_file_name);
        EditText userNameEdit = dialogView.findViewById(R.id.edit_cookie_user_name);

        String originalFileName = group.primaryProfile.file == null ? "" : group.primaryProfile.file.getName();
        int dotIndex = originalFileName.toLowerCase(Locale.US).lastIndexOf(".xml");
        if (dotIndex > 0) {
            originalFileName = originalFileName.substring(0, dotIndex);
        }
        fileNameEdit.setText(originalFileName);
        fileNameEdit.setSelection(fileNameEdit.getText().length());
        userNameEdit.setText(group.primaryProfile.userName);
        userNameEdit.setSelection(userNameEdit.getText().length());

        if (group.profiles.size() > 1) {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText("当前条目包含多个同内容 Cookie 文件，修改会同步到这一组全部文件。");
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.cookie_edit_title)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clipboard_cookie_confirm_save, (dialog, which) -> {
                    File updatedFile = cookieProfileManager.updateProfilesMetadata(
                            group.profiles,
                            fileNameEdit.getText().toString(),
                            userNameEdit.getText().toString()
                    );
                    if (updatedFile == null) {
                        Toast.makeText(activity, R.string.cookie_edit_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(activity, R.string.cookie_edit_saved, Toast.LENGTH_SHORT).show();
                    if (onUpdated != null) {
                        onUpdated.run();
                    }
                })
                .show();
    }

    private void applyCookieProfile(CookieProfileManager.CookieProfile profile) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(value -> applyCookieProfileAfterClearing(profile));
            cookieManager.flush();
        } else {
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            applyCookieProfileAfterClearing(profile);
        }
    }

    private void applyCookieProfileAfterClearing(CookieProfileManager.CookieProfile profile) {
        if (profile == null) {
            return;
        }
        String targetUrl = CookieProfileManager.buildTargetUrl(profile);
        CookieManager cookieManager = CookieManager.getInstance();
        List<String> cookieEntries = CookieProfileManager.buildCookieApplicationList(profile.userCookies);
        if (cookieEntries.isEmpty()) {
            cookieManager.setCookie(profile.userDomain, profile.userCookies);
            cookieManager.setCookie(targetUrl, profile.userCookies);
        } else {
            for (String cookieEntry : cookieEntries) {
                cookieManager.setCookie(profile.userDomain, cookieEntry);
                cookieManager.setCookie(targetUrl, cookieEntry);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
        }
        host.updateUrlInput(targetUrl);
        host.loadUrl(targetUrl);
        Toast.makeText(activity, R.string.cookie_applied, Toast.LENGTH_SHORT).show();
    }

    private String buildFontMenuTitle(int currentMode, int itemMode, String label) {
        return (currentMode == itemMode ? "✓ " : "") + label;
    }

    private static final class CookieProfileGroup {
        final String key;
        final ArrayList<CookieProfileManager.CookieProfile> profiles = new ArrayList<>();
        final LinkedHashSet<String> fileNames = new LinkedHashSet<>();
        final LinkedHashSet<String> userNames = new LinkedHashSet<>();
        CookieProfileManager.CookieProfile primaryProfile;
        String displayFileNames;
        String displayUserNames;

        CookieProfileGroup(String key, CookieProfileManager.CookieProfile profile) {
            this.key = key;
            add(profile);
        }

        void add(CookieProfileManager.CookieProfile profile) {
            if (profile == null) {
                return;
            }
            if (primaryProfile == null) {
                primaryProfile = profile;
            }
            profiles.add(profile);
            if (profile.file != null) {
                fileNames.add(profile.file.getName());
            }
            if (!TextUtils.isEmpty(profile.userName)) {
                userNames.add(profile.userName);
            }
            displayFileNames = TextUtils.join("; ", fileNames);
            displayUserNames = TextUtils.join("; ", userNames);
        }
    }
}

final class ClipboardCookieImportController {
    private final AppCompatActivity activity;
    private final CookieProfileManager cookieProfileManager;
    private final Runnable requestAllFilesAccessAction;
    private final Runnable delayedClipboardCheck = this::checkClipboardIfNeeded;

    private AlertDialog confirmDialog;
    private CookieProfileManager.ImportedProfile pendingImportProfile;
    private String pendingImportSignature;
    private String pendingImportFileName;
    private String pendingImportUserName;
    private String lastHandledSignature;
    private String lastDismissedSignature;
    private boolean waitingForStoragePermission;

    ClipboardCookieImportController(
            AppCompatActivity activity,
            CookieProfileManager cookieProfileManager,
            Runnable requestAllFilesAccessAction
    ) {
        this.activity = activity;
        this.cookieProfileManager = cookieProfileManager;
        this.requestAllFilesAccessAction = requestAllFilesAccessAction;
    }

    void onResume() {
        if (waitingForStoragePermission) {
            if (cookieProfileManager.canAccessRootDirectory() && pendingImportProfile != null) {
                saveImportedProfile(
                        pendingImportProfile,
                        pendingImportSignature,
                        pendingImportFileName,
                        pendingImportUserName
                );
            }
        }
    }

    void onWindowFocusChanged(boolean hasFocus) {
        activity.getWindow().getDecorView().removeCallbacks(delayedClipboardCheck);
        if (!hasFocus) {
            return;
        }
        activity.getWindow().getDecorView().postDelayed(delayedClipboardCheck, 200L);
    }

    private void checkClipboardIfNeeded() {
        if (confirmDialog != null && confirmDialog.isShowing()) {
            return;
        }

        ClipboardSnapshot snapshot = readClipboardSnapshot();
        if (snapshot == null) {
            return;
        }
        if (snapshot.signature.equals(lastHandledSignature) || snapshot.signature.equals(lastDismissedSignature)) {
            return;
        }

        CookieProfileManager.ImportedProfile importedProfile =
                cookieProfileManager.parseImportedProfileText(snapshot.text);
        if (importedProfile == null) {
            return;
        }
        showImportConfirmation(importedProfile, snapshot.signature);
    }

    private ClipboardSnapshot readClipboardSnapshot() {
        if (!activity.hasWindowFocus()) {
            return null;
        }
        ClipboardManager clipboardManager =
                (ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            return null;
        }
        android.content.ClipData clipData;
        try {
            clipData = clipboardManager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() == 0) {
                return null;
            }
        } catch (SecurityException e) {
            Log.w("FlashBrowser", "Clipboard access denied before window focus settles", e);
            return null;
        }
        CharSequence coerced = clipData.getItemAt(0).coerceToText(activity);
        if (TextUtils.isEmpty(coerced)) {
            return null;
        }
        String text = coerced.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return new ClipboardSnapshot(text, buildSignature(text));
    }

    private void showImportConfirmation(
            CookieProfileManager.ImportedProfile importedProfile,
            String signature
    ) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_cookie_profile_editor, null);
        TextView messageView = dialogView.findViewById(R.id.text_cookie_editor_message);
        EditText fileNameEdit = dialogView.findViewById(R.id.edit_cookie_file_name);
        EditText userNameEdit = dialogView.findViewById(R.id.edit_cookie_user_name);
        View serverSelectionLayout = dialogView.findViewById(R.id.layout_cookie_server_selection);
        Spinner serverSpinner = dialogView.findViewById(R.id.spinner_cookie_server);
        EditText serverNumberEdit = dialogView.findViewById(R.id.edit_cookie_server_number);
        messageView.setVisibility(View.VISIBLE);
        boolean requiresServerSelection = importedProfile.requiresServerSelection;
        messageView.setText(requiresServerSelection
                ? R.string.clipboard_cookie_detected_message_select_server
                : R.string.clipboard_cookie_detected_message);

        if (requiresServerSelection) {
            serverSelectionLayout.setVisibility(View.VISIBLE);
            ArrayList<String> serverOptions = buildServerOptionLabels();
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    activity,
                    android.R.layout.simple_spinner_item,
                    serverOptions
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            serverSpinner.setAdapter(adapter);
            serverSpinner.setSelection(0, false);
            serverSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position > 0) {
                        serverNumberEdit.setText(serverOptions.get(position));
                        serverNumberEdit.setSelection(serverNumberEdit.getText().length());
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        } else {
            serverSelectionLayout.setVisibility(View.GONE);
        }

        String defaultName = TextUtils.isEmpty(importedProfile.userName)
                ? cookieProfileManager.buildDefaultProfileName()
                : importedProfile.userName;
        fileNameEdit.setText(defaultName);
        fileNameEdit.setSelection(fileNameEdit.getText().length());
        userNameEdit.setText(defaultName);
        userNameEdit.setSelection(userNameEdit.getText().length());

        final boolean[] accepted = {false};
        confirmDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.clipboard_cookie_detected_title)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> lastDismissedSignature = signature)
                .setPositiveButton(R.string.clipboard_cookie_confirm_save, null)
                .create();
        confirmDialog.setOnShowListener(dialog -> {
            Button positiveButton = confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                CookieProfileManager.ImportedProfile resolvedProfile = importedProfile;
                if (requiresServerSelection) {
                    int serverNumber = resolveClipboardServerNumber(
                            serverSpinner,
                            serverNumberEdit.getText().toString()
                    );
                    if (serverNumber < 1 || serverNumber > 46) {
                        Toast.makeText(activity, R.string.clipboard_cookie_server_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String userDomain = CookieProfileManager.buildServerRootUrlFromLegacySubdomain("s" + serverNumber);
                    if (TextUtils.isEmpty(userDomain)) {
                        Toast.makeText(activity, R.string.clipboard_cookie_server_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    resolvedProfile = importedProfile.withUserDomain(userDomain);
                }
                accepted[0] = true;
                handleConfirmedImport(
                        resolvedProfile,
                        signature,
                        fileNameEdit.getText().toString(),
                        userNameEdit.getText().toString()
                );
                confirmDialog.dismiss();
            });
        });
        confirmDialog.setOnDismissListener(dialog -> {
            if (!accepted[0]) {
                lastDismissedSignature = signature;
            }
            confirmDialog = null;
        });
        confirmDialog.show();
    }

    private void handleConfirmedImport(
            CookieProfileManager.ImportedProfile importedProfile,
            String signature,
            String fileName,
            String userName
    ) {
        if (!cookieProfileManager.canAccessRootDirectory()) {
            pendingImportProfile = importedProfile;
            pendingImportSignature = signature;
            pendingImportFileName = fileName;
            pendingImportUserName = userName;
            waitingForStoragePermission = true;
            requestAllFilesAccessAction.run();
            return;
        }
        saveImportedProfile(importedProfile, signature, fileName, userName);
    }

    private void saveImportedProfile(
            CookieProfileManager.ImportedProfile importedProfile,
            String signature,
            String fileName,
            String userName
    ) {
        File savedFile = cookieProfileManager.saveImportedProfile(importedProfile, fileName, userName);
        waitingForStoragePermission = false;
        pendingImportProfile = null;
        pendingImportSignature = null;
        pendingImportFileName = null;
        pendingImportUserName = null;
        if (savedFile == null) {
            Toast.makeText(activity, R.string.clipboard_cookie_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        lastHandledSignature = signature;
        lastDismissedSignature = null;
        Toast.makeText(
                activity,
                activity.getString(R.string.clipboard_cookie_saved, savedFile.getName()),
                Toast.LENGTH_LONG
        ).show();
    }

    private ArrayList<String> buildServerOptionLabels() {
        ArrayList<String> options = new ArrayList<>(47);
        options.add(activity.getString(R.string.clipboard_cookie_server_select));
        for (int server = 1; server <= 46; server += 1) {
            options.add(String.valueOf(server));
        }
        return options;
    }

    private int resolveClipboardServerNumber(Spinner serverSpinner, String manualInput) {
        String normalizedManual = manualInput == null ? "" : manualInput.replaceAll("[^0-9]", "").trim();
        if (!TextUtils.isEmpty(normalizedManual)) {
            try {
                return Integer.parseInt(normalizedManual);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        if (serverSpinner == null || serverSpinner.getSelectedItemPosition() <= 0) {
            return -1;
        }
        Object selected = serverSpinner.getSelectedItem();
        if (!(selected instanceof String)) {
            return -1;
        }
        try {
            return Integer.parseInt(((String) selected).trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String buildSignature(String text) {
        return Integer.toHexString(text.hashCode()) + ":" + text.length();
    }

    private static final class ClipboardSnapshot {
        final String text;
        final String signature;

        ClipboardSnapshot(String text, String signature) {
            this.text = text;
            this.signature = signature;
        }
    }
}
