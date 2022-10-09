package cj.instawall.plus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

@SuppressLint("SetJavaScriptEnabled")
public class InstaWebView extends WebView {
    final String TAG = "CJ";
    Context context;

    public InstaWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        this.getSettings().setDomStorageEnabled(true);
        this.getSettings().setJavaScriptEnabled(true);
        this.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
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
                    SharedPreferences.Editor editor = context.getSharedPreferences(
                            MainActivity.GLOBAL_SHARED_PREF, Context.MODE_PRIVATE).edit();
                    editor.putString("X-IG-App-ID", headers.get("X-IG-App-ID"));
                    editor.putString("cookie", CookieManager.getInstance()
                            .getCookie("https://www.instagram.com"));
                    editor.apply();
//                    Log.d(TAG, "cookie: " + CookieManager.getInstance()
//                            .getCookie("https://www.instagram.com"));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
    }

    public void login() {
        this.loadUrl("https://www.instagram.com/accounts/login/");
    }
}
