package dev.vantage.gui.page;

import dev.vantage.Vantage;
import dev.vantage.gui.Icons;
import dev.vantage.gui.Theme;
import dev.vantage.gui.component.SettingComponent;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.gui.widget.Widgets;
import dev.vantage.module.impl.client.ClickGuiModule;
import dev.vantage.setting.ColorSetting;

import java.util.ArrayList;
import java.util.List;

/** Colour presets as live previews, then every colour on its own for fine tuning. */
public class ThemesPage extends Page {

    private static final int COLUMNS = 3;
    private static final float SWATCH_HEIGHT = 64.0f;
    private static final float GAP = 8.0f;

    private final List<Object[]> swatches = new ArrayList<Object[]>();
    private final List<SettingComponent> pickers = new ArrayList<SettingComponent>();

    @Override
    public String title() {
        return "Themes";
    }

    @Override
    public String subtitle() {
        return "Choose your colour story";
    }

    private static ClickGuiModule menu() {
        return Vantage.instance().modules().get(ClickGuiModule.class);
    }

    private List<SettingComponent> pickers() {
        if (pickers.isEmpty()) {
            for (ColorSetting setting : menu().colourSettings()) {
                pickers.add(SettingComponent.create(setting));
            }
        }
        return pickers;
    }

    @Override
    public float renderBody(float x, float top, float width, float mouseX, float mouseY) {
        swatches.clear();
        float swatchWidth = (width - GAP * (COLUMNS - 1)) / COLUMNS;
        ClickGuiModule.Preset[] presets = ClickGuiModule.Preset.values();
        for (int i = 0; i < presets.length; i++) {
            ClickGuiModule.Preset preset = presets[i];
            float sx = x + (i % COLUMNS) * (swatchWidth + GAP);
            float sy = top + (i / COLUMNS) * (SWATCH_HEIGHT + GAP);
            Widgets.Rect bounds = new Widgets.Rect(sx, sy, swatchWidth, SWATCH_HEIGHT);
            swatches.add(new Object[]{preset, bounds});
            drawSwatch(preset, sx, sy, swatchWidth, bounds.contains(mouseX, mouseY), menu().matches(preset));
        }
        int rows = (presets.length + COLUMNS - 1) / COLUMNS;
        float cursor = top + rows * (SWATCH_HEIGHT + GAP) + 6.0f;

        Widgets.sectionLabel("CUSTOM", x + 2.0f, cursor);
        cursor += 12.0f;
        float cardTop = cursor;
        float inner = 0.0f;
        for (SettingComponent picker : pickers()) {
            inner += picker.getHeight();
        }
        Widgets.card(x, cardTop, width, inner + 12.0f, false);
        cursor += 6.0f;
        for (SettingComponent picker : pickers()) {
            picker.setBounds(x + 8.0f, cursor, width - 16.0f);
            picker.render(mouseX, mouseY);
            cursor += picker.getHeight();
        }
        return cursor + 6.0f - top;
    }

    /** A miniature of the menu in the preset's colours. */
    private static void drawSwatch(ClickGuiModule.Preset preset, float x, float y, float width, boolean hovered, boolean active) {
        RenderUtil.roundedRect(x, y, width, SWATCH_HEIGHT, 7.0, preset.panel);
        RenderUtil.roundedOutline(x, y, width, SWATCH_HEIGHT, 7.0, active ? 1.5f : 1.0f,
                active ? preset.accent : (hovered ? RenderUtil.withAlpha(preset.text, 90) : RenderUtil.withAlpha(preset.text, 30)));
        // Sidebar strip with a selected entry, and two cards on the right.
        RenderUtil.roundedRect(x + 6.0f, y + 6.0f, 26.0f, 36.0f, 4.0, RenderUtil.shift(preset.panel, -0.25));
        RenderUtil.roundedRect(x + 9.0f, y + 12.0f, 20.0f, 6.0f, 2.0, RenderUtil.withAlpha(preset.accent, 70));
        RenderUtil.rect(x + 9.0f, y + 12.0f, 1.5f, 6.0f, preset.accent);
        RenderUtil.roundedRect(x + 36.0f, y + 6.0f, width - 42.0f, 12.0f, 3.0, preset.row);
        RenderUtil.roundedRect(x + 36.0f, y + 22.0f, width - 42.0f, 12.0f, 3.0, preset.row);
        RenderUtil.roundedRect(x + width - 20.0f, y + 9.5f, 10.0f, 5.0f, 2.5, preset.accent);
        RenderUtil.roundedRect(x + 40.0f, y + 10.5f, 22.0f, 3.0f, 1.5, RenderUtil.withAlpha(preset.text, 200));
        RenderUtil.roundedRect(x + 40.0f, y + 26.5f, 30.0f, 3.0f, 1.5, RenderUtil.withAlpha(preset.text, 120));
        Fonts.SMALL_BOLD.drawString(preset.label, x + 8.0f, y + SWATCH_HEIGHT - 16.0f, preset.text);
        if (active) {
            Fonts.ICONS.drawRightAligned(String.valueOf(Icons.CIRCLE_CHECK), x + width - 8.0f, y + SWATCH_HEIGHT - 16.5f, preset.accent);
        }
    }

    @Override
    public boolean bodyClicked(float mouseX, float mouseY, int button) {
        for (Object[] entry : swatches) {
            if (((Widgets.Rect) entry[1]).contains(mouseX, mouseY)) {
                menu().applyPreset((ClickGuiModule.Preset) entry[0]);
                return true;
            }
        }
        for (SettingComponent picker : pickers()) {
            if (picker.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(int button) {
        for (SettingComponent picker : pickers()) {
            picker.mouseReleased(button);
        }
    }
}
