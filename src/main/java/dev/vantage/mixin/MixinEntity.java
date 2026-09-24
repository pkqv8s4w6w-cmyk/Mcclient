package dev.vantage.mixin;

import dev.vantage.event.EventBus;
import dev.vantage.event.HitboxEvent;
import dev.vantage.event.MoveEvent;
import dev.vantage.event.StrafeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Movement and hitboxes. Movement hooks only fire for the local player. */
@Mixin(Entity.class)
public abstract class MixinEntity {

    /** Set while re-entering moveEntity with changed values, so the event is not posted twice. */
    private static boolean vantage$reentering;
    private static boolean vantage$safeWalk;

    private boolean vantage$isLocalPlayer() {
        return (Object) this == Minecraft.getMinecraft().thePlayer;
    }

    @Inject(method = "moveEntity", at = @At("HEAD"), cancellable = true)
    private void vantage$onMove(double x, double y, double z, CallbackInfo callback) {
        if (vantage$reentering || !vantage$isLocalPlayer()) {
            return;
        }
        vantage$safeWalk = false;
        if (!EventBus.global().hasListeners(MoveEvent.class)) {
            return;
        }
        MoveEvent event = EventBus.global().post(new MoveEvent(x, y, z));
        vantage$safeWalk = event.isSafeWalk();
        if (event.isCancelled()) {
            callback.cancel();
            return;
        }
        if (event.getX() != x || event.getY() != y || event.getZ() != z) {
            // Arguments cannot be changed from an inject, so run the move again with the new ones
            // and drop the original. Collision still applies to the replacement.
            vantage$reentering = true;
            try {
                ((Entity) (Object) this).moveEntity(event.getX(), event.getY(), event.getZ());
            } finally {
                vantage$reentering = false;
            }
            callback.cancel();
        }
    }

    /** Vanilla edge-stopping is keyed on sneaking; answering yes is all safe walk takes. */
    @Redirect(method = "moveEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;isSneaking()Z"))
    private boolean vantage$edgeSafety(Entity self) {
        return self.isSneaking() || (vantage$safeWalk && (Object) self == Minecraft.getMinecraft().thePlayer);
    }

    @Inject(method = "moveFlying", at = @At("HEAD"), cancellable = true)
    private void vantage$onStrafe(float strafe, float forward, float friction, CallbackInfo callback) {
        if (!vantage$isLocalPlayer() || !EventBus.global().hasListeners(StrafeEvent.class)) {
            return;
        }
        Entity self = (Entity) (Object) this;
        StrafeEvent event = EventBus.global().post(new StrafeEvent(strafe, forward, friction, self.rotationYaw));
        callback.cancel();
        if (event.isCancelled()) {
            return;
        }
        // Vanilla's moveFlying, with the yaw taken from the event.
        float s = event.getStrafe();
        float f = event.getForward();
        float magnitude = s * s + f * f;
        if (magnitude >= 1.0E-4F) {
            magnitude = MathHelper.sqrt_float(magnitude);
            if (magnitude < 1.0F) {
                magnitude = 1.0F;
            }
            magnitude = event.getFriction() / magnitude;
            s *= magnitude;
            f *= magnitude;
            float sin = MathHelper.sin(event.getYaw() * (float) Math.PI / 180.0F);
            float cos = MathHelper.cos(event.getYaw() * (float) Math.PI / 180.0F);
            self.motionX += (double) (s * cos - f * sin);
            self.motionZ += (double) (f * cos + s * sin);
        }
    }

    @Inject(method = "getCollisionBorderSize", at = @At("HEAD"), cancellable = true)
    private void vantage$hitbox(CallbackInfoReturnable<Float> callback) {
        if (!EventBus.global().hasListeners(HitboxEvent.class)) {
            return;
        }
        HitboxEvent event = EventBus.global().post(new HitboxEvent((Entity) (Object) this, 0.1F));
        if (event.getBorder() != 0.1F) {
            callback.setReturnValue(event.getBorder());
        }
    }
}
