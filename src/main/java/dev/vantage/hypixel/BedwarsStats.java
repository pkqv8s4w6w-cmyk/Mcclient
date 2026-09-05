package dev.vantage.hypixel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A player's public Bedwars record.
 *
 * <p>Parsing is defensive throughout. The Hypixel API omits keys rather than sending zeroes, a
 * nicked player comes back as a null {@code player} object, and an account that has never touched
 * Bedwars has no {@code stats.Bedwars} at all. Each of those is a normal response, not an error,
 * and each one has produced a null pointer in every naive stat viewer ever written.
 */
public final class BedwarsStats {

    /** Returned for a player the API knows nothing about, typically because they are nicked. */
    public static final BedwarsStats UNKNOWN = new BedwarsStats(true, 0, 0, 0, 0, 0, 0, 0, 0, -1);

    private final boolean unknown;
    private final int star;
    private final int finalKills;
    private final int finalDeaths;
    private final int kills;
    private final int deaths;
    private final int wins;
    private final int losses;
    private final int bedsBroken;
    private final int winstreak;

    private BedwarsStats(boolean unknown, int star, int finalKills, int finalDeaths, int kills,
                         int deaths, int wins, int losses, int bedsBroken, int winstreak) {
        this.unknown = unknown;
        this.star = star;
        this.finalKills = finalKills;
        this.finalDeaths = finalDeaths;
        this.kills = kills;
        this.deaths = deaths;
        this.wins = wins;
        this.losses = losses;
        this.bedsBroken = bedsBroken;
        this.winstreak = winstreak;
    }

    /**
     * Reads a {@code /v2/player} response body.
     *
     * @return the parsed stats, or {@link #UNKNOWN} when the response carries no usable player
     */
    public static BedwarsStats parse(JsonObject response) {
        if (response == null) {
            return UNKNOWN;
        }
        JsonElement playerElement = response.get("player");
        if (playerElement == null || !playerElement.isJsonObject()) {
            // A nicked player, or a name that has never logged in. Both are success responses.
            return UNKNOWN;
        }
        JsonObject player = playerElement.getAsJsonObject();

        int star = readInt(optionalObject(player, "achievements"), "bedwars_level");

        JsonObject stats = optionalObject(player, "stats");
        JsonObject bedwars = optionalObject(stats, "Bedwars");
        if (bedwars == null) {
            // The account exists but has never played Bedwars. The star is still meaningful.
            return new BedwarsStats(false, star, 0, 0, 0, 0, 0, 0, 0, -1);
        }

        return new BedwarsStats(false, star,
                readInt(bedwars, "final_kills_bedwars"),
                readInt(bedwars, "final_deaths_bedwars"),
                readInt(bedwars, "kills_bedwars"),
                readInt(bedwars, "deaths_bedwars"),
                readInt(bedwars, "wins_bedwars"),
                readInt(bedwars, "losses_bedwars"),
                readInt(bedwars, "beds_broken_bedwars"),
                // Hypixel lets players hide their winstreak, in which case the key is absent.
                bedwars.has("winstreak") ? readInt(bedwars, "winstreak") : -1);
    }

    private static JsonObject optionalObject(JsonObject parent, String key) {
        if (parent == null) {
            return null;
        }
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static int readInt(JsonObject object, String key) {
        if (object == null) {
            return 0;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return 0;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    // -- accessors --------------------------------------------------------------------------

    public boolean isUnknown() {
        return unknown;
    }

    public int getStar() {
        return star;
    }

    public int getFinalKills() {
        return finalKills;
    }

    public int getFinalDeaths() {
        return finalDeaths;
    }

    public int getWins() {
        return wins;
    }

    public int getBedsBroken() {
        return bedsBroken;
    }

    /** @return the winstreak, or -1 when the player has it hidden */
    public int getWinstreak() {
        return winstreak;
    }

    public boolean hasWinstreak() {
        return winstreak >= 0;
    }

    /**
     * Final kill/death ratio.
     *
     * <p>With no final deaths the ratio is undefined, so this follows the convention every stat
     * viewer uses and reports the final kill count instead of infinity.
     */
    public double getFinalKillDeathRatio() {
        return finalDeaths == 0 ? finalKills : finalKills / (double) finalDeaths;
    }

    public double getKillDeathRatio() {
        return deaths == 0 ? kills : kills / (double) deaths;
    }

    public double getWinLossRatio() {
        return losses == 0 ? wins : wins / (double) losses;
    }

    /** True when there is not enough history for the ratios to mean anything. */
    public boolean isLowSample() {
        return !unknown && finalKills + finalDeaths < 20;
    }
}
