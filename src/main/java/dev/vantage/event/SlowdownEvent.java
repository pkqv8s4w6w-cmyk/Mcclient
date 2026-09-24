package dev.vantage.event;

/**
 * Using an item is about to slow the player. The multiplier is applied to both movement inputs;
 * vanilla uses 0.2. Setting it to 1 removes the slowdown.
 */
public final class SlowdownEvent {

    private float multiplier = 0.2f;
    private boolean allowSprint;

    public float getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(float multiplier) {
        this.multiplier = multiplier;
    }

    /** Whether the player may keep or start sprinting while using the item. */
    public boolean isSprintAllowed() {
        return allowSprint;
    }

    public void setSprintAllowed(boolean allowSprint) {
        this.allowSprint = allowSprint;
    }
}
