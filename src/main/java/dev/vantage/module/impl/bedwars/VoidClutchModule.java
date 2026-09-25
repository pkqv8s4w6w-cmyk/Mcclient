package dev.vantage.module.impl.bedwars;

import dev.vantage.combat.RotationManager;
import dev.vantage.event.MotionEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.notify.Notifications;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.util.Ballistics;
import dev.vantage.util.BlockUtil;
import dev.vantage.util.InventoryUtil;
import dev.vantage.util.RotationUtil;
import dev.vantage.util.Simulation;
import dev.vantage.util.WorldCollider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * Saves you from being knocked into the void.
 *
 * <p>Every tick you are in the air, your fall is simulated forward with vanilla's own physics. If it
 * ends in the void, the clutch picks the best save still available: a block placed under you on
 * the tick it can reach, or, once no block can be placed in time, a pearl thrown back to the last
 * ground you stood on, aimed with the pearl's real drag and gravity.
 *
 * <p>With Only After Hit on, it only steps in when you were knocked off - not when you jump off an
 * edge on purpose.
 */
public class VoidClutchModule extends Module {

    public enum Mode { BLOCKS_THEN_PEARL, BLOCKS, PEARL }

    private static final long HIT_WINDOW_MILLIS = 2500L;
    private static final long PEARL_COOLDOWN_MILLIS = 2000L;

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "What to save yourself with", Mode.BLOCKS_THEN_PEARL));
    private final BooleanSetting afterHit = register(new BooleanSetting(
            "Only After Hit", "Only step in when you were knocked off, not when you jump", true));

    private double safeX;
    private double safeY;
    private double safeZ;
    private boolean haveSafe;
    private long lastHurtAt;
    private int lastHurtTime;
    private long lastPearlAt;

    private BlockUtil.Placement pendingBlock;
    private boolean pendingPearl;

    public VoidClutchModule() {
        super("Void Clutch", Category.BEDWARS, "Places a block or throws a pearl when you are knocked into the void");
        markBlatant();
        on(MotionEvent.class, this::onMotion);
    }

    @Override
    public void onWorldChanged() {
        haveSafe = false;
    }

    private void onMotion(MotionEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (!event.isPre()) {
            act(mc, player);
            return;
        }
        pendingBlock = null;
        pendingPearl = false;

        if (player.hurtTime > lastHurtTime) {
            lastHurtAt = System.currentTimeMillis();
        }
        lastHurtTime = player.hurtTime;

        if (player.onGround) {
            BlockPos below = new BlockPos(player.posX, player.posY - 0.5, player.posZ);
            if (!mc.theWorld.isAirBlock(below)) {
                safeX = player.posX;
                safeY = player.posY;
                safeZ = player.posZ;
                haveSafe = true;
            }
            return;
        }
        if (player.capabilities.isFlying || player.motionY > 0.0) {
            return;
        }
        if (afterHit.value() && System.currentTimeMillis() - lastHurtAt > HIT_WINDOW_MILLIS) {
            return;
        }
        Simulation.Path path = Simulation.fallingPlayer(player.posX, player.posY, player.posZ,
                player.motionX, player.motionY, player.motionZ, 80, 0.0, new WorldCollider(mc.theWorld));
        if (!path.fellIntoVoid) {
            return;
        }

        if (mode.get() != Mode.PEARL && InventoryUtil.blockSlot() >= 0) {
            pendingBlock = blockSave(player, path);
            if (pendingBlock != null) {
                Vec3 eyes = player.getPositionEyes(1.0f);
                float[] facing = RotationUtil.rotationsFor(pendingBlock.hitVec.xCoord - eyes.xCoord,
                        pendingBlock.hitVec.yCoord - eyes.yCoord, pendingBlock.hitVec.zCoord - eyes.zCoord);
                RotationManager.get().request(facing[0], facing[1], 80, 180.0f);
                return;
            }
        }
        if (mode.get() != Mode.BLOCKS && haveSafe && InventoryUtil.findHotbar(Items.ender_pearl) >= 0
                && System.currentTimeMillis() - lastPearlAt > PEARL_COOLDOWN_MILLIS) {
            float[] aim = pearlAim(player);
            if (aim != null) {
                RotationManager.get().request(aim[0], aim[1], 80, 180.0f);
                pendingPearl = true;
            }
        }
    }

    /**
     * A block under the player or the next few ticks of their fall, placed against something in
     * reach. Earlier is better: every tick of falling makes the next one harder to reach.
     */
    private static BlockUtil.Placement blockSave(EntityPlayerSP player, Simulation.Path path) {
        int lookahead = Math.min(4, path.points.size());
        for (int tick = 0; tick < lookahead; tick++) {
            double[] point = path.points.get(tick);
            BlockPos under = new BlockPos(point[0], MathHelper.floor_double(point[1]) - 1, point[2]);
            BlockUtil.Placement placement = BlockUtil.placementNear(under);
            if (placement != null && BlockUtil.eyeDistance(placement.hitVec) <= 4.5) {
                return placement;
            }
        }
        return null;
    }

    /** The rotation that throws a pearl onto the last safe ground, or null if out of reach. */
    private float[] pearlAim(EntityPlayerSP player) {
        double eyeY = player.posY + player.getEyeHeight();
        double dx = safeX - player.posX;
        double dz = safeZ - player.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        Float pitch = Ballistics.solvePitch(Simulation.Kind.THROWABLE, Math.max(0.5, horizontal), safeY - eyeY, 1.5);
        if (pitch == null) {
            return null;
        }
        float yaw = RotationUtil.rotationsFor(dx, 0.0, dz)[0];
        return new float[]{yaw, pitch};
    }

    private void act(Minecraft mc, EntityPlayerSP player) {
        if (pendingBlock != null) {
            int slot = InventoryUtil.blockSlot();
            if (slot >= 0) {
                BlockUtil.place(pendingBlock, slot, true);
            }
            pendingBlock = null;
            return;
        }
        if (pendingPearl) {
            final int slot = InventoryUtil.findHotbar(Items.ender_pearl);
            if (slot >= 0) {
                // The rotation went out with this tick's movement packet, so the throw uses it.
                InventoryUtil.withSlot(slot, () -> {
                    ItemStack pearl = player.inventory.getStackInSlot(slot);
                    mc.playerController.sendUseItem(player, mc.theWorld, pearl);
                });
                lastPearlAt = System.currentTimeMillis();
                Notifications.post("Void Clutch", "Pearled back to safety", Notifications.Kind.SUCCESS, 1500L);
            }
            pendingPearl = false;
        }
    }
}
