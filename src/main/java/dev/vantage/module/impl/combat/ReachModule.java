package dev.vantage.module.impl.combat;

import dev.vantage.event.ReachEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;

/**
 * Hit and build from further away. A 1.8 server accepts melee hits out to six blocks and block
 * placement out to eight, while the client only reaches three and four and a half.
 */
public class ReachModule extends Module {

    private final NumberSetting combat = register(new NumberSetting(
            "Combat", "How far you can hit, vanilla is 3", 3.5, 3.0, 6.0, 0.05, "m"));
    private final NumberSetting blocks = register(new NumberSetting(
            "Blocks", "How far you can build and break, vanilla is 4.5", 4.5, 4.5, 7.0, 0.1, "m"));

    public ReachModule() {
        super("Reach", Category.COMBAT, "Hit and build from further away");
        on(ReachEvent.class, event -> {
            event.setCombatReach(combat.asDouble());
            event.setBlockReach(Math.max(event.getBlockReach(), blocks.asDouble()));
        });
    }

    @Override
    public String getSuffix() {
        return String.format(java.util.Locale.ROOT, "%.2f", combat.asDouble());
    }
}
