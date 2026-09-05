package dev.vantage.threat;

import dev.vantage.hypixel.BedwarsStats;

/**
 * Everything known about one player at scoring time.
 *
 * <p>Plain data with no Minecraft types, so the engine can be exercised without a game running.
 * The adapter that reads the world builds these.
 */
public final class ThreatInput {

    private final String name;
    private final String team;
    private final char teamColourCode;
    private final BedwarsStats stats;
    private final Gear gear;
    private final boolean bedIntact;
    private final int killsThisGame;
    private final int deathsThisGame;
    private final boolean nicked;
    private final boolean self;
    private final boolean gearObserved;
    private final boolean flaggedForCheating;

    private ThreatInput(Builder builder) {
        this.name = builder.name;
        this.team = builder.team;
        this.teamColourCode = builder.teamColourCode;
        this.stats = builder.stats == null ? BedwarsStats.UNKNOWN : builder.stats;
        this.gear = builder.gear == null ? Gear.EMPTY : builder.gear;
        this.bedIntact = builder.bedIntact;
        this.killsThisGame = builder.killsThisGame;
        this.deathsThisGame = builder.deathsThisGame;
        this.nicked = builder.nicked;
        this.self = builder.self;
        this.gearObserved = builder.gearObserved;
        this.flaggedForCheating = builder.flaggedForCheating;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    public String getTeam() {
        return team;
    }

    public char getTeamColourCode() {
        return teamColourCode;
    }

    public BedwarsStats getStats() {
        return stats;
    }

    public Gear getGear() {
        return gear;
    }

    public boolean isBedIntact() {
        return bedIntact;
    }

    public int getKillsThisGame() {
        return killsThisGame;
    }

    public int getDeathsThisGame() {
        return deathsThisGame;
    }

    public boolean isNicked() {
        return nicked;
    }

    public boolean isSelf() {
        return self;
    }

    /**
     * Whether their gear could actually be seen.
     *
     * <p>False when the player is outside render distance, which in Bedwars is most of the lobby
     * most of the time. Unknown gear is not the same as no gear, and treating it as none is what
     * made good players score low.
     */
    public boolean isGearObserved() {
        return gearObserved;
    }

    public boolean isFlaggedForCheating() {
        return flaggedForCheating;
    }

    public static final class Builder {
        private final String name;
        private String team = "";
        private char teamColourCode = 'f';
        private BedwarsStats stats;
        private Gear gear;
        private boolean bedIntact = true;
        private int killsThisGame;
        private int deathsThisGame;
        private boolean nicked;
        private boolean self;
        private boolean gearObserved;
        private boolean flaggedForCheating;

        private Builder(String name) {
            this.name = name;
        }

        public Builder team(String team, char colourCode) {
            this.team = team == null ? "" : team;
            this.teamColourCode = colourCode;
            return this;
        }

        public Builder stats(BedwarsStats stats) {
            this.stats = stats;
            return this;
        }

        /** Records gear that was actually read off a loaded entity. */
        public Builder gear(Gear gear) {
            this.gear = gear;
            this.gearObserved = gear != null;
            return this;
        }

        /** Marks their gear as unknown, so the factor drops out rather than scoring zero. */
        public Builder gearUnknown() {
            this.gear = Gear.EMPTY;
            this.gearObserved = false;
            return this;
        }

        public Builder flaggedForCheating(boolean flagged) {
            this.flaggedForCheating = flagged;
            return this;
        }

        public Builder bedIntact(boolean bedIntact) {
            this.bedIntact = bedIntact;
            return this;
        }

        public Builder killsThisGame(int kills) {
            this.killsThisGame = kills;
            return this;
        }

        public Builder deathsThisGame(int deaths) {
            this.deathsThisGame = deaths;
            return this;
        }

        public Builder nicked(boolean nicked) {
            this.nicked = nicked;
            return this;
        }

        public Builder self(boolean self) {
            this.self = self;
            return this;
        }

        public ThreatInput build() {
            return new ThreatInput(this);
        }
    }
}
