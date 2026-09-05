# Vantage

A Minecraft 1.8.9 Forge client built around information rather than mechanical advantage: a threat
ranking overlay driven by the Hypixel API, a client-side cheat detector, a clean ClickGUI, and a set
of quality-of-life modules.

Everything here is the kind of thing Hypixel permits outright, so it is not limited to private
servers.

## What is in it

**Analysis**

| Module | What it does |
|---|---|
| Threat List | Ranks everyone in your game 0–10 on their stats and gear, most dangerous at the top, with their team colour, FKDR, win/loss, star and current gear. You are in the list too, highlighted, so you can see who you can take |
| Cheat Detector | Reach, backtrack, aim assist, autoclickers, anti-knockback and automated bridging. Names the team and player in chat, and flagged players go to the top of the threat list |

**HUD** — Keystrokes, CPS, Info (fps / ping / coordinates / facing), Armour, Potions.
All draggable, with alignment snapping and scroll-to-resize.

**Utility and visual** — Zoom, Toggle Sprint, Fullbright, Nick Hider.

**Client** — ClickGUI, HUD Editor, Mods Manager.

## What is deliberately not in it

No killaura, reach, backtrack, aim assist, autoclicker, fast place, or ESP. Those exist to beat
other players by mechanical advantage and are out of scope for this project. The module system is a
plain abstract class, so the codebase does not stop anyone adding their own.

## Building

Requires a JDK 17 or newer to build; the mod itself targets Java 8, which is what 1.8.9 runs on.

```
./gradlew build
```

The jar lands in `build/libs/`. Drop it into `.minecraft/mods` on a 1.8.9 Forge profile
(`11.15.1.2318` or compatible).

The build uses Essential's Architectury Loom fork rather than the classic ForgeGradle 2.x
toolchain, which needs Java 8 and Gradle 4.x. Three things are needed beyond a stock Loom setup and
are already configured:

- `loom.platform=forge`, or Loom assumes Fabric and never creates the `forge` configuration.
- A Pack200 implementation on the **settings-level** buildscript classpath. The JDK dropped Pack200
  in Java 14, but the FG2-era Forge artifacts are still pack200-compressed. It has to be at
  settings level so it lands on Loom's own classloader rather than a child one.
- `loom.forge.pack200Provider` wired to that shim explicitly — Loom does not discover it.

## Getting started in game

1. Press **Right Shift** to open the menu. Left click toggles a module, right click opens its
   settings. Hovering a module shows what it does.
2. Enable **Threat List** under Analysis, open its settings and paste a Hypixel API key from
   [developer.hypixel.net](https://developer.hypixel.net) into the masked field.
3. Press **Right Control** to open the HUD editor and drag things where you want them. Scroll over
   an element to resize it.

Every colour is customisable under **ClickGUI** in the Client category — accent, panel, rows, text
and both ends of the threat gradient. The theme reads them live, so the client restyles while you
are still dragging the picker.

### About the API key

The key is stored in `.minecraft/vantage/api.json`, restricted to your user account where the
filesystem supports it, and never written to a log. It is deliberately kept out of config profiles,
since profiles are the thing people copy between each other. The field renders as bullets so it does
not show on a stream or a screenshot.

## How the threat score works

The whole score is their public record plus what they are carrying, and nothing else. The scale is
anchored at both ends: **0** is essentially their first game and you win that fight almost every
time; **10** is a leaderboard name and you very likely lose.

```
skill = 0.55 × FKDR + 0.25 × win/loss + 0.20 × KDR      (each log-scaled to 0–10)
score = skill + star bonus (≤ +1) + gear (±1.2)
```

- **FKDR leads.** It is the closest thing Bedwars has to a direct measure of who wins a fight.
- **Win/loss counts next**, because it is hard to farm and says whether they close games out.
- **KDR counts least** — it mixes in void deaths and non-final kills, so it is the noisiest.
- Each is log-scaled. The gap between 2 and 6 FKDR matters far more than the gap between 20 and 40,
  and on a linear scale nearly every real player would be squashed into the bottom of the range.
- **Star is a bounded bonus, not a fourth ratio.** Level is mostly time played, so a grinder with a
  mediocre record cannot out-rank a good player however many stars they have.
- **Gear is a bounded adjustment, never a weight.** Weighting it heavily was what made everybody
  score alike — by mid-game the whole lobby owns iron or diamond, so gear converges and drowns the
  skill signal. The last reading is also held for a while after a player leaves render distance and
  then fades, so a rating settles instead of dropping a point when somebody rounds a corner.
- **Records too thin to judge** are pulled toward the middle and marked with a hollow dot: five
  final kills is not a 10 FKDR player, it is five fights.
- **A nicked player scores 7.** A nick hides a record, and the players who bother are far more often
  good ones avoiding attention than beginners.
- **A confirmed cheat flag floors the score at 9.5** and sorts that player above everyone.

Bed state and this game's kills are shown but not scored. They used to move every player's number
every few seconds off the kill feed without ever making it more accurate.

**You are in the list too**, in your real position with your row highlighted, so the players above
you are the ones you lose to and the players below are the ones you do not. Your row keeps its place
even when the list is capped shorter than your rank.

Six realistic player profiles are asserted as score bands in the test suite, along with the
properties that broke before — that gear cannot swing a score by more than about a point, that an
ageing gear reading fades rather than steps, and that bed state and current kills move it by exactly
zero — so the calibration is checked on every build rather than discovered in a game.

The list only appears when you are actually in a Bedwars game or its pre-game lobby, decided from
the scoreboard title, and it survives the scoreboard briefly changing rather than blanking. The
roster is **sticky**: a player has to be missing from several consecutive rebuilds before they are
dropped, so one bad read of the tab list cannot make rows appear and disappear. NPCs and shopkeepers
are filtered out by UUID version — Mojang issues version 4 for real accounts, while a server
inventing a profile derives it from a name and stamps version 3.

**Detail** controls how much of each player is shown — score and name, plus their stats, or the full
row with team and gear. Names carry their team's colour throughout.

## What the cheat detector can and cannot see

Worth being precise, because clients often claim more than they can deliver.

Another player's rotations reach the client through entity look packets, where yaw and pitch are
each **a single byte** — steps of about 1.4°. The fine-grained mouse analysis a server-side
anticheat performs, such as finding the common divisor of raw rotation deltas, needs the
unquantised floats that only the server receives. That signal is not available client-side at all,
and a check built on it would be measuring rounding noise.

Only the cheats people actually run are looked for, grouped into three toggles:

**Combat** — *Reach*: measured only for hits landed on **you**, and judged against **where you were**
rather than where you are. A server rewinds the world to compensate for latency before deciding
whether a hit lands, so asking the instantaneous distance asks a question the server never asked and
reads long for everybody. *Backtrack*: a hit that was legal a moment ago but is not now. That alone
is also what an honest bad connection looks like, so the tell is **consistency** — real latency
wanders, while a backtrack module holds packets for a set time and puts every hit at nearly the same
delay. *Aim assist*: every implementation has a cone it engages inside and a cap on how fast it may
turn, which leaves three marks — it does not overshoot, it turns at one speed, and it never loses the
target. Two of the three have to agree. *Autoclicker*: the tell is not a high rate, since people
reach sixteen clicks a second by hand. What a hand cannot do is be consistent, so this measures the
spread of the gaps across the middle of the sample — and counts the pauses at the edges, because a
person stops now and then and a timer never does. *Anti-knockback*: displacement after a hit, taken
as a median, since being hit into a wall legitimately moves you almost nowhere.

**Scaffold** — placing blocks under yourself while walking backwards is how everyone crosses a gap,
so rate alone would flag the whole lobby. The difference is where the player looks: bridging by hand
means aiming down at the block, while a scaffold keeps the view level and forward because the
placement is not coming from the view at all. A block is only attributed when it sits where a bridge
block would sit and exactly one person was close enough to have placed it; batched block changes of
more than two are ignored outright, since those are explosions and bed breaks rather than building.

**Backwards sprint** (off by default) — impossible in vanilla 1.8, and unlike the checks below it
does not depend on the positions being accurate, only on which way somebody is travelling relative
to their own facing.

### What was removed, and why

*Flight*, *speed* and *jump height* are gone. A client does not see another player's real position:
positions arrive quantised to 1/32 of a block at whatever rate the server sends them, and the game
then **interpolates** the entity between the last two. When packets come sparsely — normal for a
player standing still, far away, or whose updates got batched — the interpolated height simply holds,
and a check counting airborne ticks without descent counts up. The old flight check would report a
player standing on a block as flying. No threshold fixes that, because the input is not a measurement
of what it claims to measure. Flight has also not survived a server-side anticheat in years, so the
check had nothing to find.

### Three rules that keep it from accusing the innocent

1. **Nothing is collected outside a fight.** Mining wool produces a perfectly steady stream of swing
   packets; walking past somebody involves turning to look at them. Neither is evidence.
2. **Evidence is spent when it is judged.** The windows used to be re-read every second without ever
   being cleared, so one odd stretch of play was counted again and again until it crossed the
   threshold on its own. Nobody had to do anything twice to be accused of it.
3. **No single check convicts.** Two different checks have to agree, or one has to hold up across
   several separate windows of evidence.

Thresholds scale with the watched player's latency, which is the largest single source of wrong
answers. Every verdict carries a confidence and is a heuristic, not an accusation.

## Mods Manager

Lists every mod Forge has loaded, and lets you edit the settings of any that use Forge's standard
`.cfg` format — most 1.8.9 mods do. Configs it cannot attribute to a mod are still listed on their
own so nothing is hidden. Most mods read their config once at startup, so changes apply after a
restart.

Two things it cannot do, generically: edit mods that keep their settings in their own JSON or binary
format, and **reposition another mod's HUD**. That mod owns its own rendering and there is no hook
to move it. If you use another mod purely for a ping or FPS readout, the Info HUD here does the same
thing and can be dragged anywhere.

## Configuration files

```
.minecraft/vantage/
├── api.json              Hypixel API key, owner-only, never in a profile
├── state.json            which profile is active
└── profiles/
    └── default.json      module states, keybinds and settings
```

Profiles are safe to share. Loading is deliberately forgiving: unknown modules, settings removed
between versions, and hand-edited values of the wrong type each cost only the affected value and
never stop the game starting.

## Development

```
./gradlew test
```

256 tests cover the parts that do not need a running game: threat scoring, Hypixel response parsing,
rate limiting and caching, scoreboard and death-message parsing, HUD snapping, config round-trips,
and the detection heuristics.

The detection tests are built around the failure that matters. Every check has a fixture of
*legitimate* play that must not flag — a butterfly clicker at fourteen a second, somebody aiming by
hand across eight seeds, a player on a genuinely bad connection whose hits land looking long, an
ordinary Bedwars bridge — alongside the automated fixtures it has to catch. Every rotation fixture is
put through the same 1.4° packet quantisation the client really sees, because a check that only works
on clean numbers does not work at all.

Anything Minecraft-facing has to be checked in game. `Module`, `Setting`, `ConfigManager`, the
threat engine, the detection analyses and the Hypixel client deliberately import nothing from
Minecraft, which is what makes that split possible; `ModuleManager` owns the Forge subscriptions
and fans events out.

### Adding a module

```java
public class ExampleModule extends Module {
    private final NumberSetting amount = register(new NumberSetting(
            "Amount", "What it does", 5.0, 0.0, 10.0, 0.5));

    public ExampleModule() {
        super("Example", Category.UTILITY, "Shown under the name in the menu");
    }

    @Override
    public void onTick() {
        // called each client tick while enabled
    }
}
```

Register it in `Vantage.registerModules()`. Settings persist and appear in the menu automatically.
Extend `HudModule` instead for something drawn on screen, and it gets dragging, scaling and a
background panel for free.

## Checking the visual work

The interface cannot be verified without running the game. Worth an eye on first launch:

- [ ] Menu opens on Right Shift and the panel scales up smoothly rather than snapping
- [ ] Text is crisp, not blocky — that means the TrueType atlas built; check the log for
      "Font atlas could not be built" if it looks like the vanilla font
- [ ] Category rail highlight slides between categories
- [ ] Changing the accent colour restyles the menu immediately while dragging the picker
- [ ] Rainbow accent animates across the whole interface
- [ ] Sliders keep tracking when the cursor leaves the row mid-drag
- [ ] Settings panels scroll and clip cleanly at the panel edge
- [ ] Background set to Blur either works or falls back to a plain backdrop with a warning logged,
      rather than rendering black
- [ ] HUD editor snaps elements to edges and to each other, with guide lines
- [ ] Threat list columns stay aligned as names and numbers change
- [ ] Your own row is in the list, highlighted, at the rank the score puts you
- [ ] Ratings hold steady as players walk in and out of render distance
- [ ] No shopkeepers or NPCs in the list, and nobody vanishes from it mid-game
- [ ] A full game of ordinary play flags nobody
