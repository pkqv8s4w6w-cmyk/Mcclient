package dev.vantage.module.impl.visual;

import dev.vantage.event.Render2DEvent;
import dev.vantage.event.Render3DEvent;
import dev.vantage.gui.render.EntityColours;
import dev.vantage.gui.render.Render3D;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows players through walls, in their team colour. Box draws in the world; 2D draws a flat frame
 * on screen around where they are, with a health bar down the side.
 */
public class EspModule extends Module {

    public enum Mode { BOX, TWO_D }

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "A box in the world or a frame on screen", Mode.TWO_D));
    private final BooleanSetting health = register(new BooleanSetting(
            "Health Bar", "Show health beside the frame", true));
    private final BooleanSetting mobs = register(new BooleanSetting(
            "Mobs", "Include hostile mobs", false));
    private final NumberSetting range = register(new NumberSetting(
            "Range", "Furthest to draw", 128.0, 16.0, 256.0, 8.0, "m"));

    private final List<float[]> frames = new ArrayList<float[]>();
    private final List<EntityLivingBase> framed = new ArrayList<EntityLivingBase>();

    public EspModule() {
        super("ESP", Category.VISUAL, "See players through walls");
        health.visibleWhen(() -> mode.get() == Mode.TWO_D);
        on(Render3DEvent.class, this::onWorld);
        on(Render2DEvent.class, this::onScreen);
    }

    private List<EntityLivingBase> targets() {
        Minecraft mc = Minecraft.getMinecraft();
        List<EntityLivingBase> list = new ArrayList<EntityLivingBase>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == mc.thePlayer || !(entity instanceof EntityLivingBase) || entity.isDead) {
                continue;
            }
            boolean wanted = entity instanceof EntityPlayer || (mobs.value() && entity instanceof EntityMob);
            if (wanted && mc.thePlayer.getDistanceToEntity(entity) <= range.asDouble()) {
                list.add((EntityLivingBase) entity);
            }
        }
        return list;
    }

    private void onWorld(Render3DEvent event) {
        if (mode.get() != Mode.BOX) {
            return;
        }
        Render3D.begin();
        for (EntityLivingBase entity : targets()) {
            AxisAlignedBB box = Render3D.interpolatedBox(entity, event.getPartialTicks()).expand(0.05, 0.05, 0.05);
            Render3D.box(box, EntityColours.of(entity), 0.18f);
        }
        Render3D.end();
    }

    private void onScreen(Render2DEvent event) {
        if (mode.get() != Mode.TWO_D) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        for (EntityLivingBase entity : targets()) {
            float[] frame = screenFrame(entity, event.getPartialTicks(), scale);
            if (frame == null) {
                continue;
            }
            int colour = EntityColours.of(entity);
            float x = frame[0];
            float y = frame[1];
            float w = frame[2] - frame[0];
            float h = frame[3] - frame[1];
            // A dark outline either side keeps the frame visible against any background.
            drawFrame(x - 0.5f, y - 0.5f, w + 1.0f, h + 1.0f, 0xAA000000, 2.0f);
            drawFrame(x, y, w, h, colour, 1.0f);
            if (health.value()) {
                float fraction = Math.min(1.0f, entity.getHealth() / entity.getMaxHealth());
                RenderUtil.rect(x - 3.5f, y - 0.5f, 2.0f, h + 1.0f, 0xAA000000);
                RenderUtil.rect(x - 3.0f, y + h * (1.0f - fraction), 1.0f, h * fraction, EntityColours.health(fraction));
            }
        }
    }

    private static void drawFrame(float x, float y, float w, float h, int colour, float thickness) {
        RenderUtil.rect(x, y, w, thickness, colour);
        RenderUtil.rect(x, y + h - thickness, w, thickness, colour);
        RenderUtil.rect(x, y, thickness, h, colour);
        RenderUtil.rect(x + w - thickness, y, thickness, h, colour);
    }

    /** The screen rectangle around an entity's box, or null if any corner is behind the camera. */
    static float[] screenFrame(Entity entity, float partialTicks, int scale) {
        AxisAlignedBB box = Render3D.interpolatedBox(entity, partialTicks).expand(0.1, 0.1, 0.1);
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    float[] point = Render3D.project(x, y, z, scale);
                    if (point == null) {
                        return null;
                    }
                    minX = Math.min(minX, point[0]);
                    minY = Math.min(minY, point[1]);
                    maxX = Math.max(maxX, point[0]);
                    maxY = Math.max(maxY, point[1]);
                }
            }
        }
        return new float[]{minX, minY, maxX, maxY};
    }
}
