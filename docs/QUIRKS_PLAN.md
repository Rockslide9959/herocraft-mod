# Quirks (v0.16.7) -- build plan

Seven My Hero Academia Quirks, each a **Primary power** (one Primary at a time; `HeroTiers.claimPrimary`), sharing one
core (`com.projecthero.mod.quirk`). User decisions (2026-10-18):

- shared Quirk framework; each Quirk is a Primary power
- acquired by a **5% first-spawn calling** (after the Green Lantern ring and the Miraculous rolls miss) and by **Quirk Serums**
- **12 moves each**: R / G / X / Z / V / C and their Shift versions (like the Miraculous)
- **Erasure disables ALL powers, players included** (every power system, not just Quirks)
- detailed costume skins made by us (procedural, well-shaded 64x64, outer layer for depth) + a Dark Shadow model
- all of it ships in **0.16.7** (do not bump past 0.16.7)
- H stays the power selector; no invented H / N moves

## Slot mapping (do not get this wrong)

`SLOT_1 = R, SLOT_2 = G, SLOT_3 = X, SLOT_4 = Z, SLOT_5 = V, SLOT_6 = C`. Shift = `player.isShiftKeyDown()` (the core
passes it in). Held moves get `pressed=false` on release. Tap-vs-hold on the same key: see `hero/HoldTap`.

## Core API (already on master)

- `Quirk` enum (id, heroKey `quirk_<id>`, meterId, meterColour, animBase = 20/40/60/80/100/120/140)
- `QuirkKit` (handle / tick / stopAll / modifyIncomingDamage / active / usableWhileOverloaded / idsOf / maxCooldown) registered
  in `QuirkKits` from `quirk/<kit>/<Kit>Mod.initialize()`
- `Quirks`: state/peek/edit, hasPower/quirk/holds/activeAs, cooldownRemaining/ready/setCooldown, setAnim, setVal/setVec,
  **meter** (`meter`/`setMeter`/`addMeter` server, cheap every tick; `clientMeter` client), `overload(ticks)`/`overloaded`,
  `say`, grant/revoke, toggleCostume, tick, lifecycle
- `QuirkState` (synced to all): hasPower, quirk, costume, meter, overloadUntil, animId/animStart, abilityReadyAt, vals, vecs
- `hero/PowerSuppression`: `suppress(entity, ticks)` / `isSuppressed(entity)` / `release` -- Erasure's effect
- client: `QuirkClientKit` (applyPose, hidesSkinOverlay) registered via `QuirkClient.register` from `<Kit>Client.initialize()`

Costume textures: `assets/projecthero/textures/entity/quirk/<id>_wide.png` and `<id>_slim.png` (64x64 player-skin layout,
drawn OVER the player's own skin by the core's costume layer while `Quirks.costumeOn`). Ability ids: `quirk_<id>_<move>`.
Lang: `projecthero.quirk.ability.<abilityId>` (name) and `.desc` (one or two short sentences, numbers included; keep it
short -- tooltips must wrap).

## The seven kits

Numbers are starting points; tune for feel. Bosses (`TitanCombat.isBoss`) are never rooted / floated / thrown / one-shot:
cap at 10% max HP per hit like All Might, no knockback. Squadmates are never hurt (`Squad` checks, see existing kits).
Temporary blocks always restore (reuse the existing temporary-block helpers; never leave grief).

### Hellflame (Endeavor) -- meter: Heat (rises with use, vents ~4/s when not using fire, faster in water/rain)
At 100 Heat: **Overheat** 8 s -- Slowness II, a little self-burn, no fire moves (Shift+C Vent still works).
| Key | Plain | Shift |
|---|---|---|
| R | **Hell Spider**: thin flame threads fan out and slice everything in a line (~16 blocks) | **Hell Spider Net**: threads in a web around a target point; everything inside is rooted 3 s and burns |
| G | **Jet Burn**: short-range flame blast in a cone | **Hellflame Beam** (hold): a concentrated long-range flame beam, Heat climbs fast |
| X | **Flame Boost** (hold): jets from the feet, flight | **Burst Dodge**: a flame blast that throws you ~8 blocks the way you move (or back) |
| Z | **Flashfire Fist**: rocket forward on flame into a burning punch | **Flashfire Uppercut**: the dash ends in an uppercut that launches the target |
| V | **Prominence Burn** (hold to charge): a flame column from the whole body, bigger with charge, big Heat | **Hellflame Sun**: a small sun overhead rains fireballs on nearby enemies for 6 s (long cooldown) |
| C | **Flame Cloak** (toggle): you burn -- melee ignites, fire immune, attackers burn; Heat rises slowly | **Vent**: dump all Heat in a radial flame burst (damage scales with Heat); usable while overheated |

### Overhaul (Chisaki) -- meter: Focus (spent by moves, regenerates); touch moves need an EMPTY main hand
| Key | Plain | Shift |
|---|---|---|
| R | **Disassemble**: touch-punch, heavy damage, the target bursts apart (particles) | **Shrapnel Burst**: the hit target bursts and the shards hit everything near it |
| G | **Spike Wave**: touch the ground, a line of stone spikes erupts toward the target (temporary) | **Spike Field**: a ring of spikes erupts all around you |
| X | **Reshape Wall**: rip up the ground into a wall in front (temporary) | **Launch Ramp**: the ground ahead rises as a ramp that flings you forward |
| Z | **Restore**: touch yourself or the ally you look at -- heal to full, clear debuffs (long cooldown) | **Reassemble Armour**: rebuild your worn armour's durability and gain absorption |
| V | **Overhaul Field**: everything around you shatters and reforms as a forest of spikes (big, temporary) | **Collapse Pit**: the ground under the target drops into a pit and closes over it (trapped 4 s) |
| C | **Fusion**: absorb nearby blocks into a bigger stone-armoured form for 30 s (reach, damage, armour) | **Shard Shot**: disassemble the block you look at into a cone of shards |

### Engine (Iida / Ingenium) -- meter: Engine heat (rises with Recipro moves and top speed, cools at walk)
Passive: Speed I and 1-block step assist. At 100: **Stall** 6 s -- Slowness III, no moves.
| Key | Plain | Shift |
|---|---|---|
| R | **Recipro Kick**: a fast kick combo | **Spin Kick**: a 360 kick around you, knockback |
| G | **Recipro Burst**: 10 s of huge speed, then a stall | **Gear Up**: 6 s of even more speed, double heat |
| X | **Engine Leap**: a big jump from the exhausts | **Air Dash**: a mid-air boost the way you look |
| Z | **Recipro Extend**: a ~15 block dash-kick hitting everything in the path | **Afterburner Kick**: one kick that launches the target far |
| V | **Rescue Carry**: grab an ally / mob and carry them while you run (again: set down) | **Rescue Throw**: throw whatever you carry the way you look |
| C | **Recipro Turbo**: 3 s of extreme speed, everything you pass is hit | **Cool Down**: stop dead and vent 50 heat (steam) |

### Dark Shadow (Tokoyami / Tsukuyomi) -- meter: Shadow (rises in darkness, falls in light)
A visible shadow companion (custom model, the Dark Shadow agent builds it) floats at your shoulder. Damage scales with
Shadow (weak in daylight, strong in the dark). At 100 Shadow in darkness: **Berserk** 8 s -- Dark Shadow rampages at
the nearest creatures (not you), you can't use moves except Shift+C.
| Key | Plain | Shift |
|---|---|---|
| R | **Shadow Strike**: Dark Shadow lunges from your body and hits up to ~8 blocks | **Shadow Flurry**: a rapid string of strikes on the target |
| G | **Shadow Guard** (hold): it wraps you and blocks damage from the front | **Shadow Counter**: a short parry window; a blocked hit is answered with a strike |
| X | **Black Fallen Angel**: shadow wings, glide and fly (toggle) | **Shadow Dive**: a fast dash the way you look |
| Z | **Shadow Grab**: snatch a mob and pull it to you | **Grab and Throw**: snatch it and hurl it the way you look |
| V | **Abyssal Rampage** (darkness only): a huge uncontrolled frenzy around you for 6 s | **Abyssal Swallow**: Dark Shadow swallows a mob and holds it 5 s, damage over time |
| C | **Black Ankh** (toggle): shadow armour with claws, damage + toughness | **Calm**: a flash of light settles Dark Shadow -- drops Shadow, ends Berserk; usable while berserk |

### Erasure (Aizawa / Eraser Head) -- meter: Dry Eyes (100 = fresh; drains while erasing, refills while not)
At 0: **forced blink** -- the erase ends, eyes shut 2 s (overload), the meter refills. Hair stands up while erasing.
**Erase cancels ANY power** through `PowerSuppression` (the core makes every power system respect it; the Erasure kit
owns what it does to MOBS: creepers can't swell, skeletons / pillagers can't shoot, endermen can't teleport, blazes /
ghasts can't fire, bosses: their special moves are held, never their basic attacks).
| Key | Plain | Shift |
|---|---|---|
| R | **Capture Lash**: the scarf whips out and pulls the target in | **Capture Disarm**: yanks the held item out of a mob's hand |
| G | **Erase** (hold, look at the target): cancels its power while you stare, drains Dry Eyes | **Erase Glare** (hold): a wide cone -- everyone in front, drains 3x faster |
| X | **Capture Swing**: the scarf as a grapple / zipline to a block | **Capture Zip**: pull yourself to the creature you look at |
| Z | **Capture Bind**: wrap the target, rooted 4 s | **Capture Hoist**: bind it and hang it up off the ground 4 s |
| V | **Bind Slam**: whip a bound (or the looked-at) target up and slam it down | **Double Bind**: bind two targets and slam them together |
| C | **Goggles** (toggle): night vision, powered players / special mobs highlighted | **Eye Drops**: refill Dry Eyes at once (long cooldown); usable while blinking |

### Explosion (Bakugo / Ground Zero) -- meter: Sweat (builds while fighting / moving, faster in heat)
Moves spend Sweat; more Sweat = bigger blasts. A hidden Arm Strain (kit val) builds with big blasts; full strain =
**Arm Strain** 5 s (Weakness, no moves).
| Key | Plain | Shift |
|---|---|---|
| R | **AP Shot**: a focused explosion fired from the palm (projectile / ray) | **AP Shot Auto-Cannon**: a rapid volley of small AP shots |
| G | **Blast Punch**: a point-blank explosion | **Explosion Uppercut**: a blast that launches the target |
| X | **Stun Grenade**: a flash -- blinds everything around you | **Smoke Blast**: a thick smoke screen around you |
| Z | **Blast Rush** (hold): explosions propel you through the air | **Blast Dash**: one burst the way you look |
| V | **Howitzer Impact**: spin into a huge tornado explosion (most of your Sweat) | **Max Shot**: a giant cannon blast along your look, all your Sweat |
| C | **Cluster**: a ring of small explosions around you | **Explosive Counter**: 2 s window -- the next melee hit on you triggers an explosion on the attacker |
Explosions never break blocks unless mobGriefing is on (and then only small); never hurt the user.

### Zero Gravity (Uraraka / Uravity) -- meter: Nausea (rises with total floated weight / time)
At 100: **Nausea** 5 s -- everything floated drops, Nausea effect, no moves. The user's own float counts.
| Key | Plain | Shift |
|---|---|---|
| R | **Float Touch**: a hit makes the mob float helplessly (up to ~5 at once) | **Float Fling**: touch, float and fling it the way you look |
| G | **Release**: drop everything you float (they take fall damage) | **Meteor Slam**: slam everything you float down hard |
| X | **Meteor Shower**: loose blocks near you float up and rain on the target (temporary, restored) | **Debris Shield**: floating rubble orbits you and stops projectiles 8 s |
| Z | **Zero G Self** (toggle): weightless drift -- jump to rise, sneak to sink | **Float Leap**: a huge weightless leap |
| V | **Comet**: everything in a wide area floats at once (bosses immune) | **Uravity Meteor**: lift a chunk of ground and drop it on the target (temporary, restored) |
| C | **Gunhead Throw**: martial-arts grab and throw (floating targets fly much further) | **Gunhead Combo**: a quick strike combo |

## Agent ownership (parallel worktrees)

- **core**: wiring checklist (HeroTiers, HeroCommand/ProjectHeroCommand incl. `quirk costume`, PowerGrants, HeroIdentity,
  PowerInfoScreen, ModCreativeTab), Quirk HUD (meter + 12 move boxes + MoveListOverlay), costume layer, Quirk Serums
  (7 items + recipes + loot), the spawn calling, **PowerSuppression enforcement across every power system** + client
  sync + HUD "ERASED", core tests, docs/QUIRKS_REFERENCE.md. Owns `quirk/*.java`, `quirk/data`, `client/quirk/*.java`,
  `hero/PowerSuppression`.
- **one per kit**: owns only `quirk/<kit>/**`, `client/quirk/<kit>/**`, its textures, its gametest class, its lang block.
- **guide**: the guidebook remake (categories Marvel / DC / Quirks / Miraculous / Other + search) incl. Quirk chapters
  built from `QuirkKits` + lang.
- **suits**: Miraculous suit gap audit + Pegasus goggles.
