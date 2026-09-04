package dev.vantage.hud.impl;

import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.Theme;
import dev.vantage.hud.HudModule;
import dev.vantage.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Your armour and held item, with durability. */
public class ArmourHud extends HudModule {

    private static final float ICON_SIZE = 16.0f;
    private static final float GAP = 2.0f;

    private final BooleanSetting includeHeld = register(new BooleanSetting(
            "Held Item", "Also show what you are holding", true));
    private final BooleanSetting vertical = register(new BooleanSetting(
            "Vertical", "Stack the icons instead of laying them out in a row", false));

    public ArmourHud() {
        super("Armour", "Shows your armour and its durability");
    }

    /** Helmet first, then down the body, then the held item. */
    private List<ItemStack> pieces() {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return stacks;
        }
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack stack = mc.thePlayer.inventory.armorInventory[slot];
            if (stack != null) {
                stacks.add(stack);
            }
        }
        if (includeHeld.value()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held != null) {
                stacks.add(held);
            }
        }
        return stacks;
    }

    @Override
    public float getContentWidth() {
        int count = Math.max(1, pieces().size());
        return vertical.value() ? ICON_SIZE : count * ICON_SIZE + (count - 1) * GAP;
    }

    @Override
    public float getContentHeight() {
        int count = Math.max(1, pieces().size());
        return vertical.value() ? count * ICON_SIZE + (count - 1) * GAP : ICON_SIZE;
    }

    @Override
    protected void renderContent() {
        List<ItemStack> stacks = pieces();
        if (stacks.isEmpty()) {
            Fonts.TINY.drawString("no armour", 0.0f, 4.0f, Theme.TEXT_FAINT);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        // Item rendering needs its own lighting and depth setup, and must put it back afterwards
        // or the rest of the interface renders unlit.
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();

        float x = 0.0f;
        float y = 0.0f;
        for (ItemStack stack : stacks) {
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, (int) x, (int) y);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, stack, (int) x, (int) y, null);
            if (vertical.value()) {
                y += ICON_SIZE + GAP;
            } else {
                x += ICON_SIZE + GAP;
            }
        }

        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
