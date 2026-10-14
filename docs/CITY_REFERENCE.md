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
