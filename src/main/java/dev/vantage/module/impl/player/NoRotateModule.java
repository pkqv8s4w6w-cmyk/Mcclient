package dev.vantage.module.impl.player;

import dev.vantage.event.PacketEvent;
import dev.vantage.mixin.accessor.S08PacketPlayerPosLookAccessor;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

/** When the server moves you, your camera stays pointed where it was. */
public class NoRotateModule extends Module {

    public NoRotateModule() {
        super("NoRotate", Category.PLAYER, "The server can move you but not turn your camera");
        on(PacketEvent.Receive.class, event -> {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player == null || !(event.getPacket() instanceof S08PacketPlayerPosLook)) {
                return;
            }
            S08PacketPlayerPosLook packet = (S08PacketPlayerPosLook) event.getPacket();
            S08PacketPlayerPosLookAccessor edit = (S08PacketPlayerPosLookAccessor) packet;
            // A relative rotation is an offset; zero leaves the camera as it is. An absolute one
            // is replaced with where the camera already points.
            boolean relativeYaw = packet.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Y_ROT);
            boolean relativePitch = packet.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.X_ROT);
            edit.vantageSetYaw(relativeYaw ? 0.0f : player.rotationYaw);
            edit.vantageSetPitch(relativePitch ? 0.0f : player.rotationPitch);
        });
    }
}
