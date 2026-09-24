package dev.vantage.threat;

/** What a player is visibly wearing and holding, which the client can read off their entity. */
public final class Gear {

    /** Ordered worst to best. Points are on the same 0-10 scale the engine works in. */
    public enum Armour {
        NONE(0.0), LEATHER(2.0), CHAINMAIL(4.5), IRON(7.0), DIAMOND(9.0);

        private final double points;

        Armour(double points) {
            this.points = points;
        }

        public double getPoints() {
            return points;
        }
    }

    public enum Weapon {
        NONE(0.0), WOOD(1.5), STONE(3.5), IRON(6.5), DIAMOND(8.5);

        private final double points;

        Weapon(double points) {
            this.points = points;
        }

        public double getPoints() {
            return points;
        }
    }

    public static final Gear EMPTY = new Gear(Armour.NONE, 0, Weapon.NONE, 0);

    private final Armour armour;
    private final int protection;
    private final Weapon weapon;
    private final int sharpness;

    public Gear(Armour armour, int protection, Weapon weapon, int sharpness) {
        this.armour = armour == null ? Armour.NONE : armour;
        this.protection = Math.max(0, Math.min(4, protection));
        this.weapon = weapon == null ? Weapon.NONE : weapon;
        this.sharpness = Math.max(0, Math.min(5, sharpness));
    }

    public Armour getArmour() {
        return armour;
    }

    public int getProtection() {
        return protection;
    }

    public Weapon getWeapon() {
        return weapon;
    }

    public int getSharpness() {
        return sharpness;
    }

    /** Two-letter shorthand for the threat list, e.g. "D/I" for diamond armour and an iron sword. */
    public String shorthand() {
        return letter(armour.name()) + "/" + letter(weapon.name());
    }

    private static String letter(String name) {
        return "NONE".equals(name) ? "-" : name.substring(0, 1);
    }
}
