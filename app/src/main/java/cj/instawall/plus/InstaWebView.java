package cj.instawall.plus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressLint("SetJavaScriptEnabled")
public class InstaWebView extends WebView {
    static final String TAG = "CJ";
    Context context;
    Runnable loginCallback;
    String interceptor;
    volatile String appID;
    final AtomicBoolean loginHandled = new AtomicBoolean(false);

    public static void setCookie(String url, String cookie){
        String[] cookies = cookie.split(";");
        Log.d(TAG, "setCookie: " + Arrays.toString(cookies));
        for (int i = 0; i < cookies.length; i++) {
            CookieManager.getInstance().setCookie(url, cookies[i]);
        }
    }

    public static void setInstaCookie(String cookie){
        InstaWebView.setCookie("https://www.instagram.com", cookie);
        InstaWebView.setCookie("https://i.instagram.com", cookie);
    }

    public InstaWebView(@NonNull Context context, Runnable loginCallback, String interceptor) {
        super(context);
        this.setBackgroundColor(Color.BLACK);
        this.context = context;
        this.loginCallback = loginCallback;
        this.interceptor = interceptor;
        InstaClient.loadAuthInfo(context);

        this.getSettings().setDomStorageEnabled(true);
        this.getSettings().setJavaScriptEnabled(true);
        this.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(interceptor, null);
                checkLogin();
                super.onPageFinished(view, url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                // instagram.com is a single page app, after logging in it doesn't always load a new page
                checkLogin();
                super.doUpdateVisitedHistory(view, url, isReload);
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Map<String, String> headers = request.getRequestHeaders();
                for (String h : headers.keySet()) {
                    if (h.equalsIgnoreCase("X-IG-App-ID")) appID = headers.get(h);
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        this.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                String msg = consoleMessage.message();
                if (msg.startsWith("set_username:")) {
                    String username = msg.substring("set_username:".length());
                    InstaClient.setCurrentUser(username);
                    InstaClient.commitAuthFile();
                    Log.d(TAG, "Username set to " + username);
                }
                Log.d(TAG, msg);
                return super.onConsoleMessage(consoleMessage);
            }
        });
    }

    // logged in once instagram has set the session cookies
    void checkLogin() {
        String cookie = CookieManager.getInstance().getCookie("https://www.instagram.com");
        if (InstaClient.getCookieValue(cookie, "sessionid") == null || InstaClient.getCookieValue(cookie, "ds_user_id") == null)
            return;
        if (!loginHandled.compareAndSet(false, true)) return;
        CookieManager.getInstance().flush();
        String userAgent = getSettings().getUserAgentString();
        new Thread(() -> {
            String username = InstaClient.fetchUsername(cookie, userAgent, appID);
            if (username == null) {
                Log.e(TAG, "checkLogin: logged in, but couldn't get the username");
                loginHandled.set(false);
                return;
            }
            InstaClient.onLogin(username, cookie, appID);
            post(loginCallback);
        }).start();
    }

    public void resetLogin() {
        loginHandled.set(false);
    }

    public void login() {
        this.loadUrl("https://www.instagram.com/accounts/login/");
    }
}
