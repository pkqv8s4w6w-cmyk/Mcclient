package dev.vantage.combat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickTimerTest {

    @Test
    void averageRateLandsInsideTheRequestedRange() {
        ClickTimer timer = new ClickTimer(new Random(7));
        long total = 0;
        int clicks = 2000;
        for (int i = 0; i < clicks; i++) {
            total += timer.nextDelay(9.0, 13.0);
        }
        double cps = 1000.0 / (total / (double) clicks);
        assertTrue(cps > 8.0 && cps < 13.5, "average cps was " + cps);
    }

    @Test
    void gapsAreNotAllTheSame() {
        ClickTimer timer = new ClickTimer(new Random(3));
        Set<Long> distinct = new HashSet<Long>();
        for (int i = 0; i < 200; i++) {
            distinct.add(timer.nextDelay(10.0, 12.0));
        }
        assertTrue(distinct.size() > 10, "only " + distinct.size() + " distinct gaps");
    }

    @Test
    void aStallDoesNotBankUpABurstOfCatchUpClicks() {
        ClickTimer timer = new ClickTimer(new Random(11));
        long now = 1_000L;
        assertTrue(timer.shouldClick(now, 10.0, 10.0));
        // Nothing for two seconds, then the timer is asked again every millisecond.
        now += 2_000L;
        int burst = 0;
        for (int i = 0; i < 40; i++) {
            if (timer.shouldClick(now + i, 10.0, 10.0)) {
                burst++;
            }
        }
        assertTrue(burst <= 1, burst + " clicks fired in 40ms after a stall");
    }

    @Test
    void neverSchedulesAnImpossiblyShortGap() {
        ClickTimer timer = new ClickTimer(new Random(5));
        for (int i = 0; i < 5000; i++) {
            assertTrue(timer.nextDelay(20.0, 20.0) >= 25L);
        }
    }
}
