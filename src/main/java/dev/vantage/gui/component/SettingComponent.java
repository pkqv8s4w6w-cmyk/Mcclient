package dev.vantage.gui.component;

import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.ColorSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.KeybindSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.setting.Setting;
import dev.vantage.setting.StringSetting;

/**
 * One editable row inside an expanded module.
 *
 * <p>Components lay out top-down and report their own height, because several of them grow when
 * interacted with - a colour picker opens inline rather than floating above the list. Keeping
 * everything in the flow avoids z-order fights with the surrounding scroll clip.
 */
public abstract class SettingComponent {

    public static final float ROW_HEIGHT = 14.0f;
    protected static final float LABEL_INSET = 10.0f;

    protected final Setting<?> setting;

    protected float x;
    protected float y;
    protected float width;

    protected SettingComponent(Setting<?> setting) {
        this.setting = setting;
    }

    /** The setting this row edits. Used by the parent row to honour visibility predicates. */
    public Setting<?> getSetting() {
        return setting;
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public abstract float getHeight();

    public abstract void render(float mouseX, float mouseY);

    /** @return true if this component consumed the click */
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        return false;
    }

    public void mouseReleased(int button) {
    }

    /** @return true if this component consumed the key */
    public boolean keyTyped(char typedCharacter, int keyCode) {
        return false;
    }

    /** True while this component wants every keystroke, so the GUI stops treating them as hotkeys. */
    public boolean isCapturingInput() {
        return false;
    }

    protected boolean isHovered(float mouseX, float mouseY, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /** Builds the right component for a setting. Unknown types are skipped rather than crashing. */
    public static SettingComponent create(Setting<?> setting) {
        if (setting instanceof BooleanSetting) {
            return new BooleanComponent((BooleanSetting) setting);
        }
        if (setting instanceof NumberSetting) {
            return new NumberComponent((NumberSetting) setting);
        }
        if (setting instanceof EnumSetting) {
            return new EnumComponent((EnumSetting<?>) setting);
        }
        if (setting instanceof ColorSetting) {
            return new ColourComponent((ColorSetting) setting);
        }
        if (setting instanceof KeybindSetting) {
            return new KeybindComponent((KeybindSetting) setting);
        }
        if (setting instanceof StringSetting) {
            return new TextComponent((StringSetting) setting);
        }
        return null;
    }
}
