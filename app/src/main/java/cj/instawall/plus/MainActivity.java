package cj.instawall.plus;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;

import cj.instawall.plus.InstaClient;

public class MainActivity extends AppCompatActivity {
    final String TAG = "CJ";
    public static final String GLOBAL_SHARED_PREF = "cj_pref_12345";
    InstaWebView wv;
    InstaClient instaClient;
    Button A, B, C, D, E;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;


    void initializeUI() {
        getSupportActionBar().hide();
        wv = findViewById(R.id.webView);
        A = findViewById(R.id.A);
        B = findViewById(R.id.B);
        C = findViewById(R.id.C);
        D = findViewById(R.id.D);
        E = findViewById(R.id.E);
        Button[] btns = {A,B,C,D,E};
        for(Button b: btns){
            b.setTransformationMethod(null);
        }
    }

    void addClickListeners() {
        A.setOnClickListener(v -> wv.login());
        B.setOnClickListener(v -> {
            HashMap<String,String> headers = new HashMap<>();
            headers.put("cookie", sharedPreferences.getString("cookie", null));
            headers.put("X-IG-App-ID", sharedPreferences.getString("X-IG-App-ID", null));
            instaClient = new InstaClient(headers);
        });
        C.setOnClickListener(v -> {
            instaClient.act(InstaClient.GET_SAVED_POSTS);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(GLOBAL_SHARED_PREF , MODE_PRIVATE);
        spEditor = sharedPreferences.edit();

        initializeUI();
        addClickListeners();




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