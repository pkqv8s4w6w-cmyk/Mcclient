package dev.vantage.hypixel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Records what the client asked for and replays a canned response. */
class FakeTransport implements Transport {

    final List<String> requestedUrls = new ArrayList<String>();
    final List<String> sentKeys = new ArrayList<String>();

    Response next = new Response(200, "{\"success\":true,\"player\":null}", null, null);
    IOException throwOnCall;

    @Override
    public Response get(String url, String apiKey) throws IOException {
        requestedUrls.add(url);
        sentKeys.add(apiKey);
        if (throwOnCall != null) {
            throw throwOnCall;
        }
        return next;
    }
}
