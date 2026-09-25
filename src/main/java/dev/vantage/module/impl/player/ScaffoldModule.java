package dev.vantage.module.impl.player;

import dev.vantage.combat.RotationManager;
import dev.vantage.event.MotionEvent;
import dev.vantage.event.MoveEvent;
import dev.vantage.event.TickStartEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.BlockUtil;
import dev.vantage.util.InventoryUtil;
import dev.vantage.util.MovementUtil;
import dev.vantage.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Places blocks under you as you walk, or helps you bridge by hand.
 *
 * <p>In Normal mode the position is worked out before the movement packet goes and the server is
 * turned to face the block; the block is placed after, so it arrives facing the right way. Blocks
 * come from the best stack in the hotbar, held only for the moment of placing, so the hand never
 * changes.
 *
 * <p>In Legit mode nothing is placed and nothing is rotated for you. You bridge the way you
 * normally would, and the sneak key is held down for you the moment you reach the edge of the block
 * you are standing on, then let go as soon as a block is under you again - the timing speed
 * bridging depends on. With Auto Place it also right-clicks when your crosshair is on the face that
 * fills the gap, exactly as if you had clicked.
 */
public class ScaffoldModule extends Module {

    public enum Mode { NORMAL, LEGIT }

    /** Legit mode only helps while you look down at a bridge, not while walking around. */
    private static final float LEGIT_MIN_PITCH = 30.0f;

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "Normal places blocks for you. Legit sneaks for you at the edge while you bridge",
            Mode.NORMAL));
    private final BooleanSetting autoPlace = register(new BooleanSetting(
            "Auto Place", "Right click for you when your crosshair is on the face that fills the gap", false));
    private final BooleanSetting rotate = register(new BooleanSetting(
            "Rotate", "Face each block as it is placed", true));
    private final BooleanSetting tower = register(new BooleanSetting(
            "Tower", "Build straight up while holding jump", true));
    private final BooleanSetting keepY = register(new BooleanSetting(
            "Keep Y", "Stay on the level you started, even while jumping", false));
    private final BooleanSetting safeWalk = register(new BooleanSetting(
            "SafeWalk", "Never step off the edge between placements", true));
    private final BooleanSetting sprint = register(new BooleanSetting(
            "Sprint", "Allow sprinting while bridging", true));
    private final BooleanSetting swing = register(new BooleanSetting(
            "Swing", "Show the arm swinging on each block", true));
    private final NumberSetting expand = register(new NumberSetting(
            "Expand", "Place this far ahead of you", 0.0, 0.0, 4.0, 0.5, "m"));
    private final NumberSetting delay = register(new NumberSetting(
            "Delay", "Ticks between placements", 0.0, 0.0, 5.0, 1.0));

    private BlockUtil.Placement pending;
    private int startY;
    private int cooldown;
    private boolean holdingSneak;

    public ScaffoldModule() {
        super("Scaffold", Category.PLAYER, "Places blocks under you as you walk, or sneaks for you at the edge");
        markBlatant();
        autoPlace.visibleWhen(() -> mode.get() == Mode.LEGIT);
        rotate.visibleWhen(this::normal);
        tower.visibleWhen(this::normal);
        keepY.visibleWhen(this::normal);
        safeWalk.visibleWhen(this::normal);
        sprint.visibleWhen(this::normal);
        swing.visibleWhen(this::normal);
        expand.visibleWhen(this::normal);
        delay.visibleWhen(this::normal);
        on(MotionEvent.class, this::onMotion);
        on(MoveEvent.class, this::onMove);
        on(TickStartEvent.class, event -> legitTick());
    }

    private boolean normal() {
        return mode.get() == Mode.NORMAL;
    }

    @Override
    public String getSuffix() {
        if (!normal()) {
            return "Legit";
        }
        return Minecraft.getMinecraft().thePlayer == null ? null : String.valueOf(InventoryUtil.blockCount());
    }

    @Override
    protected void onEnable() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            startY = MathHelper.floor_double(player.posY) - 1;
        }
        pending = null;
    }

    @Override
    protected void onDisable() {
        if (holdingSneak) {
            releaseSneak();
        }
    }

    // -- legit ------------------------------------------------------------------------------

    /**
     * Runs before the game reads the keys for this tick, so the sneak key's state is what the
     * player's movement and the sneak packet both see this tick.
     */
    private void legitTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (mode.get() != Mode.LEGIT || mc.currentScreen != null || !atEdge(player)) {
            if (holdingSneak) {
                releaseSneak();
            }
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        holdingSneak = true;
        if (autoPlace.value()) {
            clickIntoGap(mc, player);
        }
    }

    /**
     * Whether the player is bridging and about to step off: on the ground, holding blocks, looking
     * down, and with nothing under the middle of their feet now or where their momentum takes them
     * next tick. Looking a tick ahead catches the edge in time even at sprinting speed.
     */
    private static boolean atEdge(EntityPlayerSP player) {
        if (!player.onGround || player.rotationPitch < LEGIT_MIN_PITCH) {
            return false;
        }
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return false;
        }
        return BlockUtil.isReplaceable(gapUnder(player, 0.0)) || BlockUtil.isReplaceable(gapUnder(player, 1.0));
    }

    /** The block under the middle of the player's feet, {@code ticks} of momentum from now. */
    private static BlockPos gapUnder(EntityPlayerSP player, double ticks) {
        // Half a block down rather than a whole one, so a slab underfoot counts as the ground.
        return new BlockPos(player.posX + player.motionX * ticks, player.posY - 0.5,
                player.posZ + player.motionZ * ticks);
    }

    /** Presses use once if the crosshair is on a face whose placement would fill the gap. */
    private static void clickIntoGap(Minecraft mc, EntityPlayerSP player) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.getBlockPos() == null) {
            return;
        }
        BlockPos result = hit.getBlockPos().offset(hit.sideHit);
        for (double ticks = 0.0; ticks <= 1.0; ticks += 1.0) {
            BlockPos gap = gapUnder(player, ticks);
            if (result.equals(gap) && BlockUtil.isReplaceable(gap)) {
                KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                return;
            }
        }
    }

    /** Hands the sneak key back in whatever state the player is really holding it. */
    private void releaseSneak() {
        KeyBinding sneak = Minecraft.getMinecraft().gameSettings.keyBindSneak;
        KeyBinding.setKeyBindState(sneak.getKeyCode(), GameSettings.isKeyDown(sneak));
        holdingSneak = false;
    }

    // -- normal -----------------------------------------------------------------------------

    private void onMove(MoveEvent event) {
        if (!normal()) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (safeWalk.value() && player.onGround) {
            event.setSafeWalk(true);
        }
        if (!sprint.value()) {
            player.setSprinting(false);
        }
        if (tower.value() && player.movementInput.jump && !MovementUtil.isMoving() && InventoryUtil.blockSlot() >= 0) {
            // Rise a block per jump without drifting, so each placement lands straight below.
            event.setX(0.0);
            event.setZ(0.0);
            player.motionX = 0.0;
            player.motionZ = 0.0;
            if (player.onGround) {
                player.motionY = 0.42;
                event.setY(0.42);
            }
        }
    }

    private void onMotion(MotionEvent event) {
        if (!normal()) {
            pending = null;
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (event.isPre()) {
            pending = null;
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (InventoryUtil.blockSlot() < 0) {
                return;
            }
            BlockPos target = targetBelow(player);
            if (!BlockUtil.isReplaceable(target)) {
                return;
            }
            pending = BlockUtil.placementNear(target);
            if (pending != null && rotate.value()) {
                Vec3 eyes = player.getPositionEyes(1.0f);
                float[] facing = RotationUtil.rotationsFor(pending.hitVec.xCoord - eyes.xCoord,
                        pending.hitVec.yCoord - eyes.yCoord, pending.hitVec.zCoord - eyes.zCoord);
                RotationManager.get().request(facing[0], facing[1], 30, 180.0f);
            }
            return;
        }
        if (pending == null) {
            return;
        }
        BlockUtil.Placement placement = pending;
        pending = null;
        int slot = InventoryUtil.blockSlot();
        if (slot >= 0) {
            BlockUtil.place(placement, slot, swing.value());
        }
        cooldown = delay.asInt();
    }

    private BlockPos targetBelow(EntityPlayerSP player) {
        int y = keepY.value() && !player.movementInput.jump
                ? startY
                : MathHelper.floor_double(player.posY) - 1;
        if (!keepY.value() || player.movementInput.jump) {
            startY = y;
        }
        double x = player.posX;
        double z = player.posZ;
        if (expand.asDouble() > 0.0 && MovementUtil.isMoving()) {
            double direction = MovementUtil.direction();
            // Walk the expansion out one block at a time and take the first gap.
            for (double step = 0.0; step <= expand.asDouble(); step += 0.5) {
                BlockPos candidate = new BlockPos(x - Math.sin(direction) * step, y, z + Math.cos(direction) * step);
                if (BlockUtil.isReplaceable(candidate)) {
                    return candidate;
                }
            }
        }
        return new BlockPos(x, y, z);
    }
}
