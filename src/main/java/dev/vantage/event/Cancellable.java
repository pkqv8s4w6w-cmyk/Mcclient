package dev.vantage.event;

/** Base for events a handler can veto. Once cancelled an event stays cancelled. */
public abstract class Cancellable {

    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }
}
