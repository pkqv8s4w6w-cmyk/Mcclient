package dev.vantage.module.impl.movement;

import dev.vantage.mixin.accessor.MinecraftAccessor;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Runs the game faster or slower. Everything you do speeds up with it, movement included. */
public class TimerModule extends Module {

    private final NumberSetting speed = register(new NumberSetting(
            "Speed", "Game speed; 1 is normal", 1.5, 0.1, 5.0, 0.05, "x"));

    public TimerModule() {
        super("Timer", Category.MOVEMENT, "Speed up or slow down the game");
        markBlatant();
    }

    @Override
    public String getSuffix() {
        return String.format(java.util.Locale.ROOT, "%.2f", speed.asDouble());
    }

    @Override
    public void onTick() {
        ((MinecraftAccessor) Minecraft.getMinecraft()).vantageTimer().timerSpeed = speed.asFloat();
    }

    @Override
    protected void onDisable() {
        ((MinecraftAccessor) Minecraft.getMinecraft()).vantageTimer().timerSpeed = 1.0f;
    }
}
