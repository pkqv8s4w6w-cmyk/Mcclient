package dev.vantage.hud.impl;

import dev.vantage.event.ClickEvent;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.hud.HudModule;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.util.CpsMeter;

/** Your own clicks per second. Purely a readout; the detector is a separate thing entirely. */
public class CpsHud extends HudModule {

    private final BooleanSetting showRight = register(new BooleanSetting(
            "Right Button", "Also count right clicks", true));

    private final CpsMeter left = new CpsMeter();
    private final CpsMeter right = new CpsMeter();

    public CpsHud() {
        super("CPS", "Shows your clicks per second");
        // Counted from the clicks the game handles. Sampling the button once a tick, as this used
        // to, missed any click shorter than a tick and could never read above ten.
        on(ClickEvent.class, event -> (event.isLeft() ? left : right).click(System.currentTimeMillis()));
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
        Fonts.SMALL.drawString(text(), 0.0f, 0.0f, Theme.text());
    }
}
