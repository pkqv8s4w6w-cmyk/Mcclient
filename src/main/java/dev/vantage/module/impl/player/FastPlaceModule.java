package dev.vantage.module.impl.player;

import dev.vantage.event.TickStartEvent;
import dev.vantage.mixin.accessor.MinecraftAccessor;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Place blocks as fast as you click. Vanilla waits four ticks between right clicks. */
public class FastPlaceModule extends Module {

    private final NumberSetting delay = register(new NumberSetting(
            "Delay", "Ticks between placements; vanilla is 4", 0.0, 0.0, 4.0, 1.0));
    private final BooleanSetting blocksOnly = register(new BooleanSetting(
            "Blocks Only", "Leave eating, bows and the rest at vanilla speed", true));

    public FastPlaceModule() {
        super("FastPlace", Category.PLAYER, "Place blocks without the vanilla delay");
        on(TickStartEvent.class, event -> {
            Minecraft mc = Minecraft.getMinecraft();
            ItemStack held = mc.thePlayer.getHeldItem();
            if (blocksOnly.value() && (held == null || !(held.getItem() instanceof ItemBlock))) {
                return;
            }
            MinecraftAccessor access = (MinecraftAccessor) mc;
            if (access.vantageRightClickDelay() > delay.asInt()) {
                access.vantageSetRightClickDelay(delay.asInt());
            }
        });
    }
}
