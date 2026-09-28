package dev.rokid.pocketweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.JsPromptResult;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Browser shell for the RV101/Glass3 display. No vendor SDK is needed for WebView/KeyEvent. */
public final class MainActivity extends Activity {
    private WebView browser;
    private String hudScript;
    private boolean pageReady;
    private HeadInput head;
    private TextView calibration;
    private String bridgeToken;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setBackgroundDrawableResource(android.R.color.black);

        try (InputStream input = getAssets().open("hud.js");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            hudScript = new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("HUD asset missing", error);
        }

        browser = new WebView(this);
        browser.setBackgroundColor(Color.BLACK);
        browser.setFocusableInTouchMode(true);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        WebView.setWebContentsDebuggingEnabled(false);

        browser.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsPrompt(WebView view, String url, String message,
                                                String defaultValue, JsPromptResult result) {
                String prefix = "__ROKID_POCKET_WEB__:" + bridgeToken + ":";
                if (bridgeToken != null && message != null && message.startsWith(prefix)
                        && url != null && url.equals(view.getUrl())) {
                    String action = message.substring(prefix.length());
                    if ("head".equals(action) || "center".equals(action) || "back".equals(action)) {
                        result.confirm("");
                        view.post(() -> performNativeAction(action));
                        return true;
                    }
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
        });
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    return false;
                }
                Toast.makeText(MainActivity.this, "Ссылка требует другое приложение", Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                pageReady = false;
                bridgeToken = UUID.randomUUID().toString();
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (!url.equals(view.getUrl())) return;
                String script = "(function(){const __rokidBridgeToken=" +
                        org.json.JSONObject.quote(bridgeToken) + ";" + hudScript + "})()";
                view.evaluateJavascript(script, ignored -> {
                    if (url.equals(view.getUrl())) {
                        pageReady = true;
                        updateHeadLabel();
                    }
                });
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this, "Не удалось открыть страницу", Toast.LENGTH_SHORT).show();
                }
            }
        });
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        frame.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        calibration = new TextView(this);
        calibration.setBackgroundColor(Color.BLACK);
        calibration.setTextColor(Color.rgb(165, 255, 185));
        calibration.setTextSize(23);
        calibration.setGravity(Gravity.CENTER);
        calibration.setPadding(28, 48, 28, 28);
        calibration.setVisibility(View.GONE);
        frame.addView(calibration, new FrameLayout.LayoutParams(-1, -1));
        head = new HeadInput(this, new HeadInput.Listener() {
            @Override public void onPrompt(String text) {
                calibration.setText(text == null ? "" : text);
                calibration.setVisibility(text == null ? View.GONE : View.VISIBLE);
            }

            @Override public void onEnabled(boolean enabled) { updateHeadLabel(); }

            @Override public void onGesture(HeadGestureDetector.Gesture gesture) {
                switch (gesture) {
                    case NEXT: hud("next"); break;
                    case PREVIOUS: hud("previous"); break;
                    case ACTIVATE: hud("activate"); break;
                    default: break;
                }
            }
        });
        setContentView(frame);
        if (state == null || browser.restoreState(state) == null) showHome();
    }

    private void showHome() {
        pageReady = false;
        String html = "<!doctype html><html lang='ru'><meta name='viewport' " +
                "content='width=device-width,initial-scale=1'><title>Pocket Web</title>" +
                "<body style='background:#000;color:#79ffa7'>" +
                "<main><h1>POCKET WEB</h1><p>Веб-сервисы в зелёных карточках</p>" +
                "<p>Пролистывай свайпом, выбирай касанием.</p>" +
                "<a href='https://www.google.com'>Google</a><br>" +
                "<a href='https://www.wikipedia.org'>Wikipedia</a><br>" +
                "<a href='https://github.com'>GitHub</a><br>" +
                "<a href='https://m.youtube.com'>YouTube</a><br>" +
                "<a href='https://web.telegram.org'>Telegram Web</a><br>" +
                "<a href='https://chatgpt.com'>ChatGPT</a><br>" +
                "<a href='https://chat.deepseek.com'>DeepSeek</a><br>" +
                "<a href='https://chat.qwen.ai'>Qwen</a></main></body></html>";
        browser.loadDataWithBaseURL("https://pocket.invalid/", html, "text/html", "UTF-8", null);
    }

    private void hud(String command) {
        if (!pageReady) return;
        browser.evaluateJavascript("window.__rokidHud && window.__rokidHud." + command + "()", result -> {
            if ("\"back\"".equals(result)) performNativeAction("back");
            else if ("\"head\"".equals(result)) performNativeAction("head");
            else if ("\"center\"".equals(result)) performNativeAction("center");
        });
    }

    private void performNativeAction(String action) {
        switch (action) {
            case "back":
                if (browser.canGoBack()) browser.goBack(); else showHome();
                break;
            case "head":
                if (!head.available()) {
                    Toast.makeText(this, "Датчик поворота недоступен", Toast.LENGTH_SHORT).show();
                } else if (head.isEnabled()) head.disable();
                else head.startCalibration();
                break;
            case "center":
                if (head.isEnabled()) {
                    Toast.makeText(this, head.recenter() ? "Центр обновлён" : "Жду датчик",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    private void updateHeadLabel() {
        if (pageReady && head != null) {
            browser.evaluateJavascript("window.__rokidHud && window.__rokidHud.setHeadEnabled(" +
                    head.isEnabled() + ")", null);
        }
    }

    private void back() {
        if (!pageReady) {
            if (browser.canGoBack()) browser.goBack(); else showHome();
            return;
        }
        browser.evaluateJavascript("window.__rokidHud && window.__rokidHud.back()", result -> {
            if (!"\"handled\"".equals(result)) {
                if (browser.canGoBack()) browser.goBack(); else showHome();
            }
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        final int key = event.getKeyCode();
        final boolean hudKey = key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_UP
                || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER
                || key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU;
        // Consume both halves of a handled key, otherwise WebView may click a second time on UP.
        if (event.getAction() == KeyEvent.ACTION_UP && hudKey) return true;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        if (head != null && head.isCalibrating()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) head.disable();
            else if ((event.getKeyCode() == KeyEvent.KEYCODE_ENTER ||
                    event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER) &&
                    event.getRepeatCount() == 0) head.capture();
            return true;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.getRepeatCount() == 0) hud("next");
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                if (event.getRepeatCount() == 0) hud("previous");
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) hud("activate");
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (event.getRepeatCount() == 0) back();
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (event.getRepeatCount() == 0) hud("toggle");
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override public void onBackPressed() {
        if (head != null && head.isCalibrating()) head.disable();
        else back();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        browser.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (browser != null) browser.onResume();
        if (head != null) head.onResume();
    }

    @Override protected void onPause() {
        if (browser != null) browser.onPause();
        if (head != null) head.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    @Override protected void onDestroy() {
        browser.destroy();
        super.onDestroy();
    }
}
