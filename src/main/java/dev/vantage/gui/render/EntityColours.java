package dev.vantage.gui.render;

import dev.vantage.Vantage;
import dev.vantage.game.TeamColour;
import dev.vantage.game.TeamResolver;
import dev.vantage.gui.Theme;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/** One rule for what colour an entity is drawn in, so every visual module agrees. */
public final class EntityColours {

    public static final int FRIEND = 0xFF4ADE80;

    private EntityColours() {
    }

    public static int of(Entity entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (Vantage.instance().friends().isFriend(player.getName())) {
                return FRIEND;
            }
            TeamColour team = TeamResolver.teamOf(player);
            if (team != TeamColour.UNKNOWN) {
                return team.getArgb();
            }
        }
        return Theme.accent();
    }

    /** Green at full health through amber to red, for health bars. */
    public static int health(float fraction) {
        return Theme.threatColour(10.0 - Math.max(0.0f, Math.min(1.0f, fraction)) * 10.0);
    }
}
