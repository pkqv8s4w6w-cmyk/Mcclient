package dev.vantage.game;

import dev.vantage.threat.Gear;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/**
 * Reads what another player is visibly carrying.
 *
 * <p>Other players' equipment reaches the client through entity equipment packets, so this is
 * ordinary public information rather than anything hidden.
 *
 * <p>Armour tier is taken as the best piece worn, not the chestplate. Bedwars armour upgrades only
 * replace boots and leggings - the chestplate and helmet stay leather for the whole game - so
 * reading the chest slot would report every player in the lobby as leather no matter what they
 * bought.
 */
public final class GearReader {

    /** Equipment slot indices used by the 1.8 entity equipment packet. */
    private static final int SLOT_HELD = 0;
    private static final int SLOT_BOOTS = 1;
    private static final int SLOT_HELMET = 4;

    private GearReader() {
    }

    public static Gear read(EntityPlayer player) {
        if (player == null) {
            return Gear.EMPTY;
        }

        Gear.Armour bestArmour = Gear.Armour.NONE;
        int bestProtection = 0;
        for (int slot = SLOT_BOOTS; slot <= SLOT_HELMET; slot++) {
            ItemStack piece = player.getEquipmentInSlot(slot);
            if (piece == null) {
                continue;
            }
            Gear.Armour tier = armourTier(piece);
            if (tier.ordinal() > bestArmour.ordinal()) {
                bestArmour = tier;
            }
            bestProtection = Math.max(bestProtection, protectionLevel(piece));
        }

        ItemStack held = player.getEquipmentInSlot(SLOT_HELD);
        return new Gear(bestArmour, bestProtection, weaponTier(held), sharpnessLevel(held));
    }

    private static Gear.Armour armourTier(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
            return Gear.Armour.NONE;
        }
        ItemArmor.ArmorMaterial material = ((ItemArmor) stack.getItem()).getArmorMaterial();
        switch (material) {
            case DIAMOND:
                return Gear.Armour.DIAMOND;
            case IRON:
                return Gear.Armour.IRON;
            case CHAIN:
                return Gear.Armour.CHAINMAIL;
            case GOLD:
                // Gold protects about as well as chain and is rare enough not to deserve its own
                // tier in the score.
                return Gear.Armour.CHAINMAIL;
            case LEATHER:
            default:
                return Gear.Armour.LEATHER;
        }
    }

    private static Gear.Weapon weaponTier(ItemStack stack) {
        if (stack == null) {
            return Gear.Weapon.NONE;
        }
        String material;
        if (stack.getItem() instanceof ItemSword) {
            material = ((ItemSword) stack.getItem()).getToolMaterialName();
        } else if (stack.getItem() instanceof ItemAxe) {
            // An axe is a real weapon in Bedwars, though it swings slower, so it is scored a tier
            // below the equivalent sword.
            material = ((ItemAxe) stack.getItem()).getToolMaterialName();
            return demote(fromMaterialName(material));
        } else {
            return Gear.Weapon.NONE;
        }
        return fromMaterialName(material);
    }

    private static Gear.Weapon fromMaterialName(String material) {
        if (material == null) {
            return Gear.Weapon.NONE;
        }
        if ("DIAMOND".equals(material)) {
            return Gear.Weapon.DIAMOND;
        }
        if ("IRON".equals(material) || "GOLD".equals(material)) {
            return Gear.Weapon.IRON;
        }
        if ("STONE".equals(material)) {
            return Gear.Weapon.STONE;
        }
        return Gear.Weapon.WOOD;
    }

    private static Gear.Weapon demote(Gear.Weapon weapon) {
        int lowered = Math.max(0, weapon.ordinal() - 1);
        return Gear.Weapon.values()[lowered];
    }

    private static int protectionLevel(ItemStack stack) {
        try {
            return EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
        } catch (Throwable unavailable) {
            return 0;
        }
    }

    private static int sharpnessLevel(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        try {
            return EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack);
        } catch (Throwable unavailable) {
            return 0;
        }
    }
}
