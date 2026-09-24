package dev.vantage.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathMessageParserTest {

    private static Set<String> lobby(String... names) {
        Set<String> set = new HashSet<String>();
        for (String name : names) {
            set.add(name);
        }
        return set;
    }

    private static final Set<String> LOBBY = lobby("Notch", "Player2", "Steve_1");

    @Test
    void readsAPlainKill() {
        DeathMessageParser.Kill kill = DeathMessageParser.parse("Notch was killed by Player2.", LOBBY);
        assertEquals("Notch", kill.getVictim());
        assertEquals("Player2", kill.getKiller());
        assertFalse(kill.isFinalKill());
    }

    @Test
    void seesThroughRankPrefixesAndColourCodes() {
        // The real thing looks like this, and a first-token-is-the-victim rule fails on it.
        DeathMessageParser.Kill kill = DeathMessageParser.parse(
                "§b[MVP§d+§b] Notch §7was shot by §a[VIP] Player2§7. §b§lFINAL KILL!", LOBBY);
        assertEquals("Notch", kill.getVictim());
        assertEquals("Player2", kill.getKiller());
        assertTrue(kill.isFinalKill());
    }

    @Test
    void handlesADeathWithNoKiller() {
        DeathMessageParser.Kill kill = DeathMessageParser.parse("Notch fell into the void.", LOBBY);
        assertEquals("Notch", kill.getVictim());
        assertFalse(kill.hasKiller());
        assertNull(kill.getKiller());
    }

    @Test
    void doesNotTreatPlayerChatAsAKill() {
        // Without the colon guard this reads as Player2 killing Notch.
        assertNull(DeathMessageParser.parse("Notch: nice shot Player2", LOBBY));
        assertNull(DeathMessageParser.parse("§b[MVP+] Notch§f: gg Player2", LOBBY));
    }

    @Test
    void ignoresLinesWithNobodyWeKnow() {
        assertNull(DeathMessageParser.parse("The game starts in 10 seconds!", LOBBY));
        assertNull(DeathMessageParser.parse("Stranger was killed by Someone.", LOBBY));
    }

    @Test
    void namesWithUnderscoresSurviveTokenising() {
        DeathMessageParser.Kill kill = DeathMessageParser.parse("Steve_1 was killed by Notch.", LOBBY);
        assertEquals("Steve_1", kill.getVictim());
        assertEquals("Notch", kill.getKiller());
    }

    @Test
    void aSelfInflictedDeathHasNoKiller() {
        // Some messages name the victim twice; that must not count as killing themselves.
        DeathMessageParser.Kill kill = DeathMessageParser.parse(
                "Notch died. Notch was eliminated.", LOBBY);
        assertEquals("Notch", kill.getVictim());
        assertFalse(kill.hasKiller());
    }

    @Test
    void emptyAndNullInputsAreSafe() {
        assertNull(DeathMessageParser.parse(null, LOBBY));
        assertNull(DeathMessageParser.parse("", LOBBY));
        assertNull(DeathMessageParser.parse("Notch was killed by Player2.", null));
        assertNull(DeathMessageParser.parse("Notch was killed by Player2.", lobby()));
    }
}
