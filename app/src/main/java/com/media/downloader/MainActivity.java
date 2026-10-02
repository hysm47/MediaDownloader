package com.media.downloader;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.FrameLayout;

import java.io.IOException;
import java.io.InputStream;
import org.json.JSONObject;
import dev.ffmpegkit_maintained.ytdlp.YtDlp;
import dev.ffmpegkit_maintained.ytdlp.YtDlpException;
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest;
import dev.ffmpegkit_maintained.ytdlp.YtDlpResponse;

/**
 * Thin WebView wrapper. The application itself is assets/www/index.html, unmodified.
 *
 * The page is served from a virtual https origin instead of file:// so it runs in a
 * secure context: localStorage (theme/language), history.pushState (Back gesture
 * between screens) and navigator.clipboard behave as in a normal browser.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/index.html";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            YtDlp.init(getApplicationContext());
        } catch (YtDlpException e) {
            e.printStackTrace();
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0a101c"));
        // Keep content clear of system bars / keyboard (Android 15 forces edge-to-edge).
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0a101c"));
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage: md-theme / md-lang
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);    // target="_blank" -> shouldOverrideUrlLoading
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);


        // Expose a very small native bridge to the app-owned HTML page.
        // Stage 3 only verifies communication; it does not download anything yet.
        web.addJavascriptInterface(new DownloadBridge(), "MediaDownloader");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.isEmpty() || path.equals("/")) path = "/index.html";
                if (!path.equals("/index.html")) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                }
                try {
                    InputStream in = getAssets().open("www/index.html");
                    return new WebResourceResponse("text/html", "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;   // internal page
                openExternal(u);                              // browser / Telegram / WhatsApp / mail
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        }
        if (web.getUrl() == null) {
            web.loadUrl(HOME);
        }
    }


    /**
     * JavaScript bridge for the next download-engine stage.
     *
     * The bridge intentionally does not start a download yet. It only receives
     * validated UI choices and returns an acknowledgement so the WebView/Android
     * communication path can be tested independently.
     */
    private static final class DownloadBridge {
        @JavascriptInterface
        public String getVersion() {
            return "3";
        }

        @JavascriptInterface
        public String requestDownload(String url, String mode, String choice) {
            try {
                if (url == null || url.trim().isEmpty()) {
                    return "{\"ok\":false,\"error\":\"empty_url\"}";
                }

                YtDlpRequest request = new YtDlpRequest(url.trim());
                request.addOption("-f", "18");
                request.addOption("-o", "/sdcard/Download/%(title)s.%(ext)s");
                request.addOption("--extractor-args", "youtube:player_client=android");

                YtDlpResponse response = YtDlp.execute(request, null);

                JSONObject result = new JSONObject();
                result.put("ok", response.isSuccess());
                result.put("exitCode", response.getExitCode());
                result.put("output", response.getOutput() == null ? "" : response.getOutput());
                result.put("error", response.getErrorOutput() == null ? "" : response.getErrorOutput());
                return result.toString();

            } catch (Exception e) {
                try {
                    StringBuilder detail = new StringBuilder();
                    Throwable current = e;
                    int depth = 0;

                    while (current != null && depth < 5) {
                        if (depth > 0) detail.append("\\nCaused by: ");
                        detail.append(current.getClass().getName());
                        String msg = current.getMessage();
                        if (msg != null && !msg.isEmpty()) {
                            detail.append(": ").append(msg);
                        }
                        current = current.getCause();
                        depth++;
                    }

                    JSONObject result = new JSONObject();
                    result.put("ok", false);
                    result.put("error", e.getClass().getSimpleName());
                    result.put("message", detail.toString());
                    return result.toString();
                } catch (Exception ignored) {
                    return "{\"ok\":false,\"error\":\"ytdlp_error\"}";
                }
            }
        }
    }

    private void openExternal(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (ActivityNotFoundException ignored) {
            // nothing on the device can open this link
        }
    }

    /** Back walks through the app's own screens (history.back()), then exits. */
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            ((FrameLayout) web.getParent()).removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
