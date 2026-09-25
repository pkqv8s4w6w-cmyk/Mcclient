package dev.vantage.module.impl.movement;

import dev.vantage.event.TickStartEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Keep walking with an inventory or chest open. Screens release the movement keys when they open;
 * this reads the keyboard directly and presses them again. Chat is left alone, since the keys are
 * letters being typed there.
 */
public class InvMoveModule extends Module {

    public InvMoveModule() {
        super("InvMove", Category.MOVEMENT, "Walk with inventories and menus open");
        on(TickStartEvent.class, event -> tick());
    }

    private void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (screen == null || screen instanceof GuiChat) {
            return;
        }
        KeyBinding[] keys = {
                mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack, mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight, mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSprint
        };
        for (KeyBinding key : keys) {
            int code = key.getKeyCode();
            if (code > 0) {
                KeyBinding.setKeyBindState(code, Keyboard.isKeyDown(code));
            }
        }
    }
}
