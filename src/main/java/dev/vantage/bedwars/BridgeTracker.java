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
 * <p>Each placement is credited to whoever placed it. Only the bridge a player is building right
 * now is judged: the run of placements behind the newest one where each block sits next to the one
 * before and no pause was long enough to count as stopping. Blocks a player put down earlier
 * somewhere else - their own defence, a tower, a trip to mid - are not part of it, so they cannot
 * add up with a few new blocks into something that looks like a line.
 *
 * <p>That run is a rush only if it has been going for a while, is long and mostly straight, came
 * from outside your island, is getting closer to your bed, and would actually land on your island
 * if it carried on the way it is going. A bridge that merely points somewhere near you, like one to
 * mid or to the island next door, misses on that last test.
 *
 * <p>Minecraft-free and clocked by the caller, so each rule is pinned down by a test.
 */
public final class BridgeTracker {

    /** How much history is kept per player. Long enough to hold a whole bridge between islands. */
    static final long WINDOW_MILLIS = 30000L;
    /** A bridge whose newest block is older than this has stopped. */
    static final long STALE_MILLIS = 3000L;
    /** A pause longer than this between two blocks ends one bridge and starts another. */
    static final long MAX_PAUSE_MILLIS = 4000L;
    /** Two blocks further apart than this are not part of the same bridge. */
    static final double MAX_STEP = 3.5;

    static final int MIN_PLACEMENTS = 8;
    static final double MIN_LENGTH = 8.0;
    /** A bridge has to have been growing for this long before it counts. */
    static final long MIN_DURATION_MILLIS = 2500L;
    static final double MIN_SPEED = 0.6;
    /** Displacement over path walked; below this the placements are a cluster, not a line. */
    static final double MIN_STRAIGHTNESS = 0.6;
    /**
     * How much of its length a bridge must have closed on your bed. Straight at it closes all of
     * it; one passing by at an angle closes much less.
     */
    static final double MIN_CLOSING = 0.7;
    /** Slack beyond the island's radius for where the bridge's line may pass the bed. */
    static final double LANE_MARGIN = 4.0;

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
        /** Compass bearing from your bed to the bridge head, 0 north, clockwise. */
        public final double bearing;

        Rush(String player, double headX, double headY, double headZ, double distance, double speed,
             double etaSeconds, double bearing) {
            this.player = player;
            this.headX = headX;
            this.headY = headY;
            this.headZ = headZ;
            this.distance = distance;
            this.speed = speed;
            this.etaSeconds = etaSeconds;
            this.bearing = bearing;
        }

        /** Bearing as an eight-point compass direction. */
        public String direction() {
            String[] points = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
            return points[(int) Math.floor(((bearing % 360.0) + 360.0) % 360.0 / 45.0 + 0.5) & 7];
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
        while (list.size() > 160) {
            list.removeFirst();
        }
    }

    public synchronized void clear() {
        placements.clear();
    }

    /**
     * Every player bridging at your bed whose bridge is within {@code warnDistance} of your
     * island's edge, soonest arrival first.
     */
    public synchronized List<Rush> rushes(double bedX, double bedZ, double islandRadius, double warnDistance,
                                          long now) {
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
            Rush rush = analyse(entry.getKey(), currentRun(new ArrayList<double[]>(list)), bedX, bedZ,
                    islandRadius, now);
            if (rush != null && rush.distance - islandRadius <= warnDistance) {
                found.add(rush);
            }
        }
        found.sort((a, b) -> Double.compare(a.etaSeconds, b.etaSeconds));
        return found;
    }

    /**
     * The bridge being built now: walking back from the newest block for as long as each block is
     * next to the one after it and came shortly before it.
     */
    static List<double[]> currentRun(List<double[]> all) {
        if (all.isEmpty()) {
            return all;
        }
        int start = all.size() - 1;
        while (start > 0) {
            double[] earlier = all.get(start - 1);
            double[] later = all.get(start);
            double dx = later[0] - earlier[0];
            double dy = later[1] - earlier[1];
            double dz = later[2] - earlier[2];
            if (dx * dx + dy * dy + dz * dz > MAX_STEP * MAX_STEP || later[3] - earlier[3] > MAX_PAUSE_MILLIS) {
                break;
            }
            start--;
        }
        return new ArrayList<double[]>(all.subList(start, all.size()));
    }

    static Rush analyse(String player, List<double[]> run, double bedX, double bedZ, double islandRadius, long now) {
        if (run.size() < MIN_PLACEMENTS) {
            return null;
        }
        double[] tail = run.get(0);
        double[] head = run.get(run.size() - 1);
        if (now - head[3] > STALE_MILLIS || head[3] - tail[3] < MIN_DURATION_MILLIS) {
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
        double seconds = (head[3] - tail[3]) / 1000.0;
        double speed = length / seconds;
        if (speed < MIN_SPEED) {
            return null;
        }

        // Somebody who is already on your island is past warning about, and blocks placed there are
        // as likely to be a fight on your bed as a bridge to it.
        double tailDistance = Math.hypot(bedX - tail[0], bedZ - tail[2]);
        if (tailDistance <= islandRadius) {
            return null;
        }
        double headToBedX = bedX - head[0];
        double headToBedZ = bedZ - head[2];
        double distance = Math.sqrt(headToBedX * headToBedX + headToBedZ * headToBedZ);
        if (tailDistance - distance < MIN_CLOSING * length) {
            return null;
        }
        // Carry the bridge on in a straight line and see how close it passes. It has to land on
        // the island, not just head roughly toward it.
        double directionX = dx / length;
        double directionZ = dz / length;
        double ahead = headToBedX * directionX + headToBedZ * directionZ;
        double sideways = Math.abs(headToBedX * directionZ - headToBedZ * directionX);
        if (distance > islandRadius && (ahead <= 0.0 || sideways > islandRadius + LANE_MARGIN)) {
            return null;
        }

        double remaining = Math.max(0.0, distance - islandRadius);
        double bearing = Math.toDegrees(Math.atan2(head[0] - bedX, -(head[2] - bedZ)));
        return new Rush(player, head[0], head[1], head[2], distance, speed, remaining / speed, bearing);
    }
}
