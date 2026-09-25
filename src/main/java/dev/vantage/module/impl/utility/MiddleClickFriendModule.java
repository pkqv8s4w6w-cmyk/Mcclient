package dev.vantage.module.impl.utility;

import dev.vantage.Vantage;
import dev.vantage.event.TickStartEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;

/** Middle-click a player to add them to your friends, or take them off. */
public class MiddleClickFriendModule extends Module {

    private boolean wasDown;

    public MiddleClickFriendModule() {
        super("Middle Click Friend", Category.UTILITY, "Middle-click a player to add or remove them as a friend");
        on(TickStartEvent.class, event -> tick());
    }

    private void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean down = Mouse.isButtonDown(2) && mc.currentScreen == null;
        if (down && !wasDown && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
            String name = mc.objectMouseOver.entityHit.getName();
            boolean added = Vantage.instance().friends().toggle(name);
            Notifications.post(added ? "Friend added" : "Friend removed", name,
                    added ? Notifications.Kind.SUCCESS : Notifications.Kind.INFO);
        }
        wasDown = down;
    }
}
