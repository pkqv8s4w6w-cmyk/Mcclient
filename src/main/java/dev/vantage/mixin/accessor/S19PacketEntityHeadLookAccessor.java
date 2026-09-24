package dev.vantage.mixin.accessor;

import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S19PacketEntityHeadLook.class)
public interface S19PacketEntityHeadLookAccessor {

    @Accessor("entityId")
    int vantageEntityId();
}
