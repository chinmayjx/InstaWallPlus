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
import android.webkit.WebSettings;

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
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    public static final String SAVED_POSTS = "saved_posts";
    // X-IG-App-ID of instagram.com, used when the WebView didn't expose one
    public static final String DEFAULT_APP_ID = "936619743392459";
    ThreadPoolExecutor executor;
    ThreadPoolExecutor lifoExecutor;
    JSONObject postCodeToID;
    Context context;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor spEditor;
    static List<String> savedPostIDs;
    static JSONObject deletedImages;
    static Map<String, String> headers;
    static JSONObject authInfo;
    static Path authInfoFile;

    static String username, appID, cookie, sessionID, imagePath, metaPath, filesDir, savedPostPath, deletedImagePath;
    static String userAgent;

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
            return savedItem.getJSONObject("media").getString("pk");
        }

        public static int numberOfImages(JSONObject savedItem) throws JSONException {
            JSONArray carousel = savedItem.getJSONObject("media").optJSONArray("carousel_media");
            if (carousel == null) return 1;
            return carousel.length();
        }

        public static JSONObject itemAtIndex(JSONObject savedItem, int index) throws JSONException {
            JSONArray carousel = savedItem.getJSONObject("media").optJSONArray("carousel_media");
            if (carousel == null) return savedItem.getJSONObject("media");
            return (JSONObject) carousel.get(index);
        }

        public static JSONObject load(String postID) {
            try {
                String savedItem = new String(Files.readAllBytes(Paths.get(savedPostPath, postID)));
                JSONObject savedItemJSON = new JSONObject(savedItem);
                return savedItemJSON;
            } catch (Exception e) {
                Log.e(TAG, "load: " + Log.getStackTraceString(e));
            }
            return null;
        }

        public static JSONObject updateWithPostInfo(JSONObject postInfo) {
            try {
                String postID = PostInfo.postID(postInfo);
                JSONObject newSavedItem = new JSONObject();
                newSavedItem.put("media", postInfo.getJSONArray("items").get(0));
                Files.copy(new ByteArrayInputStream(newSavedItem.toString(2).getBytes()), Paths.get(savedPostPath, postID), StandardCopyOption.REPLACE_EXISTING);
                return newSavedItem;
            } catch (Exception e) {
                Log.e(TAG, "updateWithPostInfo: " + e);
                return null;
            }
        }
    }

    // auth utils -----------------------------------
    public static void initializeVariables() {
        try {
            deletedImages = null;
            savedPostIDs = null;
            username = authInfo.optString("current_user");
            appID = authInfo.optString("app_id");
            cookie = getUserProperty(username, "cookie");
            sessionID = getSessionID(cookie);

            if (appID == null || appID.isEmpty()) appID = DEFAULT_APP_ID;

            headers = new HashMap<>();
            headers.put("cookie", cookie);
            headers.put("X-IG-App-ID", appID);
            headers.put("Referer", "https://www.instagram.com/");
            String csrf = getCookieValue(cookie, "csrftoken");
            if (csrf != null) headers.put("X-CSRFToken", csrf);

            Files.createDirectories(Paths.get(filesDir, username, IMAGES));
            Files.createDirectories(Paths.get(filesDir, username, META));
            Files.createDirectories(Paths.get(filesDir, username, DELETED));
            Files.createDirectories(Paths.get(filesDir, username, SAVED_POSTS));

            metaPath = Paths.get(filesDir, username, META).toString();
            imagePath = Paths.get(filesDir, username, IMAGES).toString();
            deletedImagePath = Paths.get(filesDir, username, DELETED).toString();
            savedPostPath = Paths.get(filesDir, username, SAVED_POSTS).toString();

        } catch (Exception e) {
            Log.e(TAG, "initializeVariables: " + Log.getStackTraceString(e));
        }
    }

    public static synchronized void loadAuthInfo(Context context) {
        if (authInfo != null) return;
        try {
            authInfoFile = Paths.get(context.getFilesDir().toString(), "auth_info.json");
            if (!Files.exists(authInfoFile)) {
                try (InputStream in = new ByteArrayInputStream("{user_data: {}}".getBytes(StandardCharsets.UTF_8))) {
                    Files.copy(in, authInfoFile);
                }
            }
            authInfo = new JSONObject(new String(Files.readAllBytes(authInfoFile)));
            if (!authInfo.has("user_data")) authInfo.put("user_data", new JSONObject());
        } catch (Exception e) {
            Log.e(TAG, "loadAuthInfo: " + Log.getStackTraceString(e));
        }
    }

    // called by InstaWebView once the WebView has a logged in session
    public static synchronized void onLogin(String username, String cookie, String appID) {
        setCurrentUser(username);
        setUserProperty(username, "cookie", cookie);
        if (appID != null) setAppID(appID);
        commitAuthFile();
        if (mainInstance != null) initializeVariables();
        Log.d(TAG, "onLogin: logged in as " + username);
    }

    // instagram.com doesn't tell us the username at login anymore, so ask for it
    public static String fetchUsername(String cookie, String userAgent, String appID) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("https://www.instagram.com/api/v1/accounts/edit/web_form_data/").openConnection();
            con.setConnectTimeout(20000);
            con.setReadTimeout(30000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("cookie", cookie);
            con.setRequestProperty("User-Agent", userAgent);
            con.setRequestProperty("X-IG-App-ID", appID == null ? DEFAULT_APP_ID : appID);
            con.setRequestProperty("Referer", "https://www.instagram.com/");
            String username = checkedJSONResponse(con).getJSONObject("form_data").getString("username");
            if (!username.isEmpty()) return username;
        } catch (Exception e) {
            Log.e(TAG, "fetchUsername: " + Log.getStackTraceString(e));
        }
        return null;
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
        loadAuthInfo(context);
        if (authInfo == null) throw new Exception("can't read auth info file");
        userAgent = WebSettings.getDefaultUserAgent(context);
        // Log.d(TAG, "authInfo: " + authInfo.toString(2));

        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.lifoExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LIFOBlockingQueue());
        this.context = context;
        filesDir = context.getExternalFilesDir(null).toString();

        initializeVariables();

        if (cookie == null || username == null || username.isEmpty()) {
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
        try {
            Log.d(TAG, "test: ");
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
        List<String> postIDs = getSavedPostIDs();
        int savedItemIndex = (int) (Math.random() * postIDs.size());
        int startIndex = savedItemIndex;
        int retryCount = 0;
        while (true) {
            JSONObject savedItem = SavedItem.load(postIDs.get(savedItemIndex));
            int numberOfImages = SavedItem.numberOfImages(savedItem);
            String postID = SavedItem.postID(savedItem);
            int imageIndex = (int) (Math.random() * numberOfImages);
            int startingImageIndex = imageIndex;
            while (true) {
                JSONObject carouselItem = SavedItem.itemAtIndex(savedItem, imageIndex);
                String imageID = carouselItem.getString("pk");
                if (!getDeletedImages().has(imageID)) {
                    try {
                        String fileName = saveImageFromObject(carouselItem, postID);
                        return Paths.get(imagePath, fileName);
                    } catch (FileNotFoundException e) {
                        if (retryCount == 1) throw new Exception("Not able to refresh post");
                        Log.d(TAG, "getRandomImage: URL expired, refreshing...");
                        JSONObject postInfo = getPostInfo(postID);
                        SavedItem.updateWithPostInfo(postInfo);
                        retryCount += 1;
                        break;
                    }
                }
                imageIndex = (imageIndex + 1) % numberOfImages;
                if (imageIndex == startingImageIndex) {
                    savedItemIndex = (savedItemIndex + 1) % postIDs.size();
                    if (savedItemIndex == startIndex) {
                        throw new Exception("No undeleted saved posts found");
                    }
                    break;
                }
            }
        }
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
        return postCodeToID;
    }

    List<String> getSavedPostIDs() {
        if (savedPostIDs != null) {
            return savedPostIDs;
        }
        try {
            savedPostIDs = Files.list(Paths.get(savedPostPath)).map(p -> p.getFileName().toString()).collect(Collectors.toList());
            return savedPostIDs;
        } catch (Exception e) {
            Log.e(TAG, "savedItemFiles: " + Log.getStackTraceString(e));
        }
        return new ArrayList<>();
    }

    static JSONObject getDeletedImages() {
        if (deletedImages != null) return deletedImages;
        try {
            deletedImages = new JSONObject(new String(Files.readAllBytes(Paths.get(filesDir, username, "deleted_images.json"))));
        } catch (NoSuchFileException e) {
            try {
                deletedImages = new JSONObject("{}");
            } catch (Exception ignored) {
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
        HttpURLConnection con = getConnection("https://www.instagram.com/api/v1/media/" + postId + "/info/");
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

    private void getSavedPosts(boolean continueLast) {
        try {
            Log.d(TAG, "getSavedPosts: begin");
            String max_id = null;
            if (continueLast) {
                max_id = sharedPreferences.getString(SPKeys.LAST_SYNC_CURSOR, null);
                Log.d(TAG, String.format("Continue failed sync from cursor, %s", max_id));
            }
            List<String> postIDs = getSavedPostIDs();
            HashSet<String> set = new HashSet<>(postIDs);
            int repeatCount = 0;
            int repeatLimit = 5;
            int newCount = 0;
            boolean moreAvailable = true;
            while (moreAvailable) {
                String url = "https://www.instagram.com/api/v1/feed/saved/posts/";
                if (max_id != null) {
                    url += "?max_id=" + max_id;
                }
                HttpURLConnection con = getConnection(url);
                JSONObject res = getJSONResponse(con);
                moreAvailable = res.getBoolean("more_available");
                JSONArray tmpItems = res.getJSONArray("items");
                max_id = res.optString("next_max_id");
                Log.d(TAG, String.format("fetched %d elements, next max_id is %s", tmpItems.length(), max_id));
                boolean stop = false;
                for (int i = 0; i < tmpItems.length(); i++) {
                    if (set.contains(SavedItem.postID(tmpItems.getJSONObject(i)))) {
                        repeatCount++;
                        if (repeatCount == repeatLimit) {
                            Log.d(TAG, "repeat limit hit, sync complete");
                            stop = true;
                            break;
                        }
                    } else {
                        JSONObject item = tmpItems.getJSONObject(i);
                        String postID = SavedItem.postID(item);
                        getSavedPostIDs().add(postID);
                        Files.copy(new ByteArrayInputStream(item.toString(2).getBytes()), Paths.get(savedPostPath, postID), StandardCopyOption.REPLACE_EXISTING);
                        newCount++;
                    }
                }
                if (!max_id.isEmpty()) {
                    spEditor.putString(SPKeys.LAST_SYNC_CURSOR, max_id);
                    spEditor.apply();
                }
                if (stop) break;
                Thread.sleep(1500);
            }
            Log.d(TAG, String.format("getSavedPosts: complete, total saved posts is %d, new: %d", postIDs.size(), newCount));
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }


    private String getPostCodeToID(String code) {
        String postID = shortcodeToMediaID(code);
        Log.d(TAG, "post code " + code + " -> id " + postID);
        return postID;
    }

    // a post's shortcode is its media id in base64 (the post page no longer exposes the id)
    static String shortcodeToMediaID(String code) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        if (code.length() > 11) code = code.substring(0, 11);
        BigInteger id = BigInteger.ZERO;
        for (char c : code.toCharArray()) {
            int v = alphabet.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("bad post code: " + code);
            id = id.shiftLeft(6).add(BigInteger.valueOf(v));
        }
        return id.toString();
    }

    // http util -------------------------------------------------
    private HttpURLConnection getConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        if (userAgent != null) con.setRequestProperty("User-Agent", userAgent);
        if (url.getHost().endsWith("instagram.com")) {
            for (String h : headers.keySet()) con.setRequestProperty(h, headers.get(h));
            // a logged out session redirects to the login page, don't follow it
            con.setInstanceFollowRedirects(false);
        }
        return con;
    }

    private JSONObject getJSONResponse(HttpURLConnection con) throws IOException, JSONException {
        return checkedJSONResponse(con);
    }

    static JSONObject checkedJSONResponse(HttpURLConnection con) throws IOException, JSONException {
        int code = con.getResponseCode();
        if (code != 200) {
            String body = "";
            InputStream err = con.getErrorStream();
            if (err != null) body = readAll(err);
            if (body.length() > 500) body = body.substring(0, 500);
            Log.e(TAG, "HTTP " + code + " from " + con.getURL() + ": " + body);
            if (code == 404) throw new FileNotFoundException(con.getURL().toString());
            if ((code >= 300 && code < 400) || body.contains("login_required"))
                throw new IOException("Instagram session expired, open Instagram from the menu and log in again");
            throw new IOException("HTTP " + code + " from " + con.getURL());
        }
        return new JSONObject(readAll(con.getInputStream()));
    }

    static String readAll(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
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

    static String getCookieValue(String cookie, String name) {
        if (cookie == null) return null;
        Matcher m = Pattern.compile("(?:^|;\\s*)" + name + "=([^;]*)").matcher(cookie);
        return m.find() ? m.group(1) : null;
    }

    String filenameFromUrl(String url) {
        Pattern p = Pattern.compile("\\w*\\.(jpg|webp)");
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
}
