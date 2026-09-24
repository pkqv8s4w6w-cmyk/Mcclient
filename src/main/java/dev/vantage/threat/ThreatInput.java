package dev.vantage.threat;

import dev.vantage.hypixel.BedwarsStats;

/**
 * Everything known about one player at scoring time.
 *
 * <p>Plain data with no Minecraft types, so the engine can be exercised without a game running.
 * The adapter that reads the world builds these.
 *
 * <p>Some of what is carried here is shown but never scored. Bed state and this game's kills and
 * deaths are worth seeing — knowing an opponent cannot respawn changes how you fight them — but
 * they used to move the rating every few seconds without making it any more accurate, so
 * {@link ThreatEngine} ignores them.
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
    private final long gearAgeMillis;
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
        this.gearAgeMillis = builder.gearAgeMillis;
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

    /** Shown, not scored. */
    public boolean isBedIntact() {
        return bedIntact;
    }

    /** Shown, not scored. */
    public int getKillsThisGame() {
        return killsThisGame;
    }

    /** Shown, not scored. */
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
     * Whether their gear has ever been seen.
     *
     * <p>False when the player has not been inside render distance, which in Bedwars is most of the
     * lobby most of the time. Unknown gear is not the same as no gear, and treating it as none is
     * what made good players score low.
     */
    public boolean isGearObserved() {
        return gearObserved;
    }

    /**
     * How long ago that gear reading was taken.
     *
     * <p>Zero while they are on screen. Once they walk off, the reading is held for a while and
     * then fades, so a rating settles instead of jumping the instant someone rounds a corner.
     */
    public long getGearAgeMillis() {
        return gearAgeMillis;
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
        private long gearAgeMillis;
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

        /** Records gear read off a loaded entity this instant. */
        public Builder gear(Gear gear) {
            return gear(gear, 0L);
        }

        /**
         * Records gear last seen {@code ageMillis} ago.
         *
         * @param ageMillis 0 while they are on screen, rising once they leave render distance
         */
        public Builder gear(Gear gear, long ageMillis) {
            this.gear = gear;
            this.gearObserved = gear != null;
            this.gearAgeMillis = Math.max(0L, ageMillis);
            return this;
        }

        /** Marks their gear as never seen, so the factor drops out rather than scoring zero. */
        public Builder gearUnknown() {
            this.gear = Gear.EMPTY;
            this.gearObserved = false;
            this.gearAgeMillis = 0L;
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
