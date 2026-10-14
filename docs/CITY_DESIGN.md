# City Structure: Design Document (preview, not built yet)

> Status: **design preview, waiting for the user's approval.** Nothing in this document is registered in
> the mod yet. There is no worldgen, no structure set and no new villager. In-game screenshots of every
> building, a city block, and a "village edge to downtown" transition (with real vanilla plains houses)
> are in `scratchpad/city_preview/`. They were built by a temporary client harness
> (`scratchpad/V0162CityPreviewHarness.java.txt` + `scratchpad/CityPreviewBuilder.java.txt`), and those
> builder routines are what we'd export the NBT templates from later. The camera mixin is `scratchpad/V0162CityCameraMixin.java.txt`.

## 1. Concept: a village that grew up

A **City** is a very large, very rare village that kept growing. It should feel like vanilla Minecraft
took a normal plains village and let it develop over a few centuries:

* **At the edge it *is* a normal village.** Real vanilla plains houses (from the `minecraft:village/...`
  pools), dirt paths, the plains lamp posts, a well with a bell.
* **Further in it becomes a town.** Cobblestone/andesite lanes lined with oak-framed **townhouse rows**,
  built from the same blocks as the village houses (cobblestone plinth, oak-log frame, planks and white
  terracotta, spruce/dark-oak gable roofs), plus the market square and the park.
* **In the middle it is downtown.** Stone-brick, brick, quartz and copper towers on proper avenues
  (asphalt, curbs, sidewalks, streetlights): office skyscraper, clock tower, bank, police, hospital, the
  Daily Bulletin.

It is a vanilla village in every way that matters: villagers live in its beds, work at its job sites,
gather at its bells, are guarded by its iron golems, and it gets raided like any other village. On top of
that it adds what a hero needs: crime to fight, a bank to protect, a hospital to heal in, towers to
swing between, and **Repair Workers** who come out and rebuild whatever a fight destroys.

**Design rule: no useless buildings.** Every building has (a) a vanilla villager use (job site, beds or a
meeting point) that villagers actually use on their normal daily schedule, and (b) something the player
gets from it. Section 4 lists this for each building.

## 2. Size, rings and roads

* **Footprint:** about **240 x 240 blocks** (radius ~120). That fits the jigsaw
  `max_distance_from_center` cap of 128.
* **Three rings, each one denser than the last:**

| Ring | Radius (approx.) | Streets | Buildings |
|---|---|---|---|
| **Village ring** | 85-120 | vanilla `minecraft:village/<biome>/streets` pieces (dirt path) | **vanilla village houses** from `minecraft:village/<biome>/houses`, farms, animal pens, a well/bell meeting point |
| **Town ring** | 45-85 | **lanes**: 5 wide, mixed cobblestone/andesite/stone/gravel, dirt-path shoulders, plains-style lamp posts (oak fence + lantern) | townhouse rows, the market square + mall, the park, the fire station, the Maintenance Depot, the construction site, the walk-ups |
| **Downtown core** | 0-45 | **avenues**: 7-wide asphalt with a dashed centre line, curbs, 3-wide sidewalks, a streetlight every 12 blocks, crosswalks | apartment tower, office skyscraper, clock tower, bank, police precinct, hospital, Daily Bulletin, train station |

* **Roads connect seamlessly.** A road never just stops where one style ends: it **blends** over 8-10
  blocks. Dirt path turns into cobble lane (dirt path and coarse dirt mixed with cobblestone and
  andesite), and lane turns into avenue (cobble mixed with gray concrete and polished andesite, then curbs
  and sidewalks start). The outer lane pieces carry the **same jigsaw connector names as vanilla village
  streets** (`minecraft:street`), so vanilla street and house pieces attach to them like they would in a
  normal village. That is how vanilla houses end up "sprinkled" along the edge.
* **Terrain:** the start piece takes its Y from the heightmap and **rejects** sites where the 240-block
  square varies by more than ~8 blocks. Downtown pieces use `terrain_adaptation: beard_thin` (retaining
  foundations). The village ring keeps vanilla's terrain-matching street behaviour, so the edges follow
  hills like a normal village does.

### Vanilla-friendly palette
To avoid style clash, the city uses **the blocks villages already use**, and saves the "modern" ones for
the core:
* **Village and town rings:** cobblestone, oak/spruce/dark-oak logs and planks, white terracotta,
  glass panes, lanterns, dirt path. These are exactly the plains village materials.
* **Downtown:** stone bricks, bricks, smooth stone, polished andesite, smooth quartz, copper (weathering
  copper is already vanilla's "old city" look), deepslate tile, tuff brick. **Concrete is used only for
  road markings, signage and accents**, never for whole facades, except the hospital's white walls.
* **Biome styles** are palette swaps of the same templates: **plains** first; later **desert** (sandstone,
  terracotta, the desert-house pool at the edge), **taiga** (spruce, cobble, mossy stone) and **snowy**
  (spruce, packed ice trim, snowy house pool). The vanilla house pool at the edge always matches the
  biome.

## 3. Building catalogue (16 types + street kit)

Previews are numbered to match `scratchpad/city_preview/`. Every building faces its street.

| # | Building | Ring | Footprint / height | Palette | Inside |
|---|---|---|---|---|---|
| 01 | **Apartment Tower** | core | 17 x 13 / 31 (+ roof hut) | bricks, stone-brick sills and cornices, polished-andesite shopfront, red/white awning | deli on the ground floor (counter, shelves, **smoker**), 6 floors of flats with **12 beds**, balconies, an iron fire escape |
| 02 | **Office Skyscraper** | core | 16 x 16, setback 10 x 10 / roof 50, mast 58 | light-blue glass curtain wall, quartz mullions, polished-deepslate spandrels | lobby with diorite/deepslate floor, reception, elevator core, **cartography tables and lecterns** ("map office"), sea-lantern lit floors |
| 03 | **Clock Tower** | core | 9 x 9 (clock stage 11 x 11) / 46 | stone bricks, deepslate buttresses, four quartz/gold/black clock faces, oxidized copper spire | open belfry with a **bell**, lantern-lit shaft |
| 04 | **Police Precinct** | core | 26 x 16 / 15 (+ POLICE sign to 21) | stone bricks, polished-andesite piers, blue band, soul lanterns | front desk (**lectern**, becomes the Precinct Desk), **armoury: blast furnace, grindstone, smithing table**, waiting benches, **5 holding cells with bunks (beds)**, two cruisers outside |
| 05 | **City Bank** | core | 19 x 19 / 27 | smooth quartz, quartz columns, gold BANK frieze, deepslate-tile gable roof | marble checker floor, teller line with **lecterns**, a round vault door, an **emerald-and-gold vault** |
| 06 | **Hospital** | core | 23 x 17 (+ ambulance canopy) / 21 (+ cross sign to 30) | white/light-gray concrete, light-blue ribbon windows, quartz ledges | ward with **7 beds**, nurses' counter with **brewing stands** (pharmacy), lit floors, rooftop helipad |
| 07 | **Fire Station** | town | 17 x 17 hall + 7 x 7 hose tower / 12 hall, 38 tower | red brick, calcite trim, red bay doors | two apparatus bays (fire engine), **water cauldrons** (job site), **5 crew bunks** upstairs, F-I-R-E lettered hose tower with lookout |
| 08 | **Market Square + Mall** | town | mall 31 x 14 + plaza 31 x 18 / 19 | white terracotta, cut copper, glass shopfronts, awnings | 5 shops; plaza with **6 stalls holding smoker, composter, cartography table, fletching table, loom, barrel**; the **meeting-point bell** |
| 09 | **Central Park** | town | 33 x 33 / 12 (statue) | grass, dirt-path and stone-brick paths, azalea hedges, oak/cherry/birch | fountain with four cascades, copper hero statue, **bell pavilion (meeting point)**, benches, flower beds |
| 10 | **Train Station** | core edge | 31 x 12 facade + towers + 26-deep shed / 22 frieze, 32 towers | smooth/cut/chiseled sandstone, great arched window, weathered copper | concourse with **cartography tables**, two tracks, island platform, glass train shed, parked train |
| 11 | **Construction Site** | town | 25 x 25 / 48 (crane) | spruce hoarding, concrete frame, deepslate steel, spruce scaffold, yellow crane | half-built floors, site office with **stonecutter + smithing table**, sand/gravel/brick stock |
| 12 | **The Daily Bulletin** | core | 21 x 15, two setbacks / 29 roof, 41 sign | tuff bricks, chiseled tuff, gray glass, copper portal | newsroom and archive with **lecterns** and bookshelves on several floors, red DAILY BULLETIN sign |
| 13 | **Brownstone Walk-up + rooftop set piece** | town | 23 x 13 / 17 roof, 33 billboard | mud bricks, packed mud, spruce shutters, dark-oak cornice | 4 floors with **8 beds**, a **rooftop garden (farmland, wheat, carrots, composter)**, timber water tower, HERO COLA billboard |
| 14 | **Warehouse + Docks** | edge (on water) | 27 x 15 + quay/basin / 18 | spruce planks, dark-oak frame, stone-brick plinth, deepslate-tile roof | **barrels** (fisherman) and **smokers** (butcher), hay and barrel stacks; quay, pier, tug, shipping containers |
| 15 | **Maintenance Depot** | town | 21 x 13 + yard / 15 | polished tuff, tuff-brick piers, yellow/black hazard band, DEPOT sign | workshop (smithing table, anvil, stonecutter, crafting table, grindstone), material stock, **Repair Bench** (new job site), a bunk, maintenance truck |
| 16 | **Townhouse Row** | town | 7 x 9 per unit, rows of 3-6 / 12 | **vanilla plains palette**: cobblestone ground floor, oak-log frame, oak/spruce/white-terracotta upper walls, gable roofs | each unit: **2 beds**, one **job site** (composter, loom, fletching table, barrel, lectern or cartography table), crafting table, window box, lantern |
| (vanilla) | **Village houses** | village ring | vanilla sizes | vanilla | vanilla beds and job sites, unchanged |

**Street kit:** avenue / lane / path segments and blends, curb and sidewalk, avenue streetlights, plains
lamp posts, crosswalks, planter trees, benches, hydrants, parked cars.

## 4. What every building is for

Vanilla villagers follow a daily **schedule**: *wander/wake* (0-2000), **work** at their job site
(2000-9000, when they also restock trades), **meet** at a bell (9000-11000), then wander and **sleep** in
a claimed bed (12000+). The city puts each of these POIs where it makes sense, so the normal schedule
fills the city with life: workers head into the towers in the morning, everyone drifts to the market
square and the park in the afternoon, and they go home to the apartments and townhouses at night.

| Building | Profession / POI it serves | What villagers do there | What the player gets |
|---|---|---|---|
| **Apartment Tower** | 12 **beds** (homes); **smoker** = butcher (deli) | residents sleep here; the butcher works the deli counter | housing that grows the population; butcher trades; a tall climb and swing anchor |
| **Office Skyscraper** | **cartography tables** = cartographers; **lecterns** = librarians | the "business district": map-makers and clerks go to work in the lobby offices | **explorer maps, including a map to the nearest other City**; enchanted-book trades; the tallest web-swing and flight launch point |
| **Clock Tower** | **bell** (meeting point) | villagers gather in the clock square in the afternoon; the bell rings the alarm during raids | the clock faces show the real time of day (a daylight-sensor driven hand is a stretch goal); a raid alarm you can hear across downtown |
| **Police Precinct** | **Precinct Desk** (new) = **City Cop**; blast furnace = armorer; grindstone = weaponsmith; cell bunks = beds | cops clock in at the desk, patrol nearby streets and respond to **crime events**; armorer and weaponsmith work in the armoury | cops fight the criminals in crime events alongside you; armour and weapon trades; crime reports (the desk tells you where the active crime is) |
| **City Bank** | **lecterns** = clerks (librarians); new **Safe Deposit** block | clerks work the teller line | **emerald vault with interest:** deposit emeralds in a safe deposit box (one per player) and get **1% per in-game day, capped at +64**; the vault is the target of a future **bank-heist event** |
| **Hospital** | **brewing stands** = clerics (doctors); 7 ward beds | doctors work the pharmacy; injured villagers come here to recover | **healing:** stand in the ward or sleep in a ward bed and you get Regeneration II; doctors throw Healing potions at hurt players and villagers nearby. Cleric trades |
| **Fire Station** | **water cauldrons** = leatherworkers (firefighters); 5 crew bunks | firefighters work in the bays and sleep upstairs | **fires get put out:** when fire burns inside the city, a firefighter runs to it and puts it out (a water splash per fire block), so it doesn't burn the city down before the Repair Workers come. Leatherworker trades |
| **Market Square + Mall** | 6 stall job sites (butcher, farmer, cartographer, fletcher, shepherd, fisherman); **bell** = the main meeting point | traders stand at their stalls during work hours; everyone gathers at the bell in the afternoon | **trading hub:** six professions in one plaza. The bell is the village centre for raids and golem spawns |
| **Central Park** | **bell pavilion** (meeting point); open green | villagers gather and socialise here; kids play; golems patrol | the city's **gathering point** (gossip, breeding, golem spawning); a calm safe zone and a statue to admire |
| **Train Station** | **cartography tables** = cartographers | station staff work the concourse | City maps; later a fast-travel line between discovered Cities |
| **Construction Site** | **stonecutter** = mason; **smithing table** = toolsmith | masons and toolsmiths work at the site office | mason and toolsmith trades; **the building slowly gets finished:** the Repair Worker system runs "build mode" on it, one storey every few in-game days, until it turns into an office tower; a great swinging playground |
| **Daily Bulletin** | **lecterns** = librarians (archivists) | archivists work the newsroom | **The Daily Bulletin newspaper** (librarian trade, 1 emerald): marks the nearest active crime event, boss or raid on a map; enchanted books |
| **Walk-up + rooftop** | 8 **beds**; rooftop **composter** = farmer | residents sleep here; the farmer tends the rooftop garden | housing; a city farmer with real crops (bread trades); the water tower is a fire-fighting water source |
| **Warehouse + Docks** | **barrels** = fishermen; **smokers** = butchers | dock workers fish and smoke fish | fisherman and butcher trades; boats; containers as hiding places for crime events |
| **Maintenance Depot** | **Repair Bench** (new) = **Repair Worker**; a bunk | Repair Workers start and end every repair trip here | **the city repairs itself** (section 6); Repair Worker trades (building blocks) |
| **Townhouse Row** | 2 **beds** + 1 job site per unit | families live and work at home (farmer, shepherd, fletcher, fisherman, librarian, cartographer) | most of the city's housing; the visual bridge between village and city |
| **Vanilla village houses** | vanilla beds and job sites | exactly as in any village | familiar, and they make the city read as "a village that grew" |

**Population target:** about **70 beds** (vanilla edge houses ~18, townhouses ~24, apartments 12,
walk-up 8, hospital and cells as overflow) and **35-45 job sites**, so a city settles at about 40-60
working villagers plus children.

## 5. New city villagers

### Repair Worker (new profession: `projecthero:repair_worker`)
* **Job site:** a new **Repair Bench** block (a work table with tools; crafted from a smithing table and
  iron ingots), with its own POI type. It works in any village; outside a City the Repair Worker just
  trades building blocks.
* **Look:** hi-vis yellow vest and hard hat (profession texture overlay).
* **Schedule:** the work period is spent on repair trips (section 6); otherwise standard (meet, sleep in
  the depot bunk). During a raid they flee to the depot.
* **Count:** 2 per City. If both die, the depot "hires" a replacement after an in-game day, as long as
  its Repair Bench and bed still exist.

### City Cop (new profession: `projecthero:city_cop`)
* **Job site:** a **Precinct Desk** (lectern-style block in the precinct). Blue uniform overlay.
* **Behaviour:** they patrol within 32 blocks of their desk during work hours. Unlike other villagers
  they **attack hostile mobs** within 12 blocks (melee, 6 damage, slow baton swing) and only flee at low
  health.
* **Crime-events tie-in:** when a crime event starts inside the City, cops within 64 blocks move to it and
  fight the criminals. They're deliberately weak, so the hero is still needed.
* 3 per City, sleeping in the precinct bunks.

### Doctor and Firefighter (no new professions; vanilla variants)
* A **cleric whose job site is in the hospital** gets the Doctor behaviour: a Healing splash on
  injured players and villagers within 8 blocks of the hospital, once per 60 s, plus the ward
  Regeneration aura.
* A **leatherworker whose job site is in the fire station** gets the Firefighter behaviour: when a fire
  block appears inside the City footprint, they path to it and extinguish it (fire blocks within
  3 blocks, one every 10 ticks).
* Both are detected from **job-site POI + City structure piece**, so vanilla trades are untouched.

## 6. The repair system

### Blueprint: where "what should be here" comes from
* The City's **structure start** already stores every placed piece (template id, position, rotation,
  mirror) in the chunk data. For any `BlockPos` inside the City we find the piece covering it, load its
  `StructureTemplate` (cached) and transform it back to get the **expected block state**. That gives a
  free blueprint, with no second copy of the City stored. Roads are deterministic too.
* `CitySavedData` (per dimension) holds each City's id, bounding box, depot position, Repair Worker
  UUIDs, its **damage queue**, and an **opt-out mask**.

### Detecting damage
1. **Event tracking (the main route, cheap):** a server-side hook on `Level.setBlock`, filtered with a
   chunk-to-City lookup, records any change where the **old state matched the blueprint and the new state
   doesn't** (player mining, explosions, Titan/Hulk smashes, fire, endermen) into the damage queue, with
   a timestamp and the cause.
2. **Audit sweep (catches anything missed):** a City chunk that loads and hasn't been checked for
   2 in-game days is diffed against the blueprint in the background (at most 8 sections per tick).

### The repair cycle
1. A damaged area (a 16 x 16 column) becomes **due** once `repairDelayTicks` have passed since its
   **latest** damage. The default is **12000 ticks = 10 in-game minutes**, configurable from 1200 to
   72000. Fighting pushes this back, so nothing is rebuilt mid-battle.
2. During work hours the depot gives the oldest due area to an idle Repair Worker. It **walks out of the
   depot** (normal villager pathfinding) carrying a toolbox, using a `RepairJob` brain behaviour.
3. On arrival (within 6 blocks) it rebuilds missing blocks **one every 10 ticks, from the bottom up**:
   foundations, then walls, then glass and decoration. Each block gets an arm swing, the place sound,
   particles, and a 6-tick "ghost block" scale-in effect before the real block is set.
4. Blocks out of reach: the worker builds a **temporary scaffold column** (spruce slab on fence), climbs
   it, and takes it down afterwards. If it can't get within reach after 30 s, it falls back to
   **remote repair** (blocks fill in at the same pace while it stands at the base), so jobs never get
   stuck.
5. Up to **512 blocks per trip**, then it walks home. Big disasters take several trips.
6. **At night or during a raid**, or with hostile mobs within 16 blocks, it finishes or aborts the trip and
   goes home.

### Safety rules
* **Never overwrite player work.** A damaged position is only repaired while it is still **air, fluid or
  fire** (or loose gravel/sand that wasn't in the blueprint). If anything else now occupies it, the
  position is dropped and added to the **opt-out mask**.
* **Player renovations are respected.** If a player removes blueprint blocks and places their own blocks
  in the same 4 x 4 x 4 cell within 2 minutes, that cell is marked as edited and never touched again.
  `/projecthero city optout <area>` and `/projecthero city reset <area>` (ops) cover the rest.
* **Claims:** positions protected by a claim mod (soft-dependency hook) or by spawn protection are never
  repaired. There's a gamerule `projecthero:cityRepair` (default on) and a config switch.
* **No loot duplication.** Containers and every block with a block entity are rebuilt **empty**; loot
  tables are never re-rolled. The bank vault's emerald and gold blocks are rebuilt as **stone-brick
  filler** ("cleaned out"), and the vault's contents only refill through the Safe Deposit/heist systems.
* **No resource farming.** A repaired block **drops nothing** if it's broken again within 1 in-game
  day.
* **Entities** (villagers, golems, item frames) are never respawned by repairs; vanilla breeding and
  golem spawning handle the population.

### Performance
The `setBlock` hook costs one hash lookup outside Cities. Only **one repair job per City** runs at a time
(2 blocks/s). Blueprint templates are cached, and the sweep is rate-limited. All caches are registered
with `ServerStateReset` (see the static-state leak note).

## 7. Rarity and spawning
* **Biomes (first version):** plains, sunflower plains, meadow, savanna (`#projecthero:city_biomes`).
  Desert, taiga and snowy styles come later as palette swaps, each paired with that biome's vanilla
  house pool.
* **Structure set:** `random_spread`, **spacing 160 chunks, separation 100 chunks**, unique salt. That's
  about one City per 2560 x 2560-block region, rarer than woodland mansions (spacing 80).
* **Exclusion zone** against `minecraft:villages` (10 chunks), so no normal village grows half into a
  City. The City's own edge ring *is* the village.
* **Finding one:** cartographers in normal villages sell a **City explorer map**; `/locate structure
  projecthero:city` works; a Guidebook page.
* **Config** (`CityConfig`, VersionedConfig): enabled, spacing, separation, repairDelayTicks,
  repairBlocksPerTrip, repairWorkers, allowRepairOverClaims, bankInterestPercent.

## 8. Mobs and raids
* **Spawning:** streets are lantern-lit every 12-14 blocks, so few hostiles spawn outdoors. Big interiors
  get light blocks where needed (to be found in playtest).
* **Raids:** Bad Omen raids work as in any village, centred on the market bell. Cops fight raiders,
  Repair Workers and Doctors take cover, and repairs wait until the raid has been over for the delay.
* **Mod events:** Cities are the natural stage for the Zombie Raid, Apokolips Invasion, Syndicate raids,
  Titan/Behemoth fights and the **crime events** (which prefer a City within range of a player). The
  damage they cause is exactly what the Repair Workers fix afterwards.
* **Iron golems:** vanilla golem spawning near the meeting points (park and market), plus 3 placed at
  generation.

## 9. Performance and generation
* **Jigsaw, chunk by chunk:** no template is larger than **48 x 48 x 64**. Each piece covers at most
  3 x 3 chunks and is clipped to the chunk being generated.
* **Tall buildings are split** into a base and a tower piece (stacked through a vertical connector), so
  every NBT file stays small.
* **Piece count:** about 16 downtown/town lots, about 20 vanilla houses at the edge, about 50 street
  pieces. Jigsaw `size` 12 is enough because the grid is shallow.
* **Lighting is in the templates** (lanterns, sea lanterns, glowstone), so it's lit at generation at no
  extra cost.

## 10. Implementation plan (after approval)

### 10.1 Jigsaw structure and pools
* `worldgen/structure/city.json`: `minecraft:jigsaw`, `start_pool: projecthero:city/centers`, `size: 12`,
  `max_distance_from_center: 124`, `project_start_to_heightmap: WORLD_SURFACE_WG`,
  `terrain_adaptation: beard_thin`, `biomes: #projecthero:city_biomes`, `step: surface_structures`.
* `structure_set/city.json`: random spread with spacing 160, separation 100, and an exclusion zone against
  villages.
* **Template pools:**
  * `city/centers`: the downtown core intersection (4 variants);
  * `city/avenues`, `city/lanes`, and the blend pieces `city/blend_avenue_lane` and
    `city/blend_lane_path`. Lane pieces end in connectors named `minecraft:street`, so
    **`minecraft:village/plains/streets` pieces attach to them directly**;
  * `city/lots/core`, `city/lots/town`, `city/lots/special` (park, market, depot, docks, station);
  * **the vanilla pools themselves:** the outer street ends use `minecraft:village/plains/houses` (and
    biome equivalents) as their house pool, so the edge houses are true vanilla houses, villagers and
    all;
  * `city/decor` (lamps, benches, hydrants, cars, planters) and `city/villagers` (villager, cop and
    Repair Worker entities, like vanilla's `village/plains/villagers`).
* **Guaranteed buildings:** the depot, market, police, hospital and park are reached through
  single-element pools off the centre piece's own connectors, so every City has them.

### 10.2 Building the NBT pieces
1. The builder routines (`scratchpad/CityPreviewBuilder.java.txt`) already place each building in local
   coordinates. A dev-only command, `/projecthero dev citybuild <type> <variant>`, runs a routine and
   **saves the bounding box with `StructureTemplate.fillFromWorld` + `StructureTemplateManager.save`**
   into `data/projecthero/structure/city/...`.
2. The routine also places the jigsaw blocks (street-side connector, plus vertical connectors on tall
   buildings), so exports can be repeated; re-run the command to regenerate every template.
3. Palette variants (brick / mud brick / deepslate brick; glass colours; biome styles) are the same
   routine with a palette object passed in.
4. Optional hand polish in a creative world afterwards, re-saved with a structure block.

### 10.3 Code (new package `com.projecthero.mod.city`)
* `CityRegistry` / `CitySavedData`, `CityBlueprint` (piece lookup + template cache), `CityDamageTracker`
  (`setBlock` hook + audit sweep).
* `RepairWorker` profession + `RepairBenchBlock` + POI + `RepairJobBehavior` (walk, scaffold, build,
  return; plus "build mode" for the construction site).
* `CityCop` profession + `PrecinctDeskBlock` + POI + `CopPatrolBehavior` / `CopDefendBehavior` + the
  crime-event hook.
* `DoctorBehavior` (cleric at the hospital), `FirefighterBehavior` (leatherworker at the fire station),
  the hospital ward Regeneration aura.
* `SafeDepositBlock` + per-player vault data (bank interest); `DailyBulletinItem` (event-marking map).
* `CityConfig`, the `projecthero:cityRepair` gamerule, `/projecthero city` commands, Guidebook page and
  lang, the cartographer City-map trade.
* Client: ghost-block renderer, profession textures, block models.
* `ServerStateReset` hooks for every static cache.

### 10.4 Effort estimate

| Work | Estimate |
|---|---|
| Builder polish + palette variants + jigsaw connectors + export command, then about 35 templates | 2-3 days |
| Street/lane/blend pieces, pools wired to the vanilla village pools, structure + set + biome tags, terrain check, test worlds | 2-3 days |
| Repair Worker profession, Repair Bench, POI, trades, textures | 1.5 days |
| Repair system (blueprint lookup, damage tracker, queue, RepairJob, scaffold, ghost blocks, safety rules, construction "build mode") | 3-4 days |
| City Cop profession + patrol/defend + crime-event hook | 1.5 days |
| Doctor, Firefighter behaviours, hospital aura, Safe Deposit + interest, Daily Bulletin item | 2 days |
| Config, gamerule, commands, Guidebook, lang, City map trade | 1 day |
| GameTests (blueprint lookup, never-overwrite, no-loot, interest cap) + playtest + performance | 2 days |
| **Total** | **about 15-18 working days**, best shipped in 3 releases: **(1)** the City structure working as a vanilla village (all buildings, houses, POIs); **(2)** Repair Workers + the repair system + Firefighters; **(3)** City Cops + crime events, Doctor healing, Bank interest/heist, the Daily Bulletin |

## 11. Open questions for the user
1. Is **240 x 240** big enough, or do you want a bigger multi-start "metropolis" (more effort)?
2. Is a **10 in-game minute** repair delay the right default?
3. **City Cops:** fighting villagers as described, or passive until the crime events land?
4. **Bank interest:** is 1%/day capped at +64 emeralds OK, or would you rather have no interest and
   just a heist target?
5. Do you want the **desert/taiga/snowy** City styles in the first release, or plains/savanna only?
6. Should the construction site really "finish itself" over time (build mode), or stay a permanent set
   piece?

## 12. Preview index (`scratchpad/city_preview/`)
* `01`-`16`: each building (front-left 3/4 view, `_b` = front-right, plus interiors where noted: office lobby, police
  cells, bank vault, hospital ward, depot workshop, park statue and pavilion, station platforms,
  rooftop garden).
* `17`-`21`: a downtown block: two avenues crossing, 13 buildings (street level, intersection, two aerial
  skylines, overhead).
* `22`-`25`: **village edge to downtown**: real vanilla plains houses on a dirt path, which blends into
  a cobble lane with townhouse rows, which blends into the avenue with the towers (aerial, street level
  from the village end, the lane, and looking back from the city).
