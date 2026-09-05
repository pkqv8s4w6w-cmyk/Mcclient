package dev.vantage.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LobbyReaderTest {

    private static LobbyReader.LobbyPlayer player(String name) {
        return new LobbyReader.LobbyPlayer(UUID.randomUUID(), name, 40);
    }

    private static List<String> namesOf(List<LobbyReader.LobbyPlayer> players) {
        List<String> names = new ArrayList<String>();
        for (LobbyReader.LobbyPlayer player : players) {
            names.add(player.name);
        }
        return names;
    }

    @Test
    void keepsOnlyPlayersOnATeam() {
        // The bug this fixes: the whole tab list was used, so people standing in the hub and
        // decorative entries turned up in the threat list alongside actual opponents.
        List<LobbyReader.LobbyPlayer> tabList = Arrays.asList(
                player("Notch"), player("LobbyIdler"), player("Player2"));
        List<Boolean> onATeam = Arrays.asList(true, false, true);

        assertEquals(Arrays.asList("Notch", "Player2"),
                namesOf(LobbyReader.keepParticipants(tabList, onATeam)));
    }

    @Test
    void keepsEveryoneWhenNobodyHasATeam() {
        // Outside a team-based game nobody is assigned one, and filtering on it there would empty
        // the list rather than leaving it alone.
        List<LobbyReader.LobbyPlayer> tabList = Arrays.asList(player("A"), player("B"));
        List<Boolean> onATeam = Arrays.asList(false, false);

        assertEquals(Arrays.asList("A", "B"), namesOf(LobbyReader.keepParticipants(tabList, onATeam)));
    }

    @Test
    void anEmptyListStaysEmpty() {
        assertEquals(0, LobbyReader.keepParticipants(
                new ArrayList<LobbyReader.LobbyPlayer>(), new ArrayList<Boolean>()).size());
    }

    @Test
    void orderIsPreserved() {
        List<LobbyReader.LobbyPlayer> tabList = Arrays.asList(
                player("First"), player("Skipped"), player("Second"), player("Third"));
        List<Boolean> onATeam = Arrays.asList(true, false, true, true);

        assertEquals(Arrays.asList("First", "Second", "Third"),
                namesOf(LobbyReader.keepParticipants(tabList, onATeam)));
    }

    @Test
    void mismatchedInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LobbyReader.keepParticipants(
                Arrays.asList(player("A")), Arrays.asList(true, false)));
    }
}
