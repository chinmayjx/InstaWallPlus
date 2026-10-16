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
 * {current_user, app_id, user_data: {NAME: {cookie}}, reddit: {username, cookie}}.
 * Instagram can have several accounts (one active), reddit one.
 */
public class Accounts {
    static final String TAG = "CJ";
    private final Path file;
    private JSONObject data;

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
        try {
            Files.write(file, data.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Accounts.save: " + Log.getStackTraceString(e));
        }
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

    public synchronized void switchInstagram(String user) {
        try {
            data.put("current_user", user);
            save();
        } catch (JSONException ignored) {
        }
    }

    // reddit ------------------------------------------------------
    public synchronized String redditUser() {
        JSONObject r = data.optJSONObject("reddit");
        return r == null ? null : r.optString("username", null);
    }

    public synchronized String redditCookie() {
        JSONObject r = data.optJSONObject("reddit");
        return r == null ? null : r.optString("cookie", null);
    }

    public synchronized void loginReddit(String user, String cookie) {
        try {
            data.put("reddit", new JSONObject().put("username", user).put("cookie", cookie));
            save();
        } catch (JSONException ignored) {
        }
    }

    public synchronized void logoutReddit() {
        data.remove("reddit");
        save();
    }
}
