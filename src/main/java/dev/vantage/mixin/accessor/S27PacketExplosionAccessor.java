package dev.vantage.mixin.accessor;

import net.minecraft.network.play.server.S27PacketExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The three unnamed floats are the knockback the explosion gives the local player. */
@Mixin(S27PacketExplosion.class)
public interface S27PacketExplosionAccessor {

    @Accessor("field_149152_f")
    void vantageSetMotionX(float value);

    @Accessor("field_149153_g")
    void vantageSetMotionY(float value);

    @Accessor("field_149159_h")
    void vantageSetMotionZ(float value);
}
