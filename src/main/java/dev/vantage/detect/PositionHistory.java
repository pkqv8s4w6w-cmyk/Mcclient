package dev.vantage.detect;

/**
 * Where somebody has been for the last couple of seconds.
 *
 * <p>This exists because "how far away were they" is the wrong question. A Minecraft server does
 * not check a hit against where the victim is now; it rewinds the world to where the victim was
 * when the attacker's packet was sent, which is what makes a hit land at all across a connection
 * with any latency. So a check that only measures the current distance is asking a question the
 * server never asks, and it reads long for everybody.
 *
 * <p>The right question is whether there was <em>any</em> recent moment at which the hit would have
 * been legal, and how far back that moment was. Both answers come out of {@link #closestApproach}.
 *
 * <p>A fixed-size ring, so a long game costs the same as a short one. No Minecraft references, so
 * the geometry is pinned by tests.
 */
public final class PositionHistory {

    /** Two seconds at twenty ticks: far longer than any plausible lag compensation window. */
    public static final int CAPACITY = 40;

    /** What the closest point of a player's recent path looked like from somewhere else. */
    public static final class Approach {
        private final double distance;
        private final int ticksAgo;

        Approach(double distance, int ticksAgo) {
            this.distance = distance;
            this.ticksAgo = ticksAgo;
        }

        /** Distance to the nearest point of their hitbox at the closest recorded moment. */
        public double getDistance() {
            return distance;
        }

        /** How many ticks back that moment was. Zero means right now. */
        public int getTicksAgo() {
            return ticksAgo;
        }
    }

    private final double[] xs = new double[CAPACITY];
    private final double[] ys = new double[CAPACITY];
    private final double[] zs = new double[CAPACITY];
    private int count;
    private int head;

    /** Records one tick of position. {@code y} is the feet, as Minecraft reports it. */
    public synchronized void record(double x, double y, double z) {
        xs[head] = x;
        ys[head] = y;
        zs[head] = z;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
    }

    public synchronized int size() {
        return count;
    }

    /**
     * @param ticksAgo 0 for the most recent sample
     * @return {x, y, z}, or null when nothing that old was recorded
     */
    public synchronized double[] at(int ticksAgo) {
        if (ticksAgo < 0 || ticksAgo >= count) {
            return null;
        }
        int index = (head - 1 - ticksAgo + CAPACITY * 2) % CAPACITY;
        return new double[]{xs[index], ys[index], zs[index]};
    }

    /**
     * The closest this player's hitbox came to a point at any moment still on record.
     *
     * <p>Used to ask what the server would have asked: was this hit legal against where they were a
     * moment ago, and if so, how long ago?
     *
     * @param halfWidth   half the player's hitbox width; 0.3 in vanilla
     * @param height      the hitbox height; 1.8 in vanilla
     * @param maxTicksBack how far to rewind at most
     * @return the nearest approach, or null when there is no history at all
     */
    public synchronized Approach closestApproach(double x, double y, double z,
                                                 double halfWidth, double height,
                                                 int maxTicksBack) {
        if (count == 0) {
            return null;
        }
        double best = Double.MAX_VALUE;
        int bestTicksAgo = 0;
        int limit = Math.min(count, Math.max(1, maxTicksBack));
        for (int ticksAgo = 0; ticksAgo < limit; ticksAgo++) {
            int index = (head - 1 - ticksAgo + CAPACITY * 2) % CAPACITY;
            double distance = distanceToBox(x, y, z,
                    xs[index] - halfWidth, ys[index], zs[index] - halfWidth,
                    xs[index] + halfWidth, ys[index] + height, zs[index] + halfWidth);
            if (distance < best) {
                best = distance;
                bestTicksAgo = ticksAgo;
            }
        }
        return new Approach(best, bestTicksAgo);
    }

    /**
     * Straight-line distance from a point to the closest point of a box. Zero when the point is
     * inside it.
     *
     * <p>Eye to hitbox is what the server itself checks. Measuring centre to centre instead reads
     * about half a block long for everybody and flags the whole lobby.
     */
    public static double distanceToBox(double x, double y, double z,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        double dx = Math.max(minX - x, Math.max(0.0, x - maxX));
        double dy = Math.max(minY - y, Math.max(0.0, y - maxY));
        double dz = Math.max(minZ - z, Math.max(0.0, z - maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public synchronized void clear() {
        count = 0;
        head = 0;
    }
}
