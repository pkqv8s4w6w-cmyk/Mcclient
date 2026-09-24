package dev.vantage.event;

import net.minecraft.entity.EntityLivingBase;

/** Vanilla is about to draw the name above an entity. Cancel to draw your own instead. */
public final class NametagEvent extends Cancellable {

    private final EntityLivingBase entity;

    public NametagEvent(EntityLivingBase entity) {
        this.entity = entity;
    }

    public EntityLivingBase getEntity() {
        return entity;
    }
}
