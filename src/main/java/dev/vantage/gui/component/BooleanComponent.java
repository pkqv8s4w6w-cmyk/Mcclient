package dev.vantage.gui.component;

import dev.vantage.gui.Animated;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.setting.BooleanSetting;

/** A sliding switch. The knob and the track colour animate together. */
public class BooleanComponent extends SettingComponent {

    private static final float TRACK_WIDTH = 18.0f;
    private static final float TRACK_HEIGHT = 9.0f;

    private final BooleanSetting typed;
    private final Animated knob;

    public BooleanComponent(BooleanSetting setting) {
        super(setting);
        this.typed = setting;
        this.knob = new Animated(typed.value() ? 1.0 : 0.0, 0.07);
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

        Fonts.SMALL.drawString(typed.getName(), x + LABEL_INSET, y + 2.0f,
                typed.value() ? Theme.TEXT : Theme.TEXT_MUTED);

        knob.setTarget(typed.value() ? 1.0 : 0.0);
        double progress = knob.get();

        float trackX = x + width - TRACK_WIDTH - LABEL_INSET;
        float trackY = y + (ROW_HEIGHT - TRACK_HEIGHT) / 2.0f;

        int trackColour = RenderUtil.blend(0xFF2A2E36, Theme.accent(), progress);
        RenderUtil.roundedRect(trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT / 2.0, trackColour);

        float travel = TRACK_WIDTH - TRACK_HEIGHT;
        float knobX = trackX + TRACK_HEIGHT / 2.0f + (float) (travel * progress);
        RenderUtil.circle(knobX, trackY + TRACK_HEIGHT / 2.0f, TRACK_HEIGHT / 2.0f - 1.5f, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY, ROW_HEIGHT)) {
            typed.toggle();
            return true;
        }
        return false;
    }
}
