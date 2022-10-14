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
    Button A, B, C, D, E, F, G, H;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;
    String interceptor;

    MainService mainService;

    void initializeUI() {
        getSupportActionBar().hide();
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
        Button[] btns = {A, B, C, D, E, F, G, H};
        for (Button b : btns) {
            b.setTransformationMethod(null);
        }
    }

    void addClickListeners() {
        A.setOnClickListener(v -> wv.login());
        B.setOnClickListener(v -> {
            mainService.instaClient.act(InstaClient.GET_SAVED_POSTS);
        });
        C.setOnClickListener(v -> {
            mainService.instaClient.act(InstaClient.RANDOM_WALLPAPER);
        });
        D.setOnClickListener(v -> {
            mainService.instaClient.act(InstaClient.CONTINUE_LAST_SYNC);
        });
        E.setOnClickListener(v -> {
            new RESTServer(mainService.instaClient).startListening();
        });
        F.setOnClickListener(v -> {
            mainService.instaClient.act(InstaClient.TEST);
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
//            String f = getExternalFilesDir(null).toString() + "/chinmayjain08/images/2751610900940158259_2751610900940158259.jpg";
//            Log.d(TAG, f);
//            CJImageUtil.removeWhiteBorder(BitmapFactory.decodeFile(f));
//            new RESTServer(instaClient).startListening();
            mainService.instaClient.act(InstaClient.TEST);


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