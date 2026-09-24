package dev.vantage.module;

/** Top-level grouping shown as the ClickGUI's left-hand rail. */
public enum Category {

    ANALYSIS("Analysis", "Threat ranking and cheat detection"),
    COMBAT("Combat", "Fighting other players"),
    VISUAL("Visual", "Rendering and world appearance"),
    HUD("HUD", "On-screen information"),
    UTILITY("Utility", "Quality of life"),
    MODS("Mods", "Other mods installed alongside Vantage"),
    CLIENT("Client", "Vantage itself");

    private final String displayName;
    private final String description;

    Category(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
