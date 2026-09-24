package dev.vantage.gui.page;

import dev.vantage.Vantage;
import dev.vantage.gui.Icons;
import dev.vantage.gui.Theme;
import dev.vantage.gui.component.EnumComponent;
import dev.vantage.gui.component.KeybindComponent;
import dev.vantage.gui.component.NumberComponent;
import dev.vantage.gui.component.SettingComponent;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.widget.Widgets;
import dev.vantage.hud.HudEditScreen;
import dev.vantage.mods.ModsScreen;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.module.impl.client.ClickGuiModule;
import dev.vantage.module.impl.client.HudEditorModule;
import dev.vantage.notify.Notifications;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** How the menu itself behaves, the other screens, and the panic button. */
public class SettingsPage extends Page {

    private static final float ACTION_HEIGHT = 34.0f;
    private static final float GAP = 6.0f;

    private final List<SettingComponent> components = new ArrayList<SettingComponent>();
    private final List<Object[]> actions = new ArrayList<Object[]>();

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    public String subtitle() {
        return "Customise your client";
    }

    private List<SettingComponent> components() {
        if (components.isEmpty()) {
            ClickGuiModule menu = Vantage.instance().modules().get(ClickGuiModule.class);
            HudEditorModule hud = Vantage.instance().modules().get(HudEditorModule.class);
            components.add(new KeybindComponent(menu.getKeybind(), "Menu key"));
            components.add(new EnumComponent(menu.backgroundSetting()));
            components.add(new NumberComponent(menu.uiScaleSetting()));
            components.add(new KeybindComponent(hud.getKeybind(), "HUD editor key"));
        }
        return components;
    }

    @Override
    public float renderBody(float x, float top, float width, float mouseX, float mouseY) {
        float cursor = top;
        Widgets.sectionLabel("MENU", x + 2.0f, cursor);
        cursor += 12.0f;
        float inner = 0.0f;
        for (SettingComponent component : components()) {
            inner += component.getHeight();
        }
        Widgets.card(x, cursor, width, inner + 12.0f, false);
        float row = cursor + 6.0f;
        for (SettingComponent component : components()) {
            component.setBounds(x + 8.0f, row, width - 16.0f);
            component.render(mouseX, mouseY);
            row += component.getHeight();
        }
        cursor += inner + 12.0f + GAP * 2.0f;

        Widgets.sectionLabel("SCREENS", x + 2.0f, cursor);
        cursor += 12.0f;
        actions.clear();
        cursor = action(x, cursor, width, mouseX, mouseY, Icons.LAYOUT_DASHBOARD, "HUD Editor",
                "Drag on-screen elements into place", "Open", Widgets.Style.GHOST, "hud");
        cursor = action(x, cursor, width, mouseX, mouseY, Icons.LAYERS, "Mods Manager",
                "Other mods installed alongside Vantage", "Open", Widgets.Style.GHOST, "mods");
        cursor += GAP;

        Widgets.sectionLabel("DANGER ZONE", x + 2.0f, cursor);
        cursor += 12.0f;
        cursor = action(x, cursor, width, mouseX, mouseY, Icons.POWER, "Panic",
                "Switch every module off at once", "Disable all", Widgets.Style.DANGER, "panic");

        Fonts.TINY.drawCentred("Vantage " + Vantage.VERSION + "  •  "
                        + Vantage.instance().modules().getModules().size() + " modules",
                x + width / 2.0f, cursor + 6.0f, Theme.textFaint());
        return cursor + 20.0f - top;
    }

    private float action(float x, float y, float width, float mouseX, float mouseY, char icon, String title,
                         String description, String button, Widgets.Style style, String id) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ACTION_HEIGHT;
        Widgets.card(x, y, width, ACTION_HEIGHT, hovered);
        Fonts.ICONS_LARGE.drawString(String.valueOf(icon), x + 11.0f, y + 10.0f,
                style == Widgets.Style.DANGER ? Theme.danger() : Theme.accent());
        Fonts.SMALL_BOLD.drawString(title, x + 32.0f, y + 7.0f, Theme.text());
        Fonts.TINY.drawString(description, x + 32.0f, y + 19.0f, Theme.textFaint());
        Widgets.Rect bounds = Widgets.buttonRightAligned(button, (char) 0, x + width - 10.0f,
                y + (ACTION_HEIGHT - Widgets.BUTTON_HEIGHT) / 2.0f, style, mouseX, mouseY);
        actions.add(new Object[]{id, bounds});
        return y + ACTION_HEIGHT + GAP;
    }

    @Override
    public boolean bodyClicked(float mouseX, float mouseY, int button) {
        for (SettingComponent component : components()) {
            if (component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (Object[] entry : actions) {
            if (!((Widgets.Rect) entry[1]).contains(mouseX, mouseY)) {
                continue;
            }
            String id = (String) entry[0];
            Minecraft mc = Minecraft.getMinecraft();
            if ("hud".equals(id)) {
                mc.displayGuiScreen(new HudEditScreen());
            } else if ("mods".equals(id)) {
                mc.displayGuiScreen(new ModsScreen());
            } else if ("panic".equals(id)) {
                int count = 0;
                for (Module module : Vantage.instance().modules().getModules()) {
                    if (module.isEnabled() && module.getCategory() != Category.CLIENT) {
                        module.setEnabled(false);
                        count++;
                    }
                }
                Notifications.post("Panic", count + (count == 1 ? " module" : " modules") + " switched off",
                        Notifications.Kind.WARNING);
            }
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int button) {
        for (SettingComponent component : components()) {
            component.mouseReleased(button);
        }
    }

    @Override
    public boolean keyTyped(char character, int keyCode) {
        for (SettingComponent component : components()) {
            if (component.keyTyped(character, keyCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCapturingInput() {
        for (SettingComponent component : components()) {
            if (component.isCapturingInput()) {
                return true;
            }
        }
        return false;
    }
}
