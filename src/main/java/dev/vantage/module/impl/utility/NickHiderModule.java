package dev.vantage.module.impl.utility;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.StringSetting;
import dev.vantage.util.NameMasker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;

/**
 * Shows a name of your choosing in place of your own, for screenshots and streaming.
 *
 * <p>Entirely local. Nothing sent to the server changes and other players still see your real
 * name; this only rewrites what is drawn on your own screen.
 *
 * <p>Needs no bytecode patching. Chat is rewritten through the event that delivers it, and the tab
 * list is handled by setting the display name on your own entry, which the tab overlay already
 * prefers over the profile name. The server resends player list entries periodically, so that has
 * to be reapplied rather than set once.
 *
 * <p>The nametag above your head and the scoreboard sidebar are deliberately untouched: the first
 * is only visible in third person and the second carries team lines rather than your name, so
 * neither is worth the patching it would take.
 */
public class NickHiderModule extends Module {

    private final StringSetting nickname = register(new StringSetting(
            "Name", "What to show instead of your username", "Ghost", 16, false));
    private final BooleanSetting inChat = register(new BooleanSetting(
            "Chat", "Rewrite your name in chat messages", true));
    private final BooleanSetting inTabList = register(new BooleanSetting(
            "Tab List", "Rewrite your name in the player list", true));

    public NickHiderModule() {
        super("Nick Hider", Category.UTILITY, "Hides your username on your own screen");
    }

    private String realName() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? null : mc.thePlayer.getName();
    }

    private boolean usable() {
        return !nickname.get().trim().isEmpty() && realName() != null;
    }

    @Override
    public String rewriteChat(String raw) {
        if (!inChat.value() || !usable()) {
            return null;
        }
        String masked = NameMasker.mask(raw, realName(), nickname.get().trim());
        return masked.equals(raw) ? null : masked;
    }

    @Override
    public void onTick() {
        if (!inTabList.value() || !usable()) {
            return;
        }
        applyTabName(nickname.get().trim());
    }

    @Override
    protected void onDisable() {
        // Clearing the display name hands rendering back to the profile name.
        applyTabName(null);
    }

    private void applyTabName(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            return;
        }
        NetworkPlayerInfo self = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (self == null) {
            return;
        }
        if (name == null) {
            self.setDisplayName(null);
            return;
        }
        // Keep whatever colouring the server put around the name, so a nicked entry still looks
        // like it belongs to its team.
        String prefix = "";
        String suffix = "";
        if (mc.theWorld != null && mc.theWorld.getScoreboard() != null) {
            ScorePlayerTeam team = mc.theWorld.getScoreboard().getPlayersTeam(mc.thePlayer.getName());
            if (team != null) {
                prefix = team.getColorPrefix();
                suffix = team.getColorSuffix();
            }
        }
        self.setDisplayName(new ChatComponentText(prefix + name + suffix));
    }
}
