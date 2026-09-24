package dev.vantage.module.impl.visual;

import dev.vantage.event.Render3DEvent;
import dev.vantage.gui.render.EntityColours;
import dev.vantage.gui.render.Render3D;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

/** Lines from the crosshair to every player, so you always know which way everyone is. */
public class TracersModule extends Module {

    private final NumberSetting range = register(new NumberSetting(
            "Range", "Furthest to draw", 128.0, 16.0, 256.0, 8.0, "m"));
    private final NumberSetting opacity = register(new NumberSetting(
            "Opacity", "How solid the lines are", 70.0, 10.0, 100.0, 5.0, "%"));

    public TracersModule() {
        super("Tracers", Category.VISUAL, "Lines from your crosshair to other players");
        on(Render3DEvent.class, this::onWorld);
    }

    private void onWorld(Render3DEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        float partial = event.getPartialTicks();
        // Start a little in front of the eyes along the look direction: the centre of the screen.
        Vec3 look = mc.thePlayer.getLook(partial);
        double startX = Render3D.cameraX() + look.xCoord;
        double startY = Render3D.cameraY() + mc.thePlayer.getEyeHeight() + look.yCoord;
        double startZ = Render3D.cameraZ() + look.zCoord;
        Render3D.begin();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || mc.thePlayer.getDistanceToEntity(player) > range.asDouble()) {
                continue;
            }
            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partial + player.height / 2.0;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial;
            Render3D.line(startX, startY, startZ, x, y, z,
                    RenderUtil.withAlpha(EntityColours.of(player), opacity.asFloat() / 100.0f));
        }
        Render3D.end();
    }
}
