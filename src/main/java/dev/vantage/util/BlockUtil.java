package dev.vantage.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

/** Where a block can go, and what to click to put it there. */
public final class BlockUtil {

    /** A face to click and the point on it that places a block at the intended position. */
    public static final class Placement {
        public final BlockPos against;
        public final EnumFacing face;
        public final Vec3 hitVec;

        Placement(BlockPos against, EnumFacing face, Vec3 hitVec) {
            this.against = against;
            this.face = face;
            this.hitVec = hitVec;
        }

        /** The block the placement will create. */
        public BlockPos result() {
            return against.offset(face);
        }
    }

    /** Order to try neighbours in: below first, since that is what bridging almost always needs. */
    private static final EnumFacing[] SEARCH_ORDER = {
            EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST, EnumFacing.UP
    };

    private BlockUtil() {
    }

    public static Block blockAt(BlockPos pos) {
        return Minecraft.getMinecraft().theWorld.getBlockState(pos).getBlock();
    }

    /** Whether a block could be placed into this position: air, liquid or something replaceable. */
    public static boolean isReplaceable(BlockPos pos) {
        Block block = blockAt(pos);
        return block instanceof BlockAir || block instanceof BlockLiquid
                || block.isReplaceable(Minecraft.getMinecraft().theWorld, pos);
    }

    /**
     * Whether right-clicking this block places against it rather than using it. Chests, beds and
     * crafting tables open or do something instead.
     */
    public static boolean canPlaceAgainst(BlockPos pos) {
        Block block = blockAt(pos);
        return !isReplaceable(pos) && block.getMaterial().isSolid()
                && !(block instanceof BlockContainer) && !(block instanceof BlockWorkbench) && !(block instanceof BlockBed);
    }

    /** A way to put a block at {@code target} by clicking one of its neighbours, or null. */
    public static Placement placementFor(BlockPos target) {
        if (!isReplaceable(target)) {
            return null;
        }
        for (EnumFacing direction : SEARCH_ORDER) {
            BlockPos neighbour = target.offset(direction);
            if (canPlaceAgainst(neighbour)) {
                EnumFacing face = direction.getOpposite();
                return new Placement(neighbour, face, faceCentre(neighbour, face));
            }
        }
        return null;
    }

    /**
     * Like {@link #placementFor}, but if nothing touches the target directly, looks one step further
     * out and returns the placement that fills the gap nearest the target first.
     */
    public static Placement placementNear(BlockPos target) {
        Placement direct = placementFor(target);
        if (direct != null) {
            return direct;
        }
        for (EnumFacing direction : SEARCH_ORDER) {
            if (direction == EnumFacing.UP) {
                continue;
            }
            Placement bridging = placementFor(target.offset(direction));
            if (bridging != null) {
                return bridging;
            }
        }
        return null;
    }

    /** The centre of one face of a block, nudged inward so the ray lands on the block. */
    public static Vec3 faceCentre(BlockPos pos, EnumFacing face) {
        return new Vec3(
                pos.getX() + 0.5 + face.getFrontOffsetX() * 0.49,
                pos.getY() + 0.5 + face.getFrontOffsetY() * 0.49,
                pos.getZ() + 0.5 + face.getFrontOffsetZ() * 0.49);
    }

    /**
     * Places a block from a hotbar slot against a neighbour, holding the slot only for the moment
     * of placing.
     *
     * @return whether the game accepted the placement
     */
    public static boolean place(final Placement placement, final int slot, final boolean visibleSwing) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityPlayerSP player = mc.thePlayer;
        final boolean[] placed = {false};
        InventoryUtil.withSlot(slot, () -> {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (mc.playerController.onPlayerRightClick(player, mc.theWorld, stack,
                    placement.against, placement.face, placement.hitVec)) {
                placed[0] = true;
                if (visibleSwing) {
                    player.swingItem();
                } else {
                    PacketUtil.send(new C0APacketAnimation());
                }
            }
        });
        return placed[0];
    }

    /** Distance from the player's eyes to a point, for reach checks. */
    public static double eyeDistance(Vec3 point) {
        return Minecraft.getMinecraft().thePlayer.getPositionEyes(1.0f).distanceTo(point);
    }
}
