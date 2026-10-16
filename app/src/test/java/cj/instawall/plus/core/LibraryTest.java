package cj.instawall.plus.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class LibraryTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    Path files;
    Fakes.FakeHttp http;
    Source reddit;
    Library library;

    @Before
    public void setUp() throws Exception {
        files = tmp.newFolder("files").toPath();
        http = new Fakes.FakeHttp();
        reddit = Source.reddit(files, "me", http);
        library = new Library();
        library.setSources(Collections.singletonList(reddit));
    }

    void savePost(Source s, String id, String... imageUrls) throws Exception {
        if (imageUrls.length == 1) {
            s.posts.put(id, RedditTest.imagePost(id, imageUrls[0]));
        } else {
            String[] mids = new String[imageUrls.length];
            for (int i = 0; i < mids.length; i++) mids[i] = id + "m" + i;
            s.posts.put(id, RedditTest.gallery(id, mids));
            for (String m : mids) http.file("https://preview.redd.it/" + m + ".jpg", "img " + m);
            return;
        }
        http.file(imageUrls[0], "img " + imageUrls[0]);
    }

    @Test
    public void downloadsAnImageOnceAndReusesTheFile() throws Exception {
        savePost(reddit, "a", "https://i.redd.it/a.jpg");
        Path f = library.randomImage(new Random(1));
        assertEquals("a_0.jpg", f.getFileName().toString());
        assertEquals("img https://i.redd.it/a.jpg", new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
        http.requested.clear();
        library.randomImage(new Random(2));
        assertTrue(http.requested.isEmpty());
    }

    @Test
    public void trashedImagesAreNeverPicked() throws Exception {
        savePost(reddit, "a", "https://i.redd.it/a.jpg");
        savePost(reddit, "b", "https://i.redd.it/b.jpg");
        reddit.trash.delete(reddit.fetch(reddit.post("a"), 0));
        for (int i = 0; i < 20; i++) assertEquals("b_0.jpg", library.randomImage(new Random(i)).getFileName().toString());
    }

    @Test
    public void expiredUrlsAreRefreshedOnce() throws Exception {
        reddit.posts.put("a", RedditTest.imagePost("a", "https://i.redd.it/old.jpg"));
        http.respond(Reddit.WEB + "/by_id/t3_a.json?raw_json=1", new JSONObject().put("data", new JSONObject().put("children",
                new JSONArray().put(new JSONObject().put("data", RedditTest.imagePost("a", "https://i.redd.it/new.jpg"))))));
        http.file("https://i.redd.it/new.jpg", "fresh");
        Path f = library.randomImage(new Random(1));
        assertEquals("fresh", new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
        assertEquals("https://i.redd.it/new.jpg", reddit.post("a").images.get(0).url);
        assertFalse(Files.exists(f.resolveSibling(f.getFileName() + ".part")));
    }

    @Test
    public void everySavedPostIsEquallyLikelyAcrossSources() throws Exception {
        Source insta = new Source("instagram", "x", files.resolve("x"), http, null, (id, raw) -> Reddit.parse(raw), id -> null, SyncPolicy.noDelays());
        library.setSources(Arrays.asList(reddit, insta));
        savePost(reddit, "r1", "https://i.redd.it/r1.jpg");
        for (int i = 0; i < 9; i++) savePost(insta, "i" + i, "https://i.redd.it/i" + i + ".jpg");
        int fromReddit = 0;
        Random rnd = new Random(42);
        for (int i = 0; i < 2000; i++) if (reddit.owns(library.randomImage(rnd))) fromReddit++;
        assertTrue("reddit picked " + fromReddit, fromReddit > 120 && fromReddit < 280);
    }

    @Test
    public void disabledSourcesAreLeftOut() throws Exception {
        savePost(reddit, "a", "https://i.redd.it/a.jpg");
        Set<String> off = new HashSet<>(Collections.singletonList("reddit"));
        library.setDisabled(off);
        try {
            library.randomImage(new Random(1));
            fail();
        } catch (FileNotFoundException expected) {
        }
        assertEquals(0, library.totalPosts());
    }

    @Test
    public void locateFindsThePostAndPositionOfAnImage() throws Exception {
        savePost(reddit, "g", "https://i.redd.it/1.jpg", "https://i.redd.it/2.jpg");
        Path f = reddit.fetch(reddit.post("g"), 1);
        Library.Located l = library.locate(f);
        assertNotNull(l);
        assertEquals(reddit, l.source);
        assertEquals(1, l.index);
        assertEquals(2, l.post.images.size());
    }

    @Test
    public void trashRestoreAndPermanentDelete() throws Exception {
        savePost(reddit, "a", "https://i.redd.it/a.jpg");
        Path f = reddit.fetch(reddit.post("a"), 0);
        reddit.trash.delete(f);
        assertFalse(Files.exists(f));
        assertEquals(1, library.trash().size());
        Path back = reddit.trash.restore(library.trash().get(0), reddit.imagesDir());
        assertTrue(Files.exists(back));
        assertFalse(reddit.trash.isDeleted("a", "0"));
        reddit.trash.delete(back);
        reddit.trash.deletePermanently(library.trash().get(0));
        assertTrue(library.trash().isEmpty());
        // still excluded from wallpapers
        assertTrue(reddit.trash.isDeleted("a", "0"));
        // and the record survives a reload
        assertTrue(new Trash(reddit.root).isDeleted("a", "0"));
    }

    @Test
    public void oldInstagramTrashEntriesStillCount() throws Exception {
        Files.createDirectories(reddit.root);
        Files.write(reddit.root.resolve("deleted_images.json"), "{\"555\": {\"postID\": \"444\", \"fileName\": \"444_555.jpg\", \"ts\": 1}}".getBytes(StandardCharsets.UTF_8));
        Trash t = new Trash(reddit.root);
        assertTrue(t.isDeleted("444", "555"));
        assertFalse(t.isDeleted("999", "555"));
    }
}
