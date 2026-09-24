package dev.vantage.game;

/** A team's standing in the current game, as read off the scoreboard. */
public final class TeamState {

    private final TeamColour colour;
    private final boolean bedIntact;
    private final boolean eliminated;
    private final int playersAlive;
    private final boolean yourTeam;

    public TeamState(TeamColour colour, boolean bedIntact, boolean eliminated, int playersAlive, boolean yourTeam) {
        this.colour = colour;
        this.bedIntact = bedIntact;
        this.eliminated = eliminated;
        this.playersAlive = playersAlive;
        this.yourTeam = yourTeam;
    }

    public TeamColour getColour() {
        return colour;
    }

    /** True while the team can still respawn. */
    public boolean isBedIntact() {
        return bedIntact;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    /** Survivors remaining once the bed is gone; -1 while the bed still stands. */
    public int getPlayersAlive() {
        return playersAlive;
    }

    public boolean isYourTeam() {
        return yourTeam;
    }

    @Override
    public String toString() {
        return colour.getDisplayName() + (eliminated ? " out" : bedIntact ? " bed" : " " + playersAlive + " left");
    }
}
