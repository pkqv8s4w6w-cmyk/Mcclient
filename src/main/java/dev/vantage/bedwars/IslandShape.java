package dev.vantage.bedwars;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The outline of an island seen from above, found by walking out from the bed.
 *
 * <p>An island is a patch of solid columns joined together. Walking over every solid column would
 * also walk down each bridge built off the island, so the walk only passes through columns that
 * have solid ground on all four sides. No column of a one or two wide bridge does, so the walk
 * stops where each bridge begins. The island's own rim fails the same test, so it is added back
 * at the end, along with the first block of each bridge.
 *
 * <p>Minecraft-free: the caller says what each column holds, so the rules are tested on drawn maps.
 */
public final class IslandShape {

    /** What one column holds around the bed's height. */
    public enum Column { LAND, OPEN, UNLOADED }

    /** The world as columns. Coordinates are block coordinates. */
    public interface Terrain {
        Column at(int x, int z);
    }

    /** How far from the start the walk looks for solid ground to begin on. */
    private static final int START_SEARCH = 3;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final Set<Long> columns;
    private final List<int[]> rim = new ArrayList<int[]>();
    private final boolean complete;
    private List<int[]> outline;

    private IslandShape(Set<Long> columns, boolean complete) {
        this.columns = columns;
        this.complete = complete;
        for (long key : columns) {
            int x = (int) (key >> 32);
            int z = (int) key;
            for (int[] side : SIDES) {
                if (!columns.contains(key(x + side[0], z + side[1]))) {
                    rim.add(new int[]{x, z});
                    break;
                }
            }
        }
    }

    /**
     * Walks out from a column to find the island it stands on.
     *
     * @param maxRadius columns further than this from the start, on either axis, are ignored
     * @return the island, or null if there is no solid ground near the start
     */
    public static IslandShape detect(Terrain terrain, int startX, int startZ, int maxRadius) {
        Sampler sampler = new Sampler(terrain, startX, startZ, maxRadius);
        int[] start = firstCore(sampler, startX, startZ);
        if (start == null) {
            return null;
        }
        Set<Long> core = new HashSet<Long>();
        Deque<int[]> queue = new ArrayDeque<int[]>();
        core.add(key(start[0], start[1]));
        queue.add(start);
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] side : SIDES) {
                int x = at[0] + side[0];
                int z = at[1] + side[1];
                long key = key(x, z);
                if (!core.contains(key) && sampler.isCore(x, z)) {
                    core.add(key);
                    queue.add(new int[]{x, z});
                }
            }
        }
        // Diagonals too, or a square island would come back with its corners cut off.
        Set<Long> island = new HashSet<Long>(core);
        for (long key : core) {
            int x = (int) (key >> 32);
            int z = (int) key;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (sampler.isLand(x + dx, z + dz)) {
                        island.add(key(x + dx, z + dz));
                    }
                }
            }
        }
        return new IslandShape(island, !sampler.sawUnloaded);
    }

    /** A round island, used before the real one has been found. */
    public static IslandShape circle(double centreX, double centreZ, double radius) {
        Set<Long> columns = new HashSet<Long>();
        int reach = (int) Math.ceil(radius) + 1;
        int middleX = (int) Math.floor(centreX);
        int middleZ = (int) Math.floor(centreZ);
        for (int x = middleX - reach; x <= middleX + reach; x++) {
            for (int z = middleZ - reach; z <= middleZ + reach; z++) {
                double dx = x + 0.5 - centreX;
                double dz = z + 0.5 - centreZ;
                if (dx * dx + dz * dz <= radius * radius) {
                    columns.add(key(x, z));
                }
            }
        }
        return new IslandShape(columns, true);
    }

    private static int[] firstCore(Sampler sampler, int startX, int startZ) {
        for (int ring = 0; ring <= START_SEARCH; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && sampler.isCore(startX + dx, startZ + dz)) {
                        return new int[]{startX + dx, startZ + dz};
                    }
                }
            }
        }
        return null;
    }

    /**
     * False if part of the walk ran into unloaded chunks, so the island may be bigger than this.
     * Worth trying again once more of the world has arrived.
     */
    public boolean isComplete() {
        return complete;
    }

    /** How many columns the island covers. */
    public int size() {
        return columns.size();
    }

    public boolean contains(double x, double z) {
        return columns.contains(key((int) Math.floor(x), (int) Math.floor(z)));
    }

    /** Horizontal distance from a point to the nearest part of the island; 0 on it. */
    public double distanceTo(double x, double z) {
        if (contains(x, z)) {
            return 0.0;
        }
        double best = Double.MAX_VALUE;
        for (int[] column : rim) {
            double dx = Math.max(0.0, Math.max(column[0] - x, x - (column[0] + 1)));
            double dz = Math.max(0.0, Math.max(column[1] - z, z - (column[1] + 1)));
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(best);
    }

    /** The island's border as block-edge segments, each {x0, z0, x1, z1}, for drawing. */
    public List<int[]> outline() {
        if (outline == null) {
            outline = traceOutline();
        }
        return outline;
    }

    private List<int[]> traceOutline() {
        List<int[]> segments = new ArrayList<int[]>();
        for (int[] column : rim) {
            int x = column[0];
            int z = column[1];
            if (!columns.contains(key(x + 1, z))) {
                segments.add(new int[]{x + 1, z, x + 1, z + 1});
            }
            if (!columns.contains(key(x - 1, z))) {
                segments.add(new int[]{x, z, x, z + 1});
            }
            if (!columns.contains(key(x, z + 1))) {
                segments.add(new int[]{x, z + 1, x + 1, z + 1});
            }
            if (!columns.contains(key(x, z - 1))) {
                segments.add(new int[]{x, z, x + 1, z});
            }
        }
        return Collections.unmodifiableList(segments);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Asks the terrain about each column once, and remembers whether any were not loaded. */
    private static final class Sampler {
        private final Terrain terrain;
        private final int startX;
        private final int startZ;
        private final int maxRadius;
        private final Map<Long, Boolean> land = new HashMap<Long, Boolean>();
        private boolean sawUnloaded;

        Sampler(Terrain terrain, int startX, int startZ, int maxRadius) {
            this.terrain = terrain;
            this.startX = startX;
            this.startZ = startZ;
            this.maxRadius = maxRadius;
        }

        boolean isLand(int x, int z) {
            if (Math.abs(x - startX) > maxRadius || Math.abs(z - startZ) > maxRadius) {
                return false;
            }
            long key = key(x, z);
            Boolean known = land.get(key);
            if (known == null) {
                Column column = terrain.at(x, z);
                if (column == Column.UNLOADED) {
                    sawUnloaded = true;
                }
                known = column == Column.LAND;
                land.put(key, known);
            }
            return known;
        }

        boolean isCore(int x, int z) {
            if (!isLand(x, z)) {
                return false;
            }
            for (int[] side : SIDES) {
                if (!isLand(x + side[0], z + side[1])) {
                    return false;
                }
            }
            return true;
        }
    }
}
