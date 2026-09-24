package dev.vantage.module;

import dev.vantage.gui.Icons;

/**
 * Top-level grouping shown as the menu's sidebar.
 *
 * <p>{@link #CLIENT} is not listed there: its modules are actions such as opening the HUD editor,
 * and they appear on the Settings page instead.
 */
public enum Category {

    COMBAT("Combat", "Aim, reach and knockback", Icons.SWORDS),
    MOVEMENT("Movement", "Speed, flight and falls", Icons.FOOTPRINTS),
    PLAYER("Player", "Building, tools and inventory", Icons.PICKAXE),
    VISUAL("Visuals", "See more, react faster", Icons.EYE),
    BEDWARS("Bedwars", "Beds, rushes and resources", Icons.BED),
    ANALYSIS("Analysis", "Threat ranking and cheat detection", Icons.RADAR),
    HUD("HUD", "On-screen information", Icons.LAYOUT_DASHBOARD),
    UTILITY("Misc", "Quality of life tools", Icons.WRENCH),
    CLIENT("Client", "Vantage itself", Icons.SETTINGS);

    private final String displayName;
    private final String description;
    private final char icon;

    Category(String displayName, String description, char icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public char getIcon() {
        return icon;
    }

    /** Whether this category gets its own entry in the sidebar. */
    public boolean isListed() {
        return this != CLIENT;
    }
}
