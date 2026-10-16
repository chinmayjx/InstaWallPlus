package cj.instawall.plus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Message;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import cj.instawall.plus.core.Instagram;
import cj.instawall.plus.core.Reddit;

/**
 * instagram.com / reddit.com in a WebView, to log in. Notices a logged in session from the
 * cookies, asks the service who it is, saves the account and tells the listener.
 */
@SuppressLint("SetJavaScriptEnabled")
public class LoginWebView extends WebView {
    static final String TAG = "CJ";
    static final String INSTAGRAM_URL = "https://www.instagram.com";
    static final String REDDIT_URL = "https://www.reddit.com";

    public interface Listener {
        void onLoggedIn(String sourceId, String username);
    }

    private final Core core;
    private final Listener listener;
    private volatile String service = Instagram.SOURCE_ID;
    private final AtomicBoolean handled = new AtomicBoolean(false);
    private volatile String lastRedditCookie;
    private volatile String appId;

    public LoginWebView(@NonNull Context context, Core core, Listener listener) {
        super(context);
        this.core = core;
        this.listener = listener;
        setBackgroundColor(Color.BLACK);
        getSettings().setDomStorageEnabled(true);
        getSettings().setJavaScriptEnabled(true);
        // "opens in a new tab" links and popups (google sign in, forgot password) open here instead
        getSettings().setSupportMultipleWindows(true);
        getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView popup = new WebView(view.getContext());
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        LoginWebView.this.loadUrl(request.getUrl().toString());
                        v.destroy();
                        return true;
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                checkLogin();
                super.onPageFinished(view, url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                // both sites are single page apps, logging in doesn't always load a new page
                checkLogin();
                super.doUpdateVisitedHistory(view, url, isReload);
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Map<String, String> headers = request.getRequestHeaders();
                for (String h : headers.keySet()) if (h.equalsIgnoreCase("X-IG-App-ID")) appId = headers.get(h);
                return super.shouldInterceptRequest(view, request);
            }
        });
    }

    /** instagram with the current account's session (logs it in again if it expired) */
    public void openInstagram() {
        service = Instagram.SOURCE_ID;
        handled.set(false);
        String user = core.accounts.instagramUser();
        String cookie = user == null ? null : core.accounts.instagramCookie(user);
        if (cookie != null) setCookies(INSTAGRAM_URL, cookie);
        loadUrl(INSTAGRAM_URL + "/accounts/login/");
    }

    /** instagram logged out, to add another account */
    public void addInstagramAccount() {
        service = Instagram.SOURCE_ID;
        handled.set(false);
        CookieManager.getInstance().removeAllCookies(done -> post(() -> loadUrl(INSTAGRAM_URL + "/accounts/login/")));
    }

    public void openReddit() {
        service = Reddit.SOURCE_ID;
        handled.set(false);
        lastRedditCookie = null;
        String cookie = core.accounts.redditCookie();
        if (cookie != null) setCookies(REDDIT_URL, cookie);
        loadUrl(REDDIT_URL + "/login/");
    }

    public static void setCookies(String url, String cookie) {
        for (String c : cookie.split(";")) CookieManager.getInstance().setCookie(url, c.trim());
        CookieManager.getInstance().flush();
    }

    private void checkLogin() {
        if (Reddit.SOURCE_ID.equals(service)) checkReddit();
        else checkInstagram();
    }

    private void checkInstagram() {
        String cookie = CookieManager.getInstance().getCookie(INSTAGRAM_URL);
        if (AndroidHttp.cookieValue(cookie, "sessionid") == null || AndroidHttp.cookieValue(cookie, "ds_user_id") == null) return;
        if (!handled.compareAndSet(false, true)) return;
        CookieManager.getInstance().flush();
        new Thread(() -> {
            String user = core.instagramUsername(cookie);
            if (user == null) {
                Log.e(TAG, "logged into instagram, but couldn't get the username");
                handled.set(false);
                return;
            }
            core.accounts.loginInstagram(user, cookie, appId);
            loggedIn(Instagram.SOURCE_ID, user);
        }).start();
    }

    // reddit's session cookie names change; ask who we are whenever the cookies change
    private void checkReddit() {
        String cookie = CookieManager.getInstance().getCookie(REDDIT_URL);
        if (cookie == null || cookie.equals(lastRedditCookie) || handled.get()) return;
        lastRedditCookie = cookie;
        new Thread(() -> {
            String user = core.redditUsername();
            if (user == null || !handled.compareAndSet(false, true)) return;
            CookieManager.getInstance().flush();
            core.accounts.loginReddit(user, cookie);
            loggedIn(Reddit.SOURCE_ID, user);
        }).start();
    }

    private void loggedIn(String sourceId, String user) {
        Log.d(TAG, "logged into " + sourceId + " as " + user);
        core.reload();
        post(() -> listener.onLoggedIn(sourceId, user));
    }
}
