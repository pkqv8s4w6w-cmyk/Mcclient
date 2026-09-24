package dev.vantage.module.impl.combat;

import dev.vantage.event.TickStartEvent;
import dev.vantage.mixin.accessor.MinecraftAccessor;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Removes the half-second lockout after a click that hits nothing. In vanilla a single miss stops
 * the next ten ticks of clicks from registering, which is most of what "the game ate my click" is.
 */
public class NoClickDelayModule extends Module {

    public NoClickDelayModule() {
        super("No Click Delay", Category.COMBAT, "Missed clicks no longer lock out the next ones");
        on(TickStartEvent.class, event ->
                ((MinecraftAccessor) Minecraft.getMinecraft()).vantageSetLeftClickCounter(0));
    }
}
