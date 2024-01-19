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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class InstaClient {
    public static final String TAG = "CJ";
    public static final String META = "meta";
    public static final String IMAGES = "images";
    public static final String DELETED = ".deleted_images";
    ThreadPoolExecutor executor;
    ThreadPoolExecutor lifoExecutor;
    JSONObject postCodeToID;
    Context context;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;
    static JSONObject deletedImages;
    static JSONArray savedPostsJSON;
    static Map<String, String> headers;
    static JSONObject authInfo;
    static Path authInfoFile;

    static String username, appID, cookie, sessionID, imagePath, metaPath, filesDir, deletedImagePath;

    // utilities to access JSON ---------------------
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

        public static String postCode(JSONObject postInfo) throws JSONException {
            return postInfo.getJSONArray("items").getJSONObject(0).getString("code");
        }

        public static int imageIndexInPost(JSONObject postInfo, String imageID) {
            try {
                JSONArray cm = carouselMedia(postInfo);
                for (int i = 0; i < cm.length(); i++) {
                    JSONObject c = cm.getJSONObject(i);
                    String id = c.optString("pk");
                    if (imageID.equals(id)) {
                        return i;
                    }
                }
            } catch (JSONException e) {
                return 0;
            }
            return -1;
        }
    }

    static class SavedItem {
        public static String postID(JSONObject savedItem) throws JSONException {
            return savedItem.getJSONObject("node").getString("id");
        }

        public static String postCode(JSONObject savedItem) throws JSONException {
            return savedItem.getJSONObject("node").getString("shortcode");
        }

        public static int numberOfImages(JSONObject savedItem) throws JSONException{
            JSONArray carousel = savedItem.getJSONObject("media").optJSONArray("carousel_media");
            if (carousel == null) return 1;
            return carousel.length();
        }
    }

    // auth utils -----------------------------------
    public static void initializeVariables() {
        try {
            deletedImages = null;
            savedPostsJSON = null;
            username = authInfo.optString("current_user");
            appID = authInfo.optString("app_id");
            cookie = getUserProperty(username, "cookie");
            sessionID = getSessionID(cookie);

            headers = new HashMap<>();
            headers.put("cookie", cookie);
            headers.put("X-IG-App-ID", appID);

            Files.createDirectories(Paths.get(filesDir, username, IMAGES));
            Files.createDirectories(Paths.get(filesDir, username, META));
            Files.createDirectories(Paths.get(filesDir, username, DELETED));

            metaPath = Paths.get(filesDir, username, META).toString();
            imagePath = Paths.get(filesDir, username, IMAGES).toString();
            deletedImagePath = Paths.get(filesDir, username, DELETED).toString();

        } catch (Exception e) {
            Log.e(TAG, "initializeVariables: " + Log.getStackTraceString(e));
        }
    }

    public static void commitAuthFile() {
        try (InputStream in = new ByteArrayInputStream(authInfo.toString(2).getBytes(StandardCharsets.UTF_8))) {
            Files.copy(in, authInfoFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Log.e(TAG, "commitAuthFile: " + Log.getStackTraceString(e));
        }
    }

    public static void setCurrentUser(String username) {
        try {
            authInfo.put("current_user", username);
            InstaClient.username = username;
        } catch (JSONException e) {
            Log.e(TAG, "setCurrentUser: " + Log.getStackTraceString(e));
        }
    }

    public static void switchToUser(String user) {
        InstaClient.setCurrentUser(user);
        InstaClient.commitAuthFile();
        InstaClient.initializeVariables();
    }

    public static ArrayList<String> getLoggedInUsers() {
        try {
            ArrayList<String> arr = new ArrayList<>();
            JSONArray ja = authInfo.getJSONObject("user_data").names();
            if (ja == null) return arr;
            for (int i = 0; i < ja.length(); i++) {
                arr.add(ja.getString(i));
            }
            return arr;
        } catch (Exception e) {
            Log.e(TAG, "getLoggedInUsers: " + Log.getStackTraceString(e));
        }
        return new ArrayList<>();
    }

    public static String getNextUser() {
        ArrayList<String> users = getLoggedInUsers();
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).equals(username)) return users.get((i + 1) % users.size());
        }
        return null;
    }

    public static void setAppID(String appID) {
        try {
            authInfo.put("app_id", appID);
        } catch (JSONException e) {
            Log.e(TAG, "setAppID: " + Log.getStackTraceString(e));
        }
    }

    public static void setCurrentUserProperty(String property, String value) {
        if (username != null) {
            setUserProperty(username, property, value);
        } else {
            Log.e(TAG, "failed to set user property, because current user in null");
        }
    }

    public static void setUserProperty(String username, String property, String value) {
        try {
            JSONObject userData = authInfo.getJSONObject("user_data");
            if (!userData.has(username)) userData.put(username, new JSONObject("{}"));
            userData.getJSONObject(username).put(property, value);
        } catch (JSONException e) {
            Log.e(TAG, "setUserProperty: " + Log.getStackTraceString(e));
        }
    }

    public static String getUserProperty(String username, String property) {
        try {
            JSONObject userData = authInfo.getJSONObject("user_data").getJSONObject(username);
            return userData.getString(property);
        } catch (JSONException e) {
            Log.e(TAG, "failed to get user property, " + property);
        }
        return null;
    }
    // -----------------------------------------------

    private static InstaClient mainInstance = null;

    public static InstaClient getInstance(Context context) throws Exception {
        if (mainInstance == null) {
            mainInstance = new InstaClient(context.getApplicationContext());
        }
        return mainInstance;
    }

    private InstaClient(Context context) throws Exception {
        sharedPreferences = context.getSharedPreferences(ViewActivity.GLOBAL_SHARED_PREF, Context.MODE_PRIVATE);
        spEditor = sharedPreferences.edit();
        authInfoFile = Paths.get(context.getFilesDir().toString(), "auth_info.json");
        if (!Files.exists(authInfoFile)) {
            try (InputStream in = new ByteArrayInputStream("{user_data: {}}".getBytes(StandardCharsets.UTF_8))) {
                Files.copy(in, authInfoFile);
            }
            throw new Exception("no auth info file found, created one");
        }
        authInfo = new JSONObject(new String(Files.readAllBytes(authInfoFile)));
        // Log.d(TAG, "authInfo: " + authInfo.toString(2));

        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.lifoExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LIFOBlockingQueue());
        this.context = context;
        filesDir = context.getExternalFilesDir(null).toString();

        initializeVariables();

        if (cookie == null || appID == null || username == null) {
            Log.e(TAG, "Can't create InstaClient");
            throw new Exception("something's null for InstaClient");
        }
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

    public void act_getRandomImageAsync(Consumer<Path> callback) {
        lifoExecutor.execute(() -> {
            try {
                callback.accept(getRandomImage());
            } catch (Exception e) {
                Log.e(TAG, "act_getRandomImageAsync: " + Log.getStackTraceString(e));
            }
        });
    }

    public void act_setWallpaper(Path path) {
        executor.execute(() -> setWallpaper(path));
    }

    public void act_test() {
        executor.execute(this::test);
    }
    // ----------------------------------------------------------

    private void test() {
        String TAG = "TEST";
        try {
            Log.d(TAG, "test: me");
//            Log.d(TAG, "test: " + getPostCodeToID("CnSr4Q1ykSK"));
//            getPostInfo("3013664083037275274");
        } catch (Exception e) {
            Log.d(TAG, "InstaClient, test: " + Log.getStackTraceString(e));
        }
    }

    // utils to access app data ---------------------------------

    Bitmap bitmapByFileName(String name) {
        return BitmapFactory.decodeFile(Paths.get(imagePath, name).toString());
    }

    static Path pathByFileName(String name) {
        return Paths.get(imagePath, name);
    }

    boolean qualityCheck(Path image, JSONObject imageInfo) throws JSONException {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        Bitmap img = BitmapFactory.decodeFile(image.toString(), opt);
        String requiredDimensions = imageInfo.getString("width") + " x " + imageInfo.getString("height");
        String actualDimensions = opt.outWidth + " x " + opt.outHeight;
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

    JSONObject getBestImage(JSONObject c) throws JSONException {
        JSONObject bestImage = null;
        JSONObject originalImage = null;
        int originalWidth = c.optInt("original_width");
        int originalHeight = c.optInt("original_height");
        JSONArray imageVersions = c.getJSONObject("image_versions2").getJSONArray("candidates");
        int mxa = 0;
        for (int j = 0; j < imageVersions.length(); j++) {
            JSONObject iv = imageVersions.getJSONObject(j);
            int cw = iv.getInt("width");
            int ch = iv.getInt("height");
            int ca = cw * ch;
            if (ca > mxa) {
                bestImage = iv;
                mxa = ca;
            }
            if (cw == originalWidth && ch == originalHeight) {
                originalImage = iv;
            }
        }
        int bestWidth = bestImage.getInt("width");
        int bestHeight = bestImage.getInt("height");
        if (originalWidth != bestWidth || originalHeight != bestHeight) {
            Log.d(TAG, "best image doesn't match original dimensions");
            Log.d(TAG, "best: " + bestWidth + " x " + bestHeight);
            Log.d(TAG, "original: " + originalWidth + " x " + originalHeight);
            if (originalImage != null) {
                Log.d(TAG, "found and using original image");
            }
        }
        return originalImage == null ? bestImage : originalImage;
    }

    // return random imageID from postInfo
    String getImageAtIndexInPost(JSONObject postInfo, int index) throws JSONException {
        try {
            JSONArray carouselMedia = PostInfo.carouselMedia(postInfo);
            if (index == -1) index = (int) (Math.random() * carouselMedia.length());
            return carouselMedia.getJSONObject(index).getString("pk");
        } catch (JSONException e) {
            if (index == -1) index = 0;
            return PostInfo.noCarouselImage(postInfo).getString("pk");
        }
    }

    int numberOfImagesInPost(JSONObject postInfo) {
        try {
            return PostInfo.carouselMedia(postInfo).length();
        } catch (JSONException e) {
            return 1;
        }
    }

    private Path getRandomImage() throws Exception {
        JSONArray savedPosts = getSavedPostsJSON();
        int savedItemIndex = (int) (Math.random() * savedPosts.length());
        int startIndex = savedItemIndex;
        while (true) {
            JSONObject savedItem = savedPosts.getJSONObject(savedItemIndex);
            int numberOfImages = SavedItem.numberOfImages(savedItem);
            Log.d(TAG, "getRandomImage: " + numberOfImages);
            break;
//            String postID = SavedItem.postID(savedItem);
//            JSONObject randomPostInfo = null;
//            try {
//                randomPostInfo = getPostInfo(postID);
//            } catch (FileNotFoundException e) {
//                Log.e(TAG, "can't get post info for " + postID + ", check " + "https://www.instagram.com/p/" + SavedItem.postCode(savedItem));
//            }
//            int nInPost = numberOfImagesInPost(randomPostInfo);
//            int imageIndex = (int) (Math.random() * nInPost);
//            int startingImageIndex = imageIndex;
//            while (true) {
//                String imageID = getImageAtIndexInPost(randomPostInfo, imageIndex);
//                if (!getDeletedImages().has(imageID))
//                    return Paths.get(imagePath, getImageInPost(randomPostInfo, imageID));
//                imageIndex = (imageIndex + 1) % nInPost;
//                if (imageIndex == startingImageIndex) {
//                    savedItemIndex = (savedItemIndex + 1) % savedPosts.length();
//                    if (savedItemIndex == startIndex) {
//                        throw new Exception("No undeleted saved posts found");
//                    }
//                    break;
//                }
//            }
        }
        return null;
    }

    // modify app data ------------------------------------------

    public void saveDeletedImages() throws JSONException, IOException {
        Files.copy(new ByteArrayInputStream(deletedImages.toString(2).getBytes(StandardCharsets.UTF_8)), Paths.get(filesDir, username, "deleted_images.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    public void deleteImage(Path p) {
        if (p == null) return;
        try {
            String[] tk = p.toString().split("/");
            String fileName = tk[tk.length - 1];
            tk = fileName.split("\\.")[0].split("_");
            String postID = tk[0];
            String imageID = tk[1];

            JSONObject j = new JSONObject();
            j.put("postID", postID);
            j.put("fileName", fileName);
            j.put("ts", System.currentTimeMillis());
            getDeletedImages().put(imageID, j);

            Files.move(p, Paths.get(deletedImagePath, fileName), StandardCopyOption.REPLACE_EXISTING);

            saveDeletedImages();
        } catch (Exception e) {
            Log.e(TAG, "deleteImage: " + Log.getStackTraceString(e));
        }
    }

    public void restoreImage(Path p) {
        if (p == null) return;
        try {
            String[] tk = p.toString().split("/");
            String fileName = tk[tk.length - 1];
            tk = fileName.split("\\.")[0].split("_");
            String imageID = tk[1];

            getDeletedImages().remove(imageID);

            Files.move(p, Paths.get(imagePath, fileName), StandardCopyOption.REPLACE_EXISTING);

            saveDeletedImages();
        } catch (Exception e) {
            Log.e(TAG, "restoreImage: " + Log.getStackTraceString(e));
        }

    }

    // ----------------------------------------------------------

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
        setWallpaper(Paths.get(imagePath, getImageInPost(postInfo, getImageAtIndexInPost(postInfo, -1))));
    }

    // sync unimportant variables with cache files --------------
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
            if (postCodeToID != null)
                Files.copy(new ByteArrayInputStream(postCodeToID.toString(2).getBytes(StandardCharsets.UTF_8)), Paths.get(filesDir, "post_code_to_id.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Log.e(TAG, "saveFiles: " + Log.getStackTraceString(e));
        }
    }

    // read objects from saved files -----------------------------
    private JSONObject getPostCodeToIDJSON() throws JSONException {
        if (postCodeToID != null) return postCodeToID;
        try {
            postCodeToID = new JSONObject(new String(Files.readAllBytes(Paths.get(filesDir, "post_code_to_id.json"))));
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
            String savedPosts = new String(Files.readAllBytes(Paths.get(filesDir, username, "saved_posts.json")));
            savedPostsJSON = new JSONArray(savedPosts);
        } catch (NoSuchFileException e) {
            try {
                savedPostsJSON = new JSONArray("[]");
            } catch (Exception ignored) {
            }
        } catch (Exception f) {
            Log.e(TAG, "getSavedPostsJSONFromDevice: " + Log.getStackTraceString(f));
        }
        return savedPostsJSON;
    }

    static JSONObject getDeletedImages() {
        if (deletedImages != null) return deletedImages;
        try {
            deletedImages = new JSONObject(new String(Files.readAllBytes(Paths.get(filesDir, username, "deleted_images.json"))));
        } catch (NoSuchFileException e) {
            try {
                deletedImages = new JSONObject("{}");
            } catch (Exception f) {
            }
        } catch (Exception f) {
            Log.e(TAG, "getDeletedImages: " + Log.getStackTraceString(f));
        }
        return deletedImages;
    }

    // get data from instagram -----------------------------------
    JSONObject postInfoFromCache(String postId) {
        try {
            if (Files.exists(Paths.get(metaPath, postId + ".json"))) {
                return new JSONObject(new String(Files.readAllBytes(Paths.get(metaPath, postId + ".json"))));
            }
        } catch (Exception e) {
            Log.e(TAG, "postInfoFromCache: " + Log.getStackTraceString(e));
        }
        return null;
    }

    JSONObject getPostInfo(String postId) throws IOException, JSONException {
        if (Files.exists(Paths.get(metaPath, postId + ".json"))) {
            Log.d(TAG, "found " + postId + " info locally");
            return new JSONObject(new String(Files.readAllBytes(Paths.get(metaPath, postId + ".json"))));
        }
        Log.d(TAG, "fetching " + postId + " from IG");
        HttpURLConnection con = getConnection("https://i.instagram.com/api/v1/media/" + postId + "/info/");
        JSONObject res = getJSONResponse(con);
        Files.copy(new ByteArrayInputStream(res.toString(2).getBytes()), Paths.get(metaPath, postId + ".json"), StandardCopyOption.REPLACE_EXISTING);
        return res;
    }

    String getImageInPost(JSONObject postInfo, String imageId, int retryCount) throws IOException, JSONException {
        String postID = PostInfo.postID(postInfo);
        try {
            try {
                return getImageInCarousel(postInfo, imageId);
            } catch (JSONException e) {
                return saveImageFromObject(PostInfo.noCarouselImage(postInfo), postID);
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "getImageInPost: " + "url expired, refreshing post info and retrying...");
            Files.deleteIfExists(Paths.get(metaPath, postID + ".json"));
            try {
                if (retryCount < 1)
                    return getImageInPost(getPostInfo(postID), imageId, retryCount + 1);
            } catch (FileNotFoundException f) {
                Log.d(TAG, "can't refresh post info, post probably deleted: https://www.instagram.com/p/" + PostInfo.postCode(postInfo));
            }
        }
        return null;
    }

    String getImageInPost(JSONObject postInfo, String imageId) throws IOException, JSONException {
        return getImageInPost(postInfo, imageId, 0);
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

    String saveImageFromObject(JSONObject c, String postID) throws JSONException, IOException {
        String imageID = c.getString("pk");
        String newFileName = postID + "_" + imageID + ".jpg";
        JSONObject iv = getBestImage(c);

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
            if (qualityCheck(Paths.get(imagePath, newFileName), iv)) return newFileName;
        }
        String url = iv.getString("url");
        Log.d(TAG, "downloading image: " + url);
        downloadFromURL(url, Paths.get(imagePath, newFileName));

        return newFileName;
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
        try {
            Log.d(TAG, "getSavedPosts: begin");
            String max_id = null;
            JSONArray items = new JSONArray();
            JSONArray newItems = new JSONArray();
            boolean moreAvailable = true;
            while (moreAvailable){
                String url = "https://www.instagram.com/api/v1/feed/saved/posts/";
                if (max_id != null){
                    url += "?max_id=" + max_id;
                }
                HttpURLConnection con = getConnection(url);
                JSONObject res = getJSONResponse(con);
                moreAvailable = res.getBoolean("more_available");
                JSONArray tmpItems = res.getJSONArray("items");
                for (int i=0; i < tmpItems.length(); i++){
                    newItems.put(tmpItems.get(i));
                }
                max_id = res.optString("next_max_id");
                Log.d(TAG, String.format("got %d new posts, next max_id is %s", tmpItems.length(), max_id));
            }
            Log.d(TAG, String.format("getSavedPosts: complete, total saved posts is %d, new: %d", items.length(), newItems.length()));
            for (int i=newItems.length() - 1; i >= 0; i--){
                items.put(newItems.get(i));
            }
            Files.copy(new ByteArrayInputStream(items.toString(2).getBytes()), Paths.get(filesDir, username, "saved_posts.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
//        HashSet<String> set = new HashSet<>();
//        JSONArray edges = getSavedPostsJSON();
//        JSONArray newEdges = new JSONArray();
//        try {
//            for (int i = 0; i < edges.length(); i++) {
//                set.add(SavedItem.postID(edges.getJSONObject(i)));
//            }
//            JSONObject savedMedia = null;
//            if (continueLast) {
//                savedMedia = new JSONObject("{\"edges\":[],\"count\": -1,\"page_info\": {\n" +
//                        "                    \"has_next_page\": true,\n" +
//                        "                    \"end_cursor\": \"" + sharedPreferences.getString(SPKeys.LAST_SYNC_CURSOR, "") + "\"\n" +
//                        "                }}");
//            } else {
//                JSONObject userInfo = getUserInfo();
//                savedMedia = userInfo.getJSONObject("data")
//                        .getJSONObject("user")
//                        .getJSONObject("edge_saved_media");
//            }
//
//            Log.d(TAG, "User has " + savedMedia.getInt("count") + " saved posts");
//            getAllSavedPosts(newEdges, savedMedia, set, 0, 5);
//            Log.d(TAG, "Saved posts fetch complete: " + edges.length() + " posts, found " + (newEdges.length()) + " new");
//            for (int i = newEdges.length() - 1; i >= 0; i--) {
//                savedPostsJSON.put(newEdges.getJSONObject(i));
//            }
//            Files.copy(new ByteArrayInputStream(edges.toString(2).getBytes()), Paths.get(filesDir, username, "saved_posts.json"), StandardCopyOption.REPLACE_EXISTING);
//        } catch (Exception e) {
//            Log.e(TAG, Log.getStackTraceString(e));
//        }
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

    private String getPostCodeToID(String code) {
        try {
            if (getPostCodeToIDJSON().has(code)) {
                Log.d(TAG, "found postID for code locally, " + code + " : " + getPostCodeToIDJSON().getString(code));
                return getPostCodeToIDJSON().getString(code);
            }
            Log.d(TAG, "get post id from code:" + code);
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

    // http util -------------------------------------------------
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

    void downloadFromURL(String url, Path filePath) throws IOException {
        HttpURLConnection con = getConnection(url);
        Files.copy(con.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        con.getInputStream().close();
    }

    // miscellaneous utils -----------------------------------------
    static String getSessionID(String cookie) {
        if (cookie == null) return null;
        Matcher m = Pattern.compile("sessionid=(\\d*)").matcher(cookie);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    String filenameFromUrl(String url) {
        Pattern p = Pattern.compile("\\w*.jpg");
        Matcher m = p.matcher(url);
        if (m.find()) {
            return url.substring(m.start(), m.end());
        }
        return "none";
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
            //           Log.d(TAG, String.valueOf(w) + " x " + String.valueOf(h));
            int sh = (int) ((float) w / (float) bitmap.getWidth() * (float) bitmap.getHeight());
            Bitmap background = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(background);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, sh, true);

            canvas.drawBitmap(bitmap, 0, (h - sh) >> 1, new Paint());

            background.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(context.getExternalFilesDir(null), "test.png")));
            //           Log.d(TAG, String.valueOf(sh));
            WallpaperManager manager = WallpaperManager.getInstance(context);
            manager.setBitmap(background);
            FileOutputStream out = new FileOutputStream(Paths.get(filesDir, username, "recent_wallpapers.txt").toString(), true);
            out.write((path + "\n").getBytes(StandardCharsets.UTF_8));
            out.close();
            Log.d(TAG, "Wallpaper set successfully");
        } catch (Exception e) {
            Log.d(TAG, "Failed to set wallpaper " + Log.getStackTraceString(e));
        }
    }

    static String toJSONArray(HashSet<String> set) {
        StringBuilder sb = new StringBuilder("[\n");
        Iterator<String> it = set.iterator();
        while (it.hasNext()) {
            sb.append("\t\"");
            sb.append(it.next());
            sb.append("\"" + (it.hasNext() ? "," : "") + "\n");
        }
        sb.append("]");
        return sb.toString();
    }
    // -------------------------------------------------------------
}
