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
import java.util.regex.Pattern;

/** Pulls the current lobby out of the game: who is here, what team they are on, and their gear. */
public final class LobbyReader {

    /** Display slot 1 is the sidebar. */
    private static final int SIDEBAR_SLOT = 1;

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

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
     * Everyone taking part in the current game.
     *
     * <p>The raw tab list is not that. In a Bedwars lobby it carries every player standing around
     * the hub, and minigame tab lists carry decorative entries too, which is why unrelated names
     * were turning up in the threat list. Every participant in a Bedwars game is assigned to a
     * scoreboard team, so that assignment is the filter.
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

        Scoreboard scoreboard = mc.theWorld == null ? null : mc.theWorld.getScoreboard();
        List<Boolean> onATeam = new ArrayList<Boolean>();

        for (NetworkPlayerInfo info : infoMap) {
            GameProfile profile = info.getGameProfile();
            if (profile == null || profile.getId() == null || profile.getName() == null) {
                continue;
            }
            if (!VALID_NAME.matcher(profile.getName()).matches()) {
                continue;
            }
            players.add(new LobbyPlayer(profile.getId(), profile.getName(), info.getResponseTime()));
            onATeam.add(scoreboard != null && scoreboard.getPlayersTeam(profile.getName()) != null);
        }
        return keepParticipants(players, onATeam);
    }

    /**
     * Drops entries with no team, but only when at least one entry has one.
     *
     * <p>Separated out and free of Minecraft types so the rule can be tested. The fallback matters:
     * outside a team-based game nobody has a team, and filtering on it there would empty the list
     * rather than leaving it alone.
     *
     * @param players   candidates, in tab list order
     * @param onATeam   whether each has a scoreboard team, same length and order
     */
    public static List<LobbyPlayer> keepParticipants(List<LobbyPlayer> players, List<Boolean> onATeam) {
        if (players.size() != onATeam.size()) {
            throw new IllegalArgumentException("players and onATeam must line up");
        }
        boolean anyTeams = false;
        for (Boolean flag : onATeam) {
            if (Boolean.TRUE.equals(flag)) {
                anyTeams = true;
                break;
            }
        }
        if (!anyTeams) {
            return players;
        }
        List<LobbyPlayer> participants = new ArrayList<LobbyPlayer>(players.size());
        for (int i = 0; i < players.size(); i++) {
            if (Boolean.TRUE.equals(onATeam.get(i))) {
                participants.add(players.get(i));
            }
        }
        return participants;
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
