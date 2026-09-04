package dev.vantage.hypixel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedwarsStatsTest {

    private static JsonObject json(String text) {
        return new JsonParser().parse(text).getAsJsonObject();
    }

    private static final String FULL = "{"
            + "\"success\":true,"
            + "\"player\":{"
            + "  \"displayname\":\"Notch\","
            + "  \"achievements\":{\"bedwars_level\":412},"
            + "  \"stats\":{\"Bedwars\":{"
            + "     \"final_kills_bedwars\":6200,"
            + "     \"final_deaths_bedwars\":500,"
            + "     \"kills_bedwars\":14000,"
            + "     \"deaths_bedwars\":9000,"
            + "     \"wins_bedwars\":1500,"
            + "     \"losses_bedwars\":400,"
            + "     \"beds_broken_bedwars\":2600,"
            + "     \"winstreak\":12"
            + "  }}"
            + "}}";

    @Test
    void parsesACompleteResponse() {
        BedwarsStats stats = BedwarsStats.parse(json(FULL));
        assertFalse(stats.isUnknown());
        assertEquals(412, stats.getStar());
        assertEquals(6200, stats.getFinalKills());
        assertEquals(500, stats.getFinalDeaths());
        assertEquals(2600, stats.getBedsBroken());
        assertEquals(12, stats.getWinstreak());
        assertEquals(12.4, stats.getFinalKillDeathRatio(), 1e-9);
        assertEquals(3.75, stats.getWinLossRatio(), 1e-9);
    }

    @Test
    void aNickedPlayerComesBackAsSuccessWithANullPlayer() {
        // This is the shape that breaks naive parsers: success is true, player is null.
        BedwarsStats stats = BedwarsStats.parse(json("{\"success\":true,\"player\":null}"));
        assertTrue(stats.isUnknown());
    }

    @Test
    void anAccountThatHasNeverPlayedBedwarsIsKnownButEmpty() {
        BedwarsStats stats = BedwarsStats.parse(json(
                "{\"success\":true,\"player\":{\"achievements\":{\"bedwars_level\":3},\"stats\":{}}}"));
        assertFalse(stats.isUnknown(), "the account exists, so it is not unknown");
        assertEquals(3, stats.getStar());
        assertEquals(0, stats.getFinalKills());
    }

    @Test
    void absentKeysReadAsZeroRatherThanThrowing() {
        BedwarsStats stats = BedwarsStats.parse(json(
                "{\"player\":{\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":40}}}}"));
        assertEquals(40, stats.getFinalKills());
        assertEquals(0, stats.getFinalDeaths());
        assertEquals(0, stats.getStar(), "no achievements object at all");
    }

    @Test
    void aHiddenWinstreakIsReportedAsAbsentNotZero() {
        // Hypixel lets players hide their winstreak, in which case the key is simply missing.
        // Reporting that as 0 would make a hidden streak look like a losing one.
        BedwarsStats stats = BedwarsStats.parse(json("{\"player\":{\"stats\":{\"Bedwars\":{"
                + "\"final_kills_bedwars\":6200,\"final_deaths_bedwars\":500}}}}"));
        assertFalse(stats.hasWinstreak());
        assertEquals(-1, stats.getWinstreak());
        assertEquals(12.4, stats.getFinalKillDeathRatio(), 1e-9);
    }

    @Test
    void undefinedRatiosFallBackToTheKillCount() {
        BedwarsStats stats = BedwarsStats.parse(json(
                "{\"player\":{\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":7,\"final_deaths_bedwars\":0}}}}"));
        assertEquals(7.0, stats.getFinalKillDeathRatio(), 1e-9);
    }

    @Test
    void wronglyTypedValuesDegradeToZero() {
        BedwarsStats stats = BedwarsStats.parse(json(
                "{\"player\":{\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":\"lots\"}}}}"));
        assertEquals(0, stats.getFinalKills());
    }

    @Test
    void aNullResponseIsUnknown() {
        assertTrue(BedwarsStats.parse(null).isUnknown());
    }

    @Test
    void thinRecordsAreFlaggedAsLowSample() {
        BedwarsStats thin = BedwarsStats.parse(json(
                "{\"player\":{\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":5,\"final_deaths_bedwars\":1}}}}"));
        assertTrue(thin.isLowSample());
        assertFalse(BedwarsStats.parse(json(FULL)).isLowSample());
    }
}
