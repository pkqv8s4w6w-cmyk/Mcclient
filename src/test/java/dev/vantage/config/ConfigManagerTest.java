package dev.vantage.config;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.ColorSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.setting.StringSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    enum Style { COMPACT, DETAILED }

    /** Covers every setting type in one module so a round trip exercises all serialisers. */
    static class SampleModule extends Module {
        final BooleanSetting flag = register(new BooleanSetting("Flag", "", false));
        final NumberSetting amount = register(new NumberSetting("Amount", "", 5.0, 0.0, 10.0, 0.5));
        final EnumSetting<Style> style = register(new EnumSetting<>("Style", "", Style.COMPACT));
        final ColorSetting colour = register(new ColorSetting("Colour", "", 0xFF112233));
        final StringSetting label = register(new StringSetting("Label", "", "hello"));

        SampleModule() {
            super("Sample", Category.UTILITY, "");
        }
    }

    static class OtherModule extends Module {
        final BooleanSetting flag = register(new BooleanSetting("Flag", "", true));

        OtherModule() {
            super("Other", Category.HUD, "");
        }
    }

    @Test
    void everySettingTypeSurvivesARoundTrip(@TempDir Path dir) throws IOException {
        ConfigManager config = new ConfigManager(dir);

        SampleModule saved = new SampleModule();
        saved.setEnabled(true);
        saved.getKeybind().set(42);
        saved.flag.set(true);
        saved.amount.set(7.5);
        saved.style.set(Style.DETAILED);
        saved.colour.set(0xFFAABBCC);
        saved.colour.setRainbow(true);
        saved.colour.setRainbowSpeed(2.5);
        saved.label.set("changed");
        config.save("default", Collections.singletonList(saved));

        SampleModule loaded = new SampleModule();
        assertTrue(config.load("default", Collections.singletonList(loaded)));

        assertTrue(loaded.isEnabled());
        assertEquals(42, loaded.getKeybind().get());
        assertTrue(loaded.flag.value());
        assertEquals(7.5, loaded.amount.asDouble(), 1e-9);
        assertEquals(Style.DETAILED, loaded.style.get());
        assertEquals(0xFFAABBCC, loaded.colour.get());
        assertTrue(loaded.colour.isRainbow());
        assertEquals(2.5, loaded.colour.getRainbowSpeed(), 1e-9);
        assertEquals("changed", loaded.label.get());
    }

    @Test
    void loadingReportsFalseOnFirstRun(@TempDir Path dir) throws IOException {
        ConfigManager config = new ConfigManager(dir);
        assertFalse(config.load("default", Collections.singletonList(new SampleModule())));
    }

    @Test
    void aModuleAddedSinceTheProfileWasWrittenKeepsItsDefaults(@TempDir Path dir) throws IOException {
        ConfigManager config = new ConfigManager(dir);
        config.save("default", Collections.<Module>singletonList(new SampleModule()));

        OtherModule fresh = new OtherModule();
        List<Module> modules = Arrays.asList(new SampleModule(), fresh);
        config.load("default", modules);

        assertTrue(fresh.flag.value(), "a module missing from the file should keep its default");
    }

    @Test
    void aRemovedSettingIsReportedRatherThanFailing(@TempDir Path dir) throws IOException {
        ConfigManager config = new ConfigManager(dir);
        config.save("default", Arrays.asList(new SampleModule(), new OtherModule()));

        List<String> orphans = config.findOrphanedKeys("default", Collections.<Module>singletonList(new SampleModule()));
        assertEquals(Collections.singletonList("other"), orphans);
    }

    @Test
    void wronglyTypedValuesFallBackInsteadOfThrowing(@TempDir Path dir) throws IOException {
        ConfigManager config = new ConfigManager(dir);
        Files.createDirectories(dir.resolve("profiles"));
        // "amount" is a string and "enabled" is a number: both wrong, neither fatal.
        String handEdited = "{\"version\":1,\"modules\":{\"sample\":{\"enabled\":7,"
                + "\"keybind\":\"nope\",\"settings\":{\"amount\":\"lots\",\"flag\":true}}}}";
        Files.write(dir.resolve("profiles").resolve("default.json"), handEdited.getBytes(StandardCharsets.UTF_8));

        SampleModule module = new SampleModule();
        config.load("default", Collections.<Module>singletonList(module));

        assertEquals(5.0, module.amount.asDouble(), 1e-9, "bad number should keep its default");
        assertTrue(module.flag.value(), "the valid neighbouring setting should still apply");
    }

    @Test
    void malformedJsonIsSurfacedAsIoExceptionNotACrash(@TempDir Path dir) throws IOException {
        ConfigManager config = new ConfigManager(dir);
        Files.createDirectories(dir.resolve("profiles"));
        Files.write(dir.resolve("profiles").resolve("default.json"), "{ this is not json".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class,
                () -> config.load("default", Collections.<Module>singletonList(new SampleModule())));
    }

    @Test
    void profileNamesCannotEscapeTheProfileDirectory(@TempDir Path dir) {
        ConfigManager config = new ConfigManager(dir);
        Path escaped = config.profileFile("../../../etc/passwd");
        assertEquals(config.getProfileDirectory(), escaped.getParent(),
                "a traversal attempt must still land inside the profile directory");
    }

    @Test
    void profileNamesAreNormalised() {
        assertEquals("my_config", ConfigManager.sanitiseProfileName("My Config"));
        assertEquals("default", ConfigManager.sanitiseProfileName("   "));
        assertEquals("default", ConfigManager.sanitiseProfileName(null));
    }

    @Test
    void activeProfileRoundTrips(@TempDir Path dir) throws IOException {
        ConfigManager config = new ConfigManager(dir);
        assertEquals("default", config.getActiveProfile());
        config.setActiveProfile("Bedwars Setup");
        assertEquals("bedwars_setup", config.getActiveProfile());
    }

    @Test
    void theDefaultProfileCannotBeDeleted(@TempDir Path dir) throws IOException {
        ConfigManager config = new ConfigManager(dir);
        config.save("default", Collections.<Module>singletonList(new SampleModule()));
        assertThrows(IOException.class, () -> config.deleteProfile("default"));
    }

    @Test
    void savingTwiceLeavesNoTemporaryFileBehind(@TempDir Path dir) throws IOException {
        ConfigManager config = new ConfigManager(dir);
        List<Module> modules = Collections.<Module>singletonList(new SampleModule());
        config.save("default", modules);
        config.save("default", modules);

        try (java.util.stream.Stream<Path> files = Files.list(config.getProfileDirectory())) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void resetRestoresCompiledDefaults(@TempDir Path dir) {
        SampleModule module = new SampleModule();
        module.setEnabled(true);
        module.amount.set(9.0);
        module.label.set("dirty");

        ConfigManager.resetToDefaults(Collections.<Module>singletonList(module));

        assertFalse(module.isEnabled());
        assertEquals(5.0, module.amount.asDouble(), 1e-9);
        assertEquals("hello", module.label.get());
    }
}
