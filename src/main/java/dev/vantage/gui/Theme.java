package dev.vantage.gui;

import dev.vantage.gui.render.RenderUtil;
import dev.vantage.setting.ColorSetting;

/**
 * The client's palette and spacing rhythm.
 *
 * <p>Every colour is read through a method rather than a constant, and each one can be bound to a
 * user setting. Binding rather than copying means dragging a colour picker restyles the whole
 * client on the next frame, and an animated colour keeps animating, without any call site having
 * to remember to refresh.
 *
 * <p>Unbound colours fall back to the defaults below, so the theme works before the ClickGUI module
 * has been constructed.
 */
public final class Theme {

    private Theme() {
    }

    // -- defaults ---------------------------------------------------------------------------

    private static final int DEFAULT_ACCENT = 0xFF4C8DFF;
    private static final int DEFAULT_PANEL = 0xFF0E0F12;
    private static final int DEFAULT_RAIL = 0xFF0A0B0D;
    private static final int DEFAULT_ROW = 0xFF16181D;
    private static final int DEFAULT_TEXT = 0xFFE8EAED;
    private static final int DEFAULT_SAFE = 0xFF4ADE80;
    private static final int DEFAULT_DANGER = 0xFFFF5A5A;

    // -- bindings ---------------------------------------------------------------------------

    private static ColorSetting accentSetting;
    private static ColorSetting panelSetting;
    private static ColorSetting rowSetting;
    private static ColorSetting textSetting;
    private static ColorSetting safeSetting;
    private static ColorSetting dangerSetting;

    public static void bindAccent(ColorSetting setting) {
        accentSetting = setting;
    }

    public static void bindPanel(ColorSetting setting) {
        panelSetting = setting;
    }

    public static void bindRow(ColorSetting setting) {
        rowSetting = setting;
    }

    public static void bindText(ColorSetting setting) {
        textSetting = setting;
    }

    /** The two ends of the threat gradient: safe at 0, dangerous at 10. */
    public static void bindThreatRange(ColorSetting safe, ColorSetting danger) {
        safeSetting = safe;
        dangerSetting = danger;
    }

    private static int read(ColorSetting setting, int fallback) {
        return setting == null ? fallback : setting.display();
    }

    // -- surfaces ---------------------------------------------------------------------------

    public static int accent() {
        return read(accentSetting, DEFAULT_ACCENT);
    }

    public static int panel() {
        return read(panelSetting, DEFAULT_PANEL);
    }

    /** The category strip, a shade below the panel so the two read as separate planes. */
    public static int rail() {
        return panelSetting == null ? DEFAULT_RAIL : RenderUtil.shift(panel(), -0.25);
    }

    public static int row() {
        return read(rowSetting, DEFAULT_ROW);
    }

    public static int rowHover() {
        return RenderUtil.shift(row(), 0.06);
    }

    /** Dimmed backdrop behind the window. */
    public static int backdrop() {
        return RenderUtil.withAlpha(RenderUtil.shift(panel(), -0.4), 190);
    }

    // -- text -------------------------------------------------------------------------------

    public static int text() {
        return read(textSetting, DEFAULT_TEXT);
    }

    /** Labels and secondary values. */
    public static int textMuted() {
        return RenderUtil.blend(text(), panel(), 0.45);
    }

    /** Hints and disabled states. */
    public static int textFaint() {
        return RenderUtil.blend(text(), panel(), 0.68);
    }

    // -- lines ------------------------------------------------------------------------------

    /** Hairline separators. Deliberately weaker than a border. */
    public static int divider() {
        return RenderUtil.withAlpha(text(), 20);
    }

    public static int border() {
        return RenderUtil.withAlpha(text(), 34);
    }

    public static int accentDim() {
        return RenderUtil.withAlpha(accent(), 0.22f);
    }

    public static int accentHover() {
        return RenderUtil.shift(accent(), 0.12);
    }

    // -- semantic ---------------------------------------------------------------------------

    public static int safe() {
        return read(safeSetting, DEFAULT_SAFE);
    }

    public static int danger() {
        return read(dangerSetting, DEFAULT_DANGER);
    }

    /**
     * Grades a 0-10 threat score from the safe colour to the dangerous one.
     *
     * <p>Passes through a bright midpoint rather than blending the ends directly: going straight
     * from green to red crosses a muddy brown at exactly the scores that matter most.
     */
    public static int threatColour(double score) {
        double clamped = Math.max(0.0, Math.min(10.0, score));
        int middle = RenderUtil.blend(safe(), danger(), 0.5);
        // Lift the midpoint's brightness so it reads as amber rather than sludge.
        middle = RenderUtil.shift(middle, 0.25);
        return clamped <= 5.0
                ? RenderUtil.blend(safe(), middle, clamped / 5.0)
                : RenderUtil.blend(middle, danger(), (clamped - 5.0) / 5.0);
    }

    /** The amber midpoint of the threat range, for cautionary text. */
    public static int warning() {
        return threatColour(5.0);
    }

    // -- geometry ---------------------------------------------------------------------------

    /**
     * The single spacing unit. Every gap, inset and padding in the interface is a multiple of this,
     * which is most of what makes a layout look deliberate rather than assembled.
     */
    public static final float UNIT = 4.0f;

    public static final double PANEL_RADIUS = 6.0;
    public static final double ROW_RADIUS = 4.0;
    public static final double CHIP_RADIUS = 3.0;
}
