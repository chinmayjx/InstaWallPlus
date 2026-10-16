package cj.instawall.plus;

import android.content.ComponentName;
import java.util.Locale;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.appbar.MaterialToolbar;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.view.MenuItem;
import android.view.Menu;
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
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class ViewActivity extends AppCompatActivity implements InstaClient.SyncListener {
    public static final String TAG = "CJ";
    public static final String GLOBAL_SHARED_PREF = "cj_pref_12345";
    // the grid's datasets that have something to show, in chip order
    static final RVAdapter.Dataset[] CHIP_DATASETS = {RVAdapter.Dataset.Random, RVAdapter.Dataset.Downloaded, RVAdapter.Dataset.Recent_wallpapers, RVAdapter.Dataset.Trash};
    InstaClient instaClient;
    RecyclerView rv;
    Spinner accountSwitcher;
    ChipGroup datasetChips;
    RVAdapter rvAdapter;
    ImageViewer imageViewer;
    DrawerLayout drawerLayout;
    MaterialToolbar toolbar;
    NavigationView navView;
    TextView headerStats;
    FloatingActionButton fab;
    int displayWidth;

    MainService mainService;
    String interceptor;
    BiometricAuth biometricAuth;
    Handler handler;
    View wvHolder;
    MaterialToolbar wvToolbar;
    View syncBanner;
    SwipeRefreshLayout swipeRefresh;
    TextView syncStatus;
    // set while logging into another account, the browser closes once that worked
    boolean addingAccount;
    InstaWebView wv;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;
    ArrayList<String> loggedInUsers;
    int currentUserIndex;

    void initializeViews() {
        readScripts();

        imageViewer = findViewById(R.id.imgViewer);
        drawerLayout = findViewById(R.id.drawer_layout);
        toolbar = findViewById(R.id.toolbar);
        // swiping in from the edge is the system back gesture, so the menu needs a button
        toolbar.setNavigationOnClickListener(v -> drawerLayout.open());
        toolbar.setOnMenuItemClickListener(this::onToolbarItem);
        navView = findViewById(R.id.nav_view);
        headerStats = navView.getHeaderView(0).findViewById(R.id.header_stats);
        accountSwitcher = navView.getHeaderView(0).findViewById(R.id.accountSwitcher);
        fab = findViewById(R.id.grid_fab);
        rv = findViewById(R.id.recycler_grid);
        wvHolder = findViewById(R.id.wvHolder);
        wvToolbar = findViewById(R.id.wv_toolbar);
        wvToolbar.setNavigationOnClickListener(v -> hideWebView());
        syncBanner = findViewById(R.id.sync_banner);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setColorSchemeResources(R.color.iw_accent);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.iw_surface_high);
        swipeRefresh.setOnRefreshListener(this::onPullToRefresh);
        syncStatus = findViewById(R.id.sync_status);
        datasetChips = findViewById(R.id.dataset_chips);

        wv = new InstaWebView(this, this::onLoggedIn, interceptor);
        ((FrameLayout) findViewById(R.id.wv_container)).addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void onLoggedIn() {
        boolean firstLogin = instaClient == null;
        if (mainService != null) mainService.createInstaClient();
        Toast.makeText(this, "Logged in as " + InstaClient.username, Toast.LENGTH_SHORT).show();
        // the grid and menu were set up without a client, start over with one
        if (firstLogin) {
            recreate();
            return;
        }
        // another account: show it everywhere without a restart
        setupAccountSwitcher();
        updateHeader();
        selectDataset(RVAdapter.Dataset.Random);
        rvAdapter.setCurrentDataset(RVAdapter.Dataset.Random.ordinal());
        if (addingAccount) {
            addingAccount = false;
            hideWebView();
        }
    }

    void setupRVAdapter() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        displayWidth = displayMetrics.widthPixels;
        rvAdapter = new RVAdapter(instaClient, this);

        rvAdapter.onEnterSelected = () -> fab.setVisibility(View.VISIBLE);
        rvAdapter.onExitSelected = () -> fab.setVisibility(View.GONE);

        rv.setAdapter(rvAdapter);
        rv.setLayoutManager(new GridLayoutManager(this, Math.max(2, displayWidth / 500)));
    }

    void setupDatasetChips() {
        for (int i = 0; i < CHIP_DATASETS.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.dataset_chip, datasetChips, false);
            chip.setId(View.generateViewId());
            chip.setText(CHIP_DATASETS[i].toString());
            chip.setTag(CHIP_DATASETS[i]);
            datasetChips.addView(chip);
        }
        datasetChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            RVAdapter.Dataset d = (RVAdapter.Dataset) group.findViewById(checkedIds.get(0)).getTag();
            rvAdapter.setCurrentDataset(d.ordinal());
            setTapAction(0);
        });
        selectDataset(RVAdapter.Dataset.Random);
    }

    void selectDataset(RVAdapter.Dataset dataset) {
        for (int i = 0; i < datasetChips.getChildCount(); i++) {
            View chip = datasetChips.getChildAt(i);
            if (chip.getTag() == dataset) datasetChips.check(chip.getId());
        }
    }

    static int iconFor(RVAdapter.ClickAction action) {
        switch (action) {
            case Select:
                return R.drawable.ic_select;
            case Set_wallpaper:
                return R.drawable.ic_wallpaper;
            case Delete:
            case Permanently_delete:
                return R.drawable.ic_delete;
            case Restore:
                return R.drawable.ic_restore;
            default:
                return R.drawable.ic_view;
        }
    }

    // what tapping a photo does, shown as the toolbar icon
    void setTapAction(int index) {
        rvAdapter.setCurrentClickAction(index);
        MenuItem item = toolbar.getMenu().findItem(R.id.action_tap_mode);
        RVAdapter.ClickAction action = rvAdapter.currentClickAction;
        if (item == null || action == null) return;
        item.setIcon(iconFor(action));
        item.setTitle("On tap: " + action);
    }

    void showTapActionMenu() {
        View anchor = toolbar.findViewById(R.id.action_tap_mode);
        PopupMenu popup = new PopupMenu(this, anchor == null ? toolbar : anchor);
        popup.getMenu().add(Menu.NONE, Menu.NONE, Menu.NONE, "When I tap a photo").setEnabled(false);
        for (int i = 0; i < rvAdapter.currentClickActions.size(); i++) {
            RVAdapter.ClickAction a = rvAdapter.currentClickActions.get(i);
            popup.getMenu().add(Menu.NONE, i, i + 1, a.toString()).setIcon(iconFor(a))
                    .setCheckable(true).setChecked(a == rvAdapter.currentClickAction);
        }
        popup.setForceShowIcon(true);
        popup.setOnMenuItemClickListener(mi -> {
            setTapAction(mi.getItemId());
            return true;
        });
        popup.show();
    }

    boolean onToolbarItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_random_wallpaper) {
            setRandomWallpaper();
            return true;
        } else if (id == R.id.action_tap_mode) {
            showTapActionMenu();
            return true;
        }
        return false;
    }

    void setRandomWallpaper() {
        if (instaClient == null) return;
        Toast.makeText(this, "Setting a random wallpaper", Toast.LENGTH_SHORT).show();
        instaClient.act_setRandomWallpaper();
    }

    void updateHeader() {
        if (instaClient == null) {
            headerStats.setText("Not logged in");
            return;
        }
        headerStats.setText(String.format(Locale.US, "%,d saved posts", instaClient.getSavedPostIDs().size()));
    }

    void setupAccountSwitcher() {
        loggedInUsers = InstaClient.getLoggedInUsers();
        currentUserIndex = loggedInUsers.indexOf(InstaClient.username);
        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(accountSwitcher.getContext(), android.R.layout.simple_spinner_dropdown_item, loggedInUsers);
        accountSwitcher.setAdapter(accountAdapter);
        accountSwitcher.setSelection(currentUserIndex);
        accountSwitcher.setVisibility(loggedInUsers.isEmpty() ? View.GONE : View.VISIBLE);
        accountSwitcher.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == currentUserIndex) return;
                biometricAuth.authenticate("Switch to " + loggedInUsers.get(i), () -> {
                    String user = loggedInUsers.get(i);
                    Log.d(TAG, "switching account to " + user);
                    InstaClient.switchToUser(user);
                    currentUserIndex = i;

                    CookieManager.getInstance().removeAllCookies(null);
                    String cookie = InstaClient.getUserProperty(user, "cookie");
                    InstaWebView.setInstaCookie(cookie);

                    handler.post(() -> {
                        accountSwitcher.setSelection(i);
                        wv.loadUrl("about:blank");
                        selectDataset(RVAdapter.Dataset.Random);
                        rvAdapter.setCurrentDataset(RVAdapter.Dataset.Random.ordinal());
                        rvAdapter.notifyDataSetChanged();
                        updateHeader();
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

    void setupMenu() {
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_open_instagram) {
                openInstagram();
            } else if (id == R.id.nav_new_login) {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                wv.resetLogin();
                addingAccount = true;
                wvToolbar.setSubtitle("Log in with another account");
                showWebView();
                drawerLayout.close();
                wv.loadUrl("https://www.instagram.com");
            } else if (instaClient == null) {
                Toast.makeText(this, "Log in first", Toast.LENGTH_SHORT).show();
                openInstagram();
            } else if (id == R.id.nav_sync) {
                onSyncProgress("Starting sync");
                instaClient.act_getSavedPosts();
                drawerLayout.close();
            } else if (id == R.id.nav_continue_sync) {
                onSyncProgress("Starting sync");
                instaClient.act_continueLastSync();
                drawerLayout.close();
            } else if (id == R.id.nav_random_wallpaper) {
                setRandomWallpaper();
                drawerLayout.close();
            } else if (id == R.id.nav_rest_server) {
                new RESTServer(instaClient).startListening();
                updateMenuTitles();
            } else if (id == R.id.nav_test) {
                onButtonTest();
            } else if (id == R.id.nav_simulate_loading) {
                ImageViewer.simulateLoading = !ImageViewer.simulateLoading;
                updateMenuTitles();
            }
            return false;
        });
        updateMenuTitles();
    }

    void updateMenuTitles() {
        Menu m = navView.getMenu();
        m.findItem(R.id.nav_rest_server).setTitle(RESTServer.isRunning ? "REST server running" : "Start REST server");
        m.findItem(R.id.nav_simulate_loading).setTitle(ImageViewer.simulateLoading ? "Simulating loading" : "Simulate loading");
    }

    void openInstagram() {
        boolean loggedIn = instaClient != null && InstaClient.username != null && !InstaClient.username.isEmpty();
        wvToolbar.setSubtitle(loggedIn ? "Logged in as @" + InstaClient.username : "Log in to continue");
        showWebView();
        drawerLayout.close();
        Log.d(TAG, "openInstagram: " + InstaClient.username);
        String cookie = InstaClient.getUserProperty(InstaClient.username, "cookie");
        if (cookie != null)
            InstaWebView.setInstaCookie(cookie);
        wv.login();
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
        setupDatasetChips();
        setupAccountSwitcher();
        setupMenu();
        updateHeader();
        setupBiometric();

        onCreateTest();

        // nothing works without a login, so start there instead of on an empty grid
        if (instaClient == null) openInstagram();
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
        wvHolder.setVisibility(View.VISIBLE);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    void hideWebView() {
        wvHolder.setVisibility(View.INVISIBLE);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    // pull to refresh: sync new saves and reload the grid. The banner shows the sync, so the
    // spinner only stays for the moment it takes to start
    void onPullToRefresh() {
        swipeRefresh.setRefreshing(false);
        if (instaClient == null) {
            openInstagram();
            return;
        }
        rvAdapter.setCurrentDataset(rvAdapter.getCurrentDataset().ordinal());
        if (InstaClient.syncing) return;
        onSyncProgress("Starting sync");
        instaClient.act_getSavedPosts();
    }

    // sync progress ---------------------------------------------
    @Override
    protected void onResume() {
        super.onResume();
        InstaClient.syncListener = this;
        if (InstaClient.syncing) onSyncProgress(InstaClient.syncStatus);
        else syncBanner.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        if (InstaClient.syncListener == this) InstaClient.syncListener = null;
        super.onPause();
    }

    @Override
    public void onSyncProgress(String status) {
        handler.post(() -> {
            syncBanner.setVisibility(View.VISIBLE);
            syncStatus.setText(status == null ? "Syncing saved posts" : status);
            updateHeader();
        });
    }

    @Override
    public void onSyncDone(String message) {
        handler.post(() -> {
            syncBanner.setVisibility(View.GONE);
            updateHeader();
            Snackbar.make(findViewById(R.id.recycler_grid), message, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isOpen()) {
            drawerLayout.close();
        } else if (wv != null && wvHolder.getVisibility() == View.VISIBLE) {
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