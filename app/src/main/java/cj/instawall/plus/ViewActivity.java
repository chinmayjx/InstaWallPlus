package cj.instawall.plus;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.Scanner;

public class ViewActivity extends AppCompatActivity {
    public static final String TAG = "CJ";
    public static final String GLOBAL_SHARED_PREF = "cj_pref_12345";
    InstaClient instaClient;
    RecyclerView rv;
    Spinner gridDataset, gridAction;
    RVAdapter rvAdapter;
    ImageViewer imageViewer;
    DrawerLayout drawerLayout;
    ListView drawerLV;
    FloatingActionButton fab;
    int displayWidth;

    ArrayAdapter<String> menuAdapter;
    MainService mainService;
    String interceptor;
    BiometricAuth biometricAuth;
    Handler handler;
    FrameLayout wvHolder;
    InstaWebView wv;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;

    void initializeViews(){
        readScripts();

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        imageViewer = findViewById(R.id.imgViewer);
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerLV = findViewById(R.id.nav_drawer);
        fab = findViewById(R.id.grid_fab);
        rv = findViewById(R.id.recycler_grid);
        wvHolder = findViewById(R.id.wvHolder);
        gridDataset = findViewById(R.id.grid_dataset);
        gridAction = findViewById(R.id.grid_action);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        wv = new InstaWebView(this, () -> mainService.createInstaClient(), interceptor);
        wvHolder.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void setupRVAdapter(){
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        displayWidth = displayMetrics.widthPixels;
        rvAdapter = new RVAdapter(instaClient, this);

        rvAdapter.onEnterSelected = () -> fab.setVisibility(View.VISIBLE);
        rvAdapter.onExitSelected = () -> fab.setVisibility(View.GONE);

        rv.setAdapter(rvAdapter);
        rv.setLayoutManager(new GridLayoutManager(this, displayWidth / 500));
    }

    void setupSpinners(){
        ArrayAdapter<RVAdapter.Dataset> datasetAdapter = new ArrayAdapter<RVAdapter.Dataset>(this, android.R.layout.simple_spinner_item, RVAdapter.Dataset.values());
        datasetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridDataset.setAdapter(datasetAdapter);

        ArrayAdapter<RVAdapter.ClickAction> actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rvAdapter.currentClickActions);
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridAction.setAdapter(actionAdapter);


        gridDataset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                rvAdapter.setCurrentDataset(i);
                actionAdapter.notifyDataSetChanged();
                gridAction.setSelection(0);
                rvAdapter.setCurrentClickAction(0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        gridAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                rvAdapter.setCurrentClickAction(adapterView.getSelectedItemPosition());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    String[] menu = new String[]{"჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻", "", "Instagram.com", "Sync", "Continue Failed Sync", "Random Wallpaper", "Start REST Server", "Test", "New Login", "MainActivity", ImageViewer.simulateLoading ? "Simulating Loading" : "Simulate Loading"};
    void setupMenu(){
        menuAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menu);
        drawerLV.setAdapter(menuAdapter);
        drawerLV.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                switch (i) {
                    case 1:
                        biometricAuth.authenticate();
                        break;
                    case 2:
                        wvHolder.setVisibility(View.VISIBLE);
                        drawerLayout.close();
                        Log.d(TAG, "onItemClick: " + InstaClient.username);
                        InstaWebView.setInstaCookie(InstaClient.getUserProperty(InstaClient.username, "cookie"));
                        wv.login();
                        break;
                    case 3:
                        instaClient.act_getSavedPosts();
                        break;
                    case 4:
                        instaClient.act_continueLastSync();
                        break;
                    case 5:
                        instaClient.act_setRandomWallpaper();
                        break;
                    case 6:
                        new RESTServer(instaClient).startListening();
                        break;
                    case 7:
                        break;
                    case 8:
                        CookieManager.getInstance().removeAllCookies(null);
                        CookieManager.getInstance().flush();
                        wvHolder.setVisibility(View.VISIBLE);
                        drawerLayout.close();
                        wv.loadUrl("https://www.instagram.com");
                        break;
                    case 10:
                        if (ImageViewer.simulateLoading) {
                            ImageViewer.simulateLoading = false;
                            menu[10] = "Simulate Loading";
                        } else {
                            ImageViewer.simulateLoading = true;
                            menu[10] = "Simulating Loading";
                        }
                        menuAdapter.notifyDataSetChanged();
                        break;
                }
            }
        });
    }

    void setupBiometric(){
        biometricAuth = new BiometricAuth(this, () -> {
            CookieManager.getInstance().removeAllCookies(null);
            String user = InstaClient.getNextUser();
            String cookie = InstaClient.getUserProperty(user, "cookie");
//            Log.d(TAG, "cookie set: " + cookie);
            InstaWebView.setInstaCookie(cookie);
//            wv.loadUrl("https://www.instagram.com/" + user + "/saved/");

            InstaClient.switchToUser(user);

            handler.post(() -> {
                wv.loadUrl("about:blank");
                menu[1] = user;
                menuAdapter.notifyDataSetChanged();
                rvAdapter.setCurrentDataset(0);
                rvAdapter.notifyDataSetChanged();
            });
        });

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);
        try {
            instaClient = InstaClient.getInstance(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        bindService(new Intent(this, MainService.class), serviceConnection, Context.BIND_AUTO_CREATE);

        sharedPreferences = getSharedPreferences(GLOBAL_SHARED_PREF, MODE_PRIVATE);
        spEditor = sharedPreferences.edit();

        handler = new Handler(Looper.getMainLooper());

        initializeViews();
        setupRVAdapter();
        setupSpinners();
        setupMenu();
        setupBiometric();
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

    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "ViewActivity connected to MainService");
            MainService.MainBinder binder = (MainService.MainBinder) iBinder;
            mainService = binder.getService();

            menu[1] = InstaClient.username;
            menuAdapter.notifyDataSetChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "ViewActivity disconnected from MainService");
        }
    };

    public void showImageViewer() {
        imageViewer.setVisibility(View.VISIBLE);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wvHolder.getVisibility() == View.VISIBLE) {
            if (wv.canGoBack()) wv.goBack();
            else wvHolder.setVisibility(View.INVISIBLE);
        } else if (rvAdapter.selected.size() > 0) {
            rvAdapter.clearSelection();
        } else if (imageViewer.getVisibility() == View.VISIBLE) {
            imageViewer.setVisibility(View.INVISIBLE);
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        } else {
            super.onBackPressed();
        }
    }
}