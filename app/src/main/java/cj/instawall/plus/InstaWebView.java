package cj.instawall.plus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@SuppressLint("SetJavaScriptEnabled")
public class InstaWebView extends WebView {
    final String TAG = "CJ";
    Context context;
    Runnable loginCallback;
    String interceptor;
    SharedPreferences.Editor editor;

    public InstaWebView(@NonNull Context context, Runnable loginCallback, String interceptor) {
        super(context);

        this.context = context;
        this.loginCallback = loginCallback;
        this.interceptor = interceptor;

        editor = context.getSharedPreferences(
                MainActivity.GLOBAL_SHARED_PREF, Context.MODE_PRIVATE).edit();

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
                    Map<String,String> headers = request.getRequestHeaders();
//                    for (String h : headers.keySet()) {
//                        Log.d(TAG, h + ":" + headers.get(h));
//                    }
                    editor.putString("X-IG-App-ID", headers.get("X-IG-App-ID"));
                    editor.putString("cookie", CookieManager.getInstance()
                            .getCookie("https://www.instagram.com"));
                    editor.apply();
                    loginCallback.run();

//                    Log.d(TAG, "cookie: " + CookieManager.getInstance()
//                            .getCookie("https://www.instagram.com"));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        this.setWebChromeClient(new WebChromeClient(){
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                String msg = consoleMessage.message();
                if(msg.startsWith("set_username:")){
                    String username = msg.substring("set_username:".length());
                    editor.putString("username", username);
                    editor.apply();
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
