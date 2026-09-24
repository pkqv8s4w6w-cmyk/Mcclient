package dev.vantage.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Players every combat module leaves alone.
 *
 * <p>Kept in its own file rather than in a profile. Profiles are for swapping setups, and who your
 * friends are does not change when you switch from a close-range setup to a bridging one.
 *
 * <p>Names are matched case-insensitively, since Minecraft usernames are, but stored with the
 * capitalisation they were added with so the list reads properly.
 */
public final class FriendManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, String> byKey = new LinkedHashMap<String, String>();

    public FriendManager(Path file) {
        this.file = file;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public synchronized boolean isFriend(String name) {
        return name != null && byKey.containsKey(key(name));
    }

    /** @return true if the name was not already a friend */
    public synchronized boolean add(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        if (byKey.containsKey(key(name))) {
            return false;
        }
        byKey.put(key(name), name.trim());
        return true;
    }

    public synchronized boolean remove(String name) {
        return name != null && byKey.remove(key(name)) != null;
    }

    /** Adds or removes, and says which happened: true means they are now a friend. */
    public synchronized boolean toggle(String name) {
        if (isFriend(name)) {
            remove(name);
            return false;
        }
        add(name);
        return true;
    }

    public synchronized List<String> list() {
        return Collections.unmodifiableList(new ArrayList<String>(byKey.values()));
    }

    public synchronized void load() throws IOException {
        byKey.clear();
        if (!Files.exists(file)) {
            return;
        }
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        JsonElement root;
        try {
            root = new JsonParser().parse(text);
        } catch (RuntimeException malformed) {
            // A hand-edited file that no longer parses costs the list, not the game.
            return;
        }
        if (root == null || !root.isJsonArray()) {
            return;
        }
        for (JsonElement entry : root.getAsJsonArray()) {
            if (entry.isJsonPrimitive()) {
                add(entry.getAsString());
            }
        }
    }

    public synchronized void save() throws IOException {
        JsonArray array = new JsonArray();
        for (String name : byKey.values()) {
            array.add(new com.google.gson.JsonPrimitive(name));
        }
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, GSON.toJson(array).getBytes(StandardCharsets.UTF_8));
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
