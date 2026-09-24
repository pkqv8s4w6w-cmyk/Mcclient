package dev.vantage.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameMaskerTest {

    @Test
    void replacesTheNameInAChatLine() {
        assertEquals("<Ghost> hello",
                NameMasker.mask("<Notch> hello", "Notch", "Ghost"));
    }

    @Test
    void replacesEveryOccurrence() {
        assertEquals("Ghost was killed by Player2. Ghost respawned.",
                NameMasker.mask("Notch was killed by Player2. Notch respawned.", "Notch", "Ghost"));
    }

    @Test
    void leavesLongerNamesContainingItAlone() {
        // The case that makes a naive replace embarrassing in chat.
        assertEquals("Ghost and Notchy and NotNotch",
                NameMasker.mask("Notch and Notchy and NotNotch", "Notch", "Ghost"));
    }

    @Test
    void underscoresCountAsPartOfAName() {
        assertEquals("Steve_1 joined",
                NameMasker.mask("Steve_1 joined", "Steve", "Ghost"));
        assertEquals("Ghost joined",
                NameMasker.mask("Steve_1 joined", "Steve_1", "Ghost"));
    }

    @Test
    void survivesColourCodesAroundTheName() {
        assertEquals("§b[MVP§d+§b] §fGhost§7: hi",
                NameMasker.mask("§b[MVP§d+§b] §fNotch§7: hi", "Notch", "Ghost"));
    }

    @Test
    void leavesOtherPlayersAlone() {
        assertEquals("Player2 killed Player3",
                NameMasker.mask("Player2 killed Player3", "Notch", "Ghost"));
    }

    @Test
    void aNoOpReplacementChangesNothing() {
        assertEquals("Notch wins", NameMasker.mask("Notch wins", "Notch", "Notch"));
    }

    @Test
    void nullAndEmptyInputsAreSafe() {
        assertEquals(null, NameMasker.mask(null, "Notch", "Ghost"));
        assertEquals("text", NameMasker.mask("text", null, "Ghost"));
        assertEquals("text", NameMasker.mask("text", "", "Ghost"));
        assertEquals("text", NameMasker.mask("text", "Notch", null));
    }
}
