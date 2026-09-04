package dev.vantage.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpsMeterTest {

    @Test
    void countsClicksInsideTheWindow() {
        CpsMeter counter = new CpsMeter();
        for (int i = 0; i < 10; i++) {
            counter.click(1000L + i * 50L);
        }
        assertEquals(10, counter.perSecond(1450L));
    }

    @Test
    void clicksFallOutOfTheWindowAsTimePasses() {
        CpsMeter counter = new CpsMeter();
        counter.click(1000L);
        counter.click(1500L);
        assertEquals(2, counter.perSecond(1900L));
        // The first click is now more than a second old.
        assertEquals(1, counter.perSecond(2100L));
        assertEquals(0, counter.perSecond(2600L));
    }

    @Test
    void theRateDoesNotResetOnASecondBoundary() {
        // A counter that zeroed every second would read 0 here despite steady clicking.
        CpsMeter counter = new CpsMeter();
        for (int i = 0; i < 30; i++) {
            counter.click(1000L + i * 100L);
        }
        assertEquals(10, counter.perSecond(3900L));
    }

    @Test
    void aBurstBeyondCapacityDoesNotBreakTheReading() {
        CpsMeter counter = new CpsMeter();
        for (int i = 0; i < 200; i++) {
            counter.click(1000L + i);
        }
        assertEquals(64, counter.perSecond(1200L), "capped at the buffer size, not corrupted");
    }

    @Test
    void resetClearsTheWindow() {
        CpsMeter counter = new CpsMeter();
        counter.click(1000L);
        counter.reset();
        assertEquals(0, counter.perSecond(1000L));
    }
}
