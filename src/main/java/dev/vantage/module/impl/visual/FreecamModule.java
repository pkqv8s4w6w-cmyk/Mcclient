package dev.vantage.module.impl.visual;

import dev.vantage.event.CameraEvent;
import dev.vantage.event.MoveEvent;
import dev.vantage.event.PacketEvent;
import dev.vantage.event.PushOutEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;

/**
 * Leaves your body where it is and flies the camera around freely, through walls. A copy of you
 * stands where you really are; the server keeps seeing you there, since no movement is sent until
 * you switch back.
 */
public class FreecamModule extends Module {

    private final NumberSetting speed = register(new NumberSetting(
            "Speed", "Flying speed in blocks per tick", 1.0, 0.1, 4.0, 0.1));

    private EntityOtherPlayerMP body;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public FreecamModule() {
        super("Freecam", Category.VISUAL, "Fly the camera around while your body stays put");
        forgetEnabledOnSave();
        on(PacketEvent.Send.class, event -> {
            if (event.getPacket() instanceof C03PacketPlayer || event.getPacket() instanceof C0BPacketEntityAction) {
                event.cancel();
            }
        });
        on(MoveEvent.class, this::onMove);
        on(PushOutEvent.class, PushOutEvent::cancel);
        on(CameraEvent.class, event -> event.setFreeCamera(true));
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            setEnabledSilently(false);
            return;
        }
        x = player.posX;
        y = player.posY;
        z = player.posZ;
        yaw = player.rotationYaw;
        pitch = player.rotationPitch;
        body = new EntityOtherPlayerMP(mc.theWorld, player.getGameProfile());
        body.copyLocationAndAnglesFrom(player);
        body.rotationYawHead = player.rotationYawHead;
        body.inventory.copyInventory(player.inventory);
        mc.theWorld.addEntityToWorld(-420, body);
        player.noClip = true;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (body != null && mc.theWorld != null) {
            mc.theWorld.removeEntityFromWorld(-420);
        }
        body = null;
        if (player != null) {
            player.noClip = false;
            player.setPositionAndRotation(x, y, z, yaw, pitch);
            player.motionX = 0.0;
            player.motionY = 0.0;
            player.motionZ = 0.0;
        }
    }

    @Override
    public void onWorldChanged() {
        // A new world means the old body is gone and the saved position is meaningless.
        if (isEnabled()) {
            body = null;
            setEnabled(false);
        }
    }

    private void onMove(MoveEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        player.noClip = true;
        player.onGround = false;
        double vertical = 0.0;
        if (player.movementInput.jump) {
            vertical = speed.asDouble();
        } else if (player.movementInput.sneak) {
            vertical = -speed.asDouble();
        }
        player.motionY = vertical;
        event.setY(vertical);
        MovementUtil.setSpeed(event, speed.asDouble());
    }
}
