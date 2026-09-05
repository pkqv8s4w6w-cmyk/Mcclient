package dev.vantage.net;

import dev.vantage.Vantage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Blocks another player's movement packets outright for a window, pinning them where they were.
 *
 * <p>A second Netty handler alongside {@link PacketObserver}, installed the same way and for the
 * same reason - no bytecode patching, and a reconnect is recovered from by re-attaching rather than
 * by tracking connection state. Two handlers on one pipeline is ordinary Netty, and the order they
 * end up in does not matter here: the three packet types the observer reads are never among the
 * ones held, so they reach it at the same moment either way.
 *
 * <p><b>Held, not slowed.</b> Everything for an engaged target is withheld until their window
 * closes, then delivered in one catch-up burst. A running delay would only ever leave a target
 * trailing by a fixed time, which is worth about a block at sprint speed; pinning them in place
 * keeps them inside reach for as long as the window lasts, which is the point. It also means
 * distance can never be what ends a window - a pinned player's distance stops changing - so
 * {@link HoldWindow} ends it on a clock.
 *
 * <p>Each target gets its own queue. 1.8.9 movement is relative, so order matters within one
 * player's stream and not at all between two, and a shared queue would make one target's deadline
 * drag on another's.
 *
 * <p>Only three packet types are ever held, and only for entities the caller has listed:
 * {@code S14PacketEntity} and its relative-move subclasses, {@code S18PacketEntityTeleport}, and
 * {@code S19PacketEntityHeadLook}. Everything else passes straight through, untouched and unqueued.
 *
 * <p><b>Nothing addressed to the local player is ever held.</b> Checked here against the player's
 * own entity id rather than left to whoever builds the target list, because it is the one property
 * that must not depend on a caller getting something right. Other clients block all inbound traffic
 * and so delay their own knockback, which is what their "disable on hit" setting exists to paper
 * over; holding three packet types for named entities cannot do that in the first place.
 *
 * <p>All queue and window work happens on the channel's event loop.
 */
public final class PacketDelayer extends ChannelInboundHandlerAdapter {

    private static final String HANDLER_NAME = "vantage_packet_delayer";
    private static final String INSERT_BEFORE = "packet_handler";

    /** Returned when a packet is not one of the delayable kinds, or its id could not be read. */
    private static final int NO_ENTITY = Integer.MIN_VALUE;

    /** Generous for one player's movement over a window; overflow releases rather than drops. */
    private static final int PER_TARGET_CAPACITY = 256;

    /** How long after the last hold an entity still counts as distorted. See {@link #isDistorting}. */
    private static final long MINIMUM_DISTORTION_GRACE_MILLIS = 1000L;

    /**
     * Neither of these two packets exposes its entity id, and the project carries no access
     * transformer or Mixin, so the fields are read reflectively. Both names are supplied because a
     * mod compiled against MCP names runs against SRG ones: {@code entityId} is what the field is
     * called in the development environment, {@code field_149074_a} what it is called in game.
     */
    private static final Field ENTITY_MOVE_ID =
            resolve(S14PacketEntity.class, "field_149074_a", "entityId");
    private static final Field HEAD_LOOK_ID =
            resolve(S19PacketEntityHeadLook.class, "field_149384_a", "entityId");

    private static PacketDelayer installed;

    /** One queue per target. Event loop only. */
    private final Map<Integer, HeldPacketQueue> queues = new HashMap<Integer, HeldPacketQueue>();

    /** Engagement and cooldown state. Event loop only. */
    private final HoldWindow window = new HoldWindow();

    /** When each entity last had a packet held. Read from the client thread, hence concurrent. */
    private final Map<Integer, Long> lastHeldAt = new ConcurrentHashMap<Integer, Long>();

    private volatile Set<Integer> eligible = Collections.emptySet();
    private volatile long maxDelayMillis;
    private volatile long cooldownMillis;
    private volatile boolean active;
    private volatile int localEntityId = NO_ENTITY;
    private volatile ChannelHandlerContext context;

    /** Only touched on the event loop, so one release task is ever in flight. */
    private boolean releaseScheduled;

    public static PacketDelayer instance() {
        if (installed == null) {
            installed = new PacketDelayer();
        }
        return installed;
    }

    /**
     * Whether the entity ids can be read at all.
     *
     * <p>False means the two reflective lookups failed, which would leave relative movement passing
     * straight through - the module would appear to run while doing nothing at all. Callers should
     * refuse to enable rather than pretend.
     */
    public static boolean isUsable() {
        return ENTITY_MOVE_ID != null && HEAD_LOOK_ID != null;
    }

    // -- configuration, called from the client thread ----------------------------------------

    /**
     * The targets currently worth holding - already filtered for distance and hurt time by the
     * caller. Whether one is actually held also depends on their window and cooldown, which are
     * decided here.
     */
    public void setEligible(Set<Integer> newEligible) {
        this.eligible = newEligible == null || newEligible.isEmpty()
                ? Collections.<Integer>emptySet()
                : Collections.unmodifiableSet(newEligible);
    }

    public void setMaxDelayMillis(long millis) {
        this.maxDelayMillis = Math.max(0L, millis);
    }

    public void setCooldownMillis(long millis) {
        this.cooldownMillis = Math.max(0L, millis);
    }

    /** The local player's entity id, so their own packets can be excluded outright. */
    public void setLocalEntityId(int entityId) {
        this.localEntityId = entityId;
    }

    /** Nothing is held while this is false, whatever the eligible set says. */
    public void setActive(boolean value) {
        this.active = value;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Whether this entity's movement is currently being distorted.
     *
     * <p>True through both halves of the effect - the stall while packets are held, and the catch-up
     * burst when the window closes - because anything differencing consecutive positions has to
     * discard both. The grace period runs past the last hold for that reason; the catch-up happens
     * after holding has already stopped.
     */
    public boolean isDistorting(int entityId) {
        Long at = lastHeldAt.get(Integer.valueOf(entityId));
        if (at == null) {
            return false;
        }
        long grace = Math.max(MINIMUM_DISTORTION_GRACE_MILLIS, maxDelayMillis + 500L);
        return System.currentTimeMillis() - at.longValue() < grace;
    }

    // -- pipeline ----------------------------------------------------------------------------

    /** Adds the handler to the current connection if it is not already there. */
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
            Vantage.LOGGER.warn("Could not attach the packet delayer; Backtrack will do nothing", failure);
        }
    }

    /** Stops holding, hands back everything queued, then leaves the pipeline. */
    public void detach() {
        active = false;
        final ChannelHandlerContext ctx = context;
        if (ctx != null) {
            onEventLoop(ctx, new Runnable() {
                @Override
                public void run() {
                    releaseEverything(ctx);
                    removeFrom(ctx.pipeline());
                    context = null;
                    // A pending task would clear this itself, but only if the event loop lives long
                    // enough to run it. Left set, nothing would ever schedule again on reconnect.
                    releaseScheduled = false;
                }
            });
            return;
        }
        // Never attached, or the connection went away underneath us.
        discardEverything();
        NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
        if (handler != null) {
            try {
                removeFrom(handler.getNetworkManager().channel().pipeline());
            } catch (Throwable ignored) {
                // Already gone.
            }
        }
    }

    /**
     * Closes every open window and hands back what was held.
     *
     * <p>The effect ending has to look like the connection catching up, not like packets going
     * missing - a dropped relative move would leave that player permanently offset.
     */
    public void flushAll() {
        final ChannelHandlerContext ctx = context;
        if (ctx == null) {
            discardEverything();
            return;
        }
        onEventLoop(ctx, new Runnable() {
            @Override
            public void run() {
                releaseEverything(ctx);
            }
        });
    }

    /**
     * Ends windows that have run out or whose target stopped being worth holding.
     *
     * <p>Called once a tick. A pinned player sends nothing further, so without this their window
     * would only close when the next packet happened to arrive - which, for someone standing still
     * behind a wall, could be a long time.
     */
    public void tick() {
        final ChannelHandlerContext ctx = context;
        if (ctx == null) {
            return;
        }
        onEventLoop(ctx, new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Set<Integer> live = eligible;
                for (Integer id : window.engaged()) {
                    boolean lapsed = window.remaining(id.intValue(), now) <= 0L;
                    if (lapsed || !active || !live.contains(id)) {
                        endWindow(ctx, id.intValue(), now);
                    }
                }
                window.pruneCooldowns(now);
            }
        });
    }

    /** Throws away queued packets and per-entity state. Only for a world that has already gone. */
    public void clear() {
        discardEverything();
        lastHeldAt.clear();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.context = ctx;
        super.handlerAdded(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // The connection is gone; there is nothing left to deliver these to.
        discardEverything();
        lastHeldAt.clear();
        this.context = null;
        releaseScheduled = false;
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        try {
            if (hold(ctx, message)) {
                return;
            }
        } catch (Throwable failure) {
            // Never let this break the connection: fall through and pass the packet along.
            Vantage.LOGGER.warn("Packet delayer threw; passing the packet straight through", failure);
        }
        super.channelRead(ctx, message);
    }

    /** @return true when the packet was queued, and so must not be passed on yet */
    private boolean hold(ChannelHandlerContext ctx, Object message) {
        int entityId = delayableEntityId(message);
        if (entityId == NO_ENTITY || entityId == localEntityId) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (!active || !eligible.contains(Integer.valueOf(entityId))) {
            // No longer worth holding: let them catch up before this packet goes through.
            if (window.isEngaged(entityId)) {
                endWindow(ctx, entityId, now);
            }
            return false;
        }
        if (window.isCoolingDown(entityId, now)) {
            return false;
        }
        if (!window.beginOrContinue(entityId, now, maxDelayMillis)) {
            // The window just ran out on this packet's arrival.
            endWindow(ctx, entityId, now);
            return false;
        }

        // Every packet in one window releases when the window closes, not one delay each - that is
        // what pins the target rather than trailing them.
        long remaining = window.remaining(entityId, now);
        lastHeldAt.put(Integer.valueOf(entityId), Long.valueOf(now));
        HeldPacketQueue queue = queueFor(entityId);
        release(ctx, queue.hold(message, entityId, now, remaining));
        ensureReleaseScheduled(ctx);
        return true;
    }

    private HeldPacketQueue queueFor(int entityId) {
        Integer key = Integer.valueOf(entityId);
        HeldPacketQueue queue = queues.get(key);
        if (queue == null) {
            queue = new HeldPacketQueue(PER_TARGET_CAPACITY);
            queues.put(key, queue);
        }
        return queue;
    }

    /** Ends one target's window, delivers what was held, and starts their cooldown. */
    private void endWindow(ChannelHandlerContext ctx, int entityId, long now) {
        HeldPacketQueue queue = queues.remove(Integer.valueOf(entityId));
        if (queue != null) {
            release(ctx, queue.drainAll());
        }
        window.end(entityId, now, cooldownMillis);
    }

    private void releaseEverything(ChannelHandlerContext ctx) {
        long now = System.currentTimeMillis();
        for (Integer id : window.engaged()) {
            endWindow(ctx, id.intValue(), now);
        }
        // Anything queued without an open window, which should not happen, still goes out.
        for (HeldPacketQueue queue : queues.values()) {
            release(ctx, queue.drainAll());
        }
        queues.clear();
    }

    private void discardEverything() {
        for (HeldPacketQueue queue : queues.values()) {
            queue.discardAll();
        }
        queues.clear();
        window.clear();
    }

    // -- releasing, all on the event loop -----------------------------------------------------

    private void ensureReleaseScheduled(final ChannelHandlerContext ctx) {
        if (releaseScheduled) {
            return;
        }
        Long due = earliestRelease();
        if (due == null) {
            return;
        }
        long wait = Math.max(0L, due.longValue() - System.currentTimeMillis());
        releaseScheduled = true;
        try {
            ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    releaseScheduled = false;
                    long now = System.currentTimeMillis();
                    for (Integer id : window.expired(now)) {
                        endWindow(ctx, id.intValue(), now);
                    }
                    // Release times can be pushed back to preserve order, so a window's packets are
                    // not always due when its task fires. Chaining from the earliest still queued
                    // is what stops a straggler sitting there.
                    for (Map.Entry<Integer, HeldPacketQueue> entry : queues.entrySet()) {
                        release(ctx, entry.getValue().drainDue(now));
                    }
                    ensureReleaseScheduled(ctx);
                }
            }, wait, TimeUnit.MILLISECONDS);
        } catch (Throwable failure) {
            releaseScheduled = false;
            // No way to schedule means no way to ever deliver these; hand them over now instead.
            Vantage.LOGGER.warn("Could not schedule a packet release; delivering everything now", failure);
            releaseEverything(ctx);
        }
    }

    private Long earliestRelease() {
        Long earliest = null;
        for (HeldPacketQueue queue : queues.values()) {
            Long due = queue.nextReleaseAtMillis();
            if (due != null && (earliest == null || due.longValue() < earliest.longValue())) {
                earliest = due;
            }
        }
        return earliest;
    }

    private void release(ChannelHandlerContext ctx, List<HeldPacketQueue.Held> held) {
        if (held.isEmpty()) {
            return;
        }
        if (!ctx.channel().isActive()) {
            return;
        }
        for (int i = 0; i < held.size(); i++) {
            try {
                ctx.fireChannelRead(held.get(i).packet);
            } catch (Throwable failure) {
                Vantage.LOGGER.warn("Could not deliver a delayed packet", failure);
            }
        }
    }

    private static void onEventLoop(ChannelHandlerContext ctx, Runnable task) {
        try {
            if (ctx.executor().inEventLoop()) {
                task.run();
            } else {
                ctx.executor().execute(task);
            }
        } catch (Throwable failure) {
            Vantage.LOGGER.warn("Could not reach the network thread", failure);
        }
    }

    private static void removeFrom(ChannelPipeline pipeline) {
        try {
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }
        } catch (Throwable ignored) {
            // Already removed, or the pipeline is being torn down.
        }
    }

    // -- packet inspection ---------------------------------------------------------------------

    /**
     * @return the entity a delayable packet belongs to, or {@link #NO_ENTITY} for everything else
     */
    private static int delayableEntityId(Object message) {
        // S15PacketEntityRelMove, S16PacketEntityLook and S17PacketEntityLookMove are all nested
        // subclasses of S14PacketEntity in 1.8.9, so this covers relative movement entirely.
        if (message instanceof S14PacketEntity) {
            return readInt(ENTITY_MOVE_ID, message);
        }
        if (message instanceof S18PacketEntityTeleport) {
            return ((S18PacketEntityTeleport) message).getEntityId();
        }
        if (message instanceof S19PacketEntityHeadLook) {
            return readInt(HEAD_LOOK_ID, message);
        }
        return NO_ENTITY;
    }

    private static Field resolve(Class<?> owner, String... names) {
        try {
            Field field = ReflectionHelper.findField(owner, names);
            field.setAccessible(true);
            return field;
        } catch (Throwable failure) {
            Vantage.LOGGER.error("Could not find the entity id field on {}; Backtrack is unavailable",
                    owner.getSimpleName(), failure);
            return null;
        }
    }

    private static int readInt(Field field, Object owner) {
        if (field == null) {
            return NO_ENTITY;
        }
        try {
            return field.getInt(owner);
        } catch (Throwable failure) {
            return NO_ENTITY;
        }
    }
}
