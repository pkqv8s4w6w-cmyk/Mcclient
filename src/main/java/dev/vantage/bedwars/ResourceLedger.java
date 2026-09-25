package dev.vantage.bedwars;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An estimate of what each player can spend.
 *
 * <p>What a player picks up is seen exactly: the pickup packet names the collector and the item.
 * What they spend is only seen when it shows up on them - new armour, a better sword, a fireball in
 * hand - so spending is a lower bound and the balance is an upper bound. That is the useful
 * direction for the question it answers: "could they have a pearl right now?"
 *
 * <p>Minecraft-free; the module feeds it.
 */
public final class ResourceLedger {

    public enum Resource { IRON, GOLD, DIAMOND, EMERALD }

    /** Things worth knowing someone could buy, at default Bedwars shop prices. */
    public enum Purchase {
        FIREBALL("Fireball", Resource.IRON, 40),
        ENDER_PEARL("Pearl", Resource.EMERALD, 4),
        TNT("TNT", Resource.GOLD, 4),
        DIAMOND_ARMOUR("Diamond armour", Resource.EMERALD, 6),
        IRON_ARMOUR("Iron armour", Resource.GOLD, 12),
        CHAIN_ARMOUR("Chain armour", Resource.IRON, 24),
        DIAMOND_SWORD("Diamond sword", Resource.EMERALD, 4),
        IRON_SWORD("Iron sword", Resource.GOLD, 7),
        STONE_SWORD("Stone sword", Resource.IRON, 10),
        BOW("Bow", Resource.GOLD, 12),
        GOLDEN_APPLE("Golden apple", Resource.GOLD, 3),
        KNOCKBACK_STICK("Knockback stick", Resource.GOLD, 5),
        INVISIBILITY("Invisibility", Resource.EMERALD, 2),
        SPEED_POTION("Speed potion", Resource.EMERALD, 1),
        BRIDGE_EGG("Bridge egg", Resource.EMERALD, 1),
        WATER_BUCKET("Water bucket", Resource.GOLD, 3),
        SHEARS("Shears", Resource.IRON, 20),
        WOOL("Wool", Resource.IRON, 4);

        private final String label;
        private final Resource currency;
        private final int price;

        Purchase(String label, Resource currency, int price) {
            this.label = label;
            this.currency = currency;
            this.price = price;
        }

        public String getLabel() {
            return label;
        }

        public Resource getCurrency() {
            return currency;
        }

        public int getPrice() {
            return price;
        }
    }

    /** The purchases worth warning about, most dangerous first, for "can afford" hints. */
    public static final Purchase[] THREATS = {
            Purchase.ENDER_PEARL, Purchase.DIAMOND_ARMOUR, Purchase.FIREBALL, Purchase.TNT,
            Purchase.INVISIBILITY, Purchase.DIAMOND_SWORD, Purchase.KNOCKBACK_STICK, Purchase.BOW
    };

    private final Map<String, EnumMap<Resource, Integer>> collected = new HashMap<String, EnumMap<Resource, Integer>>();
    private final Map<String, EnumMap<Resource, Integer>> spent = new HashMap<String, EnumMap<Resource, Integer>>();

    private static EnumMap<Resource, Integer> row(Map<String, EnumMap<Resource, Integer>> table, String player) {
        EnumMap<Resource, Integer> row = table.get(player);
        if (row == null) {
            row = new EnumMap<Resource, Integer>(Resource.class);
            for (Resource resource : Resource.values()) {
                row.put(resource, 0);
            }
            table.put(player, row);
        }
        return row;
    }

    public synchronized void collect(String player, Resource resource, int amount) {
        EnumMap<Resource, Integer> row = row(collected, player);
        row.put(resource, row.get(resource) + Math.max(0, amount));
    }

    public synchronized void spend(String player, Purchase purchase, int times) {
        EnumMap<Resource, Integer> row = row(spent, player);
        row.put(purchase.currency, row.get(purchase.currency) + purchase.price * Math.max(0, times));
    }

    /** Collected minus seen spending, never below zero. */
    public synchronized int balance(String player, Resource resource) {
        int in = row(collected, player).get(resource);
        int out = row(spent, player).get(resource);
        return Math.max(0, in - out);
    }

    public synchronized int collectedTotal(String player, Resource resource) {
        return row(collected, player).get(resource);
    }

    /** Which of the threats this player could buy right now, most dangerous first. */
    public synchronized List<Purchase> affordableThreats(String player) {
        List<Purchase> affordable = new ArrayList<Purchase>();
        for (Purchase purchase : THREATS) {
            if (balance(player, purchase.currency) >= purchase.price) {
                affordable.add(purchase);
            }
        }
        return affordable;
    }

    public synchronized void clear() {
        collected.clear();
        spent.clear();
    }
}
