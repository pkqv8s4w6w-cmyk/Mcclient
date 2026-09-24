package dev.vantage.module.impl.movement;

import dev.vantage.event.MoveEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.client.Minecraft;

/** Never walk off an edge: stops at block edges the way sneaking does, at full speed. */
public class SafeWalkModule extends Module {

    public SafeWalkModule() {
        super("SafeWalk", Category.MOVEMENT, "Stop at block edges without sneaking");
        on(MoveEvent.class, event -> {
            if (Minecraft.getMinecraft().thePlayer.onGround) {
                event.setSafeWalk(true);
            }
        });
    }
}
