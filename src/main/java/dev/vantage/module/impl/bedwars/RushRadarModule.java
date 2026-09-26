package dev.vantage.module.impl.bedwars;

import dev.vantage.Vantage;
import dev.vantage.bedwars.BridgeTracker;
import dev.vantage.bedwars.IslandShape;
import dev.vantage.event.PacketEvent;
import dev.vantage.event.Render2DEvent;
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
import dev.vantage.util.RotationUtil;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

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
 * <p>Every block that appears is credited to the enemy standing next to it, since a bridge is
 * built from its own end. {@link BridgeTracker} then decides which of those runs are rushes, and
 * times each one to the edge of your island, which {@link IslandShape} finds by walking out from
 * your bed.
 *
 * <p>Each rush is announced at most twice: when it starts, and when it is a few seconds out. In
 * between, an arrow beside the crosshair points at the bridge with the seconds left, the panel
 * lists every rush soonest first, and the bridge head is marked in the world.
 */
public class RushRadarModule extends HudModule {

    private static final double CREDIT_RADIUS = 5.5;
    /** Seconds out at which a rush counts as about to arrive. */
    private static final double CLOSE_SECONDS = 5.0;
    /** Seconds out at which the arrow starts turning from amber to red. */
    private static final double FAR_SECONDS = 15.0;
    /** A rush gone this long is announced again if it comes back, rather than carrying on. */
    private static final long FORGET_MILLIS = 10000L;
    private static final long FADE_IN_MILLIS = 300L;
    /** Used until the island has been found, and on maps where it cannot be. */
    private static final double FALLBACK_RADIUS = 12.0;
    /** Anything smaller was not the island: the bed on a platform, or the scan starting badly. */
    private static final int MIN_ISLAND_COLUMNS = 30;
    private static final int ISLAND_SEARCH = 40;
    private static final int ISLAND_ABOVE = 2;
    private static final int ISLAND_BELOW = 12;
    private static final int ISLAND_RETRY_TICKS = 40;
    private static final int MAX_ARROWS = 3;
    private static final float ARROW_RADIUS = 42.0f;

    private final BooleanSetting alerts = register(new BooleanSetting(
            "Alerts", "Pop a notification when a rush starts and when it is about to arrive", true));
    private final BooleanSetting sound = register(new BooleanSetting(
            "Sound", "Ping when a rush starts, and higher when it is about to arrive", true));
    private final BooleanSetting arrows = register(new BooleanSetting(
            "Arrow", "Point at each rush from beside the crosshair, with the seconds left", true));
    private final BooleanSetting marker = register(new BooleanSetting(
            "Marker", "Mark the bridge head in the world", true));
    private final BooleanSetting showIsland = register(new BooleanSetting(
            "Show Island", "Outline the island it found, to check it", false));

    private final BridgeTracker tracker = new BridgeTracker();
    private final Queue<long[]> placements = new ConcurrentLinkedQueue<long[]>();
    private final Map<String, Warning> warnings = new HashMap<String, Warning>();
    private List<BridgeTracker.Rush> rushes = Collections.emptyList();
    private IslandShape island;
    private BlockPos islandBed;
    /** Whether {@link #island} came from the world rather than the stand-in circle. */
    private boolean islandFound;
    private boolean islandSettled;
    private int ticks;

    /** What has been said about one player's rush so far. */
    private static final class Warning {
        final long firstSeen;
        long lastSeen;
        boolean warnedClose;

        Warning(long now) {
            firstSeen = now;
            lastSeen = now;
        }
    }

    public RushRadarModule() {
        super("Rush Radar", Category.BEDWARS, "Warns you when someone is bridging to your bed");
        on(PacketEvent.Receive.class, this::onPacket);
        on(Render3DEvent.class, event -> drawWorld());
        on(Render2DEvent.class, this::drawArrows);
    }

    @Override
    public void onWorldChanged() {
        tracker.clear();
        placements.clear();
        warnings.clear();
        rushes = Collections.emptyList();
        island = null;
        islandBed = null;
        islandFound = false;
        islandSettled = false;
        ticks = 0;
    }

    /** Network thread: only queue what appeared, the attribution needs the world. */
    private void onPacket(PacketEvent.Receive event) {
        long now = System.currentTimeMillis();
        if (event.getPacket() instanceof S23PacketBlockChange) {
            S23PacketBlockChange packet = (S23PacketBlockChange) event.getPacket();
            if (packet.getBlockState() != null && packet.getBlockState().getBlock() != Blocks.air) {
                BlockPos pos = packet.getBlockPosition();
                placements.add(new long[]{pos.getX(), pos.getY(), pos.getZ(), now});
            }
        } else if (event.getPacket() instanceof S22PacketMultiBlockChange) {
            for (S22PacketMultiBlockChange.BlockUpdateData update
                    : ((S22PacketMultiBlockChange) event.getPacket()).getChangedBlocks()) {
                if (update.getBlockState() != null && update.getBlockState().getBlock() != Blocks.air) {
                    BlockPos pos = update.getPos();
                    placements.add(new long[]{pos.getX(), pos.getY(), pos.getZ(), now});
                }
            }
        }
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
        ticks++;
        refreshIsland(mc.theWorld, own);
        long now = System.currentTimeMillis();
        rushes = tracker.rushes(own.centreX(), own.centreZ(), island, now);
        warn(mc, now);
    }

    // -- island -----------------------------------------------------------------------------

    /**
     * Finds the island under your bed, retrying every couple of seconds while part of it is still
     * unloaded. Until then, and if it never turns up, a plain circle stands in for it.
     */
    private void refreshIsland(World world, BedTracker.Bed own) {
        boolean newBed = !own.getHead().equals(islandBed);
        if (newBed) {
            islandBed = own.getHead();
            island = IslandShape.circle(own.centreX(), own.centreZ(), FALLBACK_RADIUS);
            islandFound = false;
            islandSettled = false;
        }
        if (islandSettled || (!newBed && ticks % ISLAND_RETRY_TICKS != 0)) {
            return;
        }
        IslandShape found = IslandShape.detect(terrain(world, islandBed.getY()),
                islandBed.getX(), islandBed.getZ(), ISLAND_SEARCH);
        if (found == null || found.size() < MIN_ISLAND_COLUMNS) {
            return;
        }
        // A retry after you have walked off sees less of the island, not more, so a partial
        // result only replaces a bigger partial one once it is whole.
        if (found.isComplete() || !islandFound || found.size() >= island.size()) {
            island = found;
            islandFound = true;
            islandSettled = found.isComplete();
        }
    }

    /** A column is ground if anything solid stands in it from just above the bed to well below. */
    private static IslandShape.Terrain terrain(World world, int bedY) {
        return (x, z) -> {
            // The client hands back an empty chunk for one it has not been sent yet.
            if (world.getChunkFromChunkCoords(x >> 4, z >> 4).isEmpty()) {
                return IslandShape.Column.UNLOADED;
            }
            for (int y = bedY + ISLAND_ABOVE; y >= bedY - ISLAND_BELOW; y--) {
                Material material = world.getBlockState(new BlockPos(x, y, z)).getBlock().getMaterial();
                if (material != Material.air && !material.isReplaceable() && !material.isLiquid()) {
                    return IslandShape.Column.LAND;
                }
            }
            return IslandShape.Column.OPEN;
        };
    }

    // -- warnings ---------------------------------------------------------------------------

    /** Speaks up when a rush starts and when it gets close, and stays quiet in between. */
    private void warn(Minecraft mc, long now) {
        for (BridgeTracker.Rush rush : rushes) {
            boolean close = rush.etaSeconds <= CLOSE_SECONDS;
            Warning warning = warnings.get(rush.player);
            if (warning == null) {
                warning = new Warning(now);
                // One spotted already close gets the close warning alone, not both at once.
                warning.warnedClose = close;
                warnings.put(rush.player, warning);
                announce(mc, rush, close);
            } else if (close && !warning.warnedClose) {
                warning.warnedClose = true;
                announce(mc, rush, true);
            }
            warning.lastSeen = now;
        }
        warnings.values().removeIf(warning -> now - warning.lastSeen > FORGET_MILLIS);
    }

    private void announce(Minecraft mc, BridgeTracker.Rush rush, boolean close) {
        if (alerts.value()) {
            Notifications.post(close ? "Rush almost here" : "Rush incoming",
                    String.format(Locale.ROOT, "%s  •  %.0fm  •  %s", rush.player, rush.distance, eta(rush)),
                    close ? Notifications.Kind.DANGER : Notifications.Kind.WARNING, 4000L);
        }
        if (sound.value()) {
            // Higher for the second warning, so the two can be told apart without looking.
            mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation("note.pling"),
                    0.8f, close ? 2.0f : 1.2f, (float) mc.thePlayer.posX,
                    (float) (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()), (float) mc.thePlayer.posZ));
        }
    }

    /** The nearest enemy to a new block, or null if nobody plausible was there. */
    private static EntityPlayer placerOf(Minecraft mc, double x, double y, double z) {
        EntityPlayer best = null;
        double bestDistance = CREDIT_RADIUS * CREDIT_RADIUS;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || TeamResolver.isTeammate(player)
                    || Vantage.instance().friends().isFriend(player.getName())) {
                continue;
            }
            double distance = player.getDistanceSq(x, y, z);
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

    /** Amber while a rush is far off, turning red as it closes. */
    private static int urgency(BridgeTracker.Rush rush) {
        return RenderUtil.blend(Theme.warning(), Theme.danger(),
                (FAR_SECONDS - rush.etaSeconds) / (FAR_SECONDS - CLOSE_SECONDS));
    }

    // -- drawing ----------------------------------------------------------------------------

    private void drawWorld() {
        BedTracker.Bed own = BedTracker.get().getOwnBed();
        boolean outline = showIsland.value() && island != null && own != null;
        boolean markers = marker.value() && !rushes.isEmpty();
        if (!outline && !markers) {
            return;
        }
        Render3D.begin();
        if (outline) {
            double y = own.y() + 0.05;
            int colour = RenderUtil.withAlpha(Theme.accent(), 0.8f);
            for (int[] edge : island.outline()) {
                Render3D.line(edge[0], y, edge[1], edge[2], y, edge[3], colour);
            }
        }
        if (markers) {
            for (BridgeTracker.Rush rush : rushes) {
                Render3D.box(new AxisAlignedBB(rush.headX - 0.5, rush.headY, rush.headZ - 0.5,
                        rush.headX + 0.5, rush.headY + 1.0, rush.headZ + 0.5), Theme.danger(), 0.25f);
                if (own != null) {
                    Render3D.line(rush.headX, rush.headY + 0.5, rush.headZ, own.centreX(), own.y() + 0.5, own.centreZ(),
                            RenderUtil.withAlpha(Theme.danger(), 0.6f));
                }
            }
        }
        Render3D.end();
    }

    /**
     * An arrow on a ring around the crosshair for each rush, pointing the way you would turn to
     * face the bridge, with the seconds left beside it.
     */
    private void drawArrows(Render2DEvent event) {
        if (!arrows.value() || rushes.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : mc.thePlayer;
        float partial = event.getPartialTicks();
        float yaw = viewer.prevRotationYaw + (viewer.rotationYaw - viewer.prevRotationYaw) * partial;
        double viewX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partial;
        double viewZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partial;
        float centreX = event.getWidth() / 2.0f;
        float centreY = event.getHeight() / 2.0f;
        long now = System.currentTimeMillis();
        for (int i = 0; i < rushes.size() && i < MAX_ARROWS; i++) {
            BridgeTracker.Rush rush = rushes.get(i);
            Warning warning = warnings.get(rush.player);
            float alpha = warning == null ? 1.0f : Math.min(1.0f, (now - warning.firstSeen) / (float) FADE_IN_MILLIS);
            if (rush.etaSeconds <= CLOSE_SECONDS) {
                // A slow pulse in the last few seconds; steady before that.
                alpha *= 0.7f + 0.3f * (float) (0.5 + 0.5 * Math.sin(now / 120.0));
            }
            if (alpha < 0.05f) {
                continue;
            }
            float bearing = RotationUtil.rotationsFor(rush.headX - viewX, 0.0, rush.headZ - viewZ)[0];
            double turn = Math.toRadians(RotationUtil.yawDifference(yaw, bearing));
            // Straight ahead is up the screen, and a bridge off to your right points right.
            float dirX = (float) Math.sin(turn);
            float dirY = (float) -Math.cos(turn);
            int colour = urgency(rush);
            drawArrow(centreX, centreY, dirX, dirY, colour, alpha);

            String label = String.format(Locale.ROOT, "%.0fs", Math.max(0.0, rush.etaSeconds));
            float labelX = centreX + dirX * (ARROW_RADIUS + 17.0f);
            float labelY = centreY + dirY * (ARROW_RADIUS + 17.0f);
            float width = Fonts.TINY.getWidth(label) + 6.0f;
            RenderUtil.roundedRect(labelX - width / 2.0f, labelY - 5.0f, width, 10.0f, 2.5,
                    RenderUtil.withAlpha(Theme.panel(), (int) (210 * alpha)));
            Fonts.TINY.drawCentred(label, labelX, labelY - 3.5f, RenderUtil.withAlpha(colour, alpha));
        }
    }

    private static void drawArrow(float centreX, float centreY, float dirX, float dirY, int colour, float alpha) {
        float sideX = -dirY;
        float sideY = dirX;
        float baseX = centreX + dirX * ARROW_RADIUS;
        float baseY = centreY + dirY * ARROW_RADIUS;
        float tipX = centreX + dirX * (ARROW_RADIUS + 9.0f);
        float tipY = centreY + dirY * (ARROW_RADIUS + 9.0f);
        // A dark copy a little larger underneath keeps it readable against sky and snow alike.
        RenderUtil.triangle(tipX + dirX * 1.5f, tipY + dirY * 1.5f,
                baseX - dirX + sideX * 7.0f, baseY - dirY + sideY * 7.0f,
                baseX - dirX - sideX * 7.0f, baseY - dirY - sideY * 7.0f,
                RenderUtil.withAlpha(0xFF000000, 0.45f * alpha));
        RenderUtil.triangle(tipX, tipY,
                baseX + sideX * 5.5f, baseY + sideY * 5.5f,
                baseX - sideX * 5.5f, baseY - sideY * 5.5f,
                RenderUtil.withAlpha(colour, alpha));
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
            lines.add(String.format(Locale.ROOT, "%s  %.0fm  %s", rush.player, rush.distance, eta(rush)));
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
        List<String> lines = lines();
        float y = 12.0f;
        for (int i = 0; i < rushes.size() && i < lines.size(); i++) {
            BridgeTracker.Rush rush = rushes.get(i);
            EntityPlayer player = Minecraft.getMinecraft().theWorld.getPlayerEntityByName(rush.player);
            TeamColour team = player == null ? TeamColour.UNKNOWN : TeamResolver.teamOf(player);
            RenderUtil.circle(2.0f, y + 4.0f, 2.0f, team == TeamColour.UNKNOWN ? Theme.danger() : team.getArgb());
            Fonts.SMALL.drawString(lines.get(i), 6.0f, y,
                    rush.etaSeconds <= CLOSE_SECONDS ? Theme.danger() : Theme.text());
            y += 10.0f;
        }
        if (rushes.isEmpty()) {
            Fonts.SMALL.drawString(lines.get(0), 0.0f, y, Theme.textMuted());
        }
    }
}
