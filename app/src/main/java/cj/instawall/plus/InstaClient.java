package cj.instawall.plus;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    JSONObject postCodeToID;
    Context context;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;

    static class PostInfo {
        public static JSONObject noCarouselImage(JSONObject postInfo) throws JSONException {
            return postInfo.getJSONArray("items").getJSONObject(0);
        }

        public static JSONArray carouselMedia(JSONObject postInfo) throws JSONException {
            return postInfo.getJSONArray("items").getJSONObject(0).getJSONArray("carousel_media");
        }

        public static String postID(JSONObject postInfo) throws JSONException {
            return postInfo.getJSONArray("items").getJSONObject(0).getString("pk");
        }
    }

    static class SavedItem {
        public static String postID(JSONObject savedItem) throws JSONException {
            return savedItem.getJSONObject("node").getString("id");
        }
    }

    public InstaClient(Context context) throws Exception {
        sharedPreferences = context.getSharedPreferences(MainActivity.GLOBAL_SHARED_PREF, Context.MODE_PRIVATE);
        spEditor = sharedPreferences.edit();
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
        this.executor = Executors.newSingleThreadExecutor();
        this.sessionID = getSessionID(headers.get("cookie"));
        this.context = context;
        this.assetsDir = context.getExternalFilesDir(null).toString();

        Files.createDirectories(Paths.get(assetsDir, username, IMAGES));
        Files.createDirectories(Paths.get(assetsDir, username, META));
        this.metaPath = Paths.get(assetsDir, username, META).toString();
        this.imagePath = Paths.get(assetsDir, username, IMAGES).toString();
    }


    // actions --------------------------------------------------
    public void act_setRandomWallpaper() {
        executor.execute(this::setRandomWallpaper);
    }

    public void act_continueLastSync() {
        executor.execute(() -> getSavedPosts(true));
    }

    public void act_getSavedPosts() {
        executor.execute(() -> getSavedPosts(false));
    }

    public void act_getUserInfo() {
        executor.execute(this::getUserInfo);
    }

    public void act_setWallpaperFromCode(String code) {
        executor.execute(() -> {
            try {
                setWallpaperFromCode(code);
            } catch (Exception e) {
                Log.e(TAG, "can't set wallpaper from code" + Log.getStackTraceString(e));
            }
        });
    }

    public Path act_getRandomImage() {
        try {
            Future<Path> future = executor.submit(this::getRandomImage);
            return future.get();
        } catch (Exception e) {
            Log.e(TAG, "act_getRandomImage: " + Log.getStackTraceString(e));
        }
        return null;
    }

    public void act_setWallpaper(Path path) {
        executor.execute(() -> setWallpaper(path));
    }

    public void act_test() {
        executor.execute(this::test);
    }
    // ----------------------------------------------------------

    private void test() {
        try {

        } catch (Exception e) {
            Log.d(TAG, "InstaClient, test: " + Log.getStackTraceString(e));
        }
    }

    Bitmap bitmapByFileName(String name) {
        return BitmapFactory.decodeFile(Paths.get(imagePath, name).toString());
    }

    Path pathByFileName(String name) {
        return Paths.get(imagePath, name);
    }

    private void setRandomWallpaper() {
        try {
            setWallpaper(getRandomImage());
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void setWallpaperFromCode(String code) throws JSONException, IOException {
        String postID = getPostCodeToID(code);
        JSONObject postInfo = getPostInfo(postID);
        setWallpaper(Paths.get(imagePath, getImageInPost(postInfo, getRandomImageInPost(postInfo))));
    }

    private int commitCount = 0;
    private final int commitFrequency = 10;

    private void commit() {
        commitCount++;
        if (commitCount >= commitFrequency) {
            Log.d(TAG, "commitLimit reached, saving files");
            saveFiles();
            commitCount = 0;
        }
    }

    void saveFiles() {
        try {
            Files.copy(new ByteArrayInputStream(postCodeToID.toString(2).getBytes(StandardCharsets.UTF_8)), Paths.get(assetsDir, "post_code_to_id.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Log.e(TAG, "saveFiles: " + Log.getStackTraceString(e));
        }
    }

    private JSONObject getPostCodeToIDJSON() throws JSONException {
        if (postCodeToID != null) return postCodeToID;
        try {
            postCodeToID = new JSONObject(new String(Files.readAllBytes(Paths.get(assetsDir, "post_code_to_id.json"))));
        } catch (NoSuchFileException e) {
            try {
                postCodeToID = new JSONObject("{}");
            } catch (Exception f) {
            }
        } catch (Exception f) {
            Log.e(TAG, "getPostCodeToIDJSON: " + Log.getStackTraceString(f));
        }
        JSONArray savedPosts = getSavedPostsJSON();
        for (int i = 0; i < savedPosts.length(); i++) {
            JSONObject node = savedPosts.getJSONObject(i).optJSONObject("node");
            if (node != null) {
                postCodeToID.put(node.optString("shortcode"), node.optString("id"));
            }
        }
        return postCodeToID;
    }

    JSONArray getSavedPostsJSON() {
        if (savedPostsJSON != null) return savedPostsJSON;
        try {
            String savedPosts = new String(Files.readAllBytes(Paths.get(assetsDir, username, "saved_posts.json")));
            savedPostsJSON = new JSONArray(savedPosts);
        } catch (NoSuchFileException e) {
            try {
                savedPostsJSON = new JSONArray("[]");
            } catch (Exception f) {
            }
        } catch (Exception f) {
            Log.e(TAG, "getSavedPostsJSONFromDevice: " + Log.getStackTraceString(f));
        }
        return savedPostsJSON;
    }

    JSONObject getRandomSavedItem() throws JSONException {
        JSONArray savedPosts = getSavedPostsJSON();
        return savedPosts.getJSONObject((int) (Math.random() * savedPosts.length()));
    }

    // return random postID from postInfo
    String getRandomImageInPost(JSONObject postInfo) throws JSONException {
        try {
            JSONArray carouselMedia = PostInfo.carouselMedia(postInfo);
            return carouselMedia.getJSONObject((int) (Math.random() * carouselMedia.length())).getString("pk");
        } catch (JSONException e) {
            return PostInfo.noCarouselImage(postInfo).getString("pk");
        }
    }

    private Path getRandomImage() throws JSONException, IOException {
        JSONObject randomPost = getPostInfo(SavedItem.postID(getRandomSavedItem()));
        String randomImage = getRandomImageInPost(randomPost);
        return Paths.get(imagePath, getImageInPost(randomPost, randomImage));
    }

    JSONObject getPostInfo(String postId) {
        try {
            if (Files.exists(Paths.get(metaPath, postId + ".json"))) {
                Log.d(TAG, "found " + postId + " info locally");
                return new JSONObject(new String(Files.readAllBytes(Paths.get(metaPath, postId + ".json"))));
            }
            Log.d(TAG, "fetching " + postId + " from IG");
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
        Files.copy(con.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        con.getInputStream().close();
    }

    String filenameFromUrl(String url) {
        Pattern p = Pattern.compile("\\w*.jpg");
        Matcher m = p.matcher(url);
        if (m.find()) {
            return url.substring(m.start(), m.end());
        }
        return "none";
    }

    String getImageInPost(JSONObject postInfo, String imageId) throws IOException, JSONException {
        try {
            return getImageInCarousel(postInfo, imageId);
        } catch (JSONException e) {
            return saveImageFromObject(PostInfo.noCarouselImage(postInfo), PostInfo.postID(postInfo));
        }
    }

    String getImageInCarousel(JSONObject postInfo, String imageId) throws JSONException, IOException {
        JSONArray carouselMedia = PostInfo.carouselMedia(postInfo);
        String postID = PostInfo.postID(postInfo);
        String newFileName = null;
        for (int i = 0; i < carouselMedia.length(); i++) {
            JSONObject c = carouselMedia.getJSONObject(i);
            String imageID = c.optString("pk");
            if (imageID.equals(imageId)) {
                newFileName = saveImageFromObject(c, postID);
                break;
            }
        }
        return newFileName;
    }

    boolean qualityCheck(Path image, JSONObject imageInfo) throws JSONException {
        Bitmap img = BitmapFactory.decodeFile(image.toString());
        String requiredDimensions = imageInfo.getString("width") + " x " + imageInfo.getString("height");
        String actualDimensions = img.getWidth() + " x " + img.getHeight();
        if (requiredDimensions.equals(actualDimensions)) {
            Log.d(TAG, "qualityCheck: pass");
            return true;
        } else {
            Log.d(TAG, "qualityCheck: fail, replacing local file.");
            Log.d(TAG, "required dimensions: " + requiredDimensions);
            Log.d(TAG, "actual dimensions: " + actualDimensions);
            return false;
        }
    }

    String saveImageFromObject(JSONObject c, String postID) throws JSONException, IOException {
        String imageID = c.getString("pk");
        String newFileName = postID + "_" + imageID + ".jpg";
        String originalWidth = c.getString("original_width");
        String originalHeight = c.getString("original_height");
        JSONArray imageVersions = c.getJSONObject("image_versions2").getJSONArray("candidates");
        for (int j = 0; j < imageVersions.length(); j++) {
            JSONObject iv = imageVersions.getJSONObject(j);
            if (iv.optString("width").equals(originalWidth) && iv.optString("height").equals(originalHeight)) {
                Log.d(TAG, iv.getString("url"));
                String oldFileName = filenameFromUrl(iv.getString("url"));
                Log.d(TAG, oldFileName);
                if (Files.exists(Paths.get(imagePath, oldFileName)) || Files.exists(Paths.get(imagePath, newFileName))) {
                    if (Files.exists(Paths.get(imagePath, oldFileName))) {
                        Files.move(Paths.get(imagePath, oldFileName), Paths.get(imagePath, newFileName), StandardCopyOption.REPLACE_EXISTING);
                        Log.d(TAG, "File already exists, renamed " + oldFileName + " to " + newFileName);
                    } else {
                        Log.d(TAG, "File already exists, " + newFileName);
                    }
                    if (qualityCheck(Paths.get(imagePath, newFileName), iv)) break;
                }
                String url = iv.getString("url");
                Log.d(TAG, "downloading image: " + url);
                downloadFromURL(url, Paths.get(imagePath, newFileName));
            }
        }
        return newFileName;
    }

    private void setWallpaper(Path path) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(path.toString());
            bitmap = CJImageUtil.removeWhiteBorder(bitmap);
            DisplayMetrics met = new DisplayMetrics();
            ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(met);
            int w = met.widthPixels;
            int h = met.heightPixels;
            if (w > h) {
                int tmp = w;
                w = h;
                h = tmp;
            }
//            Log.d(TAG, String.valueOf(w) + " x " + String.valueOf(h));
            int sh = (int) ((float) w / (float) bitmap.getWidth() * (float) bitmap.getHeight());
            Bitmap background = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(background);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, sh, true);

            canvas.drawBitmap(bitmap, 0, (h - sh) >> 1, new Paint());

            background.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(context.getExternalFilesDir(null), "test.png")));
//            Log.d(TAG, String.valueOf(sh));
            WallpaperManager manager = WallpaperManager.getInstance(context);
            manager.setBitmap(background);
            Log.d(TAG, "Wallpaper set successfully");
        } catch (Exception e) {
            Log.d(TAG, "Failed to set wallpaper " + Log.getStackTraceString(e));
        }
    }

    String getSessionID(String cookie) {
        Matcher m = Pattern.compile("sessionid=(\\d*)").matcher(cookie);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private HttpURLConnection getConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        for (String h : headers.keySet()) con.setRequestProperty(h, headers.get(h));
        return con;
    }

    private JSONObject getJSONResponse(HttpURLConnection con) throws IOException, JSONException {
        JSONObject res = new JSONObject(new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n")));
        con.getInputStream().close();
        return res;
    }

    private String getStringResponse(HttpURLConnection con) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder res = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            res.append(line);
            res.append('\n');
        }
        in.close();
        return res.toString();
    }

    private String getPostCodeToID(String code) {
        try {
            if (getPostCodeToIDJSON().has(code)) {
                Log.d(TAG, "found postID for code locally, " + code + " : " + getPostCodeToIDJSON().getString(code));
                return getPostCodeToIDJSON().getString(code);
            }
            Log.d(TAG, "get post id from code");
            HttpURLConnection con = getConnection("https://www.instagram.com/p/" + code);
            String res = getStringResponse(con);
            Matcher matcher = Pattern.compile("meta\\s*property\\s*=\\s*\"al:ios:url\"[^>]*media\\?id=(\\d+)").matcher(res);
            String postID = null;
            while (matcher.find()) {
                Log.d(TAG, "match: ");
                for (int i = 0; i <= matcher.groupCount(); i++) {
                    Log.d(TAG, "\tgroup: " + matcher.group(i));
                }
                postID = matcher.group(1);
            }
            if (postID != null) {
                getPostCodeToIDJSON().put(code, postID);
                commit();
            }
            return postID;
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return null;
    }

    private JSONObject getUserInfo() {
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

    private void getSavedPosts(boolean continueLast) {

        HashSet<String> set = new HashSet<>();
        JSONArray edges = getSavedPostsJSON();
        int oldLength = edges.length();
        try {
            for (int i = 0; i < edges.length(); i++) {
                set.add(SavedItem.postID(edges.getJSONObject(i)));
            }
            JSONObject savedMedia = null;
            if (continueLast) {
                savedMedia = new JSONObject("{\"edges\":[],\"count\": -1,\"page_info\": {\n" +
                        "                    \"has_next_page\": true,\n" +
                        "                    \"end_cursor\": \"" + sharedPreferences.getString(SPKeys.LAST_SYNC_CURSOR, "") + "\"\n" +
                        "                }}");
            } else {
                JSONObject userInfo = getUserInfo();
                savedMedia = userInfo.getJSONObject("data")
                        .getJSONObject("user")
                        .getJSONObject("edge_saved_media");
            }

            Log.d(TAG, "User has " + savedMedia.getInt("count") + " saved posts");
            getAllSavedPosts(edges, savedMedia, set, 0, 5);
            Log.d(TAG, "Saved posts fetch complete: " + edges.length() + " posts, found " + (edges.length() - oldLength) + " new");
            Files.copy(new ByteArrayInputStream(edges.toString(2).getBytes()), Paths.get(assetsDir, username, "saved_posts.json"), StandardCopyOption.REPLACE_EXISTING);
            this.savedPostsJSON = null;
//            Log.d(TAG, edges.toString());
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void getAllSavedPosts(JSONArray edges, JSONObject savedMedia, HashSet<String> set,
                                  int repeatCount, int repeatLimit) {
        try {
            JSONArray newEdges = savedMedia.optJSONArray("edges");
            if (newEdges != null) {
                for (int i = 0; i < newEdges.length(); i++) {
                    if (set.contains(SavedItem.postID(newEdges.getJSONObject(i)))) {
                        repeatCount++;
                    } else {
                        edges.put(newEdges.get(i));
                    }
                }
            }
            Log.d(TAG, "Got " + edges.length() + " posts");
            JSONObject pageInfo = savedMedia.getJSONObject("page_info");
            Log.d(TAG, newEdges.length() + " : " + pageInfo.toString(2));
            if (repeatCount >= repeatLimit) {
                Log.d(TAG, "post repeat limit reached, sync complete");
                return;
            }
            if (pageInfo.getBoolean("has_next_page")) {
                String cursor = pageInfo.getString("end_cursor");
                if (!cursor.isEmpty()) {
                    spEditor.putString(SPKeys.LAST_SYNC_CURSOR, cursor);
                    spEditor.apply();
                }
                HttpURLConnection con = getConnection("https://www.instagram.com/graphql/query/?query_hash=2ce1d673055b99250e93b6f88f878fde&variables=" +
                        URLEncoder.encode("{\"id\":\"" + sessionID + "\",\"first\":100,\"after\":\"" + cursor + "\"}", StandardCharsets.UTF_8.toString()));
                JSONObject res = getJSONResponse(con);
                savedMedia = res.getJSONObject("data")
                        .getJSONObject("user")
                        .getJSONObject("edge_saved_media");
                getAllSavedPosts(edges, savedMedia, set, repeatCount, repeatLimit);
            }

        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }
}
