package dev.vantage.module.impl.combat;

import dev.vantage.event.AttackEvent;
import dev.vantage.event.Stage;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Keeps your speed and sprint when you land a hit. Vanilla cuts your motion to 60% and stops the
 * sprint with every sprinting hit, which is what lets a chased player get away.
 */
public class KeepSprintModule extends Module {

    private final NumberSetting keep = register(new NumberSetting(
            "Keep Speed", "How much of your speed to keep through a hit; vanilla keeps 60%", 100.0, 60.0, 100.0, 5.0, "%"));

    private double motionX;
    private double motionZ;
    private boolean sprinting;

    public KeepSprintModule() {
        super("KeepSprint", Category.COMBAT, "Keep your speed and sprint when you hit someone");
        markBlatant();
        on(AttackEvent.class, this::onAttack);
    }

    private void onAttack(AttackEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (event.getStage() == Stage.PRE) {
            motionX = player.motionX;
            motionZ = player.motionZ;
            sprinting = player.isSprinting();
            return;
        }
        if (!sprinting) {
            return;
        }
        double factor = keep.asDouble() / 100.0;
        player.motionX = motionX * factor;
        player.motionZ = motionZ * factor;
        player.setSprinting(true);
    }
}
