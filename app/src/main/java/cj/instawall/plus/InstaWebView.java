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

@SuppressLint("SetJavaScriptEnabled")
public class InstaWebView extends WebView {
    static final String TAG = "CJ";
    Context context;
    Runnable loginCallback;
    String interceptor;

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

        this.getSettings().setDomStorageEnabled(true);
        this.getSettings().setJavaScriptEnabled(true);
        this.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(interceptor, null);
                super.onPageFinished(view, url);
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().contains("api/v1/feed/timeline") &&
                        request.getMethod().equals("POST")) {
                    Map<String, String> headers = request.getRequestHeaders();

                    if (headers.containsKey("X-IG-App-ID"))
                        InstaClient.setAppID(headers.get("X-IG-App-ID"));

                    InstaClient.setCurrentUserProperty("cookie", CookieManager.getInstance()
                            .getCookie("https://www.instagram.com"));
                    InstaClient.commitAuthFile();
                    loginCallback.run();
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

    public void login() {
        this.loadUrl("https://www.instagram.com/accounts/login/");
    }
}
