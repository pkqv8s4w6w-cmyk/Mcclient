package dev.vantage.module.impl.combat;

import dev.vantage.combat.ClickTimer;
import dev.vantage.combat.CombatUtil;
import dev.vantage.combat.RotationManager;
import dev.vantage.combat.TargetFinder;
import dev.vantage.combat.TargetSettings;
import dev.vantage.event.MotionEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.InventoryUtil;
import dev.vantage.util.PacketUtil;
import dev.vantage.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.List;

/**
 * Attacks nearby targets without the player aiming.
 *
 * <p>Targets are picked and turned toward before the movement packet goes, so the server receives
 * the rotation first; the attack itself happens after, so it arrives already facing the target.
 */
public class KillAuraModule extends Module {

    public enum Mode { SINGLE, SWITCH, MULTI }

    public enum Rotations { SILENT, CLIENT, NONE }

    public enum AutoBlock { NONE, FAKE, REAL }

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "One target, cycle between several, or hit everyone in range", Mode.SWITCH));
    private final NumberSetting range = register(new NumberSetting(
            "Range", "How far away a target can be hit", 4.0, 2.5, 6.0, 0.1, "m"));
    private final NumberSetting minCps = register(new NumberSetting(
            "Min CPS", "Slowest attack rate", 9.0, 1.0, 20.0, 1.0));
    private final NumberSetting maxCps = register(new NumberSetting(
            "Max CPS", "Fastest attack rate", 13.0, 1.0, 20.0, 1.0));
    private final EnumSetting<Rotations> rotations = register(new EnumSetting<Rotations>(
            "Rotations", "Silent turns only what the server sees; Client turns the camera too", Rotations.SILENT));
    private final NumberSetting turnSpeed = register(new NumberSetting(
            "Turn Speed", "Most degrees turned per tick", 90.0, 10.0, 180.0, 5.0, "°"));
    private final NumberSetting fov = register(new NumberSetting(
            "FOV", "Only targets inside this cone", 360.0, 30.0, 360.0, 10.0, "°"));
    private final EnumSetting<TargetFinder.Sort> sort = register(new EnumSetting<TargetFinder.Sort>(
            "Priority", "Which target comes first", TargetFinder.Sort.DISTANCE));
    private final NumberSetting switchDelay = register(new NumberSetting(
            "Switch Delay", "How long to stay on one target in Switch mode", 300.0, 50.0, 1000.0, 50.0, "ms"));
    private final EnumSetting<AutoBlock> autoBlock = register(new EnumSetting<AutoBlock>(
            "Auto Block", "Fake shows the animation; Real blocks between hits", AutoBlock.NONE));
    private final BooleanSetting raytrace = register(new BooleanSetting(
            "Raytrace", "Only attack once the rotation is actually on the target", false));
    private final BooleanSetting swing = register(new BooleanSetting(
            "Swing", "Show the arm swinging", true));
    private final BooleanSetting weaponOnly = register(new BooleanSetting(
            "Weapon Only", "Only attack while holding a sword or tool", false));
    private final TargetSettings targets = new TargetSettings();

    private final ClickTimer timer = new ClickTimer();
    private EntityLivingBase target;
    private List<EntityLivingBase> inRange;
    private int switchIndex;
    private long switchedAt;
    private boolean blocking;

    public KillAuraModule() {
        super("KillAura", Category.COMBAT, "Attacks targets around you without aiming");
        markBlatant();
        registerAll(targets.all());
        turnSpeed.visibleWhen(() -> rotations.get() != Rotations.NONE);
        switchDelay.visibleWhen(() -> mode.get() == Mode.SWITCH);
        on(MotionEvent.class, this::onMotion);
    }

    @Override
    public String getSuffix() {
        return mode.currentLabel();
    }

    /** The current target, for the target HUD and target strafe. */
    public EntityLivingBase getTarget() {
        return isEnabled() ? target : null;
    }

    @Override
    protected void onEnable() {
        timer.reset();
        target = null;
    }

    @Override
    protected void onDisable() {
        target = null;
        stopBlocking();
    }

    private void onMotion(MotionEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (event.isPre()) {
            pickTarget(player);
            if (target != null) {
                rotate(player);
            } else {
                stopBlocking();
            }
            return;
        }
        if (target == null) {
            return;
        }
        if (weaponOnly.value() && !InventoryUtil.holdingWeapon()) {
            return;
        }
        if (!timer.shouldClick(System.currentTimeMillis(), minCps.asDouble(), maxCps.asDouble())) {
            return;
        }
        attack(player);
    }

    private void pickTarget(EntityPlayerSP player) {
        // Search a little past attack range, so the aura is already turned when a target steps in.
        inRange = TargetFinder.find(targets, range.asDouble() + 1.0, fov.asFloat(), sort.get());
        if (inRange.isEmpty()) {
            target = null;
            return;
        }
        if (mode.get() == Mode.SWITCH && inRange.size() > 1) {
            long now = System.currentTimeMillis();
            if (now - switchedAt >= switchDelay.asInt()) {
                switchIndex = (switchIndex + 1) % inRange.size();
                switchedAt = now;
            }
            target = inRange.get(Math.min(switchIndex, inRange.size() - 1));
        } else {
            target = inRange.get(0);
        }
    }

    private void rotate(EntityPlayerSP player) {
        float[] wanted = TargetFinder.aimAt(player, target, 0.6);
        switch (rotations.get()) {
            case SILENT:
                RotationManager.get().request(wanted[0], wanted[1], 10, turnSpeed.asFloat());
                break;
            case CLIENT:
                float[] next = RotationUtil.stepTowards(player.rotationYaw, player.rotationPitch,
                        wanted[0], wanted[1], turnSpeed.asFloat(), turnSpeed.asFloat());
                player.rotationYaw = next[0];
                player.rotationPitch = next[1];
                break;
            default:
                break;
        }
    }

    private void attack(EntityPlayerSP player) {
        boolean hitSomething = false;
        if (mode.get() == Mode.MULTI) {
            for (EntityLivingBase each : inRange) {
                if (TargetFinder.distanceToBox(player, each) <= range.asDouble()) {
                    releaseBlockForHit();
                    CombatUtil.attack(each, swing.value());
                    hitSomething = true;
                }
            }
        } else if (TargetFinder.distanceToBox(player, target) <= range.asDouble() && rotationOnTarget(player)) {
            releaseBlockForHit();
            CombatUtil.attack(target, swing.value());
            hitSomething = true;
        }
        if (hitSomething) {
            startBlocking(player);
        }
    }

    private boolean rotationOnTarget(EntityPlayerSP player) {
        if (!raytrace.value() || rotations.get() == Rotations.NONE) {
            return true;
        }
        float yaw = rotations.get() == Rotations.SILENT ? RotationManager.get().getServerYaw() : player.rotationYaw;
        float pitch = rotations.get() == Rotations.SILENT ? RotationManager.get().getServerPitch() : player.rotationPitch;
        return CombatUtil.rayHits(yaw, pitch, target, range.asDouble());
    }

    private boolean holdingSword(EntityPlayerSP player) {
        ItemStack held = player.getHeldItem();
        return held != null && held.getItem() instanceof ItemSword;
    }

    private void startBlocking(EntityPlayerSP player) {
        if (autoBlock.get() == AutoBlock.NONE || !holdingSword(player)) {
            return;
        }
        ItemStack held = player.getHeldItem();
        if (autoBlock.get() == AutoBlock.REAL) {
            Minecraft.getMinecraft().playerController.sendUseItem(player, player.worldObj, held);
        } else {
            // The animation only: the server is never told.
            player.setItemInUse(held, held.getMaxItemUseDuration());
        }
        blocking = true;
    }

    /** A blocking player cannot attack, so a real block has to be dropped for the hit. */
    private void releaseBlockForHit() {
        if (blocking && autoBlock.get() == AutoBlock.REAL) {
            PacketUtil.send(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                    BlockPos.ORIGIN, EnumFacing.DOWN));
        }
    }

    private void stopBlocking() {
        if (!blocking) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            if (autoBlock.get() == AutoBlock.REAL) {
                mc.playerController.onStoppedUsingItem(mc.thePlayer);
            } else {
                mc.thePlayer.stopUsingItem();
            }
        }
        blocking = false;
    }
}
