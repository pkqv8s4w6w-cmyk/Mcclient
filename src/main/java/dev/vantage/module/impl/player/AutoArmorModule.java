package dev.vantage.module.impl.player;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

/** Wears the best armour you are carrying. */
public class AutoArmorModule extends Module {

    private final NumberSetting delay = register(new NumberSetting(
            "Delay", "Time between pieces", 150.0, 0.0, 500.0, 10.0, "ms"));

    private long lastMove;

    public AutoArmorModule() {
        super("AutoArmor", Category.PLAYER, "Puts on the best armour you carry");
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiContainer && !(mc.currentScreen instanceof GuiInventory)) {
            return;
        }
        if (System.currentTimeMillis() - lastMove < delay.asInt()) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        // armorType 0 is the helmet; worn armour is stored in reverse, boots first.
        for (int type = 0; type < 4; type++) {
            ItemStack worn = player.inventory.armorInventory[3 - type];
            double wornValue = value(worn);
            int bestIndex = -1;
            double bestValue = wornValue;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack != null && stack.getItem() instanceof ItemArmor
                        && ((ItemArmor) stack.getItem()).armorType == type && value(stack) > bestValue) {
                    bestIndex = i;
                    bestValue = value(stack);
                }
            }
            if (bestIndex < 0) {
                continue;
            }
            int windowId = player.inventoryContainer.windowId;
            int armourSlot = 5 + type;
            if (worn != null) {
                // Shift-click the worn piece off first; it needs a free slot to go to.
                if (player.inventory.getFirstEmptyStack() < 0) {
                    continue;
                }
                mc.playerController.windowClick(windowId, armourSlot, 0, 1, player);
            }
            int from = bestIndex < 9 ? bestIndex + 36 : bestIndex;
            mc.playerController.windowClick(windowId, from, 0, 1, player);
            lastMove = System.currentTimeMillis();
            return;
        }
    }

    private static double value(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
            return -1.0;
        }
        ItemArmor armour = (ItemArmor) stack.getItem();
        int protection = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
        return armour.damageReduceAmount + protection * 0.75;
    }
}
