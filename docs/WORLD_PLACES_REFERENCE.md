# Small world places (v0.17.7) — reference

Design: `docs/STRUCTURES_V0177_DESIGN.md` §5 (and the Tier 1 finds part of §2). Builds on the v0.17.5 Tier 1 Finds
(`docs/LOOT_PROGRESSION_REFERENCE.md`, "Tier 1 Finds"): same package, same `FindStructure` / `FindPiece` machinery.

Code: `com.projecthero.mod.loot.finds` — `FindStructure` (one structure class, a `Kind` per place), `FindsMod`
(structure types, piece types, keys), one `*Piece` per place, `FindMobs` (the few loiterers). The Rooftop Stash is added
to Cities by `city/CityStructure.findGenerationPoint`. Tests: `WorldPlacesGameTests`. Data generator:
`scratchpad/v0177/gen_world_places.js` (structure / set / biome tag JSON + the six themed loot tables). Lang:
`scratchpad/v0177/world_places_lang.json` (added key by key with `scratchpad/v0177/lang_add.js`). Harness:
`scratchpad/WorldPlacesHarness.v0177.java.txt`. Screenshots: `docs/screenshots/v0177/world_places/`.

No alarm system, no guard roles: these places are unguarded or lightly guarded. Their mobs carry the tag
`projecthero.find_mob`, never the Tier 2 `projecthero.site_guard` tag. No static state (nothing for `ServerStateReset`).

## The places

| Id | Biomes (tag) | Spacing / separation (chunks) | Salt | Village exclusion | Guards |
|---|---|---|---|---|---|
| `ruined_hero_base` | `#ruined_hero_base_biomes`: hp_surface (forests, taiga, savanna, hills, plains, meadow, snowy plains/taiga, grove, cherry), jungles, dark forest, swamp | 24 / 8 | 1777101 | 3 chunks | none |
| `crashed_parademon_pod` | `#crashed_parademon_pod_biomes`: badlands, savannas, desert, plains, sunflower plains, snowy plains, meadow, ice spikes | 26 / 8 | 1777102 | 3 | 2 Parademons (standard + ranged), leash 10 |
| `abandoned_workshop` | `#abandoned_workshop_biomes`: forests, taiga, plains, sunflower plains, meadow, cherry grove, savanna, swamp, snowy plains | 22 / 7 | 1777103 | 2 | none |
| `smugglers_den` | `#smugglers_den_biomes`: hills, taiga, badlands, jungle, forests, savannas, stony shore, meadow, grove, cherry grove, snowy slopes, plains — but only at the foot of a cliff | 10 / 3 | 1777104 | 2 | 3 Syndicate smugglers (pistol gunman, thug in a hood / mask skin, shotgun gunman), leash 8 |
| `tech_meteor` | `#finds_land_biomes` (the Crashed Drone's) | 20 / 6 | 1777105 | none | none |
| `rooftop_stash` | only in a City (piece of `projecthero:city`, 1-2 per City) | — | — | — | none |

Salts are new and unique among all `projecthero` structure sets (checked by `placesLoadWithUniqueSalts`, which reads the
data files). For comparison the v0.17.5 finds use 10-16 spacing and the old sites 60-120, so the new places sit between
the two: rarer than a find, much commoner than a site.

All five structures: `step: top_layer_modification` (after trees, so nothing grows through them), never on water,
never on a slope steeper than `Kind.maxSlope` across the footprint (pod 6, base 4, workshop 3, meteor 5). The pieces
are code-built (`FindPiece`: local coordinates with the front at local south, rotation / variant / layout from the
position seed, writes clipped to the chunk being generated).

### Smugglers' Den placement

`FindStructure.denPoint`: the four directions are tried (seed-ordered); one is taken when, measured on the noise
heights, the ground rises at least 3 blocks within 5 blocks that way, stays at least 5 up 8 blocks in and 4 up 11 in, 3 up
4 blocks to either side at 8 in (the room), and the ground 3 blocks out in front is within 3 of the entrance and dry.
The piece is then turned so local north points into the cliff, and the face position (`faceZ`, the first column of rock
on the entrance line) and the cliff height (`cliffTop`) are saved in the piece NBT, so every chunk agrees on them.
Layout 1 (side store) is only used when the rock also reaches 8-10 blocks to the right. Any breach in the carved
ceiling is sealed with stone. Tests and the harness call `SmugglersDenPiece.measure(level)` on a live level instead.

### Rooftop Vigilante Stash placement

`CityStructure.findGenerationPoint` wraps the jigsaw's generation stub: after all City pieces are laid out,
`RooftopStashPiece.addTo(builder, seed)` picks up to two flat-roofed buildings (`ROOFS`: apartment tower, brownstone
walk-up, fire station, police precinct, hospital, Daily Bulletin) and adds a piece covering the **one chunk** that holds
the most of the building (so the building is always placed in that chunk first). When it builds, the piece searches
that chunk (inside the building, 2 in from its edges) for the highest 7 x 7 patch where at least 30 of the 49 columns
share one top height at least 5 above the building's foot -- a building box also holds yards, gardens and aprons, which
must never get it -- and builds there; no such patch, no stash. It writes only into air above the roof, and only on
columns flat with it. The City repair blueprint ignores it (it only restores blueprint blocks into air; the stash is all
on cells the blueprint says are air, and it is not a `PoolElementStructurePiece`). Cities generated before v0.17.7 have no stashes.

## Loot

Every place holds a **Tier 1 Hero Vault** (`chests/vault_tier1`: Salvaged Tech, a supply, valuables, Site Locator 35%)
plus a **themed container** with its own table in `data/projecthero/loot_table/chests/finds/` (rolled on first open,
vanilla style, per-position seed):

| Place | Container | Table | Highlights | Site Locator |
|---|---|---|---|---|
| Ruined Hero Base | locker (barrel; chest in the tower) | `ruined_hero_base` | 2-5 Salvaged Tech, a worn dyed-leather costume piece or shield, 1-2 supplies, paper / books / XP | 50% |
| Crashed Parademon Pod | Apokolips tech cache (barrel) | `crashed_parademon_pod` | 3-6 Salvaged Tech, Hero Alloy Scrap 40%, energy cells / stim, redstone, blaze powder, gunpowder | 40% |
| Abandoned Workshop | parts bin (barrel) | `abandoned_workshop` | 4-8 Salvaged Tech always, scrap 25%, repair kit 50%, copper, redstone, pistons, a clock | 20% |
| Smugglers' Den | contraband chest | `smugglers_den` | 2-6 emeralds, gold, gunpowder, arrows, leather, a spyglass or a diamond now and then | 30% |
| Rooftop Stash | vigilante's barrel | `rooftop_stash` | med kits / energy cell, 2-4 Salvaged Tech, arrows, food, spyglass, an ender pearl now and then | 25% |
| Tech Meteor | half-buried scrap barrel | `tech_meteor` | 3-7 Salvaged Tech, scrap 50%, glowstone dust, amethyst, prismarine crystals | none |

## How each is built

| Place | Layouts | Variants | Notes |
|---|---|---|---|
| Ruined Hero Base (half 8) | 0 concrete hideout (roof caved in over the east half, shot-out windows, a fallen dish), 1 brick warehouse lair (beams, a mezzanine with a bunk over the vault, a fallen beam), 2 toppled watchtower (round stump, a ledge and ladder, the fallen top lying in drums beside it) | hero colour: 0 Blue Comet, 1 Red Sentinel, 2 Green Warden (suit dye, banners, rug, trim) | glass costume case (quartz plinth, panes, front smashed, lamp) with an armour stand in dyed leather; case notes (`RuinedHeroBasePiece.notes`, an intro per hero + 3 shared pages about the progression chain); desk with dead monitors, bunk, training target |
| Crashed Parademon Pod (half 8) | 0 upright pod in the crater, 1 pod on its side at the end of a skid trench, 2 twin pods (one shattered in its own small crater) | trim: 0 gilded blackstone, 1 red nether brick, 2 oxidised copper | scorched crater (blackstone / basalt / magma / netherrack), burnt ring, lit campfires for smoke (never spreading fire), blown-off hatch lying in the dirt, red glass ports, a warning lamp |
| Abandoned Workshop (half 8) | 0 timber barn (gabled stair roof with a hole, one door hanging open, the other fallen), 1 brick garage (flat roof, skylight, roll-up door jammed half open, oil-stained drive), 2 copper-roofed lab (tuff on cobble, stepped copper roof, lightning-rod mast) | wood: 0 spruce, 1 oak, 2 dark oak | broken suit frame (armour stand in a damaged iron chestplate and helmet, chains and a gantry beam), workbenches (smithing table, stonecutter, grindstone, chipped anvil, blueprints), a copper tesla coil, dust carpets and cobwebs |
| Smugglers' Den (half 11) | 0 one cave room, 1 a room and a side store (the vault in the store) | entrance: 0 vines + bushes, 1 waterfall (a stone spout at the cliff top, the fall in front of the mouth, a rimmed pool; needs a cliff 5+ high, else vines), 2 boarded mine adit (rails, boards, a KEEP OUT sign) | timber frames down the tunnel, plank walkway, crates, card table with candles, bedrolls, lantern on a chain, unlit campfire |
| Rooftop Stash (half 3) | 0 tarp lean-to with a bedroll, 1 radio post (dish, antenna, desk with a laptop), 2 plank shack with a door | tarp colour: 0 black, 1 purple, 2 red | |
| Tech Meteor (half 7) | 0 one meteor, 1 split meteor (vault in the gap), 2 meteor shower (two small craters) | glow: 0 cyan (sea lantern / prismarine, end rods), 1 green (verdant froglight / lime glass), 2 violet (pearlescent froglight / amethyst, clusters) | basalt / blackstone / crying obsidian lump with glowing veins, crystals on top, scrap strewn on the rim (bars, grates, lit bulbs, chains) |

## Tier 1 finds: new layouts and decoration (v0.17.7)

`FindPiece` now has a `layout` next to `variant` (`seed >>> 29`; saved as NBT `Layout`, absent = 0, so pieces saved
before v0.17.7 keep their old look). `force(variant, layout, rotation)` pins all three for tests and the harness.

| Find | Layout 0 (the v0.17.5 build + decoration pass) | Layout 1 | Layout 2 |
|---|---|---|---|
| Crashed Drone | quad-rotor + a blinking flight recorder and a trailing cable | fixed-wing recon drone in a long skid crater (variants: a wing tip torn off) | heavy cargo hauler broken in two, the vault-pod in its straps between the halves, spilled crates (variant: which rotor still glows) |
| Hidden Cache | 5 x 5 room + supply barrel, candle, crate, bedroll | wide 7 x 5 room with pillars, a bookshelf, barrels, a map table | the way in is a hollow tree stump (the marker) with a ladder down the hollow |
| Abandoned Hero Outpost | one-room ruin + a cold firepit with log seats | plus a lookout tower off the back corner, its top half fallen | a dugout: the room sunk two blocks, banked earth, steps up out of the door, moss on the roof, a radio mast |

All layouts keep the v0.17.5 invariants (the drone's vault door opens onto air, the cache vault is 3-5 below the grass
under a marker on coarse dirt, the outpost has its field notes and 2-4 mobs), so `TierOneFindsGameTests` still holds.

**Supply Drop dressing** (`SupplyDropEntity.decorate`, on landing, random look): 0 the red-and-white parachute draped
behind the crate, 1 parachute + a lit signal flare (campfire) and a lantern, 2 the pallet smashed on landing (slabs and
trapdoors strewn) + parachute. Only into air (or a plant: grass, flowers) over sturdy ground; every block is saved on the entity (NBT `Deco`) and
removed again when the crate is collected (also if the vault was broken).

## Guidebook / JEI

Loot & Progression chapter: a "Small World Places" section after Tier 1 Finds (one line per place,
`projecthero.guide.loot.place.<id>`). Structures chapter: a closing "Small world places" paragraph pointing there. No new
items, so no JEI pages. Structure names: `structure.projecthero.<id>` (used by Locator tooltips if a place is ever added
to a Locator tag; none are — Locators start at Tier 2).

## Tests (`WorldPlacesGameTests`, each in its own batch)

- every layout of the base, pod, workshop and meteor builds on a plinth with its tier-1 vault (door onto air), its
  themed container with the right loot table, and its mobs / suit stand / notes;
- the den carves into a test cliff (measured face, room carved, rock over it, 3 smugglers, the waterfall's spout and pool);
- the rooftop stash finds a roof with a vent on it and stands on it; a whole City assembled in memory gets 1-2 stashes,
  each inside one chunk;
- every Tier 1 find layout (and drone variant) holds its vault with the v0.17.5 invariants;
- the Supply Drop dressing is placed for all three looks and packed away with the crate;
- the five structures load with biomes, and every projecthero structure-set salt is unique.
