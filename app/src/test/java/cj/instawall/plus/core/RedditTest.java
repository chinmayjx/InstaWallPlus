package cj.instawall.plus.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class RedditTest {
    static JSONObject imagePost(String id, String url) throws Exception {
        return new JSONObject().put("id", id).put("name", "t3_" + id).put("url_overridden_by_dest", url).put("post_hint", "image")
                .put("preview", new JSONObject().put("images", new JSONArray().put(new JSONObject()
                        .put("source", new JSONObject().put("url", "https://preview.redd.it/" + id + ".jpg?width=3840").put("width", 3840).put("height", 2160)))));
    }

    static JSONObject gallery(String id, String... mediaIds) throws Exception {
        JSONArray items = new JSONArray();
        JSONObject meta = new JSONObject();
        for (String m : mediaIds) {
            items.put(new JSONObject().put("media_id", m));
            meta.put(m, new JSONObject().put("status", "valid").put("e", "Image")
                    .put("s", new JSONObject().put("u", "https://preview.redd.it/" + m + ".jpg").put("x", 2000).put("y", 1000)));
        }
        return new JSONObject().put("id", id).put("is_gallery", true).put("gallery_data", new JSONObject().put("items", items)).put("media_metadata", meta);
    }

    @Test
    public void directImageLinkIsUsedAsIs() throws Exception {
        SavedPost p = Reddit.parse(imagePost("abc", "https://i.redd.it/xyz.png"));
        assertEquals(1, p.images.size());
        assertEquals("https://i.redd.it/xyz.png", p.images.get(0).url);
        assertEquals(3840, p.images.get(0).width);
        assertEquals("abc", p.id);
    }

    @Test
    public void otherLinksFallBackToThePreviewImage() throws Exception {
        SavedPost p = Reddit.parse(imagePost("abc", "https://www.flickr.com/photos/1"));
        assertEquals("https://preview.redd.it/abc.jpg?width=3840", p.images.get(0).url);
    }

    @Test
    public void galleryKeepsTheGalleryOrderAndSkipsBrokenOrAnimatedItems() throws Exception {
        JSONObject g = gallery("g1", "m1", "m2", "m3");
        g.getJSONObject("media_metadata").getJSONObject("m2").put("status", "failed");
        g.getJSONObject("media_metadata").getJSONObject("m3").put("e", "AnimatedImage");
        g.getJSONObject("gallery_data").getJSONArray("items").put(0, new JSONObject().put("media_id", "m3"));
        g.getJSONObject("gallery_data").getJSONArray("items").put(2, new JSONObject().put("media_id", "m1"));
        SavedPost p = Reddit.parse(g);
        assertEquals(1, p.images.size());
        assertEquals("m1", p.images.get(0).id);
    }

    @Test
    public void videosAndTextPostsHaveNoImages() throws Exception {
        assertTrue(Reddit.parse(imagePost("v", "https://v.redd.it/x").put("is_video", true)).images.isEmpty());
        assertTrue(Reddit.parse(new JSONObject().put("id", "t").put("url", "https://www.reddit.com/r/x/comments/t/")).images.isEmpty());
    }

    @Test
    public void feedKeepsImagePostsOnlyAndFollowsAfter() throws Exception {
        JSONArray children = new JSONArray()
                .put(new JSONObject().put("kind", "t3").put("data", imagePost("a", "https://i.redd.it/a.jpg")))
                .put(new JSONObject().put("kind", "t1").put("data", new JSONObject().put("id", "comment")))
                .put(new JSONObject().put("kind", "t3").put("data", new JSONObject().put("id", "text").put("url", "https://reddit.com/x")))
                .put(new JSONObject().put("kind", "t3").put("data", gallery("g", "m1", "m2")));
        Fakes.FakeHttp http = new Fakes.FakeHttp()
                .respond(Reddit.WEB + "/user/me/saved.json?limit=100&raw_json=1", new JSONObject().put("data", new JSONObject().put("after", "t3_g").put("children", children)))
                .respond(Reddit.WEB + "/user/me/saved.json?limit=100&raw_json=1&after=t3_g", new JSONObject().put("data", new JSONObject().put("after", JSONObject.NULL).put("children", new JSONArray())));
        Reddit.Feed feed = new Reddit.Feed(http, "me");
        FeedPage first = feed.fetch(null);
        assertEquals(2, first.items.size());
        assertEquals("a", feed.itemId(first.items.get(0)));
        assertEquals("t3_g", first.nextCursor);
        assertNull(feed.fetch("t3_g").nextCursor);
    }
}
