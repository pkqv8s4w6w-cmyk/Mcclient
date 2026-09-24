package dev.vantage.mixin;

import dev.vantage.event.EventBus;
import dev.vantage.event.PacketEvent;
import dev.vantage.util.PacketUtil;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Posts every packet on the client's own connection to the event bus.
 *
 * <p>In singleplayer the integrated server has network managers of its own, and they run this same
 * code. Only the connection the local player is using is reported, so no module ever sees the
 * server side of a local game.
 */
@Mixin(NetworkManager.class)
public abstract class MixinNetworkManager {

    private boolean vantage$isClientConnection() {
        NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
        return handler != null && handler.getNetworkManager() == (Object) this;
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void vantage$onSend(Packet<?> packet, CallbackInfo callback) {
        if (PacketUtil.isSilent() || !EventBus.global().hasListeners(PacketEvent.Send.class)
                || !vantage$isClientConnection()) {
            return;
        }
        if (EventBus.global().post(new PacketEvent.Send(packet)).isCancelled()) {
            callback.cancel();
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void vantage$onReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
        if (!EventBus.global().hasListeners(PacketEvent.Receive.class) || !vantage$isClientConnection()) {
            return;
        }
        if (EventBus.global().post(new PacketEvent.Receive(packet)).isCancelled()) {
            callback.cancel();
        }
    }
}
