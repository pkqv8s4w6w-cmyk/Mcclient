package dev.vantage.game;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pulls the current lobby out of the game: who is here, what team they are on, and their gear. */
public final class LobbyReader {

    /** Display slot 1 is the sidebar. */
    private static final int SIDEBAR_SLOT = 1;

    /** One tab list entry, reduced to what the threat list needs. */
    public static final class LobbyPlayer {
        public final UUID uuid;
        public final String name;
        public final int ping;

        public LobbyPlayer(UUID uuid, String name, int ping) {
            this.uuid = uuid;
            this.name = name;
            this.ping = ping;
        }
    }

    private LobbyReader() {
    }

    /**
     * Everyone in the tab list who looks like a real player.
     *
     * <p>Whether these are opponents worth listing is decided by {@link GameDetector} from the
     * scoreboard title, not here: in a Bedwars game the tab list is exactly the participants, and
     * in a hub there is nothing worth showing at all.
     */
    public static List<LobbyPlayer> readPlayers() {
        List<LobbyPlayer> players = new ArrayList<LobbyPlayer>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) {
            return players;
        }
        Collection<NetworkPlayerInfo> infoMap = mc.getNetHandler().getPlayerInfoMap();
        if (infoMap == null) {
            return players;
        }
        UUID own = mc.thePlayer == null ? null : mc.thePlayer.getGameProfile().getId();
        for (NetworkPlayerInfo info : infoMap) {
            GameProfile profile = info.getGameProfile();
            if (profile == null || profile.getId() == null || profile.getName() == null) {
                continue;
            }
            // Never filter yourself out, whatever your profile looks like.
            if (!profile.getId().equals(own)
                    && !PlayerIdentity.isRealAccount(profile.getId(), profile.getName())) {
                continue;
            }
            players.add(new LobbyPlayer(profile.getId(), profile.getName(), info.getResponseTime()));
        }
        return players;
    }

    /** The scoreboard's title, which is what says which game you are in. */
    public static String readSidebarTitle() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.theWorld.getScoreboard() == null) {
            return "";
        }
        ScoreObjective sidebar = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(SIDEBAR_SLOT);
        return sidebar == null ? "" : sidebar.getDisplayName();
    }

    /** The sidebar's lines, top to bottom, with their formatting intact. */
    public static List<String> readSidebar() {
        List<String> lines = new ArrayList<String>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return lines;
        }
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) {
            return lines;
        }
        ScoreObjective sidebar = scoreboard.getObjectiveInDisplaySlot(SIDEBAR_SLOT);
        if (sidebar == null) {
            return lines;
        }
        for (Score score : scoreboard.getSortedScores(sidebar)) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            // The visible text is the team prefix and suffix wrapped around the entry name.
            lines.add(ScorePlayerTeam.formatPlayerName(team, score.getPlayerName()));
        }
        return lines;
    }

    /**
     * The team colour the server assigned each player, read from the scoreboard rather than
     * guessed from the tab list layout.
     */
    public static Map<String, TeamColour> readTeamAssignments(List<LobbyPlayer> players) {
        Map<String, TeamColour> assignments = new HashMap<String, TeamColour>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.theWorld.getScoreboard() == null) {
            return assignments;
        }
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        for (LobbyPlayer player : players) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(player.name);
            assignments.put(player.name, team == null
                    ? TeamColour.UNKNOWN
                    : TeamColour.fromColourCode(firstColourCode(team.getColorPrefix())));
        }
        return assignments;
    }

    private static char firstColourCode(String prefix) {
        if (prefix == null) {
            return '7';
        }
        for (int i = 0; i < prefix.length() - 1; i++) {
            if (prefix.charAt(i) == '§') {
                return prefix.charAt(i + 1);
            }
        }
        return '7';
    }

    /** @return the loaded entity for a player, or null when they are out of render range */
    public static EntityPlayer findEntity(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld == null ? null : mc.theWorld.getPlayerEntityByName(name);
    }
}
