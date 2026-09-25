package dev.vantage.event;

import net.minecraft.entity.Entity;

/**
 * The local player is attacking an entity. {@link Stage#PRE} fires before the attack packet goes,
 * {@link Stage#POST} after the attack has been applied locally.
 */
public final class AttackEvent extends Cancellable {

    private final Stage stage;
    private final Entity target;

    public AttackEvent(Stage stage, Entity target) {
        this.stage = stage;
        this.target = target;
    }

    public Stage getStage() {
        return stage;
    }

    public Entity getTarget() {
        return target;
    }
}
