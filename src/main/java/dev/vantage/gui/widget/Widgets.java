package dev.vantage.gui.widget;

import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;

/**
 * Drawing for the small controls the menu repeats everywhere: buttons, chips and switches.
 *
 * <p>Immediate mode: each call draws and returns the bounds it used, and the caller keeps them for
 * hit testing on the next click. Nothing here holds state.
 */
public final class Widgets {

    public enum Style { PRIMARY, GHOST, DANGER }

    public static final float BUTTON_HEIGHT = 16.0f;
    public static final float SWITCH_WIDTH = 20.0f;
    public static final float SWITCH_HEIGHT = 11.0f;

    private Widgets() {
    }

    /** Bounds of something drawn, for hit testing. */
    public static final class Rect {
        public final float x;
        public final float y;
        public final float width;
        public final float height;

        public Rect(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean contains(float px, float py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    public static float buttonWidth(String label, char icon) {
        float width = Fonts.SMALL_BOLD.getWidth(label) + 16.0f;
        if (icon != 0) {
            width += 12.0f;
        }
        return width;
    }

    /**
     * A pill button with its right edge at {@code right}.
     *
     * @param icon a Lucide codepoint, or 0 for none
     */
    public static Rect buttonRightAligned(String label, char icon, float right, float y, Style style,
                                          float mouseX, float mouseY) {
        float width = buttonWidth(label, icon);
        return button(label, icon, right - width, y, style, mouseX, mouseY);
    }

    public static Rect button(String label, char icon, float x, float y, Style style, float mouseX, float mouseY) {
        float width = buttonWidth(label, icon);
        Rect bounds = new Rect(x, y, width, BUTTON_HEIGHT);
        boolean hovered = bounds.contains(mouseX, mouseY);
        int background;
        int foreground;
        switch (style) {
            case PRIMARY:
                background = hovered ? Theme.accentHover() : Theme.accent();
                foreground = 0xFFFFFFFF;
                break;
            case DANGER:
                background = hovered ? RenderUtil.withAlpha(Theme.danger(), 60) : RenderUtil.withAlpha(Theme.danger(), 28);
                foreground = Theme.danger();
                break;
            default:
                background = hovered ? Theme.rowHover() : Theme.row();
                foreground = hovered ? Theme.text() : Theme.textMuted();
        }
        RenderUtil.roundedRect(x, y, width, BUTTON_HEIGHT, 5.0, background);
        if (style == Style.GHOST) {
            RenderUtil.roundedOutline(x, y, width, BUTTON_HEIGHT, 5.0, 1.0f, Theme.border());
        }
        float textX = x + 8.0f;
        if (icon != 0) {
            Fonts.ICONS.drawString(String.valueOf(icon), textX, y + (BUTTON_HEIGHT - Fonts.ICONS.getHeight()) / 2.0f + 0.5f, foreground);
            textX += 12.0f;
        }
        Fonts.SMALL_BOLD.drawString(label, textX, y + (BUTTON_HEIGHT - Fonts.SMALL_BOLD.getHeight()) / 2.0f, foreground);
        return bounds;
    }

    /** A small rounded tag. @return its width */
    public static float chip(String label, float x, float y, int colour) {
        float width = Fonts.TINY.getWidth(label) + 8.0f;
        RenderUtil.roundedRect(x, y, width, 10.0f, 3.0, RenderUtil.withAlpha(colour, 40));
        Fonts.TINY.drawString(label, x + 4.0f, y + 1.5f, colour);
        return width;
    }

    /** A sliding on/off switch. {@code progress} runs 0 (off) to 1 (on) so it can animate. */
    public static Rect toggle(float x, float y, double progress) {
        int track = RenderUtil.blend(RenderUtil.shift(Theme.row(), 0.12), Theme.accent(), progress);
        RenderUtil.roundedRect(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2.0, track);
        float travel = SWITCH_WIDTH - SWITCH_HEIGHT;
        float knobX = x + SWITCH_HEIGHT / 2.0f + (float) (travel * progress);
        RenderUtil.circle(knobX, y + SWITCH_HEIGHT / 2.0f, SWITCH_HEIGHT / 2.0f - 1.6f, 0xFFFFFFFF);
        return new Rect(x, y, SWITCH_WIDTH, SWITCH_HEIGHT);
    }

    /** An icon-only square button. */
    public static Rect iconButton(char icon, float x, float y, int colour, float mouseX, float mouseY) {
        Rect bounds = new Rect(x, y, BUTTON_HEIGHT, BUTTON_HEIGHT);
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (hovered) {
            RenderUtil.roundedRect(x, y, BUTTON_HEIGHT, BUTTON_HEIGHT, 5.0, Theme.rowHover());
        }
        Fonts.ICONS.drawCentred(String.valueOf(icon), x + BUTTON_HEIGHT / 2.0f,
                y + (BUTTON_HEIGHT - Fonts.ICONS.getHeight()) / 2.0f + 0.5f, hovered ? colour : Theme.textMuted());
        return bounds;
    }

    /** A card background: the rounded panel every list item sits on. */
    public static void card(float x, float y, float width, float height, boolean hovered) {
        RenderUtil.roundedRect(x, y, width, height, 7.0, hovered ? Theme.rowHover() : Theme.row());
    }

    /** The faint upper-case label above a group, as in the sidebar. */
    public static void sectionLabel(String label, float x, float y) {
        Fonts.TINY.drawString(label, x, y, Theme.textFaint());
    }
}
