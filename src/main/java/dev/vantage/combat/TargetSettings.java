package dev.vantage.combat;

import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.Setting;

/**
 * The "who counts as a target" settings every combat module shares, so they read the same and
 * behave the same everywhere. A module registers {@link #all()} alongside its own settings.
 */
public final class TargetSettings {

    public final BooleanSetting players = new BooleanSetting("Players", "Target other players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Target hostile mobs", false);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Target passive mobs and villagers", false);
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Target invisible entities", true);
    public final BooleanSetting teams = new BooleanSetting("Ignore Team", "Leave your own team alone", true);
    public final BooleanSetting walls = new BooleanSetting("Through Walls", "Target entities you cannot see", false);

    public Setting<?>[] all() {
        return new Setting<?>[]{players, mobs, animals, invisibles, teams, walls};
    }
}
