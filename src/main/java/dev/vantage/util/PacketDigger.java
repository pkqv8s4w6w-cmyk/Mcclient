package dev.vantage.util;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * Breaks a block by talking to the server directly, without the crosshair on it.
 *
 * <p>A 1.8 server lets a block go when the digging it has timed reaches 70%, and it judges that
 * with whatever is in the player's hand at the moment digging stops. So the dig can start holding
 * anything, and the best tool only has to be held for the single packet that finishes it. The
 * server also counts a player off the ground as digging five times slower, and it checks that
 * state at the finishing packet too, so the finish waits until the rate is good enough.
 */
public final class PacketDigger {

    /** What the server needs to see, with a sliver of margin for a tick of latency. */
    private static final float FINISH_THRESHOLD = 0.75f;

    private BlockPos target;
    private EnumFacing face;
    private int ticks;

    public boolean isDigging() {
        return target != null;
    }

    public BlockPos getTarget() {
        return target;
    }

    /** Starts on a block, abandoning any other. */
    public void start(BlockPos pos, EnumFacing side) {
        if (pos.equals(target)) {
            return;
        }
        abort();
        target = pos;
        face = side;
        ticks = 0;
        PacketUtil.send(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, side));
    }

    /**
     * Advances one tick.
     *
     * @return true once the block has been finished off this tick
     */
    public boolean tick() {
        if (target == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Block block = mc.theWorld.getBlockState(target).getBlock();
        if (block.getMaterial().isReplaceable()) {
            target = null;
            return true;
        }
        ticks++;
        PacketUtil.send(new C0APacketAnimation());
        final int slot = InventoryUtil.toolSlotFor(block);
        float perTick = relativeHardness(mc.thePlayer, mc.thePlayer.inventory.getStackInSlot(slot), block);
        // The server multiplies by elapsed ticks plus one.
        if (perTick * (ticks + 1) < FINISH_THRESHOLD) {
            return false;
        }
        final BlockPos finishing = target;
        final EnumFacing side = face;
        InventoryUtil.withSlot(slot, () -> PacketUtil.send(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, finishing, side)));
        target = null;
        return true;
    }

    public void abort() {
        if (target != null && Minecraft.getMinecraft().getNetHandler() != null) {
            PacketUtil.send(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, target, face));
        }
        target = null;
    }

    /** Vanilla's per-tick digging progress for a block with this item, including the air penalty. */
    public static float relativeHardness(EntityPlayerSP player, ItemStack stack, Block block) {
        float hardness = block.getBlockHardness(player.worldObj, BlockPos.ORIGIN);
        if (hardness < 0.0f) {
            return 0.0f;
        }
        if (hardness == 0.0f) {
            return 1.0f;
        }
        boolean harvestable = block.getMaterial().isToolNotRequired() || (stack != null && stack.canHarvestBlock(block));
        float speed = InventoryUtil.digSpeed(stack, block);
        if (player.isInsideOfMaterial(net.minecraft.block.material.Material.water)) {
            speed /= 5.0f;
        }
        if (!player.onGround) {
            speed /= 5.0f;
        }
        return speed / hardness / (harvestable ? 30.0f : 100.0f);
    }
}
