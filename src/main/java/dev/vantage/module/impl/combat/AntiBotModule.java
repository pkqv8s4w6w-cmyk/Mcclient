package dev.vantage.module.impl.combat;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Keeps combat modules off things that look like players but are not: shopkeepers, hologram
 * carriers and other fake players a server spawns.
 *
 * <p>Every check is evidence that an entity is <em>not</em> a real player, never the absence of
 * evidence that it is, so a real player is not skipped because something about them was unreadable.
 */
public class AntiBotModule extends Module {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final BooleanSetting tabList = register(new BooleanSetting(
            "Tab List", "Players missing from the tab list are fake", true));
    private final BooleanSetting names = register(new BooleanSetting(
            "Invalid Names", "Names no account could have are fake", true));
    private final BooleanSetting uuids = register(new BooleanSetting(
            "UUID Version", "On online-mode servers, profiles Mojang did not issue are fake. "
                    + "Ignored automatically on offline-mode servers", false));
    private final BooleanSetting idle = register(new BooleanSetting(
            "Never Moved", "Players who have not moved since they appeared are fake", false));

    private final Map<Integer, double[]> firstSeen = new HashMap<Integer, double[]>();
    private final Map<Integer, Boolean> hasMoved = new HashMap<Integer, Boolean>();

    public AntiBotModule() {
        super("AntiBot", Category.COMBAT, "Stops combat modules targeting fake players and NPCs");
    }

    @Override
    public void onWorldChanged() {
        firstSeen.clear();
        hasMoved.clear();
    }

    @Override
    public void onTick() {
        if (!idle.value()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            int id = player.getEntityId();
            double[] origin = firstSeen.get(id);
            if (origin == null) {
                firstSeen.put(id, new double[]{player.posX, player.posY, player.posZ});
                continue;
            }
            if (!Boolean.TRUE.equals(hasMoved.get(id))) {
                double dx = player.posX - origin[0];
                double dz = player.posZ - origin[2];
                if (dx * dx + dz * dz > 0.25) {
                    hasMoved.put(id, Boolean.TRUE);
                }
            }
        }
    }

    public boolean isBot(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player == mc.thePlayer) {
            return false;
        }
        if (names.value() && !VALID_NAME.matcher(player.getName()).matches()) {
            return true;
        }
        NetHandlerPlayClient handler = mc.getNetHandler();
        NetworkPlayerInfo info = handler == null ? null : handler.getPlayerInfo(player.getUniqueID());
        if (tabList.value() && handler != null && info == null) {
            return true;
        }
        if (uuids.value() && onlineModeServer(mc) && player.getUniqueID().version() != 4) {
            return true;
        }
        return idle.value() && !Boolean.TRUE.equals(hasMoved.get(player.getEntityId()));
    }

    /**
     * Offline-mode servers hand every player a version 3 UUID derived from their name, so the UUID
     * rule would call everybody a bot there. Your own UUID says which kind of server this is.
     */
    private static boolean onlineModeServer(Minecraft mc) {
        UUID own = mc.thePlayer == null ? null : mc.thePlayer.getUniqueID();
        return own != null && own.version() == 4;
    }
}
