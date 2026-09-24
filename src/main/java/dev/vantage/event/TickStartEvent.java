package dev.vantage.event;

/**
 * A client tick is starting, before keybinds are processed. The place to press keys on the player's
 * behalf so the game handles them exactly as if they had been pressed.
 */
public final class TickStartEvent {

    public static final TickStartEvent INSTANCE = new TickStartEvent();

    private TickStartEvent() {
    }
}
