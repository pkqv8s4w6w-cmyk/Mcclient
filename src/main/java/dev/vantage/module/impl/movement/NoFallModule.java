package dev.vantage.module.impl.movement;

import dev.vantage.event.MotionEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * No fall damage. The server works out fall damage from whether each movement packet says the
 * player is on the ground, so saying so on the way down resets the fall before it can hurt.
 */
public class NoFallModule extends Module {

    public NoFallModule() {
        super("NoFall", Category.MOVEMENT, "Take no fall damage");
        markBlatant();
        on(MotionEvent.class, event -> {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (event.isPre() && player.fallDistance > 2.5f && !player.capabilities.isFlying) {
                event.setOnGround(true);
            }
        });
    }
}
