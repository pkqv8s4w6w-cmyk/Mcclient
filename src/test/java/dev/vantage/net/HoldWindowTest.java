package dev.vantage.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldWindowTest {

    private static final int TARGET = 42;
    private static final int OTHER = 43;

    @Test
    void aWindowOpensOnTheFirstPacketAndRunsForTheMaximumDelay() {
        HoldWindow window = new HoldWindow();

        assertTrue(window.beginOrContinue(TARGET, 1000L, 250L), "opens");
        assertTrue(window.isEngaged(TARGET));
        assertEquals(250L, window.remaining(TARGET, 1000L));
        assertEquals(100L, window.remaining(TARGET, 1150L));
    }

    @Test
    void theWindowKeepsItsOriginalDeadlineAsMorePacketsArrive() {
        // The deadline is set once, at the start. Re-arming it on every packet would pin a moving
        // player forever, since a moving player never stops sending them.
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);

        window.beginOrContinue(TARGET, 1100L, 250L);
        window.beginOrContinue(TARGET, 1200L, 250L);

        assertEquals(50L, window.remaining(TARGET, 1200L), "still counting from 1000, not 1200");
        assertFalse(window.beginOrContinue(TARGET, 1250L, 250L), "closed on time");
    }

    @Test
    void aClosedWindowReportsItselfClosedRatherThanReopening() {
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);

        assertFalse(window.beginOrContinue(TARGET, 1300L, 250L));
        assertEquals(0L, window.remaining(TARGET, 1300L), "never negative");
    }

    @Test
    void aZeroMaximumDelayNeverOpensAWindow() {
        HoldWindow window = new HoldWindow();

        assertFalse(window.beginOrContinue(TARGET, 1000L, 0L));
        assertFalse(window.isEngaged(TARGET));
    }

    @Test
    void endingAWindowStartsTheCooldown() {
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);

        window.end(TARGET, 1250L, 1000L);

        assertFalse(window.isEngaged(TARGET));
        assertTrue(window.isCoolingDown(TARGET, 1250L));
        assertTrue(window.isCoolingDown(TARGET, 2249L));
        assertFalse(window.isCoolingDown(TARGET, 2250L), "lapses on the boundary");
    }

    @Test
    void aTargetCanBeHeldAgainOnceTheCooldownLapses() {
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);
        window.end(TARGET, 1250L, 1000L);

        assertTrue(window.beginOrContinue(TARGET, 2250L, 250L));
        assertEquals(250L, window.remaining(TARGET, 2250L));
    }

    @Test
    void aZeroCooldownLetsTheNextWindowOpenImmediately() {
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);

        window.end(TARGET, 1250L, 0L);

        assertFalse(window.isCoolingDown(TARGET, 1250L));
        assertTrue(window.beginOrContinue(TARGET, 1250L, 250L));
    }

    @Test
    void windowsAreTrackedPerPlayer() {
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);
        window.beginOrContinue(OTHER, 1100L, 250L);

        window.end(TARGET, 1150L, 1000L);

        assertFalse(window.isEngaged(TARGET));
        assertTrue(window.isEngaged(OTHER), "one target ending must not release another");
        assertFalse(window.isCoolingDown(OTHER, 1150L));
        assertEquals(200L, window.remaining(OTHER, 1150L));
    }

    @Test
    void expiredNamesOnlyTheWindowsThatHaveRunOut() {
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);
        window.beginOrContinue(OTHER, 1000L, 800L);

        assertTrue(window.expired(1100L).isEmpty(), "both still running");
        assertEquals(java.util.Arrays.asList(Integer.valueOf(TARGET)), window.expired(1250L));
        assertEquals(2, window.expired(1800L).size());
    }

    @Test
    void expiryIsHowAPinnedPlayerGetsReleasedAtAll() {
        // A held player sends nothing further, so nothing arrives to notice the deadline on. If
        // expired() missed them they would stay frozen until they happened to move again.
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);

        assertEquals(1, window.expired(1250L).size(), "due exactly on the deadline");
    }

    @Test
    void endingATargetThatWasNeverHeldIsHarmless() {
        HoldWindow window = new HoldWindow();

        window.end(TARGET, 1000L, 500L);

        assertFalse(window.isEngaged(TARGET));
        assertTrue(window.isCoolingDown(TARGET, 1000L), "cooldown still applies");
    }

    @Test
    void lapsedCooldownsArePrunedRatherThanKeptForever() {
        HoldWindow window = new HoldWindow();
        window.end(TARGET, 1000L, 500L);
        window.end(OTHER, 1000L, 5000L);

        window.pruneCooldowns(1500L);

        assertFalse(window.isCoolingDown(TARGET, 1500L));
        assertTrue(window.isCoolingDown(OTHER, 1500L), "still inside its own cooldown");
    }

    @Test
    void forgettingAPlayerDropsBothTheirWindowAndTheirCooldown() {
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);
        window.end(TARGET, 1100L, 5000L);

        window.forget(TARGET);

        assertFalse(window.isEngaged(TARGET));
        assertFalse(window.isCoolingDown(TARGET, 1100L));
    }

    @Test
    void clearingDropsEverything() {
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);
        window.end(OTHER, 1000L, 5000L);

        window.clear();

        assertTrue(window.engaged().isEmpty());
        assertFalse(window.isCoolingDown(OTHER, 1000L));
    }

    @Test
    void theEngagedSetIsACopySafeToIterateWhileEndingWindows() {
        HoldWindow window = new HoldWindow();
        window.beginOrContinue(TARGET, 1000L, 250L);
        window.beginOrContinue(OTHER, 1000L, 250L);

        for (Integer id : window.engaged()) {
            window.end(id.intValue(), 1250L, 0L);
        }

        assertTrue(window.engaged().isEmpty(), "no ConcurrentModificationException, and all closed");
    }
}
