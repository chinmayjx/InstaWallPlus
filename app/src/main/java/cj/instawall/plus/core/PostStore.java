package cj.instawall.plus.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Raw saved posts, one file per post (named by id) in a directory. */
public final class PostStore {
    private final Path dir;
    private List<String> ids;

    public PostStore(Path dir) {
        this.dir = dir;
    }

    public synchronized List<String> ids() {
        if (ids == null) {
            ids = new CopyOnWriteArrayList<>();
            try {
                Files.createDirectories(dir);
                try (Stream<Path> s = Files.list(dir)) {
                    ids.addAll(s.map(p -> p.getFileName().toString()).collect(Collectors.toList()));
                }
            } catch (IOException ignored) {
            }
        }
        return Collections.unmodifiableList(ids);
    }

    public int size() {
        return ids().size();
    }

    public boolean has(String id) {
        return ids().contains(id);
    }

    public synchronized void put(String id, JSONObject raw) throws IOException, JSONException {
        Files.createDirectories(dir);
        Files.write(dir.resolve(id), raw.toString(2).getBytes(StandardCharsets.UTF_8));
        ids();
        if (!ids.contains(id)) ids.add(id);
    }

    public JSONObject get(String id) {
        try {
            return new JSONObject(new String(Files.readAllBytes(dir.resolve(id)), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> snapshot() {
        return new ArrayList<>(ids());
    }
}
