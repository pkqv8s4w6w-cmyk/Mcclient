package dev.vantage;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    public static Vantage instance() {
        return instance;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[{}] pre-init, version {}", MOD_NAME, VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[{}] ready", MOD_NAME);
    }
}
