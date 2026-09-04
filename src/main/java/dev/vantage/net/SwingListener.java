package dev.vantage.net;

import dev.vantage.Vantage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S0BPacketAnimation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the exact arrival time of other players' arm swings.
 *
 * <p>Reads them off the network pipeline rather than from entity state, because entity state only
 * updates once a tick. Fifty millisecond resolution would round every click interval to a multiple
 * of a tick, which both invents consistency where there is none and hides it where there is - the
 * click timing analysis would be measuring the tick rate rather than the player.
 *
 * <p>This is a plain Netty handler added to the connection's pipeline, so it needs no bytecode
 * patching. Callbacks arrive on the network thread, hence the concurrent map.
 */
public final class SwingListener extends ChannelInboundHandlerAdapter {

    private static final String HANDLER_NAME = "vantage_swing_listener";
    private static final String INSERT_BEFORE = "packet_handler";

    /** Swing arm is animation type zero; the others are damage, eating and so on. */
    private static final int ANIMATION_SWING_ARM = 0;

    private final Map<Integer, Long> lastSwing = new ConcurrentHashMap<Integer, Long>();
    private volatile SwingSink sink;

    /** Where swings are delivered. Implemented by the detector. */
    public interface SwingSink {
        void onSwing(int entityId, long timestampMillis);
    }

    private static SwingListener installed;

    public static SwingListener instance() {
        if (installed == null) {
            installed = new SwingListener();
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
     * without needing to track connection state.
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
            // simply gets no click timing.
            Vantage.LOGGER.warn("Could not attach the swing listener; click timing is unavailable", failure);
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
                S0BPacketAnimation animation = (S0BPacketAnimation) message;
                if (animation.getAnimationType() == ANIMATION_SWING_ARM) {
                    long now = System.currentTimeMillis();
                    lastSwing.put(animation.getEntityID(), now);
                    SwingSink target = sink;
                    if (target != null) {
                        target.onSwing(animation.getEntityID(), now);
                    }
                }
            }
        } catch (Throwable failure) {
            // Never let analysis break the connection: the packet must still be passed along.
            Vantage.LOGGER.warn("Swing listener threw", failure);
        }
        super.channelRead(context, message);
    }

    public Long getLastSwing(int entityId) {
        return lastSwing.get(entityId);
    }

    public void clear() {
        lastSwing.clear();
    }
}
