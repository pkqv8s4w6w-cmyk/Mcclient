package dev.vantage.gui.component;

import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.setting.EnumSetting;

/**
 * Cycles through an enum's constants.
 *
 * <p>Click advances, right-click goes back. A floating dropdown would look tidier, but it would
 * have to paint outside the scroll clip that contains this row, and the seams that produces are
 * worse than the extra clicks.
 */
public class EnumComponent extends SettingComponent {

    private final EnumSetting<?> typed;

    public EnumComponent(EnumSetting<?> setting) {
        super(setting);
        this.typed = setting;
    }

    @Override
    public float getHeight() {
        return ROW_HEIGHT;
    }

    @Override
    public void render(float mouseX, float mouseY) {
        boolean hovered = isHovered(mouseX, mouseY, ROW_HEIGHT);
        if (hovered) {
            RenderUtil.roundedRect(x, y, width, ROW_HEIGHT, 3.0, 0x0AFFFFFF);
        }

        Fonts.SMALL.drawString(typed.getName(), x + LABEL_INSET, y + 2.0f, Theme.textMuted());

        String value = typed.currentLabel();
        float right = x + width - LABEL_INSET;
        // A caret hints that the value is clickable rather than just a readout.
        Fonts.TINY.drawRightAligned("▼", right, y + 3.5f, hovered ? Theme.accent() : Theme.textFaint());
        Fonts.SMALL.drawRightAligned(value, right - 8.0f, y + 2.0f, hovered ? Theme.text() : Theme.accent());
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!isHovered(mouseX, mouseY, ROW_HEIGHT)) {
            return false;
        }
        if (button == 0) {
            typed.cycle();
            return true;
        }
        if (button == 1) {
            // Cycle backwards by going all the way round, which avoids a second API on the typed.
            int count = typed.getOptions().length;
            for (int i = 0; i < count - 1; i++) {
                typed.cycle();
            }
            return true;
        }
        return false;
    }
}
