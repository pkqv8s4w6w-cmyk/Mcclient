package dev.vantage.game;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;

/**
 * Works out which team a player is on, without knowing which Bedwars plugin the server runs.
 *
 * <p>Three sources, in order of trust:
 * <ol>
 *   <li>The scoreboard team's prefix. Every mainstream Bedwars plugin puts players in a team to
 *       colour their nametag, so this is right almost everywhere.</li>
 *   <li>Leather armour dye. Every plugin dyes the starting armour; it survives a nametag plugin
 *       that hides scoreboard teams.</li>
 *   <li>The colour the name is drawn in.</li>
 * </ol>
 */
public final class TeamResolver {

    private TeamResolver() {
    }

    public static TeamColour teamOf(EntityPlayer player) {
        if (player == null) {
            return TeamColour.UNKNOWN;
        }
        ScorePlayerTeam team = player.worldObj == null ? null
                : player.worldObj.getScoreboard().getPlayersTeam(player.getName());
        if (team != null) {
            TeamColour fromPrefix = TeamColours.fromFormatted(team.getColorPrefix());
            if (fromPrefix != TeamColour.UNKNOWN) {
                return fromPrefix;
            }
        }
        TeamColour fromArmour = fromArmour(player);
        if (fromArmour != TeamColour.UNKNOWN) {
            return fromArmour;
        }
        return TeamColours.firstFromFormatted(player.getDisplayName().getFormattedText());
    }

    private static TeamColour fromArmour(EntityPlayer player) {
        // Chestplate first: boots and helmet are the slots most often swapped for iron or diamond.
        int[] slots = {2, 1, 3, 0};
        for (int slot : slots) {
            ItemStack stack = player.inventory.armorInventory[slot];
            if (stack != null && stack.getItem() instanceof ItemArmor) {
                ItemArmor armour = (ItemArmor) stack.getItem();
                if (armour.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER && armour.hasColor(stack)) {
                    TeamColour colour = TeamColours.fromArmourDye(armour.getColor(stack));
                    if (colour != TeamColour.UNKNOWN) {
                        return colour;
                    }
                }
            }
        }
        return TeamColour.UNKNOWN;
    }

    /** The local player's team. */
    public static TeamColour ownTeam() {
        return teamOf(Minecraft.getMinecraft().thePlayer);
    }

    /**
     * Whether an entity is on your team. Unknown teams are never treated as the same, so a player
     * whose team cannot be read is always a valid target rather than always a protected one.
     */
    public static boolean isTeammate(Entity entity) {
        if (!(entity instanceof EntityPlayer) || entity == Minecraft.getMinecraft().thePlayer) {
            return false;
        }
        TeamColour own = ownTeam();
        return own != TeamColour.UNKNOWN && own == teamOf((EntityPlayer) entity);
    }
}
