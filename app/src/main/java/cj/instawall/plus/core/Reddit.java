package cj.instawall.plus.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reddit's web JSON (the logged in session's /user/NAME/saved.json) and turning posts into {@link SavedPost}s. */
public final class Reddit {
    public static final String SOURCE_ID = "reddit";
    static final String WEB = "https://www.reddit.com";
    static final Pattern DIRECT_IMAGE = Pattern.compile("^https://(i\\.redd\\.it|i\\.imgur\\.com)/[^?#]+\\.(jpe?g|png|webp)$", Pattern.CASE_INSENSITIVE);

    private Reddit() {
    }

    /** Stored items are the post objects ("data" of a t3 listing child). Only posts with images are kept. */
    public static final class Feed implements SavedFeed {
        private final Http http;
        private final String username;

        public Feed(Http http, String username) {
            this.http = http;
            this.username = username;
        }

        @Override
        public FeedPage fetch(String cursor) throws IOException, JSONException {
            String url = WEB + "/user/" + username + "/saved.json?limit=100&raw_json=1" + (cursor == null ? "" : "&after=" + cursor);
            JSONObject data = http.getJSON(url).getJSONObject("data");
            JSONArray children = data.getJSONArray("children");
            List<JSONObject> posts = new ArrayList<>();
            for (int i = 0; i < children.length(); i++) {
                JSONObject c = children.getJSONObject(i);
                if (!"t3".equals(c.optString("kind"))) continue; // saved comments
                JSONObject post = c.getJSONObject("data");
                if (!parse(post).images.isEmpty()) posts.add(post);
            }
            return new FeedPage(posts, data.isNull("after") ? null : data.optString("after", null));
        }

        @Override
        public String itemId(JSONObject post) throws JSONException {
            return post.getString("id");
        }
    }

    public static JSONObject refresh(Http http, String postId) throws IOException, JSONException {
        JSONObject listing = http.getJSON(WEB + "/by_id/t3_" + postId + ".json?raw_json=1");
        return listing.getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
    }

    public static SavedPost parse(JSONObject post) throws JSONException {
        String id = post.getString("id");
        List<ImageRef> images = new ArrayList<>();
        if (post.optBoolean("is_gallery")) {
            JSONObject meta = post.optJSONObject("media_metadata");
            JSONObject gallery = post.optJSONObject("gallery_data");
            JSONArray items = gallery == null ? null : gallery.optJSONArray("items");
            if (meta != null && items != null) {
                for (int i = 0; i < items.length(); i++) {
                    String mediaId = items.getJSONObject(i).getString("media_id");
                    JSONObject m = meta.optJSONObject(mediaId);
                    if (m == null || !"valid".equals(m.optString("status")) || !"Image".equals(m.optString("e"))) continue;
                    JSONObject s = m.getJSONObject("s");
                    if (!s.has("u")) continue; // animated
                    images.add(new ImageRef(mediaId, s.getString("u"), s.optInt("x"), s.optInt("y")));
                }
            }
        } else if (!post.optBoolean("is_video")) {
            String url = post.optString("url_overridden_by_dest", post.optString("url"));
            JSONObject source = previewSource(post);
            if (DIRECT_IMAGE.matcher(url).matches()) {
                images.add(new ImageRef("0", url, source == null ? 0 : source.optInt("width"), source == null ? 0 : source.optInt("height")));
            } else if (source != null && "image".equals(post.optString("post_hint"))) {
                images.add(new ImageRef("0", source.getString("url"), source.optInt("width"), source.optInt("height")));
            }
        }
        return new SavedPost(SOURCE_ID, id, images);
    }

    private static JSONObject previewSource(JSONObject post) {
        JSONObject preview = post.optJSONObject("preview");
        JSONArray imgs = preview == null ? null : preview.optJSONArray("images");
        if (imgs == null || imgs.length() == 0) return null;
        return imgs.optJSONObject(0).optJSONObject("source");
    }
}
