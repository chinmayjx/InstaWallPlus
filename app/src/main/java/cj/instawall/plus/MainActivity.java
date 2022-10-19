package cj.instawall.plus;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    Button A, B, C, D, E, F, G, H, I;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;
    String interceptor;

    MainService mainService;

    void initializeUI() {
        if(getSupportActionBar() != null) getSupportActionBar().hide();
        wvHolder = findViewById(R.id.webViewHolder);
        wv = new InstaWebView(this, () -> mainService.createInstaClient(), interceptor);
        wvHolder.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        A = findViewById(R.id.A);
        B = findViewById(R.id.B);
        C = findViewById(R.id.C);
        D = findViewById(R.id.D);
        E = findViewById(R.id.E);
        F = findViewById(R.id.F);
        G = findViewById(R.id.G);
        H = findViewById(R.id.H);
        I = findViewById(R.id.I);
        Button[] btns = {A, B, C, D, E, F, G, H, I};
        for (Button b : btns) {
            b.setTransformationMethod(null);
        }
    }

    void addClickListeners() {
        A.setOnClickListener(v -> wv.login());
        B.setOnClickListener(v -> {
            mainService.instaClient.act_getSavedPosts();
        });
        C.setOnClickListener(v -> {
            mainService.instaClient.act_setRandomWallpaper();
        });
        D.setOnClickListener(v -> {
            mainService.instaClient.act_continueLastSync();
        });
        E.setOnClickListener(v -> {
            new RESTServer(mainService.instaClient).startListening();
        });
        F.setOnClickListener(v -> {
//            mainService.instaClient.act_test();
            InstaClient.setCurrentUser("chinmayjain08");
            InstaClient.commitAuthFile();
            InstaClient.initializeVariables();
        });
        G.setOnClickListener(v -> {
            wv.loadUrl("about:blank");
            CookieManager.getInstance().removeAllCookies(null);
            String user = InstaClient.getNextUser();
            String cookie = InstaClient.getUserProperty(user, "cookie");
//            Log.d(TAG, "cookie set: " + cookie);
            InstaWebView.setCookie("https://www.instagram.com", cookie);
            InstaWebView.setCookie("https://i.instagram.com", cookie);
//            wv.loadUrl("https://www.instagram.com/" + user + "/saved/");

            InstaClient.switchToUser(user);

            G.setText(user);

        });
        H.setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            wv.loadUrl("https://www.instagram.com");
        });
        I.setOnClickListener(v -> {
            startActivity(new Intent(this, ViewActivity.class));
        });
    }

    void readScripts() {
        try {
            Scanner s = new Scanner(getAssets().open("Interceptor.js")).useDelimiter("\\A");
            interceptor = s.hasNext() ? s.next() : "";
            s.close();
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    void onCreateTest() {
        try {
//            mainService.instaClient.act_test();
        } catch (Exception e) {
            Log.e(TAG, "onCreateTest: failed, " + Log.getStackTraceString(e));
        }
    }

    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "MainActivity connected to MainService");
            MainService.MainBinder binder = (MainService.MainBinder) iBinder;
            mainService = binder.getService();
            addClickListeners();
            onCreateTest();

            G.setText(InstaClient.username);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "MainActivity disconnected from MainService");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindService(new Intent(this, MainService.class), serviceConnection, Context.BIND_AUTO_CREATE);

        readScripts();

        sharedPreferences = getSharedPreferences(GLOBAL_SHARED_PREF, MODE_PRIVATE);
        spEditor = sharedPreferences.edit();

        initializeUI();
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