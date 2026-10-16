package cj.instawall.plus;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cj.instawall.plus.core.Instagram;
import cj.instawall.plus.core.Reddit;
import cj.instawall.plus.core.Source;

public class ViewActivity extends AppCompatActivity implements Core.SyncListener, LoginWebView.Listener {
    public static final String TAG = "CJ";
    // the grid's datasets, in chip order
    static final RVAdapter.Dataset[] CHIP_DATASETS = {RVAdapter.Dataset.Random, RVAdapter.Dataset.Downloaded, RVAdapter.Dataset.Recent_wallpapers, RVAdapter.Dataset.Trash};

    Core core;
    RecyclerView rv;
    Spinner accountSwitcher;
    ChipGroup datasetChips;
    RVAdapter rvAdapter;
    Gallery gallery;
    DrawerLayout drawerLayout;
    MaterialToolbar toolbar;
    NavigationView navView;
    TextView headerStats;
    FloatingActionButton fab;
    BiometricAuth biometricAuth;
    Handler handler;
    View wvHolder;
    MaterialToolbar wvToolbar;
    View syncBanner;
    SwipeRefreshLayout swipeRefresh;
    TextView syncStatus;
    LoginWebView wv;
    // every account of every service; one is the active data source
    List<String[]> accounts = new ArrayList<>();
    int currentAccountIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);
        core = Core.get(this);
        handler = new Handler(Looper.getMainLooper());
        biometricAuth = new BiometricAuth(this);

        initializeViews();
        setupGrid();
        setupDatasetChips();
        setupAccountSwitcher();
        setupMenu();
        updateHeader();

        // nothing works without a login, so start there instead of on an empty grid
        if (!core.loggedIn()) openInstagram();
    }

    void initializeViews() {
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
        syncStatus = findViewById(R.id.sync_status);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setColorSchemeResources(R.color.iw_accent);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.iw_surface_high);
        swipeRefresh.setOnRefreshListener(this::onPullToRefresh);
        datasetChips = findViewById(R.id.dataset_chips);

        wv = new LoginWebView(this, core, this);
        ((FrameLayout) findViewById(R.id.wv_container)).addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // grid ---------------------------------------------------------
    void setupGrid() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        rvAdapter = new RVAdapter(core, this);
        rvAdapter.onEnterSelected = () -> fab.setVisibility(View.VISIBLE);
        rvAdapter.onExitSelected = () -> fab.setVisibility(View.GONE);
        rv.setAdapter(rvAdapter);
        rv.setLayoutManager(new GridLayoutManager(this, Math.max(2, displayMetrics.widthPixels / 500)));
        gallery = new Gallery(this, core, rvAdapter);
    }

    void setupDatasetChips() {
        for (RVAdapter.Dataset d : CHIP_DATASETS) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.dataset_chip, datasetChips, false);
            chip.setId(View.generateViewId());
            chip.setText(d.toString());
            chip.setTag(d);
            datasetChips.addView(chip);
        }
        datasetChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            rvAdapter.setCurrentDataset((RVAdapter.Dataset) group.findViewById(checkedIds.get(0)).getTag());
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

    /** after logins, account switches and source toggles */
    void reloadGrid() {
        selectDataset(RVAdapter.Dataset.Random);
        rvAdapter.setCurrentDataset(RVAdapter.Dataset.Random);
        updateHeader();
        updateMenu();
    }

    // pull to refresh: sync new saves and reload the grid. The banner shows the sync, so the
    // spinner only stays for the moment it takes to start
    void onPullToRefresh() {
        swipeRefresh.setRefreshing(false);
        if (!core.loggedIn()) {
            openInstagram();
            return;
        }
        rvAdapter.setCurrentDataset(rvAdapter.getCurrentDataset());
        if (!Core.syncing) startSync(false);
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
        if (!core.loggedIn()) return;
        Toast.makeText(this, "Setting a random wallpaper", Toast.LENGTH_SHORT).show();
        core.setRandomWallpaper();
    }

    // side menu ----------------------------------------------------
    void updateHeader() {
        Source active = core.active();
        if (active == null) {
            headerStats.setText("Not logged in");
            return;
        }
        headerStats.setText(String.format(Locale.US, "%,d saved posts", active.posts.size()));
    }

    static String label(String[] account) {
        return (Reddit.SOURCE_ID.equals(account[0]) ? "u/" : "@") + account[1];
    }

    // the header's dropdown picks the one data source, from all accounts of all services
    void setupAccountSwitcher() {
        accounts = core.accounts.allAccounts();
        currentAccountIndex = -1;
        String service = core.accounts.activeService(), user = core.accounts.activeUser();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < accounts.size(); i++) {
            String[] a = accounts.get(i);
            labels.add(label(a));
            if (a[0].equals(service) && a[1].equals(user)) currentAccountIndex = i;
        }
        accountSwitcher.setAdapter(new ArrayAdapter<>(accountSwitcher.getContext(), android.R.layout.simple_spinner_dropdown_item, labels));
        accountSwitcher.setSelection(Math.max(0, currentAccountIndex));
        accountSwitcher.setVisibility(accounts.isEmpty() ? View.GONE : View.VISIBLE);
        accountSwitcher.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == currentAccountIndex) return;
                String[] a = accounts.get(i);
                biometricAuth.authenticate("Switch to " + label(a), () -> {
                    Log.d(TAG, "switching to " + a[0] + " " + a[1]);
                    core.activate(a[0], a[1]);
                    currentAccountIndex = i;
                    handler.post(() -> {
                        accountSwitcher.setSelection(i);
                        wv.loadUrl("about:blank");
                        reloadGrid();
                    });
                }, () -> handler.post(() -> accountSwitcher.setSelection(currentAccountIndex)));
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
                showWebView("Instagram", "Log in with another account");
                wv.addInstagramAccount();
            } else if (id == R.id.nav_reddit) {
                onRedditItem();
            } else if (!core.loggedIn()) {
                Toast.makeText(this, "Log in first", Toast.LENGTH_SHORT).show();
                openInstagram();
            } else if (id == R.id.nav_sync) {
                startSync(false);
                drawerLayout.close();
            } else if (id == R.id.nav_continue_sync) {
                startSync(true);
                drawerLayout.close();
            } else if (id == R.id.nav_random_wallpaper) {
                setRandomWallpaper();
                drawerLayout.close();
            } else if (id == R.id.nav_rest_server) {
                new RESTServer(core).startListening();
                updateMenu();
            }
            return false;
        });
        updateMenu();
    }

    // the reddit item: log in, add another account, open, log out (switching is in the header)
    void onRedditItem() {
        String user = core.accounts.redditUser();
        if (user == null) {
            openReddit();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reddit")
                .setItems(new String[]{"Add another Reddit account", "Open Reddit", "Log out of u/" + user}, (d, which) -> {
                    if (which == 0) {
                        showWebView("Reddit", "Log in with another account");
                        wv.addRedditAccount();
                    } else if (which == 1) {
                        openReddit();
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Log out of u/" + user + "?")
                                .setMessage("Its synced posts stay on the phone and come back if you log in again.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Log out", (d2, w) -> {
                                    core.logoutReddit(user);
                                    setupAccountSwitcher();
                                    reloadGrid();
                                }).show();
                    }
                }).show();
    }

    void updateMenu() {
        Menu m = navView.getMenu();
        String reddit = core.accounts.redditUser();
        m.findItem(R.id.nav_reddit).setTitle(reddit == null ? "Log in to Reddit" : "Reddit accounts");
        m.findItem(R.id.nav_rest_server).setTitle(RESTServer.isRunning ? "REST server running" : "Start REST server");
    }

    // logging in -----------------------------------------------------
    void openInstagram() {
        String user = core.accounts.instagramUser();
        showWebView("Instagram", user != null ? "Logged in as @" + user : "Log in to continue");
        wv.openInstagram();
    }

    void openReddit() {
        String user = core.accounts.redditUser();
        showWebView("Reddit", user != null ? "Logged in as u/" + user : "Log in to add your Reddit saves");
        wv.openReddit();
    }

    @Override
    public void onLoggedIn(String sourceId, String username) {
        Toast.makeText(this, "Logged in as " + (Reddit.SOURCE_ID.equals(sourceId) ? "u/" : "@") + username, Toast.LENGTH_SHORT).show();
        setupAccountSwitcher();
        reloadGrid();
        hideWebView();
        // a new account has nothing yet, fetch its saves
        Source s = core.library.source(sourceId);
        if (s != null && s.posts.size() == 0 && !Core.syncing) startSync(sourceId, false);
    }

    void showWebView(String title, String subtitle) {
        wvToolbar.setTitle(title);
        wvToolbar.setSubtitle(subtitle);
        wvHolder.setVisibility(View.VISIBLE);
        drawerLayout.close();
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    void hideWebView() {
        wv.closed();
        wvHolder.setVisibility(View.INVISIBLE);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    void onGalleryShown(boolean shown) {
        drawerLayout.setDrawerLockMode(shown ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    // sync progress ---------------------------------------------
    void startSync(boolean resumeOnly) {
        startSync(null, resumeOnly);
    }

    void startSync(String sourceId, boolean resumeOnly) {
        onSyncProgress("Starting sync");
        core.sync(sourceId, resumeOnly);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Core.syncListener = this;
        if (Core.syncing) onSyncProgress(Core.syncStatus);
        else syncBanner.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        if (Core.syncListener == this) Core.syncListener = null;
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
            Snackbar.make(rv, message, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isOpen()) {
            drawerLayout.close();
        } else if (wvHolder.getVisibility() == View.VISIBLE) {
            if (wv.canGoBack()) wv.goBack();
            else hideWebView();
        } else if (gallery.isOpen()) {
            gallery.back();
        } else if (rvAdapter.selected.size() > 0) {
            rvAdapter.clearSelection();
        } else {
            super.onBackPressed();
        }
    }
}
