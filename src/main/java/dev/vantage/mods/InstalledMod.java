package dev.vantage.mods;

import java.io.File;

/** One mod Forge has loaded, and its config file if a readable one was found. */
public final class InstalledMod {

    private final String id;
    private final String name;
    private final String version;
    private final File configFile;

    InstalledMod(String id, String name, String version, File configFile) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.configFile = configFile;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public File getConfigFile() {
        return configFile;
    }

    public boolean isEditable() {
        return configFile != null;
    }
}
