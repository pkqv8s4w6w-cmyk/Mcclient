package dev.vantage.mixin;

import dev.vantage.event.EventBus;
import dev.vantage.event.MotionEvent;
import dev.vantage.event.PushOutEvent;
import dev.vantage.event.SlowdownEvent;
import dev.vantage.event.Stage;
import dev.vantage.event.UpdateEvent;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The local player's update and the movement packet it sends.
 *
 * <p>Rotation and ground state are swapped into the entity for the length of
 * {@code onUpdateWalkingPlayer} and put back afterwards, rather than redirected call by call. The
 * method reads them in several places to decide which packet to send, and a swap keeps every one of
 * those reads consistent with no chance of missing one.
 */
@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP {

    private float vantage$realYaw;
    private float vantage$realPitch;
    private boolean vantage$realOnGround;
    private boolean vantage$swapped;

    private EntityPlayerSP vantage$self() {
        return (EntityPlayerSP) (Object) this;
    }

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void vantage$onUpdate(CallbackInfo callback) {
        EventBus.global().post(UpdateEvent.INSTANCE);
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("HEAD"))
    private void vantage$preMotion(CallbackInfo callback) {
        EntityPlayerSP self = vantage$self();
        MotionEvent event = new MotionEvent(Stage.PRE, self.posX, self.getEntityBoundingBox().minY, self.posZ,
                self.rotationYaw, self.rotationPitch, self.onGround);
        EventBus.global().post(event);

        vantage$realYaw = self.rotationYaw;
        vantage$realPitch = self.rotationPitch;
        vantage$realOnGround = self.onGround;
        self.rotationYaw = event.getYaw();
        self.rotationPitch = event.getPitch();
        self.onGround = event.isOnGround();
        vantage$swapped = true;
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("RETURN"))
    private void vantage$postMotion(CallbackInfo callback) {
        if (!vantage$swapped) {
            return;
        }
        EntityPlayerSP self = vantage$self();
        float sentYaw = self.rotationYaw;
        float sentPitch = self.rotationPitch;
        boolean sentOnGround = self.onGround;
        self.rotationYaw = vantage$realYaw;
        self.rotationPitch = vantage$realPitch;
        self.onGround = vantage$realOnGround;
        vantage$swapped = false;

        EventBus.global().post(new MotionEvent(Stage.POST, self.posX, self.getEntityBoundingBox().minY, self.posZ,
                sentYaw, sentPitch, sentOnGround));
    }

    /**
     * The first {@code isUsingItem} in {@code onLivingUpdate} is the one that cuts movement input to
     * a fifth. Answering false skips vanilla's cut; the event's multiplier is applied here instead,
     * so a partial slowdown is possible as well as none.
     */
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 0))
    private boolean vantage$itemSlowdown(EntityPlayerSP self) {
        boolean using = self.isUsingItem();
        if (!using || self.isRiding() || !EventBus.global().hasListeners(SlowdownEvent.class)) {
            return using;
        }
        SlowdownEvent event = EventBus.global().post(new SlowdownEvent());
        self.movementInput.moveStrafe *= event.getMultiplier();
        self.movementInput.moveForward *= event.getMultiplier();
        return false;
    }

    /** The two later checks stop sprinting from starting while an item is in use. */
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 1))
    private boolean vantage$sprintCheckOne(EntityPlayerSP self) {
        return vantage$usingBlocksSprint(self);
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 2))
    private boolean vantage$sprintCheckTwo(EntityPlayerSP self) {
        return vantage$usingBlocksSprint(self);
    }

    private boolean vantage$usingBlocksSprint(EntityPlayerSP self) {
        boolean using = self.isUsingItem();
        if (!using || !EventBus.global().hasListeners(SlowdownEvent.class)) {
            return using;
        }
        return !EventBus.global().post(new SlowdownEvent()).isSprintAllowed();
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void vantage$pushOut(double x, double y, double z, CallbackInfoReturnable<Boolean> callback) {
        if (EventBus.global().hasListeners(PushOutEvent.class)
                && EventBus.global().post(new PushOutEvent()).isCancelled()) {
            callback.setReturnValue(false);
        }
    }
}
