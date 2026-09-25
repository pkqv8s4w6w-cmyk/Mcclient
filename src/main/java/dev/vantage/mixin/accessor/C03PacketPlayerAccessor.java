package dev.vantage.mixin.accessor;

import net.minecraft.network.play.client.C03PacketPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C03PacketPlayer.class)
public interface C03PacketPlayerAccessor {

    @Accessor("onGround")
    void vantageSetOnGround(boolean onGround);
}
