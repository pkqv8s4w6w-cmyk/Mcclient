package dev.vantage.module.impl.analysis;

import dev.vantage.detect.AimAnalysis;
import dev.vantage.detect.BacktrackAnalysis;
import dev.vantage.detect.ClickAnalysis;
import dev.vantage.detect.CombatSession;
import dev.vantage.detect.Flagged;
import dev.vantage.detect.HitSample;
import dev.vantage.detect.MovementAnalysis;
import dev.vantage.detect.MovementSample;
import dev.vantage.detect.PlayerTrack;
import dev.vantage.detect.PositionHistory;
import dev.vantage.detect.ReachAnalysis;
import dev.vantage.detect.ScaffoldAnalysis;
import dev.vantage.detect.VelocityAnalysis;
import dev.vantage.detect.ViolationAccumulator;
import dev.vantage.game.LobbyReader;
import dev.vantage.game.TeamColour;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
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
 * <p>Only the cheats people actually run are looked for: reach, backtrack, aim assist, automated
 * bridging and autoclickers. Flight, speed and jump height are gone. They have not survived a
 * server-side anticheat in years so nobody runs them, and more to the point a client cannot measure
 * them — another player's position arrives quantised and interpolated, so the checks were reading
 * their own smoothing and reporting players who were standing still. See {@link MovementAnalysis}.
 *
 * <p>Three rules keep this from accusing the innocent, which is the only failure that matters:
 *
 * <ol>
 *   <li><b>Nothing is collected outside a fight.</b> Mining wool produces a perfectly steady stream
 *       of swing packets; walking past somebody involves turning to look at them. Neither is
 *       evidence, and gathering it was most of the problem. See {@link CombatSession}.
 *   <li><b>Evidence is spent when it is judged.</b> The windows used to be re-read every second
 *       without being cleared, so one odd stretch of play was counted again and again until it
 *       crossed the threshold on its own. See {@link PlayerTrack}.
 *   <li><b>No single check convicts.</b> Two different checks have to agree, or one has to hold up
 *       across several separate windows. See {@link ViolationAccumulator}.
 * </ol>
 *
 * <p>Every verdict is still a heuristic with a confidence attached, not an accusation, and
 * thresholds scale with the watched player's latency.
 */
public class CheatDetectorModule extends Module {

    private static final int ANALYSIS_INTERVAL = 20;

    /** How far back a hit may be rewound before it stops being lag and starts being a choice. */
    private static final int REWIND_TICKS = 20;

    /** Vanilla player hitbox, which is what the server checks a hit against. */
    private static final double HITBOX_HALF_WIDTH = 0.3;
    private static final double HITBOX_HEIGHT = 1.8;

    /** How close somebody has to be for their fight to count as a fight with you. */
    private static final double COMBAT_RANGE = 7.0;

    /** Sample sizes each check needs before it will reach a verdict. */
    private static final int MIN_SWINGS = 26;
    private static final int MIN_ROTATIONS = 40;
    private static final int MIN_HITS = 10;
    private static final int MIN_KNOCKBACKS = 6;
    private static final int MIN_PLACEMENTS = 12;
    private static final int MIN_MOVEMENT = 40;

    /** Ticks after a hit over which to measure how far knockback moved someone. */
    private static final int KNOCKBACK_WINDOW = 5;

    /** The least a normal hit should move somebody, in blocks. */
    private static final double EXPECTED_KNOCKBACK = 0.5;

    private static final double LOCK_CONE_DEGREES = AimAnalysis.DEFAULT_CONE_DEGREES;
    private static final double CLICK_SPREAD_MILLIS = ClickAnalysis.DEFAULT_MAX_SPREAD_MILLIS;
    private static final double ALLOWED_REACH = ReachAnalysis.DEFAULT_ALLOWED_REACH;
    private static final int ALLOWED_BACKWARDS_TICKS = 10;
    private static final double LEVEL_PITCH = 30.0;
    private static final double BEHIND_ANGLE = 60.0;

    /** How far a block may be from somebody before it was clearly not them who placed it. */
    private static final double PLACEMENT_RANGE = 3.0;

    /** And how far below their feet, so only blocks that could be a bridge are attributed. */
    private static final int PLACEMENT_DEPTH = 2;

    /** Suspicion needed before speaking up, and how long an untouched score takes to halve. */
    private static final double NOTIFY_AT = 4.5;
    private static final double SUSPICION_HALF_LIFE_MILLIS = 30_000.0;

    private final BooleanSetting combat = register(new BooleanSetting(
            "Combat", "Reach, backtrack, aim assist, autoclickers and anti-knockback", true));
    private final BooleanSetting scaffold = register(new BooleanSetting(
            "Scaffold", "Automated bridging", true));
    private final BooleanSetting backwardsSprint = register(new BooleanSetting(
            "Backwards Sprint", "Sprinting while walking backwards, which vanilla cannot do", false));
    private final NumberSetting sensitivity = register(new NumberSetting(
            "Sensitivity", "Higher catches more and is wrong more often", 1.0, 0.5, 1.5, 0.05, "x"));
    private final BooleanSetting chatNotifications = register(new BooleanSetting(
            "Chat Notifications", "Announce detections in chat", true));

    private final Map<Integer, PlayerTrack> tracks = new HashMap<Integer, PlayerTrack>();
    private final Map<Integer, CombatSession> sessions = new HashMap<Integer, CombatSession>();
    private final Map<String, ViolationAccumulator> suspicion = new HashMap<String, ViolationAccumulator>();
    private final Map<Integer, double[]> lastPosition = new HashMap<Integer, double[]>();
    private final Map<Integer, Integer> lastHurtTime = new HashMap<Integer, Integer>();
    private final Map<Integer, double[]> pendingKnockback = new HashMap<Integer, double[]>();

    /** Where you have been, which is the only position data on this client worth trusting. */
    private final PositionHistory ownPositions = new PositionHistory();

    private int tickCounter;
    private int selfLastHurtTime;

    public CheatDetectorModule() {
        super("Cheat Detector", Category.ANALYSIS, "Watches other players for signs of automation");
    }

    @Override
    protected void onEnable() {
        PacketObserver observer = PacketObserver.instance();
        observer.setSink((entityId, timestamp) -> {
            // Swings only count while a fight is under way. A player mining wool sends exactly the
            // same packet at exactly the same steady rate as an autoclicker, and counting it was
            // why half a Bedwars lobby looked automated.
            CombatSession session = sessions.get(entityId);
            if (session != null && session.isOpen(timestamp)) {
                trackFor(entityId).recordSwing(timestamp);
            }
        });
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
        sessions.clear();
        suspicion.clear();
        lastPosition.clear();
        lastHurtTime.clear();
        pendingKnockback.clear();
        ownPositions.clear();
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

    private CombatSession sessionFor(int entityId) {
        CombatSession session = sessions.get(entityId);
        if (session == null) {
            session = new CombatSession();
            sessions.put(entityId, session);
        }
        return session;
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

        // Yours first: every measurement of a hit is taken against where you were, so the history
        // has to already contain this tick before anything is judged against it.
        ownPositions.record(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        noticeBlowsLanded(mc, others, now);

        for (EntityPlayer player : others) {
            CombatSession session = sessions.get(player.getEntityId());
            if (session == null || !session.isOpen(now)) {
                // Not fighting anyone you can see. Deliberately blind.
                lastPosition.remove(player.getEntityId());
                PlayerTrack existing = tracks.get(player.getEntityId());
                if (existing != null) {
                    // So the next fight's first turn is not differenced against this one's last.
                    existing.breakRotationContinuity();
                }
                continue;
            }
            sampleFight(mc, player, now);
        }

        if (scaffold.value()) {
            attributePlacements(others);
        } else {
            PacketObserver.instance().drainPlacements();
        }

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

    /**
     * Notices damage in either direction and opens or extends the fight it belongs to.
     *
     * <p>A blow you take is also the only honest reach measurement available, so it is recorded
     * here rather than in a separate pass.
     */
    private void noticeBlowsLanded(Minecraft mc, List<EntityPlayer> others, long now) {
        int hurtTime = mc.thePlayer.hurtTime;
        boolean youWereHit = hurtTime > selfLastHurtTime;
        selfLastHurtTime = hurtTime;

        if (youWereHit) {
            EntityPlayer attacker = mostRecentSwinger(others, now);
            if (attacker != null) {
                sessionFor(attacker.getEntityId()).exchange(now);
                if (combat.value()) {
                    trackFor(attacker.getEntityId()).recordHitOnYou(measureHit(mc, attacker));
                }
            }
        }

        for (EntityPlayer player : others) {
            int id = player.getEntityId();
            Integer previous = lastHurtTime.get(id);
            lastHurtTime.put(id, player.hurtTime);
            // hurtTime is set to its maximum the moment the hurt animation arrives, then counts
            // down. Somebody taking a hit close enough to see is a fight worth watching, whether
            // or not you are the one landing it.
            boolean justHurt = previous != null && player.hurtTime > previous;
            if (justHurt && mc.thePlayer.getDistanceToEntity(player) <= COMBAT_RANGE) {
                sessionFor(id).exchange(now);
            }
            if (combat.value()) {
                sampleKnockback(player, justHurt, now);
            }
        }
    }

    /**
     * Measures how far a player travelled in the ticks after they were hit.
     *
     * <p>A blow shoves you, and how far you travel afterwards follows from the game's physics.
     * Somebody who barely moves after being hit repeatedly is cancelling it. Unlike the movement
     * checks that were removed, this survives the client only having an interpolated copy of their
     * position: half a block over five ticks is far larger than the smoothing error, and the
     * verdict is taken across several hits rather than one.
     */
    private void sampleKnockback(EntityPlayer player, boolean justHurt, long now) {
        int id = player.getEntityId();
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
        CombatSession session = sessions.get(id);
        if (justHurt && session != null && session.isOpen(now)) {
            pendingKnockback.put(id, new double[]{KNOCKBACK_WINDOW, player.posX, player.posZ});
        }
    }

    /**
     * Measures one blow both ways: how far they were when it arrived, and the closest they came at
     * any point still on record. See {@link HitSample} for why both are needed.
     */
    private HitSample measureHit(Minecraft mc, EntityPlayer attacker) {
        double eyeX = attacker.posX;
        double eyeY = attacker.posY + attacker.getEyeHeight();
        double eyeZ = attacker.posZ;

        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double now = PositionHistory.distanceToBox(eyeX, eyeY, eyeZ,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);

        PositionHistory.Approach best = ownPositions.closestApproach(eyeX, eyeY, eyeZ,
                HITBOX_HALF_WIDTH, HITBOX_HEIGHT, REWIND_TICKS);
        if (best == null) {
            return new HitSample(now, now, 0);
        }
        return new HitSample(now, Math.min(now, best.getDistance()), best.getTicksAgo());
    }

    /** Records where one player is looking and how they are moving, for as long as you are fighting. */
    private void sampleFight(Minecraft mc, EntityPlayer player, long now) {
        int id = player.getEntityId();

        if (combat.value()) {
            // The target is you. Guessing at who else somebody might be aiming at is how the old
            // check ended up measuring rotation against a bystander thirty blocks away.
            trackFor(id).recordRotation(player.rotationYaw, signedAngleTo(player, mc.thePlayer), now);
        }

        double[] previous = lastPosition.get(id);
        lastPosition.put(id, new double[]{player.posX, player.posY, player.posZ});
        if (previous == null || !backwardsSprint.value()) {
            return;
        }

        double dx = player.posX - previous[0];
        double dz = player.posZ - previous[2];
        double travelled = Math.sqrt(dx * dx + dz * dz);

        // A step this large is the server repositioning them, not the player moving. Marking it
        // lets the check discard the pair rather than reading it as impossible movement.
        boolean teleported = travelled > 8.0;

        Vec3 look = player.getLook(1.0f);
        double lookLength = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        double facingDot = 1.0;
        if (travelled > 1e-4 && lookLength > 1e-4) {
            facingDot = (look.xCoord * dx + look.zCoord * dz) / (lookLength * travelled);
        }

        trackFor(id).recordMovement(new MovementSample(player.posX, player.posY, player.posZ,
                player.onGround, player.isSprinting(), facingDot, teleported));
    }

    /**
     * Attributes each newly placed block to whoever could actually have placed it.
     *
     * <p>Much stricter than it was. The old version handed every block to the nearest player within
     * six blocks, so during a fight near a bridge it credited whoever happened to be closest, and a
     * single explosion — which arrives as one batched change packet — handed somebody thirty
     * "placements" at once. Now a block only counts when it sits where a bridge block would sit,
     * and only when exactly one person was close enough to have put it there. Anything ambiguous is
     * dropped rather than guessed at.
     */
    private void attributePlacements(List<EntityPlayer> players) {
        for (PacketObserver.Placement placement : PacketObserver.instance().drainPlacements()) {
            BlockPos position = placement.position;
            EntityPlayer placer = null;
            boolean ambiguous = false;

            for (EntityPlayer player : players) {
                if (!couldHavePlaced(player, position)) {
                    continue;
                }
                if (placer != null) {
                    ambiguous = true;
                    break;
                }
                placer = player;
            }
            if (placer == null || ambiguous) {
                continue;
            }
            trackFor(placer.getEntityId()).recordPlacement(
                    placer.rotationPitch, angleToBlock(placer, position));
        }
    }

    /** Whether a block sits where this player could have bridged it: near them, and at their feet. */
    private static boolean couldHavePlaced(EntityPlayer player, BlockPos block) {
        double dx = block.getX() + 0.5 - player.posX;
        double dz = block.getZ() + 0.5 - player.posZ;
        if (Math.sqrt(dx * dx + dz * dz) > PLACEMENT_RANGE) {
            return false;
        }
        int feet = (int) Math.floor(player.posY);
        return block.getY() <= feet && block.getY() >= feet - PLACEMENT_DEPTH;
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

    /**
     * The signed horizontal angle from where a player is looking to where a target is.
     *
     * <p>Signed on purpose: the sign says which side of their crosshair the target sits on, so a
     * sign change means they swung past it. That is the overshoot {@link AimAnalysis} counts, and
     * an unsigned angle throws it away.
     */
    private static double signedAngleTo(EntityPlayer from, EntityPlayer to) {
        double dx = to.posX - from.posX;
        double dz = to.posZ - from.posZ;
        double desiredYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        return PlayerTrack.wrapDegrees(desiredYaw - from.rotationYaw);
    }

    private static double angleToBlock(EntityPlayer from, BlockPos block) {
        Vec3 look = from.getLook(1.0f).normalize();
        Vec3 toBlock = new Vec3(
                block.getX() + 0.5 - from.posX,
                block.getY() + 0.5 - (from.posY + from.getEyeHeight()),
                block.getZ() + 0.5 - from.posZ).normalize();
        double dot = look.dotProduct(toBlock);
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
            if (scaffold.value()) {
                runScaffoldCheck(track, name, teams, now);
            }
            if (backwardsSprint.value()) {
                runMovementCheck(track, name, teams, now, slack);
            }
        }
        expireStaleFlags(now);
    }

    private void runCombatChecks(PlayerTrack track, String name, Map<String, TeamColour> teams,
                                 long now, double slack) {
        long[] swings = track.takeSwings(MIN_SWINGS);
        if (swings != null) {
            ClickAnalysis.Result clicks = ClickAnalysis.analyse(swings, CLICK_SPREAD_MILLIS / slack);
            if (clicks.isSuspicious()) {
                report(name, teams, now, clicks.getConfidence() * 2.5, "Autoclicker",
                        String.format(Locale.ROOT, "%.1f cps, %.1fms spread over %d clicks",
                                clicks.getClicksPerSecond(), clicks.getStandardDeviationMillis(),
                                clicks.getSamples()));
            }
        }

        double[][] rotations = track.takeRotations(MIN_ROTATIONS);
        if (rotations != null) {
            AimAnalysis.Result aim = AimAnalysis.analyse(rotations[0], rotations[1], LOCK_CONE_DEGREES);
            if (aim.isSuspicious()) {
                report(name, teams, now, aim.getConfidence() * 2.5, "Aim assist",
                        String.format(Locale.ROOT, "%.0f%% tracking, %.0f%% overshoot over %d swings",
                                aim.getTrackingRate() * 100.0, aim.getOvershootRate() * 100.0,
                                aim.getEngagements()));
            }
        }

        // Reach and backtrack read the same hits, so they are judged together and spent once.
        List<HitSample> hits = track.peekHits(MIN_HITS);
        if (hits != null) {
            double allowed = ALLOWED_REACH * slack;

            ReachAnalysis.Result reach = ReachAnalysis.analyse(hits, allowed);
            if (reach.isSuspicious()) {
                report(name, teams, now, reach.getConfidence() * 2.5, "Reach",
                        String.format(Locale.ROOT, "%.2fm median over %d hits",
                                reach.getMedian(), reach.getSamples()));
            }

            BacktrackAnalysis.Result backtrack = BacktrackAnalysis.analyse(hits, allowed);
            if (backtrack.isSuspicious()) {
                report(name, teams, now, backtrack.getConfidence() * 2.5, "Backtrack",
                        String.format(Locale.ROOT, "%.0f ticks behind, +/-%.1f, over %d hits",
                                backtrack.getMedianTicksAgo(), backtrack.getSpreadTicks(),
                                backtrack.getHits()));
            }
            track.clearHits();
        }

        double[] knockbacks = track.takeHitDisplacements(MIN_KNOCKBACKS);
        if (knockbacks != null) {
            VelocityAnalysis.Result velocity = VelocityAnalysis.analyse(
                    knockbacks, EXPECTED_KNOCKBACK / slack);
            if (velocity.isSuspicious()) {
                report(name, teams, now, velocity.getConfidence() * 2.5, "Anti-knockback",
                        String.format(Locale.ROOT, "%.2fm after %d hits",
                                velocity.getMedianDisplacement(), velocity.getHits()));
            }
        }
    }

    private void runScaffoldCheck(PlayerTrack track, String name, Map<String, TeamColour> teams,
                                  long now) {
        double[][] placements = track.takePlacements(MIN_PLACEMENTS);
        if (placements == null) {
            return;
        }
        ScaffoldAnalysis.Result result = ScaffoldAnalysis.analyse(
                placements[0], placements[1], LEVEL_PITCH, BEHIND_ANGLE);
        if (result.isSuspicious()) {
            report(name, teams, now, result.getConfidence() * 2.5, "Scaffold",
                    String.format(Locale.ROOT, "%.0f%% of %d placements",
                            result.getAutomatedFraction() * 100.0, result.getPlacements()));
        }
    }

    private void runMovementCheck(PlayerTrack track, String name, Map<String, TeamColour> teams,
                                  long now, double slack) {
        List<MovementSample> samples = track.takeMovement(MIN_MOVEMENT);
        if (samples == null) {
            return;
        }
        MovementAnalysis.Result backwards = MovementAnalysis.omniSprint(
                samples, (int) Math.round(ALLOWED_BACKWARDS_TICKS * slack));
        if (backwards.isSuspicious()) {
            report(name, teams, now, backwards.getConfidence() * 2.5, "Backwards sprint",
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
            accumulator = new ViolationAccumulator(SUSPICION_HALF_LIFE_MILLIS, NOTIFY_AT);
            suspicion.put(name, accumulator);
        }
        accumulator.add(check, weight, now);

        // The accumulator decides, not this call: one check saying something once is not enough,
        // however confident it was.
        if (!accumulator.shouldNotify(now)) {
            return;
        }

        String strongest = accumulator.strongestCheck(now);
        Flagged.set(name, strongest == null ? check : strongest);

        if (!chatNotifications.value()) {
            return;
        }
        TeamColour team = teams.get(name);
        StringBuilder message = new StringBuilder("§8[§bVantage§8] §f");
        if (team != null && team != TeamColour.UNKNOWN) {
            message.append('§').append(team.getColourCode()).append(team.getDisplayName()).append(" §7- §f");
        }
        message.append(name).append(" §7may be using §c").append(strongest == null ? check : strongest)
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
