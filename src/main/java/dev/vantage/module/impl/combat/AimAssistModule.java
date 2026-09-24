package dev.vantage.module.impl.combat;

import dev.vantage.combat.TargetFinder;
import dev.vantage.combat.TargetSettings;
import dev.vantage.event.Render2DEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.InventoryUtil;
import dev.vantage.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Mouse;

/**
 * Pulls the crosshair toward the nearest target while you fight.
 *
 * <p>Runs every frame rather than every tick, scaled by the time the frame took, so the pull is as
 * smooth as moving the mouse and takes the same time at any frame rate.
 */
public class AimAssistModule extends Module {

    private final NumberSetting speed = register(new NumberSetting(
            "Speed", "How hard the aim is pulled, in degrees per second", 120.0, 10.0, 540.0, 10.0, "°/s"));
    private final NumberSetting fov = register(new NumberSetting(
            "FOV", "Only targets this close to the crosshair", 60.0, 10.0, 180.0, 5.0, "°"));
    private final NumberSetting range = register(new NumberSetting(
            "Range", "How far a target can be", 4.5, 3.0, 8.0, 0.1, "m"));
    private final BooleanSetting whileClicking = register(new BooleanSetting(
            "While Clicking", "Only while the attack button is held", true));
    private final BooleanSetting weaponOnly = register(new BooleanSetting(
            "Weapon Only", "Only while holding a sword or tool", false));
    private final BooleanSetting vertical = register(new BooleanSetting(
            "Vertical", "Pull up and down as well as sideways", false));
    private final TargetSettings targets = new TargetSettings();

    private long lastFrame;

    public AimAssistModule() {
        super("AimAssist", Category.COMBAT, "Smoothly pulls your aim onto the nearest target");
        registerAll(targets.all());
        on(Render2DEvent.class, this::onFrame);
    }

    private void onFrame(Render2DEvent event) {
        long now = System.nanoTime();
        float seconds = lastFrame == 0L ? 0.0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
        lastFrame = now;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            return;
        }
        if (whileClicking.value() && !Mouse.isButtonDown(0)) {
            return;
        }
        if (weaponOnly.value() && !InventoryUtil.holdingWeapon()) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        EntityLivingBase target = TargetFinder.best(targets, range.asDouble(), fov.asFloat(), TargetFinder.Sort.ANGLE);
        if (target == null) {
            return;
        }
        float[] wanted = TargetFinder.aimAt(player, target, 0.6);
        float step = speed.asFloat() * seconds;
        // Ease in as the crosshair closes on the target, so it settles rather than overshooting.
        float yawGap = Math.abs(RotationUtil.yawDifference(player.rotationYaw, wanted[0]));
        float yawStep = Math.min(step, yawGap * 0.6f + 0.2f);
        float pitchStep = vertical.value() ? Math.min(step, Math.abs(wanted[1] - player.rotationPitch) * 0.6f) : 0.0f;
        float[] next = RotationUtil.stepTowards(player.rotationYaw, player.rotationPitch, wanted[0], wanted[1],
                yawStep, pitchStep);
        player.rotationYaw = next[0];
        player.rotationPitch = next[1];
    }
}
