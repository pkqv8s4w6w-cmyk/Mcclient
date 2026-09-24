package dev.vantage.module.impl.combat;

import dev.vantage.combat.ClickTimer;
import dev.vantage.combat.TargetFinder;
import dev.vantage.combat.TargetSettings;
import dev.vantage.event.TickStartEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;

/** Attacks whatever valid target is under the crosshair. */
public class TriggerBotModule extends Module {

    private final NumberSetting minCps = register(new NumberSetting(
            "Min CPS", "Slowest attack rate", 8.0, 1.0, 20.0, 1.0));
    private final NumberSetting maxCps = register(new NumberSetting(
            "Max CPS", "Fastest attack rate", 12.0, 1.0, 20.0, 1.0));
    private final TargetSettings targets = new TargetSettings();
    private final ClickTimer timer = new ClickTimer();

    public TriggerBotModule() {
        super("TriggerBot", Category.COMBAT, "Attacks targets that cross your crosshair");
        registerAll(targets.all());
        on(TickStartEvent.class, event -> tick());
    }

    private void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || !(mc.objectMouseOver.entityHit instanceof EntityLivingBase)) {
            timer.reset();
            return;
        }
        EntityLivingBase hit = (EntityLivingBase) mc.objectMouseOver.entityHit;
        if (!TargetFinder.isValid(hit, targets, 6.0, 360.0f)) {
            return;
        }
        if (timer.shouldClick(System.currentTimeMillis(), minCps.asDouble(), maxCps.asDouble())) {
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        }
    }
}
