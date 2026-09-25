package dev.vantage.module.impl.bedwars;

import dev.vantage.combat.RotationManager;
import dev.vantage.event.MotionEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.notify.Notifications;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
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
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saves you from being knocked into the void.
 *
 * <p>Every tick you are in the air, your fall is simulated forward with vanilla's own physics. If it
 * ends in the void, the clutch picks the best save still available: blocks placed under where you
 * are about to come down, or, once no block can be placed in time, a pearl thrown back to the last
 * ground you stood on, aimed with the pearl's real drag and gravity.
 *
 * <p>It starts the moment you leave the ground, on the way up as well as down, because that is when
 * you are still close to the bridge you were knocked off. A block has to be placed against one that
 * is already there, so when nothing touches the spot under you it builds out to it: a short chain of
 * blocks from the nearest solid block, the shortest one that can be finished before you pass over
 * the spot. Blocks Per Tick sets how much of the chain goes down in one tick.
 *
 * <p>With Only After Hit on, it only steps in when you were knocked off - not when you jump off an
 * edge on purpose.
 */
public class VoidClutchModule extends Module {

    public enum Mode { BLOCKS_THEN_PEARL, BLOCKS, PEARL }

    private static final long HIT_WINDOW_MILLIS = 2500L;
    private static final long PEARL_COOLDOWN_MILLIS = 2000L;
    /** How far along the fall to look for somewhere to land. */
    private static final int LOOKAHEAD_TICKS = 12;
    /** The most blocks a chain out to you may take. */
    private static final int MAX_CHAIN = 3;
    /** How far from your eyes a block may be clicked. */
    private static final double REACH = 4.5;

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "What to save yourself with", Mode.BLOCKS_THEN_PEARL));
    private final BooleanSetting afterHit = register(new BooleanSetting(
            "Only After Hit", "Only step in when you were knocked off, not when you jump", true));
    private final NumberSetting blocksPerTick = register(new NumberSetting(
            "Blocks Per Tick", "How many blocks it may place in one tick when it has to build out to you",
            1.0, 1.0, 3.0, 1.0));

    private double safeX;
    private double safeY;
    private double safeZ;
    private boolean haveSafe;
    private long lastHurtAt;
    private int lastHurtTime;
    private long lastPearlAt;

    /** Blocks to place after this tick's movement packet, in order, each resting on the one before. */
    private List<BlockPos> pendingBlocks;
    private boolean pendingPearl;

    public VoidClutchModule() {
        super("Void Clutch", Category.BEDWARS, "Builds under you or throws a pearl when you are knocked into the void");
        markBlatant();
        blocksPerTick.visibleWhen(() -> mode.get() != Mode.PEARL);
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
        pendingBlocks = null;
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
        if (player.capabilities.isFlying) {
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
            List<BlockPos> chain = blockSave(player, path, blocksPerTick.asInt());
            BlockUtil.Placement first = chain == null ? null : BlockUtil.placementFor(chain.get(0));
            if (first != null) {
                pendingBlocks = chain.subList(0, Math.min(chain.size(), blocksPerTick.asInt()));
                Vec3 eyes = player.getPositionEyes(1.0f);
                float[] facing = RotationUtil.rotationsFor(first.hitVec.xCoord - eyes.xCoord,
                        first.hitVec.yCoord - eyes.yCoord, first.hitVec.zCoord - eyes.zCoord);
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
     * The blocks to place, in order, so that there is something to land on where the fall comes
     * down. Every block height the fall drops through is a candidate, at the exact spot it drops
     * through it, since that is where a block there would catch it. The shortest chain wins, and of
     * equal chains the earliest, since every tick of falling takes you further from the bridge. A
     * chain only counts if it can be finished before you get there.
     *
     * <p>Nothing above your feet right now is considered: on the way up you would have to pass
     * through it, and it would stop the jump rather than catch the fall.
     *
     * @return the chain from the block that can be placed now to the one you will land on, or null
     */
    private static List<BlockPos> blockSave(EntityPlayerSP player, Simulation.Path path, int perTick) {
        AxisAlignedBB body = player.getEntityBoundingBox();
        Set<BlockPos> tried = new HashSet<BlockPos>();
        List<BlockPos> best = null;
        int last = Math.min(LOOKAHEAD_TICKS, path.points.size() - 1);
        for (int tick = 0; tick < last; tick++) {
            double[] from = path.points.get(tick);
            double[] to = path.points.get(tick + 1);
            if (to[1] >= from[1]) {
                continue;
            }
            for (int top = MathHelper.floor_double(Math.min(from[1], body.minY)); top > to[1]; top--) {
                double along = (from[1] - top) / (from[1] - to[1]);
                BlockPos landing = new BlockPos(from[0] + (to[0] - from[0]) * along, top - 1,
                        from[2] + (to[2] - from[2]) * along);
                if (!tried.add(landing)) {
                    continue;
                }
                List<BlockPos> chain = chainTo(landing, body);
                if (chain == null) {
                    continue;
                }
                int ticksToBuild = (chain.size() + perTick - 1) / perTick - 1;
                if (ticksToBuild <= tick && (best == null || chain.size() < best.size())) {
                    best = chain;
                }
            }
        }
        return best;
    }

    /**
     * The shortest run of blocks from something solid out to {@code landing}, searched outward from
     * the landing spot so the first block reached that can be placed now ends the search.
     *
     * @return the chain, starting with the block that can be placed now and ending at the landing
     * spot, or null if it would take more than {@link #MAX_CHAIN} blocks
     */
    private static List<BlockPos> chainTo(BlockPos landing, AxisAlignedBB body) {
        if (!canFill(landing, body)) {
            return null;
        }
        Map<BlockPos, BlockPos> towardLanding = new HashMap<BlockPos, BlockPos>();
        Map<BlockPos, Integer> depth = new HashMap<BlockPos, Integer>();
        Deque<BlockPos> queue = new ArrayDeque<BlockPos>();
        towardLanding.put(landing, null);
        depth.put(landing, 1);
        queue.add(landing);
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            BlockUtil.Placement placement = BlockUtil.placementFor(cell);
            if (placement != null && BlockUtil.eyeDistance(placement.hitVec) <= REACH) {
                List<BlockPos> chain = new ArrayList<BlockPos>();
                for (BlockPos step = cell; step != null; step = towardLanding.get(step)) {
                    chain.add(step);
                }
                return chain;
            }
            int next = depth.get(cell) + 1;
            if (next > MAX_CHAIN) {
                continue;
            }
            for (EnumFacing direction : EnumFacing.values()) {
                BlockPos neighbour = cell.offset(direction);
                if (towardLanding.containsKey(neighbour) || !canFill(neighbour, body)) {
                    continue;
                }
                towardLanding.put(neighbour, cell);
                depth.put(neighbour, next);
                queue.add(neighbour);
            }
        }
        return null;
    }

    /** Whether a block could go here: empty, within reach, and not inside the player. */
    private static boolean canFill(BlockPos cell, AxisAlignedBB body) {
        if (!BlockUtil.isReplaceable(cell)) {
            return false;
        }
        AxisAlignedBB space = new AxisAlignedBB(cell, cell.add(1, 1, 1));
        if (body.intersectsWith(space)) {
            return false;
        }
        Vec3 centre = new Vec3(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
        return BlockUtil.eyeDistance(centre) <= REACH + 0.5;
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
        if (pendingBlocks != null) {
            // Each block goes down against the one before it, which is already in the world by the
            // time the next is placed.
            for (BlockPos cell : pendingBlocks) {
                int slot = InventoryUtil.blockSlot();
                BlockUtil.Placement placement = BlockUtil.placementFor(cell);
                if (slot < 0 || placement == null || BlockUtil.eyeDistance(placement.hitVec) > REACH
                        || !BlockUtil.place(placement, slot, true)) {
                    break;
                }
            }
            pendingBlocks = null;
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
