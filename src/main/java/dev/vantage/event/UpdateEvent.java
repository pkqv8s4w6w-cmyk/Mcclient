package dev.vantage.event;

/**
 * The local player's update is starting, before input becomes movement. The place to change input
 * or motion for this tick.
 */
public final class UpdateEvent {

    public static final UpdateEvent INSTANCE = new UpdateEvent();

    private UpdateEvent() {
    }
}
