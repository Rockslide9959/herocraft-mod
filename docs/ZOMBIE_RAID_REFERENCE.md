# Zombie Raid — Reference

The Zombie Raid is Project Hero's first **world event**: a twelve-wave survival encounter that comes to
*you*, triggered by a twenty-minute curse you can catch in two different ways. It is built on a small
reusable event framework (`com.projecthero.mod.event`) so later events — an End invasion, a robot
uprising, a world boss — do not have to reinvent waves, participants, boundaries or persistence.

---

## Part 1 — Player guide

### The Gravebound Curse

Twenty minutes of active play. When it runs out, the dead come for you wherever you are standing.

Since v0.14.13 the countdown shows as the **Gravebound** status effect (icon + time left with your other effects)
instead of a HUD block. The effect is only a display (`GraveboundEffect.sync`): the curse still lives in the
persistent attachment, so milk or death strip the icon for at most a second before it is put back.

There are exactly **two** ways to catch it, and they are the same curse:

| Route | How |
| --- | --- |
| **Graveyard** | Find a naturally generated Graveyard and right-click the **Cursed Grave** in its crypt. |
| **Cursed Zombie** | Take a hit from a rare, obviously-cursed zombie that spawns anywhere in the Overworld. |

A second source **never** resets, extends or stacks your timer. If you already have 12:34 left, another
Cursed Zombie hitting you leaves you with 12:34.

**Fully repeatable.** Beating a raid does not close either route — `GraveboundCurse.apply` no-ops only
while you are *currently* cursed, and nothing anywhere gates re-cursing (or the raid) on how many you
have already cleared. v0.9.9 also hardened the payoff: if a fresh curse expires and the raid start is
refused because a *stale* leftover raid record (an abandoned one not yet cleaned up) sits within the
minimum spacing, `ZombieRaidStarter` aborts that record and starts the raid anyway — a served curse
always pays out.

**What cannot remove it:** logging out (the timer simply pauses), dying, changing dimension,
restarting the game or server, drinking milk, or any other effect-clearing mechanic. The curse is not a
status effect — there is nothing for those to reach.

**What can remove it:** eating an **Enchanted Golden Apple**. That is the whole decision the event is
built around — spend a very expensive item to walk away, or keep the curse, prepare, and take the
rewards.

The remaining curse time shows in a small block on the **right edge, near the vertical middle**
(`RaidHud.renderCurse` — clear of the hotbar, the raid bar and the vanilla effect icons). It gets
noisier as it approaches zero: faint ambience early, undead sounds and the odd nearby zombie later,
and in the last two minutes a heartbeat, soul particles and messages like
*"Something is following you..."*.

**When the timer runs out the raid begins right where the player is standing** (only lifted to the
surface if they were mining deep underground — `ZombieRaidStarter.raidCenter`). From v0.7.2 that
moment is unmistakable: a **vanilla-style raid bar** ("Zombie Raid — Wave X/12 · N enemies left", or
the between-wave countdown) appears at the top of the screen for everyone within ~96 blocks, and the
**sky in that area washes a dark purple** — a distance-faded tint applied client-side by
`FogRendererMixin` (horizon / ambient colour **and**, from v0.9.2, a `setupFog` hook that pulls the
atmospheric fog distance in to a genuine ~90-block purple haze so the horizon reads violet instead of
blue) and `ClientLevelMixin` (sky dome), driven by the `RaidSkyPayload` the server sends as a player
crosses the raid boundary (`ZombieRaidNetworking`). v0.9.9 closed the last gap — a blue band between
the purple dome and the purple horizon, which was the raw GL clear colour showing through where the
sky-dome mesh ends; `FogRendererMixin` now re-issues `RenderSystem.clearColor` with the tinted values,
and the tint at the centre of the raid is full (no daytime colour left).
Undead that stray more than ~60 blocks from the centre are walked back; ones dragged much further are
pulled straight back (`EventInstance#tetherOwnedMobs` + `Mob#restrictTo`) — bosses are exempt.

Every mob the curse and the raid put in the world is **immune to sunlight**: the raid families all
extend `RaidUndead` (no daylight burning, no drowned conversion, no reinforcements) and the sword
skeleton overrides `isSunBurnTick`. "changes 22" closed the last hole — the final-stage "watcher"
zombie the curse spawns was a literal `EntityType.ZOMBIE` and simply burned to death before the
player noticed it, since the curse ticks on a wall clock and lands in daylight about half the time.
It is now a BASIC `RaidZombie` (22 HP / 3.5 damage — near-identical to the vanilla mob it replaces),
registered with no event instance, so it is still a warning rather than part of a wave count.

### The Graveyard

A rare above-ground structure in temperate Overworld biomes. Broken perimeter wall, gravel paths, rows
of gravestones on raised plots, dead bushes, cobwebs, bones, skulls, soul lanterns — and a stone crypt
at the centre holding the **Cursed Grave** and a loot chest.

Find one with:

```bash
/locate structure projecthero:graveyard
```

### The Cursed Zombie

Roughly **1%** of naturally spawning zombies come up cursed instead (**~3.5%** near a Graveyard you
have already been to) — v0.9.3 doubled these from 0.5% / 2.5%. It is permanently outlined in purple,
trails soul particles and calls out with a low distorted voice — you are meant to see it coming and
decide whether to fight it.

It is stronger than a zombie and nowhere near a miniboss: 30 HP, a bit more damage and speed, and a much
longer, more persistent chase. Its threat is the curse. Killing it afterwards does not lift the curse.

**The curse — and so the whole raid — is fully repeatable.** `GraveboundCurse.apply` is a no-op only
while you are *currently* cursed; once a raid finishes your curse timer is at zero and a fresh Cursed
Zombie hit (or re-activating a Graveyard's Cursed Grave) curses you again, no matter how many raids
you have already beaten. The v0.9.3 spawn-chance bump is so re-encountering a carrier actually happens
often enough to notice.

### The raid

Twelve waves, with a **Powered Zombie Boss** on waves 4, 8 and 12.

| Wave | Solo count | Composition |
| --- | --- | --- |
| 1 | 12 | 12 Risen |
| 2 | 18 | 10 Risen, 8 Baby |
| 3 | 16 | 10 Risen, 6 Armoured |
| 4 | 20 + boss | 10 Risen, 6 Baby, 4 Armoured, **Powered Zombie Boss** |
| 5 | 18 | 8 Risen, 10 Sword Skeletons |
| 6 | 20 | 12 Risen, 8 Acid |
| 7 | 14 | 4 Juggernauts, 10 Armoured |
| 8 | 34 + boss | 12 Risen, 8 Baby, 8 Armoured, 6 Sword Skeletons, **Powered Zombie Boss** |
| 9 | 26 | 8 Risen, 8 Baby, 10 Sword Skeletons |
| 10 | 30 | 4 Juggernauts, 12 Baby, 8 Risen, 6 Acid |
| 11 | 52 | 12 Risen, 10 Baby, 8 Armoured, 8 Acid, 10 Sword Skeletons, 4 Juggernauts |
| 12 | 34 + boss | 10 Armoured, 8 Acid, 10 Sword Skeletons, 6 Juggernauts, **Final Powered Zombie Boss** |

v0.9.9 doubled every wave. The solo counts above scale up with how many players are actually present
(`mobCountPerExtraPlayer`, lowered to 0.30 in the same pass), and the raid drips spawns in and holds
at a hard live-mob ceiling (`maxLiveMobs`, raised to 120) — a big group's wave that asks for more than
that just arrives in stages as earlier enemies fall.

### Raid enemies

| Enemy | What it does |
| --- | --- |
| **Risen Zombie** | Frontline. A little faster and longer-sighted than a wild zombie. |
| **Baby Zombie** | Fast harassment. You cannot stand still. |
| **Armoured Zombie** | 45 HP, heavy armour and toughness. There to stop the raid being won from range. |
| **Acid Zombie** | Lobs slow, visible acid globs that leave a short-lived damaging pool. Area denial. |
| **Sword Skeleton** | Sword and shield, closes fast, blocks. Hunts whoever is standing back shooting. |
| **Juggernaut Zombie** | 120 HP, oversized, slow. Winds up visibly, then charges in a straight line for heavy damage and huge knockback. Sidestep it. It does **not** break blocks. |
| **Empowered Zombie** | The boss. See below. |

### Powered Zombie Bosses

Each one carries a random **Experimental Power** and fights with it. The boss bar names it:

```
EMPOWERED ZOMBIE — GEOKINESIS
```

Health scales with the number of participants: **400 / 600 / 800 / 1000** for one to four players, and
larger groups face a boss with heavier armour (Minecraft caps mob health at 1024, so the surplus goes
into armour instead). Melee damage (v0.9.9): **15** on waves 4 and 8, **18** for the wave-12 final
boss, which is also tougher, faster, acts more often, and may carry **two** compatible powers.

They pick targets rather than tunnelling on one person: they close on archers, follow fliers, and save
their area attacks for when players bunch up. Nothing they do is unavoidable — every ability is
telegraphed, directional, or both.

**v0.9.9 — bosses feel their power.** Every boss now:

- **plants and winds up (~1s, with a charge-up sound) before a ranged attack**, then fires *accurately*
  at where you are by then — read the tell and move, or take the hit (`BossPowerController.beginRangedCast`
  / `resolvePendingCast`; applied to Pyrokinesis fireballs, Geokinesis boulders, and the Laser Vision /
  Electrokinesis charges, which also lead a moving target slightly);
- **shoves you off it when you crowd it** — a power-independent radial knockback on ~5s cooldown, so
  meleeing any boss is never a free ride (`EmpoweredZombie.closeRangeRepel`);
- **Super Speed** commits to a *blitz*: blurs to you, lands a 3-hit flurry, launches you away, then
  will not blitz again for ~8s;
- **Flight** takes off on its own and holds station high above you, weaving so it is a hard shot,
  dive-bombs straight down for a heavy hit — and is **driven back to the ground and slowed for ~6s
  whenever it is hit in the air**, so shooting it down is the counterplay. It is faster in the air
  than on the ground.

Boss-capable powers: Super Strength, Laser Vision, Flight, Super Speed, Geokinesis, Electrokinesis,
Pyrokinesis, Cryokinesis, Teleportation, Super Durability, Sonic Scream, Shockwave Manipulation,
Gravity Manipulation, Magnetic Manipulation.

> A Magnetism boss will haul you around and clamp your own armour down on you if you are wearing metal
> — but it never takes, damages or destroys a single item.

### Rewards

**Grave Essence** drops from everything in the raid — a small chance from basic enemies, 0–1 from
specials, 1–2 from Juggernauts, 4–8 from a boss and 10–18 from the final boss. Cursed Zombies drop it
too. (v0.13.6: made much rarer across the board — it used to be a 50% drop off every single basic
zombie, which flooded players; it is now also the key ingredient in Knight's Soul, see below.)

Every Powered Zombie Boss drops a **Corrupted Power Core** stamped with its power ("Corrupted
Geokinesis Core"). These are research and crafting reagents — **you cannot eat one to gain the power**.
Bosses also sometimes drop a power trophy head, the **Empowered Zombie Head**. The final boss always drops a
distinct one, the horned and crowned **Grave Champion Head**. Since v0.14.4 both are real heads. You can wear them
(shift-click, drag onto the head slot, right-click the air, or use a dispenser) and they sit on your head like a
vanilla mob head. You can also place them on the floor or mount them on a wall. They remember who took them and on
which day, and their eyes glow in the colour of the boss's power. While worn, the Empowered Zombie Head makes zombies
notice you from half as far away, and the Grave Champion Head does that for every undead. See "v0.14.4 -- trophy
heads" at the end.

v0.14.21: every boss also drops **valuables**, scaled by how late its wave is (`ZombieRaidRewards.bossValuables`):
lapis 4-8 on wave 1 up to 16-28 on wave 12, redstone, iron, gold, emeralds, amethyst shards and (from the middle waves)
diamonds; the final boss adds 1-2 lapis blocks, 1-2 golden apples and an enchanted book.

Clearing wave 12 spawns a **Cursed Grave Chest** at the raid centre with:

- **Guaranteed:** 10–20 Grave Essence, and the final boss's Corrupted Power Core
- **Common:** diamonds, emeralds, golden apples, enchanted books, XP bottles
- **Uncommon:** netherite scrap, ancient debris
- **Rare:** Enchanted Golden Apple (~20%), Gravewalker Charm (~15%), Gravekeeper Shield (~10%),
  a trophy (~10%), Necrotic Blade (~8%)
- **Extremely rare:** Undying Totem (~3%)
- **v0.14.21 valuables** (`ZombieRaidRewards.completionValuables`, scattered in after the table): 24-40 lapis,
  1-3 lapis blocks, 16-32 redstone, 16-32 iron, 8-16 gold, 3-6 diamonds, 6-12 emeralds, 8-16 amethyst, 2-4 golden
  apples and an enchanted book, plus 20% netherite scrap (1-2) and 15% ancient debris. A third more per extra
  eligible participant, up to double for four or more (the Horde chests' rule).

### Raid items

| Item | What it does |
| --- | --- |
| **Grave Essence** | The raid's material. Used by raid recipes and reserved for future supernatural content. |
| **Corrupted Power Core** | Remembers the boss's power. A reagent, never a shortcut to the power. |
| **Gravewalker Charm** | Below 20% health, grants Resistance I and Speed I for 5s. Two-minute cooldown, tracked on the item itself. |
| **Undying Totem** | Three charges. Held in a hand, each prevented death spends one; the last one destroys it. |
| **Necrotic Blade** | 9 base damage, 20% chance of Wither, and killing undead sharpens it for a short window (max 5 stacks). |
| **Gravekeeper Shield** | Blocks like a normal shield. 25% less damage from undead, 55% less from acid, and near-immunity to a Juggernaut charge's knockback. Nothing against anything else. |
| **Heart of the Grave** | First-clear reward. The key to repeatable raids. |
| **Grave Ritual Totem** | Use it in a Graveyard to curse yourself deliberately. Consumed on a successful activation. |
| **Knight's Soul** | Right-click a Respawn Anchor to summon The Oathbreaker, a 4-block knight boss. Consumed on use; he appears 5 seconds later — see `docs/OATHBREAKER_REFERENCE.md`. |

### Recipes

**Enchanted Golden Apple** — deliberately expensive; this is the price of skipping a raid.

```
Netherite Scrap | Gold Block   | Netherite Scrap
Gold Block      | Golden Apple | Gold Block
Netherite Scrap | Gold Block   | Netherite Scrap
```

**Grave Ritual Totem**

```
Grave Essence | Soul Sand           | Grave Essence
Rotten Flesh  | Heart of the Grave  | Rotten Flesh
Soul Sand     | Skeleton Skull      | Soul Sand
```

**Knight's Soul** — an Abyssal Core (dropped by The Abyssal Behemoth, `docs/ABYSSAL_BEHEMOTH_REFERENCE.md`)
surrounded by Grave Essence:

```
Grave Essence | Grave Essence | Grave Essence
Grave Essence | Abyssal Core  | Grave Essence
Grave Essence | Grave Essence | Grave Essence
```

### Power research

Killing a Powered Zombie Boss adds 25% analysis toward its power. At 100% the entry is marked complete
in your **Your Power** screen (default `I`), which shows *POWER ANALYSIS — Research Progress: n%* above
the power's full guide entry.

### Advancements

`Gravebound` · `Not Today` · `Zombie Slayer` · `Deathless` · `One-Man Army` · `Last Stand` ·
`Power Breaker` · `Gravewalker` · `Power Analysis`

---

## Part 2 — Developer notes

### Package layout

```
com.projecthero.mod.event/                 reusable world-event framework
  EventConfig            two config sections (framework / zombieRaid) -> config/projecthero_events.json
  EventState             PENDING / RUNNING / PAUSED / COMPLETED / FAILED
  EventInstance          abstract: identity, participants, owned mobs, abandon logic, save/load
  EventParticipants      presence, eligibility, deaths
  EventObjective         win/lose condition interface
  WaveDefinition         wave data + per-entry group scaling
  EventSpawns            legal spawn-position search
  EventTypes             typeId -> factory registry (for save/load)
  EventSavedData         SavedData holding the live instances
  EventManager           start / query / tick
com.projecthero.mod.event.raid/            the Zombie Raid itself
com.projecthero.mod.event.entity/          raid mobs + the Cursed Zombie spawn conversion
com.projecthero.mod.event.boss/            BossPowerController + BossPowers registry
com.projecthero.mod.event.boss.power/      one controller per boss-capable Experimental Power
com.projecthero.mod.grave/                 the curse, its hooks, the Cursed Grave block, raid items
com.projecthero.mod.worldgen/              GraveyardStructure / GraveyardPiece / GraveyardTracker
```

### Adding a new event

1. Subclass `EventInstance`, implement `typeId()`, `displayName()` and `onTick(ServerLevel)`.
2. Register it in `EventTypes.initialize()` — one line.
3. Start it with `EventManager.start(level, instance, center)`.

You get for free: participant tracking and presence, group-size scaling input, the warn/pause/fail
boundary behaviour, owned-mob tracking and cleanup, and full save/load. If it has waves, reuse
`WaveDefinition` and copy `ZombieRaid`'s owed-count loop; if it has a different objective, implement
`EventObjective` (the raid ships `SurviveWavesObjective`, and the interface exists so *Protect
Villagers*, *Stop Ritual*, *Destroy Anchors* and friends can be added without touching the base class).

### Adding a boss power

One class extending `BossPowerController` plus one line in `BossPowers.initialize()`. The boss bar
label, aura, Corrupted Power Core and trophy all read the power's own registry entry, so nothing else
needs to know it exists. Override `compatibleWith` to refuse a dual-power pairing.

Boss controllers are a **separate, boss-compatible implementation** of each power — they never touch the
player-facing ability handlers, because those are built around `ServerPlayer`, `AbilityContext` and
attachment-backed cooldowns. That isolation is deliberate: a broken boss cannot break a player's power.

### Performance notes

Everything below is a deliberate choice, not an accident:

- **No world scans.** Participants come from `ServerLevel#players()`; owned mobs are tracked by UUID and
  resolved with `ServerLevel#getEntity(UUID)` (a hash lookup). No AABB queries or chunk walks in the
  event loop.
- **Slow cadences.** Events tick every 10 ticks; boss abilities every 10; boss retargeting every 70;
  boss aura every 5. The curse decrements every tick but only *syncs* once a second.
- **Drip spawning.** A wave spawns at most 3 mobs per event tick, from persisted owed-counts.
- **No structure searches on the spawn path.** `GraveyardTracker` caches Graveyard chunk positions from
  `CHUNK_LOAD` (capped, per-dimension) so the Cursed Zombie proximity bonus is a handful of distance
  comparisons. The conversion roll itself is two `nextDouble()` calls, the second only reached by the
  ~2% that could convert at all.
- **No force-loading.** A spawn candidate in an unloaded chunk is rejected, not loaded. An event with
  nobody present pauses instead of working.
- **Acid pools are vanilla `AreaEffectCloud`s** — they expire themselves and cannot leak.
- **No leaks.** Live event state lives in `EventSavedData` (owned by the world), not a static map. The
  three static caches that do exist (`CursedZombieSpawns`, `ZombieRaidNetworking`, `GraveyardTracker`)
  are all cleared by `ServerStateReset` on server stop.
- **The raid bar is a vanilla `ServerBossEvent`** (`EventBossBar`) — it syncs itself, no mod packets.
  The only raid packet left is `RaidSkyPayload`, sent once as a player crosses the dark-purple-sky
  boundary and de-duplicated per player in `ZombieRaidNetworking`.

### Debug commands

Op level 2. Both `zombieraid` and `zombieRaid` work.

| Command | Effect |
| --- | --- |
| `/heropack zombieraid start` | Start a raid at your position |
| `/heropack zombieraid stop` | Stop and clean up the raid you are in |
| `/heropack zombieraid status` | Curse timer, raid state, active world events |
| `/heropack zombieraid wave <1-12>` | Jump to a wave (starts a raid if needed) |
| `/heropack zombieraid completeWave` | Kill the current wave so it advances normally |
| `/heropack zombieraid curse` | Apply the Gravebound Curse to yourself |
| `/heropack zombieraid clearCurse` | Remove it |
| `/heropack zombieraid boss <power>` | Spawn an Empowered Zombie with that power (shorthand works, e.g. `geokinesis`) |
| `/heropack zombieraid spawnCursedZombie` | Spawn one in front of you |
| `/heropack zombieraid giveEssence [count]` | Give Grave Essence (default 32) |
| `/heropack zombieraid markGraveyard` | Register your position as a Graveyard, for testing the raised spawn rate |
| `/heroraid start gravebound` | Start the raid here immediately (shared start/stop command — `supervillain` also works) |
| `/heroraid stop` | Force-end **every** active Project Hero world event and clear all curses |
| `/heroraid cleartimers` | Clear every curse countdown + any raid still in its pre-wave countdown; leave running raids alone |

### Configuration

`config/projecthero_events.json`, written with defaults on first launch and re-written on every start so
new keys appear after a mod update (same behaviour as `config/projecthero.json`).

`framework` — event radius, warn/abandon radii, pause and fail timeouts, tick interval, spawn
distances, live-mob ceiling, minimum distance between events.

`zombieRaid` — curse duration and final-stage threshold, Cursed Zombie spawn chances and Graveyard
proximity radius, wave count, group scaling, between-wave delay, stall timeout, boss health and
cooldown scaling, final-boss multipliers and dual-power chance, every Grave Essence drop range, trophy
chance, and the Gravewalker / Undying Totem / Necrotic Blade tuning.

Graveyard rarity is datapack-side, in
`data/projecthero/worldgen/structure_set/graveyard.json` (`spacing` / `separation`).

### Automated coverage

`src/gametest/java/.../ZombieRaidGameTests.java` runs inside a real Minecraft server via
`./gradlew runGameTest`. It covers: the curse applying once and never resetting, milk and effect
clearing not removing it, only an Enchanted Golden Apple removing it, the curse outliving its source,
a cleared curse scheduling no raid, the wave table's shape and scaling bounds, every boss power mapping
to a real Experimental Power, boss health scaling (including the 1024 clamp behaviour), the final boss
being stronger, Undying Totem charge accounting, Necrotic Blade stack clamping and expiry, Corrupted
Power Core identity, entity attribute registration, the Graveyard being registered and locatable, and
raid start/abort leaving no mobs behind.

Two of those tests were written before the code was right and caught real bugs: a fully spent Undying
Totem reported a full charge count again (an empty stack fell through to the "component-less means
fresh" default), and boss health silently flat-lined at vanilla's 1024 cap for groups of five or more.

## v0.12.23

- The Cursed Zombie applies the Gravebound Curse on any attack against a (non-creative) player, not only when vanilla
  reports damage -- powers that dodge/soak/cancel-and-reapply hits used to swallow it. Never gated by earlier raids.

## v0.14.4 -- trophy heads

Both boss heads are now real heads. The **Grave Champion Head** (`final_boss_trophy`, dropped by the wave-12 boss every
time) and the **Empowered Zombie Head** (`boss_trophy`, dropped by any Powered Zombie Boss at `bossTrophyChance`, and
also found as chest loot) are both `BossTrophyItem` (`StandingAndWallBlockItem` + `Equipable`, HEAD). The v0.9.22
`GraveChampionHeadItem` / `GraveChampionHeadBlock` are gone.

- **Worn:** shift-click, drag, right-click the air, a dispenser (`ArmorItem.DISPENSE_ITEM_BEHAVIOR`), armour stands and
  mobs. The head used to float above the wearer's head, because the old item model's `head` transform lifted it 14.25
  units at 1.5x. The new transform is derived from `CustomHeadLayer`'s bytecode. A non-skull block item goes through
  `translateToHead` (0.625 scale, so 1 model unit = 0.625 px). With `scale 1.9, translation [0, 8.8, 0]`, the 8-unit
  skull sits on the neck at 9.5 px, the same size and seat as a vanilla mob head. The model's north face ends up as the
  wearer's face, as with a carved pumpkin. Derivation: `scratchpad/gen_grave_heads_v0144.js`. The other display
  transforms copy vanilla `template_skull`, turned 180 degrees because our face is on north.
- **Perk while worn** (`TrophyHeads#detectionFactor`, applied by `TrophyHeadVisibilityMixin` on
  `getVisibilityPercent`, for any wearer): the Empowered Zombie Head halves how far zombies notice you, and the Grave
  Champion Head does it for every undead. This is vanilla's mob-head rule, scaled to the trophy.
- **Placed:** each head has a floor block and a wall block. `TrophyHeadBlock` stands on the floor with 4-way facing.
  `TrophyHeadWallBlock` mounts on a wall, using vanilla's wall-skull shapes and placement. Ids: `grave_champion_head` /
  `grave_champion_wall_head`, `empowered_zombie_head` / `empowered_zombie_wall_head`. `TrophyHeadBlockEntity`
  (`projecthero:trophy_head`) keeps `power_key` + `trophy_record` via `applyImplicitComponents` /
  `collectImplicitComponents`, and the loot tables `copy_components` them back. So breaking a head gives back the same
  named trophy. Right-clicking a placed head shows its plaque in the action bar.
- **Record:** `GraveComponents.TROPHY_RECORD` (`TrophyRecord(slayer, day)`). `ZombieRaidRewards` stamps it from the
  boss's kill credit and the world day. The tooltip shows it as "Slain by X on day N", with a blue perk line below.
- **Look:** new 3D models and 64x64 textures, from `scratchpad/gen_grave_heads_v0144.js` and previewed offline with
  `scratchpad/preview_heads_v0144.js`.
  - The Champion: a cracked skull under a rusted iron-and-gold crown, with bone spikes, a crown gem and curling horns.
  - The Zombie Head: rotting, with a sigil carved into the brow and a crack in the skull.
  - Faces with `tintindex 0` are glow decals, tinted per power family (`TrophyHeads#glowColor`). Held items get the
    colour from the item colour provider and placed heads from the block entity through the block colour provider.
    `TrophyHeadClient` draws those faces full-bright through FRAPI.
  - Blocks render in the cutout layer. The old flat item textures were removed.
- Tests: `V0144GraveHulkGameTests` covers the head slot, place/break keeping power + record, and the detection factors.

## v0.14.4 -- repeatable raids

Player report: the Gravebound Curse / Zombie Raid should be repeatable. Investigation found **no hard
blocker** in the curse -> raid -> victory -> curse-again chain: `GraveboundCurse.apply` only refuses an
already-cursed player, no source checks `raidsCompleted` / `heartOfTheGraveGranted`, a finished raid is
removed from `EventSavedData` the tick it completes, and the user's saves hold no stuck event or curse
state (every player's `gravebound_state` had `curse_ticks` 0; `raids_completed` up to 5). The user's logs
show one natural curse -> raid -> clear cycle; every other Zombie Raid there was started by command. What
*could* make it look one-time, now fixed:

- **A used Cursed Grave looked spent forever.** It set `LIT` on use and never cleared it. It now random-ticks
  while lit (like redstone ore) and rekindles after about a minute (`CursedGraveBlock.randomTick`).
- **A served curse could be thrown away.** If the curse ran out 96-256 blocks from someone else's *running*
  Zombie Raid, `startForCursedPlayer` was refused by the minimum event spacing and only printed "another
  raid rages nearby" -- no raid, no curse, nothing left to wait for. It now re-arms a 60 s curse
  (`GraveboundCurse.rearm`, `ZombieRaidStarter.DEFER_TICKS`, message `event.projecthero.zombie_raid.deferred`)
  so the raid still comes once the other one ends or the player moves away.
- Guide text (`projecthero.guide.zombie_raid.sources.body` / `.repeat.body`) now says outright that beating
  a raid never makes you immune and that every source works again.

**Tests** (`RaidRepeatGameTests`): the full lifecycle twice -- Cursed-Zombie curse -> expire -> raid starts
-> jump to wave 12 and clear it -> raid COMPLETED (raidsCompleted recorded) and removed -> cursed again ->
expire -> a second raid starts; every source (Graveyard, Cursed Zombie, Ritual) curses a 7-raid veteran; a
curse expiring 150 blocks from a running raid is deferred, not lost; a lit Cursed Grave rekindles.

Also noticed (not changed here): the user's `config/projecthero_events.json` still carries the pre-v0.13.6
Grave Essence values (`graveEssenceFromBasicChance` 0.5 etc.) -- `EventConfig` re-writes loaded values, so
default changes never reach existing configs (same stale-config pattern as the Behemoth fix).

## v0.14.4 -- event config migration
`config/projecthero_events.json` is re-written with whatever it loaded, so changed defaults never reached an existing
file: installs from before v0.13.6 still dropped Grave Essence at the old flood rates (0.5 per basic raid zombie, boss
10-20, final boss 25-40, Cursed Zombie 0.33). The file now carries `configVersion`; a file without it has exactly the
Grave Essence / Cursed Zombie drop values reset to the current defaults (every other value is kept) and is stamped v1.
Test: `RaidRepeatGameTests#aPreVersionEventConfigGetsTheCurrentGraveEssenceDrops`.
