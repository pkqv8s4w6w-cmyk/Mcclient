package dev.vantage.module.impl.bedwars;

import dev.vantage.bedwars.ResourceLedger;
import dev.vantage.bedwars.ResourceLedger.Purchase;
import dev.vantage.bedwars.ResourceLedger.Resource;
import dev.vantage.event.PacketEvent;
import dev.vantage.game.TeamColour;
import dev.vantage.game.TeamResolver;
import dev.vantage.gui.Icons;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.module.Category;
import dev.vantage.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S0DPacketCollectItem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estimates what every enemy team can afford.
 *
 * <p>Pickups are exact: the server announces who collected which item. Spending is only what shows
 * up on a player - an armour upgrade, a better sword, more fireballs in hand than last time - so
 * the balance is an upper bound. That is the direction that matters: if the panel says a team
 * cannot afford a pearl, they cannot.
 */
public class EconomyTrackerModule extends HudModule {

    private final BooleanSetting teammates = register(new BooleanSetting(
            "Teammates", "Include your own team", false));

    private final ResourceLedger ledger = new ResourceLedger();
    private final Map<String, Integer> armourTier = new HashMap<String, Integer>();
    private final Map<String, Integer> swordTier = new HashMap<String, Integer>();
    private final Map<String, Map<Item, Integer>> heldCounts = new HashMap<String, Map<Item, Integer>>();
    private final Map<String, TeamColour> teamOf = new HashMap<String, TeamColour>();
    private int ticks;

    public EconomyTrackerModule() {
        super("Economy Tracker", Category.BEDWARS, "Estimates what each enemy team can afford");
        on(PacketEvent.Receive.class, this::onPacket);
    }

    @Override
    public void onWorldChanged() {
        ledger.clear();
        armourTier.clear();
        swordTier.clear();
        heldCounts.clear();
        teamOf.clear();
    }

    private void onPacket(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof S0DPacketCollectItem)) {
            return;
        }
        final S0DPacketCollectItem packet = (S0DPacketCollectItem) event.getPacket();
        // Queued ahead of the game's own handling of this packet, which removes the item entity.
        Minecraft.getMinecraft().addScheduledTask(() -> recordPickup(packet.getCollectedItemEntityID(), packet.getEntityID()));
    }

    private void recordPickup(int itemId, int collectorId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }
        Entity item = mc.theWorld.getEntityByID(itemId);
        Entity collector = mc.theWorld.getEntityByID(collectorId);
        if (!(item instanceof EntityItem) || !(collector instanceof EntityPlayer)) {
            return;
        }
        ItemStack stack = ((EntityItem) item).getEntityItem();
        Resource resource = stack == null ? null : resourceOf(stack.getItem());
        if (resource != null) {
            ledger.collect(collector.getName(), resource, stack.stackSize);
        }
    }

    static Resource resourceOf(Item item) {
        if (item == Items.iron_ingot) {
            return Resource.IRON;
        }
        if (item == Items.gold_ingot) {
            return Resource.GOLD;
        }
        if (item == Items.diamond) {
            return Resource.DIAMOND;
        }
        if (item == Items.emerald) {
            return Resource.EMERALD;
        }
        return null;
    }

    @Override
    public void onTick() {
        if (ticks++ % 5 != 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) {
                continue;
            }
            TeamColour team = TeamResolver.teamOf(player);
            if (team != TeamColour.UNKNOWN) {
                teamOf.put(player.getName(), team);
            }
            observeArmour(player);
            observeHeld(player);
        }
    }

    /** Bedwars armour upgrades change the leggings and boots, so read the leggings. */
    private void observeArmour(EntityPlayer player) {
        ItemStack leggings = player.inventory.armorInventory[1];
        if (leggings == null || !(leggings.getItem() instanceof ItemArmor)) {
            return;
        }
        ItemArmor.ArmorMaterial material = ((ItemArmor) leggings.getItem()).getArmorMaterial();
        int tier = material == ItemArmor.ArmorMaterial.DIAMOND ? 3
                : material == ItemArmor.ArmorMaterial.IRON ? 2
                : material == ItemArmor.ArmorMaterial.CHAIN ? 1 : 0;
        Integer previous = armourTier.get(player.getName());
        armourTier.put(player.getName(), tier);
        if (previous == null || tier <= previous) {
            return;
        }
        Purchase bought = tier == 3 ? Purchase.DIAMOND_ARMOUR : tier == 2 ? Purchase.IRON_ARMOUR : Purchase.CHAIN_ARMOUR;
        ledger.spend(player.getName(), bought, 1);
    }

    private void observeHeld(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held == null) {
            return;
        }
        Item item = held.getItem();
        String name = player.getName();
        if (item instanceof ItemSword) {
            int tier = item == Items.diamond_sword ? 3 : item == Items.iron_sword ? 2 : item == Items.stone_sword ? 1 : 0;
            Integer previous = swordTier.get(name);
            swordTier.put(name, Math.max(tier, previous == null ? 0 : previous));
            if (previous != null && tier > previous) {
                ledger.spend(name, tier == 3 ? Purchase.DIAMOND_SWORD : tier == 2 ? Purchase.IRON_SWORD : Purchase.STONE_SWORD, 1);
            }
            return;
        }
        Purchase purchase = purchaseFor(held);
        if (purchase == null) {
            return;
        }
        Map<Item, Integer> counts = heldCounts.get(name);
        if (counts == null) {
            counts = new HashMap<Item, Integer>();
            heldCounts.put(name, counts);
        }
        Integer previous = counts.get(item);
        int now = held.stackSize;
        counts.put(item, now);
        boolean oneOff = purchase == Purchase.BOW || purchase == Purchase.SHEARS || purchase == Purchase.KNOCKBACK_STICK;
        if (previous == null) {
            // First sight: at least what is in hand was bought.
            ledger.spend(name, purchase, oneOff ? 1 : now);
        } else if (!oneOff && now > previous) {
            ledger.spend(name, purchase, now - previous);
        }
    }

    private static Purchase purchaseFor(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.fire_charge) {
            return Purchase.FIREBALL;
        }
        if (item == Item.getItemFromBlock(Blocks.tnt)) {
            return Purchase.TNT;
        }
        if (item == Items.ender_pearl) {
            return Purchase.ENDER_PEARL;
        }
        if (item == Items.golden_apple) {
            return Purchase.GOLDEN_APPLE;
        }
        if (item == Items.bow) {
            return Purchase.BOW;
        }
        if (item == Items.egg) {
            return Purchase.BRIDGE_EGG;
        }
        if (item == Items.water_bucket) {
            return Purchase.WATER_BUCKET;
        }
        if (item == Items.shears) {
            return Purchase.SHEARS;
        }
        if (item == Items.stick && EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack) > 0) {
            return Purchase.KNOCKBACK_STICK;
        }
        return null;
    }

    // -- panel ------------------------------------------------------------------------------

    /** One row per team: the combined balance of the players seen on it. */
    private static final class TeamRow {
        final TeamColour team;
        final EnumMap<Resource, Integer> bank = new EnumMap<Resource, Integer>(Resource.class);
        final List<Purchase> affordable = new ArrayList<Purchase>();

        TeamRow(TeamColour team) {
            this.team = team;
            for (Resource resource : Resource.values()) {
                bank.put(resource, 0);
            }
        }
    }

    private List<TeamRow> rows() {
        TeamColour own = TeamResolver.ownTeam();
        Map<TeamColour, TeamRow> byTeam = new EnumMap<TeamColour, TeamRow>(TeamColour.class);
        Map<TeamColour, List<String>> members = new EnumMap<TeamColour, List<String>>(TeamColour.class);
        for (Map.Entry<String, TeamColour> entry : teamOf.entrySet()) {
            TeamColour team = entry.getValue();
            if (team == own && !teammates.value()) {
                continue;
            }
            TeamRow row = byTeam.get(team);
            if (row == null) {
                row = new TeamRow(team);
                byTeam.put(team, row);
                members.put(team, new ArrayList<String>());
            }
            members.get(team).add(entry.getKey());
            for (Resource resource : Resource.values()) {
                row.bank.put(resource, row.bank.get(resource) + ledger.balance(entry.getKey(), resource));
            }
        }
        List<TeamRow> list = new ArrayList<TeamRow>(byTeam.values());
        for (TeamRow row : list) {
            for (Purchase threat : ResourceLedger.THREATS) {
                if (row.bank.get(threat.getCurrency()) >= threat.getPrice()) {
                    row.affordable.add(threat);
                }
                if (row.affordable.size() == 2) {
                    break;
                }
            }
        }
        list.sort((a, b) -> Integer.compare(b.bank.get(Resource.EMERALD) * 30 + b.bank.get(Resource.DIAMOND) * 10
                + b.bank.get(Resource.GOLD) * 3 + b.bank.get(Resource.IRON),
                a.bank.get(Resource.EMERALD) * 30 + a.bank.get(Resource.DIAMOND) * 10
                        + a.bank.get(Resource.GOLD) * 3 + a.bank.get(Resource.IRON)));
        return list;
    }

    private static final int[] RESOURCE_COLOURS = {0xFFE8E8E8, 0xFFFFD34D, 0xFF5CE1E6, 0xFF4ADE80};
    private static final String[] RESOURCE_LETTERS = {"i", "g", "d", "e"};

    private static String affordText(TeamRow row) {
        if (row.affordable.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Purchase purchase : row.affordable) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(purchase.getLabel());
        }
        return text.toString();
    }

    @Override
    public float getContentWidth() {
        float widest = Fonts.SMALL_BOLD.getWidth("Economy") + 14.0f;
        for (TeamRow row : rows()) {
            widest = Math.max(widest, 58.0f + 4 * 22.0f + Fonts.TINY.getWidth(affordText(row)));
        }
        return Math.max(widest, 120.0f);
    }

    @Override
    public float getContentHeight() {
        return 12.0f + Math.max(1, rows().size()) * 10.0f;
    }

    @Override
    protected void renderContent() {
        Fonts.ICONS.drawString(String.valueOf(Icons.GAUGE), 0.0f, 0.5f, Theme.accent());
        Fonts.SMALL_BOLD.drawString("Economy", 12.0f, 0.0f, Theme.text());
        List<TeamRow> rows = rows();
        float y = 12.0f;
        if (rows.isEmpty()) {
            Fonts.SMALL.drawString("Nobody seen yet", 0.0f, y, Theme.textMuted());
            return;
        }
        for (TeamRow row : rows) {
            RenderUtil.circle(2.0f, y + 4.0f, 2.0f, row.team.getArgb());
            Fonts.SMALL.drawString(row.team.getDisplayName(), 7.0f, y, row.team.getArgb());
            float x = 52.0f;
            for (Resource resource : Resource.values()) {
                String amount = row.bank.get(resource) + RESOURCE_LETTERS[resource.ordinal()];
                Fonts.TINY.drawString(amount, x, y + 1.0f, RESOURCE_COLOURS[resource.ordinal()]);
                x += 22.0f;
            }
            Fonts.TINY.drawString(affordText(row), x + 4.0f, y + 1.0f, Theme.danger());
            y += 10.0f;
        }
    }
}
