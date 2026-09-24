package dev.vantage.mixin;

import dev.vantage.event.AttackEvent;
import dev.vantage.event.EventBus;
import dev.vantage.event.Stage;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attacks. The pre event fires before the attack packet is queued, so it can still be cancelled. */
@Mixin(PlayerControllerMP.class)
public abstract class MixinPlayerControllerMP {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void vantage$preAttack(EntityPlayer player, Entity target, CallbackInfo callback) {
        if (EventBus.global().hasListeners(AttackEvent.class)
                && EventBus.global().post(new AttackEvent(Stage.PRE, target)).isCancelled()) {
            callback.cancel();
        }
    }

    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void vantage$postAttack(EntityPlayer player, Entity target, CallbackInfo callback) {
        if (EventBus.global().hasListeners(AttackEvent.class)) {
            EventBus.global().post(new AttackEvent(Stage.POST, target));
        }
    }
}
