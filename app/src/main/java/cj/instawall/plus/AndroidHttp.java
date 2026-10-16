package cj.instawall.plus;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import cj.instawall.plus.core.Http;

/** {@link Http} over HttpURLConnection. API requests get the source's headers (cookie etc.), downloads don't. */
public class AndroidHttp implements Http {
    static final String TAG = "CJ";
    private final String apiHost;
    private final Supplier<Map<String, String>> headers;
    private final String userAgent;
    private final Set<Integer> sessionExpiredCodes;

    /**
     * @param apiHost             requests to this host (and its subdomains) get the headers
     * @param sessionExpiredCodes status codes that mean "logged out" for this service
     */
    public AndroidHttp(String apiHost, Supplier<Map<String, String>> headers, String userAgent, Set<Integer> sessionExpiredCodes) {
        this.apiHost = apiHost;
        this.headers = headers;
        this.userAgent = userAgent;
        this.sessionExpiredCodes = sessionExpiredCodes;
    }

    private HttpURLConnection open(String url, boolean api) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20_000);
        con.setReadTimeout(30_000);
        if (userAgent != null) con.setRequestProperty("User-Agent", userAgent);
        if (api && con.getURL().getHost().endsWith(apiHost)) {
            for (Map.Entry<String, String> h : headers.get().entrySet()) con.setRequestProperty(h.getKey(), h.getValue());
            // a logged out session redirects to the login page, don't follow it
            con.setInstanceFollowRedirects(false);
        }
        return con;
    }

    @Override
    public JSONObject getJSON(String url) throws IOException {
        HttpURLConnection con = open(url, true);
        int code = con.getResponseCode();
        if (code != 200) {
            String body = con.getErrorStream() == null ? "" : readAll(con.getErrorStream());
            if (body.length() > 300) body = body.substring(0, 300);
            Log.e(TAG, "HTTP " + code + " from " + url + ": " + body);
            if (code == 404) throw new FileNotFoundException(url);
            if ((code >= 300 && code < 400) || sessionExpiredCodes.contains(code) || body.contains("login_required"))
                throw new SessionExpiredException("logged out (HTTP " + code + ")");
            throw new StatusException(code, "HTTP " + code + " from " + url);
        }
        try {
            return new JSONObject(readAll(con.getInputStream()));
        } catch (JSONException e) {
            throw new IOException("not JSON from " + url, e);
        }
    }

    @Override
    public void download(String url, Path to) throws IOException {
        HttpURLConnection con = open(url, false);
        int code = con.getResponseCode();
        if (code >= 400) throw new FileNotFoundException("HTTP " + code + " for " + url);
        try (InputStream in = con.getInputStream()) {
            Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String readAll(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
    }

    /** value of a cookie in a Cookie header string */
    static String cookieValue(String cookie, String name) {
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
        }
        return null;
    }
}
