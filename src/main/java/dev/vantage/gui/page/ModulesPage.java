package dev.vantage.gui.page;

import dev.vantage.gui.Animated;
import dev.vantage.gui.Theme;
import dev.vantage.gui.component.ModuleCard;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.gui.widget.Widgets;
import dev.vantage.module.Category;
import dev.vantage.module.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A list of module cards: one category, or everything matching the search.
 *
 * <p>The header carries an All / Enabled / Disabled filter with an underline that slides between
 * the three, and a count of what is shown.
 */
public class ModulesPage extends Page {

    private enum Filter { ALL, ENABLED, DISABLED }

    private static final float CARD_GAP = 6.0f;

    private final String title;
    private final String subtitle;
    private final Supplier<List<ModuleCard>> source;
    private final Animated underlineX = new Animated(0.0, 0.05);
    private final Animated underlineWidth = new Animated(0.0, 0.05);
    private Filter filter = Filter.ALL;
    private final List<Widgets.Rect> tabBounds = new ArrayList<Widgets.Rect>();

    public ModulesPage(String title, String subtitle, Supplier<List<ModuleCard>> source) {
        this.title = title;
        this.subtitle = subtitle;
        this.source = source;
    }

    /** A page for one category. */
    public static ModulesPage forCategory(Category category, List<ModuleCard> all) {
        return new ModulesPage(category.getDisplayName(), category.getDescription(), () -> {
            List<ModuleCard> cards = new ArrayList<ModuleCard>();
            for (ModuleCard card : all) {
                if (card.getModule().getCategory() == category) {
                    cards.add(card);
                }
            }
            return cards;
        });
    }

    /** A page listing modules whose name or description contains the query. */
    public static ModulesPage forSearch(Supplier<String> query, List<ModuleCard> all) {
        return new ModulesPage("Search", "Every module that matches", () -> {
            String needle = query.get().trim().toLowerCase(Locale.ROOT);
            List<ModuleCard> cards = new ArrayList<ModuleCard>();
            for (ModuleCard card : all) {
                Module module = card.getModule();
                if (module.getCategory() == Category.CLIENT) {
                    continue;
                }
                if (module.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || module.getDescription().toLowerCase(Locale.ROOT).contains(needle)
                        || module.getCategory().getDisplayName().toLowerCase(Locale.ROOT).contains(needle)) {
                    cards.add(card);
                }
            }
            return cards;
        });
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String subtitle() {
        return subtitle;
    }

    private List<ModuleCard> shown() {
        List<ModuleCard> cards = new ArrayList<ModuleCard>();
        for (ModuleCard card : source.get()) {
            boolean enabled = card.getModule().isEnabled();
            if (filter == Filter.ALL || (filter == Filter.ENABLED) == enabled) {
                cards.add(card);
            }
        }
        return cards;
    }

    @Override
    public void renderHeaderControls(float right, float y, float mouseX, float mouseY) {
        String count = shown().size() + (shown().size() == 1 ? " module" : " modules");
        float countWidth = Fonts.TINY.getWidth(count);
        Fonts.TINY.drawString(count, right - countWidth, y + 4.0f, Theme.textFaint());

        String[] labels = {"All", "Enabled", "Disabled"};
        tabBounds.clear();
        float x = right - countWidth - 14.0f;
        float[] positions = new float[labels.length];
        float[] widths = new float[labels.length];
        for (int i = labels.length - 1; i >= 0; i--) {
            widths[i] = Fonts.SMALL.getWidth(labels[i]);
            x -= widths[i];
            positions[i] = x;
            x -= 12.0f;
        }
        for (int i = 0; i < labels.length; i++) {
            Widgets.Rect bounds = new Widgets.Rect(positions[i] - 3.0f, y, widths[i] + 6.0f, 14.0f);
            tabBounds.add(bounds);
            boolean active = filter.ordinal() == i;
            int colour = active ? Theme.accent() : (bounds.contains(mouseX, mouseY) ? Theme.text() : Theme.textMuted());
            Fonts.SMALL.drawString(labels[i], positions[i], y + 2.0f, colour);
        }
        underlineX.setTarget(positions[filter.ordinal()]);
        underlineWidth.setTarget(widths[filter.ordinal()]);
        RenderUtil.roundedRect((float) underlineX.get(), y + 13.0f, (float) underlineWidth.get(), 1.5f, 0.75, Theme.accent());
    }

    @Override
    public boolean headerClicked(float mouseX, float mouseY, int button) {
        for (int i = 0; i < tabBounds.size(); i++) {
            if (tabBounds.get(i).contains(mouseX, mouseY)) {
                filter = Filter.values()[i];
                return true;
            }
        }
        return false;
    }

    @Override
    public float renderBody(float x, float top, float width, float mouseX, float mouseY) {
        List<ModuleCard> cards = shown();
        float cursor = top;
        for (ModuleCard card : cards) {
            card.render(x, cursor, width, mouseX, mouseY);
            cursor += card.getHeight() + CARD_GAP;
        }
        if (cards.isEmpty()) {
            String empty = filter == Filter.ENABLED ? "Nothing switched on here yet"
                    : filter == Filter.DISABLED ? "Everything here is on" : "Nothing matches";
            Fonts.SMALL.drawCentred(empty, x + width / 2.0f, top + 30.0f, Theme.textFaint());
            return 60.0f;
        }
        return cursor - top;
    }

    @Override
    public boolean bodyClicked(float mouseX, float mouseY, int button) {
        for (ModuleCard card : shown()) {
            if (card.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(int button) {
        for (ModuleCard card : source.get()) {
            card.mouseReleased(button);
        }
    }

    @Override
    public boolean keyTyped(char character, int keyCode) {
        for (ModuleCard card : shown()) {
            if (card.keyTyped(character, keyCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCapturingInput() {
        for (ModuleCard card : shown()) {
            if (card.isCapturingInput()) {
                return true;
            }
        }
        return false;
    }
}
