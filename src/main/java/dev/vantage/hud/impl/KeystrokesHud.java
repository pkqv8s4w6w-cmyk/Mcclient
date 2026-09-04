package dev.vantage.hud.impl;

import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.util.CpsMeter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/** The movement keys and mouse buttons, lighting up as they are pressed. */
public class KeystrokesHud extends HudModule {

    private static final float KEY_SIZE = 16.0f;
    private static final float GAP = 2.0f;

    private final BooleanSetting showMouse = register(new BooleanSetting(
            "Mouse", "Include the mouse buttons", true));
    private final BooleanSetting showCps = register(new BooleanSetting(
            "CPS On Buttons", "Show clicks per second on the mouse buttons", true));

    private final CpsMeter left = new CpsMeter();
    private final CpsMeter right = new CpsMeter();
    private boolean leftWasDown;
    private boolean rightWasDown;

    public KeystrokesHud() {
        super("Keystrokes", "Shows movement keys and mouse buttons");
        showCps.visibleWhen(showMouse::value);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        boolean leftDown = mc.gameSettings.keyBindAttack.isKeyDown();
        if (leftDown && !leftWasDown) {
            left.click(now);
        }
        leftWasDown = leftDown;
        boolean rightDown = mc.gameSettings.keyBindUseItem.isKeyDown();
        if (rightDown && !rightWasDown) {
            right.click(now);
        }
        rightWasDown = rightDown;
    }

    @Override
    public float getContentWidth() {
        return KEY_SIZE * 3.0f + GAP * 2.0f;
    }

    @Override
    public float getContentHeight() {
        float rows = showMouse.value() ? 3.0f : 2.0f;
        return KEY_SIZE * rows + GAP * (rows - 1.0f);
    }

    @Override
    protected void renderContent() {
        Minecraft mc = Minecraft.getMinecraft();
        float row = KEY_SIZE + GAP;

        drawKey("W", mc.gameSettings.keyBindForward, KEY_SIZE + GAP, 0.0f, KEY_SIZE);
        drawKey("A", mc.gameSettings.keyBindLeft, 0.0f, row, KEY_SIZE);
        drawKey("S", mc.gameSettings.keyBindBack, KEY_SIZE + GAP, row, KEY_SIZE);
        drawKey("D", mc.gameSettings.keyBindRight, (KEY_SIZE + GAP) * 2.0f, row, KEY_SIZE);

        if (!showMouse.value()) {
            return;
        }
        long now = System.currentTimeMillis();
        float mouseWidth = (getContentWidth() - GAP) / 2.0f;
        drawWide(showCps.value() ? String.valueOf(left.perSecond(now)) : "LMB",
                mc.gameSettings.keyBindAttack.isKeyDown(), 0.0f, row * 2.0f, mouseWidth);
        drawWide(showCps.value() ? String.valueOf(right.perSecond(now)) : "RMB",
                mc.gameSettings.keyBindUseItem.isKeyDown(), mouseWidth + GAP, row * 2.0f, mouseWidth);
    }

    private void drawKey(String label, KeyBinding binding, float x, float y, float size) {
        drawWide(label, binding.isKeyDown(), x, y, size);
    }

    private void drawWide(String label, boolean pressed, float x, float y, float width) {
        int background = pressed ? Theme.accent() : 0x50202329;
        RenderUtil.roundedRect(x, y, width, KEY_SIZE, 3.0, background);
        Fonts.SMALL.drawCentred(label, x + width / 2.0f, y + (KEY_SIZE - Fonts.SMALL.getHeight()) / 2.0f + 1.0f,
                pressed ? 0xFFFFFFFF : Theme.TEXT_MUTED);
    }
}
