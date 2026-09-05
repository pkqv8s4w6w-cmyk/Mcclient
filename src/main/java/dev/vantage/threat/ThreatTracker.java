package dev.vantage.threat;

import dev.vantage.Vantage;
import dev.vantage.detect.Flagged;
import dev.vantage.game.DeathMessageParser;
import dev.vantage.game.GameDetector;
import dev.vantage.game.LobbyReader;
import dev.vantage.game.SidebarParser;
import dev.vantage.game.TeamColour;
import dev.vantage.game.TeamState;
import dev.vantage.hypixel.ApiResult;
import dev.vantage.hypixel.BedwarsStats;
import dev.vantage.hypixel.HypixelApi;
import dev.vantage.hypixel.RateLimiter;
import dev.vantage.hypixel.StatsCache;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Keeps the ranked player list up to date.
 *
 * <p>Lookups run on a background thread and only ever write into the cache; the list itself is
 * rebuilt on the client thread from whatever the cache holds at that moment. Nothing waits on the
 * network, so a slow API response cannot stall a frame.
 */
public final class ThreatTracker {

    /** Rebuild the list four times a second; more often just burns frames. */
    private static final int REBUILD_INTERVAL_TICKS = 5;

    private final StatsCache cache = new StatsCache(RateLimiter.SYSTEM);
    private final HypixelApi api = HypixelApi.live();
    private final ExecutorService lookups;

    private final Map<String, Integer> killsThisGame = new HashMap<String, Integer>();
    private final Map<String, Integer> deathsThisGame = new HashMap<String, Integer>();

    private volatile String apiKey = "";
    private volatile List<ThreatEntry> entries = Collections.emptyList();
    private volatile ApiResult.Status lastFailure;
    private volatile boolean inGame;

    private int tickCounter;

    public ThreatTracker() {
        this.lookups = Executors.newFixedThreadPool(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "Vantage-hypixel");
                // Daemon so a pending lookup can never hold the game open on exit.
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void setApiKey(String key) {
        this.apiKey = key == null ? "" : key.trim();
        if (!this.apiKey.isEmpty()) {
            // A new key deserves a fresh attempt at everything that failed under the old one.
            cache.clear();
            lastFailure = null;
        }
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    public List<ThreatEntry> getEntries() {
        return entries;
    }

    public StatsCache getCache() {
        return cache;
    }

    /** A short line explaining why the list is empty or incomplete, or null when all is well. */
    public String getStatusMessage() {
        if (!inGame) {
            return "Not in a game";
        }
        if (apiKey.isEmpty()) {
            return "No API key set";
        }
        ApiResult.Status failure = lastFailure;
        if (failure == null) {
            return null;
        }
        switch (failure) {
            case INVALID_KEY:
                return "API key rejected";
            case RATE_LIMITED:
                return "Rate limited";
            case UNAVAILABLE:
                return "API unreachable";
            case MALFORMED:
                return "Unexpected API response";
            default:
                return null;
        }
    }

    /** Drops per-game state. Called when the player joins a different world. */
    public void reset() {
        inGame = false;
        killsThisGame.clear();
        deathsThisGame.clear();
        entries = Collections.emptyList();
    }

    public void onChatMessage(String raw) {
        List<LobbyReader.LobbyPlayer> players = LobbyReader.readPlayers();
        Set<String> names = new HashSet<String>();
        for (LobbyReader.LobbyPlayer player : players) {
            names.add(player.name);
        }
        DeathMessageParser.Kill kill = DeathMessageParser.parse(raw, names);
        if (kill == null) {
            return;
        }
        increment(deathsThisGame, kill.getVictim());
        if (kill.hasKiller()) {
            increment(killsThisGame, kill.getKiller());
        }
    }

    private static void increment(Map<String, Integer> counter, String name) {
        Integer current = counter.get(name);
        counter.put(name, current == null ? 1 : current + 1);
    }

    public void tick(ThreatWeights weights, boolean enemiesOnly) {
        if (++tickCounter < REBUILD_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        // The tab list alone is not a lobby. In a hub it carries everyone standing around, which
        // is how unrelated names ended up in the list; the scoreboard title is what says whether
        // there is a game to list at all. Inside one, the tab list is exactly the participants.
        inGame = GameDetector.isBedwars(LobbyReader.readSidebarTitle());
        if (!inGame) {
            entries = Collections.emptyList();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        List<LobbyReader.LobbyPlayer> players = LobbyReader.readPlayers();
        Map<String, TeamColour> teamOf = LobbyReader.readTeamAssignments(players);
        Map<String, TeamState> teamStates = SidebarParser.parse(LobbyReader.readSidebar());

        String selfName = mc.thePlayer.getName();
        TeamColour ownTeam = teamOf.get(selfName);

        List<ThreatInput> inputs = new ArrayList<ThreatInput>(players.size());
        for (LobbyReader.LobbyPlayer player : players) {
            boolean self = player.name.equals(selfName);
            TeamColour team = teamOf.get(player.name);
            if (team == null) {
                team = TeamColour.UNKNOWN;
            }
            if (enemiesOnly && !self && ownTeam != null && ownTeam != TeamColour.UNKNOWN && team == ownTeam) {
                continue;
            }

            BedwarsStats stats = cache.get(player.uuid);
            if (stats == null) {
                stats = BedwarsStats.UNKNOWN;
                requestStats(player.uuid);
            }

            TeamState state = teamStates.get(team.getDisplayName().toLowerCase(java.util.Locale.ROOT));
            EntityPlayer entity = LobbyReader.findEntity(player.name);

            ThreatInput.Builder builder = ThreatInput.builder(player.name)
                    .team(team.getDisplayName(), team.getColourCode())
                    .stats(stats)
                    // Only call them nicked once a lookup actually came back empty; before that
                    // the stats are merely not fetched yet.
                    .nicked(stats.isUnknown() && !cache.needsFetch(player.uuid) && !cache.isFailed(player.uuid))
                    .bedIntact(state == null || state.isBedIntact())
                    .killsThisGame(count(killsThisGame, player.name))
                    .deathsThisGame(count(deathsThisGame, player.name))
                    .flaggedForCheating(Flagged.is(player.name))
                    .self(self);

            // Out of render distance means their gear is unknown, not absent. Scoring it as none
            // is what made good players read as harmless.
            if (entity == null) {
                builder.gearUnknown();
            } else {
                builder.gear(dev.vantage.game.GearReader.read(entity));
            }
            inputs.add(builder.build());
        }

        entries = ThreatEngine.rank(inputs, weights);
        cache.prune();
    }

    private static int count(Map<String, Integer> counter, String name) {
        Integer value = counter.get(name);
        return value == null ? 0 : value;
    }

    private void requestStats(final java.util.UUID uuid) {
        if (apiKey.isEmpty() || !cache.claim(uuid)) {
            return;
        }
        final String key = apiKey;
        lookups.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ApiResult result = api.fetch(uuid, key);
                    if (result.isOk()) {
                        cache.store(uuid, result.getStats());
                        lastFailure = null;
                    } else {
                        cache.storeFailure(uuid);
                        lastFailure = result.getStatus();
                    }
                } catch (Throwable failure) {
                    cache.storeFailure(uuid);
                    Vantage.LOGGER.warn("Stats lookup failed", failure);
                }
            }
        });
    }

    public void shutdown() {
        lookups.shutdownNow();
    }
}
