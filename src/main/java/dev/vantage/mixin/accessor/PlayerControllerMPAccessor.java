package dev.vantage.mixin.accessor;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerControllerMP.class)
public interface PlayerControllerMPAccessor {

    @Accessor("curBlockDamageMP")
    float vantageBlockDamage();

    @Accessor("curBlockDamageMP")
    void vantageSetBlockDamage(float damage);

    @Accessor("blockHitDelay")
    void vantageSetBlockHitDelay(int ticks);

    @Accessor("currentBlock")
    BlockPos vantageCurrentBlock();

    @Accessor("isHittingBlock")
    boolean vantageIsHittingBlock();

    @Invoker("syncCurrentPlayItem")
    void vantageSyncHeldItem();
}
