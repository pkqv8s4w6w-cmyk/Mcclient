package dev.vantage.gui.component;

import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.setting.StringSetting;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * An editable text field.
 *
 * <p>Masked settings render as bullets and never show their contents, which is what the Hypixel
 * API key field uses so the value cannot be read off a screenshot or a stream.
 */
public class TextComponent extends SettingComponent {

    private static final float FIELD_HEIGHT = 13.0f;
    private static final float ROW = 24.0f;

    private final StringSetting typed;
    private boolean focused;

    public TextComponent(StringSetting setting) {
        super(setting);
        this.typed = setting;
    }

    @Override
    public float getHeight() {
        return ROW;
    }

    @Override
    public boolean isCapturingInput() {
        return focused;
    }

    private float fieldY() {
        return y + 10.0f;
    }

    @Override
    public void render(float mouseX, float mouseY) {
        Fonts.SMALL.drawString(typed.getName(), x + LABEL_INSET, y - 1.0f, Theme.textMuted());

        float fieldX = x + LABEL_INSET;
        float fieldWidth = width - LABEL_INSET * 2.0f;
        RenderUtil.roundedRect(fieldX, fieldY(), fieldWidth, FIELD_HEIGHT, 3.0, 0xFF101216);
        RenderUtil.roundedOutline(fieldX, fieldY(), fieldWidth, FIELD_HEIGHT, 3.0, 1.0f,
                focused ? Theme.accent() : Theme.border());

        String display = displayText();
        float textY = fieldY() + 2.0f;
        if (display.isEmpty() && !focused) {
            Fonts.SMALL.drawString(placeholder(), fieldX + 5.0f, textY, Theme.textFaint());
        } else {
            String trimmed = Fonts.SMALL.trimToWidth(display, fieldWidth - 12.0f);
            float endX = Fonts.SMALL.drawString(trimmed, fieldX + 5.0f, textY, Theme.text());
            // Blink at roughly 1.5Hz, the rate that reads as a caret rather than a strobe.
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                RenderUtil.rect(endX + 1.0f, textY, 0.8f, Fonts.SMALL.getHeight() - 3.0f, Theme.accent());
            }
        }
    }

    private String placeholder() {
        return typed.isMasked() ? "not set" : "empty";
    }

    private String displayText() {
        String raw = typed.get();
        if (!typed.isMasked()) {
            return raw;
        }
        StringBuilder masked = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            masked.append('•');
        }
        return masked.toString();
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        boolean insideField = mouseX >= x + LABEL_INSET && mouseX <= x + width - LABEL_INSET
                && mouseY >= fieldY() && mouseY <= fieldY() + FIELD_HEIGHT;
        if (button == 0) {
            focused = insideField;
            return insideField;
        }
        if (button == 1 && insideField) {
            typed.set("");
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedCharacter, int keyCode) {
        if (!focused) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
            focused = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            String current = typed.get();
            if (!current.isEmpty()) {
                typed.set(current.substring(0, current.length() - 1));
            }
            return true;
        }
        if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            // Pasting matters here: an API key is 36 characters nobody wants to type.
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard != null) {
                typed.set(typed.get() + clipboard.trim());
            }
            return true;
        }
        if (GuiScreen.isKeyComboCtrlA(keyCode)) {
            typed.set("");
            return true;
        }
        if (ChatAllowed.isPrintable(typedCharacter)) {
            typed.set(typed.get() + typedCharacter);
            return true;
        }
        return true; // Swallow everything else while focused, so typing cannot toggle modules.
    }

    /** Keeps control characters out of settings without pulling in Minecraft's chat filter. */
    static final class ChatAllowed {
        private ChatAllowed() {
        }

        static boolean isPrintable(char character) {
            return character >= 32 && character != 127;
        }
    }
}
