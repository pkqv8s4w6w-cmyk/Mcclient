package dev.vantage.mixin.accessor;

import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S08PacketPlayerPosLook.class)
public interface S08PacketPlayerPosLookAccessor {

    @Accessor("yaw")
    void vantageSetYaw(float yaw);

    @Accessor("pitch")
    void vantageSetPitch(float pitch);
}
