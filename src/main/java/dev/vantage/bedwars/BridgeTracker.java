package dev.vantage.bedwars;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Spots bridges being built toward your bed.
 *
 * <p>Each placement is credited to whoever placed it. A rush looks like a run of placements by one
 * player that is long, mostly straight, still growing, and heading at your island; a defence being
 * built, a tower, or someone bridging to mid all fail at least one of those. From the run's length
 * and age comes a speed, and from the speed and the gap left, an arrival time.
 *
 * <p>Minecraft-free and clocked by the caller, so each rule is pinned down by a test.
 */
public final class BridgeTracker {

    /** Placements older than this no longer count toward a bridge. */
    static final long WINDOW_MILLIS = 8000L;
    /** A bridge whose newest block is older than this has stopped. */
    static final long STALE_MILLIS = 3000L;
    static final int MIN_PLACEMENTS = 4;
    static final double MIN_LENGTH = 4.0;
    static final double MIN_SPEED = 0.6;
    /** How far off the line to your bed a bridge may head and still count, in degrees. */
    static final double MAX_ANGLE = 35.0;
    /** Displacement over path walked; below this the placements are a cluster, not a line. */
    static final double MIN_STRAIGHTNESS = 0.6;

    /** A bridge heading your way. */
    public static final class Rush {
        public final String player;
        /** Where the newest block went. */
        public final double headX;
        public final double headY;
        public final double headZ;
        /** From the bridge head to your bed, ignoring height. */
        public final double distance;
        /** Blocks per second along the bridge. */
        public final double speed;
        /** Seconds until the bridge reaches your island at its current pace. */
        public final double etaSeconds;

        Rush(String player, double headX, double headY, double headZ, double distance, double speed,
             double etaSeconds) {
            this.player = player;
            this.headX = headX;
            this.headY = headY;
            this.headZ = headZ;
            this.distance = distance;
            this.speed = speed;
            this.etaSeconds = etaSeconds;
        }
    }

    private final Map<String, Deque<double[]>> placements = new HashMap<String, Deque<double[]>>();

    public synchronized void record(String player, double x, double y, double z, long time) {
        Deque<double[]> list = placements.get(player);
        if (list == null) {
            list = new ArrayDeque<double[]>();
            placements.put(player, list);
        }
        list.addLast(new double[]{x, y, z, time});
        while (list.size() > 64) {
            list.removeFirst();
        }
    }

    public synchronized void clear() {
        placements.clear();
    }

    /** Every player currently bridging at your bed, soonest arrival first. */
    public synchronized List<Rush> rushes(double bedX, double bedZ, double islandRadius, long now) {
        List<Rush> found = new ArrayList<Rush>();
        Iterator<Map.Entry<String, Deque<double[]>>> entries = placements.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Deque<double[]>> entry = entries.next();
            Deque<double[]> list = entry.getValue();
            while (!list.isEmpty() && now - list.peekFirst()[3] > WINDOW_MILLIS) {
                list.removeFirst();
            }
            if (list.isEmpty()) {
                entries.remove();
                continue;
            }
            Rush rush = analyse(entry.getKey(), new ArrayList<double[]>(list), bedX, bedZ, islandRadius, now);
            if (rush != null) {
                found.add(rush);
            }
        }
        found.sort((a, b) -> Double.compare(a.etaSeconds, b.etaSeconds));
        return found;
    }

    static Rush analyse(String player, List<double[]> run, double bedX, double bedZ, double islandRadius, long now) {
        if (run.size() < MIN_PLACEMENTS) {
            return null;
        }
        double[] tail = run.get(0);
        double[] head = run.get(run.size() - 1);
        if (now - head[3] > STALE_MILLIS) {
            return null;
        }
        double dx = head[0] - tail[0];
        double dz = head[2] - tail[2];
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < MIN_LENGTH) {
            return null;
        }
        double walked = 0.0;
        for (int i = 1; i < run.size(); i++) {
            double sx = run.get(i)[0] - run.get(i - 1)[0];
            double sz = run.get(i)[2] - run.get(i - 1)[2];
            walked += Math.sqrt(sx * sx + sz * sz);
        }
        if (walked <= 0.0 || length / walked < MIN_STRAIGHTNESS) {
            return null;
        }
        double seconds = Math.max(0.05, (head[3] - tail[3]) / 1000.0);
        double speed = length / seconds;
        if (speed < MIN_SPEED) {
            return null;
        }
        // Heading at the bed, measured from where the bridge started.
        double toBedX = bedX - tail[0];
        double toBedZ = bedZ - tail[2];
        double toBed = Math.sqrt(toBedX * toBedX + toBedZ * toBedZ);
        if (toBed <= 0.0) {
            return null;
        }
        double cosine = (dx * toBedX + dz * toBedZ) / (length * toBed);
        if (cosine < Math.cos(Math.toRadians(MAX_ANGLE))) {
            return null;
        }
        double headToBedX = bedX - head[0];
        double headToBedZ = bedZ - head[2];
        double distance = Math.sqrt(headToBedX * headToBedX + headToBedZ * headToBedZ);
        double remaining = Math.max(0.0, distance - islandRadius);
        return new Rush(player, head[0], head[1], head[2], distance, speed, remaining / speed);
    }
}
