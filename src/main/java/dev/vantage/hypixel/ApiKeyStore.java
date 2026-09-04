package dev.vantage.hypixel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores the Hypixel API key.
 *
 * <p>Kept in its own file rather than in the config profile, because profiles are the thing people
 * hand to a friend to copy a setup. On systems that support it the file is restricted to the
 * owner. The key is never written to a log, and the interface renders it masked.
 */
public final class ApiKeyStore {

    private static final String FILE_NAME = "api.json";
    private static final String KEY_FIELD = "hypixel_key";

    private final Path file;

    public ApiKeyStore(Path directory) {
        this.file = directory.resolve(FILE_NAME);
    }

    public Path getFile() {
        return file;
    }

    /** @return the stored key, or an empty string when none is set */
    public String load() {
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            return json.has(KEY_FIELD) ? json.get(KEY_FIELD).getAsString() : "";
        } catch (IOException | RuntimeException unreadable) {
            // Never log the exception body here; it can echo file content.
            return "";
        }
    }

    public void save(String key) throws IOException {
        Files.createDirectories(file.getParent());

        JsonObject json = new JsonObject();
        json.addProperty(KEY_FIELD, key == null ? "" : key.trim());

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(json.toString());
        }
        restrictPermissions();
    }

    public void clear() throws IOException {
        Files.deleteIfExists(file);
    }

    /** Best effort: POSIX systems get owner-only, Windows silently keeps its own defaults. */
    private void restrictPermissions() {
        try {
            Set<PosixFilePermission> ownerOnly = new HashSet<PosixFilePermission>();
            ownerOnly.add(PosixFilePermission.OWNER_READ);
            ownerOnly.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, ownerOnly);
        } catch (IOException | UnsupportedOperationException unsupported) {
            // Not a POSIX filesystem. Nothing to do.
        }
    }

    /**
     * Shape check for a Hypixel developer key, which is a dashed UUID.
     *
     * <p>Only catches obvious paste mistakes; whether the key actually works is something only the
     * API can answer.
     */
    public static boolean looksWellFormed(String key) {
        return key != null && key.trim().matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
}
