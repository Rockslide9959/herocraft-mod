# Villain Hideouts (v0.17.5, rebuilt v0.17.7, Tier 3) — reference

Design: `docs/STRUCTURES_PROGRESSION_DESIGN.md` §2. Loot hooks: `docs/LOOT_PROGRESSION_REFERENCE.md`.
Code: `com.projecthero.mod.hideout` (server/common), `com.projecthero.mod.client.hideout` (client).
Tests: `VillainHideoutGameTests`. Assets: `node scratchpad/gen_hideouts_v0175.js`; lang: `node scratchpad/lang_hideouts_v0175.js`
(adds keys one by one).

## v0.17.7: the 10-15 minute crawl

Every hideout keeps its v0.17.5 layout as the **outer level** and gains, below it, the shared **crawl wing**
(`CrawlWing`, themed by a per-faction `CrawlTheme`: palette, guard kinds, vent kind, tech or not, alarm block style,
decoration hooks). Structure ids, biomes, spacing, locator tags, artifacts, clearing and the raid calm are unchanged.
Every hideout is an alarm zone (profile `hideout/<faction>`, tier 3, `HideoutAlarmProfiles`; see
`docs/ALARM_REFERENCE.md`). `HideoutPiece.buildAll` registers the zone by hand (worldgen zones come from the start).

- **Outer defences** (added to each old layout): Alarm Panel #1 with an Alarm Runner nearby, a Sniper perch, a Medic and a
  Shield-bearer among the old guards, cameras (tech factions), Alarm Lamps, spawn points. Kingpin: a fenced back lot
  (guard booth with the panel, a water-tower sniper, a truck, floodlights). Ultron: panel in the entrance hall, a roof
  turret nest. Apokolips: panel inside the gate, two perch snipers, cameras on the tower. Symbiote: an overgrown console
  in the great chamber, a sniper on a biomass column. Frost: a rune-stone ward (ancient panel) in the vestibule, brazier
  lamps, a log watchtower west of the hall.
- **Stairwell** from the old final room (Kingpin's counting room, Ultron's control room, Apokolips's gate floor,
  Symbiote's den, the Frost dais beside the throne) down to the wing's anchor.
- **Wing** (local to the anchor, toward +z): checkpoint (panel #2, camera, Shield-bearer) -> trap gauntlet (laser
  tripwires knee / head high for tech factions, string tripwires on the faction alarm for Symbiote / Frost; vents; an
  arrow crossfire; an end camera) -> Captain's quarters (Captain + keycard, Medic, Runner, Sniper on a mezzanine, a Tier 2
  barrel) -> **keycard Blast Door** -> arena (floor 2 lower, 21 x 21, pillars, panel #3, four spawn points, two elites, the
  mini-boss) -> vault room (sealed core + sealed vault, 4 apart).

| Hideout | Wing | Anchor (ax, ay, az) | Stairwell from (floor y), z | New bounds x / y / z |
|---|---|---|---|---|
| Kingpin's Front | the fight club | (6, -20, 25) | counting room (-8), 13 | -12..17 / -23..13 / -16..92 |
| Ultron Forge | the Deep Forge | (4, -16, 25) | control room (0), 9 | -15..15 / -19..14 / -28..92 |
| Apokolips Outpost | the Fire Pits | (0, -18, 15) | tower gate floor (-4), 1 | -22..22 / -21..28 / -22..82 |
| Symbiote Hive | the Brood Lab | (0, -37, 32) | the den (-29), 24 | -16..31 / -40..3 / -16..99 |
| Frost Giant Hall | the Jarl's Barrow (ancient style) | (-5, -16, 32) | the dais (2), 14 | -16..12 / -19..17 / -23..99 |

Core = `CrawlWing.core(ax, ay, az)` = (ax, ay - 1, az + 62); vault = (ax, ay - 1, az + 66); boss at (ax, ay - 1, az + 49).
Gametests build hideouts 60 blocks above the test (the Symbiote wing reaches 40 down).

## The five hideouts

| Structure id | Biomes | Guards (existing mobs) | Mini-boss | Traps | Artifact |
|---|---|---|---|---|---|
| `kingpins_front` | plains, sunflower plains, meadow, savanna (3+ chunks from villages) | Syndicate thugs, pistol / shotgun / sniper gunmen, an Enforcer | **Kingpin's Enforcer** | side-door tripwire siren, arrow-plate corridor | Kingpin's Gilded Cane |
| `ultron_forge` | stony / jagged peaks, windswept hills | Ultron drones, Sentinel Drones, a Heavy, a Lancer | **Forge Overseer** | laser tripwire sensors, plasma vents | Ultron Cortex Shard |
| `apokolips_outpost` | badlands, desert, savanna plateau | Parademons (standard, gunners on perches, elites, a brute) | **Parademon Commander** | Boom Tube beacon tripwire, fire vents | Mother Box Fragment |
| `symbiote_hive` | forests, taiga, swamp (underground) | Symbiote-infested zombies, husks, skeletons, spiders, a vindicator | **Hive Mother** | strand tripwire to egg pods, biomass vents | Hive Heart |
| `frost_giant_hall` | snowy plains / taiga, ice spikes, grove, snowy slopes | Rime Hounds, Jotun Raiders, a Frost Shaman | **Frost Giant Jarl** | arrows from floor dispensers, war-horn tripwire | Frostbrand |

Placement: `random_spread`, spacing 150 / separation 60 / frequency 0.85, salts 1705101-1705105 (old sites: spacing ~120).
All five are in `#projecthero:locator/tier3`. Never generated in the sea (surface below sea level - 2 is skipped).

## How it is built

- One structure type `projecthero:villain_hideout` (`HideoutStructure`, JSON field `faction`) and one piece type
  (`HideoutPiece`: faction, origin, rotation, saved). `HideoutWorldgen` registers both (not in `ModStructureTypes`).
- `HideoutBuilder.of(faction)` draws the whole place in local coordinates (entrance to local north, y 0 = floor) on a
  `HideoutCanvas`, which rotates every write and clips it to the chunk being generated. Guards / the boss are spawned
  only by the chunk that holds their feet (once each). `HideoutPiece.buildAll(ServerLevel)` builds it in one go (tests, harness).
- `coreLocal()` / `vaultLocal()` give the core and sealed-vault positions; `HideoutPiece.corePos()` / `vaultPos()` in the world.

## Blocks

| Block | States | Notes |
|---|---|---|
| `hideout_core` | `faction`, `sealed` | Sealed: destroy progress 0 (unbreakable), hit/use says who guards it. Unsealed: breakable by hand (pickaxe faster). Removing an unsealed core = **cleared**. Push reaction BLOCK, huge blast resistance, no drops. |
| `hideout_seal` | `facing`, `faction` | Sealed Hideout Vault: unbreakable; turns into an open Tier 3 Hero Vault (same facing) on clearing (`HideoutClearing.unsealVaults`, 20-block search round the core). |
| `hideout_alarm` | `faction`, `spent` | A tripwire hook hangs on it; the first power change fires it once (`HideoutAlarms.trigger`): sound + 3 guards (+1 per extra player within 24, max 6) near it, faction arrival effect. Breaking it disarms the trap. |
| `hideout_vent` | `kind` (fire / plasma / biomass / frost), `phase` | A player stepping on it arms it (hiss, smoke); 12 ticks later it erupts in a 1x2.5 column (fire 5 + burn; plasma 5 + slow; biomass 3 + slow + wither; frost 4 + freeze + slow); 40-tick cooldown. Scheduled ticks only, no block entity. |
| `hive_biomass`, `hive_pod` | | Hive walls (slow to walk on, drips) and glowing egg sacs. |

## Clearing and the raid calm

1. Mini-boss dies -> `HideoutClearing.bossDefeated(level, core, faction)`: `HideoutState` records it, the core unseals.
2. Player breaks the core -> `HideoutClearing.coreBroken`: cleared for good (`HideoutState.markCleared`), vaults unseal,
   title + chat to players within 64.
3. **Raid calm** (`HideoutCalm`): for `CALM_TICKS` (5 in-game days) within `CALM_RADIUS` (512, horizontal) of a cleared
   hideout, that faction's events can't start:
   - `EventManager.start` asks `HideoutCalm.refuses(level, instance, center)`: Syndicate Bust + every `CrimeEvent` (Kingpin),
     `UltronUprising` (Ultron), `DarkseidRaid` (Apokolips), `FrostGiantAssault` (Frost). Players within 48 get a message.
   - Natural spawners: `CarnageSpawner` (Symbiote), `UltronSpawner` / `FrostGiantSpawner` natural triggers.
   - `/projecthero raid ...` (`RaidAdminCommand`) runs inside `HideoutCalm.bypass`, so operator starts are never refused.
- A sealed core someone hits checks its guardian still exists within 96 blocks; if the boss vanished without dying
  (Peaceful, `/kill @e`-style discards) the seal drops anyway, so a hideout never gets stuck.

`HideoutState` is per-dimension `SavedData` (`data/projecthero_hideouts.dat`), keyed by core position. No static
per-world state anywhere (nothing for `ServerStateReset`). `HideoutCalm.BYPASS` is a `ThreadLocal<Boolean>`;
`HideoutClient`'s Cortex reveal timer is client-only and reset on disconnect.

## Mini-bosses (`hideout.boss`)

Each extends its faction's existing mob class (so it reuses that renderer / model) and carries a `HideoutBossLink`:
core position (saved as `HideoutCore`), a boss bar shown to players within 40 blocks, the shared `BossThreat` table
(`HurtByTargetGoal` removed), a leash (walks home past 24 blocks with nobody to fight, put back past 64), death report.

| Boss | Base | HP / armour | Moves |
|---|---|---|---|
| Kingpin's Enforcer | `SyndicateEnforcer`, x1.6, skin `syndicate/hideout_enforcer.png` (recolour of `thug_a`) | 340 / 12 | Shoulder Charge (inherited), Haymaker Combo (7, 7, 14 + launch), Seismic Stomp (1 s ring telegraph, 12 + throw within 6 on the ground), "Boys!" at 50% (2 thugs + a gunman) |
| Forge Overseer | `UltronHeavyEntity`, x1.7, `UltronSkin.OVERSEER` (graphite/gold recolour of `heavy`) | 420 / 12 | Missile Barrage + Stamp (inherited), Overload Beam (1 s telegraph, 1.5 s slow-tracking beam, 4 per 5 ticks, blocked by walls, `BeamClash.report` every live tick), Deploy Drones (2, max 3 near) |
| Parademon Commander | `ParademonEntity` (Elite), x1.5, `parademon_commander.png` (crimson recolour of the Elite) | 380 / 12 | claws + flight (inherited), Bolt Fan (5 bolts x 6), Dive Strike (climb, 0.6 s flame-ring mark, plunge: 12 + fire + throw in 4.5), Boom Tube Reinforcements (2, max 3 near) |
| Hive Mother | `Monster`, x1.8, Carnage's player model, `hideout/hive_mother.png` (black recolour of `carnage`) | 400 / 8 | claws (+Wither), Tendril Lash (up to 3 in sight within 12: 7 + drag), Spike Volley (6 black spikes x 5), Brood (2 infested spiders, max 4); fire x1.5 |
| Frost Giant Jarl | `FrostGiantWarlordEntity`, 1.6 x 6.5 box (model fitted by `FrostModelFit`) | 650 / 12 | Cleave, Sword Plant, Rally, kneel at 30% (all inherited); ignores braziers; the Warlord's own threat table is shared with the link |

**v0.17.7 second phase** (`HideoutPhase`, saved as `Phase2`): once below half health (the Enforcer 45%) -- an announcement,
the boss bar turns red ("(enraged)"), +20% speed, and a telegraphed attack (particle rings mark each spot first). The
shared threat table is untouched.

| Boss | Phase | New attack | Cooldown |
|---|---|---|---|
| Kingpin's Enforcer | Untouchable (Resistance I) | **Bull Rush**: three charges, 0.5 s wind-up each, 10 + throw per hit, stops on walls | 10-13 s |
| Forge Overseer | Overclock | **Plasma Mines**: 5 round the target, 1.5 s, radius 2.2, 9 + Slowness II | 8 s |
| Parademon Commander | Air Raid | **Firebomb Run**: 6 marks in a line through the target ignite one by one, 9 + 4 s fire | 8.5 s |
| Hive Mother | Frenzy (+2 infested spiders) | **Biomass Eruption**: under each player within 16 (max 4), 1.25 s, 10 + launch + Wither | 7.5 s |
| Frost Giant Jarl | Winter's Wrath | **Ice Spike line** marching to the target (10 spots), 10 + freeze + Slowness II; blizzard aura (Slowness I + frost within 12 every 2 s) | 7 s |

Test hooks: `phase().enter(level)`, `phase().startAttack(level)`, `KingpinEnforcerBoss.startRush()`.

Drops (`loot_table/entities/<id>.json`): 3-5 Vibranium Shards, 4-8 XP bottles, a 25% tier 4 Locator (epic), plus faction
valuables. Test hooks: `startStomp/startCombo`, `startBeam/startDeploy`, `startFan/startDive/startTube`, `start(action)`.

## Artifacts (`hideout.item`)

One per faction, rolled **per player** from that hideout's vault: `chests/vault_tier3_artifacts` has a pool per faction
gated by `{"condition": "projecthero:near_hideout", "faction": "<f>", "radius": 32}` (`NearHideoutCondition`: the loot
origin is within 32 blocks of a cleared hideout of that faction) and a random chance (20%; 15% for the two Legendaries).
So a Tier 3 vault anywhere else rolls no artifact. Chosen over boss drops so every player in the party gets a chance.

| Item | Rarity | Effect |
|---|---|---|
| `gilded_cane` (Kingpin's Gilded Cane) | Epic | 9 attack, slow, +1.5 knockback; use: Cane Sweep (160-degree arc, 4 blocks, 8 + strong throw), 6 s cooldown; 900 uses. Distinct from the Syndicate Bust's `kingpin_cane` (the cane gun). |
| `ultron_cortex_shard` | Epic | use: hostiles within 32 outlined through walls for 8 s, viewer-only (`EntityGlowMixin` -> `HideoutClient.cortexReveals`), 45 s cooldown |
| `mother_box_fragment` | Legendary | use: Boom Tube to the furthest standable spot up to 30 blocks along your look (needs ground), 30 s cooldown; no landing = nothing spent |
| `hive_heart` | Legendary | carried: below half health, +1 health every 2 s (first heart in the inventory only) |
| `frostbrand` | Epic | diamond-grade sword (+1); hits add Slowness II 3 s + 100 freeze ticks |

## Guidebook / JEI

Chapter `projecthero.guide.hideouts` ("Villain Hideouts", `HeroPackGuide.CH_HIDEOUTS` = 78, Items, Places & Crafting,
icon `hideout_core`); framing chapters are now 79 (`HeroPackGameTests`, `MiraculousTurtleHorseGameTests`). JEI info pages
for the five artifacts and the core (v0.17.7: and the three new artifacts, the Oath Ballista and the Carnage biomass / egg sac; the Structures chapter lists all eight hideouts).

## v0.17.7: three new hideouts (Gravebound Crypt, Carnage Nest, Oathbreaker Keep)

Design: `docs/STRUCTURES_V0177_DESIGN.md` §4. Same structure type, piece, canvas, crawl wing, alarm profile pattern
(`hideout/<faction>`, tier 3), clearing and raid calm as the five above -- they are three more `HideoutFaction` values
(`gravebound`, `carnage`, `oathbreaker`; serialized names are save / block-state / loot values, never change them).
Assets: `node scratchpad/v0177/gen_new_hideouts_v0177.js` (merges into the existing blockstates and the tier 3 artifact
table); lang: `scratchpad/v0177/new_hideouts_lang.json` + `new_hideouts_lang2.json` via `lang_add.js`. Tests: three more
`hideoutFlow` tests in `VillainHideoutGameTests` (`graveboundCryptClears`, `carnageNestClears`, `oathbreakerKeepClears`);
the new bosses are in `HideoutEntityTypes.bosses()`, so `miniBossesSwitchToTopThreat` and `AlarmGameTests.everyMiniBossHasASecondPhase`
cover them. Harness: `scratchpad/NewHideoutsHarness.v0177.java.txt`. Shots: `docs/screenshots/v0177/new_hideouts/`.

| Structure id | Biomes | Salt (exclusion) | Builder | Anchor (ax, ay, az) / stair from | Bounds x / y / z |
|---|---|---|---|---|---|
| `gravebound_crypt` | dark forest, swamp, mangrove swamp, old-growth spruce taiga | 1707731 (6 chunks from `symbiote_hive`) | `GraveboundCryptBuilder` | (0, -17, 46) / ossuary floor -9 | -18..18 / -20..15 / -17..113 |
| `carnage_nest` | plains, forest, flower forest, birch forest, meadow, taiga, cherry grove | 1707732 (6 from `kingpins_front`) | `CarnageNestBuilder` | (0, -22, 34) / hatchery floor -14 | -21..21 / -25..14 / -17..101 |
| `oathbreaker_keep` | windswept hills / gravelly hills / forest / savanna, stony peaks | 1707733 (6 from `ultron_forge`) | `OathbreakerKeepBuilder` | (0, -14, 24) / great hall floor 0 | -20..20 / -17..19 / -16..91 |

Spacing 150 / separation 60 / frequency 0.85 like the others; terrain adaptation `none` (each builder clears its own
ground and footings; beards would carve the underground wings). All three in `#projecthero:locator/tier3`.

### Layouts

- **Gravebound Crypt** (ancient alarm style, not tech): a fenced graveyard (dark oak rails between mossy posts, soul
  lanterns, a lych-gate, crooked headstones, open graves, dead trees) round a ruined chapel (mossy stone, a gable roof fallen
  in over the nave, purple glass, pews, a candle-lit altar with a crying-obsidian sigil). Outer defences: a **cursed
  headstone tripwire** across the path, a Bone Blade **sniper** on the bell-tower platform (ladder, bell), Alarm Panel #1 (a
  carved ward stone) by the altar with its **runner**, an Acid Zombie **medic**, an armoured Risen **shield-bearer** at the
  door. The aisle stair (`CrawlWing.stairwell`) drops 9 to the **ossuary**: a bone-niched entry hall, then a vaulted
  burial chamber (niches with skulls along both walls, eight sarcophagi, six pillars, chained soul lanterns, four
  **curse vents**, a second curse tripwire across its doorway, a Juggernaut), side tombs (west: a **Cursed Zombie** -- its
  hit is the real Gravebound Curse -- guarding a Tier 1 barrel; east: a barrel). The chamber's stair goes down to the
  wing ("the Lord's Barrow": bone niches, the embalmer's quarters, a tomb arena with a ring of sarcophagi).
- **Carnage Nest** (tech: cameras + lasers): a 14-block crater bowl (blackstone / basalt / magma / Carnage biomass,
  pulsing egg sacs in the cracks, crimson vents) with a raised rim, round a cracked crimson meteor (obsidian, crying
  obsidian, red nether brick, glowing egg cracks). A broken quarantine fence round the rim, floodlights, a crashed
  supply crate. The **field station**: the guard post (Alarm Panel #1, its Stalker **runner**, two cameras, lamps),
  an overrun lab module (Spitter **medic**, specimen tank, cocoon, Tier 1 barrel, a camera) and a watchtower (Spitter
  **sniper**, floodlight). A thrall **shield-bearer** in the bowl. A breach in the meteor's north face leads 9 down to
  the **hatchery**: an egg-walled cave (crimson egg sacs + biomass lining), egg clusters, biomass columns, cocooned
  scientists, three crimson vents, a strand tripwire to an egg sac, broodlings, a Brute. Then the containment labs
  (light grey concrete overgrown with biomass and eggs, egg-sac pillars, consoles, lab benches, a broken specimen cage).
- **Oathbreaker Keep** (ancient style): a 33 x 33 curtain wall (2 thick, crenellated wall-walks at y 8-9, ladders up
  from the courtyard, arrow slits, a collapsed north-west stretch with rubble), four 5 x 5 corner towers (solid below the
  wall-walk, a room above, ladder to the crenellated roof), a gatehouse (raised portcullis, gate room, soul-fire braziers
  outside). **Oath Ballistae** on all four towers, the gatehouse, the keep roof and the south wall-walk. An Arbalist
  **sniper** on the north-east tower, an Arbalist on the west wall-walk. The courtyard: a fenced training yard of straw
  dummies, a barracks lean-to with bedrolls, a broken oath shrine between soul-fire braziers, a well, rubble, barrels.
  The keep (15 x 11, three levels): the great hall (long table, banners, chandeliers, the champion's empty throne, Alarm
  Panel #1 -- a rune ward stone -- with its Squire **runner**, a Squire **medic**), stairs up to the armoury (racks, anvil,
  smithing table, a Tier 1 barrel), ladder to the roof (ballista, banner pole). A Knight **shield-bearer** in the gate.
  The hall-floor stair drops 14 to the undercroft (weathered stone, banners, weapon racks, the hall of oaths with
  braziers, oath tablets and broken statues).

### Guards (`HideoutGuards.Role`)

| Faction | Roles (entity) | Notes |
|---|---|---|
| Gravebound | `RISEN` / `RISEN_ARMOURED` (raid zombie basic / armoured), `ACID`, `BONE_BLADE` (sword skeleton), `JUGGERNAUT`, `CURSED` | the Zombie Raid's player-model undead; reinforcements claw up out of the floor (soul + rooted-dirt particles) |
| Carnage | `THRALL`, `THRALL_BRUTE`, `THRALL_STALKER`, `THRALL_SPITTER` (`crimson_thrall`), `BROODLING` (Carnage's `crimson_spawn`, no parent = never dissolves) | red dust arrival |
| Oathbreaker | `KNIGHT`, `SQUIRE`, `ARBALIST`, `OATHSWORN` (`oath_knight`) | soul-fire arrival |

`HideoutSoldier` (`hideout.mob`, two entity types, synced `Kind`): Thrall 34 hp / 7, Brute x1.3 60 / 10, Stalker fast +
leap, Spitter lobs Carnage's goo globs (`CarnageAttackEntity.glob`); crimson hits add Wither 2 s, fire x1.5. Knight 36 / 7
iron sword + shield, Squire 26 / 5 fast, Arbalist crossbow bolts (~9), Oathsworn x1.2 70 / 12 netherite sword, hits set
alight. Allied within a faction and with every hideout boss; `HurtByTargetGoal` alerts the rest. Skins
`textures/entity/hideout/soldier/<kind>.png` (thralls: shipped human skins under smooth-noise red goo; knights painted
part by part: plate, helm with eye slit, tabard with the oath sigil, belt, pauldrons). Renderer `HideoutSoldierRenderer`
(player model; Arbalists raise the crossbow, bare-handed thralls reach like zombies).

`CarnageAttackEntity` now never hits its owner's allies (`owner.isAlliedTo(victim)`), so the Brood Spawn's and the
Spitters' goo spares their own side.

### Traps and blocks

- **Gravebound Curse traps**: the faction's `hideout_alarm` is a cursed headstone (purple rune). When its tripwire
  snaps: the usual squad (`HideoutAlarms.trigger`) plus the **Crypt Curse** on every player within 6
  (`HideoutAlarms.curse`): Wither II 6 s, Darkness 8 s, Slowness II 6 s, Hunger 10 s. Deliberately *not* the Gravebound
  Curse itself (that schedules a 12-wave Zombie Raid); only the Cursed Zombie in the west side tomb carries the real
  curse, as it does in the world. The crawl wing's string tripwires use the same headstone, so the gauntlet curses too.
- **Vents** (`hideout_vent`): new kinds `curse` (soul fire, 4 + Wither + Slowness) and `crimson` (red goo, 4 + Slowness IV
  2.5 s + Wither).
- `carnage_biomass` (sticky red wall, drips) and `crimson_egg` (pulsing animated egg sac, light 7) --
  `CrimsonBiomassBlock`. (`crimson_biomass` was already Carnage's item id.)
- `oath_ballista` (`OathBallistaBlock` + block entity, facing): every 3 s a heavy bolt (base 2.8 at speed 3.2, ~9) at the
  nearest player within 28 blocks in its 120-degree arc, in sight -- **only while its alarm zone is Suspicious or Alert**
  (no zone = always). Wood, axe breaks it. Element model on dark oak / spruce / iron textures.

### Mini-bosses (`MiniBossBase`)

All three share `MiniBossBase` (Monster + `HideoutBoss`): the `HideoutBossLink` (bar within 40, shared `BossThreat`, no
`HurtByTargetGoal`, leash), a `HideoutPhase` at 50% (saved), one synced action byte, melee only between moves, allied
with every guard. Player models with detailed skins (`MiniBossRenderer`, posed from the action; size from SCALE).
`HideoutBoss.phase()` is now an interface method (default null; the five originals already had it).

| Boss | Base / skin | HP / armour | Moves | Phase 2 (cooldown 7.5 s) |
|---|---|---|---|---|
| Crypt Lord | x1.7, `hideout/crypt_lord` (painted: rotted face, violet eyes, tarnished crown, purple robe with gold embroidery, ermine collar) | 460 / 10 | Soul Reap (0.6 s, 200-degree sweep, 12 + Wither), Grave Grasp (rings under up to 3 players, 1 s, 8 + root), Curse Volley (3 soul bolts, 6 + Wither), Raise the Dead (2 Risen, max 4) | **Requiem**: rings of soul-fire graves at 3 / 6 / 9 blocks erupt 0.4 s apart (9 + Wither + Slowness III); raises 3 Risen |
| Brood Spawn of Carnage | x1.6, `hideout/brood_spawn` (Carnage's skin deeper red, egg sacs, black veins) | 440 / 8, fire x1.5 | Spike Line (10 Carnage spikes to the target), Goo Volley (5 globs), Snare (2 s root), Pounce (leap + shockwave), Brood (3 broodlings, max 6) | **Bloodbath**: 4 spike lines in a cross, then 4 in an X (9 + launch + Wither); births 3 broodlings |
| Oathbound Champion | x1.5, `hideout/oathbound_champion` (blackened plate, gold trim, crowned helm, cyan cape, soul cracks), netherite sword | 520 / 14 | Oath Combo (3 cuts, 8 / 8 / 12 + throw), Shield Charge (0.6 s brace, ~10 blocks, 10 + throw, stops on walls), Oath Slam (1 s ring, 14 + launch + fire in 5), Rally (knights within 16: Strength + Speed 10 s, heal 6; a squire if under 2) | **Judgement Blades**: 3 lines of falling soul blades in a fan toward the target (10 + fire); calls 2 knights |

Drops (`loot_table/entities/<id>.json`): the usual 3-5 Vibranium Shards, 4-8 XP bottles, 25% tier 4 Locator, plus gold +
echo shards (Crypt Lord), redstone + ender pearls (Brood Spawn), iron + diamonds (Champion). Soldiers: thralls redstone /
rotten flesh, knights iron nuggets / arrows.

### Artifacts (tier 3 artifact pools, `near_hideout` 32)

| Item | Rarity / chance | Effect |
|---|---|---|
| `gravecaller_bell` (Gravecaller Bell) | Epic, 20% | use: undead within 16 get Slowness IV + Weakness II 6 s and drop their target; other hostiles Slowness I 3 s; 40 s cooldown (no outlines: the v0.15.4 privacy rule) |
| `brood_fang` (Brood Fang) | Epic, 20% | diamond-grade, faster sword (7 damage, -2.0 speed); hits add Wither I 2 s and heal you 1 |
| `oath_sigil` (Champion's Oath Sigil) | Legendary, 15% | use: Resistance II + Strength I 8 s; 60 s cooldown |

### Raid calm

- Gravebound: `ZOMBIE_RAID` is mapped in `HideoutCalm.factionOf` (Zombie Raid starts are refused nearby -- a cursed player
  near a cleared crypt has the curse rearmed every 60 s by `ZombieRaidStarter` until they leave the calm or cure it) and
  `CursedZombieSpawns.convert` skips conversions in the calm.
- Carnage: `CarnageSpawner` checks the Symbiote **or** the Carnage calm.
- Oathbreaker: the faction had no natural event, so it got one: **Oath-bound patrols** (`OathPatrols`): every 2 min at
  night, each survival player in the Keep's biomes has a 1 in 6 chance that a knight + arbalist + squire patrol rides out
  24-40 blocks away (not persistent, not guards; never on Peaceful, with mob spawning off, with knights already within 64,
  or in a cleared Keep's calm).
