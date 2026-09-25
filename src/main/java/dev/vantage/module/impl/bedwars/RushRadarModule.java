package dev.vantage.module.impl.bedwars;

import dev.vantage.Vantage;
import dev.vantage.bedwars.BridgeTracker;
import dev.vantage.event.PacketEvent;
import dev.vantage.event.Render3DEvent;
import dev.vantage.game.BedTracker;
import dev.vantage.game.TeamColour;
import dev.vantage.game.TeamResolver;
import dev.vantage.gui.Icons;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.Render3D;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.module.Category;
import dev.vantage.notify.Notifications;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Warns you when someone is bridging toward your bed, and when they will get there.
 *
 * <p>Every solid block that appears is credited to the enemy who could have just placed it: one
 * standing over it or right beside it, since a bridge is built from its own end under the builder's
 * feet. {@link BridgeTracker} then decides which of those runs are rushes, and only a rush within
 * the warn distance of your island is shown. The panel lists them soonest first, and the bridge
 * head is marked in the world.
 */
public class RushRadarModule extends HudModule {

    /** How far a block can be from the feet of whoever placed it, sideways and up or down. */
    private static final double CREDIT_REACH = 4.5;
    private static final double CREDIT_BELOW = 3.0;
    private static final double CREDIT_ABOVE = 1.0;
    private static final long ALERT_COOLDOWN_MILLIS = 6000L;

    private final NumberSetting islandRadius = register(new NumberSetting(
            "Island Radius", "How far your island reaches from your bed", 12.0, 6.0, 30.0, 1.0, "m"));
    private final NumberSetting warnDistance = register(new NumberSetting(
            "Warn Distance", "How close to your island a bridge has to get before you are warned",
            25.0, 5.0, 80.0, 1.0, "m"));
    private final BooleanSetting alerts = register(new BooleanSetting(
            "Alerts", "Pop a notification when a rush starts", true));
    private final BooleanSetting marker = register(new BooleanSetting(
            "Marker", "Mark the bridge head in the world", true));

    private final BridgeTracker tracker = new BridgeTracker();
    private final Queue<long[]> placements = new ConcurrentLinkedQueue<long[]>();
    private final Map<String, Long> lastAlert = new HashMap<String, Long>();
    private List<BridgeTracker.Rush> rushes = Collections.emptyList();

    public RushRadarModule() {
        super("Rush Radar", Category.BEDWARS, "Warns you when someone is bridging to your bed");
        on(PacketEvent.Receive.class, this::onPacket);
        on(Render3DEvent.class, event -> drawMarkers());
    }

    @Override
    public void onWorldChanged() {
        tracker.clear();
        placements.clear();
        lastAlert.clear();
        rushes = Collections.emptyList();
    }

    /** Network thread: only queue what appeared, the attribution needs the world. */
    private void onPacket(PacketEvent.Receive event) {
        long now = System.currentTimeMillis();
        if (event.getPacket() instanceof S23PacketBlockChange) {
            S23PacketBlockChange packet = (S23PacketBlockChange) event.getPacket();
            if (isBridgeBlock(packet.getBlockState())) {
                BlockPos pos = packet.getBlockPosition();
                placements.add(new long[]{pos.getX(), pos.getY(), pos.getZ(), now});
            }
        } else if (event.getPacket() instanceof S22PacketMultiBlockChange) {
            for (S22PacketMultiBlockChange.BlockUpdateData update
                    : ((S22PacketMultiBlockChange) event.getPacket()).getChangedBlocks()) {
                if (isBridgeBlock(update.getBlockState())) {
                    BlockPos pos = update.getPos();
                    placements.add(new long[]{pos.getX(), pos.getY(), pos.getZ(), now});
                }
            }
        }
    }

    /**
     * Whether a block could be part of a bridge. Leaves out air, and the non-solid and half-height
     * changes a server sends all the time - doors, torches, crops, liquids - which say nothing about
     * where anybody is going.
     */
    private static boolean isBridgeBlock(IBlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block != Blocks.air && block.isFullCube() && block.getMaterial().isSolid()
                && !(block instanceof BlockBed);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        long[] placement;
        while ((placement = placements.poll()) != null) {
            EntityPlayer placer = placerOf(mc, placement[0] + 0.5, placement[1] + 0.5, placement[2] + 0.5);
            if (placer != null) {
                tracker.record(placer.getName(), placement[0] + 0.5, placement[1], placement[2] + 0.5, placement[3]);
            }
        }
        BedTracker.Bed own = BedTracker.get().getOwnBed();
        if (own == null) {
            rushes = Collections.emptyList();
            return;
        }
        long now = System.currentTimeMillis();
        rushes = tracker.rushes(own.centreX(), own.centreZ(), islandRadius.asDouble(), warnDistance.asDouble(), now);
        if (!alerts.value()) {
            return;
        }
        for (BridgeTracker.Rush rush : rushes) {
            Long last = lastAlert.get(rush.player);
            if (last == null || now - last > ALERT_COOLDOWN_MILLIS) {
                lastAlert.put(rush.player, now);
                Notifications.post("Rush from the " + rush.direction(),
                        String.format(Locale.ROOT, "%s  •  %.0fm  •  %s", rush.player, rush.distance, eta(rush)),
                        Notifications.Kind.DANGER, 4000L);
            }
        }
    }

    /**
     * The enemy who placed a new block, or null if nobody plausible was there. A bridge block goes
     * below the builder's feet, or level with them for a moment while jumping, and within reach
     * sideways; anybody who does not fit that did not place it.
     */
    private static EntityPlayer placerOf(Minecraft mc, double x, double y, double z) {
        EntityPlayer best = null;
        double bestDistance = CREDIT_REACH * CREDIT_REACH;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isSpectator() || TeamResolver.isTeammate(player)
                    || Vantage.instance().friends().isFriend(player.getName())) {
                continue;
            }
            double below = player.posY - y;
            if (below > CREDIT_BELOW || below < -CREDIT_ABOVE) {
                continue;
            }
            double dx = player.posX - x;
            double dz = player.posZ - z;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static String eta(BridgeTracker.Rush rush) {
        return rush.etaSeconds < 1.0 ? "arriving" : String.format(Locale.ROOT, "%.0fs", rush.etaSeconds);
    }

    private void drawMarkers() {
        if (!marker.value() || rushes.isEmpty()) {
            return;
        }
        BedTracker.Bed own = BedTracker.get().getOwnBed();
        Render3D.begin();
        for (BridgeTracker.Rush rush : rushes) {
            Render3D.box(new net.minecraft.util.AxisAlignedBB(rush.headX - 0.5, rush.headY, rush.headZ - 0.5,
                    rush.headX + 0.5, rush.headY + 1.0, rush.headZ + 0.5), Theme.danger(), 0.25f);
            if (own != null) {
                Render3D.line(rush.headX, rush.headY + 0.5, rush.headZ, own.centreX(), own.y() + 0.5, own.centreZ(),
                        RenderUtil.withAlpha(Theme.danger(), 0.6f));
            }
        }
        Render3D.end();
    }

    // -- panel ------------------------------------------------------------------------------

    private List<String> lines() {
        List<String> lines = new ArrayList<String>();
        if (BedTracker.get().getOwnBed() == null) {
            lines.add("No bed tracked");
            return lines;
        }
        if (rushes.isEmpty()) {
            lines.add("All clear");
            return lines;
        }
        for (BridgeTracker.Rush rush : rushes) {
            lines.add(String.format(Locale.ROOT, "%s  %s  %.0fm  %s", rush.player, rush.direction(), rush.distance, eta(rush)));
        }
        return lines;
    }

    @Override
    public float getContentWidth() {
        float widest = Fonts.SMALL_BOLD.getWidth("Rush Radar") + 14.0f;
        for (String line : lines()) {
            widest = Math.max(widest, Fonts.SMALL.getWidth(line) + 6.0f);
        }
        return widest;
    }

    @Override
    public float getContentHeight() {
        return 12.0f + lines().size() * 10.0f;
    }

    @Override
    protected void renderContent() {
        int headline = rushes.isEmpty() ? Theme.textMuted() : Theme.danger();
        Fonts.ICONS.drawString(String.valueOf(Icons.RADAR), 0.0f, 0.5f, headline);
        Fonts.SMALL_BOLD.drawString("Rush Radar", 12.0f, 0.0f, Theme.text());
        float y = 12.0f;
        for (int i = 0; i < rushes.size(); i++) {
            BridgeTracker.Rush rush = rushes.get(i);
            EntityPlayer player = Minecraft.getMinecraft().theWorld.getPlayerEntityByName(rush.player);
            TeamColour team = player == null ? TeamColour.UNKNOWN : TeamResolver.teamOf(player);
            RenderUtil.circle(2.0f, y + 4.0f, 2.0f, team == TeamColour.UNKNOWN ? Theme.danger() : team.getArgb());
            Fonts.SMALL.drawString(lines().get(i), 6.0f, y, Theme.text());
            y += 10.0f;
        }
        if (rushes.isEmpty()) {
            Fonts.SMALL.drawString(lines().get(0), 0.0f, y, Theme.textMuted());
        }
    }
}
