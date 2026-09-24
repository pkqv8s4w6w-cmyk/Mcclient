package dev.vantage.hud.impl;

import dev.vantage.Vantage;
import dev.vantage.gui.Icons;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.hud.HudModule;
import dev.vantage.setting.BooleanSetting;
import net.minecraft.client.Minecraft;

/** The client name with frame rate and the active profile beside it. */
public class WatermarkHud extends HudModule {

    private final BooleanSetting details = register(new BooleanSetting(
            "Details", "Show frame rate and profile", true));

    public WatermarkHud() {
        super("Watermark", "Client name, frame rate and profile");
    }

    private String detailText() {
        return Minecraft.getDebugFPS() + " fps  •  " + Vantage.instance().getActiveProfile();
    }

    @Override
    public float getContentWidth() {
        float width = 12.0f + Fonts.TITLE.getWidth(Vantage.MOD_NAME);
        if (details.value()) {
            width += 6.0f + Fonts.SMALL.getWidth(detailText());
        }
        return width;
    }

    @Override
    public float getContentHeight() {
        return Fonts.TITLE.getHeight();
    }

    @Override
    protected void renderContent() {
        Fonts.ICONS.drawString(String.valueOf(Icons.CROWN), 0.0f, 1.5f, Theme.accent());
        float x = Fonts.TITLE.drawString(Vantage.MOD_NAME, 12.0f, 0.0f, Theme.text());
        if (details.value()) {
            Fonts.SMALL.drawString(detailText(), x + 6.0f,
                    (Fonts.TITLE.getHeight() - Fonts.SMALL.getHeight()) / 2.0f + 0.5f, Theme.textMuted());
        }
    }
}
