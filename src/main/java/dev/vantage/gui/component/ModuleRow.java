package dev.vantage.gui.component;

import dev.vantage.gui.Animated;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Module;
import dev.vantage.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * One module in the list: a name that toggles, and its settings expanding underneath.
 *
 * <p>Left click toggles, right click opens the settings. That split is the convention in this kind
 * of client and keeps the common action to one click.
 *
 * <p>Deliberately carries no description text. A subtitle on every row is what made the list read
 * as a wall rather than a list; the description surfaces as a tooltip after a short hover instead,
 * which the parent screen draws so it can sit above everything else.
 */
public class ModuleRow {

    public static final float HEADER_HEIGHT = 19.0f;

    /** How long the cursor must rest on a row before its description appears. */
    private static final long TOOLTIP_DELAY_MILLIS = 450L;

    private final Module module;
    private final List<SettingComponent> components = new ArrayList<SettingComponent>();

    private final Animated expansion = new Animated(0.0, 0.055);
    private final Animated lit;
    private final Animated hoverGlow = new Animated(0.0, 0.04);

    private boolean expanded;
    private boolean hovered;
    private long hoverSince;

    private float x;
    private float y;
    private float width;

    public ModuleRow(Module module) {
        this.module = module;
        this.lit = new Animated(module.isEnabled() ? 1.0 : 0.0, 0.045);
        for (Setting<?> setting : module.getSettings()) {
            SettingComponent component = SettingComponent.create(setting);
            if (component != null) {
                components.add(component);
            }
        }
    }

    public Module getModule() {
        return module;
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public float getY() {
        return y;
    }

    /** True while the cursor has rested long enough that a tooltip is warranted. */
    public boolean wantsTooltip() {
        return hovered
                && !module.getDescription().isEmpty()
                && System.currentTimeMillis() - hoverSince > TOOLTIP_DELAY_MILLIS;
    }

    public String getTooltip() {
        return module.getDescription();
    }

    private float settingsHeight() {
        if (components.isEmpty()) {
            return 0.0f;
        }
        float total = Theme.UNIT;
        for (SettingComponent component : components) {
            if (component.getSetting().isVisible()) {
                total += component.getHeight();
            }
        }
        return total + Theme.UNIT;
    }

    public float getHeight() {
        expansion.setTarget(expanded && !components.isEmpty() ? 1.0 : 0.0);
        return HEADER_HEIGHT + (float) (settingsHeight() * expansion.get());
    }

    public void render(float mouseX, float mouseY) {
        boolean nowHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        if (nowHovered && !hovered) {
            hoverSince = System.currentTimeMillis();
        }
        hovered = nowHovered;

        lit.setTarget(module.isEnabled() ? 1.0 : 0.0);
        hoverGlow.setTarget(nowHovered ? 1.0 : 0.0);
        double on = lit.get();
        double hover = hoverGlow.get();

        float totalHeight = getHeight();

        // Base row, lifted on hover and tinted toward the accent when the module is on.
        int background = RenderUtil.blend(Theme.row(), Theme.rowHover(), hover);
        background = RenderUtil.blend(background, Theme.accentDim(), on * 0.9);
        RenderUtil.roundedRect(x, y, width, totalHeight, Theme.ROW_RADIUS, background);

        // A bar down the left edge reads far faster than a colour change alone.
        if (on > 0.01) {
            RenderUtil.roundedRect(x, y + 4.0f, 2.0f, HEADER_HEIGHT - 8.0f, 1.0,
                    RenderUtil.withAlpha(Theme.accent(), (float) on));
        }

        int nameColour = RenderUtil.blend(Theme.textMuted(), Theme.text(), on);
        float textY = y + (HEADER_HEIGHT - Fonts.BODY.getHeight()) / 2.0f;
        Fonts.BODY.drawString(module.getName(), x + Theme.UNIT * 2.0f, textY, nameColour);

        if (!components.isEmpty()) {
            // The glyph swaps rather than rotating; a baked atlas cannot spin.
            Fonts.TINY.drawRightAligned(expansion.get() > 0.5 ? "▲" : "▼",
                    x + width - Theme.UNIT * 2.0f, y + (HEADER_HEIGHT - Fonts.TINY.getHeight()) / 2.0f,
                    nowHovered ? Theme.textMuted() : Theme.textFaint());
        }

        float openness = (float) expansion.get();
        if (openness < 0.01f || components.isEmpty()) {
            return;
        }

        // A rule down the settings group ties them to their module rather than leaving them
        // floating as separate rows.
        float groupTop = y + HEADER_HEIGHT;
        float groupHeight = totalHeight - HEADER_HEIGHT;
        RenderUtil.rect(x + Theme.UNIT * 2.0f, groupTop, 1.0f, groupHeight,
                RenderUtil.withAlpha(Theme.accent(), 0.25f * openness));

        float cursor = groupTop + Theme.UNIT;
        float indent = Theme.UNIT * 2.5f;
        for (SettingComponent component : components) {
            if (!component.getSetting().isVisible()) {
                continue;
            }
            component.setBounds(x + indent, cursor, width - indent);
            component.render(mouseX, mouseY);
            cursor += component.getHeight();
        }
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        boolean onHeader = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        if (onHeader) {
            if (button == 0) {
                module.toggle();
                return true;
            }
            if (button == 1 && !components.isEmpty()) {
                expanded = !expanded;
                return true;
            }
            return false;
        }
        if (expansion.get() < 0.5) {
            return false;
        }
        for (SettingComponent component : components) {
            if (component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(int button) {
        for (SettingComponent component : components) {
            component.mouseReleased(button);
        }
    }

    public boolean keyTyped(char typedCharacter, int keyCode) {
        for (SettingComponent component : components) {
            if (component.keyTyped(typedCharacter, keyCode)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCapturingInput() {
        for (SettingComponent component : components) {
            if (component.isCapturingInput()) {
                return true;
            }
        }
        return false;
    }

    public void collapse() {
        expanded = false;
    }
}
