package dev.vantage.module.impl.analysis;

import dev.vantage.detect.AimAnalysis;
import dev.vantage.detect.ClickAnalysis;
import dev.vantage.detect.Flagged;
import dev.vantage.detect.MovementAnalysis;
import dev.vantage.detect.MovementSample;
import dev.vantage.detect.PlayerTrack;
import dev.vantage.detect.ReachAnalysis;
import dev.vantage.detect.ScaffoldAnalysis;
import dev.vantage.detect.VelocityAnalysis;
import dev.vantage.detect.ViolationAccumulator;
import dev.vantage.game.LobbyReader;
import dev.vantage.game.TeamColour;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.net.PacketDelayer;
import dev.vantage.net.PacketObserver;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Watches other players for the patterns automation leaves behind, and says so in chat.
 *
 * <p>What a client can see is narrower than what a server can, and only checks that survive that
 * gap are implemented. Rotations arrive quantised to a single byte per axis, about 1.4 degrees, so
 * the fine-grained mouse analysis a server-side anticheat runs is not possible here at all.
 * Backtrack is not detectable either — it is a property of the attacker's packet timing against the
 * server, which a third party never sees — so there is no check for it rather than a faked one.
 * (That this client now ships a Backtrack module of its own changes nothing here: watching someone
 * else use one is still not something a client can do.)
 *
 * <p>Every verdict is a heuristic with a confidence attached, not an accusation. Thresholds scale
 * with the watched player's latency, which is the largest single source of wrong answers.
 */
public class CheatDetectorModule extends Module {

    private static final int ANALYSIS_INTERVAL = 20;

    /** Ticks after a hit over which to measure how far knockback moved someone. */
    private static final int KNOCKBACK_WINDOW = 5;

    private static final double SNAP_DEGREES = 30.0;
    private static final double LOCK_DEGREES = 3.0;
    private static final double CLICK_SPREAD_MILLIS = 5.0;
    private static final double ALLOWED_REACH = 3.4;
    private static final double ALLOWED_SPEED = 0.45;
    private static final int ALLOWED_HOVER_TICKS = 20;
    private static final int ALLOWED_BACKWARDS_TICKS = 10;
    private static final double ALLOWED_JUMP = 2.0;
    private static final double EXPECTED_KNOCKBACK = 0.5;
    private static final double LEVEL_PITCH = 30.0;
    private static final double BEHIND_ANGLE = 60.0;

    /** A block further than this from a player was almost certainly not placed by them. */
    private static final double PLACEMENT_RANGE = 6.0;

    private final BooleanSetting combat = register(new BooleanSetting(
            "Combat", "Autoclicker, aim assist, reach and anti-knockback", true));
    private final BooleanSetting movement = register(new BooleanSetting(
            "Movement", "Speed, flight, jump height and backwards sprinting", true));
    private final BooleanSetting scaffold = register(new BooleanSetting(
            "Scaffold", "Automated bridging", true));
    private final NumberSetting sensitivity = register(new NumberSetting(
            "Sensitivity", "Higher catches more and is wrong more often", 1.0, 0.5, 1.5, 0.05, "x"));
    private final BooleanSetting chatNotifications = register(new BooleanSetting(
            "Chat Notifications", "Announce detections in chat", true));

    private final Map<Integer, PlayerTrack> tracks = new HashMap<Integer, PlayerTrack>();
    private final Map<String, ViolationAccumulator> suspicion = new HashMap<String, ViolationAccumulator>();
    private final Map<Integer, double[]> lastPosition = new HashMap<Integer, double[]>();
    private final Map<Integer, Integer> lastHurtTime = new HashMap<Integer, Integer>();
    private final Map<Integer, double[]> pendingKnockback = new HashMap<Integer, double[]>();

    private int tickCounter;
    private int selfLastHurtTime;

    public CheatDetectorModule() {
        super("Cheat Detector", Category.ANALYSIS, "Watches other players for signs of automation");
    }

    @Override
    protected void onEnable() {
        PacketObserver observer = PacketObserver.instance();
        observer.setSink((entityId, timestamp) -> trackFor(entityId).recordSwing(timestamp));
        observer.attachIfNeeded();
    }

    @Override
    protected void onDisable() {
        PacketObserver.instance().setSink(null);
        PacketObserver.instance().detach();
        clearState();
    }

    @Override
    public void onWorldChanged() {
        clearState();
    }

    private void clearState() {
        tracks.clear();
        suspicion.clear();
        lastPosition.clear();
        lastHurtTime.clear();
        pendingKnockback.clear();
        Flagged.clear();
        PacketObserver.instance().clear();
    }

    private PlayerTrack trackFor(int entityId) {
        synchronized (tracks) {
            PlayerTrack track = tracks.get(entityId);
            if (track == null) {
                track = new PlayerTrack();
                tracks.put(entityId, track);
            }
            return track;
        }
    }

    // -- sampling ---------------------------------------------------------------------------

    @Override
    public void onTick() {
        PacketObserver.instance().attachIfNeeded();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        long now = System.currentTimeMillis();

        List<EntityPlayer> others = otherPlayers(mc);
        for (EntityPlayer player : others) {
            sampleMovementAndRotation(mc, player, now);
            sampleKnockback(player);
        }
        if (scaffold.value()) {
            attributePlacements(others);
        } else {
            PacketObserver.instance().drainPlacements();
        }
        sampleReachOnSelf(mc, now, others);

        if (++tickCounter < ANALYSIS_INTERVAL) {
            return;
        }
        tickCounter = 0;
        runAnalyses(mc, others, now);
    }

    private List<EntityPlayer> otherPlayers(Minecraft mc) {
        List<EntityPlayer> result = new ArrayList<EntityPlayer>();
        for (Object entity : new ArrayList<Object>(mc.theWorld.playerEntities)) {
            if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                result.add((EntityPlayer) entity);
            }
        }
        return result;
    }

    private void sampleMovementAndRotation(Minecraft mc, EntityPlayer player, long now) {
        int id = player.getEntityId();
        double[] previous = lastPosition.get(id);
        lastPosition.put(id, new double[]{player.posX, player.posY, player.posZ});

        EntityPlayer target = nearestOtherPlayer(mc, player);
        double angleToTarget = target == null ? 180.0 : angleBetween(player, target);
        trackFor(id).recordRotation(player.rotationYaw, angleToTarget, now);

        if (previous == null) {
            return;
        }
        double dx = player.posX - previous[0];
        double dz = player.posZ - previous[2];
        double travelled = Math.sqrt(dx * dx + dz * dz);

        // A step this large is the server repositioning them, not the player moving. Marking it
        // lets every movement check discard the pair rather than reading it as impossible speed.
        //
        // Backtrack distorts movement the same way from this end: a player whose packets are being
        // held stalls and then catches up in one tick. Left unmarked, this client's own delaying
        // would read as that player flying or speeding, and the detector would flag whoever the
        // user is fighting. The same discard handles it, so no separate suppression is needed.
        boolean teleported = travelled > 8.0 || PacketDelayer.instance().isDistorting(id);

        Vec3 look = player.getLook(1.0f);
        double lookLength = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        double facingDot = 1.0;
        if (travelled > 1e-4 && lookLength > 1e-4) {
            facingDot = (look.xCoord * dx + look.zCoord * dz) / (lookLength * travelled);
        }

        trackFor(id).recordMovement(new MovementSample(player.posX, player.posY, player.posZ,
                player.onGround, player.isSprinting(), facingDot, teleported));
    }

    /** Measures how far a player travelled in the ticks after they were hit. */
    private void sampleKnockback(EntityPlayer player) {
        int id = player.getEntityId();
        Integer previousHurt = lastHurtTime.get(id);
        lastHurtTime.put(id, player.hurtTime);

        double[] pending = pendingKnockback.get(id);
        if (pending != null) {
            pending[0] -= 1.0;
            if (pending[0] <= 0.0) {
                double dx = player.posX - pending[1];
                double dz = player.posZ - pending[2];
                trackFor(id).recordHitDisplacement(Math.sqrt(dx * dx + dz * dz));
                pendingKnockback.remove(id);
            }
            return;
        }
        // hurtTime is set to its maximum the moment the hurt animation arrives, then counts down.
        if (previousHurt != null && player.hurtTime > previousHurt) {
            pendingKnockback.put(id, new double[]{KNOCKBACK_WINDOW, player.posX, player.posZ});
        }
    }

    /**
     * Attributes each newly placed block to whoever was near enough to have placed it, and records
     * where they were looking at the time.
     */
    private void attributePlacements(List<EntityPlayer> players) {
        for (PacketObserver.Placement placement : PacketObserver.instance().drainPlacements()) {
            BlockPos position = placement.position;
            EntityPlayer placer = null;
            double bestDistance = PLACEMENT_RANGE * PLACEMENT_RANGE;
            for (EntityPlayer player : players) {
                double distance = player.getDistanceSq(
                        position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    placer = player;
                }
            }
            if (placer == null) {
                continue;
            }
            trackFor(placer.getEntityId()).recordPlacement(
                    placer.rotationPitch, angleToBlock(placer, position));
        }
    }

    private void sampleReachOnSelf(Minecraft mc, long now, List<EntityPlayer> others) {
        int hurtTime = mc.thePlayer.hurtTime;
        boolean justHurt = hurtTime > selfLastHurtTime;
        selfLastHurtTime = hurtTime;
        if (!justHurt || !combat.value()) {
            return;
        }
        EntityPlayer attacker = mostRecentSwinger(others, now);
        if (attacker == null) {
            return;
        }
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double distance = ReachAnalysis.distanceToBox(
                attacker.posX, attacker.posY + attacker.getEyeHeight(), attacker.posZ,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        trackFor(attacker.getEntityId()).recordHitOnYou(distance);
    }

    private EntityPlayer mostRecentSwinger(List<EntityPlayer> players, long now) {
        EntityPlayer best = null;
        long bestTime = now - 300L;
        for (EntityPlayer candidate : players) {
            Long swing = PacketObserver.instance().getLastSwing(candidate.getEntityId());
            if (swing != null && swing > bestTime) {
                bestTime = swing;
                best = candidate;
            }
        }
        return best;
    }

    private EntityPlayer nearestOtherPlayer(Minecraft mc, EntityPlayer from) {
        EntityPlayer best = null;
        double bestDistance = 64.0;
        for (Object entity : mc.theWorld.playerEntities) {
            if (!(entity instanceof EntityPlayer) || entity == from) {
                continue;
            }
            EntityPlayer candidate = (EntityPlayer) entity;
            double distance = from.getDistanceSqToEntity(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static double angleBetween(EntityPlayer from, EntityPlayer to) {
        Vec3 look = from.getLook(1.0f).normalize();
        Vec3 toTarget = new Vec3(
                to.posX - from.posX,
                (to.posY + to.getEyeHeight()) - (from.posY + from.getEyeHeight()),
                to.posZ - from.posZ).normalize();
        return degreesBetween(look, toTarget);
    }

    private static double angleToBlock(EntityPlayer from, BlockPos block) {
        Vec3 look = from.getLook(1.0f).normalize();
        Vec3 toBlock = new Vec3(
                block.getX() + 0.5 - from.posX,
                block.getY() + 0.5 - (from.posY + from.getEyeHeight()),
                block.getZ() + 0.5 - from.posZ).normalize();
        return degreesBetween(look, toBlock);
    }

    private static double degreesBetween(Vec3 first, Vec3 second) {
        double dot = first.dotProduct(second);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    // -- analysis ---------------------------------------------------------------------------

    private void runAnalyses(Minecraft mc, List<EntityPlayer> players, long now) {
        Map<String, TeamColour> teams = LobbyReader.readTeamAssignments(LobbyReader.readPlayers());

        for (EntityPlayer player : players) {
            String name = player.getName();
            PlayerTrack track = trackFor(player.getEntityId());
            double slack = latencyAllowance(mc, name) / sensitivity.asDouble();

            if (combat.value()) {
                runCombatChecks(track, name, teams, now, slack);
            }
            if (movement.value()) {
                runMovementChecks(track, name, teams, now, slack);
            }
            if (scaffold.value()) {
                ScaffoldAnalysis.Result result = ScaffoldAnalysis.analyse(
                        track.getPlacementPitches(), track.getPlacementAngles(),
                        LEVEL_PITCH, BEHIND_ANGLE);
                if (result.isSuspicious()) {
                    report(name, teams, now, result.getConfidence() * 3.0, "Scaffold",
                            String.format(Locale.ROOT, "%.0f%% of %d placements",
                                    result.getAutomatedFraction() * 100.0, result.getPlacements()));
                }
            }
        }
        expireStaleFlags(now);
    }

    private void runCombatChecks(PlayerTrack track, String name, Map<String, TeamColour> teams,
                                 long now, double slack) {
        ClickAnalysis.Result clicks = ClickAnalysis.analyse(
                track.getSwingTimes(), CLICK_SPREAD_MILLIS / slack);
        if (clicks.isSuspicious()) {
            report(name, teams, now, clicks.getConfidence() * 3.0, "Autoclicker",
                    String.format(Locale.ROOT, "%.1f cps, %.1fms spread",
                            clicks.getClicksPerSecond(), clicks.getStandardDeviationMillis()));
        }

        AimAnalysis.Result aim = AimAnalysis.analyse(track.getYawDeltas(), track.getTargetAngles(),
                SNAP_DEGREES * slack, LOCK_DEGREES);
        if (aim.isSuspicious()) {
            report(name, teams, now, aim.getConfidence() * 3.0, "Aim assist",
                    aim.getSnaps() >= 3
                            ? aim.getSnaps() + " snaps onto target"
                            : String.format(Locale.ROOT, "%.0f%% locked on", aim.getLockedFraction() * 100.0));
        }

        ReachAnalysis.Result reach = ReachAnalysis.analyse(
                track.getHitsOnYou(), ALLOWED_REACH * slack);
        if (reach.isSuspicious()) {
            report(name, teams, now, reach.getConfidence() * 3.0, "Reach",
                    String.format(Locale.ROOT, "%.2fm median over %d hits",
                            reach.getMedian(), reach.getSamples()));
        }

        VelocityAnalysis.Result velocity = VelocityAnalysis.analyse(
                track.getHitDisplacements(), EXPECTED_KNOCKBACK / slack);
        if (velocity.isSuspicious()) {
            report(name, teams, now, velocity.getConfidence() * 2.5, "Anti-knockback",
                    String.format(Locale.ROOT, "%.2fm after %d hits",
                            velocity.getMedianDisplacement(), velocity.getHits()));
        }
    }

    private void runMovementChecks(PlayerTrack track, String name, Map<String, TeamColour> teams,
                                   long now, double slack) {
        List<MovementSample> samples = track.getMovementSamples();

        MovementAnalysis.Result speed = MovementAnalysis.speed(samples, ALLOWED_SPEED * slack);
        if (speed.isSuspicious()) {
            report(name, teams, now, speed.getConfidence() * 3.0, "Speed",
                    String.format(Locale.ROOT, "%.2f blocks/tick sustained", speed.getMeasured()));
        }

        MovementAnalysis.Result hover = MovementAnalysis.hover(
                samples, (int) Math.round(ALLOWED_HOVER_TICKS * slack));
        if (hover.isSuspicious()) {
            report(name, teams, now, hover.getConfidence() * 3.0, "Flight",
                    String.format(Locale.ROOT, "%.0f ticks airborne without falling", hover.getMeasured()));
        }

        MovementAnalysis.Result jump = MovementAnalysis.jumpHeight(samples, ALLOWED_JUMP * slack);
        if (jump.isSuspicious()) {
            report(name, teams, now, jump.getConfidence() * 2.0, "Jump height",
                    String.format(Locale.ROOT, "%.2f blocks", jump.getMeasured()));
        }

        MovementAnalysis.Result backwards = MovementAnalysis.omniSprint(
                samples, (int) Math.round(ALLOWED_BACKWARDS_TICKS * slack));
        if (backwards.isSuspicious()) {
            report(name, teams, now, backwards.getConfidence() * 3.0, "Backwards sprint",
                    String.format(Locale.ROOT, "%.0f ticks", backwards.getMeasured()));
        }
    }

    /**
     * Widens thresholds for a laggy player.
     *
     * <p>Latency distorts every one of these signals, and treating a laggy honest player the same
     * as a local one is the most common way a detector like this ends up wrong.
     */
    private double latencyAllowance(Minecraft mc, String name) {
        int ping = pingOf(mc, name);
        if (ping <= 100) {
            return 1.0;
        }
        return 1.0 + Math.min(0.5, (ping - 100) / 800.0);
    }

    private int pingOf(Minecraft mc, String name) {
        if (mc.getNetHandler() == null) {
            return 0;
        }
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            if (info.getGameProfile() != null && name.equals(info.getGameProfile().getName())) {
                return info.getResponseTime();
            }
        }
        return 0;
    }

    private void report(String name, Map<String, TeamColour> teams, long now,
                        double weight, String check, String detail) {
        ViolationAccumulator accumulator = suspicion.get(name);
        if (accumulator == null) {
            // Half a minute half-life: a pattern has to persist to build up.
            accumulator = new ViolationAccumulator(30_000.0, 6.0);
            suspicion.put(name, accumulator);
        }
        accumulator.add(weight, now);
        if (!accumulator.shouldNotify(now)) {
            return;
        }

        TeamColour team = teams.get(name);
        Flagged.set(name, check);

        if (!chatNotifications.value()) {
            return;
        }
        StringBuilder message = new StringBuilder("§8[§bVantage§8] §f");
        if (team != null && team != TeamColour.UNKNOWN) {
            message.append('§').append(team.getColourCode()).append(team.getDisplayName()).append(" §7- §f");
        }
        message.append(name).append(" §7may be using §c").append(check)
                .append(" §8(").append(detail).append(')');
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(message.toString()));
    }

    private void expireStaleFlags(long now) {
        Iterator<Map.Entry<String, ViolationAccumulator>> iterator = suspicion.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ViolationAccumulator> entry = iterator.next();
            if (!entry.getValue().isOverThreshold(now)) {
                Flagged.unset(entry.getKey());
                if (entry.getValue().get(now) <= 0.0) {
                    iterator.remove();
                }
            }
        }
    }
}
