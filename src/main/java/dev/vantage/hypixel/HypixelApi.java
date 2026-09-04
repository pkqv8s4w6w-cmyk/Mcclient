package dev.vantage.hypixel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.UUID;

/**
 * Client for the public Hypixel API.
 *
 * <p>Only reads {@code /v2/player}, which is what the Bedwars record lives on. The key travels in
 * the {@code API-Key} header, never in the query string, so it cannot leak through a proxy log or
 * a redirect.
 */
public final class HypixelApi {

    private static final String PLAYER_ENDPOINT = "https://api.hypixel.net/v2/player?uuid=";

    private final Transport transport;
    private final RateLimiter rateLimiter;

    public HypixelApi(Transport transport, RateLimiter rateLimiter) {
        this.transport = transport;
        this.rateLimiter = rateLimiter;
    }

    public static HypixelApi live() {
        return new HypixelApi(new HttpTransport(), RateLimiter.hypixelDefault(RateLimiter.SYSTEM));
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    /** Strips the dashes Minecraft uses; the API wants the bare 32 hex characters. */
    static String undashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    public ApiResult fetch(UUID uuid, String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ApiResult.failure(ApiResult.Status.INVALID_KEY, "no API key set");
        }
        if (!rateLimiter.tryAcquire()) {
            return ApiResult.failure(ApiResult.Status.RATE_LIMITED,
                    "budget spent, resets in " + (rateLimiter.millisUntilReset() / 1000) + "s");
        }

        Transport.Response response;
        try {
            response = transport.get(PLAYER_ENDPOINT + undashed(uuid), apiKey);
        } catch (IOException failure) {
            // Deliberately does not include the exception message in anything user-visible that
            // could echo the request, to keep the key out of logs.
            return ApiResult.failure(ApiResult.Status.UNAVAILABLE, "could not reach the API");
        }

        rateLimiter.adopt(response.rateLimitRemaining, response.rateLimitResetSeconds);

        switch (response.statusCode) {
            case 200:
                return parseBody(response.body);
            case 403:
                return ApiResult.failure(ApiResult.Status.INVALID_KEY, "the API rejected this key");
            case 429:
                rateLimiter.exhaust(response.rateLimitResetSeconds);
                return ApiResult.failure(ApiResult.Status.RATE_LIMITED, "rate limited by the API");
            default:
                return ApiResult.failure(ApiResult.Status.UNAVAILABLE, "API returned " + response.statusCode);
        }
    }

    private ApiResult parseBody(String body) {
        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (json.has("success") && !json.get("success").getAsBoolean()) {
                String cause = json.has("cause") ? json.get("cause").getAsString() : "unknown";
                // An unsuccessful 200 with a key complaint is still a key problem.
                boolean keyProblem = cause.toLowerCase(java.util.Locale.ROOT).contains("key");
                return ApiResult.failure(
                        keyProblem ? ApiResult.Status.INVALID_KEY : ApiResult.Status.UNAVAILABLE, cause);
            }
            return ApiResult.ok(BedwarsStats.parse(json));
        } catch (RuntimeException malformed) {
            return ApiResult.failure(ApiResult.Status.MALFORMED, "unexpected response body");
        }
    }

    /**
     * Checks a key by making one real request.
     *
     * <p>There is no cheaper endpoint that validates a key on its own, so this spends a request
     * from the budget. Worth it: the alternative is the user wondering why nothing populates.
     */
    public ApiResult validate(String apiKey) {
        // A known, permanent account, so the call exercises the key rather than the name.
        return fetch(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), apiKey);
    }
}
