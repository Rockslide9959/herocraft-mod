# City Reference

The City is a very rare, very large village that grew into a town and then a downtown. The design (and the user's
approval) is `docs/CITY_DESIGN.md`. This file documents what is built.

* **Part A** (this section): the worldgen, the layout, the buildings and the templates.
* **Part B** (City systems: Precinct Desk / Repair Bench / Safe Deposit blocks, City Cops, Repair Workers, the repair
  system, the bank and the newspaper) is documented in its own section.

---

## Part A: worldgen and buildings (v0.16.2)

### Ids

| What | Id |
|---|---|
| Structure | `projecthero:city` (`worldgen/structure/city.json`) |
| Structure type | `projecthero:city` (`com.projecthero.mod.city.CityStructure`, registered in `CityWorldgen`) |
| Structure set | `projecthero:city` (`worldgen/structure_set/city.json`) |
| Structure tags | `#projecthero:city` (for `/locate structure #projecthero:city`), `#projecthero:on_city_maps` (City Map target) |
| Biome tag | `#projecthero:city_biomes`: plains, sunflower plains, meadow, savanna |
| Template pools | `projecthero:city/...` (`worldgen/template_pool/city/`, 45 pools) |
| Templates | `projecthero:city/street/*`, `city/block/*`, `city/building/*` (`structure/city/`, 46 NBT files) |
| City Map | cartographer level 3 (Journeyman), 16 emeralds + compass, item name `filled_map.projecthero.city` |

### Rarity and placement

* `random_spread`, **spacing 160 chunks, separation 100**, salt `1621016024`: one candidate per 2560 x 2560 blocks.
* **Exclusion zone** 10 chunks against `minecraft:villages`.
* The candidate must be in a City biome (checked first, before anything expensive) and pass the **terrain check**
  (`CityStructure.siteProblem`): the downtown core (+-48, 5 x 5 samples) varies by at most 14 blocks and has no water;
  the whole footprint (+-104, 9 x 9 samples) varies by at most 36 and is at most 25% water. All four limits are in
  `city.json` (`max_core_height_variation`, `max_height_variation`, `max_water_percent`). A rejected site costs a few
  noise-column lookups and bails out at the first broken limit.
* `/locate structure projecthero:city` and `/place structure projecthero:city` work (`/place` skips the biome test but
  not the terrain test).

### Generation

`CityStructure.findGenerationPoint` calls vanilla `JigsawPlacement.addPieces` (start pool `projecthero:city/center`,
`size` 20, `max_distance_from_center` 116, expansion hack on like villages, start projected to `WORLD_SURFACE_WG`).
Every piece is a vanilla `PoolElementStructurePiece`, placed chunk by chunk at the `surface_structures` step like a
village. `terrain_adaptation` is `beard_box` (foundations under buildings, hills carved out of their boxes).

**Village style by biome:** the village-edge connectors point at three alias pools that the structure redirects by the
centre's biome (`BiomeTags.HAS_VILLAGE_SAVANNA` -> savanna, otherwise plains):

| Alias pool (its JSON is the plains copy) | Savanna city | Plains / meadow city |
|---|---|---|
| `projecthero:city/village/houses` | `minecraft:village/savanna/houses` | `minecraft:village/plains/houses` |
| `projecthero:city/village/streets` | `minecraft:village/savanna/streets` | `minecraft:village/plains/streets` |
| `projecthero:city/villagers` | `minecraft:village/savanna/villagers` | `minecraft:village/plains/villagers` |

### The layout

Coordinates are relative to the centre crossing (`+x` = whichever arm the random start rotation points east).

```
            village edge (vanilla houses + streets, ~100-116)
     +----------------- ring road (R = 96) -----------------+
     |  town lots          | radial lane |        town lots |
     |      +--------------+--- avenue --+--------------+   |
     |      | block (48x48)|             | block        |   |
     |------+--- avenue ---+  crossing   +--- avenue ---+---|
     |      | block        |             | block        |   |
     |      +--------------+--- avenue --+--------------+   |
     |  town lots          | radial lane |        town lots |
     +-----------------------------------------------------+
```

* **Crossing** (start, 15 x 15) with four connectors, each to its own arm pool (`city/arms/a..d`), so every arm is a
  fixed chain: **avenue** (48 x 15, x 8..55) -> **blend** avenue-to-lane (8 x 9, 56..63) -> **radial lane** (28 x 9,
  64..91) -> **junction** (9 x 9, 92..100). Street templates attach only by their back connector
  (`projecthero:street`; forward connectors are named `projecthero:street_out`).
* Each avenue carries a **city block** on its left (counter-clockwise) side, so the four quadrants are owned by one arm
  each (a pinwheel) and never compete:

  | Arm | Block | Front-left lot | Front-right lot | Back lot (faces the other avenue) | Radial lane, left / right |
  |---|---|---|---|---|---|
  | a | `civic` | Police Precinct | Office Skyscraper | City Bank | Market Square / random town lot |
  | b | `medical` | Hospital | Apartment Tower | Apartment or Office | Central Park / random town lot |
  | c | `news` | Daily Bulletin | Clock Tower | Walk-up, Apartment or Office | Maintenance Depot / random town lot |
  | d | `station` | Train Station (whole block) | | | Fire Station / random town lot |

  A block is a 48 x 48 rigid ground plate (box 64 tall, void above the ground) with lot jigsaws on its edges; its
  buildings are its children, placed inside its box. The corner no building uses is a courtyard garden.
* Each junction turns left onto the **ring road** (`ring_1`, `ring_2`, `ring_3`, `ring_corner`, `ring_4`, `ring_5`,
  `ring_6`, each 29 long), which ends exactly at the next arm's junction, closing the square ring at R = 96. The ring
  road is the village-edge road (mostly dirt path with cobble). Its inner side has a town lot per segment; its outer
  side has one vanilla house connector (`minecraft:building_entrance`) and one short dirt `edge_path` (two more
  house connectors) per segment; the junctions and corners send a full vanilla street (`minecraft:street`) outward.
* Depth: crossing 0, avenue 1, block 2, buildings 3, villagers 4; ring segments reach depth 11, vanilla streets grow
  from there to the `size` limit of 20. Everything stays inside 116 blocks of the centre (inside the 8-chunk reference
  range of the start chunk).

**Town lot pools** (`city/lots/...`): `town_big` (radial lanes' right side: construction site, docks, walk-up, 4- and
5-unit townhouse rows), `ring` (ring road inner side: 3- and 4-unit townhouse rows, walk-up, pocket park, garden plot),
fallback `town_small` (pocket park, garden plot, kiosk plaza). Where two lots want the same ground the jigsaw keeps the
first and the second falls back to a smaller lot or nothing.

### Templates

| Template | Size (x y z) | Notes |
|---|---|---|
| `street/crossing` | 15 8 15 | asphalt, kerbs, four zebras, four lamps |
| `street/avenue_a..d` | 48 8 15 | identical surface; differ in their block and blend pools |
| `street/blend_a..d` | 8 8 9 | asphalt breaks up into cobble |
| `street/radial_a..d` | 28 8 9 | cobble lane, dirt-path shoulders, plains lamps; differ in the guaranteed lot pool |
| `street/junction`, `street/ring_corner` | 9 8 9 | lane squares |
| `street/ring_1..6` | 29 8 9 | the village-edge ring road |
| `block/civic`, `medical`, `news`, `station` | 48 64 48 | ground plate, lot jigsaws, courtyard |
| `building/police_precinct` | 28 26 25 | 3 Precinct Desks, armoury, 5 cell bunks |
| `building/office_skyscraper` | 18 60 22 | 2 cartography tables, 2 lecterns |
| `building/city_bank` | 21 28 23 | open vault door, 18 Safe Deposits, 2 lecterns |
| `building/hospital` | 25 32 26 | 7 ward beds, 2 brewing stands |
| `building/apartment_tower` | 20 35 18 | 12 beds, smoker, switchback stairwell |
| `building/daily_bulletin` | 32 42 19 | 6 lecterns, stairs to the first floor |
| `building/clock_tower` | 15 47 15 | bell, iron golem |
| `building/train_station` | 47 34 46 | 2 cartography tables, platforms, train |
| `building/brownstone_walkup` | 27 35 20 | 8 beds, roof garden + composter, HERO COLA billboard |
| `building/fire_station` | 27 40 26 | 5 bunks, 2 water cauldrons |
| `building/market_square` | 35 20 35 | 6 stall job sites, bell, iron golem |
| `building/central_park` | 33 15 34 | fountain, statue, pavilion bell, iron golem |
| `building/construction_site` | 33 49 27 | stonecutter, smithing table |
| `building/warehouse_docks` | 33 23 41 | 4 barrels, 2 smokers, quay and basin (street side is the warehouse's back) |
| `building/maintenance_depot` | 23 17 23 | 2 Repair Benches, 2 bunks, workshop |
| `building/townhouse_row_3/4/5` | 21/28/35 15 24 | per unit: 2 beds upstairs, 1 job site, stairs, back garden |
| `building/pocket_park`, `garden_plot`, `kiosk_plaza` | small | fillers |

Template conventions (all generated by the exporter, see below):

* **Buildings and blocks are rigid.** The street side is the template's max-z edge: a one-row apron carrying the lot
  jigsaw (`projecthero:lot` / `projecthero:block`) at template y = ground + 1. Below the ground layer only placed
  blocks are saved (terrain shows through); above it every empty cell is saved as air, so a building clears its box.
* **Downtown is flat, the rest follows the land.** The crossing and the four avenues are rigid (one plane at the
  centre's height, so the blocks line up with their sidewalks; the terrain check keeps the core within 14 blocks and
  beard_box blends it in). Blends, lanes, the ring road and the edge paths are terrain-matching. Street templates:
  road surface at template y 0, two rows of air above it (clears grass that neighbouring chunks plant early), lamps
  as single columns (a sloped street can't shear them), structure void elsewhere, box 8 tall.
* **No random processors** on any City template, so part B's repair blueprint can read them as-is. (The vanilla
  village-edge pieces keep vanilla's own processors, e.g. `mossify_10_percent`; they are not blueprint material.)
* **Villagers** arrive like in vanilla: floor jigsaws (`minecraft:bottom`) into `projecthero:city/villagers`;
  **iron golems** the same way into `minecraft:village/common/iron_golem` (clock square, market, park).
* Multi-storey homes have **stairs** (villagers path up stairs, not ladders), so every bed and job site is claimable.

### Building ids part B recognises

Part B finds a building by a substring of the template id of the piece holding a job site:

| Part B role | Substring | Template id |
|---|---|---|
| Doctor + ward Regeneration | `hospital` | `projecthero:city/building/hospital` |
| Firefighter | `fire_station` | `projecthero:city/building/fire_station` |
| Daily Bulletin newspaper | `bulletin` | `projecthero:city/building/daily_bulletin` |
| Bank | `bank` | `projecthero:city/building/city_bank` |
| Police | `police`, `precinct` | `projecthero:city/building/police_precinct` |
| Repair Workers' home | `depot`, `maintenance` | `projecthero:city/building/maintenance_depot` |

Every building piece is rigid, processor-free and single-palette. Note that the downtown buildings sit inside a block
piece (`city/block/civic|medical|news|station`) whose box also contains them, so a lookup must take the building
piece, not the first piece whose box contains the position.

### Notes for part B (blueprint)

* A position is "in a City" when `level.structureManager().getStructureWithPieceAt(pos, <projecthero:city>)` is
  valid. City pieces are `PoolElementStructurePiece`s whose element is a `single_pool_element` with a
  `projecthero:city/...` location; vanilla village pieces (`legacy_single_pool_element`, `minecraft:village/...`) are
  part of the City too but are randomised by vanilla processors.
* **Buildings nest inside blocks.** A downtown block piece's box contains its buildings' boxes; the block template is
  just ground. For a position, take the **last** piece in the start's piece list whose template has a non-void block
  there (children are placed after their parents).
* **Jigsaw blocks** in a template become their `final_state` when placed (`minecraft:air`, `minecraft:structure_void`
  = nothing, or a floor block for villager/golem spots).
* **Lanes, the ring road and edge paths are terrain-matching** (the crossing and avenues are rigid): each column is shifted to `WORLD_SURFACE_WG - 1` at placement, so a street
  block's expected y is not the template y + piece y (and a block may sit slightly outside the piece box on a slope).
* Containers (barrels, chests) are empty in the templates; there are no loot tables.

### Regenerating the templates

The builder routines live in the gametest source set (never shipped): `gametest/city/CityBuilder.java` (every building
and the street kit) and `gametest/city/CityTemplateExporter.java` (sizes, jigsaws, block layouts and all the pool JSON).

```
PROJECTHERO_CITY_EXPORT=1 ./gradlew runGameTest -PtestFilter=exportCityTemplates
```

rewrites `src/main/resources/data/projecthero/structure/city/**` and `worldgen/template_pool/city/**` (about 10 s).
Without the variable the test is a no-op. The block layouts are computed from the measured building sizes, so a
building that grows moves its lot jigsaw automatically (the exporter logs a WARN if a lot no longer fits).

### Tests

`CityV0162GameTests` (`-PtestFilter=CityV0162`): structure/set/tags load (spacing, separation, biomes), every template
and pool loads and stays within 48 x 64 x 48, the buildings' beds / job sites / bells / part B blocks / villager and
golem spots, stairs in multi-storey homes, a real piece placed through its pool element, the whole layout assembled in
memory (every guaranteed building, 4 junctions and corners, vanilla edge pieces, extent and timing), and the City Map
trade.

### Temporary: part B block placeholders

`com.projecthero.mod.city.CityBlocksPlaceholder` registers bare `projecthero:precinct_desk`, `projecthero:repair_bench`
and `projecthero:safe_deposit` (horizontal `facing`, no item, no model) so the templates load in part A's branch. Part
B's real blocks replace it at merge (delete the file and its `initialize()` line in `ProjectHeroMod`).

# The City (v0.16.2)

The City is a very rare, very large village that grew into a town and a downtown (design: `docs/CITY_DESIGN.md`).
Part A is the worldgen (the `projecthero:city` jigsaw structure and its buildings). Part B is the systems that run
inside it. Each part has its own section here.

<!-- City part A (worldgen and buildings) goes in its own section above or below part B. -->

## Part B: the systems

Package `com.projecthero.mod.city`. The entry point is `City.initialize()` (called from `ProjectHeroMod` after
`CityConfig.load()`). Everything finds Cities through `CityLocator`, so part B works on any City built from jigsaw
pieces, and on a test City registered by hand.

### The shared contract with part A

| Thing | Id | Notes |
|---|---|---|
| Structure | `projecthero:city` | `CityLocator.CITY_KEY`. Membership: `level.structureManager().getStructureWithPieceAt(pos, city)`. |
| City Cop job site | `projecthero:precinct_desk` | Block + POI type (1 ticket). Horizontal `facing`. Use it to read the crime reports. |
| Repair Worker job site | `projecthero:repair_bench` | Block + POI type (1 ticket). Horizontal `facing`. Use it to see the repair board. |
| Bank vault | `projecthero:safe_deposit` | Horizontal `facing`. Opens the user's own emerald account. |
| Professions | `projecthero:city_cop`, `projecthero:repair_worker` | Both POI types are in `#minecraft:acquirable_job_site`, so unemployed villagers claim them like any job site. |
| Newspaper | `projecthero:daily_bulletin` | Item. |

Part A must register no blocks with these ids itself. Its `CityBlocksPlaceholder` is replaced by `city/CityBlocks`
at merge.

**Which building is which.** A doctor, a firefighter and the newspaper seller are vanilla professions whose job site is
in a certain building. `CityBuilding` reads this from the template id of the piece that holds the job site, by a
substring of its path:

| Building | Substrings |
|---|---|
| Hospital | `hospital` |
| Fire station | `fire_station`, `firestation`, `fire_hall` |
| Daily Bulletin | `bulletin`, `newspaper` |
| Bank | `bank` |
| Police | `police`, `precinct` |
| Depot | `depot`, `maintenance` |

So `projecthero:city/core/hospital_base` and `.../hospital_tower` are both the hospital.

### Classes

| Class | Job |
|---|---|
| `City` | Entry point. Registers the hooks, the `projectheroCityRepair` gamerule (default on), and the crime listener. Has `repairActive(level)` and `clearSessionState()` (in `ServerStateReset`). |
| `CityLocator`, `CityArea` | Finds which City a position is in. `cityAt` gives the City with a piece at the position; `cityAround` gives the City whose outer bounds hold it. A per-level set of loaded City chunks (from `CHUNK_LOAD` structure references) is the one-hash-lookup filter. `register`/`unregister` add a City made of hand-built pieces (gametests). |
| `CityBlueprint` | The expected block at a position, read from the placed pieces (see below). |
| `CitySavedData` | Per dimension (`projecthero_city`). Holds each City's damage queue, its opt-out cells and its hire clock, plus the level-wide no-drop positions and any repair scaffolding still standing. |
| `CityDamageTracker` | The block-change hook, player renovations, the audit sweep and the fire list. |
| `CityRepair` | Repair trips, scaffolding, self-repair, hiring, the anti-dupe drop check and the repair board. |
| `CityCops` | City Cop stats, targeting, combat, patrol and crime calls. |
| `CityServices` | Doctor, firefighter and the newspaper seller's trade. |
| `CityVillagers` | The per-villager dispatcher (`CityVillagerMixin`). |
| `CityBuilding` | Which building a piece is. |
| `bank/BankAccounts`, `bank/SafeDepositBlock`, `bank/SafeDepositMenu` | The bank. |
| `DailyBulletin`, `DailyBulletinItem` | The newspaper. |
| `CityBlocks`, `CityTrades`, `CityCommands`, `CityConfig` | Registration, trades, `/projecthero city`, the config. |

Mixins: `CityLevelChunkMixin` (`LevelChunk.setBlockState` RETURN), `CityBlockDropMixin` (`Block.popResource` HEAD),
`CityBlockItemMixin` (marks a player's `BlockItem.place`), `CityVillagerMixin` (`Villager.customServerAiStep` TAIL),
`CityVillagerPanicMixin` (`VillagerPanicTrigger.hasHostile`/`isHurt`), plus two accessors:
`CitySinglePoolElementAccessor` and `CityStructureTemplateAccessor`. On the client, `CityVillagerSwingMixin`
bobs a villager's folded arms on a swing, and `client/city/SafeDepositScreen` draws the vault screen.

### The blueprint (`CityBlueprint`)

No copy of the City is stored. For a `PoolElementStructurePiece` whose element is a `SinglePoolElement`:

- The piece has a template, a corner (`getPosition()`) and a rotation. Pool pieces are never mirrored and rotate
  about their corner.
- `local = transform(world - corner, NONE, inverse(rotation), ZERO)`.
- The expected state is `template[local].rotate(rotation)`.

Each template's palette is indexed once by local position and kept in an LRU of 96 templates. Only non-air blocks are
indexed. Jigsaw, structure and structure-void blocks are left out, because placement replaces them.

These have no expectation (null): other piece kinds, multi-palette templates, and terrain-matching pieces (village
streets get bent onto the ground). `forEachExpected` walks a piece's expected blocks inside a box, for the sweep.
`templateId` gives the template id, which is how `CityBuilding` tells buildings apart.

### Repair system

**Detecting damage (`CityDamageTracker`).**

1. **Block-change hook.** Every change in a loaded City chunk is queued, then looked at at the end of the tick.
   Damage is recorded when the old block matched the blueprint and the new one is air, fluid or fire. This catches
   mining, explosions, fire, smashes and endermen. A burning block is also added to the firefighters' fire list.
2. **Player renovations.** `CityBlockItemMixin` marks when a player places a block. Such a block is a renovation if it
   is a different block from the blueprint's, and either its slot was queued, or its 4x4x4 cell took blueprint damage
   within `renovationWindowTicks` (2 min). A renovation opts the whole cell out: its queued damage is dropped and it
   is never repaired. If the player places the blueprint's own block, the slot just counts as fixed.
3. **Audit sweep.** Every `sweepIntervalTicks` (40), one loaded City chunk is diffed against the blueprint. Each chunk
   is swept at most once per `sweepChunkEveryTicks` (48000, 2 days), and at most 4096 positions are queued per sweep.
   This catches damage done while the systems were off. Pieces with structure processors are skipped, because a
   processor may have left a slot empty on purpose.

Damage is grouped into 16x16 columns. Each column keeps the time of its latest damage.

**A repair trip (`CityRepair`).** Once a second, each City whose oldest due column has no running job gets one. A
column is due once its latest damage is `repairDelayTicks` old: 12000, which is 10 in-game minutes (clamped to
1200-72000), so nothing is rebuilt mid-fight. A trip needs all of these:

- work hours (day time 2000-9000), when `workHoursOnly` is on
- no raid and no monster within 16 blocks
- an idle Repair Worker in the City

The nearest worker:

- walks out with a hammer (a mace in its folded arms)
- rebuilds bottom-up, one block every `ticksPerBlock` (10), full blocks before the rest at each height
- swings its arms on each block, with block particles, wax-on sparkles and the block's place sound
- does up to `blocksPerTrip` (512) blocks a trip, all within about 48 blocks of the first one

How it reaches the blocks:

| Case | What it does |
|---|---|
| Within reach (4.5 blocks from its eyes) | Builds from where it stands. Its AI is paused while it builds. |
| Higher than 3 blocks above its feet, within 3 blocks sideways | Climbs a temporary **scaffolding column**, one block a step (24 at most). At the end of the stretch it takes the column down from the top and steps down. |
| Not within reach after `reachTimeoutTicks` (600, 30 s) | Repairs from where it stands. |

A trip ends early at night, during a raid, or with a monster within 16 blocks. It also ends when the trip is done or
the worker is gone. When it ends:

- the scaffolding comes down
- the worker gets its AI back and drops the hammer
- it walks back to its bench

If a block's gap is occupied by someone, that block is done last. If the worker itself is standing in the gap, it
steps aside first.

**No worker.** With no Repair Worker in the City, due damage fills in on its own once it is `selfRepairAfterTicks`
(24000, a day) past due, one block every `selfRepairTicksPerBlock` (40).

The depot also hires. Every 10 s it checks its staff, and while the City has fewer than `workersPerCity` (2) workers,
it hires one every `hireDelayTicks` (24000). A hire is a new villager at a free Repair Bench in the City:

- its profession is set to Repair Worker and its brain is rebuilt
- it takes the bench's POI ticket and gets the bench as its job site

A bench is free when its POI ticket isn't taken. A bench held by a worker in an unloaded chunk still counts as taken.

**Safety (checked again when each block is placed).**

- A slot is only filled while it is air, fluid or fire. Anything else, such as a player's block, drops the slot and
  opts its cell out.
- Slots inside vanilla spawn protection are never filled. This applies on dedicated servers, in the Overworld, unless
  `repairInsideSpawnProtection` is set.
- Blocks are placed with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, the same flags as structure placement, so half-built
  doors and beds don't pop off. Containers are emptied, and no block entity data or loot table is ever copied.
- **The bank vault comes back as filler.** These blocks are rebuilt as stone bricks: emerald, gold, diamond, iron,
  netherite, lapis, copper and the raw-ore blocks, ancient debris and beacons.
- **Anti-dupe.** A rebuilt block is marked for `noDropTicks` (24000, a day). If it is broken in that time,
  `CityBlockDropMixin` cancels its drops, both player breaking and explosions. Once the block is replaced, the mark
  lasts 2 ticks so it covers that break's drops only.
- Repair changes, scaffolding included, are never recorded as damage.
- Entities are never respawned by repairs.
- A worker left frozen by a restart gets its AI back when it loads (`FROZEN_TAG`). Scaffolding left over is removed
  10 s after load.

### City Cop (`projecthero:city_cop`)

**Look.** A navy uniform with a gold badge, a duty belt and a police cap (`textures/entity/villager/profession/city_cop.png`,
with a zombie copy). It carries a stick as a baton.

**Stats** (`syncStats`, permanent modifiers, removed again if the villager loses the job). It is a weaker iron golem in
a villager body:

| Stat | Value |
|---|---|
| Health | 40 |
| Armour | 8 |
| Baton damage | 6 |
| Swing | one every 20 ticks |
| Reach | 2.4 blocks |
| Healing out of combat | 0.5 HP a second |

Above a quarter of its health a cop doesn't panic at monsters or wounds (`CityVillagerPanicMixin`). Below that, vanilla
panic takes over and it flees.

**Who it fights** (rechecked every 10 ticks):

1. A player who hit it in the last 10 s, if that player is not in creative or spectator.
2. The nearest monster (`Enemy`) within 12 blocks that it can see.
3. A crime crook within 16 blocks, but only once its crime has run `crimeEngageDelaySeconds` (20). Crooks take
   `crookDamageMultiplier` (0.5) of the damage. Cops help, but the hero still matters.

It never attacks any other player.

**Crime calls.** `Crimes.addListener` (a new hook in the crime system) tells the cops when a crime begins. Cops within
`crimeResponseRange` (64) head to the scene. Between fights, a cop near a running crime keeps walking to the nearest
crook.

**Patrol.** In work hours an idle cop walks to random street spots within `patrolRadius` (32) of its desk.

**Trades.** Bread and beef, a shield, an iron sword, chainmail, a crossbow and a spyglass. At Master: the Police
Scanner (starts a Syndicate Bust) and the Daily Bulletin.

### Repair Worker (`projecthero:repair_worker`)

A hi-vis yellow vest with reflective stripes, a grey work shirt, jeans and a yellow hard hat. Outside a City it only
trades building blocks: stone bricks, planks, glass, bricks, scaffolding, polished andesite, lanterns, smooth quartz,
cut copper, deepslate tiles and tuff bricks. It buys cobblestone and iron.

### Doctor (a cleric with its job site in the hospital)

- **Ward.** Every 2 s, every player and villager inside a hospital piece within 48 blocks of the doctor's job site gets
  Regeneration II (`wardRegenAmplifier`) for 5 s.
- **Splash.** Once every `splashCooldownTicks` (1200), a doctor throws a Healing splash potion at the most hurt player
  or villager within 8 blocks that it can see. Creative players don't count.
- **Paid heal.** Sneak and use a doctor while holding `healCostEmeralds` (2) emeralds for a full heal. It also clears
  poison, wither and hunger and adds 10 s of Regeneration I. Normal use opens the trades as usual.

### Firefighter (a leatherworker with its job site in the fire station)

Every fire block that appears inside a City's bounds goes on the fire list. Every 10 ticks a firefighter picks the
nearest fire within `responseRange` (128) of its station and walks to it. Within `extinguishRadius + 1.5` blocks it
puts out one fire within 3 blocks of the target every `ticksPerFire` (10), with an extinguish sound and splash and
cloud particles. A fire it hasn't reached after 30 s, within 16 blocks, it douses from where it is. Firefighters
answer street-crime arson too.

### The bank (`safe_deposit`, `bank/BankAccounts`)

- **One account per player.** It is stored in the Overworld (`projecthero_bank`). Every Safe Deposit opens the same
  account, so the block itself holds nothing. Breaking it, or a repair rebuilding it, can never lose or duplicate
  anyone's emeralds.
- **The vault.** 27 slots. Only emeralds and emerald blocks fit, through slot clicks and shift-clicks alike. The block
  has no block entity, so hoppers can't reach it.
- **The screen.** The 3-row chest frame. The title bar shows the vault's emerald value and the interest earned out of
  the cap: "195 em  +9/64".
- **Interest.** `interestPercentPerDay` (1%) of the emerald value per in-game day. It is worked out when the owner next
  opens a vault, day by day, so it compounds. Fractions carry over. The whole emeralds go into the vault; if there is
  no room, they wait in the books. Interest stops at `interestCapEmeralds` (64) emeralds per account, ever.
- **Heist hook.** `BankAccounts.addListener` sees every vault opening (player, block, account).
  `takeForHeist(player, maxValue)` removes emeralds from an account, emeralds first and then blocks. A future
  bank-heist event can use both. Nothing calls them yet.
- **Recipe.** Eight iron ingots around an emerald block.

### The Daily Bulletin (`daily_bulletin`)

Librarians whose job site is in the Daily Bulletin building always sell it, for `priceEmeralds` (1). It is an extra
trade, added every 10 s if missing. Master cops sell it too.

Reading it opens the vanilla book screen. The server fills `WRITTEN_BOOK_CONTENT` and sends `ClientboundOpenBookPacket`.
The paper is reprinted every time it is opened, and has two kinds of page:

- **Page 1:** the "THE DAILY BULLETIN" masthead, "Day N edition", and a count of the stories.
- **Then:** three stories a page, nearest first, each with "~190 m E" directions from where it was read. A story is
  one of these:
  - every running crime ("CRIME: Arson at Hollowgate")
  - every running world event from `EventManager` ("ALERT: ...")
  - the nearest vanilla raid
  
  Only those within `reportRange` (1024) are listed.

The Precinct Desk reads out the same reports in chat.

### Commands (op)

| Command | What it does |
|---|---|
| `/projecthero city status` | Shows the City you stand in: pieces, damaged blocks and areas, renovated cells, workers and the running trip. |
| `/projecthero city repair` | Starts a trip now over everything queued, ignoring the delay and the time of day. With no worker, it fills in on its own. |
| `/projecthero city sweep` | Diffs your chunk against the blueprint now. |
| `/projecthero city optout <radius>` / `reset <radius>` | Marks or clears renovated cells around you. |

Gamerule `projectheroCityRepair` (default true) turns repairs, and damage recording, off.

### Config: `config/projecthero_city.json` (`CityConfig`, VersionedConfig v1)

| Section | Keys (defaults) |
|---|---|
| `repair` (the server's) | `enabled` true, `repairDelayTicks` 12000, `blocksPerTrip` 512, `ticksPerBlock` 10, `workersPerCity` 2, `hireDelayTicks` 24000, `reachTimeoutTicks` 600, `selfRepairAfterTicks` 24000, `selfRepairTicksPerBlock` 40, `noDropTicks` 24000, `renovationWindowTicks` 2400, `sweepIntervalTicks` 40, `sweepChunkEveryTicks` 48000, `repairInsideSpawnProtection` false, `workHoursOnly` true |
| `cops` | `maxHealth` 40, `armor` 8, `attackDamage` 6, `attackCooldownTicks` 20, `attackReach` 2.4, `monsterRange` 12, `patrolRadius` 32, `crimeResponseRange` 64, `crimeEngageDelaySeconds` 20, `crookDamageMultiplier` 0.5, `fleeHealthFraction` 0.25, `regenPerSecond` 0.5 |
| `doctor` | `splashRange` 8, `splashCooldownTicks` 1200, `wardRegenAmplifier` 1, `healCostEmeralds` 2 |
| `firefighter` | `extinguishRadius` 3, `ticksPerFire` 10, `responseRange` 128 |
| `bank` (the server's) | `interestEnabled` true, `interestPercentPerDay` 1.0, `interestCapEmeralds` 64 |
| `bulletin` | `reportRange` 1024, `priceEmeralds` 1 |

### Shared-file touches

- `ProjectHeroMod`: `CityConfig.load()` and `City.initialize()`.
- `ProjectHeroCommand`: the `city` subtree.
- `ServerStateReset`: `City.clearSessionState()`.
- Crime: `Crimes.addListener` / `CrimeListener`, and `CrimeEvent.onTick` fires it once a crime has begun.
- `HeroPackGuide`: chapter `CH_CITY = 37`, "City Services", in the World section. The power chapters now start at 38,
  so `HeroPackGameTests` expects 38 framing chapters.
- Mixin lists, lang, `#minecraft:acquirable_job_site`, `mineable/pickaxe` (vault) and `mineable/axe` (desk and bench).

### Tests: `CityV0162GameTests`

Every test builds its own City. It places blocks, captures them into a template with `fillFromWorld`, wraps it in a real
`PoolElementStructurePiece`, and registers it with `CityLocator.register`.

| Test | Batch | Checks |
|---|---|---|
| `cityConfigDefaults` | default | Defaults, the stamp, and that the server's switch survives a load. |
| `cityBlueprintResolvesPlacedPieces` | default | A piece placed with each of the four rotations matches the blueprint block for block, rotated stairs and chest included. |
| `cityRepairWorkerRebuildsButNeverOverwrites` | `city_repair` | Broken and burnt slots are queued. A player's block opts its cell out. A Repair Worker rebuilds the queued slots and leaves the player's block alone. Rebuilt blocks drop nothing. The worker gets its AI back. |
| `cityRepairClimbsScaffoldingForHighBlocks` | `city_repair` | The top of a 9-high pillar is rebuilt from a scaffold, and no scaffolding is left afterwards. |
| `cityDepotHiresAWorker` | `city_repair` | A free Repair Bench gets a new Repair Worker with that job site. A bench takes one worker. |
| `cityCopClaimsThePrecinctDesk` | `city_cop` | An unemployed villager claims the desk and becomes a 40-health City Cop. |
| `cityCopFightsZombiesNotPlayers` | `city_cop` | The cop hits a zombie and never targets or hits a survival player, until that player hits it. |
| `cityVaultDepositWithdrawAndInterest` | default | 100 emeralds pay 1, then 2 over two days; the 64 cap holds; dirt is refused; shift-click withdraws and deposits; the block opens the menu. |
| `cityDoctorHeals` | default | A cleric in the hospital is a doctor, and a hurt villager in the ward gets Regeneration and heals. |
| `cityFirefighterPutsOutFires` | default | A leatherworker in the fire station puts out a netherrack fire. |
| `cityBulletinListsActiveCrime` | `city_bulletin` | A running arson is listed with its distance and direction. Reading the item fills in its pages. A newspaper-office librarian sells it. |

### Gotchas

- **A villager's brain is built for its profession.** After `setVillagerData(...setProfession(x))`, call
  `refreshBrain(level)`. If you don't, `ValidateNearbyPoi` still checks against the old profession's job site and
  erases `JOB_SITE` every tick. Vanilla's `AssignProfessionFromJobSite` does the same.
- **The gametest mock player always counts as creative** (`isCreative()` is overridden). The cop test builds a real
  survival `ServerPlayer` on an embedded connection.
- **A template is no blueprint** for terrain-matching pieces, for multi-palette templates, or for slots a processor
  emptied. Hence the null expectations, and the processor skip in the sweep.

### Not done yet

- The 6-tick "ghost block" scale-in, the "build mode" for the construction site, and claim-mod protection. Only
  vanilla spawn protection is respected.
- The Doctor "injured villagers walk to the hospital" behaviour.
- The bank heist itself (only the hook exists).
