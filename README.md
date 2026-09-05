# Vantage

A Minecraft 1.8.9 Forge client: a threat ranking overlay driven by the Hypixel API, a client-side
cheat detector, a clean ClickGUI, a set of quality-of-life modules, and one combat module.

**This build is for a private server.** Everything here except Backtrack is the kind of thing
Hypixel permits outright, and used to be the whole point of the project. Backtrack is not — it is a
combat advantage, public servers ban for it, and its presence in the jar is what makes the jar
unsafe to run on one. It is in here because the client is used on a whitelisted server where the
people being hit know it is there. That is the only setting it belongs in.

## What is in it

**Analysis**

| Module | What it does |
|---|---|
| Threat List | Ranks everyone in your game 0–10, most dangerous at the top, with their team colour, final kill ratio, star and current gear |
| Cheat Detector | Nine checks across combat, movement and building. Names the team and player in chat, and flagged players go to the top of the threat list |

**Combat**

| Module | What it does |
|---|---|
| Backtrack | Holds nearby players' movement packets back for a set time, so you hit them where they were rather than where they are |

**HUD** — Keystrokes, CPS, Info (fps / ping / coordinates / facing), Armour, Potions.
All draggable, with alignment snapping and scroll-to-resize.

**Utility and visual** — Zoom, Toggle Sprint, Fullbright, Nick Hider.

**Client** — ClickGUI, HUD Editor, Mods Manager.

## What is deliberately not in it

No killaura, reach, aim assist, autoclicker, fast place, or ESP. Backtrack changes where a player
is; none of those change where *you* aim or when you click, and that line is where this stops. The
module system is a plain abstract class, so the codebase does not stop anyone adding their own.

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

The scale is anchored at both ends. **0** is essentially their first game and you win that fight
almost every time; **10** is ranked tier, a very high final kill ratio, and you very likely lose.

- **Stats lead, at 55%.** Final kill/death ratio and Bedwars level, log-scaled, with FKDR
  outweighing star four to one — star is mostly time played, FKDR is skill. The gap between 2 and 6
  FKDR matters far more than the gap between 20 and 40, which is why it is not linear. Records too
  thin to judge are pulled toward the bottom of the scale rather than the middle.
- **Gear at 30%, but only when it can be seen.** Outside render distance gear is *unknown*, not
  absent, and the factor drops out so stats carry the score. Treating unknown as none is what made
  good players read as harmless.
- **Bed and current form are small adjustments**, not co-equal terms: an intact bed is worth +0.3,
  kills this game up to +1.2. As blended factors they dragged an excellent player with an untouched
  bed and no kills yet down to about 8.5, which is backwards.
- **A confirmed cheat flag floors the score at 9.5** and sorts that player above everyone.

Six realistic player profiles are asserted as score bands in the test suite, so the calibration is
checked on every build rather than discovered in a game.

The list only appears when you are actually in a Bedwars game or its pre-game lobby, decided from
the scoreboard title. A tab list on its own is not a lobby: in a hub it carries everyone standing
around, which is how unrelated names ended up in the list. Inside a game the tab list is exactly the
participants, so nothing further needs filtering.

**Detail** controls how much of each player is shown — score and name, plus their stats, or the full
row with team and gear. Names carry their team's colour throughout.

## How Backtrack works

It uses a gap vanilla leaves open. A server accepts a melee hit whenever the attacker is within
**six** blocks of the target — `NetHandlerPlayServer.processUseEntity` checks
`getDistanceSqToEntity < 36.0` — but your client only traces for a target out to **three**. The top
half of that window is unreachable in normal play.

Holding a player's position packets back closes it. Their entity keeps the coordinates it had a
moment ago, so the model and its bounding box sit where they were; the crosshair trace hits that
stale box; and the attack packet that follows carries nothing but an entity id. The server checks
its own current positions, finds you inside six blocks, and counts the hit.

So nothing in the module spoofs a rotation, redirects an attack, or invents a position. It holds
three packet types — relative movement, teleports and head look — and vanilla does the rest. That
is also why there is nothing extra drawn on screen: the player model *is* the indicator, because
the stale position is where the model genuinely is.

| Setting | Default | What it does |
|---|---|---|
| Delay | 200ms | How far behind their real position nearby players are held |
| Min Distance | 0m | Players closer than this are left alone; you can already hit them |
| Max Distance | 5m | Players further than this are left alone; past six the server refuses the hit anyway |
| Release On Hurt | on | Hands back everything held the moment you take a hit |

Distances are measured centre to centre, the same way the server measures the six-block limit, so
the sliders mean the same thing that check does.

A held player is not frozen. Once the queue fills, packets come out at the rate they go in, leaving
that player a constant distance behind rather than stopped — so someone running away still reads as
leaving the band and holding stops on its own.

**Nothing addressed to you is ever held.** That is checked against your own entity id in
`PacketDelayer`, not left to the module's target list, because delaying your own velocity packets
delays your own knockback — the most obvious tell there is, and the thing server-side anticheats
actually punish.

Packets are never dropped, only deferred. 1.8.9 movement is mostly *relative* — `S14PacketEntity`
says "move that player 0.3 east", not "that player is here" — so a lost or reordered packet leaves
that player permanently offset from where the server has them. `HeldPacketQueue` keeps release times
monotonic and releases early rather than discarding when it fills; those rules are what the unit
tests cover.

The Cheat Detector is told which players are being held, and marks their movement samples the same
way it marks a server teleport, so this client's own delaying does not read as the other player
flying.

## What the cheat detector can and cannot see

Worth being precise, because clients often claim more than they can deliver.

Another player's rotations reach the client through entity look packets, where yaw and pitch are
each **a single byte** — steps of about 1.4°. The fine-grained mouse analysis a server-side
anticheat performs, such as finding the common divisor of raw rotation deltas, needs the
unquantised floats that only the server receives. That signal is not available client-side at all,
and a check built on it would be measuring rounding noise.

Nine checks are implemented, grouped into three toggles:

**Combat** — *Autoclicker*: the tell is not a high rate, since people reach sixteen clicks a second
by hand and that is legitimate. What a hand cannot do is be consistent, so this measures the spread
of the gaps. Timing comes off the network pipeline, not entity state, which only updates once a tick.
*Aim assist*: large single-tick turns that land on a target, and a view that stays locked on one
through movement that should have disturbed it. *Reach*: measured only for hits landed on **you** —
for an attack between two other players the client sees neither the attack nor the positions the
server used. *Anti-knockback*: displacement after a hit, taken as a median, since being hit into a
wall legitimately moves you almost nowhere.

**Movement** — *Speed*, judged on the median tick rather than the fastest, because one long tick is
a rubber-band. *Flight*, from runs of airborne ticks with no descent. *Jump height*, deliberately
conservative since the client cannot see another player's potion effects. *Backwards sprinting*,
which is impossible in vanilla 1.8 and so has almost no false-positive surface.

**Scaffold** — placing blocks under yourself while walking backwards is how everyone crosses a gap,
so rate alone would flag the whole lobby. The difference is where the player looks: bridging by hand
means aiming down at the block, while a scaffold keeps the view level and forward because the
placement is not coming from the view at all.

Every movement check discards any pair of samples where either end was teleported. Positions arrive
quantised to 1/32 of a block and a server reposition resets them, so differencing across one reads
as impossible speed — the largest source of false positives in movement detection, ahead of latency.

**Backtrack is not detectable from a client**, so there is no check for it. It is a property of the
attacker's packet timing against the server, which a third-party client never sees. This client now
ships a Backtrack module itself, which changes nothing here — using one and spotting someone else
using one are unrelated problems, and only the second is impossible.

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

207 tests cover the parts that do not need a running game: threat scoring, Hypixel response parsing,
rate limiting and caching, scoreboard and death-message parsing, HUD snapping, config round-trips,
Backtrack's packet ordering rules, and the detection heuristics — including sequences built to look
human and to look automated, a check that packet quantisation alone never reads as cheating, and six
player profiles asserted as threat score bands.

Anything Minecraft-facing has to be checked in game. `Module`, `Setting`, `ConfigManager`, the
threat engine, the detection analyses, the Hypixel client and `HeldPacketQueue` deliberately import
nothing from Minecraft, which is what makes that split possible; `ModuleManager` owns the Forge
subscriptions and fans events out.

Backtrack is split along that same line on purpose. The ordering rules that matter — never reorder,
never drop, forget the old schedule after a flush — live in `HeldPacketQueue` and are tested against
a fake clock, because they are the part where a mistake shows up as a player stuck in the wrong
place rather than as anything obvious. `PacketDelayer` holds only the Netty plumbing.

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
