package cj.instawall.plus.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One logged in account of a service: where its posts and images live, how to fetch its saved
 * feed and how to read its posts. Layout under root: saved_posts/, images/, deleted_images.json,
 * .deleted_images/ (the original instagram layout).
 */
public final class Source {
    public interface Parser {
        SavedPost parse(String postId, JSONObject raw) throws JSONException;
    }

    public interface Refresher {
        JSONObject refresh(String postId) throws IOException, JSONException;
    }

    public final String id;
    public final String account;
    public final Path root;
    public final PostStore posts;
    public final Trash trash;
    public final SavedFeed feed;
    public final SyncPolicy policy;
    private final Http http;
    private final Parser parser;
    private final Refresher refresher;

    public Source(String id, String account, Path root, Http http, SavedFeed feed, Parser parser, Refresher refresher, SyncPolicy policy) {
        this.id = id;
        this.account = account;
        this.root = root;
        this.http = http;
        this.feed = feed;
        this.parser = parser;
        this.refresher = refresher;
        this.policy = policy;
        this.posts = new PostStore(root.resolve("saved_posts"));
        this.trash = new Trash(root);
    }

    public static Source instagram(Path filesDir, String account, Http http) {
        return new Source(Instagram.SOURCE_ID, account, filesDir.resolve(account), http, new Instagram.Feed(http),
                Instagram::parse, postId -> Instagram.refresh(http, postId), SyncPolicy.instagram());
    }

    public static Source reddit(Path filesDir, String account, Http http) {
        return new Source(Reddit.SOURCE_ID, account, filesDir.resolve("reddit").resolve(account), http, new Reddit.Feed(http, account),
                (postId, raw) -> Reddit.parse(raw), postId -> Reddit.refresh(http, postId), SyncPolicy.reddit());
    }

    public String label() {
        return Reddit.SOURCE_ID.equals(id) ? "u/" + account : "@" + account;
    }

    public Path imagesDir() {
        return root.resolve("images");
    }

    public boolean owns(Path file) {
        return file.startsWith(root);
    }

    public SavedPost post(String postId) {
        JSONObject raw = posts.get(postId);
        if (raw == null) return null;
        try {
            return parser.parse(postId, raw);
        } catch (JSONException e) {
            return null;
        }
    }

    public Path file(String postId, String imageId) {
        return imagesDir().resolve(ImageName.fileName(postId, imageId));
    }

    /**
     * The image's file, downloading it if needed. If the stored url expired, the post is fetched
     * again once.
     *
     * @throws FileNotFoundException when the post or image is gone
     */
    public Path fetch(SavedPost post, int index) throws IOException, JSONException {
        ImageRef ref = post.images.get(index);
        Path f = file(post.id, ref.id);
        if (Files.exists(f)) return f;
        Files.createDirectories(f.getParent());
        try {
            download(ref.url, f);
        } catch (FileNotFoundException expired) {
            JSONObject fresh = refresher.refresh(post.id);
            posts.put(post.id, fresh);
            SavedPost p = parser.parse(post.id, fresh);
            int i = p.indexOf(ref.id);
            if (i < 0) throw new FileNotFoundException("image " + ref.id + " is no longer in post " + post.id);
            download(p.images.get(i).url, f);
        }
        return f;
    }

    // via a temp file so a failed download doesn't leave half an image behind
    private void download(String url, Path f) throws IOException {
        Path tmp = f.resolveSibling(f.getFileName() + ".part");
        try {
            http.download(url, tmp);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
