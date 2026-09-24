package dev.vantage.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A small typed event bus for the hooks Forge does not provide: outgoing packets, the movement
 * packet the player is about to send, collision-free movement, and so on.
 *
 * <p>Forge's own bus stays in charge of ticks, rendering and chat through {@code ModuleManager}.
 * This one exists because the mixins fire far more often than Forge events and need handlers that
 * can mutate the event in place, which reflection-dispatched {@code @SubscribeEvent} is too slow and
 * too loosely typed for.
 *
 * <p>Handlers are held in copy-on-write lists because packet events are posted from the network
 * thread while modules are toggled from the client thread. Each list is sorted by priority, highest
 * first, so a module can ask to run before or after another without knowing who the other is.
 *
 * <p>Deliberately free of Minecraft imports, like {@code Module}, so it can be tested directly.
 */
public final class EventBus {

    /** Default priority. The rotation manager runs well below this so modules request first. */
    public static final int NORMAL = 0;

    private static final EventBus GLOBAL = new EventBus();

    public static EventBus global() {
        return GLOBAL;
    }

    /** One handler for one event type. Kept by the owner so it can be removed again. */
    public static final class Listener<E> {
        private final Class<E> type;
        private final int priority;
        private final Consumer<E> handler;
        private final Object owner;

        public Listener(Class<E> type, int priority, Consumer<E> handler, Object owner) {
            this.type = type;
            this.priority = priority;
            this.handler = handler;
            this.owner = owner;
        }

        public Class<E> getType() {
            return type;
        }

        public int getPriority() {
            return priority;
        }

        public Object getOwner() {
            return owner;
        }
    }

    private final Map<Class<?>, CopyOnWriteArrayList<Listener<?>>> byType =
            new ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Listener<?>>>();

    private volatile BiConsumer<Object, Throwable> failureHandler;

    /**
     * Called with the listener's owner when a handler throws. The module manager uses this to
     * switch the owning module off, the same way it treats a module that throws in a tick.
     */
    public void setFailureHandler(BiConsumer<Object, Throwable> handler) {
        this.failureHandler = handler;
    }

    public <E> Listener<E> subscribe(Class<E> type, Consumer<E> handler) {
        Listener<E> listener = new Listener<E>(type, NORMAL, handler, null);
        register(listener);
        return listener;
    }

    public <E> Listener<E> subscribe(Class<E> type, int priority, Consumer<E> handler, Object owner) {
        Listener<E> listener = new Listener<E>(type, priority, handler, owner);
        register(listener);
        return listener;
    }

    public synchronized void register(Listener<?> listener) {
        CopyOnWriteArrayList<Listener<?>> list = byType.get(listener.type);
        if (list == null) {
            list = new CopyOnWriteArrayList<Listener<?>>();
            byType.put(listener.type, list);
        }
        if (list.contains(listener)) {
            return;
        }
        // Insert in priority order in one write, rather than add-then-sort, so a post running on
        // another thread never sees a half-sorted list.
        List<Listener<?>> sorted = new ArrayList<Listener<?>>(list);
        int index = 0;
        while (index < sorted.size() && sorted.get(index).priority >= listener.priority) {
            index++;
        }
        sorted.add(index, listener);
        list.clear();
        list.addAll(sorted);
    }

    public synchronized void unregister(Listener<?> listener) {
        CopyOnWriteArrayList<Listener<?>> list = byType.get(listener.type);
        if (list != null) {
            list.remove(listener);
        }
    }

    /** True if anything is listening for this exact type, so hot paths can skip allocating. */
    public boolean hasListeners(Class<?> type) {
        CopyOnWriteArrayList<Listener<?>> list = byType.get(type);
        return list != null && !list.isEmpty();
    }

    /**
     * Delivers an event to every handler registered for its exact class.
     *
     * <p>Subclasses are not matched against their parents' handlers: a module listening for
     * {@code PacketEvent.Send} should never be handed a receive.
     *
     * @return the same event, for call sites that read it back
     */
    @SuppressWarnings("unchecked")
    public <E> E post(E event) {
        CopyOnWriteArrayList<Listener<?>> list = byType.get(event.getClass());
        if (list == null) {
            return event;
        }
        for (Listener<?> listener : list) {
            try {
                ((Consumer<E>) listener.handler).accept(event);
            } catch (Throwable failure) {
                BiConsumer<Object, Throwable> onFailure = failureHandler;
                if (onFailure != null) {
                    onFailure.accept(listener.owner, failure);
                }
                // A handler that failed stays out of the way from here on.
                unregister(listener);
            }
        }
        return event;
    }
}
