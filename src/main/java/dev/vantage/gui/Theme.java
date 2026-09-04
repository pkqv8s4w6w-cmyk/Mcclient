package dev.vantage.gui;

import dev.vantage.gui.render.RenderUtil;
import dev.vantage.setting.ColorSetting;

/**
 * The client's palette.
 *
 * <p>Every colour the interface draws comes from here, so nothing is hardcoded at a call site and
 * changing the accent restyles the whole client live. The neutrals are fixed dark greys; only the
 * accent is user-facing, which is what keeps a custom colour from ever producing an unreadable
 * combination.
 */
public final class Theme {

    private Theme() {
    }

    private static final int DEFAULT_ACCENT = 0xFF4C8DFF;

    /**
     * Read live rather than copied, so an animated accent sweeps the whole interface without
     * every call site having to remember to refresh it first.
     */
    private static ColorSetting accentSetting;

    public static void bindAccent(ColorSetting setting) {
        accentSetting = setting;
    }

    public static int accent() {
        return accentSetting == null ? DEFAULT_ACCENT : accentSetting.display();
    }

    // Surfaces, darkest to lightest.
    public static final int BACKDROP = 0xC0090A0C;
    public static final int PANEL = 0xFF15161A;
    public static final int PANEL_RAISED = 0xFF1C1E24;
    public static final int PANEL_HOVER = 0xFF23262D;
    public static final int RAIL = 0xFF101116;

    public static final int BORDER = 0x26FFFFFF;
    public static final int DIVIDER = 0x14FFFFFF;

    // Text.
    public static final int TEXT = 0xFFF2F3F5;
    public static final int TEXT_MUTED = 0xFF9AA0AA;
    public static final int TEXT_FAINT = 0xFF5F646E;

    // Semantic colours for the threat list and detector.
    public static final int DANGER = 0xFFFF5A5A;
    public static final int WARNING = 0xFFFFBB3D;
    public static final int SAFE = 0xFF4ADE80;

    public static final double CORNER_RADIUS = 5.0;
    public static final double PANEL_RADIUS = 8.0;

    public static int accentDim() {
        return RenderUtil.withAlpha(accent(), 0.22f);
    }

    public static int accentHover() {
        return RenderUtil.shift(accent(), 0.12);
    }

    /**
     * Grades a 0-10 threat score from green through amber to red.
     *
     * <p>Deliberately not a plain two-colour blend: going straight green to red passes through a
     * muddy brown around the midpoint, which is exactly where the interesting scores sit.
     */
    public static int threatColour(double score) {
        double clamped = Math.max(0.0, Math.min(10.0, score));
        if (clamped <= 5.0) {
            return RenderUtil.blend(SAFE, WARNING, clamped / 5.0);
        }
        return RenderUtil.blend(WARNING, DANGER, (clamped - 5.0) / 5.0);
    }
}
