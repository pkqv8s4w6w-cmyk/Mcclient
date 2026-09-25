package dev.vantage.module.impl.combat;

import dev.vantage.event.PacketEvent;
import dev.vantage.event.UpdateEvent;
import dev.vantage.mixin.accessor.S12PacketEntityVelocityAccessor;
import dev.vantage.mixin.accessor.S27PacketExplosionAccessor;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

/**
 * Takes less knockback.
 *
 * <p>Knockback is the server telling the client to move, so scaling that packet scales the push.
 * Jump Reset instead leaves the packet alone and jumps the moment the hit lands, which cuts
 * horizontal knockback by the same physics a well-timed real jump does.
 *
 * <p>The packet arrives on the network thread. It is edited there, before the game reads it; the
 * jump is handed to the client thread.
 */
public class VelocityModule extends Module {

    public enum Mode { PERCENT, CANCEL, JUMP_RESET }

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "Scale the push, drop it entirely, or jump into it", Mode.PERCENT));
    private final NumberSetting horizontal = register(new NumberSetting(
            "Horizontal", "How much sideways knockback to keep", 0.0, 0.0, 100.0, 1.0, "%"));
    private final NumberSetting vertical = register(new NumberSetting(
            "Vertical", "How much upward knockback to keep", 100.0, 0.0, 100.0, 1.0, "%"));
    private final BooleanSetting explosions = register(new BooleanSetting(
            "Explosions", "Also reduce knockback from TNT and fireballs", true));

    private volatile boolean jumpPending;

    public VelocityModule() {
        super("Velocity", Category.COMBAT, "Reduces the knockback you take");
        markBlatant();
        horizontal.visibleWhen(() -> mode.get() == Mode.PERCENT);
        vertical.visibleWhen(() -> mode.get() == Mode.PERCENT);
        on(PacketEvent.Receive.class, this::onPacket);
        on(UpdateEvent.class, event -> jumpIfDue());
    }

    @Override
    public String getSuffix() {
        return mode.get() == Mode.PERCENT ? horizontal.asInt() + "% " + vertical.asInt() + "%" : mode.currentLabel();
    }

    private void onPacket(PacketEvent.Receive event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() != player.getEntityId()) {
                return;
            }
            switch (mode.get()) {
                case CANCEL:
                    event.cancel();
                    break;
                case JUMP_RESET:
                    jumpPending = true;
                    break;
                default:
                    S12PacketEntityVelocityAccessor edit = (S12PacketEntityVelocityAccessor) packet;
                    edit.vantageSetMotionX((int) (packet.getMotionX() * horizontal.asDouble() / 100.0));
                    edit.vantageSetMotionY((int) (packet.getMotionY() * vertical.asDouble() / 100.0));
                    edit.vantageSetMotionZ((int) (packet.getMotionZ() * horizontal.asDouble() / 100.0));
            }
        } else if (event.getPacket() instanceof S27PacketExplosion && explosions.value()) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            S27PacketExplosionAccessor edit = (S27PacketExplosionAccessor) packet;
            double h = mode.get() == Mode.PERCENT ? horizontal.asDouble() / 100.0 : 0.0;
            double v = mode.get() == Mode.PERCENT ? vertical.asDouble() / 100.0 : 0.0;
            if (mode.get() == Mode.JUMP_RESET) {
                return;
            }
            edit.vantageSetMotionX((float) (packet.func_149149_c() * h));
            edit.vantageSetMotionY((float) (packet.func_149144_d() * v));
            edit.vantageSetMotionZ((float) (packet.func_149147_e() * h));
        }
    }

    private void jumpIfDue() {
        if (!jumpPending) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player.onGround && player.hurtTime > 0) {
            player.jump();
        }
        if (player.hurtTime == 0 || !player.onGround) {
            jumpPending = false;
        }
    }
}
