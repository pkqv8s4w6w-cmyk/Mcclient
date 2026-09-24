package dev.vantage.mixin.accessor;

import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The camera position world rendering is translated by, for drawing in world space. */
@Mixin(RenderManager.class)
public interface RenderManagerAccessor {

    @Accessor("renderPosX")
    double vantageRenderX();

    @Accessor("renderPosY")
    double vantageRenderY();

    @Accessor("renderPosZ")
    double vantageRenderZ();
}
