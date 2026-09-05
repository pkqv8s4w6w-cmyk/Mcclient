package dev.vantage.hud.impl;

import dev.vantage.Vantage;
import dev.vantage.detect.Flagged;
import dev.vantage.game.TeamColour;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.hypixel.ApiKeyStore;
import dev.vantage.hypixel.BedwarsStats;
import dev.vantage.module.Category;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.setting.StringSetting;
import dev.vantage.threat.ThreatEntry;
import dev.vantage.threat.ThreatTracker;
import dev.vantage.threat.ThreatWeights;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Ranks everyone in the game by how dangerous they are, most dangerous at the top.
 *
 * <p>Columns are measured against the current contents every frame so they stay aligned as names
 * and numbers change, rather than being padded to a guessed width. Names carry their team's colour,
 * which is the fastest way to tell at a glance whether the player at the top of the list is someone
 * you are about to fight or someone standing next to you.
 */
public class ThreatListHud extends HudModule {

    /** How much of each player to show. One setting rather than a toggle per column. */
    public enum Detail {
        /** Just the number and who it belongs to. */
        SCORE,
        /** Adds the stats behind the number. */
        STATS,
        /** Adds their team and what they are carrying. */
        FULL
    }

    private static final float ROW_HEIGHT = 10.0f;
    private static final float COLUMN_GAP = 5.0f;
    private static final float FLAG_SIZE = 4.0f;

    private final NumberSetting maxPlayers = register(new NumberSetting(
            "Max Players", "How many rows to show", 8, 1, 16, 1));
    private final EnumSetting<Detail> detail = register(new EnumSetting<Detail>(
            "Detail", "How much to show for each player", Detail.FULL));
    private final BooleanSetting enemiesOnly = register(new BooleanSetting(
            "Enemies Only", "Hide your own team", false));
    private final StringSetting apiKey = register(new StringSetting(
            "Hypixel API Key", "From developer.hypixel.net. Stored outside your config profile.",
            "", 64, true));

    private final ThreatTracker tracker = new ThreatTracker();
    private ApiKeyStore keyStore;
    private boolean keyLoaded;

    // Column widths, measured once per frame in getContentWidth and reused by renderContent.
    private float scoreColumn;
    private float teamColumn;
    private float nameColumn;
    private float ratioColumn;
    private float starColumn;
    private float gearColumn;
    private float measuredWidth;
    private int rowsShown;

    public ThreatListHud() {
        super("Threat List", Category.ANALYSIS, "Ranks the lobby by how dangerous each player is");
        apiKey.notPersisted();
        apiKey.onChange(value -> {
            tracker.setApiKey(value);
            persistKey(value);
        });
    }

    // -- API key ----------------------------------------------------------------------------

    /**
     * The key file, created the first time anything needs it.
     *
     * <p>Built on demand rather than on the module's first tick. Tying it to the tick meant a key
     * typed before the module had ever ticked was held in memory and silently never written, so it
     * vanished on the next restart.
     */
    private ApiKeyStore keyStore() {
        if (keyStore == null) {
            keyStore = new ApiKeyStore(Vantage.instance().config().getRoot());
        }
        return keyStore;
    }

    private void ensureKeyLoaded() {
        if (keyLoaded) {
            return;
        }
        keyLoaded = true;
        String stored = keyStore().load();
        if (!stored.isEmpty()) {
            apiKey.set(stored);
            tracker.setApiKey(stored);
        }
    }

    private void persistKey(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                keyStore().clear();
            } else {
                keyStore().save(value);
            }
        } catch (IOException failure) {
            // Deliberately never logs the value.
            Vantage.LOGGER.error("Could not write the API key file", failure);
        }
    }

    // -- lifecycle --------------------------------------------------------------------------

    @Override
    public void onTick() {
        ensureKeyLoaded();
        tracker.tick(ThreatWeights.DEFAULT, enemiesOnly.value());
    }

    @Override
    public void onChatMessage(String raw) {
        tracker.onChatMessage(raw);
    }

    @Override
    public void onWorldChanged() {
        tracker.reset();
    }

    @Override
    protected void onDisable() {
        tracker.reset();
    }

    private List<ThreatEntry> visibleEntries() {
        List<ThreatEntry> all = tracker.getEntries();
        return all.subList(0, Math.max(0, Math.min(all.size(), maxPlayers.asInt())));
    }

    // -- cell text --------------------------------------------------------------------------

    private static String scoreText(ThreatEntry entry) {
        return String.format(Locale.ROOT, "%.1f", entry.getScore());
    }

    private static String ratioText(ThreatEntry entry) {
        BedwarsStats stats = entry.getInput().getStats();
        if (stats.isUnknown()) {
            return entry.getInput().isNicked() ? "nick" : "-";
        }
        return String.format(Locale.ROOT, "%.1f", stats.getFinalKillDeathRatio());
    }

    private static String starText(ThreatEntry entry) {
        BedwarsStats stats = entry.getInput().getStats();
        // The four-pointed star the obvious choice has no glyph in the bundled font, checked on a
        // real Java 8 runtime; the five-pointed one does.
        return stats.isUnknown() ? "-" : "★" + stats.getStar();
    }

    private boolean showStats() {
        return detail.get() != Detail.SCORE;
    }

    private boolean showTeamAndGear() {
        return detail.get() == Detail.FULL;
    }

    // -- layout -----------------------------------------------------------------------------

    @Override
    public float getContentWidth() {
        List<ThreatEntry> entries = visibleEntries();
        rowsShown = entries.size();

        // A fixed score column keeps names aligned; "10.0" is the widest it gets.
        scoreColumn = Fonts.SMALL_BOLD.getWidth("10.0");
        teamColumn = 0.0f;
        nameColumn = 0.0f;
        ratioColumn = 0.0f;
        starColumn = 0.0f;
        gearColumn = 0.0f;

        for (ThreatEntry entry : entries) {
            float nameWidth = Fonts.SMALL.getWidth(entry.getName());
            if (entry.isFlaggedForCheating()) {
                nameWidth += FLAG_SIZE + 3.0f;
            }
            nameColumn = Math.max(nameColumn, nameWidth);

            if (showTeamAndGear()) {
                teamColumn = Math.max(teamColumn, Fonts.SMALL.getWidth(entry.getTeam()));
                gearColumn = Math.max(gearColumn,
                        Fonts.SMALL.getWidth(entry.getInput().getGear().shorthand()));
            }
            if (showStats()) {
                ratioColumn = Math.max(ratioColumn, Fonts.SMALL.getWidth(ratioText(entry)));
                starColumn = Math.max(starColumn, Fonts.SMALL.getWidth(starText(entry)));
            }
        }

        float total = scoreColumn;
        for (float column : new float[]{teamColumn, nameColumn, ratioColumn, starColumn, gearColumn}) {
            if (column > 0.0f) {
                total += COLUMN_GAP + column;
            }
        }

        String status = tracker.getStatusMessage();
        if (status != null) {
            total = Math.max(total, Fonts.SMALL.getWidth(status));
        }
        if (entries.isEmpty() && status == null) {
            total = Math.max(total, Fonts.SMALL.getWidth("Waiting for players"));
        }
        // A minimum keeps the panel from collapsing to a sliver between games.
        measuredWidth = Math.max(70.0f, total);
        return measuredWidth;
    }

    @Override
    public float getContentHeight() {
        return Math.max(1, rowsShown) * ROW_HEIGHT;
    }

    // -- drawing ----------------------------------------------------------------------------

    @Override
    protected void renderContent() {
        List<ThreatEntry> entries = visibleEntries();
        if (entries.isEmpty()) {
            String status = tracker.getStatusMessage();
            Fonts.SMALL.drawString(status == null ? "Waiting for players" : status, 0.0f, 0.0f,
                    status == null ? Theme.textFaint() : Theme.warning());
            return;
        }

        float y = 0.0f;
        for (ThreatEntry entry : entries) {
            drawRow(entry, y);
            y += ROW_HEIGHT;
        }
    }

    private void drawRow(ThreatEntry entry, float y) {
        boolean flagged = entry.isFlaggedForCheating();
        int teamColour = TeamColour.fromName(entry.getTeam()).getArgb();
        int scoreColour = Theme.threatColour(entry.getScore());

        float x = 0.0f;
        // Right-aligned in its column so the decimal points line up down the list.
        Fonts.SMALL_BOLD.drawRightAligned(scoreText(entry), scoreColumn, y, scoreColour);
        x += scoreColumn;

        if (teamColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(entry.getTeam(), x, y, teamColour);
            x += teamColumn;
        }

        x += COLUMN_GAP;
        // Their team's colour, so who is on which side reads without checking the team column.
        // A flagged player overrides it, since that matters more than which team they are on.
        float nameEnd = Fonts.SMALL.drawString(entry.getName(), x,
                y, flagged ? Theme.danger() : teamColour);
        if (flagged) {
            // A shape as well as the colour, so the mark survives a colour-blind reading.
            RenderUtil.circle(nameEnd + 3.0f + FLAG_SIZE / 2.0f, y + ROW_HEIGHT / 2.0f - 1.0f,
                    FLAG_SIZE / 2.0f, Theme.danger());
        }
        x += nameColumn;

        if (ratioColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(ratioText(entry), x, y, Theme.textMuted());
            x += ratioColumn;
        }
        if (starColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(starText(entry), x, y, Theme.textMuted());
            x += starColumn;
        }
        if (gearColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(entry.getInput().getGear().shorthand(), x, y, Theme.textFaint());
        }
    }
}
