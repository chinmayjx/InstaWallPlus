package cj.instawall.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InstaClient {
    final String TAG = "CJ";
    public static final String META = "meta";
    public static final String IMAGES = "images";
    Map<String, String> headers;
    ExecutorService executor;
    String username, sessionID, assetsDir;
    String metaPath, imagePath;
    JSONArray savedPostsJSON;
    Context context;
    SharedPreferences sharedPreferences;

    public InstaClient(Context context) throws Exception {
        sharedPreferences = context.getSharedPreferences(MainActivity.GLOBAL_SHARED_PREF, Context.MODE_PRIVATE);
        String cookie = sharedPreferences.getString("cookie", null);
        String appID = sharedPreferences.getString("X-IG-App-ID", null);
        username = sharedPreferences.getString("username", null);
        if (cookie == null || appID == null || username == null) {
            Log.e(TAG, "Can't create InstaClient");
            throw new Exception("Somethings null for InstaClient");
        }
        headers = new HashMap<>();
        headers.put("cookie", cookie);
        headers.put("X-IG-App-ID", appID);
        this.executor = Executors.newCachedThreadPool();
        this.sessionID = getSessionID(headers.get("cookie"));
        this.context = context;
        this.assetsDir = context.getExternalFilesDir(null).toString();

        Files.createDirectories(Paths.get(assetsDir, username, IMAGES));
        Files.createDirectories(Paths.get(assetsDir, username, META));
        this.metaPath = Paths.get(assetsDir, username, META).toString();
        this.imagePath = Paths.get(assetsDir, username, IMAGES).toString();
    }

    public static final int TEST = 0;
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
            case TEST:
                executor.execute(this::test);
                break;
        }
    }

    void test() {
        try {
            getImageInPost(getPostInfo("2938978685088465262"), "2938978678755199850");
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    JSONArray getSavedPostsJSON() {
        if (savedPostsJSON != null) return savedPostsJSON;
        try {
            String savedPosts = new String(Files.readAllBytes(Paths.get(assetsDir, username, "saved_posts.json")));
            JSONArray ob = new JSONArray(savedPosts);
            return ob;
        } catch (Exception e) {
            Log.e(TAG, "getSavedPostsJSONFromDevice: " + Log.getStackTraceString(e));
        }
        return null;
    }

    JSONObject getRandomPostJSON() throws JSONException {
        JSONArray savedPosts = getSavedPostsJSON();
        return savedPosts.getJSONObject((int) (Math.random() * savedPosts.length()));
    }

    JSONObject getPostInfo(String postId) {
        try {
            if (Files.exists(Paths.get(metaPath, postId + ".json"))) {
                Log.d(TAG, "found " + postId + " info locally");
                return new JSONObject(new String(Files.readAllBytes(Paths.get(metaPath, postId + ".json"))));
            }
            HttpURLConnection con = getConnection("https://i.instagram.com/api/v1/media/" + postId + "/info/");
            JSONObject res = getJSONResponse(con);
            Files.copy(new ByteArrayInputStream(res.toString(2).getBytes()), Paths.get(metaPath, postId + ".json"));
            return res;
        } catch (Exception e) {
            Log.e(TAG, "downloadPostInfo: " + Log.getStackTraceString(e));
        }
        return null;
    }

    void downloadFromURL(String url, Path filePath) throws IOException {
        HttpURLConnection con = getConnection(url);
        Files.copy(con.getInputStream(), filePath);
    }

    String filenameFromUrl(String url) {
        Pattern p = Pattern.compile("\\w*.jpg");
        Matcher m = p.matcher(url);
        if (m.find()) {
            return url.substring(m.start(), m.end());
        }
        return "none";
    }

    String getImageInPost(JSONObject postInfo, String imageId) throws JSONException, IOException {
        JSONArray carouselMedia = postInfo.getJSONArray("items").getJSONObject(0).getJSONArray("carousel_media");
        String postID = postInfo.getJSONArray("items").getJSONObject(0).getString("pk");
        String newFileName = null;
        for (int i = 0; i < carouselMedia.length(); i++) {
            JSONObject c = carouselMedia.getJSONObject(i);
            String imageID = c.optString("pk");
            if (imageID.equals(imageId)) {
                Log.d(TAG, c.getString("pk"));
                String originalWidth = c.getString("original_width");
                String originalHeight = c.getString("original_height");
                JSONArray imageVersions = c.getJSONObject("image_versions2").getJSONArray("candidates");
                for (int j = 0; j < imageVersions.length(); j++) {
                    JSONObject iv = imageVersions.getJSONObject(j);
                    if (iv.optString("width").equals(originalWidth) && iv.optString("height").equals(originalHeight)) {
                        Log.d(TAG, iv.getString("url"));
                        String oldFileName = filenameFromUrl(iv.getString("url"));
                        newFileName = postID + "_" + imageID + ".jpg";
                        Log.d(TAG, oldFileName);
                        if (Files.exists(Paths.get(imagePath, oldFileName))) {
                            Files.move(Paths.get(imagePath, oldFileName), Paths.get(imagePath, newFileName));
                            Log.d(TAG, "File already exists, renamed " + oldFileName + " to " + newFileName);
                            break;
                        } else if (Files.exists(Paths.get(imagePath, newFileName))) {
                            Log.d(TAG, "File already exists, " + newFileName);
                            break;
                        }
                        String url = iv.getString("url");
                        Log.d(TAG, "downloading image: " + url);
                        downloadFromURL(url, Paths.get(imagePath, newFileName));
                    }
                }
                break;
            }
        }
        return newFileName;
    }

    String getRandomPost() throws JSONException {
        String pid = getRandomPostJSON().getJSONObject("node").getString("id");
        Log.d(TAG, pid);
//        Log.d(TAG, getPostInfo(pid).toString(2));;
        return null;
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
        JSONArray edges = null;
        try {
            JSONObject userInfo = getUserInfo();
            JSONObject savedMedia = userInfo.getJSONObject("data")
                    .getJSONObject("user")
                    .getJSONObject("edge_saved_media");

            Log.d(TAG, "User has " + savedMedia.getInt("count") + " saved posts");
            edges = new JSONArray();
            getAllSavedPosts(edges, savedMedia);
            Log.d(TAG, "Saved posts fetch complete: " + edges.length() + " posts");
            if (edges.length() > 0) {
                Files.copy(new ByteArrayInputStream(edges.toString(2).getBytes()), Paths.get(assetsDir, username, "saved_posts.json"));
            }
//            Log.d(TAG, edges.toString());
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return edges;
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
