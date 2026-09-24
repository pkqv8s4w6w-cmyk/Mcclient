package dev.vantage.event;

import net.minecraft.entity.Entity;

/**
 * How far the crosshair trace grows an entity's box beyond its real size. Vanilla uses 0.1 for
 * every entity; a larger border makes that entity easier to hit.
 */
public final class HitboxEvent {

    private final Entity entity;
    private float border;

    public HitboxEvent(Entity entity, float border) {
        this.entity = entity;
        this.border = border;
    }

    public Entity getEntity() {
        return entity;
    }

    public float getBorder() {
        return border;
    }

    public void setBorder(float border) {
        this.border = border;
    }
}
