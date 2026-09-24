package dev.vantage.mixin.accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("timer")
    Timer vantageTimer();

    @Accessor("rightClickDelayTimer")
    int vantageRightClickDelay();

    @Accessor("rightClickDelayTimer")
    void vantageSetRightClickDelay(int ticks);

    @Accessor("leftClickCounter")
    void vantageSetLeftClickCounter(int ticks);

    @Invoker("clickMouse")
    void vantageClickMouse();

    @Invoker("rightClickMouse")
    void vantageRightClickMouse();
}
