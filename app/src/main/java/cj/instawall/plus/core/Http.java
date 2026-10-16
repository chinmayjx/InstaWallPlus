package cj.instawall.plus.core;

import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

/** The little HTTP the sources need. Errors are typed so callers can decide what to retry. */
public interface Http {
    /** @throws FileNotFoundException on 404, {@link SessionExpiredException}, {@link StatusException} */
    JSONObject getJSON(String url) throws IOException;

    /** @throws FileNotFoundException when the url is gone or expired (any 4xx) */
    void download(String url, Path to) throws IOException;

    class StatusException extends IOException {
        public final int code;

        public StatusException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    class SessionExpiredException extends IOException {
        public SessionExpiredException(String message) {
            super(message);
        }
    }
}
