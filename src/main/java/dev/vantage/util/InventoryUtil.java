package dev.vantage.util;

import dev.vantage.mixin.accessor.PlayerControllerMPAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.potion.Potion;

import java.util.Collection;

/** Finding things in the hotbar and holding them for a moment without the player seeing. */
public final class InventoryUtil {

    private InventoryUtil() {
    }

    private static InventoryPlayer inventory() {
        return Minecraft.getMinecraft().thePlayer.inventory;
    }

    /**
     * Runs an action with another hotbar slot held, then switches straight back.
     *
     * <p>The server is told about both switches within the same tick, so the action happens with
     * the right item while the player's hand and hotbar never visibly change.
     */
    public static void withSlot(int slot, Runnable action) {
        Minecraft mc = Minecraft.getMinecraft();
        InventoryPlayer inventory = inventory();
        int previous = inventory.currentItem;
        if (slot < 0 || slot > 8 || slot == previous) {
            action.run();
            return;
        }
        PlayerControllerMPAccessor controller = (PlayerControllerMPAccessor) mc.playerController;
        inventory.currentItem = slot;
        controller.vantageSyncHeldItem();
        try {
            action.run();
        } finally {
            inventory.currentItem = previous;
            controller.vantageSyncHeldItem();
        }
    }

    /** Switches the held slot for real, visibly. */
    public static void select(int slot) {
        if (slot >= 0 && slot <= 8) {
            inventory().currentItem = slot;
            ((PlayerControllerMPAccessor) Minecraft.getMinecraft().playerController).vantageSyncHeldItem();
        }
    }

    /** Whether a block is one worth placing: solid, not gravity-bound, and not something you open. */
    public static boolean isPlaceable(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block.isFullCube() && block.getMaterial().isSolid()
                && !(block instanceof BlockFalling) && !(block instanceof BlockTNT)
                && !(block instanceof BlockContainer) && !(block instanceof BlockWorkbench);
    }

    /** The hotbar slot with the most placeable blocks, or -1. The held slot wins ties. */
    public static int blockSlot() {
        InventoryPlayer inventory = inventory();
        int best = -1;
        int bestCount = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!isPlaceable(stack)) {
                continue;
            }
            int count = stack.stackSize + (slot == inventory.currentItem ? 1 : 0);
            if (count > bestCount) {
                best = slot;
                bestCount = count;
            }
        }
        return best;
    }

    /** Total placeable blocks across the hotbar. */
    public static int blockCount() {
        int total = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory().getStackInSlot(slot);
            if (isPlaceable(stack)) {
                total += stack.stackSize;
            }
        }
        return total;
    }

    /** Attack damage an item adds, sharpness included. */
    public static double damageOf(ItemStack stack) {
        if (stack == null) {
            return 0.0;
        }
        double damage = 0.0;
        Collection<AttributeModifier> modifiers = stack.getAttributeModifiers()
                .get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName());
        for (AttributeModifier modifier : modifiers) {
            damage += modifier.getAmount();
        }
        return damage + EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) * 1.25;
    }

    /** The hotbar slot of the hardest-hitting sword, or -1 if there is none. */
    public static int swordSlot() {
        int best = -1;
        double bestDamage = -1.0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory().getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemSword) {
                double damage = damageOf(stack);
                if (damage > bestDamage) {
                    best = slot;
                    bestDamage = damage;
                }
            }
        }
        return best;
    }

    /**
     * How fast an item digs a block, as vanilla computes it for the player: tool strength with
     * Efficiency, then Haste. Does not include the underwater or airborne penalties.
     */
    public static float digSpeed(ItemStack stack, Block block) {
        float speed = stack == null ? 1.0f : stack.getStrVsBlock(block);
        if (speed > 1.0f && stack != null) {
            int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
            if (efficiency > 0) {
                speed += efficiency * efficiency + 1;
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && mc.thePlayer.isPotionActive(Potion.digSpeed)) {
            speed *= 1.0f + (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2f;
        }
        return speed;
    }

    /** The hotbar slot that breaks a block fastest, or the held slot if nothing beats a bare hand. */
    public static int toolSlotFor(Block block) {
        int best = inventory().currentItem;
        float bestSpeed = digSpeed(inventory().getStackInSlot(best), block);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory().getStackInSlot(slot);
            float speed = digSpeed(stack, block);
            if (speed > bestSpeed + 0.01f) {
                best = slot;
                bestSpeed = speed;
            }
        }
        return best;
    }

    /** Whether the held item is a weapon, for modules that should only act with one out. */
    public static boolean holdingWeapon() {
        ItemStack held = inventory().getCurrentItem();
        return held != null && (held.getItem() instanceof ItemSword || held.getItem() instanceof ItemTool);
    }

    public static int findHotbar(net.minecraft.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory().getStackInSlot(slot);
            if (stack != null && stack.getItem() == item) {
                return slot;
            }
        }
        return -1;
    }
}
