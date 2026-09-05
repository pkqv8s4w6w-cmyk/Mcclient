package dev.vantage.mods;

import dev.vantage.Vantage;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.setting.Setting;
import dev.vantage.setting.StringSetting;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes another mod's Forge config file as editable settings.
 *
 * <p>Works for mods using Forge's standard {@code .cfg} format, which is most of them on 1.8.9.
 * Mods that keep their settings in their own JSON or binary format cannot be read generically and
 * are listed but not editable - there is no shared interface to go through.
 *
 * <p>List-valued properties are skipped rather than half-supported: editing a comma separated list
 * through a text box is a good way to corrupt someone's config.
 */
public final class ForgeConfigFile {

    private final File file;
    private final Configuration configuration;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private final Map<Setting<?>, Property> backing = new IdentityHashMap<Setting<?>, Property>();

    private ForgeConfigFile(File file, Configuration configuration) {
        this.file = file;
        this.configuration = configuration;
    }

    /** @return the opened file, or null if it could not be read as a Forge config */
    public static ForgeConfigFile open(File file) {
        try {
            Configuration configuration = new Configuration(file);
            configuration.load();
            ForgeConfigFile opened = new ForgeConfigFile(file, configuration);
            opened.buildSettings();
            return opened.settings.isEmpty() ? null : opened;
        } catch (Throwable unreadable) {
            Vantage.LOGGER.debug("Not a readable Forge config: {}", file.getName(), unreadable);
            return null;
        }
    }

    private void buildSettings() {
        for (String categoryName : configuration.getCategoryNames()) {
            ConfigCategory category = configuration.getCategory(categoryName);
            for (Map.Entry<String, Property> entry : category.getValues().entrySet()) {
                Property property = entry.getValue();
                if (property.isList()) {
                    continue;
                }
                Setting<?> setting = toSetting(entry.getKey(), property);
                if (setting != null) {
                    settings.add(setting);
                    backing.put(setting, property);
                }
            }
        }
    }

    private Setting<?> toSetting(String name, Property property) {
        String comment = property.comment == null ? "" : property.comment;
        try {
            switch (property.getType()) {
                case BOOLEAN:
                    return new BooleanSetting(name, comment, property.getBoolean());
                case INTEGER: {
                    double min = parse(property.getMinValue(), Integer.MIN_VALUE);
                    double max = parse(property.getMaxValue(), Integer.MAX_VALUE);
                    // Unbounded properties would produce a slider spanning the whole int range,
                    // which no one can aim. Clamp the editable span to something usable.
                    double low = Math.max(min, -10000.0);
                    double high = Math.min(max, 10000.0);
                    if (low >= high) {
                        return null;
                    }
                    return new NumberSetting(name, comment,
                            clamp(property.getInt(), low, high), low, high, 1.0);
                }
                case DOUBLE: {
                    double min = Math.max(parse(property.getMinValue(), -10000.0), -10000.0);
                    double max = Math.min(parse(property.getMaxValue(), 10000.0), 10000.0);
                    if (min >= max) {
                        return null;
                    }
                    return new NumberSetting(name, comment,
                            clamp(property.getDouble(), min, max), min, max, 0.01);
                }
                case STRING:
                    return new StringSetting(name, comment, property.getString());
                default:
                    return null;
            }
        } catch (Throwable malformed) {
            // A property whose declared type does not match its value; leave it alone.
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double parse(String text, double fallback) {
        if (text == null || text.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return file.getName();
    }

    /** Writes the edited values back. Most mods read their config once, so a restart is needed. */
    public void save() {
        for (Map.Entry<Setting<?>, Property> entry : backing.entrySet()) {
            Setting<?> setting = entry.getKey();
            Property property = entry.getValue();
            if (setting instanceof BooleanSetting) {
                property.set(((BooleanSetting) setting).value());
            } else if (setting instanceof NumberSetting) {
                NumberSetting number = (NumberSetting) setting;
                if (property.getType() == Property.Type.INTEGER) {
                    property.set(number.asInt());
                } else {
                    property.set(number.asDouble());
                }
            } else if (setting instanceof StringSetting) {
                property.set(((StringSetting) setting).get());
            }
        }
        configuration.save();
    }
}
