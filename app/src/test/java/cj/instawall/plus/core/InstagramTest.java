package cj.instawall.plus.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class InstagramTest {
    static JSONObject candidate(int w, int h, String url) throws Exception {
        return new JSONObject().put("width", w).put("height", h).put("url", url);
    }

    static JSONObject media(String pk, int ow, int oh, JSONObject... candidates) throws Exception {
        JSONArray c = new JSONArray();
        for (JSONObject x : candidates) c.put(x);
        return new JSONObject().put("pk", pk).put("original_width", ow).put("original_height", oh)
                .put("image_versions2", new JSONObject().put("candidates", c));
    }

    @Test
    public void singleImagePostPrefersTheOriginalSize() throws Exception {
        JSONObject m = media("1", 1080, 1350, candidate(640, 800, "small"), candidate(1080, 1350, "orig"), candidate(1440, 1800, "upscaled"));
        SavedPost p = Instagram.parse("1", new JSONObject().put("media", m));
        assertEquals(1, p.images.size());
        assertEquals("orig", p.images.get(0).url);
        assertEquals("1", p.images.get(0).id);
    }

    @Test
    public void withoutTheOriginalSizeTheLargestWins() throws Exception {
        JSONObject m = media("1", 999, 999, candidate(640, 800, "small"), candidate(1440, 1800, "big"));
        assertEquals("big", Instagram.parse("1", new JSONObject().put("media", m)).images.get(0).url);
    }

    @Test
    public void carouselHasEveryImageInOrder() throws Exception {
        JSONObject post = new JSONObject().put("pk", "10").put("carousel_media", new JSONArray()
                .put(media("11", 100, 100, candidate(100, 100, "a")))
                .put(media("12", 100, 100, candidate(100, 100, "b"))));
        SavedPost p = Instagram.parse("10", new JSONObject().put("media", post));
        assertEquals(2, p.images.size());
        assertEquals("12", p.images.get(1).id);
        assertEquals(1, p.indexOf("12"));
    }

    @Test
    public void shortcodeDecodesToTheMediaId() throws Exception {
        assertEquals("2943136452138208428", Instagram.shortcodeToMediaId("CjYHwKDM4ys"));
        // private post codes are longer, the id is in the first 11 characters
        assertEquals("2943136452138208428", Instagram.shortcodeToMediaId("CjYHwKDM4ysAAAAAAAAAAAAAAAAAAA"));
    }

    @Test
    public void feedPagesUntilMoreAvailableIsFalse() throws Exception {
        Fakes.FakeHttp http = new Fakes.FakeHttp()
                .respond(Instagram.API + "feed/saved/posts/", new JSONObject().put("more_available", true).put("next_max_id", "abc")
                        .put("items", new JSONArray().put(new JSONObject().put("media", new JSONObject().put("pk", "1")))))
                .respond(Instagram.API + "feed/saved/posts/?max_id=abc", new JSONObject().put("more_available", false).put("next_max_id", "")
                        .put("items", new JSONArray()));
        Instagram.Feed feed = new Instagram.Feed(http);
        FeedPage first = feed.fetch(null);
        assertEquals("abc", first.nextCursor);
        assertEquals("1", feed.itemId(first.items.get(0)));
        assertNull(feed.fetch("abc").nextCursor);
    }
}
