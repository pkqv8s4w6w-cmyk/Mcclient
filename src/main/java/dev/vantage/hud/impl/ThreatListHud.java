package dev.vantage.hud.impl;

import dev.vantage.Vantage;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Ranks everyone in the game by how dangerous they are, most dangerous at the top.
 *
 * <p>You are in the list too, in your real position, with your row picked out. That is the whole
 * point of a number on a scale: everyone above your row beats you more often than not, everyone
 * below does not, and you can tell at a glance which is which.
 *
 * <p>Columns are measured against the current contents every frame so they stay aligned as names
 * and numbers change, rather than being padded to a guessed width. Names carry their team's colour,
 * which is the fastest way to tell whether the player at the top of the list is someone you are
 * about to fight or someone standing next to you.
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
    private final BooleanSetting showSelf = register(new BooleanSetting(
            "Show Yourself", "Include your own row so you can see where you rank", true));
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
    private float fkdrColumn;
    private float wlrColumn;
    private float starColumn;
    private float gearColumn;
    private float measuredWidth;

    /**
     * The rows this frame is drawing.
     *
     * <p>Taken once in {@link #getContentWidth()} and reused by {@link #renderContent()}. The
     * tracker swaps its list from the client tick, so reading it twice in one frame could measure
     * one set of rows and then draw a different one, leaving the columns misaligned for a frame.
     */
    private List<ThreatEntry> frame = Collections.emptyList();

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
        tracker.tick(enemiesOnly.value());
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

    /**
     * The rows to draw: the top of the ranking, and your own row whether or not it made the cut.
     *
     * <p>Being ranked eleventh in a sixteen player lobby is exactly when you most want to know it,
     * so if your row falls outside the limit it takes the last slot rather than being dropped.
     */
    private List<ThreatEntry> selectRows() {
        List<ThreatEntry> all = tracker.getEntries();
        int limit = Math.max(1, maxPlayers.asInt());

        List<ThreatEntry> rows = new ArrayList<ThreatEntry>(limit);
        ThreatEntry self = null;
        for (ThreatEntry entry : all) {
            if (entry.isSelf()) {
                self = entry;
                if (!showSelf.value()) {
                    continue;
                }
            }
            if (rows.size() < limit) {
                rows.add(entry);
            }
        }

        if (showSelf.value() && self != null && !rows.contains(self)) {
            if (rows.size() >= limit && !rows.isEmpty()) {
                rows.remove(rows.size() - 1);
            }
            rows.add(self);
        }
        return rows;
    }

    // -- cell text --------------------------------------------------------------------------

    private static String scoreText(ThreatEntry entry) {
        return String.format(Locale.ROOT, "%.1f", entry.getScore());
    }

    /** A ratio, or a word saying why there is not one. */
    private static String ratio(ThreatEntry entry, double value) {
        BedwarsStats stats = entry.getInput().getStats();
        if (stats.isUnknown()) {
            return entry.getInput().isNicked() ? "nick" : "-";
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String fkdrText(ThreatEntry entry) {
        return ratio(entry, entry.getInput().getStats().getFinalKillDeathRatio());
    }

    private static String wlrText(ThreatEntry entry) {
        return ratio(entry, entry.getInput().getStats().getWinLossRatio());
    }

    /**
     * The name, marked up with the two things worth reading off it directly: whether it is you, and
     * how many kills they already have this game. A player on four kills is a different proposition
     * from the same player on none, and it costs no column to say so.
     */
    private static String nameText(ThreatEntry entry) {
        StringBuilder text = new StringBuilder();
        if (entry.isSelf()) {
            text.append("▸ ");
        }
        text.append(entry.getName());
        int kills = entry.getInput().getKillsThisGame();
        if (kills > 0) {
            text.append(" ×").append(kills);
        }
        return text.toString();
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
        frame = selectRows();

        // A fixed score column keeps names aligned; "10.0" is the widest it gets.
        scoreColumn = Fonts.SMALL_BOLD.getWidth("10.0");
        teamColumn = 0.0f;
        nameColumn = 0.0f;
        fkdrColumn = 0.0f;
        wlrColumn = 0.0f;
        starColumn = 0.0f;
        gearColumn = 0.0f;

        for (ThreatEntry entry : frame) {
            float nameWidth = Fonts.SMALL.getWidth(nameText(entry));
            if (entry.isFlaggedForCheating() || entry.isLowSample()) {
                nameWidth += FLAG_SIZE + 3.0f;
            }
            nameColumn = Math.max(nameColumn, nameWidth);

            if (showTeamAndGear()) {
                teamColumn = Math.max(teamColumn, Fonts.SMALL.getWidth(entry.getTeam()));
                gearColumn = Math.max(gearColumn,
                        Fonts.SMALL.getWidth(entry.getInput().getGear().shorthand()));
            }
            if (showStats()) {
                fkdrColumn = Math.max(fkdrColumn, Fonts.SMALL.getWidth(fkdrText(entry)));
                wlrColumn = Math.max(wlrColumn, Fonts.SMALL.getWidth(wlrText(entry)));
                starColumn = Math.max(starColumn, Fonts.SMALL.getWidth(starText(entry)));
            }
        }

        float total = scoreColumn;
        for (float column : new float[]{teamColumn, nameColumn, fkdrColumn, wlrColumn, starColumn,
                gearColumn}) {
            if (column > 0.0f) {
                total += COLUMN_GAP + column;
            }
        }

        String status = tracker.getStatusMessage();
        if (status != null) {
            total = Math.max(total, Fonts.SMALL.getWidth(status));
        }
        if (frame.isEmpty() && status == null) {
            total = Math.max(total, Fonts.SMALL.getWidth("Waiting for players"));
        }
        // A minimum keeps the panel from collapsing to a sliver between games.
        measuredWidth = Math.max(70.0f, total);
        return measuredWidth;
    }

    @Override
    public float getContentHeight() {
        return Math.max(1, frame.size()) * ROW_HEIGHT;
    }

    // -- drawing ----------------------------------------------------------------------------

    @Override
    protected void renderContent() {
        if (frame.isEmpty()) {
            String status = tracker.getStatusMessage();
            Fonts.SMALL.drawString(status == null ? "Waiting for players" : status, 0.0f, 0.0f,
                    status == null ? Theme.textFaint() : Theme.warning());
            return;
        }

        float y = 0.0f;
        for (ThreatEntry entry : frame) {
            drawRow(entry, y);
            y += ROW_HEIGHT;
        }
    }

    private void drawRow(ThreatEntry entry, float y) {
        boolean flagged = entry.isFlaggedForCheating();
        int teamColour = TeamColour.fromName(entry.getTeam()).getArgb();
        int scoreColour = Theme.threatColour(entry.getScore());

        if (entry.isSelf()) {
            // A band behind the row as well as the marker below, so your own line is findable at a
            // glance and stays findable without relying on colour.
            RenderUtil.roundedRect(-2.0, y - 1.0, measuredWidth + 4.0, ROW_HEIGHT,
                    Theme.ROW_RADIUS, RenderUtil.withAlpha(Theme.accent(), 0.18f));
        }

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
        float nameEnd = Fonts.SMALL.drawString(nameText(entry), x, y,
                flagged ? Theme.danger() : teamColour);
        if (flagged) {
            // A shape as well as the colour, so the mark survives a colour-blind reading.
            RenderUtil.circle(nameEnd + 3.0f + FLAG_SIZE / 2.0f, y + ROW_HEIGHT / 2.0f - 1.0f,
                    FLAG_SIZE / 2.0f, Theme.danger());
        } else if (entry.isLowSample()) {
            // A hollow mark for a score resting on too few games to mean much: the number is a
            // guess, and saying so is more useful than quietly presenting it as a measurement.
            RenderUtil.roundedOutline(nameEnd + 3.0f, y + ROW_HEIGHT / 2.0f - 1.0f - FLAG_SIZE / 2.0f,
                    FLAG_SIZE, FLAG_SIZE, FLAG_SIZE / 2.0, 0.6f, Theme.textFaint());
        }
        x += nameColumn;

        if (fkdrColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(fkdrText(entry), x, y, Theme.textMuted());
            x += fkdrColumn;
        }
        if (wlrColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(wlrText(entry), x, y, Theme.textMuted());
            x += wlrColumn;
        }
        if (starColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(starText(entry), x, y, Theme.textMuted());
            x += starColumn;
        }
        if (gearColumn > 0.0f) {
            x += COLUMN_GAP;
            // Dimmed once their bed is gone: the same loadout is a smaller problem when killing
            // them once ends it.
            Fonts.SMALL.drawString(entry.getInput().getGear().shorthand(), x, y,
                    entry.getInput().isBedIntact() ? Theme.textFaint()
                            : RenderUtil.withAlpha(Theme.textFaint(), 0.5f));
        }
    }
}
