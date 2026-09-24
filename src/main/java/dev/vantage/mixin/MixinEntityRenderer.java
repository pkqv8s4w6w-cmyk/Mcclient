package dev.vantage.mixin;

import dev.vantage.event.CameraEvent;
import dev.vantage.event.EventBus;
import dev.vantage.event.ReachEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The crosshair trace and camera effects.
 *
 * <p>{@code getMouseOver} traces blocks and entities with the same distance, then throws away any
 * entity hit beyond three blocks. Reach needs the entity search to go further while blocks keep
 * their own limit, so the three uses are separated: the search distance becomes the larger of the
 * two reaches, the block trace gets block reach, and the three-block cut-off becomes combat reach.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    private ReachEvent vantage$reach;

    @Inject(method = "getMouseOver", at = @At("HEAD"))
    private void vantage$beginTrace(float partialTicks, CallbackInfo callback) {
        Minecraft mc = Minecraft.getMinecraft();
        double blockReach = mc.playerController == null ? 4.5 : mc.playerController.getBlockReachDistance();
        vantage$reach = EventBus.global().post(new ReachEvent(3.0, blockReach));
    }

    @Redirect(method = "getMouseOver", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;getBlockReachDistance()F"))
    private float vantage$searchDistance(PlayerControllerMP controller) {
        float vanilla = controller.getBlockReachDistance();
        if (vantage$reach == null) {
            return vanilla;
        }
        return (float) Math.max(vantage$reach.getBlockReach(), vantage$reach.getCombatReach());
    }

    @Redirect(method = "getMouseOver", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;rayTrace(DF)Lnet/minecraft/util/MovingObjectPosition;"))
    private MovingObjectPosition vantage$blockTrace(Entity entity, double distance, float partialTicks) {
        return entity.rayTrace(vantage$reach == null ? distance : vantage$reach.getBlockReach(), partialTicks);
    }

    @ModifyConstant(method = "getMouseOver", constant = @Constant(doubleValue = 3.0D))
    private double vantage$combatReach(double vanilla) {
        return vantage$reach == null ? vanilla : vantage$reach.getCombatReach();
    }

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void vantage$hurtShake(float partialTicks, CallbackInfo callback) {
        if (EventBus.global().hasListeners(CameraEvent.class)
                && !EventBus.global().post(new CameraEvent()).isHurtShake()) {
            callback.cancel();
        }
    }

    /** Terrain culling assumes the camera is outside blocks unless the player is a spectator. */
    @Redirect(method = "renderWorldPass", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;isSpectator()Z"))
    private boolean vantage$cullAsSpectator(EntityPlayerSP player) {
        if (player.isSpectator()) {
            return true;
        }
        return EventBus.global().hasListeners(CameraEvent.class)
                && EventBus.global().post(new CameraEvent()).isFreeCamera();
    }
}
