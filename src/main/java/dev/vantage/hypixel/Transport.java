package dev.vantage.hypixel;

import java.io.IOException;

/**
 * The HTTP call itself, kept behind an interface so the client's retry, rate limiting and parsing
 * can be tested without touching the network.
 */
public interface Transport {

    /** One response: status line, body text, and the rate limit headers if present. */
    final class Response {
        public final int statusCode;
        public final String body;
        public final Integer rateLimitRemaining;
        public final Integer rateLimitResetSeconds;

        public Response(int statusCode, String body, Integer rateLimitRemaining, Integer rateLimitResetSeconds) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
            this.rateLimitRemaining = rateLimitRemaining;
            this.rateLimitResetSeconds = rateLimitResetSeconds;
        }
    }

    Response get(String url, String apiKey) throws IOException;
}
