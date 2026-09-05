package dev.vantage.gui.component;

import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.setting.KeybindSetting;
import org.lwjgl.input.Keyboard;

/** Click to listen, then press a key. Escape clears the binding rather than closing the GUI. */
public class KeybindComponent extends SettingComponent {

    private final KeybindSetting typed;
    private boolean listening;

    public KeybindComponent(KeybindSetting setting) {
        super(setting);
        this.typed = setting;
    }

    @Override
    public float getHeight() {
        return ROW_HEIGHT;
    }

    @Override
    public boolean isCapturingInput() {
        return listening;
    }

    @Override
    public void render(float mouseX, float mouseY) {
        boolean hovered = isHovered(mouseX, mouseY, ROW_HEIGHT);
        if (hovered || listening) {
            RenderUtil.roundedRect(x, y, width, ROW_HEIGHT, 3.0, listening ? Theme.accentDim() : 0x0AFFFFFF);
        }
        Fonts.SMALL.drawString(typed.getName(), x + LABEL_INSET, y + 2.0f, Theme.textMuted());

        String label = listening ? "press a key" : describe(typed.get());
        Fonts.SMALL.drawRightAligned(label, x + width - LABEL_INSET, y + 2.0f,
                listening ? Theme.accent() : (typed.isBound() ? Theme.text() : Theme.textFaint()));
    }

    public static String describe(int keyCode) {
        if (keyCode == KeybindSetting.UNBOUND) {
            return "none";
        }
        try {
            String name = Keyboard.getKeyName(keyCode);
            return name == null ? "key " + keyCode : name;
        } catch (Exception unknown) {
            return "key " + keyCode;
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!isHovered(mouseX, mouseY, ROW_HEIGHT)) {
            return false;
        }
        if (button == 0) {
            listening = !listening;
            return true;
        }
        if (button == 1) {
            typed.clear();
            listening = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedCharacter, int keyCode) {
        if (!listening) {
            return false;
        }
        // Escape means "unbind", not "close the screen", while this row is listening.
        typed.set(keyCode == Keyboard.KEY_ESCAPE ? KeybindSetting.UNBOUND : keyCode);
        listening = false;
        return true;
    }
}
