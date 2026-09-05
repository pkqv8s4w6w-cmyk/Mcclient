package dev.vantage.net;

import dev.vantage.Vantage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Watches the connection for the events the detector needs and the game does not expose in time.
 *
 * <p>Swing timing has to come off the wire rather than from entity state, which only updates once
 * a tick. Fifty millisecond resolution would round every click interval to a multiple of a tick,
 * both inventing consistency where there is none and hiding it where there is, so the click
 * analysis would end up measuring the tick rate rather than the player.
 *
 * <p>Block placements come from here too, since nothing in the entity world says a block just
 * appeared.
 *
 * <p>A plain Netty handler on the connection's pipeline, so no bytecode patching is involved.
 * Callbacks arrive on the network thread, hence the concurrent collections, and a packet is always
 * passed along even if something here throws.
 */
public final class PacketObserver extends ChannelInboundHandlerAdapter {

    private static final String HANDLER_NAME = "vantage_packet_observer";
    private static final String INSERT_BEFORE = "packet_handler";

    /** Swing arm is animation type zero; the rest are damage, eating and so on. */
    private static final int ANIMATION_SWING_ARM = 0;

    /** Enough for a busy few ticks; older entries are dropped rather than growing without bound. */
    private static final int PLACEMENT_QUEUE_LIMIT = 256;

    /** A block that just appeared, and when. */
    public static final class Placement {
        public final BlockPos position;
        public final long timestampMillis;

        Placement(BlockPos position, long timestampMillis) {
            this.position = position;
            this.timestampMillis = timestampMillis;
        }
    }

    /** Where swings are delivered. Implemented by the detector. */
    public interface SwingSink {
        void onSwing(int entityId, long timestampMillis);
    }

    private final Map<Integer, Long> lastSwing = new ConcurrentHashMap<Integer, Long>();
    private final Queue<Placement> placements = new ConcurrentLinkedQueue<Placement>();
    private volatile SwingSink sink;

    private static PacketObserver installed;

    public static PacketObserver instance() {
        if (installed == null) {
            installed = new PacketObserver();
        }
        return installed;
    }

    public void setSink(SwingSink sink) {
        this.sink = sink;
    }

    /**
     * Adds the handler to the current connection if it is not already there.
     *
     * <p>Called every tick rather than hooked to a connect event, so it recovers from a reconnect
     * without tracking connection state.
     */
    public void attachIfNeeded() {
        NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
        if (handler == null) {
            return;
        }
        try {
            ChannelPipeline pipeline = handler.getNetworkManager().channel().pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                return;
            }
            pipeline.addBefore(INSERT_BEFORE, HANDLER_NAME, this);
        } catch (Throwable failure) {
            // A pipeline that does not look as expected is not worth crashing over; the detector
            // simply loses click timing and placements.
            Vantage.LOGGER.warn("Could not attach the packet observer; some checks are unavailable", failure);
        }
    }

    public void detach() {
        NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
        if (handler == null) {
            return;
        }
        try {
            ChannelPipeline pipeline = handler.getNetworkManager().channel().pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }
        } catch (Throwable ignored) {
            // Already gone, or the connection closed underneath us.
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        try {
            if (message instanceof S0BPacketAnimation) {
                readSwing((S0BPacketAnimation) message);
            } else if (message instanceof S23PacketBlockChange) {
                readBlockChange((S23PacketBlockChange) message);
            } else if (message instanceof S22PacketMultiBlockChange) {
                readMultiBlockChange((S22PacketMultiBlockChange) message);
            }
        } catch (Throwable failure) {
            // Never let analysis break the connection: the packet must still be passed along.
            Vantage.LOGGER.warn("Packet observer threw", failure);
        }
        super.channelRead(context, message);
    }

    private void readSwing(S0BPacketAnimation animation) {
        if (animation.getAnimationType() != ANIMATION_SWING_ARM) {
            return;
        }
        long now = System.currentTimeMillis();
        lastSwing.put(animation.getEntityID(), now);
        SwingSink target = sink;
        if (target != null) {
            target.onSwing(animation.getEntityID(), now);
        }
    }

    private void readBlockChange(S23PacketBlockChange packet) {
        // Only blocks appearing matter; a block being broken is not a placement.
        if (packet.getBlockState() != null && packet.getBlockState().getBlock() != Blocks.air) {
            offer(packet.getBlockPosition());
        }
    }

    private void readMultiBlockChange(S22PacketMultiBlockChange packet) {
        for (S22PacketMultiBlockChange.BlockUpdateData update : packet.getChangedBlocks()) {
            if (update.getBlockState() != null && update.getBlockState().getBlock() != Blocks.air) {
                offer(update.getPos());
            }
        }
    }

    private void offer(BlockPos position) {
        if (position == null) {
            return;
        }
        while (placements.size() >= PLACEMENT_QUEUE_LIMIT) {
            placements.poll();
        }
        placements.add(new Placement(position, System.currentTimeMillis()));
    }

    /** Takes everything queued since the last call. Drained once a tick by the detector. */
    public java.util.List<Placement> drainPlacements() {
        java.util.List<Placement> drained = new java.util.ArrayList<Placement>();
        Placement placement;
        while ((placement = placements.poll()) != null) {
            drained.add(placement);
        }
        return drained;
    }

    public Long getLastSwing(int entityId) {
        return lastSwing.get(entityId);
    }

    public void clear() {
        lastSwing.clear();
        placements.clear();
    }
}
