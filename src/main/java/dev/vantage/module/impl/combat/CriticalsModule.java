package dev.vantage.module.impl.combat;

import dev.vantage.event.AttackEvent;
import dev.vantage.event.Stage;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.EnumSetting;
import dev.vantage.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Makes grounded hits critical.
 *
 * <p>A 1.8 server scores a critical when the attacker is off the ground and has fallen any
 * distance at all. Packet mode reports a tiny hop and drop just before the attack - enough for the
 * server to count a fall, too small to move the player. Jump mode does it for real with a small
 * jump, which the attack then lands on the way down.
 */
public class CriticalsModule extends Module {

    public enum Mode { PACKET, JUMP }

    private static final double[] PACKET_OFFSETS = {0.0625, 0.0, 1.1E-5, 0.0};

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "Packet hops without moving; Jump does a small real hop", Mode.PACKET));

    public CriticalsModule() {
        super("Criticals", Category.COMBAT, "Every hit on the ground lands as a critical");
        markBlatant();
        on(AttackEvent.class, this::onAttack);
    }

    @Override
    public String getSuffix() {
        return mode.currentLabel();
    }

    private void onAttack(AttackEvent event) {
        if (event.getStage() != Stage.PRE || !(event.getTarget() instanceof EntityLivingBase)) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (!player.onGround || player.isInWater() || player.isInLava() || player.isOnLadder() || player.isRiding()) {
            return;
        }
        // Only worth doing when the hit can land; spamming offsets at an immune target is noise.
        EntityLivingBase target = (EntityLivingBase) event.getTarget();
        if (target.hurtResistantTime > target.maxHurtResistantTime / 2) {
            return;
        }
        if (mode.get() == Mode.PACKET) {
            for (double offset : PACKET_OFFSETS) {
                PacketUtil.sendSilent(new C03PacketPlayer.C04PacketPlayerPosition(
                        player.posX, player.posY + offset, player.posZ, false));
            }
        } else {
            player.motionY = 0.25;
        }
    }
}
