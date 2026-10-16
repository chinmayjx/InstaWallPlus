package cj.instawall.plus.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/** All logged in sources together: the wallpaper pool, the grid's lists and finding an image's post. */
public final class Library {
    private final List<Source> sources = new CopyOnWriteArrayList<>();
    private volatile Set<String> disabled = java.util.Collections.emptySet();

    public void setSources(List<Source> list) {
        sources.clear();
        sources.addAll(list);
    }

    public List<Source> sources() {
        return new ArrayList<>(sources);
    }

    /** source ids (instagram, reddit) to leave out of wallpapers and the grid */
    public void setDisabled(Set<String> ids) {
        disabled = ids;
    }

    public List<Source> enabled() {
        List<Source> l = new ArrayList<>();
        for (Source s : sources) if (!disabled.contains(s.id)) l.add(s);
        return l;
    }

    public Source source(String id) {
        for (Source s : sources) if (s.id.equals(id)) return s;
        return null;
    }

    public int totalPosts() {
        int n = 0;
        for (Source s : enabled()) n += s.posts.size();
        return n;
    }

    /**
     * A random image that isn't in the trash, downloaded. Sources are weighted by how many posts
     * they have, so every saved post is equally likely.
     */
    public Path randomImage(Random random) throws IOException {
        List<Source> pool = new ArrayList<>();
        for (Source s : enabled()) if (s.posts.size() > 0) pool.add(s);
        if (pool.isEmpty()) throw new FileNotFoundException("no saved posts yet");
        int total = 0;
        for (Source s : pool) total += s.posts.size();
        IOException last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            int k = random.nextInt(total);
            Source src = null;
            for (Source s : pool) {
                if (k < s.posts.size()) {
                    src = s;
                    break;
                }
                k -= s.posts.size();
            }
            List<String> ids = src.posts.ids();
            if (ids.isEmpty()) continue;
            SavedPost post = src.post(ids.get(random.nextInt(ids.size())));
            if (post == null || post.images.isEmpty()) continue;
            List<Integer> usable = new ArrayList<>();
            for (int i = 0; i < post.images.size(); i++) if (!src.trash.isDeleted(post.id, post.images.get(i).id)) usable.add(i);
            if (usable.isEmpty()) continue;
            try {
                return src.fetch(post, usable.get(random.nextInt(usable.size())));
            } catch (IOException e) {
                last = e; // deleted post, network hiccup: try another one
            } catch (org.json.JSONException e) {
                last = new IOException(e);
            }
        }
        throw last != null ? last : new FileNotFoundException("no usable images");
    }

    public static final class Located {
        public final Source source;
        public final SavedPost post;
        public final int index;

        Located(Source source, SavedPost post, int index) {
            this.source = source;
            this.post = post;
            this.index = index;
        }
    }

    /** the post an image file belongs to, or null */
    public Located locate(Path image) {
        for (Source s : sources) {
            if (!s.owns(image)) continue;
            ImageName n;
            try {
                n = ImageName.of(image);
            } catch (IllegalArgumentException e) {
                return null;
            }
            SavedPost p = s.post(n.postId);
            if (p == null) return null;
            return new Located(s, p, Math.max(0, p.indexOf(n.imageId)));
        }
        return null;
    }

    public Source owner(Path file) {
        for (Source s : sources) if (s.owns(file)) return s;
        return null;
    }

    /** downloaded images of enabled sources, newest first */
    public List<Path> downloaded() {
        List<Path> all = new ArrayList<>();
        for (Source s : enabled()) {
            if (!Files.isDirectory(s.imagesDir())) continue;
            try (Stream<Path> files = Files.list(s.imagesDir())) {
                files.filter(p -> p.toString().endsWith(".jpg")).forEach(all::add);
            } catch (IOException ignored) {
            }
        }
        all.sort(Comparator.comparingLong(Library::modified).reversed());
        return all;
    }

    public List<Path> trash() {
        List<Path> all = new ArrayList<>();
        for (Source s : enabled()) all.addAll(s.trash.list());
        return all;
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
