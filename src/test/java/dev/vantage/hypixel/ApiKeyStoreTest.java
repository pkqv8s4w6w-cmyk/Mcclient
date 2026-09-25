package dev.vantage.hypixel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyStoreTest {

    private static final String KEY = "11111111-2222-3333-4444-555555555555";

    @Test
    void roundTripsAKey(@TempDir Path dir) throws IOException {
        ApiKeyStore store = new ApiKeyStore(dir);
        store.save(KEY);
        assertEquals(KEY, store.load());
    }

    @Test
    void trimsPastedWhitespace(@TempDir Path dir) throws IOException {
        ApiKeyStore store = new ApiKeyStore(dir);
        store.save("  " + KEY + "\n");
        assertEquals(KEY, store.load());
    }

    @Test
    void anAbsentFileMeansNoKeyRatherThanAnError(@TempDir Path dir) {
        assertEquals("", new ApiKeyStore(dir).load());
    }

    @Test
    void anUnreadableFileDegradesToNoKey(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("api.json"), "not json at all".getBytes(StandardCharsets.UTF_8));
        assertEquals("", new ApiKeyStore(dir).load());
    }

    @Test
    void clearingRemovesTheFile(@TempDir Path dir) throws IOException {
        ApiKeyStore store = new ApiKeyStore(dir);
        store.save(KEY);
        store.clear();
        assertFalse(Files.exists(store.getFile()));
        assertEquals("", store.load());
    }

    @Test
    void theKeyLivesOutsideTheSharedProfile(@TempDir Path dir) throws IOException {
        // Profiles get copied between people; the key must not ride along.
        ApiKeyStore store = new ApiKeyStore(dir);
        store.save(KEY);
        assertEquals("api.json", store.getFile().getFileName().toString());
        assertFalse(store.getFile().toString().contains("profiles"));
    }

    @Test
    void obviousPasteMistakesAreCaught() {
        assertTrue(ApiKeyStore.looksWellFormed(KEY));
        assertTrue(ApiKeyStore.looksWellFormed("  " + KEY + " "));
        assertFalse(ApiKeyStore.looksWellFormed("not-a-key"));
        assertFalse(ApiKeyStore.looksWellFormed(""));
        assertFalse(ApiKeyStore.looksWellFormed(null));
        assertFalse(ApiKeyStore.looksWellFormed(KEY.replace("-", "")));
    }
}
