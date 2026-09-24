package dev.vantage.hypixel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Real HTTP, using the JDK client so the mod pulls in no extra dependencies. */
public final class HttpTransport implements Transport {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;

    @Override
    public Response get(String url, String apiKey) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("API-Key", apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Vantage/1.8.9");

            int status = connection.getResponseCode();
            // A non-2xx response puts the body on the error stream instead.
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream == null ? "" : read(stream);

            return new Response(status, body,
                    headerAsInt(connection, "RateLimit-Remaining"),
                    headerAsInt(connection, "RateLimit-Reset"));
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static Integer headerAsInt(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
