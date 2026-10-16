package cj.instawall.plus.core;

import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Fakes {
    private Fakes() {
    }

    /** Responses per url; a queued exception is thrown once before falling back to the response. */
    static final class FakeHttp implements Http {
        final Map<String, JSONObject> json = new HashMap<>();
        final Map<String, Deque<IOException>> failures = new HashMap<>();
        final Map<String, String> files = new HashMap<>();
        final List<String> requested = new ArrayList<>();

        FakeHttp respond(String url, JSONObject res) {
            json.put(url, res);
            return this;
        }

        FakeHttp failOnce(String url, IOException e) {
            failures.computeIfAbsent(url, k -> new ArrayDeque<>()).add(e);
            return this;
        }

        FakeHttp file(String url, String content) {
            files.put(url, content);
            return this;
        }

        @Override
        public JSONObject getJSON(String url) throws IOException {
            requested.add(url);
            Deque<IOException> f = failures.get(url);
            if (f != null && !f.isEmpty()) throw f.poll();
            JSONObject res = json.get(url);
            if (res == null) throw new FileNotFoundException(url);
            return res;
        }

        @Override
        public void download(String url, Path to) throws IOException {
            requested.add(url);
            String content = files.get(url);
            if (content == null) throw new FileNotFoundException(url);
            Files.write(to, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    static final class MapCursors implements CursorStore {
        final Map<String, String> map = new HashMap<>();

        @Override
        public String get(String key) {
            return map.get(key);
        }

        @Override
        public void put(String key, String cursor) {
            map.put(key, cursor);
        }

        @Override
        public void remove(String key) {
            map.remove(key);
        }
    }

    static final class RecordingSleeper implements Sleeper {
        final List<Long> sleeps = new ArrayList<>();

        @Override
        public void sleep(long ms) {
            sleeps.add(ms);
        }
    }

    /** a feed of numbered posts "p0".."pN", pageSize per page, cursors "c<index>" */
    static final class ListFeed implements SavedFeed {
        final List<String> ids = new ArrayList<>();
        final int pageSize;
        final Map<String, Deque<IOException>> failures = new HashMap<>();
        final List<String> fetched = new ArrayList<>();

        ListFeed(int count, int pageSize) {
            for (int i = 0; i < count; i++) ids.add("p" + i);
            this.pageSize = pageSize;
        }

        /** newest first: prepend */
        void addNewest(int n) {
            for (int i = 0; i < n; i++) ids.add(0, "new" + i);
        }

        void failOnce(String cursor, IOException e) {
            failures.computeIfAbsent(String.valueOf(cursor), k -> new ArrayDeque<>()).add(e);
        }

        @Override
        public FeedPage fetch(String cursor) throws IOException, org.json.JSONException {
            fetched.add(cursor);
            Deque<IOException> f = failures.get(String.valueOf(cursor));
            if (f != null && !f.isEmpty()) throw f.poll();
            int start = cursor == null ? 0 : Integer.parseInt(cursor.substring(1));
            List<JSONObject> items = new ArrayList<>();
            for (int i = start; i < Math.min(ids.size(), start + pageSize); i++) items.add(new JSONObject().put("id", ids.get(i)));
            String next = start + pageSize < ids.size() ? "c" + (start + pageSize) : null;
            return new FeedPage(items, next);
        }

        @Override
        public String itemId(JSONObject item) throws org.json.JSONException {
            return item.getString("id");
        }
    }
}
