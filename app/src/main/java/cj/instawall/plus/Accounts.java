package cj.instawall.plus;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Logged in accounts, in files/auth_info.json:
 * {current_user, app_id, user_data: {NAME: {cookie}}, reddit_current, reddit_accounts: {NAME: {cookie}}}.
 * Both services can have several accounts; current_user / reddit_current is the one the login
 * browser uses. Each account entry can carry "nsfw": true, and nsfw_mode picks which are used.
 */
public class Accounts {
    static final String TAG = "CJ";
    private final Path file;
    private JSONObject data;

    /** for tests: no file */
    Accounts(JSONObject data) {
        this.file = null;
        this.data = data;
        try {
            if (!data.has("user_data")) data.put("user_data", new JSONObject());
        } catch (JSONException ignored) {
        }
    }

    public Accounts(Path file) {
        this.file = file;
        try {
            data = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (Exception e) {
            data = new JSONObject();
        }
        try {
            if (!data.has("user_data")) data.put("user_data", new JSONObject());
        } catch (JSONException ignored) {
        }
    }

    private synchronized void save() {
        if (file == null) return;
        try {
            Files.write(file, data.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Accounts.save: " + Log.getStackTraceString(e));
        }
    }

    // NSFW mode -------------------------------------------------------
    // every account is marked NSFW or not (an "nsfw" flag on its entry); nsfw_mode picks which
    // kind the app uses. All accounts of that kind are used together.

    public synchronized boolean nsfwMode() {
        return data.optBoolean("nsfw_mode", false);
    }

    public synchronized void setNsfwMode(boolean on) {
        try {
            data.put("nsfw_mode", on);
            save();
        } catch (JSONException ignored) {
        }
    }

    private JSONObject entry(String service, String user) {
        JSONObject all = "reddit".equals(service) ? redditAccounts() : data.optJSONObject("user_data");
        return all == null ? null : all.optJSONObject(user);
    }

    public synchronized boolean isNsfw(String service, String user) {
        JSONObject e = entry(service, user);
        return e != null && e.optBoolean("nsfw", false);
    }

    /** whether the account was ever marked (new accounts get asked) */
    public synchronized boolean isMarked(String service, String user) {
        JSONObject e = entry(service, user);
        return e != null && e.has("nsfw");
    }

    public synchronized void setNsfw(String service, String user, boolean nsfw) {
        JSONObject e = entry(service, user);
        if (e == null) return;
        try {
            e.put("nsfw", nsfw);
            save();
        } catch (JSONException ignored) {
        }
    }

    /** every account as {service, user}, instagram first */
    public synchronized List<String[]> allAccounts() {
        List<String[]> l = new ArrayList<>();
        for (String u : instagramUsers()) if (instagramCookie(u) != null) l.add(new String[]{"instagram", u});
        for (String u : redditUsers()) l.add(new String[]{"reddit", u});
        return l;
    }

    /** the accounts the app uses: the NSFW ones in NSFW mode, the others otherwise */
    public synchronized List<String[]> activeAccounts() {
        boolean mode = nsfwMode();
        List<String[]> l = new ArrayList<>();
        for (String[] a : allAccounts()) if (isNsfw(a[0], a[1]) == mode) l.add(a);
        return l;
    }

    // instagram ---------------------------------------------------
    public synchronized String instagramUser() {
        String u = data.optString("current_user", "");
        return u.isEmpty() || instagramCookie(u) == null ? null : u;
    }

    public synchronized String instagramCookie(String user) {
        JSONObject u = data.optJSONObject("user_data") == null ? null : data.optJSONObject("user_data").optJSONObject(user);
        return u == null ? null : u.optString("cookie", null);
    }

    public synchronized String instagramAppId() {
        String id = data.optString("app_id", "");
        return id.isEmpty() ? null : id;
    }

    public synchronized List<String> instagramUsers() {
        List<String> l = new ArrayList<>();
        JSONArray names = data.optJSONObject("user_data").names();
        if (names != null) for (int i = 0; i < names.length(); i++) l.add(names.optString(i));
        return l;
    }

    public synchronized void loginInstagram(String user, String cookie, String appId) {
        try {
            JSONObject users = data.getJSONObject("user_data");
            if (!users.has(user)) users.put(user, new JSONObject());
            users.getJSONObject(user).put("cookie", cookie);
            data.put("current_user", user);
            if (appId != null) data.put("app_id", appId);
            save();
        } catch (JSONException e) {
            Log.e(TAG, "loginInstagram: " + e);
        }
    }

    /** the account the instagram browser logs in with */
    public synchronized void switchInstagram(String user) {
        try {
            data.put("current_user", user);
            save();
        } catch (JSONException ignored) {
        }
    }

    public synchronized void logoutInstagram(String user) {
        data.optJSONObject("user_data").remove(user);
        if (user.equals(data.optString("current_user"))) {
            List<String> left = instagramUsers();
            try {
                data.put("current_user", left.isEmpty() ? "" : left.get(0));
            } catch (JSONException ignored) {
            }
        }
        save();
    }

    // reddit ------------------------------------------------------
    // reddit_accounts: {NAME: {cookie}}, reddit_current: NAME (one active, like instagram)

    private JSONObject redditAccounts() {
        try {
            // older versions kept one account in "reddit": {username, cookie}
            JSONObject legacy = data.optJSONObject("reddit");
            if (!data.has("reddit_accounts")) data.put("reddit_accounts", new JSONObject());
            if (legacy != null) {
                String name = legacy.optString("username", "");
                if (!name.isEmpty()) {
                    data.getJSONObject("reddit_accounts").put(name, new JSONObject().put("cookie", legacy.optString("cookie")));
                    if (!data.has("reddit_current")) data.put("reddit_current", name);
                }
                data.remove("reddit");
                save();
            }
            return data.getJSONObject("reddit_accounts");
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public synchronized String redditUser() {
        JSONObject accounts = redditAccounts(); // migrates the old format first
        String u = data.optString("reddit_current", "");
        return u.isEmpty() || !accounts.has(u) ? null : u;
    }

    public synchronized List<String> redditUsers() {
        List<String> l = new ArrayList<>();
        JSONArray names = redditAccounts().names();
        if (names != null) for (int i = 0; i < names.length(); i++) l.add(names.optString(i));
        return l;
    }

    public synchronized String redditCookie(String user) {
        JSONObject a = redditAccounts().optJSONObject(user);
        return a == null ? null : a.optString("cookie", null);
    }

    /** adds or updates the account (keeping its NSFW mark) and makes it the browser's session */
    public synchronized void loginReddit(String user, String cookie) {
        try {
            JSONObject all = redditAccounts();
            if (!all.has(user)) all.put(user, new JSONObject());
            all.getJSONObject(user).put("cookie", cookie);
            data.put("reddit_current", user);
            save();
        } catch (JSONException ignored) {
        }
    }

    /** reddit refreshes its session cookies; keep the stored copy current */
    public synchronized void updateRedditCookie(String user, String cookie) {
        JSONObject a = redditAccounts().optJSONObject(user);
        if (a == null || cookie == null || cookie.equals(a.optString("cookie"))) return;
        try {
            a.put("cookie", cookie);
            save();
        } catch (JSONException ignored) {
        }
    }

    public synchronized void switchReddit(String user) {
        if (!redditAccounts().has(user)) return;
        try {
            data.put("reddit_current", user);
            save();
        } catch (JSONException ignored) {
        }
    }

    /** removes the account; another one (if any) becomes active */
    public synchronized void logoutReddit(String user) {
        redditAccounts().remove(user);
        if (user.equals(data.optString("reddit_current"))) {
            List<String> left = redditUsers();
            data.remove("reddit_current");
            if (!left.isEmpty()) {
                try {
                    data.put("reddit_current", left.get(0));
                } catch (JSONException ignored) {
                }
            }
        }
        save();
    }
}
