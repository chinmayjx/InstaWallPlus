package cj.instawall.plus.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Deleted images of one source account. deleted_images.json maps POSTID_IMAGEID to
 * {postID, fileName, ts} (older instagram entries use the bare image id, which is unique there);
 * the files are moved to .deleted_images. Permanently deleting removes
 * the file but keeps the entry, so the image is never picked as a wallpaper again.
 */
public final class Trash {
    private final Path index;
    private final Path dir;
    private JSONObject entries;

    public Trash(Path root) {
        this.index = root.resolve("deleted_images.json");
        this.dir = root.resolve(".deleted_images");
    }

    private synchronized JSONObject entries() {
        if (entries == null) {
            try {
                entries = new JSONObject(new String(Files.readAllBytes(index), StandardCharsets.UTF_8));
            } catch (Exception e) {
                entries = new JSONObject();
            }
        }
        return entries;
    }

    private static String key(String postId, String imageId) {
        return postId + "_" + imageId;
    }

    public synchronized boolean isDeleted(String postId, String imageId) {
        if (entries().has(key(postId, imageId))) return true;
        JSONObject legacy = entries().optJSONObject(imageId);
        return legacy != null && postId.equals(legacy.optString("postID"));
    }

    public synchronized void delete(Path image) throws IOException, JSONException {
        ImageName n = ImageName.of(image);
        Files.createDirectories(dir);
        entries().put(key(n.postId, n.imageId), new JSONObject().put("postID", n.postId).put("fileName", image.getFileName().toString())
                .put("ts", System.currentTimeMillis()));
        Files.move(image, dir.resolve(image.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        save();
    }

    /** @return where the image is now */
    public synchronized Path restore(Path trashed, Path imagesDir) throws IOException, JSONException {
        ImageName n = ImageName.of(trashed);
        entries().remove(key(n.postId, n.imageId));
        entries().remove(n.imageId);
        Files.createDirectories(imagesDir);
        Path to = imagesDir.resolve(trashed.getFileName());
        Files.move(trashed, to, StandardCopyOption.REPLACE_EXISTING);
        save();
        return to;
    }

    public synchronized void deletePermanently(Path trashed) throws IOException {
        Files.deleteIfExists(trashed);
    }

    /** trashed files that still exist, newest first */
    public synchronized List<Path> list() {
        List<Path> files = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        Iterator<String> it = entries().keys();
        while (it.hasNext()) {
            JSONObject e = entries().optJSONObject(it.next());
            if (e == null) continue;
            Path f = dir.resolve(e.optString("fileName"));
            if (!Files.exists(f)) continue;
            long ts = e.optLong("ts");
            int i = 0;
            while (i < times.size() && times.get(i) > ts) i++;
            files.add(i, f);
            times.add(i, ts);
        }
        return files;
    }

    public boolean owns(Path p) {
        return p.startsWith(dir);
    }

    private void save() throws IOException, JSONException {
        Files.createDirectories(index.getParent());
        Files.write(index, entries().toString(2).getBytes(StandardCharsets.UTF_8));
    }
}
