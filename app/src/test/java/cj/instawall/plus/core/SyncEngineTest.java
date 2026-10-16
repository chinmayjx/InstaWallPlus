package cj.instawall.plus.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SyncEngineTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    PostStore store;
    Fakes.MapCursors cursors;
    Fakes.RecordingSleeper sleeper;
    List<String> progress;
    static final String KEY = "cursor:test";

    @Before
    public void setUp() throws Exception {
        store = new PostStore(tmp.newFolder("posts").toPath());
        cursors = new Fakes.MapCursors();
        sleeper = new Fakes.RecordingSleeper();
        progress = new ArrayList<>();
    }

    SyncEngine engine(SavedFeed feed) {
        return engine(feed, SyncPolicy.noDelays());
    }

    SyncEngine engine(SavedFeed feed, SyncPolicy policy) {
        return new SyncEngine("Test", feed, store, cursors, KEY, policy, sleeper, progress::add, new Random(1));
    }

    @Test
    public void firstSyncCopiesTheWholeFeedAndClearsTheCursor() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(25, 10);
        SyncEngine.Result r = engine(feed).sync();
        assertEquals(SyncEngine.Outcome.END_OF_FEED, r.outcome);
        assertEquals(25, r.newPosts);
        assertEquals(25, store.size());
        assertNull(cursors.get(KEY));
    }

    @Test
    public void laterSyncStopsOnceItReachesKnownPosts() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(50, 10);
        engine(feed).sync();
        feed.addNewest(3);
        feed.fetched.clear();
        SyncEngine.Result r = engine(feed).sync();
        assertEquals(SyncEngine.Outcome.CAUGHT_UP, r.outcome);
        assertEquals(3, r.newPosts);
        assertEquals(1, feed.fetched.size());
    }

    @Test
    public void failureKeepsTheCursorOfTheLastFinishedPage() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(50, 10);
        feed.failOnce("c30", new Http.SessionExpiredException("logged out"));
        SyncEngine.Result r = engine(feed).sync();
        assertEquals(SyncEngine.Outcome.FAILED, r.outcome);
        assertEquals(30, store.size());
        assertEquals("c30", cursors.get(KEY));
        assertTrue(r.message.contains("session expired"));
    }

    @Test
    public void resumeContinuesFromTheCursorWithoutGapsOrDuplicates() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(50, 10);
        feed.failOnce("c30", new Http.SessionExpiredException("logged out"));
        engine(feed).sync();
        feed.fetched.clear();
        SyncEngine.Result r = engine(feed).resume();
        assertEquals(SyncEngine.Outcome.END_OF_FEED, r.outcome);
        assertEquals("c30", feed.fetched.get(0));
        assertEquals(20, r.newPosts);
        assertEquals(50, store.size());
        assertNull(cursors.get(KEY));
    }

    @Test
    public void syncAfterAnInterruptionChecksTheTopThenContinuesWithoutOverwritingTheCursor() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(50, 10);
        feed.failOnce("c30", new Http.SessionExpiredException("logged out"));
        engine(feed).sync();
        feed.addNewest(2);
        feed.fetched.clear();
        SyncEngine.Result r = engine(feed).sync();
        // top page first (new saves), then straight to the interrupted position
        assertNull(feed.fetched.get(0));
        assertEquals("c30", feed.fetched.get(1));
        assertEquals(22, r.newPosts);
        assertEquals(52, store.size());
        assertNull(cursors.get(KEY));
    }

    @Test
    public void resumeWithoutAnInterruptedSyncDoesNothing() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(5, 10);
        assertEquals(SyncEngine.Outcome.NOTHING_TO_RESUME, engine(feed).resume().outcome);
        assertTrue(feed.fetched.isEmpty());
    }

    @Test
    public void rateLimitsAreRetriedWithDoublingWaits() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(20, 10);
        feed.failOnce("c10", new Http.StatusException(429, "slow down"));
        feed.failOnce("c10", new Http.StatusException(429, "slow down"));
        SyncPolicy p = SyncPolicy.noDelays();
        p.firstRateLimitRetryMs = 1000;
        SyncEngine.Result r = engine(feed, p).sync();
        assertEquals(SyncEngine.Outcome.END_OF_FEED, r.outcome);
        assertEquals(20, store.size());
        assertTrue(sleeper.sleeps.contains(1000L));
        assertTrue(sleeper.sleeps.contains(2000L));
        assertTrue(progress.stream().anyMatch(s -> s.contains("rate limiting")));
    }

    @Test
    public void networkErrorsAreRetried() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(20, 10);
        feed.failOnce("c10", new SocketTimeoutException("timeout"));
        SyncEngine.Result r = engine(feed).sync();
        assertEquals(SyncEngine.Outcome.END_OF_FEED, r.outcome);
        assertEquals(20, store.size());
    }

    @Test
    public void givesUpAfterMaxRetriesAndKeepsTheCursor() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(20, 10);
        for (int i = 0; i < 10; i++) feed.failOnce("c10", new Http.StatusException(503, "down"));
        SyncEngine.Result r = engine(feed).sync();
        assertEquals(SyncEngine.Outcome.FAILED, r.outcome);
        assertEquals("c10", cursors.get(KEY));
    }

    @Test
    public void clientErrorsAreNotRetried() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(20, 10);
        feed.failOnce("c10", new Http.StatusException(400, "bad"));
        engine(feed).sync();
        assertEquals(2, feed.fetched.size());
    }

    @Test
    public void pacesPagesAndTakesLongBreaks() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(60, 10);
        SyncPolicy p = SyncPolicy.noDelays();
        p.pageDelayMinMs = 100;
        p.pageDelayMaxMs = 200;
        p.longPauseEveryPages = 3;
        p.longPauseMinMs = 5000;
        p.longPauseMaxMs = 6000;
        engine(feed, p).sync();
        // 6 pages, sleeps after the first 5
        assertEquals(5, sleeper.sleeps.size());
        for (int i = 0; i < 5; i++) {
            long s = sleeper.sleeps.get(i);
            if (i == 2) assertTrue(s >= 5000 && s < 6000);
            else assertTrue(s >= 100 && s < 200);
        }
    }

    @Test
    public void storeKeepsItemsUnderTheFeedsId() throws Exception {
        Fakes.ListFeed feed = new Fakes.ListFeed(3, 10);
        engine(feed).sync();
        assertTrue(store.has("p0"));
        assertEquals("p2", store.get("p2").getString("id"));
        assertFalse(store.has("p3"));
    }
}
