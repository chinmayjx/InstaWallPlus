package cj.instawall.plus.core;

/** Pacing for a saved feed; instagram rate limits accounts that page through it too fast. */
public final class SyncPolicy {
    public long pageDelayMinMs, pageDelayMaxMs;
    public int longPauseEveryPages;
    public long longPauseMinMs, longPauseMaxMs;
    public int maxRetries = 4;
    public long firstRateLimitRetryMs = 120_000, firstNetworkRetryMs = 30_000;
    /** this many already synced posts in a row means we caught up */
    public int repeatLimit = 5;

    public static SyncPolicy instagram() {
        SyncPolicy p = new SyncPolicy();
        p.pageDelayMinMs = 6_000;
        p.pageDelayMaxMs = 12_000;
        p.longPauseEveryPages = 25;
        p.longPauseMinMs = 60_000;
        p.longPauseMaxMs = 120_000;
        return p;
    }

    public static SyncPolicy reddit() {
        SyncPolicy p = new SyncPolicy();
        p.pageDelayMinMs = 2_000;
        p.pageDelayMaxMs = 4_000;
        p.longPauseEveryPages = 20;
        p.longPauseMinMs = 30_000;
        p.longPauseMaxMs = 60_000;
        return p;
    }

    public static SyncPolicy noDelays() {
        SyncPolicy p = new SyncPolicy();
        p.longPauseEveryPages = Integer.MAX_VALUE;
        p.firstRateLimitRetryMs = 0;
        p.firstNetworkRetryMs = 0;
        return p;
    }
}
