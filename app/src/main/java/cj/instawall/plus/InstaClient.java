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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
    // syncing is slow on purpose, keep it off the executor that loads images
    ThreadPoolExecutor syncExecutor;
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
        this.syncExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
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
        syncExecutor.execute(() -> getSavedPosts(true));
    }

    public void act_getSavedPosts() {
        syncExecutor.execute(() -> getSavedPosts(false));
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

    // blocking, for callers that already run in the background
    public void setRandomWallpaperNow() {
        setRandomWallpaper();
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

    // removes the file for good; the entry stays in deleted_images so the post never comes back as a wallpaper
    public void permanentlyDeleteImage(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (Exception e) {
            Log.e(TAG, "permanentlyDeleteImage: " + Log.getStackTraceString(e));
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
            savedPostIDs = new CopyOnWriteArrayList<>(Files.list(Paths.get(savedPostPath)).map(p -> p.getFileName().toString()).collect(Collectors.toList()));
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

    // pacing for the saved feed, instagram rate limits accounts that page through it too fast
    static final int PAGE_DELAY_MIN_MS = 6_000, PAGE_DELAY_MAX_MS = 12_000;
    static final int LONG_PAUSE_EVERY_PAGES = 25, LONG_PAUSE_MIN_MS = 60_000, LONG_PAUSE_MAX_MS = 120_000;
    static final int MAX_RETRIES = 4, FIRST_RETRY_DELAY_MS = 120_000;

    enum SyncResult {CAUGHT_UP, END_OF_FEED, FAILED}

    // progress of a running sync, for the UI
    public interface SyncListener {
        void onSyncProgress(String status);

        void onSyncDone(String message);
    }

    static volatile SyncListener syncListener;
    static volatile boolean syncing;
    static volatile String syncStatus;
    private int syncNewPosts;

    private void reportSync(String status) {
        syncStatus = status;
        SyncListener l = syncListener;
        if (l != null) l.onSyncProgress(status);
    }

    // the interrupted sync's cursor is per account
    private String cursorKey(String user) {
        return SPKeys.LAST_SYNC_CURSOR + ":" + user;
    }

    // Synchronize: fetch new saves from the top until we reach posts we already have, then finish
    // an interrupted sync (if any) from where it stopped. Continue_Failed_Sync only does the latter.
    private void getSavedPosts(boolean continueLast) {
        // the account can be switched while syncing, stick to the one we started with
        String user = username;
        String postDir = savedPostPath;
        List<String> ids = getSavedPostIDs();
        String key = cursorKey(user);
        // cursors used to be stored for the whole app, it belongs to whoever is logged in now
        String legacy = sharedPreferences.getString(SPKeys.LAST_SYNC_CURSOR, null);
        if (legacy != null) {
            if (!sharedPreferences.contains(key)) spEditor.putString(key, legacy);
            spEditor.remove(SPKeys.LAST_SYNC_CURSOR);
            spEditor.commit();
        }
        String pending = sharedPreferences.getString(key, null);
        Log.d(TAG, "getSavedPosts: begin for " + user + ", pending cursor: " + (pending != null));
        syncing = true;
        syncNewPosts = 0;
        String result = null;
        try {
            if (!continueLast) {
                reportSync("Checking for new saves");
                // with an interrupted sync pending, the cursor belongs to it; don't overwrite it from the top
                SyncResult r = syncSavedPosts(null, pending == null ? key : null, postDir, ids);
                if (r == SyncResult.FAILED) return;
                if (pending == null) {
                    clearSyncCursor(key);
                    return;
                }
            }
            if (pending == null) {
                Log.d(TAG, "getSavedPosts: nothing to continue");
                result = "No interrupted sync to continue";
                return;
            }
            Log.d(TAG, "getSavedPosts: continuing interrupted sync");
            reportSync("Continuing where the last sync stopped");
            if (syncSavedPosts(pending, key, postDir, ids) != SyncResult.FAILED) clearSyncCursor(key);
        } finally {
            syncing = false;
            if (result == null) result = syncStatus != null && syncStatus.startsWith("Sync stopped")
                    ? syncStatus
                    : String.format(Locale.US, "Sync complete, %,d new posts", syncNewPosts);
            SyncListener l = syncListener;
            if (l != null) l.onSyncDone(result);
        }
    }

    private void clearSyncCursor(String key) {
        spEditor.remove(key);
        spEditor.commit();
    }

    // pages through the saved feed from startCursor (null = newest). With a cursorKey, the cursor of
    // every finished page is stored so a failed sync can be continued.
    private SyncResult syncSavedPosts(String startCursor, String cursorKey, String postDir, List<String> ids) {
        String max_id = startCursor;
        HashSet<String> known = new HashSet<>(ids);
        int repeatCount = 0;
        int repeatLimit = 5;
        int newCount = 0;
        int pages = 0;
        try {
            while (true) {
                String url = "https://www.instagram.com/api/v1/feed/saved/posts/";
                if (max_id != null) url += "?max_id=" + max_id;
                JSONObject res = getJSONWithRetry(url);
                pages++;
                JSONArray items = res.getJSONArray("items");
                boolean moreAvailable = res.optBoolean("more_available");
                max_id = res.optString("next_max_id");
                boolean caughtUp = false;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String postID = SavedItem.postID(item);
                    if (known.contains(postID)) {
                        if (++repeatCount >= repeatLimit) caughtUp = true;
                        continue;
                    }
                    known.add(postID);
                    ids.add(postID);
                    Files.copy(new ByteArrayInputStream(item.toString(2).getBytes()), Paths.get(postDir, postID), StandardCopyOption.REPLACE_EXISTING);
                    newCount++;
                    syncNewPosts++;
                }
                Log.d(TAG, String.format("sync page %d: %d items, %d new so far, total %d", pages, items.length(), newCount, ids.size()));
                reportSync(String.format(Locale.US, "%,d posts synced, %,d new", ids.size(), syncNewPosts));
                if (caughtUp) {
                    Log.d(TAG, "sync: reached already synced posts");
                    return SyncResult.CAUGHT_UP;
                }
                if (!moreAvailable || max_id.isEmpty()) {
                    Log.d(TAG, "sync: reached the end of saved posts");
                    return SyncResult.END_OF_FEED;
                }
                if (cursorKey != null) {
                    spEditor.putString(cursorKey, max_id);
                    spEditor.commit();
                }
                long delay = randomBetween(PAGE_DELAY_MIN_MS, PAGE_DELAY_MAX_MS);
                if (pages % LONG_PAUSE_EVERY_PAGES == 0) {
                    delay = randomBetween(LONG_PAUSE_MIN_MS, LONG_PAUSE_MAX_MS);
                    Log.d(TAG, "sync: taking a " + delay / 1000 + "s break");
                    reportSync(String.format(Locale.US, "%,d posts synced, %,d new · short break (%ds) to stay under rate limits", ids.size(), syncNewPosts, delay / 1000));
                }
                Thread.sleep(delay);
            }
        } catch (Exception e) {
            Log.e(TAG, "sync failed after " + pages + " pages, " + newCount + " new posts. Use Continue Failed Sync to resume. " + Log.getStackTraceString(e));
            String reason = e instanceof SessionExpiredException ? "Instagram session expired, log in again" : e.getMessage();
            reportSync("Sync stopped: " + reason + ". Continue it from the menu.");
            return SyncResult.FAILED;
        }
    }

    // retries rate limits, server errors and network errors with exponential backoff
    private JSONObject getJSONWithRetry(String url) throws Exception {
        long rateLimitDelay = FIRST_RETRY_DELAY_MS;
        long networkDelay = 30_000;
        for (int attempt = 0; ; attempt++) {
            try {
                return getJSONResponse(getConnection(url));
            } catch (SessionExpiredException | FileNotFoundException e) {
                throw e;
            } catch (HttpStatusException e) {
                if ((e.code != 429 && e.code < 500) || attempt >= MAX_RETRIES) throw e;
                Log.w(TAG, "HTTP " + e.code + ", waiting " + rateLimitDelay / 1000 + "s before retrying");
                reportSync((e.code == 429 ? "Instagram is rate limiting" : "Instagram error " + e.code) + ", retrying in " + rateLimitDelay / 60_000 + " min");
                Thread.sleep(rateLimitDelay);
                rateLimitDelay *= 2;
            } catch (IOException e) {
                if (attempt >= MAX_RETRIES) throw e;
                Log.w(TAG, "network error (" + e + "), waiting " + networkDelay / 1000 + "s before retrying");
                reportSync("Network problem, retrying in " + networkDelay / 1000 + "s");
                Thread.sleep(networkDelay);
                networkDelay *= 2;
            }
        }
    }

    static long randomBetween(long min, long max) {
        return min + (long) (Math.random() * (max - min));
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
                throw new SessionExpiredException();
            throw new HttpStatusException(code, "HTTP " + code + " from " + con.getURL());
        }
        return new JSONObject(readAll(con.getInputStream()));
    }

    static class SessionExpiredException extends IOException {
        SessionExpiredException() {
            super("Instagram session expired, open Instagram from the menu and log in again");
        }
    }

    static class HttpStatusException extends IOException {
        final int code;

        HttpStatusException(int code, String message) {
            super(message);
            this.code = code;
        }
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
