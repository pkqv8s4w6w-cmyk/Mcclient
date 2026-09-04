package dev.vantage.gui.component;

import dev.vantage.gui.Animated;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.setting.ColorSetting;

import java.awt.Color;

/**
 * A swatch that opens a picker inline beneath it.
 *
 * <p>Saturation and value on the square, hue and alpha on their own strips. The picker is part of
 * the row's height rather than an overlay, so it scrolls with everything else.
 */
public class ColourComponent extends SettingComponent {

    private static final float SQUARE_SIZE = 46.0f;
    private static final float STRIP_WIDTH = 7.0f;
    private static final float GAP = 5.0f;
    private static final float PICKER_HEIGHT = SQUARE_SIZE + 22.0f;

    private final ColorSetting typed;
    private final Animated expansion = new Animated(0.0, 0.10);

    private boolean expanded;
    private int dragTarget = -1; // 0 square, 1 hue, 2 alpha

    private float hue;
    private float saturation;
    private float brightness;

    public ColourComponent(ColorSetting setting) {
        super(setting);
        this.typed = setting;
        readBackHsb();
    }

    private void readBackHsb() {
        float[] hsb = Color.RGBtoHSB(typed.getRed(), typed.getGreen(), typed.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    private void writeHsb() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        typed.setRgb(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
    }

    @Override
    public float getHeight() {
        expansion.setTarget(expanded ? 1.0 : 0.0);
        return ROW_HEIGHT + (float) (PICKER_HEIGHT * expansion.get());
    }

    private float squareX() {
        return x + LABEL_INSET;
    }

    private float squareY() {
        return y + ROW_HEIGHT + 3.0f;
    }

    private float hueX() {
        return squareX() + SQUARE_SIZE + GAP;
    }

    private float alphaX() {
        return hueX() + STRIP_WIDTH + GAP;
    }

    @Override
    public void render(float mouseX, float mouseY) {
        if (dragTarget >= 0) {
            updateFromDrag(mouseX, mouseY);
        }

        Fonts.SMALL.drawString(typed.getName(), x + LABEL_INSET, y + 2.0f, Theme.TEXT_MUTED);

        float swatchSize = 9.0f;
        float swatchX = x + width - LABEL_INSET - swatchSize;
        float swatchY = y + (ROW_HEIGHT - swatchSize) / 2.0f;
        // Checkerboard behind the swatch so a translucent colour is obviously translucent.
        RenderUtil.roundedRect(swatchX, swatchY, swatchSize, swatchSize, 2.5, 0xFF3A3F47);
        RenderUtil.roundedRect(swatchX, swatchY, swatchSize / 2.0f, swatchSize, 2.5, 0xFF20232A);
        RenderUtil.roundedRect(swatchX, swatchY, swatchSize, swatchSize, 2.5, typed.display());
        RenderUtil.roundedOutline(swatchX, swatchY, swatchSize, swatchSize, 2.5, 1.0f, 0x40FFFFFF);

        float openness = (float) expansion.get();
        if (openness < 0.01f) {
            return;
        }

        int fade = (int) (255 * openness);
        drawSaturationValueSquare(fade);
        drawHueStrip(fade);
        drawAlphaStrip(fade);

        String readout = String.format("#%06X", typed.get() & 0xFFFFFF);
        Fonts.TINY.drawString(readout, squareX(), squareY() + SQUARE_SIZE + 4.0f,
                RenderUtil.withAlpha(Theme.TEXT_MUTED, fade));

        // Rainbow toggle sits with the picker, since it only matters while choosing a colour.
        float toggleX = alphaX() + STRIP_WIDTH + GAP + 2.0f;
        boolean rainbowHovered = mouseX >= toggleX && mouseX <= toggleX + 46.0f
                && mouseY >= squareY() && mouseY <= squareY() + 12.0f;
        int rainbowColour = typed.isRainbow() ? Theme.accent() : 0xFF2A2E36;
        RenderUtil.roundedRect(toggleX, squareY(), 46.0f, 12.0f, 3.0,
                RenderUtil.withAlpha(rainbowHovered ? RenderUtil.shift(rainbowColour, 0.1) : rainbowColour, fade));
        Fonts.TINY.drawCentred("Rainbow", toggleX + 23.0f, squareY() + 2.0f,
                RenderUtil.withAlpha(typed.isRainbow() ? 0xFFFFFFFF : Theme.TEXT_MUTED, fade));
    }

    private void drawSaturationValueSquare(int fade) {
        int pure = 0xFF000000 | (Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF);
        RenderUtil.rect(squareX(), squareY(), SQUARE_SIZE, SQUARE_SIZE, RenderUtil.withAlpha(pure, fade));
        // White on the left fading out, then black rising from the bottom: standard SV square.
        RenderUtil.horizontalGradientRect(squareX(), squareY(), SQUARE_SIZE, SQUARE_SIZE,
                RenderUtil.withAlpha(0xFFFFFFFF, fade), 0x00FFFFFF);
        RenderUtil.gradientRect(squareX(), squareY(), SQUARE_SIZE, SQUARE_SIZE,
                0x00000000, RenderUtil.withAlpha(0xFF000000, fade));

        float markerX = squareX() + saturation * SQUARE_SIZE;
        float markerY = squareY() + (1.0f - brightness) * SQUARE_SIZE;
        RenderUtil.circle(markerX, markerY, 2.6f, RenderUtil.withAlpha(0xFF000000, fade));
        RenderUtil.circle(markerX, markerY, 1.8f, RenderUtil.withAlpha(0xFFFFFFFF, fade));
    }

    private void drawHueStrip(int fade) {
        int segments = 6;
        float segmentHeight = SQUARE_SIZE / segments;
        for (int i = 0; i < segments; i++) {
            int top = 0xFF000000 | (Color.HSBtoRGB(i / (float) segments, 1.0f, 1.0f) & 0xFFFFFF);
            int bottom = 0xFF000000 | (Color.HSBtoRGB((i + 1) / (float) segments, 1.0f, 1.0f) & 0xFFFFFF);
            RenderUtil.gradientRect(hueX(), squareY() + i * segmentHeight, STRIP_WIDTH, segmentHeight,
                    RenderUtil.withAlpha(top, fade), RenderUtil.withAlpha(bottom, fade));
        }
        float markerY = squareY() + hue * SQUARE_SIZE;
        RenderUtil.rect(hueX() - 1.0f, markerY - 1.0f, STRIP_WIDTH + 2.0f, 2.0f,
                RenderUtil.withAlpha(0xFFFFFFFF, fade));
    }

    private void drawAlphaStrip(int fade) {
        int opaque = 0xFF000000 | (typed.get() & 0xFFFFFF);
        RenderUtil.gradientRect(alphaX(), squareY(), STRIP_WIDTH, SQUARE_SIZE,
                RenderUtil.withAlpha(opaque, fade), RenderUtil.withAlpha(opaque & 0x00FFFFFF, fade));
        float markerY = squareY() + (1.0f - typed.getAlpha() / 255.0f) * SQUARE_SIZE;
        RenderUtil.rect(alphaX() - 1.0f, markerY - 1.0f, STRIP_WIDTH + 2.0f, 2.0f,
                RenderUtil.withAlpha(0xFFFFFFFF, fade));
    }

    private void updateFromDrag(float mouseX, float mouseY) {
        float verticalFraction = clamp01((mouseY - squareY()) / SQUARE_SIZE);
        if (dragTarget == 0) {
            saturation = clamp01((mouseX - squareX()) / SQUARE_SIZE);
            brightness = 1.0f - verticalFraction;
            writeHsb();
        } else if (dragTarget == 1) {
            hue = verticalFraction;
            writeHsb();
        } else if (dragTarget == 2) {
            typed.setAlpha(Math.round((1.0f - verticalFraction) * 255.0f));
        }
    }

    private static float clamp01(float value) {
        return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button == 0 && mouseY >= y && mouseY <= y + ROW_HEIGHT && mouseX >= x && mouseX <= x + width) {
            expanded = !expanded;
            return true;
        }
        if (!expanded || expansion.get() < 0.5) {
            return false;
        }
        boolean withinPickerRows = mouseY >= squareY() && mouseY <= squareY() + SQUARE_SIZE;
        if (button == 0 && withinPickerRows) {
            if (mouseX >= squareX() && mouseX <= squareX() + SQUARE_SIZE) {
                dragTarget = 0;
            } else if (mouseX >= hueX() && mouseX <= hueX() + STRIP_WIDTH) {
                dragTarget = 1;
            } else if (mouseX >= alphaX() && mouseX <= alphaX() + STRIP_WIDTH) {
                dragTarget = 2;
            }
            if (dragTarget >= 0) {
                updateFromDrag(mouseX, mouseY);
                return true;
            }
        }
        float toggleX = alphaX() + STRIP_WIDTH + GAP + 2.0f;
        if (button == 0 && mouseX >= toggleX && mouseX <= toggleX + 46.0f
                && mouseY >= squareY() && mouseY <= squareY() + 12.0f) {
            typed.setRainbow(!typed.isRainbow());
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int button) {
        dragTarget = -1;
    }
}
