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
    AccountsPage accountsPage;

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
        setupMenu();
        updateHeader();

        // nothing works without an account, so start there instead of on an empty grid
        if (!core.hasAccounts()) accountsPage.open();
    }

    void initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        toolbar = findViewById(R.id.toolbar);
        // swiping in from the edge is the system back gesture, so the menu needs a button
        toolbar.setNavigationOnClickListener(v -> drawerLayout.open());
        toolbar.setOnMenuItemClickListener(this::onToolbarItem);
        navView = findViewById(R.id.nav_view);
        headerStats = navView.getHeaderView(0).findViewById(R.id.header_stats);
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

        accountsPage = new AccountsPage(this, core);
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
            noAccounts();
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
        boolean nsfw = core.accounts.nsfwMode();
        toolbar.setSubtitle(nsfw ? "NSFW mode" : null);
        List<Source> used = core.library.sources();
        if (used.isEmpty()) {
            headerStats.setText(core.hasAccounts() ? (nsfw ? "NSFW mode · no NSFW accounts" : "No accounts in normal mode") : "Not logged in");
            return;
        }
        int posts = 0;
        for (Source s : used) posts += s.posts.size();
        headerStats.setText(String.format(Locale.US, "%s · %d account%s · %,d saved posts",
                nsfw ? "NSFW mode" : "Normal mode", used.size(), used.size() == 1 ? "" : "s", posts));
    }

    /** nothing to show in this mode: off to the accounts page */
    void noAccounts() {
        Toast.makeText(this, core.hasAccounts() ? "No accounts in this mode" : "Log in first", Toast.LENGTH_SHORT).show();
        accountsPage.open();
    }

    // NSFW mode: turning it on needs the fingerprint, turning it off is instant (hide it fast)
    void toggleNsfwMode() {
        if (core.accounts.nsfwMode()) {
            setNsfwMode(false);
        } else {
            biometricAuth.authenticate("Turn on NSFW mode", () -> handler.post(() -> setNsfwMode(true)));
        }
    }

    void setNsfwMode(boolean on) {
        core.setNsfwMode(on);
        if (accountsPage.isOpen()) accountsPage.refresh();
        reloadGrid();
    }

    void setupMenu() {
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_accounts) {
                drawerLayout.close();
                accountsPage.open();
            } else if (id == R.id.nav_nsfw) {
                toggleNsfwMode();
            } else if (!core.loggedIn()) {
                drawerLayout.close();
                noAccounts();
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

    void updateMenu() {
        Menu m = navView.getMenu();
        MenuItem nsfw = m.findItem(R.id.nav_nsfw);
        View sw = nsfw.getActionView();
        if (sw instanceof com.google.android.material.materialswitch.MaterialSwitch) {
            com.google.android.material.materialswitch.MaterialSwitch s = (com.google.android.material.materialswitch.MaterialSwitch) sw;
            s.setChecked(core.accounts.nsfwMode());
            // taps go to the menu item, which asks for the fingerprint first
            s.setClickable(false);
            s.setFocusable(false);
        }
        m.findItem(R.id.nav_rest_server).setTitle(RESTServer.isRunning ? "REST server running" : "Start REST server");
    }

    // logging in -----------------------------------------------------
    void addInstagramAccount() {
        showWebView("Instagram", "Log in to add an account");
        wv.addInstagramAccount();
    }

    void addRedditAccount() {
        showWebView("Reddit", "Log in to add an account");
        wv.addRedditAccount();
    }

    /** the service's site with this account's session (logs it in again if it expired) */
    void openAccount(String service, String user) {
        if (Reddit.SOURCE_ID.equals(service)) {
            core.accounts.switchReddit(user);
            showWebView("Reddit", "u/" + user);
            wv.openReddit();
        } else {
            core.accounts.switchInstagram(user);
            showWebView("Instagram", "@" + user);
            wv.openInstagram();
        }
    }

    @Override
    public void onLoggedIn(String sourceId, String username) {
        hideWebView();
        String label = AccountsPage.label(sourceId, username);
        if (core.accounts.isMarked(sourceId, username)) {
            // logged in again (an expired session): keeps its mark
            Toast.makeText(this, "Logged in as " + label, Toast.LENGTH_SHORT).show();
            afterLogin(sourceId, username);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logged in as " + label)
                .setMessage("Is this an NSFW account? NSFW accounts are only used in NSFW mode.")
                .setCancelable(false)
                .setNegativeButton("Normal", (d, w) -> {
                    core.setNsfw(sourceId, username, false);
                    afterLogin(sourceId, username);
                })
                .setPositiveButton("NSFW", (d, w) -> {
                    core.setNsfw(sourceId, username, true);
                    afterLogin(sourceId, username);
                })
                .show();
    }

    private void afterLogin(String sourceId, String username) {
        if (accountsPage.isOpen()) accountsPage.refresh();
        reloadGrid();
        Source s = core.library.source(sourceId, username);
        if (s == null) {
            Snackbar.make(rv, AccountsPage.label(sourceId, username) + " is used in " + (core.accounts.nsfwMode() ? "normal" : "NSFW") + " mode", Snackbar.LENGTH_LONG).show();
            return;
        }
        // a new account has nothing yet, fetch its saves
        if (s.posts.size() == 0 && !Core.syncing) startSync(s, false);
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

    void onOverlayShown(boolean shown) {
        onGalleryShown(shown);
    }

    void onGalleryShown(boolean shown) {
        drawerLayout.setDrawerLockMode(shown ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    // sync progress ---------------------------------------------
    void startSync(boolean resumeOnly) {
        startSync(null, resumeOnly);
    }

    void startSync(Source only, boolean resumeOnly) {
        onSyncProgress("Starting sync");
        core.sync(only, resumeOnly);
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
        } else if (accountsPage.isOpen()) {
            accountsPage.close();
        } else if (rvAdapter.selected.size() > 0) {
            rvAdapter.clearSelection();
        } else {
            super.onBackPressed();
        }
    }
}
