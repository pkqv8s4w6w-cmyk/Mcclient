package dev.vantage.module.impl.combat;

import dev.vantage.event.AttackEvent;
import dev.vantage.event.Stage;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;

/**
 * Gives every hit sprint knockback.
 *
 * <p>The server stops your sprint the moment a sprinting hit lands, and only a fresh sprint start
 * earns the bonus knockback on the next one. Players tap W to restart it; this tells the server
 * the sprint restarted straight after each hit, without the tap.
 */
public class WTapModule extends Module {

    public WTapModule() {
        super("WTap", Category.COMBAT, "Every hit gets sprint knockback");
        on(AttackEvent.class, this::onAttack);
    }

    private void onAttack(AttackEvent event) {
        if (event.getStage() != Stage.POST || !(event.getTarget() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (!player.isSprinting() && player.moveForward <= 0.0f) {
            return;
        }
        PacketUtil.send(new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SPRINTING));
        PacketUtil.send(new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SPRINTING));
        player.setSprinting(true);
    }
}
