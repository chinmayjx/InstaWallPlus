package cj.instawall.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.WebSettings;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import cj.instawall.plus.core.CursorStore;
import cj.instawall.plus.core.Instagram;
import cj.instawall.plus.core.Library;
import cj.instawall.plus.core.Reddit;
import cj.instawall.plus.core.Sleeper;
import cj.instawall.plus.core.Source;
import cj.instawall.plus.core.SyncEngine;

/** App-wide state: accounts, the library of sources, syncing and wallpapers. */
public final class Core {
    static final String TAG = "CJ";
    static final String PREFS = "cj_pref_12345";

    public interface SyncListener {
        void onSyncProgress(String status);

        void onSyncDone(String message);
    }

    private static Core instance;

    public static synchronized Core get(Context context) {
        if (instance == null) instance = new Core(context.getApplicationContext());
        return instance;
    }

    public final Context context;
    public final Accounts accounts;
    public final Library library = new Library();
    public final Path filesDir;
    final SharedPreferences prefs;
    final String userAgent;
    private final Random random = new Random();
    // one at a time for wallpapers and downloads; the grid wants the newest request first
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor gridWork = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LIFOBlockingQueue());
    // syncing is slow on purpose, keep it off the executors that load images
    private final ExecutorService syncWork = Executors.newSingleThreadExecutor();

    static volatile SyncListener syncListener;
    static volatile boolean syncing;
    static volatile String syncStatus;

    private Core(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.filesDir = context.getExternalFilesDir(null).toPath();
        this.accounts = new Accounts(context.getFilesDir().toPath().resolve("auth_info.json"));
        this.userAgent = WebSettings.getDefaultUserAgent(context);
        reload();
    }

    public void logoutReddit(String user) {
        boolean wasCurrent = user.equals(accounts.redditUser());
        accounts.logoutReddit(user);
        if (wasCurrent) {
            String next = accounts.redditUser();
            LoginWebView.useRedditSession(next == null ? null : accounts.redditCookie(next));
        }
        reload();
    }

    public void logoutInstagram(String user) {
        accounts.logoutInstagram(user);
        reload();
    }

    public void setNsfwMode(boolean on) {
        accounts.setNsfwMode(on);
        reload();
    }

    public void setNsfw(String service, String user, boolean nsfw) {
        accounts.setNsfw(service, user, nsfw);
        reload();
    }

    /** a source for any account, used or not (for post counts on the accounts page) */
    public Source sourceFor(String service, String user) {
        if (Reddit.SOURCE_ID.equals(service)) return Source.reddit(filesDir, user, redditHttp());
        return Source.instagram(filesDir, user, instagramHttp(() -> accounts.instagramCookie(user)));
    }

    /**
     * The library holds the accounts of the current mode (NSFW or normal) together: grid,
     * wallpapers, widget and sync use all of them. Rebuild after a login, logout or mode change.
     */
    public synchronized void reload() {
        List<Source> sources = new ArrayList<>();
        for (String[] a : accounts.activeAccounts()) sources.add(sourceFor(a[0], a[1]));
        library.setSources(sources);
    }

    public boolean hasAccounts() {
        return !accounts.allAccounts().isEmpty();
    }

    public boolean loggedIn() {
        return !library.sources().isEmpty();
    }

    // http per service ---------------------------------------------
    AndroidHttp instagramHttp(java.util.function.Supplier<String> cookie) {
        return new AndroidHttp("instagram.com", () -> {
            Map<String, String> h = new HashMap<>();
            String c = cookie.get();
            h.put("Cookie", c);
            String appId = accounts.instagramAppId();
            h.put("X-IG-App-ID", appId == null ? Instagram.DEFAULT_APP_ID : appId);
            h.put("Referer", "https://www.instagram.com/");
            String csrf = AndroidHttp.cookieValue(c, "csrftoken");
            if (csrf != null) h.put("X-CSRFToken", csrf);
            return h;
        }, userAgent, Collections.emptySet());
    }

    // JSON through a WebView with the app's (shared) cookies, images over plain http
    private WebViewHttp redditHttp;

    synchronized WebViewHttp redditHttp() {
        if (redditHttp == null)
            redditHttp = new WebViewHttp(context, new AndroidHttp("reddit.com", Collections::emptyMap, userAgent, Collections.emptySet()));
        return redditHttp;
    }

    /** username of the session in an instagram cookie, or null */
    String instagramUsername(String cookie) {
        try {
            return instagramHttp(() -> cookie).getJSON("https://www.instagram.com/api/v1/accounts/edit/web_form_data/")
                    .getJSONObject("form_data").getString("username");
        } catch (Exception e) {
            Log.e(TAG, "instagramUsername: " + e);
            return null;
        }
    }

    /** username of the WebView's reddit session, or null when logged out. Blocks. */
    String redditUsername() {
        try {
            JSONObject data = redditHttp().getJSON("https://www.reddit.com/api/me.json").optJSONObject("data");
            String name = data == null ? null : data.optString("name", null);
            return name == null || name.isEmpty() ? null : name;
        } catch (Exception e) {
            Log.e(TAG, "redditUsername: " + e);
            return null;
        }
    }

    // sync -----------------------------------------------------------
    private CursorStore cursors() {
        return new CursorStore() {
            @Override
            public String get(String key) {
                return prefs.getString(key, null);
            }

            @Override
            public void put(String key, String cursor) {
                prefs.edit().putString(key, cursor).commit();
            }

            @Override
            public void remove(String key) {
                prefs.edit().remove(key).commit();
            }
        };
    }

    private String cursorKey(Source s) {
        String key = "sync_cursor:" + s.id + ":" + s.account;
        // older versions: one app-wide key, then one per instagram user
        if (Instagram.SOURCE_ID.equals(s.id) && !prefs.contains(key)) {
            for (String old : new String[]{"last_sync_cursor:" + s.account, "last_sync_cursor"}) {
                String c = prefs.getString(old, null);
                if (c != null) {
                    prefs.edit().putString(key, c).remove(old).commit();
                    break;
                }
            }
        }
        return key;
    }

    /** sync every account in use (or just one), one after the other */
    public void sync(Source only, boolean resumeOnly) {
        syncing = true;
        syncStatus = "Starting sync";
        syncWork.execute(() -> {
            List<String> messages = new ArrayList<>();
            boolean swapped = false;
            try {
                for (Source s : only != null ? Collections.singletonList(only) : library.sources()) {
                    boolean reddit = Reddit.SOURCE_ID.equals(s.id);
                    if (reddit) {
                        // the reddit WebView has one session at a time: this account's
                        LoginWebView.useRedditSession(accounts.redditCookie(s.account));
                        swapped = true;
                    }
                    SyncEngine engine = new SyncEngine(s.label(), s.feed, s.posts, cursors(),
                            cursorKey(s), s.policy, Sleeper.REAL, this::reportSync, random);
                    SyncEngine.Result r = resumeOnly ? engine.resume() : engine.sync();
                    Log.d(TAG, "sync " + s.id + " " + s.account + ": " + r.outcome + ", " + r.newPosts + " new");
                    // reddit refreshes session cookies as it goes, keep the stored copy current
                    if (reddit)
                        accounts.updateRedditCookie(s.account, android.webkit.CookieManager.getInstance().getCookie(LoginWebView.REDDIT_URL));
                    messages.add(r.message);
                }
            } catch (Exception e) {
                Log.e(TAG, "sync: " + Log.getStackTraceString(e));
                messages.add("Sync failed: " + e.getMessage());
            } finally {
                if (swapped) {
                    String current = accounts.redditUser();
                    LoginWebView.useRedditSession(current == null ? null : accounts.redditCookie(current));
                }
                syncing = false;
                SyncListener l = syncListener;
                if (l != null) l.onSyncDone(messages.isEmpty() ? "Nothing to sync" : String.join("\n", messages));
            }
        });
    }

    private void reportSync(String status) {
        syncStatus = status;
        SyncListener l = syncListener;
        if (l != null) l.onSyncProgress(status);
    }

    // wallpapers -----------------------------------------------------
    public Path randomImage() throws IOException {
        return library.randomImage(random);
    }

    /** for the grid: newest request first */
    public void randomImageAsync(Consumer<Path> callback) {
        gridWork.execute(() -> {
            try {
                callback.accept(randomImage());
            } catch (Exception e) {
                Log.e(TAG, "randomImageAsync: " + e);
            }
        });
    }

    public void setRandomWallpaper() {
        work.execute(this::setRandomWallpaperNow);
    }

    /** blocking, for callers already in the background */
    public void setRandomWallpaperNow() {
        try {
            setWallpaperNow(randomImage());
        } catch (Exception e) {
            Log.e(TAG, "setRandomWallpaper: " + Log.getStackTraceString(e));
        }
    }

    /** runs after the wallpaper work queued so far */
    public void afterQueuedWork(Runnable r) {
        work.execute(r);
    }

    public void setWallpaper(Path image) {
        work.execute(() -> setWallpaperNow(image));
    }

    private void setWallpaperNow(Path image) {
        if (WallpaperSetter.set(context, image)) logRecent(image);
    }

    /** a shared instagram link: that post as the wallpaper */
    public void setWallpaperFromInstagramCode(String code) {
        work.execute(() -> {
            try {
                Source s = library.source(Instagram.SOURCE_ID);
                if (s == null) throw new IOException("not logged into instagram");
                String id = Instagram.shortcodeToMediaId(code);
                if (!s.posts.has(id)) s.posts.put(id, Instagram.refresh(instagramHttpFor(s), id));
                cj.instawall.plus.core.SavedPost p = s.post(id);
                setWallpaperNow(s.fetch(p, random.nextInt(p.images.size())));
            } catch (Exception e) {
                Log.e(TAG, "setWallpaperFromInstagramCode: " + Log.getStackTraceString(e));
            }
        });
    }

    private AndroidHttp instagramHttpFor(Source s) {
        return instagramHttp(() -> accounts.instagramCookie(s.account));
    }

    // recently used wallpapers, newest last ----------------------------
    private Path recentFile() {
        return filesDir.resolve("recent_wallpapers.txt");
    }

    private void logRecent(Path image) {
        try {
            Files.write(recentFile(), (image + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Log.e(TAG, "logRecent: " + e);
        }
    }

    /** newest first, files that still exist, no duplicates (includes the old per-account lists) */
    public List<Path> recentWallpapers() {
        List<String> lines = new ArrayList<>();
        for (Source s : library.sources()) {
            try {
                lines.addAll(Files.readAllLines(s.root.resolve("recent_wallpapers.txt")));
            } catch (IOException ignored) {
            }
        }
        try {
            lines.addAll(Files.readAllLines(recentFile()));
        } catch (IOException ignored) {
        }
        Collections.reverse(lines);
        Set<Path> seen = new LinkedHashSet<>();
        for (String l : lines) {
            Path p = Paths.get(l.trim());
            if (!l.trim().isEmpty() && Files.exists(p) && library.owner(p) != null) seen.add(p);
        }
        return new ArrayList<>(seen);
    }

    // trash ---------------------------------------------------------------
    public void delete(Path image) {
        Source s = library.owner(image);
        if (s == null) return;
        try {
            s.trash.delete(image);
        } catch (Exception e) {
            Log.e(TAG, "delete: " + e);
        }
    }

    public void restore(Path trashed) {
        Source s = library.owner(trashed);
        if (s == null) return;
        try {
            s.trash.restore(trashed, s.imagesDir());
        } catch (Exception e) {
            Log.e(TAG, "restore: " + e);
        }
    }

    public void deletePermanently(Path trashed) {
        Source s = library.owner(trashed);
        if (s == null) return;
        try {
            s.trash.deletePermanently(trashed);
        } catch (Exception e) {
            Log.e(TAG, "deletePermanently: " + e);
        }
    }
}
