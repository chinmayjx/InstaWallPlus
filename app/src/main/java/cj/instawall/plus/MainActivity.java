package cj.instawall.plus;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cj.instawall.plus.InstaClient;

public class MainActivity extends AppCompatActivity {
    final String TAG = "CJ";
    public static final String GLOBAL_SHARED_PREF = "cj_pref_12345";
    FrameLayout wvHolder;
    InstaWebView wv;
    InstaClient instaClient;
    Button A, B, C, D, E;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;
    String interceptor;

    void createInstaClient() {
        HashMap<String, String> headers = new HashMap<>();
        String cookie = sharedPreferences.getString("cookie", null);
        String appID = sharedPreferences.getString("X-IG-App-ID", null);
        if (cookie == null || appID == null) {
            Log.e(TAG, "Can't create InstaClient in MainActivity");
        }
        headers.put("cookie", cookie);
        headers.put("X-IG-App-ID", appID);
        instaClient = new InstaClient(headers, sharedPreferences.getString("username", null));
        Log.d(TAG, "Updated InstaClient in MainActivity");
    }

    void initializeUI() {
        getSupportActionBar().hide();
        wvHolder = findViewById(R.id.webViewHolder);
        wv = new InstaWebView(this, this::createInstaClient, interceptor);
        wvHolder.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        A = findViewById(R.id.A);
        B = findViewById(R.id.B);
        C = findViewById(R.id.C);
        D = findViewById(R.id.D);
        E = findViewById(R.id.E);
        Button[] btns = {A, B, C, D, E};
        for (Button b : btns) {
            b.setTransformationMethod(null);
        }
    }

    void addClickListeners() {
        A.setOnClickListener(v -> wv.login());
        B.setOnClickListener(v -> {
            instaClient.act(InstaClient.GET_SAVED_POSTS);
        });
        C.setOnClickListener(v -> {

        });
    }

    void readScripts() {
        try {
            Scanner s = new Scanner(getAssets().open("Interceptor.js")).useDelimiter("\\A");
            interceptor = s.hasNext() ? s.next() : "";
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        readScripts();

        sharedPreferences = getSharedPreferences(GLOBAL_SHARED_PREF, MODE_PRIVATE);
        spEditor = sharedPreferences.edit();

        initializeUI();
        addClickListeners();

        createInstaClient();
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