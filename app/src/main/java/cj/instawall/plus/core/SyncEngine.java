package cj.instawall.plus.core;

import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

/**
 * Copies a saved feed into a {@link PostStore}.
 *
 * <p>sync(): new saves from the top until it reaches posts it already has, then, if an earlier
 * sync was interrupted, continues that one from its cursor. resume() only does the latter.
 * While paging, the cursor of every finished page is stored, so killing the app or a failure
 * loses at most one page. The top-down pass doesn't store a cursor when an interrupted sync is
 * pending, so it can't overwrite that one.
 */
public final class SyncEngine {
    public interface Listener {
        void onProgress(String status);
    }

    public enum Outcome {CAUGHT_UP, END_OF_FEED, NOTHING_TO_RESUME, FAILED}

    public static final class Result {
        public final Outcome outcome;
        public final int newPosts;
        public final String message;

        Result(Outcome outcome, int newPosts, String message) {
            this.outcome = outcome;
            this.newPosts = newPosts;
            this.message = message;
        }
    }

    private final String name;
    private final SavedFeed feed;
    private final PostStore store;
    private final CursorStore cursors;
    private final String cursorKey;
    private final SyncPolicy policy;
    private final Sleeper sleeper;
    private final Listener listener;
    private final Random random;
    private int newPosts;

    public SyncEngine(String name, SavedFeed feed, PostStore store, CursorStore cursors, String cursorKey,
                      SyncPolicy policy, Sleeper sleeper, Listener listener, Random random) {
        this.name = name;
        this.feed = feed;
        this.store = store;
        this.cursors = cursors;
        this.cursorKey = cursorKey;
        this.policy = policy;
        this.sleeper = sleeper;
        this.listener = listener == null ? s -> {
        } : listener;
        this.random = random == null ? new Random() : random;
    }

    public Result sync() {
        newPosts = 0;
        String pending = cursors.get(cursorKey);
        report("checking for new saves");
        Pass top = page(null, pending == null);
        if (top.failure != null) return failed(top.failure);
        if (pending == null) {
            cursors.remove(cursorKey);
            return done(top.outcome);
        }
        return continueFrom(pending);
    }

    public Result resume() {
        newPosts = 0;
        String pending = cursors.get(cursorKey);
        if (pending == null) return new Result(Outcome.NOTHING_TO_RESUME, 0, "No interrupted " + name + " sync to continue");
        return continueFrom(pending);
    }

    private Result continueFrom(String cursor) {
        report("continuing where the last sync stopped");
        Pass p = page(cursor, true);
        if (p.failure != null) return failed(p.failure);
        cursors.remove(cursorKey);
        return done(p.outcome);
    }

    private Result done(Outcome outcome) {
        return new Result(outcome, newPosts, String.format(Locale.US, "%s sync complete, %,d new posts", name, newPosts));
    }

    private Result failed(Exception e) {
        String reason = e instanceof Http.SessionExpiredException ? name + " session expired, log in again" : String.valueOf(e.getMessage());
        String message = name + " sync stopped: " + reason + ". Continue it from the menu.";
        report(message);
        return new Result(Outcome.FAILED, newPosts, message);
    }

    private static final class Pass {
        Outcome outcome;
        Exception failure;
    }

    private Pass page(String startCursor, boolean saveCursor) {
        Pass pass = new Pass();
        String cursor = startCursor;
        int repeats = 0;
        int pages = 0;
        try {
            while (true) {
                FeedPage page = fetchWithRetry(cursor);
                pages++;
                boolean caughtUp = false;
                for (JSONObject item : page.items) {
                    String id = feed.itemId(item);
                    if (store.has(id)) {
                        if (++repeats >= policy.repeatLimit) caughtUp = true;
                        continue;
                    }
                    store.put(id, item);
                    newPosts++;
                }
                report(String.format(Locale.US, "%,d posts synced, %,d new", store.size(), newPosts));
                if (caughtUp) {
                    pass.outcome = Outcome.CAUGHT_UP;
                    return pass;
                }
                if (page.nextCursor == null) {
                    pass.outcome = Outcome.END_OF_FEED;
                    return pass;
                }
                cursor = page.nextCursor;
                if (saveCursor) cursors.put(cursorKey, cursor);
                long delay = between(policy.pageDelayMinMs, policy.pageDelayMaxMs);
                if (pages % policy.longPauseEveryPages == 0) {
                    delay = between(policy.longPauseMinMs, policy.longPauseMaxMs);
                    report(String.format(Locale.US, "%,d posts synced, %,d new · short break (%ds) to stay under rate limits",
                            store.size(), newPosts, delay / 1000));
                }
                sleeper.sleep(delay);
            }
        } catch (Exception e) {
            pass.failure = e;
            return pass;
        }
    }

    // rate limits / server errors: long backoff; network errors: shorter; anything else: give up
    private FeedPage fetchWithRetry(String cursor) throws Exception {
        long rateLimitDelay = policy.firstRateLimitRetryMs;
        long networkDelay = policy.firstNetworkRetryMs;
        for (int attempt = 0; ; attempt++) {
            try {
                return feed.fetch(cursor);
            } catch (Http.SessionExpiredException | FileNotFoundException e) {
                throw e;
            } catch (Http.StatusException e) {
                if ((e.code != 429 && e.code < 500) || attempt >= policy.maxRetries) throw e;
                report((e.code == 429 ? name + " is rate limiting" : name + " error " + e.code)
                        + ", retrying in " + Math.max(1, rateLimitDelay / 60_000) + " min");
                sleeper.sleep(rateLimitDelay);
                rateLimitDelay *= 2;
            } catch (IOException e) {
                if (attempt >= policy.maxRetries) throw e;
                report("network problem, retrying in " + networkDelay / 1000 + "s");
                sleeper.sleep(networkDelay);
                networkDelay *= 2;
            }
        }
    }

    private void report(String status) {
        listener.onProgress(name + ": " + status);
    }

    private long between(long min, long max) {
        return max <= min ? min : min + (long) (random.nextDouble() * (max - min));
    }
}
