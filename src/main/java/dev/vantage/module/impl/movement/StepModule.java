package dev.vantage.module.impl.movement;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Walks straight up ledges instead of jumping them. */
public class StepModule extends Module {

    private static final float VANILLA_STEP = 0.6f;

    private final NumberSetting height = register(new NumberSetting(
            "Height", "Tallest ledge to walk up", 1.0, 1.0, 2.5, 0.5, "m"));

    public StepModule() {
        super("Step", Category.MOVEMENT, "Walk up blocks without jumping");
        markBlatant();
    }

    @Override
    public void onTick() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        player.stepHeight = player.isRiding() ? VANILLA_STEP : height.asFloat();
    }

    @Override
    protected void onDisable() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            player.stepHeight = VANILLA_STEP;
        }
    }
}
