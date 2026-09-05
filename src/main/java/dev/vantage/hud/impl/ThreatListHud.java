package dev.vantage.hud.impl;

import dev.vantage.Vantage;
import dev.vantage.detect.Flagged;
import dev.vantage.game.TeamColour;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.hypixel.ApiKeyStore;
import dev.vantage.module.Category;
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
 * Ranks everyone in the game by how dangerous they are, most dangerous at the top.
 *
 * <p>Deliberately just a score and a name in their team's colour. The earlier version also showed
 * team, final kill ratio, star and gear in aligned columns, which was four numbers asking to be
 * read and compared when the whole point of the score is that it already accounts for them.
 */
public class ThreatListHud extends HudModule {

    private static final float ROW_HEIGHT = 10.0f;
    private static final float GAP = 5.0f;
    private static final float FLAG_SIZE = 4.0f;

    private final NumberSetting maxPlayers = register(new NumberSetting(
            "Max Players", "How many rows to show", 8, 1, 16, 1));
    private final BooleanSetting enemiesOnly = register(new BooleanSetting(
            "Enemies Only", "Hide your own team", false));
    private final BooleanSetting scoreOnRight = register(new BooleanSetting(
            "Score On Right", "Put the number after the name instead of before it", false));
    private final StringSetting apiKey = register(new StringSetting(
            "Hypixel API Key", "From developer.hypixel.net. Stored outside your config profile.",
            "", 64, true));

    private final ThreatTracker tracker = new ThreatTracker();
    private ApiKeyStore keyStore;

    private float scoreColumn;
    private float nameColumn;
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

    private boolean keyLoaded;

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

    private static String scoreText(ThreatEntry entry) {
        return String.format(Locale.ROOT, "%.1f", entry.getScore());
    }

    // -- layout -----------------------------------------------------------------------------

    @Override
    public float getContentWidth() {
        List<ThreatEntry> entries = visibleEntries();
        rowsShown = entries.size();

        // A fixed score column keeps the names aligned; "10.0" is the widest it gets.
        scoreColumn = Fonts.SMALL_BOLD.getWidth("10.0");
        nameColumn = 0.0f;
        for (ThreatEntry entry : entries) {
            float width = Fonts.SMALL.getWidth(entry.getName());
            if (entry.isFlaggedForCheating()) {
                width += FLAG_SIZE + 3.0f;
            }
            nameColumn = Math.max(nameColumn, width);
        }

        String status = tracker.getStatusMessage();
        float total = scoreColumn + GAP + nameColumn;
        if (status != null) {
            total = Math.max(total, Fonts.SMALL.getWidth(status));
        }
        if (entries.isEmpty() && status == null) {
            total = Math.max(total, Fonts.SMALL.getWidth("Waiting for players"));
        }
        measuredWidth = Math.max(58.0f, total);
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
        int scoreColour = Theme.threatColour(entry.getScore());
        boolean flagged = entry.isFlaggedForCheating();
        int nameColour = flagged
                ? Theme.danger()
                : TeamColour.fromName(entry.getTeam()).getArgb();

        String score = scoreText(entry);
        float nameX;
        if (scoreOnRight.value()) {
            nameX = 0.0f;
            Fonts.SMALL_BOLD.drawRightAligned(score, measuredWidth, y, scoreColour);
        } else {
            nameX = scoreColumn + GAP;
            // Right-aligned inside its column so the decimal points line up down the list.
            Fonts.SMALL_BOLD.drawRightAligned(score, scoreColumn, y, scoreColour);
        }

        float nameEnd = Fonts.SMALL.drawString(entry.getName(), nameX, y, nameColour);

        if (flagged) {
            // A shape as well as the colour, so the mark survives a colour-blind reading.
            RenderUtil.circle(nameEnd + 3.0f + FLAG_SIZE / 2.0f, y + ROW_HEIGHT / 2.0f - 1.0f,
                    FLAG_SIZE / 2.0f, Theme.danger());
        }
    }
}
