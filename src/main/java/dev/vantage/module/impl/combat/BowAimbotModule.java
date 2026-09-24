package dev.vantage.module.impl.combat;

import dev.vantage.combat.RotationManager;
import dev.vantage.combat.TargetFinder;
import dev.vantage.combat.TargetSettings;
import dev.vantage.event.MotionEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.Ballistics;
import dev.vantage.util.RotationUtil;
import dev.vantage.util.Simulation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;

/**
 * Aims a drawn bow so the arrow lands on the target.
 *
 * <p>The pitch is found by simulating the arrow with vanilla's own drag and gravity and searching
 * for the angle that reaches the target's distance at its height, rather than with the drag-free
 * formula most aimbots use, which falls short at range. The target is led by how far it will walk
 * during the flight.
 */
public class BowAimbotModule extends Module {

    private final NumberSetting range = register(new NumberSetting(
            "Range", "Furthest target to aim at", 60.0, 10.0, 120.0, 5.0, "m"));
    private final NumberSetting fov = register(new NumberSetting(
            "FOV", "Only targets this close to the crosshair", 90.0, 10.0, 360.0, 5.0, "°"));
    private final BooleanSetting predict = register(new BooleanSetting(
            "Predict", "Lead moving targets", true));
    private final BooleanSetting silent = register(new BooleanSetting(
            "Silent", "Aim without moving the camera", true));
    private final TargetSettings targets = new TargetSettings();

    public BowAimbotModule() {
        super("BowAimbot", Category.COMBAT, "Aims your bow so arrows land");
        markBlatant();
        registerAll(targets.all());
        on(MotionEvent.class, this::onMotion);
    }

    private void onMotion(MotionEvent event) {
        if (!event.isPre()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        ItemStack using = player.getItemInUse();
        if (using == null || !(using.getItem() instanceof ItemBow)) {
            return;
        }
        EntityLivingBase target = TargetFinder.best(targets, range.asDouble(), fov.asFloat(), TargetFinder.Sort.ANGLE);
        if (target == null) {
            return;
        }
        int charge = using.getMaxItemUseDuration() - player.getItemInUseCount();
        double velocity = Math.max(0.5, Simulation.bowVelocity(Math.max(charge, 1)));

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;
        double aimX = target.posX;
        double aimY = target.posY + target.height * 0.5;
        double aimZ = target.posZ;
        if (predict.value()) {
            // Two passes are enough for the lead to settle.
            for (int pass = 0; pass < 2; pass++) {
                double distance = Math.sqrt((aimX - eyeX) * (aimX - eyeX) + (aimZ - eyeZ) * (aimZ - eyeZ));
                double flightTicks = distance / velocity;
                aimX = target.posX + (target.posX - target.lastTickPosX) * flightTicks;
                aimZ = target.posZ + (target.posZ - target.lastTickPosZ) * flightTicks;
            }
        }
        double dx = aimX - eyeX;
        double dz = aimZ - eyeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        Float pitch = solvePitch(horizontal, aimY - eyeY, velocity);
        if (pitch == null) {
            return;
        }
        float yaw = RotationUtil.rotationsFor(dx, 0.0, dz)[0];
        if (silent.value()) {
            RotationManager.get().request(yaw, pitch, 20, 180.0f);
        } else {
            player.rotationYaw = player.rotationYaw + RotationUtil.yawDifference(player.rotationYaw, yaw);
            player.rotationPitch = pitch;
        }
    }

    static Float solvePitch(double horizontal, double height, double velocity) {
        return Ballistics.solvePitch(Simulation.Kind.ARROW, horizontal, height, velocity);
    }
}
