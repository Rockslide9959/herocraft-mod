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
for the five artifacts and the core.
