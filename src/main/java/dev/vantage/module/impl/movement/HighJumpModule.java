package dev.vantage.module.impl.movement;

import dev.vantage.event.JumpEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;

/** Jumps higher. 0.42 is a normal jump; 1.0 clears about four blocks. */
public class HighJumpModule extends Module {

    private final NumberSetting height = register(new NumberSetting(
            "Height", "Jump strength", 0.8, 0.42, 3.0, 0.01));

    public HighJumpModule() {
        super("HighJump", Category.MOVEMENT, "Jump higher");
        markBlatant();
        on(JumpEvent.class, event -> event.setMotionY(height.asFloat()));
    }
}
