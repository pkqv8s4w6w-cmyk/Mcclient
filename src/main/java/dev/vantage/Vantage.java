package dev.vantage;

import dev.vantage.combat.RotationManager;
import dev.vantage.config.ConfigManager;
import dev.vantage.config.FriendManager;
import dev.vantage.hud.impl.ArmourHud;
import dev.vantage.hud.impl.ArrayListHud;
import dev.vantage.hud.impl.CpsHud;
import dev.vantage.hud.impl.InfoHud;
import dev.vantage.hud.impl.KeystrokesHud;
import dev.vantage.hud.impl.NotificationsHud;
import dev.vantage.hud.impl.PotionHud;
import dev.vantage.hud.impl.TargetHud;
import dev.vantage.hud.impl.ThreatListHud;
import dev.vantage.hud.impl.WatermarkHud;
import dev.vantage.module.impl.analysis.CheatDetectorModule;
import dev.vantage.module.impl.client.ClickGuiModule;
import dev.vantage.module.impl.client.HudEditorModule;
import dev.vantage.module.impl.client.ModsManagerModule;
import dev.vantage.module.impl.bedwars.BedEspModule;
import dev.vantage.module.impl.bedwars.BedGuardModule;
import dev.vantage.module.impl.bedwars.BedNukerModule;
import dev.vantage.module.impl.bedwars.BreachPlannerModule;
import dev.vantage.module.impl.bedwars.EconomyTrackerModule;
import dev.vantage.module.impl.bedwars.ItemEspModule;
import dev.vantage.module.impl.bedwars.ProjectileForecastModule;
import dev.vantage.module.impl.bedwars.RushRadarModule;
import dev.vantage.module.impl.bedwars.VoidClutchModule;
import dev.vantage.module.impl.combat.AimAssistModule;
import dev.vantage.module.impl.combat.AntiBotModule;
import dev.vantage.module.impl.combat.AutoClickerModule;
import dev.vantage.module.impl.combat.BacktrackModule;
import dev.vantage.module.impl.combat.BowAimbotModule;
import dev.vantage.module.impl.combat.CriticalsModule;
import dev.vantage.module.impl.combat.HitboxesModule;
import dev.vantage.module.impl.combat.KeepSprintModule;
import dev.vantage.module.impl.combat.KillAuraModule;
import dev.vantage.module.impl.combat.NoClickDelayModule;
import dev.vantage.module.impl.combat.ReachModule;
import dev.vantage.module.impl.combat.TriggerBotModule;
import dev.vantage.module.impl.combat.VelocityModule;
import dev.vantage.module.impl.combat.WTapModule;
import dev.vantage.module.impl.movement.AntiVoidModule;
import dev.vantage.module.impl.movement.BlinkModule;
import dev.vantage.module.impl.movement.FlyModule;
import dev.vantage.module.impl.movement.HighJumpModule;
import dev.vantage.module.impl.movement.InvMoveModule;
import dev.vantage.module.impl.movement.LongJumpModule;
import dev.vantage.module.impl.movement.NoFallModule;
import dev.vantage.module.impl.movement.NoSlowModule;
import dev.vantage.module.impl.movement.SafeWalkModule;
import dev.vantage.module.impl.movement.SpeedModule;
import dev.vantage.module.impl.movement.SpiderModule;
import dev.vantage.module.impl.movement.StepModule;
import dev.vantage.module.impl.movement.TargetStrafeModule;
import dev.vantage.module.impl.movement.TimerModule;
import dev.vantage.module.impl.movement.ToggleSprintModule;
import dev.vantage.module.impl.player.AutoArmorModule;
import dev.vantage.module.impl.player.AutoToolModule;
import dev.vantage.module.impl.player.ChestStealerModule;
import dev.vantage.module.impl.player.FastBreakModule;
import dev.vantage.module.impl.player.FastEatModule;
import dev.vantage.module.impl.player.FastPlaceModule;
import dev.vantage.module.impl.player.InvManagerModule;
import dev.vantage.module.impl.player.NoRotateModule;
import dev.vantage.module.impl.player.ScaffoldModule;
import dev.vantage.module.impl.utility.NickHiderModule;
import dev.vantage.module.impl.utility.ZoomModule;
import dev.vantage.module.impl.visual.ChamsModule;
import dev.vantage.module.impl.visual.EspModule;
import dev.vantage.module.impl.visual.FreecamModule;
import dev.vantage.module.impl.visual.FullbrightModule;
import dev.vantage.module.impl.visual.NametagsModule;
import dev.vantage.module.impl.visual.NoHurtCamModule;
import dev.vantage.module.impl.visual.StorageEspModule;
import dev.vantage.module.impl.visual.TimeChangerModule;
import dev.vantage.module.impl.visual.TracersModule;
import dev.vantage.module.impl.visual.TrajectoriesModule;
import dev.vantage.module.ModuleManager;
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
 * <p>Vantage is mostly an information client: it surfaces what is already on your screen and in the
 * public Hypixel API more clearly than vanilla does.
 *
 * <p>The exception is {@code BacktrackModule}, which holds other players' movement packets back so
 * they can be hit where they were. That is a combat advantage and servers ban for it, so the build
 * it is in belongs on a private server and nowhere else. Nothing here automates combat - Backtrack
 * delays packets and leaves the aiming and clicking to you - but the distinction matters to how it
 * plays, not to whether a server allows it.
 */
@Mod(modid = Vantage.MOD_ID, name = Vantage.MOD_NAME, version = Vantage.VERSION, clientSideOnly = true)
public class Vantage {

    public static final String MOD_ID = "vantage";
    public static final String MOD_NAME = "Vantage";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @Mod.Instance(MOD_ID)
    private static Vantage instance;

    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private FriendManager friendManager;
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

    public FriendManager friends() {
        return friendManager;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Path dataDirectory = Minecraft.getMinecraft().mcDataDir.toPath().resolve(MOD_ID);
        configManager = new ConfigManager(dataDirectory);
        friendManager = new FriendManager(dataDirectory.resolve("friends.json"));
        moduleManager = new ModuleManager();
        LOGGER.info("[{}] pre-init, version {}, data at {}", MOD_NAME, VERSION, dataDirectory);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Tick and input events live on the FML bus in 1.8.9; render and world events live on the
        // Forge bus. Registering on both means ModuleManager sees all of them.
        MinecraftForge.EVENT_BUS.register(moduleManager);
        FMLCommonHandler.instance().bus().register(moduleManager);

        RotationManager.get().install();
        registerModules();
        loadConfig();
        try {
            friendManager.load();
        } catch (IOException failure) {
            LOGGER.error("[{}] could not read the friends list", MOD_NAME, failure);
        }

        // The client has no reliable shutdown event, and Minecraft can exit without unwinding, so
        // persist on JVM shutdown as well as on the explicit saves the GUI performs.
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveConfig, MOD_NAME + "-config-save"));

        LOGGER.info("[{}] ready with {} modules", MOD_NAME, moduleManager.getModules().size());
    }

    private void registerModules() {
        moduleManager.registerAll(
                // Combat
                new KillAuraModule(),
                new AimAssistModule(),
                new AutoClickerModule(),
                new TriggerBotModule(),
                new ReachModule(),
                new VelocityModule(),
                new HitboxesModule(),
                new CriticalsModule(),
                new WTapModule(),
                new KeepSprintModule(),
                new BowAimbotModule(),
                new BacktrackModule(),
                new NoClickDelayModule(),
                new AntiBotModule(),
                // Movement
                new ToggleSprintModule(),
                new SpeedModule(),
                new FlyModule(),
                new LongJumpModule(),
                new HighJumpModule(),
                new StepModule(),
                new NoFallModule(),
                new NoSlowModule(),
                new SafeWalkModule(),
                new InvMoveModule(),
                new AntiVoidModule(),
                new TimerModule(),
                new BlinkModule(),
                new SpiderModule(),
                new TargetStrafeModule(),
                // Player
                new ScaffoldModule(),
                new FastPlaceModule(),
                new FastBreakModule(),
                new AutoToolModule(),
                new ChestStealerModule(),
                new InvManagerModule(),
                new AutoArmorModule(),
                new FastEatModule(),
                new NoRotateModule(),
                // Visuals
                new EspModule(),
                new NametagsModule(),
                new TracersModule(),
                new ChamsModule(),
                new StorageEspModule(),
                new TrajectoriesModule(),
                new FreecamModule(),
                new NoHurtCamModule(),
                new TimeChangerModule(),
                new FullbrightModule(),
                // Bedwars
                new BreachPlannerModule(),
                new RushRadarModule(),
                new EconomyTrackerModule(),
                new BedGuardModule(),
                new ProjectileForecastModule(),
                new VoidClutchModule(),
                new BedNukerModule(),
                new BedEspModule(),
                new ItemEspModule(),
                // Analysis
                new ThreatListHud(),
                new CheatDetectorModule(),
                // HUD
                new ArrayListHud(),
                new WatermarkHud(),
                new TargetHud(),
                new NotificationsHud(),
                new KeystrokesHud(),
                new CpsHud(),
                new InfoHud(),
                new ArmourHud(),
                new PotionHud(),
                // Misc
                new ZoomModule(),
                new NickHiderModule(),
                new ModsManagerModule(),
                // Client
                new ClickGuiModule(),
                new HudEditorModule());
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
        if (friendManager != null) {
            try {
                friendManager.save();
            } catch (IOException failure) {
                LOGGER.error("[{}] could not write the friends list", MOD_NAME, failure);
            }
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
