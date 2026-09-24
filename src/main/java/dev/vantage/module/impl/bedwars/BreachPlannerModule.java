package dev.vantage.module.impl.bedwars;

import dev.vantage.bedwars.BreachPath;
import dev.vantage.combat.RotationManager;
import dev.vantage.event.MotionEvent;
import dev.vantage.event.Render2DEvent;
import dev.vantage.event.Render3DEvent;
import dev.vantage.game.BedTracker;
import dev.vantage.game.TeamColour;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.Render3D;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.InventoryUtil;
import dev.vantage.util.PacketDigger;
import dev.vantage.util.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds the fastest way into an enemy bed with the tools you are carrying.
 *
 * <p>Every block in the defence is costed at the ticks your best hotbar tool takes to break it -
 * wool with shears, end stone with a pickaxe, wood with an axe - and the cheapest route from open
 * air to the bed is drawn in the world, block by block, with the time each one takes. Obsidian a
 * layer deep on one side and wool on another is exactly the call this makes for you.
 *
 * <p>With Auto on it digs the route itself when you are in reach, holding the right tool for each
 * block only at the moment it breaks.
 */
public class BreachPlannerModule extends Module {

    private static final int HORIZONTAL_MARGIN = 4;
    private static final int ABOVE = 4;

    private final NumberSetting range = register(new NumberSetting(
            "Range", "Plan for beds within this distance", 40.0, 8.0, 96.0, 4.0, "m"));
    private final BooleanSetting auto = register(new BooleanSetting(
            "Auto", "Dig the planned route when it is in reach", false));
    private final NumberSetting reach = register(new NumberSetting(
            "Reach", "How far away a block can be dug", 4.5, 3.0, 6.0, 0.25, "m"));

    private final PacketDigger digger = new PacketDigger();
    private BedTracker.Bed bed;
    private BreachPath.ArrayGrid grid;
    private BreachPath.Plan plan;
    private int originX;
    private int originY;
    private int originZ;
    private int ticks;

    public BreachPlannerModule() {
        super("Breach Planner", Category.BEDWARS, "Shows the fastest way through a bed's defence with your tools");
        reach.visibleWhen(auto::value);
        on(Render3DEvent.class, event -> drawRoute());
        on(Render2DEvent.class, event -> drawLabels());
        on(MotionEvent.class, this::onMotion);
    }

    @Override
    protected void onDisable() {
        digger.abort();
        plan = null;
    }

    @Override
    public void onWorldChanged() {
        digger.abort();
        plan = null;
        bed = null;
    }

    @Override
    public void onTick() {
        if (ticks++ % 10 != 0 && plan != null && bed != null) {
            return;
        }
        bed = chooseBed(Minecraft.getMinecraft().thePlayer);
        plan = bed == null ? null : buildPlan(bed);
    }

    /** The enemy bed nearest the crosshair, or failing that the nearest one. */
    private BedTracker.Bed chooseBed(EntityPlayerSP player) {
        BedTracker.Bed looked = null;
        float bestAngle = 25.0f;
        BedTracker.Bed nearest = null;
        double nearestDistance = range.asDouble();
        for (BedTracker.Bed candidate : BedTracker.get().getEnemyBeds()) {
            double distance = candidate.horizontalDistanceTo(player.posX, player.posZ);
            if (distance > range.asDouble()) {
                continue;
            }
            float[] facing = RotationUtil.rotationsFor(candidate.centreX() - player.posX,
                    candidate.y() - (player.posY + player.getEyeHeight()), candidate.centreZ() - player.posZ);
            float angle = RotationUtil.angleBetween(player.rotationYaw, player.rotationPitch, facing[0], facing[1]);
            if (angle < bestAngle) {
                bestAngle = angle;
                looked = candidate;
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return looked != null ? looked : nearest;
    }

    private BreachPath.Plan buildPlan(BedTracker.Bed target) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        BlockPos head = target.getHead();
        BlockPos foot = target.getFoot();
        originX = Math.min(head.getX(), foot.getX()) - HORIZONTAL_MARGIN;
        originY = head.getY() - 1;
        originZ = Math.min(head.getZ(), foot.getZ()) - HORIZONTAL_MARGIN;
        int sx = Math.abs(head.getX() - foot.getX()) + 1 + HORIZONTAL_MARGIN * 2;
        int sy = ABOVE + 2;
        int sz = Math.abs(head.getZ() - foot.getZ()) + 1 + HORIZONTAL_MARGIN * 2;
        grid = new BreachPath.ArrayGrid(sx, sy, sz);
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockPos pos = new BlockPos(originX + x, originY + y, originZ + z);
                    if (pos.equals(head) || pos.equals(foot)) {
                        grid.setBed(x, y, z);
                        continue;
                    }
                    Block block = world.getBlockState(pos).getBlock();
                    if (block.getMaterial() == Material.air || block.getMaterial().isReplaceable()
                            || block.getMaterial().isLiquid() || block == Blocks.bed) {
                        continue;
                    }
                    float hardness = block.getBlockHardness(world, pos);
                    int[] best = bestTicks(block, hardness);
                    grid.set(x, y, z, best[0]);
                    grid.tag(x, y, z, new int[]{best[1]});
                }
            }
        }
        return BreachPath.plan(grid);
    }

    /** @return {ticks, hotbar slot} for the fastest way to break this block */
    private static int[] bestTicks(Block block, float hardness) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        int bestTicks = BreachPath.breakTicks(hardness, InventoryUtil.digSpeed(null, block),
                block.getMaterial().isToolNotRequired());
        int bestSlot = player.inventory.currentItem;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack == null) {
                continue;
            }
            boolean harvestable = block.getMaterial().isToolNotRequired() || stack.canHarvestBlock(block);
            int ticks = BreachPath.breakTicks(hardness, InventoryUtil.digSpeed(stack, block), harvestable);
            if (ticks < bestTicks) {
                bestTicks = ticks;
                bestSlot = slot;
            }
        }
        return new int[]{bestTicks, bestSlot};
    }

    private List<BlockPos> remainingBlocks() {
        List<BlockPos> blocks = new ArrayList<BlockPos>();
        if (plan == null) {
            return blocks;
        }
        World world = Minecraft.getMinecraft().theWorld;
        for (BreachPath.Step step : plan.blocksToBreak()) {
            BlockPos pos = new BlockPos(originX + step.x, originY + step.y, originZ + step.z);
            if (!world.getBlockState(pos).getBlock().getMaterial().isReplaceable()) {
                blocks.add(pos);
            }
        }
        return blocks;
    }

    private void drawRoute() {
        List<BlockPos> blocks = remainingBlocks();
        if (bed == null || plan == null) {
            return;
        }
        Render3D.begin();
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i);
            double progress = blocks.size() == 1 ? 0.0 : i / (double) (blocks.size() - 1);
            int colour = RenderUtil.blend(Theme.accent(), Theme.danger(), progress);
            Render3D.box(new AxisAlignedBB(pos, pos.add(1, 1, 1)).expand(0.002, 0.002, 0.002), colour, 0.22f);
        }
        Render3D.end();
    }

    private void drawLabels() {
        if (bed == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int scale = resolution.getScaleFactor();
        List<BlockPos> blocks = remainingBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i);
            float[] screen = Render3D.project(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, scale);
            if (screen == null) {
                continue;
            }
            int ticks = grid.cost(pos.getX() - originX, pos.getY() - originY, pos.getZ() - originZ);
            String text = (i + 1) + "  " + String.format(Locale.ROOT, "%.1fs", ticks / 20.0);
            float width = Fonts.TINY.getWidth(text) + 6.0f;
            RenderUtil.roundedRect(screen[0] - width / 2.0f, screen[1] - 5.0f, width, 10.0f, 2.5,
                    RenderUtil.withAlpha(Theme.panel(), 210));
            Fonts.TINY.drawCentred(text, screen[0], screen[1] - 3.5f, Theme.text());
        }

        String owner = bed.getOwner() == TeamColour.UNKNOWN ? "Enemy" : bed.getOwner().getDisplayName();
        String summary;
        if (plan == null) {
            summary = owner + " bed is sealed in something unbreakable";
        } else if (blocks.isEmpty()) {
            summary = owner + " bed is open";
        } else {
            int total = 0;
            for (BlockPos pos : blocks) {
                total += grid.cost(pos.getX() - originX, pos.getY() - originY, pos.getZ() - originZ);
            }
            summary = String.format(Locale.ROOT, "%s bed  •  %d block%s  •  %.1fs  •  from the %s",
                    owner, blocks.size(), blocks.size() == 1 ? "" : "s", total / 20.0, entrySide(blocks.get(0)));
        }
        float width = Fonts.SMALL.getWidth(summary) + 12.0f;
        float x = resolution.getScaledWidth() / 2.0f - width / 2.0f;
        float y = resolution.getScaledHeight() / 2.0f + 18.0f;
        RenderUtil.roundedRect(x, y, width, 14.0f, 4.0, RenderUtil.withAlpha(Theme.panel(), 215));
        RenderUtil.roundedRect(x + 3.0f, y + 3.0f, 2.0f, 8.0f, 1.0, Theme.accent());
        Fonts.SMALL.drawString(summary, x + 8.0f, y + 2.5f, Theme.text());
    }

    /** Which side of the bed the route starts from. */
    private String entrySide(BlockPos first) {
        double dx = first.getX() + 0.5 - bed.centreX();
        double dz = first.getZ() + 0.5 - bed.centreZ();
        if (first.getY() > bed.y() + 1 && Math.abs(dx) < 2.0 && Math.abs(dz) < 2.0) {
            return "top";
        }
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        }
        return dz > 0 ? "south" : "north";
    }

    private void onMotion(MotionEvent event) {
        if (!auto.value() || plan == null || bed == null) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        List<BlockPos> blocks = remainingBlocks();
        BlockPos next = blocks.isEmpty() ? nearestBedHalf(player) : blocks.get(0);
        Vec3 centre = new Vec3(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);
        if (player.getPositionEyes(1.0f).distanceTo(centre) > reach.asDouble()) {
            digger.abort();
            return;
        }
        if (event.isPre()) {
            Vec3 eyes = player.getPositionEyes(1.0f);
            float[] facing = RotationUtil.rotationsFor(centre.xCoord - eyes.xCoord,
                    centre.yCoord - eyes.yCoord, centre.zCoord - eyes.zCoord);
            RotationManager.get().request(facing[0], facing[1], 25, 60.0f);
            return;
        }
        digger.start(next, EnumFacing.UP);
        digger.tick();
    }

    private BlockPos nearestBedHalf(EntityPlayerSP player) {
        BlockPos head = bed.getHead();
        BlockPos foot = bed.getFoot();
        return player.getDistanceSq(head) <= player.getDistanceSq(foot) ? head : foot;
    }
}
