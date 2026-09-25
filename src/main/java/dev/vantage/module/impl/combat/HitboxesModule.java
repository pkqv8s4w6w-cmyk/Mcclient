package dev.vantage.module.impl.combat;

import dev.vantage.Vantage;
import dev.vantage.event.HitboxEvent;
import dev.vantage.game.TeamResolver;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Makes other players easier to hit by growing the box the crosshair looks for. The server never
 * checks where on a player you clicked, only that you were close enough.
 */
public class HitboxesModule extends Module {

    private final NumberSetting expand = register(new NumberSetting(
            "Expand", "How much bigger to make each side", 0.25, 0.05, 1.0, 0.05, "m"));
    private final BooleanSetting ignoreTeam = register(new BooleanSetting(
            "Ignore Team", "Leave teammates and friends their normal size", true));

    public HitboxesModule() {
        super("Hitboxes", Category.COMBAT, "Makes other players easier to hit");
        on(HitboxEvent.class, this::onHitbox);
    }

    private void onHitbox(HitboxEvent event) {
        if (!(event.getEntity() instanceof EntityPlayer) || event.getEntity() == Minecraft.getMinecraft().thePlayer) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntity();
        if (ignoreTeam.value() && (TeamResolver.isTeammate(player)
                || Vantage.instance().friends().isFriend(player.getName()))) {
            return;
        }
        event.setBorder(event.getBorder() + expand.asFloat());
    }
}
