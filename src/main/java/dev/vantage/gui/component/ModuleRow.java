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
 * One module in the list: a header that toggles it, and its settings expanding underneath.
 *
 * <p>Left click toggles, right click opens the settings. That split is the convention in this kind
 * of client and means the common action never costs an extra click.
 */
public class ModuleRow {

    private static final float HEADER_HEIGHT = 26.0f;
    private static final float SETTINGS_PADDING = 4.0f;

    private final Module module;
    private final List<SettingComponent> components = new ArrayList<SettingComponent>();

    private final Animated expansion = new Animated(0.0, 0.11);
    private final Animated enabledGlow;
    private boolean expanded;

    private float x;
    private float y;
    private float width;

    public ModuleRow(Module module) {
        this.module = module;
        this.enabledGlow = new Animated(module.isEnabled() ? 1.0 : 0.0, 0.08);
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

    /** Height of the settings block when fully open, counting only currently visible settings. */
    private float expandedContentHeight() {
        if (components.isEmpty()) {
            return 0.0f;
        }
        float total = SETTINGS_PADDING;
        for (SettingComponent component : components) {
            if (!component.getSetting().isVisible()) {
                continue;
            }
            total += component.getHeight();
        }
        return total + SETTINGS_PADDING;
    }

    public float getHeight() {
        expansion.setTarget(expanded && !components.isEmpty() ? 1.0 : 0.0);
        return HEADER_HEIGHT + (float) (expandedContentHeight() * expansion.get());
    }

    public void render(float mouseX, float mouseY) {
        boolean headerHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;

        enabledGlow.setTarget(module.isEnabled() ? 1.0 : 0.0);
        double lit = enabledGlow.get();

        int background = RenderUtil.blend(
                headerHovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED,
                Theme.accentDim(),
                lit * 0.85);
        RenderUtil.roundedRect(x, y, width, getHeight(), Theme.CORNER_RADIUS, background);

        // A bar down the left edge is a much faster read than a colour change alone.
        if (lit > 0.01) {
            RenderUtil.roundedRect(x, y + 5.0f, 2.5f, HEADER_HEIGHT - 10.0f, 1.25,
                    RenderUtil.withAlpha(Theme.accent(), (float) lit));
        }

        int nameColour = RenderUtil.blend(Theme.TEXT_MUTED, Theme.TEXT, lit);
        Fonts.BODY.drawString(module.getName(), x + 11.0f, y + 4.0f, nameColour);

        if (!module.getDescription().isEmpty()) {
            String description = Fonts.TINY.trimToWidth(module.getDescription(), width - 46.0f);
            Fonts.TINY.drawString(description, x + 11.0f, y + 15.0f, Theme.TEXT_FAINT);
        }

        if (!components.isEmpty()) {
            float caretX = x + width - 13.0f;
            float caretY = y + HEADER_HEIGHT / 2.0f - 3.0f;
            // Rotating the glyph is not an option with a baked atlas, so swap it instead.
            Fonts.TINY.drawCentred(expansion.get() > 0.5 ? "▲" : "▼", caretX, caretY,
                    headerHovered ? Theme.TEXT_MUTED : Theme.TEXT_FAINT);
        }

        float openness = (float) expansion.get();
        if (openness < 0.01f || components.isEmpty()) {
            return;
        }

        float cursor = y + HEADER_HEIGHT + SETTINGS_PADDING;
        for (SettingComponent component : components) {
            if (!component.getSetting().isVisible()) {
                continue;
            }
            component.setBounds(x, cursor, width);
            component.render(mouseX, mouseY);
            cursor += component.getHeight();
        }
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        boolean headerHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        if (headerHovered) {
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
