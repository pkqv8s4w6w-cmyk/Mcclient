package dev.vantage.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {

    static final class Ping {
        final List<String> seen = new ArrayList<String>();
    }

    static final class Other {
    }

    @Test
    void deliversToHandlersOfTheExactType() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<String>();
        bus.subscribe(Ping.class, ping -> calls.add("ping"));
        bus.subscribe(Other.class, other -> calls.add("other"));

        bus.post(new Ping());

        assertEquals(1, calls.size());
        assertEquals("ping", calls.get(0));
    }

    @Test
    void higherPriorityRunsFirstRegardlessOfRegistrationOrder() {
        EventBus bus = new EventBus();
        bus.subscribe(Ping.class, -5, ping -> ping.seen.add("late"), null);
        bus.subscribe(Ping.class, 10, ping -> ping.seen.add("early"), null);
        bus.subscribe(Ping.class, 0, ping -> ping.seen.add("middle"), null);

        Ping ping = bus.post(new Ping());

        assertEquals(3, ping.seen.size());
        assertEquals("early", ping.seen.get(0));
        assertEquals("middle", ping.seen.get(1));
        assertEquals("late", ping.seen.get(2));
    }

    @Test
    void unregisteredHandlersStopReceiving() {
        EventBus bus = new EventBus();
        EventBus.Listener<Ping> listener = bus.subscribe(Ping.class, ping -> ping.seen.add("x"));
        bus.unregister(listener);

        assertTrue(bus.post(new Ping()).seen.isEmpty());
        assertFalse(bus.hasListeners(Ping.class));
    }

    @Test
    void registeringTwiceDoesNotDeliverTwice() {
        EventBus bus = new EventBus();
        EventBus.Listener<Ping> listener = new EventBus.Listener<Ping>(Ping.class, 0, ping -> ping.seen.add("x"), null);
        bus.register(listener);
        bus.register(listener);

        assertEquals(1, bus.post(new Ping()).seen.size());
    }

    @Test
    void aThrowingHandlerIsReportedWithItsOwnerAndRemovedWithoutStoppingTheRest() {
        EventBus bus = new EventBus();
        Object owner = new Object();
        List<Object> failedOwners = new ArrayList<Object>();
        bus.setFailureHandler((failedOwner, failure) -> failedOwners.add(failedOwner));
        bus.subscribe(Ping.class, 5, ping -> {
            throw new IllegalStateException("boom");
        }, owner);
        bus.subscribe(Ping.class, 0, ping -> ping.seen.add("survivor"), null);

        Ping first = bus.post(new Ping());
        Ping second = bus.post(new Ping());

        assertEquals(1, failedOwners.size());
        assertSame(owner, failedOwners.get(0));
        assertEquals(1, first.seen.size());
        assertEquals(1, second.seen.size());
    }
}
