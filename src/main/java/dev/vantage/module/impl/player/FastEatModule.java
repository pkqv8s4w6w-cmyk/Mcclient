package dev.vantage.module.impl.player;

import dev.vantage.event.UpdateEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Eat and drink faster. A 1.8 server runs a whole player update for every movement packet it gets,
 * item use included, so extra packets while eating finish the food in a fraction of the time.
 */
public class FastEatModule extends Module {

    private final NumberSetting packets = register(new NumberSetting(
            "Packets", "Extra updates sent per tick while eating", 8.0, 1.0, 32.0, 1.0));

    public FastEatModule() {
        super("FastEat", Category.PLAYER, "Eat and drink much faster");
        markBlatant();
        on(UpdateEvent.class, event -> {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            ItemStack using = player.getItemInUse();
            if (using == null || !player.onGround) {
                return;
            }
            EnumAction action = using.getItemUseAction();
            if (action != EnumAction.EAT && action != EnumAction.DRINK) {
                return;
            }
            for (int i = 0; i < packets.asInt(); i++) {
                PacketUtil.send(new C03PacketPlayer(true));
            }
        });
    }
}
