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
| Threat List | Ranks the lobby 0–10, most dangerous at the top, from lifetime stats, current gear and how the game is going for them |
| Cheat Detector | Watches other players for automated clicking, locked-on aim and long reach, and names the team and player in chat |

**HUD** — Keystrokes, CPS, Info (fps / ping / coordinates / facing), Armour, Potions.
All draggable, with alignment snapping and scroll-to-resize.

**Utility and visual** — Zoom, Toggle Sprint, Fullbright.

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
   settings.
2. Enable **Threat List** under Analysis, open its settings and paste a Hypixel API key from
   [developer.hypixel.net](https://developer.hypixel.net) into the masked field.
3. Enable **HUD Editor** under Client to drag things where you want them.

### About the API key

The key is stored in `.minecraft/vantage/api.json`, restricted to your user account where the
filesystem supports it, and never written to a log. It is deliberately kept out of config profiles,
since profiles are the thing people copy between each other. The field renders as bullets so it does
not show on a stream or a screenshot.

## How the threat score works

Three factors, blended with weights you can change in the module's settings:

- **Stats (45%)** — final kill/death ratio and Bedwars level, log-scaled. The gap between 2 and 6
  FKDR matters far more than the gap between 20 and 40, and a linear scale would put almost every
  real player in the bottom fifth of the range. Thin records are pulled toward a neutral score in
  proportion to how thin they are: someone who is 5 and 0 has not proved anything yet.
- **Gear (35%)** — armour and weapon tier plus enchantments, read from their entity. Armour is taken
  from the best piece worn rather than the chestplate, because Bedwars upgrades only replace boots
  and leggings.
- **Momentum (20%)** — kills this game, and whether their bed is still standing. An intact bed means
  every kill has to be taken again.

A nicked player drops the stats factor and is scored on gear and momentum instead of being counted
as a zero, which would rank someone in full diamond below an empty-handed one.

## What the cheat detector can and cannot see

Worth being precise, because clients often claim more than they can deliver.

Another player's rotations reach the client through entity look packets, where yaw and pitch are
each **a single byte** — steps of about 1.4°. The fine-grained mouse analysis a server-side
anticheat performs, such as finding the common divisor of raw rotation deltas, needs the
unquantised floats that only the server receives. That signal is not available client-side at all,
and a check built on it would be measuring rounding noise.

What is implemented:

- **Autoclicker** — the tell is not a high click rate. People reach sixteen clicks a second by hand
  and that is legitimate. What a hand cannot do is be consistent, so this measures the spread of
  the gaps between clicks. Timing is read off the network pipeline, not entity state, because
  entity state only updates once a tick and 50 ms resolution would round every interval to a
  multiple of a tick.
- **Aim assist** — large single-tick turns that land on a target, and a view that stays locked on
  one through movement that should have disturbed it. Both survive the packet quantisation.
- **Reach** — measured only for hits landed **on you**. For an attack between two other players the
  client sees neither the attack nor the positions the server used, so any figure would be
  guesswork dressed up as a measurement. The median is used, so one lag spike does not convict.

**Backtrack is not detectable from a client** and is not implemented. It is a property of the
attacker's packet timing against the server, which a third-party client never sees.

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

141 tests cover the parts that do not need a running game: threat scoring, Hypixel response parsing,
rate limiting and caching, scoreboard and death-message parsing, HUD snapping, config round-trips,
and the detection heuristics — including sequences built to look human and to look automated, and a
check that packet quantisation alone never reads as cheating.

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
