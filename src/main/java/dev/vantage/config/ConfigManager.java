package dev.vantage.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vantage.module.Module;
import dev.vantage.setting.Setting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes module state as JSON under the client's data directory.
 *
 * <p>Loading is deliberately forgiving. A config written by an older build, or hand-edited into
 * something malformed, should cost you the affected values and nothing more; it must never stop
 * the game from starting. Unknown modules and unknown settings are skipped, and anything absent
 * falls back to its default.
 *
 * <p>This class holds no Minecraft references so it can be exercised directly in tests.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FORMAT_VERSION = 1;
    private static final String DEFAULT_PROFILE = "default";

    private final Path root;

    public ConfigManager(Path root) {
        this.root = root;
    }

    public Path getRoot() {
        return root;
    }

    public Path getProfileDirectory() {
        return root.resolve("profiles");
    }

    /**
     * Strips anything that could walk out of the profile directory, so a profile name typed into
     * the GUI can only ever produce a file inside it.
     */
    public static String sanitiseProfileName(String name) {
        if (name == null) {
            return DEFAULT_PROFILE;
        }
        String cleaned = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        while (cleaned.startsWith("_")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned.isEmpty() ? DEFAULT_PROFILE : cleaned;
    }

    public Path profileFile(String profile) {
        return getProfileDirectory().resolve(sanitiseProfileName(profile) + ".json");
    }

    public List<String> listProfiles() {
        Path directory = getProfileDirectory();
        if (!Files.isDirectory(directory)) {
            return Collections.singletonList(DEFAULT_PROFILE);
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                names.add(fileName.substring(0, fileName.length() - ".json".length()));
            }
        } catch (IOException ignored) {
            // An unreadable directory is reported as empty rather than fatal.
        }
        if (names.isEmpty()) {
            names.add(DEFAULT_PROFILE);
        }
        Collections.sort(names);
        return names;
    }

    // -- saving -----------------------------------------------------------------------------

    public void save(String profile, Collection<Module> modules) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);

        JsonObject moduleTree = new JsonObject();
        for (Module module : modules) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.addProperty("keybind", module.getKeybind().get());

            JsonObject settingTree = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                settingTree.add(setting.getConfigKey(), setting.write());
            }
            entry.add("settings", settingTree);
            moduleTree.add(module.getConfigKey(), entry);
        }
        root.add("modules", moduleTree);

        writeAtomically(profileFile(profile), GSON.toJson(root));
    }

    /**
     * Writes via a temporary file and a move, so an interrupted save leaves the previous config
     * intact instead of a half-written one.
     */
    private void writeAtomically(Path target, String contents) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            writer.write(contents);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    // -- loading ----------------------------------------------------------------------------

    /**
     * Applies a saved profile to the given modules.
     *
     * @return true if a config file existed and was read; false if this is a first run
     */
    public boolean load(String profile, Collection<Module> modules) throws IOException {
        Path file = profileFile(profile);
        if (!Files.isRegularFile(file)) {
            return false;
        }

        JsonObject root;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return false;
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException malformed) {
            // Gson throws unchecked on syntax errors. Treat an unreadable config as absent.
            throw new IOException("config at " + file + " is not valid JSON", malformed);
        }

        if (!root.has("modules") || !root.get("modules").isJsonObject()) {
            return false;
        }
        JsonObject moduleTree = root.getAsJsonObject("modules");

        for (Module module : modules) {
            JsonElement entryElement = moduleTree.get(module.getConfigKey());
            if (entryElement == null || !entryElement.isJsonObject()) {
                continue; // Module added since this profile was written; it keeps its defaults.
            }
            applyModule(module, entryElement.getAsJsonObject());
        }
        return true;
    }

    private void applyModule(Module module, JsonObject entry) {
        if (entry.has("enabled")) {
            try {
                // Silent: the world is not loaded during startup, so onEnable must not run yet.
                module.setEnabledSilently(entry.get("enabled").getAsBoolean());
            } catch (RuntimeException ignored) {
                // Wrong type in the config; keep the default.
            }
        }
        if (entry.has("keybind")) {
            module.getKeybind().read(entry.get("keybind"));
        }
        if (!entry.has("settings") || !entry.get("settings").isJsonObject()) {
            return;
        }
        JsonObject settingTree = entry.getAsJsonObject("settings");
        for (Setting<?> setting : module.getSettings()) {
            JsonElement value = settingTree.get(setting.getConfigKey());
            if (value == null) {
                continue;
            }
            try {
                setting.read(value);
            } catch (RuntimeException ignored) {
                // One bad setting should not abort the rest of the module.
            }
        }
    }

    // -- active profile ---------------------------------------------------------------------

    public String getActiveProfile() {
        Path state = root.resolve("state.json");
        if (!Files.isRegularFile(state)) {
            return DEFAULT_PROFILE;
        }
        try (BufferedReader reader = Files.newBufferedReader(state, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed != null && parsed.isJsonObject()) {
                JsonObject object = parsed.getAsJsonObject();
                if (object.has("profile")) {
                    return sanitiseProfileName(object.get("profile").getAsString());
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Fall through to the default.
        }
        return DEFAULT_PROFILE;
    }

    public void setActiveProfile(String profile) throws IOException {
        JsonObject object = new JsonObject();
        object.addProperty("profile", sanitiseProfileName(profile));
        writeAtomically(root.resolve("state.json"), GSON.toJson(object));
    }

    public void deleteProfile(String profile) throws IOException {
        String name = sanitiseProfileName(profile);
        if (DEFAULT_PROFILE.equals(name)) {
            throw new IOException("the default profile cannot be deleted");
        }
        Files.deleteIfExists(profileFile(name));
    }

    /** Restores every setting on every module to its compiled-in default. */
    public static void resetToDefaults(Collection<Module> modules) {
        for (Module module : modules) {
            module.setEnabledSilently(false);
            module.getKeybind().reset();
            for (Setting<?> setting : module.getSettings()) {
                setting.reset();
            }
        }
    }

    /** Exposed for diagnostics: which config keys in the file matched no known module. */
    public List<String> findOrphanedKeys(String profile, Collection<Module> modules) throws IOException {
        Path file = profileFile(profile);
        List<String> orphans = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return orphans;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject() || !parsed.getAsJsonObject().has("modules")) {
                return orphans;
            }
            JsonObject moduleTree = parsed.getAsJsonObject().getAsJsonObject("modules");
            List<String> known = new ArrayList<>();
            for (Module module : modules) {
                known.add(module.getConfigKey());
            }
            for (Map.Entry<String, JsonElement> entry : moduleTree.entrySet()) {
                if (!known.contains(entry.getKey())) {
                    orphans.add(entry.getKey());
                }
            }
        } catch (RuntimeException ignored) {
            // Malformed file; nothing useful to report.
        }
        return orphans;
    }
}
