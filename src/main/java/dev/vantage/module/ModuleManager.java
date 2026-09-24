package dev.vantage.module;

import dev.vantage.Vantage;
import dev.vantage.event.EntityRenderEvent;
import dev.vantage.event.EventBus;
import dev.vantage.event.NametagEvent;
import dev.vantage.event.Stage;
import dev.vantage.event.Render2DEvent;
import dev.vantage.event.Render3DEvent;
import dev.vantage.event.TickStartEvent;
import dev.vantage.game.BedTracker;
import dev.vantage.gui.render.Render3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns the module registry and every Forge event subscription.
 *
 * <p>Modules are plain objects with no bus registration of their own; this class subscribes once
 * and fans events out to whatever is enabled. That keeps the bus stable regardless of how often
 * modules are toggled, and keeps {@link Module} free of Minecraft imports.
 */
public final class ModuleManager {

    private final List<Module> modules = new ArrayList<>();
    private WorldClient lastWorld;
    private final Map<String, Module> byConfigKey = new LinkedHashMap<>();
    private final Map<Class<?>, Module> byClass = new LinkedHashMap<>();

    public ModuleManager() {
        // A handler that throws is switched off with its module, the same as one that throws in a
        // tick, rather than failing again on every packet.
        EventBus.global().setFailureHandler((owner, failure) -> {
            if (owner instanceof Module) {
                disableAfterFailure((Module) owner, "an event handler", failure);
            } else {
                Vantage.LOGGER.error("An event handler threw and has been removed", failure);
            }
        });
    }

    public void register(Module module) {
        String key = module.getConfigKey();
        if (byConfigKey.containsKey(key)) {
            throw new IllegalStateException("two modules share the config key '" + key + "'");
        }
        modules.add(module);
        byConfigKey.put(key, module);
        byClass.put(module.getClass(), module);
    }

    /** The registered instance of a module class, or null if it is not registered. */
    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        return (T) byClass.get(type);
    }

    /** Whether a module is registered and on, for code that only needs to know that much. */
    public boolean isEnabled(Class<? extends Module> type) {
        Module module = byClass.get(type);
        return module != null && module.isEnabled();
    }

    public void registerAll(Module... toRegister) {
        for (Module module : toRegister) {
            register(module);
        }
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getByCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public Module getByConfigKey(String key) {
        return byConfigKey.get(key);
    }

    public Module getByName(String name) {
        return byConfigKey.get(name.toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    /** Modules whose name or description matches, for the ClickGUI search box. */
    public List<Module> search(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return getModules();
        }
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getName().toLowerCase(Locale.ROOT).contains(needle)
                    || module.getDescription().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(module);
            }
        }
        return result;
    }

    // -- event fan-out ----------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Minecraft start = Minecraft.getMinecraft();
            if (start.theWorld != null && start.thePlayer != null) {
                EventBus.global().post(TickStartEvent.INSTANCE);
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            lastWorld = null;
            return;
        }
        // Joining a new game gives a fresh world instance, which is the signal to drop per-game
        // state such as this round's kill counts.
        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            for (Module module : modules) {
                try {
                    module.onWorldChanged();
                } catch (Throwable failure) {
                    disableAfterFailure(module, "onWorldChanged", failure);
                }
            }
        }
        try {
            BedTracker.get().tick();
        } catch (Throwable failure) {
            Vantage.LOGGER.error("Bed tracking failed this tick", failure);
        }
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onTick();
            } catch (Throwable failure) {
                disableAfterFailure(module, "onTick", failure);
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        EventBus.global().post(new Render2DEvent(event.partialTicks,
                event.resolution.getScaledWidth(), event.resolution.getScaledHeight()));
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onRenderOverlay(event.partialTicks);
            } catch (Throwable failure) {
                disableAfterFailure(module, "onRenderOverlay", failure);
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        // Kept for the overlay, which projects world positions to the screen after this.
        Render3D.captureMatrices();
        EventBus.global().post(new Render3DEvent(event.partialTicks));
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onRenderWorld(event.partialTicks);
            } catch (Throwable failure) {
                disableAfterFailure(module, "onRenderWorld", failure);
            }
        }
    }

    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre<?> event) {
        if (EventBus.global().hasListeners(EntityRenderEvent.class)) {
            EventBus.global().post(new EntityRenderEvent(Stage.PRE, event.entity));
        }
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post<?> event) {
        if (EventBus.global().hasListeners(EntityRenderEvent.class)) {
            EventBus.global().post(new EntityRenderEvent(Stage.POST, event.entity));
        }
    }

    @SubscribeEvent
    public void onRenderNametag(RenderLivingEvent.Specials.Pre<?> event) {
        if (EventBus.global().hasListeners(NametagEvent.class)
                && EventBus.global().post(new NametagEvent(event.entity)).isCancelled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.message == null) {
            return;
        }
        String raw = event.message.getFormattedText();

        String display = raw;
        for (Module module : modules) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                String replacement = module.rewriteChat(display);
                if (replacement != null) {
                    display = replacement;
                }
            } catch (Throwable failure) {
                disableAfterFailure(module, "rewriteChat", failure);
            }
        }
        if (!display.equals(raw)) {
            event.message = new net.minecraft.util.ChatComponentText(display);
        }

        // Observers always see the original. Kill parsing and the threat list work off real names,
        // so handing them a nicked line would quietly break both.
        for (Module module : modules) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onChatMessage(raw);
            } catch (Throwable failure) {
                disableAfterFailure(module, "onChatMessage", failure);
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState()) {
            return; // Key release; only act on the press.
        }
        if (Minecraft.getMinecraft().currentScreen != null) {
            return; // A screen is open, so the key belongs to it.
        }
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_NONE) {
            return;
        }
        for (Module module : modules) {
            if (module.getKeybind().matches(key)) {
                module.toggle();
            }
        }
    }

    /**
     * A module that throws would otherwise throw again every tick and bury the log. Turn it off
     * and say so once.
     */
    private void disableAfterFailure(Module module, String hook, Throwable failure) {
        Vantage.LOGGER.error("Module '{}' threw in {} and has been disabled", module.getName(), hook, failure);
        if (!module.stopAfterFailure()) {
            return;
        }
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            module.runDisableHook();
        } else {
            // Packet handlers fail on the network thread, and the disable hook touches the game.
            Minecraft.getMinecraft().addScheduledTask(module::runDisableHook);
        }
    }
}
