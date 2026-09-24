package dev.vantage.module.impl.visual;

import dev.vantage.event.NametagEvent;
import dev.vantage.event.Render2DEvent;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.EntityColours;
import dev.vantage.gui.render.Render3D;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Readable name tags: name in team colour, health, distance and the gear a player carries, drawn on
 * screen at a constant size so they stay legible at range and through walls.
 */
public class NametagsModule extends Module {

    private final BooleanSetting healthShown = register(new BooleanSetting(
            "Health", "Show health", true));
    private final BooleanSetting distanceShown = register(new BooleanSetting(
            "Distance", "Show how far away they are", true));
    private final BooleanSetting gear = register(new BooleanSetting(
            "Gear", "Show armour and the held item", true));
    private final NumberSetting size = register(new NumberSetting(
            "Size", "Tag size", 1.0, 0.5, 2.0, 0.05, "x"));
    private final NumberSetting range = register(new NumberSetting(
            "Range", "Furthest to draw", 128.0, 16.0, 256.0, 8.0, "m"));

    public NametagsModule() {
        super("Nametags", Category.VISUAL, "Clear name tags with health, distance and gear");
        on(NametagEvent.class, event -> {
            if (event.getEntity() instanceof EntityPlayer) {
                event.cancel();
            }
        });
        on(Render2DEvent.class, this::onScreen);
    }

    private void onScreen(Render2DEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        List<EntityPlayer> players = new ArrayList<EntityPlayer>(mc.theWorld.playerEntities);
        // Furthest first, so nearer tags draw on top.
        players.sort((a, b) -> Double.compare(mc.thePlayer.getDistanceToEntity(b), mc.thePlayer.getDistanceToEntity(a)));
        for (EntityPlayer player : players) {
            if (player == mc.thePlayer || player.isDead) {
                continue;
            }
            float distance = mc.thePlayer.getDistanceToEntity(player);
            if (distance > range.asDouble()) {
                continue;
            }
            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();
            float[] screen = Render3D.project(x, y + player.height + 0.35, z, scale);
            if (screen != null) {
                drawTag(player, screen[0], screen[1], distance);
            }
        }
    }

    private void drawTag(EntityPlayer player, float centreX, float bottomY, float distance) {
        StringBuilder text = new StringBuilder(player.getName());
        if (healthShown.value()) {
            text.append("  ").append(String.format(Locale.ROOT, "%.1f", player.getHealth() + player.getAbsorptionAmount()));
        }
        if (distanceShown.value()) {
            text.append("  ").append(Math.round(distance)).append('m');
        }
        String label = text.toString();

        GlStateManager.pushMatrix();
        GlStateManager.translate(centreX, bottomY, 0.0f);
        GlStateManager.scale(size.asFloat(), size.asFloat(), 1.0f);
        float width = Fonts.SMALL.getWidth(label) + 8.0f;
        float height = Fonts.SMALL.getHeight() + 3.0f;
        RenderUtil.roundedRect(-width / 2.0f, -height, width, height, 2.5, RenderUtil.withAlpha(Theme.panel(), 190));
        RenderUtil.rect(-width / 2.0f + 2.0f, -1.0f, width - 4.0f, 1.0f, EntityColours.of(player));

        float textX = -width / 2.0f + 4.0f;
        float textY = -height + 1.5f;
        float nameEnd = Fonts.SMALL.drawString(player.getName(), textX, textY, EntityColours.of(player));
        String rest = label.substring(player.getName().length());
        if (!rest.isEmpty()) {
            float fraction = player.getHealth() / player.getMaxHealth();
            Fonts.SMALL.drawString(rest, nameEnd, textY, healthShown.value() ? EntityColours.health(fraction) : Theme.textMuted());
        }
        if (gear.value()) {
            drawGear(player, -height - 17.0f);
        }
        GlStateManager.popMatrix();
    }

    private void drawGear(EntityPlayer player, float y) {
        List<ItemStack> items = new ArrayList<ItemStack>();
        if (player.getHeldItem() != null) {
            items.add(player.getHeldItem());
        }
        for (int slot = 3; slot >= 0; slot--) {
            if (player.inventory.armorInventory[slot] != null) {
                items.add(player.inventory.armorInventory[slot]);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        float x = -items.size() * 8.0f;
        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        for (ItemStack stack : items) {
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, (int) x, (int) y);
            mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, (int) x, (int) y);
            x += 16.0f;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
        GlStateManager.enableBlend();
    }
}
