package cj.instawall.plus.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

public class SourceTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    Path files;
    Fakes.FakeHttp http;

    @Before
    public void setUp() throws Exception {
        files = tmp.newFolder("files").toPath();
        http = new Fakes.FakeHttp();
    }

    static void fill(Source s) throws Exception {
        s.posts.put("p1", RedditTest.imagePost("p1", "https://i.redd.it/p1.jpg"));
        Files.createDirectories(s.imagesDir());
        Files.write(s.imagesDir().resolve("p1_0.jpg"), new byte[]{1});
        Files.createDirectories(s.root.resolve(".deleted_images"));
        Files.write(s.root.resolve(".deleted_images").resolve("p2_0.jpg"), new byte[]{2});
        Files.write(s.root.resolve("deleted_images.json"), "[]".getBytes());
    }

    @Test
    public void deletingAnAccountsDataLeavesTheOthersAlone() throws Exception {
        Source gone = Source.reddit(files, "gone", http);
        Source kept = Source.reddit(files, "kept", http);
        Source insta = Source.instagram(files, "insta", http);
        fill(gone);
        fill(kept);
        fill(insta);
        gone.deleteData();
        assertFalse(Files.exists(gone.root));
        assertTrue(Files.exists(kept.imagesDir().resolve("p1_0.jpg")));
        assertTrue(kept.posts.has("p1"));
        assertTrue(Files.exists(insta.imagesDir().resolve("p1_0.jpg")));
        insta.deleteData();
        assertFalse(Files.exists(insta.root));
        assertTrue(kept.posts.has("p1"));
    }

    @Test
    public void anInstagramAccountNamedRedditKeepsTheRedditAccounts() throws Exception {
        Source insta = Source.instagram(files, "reddit", http);
        Source red = Source.reddit(files, "me", http);
        fill(insta);
        fill(red);
        insta.deleteData();
        assertFalse(Files.exists(insta.imagesDir()));
        assertFalse(Files.exists(insta.root.resolve("saved_posts")));
        assertTrue(Files.exists(red.imagesDir().resolve("p1_0.jpg")));
        assertTrue(red.posts.has("p1"));
    }

    @Test
    public void deletingAnAccountWithNoDataDoesNothing() throws Exception {
        Source.reddit(files, "nobody", http).deleteData();
        assertTrue(Files.exists(files));
    }
}
