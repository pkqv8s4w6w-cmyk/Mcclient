package dev.vantage.module.impl.bedwars;

import dev.vantage.event.Render2DEvent;
import dev.vantage.event.Render3DEvent;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.Render3D;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Shows dropped iron, gold, diamonds and emeralds through walls, with how many are there. */
public class ItemEspModule extends Module {

    private final BooleanSetting everything = register(new BooleanSetting(
            "All Items", "Show every dropped item, not just resources", false));
    private final NumberSetting range = register(new NumberSetting(
            "Range", "Furthest to draw", 48.0, 8.0, 128.0, 8.0, "m"));

    public ItemEspModule() {
        super("ItemESP", Category.BEDWARS, "See dropped resources through walls");
        on(Render3DEvent.class, this::onWorld);
        on(Render2DEvent.class, event -> onScreen(event.getPartialTicks()));
    }

    /** Resource colour, or 0 for an item that is not a resource. */
    static int colourOf(Item item) {
        if (item == Items.iron_ingot) {
            return 0xFFE8E8E8;
        }
        if (item == Items.gold_ingot) {
            return 0xFFFFD34D;
        }
        if (item == Items.diamond) {
            return 0xFF5CE1E6;
        }
        if (item == Items.emerald) {
            return 0xFF4ADE80;
        }
        return 0;
    }

    private int colourFor(EntityItem entity) {
        ItemStack stack = entity.getEntityItem();
        int colour = stack == null ? 0 : colourOf(stack.getItem());
        if (colour == 0 && everything.value()) {
            colour = Theme.accent();
        }
        return colour;
    }

    private void onWorld(Render3DEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Render3D.begin();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityItem) || mc.thePlayer.getDistanceToEntity(entity) > range.asDouble()) {
                continue;
            }
            int colour = colourFor((EntityItem) entity);
            if (colour != 0) {
                Render3D.box(Render3D.interpolatedBox(entity, event.getPartialTicks()).expand(0.1, 0.1, 0.1), colour, 0.2f);
            }
        }
        Render3D.end();
    }

    private void onScreen(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityItem) || mc.thePlayer.getDistanceToEntity(entity) > range.asDouble()) {
                continue;
            }
            EntityItem item = (EntityItem) entity;
            int colour = colourFor(item);
            if (colour == 0 || item.getEntityItem() == null) {
                continue;
            }
            double x = item.lastTickPosX + (item.posX - item.lastTickPosX) * partialTicks;
            double y = item.lastTickPosY + (item.posY - item.lastTickPosY) * partialTicks;
            double z = item.lastTickPosZ + (item.posZ - item.lastTickPosZ) * partialTicks;
            float[] screen = Render3D.project(x, y + 0.6, z, scale);
            if (screen != null) {
                String text = item.getEntityItem().stackSize + "x " + item.getEntityItem().getDisplayName();
                Fonts.TINY.drawCentred(text, screen[0] + 0.5f, screen[1] + 0.5f, 0xB0000000);
                Fonts.TINY.drawCentred(text, screen[0], screen[1], RenderUtil.withAlpha(colour, 255));
            }
        }
    }
}
