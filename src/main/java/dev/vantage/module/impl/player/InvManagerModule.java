package dev.vantage.module.impl.player;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/**
 * Keeps the hotbar laid out: best sword, blocks and bow in the slots you choose, and worse swords
 * thrown away. Works while your inventory is open, or all the time.
 */
public class InvManagerModule extends Module {

    private final NumberSetting swordSlot = register(new NumberSetting(
            "Sword Slot", "Hotbar slot for your best sword", 1.0, 1.0, 9.0, 1.0));
    private final NumberSetting blockSlot = register(new NumberSetting(
            "Block Slot", "Hotbar slot for your biggest stack of blocks", 9.0, 1.0, 9.0, 1.0));
    private final NumberSetting bowSlot = register(new NumberSetting(
            "Bow Slot", "Hotbar slot for a bow", 8.0, 1.0, 9.0, 1.0));
    private final BooleanSetting dropWorseSwords = register(new BooleanSetting(
            "Drop Worse Swords", "Throw away swords weaker than your best", false));
    private final BooleanSetting inventoryOnly = register(new BooleanSetting(
            "Inventory Only", "Only sort while your inventory is open", true));
    private final NumberSetting delay = register(new NumberSetting(
            "Delay", "Time between moves", 120.0, 0.0, 500.0, 10.0, "ms"));

    private long lastMove;

    public InvManagerModule() {
        super("InvManager", Category.PLAYER, "Sorts your hotbar and throws out junk");
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (inventoryOnly.value() ? !(mc.currentScreen instanceof GuiInventory) : mc.currentScreen != null
                && !(mc.currentScreen instanceof GuiInventory)) {
            return;
        }
        if (System.currentTimeMillis() - lastMove < delay.asInt()) {
            return;
        }
        if (placeBest(player, swordSlot.asInt() - 1, bestSwordIndex(player))
                || placeBest(player, blockSlot.asInt() - 1, bestBlockIndex(player))
                || placeBest(player, bowSlot.asInt() - 1, bowIndex(player))
                || (dropWorseSwords.value() && dropOneWorseSword(player))) {
            lastMove = System.currentTimeMillis();
        }
    }

    /**
     * Swaps an inventory item into a hotbar slot.
     *
     * @param inventoryIndex 0-35 in {@code InventoryPlayer} terms, or -1 for none
     * @return true if a move was made
     */
    private boolean placeBest(EntityPlayerSP player, int hotbarSlot, int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex == hotbarSlot) {
            return false;
        }
        Minecraft.getMinecraft().playerController.windowClick(player.inventoryContainer.windowId,
                containerSlot(inventoryIndex), hotbarSlot, 2, player);
        return true;
    }

    /** Inventory indices 0-8 are the hotbar at container slots 36-44; 9-35 map straight across. */
    private static int containerSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
    }

    private static int bestSwordIndex(EntityPlayerSP player) {
        int best = -1;
        double bestDamage = -1.0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemSword && InventoryUtil.damageOf(stack) > bestDamage) {
                best = i;
                bestDamage = InventoryUtil.damageOf(stack);
            }
        }
        return best;
    }

    private static int bestBlockIndex(EntityPlayerSP player) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (InventoryUtil.isPlaceable(stack) && stack.stackSize > bestCount) {
                best = i;
                bestCount = stack.stackSize;
            }
        }
        return best;
    }

    private int bowIndex(EntityPlayerSP player) {
        ItemStack inSlot = player.inventory.getStackInSlot(bowSlot.asInt() - 1);
        if (inSlot != null && inSlot.getItem() instanceof ItemBow) {
            return -1;
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBow) {
                return i;
            }
        }
        return -1;
    }

    private boolean dropOneWorseSword(EntityPlayerSP player) {
        int best = bestSwordIndex(player);
        if (best < 0) {
            return false;
        }
        double bestDamage = InventoryUtil.damageOf(player.inventory.getStackInSlot(best));
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (i != best && stack != null && stack.getItem() instanceof ItemSword
                    && InventoryUtil.damageOf(stack) < bestDamage) {
                // Mode 4 with button 1 throws the whole stack.
                Minecraft.getMinecraft().playerController.windowClick(player.inventoryContainer.windowId,
                        containerSlot(i), 1, 4, player);
                return true;
            }
        }
        return false;
    }
}
