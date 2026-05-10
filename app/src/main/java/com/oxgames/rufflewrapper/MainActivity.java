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
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Message;
import android.os.SystemClock;
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
    private static final String TAG = "RuffleWrapper";
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
    private static final int MENU_COOKIE_PROFILES = 100;
    private static final int MENU_MAPPING_CONFIG = 101;
    private static final int MENU_FONT_MODE_SANS = 102;
    private static final int MENU_FONT_MODE_SERIF = 103;
    private static final int MENU_FONT_MODE_EMBEDDED = 104;
    private static final int MENU_MANUAL_TEST_DIRECT = 105;
    private static final int MENU_MANUAL_TEST_PROXY = 106;
    private static final int MENU_RUFFLE_DEBUG_DUMP = 107;
    private static final int MENU_NAV_PVZ_YOUKIA = 108;
    private static final int FEATURE_PANEL_TAB_COOKIE = 0;
    private static final int FEATURE_PANEL_TAB_BASIC = 1;
    private static final int FEATURE_PANEL_TAB_REPOSITORY = 2;
    private static final int FEATURE_PANEL_TAB_LOG = 3;
    private static final int FONT_MODE_CHINESE_SANS = 0;
    private static final int FONT_MODE_CHINESE_SERIF = 1;
    private static final int FONT_MODE_EMBEDDED = 2;
    private static final String RUFFLE_PATH_PREFIX = "/__ruffle__/";
    private static final String PROXY_PATH_PREFIX = "/__proxy__/";
    private static final String BOOTSTRAP_SCRIPT = "bootstrap.js";
    private static final String PVZ_YOUKIA_ENTRY_URL = "http://pvz.youkia.com/index.php";
    private static final long HOVER_HOLD_MS = 450L;
    private static final long MENU_HOLD_MS = 700L;
    private static final float TOUCH_HOLD_MOVE_TOLERANCE_DP = 18f;
    private static final String DEFAULT_MANUAL_TEST_SWF = "http://pvzol.org/youkia/main.swf";
    private static final String MANUAL_TEST_PROXY_BASE_URL = "https://webbrowsertools.com/__manual_test__/index.html";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final Pattern CHARSET_PATTERN =
            Pattern.compile("charset=([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSP_META_PATTERN =
            Pattern.compile(
                    "<meta[^>]+http-equiv\\s*=\\s*(['\"])content-security-policy(?:-report-only)?\\1[^>]*>",
                    Pattern.CASE_INSENSITIVE);
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
            "accept-encoding",
            "content-length",
            "host"
    ));
    private static final Set<String> OVERRIDDEN_IE_REQUEST_HEADERS = new HashSet<>(Arrays.asList(
            "user-agent",
            "accept",
            "accept-language",
            "x-requested-with",
            "sec-ch-ua",
            "sec-ch-ua-mobile",
            "sec-ch-ua-platform",
            "sec-ch-ua-platform-version",
            "sec-ch-ua-model",
            "sec-ch-ua-full-version",
            "sec-ch-ua-full-version-list",
            "sec-fetch-site",
            "sec-fetch-mode",
            "sec-fetch-user",
            "sec-fetch-dest",
            "priority"
    ));
    private static final Set<String> STRIPPED_RESPONSE_HEADERS = new HashSet<>(Arrays.asList(
            "content-encoding",
            "content-length",
            "content-security-policy",
            "content-security-policy-report-only",
            "x-frame-options"
    ));

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
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean inAppRuffleFullscreen;
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
    private ScaleGestureDetector scaleGestureDetector;
    private float touchHoldMoveTolerancePx;
    private float gestureAnchorX;
    private float gestureAnchorY;
    private boolean simulatedHoverActive;
    private boolean consumeTouchUntilGestureEnd;
    private boolean hoverModeArmedForClick;
    private boolean hoverEnteredByHold;
    private boolean flashTouchBridgeAvailable;
    private boolean syntheticMouseHoverActive;
    private BrowserPreferenceStore preferenceStore;
    private LocalMappingManager localMappingManager;
    private CookieProfileManager cookieProfileManager;
    private final DutyRequestQueue dutyRequestQueue = new DutyRequestQueue();
    private final FeaturePanelUiController featurePanelUiController = new FeaturePanelUiController();
    private String pendingLegacyYoukiaSourceUrl;
    private String pendingLegacyYoukiaTargetUrl;
    private AlertDialog featurePanelDialog;
    private Button featurePanelTabCookieButton;
    private Button featurePanelTabBasicButton;
    private Button featurePanelTabRepositoryButton;
    private Button featurePanelTabLogButton;
    private View featurePanelCookiePage;
    private View featurePanelBasicPage;
    private View featurePanelRepositoryPage;
    private View featurePanelLogPage;
    private LinearLayout featurePanelCookieContainer;
    private TextView featurePanelCookieHintText;
    private Button featurePanelSelectAllCookiesButton;
    private EditText featurePanelConcurrencyInput;
    private EditText featurePanelRequestIntervalInput;
    private EditText featurePanelFrequentRetryIntervalInput;
    private Button featurePanelPauseResumeButton;
    private Button featurePanelCancelButton;
    private CheckBox featurePanelDailyDutyCheckBox;
    private Button featurePanelDailyDutyRunButton;
    private Button featurePanelStartSelectedButton;
    private Button featurePanelStorageAccessButton;
    private TextView featurePanelRepositoryHintText;
    private TextView featurePanelRepositorySelectedTargetsText;
    private Button featurePanelRepositoryPickTargetsButton;
    private TextView featurePanelRepositoryViewCookieText;
    private Button featurePanelRepositoryPickViewCookieButton;
    private Button featurePanelRepositoryRefreshButton;
    private Button featurePanelRepositoryRecordButton;
    private Button featurePanelRepositoryCompareButton;
    private Button featurePanelRepositoryIgnoreSelectedButton;
    private Button featurePanelRepositoryRemoveIgnoredButton;
    private LinearLayout featurePanelRepositoryCurrentContainer;
    private LinearLayout featurePanelRepositoryDeltaContainer;
    private LinearLayout featurePanelRepositoryIgnoredContainer;
    private TextView featurePanelQueueStatusText;
    private TextView featurePanelQueueLogText;
    private final ArrayList<FeatureCookieChoice> featureCookieChoices = new ArrayList<>();
    private final java.util.LinkedHashSet<String> sessionRepositoryRecordChoiceKeys = new java.util.LinkedHashSet<>();
    private final java.util.HashMap<String, WarehouseRecordManager.RepositorySnapshot> featureRepositoryCurrentSnapshots = new java.util.HashMap<>();
    private final java.util.HashMap<String, java.util.LinkedHashSet<Integer>> featureRepositorySelectedDeltaIds = new java.util.HashMap<>();
    private final java.util.HashMap<String, java.util.LinkedHashSet<Integer>> featureRepositorySelectedIgnoredIds = new java.util.HashMap<>();
    private WarehouseRecordManager warehouseRecordManager;
    private String sessionRepositoryViewChoiceKey;
    private boolean sessionRepositoryRecordSelectionInitialized;
    private boolean featureRepositoryCurrentExpanded;
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
        warehouseRecordManager = new WarehouseRecordManager(this);
        cookieProfileManager.ensureInitialized();
        dutyRequestQueue.setListener(snapshot -> runOnUiThread(() -> renderFeaturePanelQueueState(snapshot)));

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
        captureInsetAwareBaseValues();
        installWindowInsetHandlers();
        touchHoldMoveTolerancePx = getResources().getDisplayMetrics().density * TOUCH_HOLD_MOVE_TOLERANCE_DP;
        wrapper.setHapticFeedbackEnabled(false);
        wrapper.setOnLongClickListener(v -> true);
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                if (Float.isNaN(factor) || Float.isInfinite(factor)) {
                    return false;
                }

                factor = Math.max(0.75f, Math.min(1.25f, factor));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    wrapper.zoomBy(factor);
                } else if (factor > 1.02f) {
                    wrapper.zoomIn();
                } else if (factor < 0.98f) {
                    wrapper.zoomOut();
                }
                return true;
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (wrapper.canGoBack()) {
                wrapper.goBack();
            }
        });

        findViewById(R.id.btn_forward).setOnClickListener(v -> {
            if (wrapper.canGoForward()) {
                wrapper.goForward();
            }
        });

        findViewById(R.id.btn_refresh).setOnClickListener(v -> wrapper.reload());
        findViewById(R.id.btn_go).setOnClickListener(v -> showFeaturePanelDialog());
        findViewById(R.id.btn_fullscreen).setOnClickListener(v -> toggleRuffleFullscreenCompat());
        findViewById(R.id.btn_save_cookie).setOnClickListener(v -> saveCurrentCookieProfile());
        fullscreenFeaturePanelButton.setOnClickListener(v -> toggleFeaturePanelDialog());
        legacyYoukiaRedirectButton.setOnClickListener(v -> performPendingLegacyYoukiaRedirect());
        fullscreenRotateButton.setOnClickListener(v -> rotateFullscreenOrientation());
        fullscreenExitButton.setOnClickListener(v -> {
            if (customView != null) {
                hideFullscreenContent();
            } else if (inAppRuffleFullscreen) {
                toggleRuffleFullscreenCompat();
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(this::showSettingsMenu);

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                loadFromInput();
                return true;
            }
            return false;
        });

        setupWebView();
        if (savedInstanceState != null && wrapper.restoreState(savedInstanceState) != null) {
            String restoredUrl = wrapper.getUrl();
            updateUrlInput(TextUtils.isEmpty(restoredUrl) ? DEFAULT_URL : restoredUrl);
        } else {
            urlInput.setText(DEFAULT_URL);
            loadUrl(DEFAULT_URL);
        }
        wrapper.setOnTouchListener((v, event) -> handleWebViewTouch(event));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (featurePanelDialog != null && featurePanelDialog.isShowing()) {
            refreshFeaturePanelCookieChoices();
            renderFeaturePanelQueueState(dutyRequestQueue.snapshot());
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
        View root = findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            boolean fullscreenActive = customView != null || inAppRuffleFullscreen;

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
        View root = findViewById(android.R.id.content);
        if (root != null) {
            ViewCompat.requestApplyInsets(root);
        }
    }

    private void setSystemBarsHidden(boolean hidden) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
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

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                resetFullscreenStateForNavigation();
                hideLegacyYoukiaRedirectPrompt();
                flashTouchBridgeAvailable = false;
                progressBar.setVisibility(View.VISIBLE);
                updateUrlInput(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                ensureFullscreenStateMatchesPage();
                refreshFlashTouchBridgeAvailability();
                wrapper.postDelayed(MainActivity.this::refreshFlashTouchBridgeAvailability, 600L);
                updateLegacyYoukiaRedirectPrompt(url);
                updateUrlInput(url);
                if (progressBar.getProgress() >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
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
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                if (newProgress >= 100) {
                    updateUrlInput(view.getUrl());
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showFullscreenContent(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenContent();
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

    private void loadFromInput() {
        String rawInput = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
        if (rawInput.isEmpty()) {
            return;
        }

        String targetUrl = normalizeInputToUrl(rawInput);
        updateUrlInput(targetUrl);
        hideKeyboard();
        wrapper.requestFocus();
        loadUrl(targetUrl);
    }

    private void loadUrl(String url) {
        wrapper.loadUrl(url);
    }

    private void copyCookiesForRedirect(String sourceUrl, String targetUrl) {
        if (TextUtils.isEmpty(sourceUrl) || TextUtils.isEmpty(targetUrl)) {
            return;
        }

        try {
            CookieManager cookieManager = CookieManager.getInstance();
            String cookies = cookieManager.getCookie(sourceUrl);
            if (TextUtils.isEmpty(cookies)) {
                return;
            }
            cookieManager.setCookie(targetUrl, cookies);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy cookies during redirect: " + sourceUrl + " -> " + targetUrl, e);
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

    private void performPendingLegacyYoukiaRedirect() {
        String sourceUrl = pendingLegacyYoukiaSourceUrl;
        String targetUrl = pendingLegacyYoukiaTargetUrl;
        hideLegacyYoukiaRedirectPrompt();
        if (TextUtils.isEmpty(sourceUrl) || TextUtils.isEmpty(targetUrl)) {
            return;
        }
        copyCookiesForRedirect(sourceUrl, targetUrl);
        wrapper.loadUrl(targetUrl);
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

            return "http://" + subdomain + ".youkia.pvz.youkia.com/pvz/index.php/default/main";
        } catch (Exception e) {
            Log.e(TAG, "Failed to rewrite youkia url: " + rawUrl, e);
            return null;
        }
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

        hideKeyboard();
        wrapper.requestFocus();
        wrapper.loadDataWithBaseURL(baseUrl, html, "text/html", StandardCharsets.UTF_8.name(), null);
        urlInput.setText((useProxy ? "manual-proxy: " : "manual-direct: ") + sourceUrl);
        urlInput.setSelection(urlInput.getText().length());
        Toast.makeText(this, useProxy ? "Opened manual proxy test" : "Opened manual direct test", Toast.LENGTH_SHORT).show();
    }

    private String resolveManualTestSwfUrl() {
        String rawInput = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
        if (isLikelySwfUrl(rawInput)) {
            return normalizeInputToUrl(rawInput);
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
        html.append("<script>").append(buildRuffleConfigScript()).append("</script>");
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
        html.append("var result=player.load('").append(escapeJsString(loadTarget)).append("');");
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

    private String normalizeInputToUrl(String rawInput) {
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

    private String ensureScheme(String value) {
        Uri uri = Uri.parse(value);
        if (uri.getScheme() != null) {
            return value;
        }
        return "https://" + value;
    }

    private void updateUrlInput(String url) {
        if (TextUtils.isEmpty(url) || urlInput.hasFocus()) {
            return;
        }
        urlInput.setText(url);
        urlInput.setSelection(urlInput.getText().length());
    }

    private void toggleRuffleFullscreen() {
        if (customView != null) {
            hideFullscreenContent();
            return;
        }

        String script =
                "(function(){" +
                        "var exit=document.exitFullscreen||document.webkitExitFullscreen;" +
                        "if(document.fullscreenElement){" +
                        "if(exit){exit.call(document);return 'exit';}" +
                        "return 'no-exit-api';" +
                        "}" +
                        "var target=document.querySelector('ruffle-player, ruffle-embed, ruffle-object');" +
                        "if(!target){" +
                        "target=document.querySelector('embed[type*=shockwave], object[type*=shockwave], embed[src*=\".swf\"], object[data*=\".swf\"]');" +
                        "}" +
                        "if(!target){return 'not-found';}" +
                        "var enter=target.requestFullscreen||target.webkitRequestFullscreen;" +
                        "if(!enter){return 'no-enter-api';}" +
                        "enter.call(target);" +
                        "return 'enter';" +
                        "})();";

        wrapper.evaluateJavascript(script, value -> {
            if (value == null) {
                Toast.makeText(this, "无法切换 Ruffle 全屏", Toast.LENGTH_SHORT).show();
                return;
            }

            String normalized = value.replace("\"", "");
            if ("not-found".equals(normalized)) {
                Toast.makeText(this, "当前页面未找到 Ruffle 播放器", Toast.LENGTH_SHORT).show();
            } else if ("no-enter-api".equals(normalized)) {
                Toast.makeText(this, "当前页面不支持全屏接口", Toast.LENGTH_SHORT).show();
            } else if ("no-exit-api".equals(normalized)) {
                Toast.makeText(this, "当前页面无法退出全屏", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleRuffleFullscreenCompat() {
        if (customView != null) {
            hideFullscreenContent();
            return;
        }

        String script =
                "(function(){" +
                        "window.__androidRuffleFullscreen=window.__androidRuffleFullscreen||{};" +
                        "var state=window.__androidRuffleFullscreen;" +
                        "if(state.active&&state.target){" +
                        "if(state.originalStyle===null){state.target.removeAttribute('style');}" +
                        "else{state.target.setAttribute('style',state.originalStyle);}" +
                        "document.documentElement.style.overflow=state.htmlOverflow||'';" +
                        "if(document.body){document.body.style.overflow=state.bodyOverflow||'';}" +
                        "state.active=false;" +
                        "state.target=null;" +
                        "state.originalStyle=null;" +
                        "return 'exit';" +
                        "}" +
                        "var target=document.querySelector('ruffle-player, ruffle-embed, ruffle-object');" +
                        "if(!target){" +
                        "target=document.querySelector('embed[type*=shockwave], object[type*=shockwave], embed[src*=\\\".swf\\\"], object[data*=\\\".swf\\\"]');" +
                        "}" +
                        "if(!target){return 'not-found';}" +
                        "state.target=target;" +
                        "state.originalStyle=target.getAttribute('style');" +
                        "state.htmlOverflow=document.documentElement.style.overflow||'';" +
                        "state.bodyOverflow=document.body?document.body.style.overflow||'':'';" +
                        "document.documentElement.style.overflow='hidden';" +
                        "if(document.body){document.body.style.overflow='hidden';}" +
                        "target.style.position='fixed';" +
                        "target.style.left='0';" +
                        "target.style.top='0';" +
                        "target.style.width='100vw';" +
                        "target.style.height='100vh';" +
                        "target.style.maxWidth='100vw';" +
                        "target.style.maxHeight='100vh';" +
                        "target.style.margin='0';" +
                        "target.style.padding='0';" +
                        "target.style.zIndex='2147483647';" +
                        "target.style.background='#000';" +
                        "state.active=true;" +
                        "return 'enter';" +
                        "})();";

        wrapper.evaluateJavascript(script, value -> {
            if (value == null) {
                Toast.makeText(this, "Unable to toggle Ruffle fullscreen", Toast.LENGTH_SHORT).show();
                return;
            }

            String normalized = value.replace("\"", "");
            if ("enter".equals(normalized)) {
                enterInAppRuffleFullscreenMode();
            } else if ("exit".equals(normalized)) {
                exitInAppRuffleFullscreenMode();
            } else if ("not-found".equals(normalized)) {
                Toast.makeText(this, "Current page has no Ruffle player", Toast.LENGTH_SHORT).show();
            }
        });
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
        if (fullscreenRotateButton != null) {
            fullscreenRotateButton.setVisibility(View.VISIBLE);
        }
        if (fullscreenExitButton != null) {
            fullscreenExitButton.setVisibility(View.VISIBLE);
        }
        if (fullscreenFeaturePanelButton != null) {
            fullscreenFeaturePanelButton.setVisibility(View.VISIBLE);
        }

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
        if (fullscreenRotateButton != null) {
            fullscreenRotateButton.setVisibility(View.GONE);
        }
        if (fullscreenExitButton != null) {
            fullscreenExitButton.setVisibility(View.GONE);
        }
        if (fullscreenFeaturePanelButton != null) {
            fullscreenFeaturePanelButton.setVisibility(View.GONE);
        }

        setSystemBarsHidden(false);
        applySavedOrientation();
        requestInsetRefresh();
    }

    private void resetFullscreenStateForNavigation() {
        if (customView != null) {
            hideFullscreenContent();
        }
        if (inAppRuffleFullscreen) {
            exitInAppRuffleFullscreenMode();
        }
    }

    private void ensureFullscreenStateMatchesPage() {
        if (!inAppRuffleFullscreen || wrapper == null) {
            return;
        }
        String script =
                "(function(){" +
                        "var state=window.__androidRuffleFullscreen||null;" +
                        "var target=document.querySelector('ruffle-player, ruffle-embed, ruffle-object');" +
                        "if(!target){target=document.querySelector('embed[type*=shockwave], object[type*=shockwave], embed[src*=\\\".swf\\\"], object[data*=\\\".swf\\\"]');}" +
                        "return !!(state&&state.active&&state.target&&target);" +
                        "})();";
        wrapper.evaluateJavascript(script, value -> {
            boolean stillFullscreen = "true".equalsIgnoreCase(value == null ? "" : value.replace("\"", ""));
            if (!stillFullscreen && inAppRuffleFullscreen) {
                exitInAppRuffleFullscreenMode();
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        }
    }

    private boolean handleWebViewTouch(MotionEvent event) {
        if (event == null || scaleGestureDetector == null) {
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
                gestureAnchorX = event.getX();
                gestureAnchorY = event.getY();
                wrapper.postDelayed(hoverHoldRunnable, HOVER_HOLD_MS);
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
                    wrapper.postDelayed(menuHoldRunnable, MENU_HOLD_MS);
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
                shouldConsume = shouldConsume || event.getPointerCount() >= 2;
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
                break;
            default:
                shouldConsume = shouldConsume || event.getPointerCount() >= 2;
                break;
        }

        return shouldConsume || event.getPointerCount() >= 2;
    }

    private void cancelPendingTouchGestures() {
        if (wrapper == null) {
            return;
        }
        wrapper.removeCallbacks(hoverHoldRunnable);
        wrapper.removeCallbacks(menuHoldRunnable);
    }

    private boolean isSyntheticMouseEvent(MotionEvent event) {
        if (event == null || event.getPointerCount() <= 0) {
            return false;
        }
        return ((event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE)
                || event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE;
    }

    private boolean dispatchSyntheticMouseHover(float x, float y, boolean entering) {
        if (wrapper == null) {
            return false;
        }
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
            wrapper.dispatchGenericMotionEvent(enterEvent);
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
        wrapper.dispatchGenericMotionEvent(hoverEvent);
        hoverEvent.recycle();
        return true;
    }

    private boolean dispatchSyntheticMouseClick(float x, float y) {
        if (wrapper == null) {
            return false;
        }
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
        wrapper.dispatchTouchEvent(downEvent);
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
            wrapper.dispatchGenericMotionEvent(buttonPressEvent);
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
        wrapper.dispatchTouchEvent(upEvent);
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
            wrapper.dispatchGenericMotionEvent(buttonReleaseEvent);
            buttonReleaseEvent.recycle();
        }
        return true;
    }

    private boolean dispatchSyntheticMouseExit(float x, float y) {
        if (wrapper == null || !syntheticMouseHoverActive) {
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
        wrapper.dispatchGenericMotionEvent(exitEvent);
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

    private boolean shouldDispatchTapClick(MotionEvent event) {
        return flashTouchBridgeAvailable
                && event != null
                && event.getPointerCount() == 1
                && !scaleGestureDetector.isInProgress()
                && !movedBeyondTouchTolerance(event);
    }

    private boolean movedBeyondTouchTolerance(MotionEvent event) {
        if (event == null) {
            return false;
        }
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
        if (wrapper == null || TextUtils.isEmpty(method)) {
            return false;
        }
        String script = "(function(){var bridge=window.__ruffleWrapperTouchBridge;"
                + "return !!(bridge&&bridge." + method + "&&bridge." + method + "(" + x + "," + y + "));})();";
        wrapper.evaluateJavascript(script, value -> { });
        return true;
    }

    private boolean dispatchTouchBridgeSimpleCall(String method) {
        if (wrapper == null || TextUtils.isEmpty(method)) {
            return false;
        }
        String script = "(function(){var bridge=window.__ruffleWrapperTouchBridge;"
                + "return !!(bridge&&bridge." + method + "&&bridge." + method + "());})();";
        wrapper.evaluateJavascript(script, value -> { });
        return true;
    }

    private boolean dispatchTouchBridgeBooleanCall(String method, boolean value) {
        if (wrapper == null || TextUtils.isEmpty(method)) {
            return false;
        }
        String script = "(function(){var bridge=window.__ruffleWrapperTouchBridge;"
                + "return !!(bridge&&bridge." + method + "&&bridge." + method + "(" + value + "));})();";
        wrapper.evaluateJavascript(script, result -> { });
        return true;
    }

    private void refreshFlashTouchBridgeAvailability() {
        if (wrapper == null) {
            flashTouchBridgeAvailable = false;
            return;
        }

        String script =
                "(function(){"
                        + "return !!document.querySelector("
                        + "'ruffle-player,ruffle-embed,ruffle-object,"
                        + "embed[type*=\\\"shockwave\\\"],object[type*=\\\"shockwave\\\"],"
                        + "embed[src*=\\\".swf\\\"],object[data*=\\\".swf\\\"]'"
                        + ");"
                        + "})();";
        wrapper.evaluateJavascript(script, value -> {
            String normalized = value == null ? "" : value.replace("\"", "").trim();
            flashTouchBridgeAvailable = "true".equalsIgnoreCase(normalized);
        });
    }

    private void saveCurrentCookieProfile() {
        if (!cookieProfileManager.canAccessRootDirectory()) {
            Toast.makeText(this, R.string.cookie_storage_permission_needed, Toast.LENGTH_LONG).show();
            requestAllFilesAccessPermission();
            return;
        }

        String currentUrl = wrapper == null ? null : wrapper.getUrl();
        if (TextUtils.isEmpty(currentUrl)) {
            Toast.makeText(this, "No page to save cookie from", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri currentUri = Uri.parse(currentUrl);
        if (!CookieProfileManager.isSupportedSavePage(currentUri)) {
            Toast.makeText(this, "Current page cannot be saved as a cookie profile", Toast.LENGTH_SHORT).show();
            return;
        }

        String cookies = CookieManager.getInstance().getCookie(currentUrl);
        if (TextUtils.isEmpty(cookies)) {
            Toast.makeText(this, "Current page has no cookies", Toast.LENGTH_SHORT).show();
            return;
        }

        File savedFile = cookieProfileManager.saveProfileFromPage(currentUri, cookies, wrapper.getTitle());
        if (savedFile == null) {
            Toast.makeText(this, "Failed to save cookie", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Saved cookie: " + savedFile.getName(), Toast.LENGTH_SHORT).show();
    }

    private void showSettingsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        int fontMode = getSavedFontMode();
        menu.getMenu().add(0, MENU_COOKIE_PROFILES, 0, getString(R.string.cookie_profiles));
        menu.getMenu().add(0, MENU_FONT_MODE_SANS, 1, buildFontMenuTitle(fontMode, FONT_MODE_CHINESE_SANS, "Ruffle 字体: 中文无衬线"));
        menu.getMenu().add(0, MENU_FONT_MODE_SERIF, 2, buildFontMenuTitle(fontMode, FONT_MODE_CHINESE_SERIF, "Ruffle 字体: 中文衬线"));
        menu.getMenu().add(0, MENU_FONT_MODE_EMBEDDED, 3, buildFontMenuTitle(fontMode, FONT_MODE_EMBEDDED, "Ruffle 字体: 关闭设备字体"));
        menu.getMenu().add(0, ORIENTATION_LANDSCAPE, 4, getString(R.string.orientation_landscape));
        menu.getMenu().add(0, ORIENTATION_PORTRAIT, 5, getString(R.string.orientation_portrait));
        menu.getMenu().add(0, ORIENTATION_SYSTEM, 6, getString(R.string.orientation_system));
        menu.getMenu().add(0, MENU_MAPPING_CONFIG, 7, getString(R.string.mapping_config_path));
        menu.getMenu().add(0, MENU_NAV_PVZ_YOUKIA, 8, "导航到 pvz.youkia.com");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_COOKIE_PROFILES) {
                showCookieProfilesDialog();
                return true;
            }
            if (item.getItemId() == MENU_FONT_MODE_SANS) {
                saveFontMode(FONT_MODE_CHINESE_SANS);
                reloadCurrentPageForFontMode();
                return true;
            }
            if (item.getItemId() == MENU_FONT_MODE_SERIF) {
                saveFontMode(FONT_MODE_CHINESE_SERIF);
                reloadCurrentPageForFontMode();
                return true;
            }
            if (item.getItemId() == MENU_FONT_MODE_EMBEDDED) {
                saveFontMode(FONT_MODE_EMBEDDED);
                reloadCurrentPageForFontMode();
                return true;
            }
            if (item.getItemId() == MENU_MAPPING_CONFIG) {
                Toast.makeText(this, localMappingManager.getConfigFile().getAbsolutePath(), Toast.LENGTH_LONG).show();
                return true;
            }
            if (item.getItemId() == MENU_NAV_PVZ_YOUKIA) {
                loadUrl(PVZ_YOUKIA_ENTRY_URL);
                return true;
            }
            saveOrientation(item.getItemId());
            applyOrientation(item.getItemId());
            return true;
        });
        menu.show();
    }

    private void showCookieProfilesDialog() {
        if (!cookieProfileManager.canAccessRootDirectory()) {
            Toast.makeText(this, R.string.cookie_storage_permission_needed, Toast.LENGTH_LONG).show();
            requestAllFilesAccessPermission();
            return;
        }

        if (!cookieProfileManager.ensureInitialized()) {
            Toast.makeText(this, R.string.cookie_copy_failed, Toast.LENGTH_LONG).show();
            return;
        }

        List<CookieProfileManager.CookieProfile> profiles = cookieProfileManager.loadProfiles();
        if (profiles.isEmpty()) {
            Toast.makeText(this, R.string.cookie_no_profiles, Toast.LENGTH_LONG).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cookie_profiles, null);
        LinearLayout container = dialogView.findViewById(R.id.cookie_profile_container);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.cookie_profile_title)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        renderCookieProfiles(container, profiles, dialog);

        File cookieDir = cookieProfileManager.getRootDirectory();
        FileObserver observer = new FileObserver(cookieDir.getAbsolutePath(),
                FileObserver.CREATE
                        | FileObserver.CLOSE_WRITE
                        | FileObserver.MOVED_TO
                        | FileObserver.DELETE
                        | FileObserver.MOVED_FROM) {
            @Override
            public void onEvent(int event, String path) {
                runOnUiThread(() -> {
                    if (!dialog.isShowing()) {
                        return;
                    }
                    renderCookieProfiles(container, cookieProfileManager.loadProfiles(), dialog);
                });
            }
        };
        dialog.setOnShowListener(dialogInterface -> observer.startWatching());
        dialog.setOnDismissListener(dialogInterface -> observer.stopWatching());

        dialog.show();
    }

    private void renderCookieProfiles(
            LinearLayout container,
            List<CookieProfileManager.CookieProfile> profiles,
            AlertDialog dialog
    ) {
        container.removeAllViews();
        if (profiles.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(R.string.cookie_no_profiles);
            emptyView.setTextColor(0xFF374151);
            emptyView.setPadding(8, 8, 8, 8);
            container.addView(emptyView);
            return;
        }

        for (CookieProfileManager.CookieProfile profile : profiles) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_cookie_profile, container, false);
            TextView fileName = itemView.findViewById(R.id.text_file_name);
            TextView userName = itemView.findViewById(R.id.text_user_name);
            fileName.setText(getString(R.string.cookie_file_name, profile.file.getName()));
            userName.setText(getString(R.string.cookie_user_name, profile.userName));
            itemView.findViewById(R.id.btn_apply_cookie).setOnClickListener(v -> {
                dialog.dismiss();
                applyCookieProfile(profile);
            });
            container.addView(itemView);
        }
    }

    private void applyCookieProfile(CookieProfileManager.CookieProfile profile) {
        String targetUrl = CookieProfileManager.buildTargetUrl(profile);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setCookie(profile.userDomain, profile.userCookies);
        cookieManager.setCookie(targetUrl, profile.userCookies);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
        }
        updateUrlInput(targetUrl);
        loadUrl(targetUrl);
        Toast.makeText(this, R.string.cookie_applied, Toast.LENGTH_SHORT).show();
    }

    private void showFeaturePanelDialog() {
        if (featurePanelDialog != null && featurePanelDialog.isShowing()) {
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_feature_panel, null);
        featurePanelTabCookieButton = dialogView.findViewById(R.id.btn_panel_tab_cookie);
        featurePanelTabBasicButton = dialogView.findViewById(R.id.btn_panel_tab_basic);
        featurePanelTabRepositoryButton = dialogView.findViewById(R.id.btn_panel_tab_repository);
        featurePanelTabLogButton = dialogView.findViewById(R.id.btn_panel_tab_log);
        featurePanelCookiePage = dialogView.findViewById(R.id.panel_page_cookie);
        featurePanelBasicPage = dialogView.findViewById(R.id.panel_page_basic);
        featurePanelRepositoryPage = dialogView.findViewById(R.id.panel_page_repository);
        featurePanelLogPage = dialogView.findViewById(R.id.panel_page_log);
        featurePanelCookieContainer = dialogView.findViewById(R.id.panel_cookie_container);
        featurePanelCookieHintText = dialogView.findViewById(R.id.text_cookie_selection_hint);
        featurePanelSelectAllCookiesButton = dialogView.findViewById(R.id.btn_panel_select_all_cookies);
        featurePanelConcurrencyInput = dialogView.findViewById(R.id.input_panel_concurrency);
        featurePanelRequestIntervalInput = dialogView.findViewById(R.id.input_panel_request_interval);
        featurePanelFrequentRetryIntervalInput = dialogView.findViewById(R.id.input_panel_frequent_retry_interval);
        featurePanelPauseResumeButton = dialogView.findViewById(R.id.btn_panel_pause_resume);
        featurePanelCancelButton = dialogView.findViewById(R.id.btn_panel_cancel);
        featurePanelDailyDutyCheckBox = dialogView.findViewById(R.id.check_panel_daily_duty);
        featurePanelDailyDutyRunButton = dialogView.findViewById(R.id.btn_panel_daily_duty_run);
        featurePanelStartSelectedButton = dialogView.findViewById(R.id.btn_panel_start_selected);
        featurePanelStorageAccessButton = dialogView.findViewById(R.id.btn_panel_request_storage_access);
        featurePanelRepositoryHintText = dialogView.findViewById(R.id.text_panel_repository_hint);
        featurePanelRepositorySelectedTargetsText = dialogView.findViewById(R.id.text_panel_repository_selected_targets);
        featurePanelRepositoryPickTargetsButton = dialogView.findViewById(R.id.btn_panel_repository_pick_targets);
        featurePanelRepositoryViewCookieText = dialogView.findViewById(R.id.text_panel_repository_view_cookie);
        featurePanelRepositoryPickViewCookieButton = dialogView.findViewById(R.id.btn_panel_repository_pick_view_cookie);
        featurePanelRepositoryRefreshButton = dialogView.findViewById(R.id.btn_panel_repository_refresh);
        featurePanelRepositoryRecordButton = dialogView.findViewById(R.id.btn_panel_repository_record);
        featurePanelRepositoryCompareButton = dialogView.findViewById(R.id.btn_panel_repository_compare);
        featurePanelRepositoryIgnoreSelectedButton = dialogView.findViewById(R.id.btn_panel_repository_ignore_selected);
        featurePanelRepositoryRemoveIgnoredButton = dialogView.findViewById(R.id.btn_panel_repository_remove_ignored);
        featurePanelRepositoryCurrentContainer = dialogView.findViewById(R.id.panel_repository_current_container);
        featurePanelRepositoryDeltaContainer = dialogView.findViewById(R.id.panel_repository_delta_container);
        featurePanelRepositoryIgnoredContainer = dialogView.findViewById(R.id.panel_repository_ignored_container);
        featurePanelQueueStatusText = dialogView.findViewById(R.id.text_panel_queue_status);
        featurePanelQueueLogText = dialogView.findViewById(R.id.text_panel_queue_log);
        featurePanelUiController.bind(
                featurePanelTabCookieButton,
                featurePanelTabBasicButton,
                featurePanelTabRepositoryButton,
                featurePanelTabLogButton,
                featurePanelCookiePage,
                featurePanelBasicPage,
                featurePanelRepositoryPage,
                featurePanelLogPage,
                featurePanelQueueStatusText,
                featurePanelQueueLogText,
                featurePanelPauseResumeButton,
                featurePanelCancelButton,
                featurePanelDailyDutyRunButton,
                featurePanelStartSelectedButton
        );

        featurePanelConcurrencyInput.setText(String.valueOf(getSavedPanelConcurrency()));
        featurePanelRequestIntervalInput.setText(String.valueOf(getSavedPanelRequestInterval()));
        featurePanelFrequentRetryIntervalInput.setText(String.valueOf(getSavedPanelFrequentRetryInterval()));
        featurePanelDailyDutyCheckBox.setChecked(preferenceStore.isPanelDailyDutyEnabled());
        featurePanelDailyDutyCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferenceStore.setPanelDailyDutyEnabled(isChecked));
        featurePanelTabCookieButton.setOnClickListener(v -> switchFeaturePanelTab(FEATURE_PANEL_TAB_COOKIE));
        featurePanelTabBasicButton.setOnClickListener(v -> switchFeaturePanelTab(FEATURE_PANEL_TAB_BASIC));
        featurePanelTabRepositoryButton.setOnClickListener(v -> switchFeaturePanelTab(FEATURE_PANEL_TAB_REPOSITORY));
        featurePanelTabLogButton.setOnClickListener(v -> switchFeaturePanelTab(FEATURE_PANEL_TAB_LOG));
        featurePanelSelectAllCookiesButton.setOnClickListener(v -> selectAllFeaturePanelCookies());
        featurePanelRepositoryPickTargetsButton.setOnClickListener(v -> showRepositoryTargetPickerDialogV2());
        featurePanelRepositoryPickViewCookieButton.setOnClickListener(v -> showRepositoryViewPickerDialogV2());
        featurePanelRepositoryRefreshButton.setOnClickListener(v -> refreshRepositoryForSelectedTargets());
        featurePanelRepositoryRecordButton.setOnClickListener(v -> recordRepositoryForSelectedTargets());
        featurePanelRepositoryCompareButton.setOnClickListener(v -> compareRepositoryForSelectedTargets());
        featurePanelRepositoryIgnoreSelectedButton.setOnClickListener(v -> addSelectedRepositoryDeltasToIgnored());
        featurePanelRepositoryRemoveIgnoredButton.setOnClickListener(v -> removeSelectedRepositoryIgnoredItems());
        featurePanelPauseResumeButton.setOnClickListener(v -> {
            DutyRequestQueue.StateSnapshot snapshot = dutyRequestQueue.snapshot();
            if (snapshot.running && !snapshot.paused) {
                dutyRequestQueue.pause();
            } else if (snapshot.running) {
                dutyRequestQueue.resume();
            }
        });
        featurePanelCancelButton.setOnClickListener(v -> dutyRequestQueue.cancel());
        featurePanelDailyDutyRunButton.setOnClickListener(v -> startDailyDutyRequestsFromPanel(false));
        featurePanelStartSelectedButton.setOnClickListener(v -> startDailyDutyRequestsFromPanel(true));
        featurePanelStorageAccessButton.setOnClickListener(v -> requestAllFilesAccessPermission());

        featurePanelDialog = new AlertDialog.Builder(this)
                .setTitle("功能面板")
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        featurePanelDialog.setOnDismissListener(dialog -> clearFeaturePanelReferences());
        featurePanelDialog.show();
        if (featurePanelDialog.getWindow() != null) {
            int width = Math.min((int) (getResources().getDisplayMetrics().widthPixels * 0.94f), dpToPx(920));
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
            featurePanelDialog.getWindow().setLayout(width, height);
        }

        refreshFeaturePanelCookieChoices();
        refreshRepositorySelectionState();
        renderRepositoryPanelV2();
        switchFeaturePanelTab(FEATURE_PANEL_TAB_COOKIE);
        renderFeaturePanelQueueState(dutyRequestQueue.snapshot());
    }

    private void toggleFeaturePanelDialog() {
        if (featurePanelDialog != null && featurePanelDialog.isShowing()) {
            featurePanelDialog.dismiss();
            return;
        }
        showFeaturePanelDialog();
    }

    private void clearFeaturePanelReferences() {
        featurePanelDialog = null;
        featurePanelTabCookieButton = null;
        featurePanelTabBasicButton = null;
        featurePanelTabRepositoryButton = null;
        featurePanelTabLogButton = null;
        featurePanelCookiePage = null;
        featurePanelBasicPage = null;
        featurePanelRepositoryPage = null;
        featurePanelLogPage = null;
        featurePanelCookieContainer = null;
        featurePanelCookieHintText = null;
        featurePanelSelectAllCookiesButton = null;
        featurePanelConcurrencyInput = null;
        featurePanelRequestIntervalInput = null;
        featurePanelFrequentRetryIntervalInput = null;
        featurePanelPauseResumeButton = null;
        featurePanelCancelButton = null;
        featurePanelDailyDutyCheckBox = null;
        featurePanelDailyDutyRunButton = null;
        featurePanelStartSelectedButton = null;
        featurePanelStorageAccessButton = null;
        featurePanelRepositoryHintText = null;
        featurePanelRepositorySelectedTargetsText = null;
        featurePanelRepositoryPickTargetsButton = null;
        featurePanelRepositoryViewCookieText = null;
        featurePanelRepositoryPickViewCookieButton = null;
        featurePanelRepositoryRefreshButton = null;
        featurePanelRepositoryRecordButton = null;
        featurePanelRepositoryCompareButton = null;
        featurePanelRepositoryIgnoreSelectedButton = null;
        featurePanelRepositoryRemoveIgnoredButton = null;
        featurePanelRepositoryCurrentContainer = null;
        featurePanelRepositoryDeltaContainer = null;
        featurePanelRepositoryIgnoredContainer = null;
        featurePanelQueueStatusText = null;
        featurePanelQueueLogText = null;
        featurePanelUiController.clear();
        featureCookieChoices.clear();
    }

    private void refreshFeaturePanelCookieChoices() {
        if (featurePanelCookieContainer == null) {
            return;
        }

        featureCookieChoices.clear();
        FeatureCookieChoice currentPageChoice = buildCurrentPageFeatureCookieChoice();
        if (currentPageChoice != null) {
            featureCookieChoices.add(currentPageChoice);
        }

        boolean hasStorageAccess = cookieProfileManager.canAccessRootDirectory() && cookieProfileManager.ensureInitialized();
        if (hasStorageAccess) {
            List<CookieProfileManager.CookieProfile> profiles = cookieProfileManager.loadProfiles();
            for (CookieProfileManager.CookieProfile profile : profiles) {
                FeatureCookieChoice choice = new FeatureCookieChoice();
                choice.label = profile.userName + " (" + profile.file.getName() + ")";
                choice.pageUrl = CookieProfileManager.buildTargetUrl(profile);
                choice.subtitle = choice.pageUrl;
                choice.baseUrl = CookieProfileManager.buildRootUrl(profile);
                choice.cookies = profile.userCookies;
                choice.selectionKey = profile.file.getAbsolutePath();
                choice.selected = isPersistedCookieChoiceSelected(choice.selectionKey);
                choice.currentPage = false;
                featureCookieChoices.add(choice);
            }
        }

        featurePanelCookieContainer.removeAllViews();
        if (featureCookieChoices.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("当前没有可选 Cookie。");
            emptyView.setTextColor(0xFF4B5563);
            featurePanelCookieContainer.addView(emptyView);
        } else {
            LayoutInflater inflater = LayoutInflater.from(this);
            for (FeatureCookieChoice choice : featureCookieChoices) {
                View itemView = inflater.inflate(R.layout.item_panel_cookie_option, featurePanelCookieContainer, false);
                CheckBox checkBox = itemView.findViewById(R.id.check_cookie_option);
                TextView subtitle = itemView.findViewById(R.id.text_cookie_option_subtitle);
                checkBox.setText(choice.currentPage ? "当前页面 Cookie" : choice.label);
                checkBox.setChecked(choice.selected);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    choice.selected = isChecked;
                    persistFeatureCookieChoiceSelection(choice, isChecked);
                });

                StringBuilder subtitleBuilder = new StringBuilder();
                if (!choice.currentPage) {
                    subtitleBuilder.append(choice.label).append('\n');
                }
                subtitleBuilder.append(choice.subtitle == null ? "" : choice.subtitle);
                if (!TextUtils.isEmpty(choice.baseUrl)) {
                    subtitleBuilder.append("\nAMF: ").append(choice.baseUrl).append("/pvz/amf/");
                }
                if (!CookieProfileManager.isDutyRewardEligibleBaseUrl(choice.pageUrl)) {
                    subtitleBuilder.append("\n该项不会用于每日任务（URL 包含 pvzol.org）");
                }
                subtitle.setText(subtitleBuilder.toString().trim());
                featurePanelCookieContainer.addView(itemView);
            }
        }

        if (featurePanelStorageAccessButton != null) {
            featurePanelStorageAccessButton.setVisibility(hasStorageAccess ? View.GONE : View.VISIBLE);
        }
        if (featurePanelCookieHintText != null && !hasStorageAccess) {
            featurePanelCookieHintText.setText("将优先显示当前页面 Cookie。未授权时只能使用当前页面 Cookie。每日任务会自动忽略地址包含 pvzol.org 的选项。");
        }
    }

    private void onFeaturePanelCookieChoicesChanged() {
        refreshRepositorySelectionState();
        renderRepositoryPanelV2();
    }

    private void selectAllFeaturePanelCookies() {
        if (featureCookieChoices.isEmpty()) {
            return;
        }
        for (FeatureCookieChoice choice : featureCookieChoices) {
            choice.selected = true;
            persistFeatureCookieChoiceSelection(choice, true);
        }
        refreshFeaturePanelCookieChoices();
    }

    private void refreshRepositorySelectionState() {
        List<FeatureCookieChoice> availableChoices = getAvailableRepositoryChoices();
        java.util.LinkedHashSet<String> availableKeys = new java.util.LinkedHashSet<>();
        for (FeatureCookieChoice choice : availableChoices) {
            availableKeys.add(getRepositoryChoiceKey(choice));
        }
        sessionRepositoryRecordChoiceKeys.retainAll(availableKeys);
        if (!sessionRepositoryRecordSelectionInitialized) {
            sessionRepositoryRecordSelectionInitialized = true;
            for (FeatureCookieChoice choice : availableChoices) {
                if (choice.currentPage) {
                    sessionRepositoryRecordChoiceKeys.add(getRepositoryChoiceKey(choice));
                    break;
                }
            }
            if (sessionRepositoryRecordChoiceKeys.isEmpty() && !availableChoices.isEmpty()) {
                sessionRepositoryRecordChoiceKeys.add(getRepositoryChoiceKey(availableChoices.get(0)));
            }
        }

        if (TextUtils.isEmpty(sessionRepositoryViewChoiceKey)
                || !sessionRepositoryRecordChoiceKeys.contains(sessionRepositoryViewChoiceKey)) {
            sessionRepositoryViewChoiceKey = sessionRepositoryRecordChoiceKeys.isEmpty()
                    ? null
                    : sessionRepositoryRecordChoiceKeys.iterator().next();
        }
    }

    private List<FeatureCookieChoice> getAvailableRepositoryChoices() {
        return new ArrayList<>(featureCookieChoices);
    }

    private String getRepositoryChoiceKey(FeatureCookieChoice choice) {
        if (choice == null) {
            return "";
        }
        if (!TextUtils.isEmpty(choice.selectionKey)) {
            return "profile:" + choice.selectionKey;
        }
        return "current:" + choice.baseUrl + ":" + Integer.toHexString(String.valueOf(choice.cookies).hashCode());
    }

    private FeatureCookieChoice findRepositoryChoiceByKey(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        for (FeatureCookieChoice choice : featureCookieChoices) {
            if (key.equals(getRepositoryChoiceKey(choice))) {
                return choice;
            }
        }
        return null;
    }

    private List<FeatureCookieChoice> getSelectedRepositoryChoices() {
        ArrayList<FeatureCookieChoice> result = new ArrayList<>();
        for (FeatureCookieChoice choice : featureCookieChoices) {
            if (sessionRepositoryRecordChoiceKeys.contains(getRepositoryChoiceKey(choice))) {
                result.add(choice);
            }
        }
        return result;
    }

    private FeatureCookieChoice getViewedRepositoryChoice() {
        return findRepositoryChoiceByKey(sessionRepositoryViewChoiceKey);
    }

    private void showRepositoryTargetPickerDialog() {
        List<FeatureCookieChoice> availableChoices = getAvailableRepositoryChoices();
        if (availableChoices.isEmpty()) {
            Toast.makeText(this, "No cookie available.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[availableChoices.size()];
        boolean[] checked = new boolean[availableChoices.size()];
        for (int i = 0; i < availableChoices.size(); i += 1) {
            FeatureCookieChoice choice = availableChoices.get(i);
            labels[i] = choice.currentPage ? "当前页面 Cookie" : choice.label;
            checked[i] = sessionRepositoryRecordChoiceKeys.contains(getRepositoryChoiceKey(choice));
        }

        new AlertDialog.Builder(this)
                .setTitle("选择记录 Cookie")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    String key = getRepositoryChoiceKey(availableChoices.get(which));
                    if (isChecked) {
                        sessionRepositoryRecordChoiceKeys.add(key);
                    } else {
                        sessionRepositoryRecordChoiceKeys.remove(key);
                    }
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    refreshRepositorySelectionState();
                    renderRepositoryPanel();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRepositoryViewPickerDialog() {
        List<FeatureCookieChoice> selectedChoices = getSelectedRepositoryChoices();
        if (selectedChoices.isEmpty()) {
            Toast.makeText(this, "Please select record cookies first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[selectedChoices.size()];
        int checkedIndex = 0;
        for (int i = 0; i < selectedChoices.size(); i += 1) {
            FeatureCookieChoice choice = selectedChoices.get(i);
            labels[i] = choice.currentPage ? "当前页面 Cookie" : choice.label;
            if (TextUtils.equals(sessionRepositoryViewChoiceKey, getRepositoryChoiceKey(choice))) {
                checkedIndex = i;
            }
        }

        final int[] selectedIndex = new int[] {checkedIndex};
        new AlertDialog.Builder(this)
                .setTitle("选择查看 Cookie")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    sessionRepositoryViewChoiceKey = getRepositoryChoiceKey(selectedChoices.get(selectedIndex[0]));
                    renderRepositoryPanel();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void renderRepositoryPanel() {
        if (featurePanelRepositorySelectedTargetsText == null
                || featurePanelRepositoryViewCookieText == null
                || featurePanelRepositoryCurrentContainer == null
                || featurePanelRepositoryDeltaContainer == null
                || featurePanelRepositoryIgnoredContainer == null) {
            return;
        }

        refreshRepositorySelectionState();
        List<FeatureCookieChoice> selectedChoices = getSelectedRepositoryChoices();
        if (selectedChoices.isEmpty()) {
            featurePanelRepositorySelectedTargetsText.setText("当前未选择记录目标");
            featurePanelRepositoryViewCookieText.setText("当前未选择查看目标");
            renderRepositoryMessage(featurePanelRepositoryCurrentContainer, "请选择记录 Cookie。");
            renderRepositoryMessage(featurePanelRepositoryDeltaContainer, "暂无对比结果。");
            renderRepositoryMessage(featurePanelRepositoryIgnoredContainer, "暂无屏蔽物品。");
            updateRepositoryButtonsEnabled(false);
            return;
        }

        ArrayList<String> labels = new ArrayList<>();
        for (FeatureCookieChoice choice : selectedChoices) {
            labels.add(choice.currentPage ? "当前页面 Cookie" : choice.label);
        }
        featurePanelRepositorySelectedTargetsText.setText("记录目标：" + TextUtils.join("，", labels));

        FeatureCookieChoice viewedChoice = getViewedRepositoryChoice();
        if (viewedChoice == null) {
            viewedChoice = selectedChoices.get(0);
            sessionRepositoryViewChoiceKey = getRepositoryChoiceKey(viewedChoice);
        }
        featurePanelRepositoryViewCookieText.setText("当前查看：" + (viewedChoice.currentPage ? "当前页面 Cookie" : viewedChoice.label));
        updateRepositoryButtonsEnabled(true);
        renderRepositoryChoiceData(viewedChoice);
    }

    private void updateRepositoryButtonsEnabled(boolean enabled) {
        if (featurePanelRepositoryPickViewCookieButton != null) {
            featurePanelRepositoryPickViewCookieButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryRefreshButton != null) {
            featurePanelRepositoryRefreshButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryRecordButton != null) {
            featurePanelRepositoryRecordButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryCompareButton != null) {
            featurePanelRepositoryCompareButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryIgnoreSelectedButton != null) {
            featurePanelRepositoryIgnoreSelectedButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryRemoveIgnoredButton != null) {
            featurePanelRepositoryRemoveIgnoredButton.setEnabled(enabled);
        }
    }

    private void renderRepositoryChoiceData(FeatureCookieChoice choice) {
        String key = getRepositoryChoiceKey(choice);
        WarehouseRecordManager.RepositorySnapshot currentSnapshot = featureRepositoryCurrentSnapshots.get(key);
        if (currentSnapshot == null) {
            renderRepositoryMessage(featurePanelRepositoryCurrentContainer, "尚未刷新当前仓库。");
        } else {
            renderRepositoryCurrentSnapshot(currentSnapshot);
        }

        WarehouseRecordManager.RepositorySnapshot recordedSnapshot = loadRepositoryRecordedSnapshot(key);
        java.util.LinkedHashSet<Integer> ignoredIds = loadRepositoryIgnoredIds(key);
        if (recordedSnapshot == null || currentSnapshot == null) {
            renderRepositoryMessage(featurePanelRepositoryDeltaContainer, "请先刷新仓库并设置记录点。");
        } else {
            renderRepositoryDeltas(key, warehouseRecordManager.compare(currentSnapshot, recordedSnapshot, ignoredIds));
        }
        renderRepositoryIgnoredList(key, ignoredIds);
    }

    private void renderRepositoryCurrentSnapshot(WarehouseRecordManager.RepositorySnapshot snapshot) {
        featurePanelRepositoryCurrentContainer.removeAllViews();
        if (snapshot == null || snapshot.toolEntries.isEmpty()) {
            renderRepositoryMessage(featurePanelRepositoryCurrentContainer, "当前仓库为空。");
            return;
        }
        TextView textView = new TextView(this);
        StringBuilder builder = new StringBuilder();
        for (WarehouseRecordManager.ToolEntry entry : snapshot.toolEntries) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry.displayName).append(" x ").append(entry.amount);
        }
        textView.setText(builder.toString());
        textView.setTextColor(0xFF1F2937);
        textView.setBackgroundColor(0xFFFFFFFF);
        textView.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        featurePanelRepositoryCurrentContainer.addView(textView);
    }

    private void renderRepositoryDeltas(String key, List<WarehouseRecordManager.RepositoryDelta> deltas) {
        featurePanelRepositoryDeltaContainer.removeAllViews();
        java.util.LinkedHashSet<Integer> selectedIds = featureRepositorySelectedDeltaIds.get(key);
        if (selectedIds == null) {
            selectedIds = new java.util.LinkedHashSet<>();
            featureRepositorySelectedDeltaIds.put(key, selectedIds);
        }
        if (deltas == null || deltas.isEmpty()) {
            renderRepositoryMessage(featurePanelRepositoryDeltaContainer, "暂无变化。");
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (WarehouseRecordManager.RepositoryDelta delta : deltas) {
            View itemView = inflater.inflate(R.layout.item_panel_cookie_option, featurePanelRepositoryDeltaContainer, false);
            CheckBox checkBox = itemView.findViewById(R.id.check_cookie_option);
            TextView subtitle = itemView.findViewById(R.id.text_cookie_option_subtitle);
            checkBox.setText(delta.displayName);
            checkBox.setChecked(selectedIds.contains(Integer.valueOf(delta.toolId)));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                java.util.LinkedHashSet<Integer> ids = featureRepositorySelectedDeltaIds.get(key);
                if (ids == null) {
                    ids = new java.util.LinkedHashSet<>();
                    featureRepositorySelectedDeltaIds.put(key, ids);
                }
                if (isChecked) {
                    ids.add(Integer.valueOf(delta.toolId));
                } else {
                    ids.remove(Integer.valueOf(delta.toolId));
                }
            });
            subtitle.setText("变化：" + delta.deltaAmount + "，当前数量：" + delta.currentAmount);
            featurePanelRepositoryDeltaContainer.addView(itemView);
        }
    }

    private void renderRepositoryIgnoredList(String key, java.util.LinkedHashSet<Integer> ignoredIds) {
        featurePanelRepositoryIgnoredContainer.removeAllViews();
        java.util.LinkedHashSet<Integer> selectedIds = featureRepositorySelectedIgnoredIds.get(key);
        if (selectedIds == null) {
            selectedIds = new java.util.LinkedHashSet<>();
            featureRepositorySelectedIgnoredIds.put(key, selectedIds);
        }
        if (ignoredIds == null || ignoredIds.isEmpty()) {
            renderRepositoryMessage(featurePanelRepositoryIgnoredContainer, "暂无屏蔽物品。");
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Integer toolId : ignoredIds) {
            View itemView = inflater.inflate(R.layout.item_panel_cookie_option, featurePanelRepositoryIgnoredContainer, false);
            CheckBox checkBox = itemView.findViewById(R.id.check_cookie_option);
            TextView subtitle = itemView.findViewById(R.id.text_cookie_option_subtitle);
            checkBox.setText(warehouseRecordManager.nameOf(toolId.intValue()));
            checkBox.setChecked(selectedIds.contains(toolId));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                java.util.LinkedHashSet<Integer> ids = featureRepositorySelectedIgnoredIds.get(key);
                if (ids == null) {
                    ids = new java.util.LinkedHashSet<>();
                    featureRepositorySelectedIgnoredIds.put(key, ids);
                }
                if (isChecked) {
                    ids.add(toolId);
                } else {
                    ids.remove(toolId);
                }
            });
            subtitle.setText("Tool ID: " + toolId);
            featurePanelRepositoryIgnoredContainer.addView(itemView);
        }
    }

    private void renderRepositoryMessage(LinearLayout container, String message) {
        container.removeAllViews();
        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setTextColor(0xFF4B5563);
        textView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        container.addView(textView);
    }

    private void showRepositoryTargetPickerDialogV2() {
        List<FeatureCookieChoice> availableChoices = getAvailableRepositoryChoices();
        if (availableChoices.isEmpty()) {
            Toast.makeText(this, "当前没有可用的 Cookie。", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[availableChoices.size()];
        boolean[] checked = new boolean[availableChoices.size()];
        for (int i = 0; i < availableChoices.size(); i += 1) {
            FeatureCookieChoice choice = availableChoices.get(i);
            labels[i] = getRepositoryChoiceLabelV2(choice);
            checked[i] = sessionRepositoryRecordChoiceKeys.contains(getRepositoryChoiceKey(choice));
        }

        new AlertDialog.Builder(this)
                .setTitle("选择记录 Cookie")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    String key = getRepositoryChoiceKey(availableChoices.get(which));
                    if (isChecked) {
                        sessionRepositoryRecordChoiceKeys.add(key);
                    } else {
                        sessionRepositoryRecordChoiceKeys.remove(key);
                    }
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    refreshRepositorySelectionState();
                    featureRepositoryCurrentExpanded = false;
                    renderRepositoryPanelV2();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRepositoryViewPickerDialogV2() {
        List<FeatureCookieChoice> selectedChoices = getSelectedRepositoryChoices();
        if (selectedChoices.isEmpty()) {
            Toast.makeText(this, "请先选择记录 Cookie。", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[selectedChoices.size()];
        int checkedIndex = 0;
        for (int i = 0; i < selectedChoices.size(); i += 1) {
            FeatureCookieChoice choice = selectedChoices.get(i);
            labels[i] = getRepositoryChoiceLabelV2(choice);
            if (TextUtils.equals(sessionRepositoryViewChoiceKey, getRepositoryChoiceKey(choice))) {
                checkedIndex = i;
            }
        }

        final int[] selectedIndex = new int[] {checkedIndex};
        new AlertDialog.Builder(this)
                .setTitle("选择查看 Cookie")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    sessionRepositoryViewChoiceKey = getRepositoryChoiceKey(selectedChoices.get(selectedIndex[0]));
                    featureRepositoryCurrentExpanded = false;
                    renderRepositoryPanelV2();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String getRepositoryChoiceLabelV2(FeatureCookieChoice choice) {
        if (choice == null) {
            return "";
        }
        return choice.currentPage ? "当前页面 Cookie" : choice.label;
    }

    private void renderRepositoryPanelV2() {
        if (featurePanelRepositorySelectedTargetsText == null
                || featurePanelRepositoryViewCookieText == null
                || featurePanelRepositoryCurrentContainer == null
                || featurePanelRepositoryDeltaContainer == null
                || featurePanelRepositoryIgnoredContainer == null) {
            return;
        }

        refreshRepositorySelectionState();
        List<FeatureCookieChoice> selectedChoices = getSelectedRepositoryChoices();
        if (selectedChoices.isEmpty()) {
            featureRepositoryCurrentExpanded = false;
            featurePanelRepositorySelectedTargetsText.setText("当前未选择记录目标");
            featurePanelRepositoryViewCookieText.setText("当前未选择查看目标");
            if (featurePanelRepositoryHintText != null) {
                featurePanelRepositoryHintText.setText("仓库记录按 Cookie 分开保存。请先选择记录 Cookie。");
            }
            renderRepositoryMessage(featurePanelRepositoryCurrentContainer, "当前未选择记录目标");
            renderRepositoryMessage(featurePanelRepositoryDeltaContainer, "当前未选择记录目标");
            renderRepositoryMessage(featurePanelRepositoryIgnoredContainer, "当前未选择记录目标");
            updateRepositoryButtonsEnabledV2(false);
            return;
        }

        ArrayList<String> labels = new ArrayList<>();
        for (FeatureCookieChoice choice : selectedChoices) {
            labels.add(getRepositoryChoiceLabelV2(choice));
        }
        featurePanelRepositorySelectedTargetsText.setText("记录目标：" + TextUtils.join("，", labels));

        FeatureCookieChoice viewedChoice = getViewedRepositoryChoice();
        if (viewedChoice == null) {
            viewedChoice = selectedChoices.get(0);
            sessionRepositoryViewChoiceKey = getRepositoryChoiceKey(viewedChoice);
        }
        featurePanelRepositoryViewCookieText.setText("当前查看：" + getRepositoryChoiceLabelV2(viewedChoice));
        if (featurePanelRepositoryHintText != null) {
            featurePanelRepositoryHintText.setText("记录、对比和屏蔽都按各自 Cookie 分开保存。");
        }
        updateRepositoryButtonsEnabledV2(true);
        renderRepositoryChoiceDataV2(viewedChoice);
    }

    private void updateRepositoryButtonsEnabledV2(boolean enabled) {
        if (featurePanelRepositoryPickViewCookieButton != null) {
            featurePanelRepositoryPickViewCookieButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryRefreshButton != null) {
            featurePanelRepositoryRefreshButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryRecordButton != null) {
            featurePanelRepositoryRecordButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryCompareButton != null) {
            featurePanelRepositoryCompareButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryIgnoreSelectedButton != null) {
            featurePanelRepositoryIgnoreSelectedButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryRemoveIgnoredButton != null) {
            featurePanelRepositoryRemoveIgnoredButton.setEnabled(enabled);
        }
        setRepositoryContentEnabledV2(featurePanelRepositoryCurrentContainer, enabled);
        setRepositoryContentEnabledV2(featurePanelRepositoryDeltaContainer, enabled);
        setRepositoryContentEnabledV2(featurePanelRepositoryIgnoredContainer, enabled);
    }

    private void setRepositoryContentEnabledV2(View view, boolean enabled) {
        if (view == null) {
            return;
        }
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1.0f : 0.42f);
    }

    private void renderRepositoryChoiceDataV2(FeatureCookieChoice choice) {
        String key = getRepositoryChoiceKey(choice);
        WarehouseRecordManager.RepositorySnapshot currentSnapshot = featureRepositoryCurrentSnapshots.get(key);
        if (currentSnapshot == null) {
            renderRepositoryMessage(featurePanelRepositoryCurrentContainer, "尚未刷新当前仓库。");
        } else {
            renderRepositoryCurrentSnapshotV2(currentSnapshot);
        }

        WarehouseRecordManager.RepositorySnapshot recordedSnapshot = loadRepositoryRecordedSnapshot(key);
        java.util.LinkedHashSet<Integer> ignoredIds = loadRepositoryIgnoredIds(key);
        if (recordedSnapshot == null || currentSnapshot == null) {
            renderRepositoryMessage(featurePanelRepositoryDeltaContainer, "请先刷新仓库并至少记录一次。");
        } else {
            renderRepositoryDeltasV2(key, warehouseRecordManager.compare(currentSnapshot, recordedSnapshot, ignoredIds));
        }
        renderRepositoryIgnoredListV2(key, ignoredIds);
    }

    private void renderRepositoryCurrentSnapshotV2(WarehouseRecordManager.RepositorySnapshot snapshot) {
        featurePanelRepositoryCurrentContainer.removeAllViews();
        if (snapshot == null || snapshot.toolEntries.isEmpty()) {
            renderRepositoryMessage(featurePanelRepositoryCurrentContainer, "当前仓库为空。");
            return;
        }

        final int collapsedLineCount = 8;
        String fullText = buildRepositorySnapshotTextV2(snapshot);
        String[] lines = fullText.split("\n");
        boolean canCollapse = lines.length > collapsedLineCount;
        String shownText = fullText;
        if (!featureRepositoryCurrentExpanded && canCollapse) {
            shownText = TextUtils.join("\n", java.util.Arrays.asList(lines).subList(0, collapsedLineCount));
        }

        TextView textView = new TextView(this);
        textView.setText(shownText);
        textView.setTextColor(0xFF1F2937);
        textView.setBackgroundColor(0xFFFFFFFF);
        textView.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        featurePanelRepositoryCurrentContainer.addView(textView);

        if (canCollapse) {
            Button toggleButton = new Button(this);
            toggleButton.setAllCaps(false);
            toggleButton.setText(featureRepositoryCurrentExpanded ? "收起" : "展开全部");
            toggleButton.setOnClickListener(v -> {
                featureRepositoryCurrentExpanded = !featureRepositoryCurrentExpanded;
                renderRepositoryCurrentSnapshotV2(snapshot);
            });
            featurePanelRepositoryCurrentContainer.addView(toggleButton);
        }
    }

    private String buildRepositorySnapshotTextV2(WarehouseRecordManager.RepositorySnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        for (WarehouseRecordManager.ToolEntry entry : snapshot.toolEntries) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry.displayName).append(" x ").append(entry.amount);
        }
        return builder.toString();
    }

    private void renderRepositoryDeltasV2(String key, List<WarehouseRecordManager.RepositoryDelta> deltas) {
        featurePanelRepositoryDeltaContainer.removeAllViews();
        java.util.LinkedHashSet<Integer> selectedIds = featureRepositorySelectedDeltaIds.get(key);
        if (selectedIds == null) {
            selectedIds = new java.util.LinkedHashSet<>();
            featureRepositorySelectedDeltaIds.put(key, selectedIds);
        }
        if (deltas == null || deltas.isEmpty()) {
            renderRepositoryMessage(featurePanelRepositoryDeltaContainer, "暂无变化。");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (WarehouseRecordManager.RepositoryDelta delta : deltas) {
            View itemView = inflater.inflate(R.layout.item_panel_cookie_option, featurePanelRepositoryDeltaContainer, false);
            CheckBox checkBox = itemView.findViewById(R.id.check_cookie_option);
            TextView subtitle = itemView.findViewById(R.id.text_cookie_option_subtitle);
            checkBox.setText(delta.displayName);
            checkBox.setChecked(selectedIds.contains(Integer.valueOf(delta.toolId)));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                java.util.LinkedHashSet<Integer> ids = featureRepositorySelectedDeltaIds.get(key);
                if (ids == null) {
                    ids = new java.util.LinkedHashSet<>();
                    featureRepositorySelectedDeltaIds.put(key, ids);
                }
                if (isChecked) {
                    ids.add(Integer.valueOf(delta.toolId));
                } else {
                    ids.remove(Integer.valueOf(delta.toolId));
                }
            });
            subtitle.setText("变化：" + delta.deltaAmount + "，当前数量：" + delta.currentAmount);
            featurePanelRepositoryDeltaContainer.addView(itemView);
        }
    }

    private void renderRepositoryIgnoredListV2(String key, java.util.LinkedHashSet<Integer> ignoredIds) {
        featurePanelRepositoryIgnoredContainer.removeAllViews();
        java.util.LinkedHashSet<Integer> selectedIds = featureRepositorySelectedIgnoredIds.get(key);
        if (selectedIds == null) {
            selectedIds = new java.util.LinkedHashSet<>();
            featureRepositorySelectedIgnoredIds.put(key, selectedIds);
        }
        if (ignoredIds == null || ignoredIds.isEmpty()) {
            renderRepositoryMessage(featurePanelRepositoryIgnoredContainer, "暂无屏蔽物品。");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Integer toolId : ignoredIds) {
            View itemView = inflater.inflate(R.layout.item_panel_cookie_option, featurePanelRepositoryIgnoredContainer, false);
            CheckBox checkBox = itemView.findViewById(R.id.check_cookie_option);
            TextView subtitle = itemView.findViewById(R.id.text_cookie_option_subtitle);
            checkBox.setText(warehouseRecordManager.nameOf(toolId.intValue()));
            checkBox.setChecked(selectedIds.contains(toolId));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                java.util.LinkedHashSet<Integer> ids = featureRepositorySelectedIgnoredIds.get(key);
                if (ids == null) {
                    ids = new java.util.LinkedHashSet<>();
                    featureRepositorySelectedIgnoredIds.put(key, ids);
                }
                if (isChecked) {
                    ids.add(toolId);
                } else {
                    ids.remove(toolId);
                }
            });
            subtitle.setText("物品 ID：" + toolId);
            featurePanelRepositoryIgnoredContainer.addView(itemView);
        }
    }

    private void refreshRepositoryForSelectedTargets() {
        executeRepositoryBatchAction("refresh");
    }

    private void recordRepositoryForSelectedTargets() {
        executeRepositoryBatchAction("record");
    }

    private void compareRepositoryForSelectedTargets() {
        executeRepositoryBatchAction("compare");
    }

    private void executeRepositoryBatchAction(String action) {
        List<FeatureCookieChoice> targets = getSelectedRepositoryChoices();
        if (targets.isEmpty()) {
            Toast.makeText(this, "请先选择记录 Cookie。", Toast.LENGTH_SHORT).show();
            return;
        }

        setRepositoryActionButtonsEnabled(false);
        new Thread(() -> {
            ArrayList<String> messages = new ArrayList<>();
            for (FeatureCookieChoice choice : targets) {
                String key = getRepositoryChoiceKey(choice);
                String label = choice.currentPage ? "当前页面 Cookie" : choice.label;
                try {
                    WarehouseRecordManager.RepositorySnapshot snapshot =
                            warehouseRecordManager.fetchRepository(choice.baseUrl, choice.cookies);
                    featureRepositoryCurrentSnapshots.put(key, snapshot);
                    if ("record".equals(action)) {
                        saveRepositoryRecordedSnapshot(key, snapshot);
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
            runOnUiThread(() -> {
                setRepositoryActionButtonsEnabled(true);
                if (featurePanelRepositoryHintText != null && !messages.isEmpty()) {
                    featurePanelRepositoryHintText.setText(TextUtils.join(" | ", messages));
                }
                renderRepositoryPanelV2();
            });
        }).start();
    }

    private void setRepositoryActionButtonsEnabled(boolean enabled) {
        if (featurePanelRepositoryRefreshButton != null) {
            featurePanelRepositoryRefreshButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryRecordButton != null) {
            featurePanelRepositoryRecordButton.setEnabled(enabled);
        }
        if (featurePanelRepositoryCompareButton != null) {
            featurePanelRepositoryCompareButton.setEnabled(enabled);
        }
    }

    private void addSelectedRepositoryDeltasToIgnored() {
        FeatureCookieChoice viewedChoice = getViewedRepositoryChoice();
        if (viewedChoice == null) {
            return;
        }
        String key = getRepositoryChoiceKey(viewedChoice);
        java.util.LinkedHashSet<Integer> selectedIds = featureRepositorySelectedDeltaIds.get(key);
        if (selectedIds == null || selectedIds.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<Integer> ignoredIds = loadRepositoryIgnoredIds(key);
        ignoredIds.addAll(selectedIds);
        saveRepositoryIgnoredIds(key, ignoredIds);
        selectedIds.clear();
        renderRepositoryPanelV2();
    }

    private void removeSelectedRepositoryIgnoredItems() {
        FeatureCookieChoice viewedChoice = getViewedRepositoryChoice();
        if (viewedChoice == null) {
            return;
        }
        String key = getRepositoryChoiceKey(viewedChoice);
        java.util.LinkedHashSet<Integer> selectedIds = featureRepositorySelectedIgnoredIds.get(key);
        if (selectedIds == null || selectedIds.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<Integer> ignoredIds = loadRepositoryIgnoredIds(key);
        ignoredIds.removeAll(selectedIds);
        saveRepositoryIgnoredIds(key, ignoredIds);
        selectedIds.clear();
        renderRepositoryPanelV2();
    }

    private WarehouseRecordManager.RepositorySnapshot loadRepositoryRecordedSnapshot(String key) {
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
            java.util.LinkedHashMap<Integer, Integer> toolAmounts = new java.util.LinkedHashMap<>();
            java.util.Iterator<String> keys = snapshotObject.keys();
            while (keys.hasNext()) {
                String toolId = keys.next();
                toolAmounts.put(Integer.valueOf(Integer.parseInt(toolId)), Integer.valueOf(snapshotObject.optInt(toolId, 0)));
            }
            ArrayList<WarehouseRecordManager.ToolEntry> entries = new ArrayList<>();
            for (java.util.Map.Entry<Integer, Integer> entry : toolAmounts.entrySet()) {
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

    private void saveRepositoryRecordedSnapshot(String key, WarehouseRecordManager.RepositorySnapshot snapshot) {
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
            for (java.util.Map.Entry<Integer, Integer> entry : snapshot.toolAmounts.entrySet()) {
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

    private java.util.LinkedHashSet<Integer> loadRepositoryIgnoredIds(String key) {
        java.util.LinkedHashSet<Integer> result = new java.util.LinkedHashSet<>();
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

    private void saveRepositoryIgnoredIds(String key, java.util.LinkedHashSet<Integer> ignoredIds) {
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

    private FeatureCookieChoice buildCurrentPageFeatureCookieChoice() {
        String currentUrl = wrapper == null ? null : wrapper.getUrl();
        if (TextUtils.isEmpty(currentUrl)) {
            return null;
        }
        Uri currentUri = Uri.parse(currentUrl);
        if (!CookieProfileManager.isSupportedSavePage(currentUri)) {
            return null;
        }
        String cookies = CookieManager.getInstance().getCookie(currentUrl);
        String baseUrl = CookieProfileManager.buildRootUrl(currentUri);
        if (TextUtils.isEmpty(cookies) || TextUtils.isEmpty(baseUrl)) {
            return null;
        }

        FeatureCookieChoice choice = new FeatureCookieChoice();
        choice.label = TextUtils.isEmpty(wrapper.getTitle()) ? currentUri.getHost() : wrapper.getTitle();
        choice.pageUrl = currentUrl;
        choice.subtitle = currentUrl;
        choice.baseUrl = baseUrl;
        choice.cookies = cookies;
        choice.currentPage = true;
        choice.selected = preferenceStore.isCurrentPageCookieSelectedByDefault();
        return choice;
    }

    private int getSavedPanelConcurrency() {
        return preferenceStore.getPanelConcurrency(1);
    }

    private void savePanelConcurrency(int concurrency) {
        preferenceStore.setPanelConcurrency(concurrency);
    }

    private int getSavedPanelRequestInterval() {
        return preferenceStore.getPanelRequestInterval(700);
    }

    private void savePanelRequestInterval(int intervalMs) {
        preferenceStore.setPanelRequestInterval(intervalMs);
    }

    private int getSavedPanelFrequentRetryInterval() {
        return preferenceStore.getPanelFrequentRetryInterval(14000);
    }

    private void savePanelFrequentRetryInterval(int intervalMs) {
        preferenceStore.setPanelFrequentRetryInterval(intervalMs);
    }

    private void switchFeaturePanelTab(int tab) {
        featurePanelUiController.switchTab(
                tab,
                FEATURE_PANEL_TAB_COOKIE,
                FEATURE_PANEL_TAB_BASIC,
                FEATURE_PANEL_TAB_REPOSITORY,
                FEATURE_PANEL_TAB_LOG
        );
    }

    private boolean isPersistedCookieChoiceSelected(String selectionKey) {
        if (TextUtils.isEmpty(selectionKey)) {
            return false;
        }
        Set<String> selectedKeys = preferenceStore.getSelectedCookieKeys();
        return selectedKeys.contains(selectionKey);
    }

    private void persistFeatureCookieChoiceSelection(FeatureCookieChoice choice, boolean selected) {
        if (choice == null) {
            return;
        }
        if (choice.currentPage) {
            preferenceStore.setCurrentPageCookieSelectedByDefault(selected);
            return;
        }
        if (TextUtils.isEmpty(choice.selectionKey)) {
            return;
        }
        Set<String> selectedKeys = new HashSet<>(preferenceStore.getSelectedCookieKeys());
        if (selected) {
            selectedKeys.add(choice.selectionKey);
        } else {
            selectedKeys.remove(choice.selectionKey);
        }
        preferenceStore.setSelectedCookieKeys(selectedKeys);
    }

    private void startDailyDutyRequestsFromPanel(boolean startCheckedItemsOnly) {
        if (featurePanelConcurrencyInput == null
                || featurePanelRequestIntervalInput == null
                || featurePanelFrequentRetryIntervalInput == null) {
            return;
        }
        if (dutyRequestQueue.isBusy()) {
            Toast.makeText(this, "请求队列正在运行，请先暂停或终止。", Toast.LENGTH_SHORT).show();
            return;
        }

        int concurrency = parsePositiveInt(featurePanelConcurrencyInput.getText().toString(), getSavedPanelConcurrency());
        savePanelConcurrency(concurrency);
        featurePanelConcurrencyInput.setText(String.valueOf(concurrency));
        int requestIntervalMs = parseNonNegativeInt(
                featurePanelRequestIntervalInput.getText().toString(),
                getSavedPanelRequestInterval()
        );
        savePanelRequestInterval(requestIntervalMs);
        featurePanelRequestIntervalInput.setText(String.valueOf(requestIntervalMs));
        int frequentRetryIntervalMs = parseNonNegativeInt(
                featurePanelFrequentRetryIntervalInput.getText().toString(),
                getSavedPanelFrequentRetryInterval()
        );
        savePanelFrequentRetryInterval(frequentRetryIntervalMs);
        featurePanelFrequentRetryIntervalInput.setText(String.valueOf(frequentRetryIntervalMs));

        boolean dailyDutyChecked = featurePanelDailyDutyCheckBox != null && featurePanelDailyDutyCheckBox.isChecked();
        if (startCheckedItemsOnly && !dailyDutyChecked) {
            Toast.makeText(this, "No task selected.", Toast.LENGTH_SHORT).show();
            return;
        }

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
            Toast.makeText(this, "请先勾选至少一个可用 Cookie。", Toast.LENGTH_SHORT).show();
            return;
        }

        dutyRequestQueue.startDailyDutyRewards(
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

    private void renderFeaturePanelQueueState(DutyRequestQueue.StateSnapshot snapshot) {
        if (featurePanelUiController != null) {
            featurePanelUiController.renderQueueState(snapshot);
            return;
        }
        if (snapshot == null) {
            return;
        }

        if (featurePanelQueueStatusText != null) {
            String state;
            if (snapshot.cancelling) {
                state = "正在终止";
            } else if (snapshot.running && snapshot.paused) {
                state = "已暂停";
            } else if (snapshot.running) {
                state = "运行中";
            } else {
                state = "空闲";
            }
            featurePanelQueueStatusText.setText(
                    "状态：" + state
                            + "\n总数：" + snapshot.total
                            + "  排队：" + snapshot.queued
                            + "  进行中：" + snapshot.active
                            + "\n完成：" + snapshot.completed
                            + "  失败：" + snapshot.failed
                            + "  跳过：" + snapshot.skipped
            );
        }

        if (featurePanelQueueLogText != null) {
            if (snapshot.logs.isEmpty()) {
                featurePanelQueueLogText.setText("暂无日志");
            } else {
                StringBuilder builder = new StringBuilder();
                for (String line : snapshot.logs) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(line);
                }
                featurePanelQueueLogText.setText(builder.toString());
            }
        }

        if (featurePanelPauseResumeButton != null) {
            if (snapshot.running && snapshot.paused) {
                featurePanelPauseResumeButton.setText("继续");
            } else {
                featurePanelPauseResumeButton.setText("暂停");
            }
            featurePanelPauseResumeButton.setEnabled(snapshot.running && !snapshot.cancelling);
        }
        if (featurePanelCancelButton != null) {
            featurePanelCancelButton.setEnabled(snapshot.running || snapshot.paused || snapshot.cancelling);
        }
        boolean idle = !snapshot.running && !snapshot.paused && !snapshot.cancelling;
        if (featurePanelDailyDutyRunButton != null) {
            featurePanelDailyDutyRunButton.setEnabled(idle);
        }
        if (featurePanelStartSelectedButton != null) {
            featurePanelStartSelectedButton.setEnabled(idle);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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

    private String buildFontMenuTitle(int currentMode, int itemMode, String label) {
        return (currentMode == itemMode ? "✓ " : "") + label;
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

    private void showFullscreenContent(View view, WebChromeClient.CustomViewCallback callback) {
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

        browserChrome.setVisibility(View.GONE);
        if (fullscreenRotateButton != null) {
            fullscreenRotateButton.setVisibility(View.VISIBLE);
        }
        if (fullscreenExitButton != null) {
            fullscreenExitButton.setVisibility(View.VISIBLE);
        }
        if (fullscreenFeaturePanelButton != null) {
            fullscreenFeaturePanelButton.setVisibility(View.VISIBLE);
        }
        fullscreenContainer.setVisibility(View.VISIBLE);
        fullscreenContainer.removeAllViews();
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setSystemBarsHidden(true);
        requestInsetRefresh();
    }

    private void hideFullscreenContent() {
        if (customView == null) {
            return;
        }

        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        browserChrome.setVisibility(View.VISIBLE);
        if (fullscreenRotateButton != null) {
            fullscreenRotateButton.setVisibility(View.GONE);
        }
        if (fullscreenExitButton != null) {
            fullscreenExitButton.setVisibility(View.GONE);
        }
        if (fullscreenFeaturePanelButton != null) {
            fullscreenFeaturePanelButton.setVisibility(View.GONE);
        }

        setSystemBarsHidden(false);
        applySavedOrientation();
        requestInsetRefresh();

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }

        customView = null;
        customViewCallback = null;
    }

    private void rotateFullscreenOrientation() {
        if (customView == null && !inAppRuffleFullscreen) {
            return;
        }

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
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

            if (shouldInjectHtml(request, statusCode, mimeType)) {
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
            InputStream inputStream = getAssets().open(assetPath);
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
        String host = resolvedUri == null ? "" : String.valueOf(resolvedUri.getHost());
        String path = resolvedUri == null ? "" : String.valueOf(resolvedUri.getPath());
        if (host.endsWith("pvzol.org") || path.startsWith("/pvz/") || path.startsWith("/youkia/")) {
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

    private boolean shouldInjectHtml(WebResourceRequest request, int statusCode, String mimeType) {
        if (statusCode < 200 || statusCode >= 300) {
            return false;
        }

        if (mimeType == null) {
            return false;
        }

        String normalizedMimeType = mimeType.toLowerCase(Locale.US);
        return normalizedMimeType.contains("text/html");
    }

    private String injectBootstrapIntoHtml(String html) {
        String cleanedHtml = CSP_META_PATTERN.matcher(html).replaceAll("");
        String scriptTag = buildRuffleInjectionTag();
        if (cleanedHtml.contains(scriptTag)) {
            return cleanedHtml;
        }

        if (cleanedHtml.matches("(?is).*?</head>.*")) {
            return cleanedHtml.replaceFirst("(?is)</head>", Matcher.quoteReplacement(scriptTag + "</head>"));
        }

        if (cleanedHtml.matches("(?is).*?<html[^>]*>.*")) {
            return cleanedHtml.replaceFirst("(?is)<html[^>]*>", "$0" + Matcher.quoteReplacement(scriptTag));
        }

        return scriptTag + cleanedHtml;
    }

    private String buildRuffleInjectionTag() {
        return "<script>" + buildRuffleConfigScript() + "</script>"
                + "<script src=\"" + RUFFLE_PATH_PREFIX + BOOTSTRAP_SCRIPT + "\"></script>";
    }

    private String buildRuffleConfigScript() {
        int fontMode = getSavedFontMode();
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

    private String escapeJsString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
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
        return request != null
                && request.isForMainFrame()
                && isProxyableRequest(uri, request.getMethod());
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (wrapper != null) {
            wrapper.saveState(outState);
        }
    }

    private void handleBackNavigation() {
        if (customView != null) {
            hideFullscreenContent();
            return;
        }
        if (inAppRuffleFullscreen) {
            toggleRuffleFullscreenCompat();
            return;
        }
        if (wrapper.canGoBack()) {
            wrapper.goBack();
        } else {
            finish();
        }
    }

    private static final class FeatureCookieChoice {
        String label;
        String subtitle;
        String pageUrl;
        String baseUrl;
        String cookies;
        String selectionKey;
        boolean selected;
        boolean currentPage;
    }
}
