package dev.vantage.mods;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds the installed mods and, where possible, the config file belonging to each.
 *
 * <p>Forge does not record which file a mod's settings live in, so matching is by name. That is a
 * heuristic and it will miss some; any config it cannot attribute is still listed on its own so
 * nothing is silently hidden.
 */
public final class ModsManager {

    /** Mods that are part of the platform rather than something the player installed. */
    private static final List<String> PLATFORM_IDS =
            java.util.Arrays.asList("mcp", "fml", "forge", "minecraft");

    private ModsManager() {
    }

    public static List<InstalledMod> listMods() {
        List<InstalledMod> mods = new ArrayList<InstalledMod>();
        File configDirectory = Loader.instance().getConfigDir();

        for (ModContainer container : Loader.instance().getActiveModList()) {
            String id = container.getModId();
            if (PLATFORM_IDS.contains(id.toLowerCase(Locale.ROOT))) {
                continue;
            }
            mods.add(new InstalledMod(id, container.getName(), container.getVersion(),
                    findConfig(configDirectory, id, container.getName())));
        }
        Collections.sort(mods, new Comparator<InstalledMod>() {
            @Override
            public int compare(InstalledMod left, InstalledMod right) {
                // Editable mods first; that is what someone opening this screen came for.
                if (left.isEditable() != right.isEditable()) {
                    return left.isEditable() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return mods;
    }

    /** Config files in the config directory that no loaded mod claimed. */
    public static List<File> findUnattributedConfigs(List<InstalledMod> mods) {
        File directory = Loader.instance().getConfigDir();
        List<File> orphans = new ArrayList<File>();
        File[] candidates = directory.listFiles();
        if (candidates == null) {
            return orphans;
        }
        for (File candidate : candidates) {
            if (!candidate.isFile() || !candidate.getName().toLowerCase(Locale.ROOT).endsWith(".cfg")) {
                continue;
            }
            boolean claimed = false;
            for (InstalledMod mod : mods) {
                if (candidate.equals(mod.getConfigFile())) {
                    claimed = true;
                    break;
                }
            }
            if (!claimed) {
                orphans.add(candidate);
            }
        }
        Collections.sort(orphans, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return orphans;
    }

    private static File findConfig(File directory, String modId, String modName) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }
        String[] candidates = {
                modId + ".cfg",
                modName + ".cfg",
                modId.toLowerCase(Locale.ROOT) + ".cfg",
                modName.replace(" ", "") + ".cfg",
        };
        for (String candidate : candidates) {
            File file = new File(directory, candidate);
            if (file.isFile()) {
                return file;
            }
        }
        // Some mods keep a folder of their own; take its main file if there is an obvious one.
        File folder = new File(directory, modId);
        if (folder.isDirectory()) {
            File nested = new File(folder, modId + ".cfg");
            if (nested.isFile()) {
                return nested;
            }
        }
        return null;
    }
}
