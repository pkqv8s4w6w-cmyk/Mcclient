package dev.vantage.hypixel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HypixelApiTest {

    private static final UUID SOME_PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String KEY = "11111111-2222-3333-4444-555555555555";

    private static HypixelApi apiWith(FakeTransport transport, MutableClock clock) {
        return new HypixelApi(transport, new RateLimiter(100, 60_000L, clock));
    }

    @Test
    void theKeyTravelsInAHeaderAndNeverInTheUrl() {
        FakeTransport transport = new FakeTransport();
        apiWith(transport, new MutableClock()).fetch(SOME_PLAYER, KEY);

        assertFalse(transport.requestedUrls.get(0).contains(KEY),
                "the key must not appear in the URL, where proxies and redirects would log it");
        assertEquals(KEY, transport.sentKeys.get(0));
    }

    @Test
    void theUuidIsSentWithoutDashes() {
        FakeTransport transport = new FakeTransport();
        apiWith(transport, new MutableClock()).fetch(SOME_PLAYER, KEY);
        assertTrue(transport.requestedUrls.get(0).endsWith("069a79f444e94726a5befca90e38aaf5"));
    }

    @Test
    void aGoodResponseParses() {
        FakeTransport transport = new FakeTransport();
        transport.next = new Transport.Response(200,
                "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":100,"
                        + "\"final_deaths_bedwars\":10}}}}", 250, 200);

        ApiResult result = apiWith(transport, new MutableClock()).fetch(SOME_PLAYER, KEY);
        assertTrue(result.isOk());
        assertEquals(10.0, result.getStats().getFinalKillDeathRatio(), 1e-9);
    }

    @Test
    void aRejectedKeyIsReportedAsSuchRatherThanAsUnavailable() {
        FakeTransport transport = new FakeTransport();
        transport.next = new Transport.Response(403, "{\"success\":false,\"cause\":\"Invalid API key\"}", null, null);
        assertEquals(ApiResult.Status.INVALID_KEY,
                apiWith(transport, new MutableClock()).fetch(SOME_PLAYER, KEY).getStatus());
    }

    @Test
    void anUnsuccessfulTwoHundredAboutTheKeyIsStillAKeyProblem() {
        FakeTransport transport = new FakeTransport();
        transport.next = new Transport.Response(200, "{\"success\":false,\"cause\":\"Invalid API key\"}", null, null);
        assertEquals(ApiResult.Status.INVALID_KEY,
                apiWith(transport, new MutableClock()).fetch(SOME_PLAYER, KEY).getStatus());
    }

    @Test
    void beingRateLimitedSpendsTheRemainingBudget() {
        FakeTransport transport = new FakeTransport();
        MutableClock clock = new MutableClock();
        transport.next = new Transport.Response(429, "", 0, 45);

        HypixelApi api = apiWith(transport, clock);
        assertEquals(ApiResult.Status.RATE_LIMITED, api.fetch(SOME_PLAYER, KEY).getStatus());

        // The next call must not even reach the transport.
        int callsSoFar = transport.requestedUrls.size();
        assertEquals(ApiResult.Status.RATE_LIMITED, api.fetch(SOME_PLAYER, KEY).getStatus());
        assertEquals(callsSoFar, transport.requestedUrls.size(), "should back off without calling");
    }

    @Test
    void networkFailuresAreRetryableAndLeakNothing() {
        FakeTransport transport = new FakeTransport();
        transport.throwOnCall = new IOException("connect timed out to host with key " + KEY);

        ApiResult result = apiWith(transport, new MutableClock()).fetch(SOME_PLAYER, KEY);
        assertEquals(ApiResult.Status.UNAVAILABLE, result.getStatus());
        assertFalse(result.getDetail().contains(KEY),
                "the surfaced message must not carry anything that could echo the key");
    }

    @Test
    void aNonsenseBodyIsReportedAsMalformed() {
        FakeTransport transport = new FakeTransport();
        transport.next = new Transport.Response(200, "<html>gateway error</html>", null, null);
        assertEquals(ApiResult.Status.MALFORMED,
                apiWith(transport, new MutableClock()).fetch(SOME_PLAYER, KEY).getStatus());
    }

    @Test
    void aMissingKeyFailsWithoutSpendingBudget() {
        FakeTransport transport = new FakeTransport();
        HypixelApi api = apiWith(transport, new MutableClock());

        assertEquals(ApiResult.Status.INVALID_KEY, api.fetch(SOME_PLAYER, "").getStatus());
        assertEquals(ApiResult.Status.INVALID_KEY, api.fetch(SOME_PLAYER, null).getStatus());
        assertTrue(transport.requestedUrls.isEmpty(), "should not have called out at all");
        assertEquals(100, api.getRateLimiter().getRemaining(), "budget must be untouched");
    }
}
