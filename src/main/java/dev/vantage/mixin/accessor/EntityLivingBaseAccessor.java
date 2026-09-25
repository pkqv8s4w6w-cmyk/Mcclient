package dev.vantage.mixin.accessor;

import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityLivingBase.class)
public interface EntityLivingBaseAccessor {

    @Accessor("jumpTicks")
    void vantageSetJumpTicks(int ticks);
}
