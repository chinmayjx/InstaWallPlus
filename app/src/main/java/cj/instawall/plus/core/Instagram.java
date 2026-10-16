package cj.instawall.plus.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Instagram's private web API: saved feed, post info, and turning its JSON into {@link SavedPost}s. */
public final class Instagram {
    public static final String SOURCE_ID = "instagram";
    public static final String DEFAULT_APP_ID = "936619743392459";
    static final String API = "https://www.instagram.com/api/v1/";

    private Instagram() {
    }

    /** Stored items look like the saved feed's: {"media": {...}} */
    public static final class Feed implements SavedFeed {
        private final Http http;

        public Feed(Http http) {
            this.http = http;
        }

        @Override
        public FeedPage fetch(String cursor) throws IOException, JSONException {
            String url = API + "feed/saved/posts/" + (cursor == null ? "" : "?max_id=" + cursor);
            JSONObject res = http.getJSON(url);
            JSONArray items = res.getJSONArray("items");
            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) list.add(items.getJSONObject(i));
            String next = res.optBoolean("more_available") ? res.optString("next_max_id", null) : null;
            return new FeedPage(list, next);
        }

        @Override
        public String itemId(JSONObject item) throws JSONException {
            return item.getJSONObject("media").getString("pk");
        }
    }

    /** Fresh copy of a stored item, for when its image urls expired. */
    public static JSONObject refresh(Http http, String postId) throws IOException, JSONException {
        JSONObject info = http.getJSON(API + "media/" + postId + "/info/");
        return new JSONObject().put("media", info.getJSONArray("items").getJSONObject(0));
    }

    public static SavedPost parse(String postId, JSONObject item) throws JSONException {
        JSONObject media = item.has("media") ? item.getJSONObject("media") : item;
        List<ImageRef> images = new ArrayList<>();
        JSONArray carousel = media.optJSONArray("carousel_media");
        if (carousel == null) {
            images.add(image(media));
        } else {
            for (int i = 0; i < carousel.length(); i++) images.add(image(carousel.getJSONObject(i)));
        }
        return new SavedPost(SOURCE_ID, postId, images);
    }

    // the candidate with the original dimensions if there is one, else the largest
    static ImageRef image(JSONObject m) throws JSONException {
        int ow = m.optInt("original_width"), oh = m.optInt("original_height");
        JSONArray candidates = m.getJSONObject("image_versions2").getJSONArray("candidates");
        JSONObject best = null, original = null;
        long bestArea = -1;
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject c = candidates.getJSONObject(i);
            long area = (long) c.getInt("width") * c.getInt("height");
            if (area > bestArea) {
                best = c;
                bestArea = area;
            }
            if (c.getInt("width") == ow && c.getInt("height") == oh) original = c;
        }
        JSONObject pick = original != null ? original : best;
        return new ImageRef(m.getString("pk"), pick.getString("url"), pick.getInt("width"), pick.getInt("height"));
    }

    /** A post's shortcode (instagram.com/p/CODE) is its media id in base64. */
    public static String shortcodeToMediaId(String code) {
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
}
