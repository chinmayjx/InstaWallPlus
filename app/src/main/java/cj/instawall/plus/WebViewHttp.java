package cj.instawall.plus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cj.instawall.plus.core.Http;

/**
 * Reddit's JSON (saved.json, me.json) only answers a real browser session; plain HTTP clients
 * get "blocked by network security" even when logged in. So JSON requests run as fetch() in a
 * hidden WebView that's on reddit.com and shares the app's cookies. Images download normally.
 * The WebView sits on robots.txt, so none of reddit's own scripts run next to the bridge.
 */
public final class WebViewHttp implements Http {
    static final String TAG = "CJ";
    static final String ORIGIN_PAGE = "https://www.reddit.com/robots.txt";
    static final long TIMEOUT_S = 45;

    private final Context context;
    private final Http downloads;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, Response> responses = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private WebView web;
    private volatile CountDownLatch ready;

    private static final class Response {
        final CountDownLatch done = new CountDownLatch(1);
        int status;
        String body;
    }

    public WebViewHttp(Context context, Http downloads) {
        this.context = context.getApplicationContext();
        this.downloads = downloads;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void ensureReady() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("WebViewHttp blocks, don't call it on the main thread");
        synchronized (this) {
            if (ready == null) {
                ready = new CountDownLatch(1);
                CountDownLatch latch = ready;
                main.post(() -> {
                    web = new WebView(context);
                    web.getSettings().setJavaScriptEnabled(true);
                    web.addJavascriptInterface(new Bridge(), "IWBridge");
                    web.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            latch.countDown();
                        }
                    });
                    web.loadUrl(ORIGIN_PAGE);
                });
            }
        }
        try {
            if (!ready.await(TIMEOUT_S, TimeUnit.SECONDS)) {
                synchronized (this) {
                    ready = null;
                }
                throw new IOException("reddit page didn't load");
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    final class Bridge {
        @JavascriptInterface
        public void done(int id, int status, String body) {
            Response r = responses.get(id);
            if (r == null) return;
            r.status = status;
            r.body = body;
            r.done.countDown();
        }
    }

    @Override
    public JSONObject getJSON(String url) throws IOException {
        ensureReady();
        int id = ids.incrementAndGet();
        Response r = new Response();
        responses.put(id, r);
        String js = "fetch(" + JSONObject.quote(url) + ", {credentials: 'include'})"
                + ".then(r => r.text().then(t => IWBridge.done(" + id + ", r.status, t)))"
                + ".catch(e => IWBridge.done(" + id + ", 0, String(e)));";
        main.post(() -> web.evaluateJavascript(js, null));
        try {
            if (!r.done.await(TIMEOUT_S, TimeUnit.SECONDS)) throw new IOException("timed out: " + url);
        } catch (InterruptedException e) {
            throw new IOException(e);
        } finally {
            responses.remove(id);
        }
        String body = r.body == null ? "" : r.body;
        if (r.status == 200) {
            try {
                return new JSONObject(body);
            } catch (JSONException e) {
                throw new IOException("not JSON from " + url, e);
            }
        }
        Log.e(TAG, "HTTP " + r.status + " from " + url + ": " + body.substring(0, Math.min(200, body.length())));
        if (r.status == 0) throw new IOException("network error: " + body);
        if (r.status == 404) throw new FileNotFoundException(url);
        if (body.contains("blocked by network security")) throw new StatusException(r.status, "reddit blocked the request");
        if (r.status == 401 || r.status == 403) throw new SessionExpiredException("logged out (HTTP " + r.status + ")");
        throw new StatusException(r.status, "HTTP " + r.status + " from " + url);
    }

    @Override
    public void download(String url, Path to) throws IOException {
        downloads.download(url, to);
    }
}
