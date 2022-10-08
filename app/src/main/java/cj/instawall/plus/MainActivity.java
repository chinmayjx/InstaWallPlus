package cj.instawall.plus;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import java.util.Arrays;
import java.util.HashMap;

import cj.instawall.plus.InstaClient;

public class MainActivity extends AppCompatActivity {
    final String TAG = "CJ";
    String appId = null;
    WebView wv;
    Button A,B,C,D,E;

    @SuppressLint("SetJavaScriptEnabled")
    void setUpWV() {
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String cookies = CookieManager.getInstance().getCookie("https://www.instagram.com/");
                if (cookies.contains("sessionid")) {
                    Log.d(TAG, "Got sessionid cookie");
                }
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
//                for(String h : request.getRequestHeaders().keySet()){
//                    Log.d(TAG, h + " : " + request.getRequestHeaders().get(h));
//                }
                if (appId == null && request.getRequestHeaders().containsKey("X-IG-App-ID")) {
                    Log.d(TAG, "Got App ID");
                    appId = request.getRequestHeaders().get("X-IG-App-ID");
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setDomStorageEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        wv = findViewById(R.id.webView);
        A = findViewById(R.id.A);
        B = findViewById(R.id.B);
        C = findViewById(R.id.C);
        D = findViewById(R.id.D);
        E = findViewById(R.id.E);
        setUpWV();
        String cookies = CookieManager.getInstance().getCookie("https://www.instagram.com/");
        if (!cookies.contains("sessionid")) {
            wv.loadUrl("https://www.instagram.com/accounts/login/");
        } else {
            wv.loadUrl("https://www.instagram.com/chinmayjain08/saved/");
        }
        
        A.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HashMap<String,String> headers = new HashMap<>();
                headers.put("X-IG-App-ID",appId);
                InstaClient ic = new InstaClient(cookies, headers);
                ic.getSavedPosts();
            }
        });
        
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) {
            wv.goBack();
        } else {
            super.onBackPressed();
        }
    }

}