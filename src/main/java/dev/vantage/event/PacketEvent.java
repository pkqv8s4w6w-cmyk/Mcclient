package dev.vantage.event;

import net.minecraft.network.Packet;

/**
 * A packet crossing the connection.
 *
 * <p>{@link Receive} is posted on the <b>network thread</b>, before the game has seen the packet.
 * Handlers may cancel it or edit its fields in place, but must not touch the world or the player
 * from there; anything that needs the game thread has to be scheduled onto it.
 *
 * <p>{@link Send} is posted on whichever thread sent the packet, which is almost always the client
 * thread.
 */
public abstract class PacketEvent extends Cancellable {

    private final Packet<?> packet;

    protected PacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public static final class Send extends PacketEvent {
        public Send(Packet<?> packet) {
            super(packet);
        }
    }

    public static final class Receive extends PacketEvent {
        public Receive(Packet<?> packet) {
            super(packet);
        }
    }
}
