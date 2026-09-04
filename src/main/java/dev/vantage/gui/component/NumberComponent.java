package dev.vantage.gui.component;

import dev.vantage.gui.Animated;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.setting.NumberSetting;

/**
 * A label with its value on the right and a slider beneath.
 *
 * <p>Dragging continues to track the mouse after it leaves the row, which is what people expect
 * from a slider and is the difference between one that feels solid and one that keeps slipping.
 */
public class NumberComponent extends SettingComponent {

    private static final float TRACK_HEIGHT = 2.5f;
    private static final float ROW = 22.0f;

    private final NumberSetting typed;
    private final Animated fill;
    private boolean dragging;

    public NumberComponent(NumberSetting setting) {
        super(setting);
        this.typed = setting;
        this.fill = new Animated(typed.getFraction(), 0.05);
    }

    @Override
    public float getHeight() {
        return ROW;
    }

    private float trackX() {
        return x + LABEL_INSET;
    }

    private float trackWidth() {
        return width - LABEL_INSET * 2.0f;
    }

    @Override
    public void render(float mouseX, float mouseY) {
        if (dragging) {
            // Track horizontally regardless of vertical position, so sloppy drags still work.
            typed.setFraction((mouseX - trackX()) / trackWidth());
        }

        Fonts.SMALL.drawString(typed.getName(), x + LABEL_INSET, y + 1.0f, Theme.TEXT_MUTED);
        Fonts.SMALL.drawRightAligned(formatValue(), x + width - LABEL_INSET, y + 1.0f, Theme.TEXT);

        float trackY = y + ROW - TRACK_HEIGHT - 5.0f;
        RenderUtil.roundedRect(trackX(), trackY, trackWidth(), TRACK_HEIGHT, TRACK_HEIGHT / 2.0, 0xFF2A2E36);

        fill.setTarget(typed.getFraction());
        double progress = fill.get();
        float filledWidth = (float) (trackWidth() * progress);
        if (filledWidth > 0.5f) {
            RenderUtil.roundedRect(trackX(), trackY, filledWidth, TRACK_HEIGHT, TRACK_HEIGHT / 2.0, Theme.accent());
        }

        boolean nearTrack = isHovered(mouseX, mouseY, ROW);
        float knobRadius = dragging ? 4.0f : (nearTrack ? 3.5f : 3.0f);
        RenderUtil.circle(trackX() + filledWidth, trackY + TRACK_HEIGHT / 2.0f, knobRadius, 0xFFFFFFFF);
    }

    private String formatValue() {
        double value = typed.asDouble();
        String text = typed.isIntegral()
                ? String.valueOf((long) Math.round(value))
                : trimTrailingZeroes(value);
        return text + typed.getSuffix();
    }

    private static String trimTrailingZeroes(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY, ROW)) {
            dragging = true;
            typed.setFraction((mouseX - trackX()) / trackWidth());
            return true;
        }
        if (button == 1 && isHovered(mouseX, mouseY, ROW)) {
            typed.reset();
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int button) {
        dragging = false;
    }
}
