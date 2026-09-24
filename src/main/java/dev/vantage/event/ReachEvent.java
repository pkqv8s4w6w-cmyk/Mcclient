package dev.vantage.event;

/**
 * The crosshair trace is about to run. Combat reach is how far an entity can be and still be hit;
 * block reach is how far a block can be and still be targeted. Vanilla survival is 3 and 4.5.
 */
public final class ReachEvent {

    private double combatReach;
    private double blockReach;

    public ReachEvent(double combatReach, double blockReach) {
        this.combatReach = combatReach;
        this.blockReach = blockReach;
    }

    public double getCombatReach() {
        return combatReach;
    }

    public void setCombatReach(double combatReach) {
        this.combatReach = combatReach;
    }

    public double getBlockReach() {
        return blockReach;
    }

    public void setBlockReach(double blockReach) {
        this.blockReach = blockReach;
    }
}
