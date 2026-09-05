package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatSessionTest {

    @Test
    void nothingIsOpenUntilSomebodyGetsHit() {
        // The default has to be closed. Every check reads only what is collected inside a fight,
        // so a session that starts open would collect an entire game of ordinary play as evidence.
        assertFalse(new CombatSession().isOpen(0L));
        assertFalse(new CombatSession().isOpen(1_000_000L));
    }

    @Test
    void aBlowOpensIt() {
        CombatSession session = new CombatSession();
        session.exchange(1_000L);
        assertTrue(session.isOpen(1_000L));
        assertTrue(session.isOpen(1_000L + CombatSession.TIMEOUT_MILLIS - 1));
    }

    @Test
    void itClosesOnceTheFightGoesQuiet() {
        CombatSession session = new CombatSession();
        session.exchange(1_000L);
        assertFalse(session.isOpen(1_000L + CombatSession.TIMEOUT_MILLIS));
    }

    @Test
    void everyBlowExtendsIt() {
        CombatSession session = new CombatSession();
        long now = 1_000L;
        for (int blow = 0; blow < 10; blow++) {
            now += CombatSession.TIMEOUT_MILLIS / 2;
            session.exchange(now);
        }
        assertTrue(session.isOpen(now), "a running fight should not time out mid-swing");
        assertEquals(10, session.getExchanges(now));
    }

    @Test
    void aSecondFightStartsItsOwnCount() {
        CombatSession session = new CombatSession();
        session.exchange(1_000L);
        session.exchange(1_500L);

        long later = 1_000L + CombatSession.TIMEOUT_MILLIS * 3;
        assertFalse(session.isOpen(later));
        assertEquals(0, session.getExchanges(later));

        session.exchange(later);
        assertTrue(session.isOpen(later));
        assertEquals(1, session.getExchanges(later), "the earlier fight should not carry over");
    }

    @Test
    void itReportsHowLongTheFightHasRun() {
        CombatSession session = new CombatSession();
        session.exchange(1_000L);
        session.exchange(2_500L);
        assertEquals(1_500L, session.getDurationMillis(2_500L));
        assertEquals(0L, session.getDurationMillis(1_000L + CombatSession.TIMEOUT_MILLIS * 2));
    }

    @Test
    void resettingClosesIt() {
        CombatSession session = new CombatSession();
        session.exchange(1_000L);
        session.reset();
        assertFalse(session.isOpen(1_000L));
    }
}
