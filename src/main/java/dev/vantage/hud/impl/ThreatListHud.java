package dev.vantage.hud.impl;

import dev.vantage.Vantage;
import dev.vantage.detect.Flagged;
import dev.vantage.game.TeamColour;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.module.Category;
import dev.vantage.hypixel.ApiKeyStore;
import dev.vantage.hypixel.BedwarsStats;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.setting.StringSetting;
import dev.vantage.threat.ThreatEntry;
import dev.vantage.threat.ThreatTracker;
import dev.vantage.threat.ThreatWeights;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Ranks everyone in the lobby by how dangerous they are, most dangerous at the top.
 *
 * <p>Columns are measured against the current contents each frame so they stay aligned as names
 * and numbers change, rather than being padded to a guessed width.
 */
public class ThreatListHud extends HudModule {

    private static final float ROW_HEIGHT = 11.0f;
    private static final float COLUMN_GAP = 6.0f;
    private static final float HEADER_HEIGHT = 12.0f;

    private final NumberSetting maxPlayers = register(new NumberSetting(
            "Max Players", "How many rows to show", 8, 1, 16, 1));
    private final BooleanSetting showHeader = register(new BooleanSetting(
            "Header", "Show a title row", true));
    private final BooleanSetting showTeam = register(new BooleanSetting(
            "Team", "Show which team each player is on", true));
    private final BooleanSetting showRatio = register(new BooleanSetting(
            "FKDR", "Show final kill/death ratio", true));
    private final BooleanSetting showStar = register(new BooleanSetting(
            "Star", "Show Bedwars level", true));
    private final BooleanSetting showGear = register(new BooleanSetting(
            "Gear", "Show current armour and weapon", true));
    private final BooleanSetting enemiesOnly = register(new BooleanSetting(
            "Enemies Only", "Hide your own team", false));
    private final BooleanSetting markDetections = register(new BooleanSetting(
            "Mark Detections", "Highlight players the cheat detector has flagged", true));

    private final NumberSetting statsWeight = register(new NumberSetting(
            "Stats Weight", "How much lifetime stats count", 45, 0, 100, 5, "%"));
    private final NumberSetting gearWeight = register(new NumberSetting(
            "Gear Weight", "How much current gear counts", 35, 0, 100, 5, "%"));
    private final NumberSetting momentumWeight = register(new NumberSetting(
            "Momentum Weight", "How much this game's kills and bed count", 20, 0, 100, 5, "%"));

    private final StringSetting apiKey = register(new StringSetting(
            "Hypixel API Key", "From developer.hypixel.net. Stored outside your config profile.",
            "", 64, true));

    private final ThreatTracker tracker = new ThreatTracker();
    private ApiKeyStore keyStore;

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

        // The key lives in its own file, never in the shared config profile.
        apiKey.notPersisted();
        apiKey.onChange(value -> {
            tracker.setApiKey(value);
            persistKey(value);
        });
    }

    /** Loads the stored key on first use, once the client's data directory is known. */
    private void ensureKeyLoaded() {
        if (keyStore != null) {
            return;
        }
        keyStore = new ApiKeyStore(Vantage.instance().config().getRoot());
        String stored = keyStore.load();
        if (!stored.isEmpty()) {
            // Set the field without re-triggering a save of what we just read.
            apiKey.set(stored);
            tracker.setApiKey(stored);
        }
    }

    private void persistKey(String value) {
        if (keyStore == null) {
            return;
        }
        try {
            if (value == null || value.trim().isEmpty()) {
                keyStore.clear();
            } else {
                keyStore.save(value);
            }
        } catch (IOException failure) {
            // Deliberately does not log the value.
            Vantage.LOGGER.error("Could not write the API key file", failure);
        }
    }

    private ThreatWeights weights() {
        return new ThreatWeights(
                statsWeight.asDouble() / 100.0,
                gearWeight.asDouble() / 100.0,
                momentumWeight.asDouble() / 100.0);
    }

    @Override
    public void onTick() {
        ensureKeyLoaded();
        tracker.tick(weights(), enemiesOnly.value());
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
        int limit = Math.min(all.size(), maxPlayers.asInt());
        return all.subList(0, Math.max(0, limit));
    }

    // -- layout -----------------------------------------------------------------------------

    @Override
    public float getContentWidth() {
        List<ThreatEntry> entries = visibleEntries();
        rowsShown = entries.size();

        scoreColumn = Fonts.SMALL_BOLD.getWidth("10.0");
        teamColumn = 0.0f;
        nameColumn = 0.0f;
        ratioColumn = 0.0f;
        starColumn = 0.0f;
        gearColumn = 0.0f;

        for (ThreatEntry entry : entries) {
            if (showTeam.value()) {
                teamColumn = Math.max(teamColumn, Fonts.SMALL.getWidth(entry.getTeam()));
            }
            float nameWidth = Fonts.SMALL.getWidth(entry.getName());
            if (markDetections.value() && Flagged.is(entry.getName())) {
                nameWidth += 6.0f; // room for the detection dot
            }
            nameColumn = Math.max(nameColumn, nameWidth);
            if (showRatio.value()) {
                ratioColumn = Math.max(ratioColumn, Fonts.SMALL.getWidth(ratioText(entry)));
            }
            if (showStar.value()) {
                starColumn = Math.max(starColumn, Fonts.SMALL.getWidth(starText(entry)));
            }
            if (showGear.value()) {
                gearColumn = Math.max(gearColumn, Fonts.SMALL.getWidth(entry.getInput().getGear().shorthand()));
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
        measuredWidth = Math.max(96.0f, total);
        return measuredWidth;
    }

    @Override
    public float getContentHeight() {
        float header = showHeader.value() ? HEADER_HEIGHT : 0.0f;
        int rows = Math.max(1, rowsShown);
        return header + rows * ROW_HEIGHT;
    }

    // -- drawing ----------------------------------------------------------------------------

    @Override
    protected void renderContent() {
        float y = 0.0f;

        if (showHeader.value()) {
            Fonts.SMALL_BOLD.drawString("THREAT", 0.0f, 0.0f, Theme.TEXT);
            String count = rowsShown + (rowsShown == 1 ? " player" : " players");
            Fonts.TINY.drawRightAligned(count, measuredWidth, 1.0f, Theme.TEXT_FAINT);
            RenderUtil.rect(0.0f, HEADER_HEIGHT - 3.0f, measuredWidth, 0.5f, Theme.DIVIDER);
            y += HEADER_HEIGHT;
        }

        List<ThreatEntry> entries = visibleEntries();
        if (entries.isEmpty()) {
            String status = tracker.getStatusMessage();
            Fonts.SMALL.drawString(status == null ? "Waiting for players" : status, 0.0f, y,
                    status == null ? Theme.TEXT_FAINT : Theme.WARNING);
            return;
        }

        for (ThreatEntry entry : entries) {
            drawRow(entry, y);
            y += ROW_HEIGHT;
        }
    }

    private void drawRow(ThreatEntry entry, float y) {
        float x = 0.0f;
        int scoreColour = Theme.threatColour(entry.getScore());

        // A short bar behind the score reads faster than the number alone at a glance.
        RenderUtil.roundedRect(-1.5f, y - 0.5f, scoreColumn + 3.0f, ROW_HEIGHT - 1.0f, 2.0,
                RenderUtil.withAlpha(scoreColour, 0.16f));
        Fonts.SMALL_BOLD.drawString(String.format(Locale.ROOT, "%.1f", entry.getScore()), x, y, scoreColour);
        x += scoreColumn;

        if (teamColumn > 0.0f) {
            x += COLUMN_GAP;
            TeamColour team = TeamColour.fromName(entry.getTeam());
            Fonts.SMALL.drawString(entry.getTeam(), x, y, team.getArgb());
            x += teamColumn;
        }

        x += COLUMN_GAP;
        // An unresolved player is dimmed, so a name with no stats behind it is obvious.
        boolean flagged = markDetections.value() && Flagged.is(entry.getName());
        int nameColour = flagged
                ? Theme.DANGER
                : (entry.isStatsCounted() ? Theme.TEXT : Theme.TEXT_MUTED);
        Fonts.SMALL.drawString(entry.getName(), x, y, nameColour);
        if (flagged) {
            // A dot as well as the colour, so the mark survives a colour-blind reading.
            RenderUtil.circle(x + nameColumn + 3.0f, y + ROW_HEIGHT / 2.0f - 1.0f, 1.6f, Theme.DANGER);
        }
        x += nameColumn;

        if (ratioColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(ratioText(entry), x, y, Theme.TEXT_MUTED);
            x += ratioColumn;
        }
        if (starColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(starText(entry), x, y, Theme.TEXT_MUTED);
            x += starColumn;
        }
        if (gearColumn > 0.0f) {
            x += COLUMN_GAP;
            Fonts.SMALL.drawString(entry.getInput().getGear().shorthand(), x, y, Theme.TEXT_FAINT);
        }
    }

    private String ratioText(ThreatEntry entry) {
        BedwarsStats stats = entry.getInput().getStats();
        if (stats.isUnknown()) {
            return entry.getInput().isNicked() ? "nick" : "-";
        }
        return String.format(Locale.ROOT, "%.1f", stats.getFinalKillDeathRatio());
    }

    private String starText(ThreatEntry entry) {
        BedwarsStats stats = entry.getInput().getStats();
        return stats.isUnknown() ? "-" : "★" + stats.getStar();
    }
}
