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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class ViewActivity extends AppCompatActivity {
    public static final String TAG = "CJ";
    public static final String GLOBAL_SHARED_PREF = "cj_pref_12345";
    InstaClient instaClient;
    RecyclerView rv;
    Spinner gridDataset, gridAction, accountSwitcher;
    RVAdapter rvAdapter;
    ImageViewer imageViewer;
    DrawerLayout drawerLayout;
    ListView drawerLV;
    FloatingActionButton fab;
    int displayWidth;

    ArrayAdapter<MenuItems> menuAdapter;
    MainService mainService;
    String interceptor;
    BiometricAuth biometricAuth;
    Handler handler;
    FrameLayout wvHolder;
    InstaWebView wv;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;
    ArrayList<String> loggedInUsers;
    int currentUserIndex;

    void initializeViews() {
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
        accountSwitcher = findViewById(R.id.accountSwitcher);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (wvHolder.getVisibility() == View.VISIBLE) hideWebView();
            }
        });

        wv = new InstaWebView(this, () -> mainService.createInstaClient(), interceptor);
        wvHolder.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void setupRVAdapter() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        displayWidth = displayMetrics.widthPixels;
        rvAdapter = new RVAdapter(instaClient, this);

        rvAdapter.onEnterSelected = () -> fab.setVisibility(View.VISIBLE);
        rvAdapter.onExitSelected = () -> fab.setVisibility(View.GONE);

        rv.setAdapter(rvAdapter);
        rv.setLayoutManager(new GridLayoutManager(this, displayWidth / 500));
    }

    void setupSpinners() {
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

        loggedInUsers = InstaClient.getLoggedInUsers();
        currentUserIndex = loggedInUsers.indexOf(InstaClient.username);
        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, loggedInUsers);
        accountSwitcher.setAdapter(accountAdapter);
        accountSwitcher.setSelection(currentUserIndex);
        accountSwitcher.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == currentUserIndex) return;
                biometricAuth.authenticate("Switch to " + loggedInUsers.get(i), () -> {
                    String user = loggedInUsers.get(i);
                    InstaClient.switchToUser(user);
                    currentUserIndex = i;

                    CookieManager.getInstance().removeAllCookies(null);
                    String cookie = InstaClient.getUserProperty(user, "cookie");
                    InstaWebView.setInstaCookie(cookie);

                    handler.post(() -> {
                        wv.loadUrl("about:blank");
                        rvAdapter.setCurrentDataset(0);
                        gridDataset.setSelection(0);
                        rvAdapter.notifyDataSetChanged();
                    });
                }, () -> {
                    handler.post(() -> {
                        accountSwitcher.setSelection(currentUserIndex);
                    });
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    enum MenuItems {
        Design {
            @NonNull
            @Override
            public String toString() {
                return "჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻჻";
            }
        },
        Open_Instagram,
        Synchronize,
        Continue_Failed_Sync,
        Random_Wallpaper,
        Start_REST_Server {
            @NonNull
            @Override
            public String toString() {
                return RESTServer.isRunning ? "REST Server Running" : "Start REST Server";
            }
        },
        Test,
        New_Login,
        Simulate_Loading {
            @NonNull
            @Override
            public String toString() {
                return ImageViewer.simulateLoading ? "Simulating Loading" : "Simulate Loading";
            }
        };

        @NonNull
        @Override
        public String toString() {
            return this.name().replace('_', ' ');
        }
    }

    void setupMenu() {
        menuAdapter = new ArrayAdapter<MenuItems>(this, android.R.layout.simple_list_item_1, MenuItems.values());
        drawerLV.setAdapter(menuAdapter);
        drawerLV.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                MenuItems c = MenuItems.values()[i];
                switch (c) {
                    case Open_Instagram:
                        showWebView();
                        drawerLayout.close();
                        Log.d(TAG, "onItemClick: " + InstaClient.username);
                        String cookie = InstaClient.getUserProperty(InstaClient.username, "cookie");
                        if (cookie != null)
                            InstaWebView.setInstaCookie(cookie);
                        wv.login();
                        break;
                    case Synchronize:
                        instaClient.act_getSavedPosts();
                        break;
                    case Continue_Failed_Sync:
                        instaClient.act_continueLastSync();
                        break;
                    case Random_Wallpaper:
                        instaClient.act_setRandomWallpaper();
                        break;
                    case Start_REST_Server:
                        new RESTServer(instaClient).startListening();
                        menuAdapter.notifyDataSetChanged();
                        break;
                    case Test:
                        onButtonTest();
                        break;
                    case New_Login:
                        CookieManager.getInstance().removeAllCookies(null);
                        CookieManager.getInstance().flush();
                        showWebView();
                        drawerLayout.close();
                        wv.loadUrl("https://www.instagram.com");
                        break;
                    case Simulate_Loading:
                        ImageViewer.simulateLoading = !ImageViewer.simulateLoading;
                        menuAdapter.notifyDataSetChanged();
                        break;
                }
            }
        });
    }

    void setupBiometric() {
        biometricAuth = new BiometricAuth(this);
    }

    void onCreateTest(){
        if (instaClient != null)
            instaClient.act_test();
    }

    void onButtonTest(){
        instaClient.act_test();
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

        onCreateTest();
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

    void showWebView() {
        fab.setImageDrawable(AppCompatResources.getDrawable(this, android.R.drawable.ic_menu_close_clear_cancel));
        wvHolder.setVisibility(View.VISIBLE);
        fab.setVisibility(View.VISIBLE);
    }

    void hideWebView() {
        fab.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.check));
        wvHolder.setVisibility(View.INVISIBLE);
        fab.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wvHolder.getVisibility() == View.VISIBLE) {
            if (wv.canGoBack()) wv.goBack();
            else hideWebView();
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