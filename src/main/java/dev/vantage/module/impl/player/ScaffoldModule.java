package dev.vantage.module.impl.player;

import dev.vantage.combat.RotationManager;
import dev.vantage.event.MotionEvent;
import dev.vantage.event.MoveEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.BlockUtil;
import dev.vantage.util.InventoryUtil;
import dev.vantage.util.MovementUtil;
import dev.vantage.util.PacketUtil;
import dev.vantage.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * Places blocks under you as you walk.
 *
 * <p>The position is worked out before the movement packet goes and the server is turned to face
 * the block; the block is placed after, so it arrives facing the right way. Blocks come from the
 * best stack in the hotbar, held only for the moment of placing, so the hand never changes.
 */
public class ScaffoldModule extends Module {

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

    public ScaffoldModule() {
        super("Scaffold", Category.PLAYER, "Places blocks under you as you walk");
        markBlatant();
        on(MotionEvent.class, this::onMotion);
        on(MoveEvent.class, this::onMove);
    }

    @Override
    public String getSuffix() {
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

    private void onMove(MoveEvent event) {
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
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
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
        final BlockUtil.Placement placement = pending;
        pending = null;
        final int slot = InventoryUtil.blockSlot();
        if (slot < 0) {
            return;
        }
        InventoryUtil.withSlot(slot, () -> {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (mc.playerController.onPlayerRightClick(player, mc.theWorld, stack,
                    placement.against, placement.face, placement.hitVec)) {
                if (swing.value()) {
                    player.swingItem();
                } else {
                    PacketUtil.send(new C0APacketAnimation());
                }
            }
        });
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
