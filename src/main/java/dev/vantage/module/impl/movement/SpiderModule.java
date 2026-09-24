package dev.vantage.module.impl.movement;

import dev.vantage.event.UpdateEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Climb straight up walls by walking into them. */
public class SpiderModule extends Module {

    private final NumberSetting speed = register(new NumberSetting(
            "Speed", "How fast to climb, in blocks per tick", 0.2, 0.05, 1.0, 0.05));

    public SpiderModule() {
        super("Spider", Category.MOVEMENT, "Climb walls like a ladder");
        markBlatant();
        on(UpdateEvent.class, event -> {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player.isCollidedHorizontally && player.movementInput.moveForward > 0.0f) {
                player.motionY = speed.asDouble();
                player.fallDistance = 0.0f;
            }
        });
    }
}
