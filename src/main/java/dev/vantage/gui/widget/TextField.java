package dev.vantage.gui.widget;

import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;

/** A single-line input with a placeholder, used for search, new profile names and friends. */
public final class TextField {

    private final String placeholder;
    private final int maxLength;
    private final char icon;
    private String text = "";
    private boolean focused;
    private Widgets.Rect bounds = new Widgets.Rect(0, 0, 0, 0);
    private Consumer<String> onSubmit;
    private Runnable onChange;

    public TextField(String placeholder, int maxLength, char icon) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
        this.icon = icon;
    }

    public TextField onSubmit(Consumer<String> handler) {
        this.onSubmit = handler;
        return this;
    }

    public TextField onChange(Runnable handler) {
        this.onChange = handler;
        return this;
    }

    public String getText() {
        return text;
    }

    public void setText(String value) {
        text = value == null ? "" : value;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean value) {
        focused = value;
    }

    public void render(float x, float y, float width, float height) {
        bounds = new Widgets.Rect(x, y, width, height);
        RenderUtil.roundedRect(x, y, width, height, 6.0, Theme.row());
        RenderUtil.roundedOutline(x, y, width, height, 6.0, 1.0f, focused ? Theme.accent() : Theme.border());
        float textX = x + 8.0f;
        float textY = y + (height - Fonts.SMALL.getHeight()) / 2.0f;
        if (icon != 0) {
            Fonts.ICONS.drawString(String.valueOf(icon), textX, y + (height - Fonts.ICONS.getHeight()) / 2.0f + 0.5f,
                    Theme.textFaint());
            textX += 13.0f;
        }
        if (text.isEmpty() && !focused) {
            Fonts.SMALL.drawString(placeholder, textX, textY, Theme.textFaint());
            return;
        }
        String shown = text;
        float room = width - (textX - x) - 8.0f;
        // Keep the end of the text in view as it grows past the field.
        while (Fonts.SMALL.getWidth(shown) > room && shown.length() > 1) {
            shown = shown.substring(1);
        }
        float end = Fonts.SMALL.drawString(shown, textX, textY, Theme.text());
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            RenderUtil.rect(end + 1.0f, textY + 0.5f, 0.8f, Fonts.SMALL.getHeight() - 2.0f, Theme.accent());
        }
    }

    /** Focuses on a click inside, unfocuses on one outside. @return true if the click was inside */
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        boolean inside = bounds.contains(mouseX, mouseY);
        focused = inside;
        if (inside && button == 1) {
            text = "";
            changed();
        }
        return inside;
    }

    /** @return true if the key was used */
    public boolean keyTyped(char character, int keyCode) {
        if (!focused) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            focused = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (onSubmit != null) {
                onSubmit.accept(text.trim());
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!text.isEmpty()) {
                text = GuiScreen.isCtrlKeyDown() ? "" : text.substring(0, text.length() - 1);
                changed();
            }
            return true;
        }
        if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            append(GuiScreen.getClipboardString());
            return true;
        }
        if (character >= 32 && character != 127) {
            append(String.valueOf(character));
        }
        return true;
    }

    private void append(String addition) {
        if (addition == null) {
            return;
        }
        String combined = text + addition.replace("\n", " ").replace("\r", "");
        text = combined.length() > maxLength ? combined.substring(0, maxLength) : combined;
        changed();
    }

    private void changed() {
        if (onChange != null) {
            onChange.run();
        }
    }
}
