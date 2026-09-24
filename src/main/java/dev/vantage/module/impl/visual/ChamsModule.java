package dev.vantage.module.impl.visual;

import dev.vantage.event.EntityRenderEvent;
import dev.vantage.event.Stage;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

/**
 * Draws player models through walls. The model is pulled to the front of the depth buffer while it
 * draws, so walls in front of it no longer hide it.
 */
public class ChamsModule extends Module {

    public ChamsModule() {
        super("Chams", Category.VISUAL, "See player models through walls");
        on(EntityRenderEvent.class, event -> {
            if (!(event.getEntity() instanceof EntityPlayer)) {
                return;
            }
            if (event.getStage() == Stage.PRE) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(1.0f, -1100000.0f);
            } else {
                GL11.glPolygonOffset(1.0f, 1100000.0f);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
        });
    }
}
