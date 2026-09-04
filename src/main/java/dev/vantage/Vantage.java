package dev.vantage;

import dev.vantage.config.ConfigManager;
import dev.vantage.module.ModuleManager;
import dev.vantage.module.impl.client.ClickGuiModule;
import dev.vantage.module.impl.client.HudEditorModule;
import dev.vantage.hud.impl.ThreatListHud;
import dev.vantage.module.impl.analysis.CheatDetectorModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entry point for the Vantage client.
 *
 * <p>Vantage is an information client: it surfaces what is already on your screen and in the
 * public Hypixel API more clearly than vanilla does. It does not automate combat.
 */
@Mod(modid = Vantage.MOD_ID, name = Vantage.MOD_NAME, version = Vantage.VERSION, clientSideOnly = true)
public class Vantage {

    public static final String MOD_ID = "vantage";
    public static final String MOD_NAME = "Vantage";
    public static final String VERSION = "0.1.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @Mod.Instance(MOD_ID)
    private static Vantage instance;

    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private String activeProfile = "default";

    public static Vantage instance() {
        return instance;
    }

    public ModuleManager modules() {
        return moduleManager;
    }

    public ConfigManager config() {
        return configManager;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Path dataDirectory = Minecraft.getMinecraft().mcDataDir.toPath().resolve(MOD_ID);
        configManager = new ConfigManager(dataDirectory);
        moduleManager = new ModuleManager();
        LOGGER.info("[{}] pre-init, version {}, data at {}", MOD_NAME, VERSION, dataDirectory);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Tick and input events live on the FML bus in 1.8.9; render and world events live on the
        // Forge bus. Registering on both means ModuleManager sees all of them.
        MinecraftForge.EVENT_BUS.register(moduleManager);
        FMLCommonHandler.instance().bus().register(moduleManager);

        registerModules();
        loadConfig();

        // The client has no reliable shutdown event, and Minecraft can exit without unwinding, so
        // persist on JVM shutdown as well as on the explicit saves the GUI performs.
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveConfig, MOD_NAME + "-config-save"));

        LOGGER.info("[{}] ready with {} modules", MOD_NAME, moduleManager.getModules().size());
    }

    private void registerModules() {
        moduleManager.registerAll(
                new ClickGuiModule(),
                new HudEditorModule(),
                new ThreatListHud(),
                new CheatDetectorModule());
    }

    public void loadConfig() {
        try {
            activeProfile = configManager.getActiveProfile();
            boolean existed = configManager.load(activeProfile, moduleManager.getModules());
            LOGGER.info("[{}] {} profile '{}'", MOD_NAME, existed ? "loaded" : "no saved", activeProfile);
        } catch (IOException failure) {
            // Keep the compiled-in defaults rather than refusing to start.
            LOGGER.error("[{}] could not read profile '{}'; using defaults", MOD_NAME, activeProfile, failure);
        }
    }

    public void saveConfig() {
        if (configManager == null || moduleManager == null) {
            return;
        }
        try {
            configManager.save(activeProfile, moduleManager.getModules());
        } catch (IOException failure) {
            LOGGER.error("[{}] could not write profile '{}'", MOD_NAME, activeProfile, failure);
        }
    }

    public void switchProfile(String profile) {
        saveConfig();
        try {
            activeProfile = ConfigManager.sanitiseProfileName(profile);
            configManager.setActiveProfile(activeProfile);
            ConfigManager.resetToDefaults(moduleManager.getModules());
            configManager.load(activeProfile, moduleManager.getModules());
        } catch (IOException failure) {
            LOGGER.error("[{}] could not switch to profile '{}'", MOD_NAME, profile, failure);
        }
    }
}
