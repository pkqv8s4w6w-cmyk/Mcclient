package dev.vantage.module.impl.player;

import dev.vantage.event.TickStartEvent;
import dev.vantage.mixin.accessor.PlayerControllerMPAccessor;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/**
 * Break blocks faster. A 1.8 server accepts a block as broken once it has seen 70% of the digging
 * time, so finishing at 70% is as fast as the server allows without the block popping back.
 * The five-tick pause between blocks is removed as well.
 */
public class FastBreakModule extends Module {

    private final NumberSetting finishAt = register(new NumberSetting(
            "Finish At", "Break once digging is this far along; the server needs at least 70%", 70.0, 70.0, 100.0, 5.0, "%"));

    public FastBreakModule() {
        super("FastBreak", Category.PLAYER, "Break blocks faster and without the pause between them");
        on(TickStartEvent.class, event -> {
            PlayerControllerMPAccessor controller =
                    (PlayerControllerMPAccessor) Minecraft.getMinecraft().playerController;
            controller.vantageSetBlockHitDelay(0);
            if (controller.vantageIsHittingBlock() && controller.vantageBlockDamage() >= finishAt.asFloat() / 100.0f) {
                controller.vantageSetBlockDamage(1.0f);
            }
        });
    }
}
