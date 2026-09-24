package dev.vantage.threat;

import dev.vantage.game.TeamColour;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RosterTest {

    private static final Gear DIAMOND = new Gear(Gear.Armour.DIAMOND, 2, Gear.Weapon.DIAMOND, 1);
    private static final Gear IRON = new Gear(Gear.Armour.IRON, 0, Gear.Weapon.IRON, 0);

    private static Roster.Sighting seen(String name, TeamColour team, Gear gear) {
        return new Roster.Sighting(UUID.randomUUID(), name, false, team, gear);
    }

    private static List<Roster.Sighting> lobby(Roster.Sighting... sightings) {
        return Arrays.asList(sightings);
    }

    @Test
    void playersSeenNowAreListed() {
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, null), seen("Steve", TeamColour.BLUE, null)), 0L);
        assertEquals(2, roster.size());
        assertEquals(TeamColour.RED, roster.get("Notch").getTeam());
    }

    // -- the bug this exists for --------------------------------------------------------------

    @Test
    void oneMissedRebuildDoesNotDropAnyone() {
        // The regression: the list was rebuilt from the tab list several times a second, so a
        // single bad read made rows vanish and come back. A player has to be properly gone.
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, null)), 0L);
        roster.update(Collections.<Roster.Sighting>emptyList(), 100L);

        assertNotNull(roster.get("Notch"), "one empty read must not empty the list");
        assertEquals(1, roster.get("Notch").getAbsentRebuilds());
    }

    @Test
    void aPlayerWhoActuallyLeavesIsEventuallyDropped() {
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, null)), 0L);
        for (int rebuild = 0; rebuild < 5; rebuild++) {
            roster.update(Collections.<Roster.Sighting>emptyList(), 100L * rebuild);
        }
        assertNull(roster.get("Notch"), "someone gone for five rebuilds has left the game");
    }

    @Test
    void reappearingResetsTheAbsenceCount() {
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, null)), 0L);
        roster.update(Collections.<Roster.Sighting>emptyList(), 100L);
        roster.update(Collections.<Roster.Sighting>emptyList(), 200L);
        roster.update(lobby(seen("Notch", TeamColour.RED, null)), 300L);

        assertEquals(0, roster.get("Notch").getAbsentRebuilds());

        // And having come back, they get the full allowance again rather than being on a hair
        // trigger for the rest of the game.
        roster.update(Collections.<Roster.Sighting>emptyList(), 400L);
        roster.update(Collections.<Roster.Sighting>emptyList(), 500L);
        assertNotNull(roster.get("Notch"));
    }

    // -- gear ---------------------------------------------------------------------------------

    @Test
    void gearIsRememberedAfterTheyLeaveRenderDistance() {
        // Walking behind a wall used to wipe what they were carrying, which moved their rating.
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, DIAMOND)), 1_000L);
        roster.update(lobby(seen("Notch", TeamColour.RED, null)), 6_000L);

        assertSame(DIAMOND, roster.get("Notch").getGear());
        assertEquals(5_000L, roster.get("Notch").gearAgeMillis(6_000L));
    }

    @Test
    void seeingThemAgainRefreshesBothGearAndItsAge() {
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, IRON)), 1_000L);
        roster.update(lobby(seen("Notch", TeamColour.RED, DIAMOND)), 9_000L);

        assertSame(DIAMOND, roster.get("Notch").getGear());
        assertEquals(0L, roster.get("Notch").gearAgeMillis(9_000L));
    }

    @Test
    void gearIsNullUntilTheyHaveEverBeenSeenUpClose() {
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, null)), 0L);
        assertNull(roster.get("Notch").getGear(), "never seen is not the same as carrying nothing");
    }

    // -- teams --------------------------------------------------------------------------------

    @Test
    void aTeamAssignmentSurvivesTheScoreboardForgettingIt() {
        // Hypixel drops scoreboard teams during the countdown and between rounds. Forgetting them
        // greys out the whole list at exactly the moment you are reading it.
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, null)), 0L);
        roster.update(lobby(seen("Notch", TeamColour.UNKNOWN, null)), 100L);
        assertEquals(TeamColour.RED, roster.get("Notch").getTeam());
    }

    @Test
    void aRealTeamChangeIsStillTakenUp() {
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, null)), 0L);
        roster.update(lobby(seen("Notch", TeamColour.BLUE, null)), 100L);
        assertEquals(TeamColour.BLUE, roster.get("Notch").getTeam());
    }

    // -- edges --------------------------------------------------------------------------------

    @Test
    void yourOwnEntryIsMarked() {
        Roster roster = new Roster();
        roster.update(Collections.singletonList(
                new Roster.Sighting(UUID.randomUUID(), "Me", true, TeamColour.GREEN, null)), 0L);
        assertEquals(true, roster.get("Me").isSelf());
    }

    @Test
    void malformedSightingsAreSkippedRatherThanCrashing() {
        Roster roster = new Roster();
        roster.update(Arrays.asList(null, new Roster.Sighting(UUID.randomUUID(), null, false, null, null),
                seen("Notch", TeamColour.RED, null)), 0L);
        assertEquals(1, roster.size());
        roster.update(null, 100L);
        assertNotNull(roster.get("Notch"));
    }

    @Test
    void clearingEmptiesIt() {
        Roster roster = new Roster();
        roster.update(lobby(seen("Notch", TeamColour.RED, null)), 0L);
        roster.clear();
        assertEquals(0, roster.size());
    }
}
