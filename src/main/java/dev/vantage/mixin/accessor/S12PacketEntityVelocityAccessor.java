package dev.vantage.mixin.accessor;

import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S12PacketEntityVelocity.class)
public interface S12PacketEntityVelocityAccessor {

    @Accessor("motionX")
    void vantageSetMotionX(int value);

    @Accessor("motionY")
    void vantageSetMotionY(int value);

    @Accessor("motionZ")
    void vantageSetMotionZ(int value);
}
