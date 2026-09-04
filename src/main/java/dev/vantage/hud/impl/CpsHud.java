package dev.vantage.hud.impl;

import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.hud.HudModule;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.util.CpsMeter;
import net.minecraft.client.Minecraft;

/** Your own clicks per second. Purely a readout; the detector is a separate thing entirely. */
public class CpsHud extends HudModule {

    private final BooleanSetting showRight = register(new BooleanSetting(
            "Right Button", "Also count right clicks", true));

    private final CpsMeter left = new CpsMeter();
    private final CpsMeter right = new CpsMeter();

    private boolean leftWasDown;
    private boolean rightWasDown;

    public CpsHud() {
        super("CPS", "Shows your clicks per second");
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();

        // Count the transition, not the held state, or holding the button would read as clicking.
        boolean leftDown = mc.gameSettings.keyBindAttack.isKeyDown();
        if (leftDown && !leftWasDown) {
            left.click(now);
        }
        leftWasDown = leftDown;

        boolean rightDown = mc.gameSettings.keyBindUseItem.isKeyDown();
        if (rightDown && !rightWasDown) {
            right.click(now);
        }
        rightWasDown = rightDown;
    }

    private String text() {
        long now = System.currentTimeMillis();
        return showRight.value()
                ? left.perSecond(now) + " | " + right.perSecond(now) + " cps"
                : left.perSecond(now) + " cps";
    }

    @Override
    public float getContentWidth() {
        return Fonts.SMALL.getWidth(text());
    }

    @Override
    public float getContentHeight() {
        return Fonts.SMALL.getHeight();
    }

    @Override
    protected void renderContent() {
        Fonts.SMALL.drawString(text(), 0.0f, 0.0f, Theme.TEXT);
    }
}
