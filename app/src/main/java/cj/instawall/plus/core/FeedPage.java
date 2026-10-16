package cj.instawall.plus.core;

import org.json.JSONObject;

import java.util.List;

/** One page of a saved feed. nextCursor is null on the last page. */
public final class FeedPage {
    public final List<JSONObject> items;
    public final String nextCursor;

    public FeedPage(List<JSONObject> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor == null || nextCursor.isEmpty() ? null : nextCursor;
    }
}
