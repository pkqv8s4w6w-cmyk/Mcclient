package dev.vantage.module.impl.combat;

import dev.vantage.combat.TargetFinder;
import dev.vantage.combat.TargetSettings;
import dev.vantage.event.ClickEvent;
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
 * smooth as moving the mouse and takes the same time at any frame rate. It aims at the target as
 * drawn on this frame, and turns the camera the way mouse input does, so the pull shows on the
 * frame it happens instead of being spread over the next tick.
 *
 * <p>Every turn is a whole number of the steps your mouse makes at your sensitivity, like real
 * mouse movement. The fraction left over is carried to the next frame rather than dropped, which
 * would otherwise swallow the whole pull at high frame rates.
 */
public class AimAssistModule extends Module {

    /**
     * How long after a click the aim keeps helping. Clicking releases the button between clicks, so
     * testing whether it is down right now would switch the pull off most of the time.
     */
    private static final long CLICK_WINDOW_MILLIS = 450L;

    private final NumberSetting speed = register(new NumberSetting(
            "Speed", "How hard the aim is pulled, in degrees per second", 120.0, 10.0, 540.0, 10.0, "°/s"));
    private final NumberSetting fov = register(new NumberSetting(
            "FOV", "Only targets this close to the crosshair", 60.0, 10.0, 180.0, 5.0, "°"));
    private final NumberSetting range = register(new NumberSetting(
            "Range", "How far a target can be", 4.5, 3.0, 8.0, 0.1, "m"));
    private final BooleanSetting whileClicking = register(new BooleanSetting(
            "While Clicking", "Only while you are clicking or holding attack", true));
    private final BooleanSetting weaponOnly = register(new BooleanSetting(
            "Weapon Only", "Only while holding a sword or tool", false));
    private final BooleanSetting vertical = register(new BooleanSetting(
            "Vertical", "Pull up and down as well as sideways", false));
    private final TargetSettings targets = new TargetSettings();

    private long lastFrame;
    private long lastClick;
    private float yawCarry;
    private float pitchCarry;

    public AimAssistModule() {
        super("AimAssist", Category.COMBAT, "Smoothly pulls your aim onto the nearest target");
        registerAll(targets.all());
        on(Render2DEvent.class, this::onFrame);
        on(ClickEvent.class, event -> {
            if (event.isLeft()) {
                lastClick = System.currentTimeMillis();
            }
        });
    }

    @Override
    protected void onEnable() {
        lastFrame = 0L;
        clearCarry();
    }

    private boolean clicking() {
        return Mouse.isButtonDown(0) || System.currentTimeMillis() - lastClick <= CLICK_WINDOW_MILLIS;
    }

    private void clearCarry() {
        yawCarry = 0.0f;
        pitchCarry = 0.0f;
    }

    private void onFrame(Render2DEvent event) {
        long now = System.nanoTime();
        float seconds = lastFrame == 0L ? 0.0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
        lastFrame = now;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || !mc.inGameHasFocus
                || (whileClicking.value() && !clicking())
                || (weaponOnly.value() && !InventoryUtil.holdingWeapon())) {
            clearCarry();
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        EntityLivingBase target = TargetFinder.best(targets, range.asDouble(), fov.asFloat(), TargetFinder.Sort.ANGLE);
        if (target == null) {
            clearCarry();
            return;
        }
        float[] wanted = TargetFinder.aimAt(player, target, 0.6, event.getPartialTicks());
        float step = speed.asFloat() * seconds;
        // Ease in as the crosshair closes on the target, so it settles rather than overshooting.
        float yawGap = Math.abs(RotationUtil.yawDifference(player.rotationYaw, wanted[0]));
        float yawStep = Math.min(step, yawGap * 0.6f + 0.2f);
        float pitchStep = vertical.value() ? Math.min(step, Math.abs(wanted[1] - player.rotationPitch) * 0.6f) : 0.0f;
        float[] next = RotationUtil.stepTowards(player.rotationYaw, player.rotationPitch, wanted[0], wanted[1],
                yawStep, pitchStep);

        float mouseStep = RotationUtil.mouseStep(mc.gameSettings.mouseSensitivity);
        // Closer than one mouse step is on target. Pulling further would only flick a step either
        // side of it for as long as it stays there.
        if (yawGap < mouseStep) {
            yawCarry = 0.0f;
        } else {
            yawCarry += next[0] - player.rotationYaw;
        }
        if (Math.abs(wanted[1] - player.rotationPitch) < mouseStep) {
            pitchCarry = 0.0f;
        } else {
            pitchCarry += next[1] - player.rotationPitch;
        }
        float yawTurn = wholeSteps(yawCarry, mouseStep);
        float pitchTurn = wholeSteps(pitchCarry, mouseStep);
        yawCarry -= yawTurn;
        pitchCarry -= pitchTurn;
        if (yawTurn == 0.0f && pitchTurn == 0.0f) {
            return;
        }

        // The previous rotation moves too, as it does for mouse input, so the camera shows the whole
        // turn now rather than easing into it across the next tick.
        float pitch = RotationUtil.clampPitch(player.rotationPitch + pitchTurn);
        pitchTurn = pitch - player.rotationPitch;
        player.rotationYaw += yawTurn;
        player.prevRotationYaw += yawTurn;
        player.rotationPitch = pitch;
        player.prevRotationPitch += pitchTurn;
    }

    /** The largest whole number of mouse steps within {@code amount}, keeping its sign. */
    private static float wholeSteps(float amount, float mouseStep) {
        if (mouseStep <= 0.0f) {
            return amount;
        }
        return (float) ((int) (amount / mouseStep)) * mouseStep;
    }
}
