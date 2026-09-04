package dev.vantage.module.impl.analysis;

import dev.vantage.detect.AimAnalysis;
import dev.vantage.detect.ClickAnalysis;
import dev.vantage.detect.Flagged;
import dev.vantage.detect.PlayerTrack;
import dev.vantage.detect.ReachAnalysis;
import dev.vantage.detect.ViolationAccumulator;
import dev.vantage.game.LobbyReader;
import dev.vantage.game.TeamColour;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.net.SwingListener;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Watches other players for the patterns that automation leaves behind, and says so in chat.
 *
 * <p>What is measurable from a client is narrower than what a server can see, and this only
 * implements checks that survive that gap. Notably, another player's rotations arrive quantised to
 * a single byte per axis - steps of about 1.4 degrees - so the fine-grained mouse analysis a
 * server-side anticheat performs is not possible here at all. Coarse behaviour is: whether a view
 * stays glued to a target, whether large turns land on one, and whether click timing is too even
 * to come from a hand.
 *
 * <p>Every verdict is a heuristic with a confidence attached, not an accusation. Thresholds scale
 * with the watched player's latency, because a laggy connection distorts all of these signals and
 * is the single largest source of wrong answers.
 */
public class CheatDetectorModule extends Module {

    /** How often to re-run the analyses, in ticks. */
    private static final int ANALYSIS_INTERVAL = 20;

    private static final double SNAP_DEGREES = 30.0;
    private static final double LOCK_DEGREES = 3.0;

    private final BooleanSetting detectAutoclicker = register(new BooleanSetting(
            "Autoclicker", "Flag click timing too even to be from a hand", true));
    private final NumberSetting clickSpread = register(new NumberSetting(
            "Click Spread", "Timing spread below which clicking looks automated",
            5.0, 1.0, 15.0, 0.5, "ms"));

    private final BooleanSetting detectAim = register(new BooleanSetting(
            "Aim Assist", "Flag views that stay locked on a target or snap onto one", true));

    private final BooleanSetting detectReach = register(new BooleanSetting(
            "Reach", "Flag long-range hits landed on you", true));
    private final NumberSetting allowedReach = register(new NumberSetting(
            "Allowed Reach", "Distance treated as legitimate, above vanilla to absorb lag compensation",
            3.4, 3.0, 4.5, 0.05, "m"));

    private final NumberSetting notifyAt = register(new NumberSetting(
            "Notify At", "Suspicion score needed before saying anything", 6.0, 2.0, 20.0, 0.5));
    private final BooleanSetting chatNotifications = register(new BooleanSetting(
            "Chat Notifications", "Announce detections in chat", true));

    private final Map<Integer, PlayerTrack> tracks = new HashMap<Integer, PlayerTrack>();
    private final Map<String, ViolationAccumulator> suspicion = new HashMap<String, ViolationAccumulator>();
    private final Map<String, List<Double>> reachSamples = new HashMap<String, List<Double>>();

    private int tickCounter;
    private int lastHurtTime;

    public CheatDetectorModule() {
        super("Cheat Detector", Category.ANALYSIS, "Watches other players for signs of automation");
        clickSpread.visibleWhen(detectAutoclicker::value);
        allowedReach.visibleWhen(detectReach::value);
    }

    @Override
    protected void onEnable() {
        SwingListener listener = SwingListener.instance();
        listener.setSink((entityId, timestamp) -> trackFor(entityId).recordSwing(timestamp));
        listener.attachIfNeeded();
    }

    @Override
    protected void onDisable() {
        SwingListener.instance().setSink(null);
        SwingListener.instance().detach();
        clearState();
    }

    @Override
    public void onWorldChanged() {
        clearState();
    }

    private void clearState() {
        tracks.clear();
        suspicion.clear();
        reachSamples.clear();
        Flagged.clear();
        SwingListener.instance().clear();
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

    @Override
    public void onTick() {
        SwingListener.instance().attachIfNeeded();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        long now = System.currentTimeMillis();
        sampleRotations(mc, now);
        sampleReach(mc, now);

        if (++tickCounter < ANALYSIS_INTERVAL) {
            return;
        }
        tickCounter = 0;
        runAnalyses(mc, now);
    }

    /** Records each visible player's turn rate and how close their view is to a target. */
    private void sampleRotations(Minecraft mc, long now) {
        for (Object entity : new ArrayList<Object>(mc.theWorld.playerEntities)) {
            if (!(entity instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) entity;
            if (player == mc.thePlayer) {
                continue;
            }
            EntityPlayer target = nearestOtherPlayer(mc, player);
            double angle = target == null ? 180.0 : angleBetween(player, target);
            trackFor(player.getEntityId()).recordRotation(player.rotationYaw, angle, now);
        }
    }

    /** Nearest player to {@code from}, which is who any aim assist would be tracking. */
    private EntityPlayer nearestOtherPlayer(Minecraft mc, EntityPlayer from) {
        EntityPlayer best = null;
        double bestDistance = 64.0; // squared; aim assist has no reason to look further
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

    /** Angle in degrees between where {@code from} is looking and where {@code to} actually is. */
    private static double angleBetween(EntityPlayer from, EntityPlayer to) {
        Vec3 look = from.getLook(1.0f).normalize();
        Vec3 toTarget = new Vec3(
                to.posX - from.posX,
                (to.posY + to.getEyeHeight()) - (from.posY + from.getEyeHeight()),
                to.posZ - from.posZ).normalize();
        double dot = look.dotProduct(toTarget);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    /**
     * Records the distance of hits landed on the local player.
     *
     * <p>Only hits on you: for an attack between two other players the client sees neither the
     * attack nor the positions the server used, so there is nothing honest to measure.
     */
    private void sampleReach(Minecraft mc, long now) {
        int hurtTime = mc.thePlayer.hurtTime;
        boolean justHurt = hurtTime > lastHurtTime;
        lastHurtTime = hurtTime;
        if (!justHurt || !detectReach.value()) {
            return;
        }

        EntityPlayer attacker = mostRecentSwinger(mc, now);
        if (attacker == null) {
            return;
        }
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double distance = ReachAnalysis.distanceToBox(
                attacker.posX, attacker.posY + attacker.getEyeHeight(), attacker.posZ,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);

        List<Double> samples = reachSamples.get(attacker.getName());
        if (samples == null) {
            samples = new ArrayList<Double>();
            reachSamples.put(attacker.getName(), samples);
        }
        samples.add(distance);
        // Keep the window recent; gear and ping change over a game.
        while (samples.size() > 20) {
            samples.remove(0);
        }
    }

    /** The nearby player whose swing packet arrived most recently, within a short window. */
    private EntityPlayer mostRecentSwinger(Minecraft mc, long now) {
        EntityPlayer best = null;
        long bestTime = now - 300L; // a swing older than this did not cause this hit
        for (Object entity : mc.theWorld.playerEntities) {
            if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer) {
                continue;
            }
            EntityPlayer candidate = (EntityPlayer) entity;
            Long swing = SwingListener.instance().getLastSwing(candidate.getEntityId());
            if (swing != null && swing > bestTime) {
                bestTime = swing;
                best = candidate;
            }
        }
        return best;
    }

    // -- analysis ---------------------------------------------------------------------------

    private void runAnalyses(Minecraft mc, long now) {
        Map<String, TeamColour> teams = LobbyReader.readTeamAssignments(LobbyReader.readPlayers());

        for (Object entity : new ArrayList<Object>(mc.theWorld.playerEntities)) {
            if (!(entity instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) entity;
            if (player == mc.thePlayer) {
                continue;
            }
            String name = player.getName();
            PlayerTrack track = trackFor(player.getEntityId());
            double latencyAllowance = latencyAllowance(mc, name);

            if (detectAutoclicker.value()) {
                // A laggy player's click timing arrives smeared, which hides automation rather
                // than inventing it, so the bar is tightened rather than loosened.
                double spread = clickSpread.asDouble() / latencyAllowance;
                ClickAnalysis.Result result = ClickAnalysis.analyse(track.getSwingTimes(), spread);
                if (result.isSuspicious()) {
                    report(name, teams, now, result.getConfidence() * 3.0, "Autoclicker",
                            String.format(Locale.ROOT, "%.1f cps, %.1fms spread",
                                    result.getClicksPerSecond(), result.getStandardDeviationMillis()));
                }
            }

            if (detectAim.value()) {
                AimAnalysis.Result result = AimAnalysis.analyse(
                        track.getYawDeltas(), track.getTargetAngles(),
                        SNAP_DEGREES * latencyAllowance, LOCK_DEGREES);
                if (result.isSuspicious()) {
                    report(name, teams, now, result.getConfidence() * 3.0, "Aim assist",
                            result.getSnaps() >= 3
                                    ? result.getSnaps() + " snaps onto target"
                                    : String.format(Locale.ROOT, "%.0f%% locked on",
                                    result.getLockedFraction() * 100.0));
                }
            }

            if (detectReach.value()) {
                List<Double> samples = reachSamples.get(name);
                if (samples != null && !samples.isEmpty()) {
                    double[] array = new double[samples.size()];
                    for (int i = 0; i < array.length; i++) {
                        array[i] = samples.get(i);
                    }
                    ReachAnalysis.Result result = ReachAnalysis.analyse(
                            array, allowedReach.asDouble() * latencyAllowance);
                    if (result.isSuspicious()) {
                        report(name, teams, now, result.getConfidence() * 3.0, "Reach",
                                String.format(Locale.ROOT, "%.2fm median over %d hits",
                                        result.getMedian(), result.getSamples()));
                    }
                }
            }
        }

        expireStaleFlags(now);
    }

    /**
     * A multiplier that widens thresholds for a laggy player.
     *
     * <p>High latency distorts every one of these signals, and treating a laggy honest player the
     * same as a local one is the most common way a detector like this ends up wrong.
     */
    private double latencyAllowance(Minecraft mc, String name) {
        int ping = pingOf(mc, name);
        if (ping <= 100) {
            return 1.0;
        }
        // Grows to 1.5 by 500ms and no further.
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
            // Half life of half a minute: a pattern has to persist to build up.
            accumulator = new ViolationAccumulator(30_000.0, notifyAt.asDouble());
            suspicion.put(name, accumulator);
        }
        accumulator.add(weight, now);
        if (!accumulator.shouldNotify(now)) {
            return;
        }

        TeamColour team = teams.get(name);
        String teamLabel = team == null || team == TeamColour.UNKNOWN ? "" : team.getDisplayName();
        Flagged.set(name, check);

        if (!chatNotifications.value()) {
            return;
        }
        StringBuilder message = new StringBuilder("§8[§bVantage§8] §f");
        if (!teamLabel.isEmpty()) {
            message.append('§').append(team.getColourCode()).append(teamLabel).append(" §7- §f");
        }
        message.append(name).append(" §7may be using §c").append(check)
                .append(" §8(").append(detail).append(')');
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(message.toString()));
    }

    /** Drops flags for players whose suspicion has decayed away or who have left. */
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
