package dev.vantage.module.impl.bedwars;

import dev.vantage.Vantage;
import dev.vantage.game.BedTracker;
import dev.vantage.game.TeamColour;
import dev.vantage.game.TeamResolver;
import dev.vantage.gui.Icons;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.module.Category;
import dev.vantage.notify.Notifications;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Watches your bed's defence and tells you the moment it is being dug.
 *
 * <p>The blocks around your bed are remembered as they stand. When one disappears you are told
 * how many layers from the bed it was, and who was standing next to it; blocks your team adds
 * later are taken into the defence. The panel is a top-down map of how many layers cover each
 * column, with anything breached in red.
 */
public class BedGuardModule extends HudModule {

    private static final int RADIUS = 3;
    private static final int HEIGHT = 3;
    private static final long BATCH_MILLIS = 1500L;
    private static final long NEARBY_COOLDOWN_MILLIS = 5000L;

    private final NumberSetting alarmRadius = register(new NumberSetting(
            "Alarm Radius", "Warn when an enemy comes this close to your bed", 8.0, 3.0, 20.0, 1.0, "m"));
    private final BooleanSetting breakAlerts = register(new BooleanSetting(
            "Break Alerts", "Notify when a defence block is broken", true));
    private final BooleanSetting nearbyAlerts = register(new BooleanSetting(
            "Nearby Alerts", "Notify when an enemy is at your bed", true));

    private BlockPos head;
    private BlockPos foot;
    private final Map<BlockPos, Block> defence = new HashMap<BlockPos, Block>();
    private final Map<Long, Integer> originalColumns = new HashMap<Long, Integer>();
    private final Map<String, Long> lastNearbyAlert = new HashMap<String, Long>();
    private final List<String> pendingBreaks = new ArrayList<String>();
    private long firstPendingAt;
    private int nearbyEnemies;
    private int ticks;

    public BedGuardModule() {
        super("Bed Guard", Category.BEDWARS, "Alerts you the moment your bed defence is being broken");
    }

    @Override
    public void onWorldChanged() {
        head = null;
        foot = null;
        defence.clear();
        originalColumns.clear();
        lastNearbyAlert.clear();
        pendingBreaks.clear();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        BedTracker.Bed bed = BedTracker.get().getOwnBed();
        if (bed == null) {
            nearbyEnemies = 0;
            return;
        }
        if (head == null || !head.equals(bed.getHead())) {
            head = bed.getHead();
            foot = bed.getFoot();
            snapshot(mc.theWorld);
        }
        ticks++;
        if (ticks % 4 == 0) {
            checkBreaks(mc);
        }
        if (ticks % 20 == 0) {
            absorbNewBlocks(mc.theWorld);
            checkNearby(mc, bed);
        }
        flushBreaks();
    }

    private int minX() {
        return Math.min(head.getX(), foot.getX()) - RADIUS;
    }

    private int maxX() {
        return Math.max(head.getX(), foot.getX()) + RADIUS;
    }

    private int minZ() {
        return Math.min(head.getZ(), foot.getZ()) - RADIUS;
    }

    private int maxZ() {
        return Math.max(head.getZ(), foot.getZ()) + RADIUS;
    }

    private boolean isDefence(Block block, BlockPos pos) {
        return block != Blocks.air && block != Blocks.bed && !block.getMaterial().isLiquid()
                && !block.getMaterial().isReplaceable() && !pos.equals(head) && !pos.equals(foot);
    }

    private void snapshot(World world) {
        defence.clear();
        originalColumns.clear();
        absorbNewBlocks(world);
        for (BlockPos pos : defence.keySet()) {
            long column = columnKey(pos.getX(), pos.getZ());
            Integer count = originalColumns.get(column);
            originalColumns.put(column, count == null ? 1 : count + 1);
        }
    }

    private void absorbNewBlocks(World world) {
        for (int x = minX(); x <= maxX(); x++) {
            for (int y = head.getY(); y <= head.getY() + HEIGHT; y++) {
                for (int z = minZ(); z <= maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();
                    if (isDefence(block, pos) && !defence.containsKey(pos)) {
                        defence.put(pos, block);
                        long column = columnKey(x, z);
                        Integer count = originalColumns.get(column);
                        if (count == null || count < columnCount(x, z)) {
                            originalColumns.put(column, columnCount(x, z));
                        }
                    }
                }
            }
        }
    }

    private void checkBreaks(Minecraft mc) {
        Iterator<Map.Entry<BlockPos, Block>> iterator = defence.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Block> entry = iterator.next();
            BlockPos pos = entry.getKey();
            if (!mc.theWorld.isBlockLoaded(pos) || isDefence(mc.theWorld.getBlockState(pos).getBlock(), pos)) {
                continue;
            }
            iterator.remove();
            if (!breakAlerts.value()) {
                continue;
            }
            EntityPlayer breaker = nearestEnemy(mc, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6.0);
            if (breaker == null) {
                // Nobody hostile nearby: a teammate rearranging, or a block that simply changed.
                continue;
            }
            String line = (onTop(pos) ? "Top, layer " : "Layer ") + layer(pos) + "  •  " + breaker.getName();
            if (pendingBreaks.isEmpty()) {
                firstPendingAt = System.currentTimeMillis();
            }
            if (!pendingBreaks.contains(line)) {
                pendingBreaks.add(line);
            }
        }
    }

    /** Several blocks dug in quick succession arrive as one alert rather than a burst of them. */
    private void flushBreaks() {
        if (pendingBreaks.isEmpty() || System.currentTimeMillis() - firstPendingAt < BATCH_MILLIS / 3) {
            return;
        }
        String message = pendingBreaks.size() == 1 ? pendingBreaks.get(0)
                : pendingBreaks.get(pendingBreaks.size() - 1) + "  (+" + (pendingBreaks.size() - 1) + " more)";
        Notifications.post("Bed defence broken", message, Notifications.Kind.DANGER, 4000L);
        pendingBreaks.clear();
    }

    private void checkNearby(Minecraft mc, BedTracker.Bed bed) {
        nearbyEnemies = 0;
        long now = System.currentTimeMillis();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!isEnemy(mc, player) || player.getDistance(bed.centreX(), bed.y(), bed.centreZ()) > alarmRadius.asDouble()) {
                continue;
            }
            nearbyEnemies++;
            Long last = lastNearbyAlert.get(player.getName());
            if (nearbyAlerts.value() && (last == null || now - last > NEARBY_COOLDOWN_MILLIS)) {
                lastNearbyAlert.put(player.getName(), now);
                Notifications.post("Enemy at your bed", player.getName() + " is "
                        + Math.round(player.getDistance(bed.centreX(), bed.y(), bed.centreZ())) + "m from it",
                        Notifications.Kind.WARNING, 3000L);
            }
        }
    }

    private static boolean isEnemy(Minecraft mc, EntityPlayer player) {
        return player != mc.thePlayer && !player.isDead && !player.isSpectator() && !TeamResolver.isTeammate(player)
                && !Vantage.instance().friends().isFriend(player.getName());
    }

    private static EntityPlayer nearestEnemy(Minecraft mc, double x, double y, double z, double radius) {
        EntityPlayer best = null;
        double bestDistance = radius * radius;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (isEnemy(mc, player) && player.getDistanceSq(x, y, z) < bestDistance) {
                best = player;
                bestDistance = player.getDistanceSq(x, y, z);
            }
        }
        return best;
    }

    /** How many blocks out from the bed a position is: 1 is touching it. */
    private int layer(BlockPos pos) {
        int dx = Math.max(0, Math.max(Math.min(head.getX(), foot.getX()) - pos.getX(), pos.getX() - Math.max(head.getX(), foot.getX())));
        int dz = Math.max(0, Math.max(Math.min(head.getZ(), foot.getZ()) - pos.getZ(), pos.getZ() - Math.max(head.getZ(), foot.getZ())));
        int dy = Math.max(0, pos.getY() - head.getY());
        return Math.max(1, Math.max(dy, Math.max(dx, dz)));
    }

    /** Whether a position sits over the bed rather than out to one side of it. */
    private boolean onTop(BlockPos pos) {
        double centreX = (head.getX() + foot.getX()) / 2.0;
        double centreZ = (head.getZ() + foot.getZ()) / 2.0;
        return pos.getY() > head.getY() && Math.abs(pos.getX() - centreX) <= 1.0
                && Math.abs(pos.getZ() - centreZ) <= 1.0;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private int columnCount(int x, int z) {
        int count = 0;
        for (Map.Entry<BlockPos, Block> entry : defence.entrySet()) {
            if (entry.getKey().getX() == x && entry.getKey().getZ() == z) {
                count++;
            }
        }
        return count;
    }

    /** The thinnest side's layer count, the number that decides how long the bed lasts. */
    private int weakestSide() {
        if (head == null) {
            return 0;
        }
        int weakest = Integer.MAX_VALUE;
        int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] direction : directions) {
            // Count outward from whichever half of the bed faces this way.
            int headReach = head.getX() * direction[0] + head.getZ() * direction[1];
            int footReach = foot.getX() * direction[0] + foot.getZ() * direction[1];
            BlockPos start = headReach >= footReach ? head : foot;
            int layers = 0;
            for (int step = 1; step <= RADIUS; step++) {
                if (defence.containsKey(start.add(direction[0] * step, 0, direction[1] * step))) {
                    layers++;
                }
            }
            weakest = Math.min(weakest, layers);
        }
        int top = 0;
        for (int step = 1; step <= HEIGHT; step++) {
            if (defence.containsKey(head.up(step))) {
                top++;
            }
        }
        return Math.min(weakest, top);
    }

    // -- panel ------------------------------------------------------------------------------

    private static final float CELL = 6.0f;
    private static final int GRID = RADIUS * 2 + 2;

    @Override
    public float getContentWidth() {
        return Math.max(Fonts.SMALL_BOLD.getWidth("Bed Guard") + 14.0f, GRID * CELL + 70.0f);
    }

    @Override
    public float getContentHeight() {
        return 12.0f + GRID * CELL;
    }

    @Override
    protected void renderContent() {
        boolean tracked = head != null && BedTracker.get().getOwnBed() != null;
        int headline = !tracked ? Theme.textMuted() : nearbyEnemies > 0 ? Theme.danger() : Theme.safe();
        Fonts.ICONS.drawString(String.valueOf(Icons.SHIELD), 0.0f, 0.5f, headline);
        Fonts.SMALL_BOLD.drawString("Bed Guard", 12.0f, 0.0f, Theme.text());
        if (!tracked) {
            Fonts.SMALL.drawString("No bed tracked", 0.0f, 13.0f, Theme.textMuted());
            return;
        }
        float originY = 12.0f;
        int startX = minX();
        int startZ = minZ();
        for (int gx = 0; gx < GRID && startX + gx <= maxX(); gx++) {
            for (int gz = 0; gz < GRID && startZ + gz <= maxZ(); gz++) {
                int x = startX + gx;
                int z = startZ + gz;
                float cellX = gx * CELL;
                float cellY = originY + gz * CELL;
                boolean isBed = (x == head.getX() && z == head.getZ()) || (x == foot.getX() && z == foot.getZ());
                int colour;
                if (isBed) {
                    TeamColour own = TeamResolver.ownTeam();
                    colour = own == TeamColour.UNKNOWN ? Theme.accent() : own.getArgb();
                } else {
                    int now = columnCount(x, z);
                    Integer before = originalColumns.get(columnKey(x, z));
                    if (before != null && before > 0 && now == 0) {
                        colour = Theme.danger();
                    } else if (now == 0) {
                        colour = RenderUtil.withAlpha(Theme.row(), 160);
                    } else {
                        colour = RenderUtil.blend(Theme.warning(), Theme.safe(), Math.min(1.0, now / 3.0));
                    }
                }
                RenderUtil.rect(cellX + 0.5f, cellY + 0.5f, CELL - 1.0f, CELL - 1.0f, colour);
            }
        }
        float textX = GRID * CELL + 6.0f;
        int weakest = weakestSide();
        Fonts.SMALL.drawString(weakest + (weakest == 1 ? " layer" : " layers"), textX, originY + 2.0f, Theme.text());
        Fonts.TINY.drawString("on the weakest side", textX, originY + 12.0f, Theme.textMuted());
        Fonts.SMALL.drawString(nearbyEnemies == 0 ? "Nobody near" : nearbyEnemies + " enemy near",
                textX, originY + 24.0f, nearbyEnemies == 0 ? Theme.textMuted() : Theme.danger());
    }
}
