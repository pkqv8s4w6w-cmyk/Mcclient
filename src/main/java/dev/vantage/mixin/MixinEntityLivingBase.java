package dev.vantage.mixin;

import dev.vantage.event.EventBus;
import dev.vantage.event.JumpEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Jumping, for the local player only. */
@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase {

    @Shadow
    protected abstract float getJumpUpwardsMotion();

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void vantage$onJump(CallbackInfo callback) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (self != Minecraft.getMinecraft().thePlayer || !EventBus.global().hasListeners(JumpEvent.class)) {
            return;
        }
        JumpEvent event = EventBus.global().post(new JumpEvent(getJumpUpwardsMotion(), self.rotationYaw));
        callback.cancel();
        if (event.isCancelled()) {
            return;
        }
        // Vanilla's jump, with the upward motion and the sprint direction taken from the event.
        self.motionY = event.getMotionY();
        if (self.isPotionActive(Potion.jump)) {
            self.motionY += (double) ((float) (self.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F);
        }
        if (self.isSprinting()) {
            float radians = event.getYaw() * 0.017453292F;
            self.motionX -= (double) (MathHelper.sin(radians) * 0.2F);
            self.motionZ += (double) (MathHelper.cos(radians) * 0.2F);
        }
        self.isAirBorne = true;
        ForgeHooks.onLivingJump(self);
    }
}
