package io.github.zhengnanli.glancekiosk;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Full-screen Glance dashboard: one WebView, no system bars, screen kept on.
 * Links to other sites open in the default browser so the dashboard stays put.
 */
public class KioskActivity extends Activity {
    private static final long RETRY_MS = 15_000;          // after a failed load
    private static final long RELOAD_MS = 10 * 60_000;    // periodic refresh, like the CM4 kiosk
    private static final long IDLE_MS = 2 * 60_000;       // never reload while someone is using it

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private String home;
    private String homeHost;
    private long lastTouch;
    // Page zoom, as in a desktop browser: the page is laid out 1/zoom times wider than
    // the screen and scaled to fit (build.sh: KIOSK_ZOOM).
    private float zoom;

    private final Runnable retry = () -> web.reload();
    private final Runnable periodic = new Runnable() {
        @Override public void run() {
            if (SystemClock.uptimeMillis() - lastTouch >= IDLE_MS) web.reload();
            handler.postDelayed(this, RELOAD_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        home = getString(R.string.url);
        homeHost = Uri.parse(home).getHost();
        zoom = Float.parseFloat(getString(R.string.zoom));

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }

        // Remote debugging over adb (chrome://inspect), only in builds made with KIOSK_DEBUG=1.
        WebView.setWebContentsDebuggingEnabled(getResources().getBoolean(R.bool.debug));
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setUseWideViewPort(true);      // honour the viewport width set by applyZoom()
        s.setLoadWithOverviewMode(true); // and scale that width to the screen
        s.setSupportZoom(false);
        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri uri = req.getUrl();
                if (homeHost.equalsIgnoreCase(uri.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri)
                            .addCategory(Intent.CATEGORY_BROWSABLE));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    handler.removeCallbacks(retry);
                    handler.postDelayed(retry, RETRY_MS);
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                applyZoom();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                applyZoom();
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                recreate();
                return true;
            }
        });

        setContentView(web);
        if (state == null || web.restoreState(state) == null) web.loadUrl(home);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        applyZoom();
    }

    /** Lay the page out at screen width / zoom CSS pixels by rewriting its viewport tag. */
    private void applyZoom() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = Math.round(dm.widthPixels / dm.density / zoom);
        web.evaluateJavascript("(function(w){var m=document.querySelector('meta[name=viewport]');"
                + "if(!m){m=document.createElement('meta');m.name='viewport';"
                + "(document.head||document.documentElement).appendChild(m);}"
                + "var c='width='+w;if(m.content!==c)m.content=c;})(" + width + ")", null);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastTouch = SystemClock.uptimeMillis();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
        hideSystemBars();
        handler.removeCallbacks(periodic);
        handler.postDelayed(periodic, RELOAD_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(periodic);
        CookieManager.getInstance().flush();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
