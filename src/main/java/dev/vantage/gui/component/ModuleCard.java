package dev.vantage.gui.component;

import dev.vantage.gui.Animated;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.gui.widget.Widgets;
import dev.vantage.module.Module;
import dev.vantage.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * One module as a card: name and description on the left, tags and a switch on the right, and its
 * settings unfolding inside the card when clicked.
 *
 * <p>The switch toggles the module; clicking anywhere else on the card opens or closes the
 * settings. Right-clicking the card toggles too, for anyone used to the old menu.
 */
public class ModuleCard {

    public static final float HEADER_HEIGHT = 38.0f;
    private static final float SETTINGS_INSET = 14.0f;

    private final Module module;
    private final List<SettingComponent> components = new ArrayList<SettingComponent>();
    private final Animated expansion = new Animated(0.0, 0.06);
    private final Animated switchKnob;
    private final Animated hover = new Animated(0.0, 0.05);

    private boolean expanded;
    private float x;
    private float y;
    private float width;
    private Widgets.Rect switchBounds = new Widgets.Rect(0, 0, 0, 0);

    public ModuleCard(Module module) {
        this.module = module;
        this.switchKnob = new Animated(module.isEnabled() ? 1.0 : 0.0, 0.05);
        for (Setting<?> setting : module.getSettings()) {
            SettingComponent component = SettingComponent.create(setting);
            if (component != null) {
                components.add(component);
            }
        }
        // The toggle key lives outside the settings list, so it gets its own row at the end.
        components.add(new KeybindComponent(module.getKeybind(), "Keybind"));
    }

    public Module getModule() {
        return module;
    }

    private boolean hasVisibleSettings() {
        for (SettingComponent component : components) {
            if (component.getSetting().isVisible()) {
                return true;
            }
        }
        return false;
    }

    private float settingsHeight() {
        float total = 0.0f;
        for (SettingComponent component : components) {
            if (component.getSetting().isVisible()) {
                total += component.getHeight();
            }
        }
        return total + 10.0f;
    }

    public float getHeight() {
        expansion.setTarget(expanded && hasVisibleSettings() ? 1.0 : 0.0);
        return HEADER_HEIGHT + (float) (settingsHeight() * expansion.get());
    }

    public void render(float x, float y, float width, float mouseX, float mouseY) {
        this.x = x;
        this.y = y;
        this.width = width;
        boolean overHeader = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        hover.setTarget(overHeader ? 1.0 : 0.0);
        float height = getHeight();

        int background = RenderUtil.blend(Theme.row(), Theme.rowHover(), hover.get());
        RenderUtil.roundedRect(x, y, width, height, 7.0, background);
        double on = switchKnob.get();
        if (on > 0.01) {
            // A faint accent edge marks the modules that are running.
            RenderUtil.roundedRect(x, y + 9.0f, 2.0f, HEADER_HEIGHT - 18.0f, 1.0, RenderUtil.withAlpha(Theme.accent(), (float) on));
        }

        Fonts.BODY_BOLD.drawString(module.getName(), x + 12.0f, y + 8.0f,
                RenderUtil.blend(Theme.textMuted(), Theme.text(), 0.4 + 0.6 * on));
        float tagsRight = x + width - 14.0f - Widgets.SWITCH_WIDTH - 8.0f;
        String description = Fonts.TINY.trimToWidth(module.getDescription(), tagsRight - (x + 12.0f) - 70.0f);
        Fonts.TINY.drawString(description, x + 12.0f, y + 22.0f, Theme.textFaint());

        // Tags sit to the left of the switch, right to left.
        float tagX = tagsRight;
        if (module.getKeybind().isBound()) {
            String key = KeybindComponent.describe(module.getKeybind().get());
            float tagWidth = Fonts.TINY.getWidth(key) + 8.0f;
            tagX -= tagWidth;
            Widgets.chip(key, tagX, y + (HEADER_HEIGHT - 10.0f) / 2.0f, Theme.textMuted());
            tagX -= 4.0f;
        }
        if (module.isBlatant()) {
            float tagWidth = Fonts.TINY.getWidth("Blatant") + 8.0f;
            tagX -= tagWidth;
            Widgets.chip("Blatant", tagX, y + (HEADER_HEIGHT - 10.0f) / 2.0f, Theme.danger());
        }

        switchKnob.setTarget(module.isEnabled() ? 1.0 : 0.0);
        switchBounds = Widgets.toggle(x + width - 12.0f - Widgets.SWITCH_WIDTH,
                y + (HEADER_HEIGHT - Widgets.SWITCH_HEIGHT) / 2.0f, switchKnob.get());

        float openness = (float) expansion.get();
        if (openness < 0.01f) {
            return;
        }
        RenderUtil.rect(x + 12.0f, y + HEADER_HEIGHT - 1.0f, width - 24.0f, 1.0f,
                RenderUtil.withAlpha(Theme.divider(), openness));
        float cursor = y + HEADER_HEIGHT + 4.0f;
        for (SettingComponent component : components) {
            if (!component.getSetting().isVisible()) {
                continue;
            }
            component.setBounds(x + SETTINGS_INSET - 4.0f, cursor, width - SETTINGS_INSET * 2.0f + 8.0f);
            component.render(mouseX, mouseY);
            cursor += component.getHeight();
        }
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        boolean onHeader = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        if (onHeader) {
            if (switchBounds.contains(mouseX, mouseY) || button == 1 || !hasVisibleSettings()) {
                if (button == 0 || button == 1) {
                    module.toggle();
                    return true;
                }
                return false;
            }
            if (button == 0) {
                expanded = !expanded;
                return true;
            }
            return false;
        }
        if (expansion.get() < 0.5) {
            return false;
        }
        for (SettingComponent component : components) {
            // Hidden settings keep the bounds they last had, which would otherwise catch clicks
            // meant for whatever is drawn there now.
            if (component.getSetting().isVisible() && component.mouseClicked(mouseX, mouseY, button)) {
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

    public boolean keyTyped(char character, int keyCode) {
        for (SettingComponent component : components) {
            if (component.getSetting().isVisible() && component.keyTyped(character, keyCode)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCapturingInput() {
        for (SettingComponent component : components) {
            if (component.getSetting().isVisible() && component.isCapturingInput()) {
                return true;
            }
        }
        return false;
    }

    public void collapse() {
        expanded = false;
    }
}
