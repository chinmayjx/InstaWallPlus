package cj.instawall.plus.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/** A paged list of saved posts, newest first. */
public interface SavedFeed {
    /** @param cursor null for the first (newest) page */
    FeedPage fetch(String cursor) throws IOException, JSONException;

    /** id of a raw item, the same id the store keeps it under */
    String itemId(JSONObject item) throws JSONException;
}
