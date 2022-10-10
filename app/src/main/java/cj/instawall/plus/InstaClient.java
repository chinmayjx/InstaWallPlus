package cj.instawall.plus;

import android.content.Context;
import android.util.Log;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InstaClient {
    final String TAG = "CJ";
    Map<String, String> headers;
    ExecutorService executor;
    String username;
    String sessionID;

    public InstaClient(Map<String, String> headers, String username) {
        this.headers = headers;
        this.username = username;
        this.executor = Executors.newCachedThreadPool();
        this.sessionID = getSessionID(headers.get("cookie"));
    }

    public static final int GET_USER_INFO = 1;
    public static final int GET_SAVED_POSTS = 2;

    public void act(int code) {
        switch (code) {
            case GET_USER_INFO:
                executor.execute(this::getUserInfo);
                break;
            case GET_SAVED_POSTS:
                executor.execute(this::getSavedPosts);
                break;
        }
    }

    String getSessionID(String cookie) {
        Matcher m = Pattern.compile("sessionid=(\\d*)").matcher(cookie);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    HttpURLConnection getConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        for (String h : headers.keySet()) con.setRequestProperty(h, headers.get(h));
        return con;
    }

    JSONObject getJSONResponse(HttpURLConnection con) throws IOException, JSONException {
        return new JSONObject(new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n")));
    }

    public JSONObject getUserInfo() {
        try {
            Log.d(TAG, "Getting user info");
            HttpURLConnection con = getConnection("https://i.instagram.com/api/v1/users/web_profile_info/?username=" + username);
            JSONObject res = getJSONResponse(con);
            return res;
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return null;
    }

    public JSONArray getSavedPosts() {
        try {
            JSONObject userInfo = getUserInfo();
            JSONObject savedMedia = userInfo.getJSONObject("data")
                    .getJSONObject("user")
                    .getJSONObject("edge_saved_media");

            Log.d(TAG, "User has " + savedMedia.getInt("count") + " saved posts");
            JSONArray edges = new JSONArray();
            getAllSavedPosts(edges, savedMedia);
            Log.d(TAG, "Saved posts fetch complete: " + edges.length() + " posts");
//            Log.d(TAG, edges.toString());
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return null;
    }

    public void getAllSavedPosts(JSONArray edges, JSONObject savedMedia) {
        try {
            JSONArray newEdges = savedMedia.optJSONArray("edges");
            if (newEdges != null) {
                for (int i = 0; i < newEdges.length(); i++) {
                    edges.put(newEdges.get(i));
                }
            }
            Log.d(TAG, "Got " + edges.length() + " posts");
            JSONObject pageInfo = savedMedia.getJSONObject("page_info");
            Log.d(TAG, newEdges.length() + " : " + pageInfo.toString(2));
            if (pageInfo.getBoolean("has_next_page")) {
                String cursor = pageInfo.getString("end_cursor");
                HttpURLConnection con = getConnection("https://www.instagram.com/graphql/query/?query_hash=2ce1d673055b99250e93b6f88f878fde&variables=" +
                        URLEncoder.encode("{\"id\":\"" + sessionID + "\",\"first\":100,\"after\":\"" + cursor + "\"}", StandardCharsets.UTF_8.toString()));
                JSONObject res = getJSONResponse(con);
                savedMedia = res.getJSONObject("data")
                        .getJSONObject("user")
                        .getJSONObject("edge_saved_media");
                getAllSavedPosts(edges, savedMedia);
            }
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }
}
