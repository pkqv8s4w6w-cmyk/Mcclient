package dev.vantage.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The cheapest way through a bed's defence.
 *
 * <p>The defence is treated as a grid where every cell costs the ticks it takes to break whatever
 * is in it - nothing for air, a lot for obsidian, forever for bedrock. Any cell on the edge of the
 * grid that is open to the outside is a way in; any cell touching the bed is a way to it. The
 * cheapest route between the two is the one to dig, and its non-empty cells are the blocks to
 * break, in order.
 *
 * <p>Minecraft-free: the caller fills the grid from the world, so the search is tested with hand
 * made defences.
 */
public final class BreachPath {

    /** A cell that cannot be broken at all. */
    public static final int UNBREAKABLE = Integer.MAX_VALUE;

    /** One step through the defence. */
    public static final class Step {
        public final int x;
        public final int y;
        public final int z;
        /** Ticks to break this cell; zero for open space. */
        public final int cost;

        Step(int x, int y, int z, int cost) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cost = cost;
        }
    }

    /** The result: the cells walked through, and the total ticks of digging. */
    public static final class Plan {
        public final List<Step> path;
        public final int totalTicks;

        Plan(List<Step> path, int totalTicks) {
            this.path = path;
            this.totalTicks = totalTicks;
        }

        /** Only the cells that need breaking, in the order they are reached. */
        public List<Step> blocksToBreak() {
            List<Step> blocks = new ArrayList<Step>();
            for (Step step : path) {
                if (step.cost > 0) {
                    blocks.add(step);
                }
            }
            return blocks;
        }
    }

    /** What occupies each cell. Coordinates are within {@code [0, size)} on each axis. */
    public interface Grid {
        int sizeX();

        int sizeY();

        int sizeZ();

        /** Ticks to break the cell, 0 if it is open, {@link #UNBREAKABLE} if it cannot be. */
        int cost(int x, int y, int z);

        /** Whether this cell is part of the bed itself. */
        boolean isBed(int x, int y, int z);
    }

    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private BreachPath() {
    }

    /**
     * Finds the cheapest route from the outside of the grid to a cell touching the bed.
     *
     * <p>Entry is allowed on every face except the bottom, since nobody digs up from underneath an
     * island. Cells are entered by breaking them, so a route's cost is the sum of what it passes
     * through; the bed's own cells are never part of a route.
     *
     * @return the plan, or null if the bed is sealed off completely
     */
    public static Plan plan(Grid grid) {
        int sx = grid.sizeX();
        int sy = grid.sizeY();
        int sz = grid.sizeZ();
        long[] best = new long[sx * sy * sz];
        int[] previous = new int[sx * sy * sz];
        java.util.Arrays.fill(best, Long.MAX_VALUE);
        java.util.Arrays.fill(previous, -1);
        PriorityQueue<long[]> queue = new PriorityQueue<long[]>((a, b) -> Long.compare(a[0], b[0]));

        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    boolean edge = x == 0 || x == sx - 1 || z == 0 || z == sz - 1 || y == sy - 1;
                    if (!edge || grid.isBed(x, y, z)) {
                        continue;
                    }
                    int cost = grid.cost(x, y, z);
                    if (cost == UNBREAKABLE) {
                        continue;
                    }
                    int index = index(x, y, z, sy, sz);
                    if (cost < best[index]) {
                        best[index] = cost;
                        queue.add(new long[]{cost, index});
                    }
                }
            }
        }

        int goal = -1;
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int index = (int) entry[1];
            if (entry[0] > best[index]) {
                continue;
            }
            int x = index / (sy * sz);
            int y = index / sz % sy;
            int z = index % sz;
            if (touchesBed(grid, x, y, z)) {
                goal = index;
                break;
            }
            for (int[] offset : NEIGHBOURS) {
                int nx = x + offset[0];
                int ny = y + offset[1];
                int nz = z + offset[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz || grid.isBed(nx, ny, nz)) {
                    continue;
                }
                int cost = grid.cost(nx, ny, nz);
                if (cost == UNBREAKABLE) {
                    continue;
                }
                long total = entry[0] + cost;
                int next = index(nx, ny, nz, sy, sz);
                if (total < best[next]) {
                    best[next] = total;
                    previous[next] = index;
                    queue.add(new long[]{total, next});
                }
            }
        }
        if (goal < 0) {
            return null;
        }

        List<Step> path = new ArrayList<Step>();
        for (int at = goal; at >= 0; at = previous[at]) {
            int x = at / (sy * sz);
            int y = at / sz % sy;
            int z = at % sz;
            path.add(new Step(x, y, z, grid.cost(x, y, z)));
        }
        Collections.reverse(path);
        return new Plan(path, (int) Math.min(Integer.MAX_VALUE, best[goal]));
    }

    private static boolean touchesBed(Grid grid, int x, int y, int z) {
        for (int[] offset : NEIGHBOURS) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            int nz = z + offset[2];
            if (nx >= 0 && ny >= 0 && nz >= 0 && nx < grid.sizeX() && ny < grid.sizeY() && nz < grid.sizeZ()
                    && grid.isBed(nx, ny, nz)) {
                return true;
            }
        }
        return false;
    }

    private static int index(int x, int y, int z, int sy, int sz) {
        return (x * sy + y) * sz + z;
    }

    /**
     * Ticks to break a block, from vanilla's digging formula: each tick adds
     * {@code speed / hardness / (harvestable ? 30 : 100)}, and the block goes at 1.0.
     *
     * @param hardness the block's hardness; negative means unbreakable
     * @param speed    the tool's dig speed against it, efficiency and haste included
     * @param harvestable whether the tool can harvest it
     */
    public static int breakTicks(float hardness, float speed, boolean harvestable) {
        if (hardness < 0.0f) {
            return UNBREAKABLE;
        }
        if (hardness == 0.0f) {
            return 1;
        }
        double perTick = speed / hardness / (harvestable ? 30.0 : 100.0);
        if (perTick >= 1.0) {
            return 1;
        }
        return (int) Math.ceil(1.0 / perTick);
    }

    /** A convenience grid over a plain array, for tests and for building from the world. */
    public static final class ArrayGrid implements Grid {
        private final int sx;
        private final int sy;
        private final int sz;
        private final int[] costs;
        private final boolean[] bed;
        private final Map<Integer, Object> tags = new HashMap<Integer, Object>();

        public ArrayGrid(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.costs = new int[sx * sy * sz];
            this.bed = new boolean[sx * sy * sz];
        }

        public void set(int x, int y, int z, int cost) {
            costs[index(x, y, z, sy, sz)] = cost;
        }

        public void setBed(int x, int y, int z) {
            bed[index(x, y, z, sy, sz)] = true;
        }

        /** Something to remember about a cell, such as which block is there. */
        public void tag(int x, int y, int z, Object tag) {
            tags.put(index(x, y, z, sy, sz), tag);
        }

        public Object tagAt(int x, int y, int z) {
            return tags.get(index(x, y, z, sy, sz));
        }

        @Override
        public int sizeX() {
            return sx;
        }

        @Override
        public int sizeY() {
            return sy;
        }

        @Override
        public int sizeZ() {
            return sz;
        }

        @Override
        public int cost(int x, int y, int z) {
            return costs[index(x, y, z, sy, sz)];
        }

        @Override
        public boolean isBed(int x, int y, int z) {
            return bed[index(x, y, z, sy, sz)];
        }
    }
}
