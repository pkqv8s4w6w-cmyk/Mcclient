package dev.vantage.hud.impl;

import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.hud.HudModule;
import dev.vantage.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** Active potion effects and how long they have left. */
public class PotionHud extends HudModule {

    private final BooleanSetting showDuration = register(new BooleanSetting(
            "Duration", "Show time remaining", true));

    public PotionHud() {
        super("Potions", "Shows your active effects");
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<String>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return lines;
        }
        for (PotionEffect effect : mc.thePlayer.getActivePotionEffects()) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (potion == null) {
                continue;
            }
            StringBuilder line = new StringBuilder(I18n.format(potion.getName()));
            // Amplifier is zero-based, so level II arrives as 1.
            if (effect.getAmplifier() > 0) {
                line.append(' ').append(roman(effect.getAmplifier() + 1));
            }
            if (showDuration.value()) {
                line.append(" §7").append(StringUtils.ticksToElapsedTime(effect.getDuration()));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static String roman(int value) {
        switch (value) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(value);
        }
    }

    @Override
    public float getContentWidth() {
        float widest = Fonts.SMALL.getWidth("No effects");
        for (String line : lines()) {
            widest = Math.max(widest, Fonts.SMALL.getWidth(line));
        }
        return widest;
    }

    @Override
    public float getContentHeight() {
        return Math.max(1, lines().size()) * (Fonts.SMALL.getHeight() - 1.0f);
    }

    @Override
    protected void renderContent() {
        List<String> lines = lines();
        if (lines.isEmpty()) {
            Fonts.SMALL.drawString("No effects", 0.0f, 0.0f, Theme.TEXT_FAINT);
            return;
        }
        float y = 0.0f;
        for (String line : lines) {
            Fonts.SMALL.drawString(line, 0.0f, y, Theme.TEXT);
            y += Fonts.SMALL.getHeight() - 1.0f;
        }
    }
}
