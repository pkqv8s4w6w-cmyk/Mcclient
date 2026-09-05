package dev.vantage.threat;

import dev.vantage.Vantage;
import dev.vantage.detect.Flagged;
import dev.vantage.game.DeathMessageParser;
import dev.vantage.game.GameDetector;
import dev.vantage.game.GearReader;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Keeps the ranked player list up to date.
 *
 * <p>Lookups run on a background thread and only ever write into the cache; the list itself is
 * rebuilt on the client thread from whatever the cache holds at that moment. Nothing waits on the
 * network, so a slow API response cannot stall a frame.
 *
 * <p>The roster is <b>sticky</b>. A player stays listed until they have been missing from several
 * consecutive rebuilds, and their last known gear is remembered after they leave render distance.
 * Rebuilding the list from scratch every time is what made rows appear and disappear: one bad read
 * of the tab list, or one frame where the scoreboard was mid-update, used to empty the whole panel.
 */
public final class ThreatTracker {

    /** Rebuild the list four times a second; more often just burns frames. */
    private static final int REBUILD_INTERVAL_TICKS = 5;

    /**
     * How long the list survives the scoreboard saying you are not in a game.
     *
     * <p>The sidebar is briefly absent or replaced at several points in a normal game. Blanking the
     * panel the instant it changes is what made the list flicker.
     */
    private static final long GAME_GRACE_MILLIS = 4_000L;

    private final StatsCache cache = new StatsCache(RateLimiter.SYSTEM);
    private final HypixelApi api = HypixelApi.live();
    private final ExecutorService lookups;

    private final Roster roster = new Roster();
    private final Map<String, Integer> killsThisGame = new HashMap<String, Integer>();
    private final Map<String, Integer> deathsThisGame = new HashMap<String, Integer>();

    private volatile String apiKey = "";
    private volatile List<ThreatEntry> entries = Collections.emptyList();
    private volatile ApiResult.Status lastFailure;
    private volatile boolean inGame;

    private long lastInGameMillis;
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
        lastInGameMillis = 0L;
        roster.clear();
        killsThisGame.clear();
        deathsThisGame.clear();
        entries = Collections.emptyList();
    }

    public void onChatMessage(String raw) {
        Set<String> names = new HashSet<String>();
        for (Roster.Member member : roster.members()) {
            names.add(member.getName());
        }
        if (names.isEmpty()) {
            for (LobbyReader.LobbyPlayer player : LobbyReader.readPlayers()) {
                names.add(player.name);
            }
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

    public void tick(boolean enemiesOnly) {
        if (++tickCounter < REBUILD_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();

        // The tab list alone is not a lobby. In a hub it carries everyone standing around, which
        // is how unrelated names ended up in the list; the scoreboard title is what says whether
        // there is a game to list at all. Inside one, the tab list is exactly the participants.
        boolean titleSaysBedwars = GameDetector.isBedwars(LobbyReader.readSidebarTitle());
        if (titleSaysBedwars) {
            lastInGameMillis = now;
        } else if (now - lastInGameMillis < GAME_GRACE_MILLIS) {
            // Probably a momentary scoreboard change rather than the game ending. Hold what we have.
            return;
        } else {
            inGame = false;
            roster.clear();
            entries = Collections.emptyList();
            return;
        }
        inGame = true;

        if (mc.thePlayer == null) {
            return;
        }

        List<LobbyReader.LobbyPlayer> players = LobbyReader.readPlayers();
        Map<String, TeamColour> teamOf = LobbyReader.readTeamAssignments(players);
        Map<String, TeamState> teamStates = SidebarParser.parse(LobbyReader.readSidebar());
        String selfName = mc.thePlayer.getName();

        roster.update(sightings(players, teamOf, selfName), now);

        Roster.Member self = roster.get(selfName);
        TeamColour ownTeam = self == null ? null : self.getTeam();

        List<ThreatInput> inputs = new ArrayList<ThreatInput>(roster.size());
        for (Roster.Member member : roster.members()) {
            // Your own row always survives the filter: seeing where you sit is the point of it.
            if (enemiesOnly && !member.isSelf() && ownTeam != null && ownTeam != TeamColour.UNKNOWN
                    && member.getTeam() == ownTeam) {
                continue;
            }
            inputs.add(buildInput(member, teamStates, now));
        }

        entries = ThreatEngine.rank(inputs);
        cache.prune();
    }

    /** Turns this tick's view of the world into what the roster needs to fold in. */
    private static List<Roster.Sighting> sightings(List<LobbyReader.LobbyPlayer> players,
                                                   Map<String, TeamColour> teamOf, String selfName) {
        List<Roster.Sighting> sightings = new ArrayList<Roster.Sighting>(players.size());
        for (LobbyReader.LobbyPlayer player : players) {
            // Null gear means out of render distance, which the roster reads as "unchanged" rather
            // than "carrying nothing".
            EntityPlayer entity = LobbyReader.findEntity(player.name);
            sightings.add(new Roster.Sighting(player.uuid, player.name,
                    player.name.equals(selfName), teamOf.get(player.name),
                    entity == null ? null : GearReader.read(entity)));
        }
        return sightings;
    }

    private ThreatInput buildInput(Roster.Member member, Map<String, TeamState> teamStates, long now) {
        BedwarsStats stats = cache.get(member.getUuid());
        if (stats == null) {
            stats = BedwarsStats.UNKNOWN;
            requestStats(member.getUuid());
        }

        TeamColour team = member.getTeam();
        TeamState state = teamStates.get(team.getDisplayName().toLowerCase(Locale.ROOT));

        ThreatInput.Builder builder = ThreatInput.builder(member.getName())
                .team(team.getDisplayName(), team.getColourCode())
                .stats(stats)
                // Only call them nicked once a lookup actually came back empty; before that the
                // stats are merely not fetched yet.
                .nicked(stats.isUnknown() && !cache.needsFetch(member.getUuid())
                        && !cache.isFailed(member.getUuid()))
                .bedIntact(state == null || state.isBedIntact())
                .killsThisGame(count(killsThisGame, member.getName()))
                .deathsThisGame(count(deathsThisGame, member.getName()))
                .flaggedForCheating(Flagged.is(member.getName()))
                .self(member.isSelf());

        if (member.getGear() == null) {
            builder.gearUnknown();
        } else {
            // Held rather than dropped: their gear is unknown once they walk off, not absent, and
            // scoring it as absent is what made a rating fall a point for rounding a corner.
            builder.gear(member.getGear(), member.gearAgeMillis(now));
        }
        return builder.build();
    }

    private static int count(Map<String, Integer> counter, String name) {
        Integer value = counter.get(name);
        return value == null ? 0 : value;
    }

    private void requestStats(final UUID uuid) {
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
