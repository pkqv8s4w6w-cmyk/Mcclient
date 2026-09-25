package dev.vantage.event;

import net.minecraft.entity.EntityLivingBase;

/** A living entity's model is about to be drawn, or has just been. */
public final class EntityRenderEvent {

    private final Stage stage;
    private final EntityLivingBase entity;

    public EntityRenderEvent(Stage stage, EntityLivingBase entity) {
        this.stage = stage;
        this.entity = entity;
    }

    public Stage getStage() {
        return stage;
    }

    public EntityLivingBase getEntity() {
        return entity;
    }
}
