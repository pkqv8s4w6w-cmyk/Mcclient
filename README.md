# Vantage

A Minecraft 1.8.9 Forge client for Bedwars: combat, movement and render modules on the level of the
big cheat clients, six Bedwars modules that no other client has, a threat ranking and cheat detector,
and a menu in the style of Prestige.

**This build is for a private, whitelisted server** where everyone has agreed that any modification
is allowed. Most of it is bannable anywhere else. There are no anticheat bypasses or disablers in it:
the modes are tuned against a vanilla or Spigot server's own movement checks and nothing more.

## Getting started

1. Build with `./gradlew build` (JDK 17 or newer) and drop `build/libs/vantage-1.0.0.jar` into
   `.minecraft/mods` on a 1.8.9 Forge profile (`11.15.1.2318` or compatible).
2. Press **Right Shift** for the menu. Start typing anywhere in it to search.
3. Click a module's switch to turn it on; click the card itself to open its settings. Every card ends
   with a **Keybind** row for binding that module to a key.
4. Press **Right Control** for the HUD editor. Drag elements into place; scroll over one to resize it.

## The menu

A sidebar of categories and pages on the left, each with an icon and a line saying what it holds,
and the page on the right. Module pages have an **All / Enabled / Disabled** filter. The window is
laid out on a fixed canvas and scaled to fit, so it looks the same at every GUI scale.

| Page | What it is for |
|---|---|
| Configs | Apply, create and delete profiles. **Share** copies a profile to the clipboard; a friend presses **Import** to load it |
| Friends | Players every combat module leaves alone. Add by name, or switch on middle-click to add whoever you are looking at |
| Themes | Six presets (Prestige, Midnight, Rose, Mint, Sunset, Mono), each drawn as a small preview of the menu, and every colour on its own below |
| Settings | Menu key, backdrop (dim, gradient, blur or none), UI scale, the HUD editor, the Mods Manager, and **Panic**, which switches everything off |

## Modules

Tagged **Blatant** in the menu where the advantage is obvious to anyone watching.

**Combat**

| Module | What it does |
|---|---|
| KillAura | Attacks targets around you. Single, Switch or Multi; silent or camera rotations with a turn-speed cap; fake or real autoblock; optional raytrace gate so it only swings once the rotation is on target |
| AimAssist | Pulls your aim toward the nearest target while you click, per frame and scaled to frame time |
| AutoClicker | Left and right clicking with a hand-shaped rate (drift, hiccups, no fixed interval), block hitting, and leaves block breaking alone |
| TriggerBot | Attacks whatever valid target crosses the crosshair |
| Reach | Hit from up to 6 blocks and build from up to 7. Past about 5.6 some hits get dropped, because the server measures from your feet to theirs |
| Velocity | Scale knockback, cancel it, or jump-reset it; explosions included |
| Hitboxes | Makes other players' hitboxes bigger |
| Criticals | Every grounded hit is a critical, by packet or by a small hop |
| WTap | Restarts your sprint after each hit so every hit gets sprint knockback |
| KeepSprint | Keeps your speed and sprint when you land a hit |
| BowAimbot | Aims a drawn bow using the arrow's real drag and gravity, leading moving targets |
| Backtrack | Pins nearby enemies where they were so you can still hit them after they move. More below |
| No Click Delay | Removes the half-second lockout after a missed click |
| AntiBot | Keeps combat modules off NPCs and fake players |

Every combat module shares the same target settings: players, mobs, animals, invisibles, ignore
team, through walls. Friends are always skipped.

**Movement:** Speed (strafe, ground, vanilla), Fly (motion, vanilla, glide, with an anti-kick that
follows the server's own rule), LongJump, HighJump, Step, NoFall, NoSlow, SafeWalk, InvMove,
AntiVoid, Timer, Blink, Spider, TargetStrafe, and Toggle Sprint with an omni option.

**Player:** Scaffold (silent block swaps, tower, keep-Y), FastPlace, FastBreak, AutoTool,
ChestStealer, InvManager, AutoArmor, FastEat, NoRotate.

**Visuals:** ESP (world box or on-screen frame with health), Nametags with gear, Tracers, Chams,
StorageESP, Trajectories, Freecam, NoHurtCam, TimeChanger, Fullbright.

**Analysis:** Threat List (below) and Cheat Detector (further below).

**HUD:** ArrayList, Watermark, TargetHUD, Notifications, Keystrokes, CPS, Info, Armour, Potions.

**Misc:** Zoom, Nick Hider, Middle Click Friend, Mods Manager.

## The Bedwars modules

Everything here works without knowing which Bedwars plugin the server runs. Teams are read from the
scoreboard team colour, then leather armour dye, then name colour. Beds are found in the raw chunk
data and each is given to the team whose dyed wool, clay and glass surround it, since players defend with
their own colour. If that is unclear, it goes by who stood beside the bed at the start of the game.

| Module | What it does |
|---|---|
| **Breach Planner** | Costs every block in an enemy bed's defence at the ticks your best hotbar tool takes to break it, then finds the cheapest route from open air to the bed. The route is drawn block by block, each block labelled with its order and time. **Auto** digs it for you, holding the right tool only for the packet that finishes each block, which is the only moment the server checks what you are holding |
| **Rush Radar** | Credits each new block to the enemy standing beside it, and flags runs that are long, straight, still growing and aimed at your island. Tells you who, how far, and when they arrive |
| **Economy Tracker** | Counts every iron, gold, diamond and emerald each enemy picks up (the pickup packet names both the player and the item) and subtracts purchases it can see on them. Shows each team's bank and the dangerous things it could afford right now, such as a pearl or diamond armour |
| **Bed Guard** | Remembers your bed defence. Alerts on every block an enemy breaks, with how many layers from the bed it was, and when an enemy comes near. The panel is a top-down map of the layers left |
| **Projectile Forecast** | Simulates enemy pearls to their landing spot, fireballs to their impact point and blast radius, and lit TNT with its fuse. Warns over the crosshair when one is landing on you. **Auto Deflect** hits incoming fireballs back |
| **Void Clutch** | Simulates your own fall every tick. If it ends in the void, it places a block on the tick one can reach, or pearls you back to the last ground you stood on, aimed with the pearl's real ballistics. By default it only steps in after you were hit, not when you jump off on purpose |

Also **BedNuker** (breaks enemy beds in range through walls, since the server checks distance and not sight),
**BedESP** and **ItemESP** (dropped resources through walls, with counts).

Two limits worth knowing. The Economy Tracker sees spending only when it shows up on a player, so its
balance is an upper bound: if it says a team cannot afford a pearl, they cannot. And a client is
never told a lit TNT's real fuse, so Projectile Forecast counts down from a **TNT Fuse** setting
(vanilla's four seconds by default) that you can match to your server.

## How it hooks the game

Mixin 0.7.11 is packed into the jar and started through the `TweakClass` manifest attribute. The
mixins do nothing but post to a small typed event bus: packets in and out, the movement packet before
and after it is sent, movement, strafing, jumping, attacks, reach, hitboxes, item slowdown, camera
effects, clicks and options saves. Modules declare handlers with `on(...)`, and those handlers are
live exactly while the module is on.

Rotations the server sees go through one place, `RotationManager`. Modules ask each tick with a
priority; turns are rate-limited, snapped to whole mouse steps, and ease back to the camera when
nobody is asking. With silent rotations your camera never moves.

What the server accepts was checked against the decompiled 1.8.9 server, not assumed:

- **Melee** reach is six blocks feet to feet, but only three through a wall; KillAura holds its
  swing for anything the server would throw away. Block placement reaches eight and digging six,
  and neither checks sight.
- **Digging** is accepted at 70% of the break time, judged with the item held at the finishing
  packet. FastBreak finishes there; Breach Planner and BedNuker swap to the right tool only for it.
- **Every movement packet runs a full player update**, item use included, which is what FastEat
  uses.
- **Fall damage** is worked out from the ground flag in each movement packet, which is what NoFall
  uses.
- **The flight kick** counts packets that do not fall at least 1/32 of a block while nowhere near the
  ground, and only resets on landing. Periodic dips do not help, so Fly's anti-kick sinks steadily
  instead. Servers with allow-flight on never kick.

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
- **KDR counts least.** It mixes in void deaths and non-final kills, so it is the noisiest.
- Each is log-scaled. The gap between 2 and 6 FKDR matters far more than the gap between 20 and 40,
  and on a linear scale nearly every real player would be squashed into the bottom of the range.
- **Star is a bounded bonus, not a fourth ratio.** Level is mostly time played, so a grinder with a
  mediocre record cannot out-rank a good player however many stars they have.
- **Gear is a bounded adjustment, never a weight.** Weighting it heavily was what made everybody
  score alike. By mid-game the whole lobby owns iron or diamond, so gear converges and drowns the
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
properties that broke before: gear cannot swing a score by more than about a point, an ageing gear
reading fades rather than steps, and bed state and current kills move it by exactly zero. The
calibration is checked on every build rather than discovered in a game.

The list only appears when you are in a Bedwars game or its pre-game lobby: the scoreboard
title says Bed Wars, as on Hypixel, or the world has several beds in it, as on any other Bedwars
server. It survives the scoreboard briefly changing rather than blanking. The
roster is **sticky**: a player has to be missing from several consecutive rebuilds before they are
dropped, so one bad read of the tab list cannot make rows appear and disappear. NPCs and shopkeepers
are filtered out by UUID version. Mojang issues version 4 for real accounts, while a server
inventing a profile derives it from a name and stamps version 3. On an offline-mode server every real
player has a version 3 UUID too, so there the rule is skipped and only the name check applies; your
own UUID says which kind of server you are on.

**Detail** controls how much of each player is shown: score and name, plus their stats, or the full
row with team and gear. Names carry their team's colour throughout.

## How Backtrack works

It uses a gap vanilla leaves open. A server accepts a melee hit whenever the attacker is within
**six** blocks of a target they can see (`NetHandlerPlayServer.processUseEntity` checks
`getDistanceSqToEntity < 36.0`), but your client only traces for a target out to **three**. The top
half of that window is unreachable in normal play.

Blocking a player's position packets closes it. Their entity keeps the coordinates it had when the
block started, so the model and its bounding box sit where they were; the crosshair trace hits that
stale box; and the attack packet that follows carries nothing but an entity id. The server checks
its own current positions, finds you inside six blocks, and counts the hit.

So nothing in the module spoofs a rotation, redirects an attack, or invents a position. It withholds
three packet types (relative movement, teleports and head look) and vanilla does the rest. That
is also why there is nothing extra drawn on screen: the player model *is* the indicator, because
the pinned position is where the model really is.

**Held, not slowed.** Packets are blocked outright for the length of a window, not delayed by a
fixed amount each. A running delay only ever leaves a target trailing by a constant time, about a
block at sprint speed, which is marginal. Pinning them in place keeps them inside reach for as long
as the window lasts, and that is the difference between an occasional extra hit and a useful one.

It also means **distance can never be what ends a hold**: a pinned player's position stops changing,
so their distance stops changing too. The window ends on a clock. That is what Maximum Delay is for,
and why it is the setting that matters most.

| Setting | Default | What it does |
|---|---|---|
| Maximum Delay | 250ms | Longest a player may be pinned before they are let go |
| Min Distance | 1m | Players closer than this are left alone; you can already hit them |
| Max Distance | 5m | Players further than this are left alone; past six the server refuses the hit anyway |
| Maximum Hurt Time | 500ms (off) | Only hold a player once they are this close to being damageable again |
| Cooldown | 0ms (off) | How long after letting a player go before they may be held again |
| Release On Hurt | on | Let everyone go the moment you take a hit |

Teammates, friends and anything AntiBot calls a bot are never held; pinning them only makes them
stutter on your screen.

Distances are measured centre to centre, the same way the server measures the six-block limit, so
the sliders mean the same thing that check does.

**Maximum Hurt Time** and **Cooldown** are what stop the effect being one permanent stall on
whoever is nearest. At its maximum, Hurt Time is off and any eligible player is held. Lowered, a
player is only held once their damage immunity is nearly up, so the effect fires in spikes around
the moments a hit can actually land, rather than running constantly. Cooldown enforces a gap after
each release, including when a player was let go for leaving range.

## This is not fakelag

Worth stating plainly, because the two get confused and the difference is the whole point.

Fakelag (or blink) delays your **outgoing** packets. The server then does not know where you are,
so everyone sees a stale you and can hit you there, and your knockback lands late in a lump.

This module delays **incoming** packets, for other players only. `PacketDelayer extends
ChannelInboundHandlerAdapter` and has no outbound side: Backtrack never touches what you send. Your
position packets go out every tick untouched, so the server always knows exactly where you are and
other players always see you live. (Blink, in the Movement category, is the fakelag module, and it is
a separate thing you switch on deliberately.)

| | You see them | They see you |
|---|---|---|
| Backtrack | pinned, up to Maximum Delay behind | live |
| Fakelag / blink | live | behind |

**Nothing addressed to you is ever held**, either. That is checked against your own entity id in
`PacketDelayer` rather than left to the module's target list, because it is the one property that
must not depend on a caller getting something right. Other clients block all inbound traffic and so
delay their own knockback, which their "disable on hit" setting exists to paper over and which
gets their users banned. Withholding three packet types for named entities cannot do that.
`Release On Hurt` here is for how a fight reads, not for safety.

The one real side effect: you collide with a pinned player's stale hitbox, since collision uses the
same entity position your screen does. It does not affect hit registration in either direction.

Packets are never dropped, only withheld. 1.8.9 movement is mostly *relative*: `S14PacketEntity`
says "move that player 0.3 east", not "that player is here", so a lost or reordered packet leaves
that player permanently offset from where the server has them. When a window closes, everything it
held is delivered in order, and the player catches up rather than teleporting. `HeldPacketQueue`
keeps release times monotonic and releases early rather than discarding when it fills; each target
gets its own queue, since order matters within one player's stream and not at all between two.

The Cheat Detector is told which players are being held, and marks their movement samples the same
way it marks a server teleport, so this client's own delaying does not read as the other player
flying.

## What the cheat detector can and cannot see

Worth being precise, because clients often claim more than they can deliver.

Another player's rotations reach the client through entity look packets, where yaw and pitch are
each **a single byte**, in steps of about 1.4°. The fine-grained mouse analysis a server-side
anticheat performs, such as finding the common divisor of raw rotation deltas, needs the
unquantised floats that only the server receives. That signal is not available client-side at all,
and a check built on it would be measuring rounding noise.

Only the cheats people actually run are looked for, grouped into three toggles:

**Combat.** *Reach*: measured only for hits landed on **you**, and judged against **where you were**
rather than where you are. A server rewinds the world to compensate for latency before deciding
whether a hit lands, so asking the instantaneous distance asks a question the server never asked and
reads long for everybody. *Backtrack*: a hit that was legal a moment ago but is not now. That alone
is also what an honest bad connection looks like, so the tell is **consistency**. Real latency
wanders, while a backtrack module holds packets for a set time and puts every hit at nearly the same
delay. *Aim assist*: every implementation has a cone it engages inside and a cap on how fast it may
turn, which leaves three marks: it does not overshoot, it turns at one speed, and it never loses the
target. Two of the three have to agree. *Autoclicker*: the tell is not a high rate, since people
reach sixteen clicks a second by hand. What a hand cannot do is be consistent, so this measures the
spread of the gaps across the middle of the sample, and counts the pauses at the edges, because a
person stops now and then and a timer never does. *Anti-knockback*: displacement after a hit, taken
as a median, since being hit into a wall legitimately moves you almost nowhere.

**Scaffold.** Placing blocks under yourself while walking backwards is how everyone crosses a gap,
so rate alone would flag the whole lobby. The difference is where the player looks: bridging by hand
means aiming down at the block, while a scaffold keeps the view level and forward because the
placement is not coming from the view at all. A block is only attributed when it sits where a bridge
block would sit and exactly one person was close enough to have placed it; batched block changes of
more than two are ignored outright, since those are explosions and bed breaks rather than building.

**Backwards sprint** (off by default). This is impossible in vanilla 1.8, and unlike the checks below it
does not depend on the positions being accurate, only on which way somebody is travelling relative
to their own facing.

### What was removed, and why

*Flight*, *speed* and *jump height* are gone. A client does not see another player's real position:
positions arrive quantised to 1/32 of a block at whatever rate the server sends them, and the game
then **interpolates** the entity between the last two. When packets come sparsely, which is normal for a
player standing still, far away, or whose updates got batched, the interpolated height simply holds,
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
`.cfg` format, which most 1.8.9 mods do. Configs it cannot attribute to a mod are still listed on their
own so nothing is hidden. Only the values you change are written back; numbers are clamped into a
range a slider can use, so writing an untouched one would quietly overwrite it. Most mods read their
config once at startup, so changes apply after a restart.

Two things it cannot do, generically: edit mods that keep their settings in their own JSON or binary
format, and **reposition another mod's HUD**. That mod owns its own rendering and there is no hook
to move it. If you use another mod purely for a ping or FPS readout, the Info HUD here does the same
thing and can be dragged anywhere.

## Configuration files

```
.minecraft/vantage/
├── api.json              Hypixel API key, owner-only, never in a profile
├── friends.json          your friends list, shared by every profile
├── state.json            which profile is active
└── profiles/
    └── default.json      module states, keybinds and settings
```

Profiles are safe to share, since the API key is never in one, and **Share** on the Configs page copies
one straight to the clipboard. Loading is deliberately forgiving: unknown modules, settings removed
between versions, and hand-edited values of the wrong type each cost only the affected value and
never stop the game starting. Freecam and Blink are always saved as off, since both need setup when
they are switched on that a profile load cannot do.

## Building

Requires a JDK 17 or newer to build; the mod itself targets Java 8, which is what 1.8.9 runs on.

```
./gradlew build        # jar in build/libs/
./gradlew runClient    # dev client; needs a Java 8 JDK installed for the game itself
```

The build uses Essential's Architectury Loom fork rather than the classic ForgeGradle 2.x
toolchain. Beyond a stock Loom setup it configures:

- `loom.platform=forge`, or Loom assumes Fabric and never creates the `forge` configuration.
- A Pack200 implementation on the **settings-level** buildscript classpath, wired to
  `loom.forge.pack200Provider`. The FG2-era Forge artifacts are still pack200-compressed and the
  JDK dropped Pack200 in Java 14.
- Mixin, packed into the jar, with the **legacy Mixin annotation processor switched on**. Without it
  Loom writes no refmap, and every injection would miss in game because production runs on SRG
  names while the code is written against MCP ones.
- A Java 8 toolchain for `runClient`, since LaunchWrapper casts the system class loader to
  `URLClassLoader`, which stopped being true after Java 8.

## Development

```
./gradlew test
```

The tests cover everything that does not need a running game: threat scoring, Hypixel response
parsing, rate limiting and caching, scoreboard and death-message parsing, HUD snapping, config and
profile sharing round-trips, the event bus, rotation maths, click timing, team colours, projectile
and fall simulation against vanilla's own order of drag and gravity, the bow and pearl pitch
solvers, the breach route search, rush detection, the resource ledger, Backtrack's packet ordering
rules and the detection heuristics.

Anything Minecraft-facing has to be checked in game. `Module`, `Setting`, `EventBus`,
`ConfigManager`, the threat engine, the detection analyses, the Bedwars cores
(`dev.vantage.bedwars`) and the Hypixel client import nothing from Minecraft, which is what makes
that split possible.

### Adding a module

```java
public class ExampleModule extends Module {
    private final NumberSetting amount = register(new NumberSetting(
            "Amount", "What it does", 5.0, 0.0, 10.0, 0.5));

    public ExampleModule() {
        super("Example", Category.PLAYER, "Shown under the name in the menu");
        on(MotionEvent.class, event -> {
            if (event.isPre()) {
                // runs just before the movement packet goes, while the module is on
            }
        });
    }

    @Override
    public void onTick() {
        // called each client tick while enabled
    }
}
```

Register it in `Vantage.registerModules()`. Settings persist and appear in the menu automatically,
and the card gets a keybind row for free. Call `markBlatant()` in the constructor to tag it. Extend
`HudModule` instead for something drawn on screen, and it gets dragging, scaling and a background
panel.

## Checked in game

Against a local 1.8.9 server, headless, including a second client as an opponent:

- [x] Every mixin applies, and the client joins a server
- [x] KillAura kills a summoned zombie; Velocity holds position through a TNT blast; NoFall survives
      a 30 block drop
- [x] Scaffold bridges across a gap to the void; Void Clutch catches a walk into it
- [x] BedESP labels each bed with its team; Breach Planner finds the soft side of a defence and Auto
      digs through it with shears
- [x] Rush Radar calls out a player bridging toward your bed with an arrival time; Economy Tracker
      credits their pickups to their team; Bed Guard alerts when they break your defence
- [x] Projectile Forecast warns of lit TNT landing on you
- [x] Every page of the menu, search, and the HUD
