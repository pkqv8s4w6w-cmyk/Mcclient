package dev.vantage.module.impl.player;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;

/** Empties any chest you open into your inventory, one stack at a time. */
public class ChestStealerModule extends Module {

    private final NumberSetting delay = register(new NumberSetting(
            "Delay", "Time between stacks", 80.0, 0.0, 500.0, 10.0, "ms"));
    private final BooleanSetting autoClose = register(new BooleanSetting(
            "Auto Close", "Close the chest once it is empty", true));

    private long lastTake;

    public ChestStealerModule() {
        super("ChestStealer", Category.PLAYER, "Takes everything out of chests you open");
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiChest) || !(mc.thePlayer.openContainer instanceof ContainerChest)) {
            return;
        }
        ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;
        int chestSlots = chest.getLowerChestInventory().getSizeInventory();
        long now = System.currentTimeMillis();
        boolean empty = true;
        for (int index = 0; index < chestSlots; index++) {
            Slot slot = chest.getSlot(index);
            if (!slot.getHasStack()) {
                continue;
            }
            empty = false;
            if (now - lastTake < delay.asInt()) {
                return;
            }
            // Shift-click moves the stack straight into the player's inventory.
            mc.playerController.windowClick(chest.windowId, index, 0, 1, mc.thePlayer);
            lastTake = now;
            if (delay.asInt() > 0) {
                return;
            }
        }
        if (empty && autoClose.value() && now - lastTake > delay.asInt()) {
            mc.thePlayer.closeScreen();
        }
    }
}
