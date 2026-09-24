package dev.vantage.module.impl.movement;

import dev.vantage.event.SlowdownEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;

/**
 * Walk at full speed while eating, blocking or drawing a bow. The slowdown is purely client-side in
 * 1.8; the server never checks it.
 */
public class NoSlowModule extends Module {

    private final NumberSetting speed = register(new NumberSetting(
            "Speed", "Movement speed while using an item; vanilla is 20%", 100.0, 20.0, 100.0, 5.0, "%"));
    private final BooleanSetting sprint = register(new BooleanSetting(
            "Sprint", "Keep sprinting while using an item", true));

    public NoSlowModule() {
        super("NoSlow", Category.MOVEMENT, "No slowdown while eating, blocking or drawing a bow");
        markBlatant();
        on(SlowdownEvent.class, event -> {
            event.setMultiplier(speed.asFloat() / 100.0f);
            event.setSprintAllowed(sprint.value());
        });
    }
}
