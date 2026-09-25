package dev.vantage.game;

import dev.vantage.mixin.accessor.ChunkProviderClientAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Finds every bed in the loaded world and works out whose it is, without knowing the plugin.
 *
 * <p>Beds are not tile entities in 1.8, so they have to be found in the block data. The scan reads
 * the raw storage arrays a few chunks per tick, which finds a bed across a whole map in a couple of
 * seconds for a negligible cost per frame.
 *
 * <p>Ownership comes mostly from colour. Players defend their bed with their own team's wool, and
 * most maps build each island in its team's colours, so the dyed wool, clay and glass around a bed
 * say whose it is. Who stands next to a bed only counts in the first seconds after the beds appear,
 * while everyone is still on their spawn island; after that a player beside a bed is as likely to
 * be breaking it as defending it. Your own bed is the one nearest you at that moment.
 */
public final class BedTracker {

    /** One bed, identified by its head half. */
    public static final class Bed {
        private final BlockPos head;
        private final BlockPos foot;
        private final Map<TeamColour, Double> votes = new EnumMap<TeamColour, Double>(TeamColour.class);
        private final Map<TeamColour, Double> colours = new EnumMap<TeamColour, Double>(TeamColour.class);
        private boolean own;

        Bed(BlockPos head, BlockPos foot) {
            this.head = head;
            this.foot = foot;
        }

        public BlockPos getHead() {
            return head;
        }

        public BlockPos getFoot() {
            return foot;
        }

        /** The centre point between the two halves, for distances. */
        public double centreX() {
            return (head.getX() + foot.getX()) / 2.0 + 0.5;
        }

        public double centreZ() {
            return (head.getZ() + foot.getZ()) / 2.0 + 0.5;
        }

        public double y() {
            return head.getY();
        }

        /**
         * Yours if your team has been seen living here, or failing that if it is where you spawned.
         * Team evidence wins because a waiting lobby inside the arena can put you by the wrong bed.
         */
        public boolean isOwn() {
            TeamColour ownTeam = TeamResolver.ownTeam();
            TeamColour owner = getOwner();
            if (ownTeam != TeamColour.UNKNOWN && owner != TeamColour.UNKNOWN) {
                return owner == ownTeam;
            }
            return own;
        }

        /**
         * The team this bed belongs to: the dominant colour around it, or failing that whoever was
         * standing by it when the game began. Unknown if neither says.
         */
        public TeamColour getOwner() {
            TeamColour byColour = strongest(colours, 3.0);
            return byColour != TeamColour.UNKNOWN ? byColour : strongest(votes, 1.0);
        }

        private static TeamColour strongest(Map<TeamColour, Double> tally, double minimum) {
            TeamColour best = TeamColour.UNKNOWN;
            double bestCount = minimum - 1.0e-9;
            for (Map.Entry<TeamColour, Double> entry : tally.entrySet()) {
                if (entry.getValue() > bestCount) {
                    best = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            return best;
        }

        public double horizontalDistanceTo(double x, double z) {
            double dx = centreX() - x;
            double dz = centreZ() - z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    private static final BedTracker INSTANCE = new BedTracker();

    /** How far from a bed a player counts as being on its island. */
    private static final double ISLAND_RADIUS = 18.0;
    private static final int CHUNKS_PER_TICK = 24;
    /** How long after beds appear that standing beside one says it is yours. */
    private static final int SPAWN_WINDOW_TICKS = 400;
    private static final int COLOUR_RADIUS = 4;

    public static BedTracker get() {
        return INSTANCE;
    }

    private final List<Bed> beds = new ArrayList<Bed>();
    private List<BedPositions> pendingScan = new ArrayList<BedPositions>();
    private List<Chunk> scanQueue = new ArrayList<Chunk>();
    private int scanIndex;
    private WorldClient world;
    private boolean ownAssigned;
    private int ticks;
    private int bedsFoundAt = -1;

    private BedTracker() {
    }

    /** The beds found so far. */
    public synchronized List<Bed> getBeds() {
        return Collections.unmodifiableList(new ArrayList<Bed>(beds));
    }

    public synchronized Bed getOwnBed() {
        for (Bed bed : beds) {
            if (bed.isOwn()) {
                return bed;
            }
        }
        return null;
    }

    /** Beds other than your own, nearest first. */
    public synchronized List<Bed> getEnemyBeds() {
        List<Bed> enemies = new ArrayList<Bed>();
        for (Bed bed : beds) {
            if (!bed.isOwn()) {
                enemies.add(bed);
            }
        }
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            Collections.sort(enemies, (a, b) -> Double.compare(
                    a.horizontalDistanceTo(mc.thePlayer.posX, mc.thePlayer.posZ),
                    b.horizontalDistanceTo(mc.thePlayer.posX, mc.thePlayer.posZ)));
        }
        return enemies;
    }

    /** The bed whose island a point is on, or null if it is in open space between islands. */
    public synchronized Bed bedNear(double x, double z, double radius) {
        Bed nearest = null;
        double nearestDistance = radius;
        for (Bed bed : beds) {
            double distance = bed.horizontalDistanceTo(x, z);
            if (distance <= nearestDistance) {
                nearest = bed;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** Called once a tick by the client. Cheap: a slice of the scan and a vote every second. */
    public synchronized void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            reset(null);
            return;
        }
        if (mc.theWorld != world) {
            reset(mc.theWorld);
        }
        ticks++;
        scanSlice(mc);
        dropBroken(mc);
        if (ticks % 20 == 0) {
            vote(mc);
        }
    }

    private void reset(WorldClient newWorld) {
        beds.clear();
        scanQueue = new ArrayList<Chunk>();
        pendingScan = new ArrayList<BedPositions>();
        scanIndex = 0;
        world = newWorld;
        ownAssigned = false;
        ticks = 0;
        bedsFoundAt = -1;
    }

    private void scanSlice(Minecraft mc) {
        if (scanIndex >= scanQueue.size()) {
            // Start the next pass over whatever is loaded now.
            ChunkProviderClient provider = (ChunkProviderClient) mc.theWorld.getChunkProvider();
            scanQueue = new ArrayList<Chunk>(((ChunkProviderClientAccessor) provider).vantageLoadedChunks());
            scanIndex = 0;
            if (!pendingScan.isEmpty()) {
                merge(pendingScan);
                pendingScan = new ArrayList<BedPositions>();
            }
        }
        int end = Math.min(scanQueue.size(), scanIndex + CHUNKS_PER_TICK);
        for (; scanIndex < end; scanIndex++) {
            scanChunk(scanQueue.get(scanIndex), pendingScan);
        }
    }

    /** Head and foot of a bed found in the raw data. */
    private static final class BedPositions {
        final BlockPos head;
        final BlockPos foot;

        BedPositions(BlockPos head, BlockPos foot) {
            this.head = head;
            this.foot = foot;
        }
    }

    private static void scanChunk(Chunk chunk, List<BedPositions> out) {
        if (chunk == null || !chunk.isLoaded()) {
            return;
        }
        int bedId = Block.getIdFromBlock(Blocks.bed);
        for (ExtendedBlockStorage storage : chunk.getBlockStorageArray()) {
            if (storage == null || storage.isEmpty()) {
                continue;
            }
            char[] data = storage.getData();
            for (int index = 0; index < data.length; index++) {
                char value = data[index];
                if (value >> 4 != bedId) {
                    continue;
                }
                // Chunk storage holds block id << 4 | meta, which is this map's key, not getStateById's.
                IBlockState state = Block.BLOCK_STATE_IDS.getByValue(value);
                if (state == null || state.getBlock() != Blocks.bed || state.getValue(BlockBed.PART) != BlockBed.EnumPartType.HEAD) {
                    continue;
                }
                int x = (chunk.xPosition << 4) + (index & 15);
                int y = storage.getYLocation() + (index >> 8 & 15);
                int z = (chunk.zPosition << 4) + (index >> 4 & 15);
                BlockPos head = new BlockPos(x, y, z);
                BlockPos foot = head.offset(state.getValue(BlockBed.FACING).getOpposite());
                out.add(new BedPositions(head, foot));
            }
        }
    }

    private void merge(List<BedPositions> found) {
        for (BedPositions positions : found) {
            boolean known = false;
            for (Bed bed : beds) {
                if (bed.head.equals(positions.head)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                beds.add(new Bed(positions.head, positions.foot));
            }
        }
    }

    /** A broken bed is gone the moment its block is, rather than on the next full pass. */
    private void dropBroken(Minecraft mc) {
        for (int i = beds.size() - 1; i >= 0; i--) {
            Bed bed = beds.get(i);
            if (mc.theWorld.isBlockLoaded(bed.head) && mc.theWorld.getBlockState(bed.head).getBlock() != Blocks.bed) {
                beds.remove(i);
            }
        }
    }

    private void vote(Minecraft mc) {
        if (beds.isEmpty()) {
            return;
        }
        if (bedsFoundAt < 0) {
            bedsFoundAt = ticks;
        }
        for (Bed bed : beds) {
            countColours(mc, bed);
        }
        if (ticks - bedsFoundAt > SPAWN_WINDOW_TICKS) {
            return;
        }
        if (!ownAssigned && mc.thePlayer.onGround) {
            Bed nearest = bedNear(mc.thePlayer.posX, mc.thePlayer.posZ, ISLAND_RADIUS);
            if (nearest != null) {
                nearest.own = true;
                ownAssigned = true;
            }
        }
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            TeamColour team = TeamResolver.teamOf(player);
            if (team == TeamColour.UNKNOWN) {
                continue;
            }
            Bed bed = bedNear(player.posX, player.posZ, ISLAND_RADIUS);
            if (bed == null) {
                continue;
            }
            double weight = player == mc.thePlayer ? 2.0 : 1.0;
            Double current = bed.votes.get(team);
            bed.votes.put(team, (current == null ? 0.0 : current) + weight);
        }
    }

    /** Tallies dyed blocks around a bed by the team colour they match. */
    private static void countColours(Minecraft mc, Bed bed) {
        bed.colours.clear();
        BlockPos head = bed.head;
        for (int x = -COLOUR_RADIUS; x <= COLOUR_RADIUS; x++) {
            for (int y = -1; y <= COLOUR_RADIUS; y++) {
                for (int z = -COLOUR_RADIUS; z <= COLOUR_RADIUS; z++) {
                    IBlockState state = mc.theWorld.getBlockState(head.add(x, y, z));
                    TeamColour colour = dyedTeam(state);
                    if (colour != TeamColour.UNKNOWN) {
                        Double count = bed.colours.get(colour);
                        bed.colours.put(colour, (count == null ? 0.0 : count) + 1.0);
                    }
                }
            }
        }
    }

    /** The team a dyed block's colour belongs to, or unknown for anything undyed. */
    static TeamColour dyedTeam(IBlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.wool && block != Blocks.stained_hardened_clay && block != Blocks.stained_glass
                && block != Blocks.stained_glass_pane && block != Blocks.carpet) {
            return TeamColour.UNKNOWN;
        }
        net.minecraft.item.EnumDyeColor dye = (net.minecraft.item.EnumDyeColor) state.getValue(
                block == Blocks.wool ? net.minecraft.block.BlockColored.COLOR
                        : block == Blocks.stained_hardened_clay ? net.minecraft.block.BlockColored.COLOR
                        : block == Blocks.carpet ? net.minecraft.block.BlockCarpet.COLOR
                        : block == Blocks.stained_glass ? net.minecraft.block.BlockStainedGlass.COLOR
                        : net.minecraft.block.BlockStainedGlassPane.COLOR);
        switch (dye) {
            case RED:
                return TeamColour.RED;
            case BLUE:
                return TeamColour.BLUE;
            case LIME:
            case GREEN:
                return TeamColour.GREEN;
            case YELLOW:
                return TeamColour.YELLOW;
            case LIGHT_BLUE:
            case CYAN:
                return TeamColour.AQUA;
            case WHITE:
                return TeamColour.WHITE;
            case PINK:
            case MAGENTA:
                return TeamColour.PINK;
            case GRAY:
            case SILVER:
                return TeamColour.GREY;
            default:
                return TeamColour.UNKNOWN;
        }
    }
}
