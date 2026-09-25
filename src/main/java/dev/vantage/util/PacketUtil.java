package dev.vantage.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;

/** Sending packets from modules, with or without other modules getting to see them. */
public final class PacketUtil {

    private static final ThreadLocal<Boolean> SILENT = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private PacketUtil() {
    }

    /** True while {@link #sendSilent} is sending, so the send hook lets the packet straight past. */
    public static boolean isSilent() {
        return SILENT.get();
    }

    /** Sends through the normal path, so other modules see and may cancel it. */
    public static void send(Packet<?> packet) {
        NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
        if (handler != null) {
            handler.addToSendQueue(packet);
        }
    }

    /**
     * Sends without posting a send event. For packets a module builds itself and must not have
     * cancelled or rewritten by another module - Blink releasing what it held, for instance.
     */
    public static void sendSilent(Packet<?> packet) {
        NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
        if (handler == null) {
            return;
        }
        SILENT.set(Boolean.TRUE);
        try {
            handler.getNetworkManager().sendPacket(packet);
        } finally {
            SILENT.set(Boolean.FALSE);
        }
    }
}
