# Sky Fortress (v0.17.7, Tier 4 dungeon) — reference

Design: `docs/STRUCTURES_V0177_DESIGN.md` §3 "Sky Fortress". Alarm framework: `docs/ALARM_REFERENCE.md`. Patterns copied
from `docs/VAULT_DUNGEON_REFERENCE.md` / `docs/VILLAIN_HIDEOUTS_REFERENCE.md` (canvas, guards, sealed vault, threat table).
Code: `com.projecthero.mod.skyfortress` (+ `.block`, `.entity`, `.item`, `.worldgen`), client `com.projecthero.mod.client.skyfortress`.
Tests: `SkyFortressGameTests`. Assets: `node scratchpad/v0177/gen_sky_fortress.js` (skins, blocks, items, data) and
`node scratchpad/v0177/gen_kestrel.js` (Kestrel's geo / animations / texture / glowmask). Lang: `scratchpad/v0177/sky_lang.json`
via `lang_add.js`. Guide / JEI hooks: `scratchpad/v0177/sky_guide.js`; start-up hooks: `scratchpad/v0177/sky_wire.js`.
Harness: `scratchpad/SkyFortressHarness.v0177.java.txt` -> `docs/screenshots/v0177/sky_fortress/`.

## Placement

Structure `projecthero:sky_fortress` (type `projecthero:sky_fortress`, `SkyFortressStructure`), biomes
`#projecthero:sky_fortress_biomes` (plains, sunflower plains, savanna, savanna plateau, the seven ocean biomes), step
`top_layer_modification`, `random_spread` spacing 260 / separation 110 (The Vault: 200 / 80), salt 1707701, 6 chunks clear
of villages. In `#projecthero:locator/tier4` (the Dungeon Locator).

Two pieces per start:
- `SkyFortressPiece` — the ship, origin at the chunk centre with the lower deck's floor at **y 196** (`DECK_Y`); the whole
  ship spans about y 188 (keel) to y 221 (radar mast). Random rotation.
- `LaunchPadPiece` — the ground post straight under the hangar's lift hatch (`SkyFortressBuilder.HATCH`), floor at the
  generator's surface there. Over water (surface != ocean floor, or below sea level - 1) it stands on piles with its floor
  at sea level. Skipped where that ground is above y 150 (`MAX_GROUND`).

The biome test uses the ground under the hatch. The structure start's box (both pieces) is one **alarm zone**
(profile `sky_fortress`, tier 4: waves of 6 +1 per extra player).

## Layout (`SkyFortressBuilder`, local, bow = local north, y 0 = lower deck floor)

| Part | Where | What |
|---|---|---|
| Hull | x -15..15, z -44..32, keel to y -7, nacelles at x &plusmn;20 | light-grey plating, navy livery stripe, portholes, nav lights (shroomlight / verdant froglight), keel downlights, two engine nacelles on pylons (intake fan, blue exhaust glow), three stern thrusters |
| Hangar deck | z -43..-21 | open mouth (x -11..11) with a hazard-striped apron; the **lift hatch** (x -1..1, z -37..-35) through floor and keel; two parked block-built jets; catwalks with a **sniper**; two `vault_turret`s by the engine door; **Panel #1** (east wall) + an **Alarm Runner**; Shield-bearer; 2 cameras; 4 spawn points; **2 Parachute Crates** by the mouth; a Tier 2 barrel |
| Engine room | z -19..-3 | copper and deepslate; **four turbine housings** (cores at x &plusmn;7, z -15 / -7, facing the centre walk); 3 laser tripwires (knee / head / knee); **Panel #2**; Medic, Shield-bearer, trooper, gunner; 2 cameras |
| Cell block | z -1..14 | six barred cells (iron doors, a button outside each), **four prisoners**; guard desk with **Panel #3**; the **Captain** (keycard), a Runner, a Shield-bearer; the **Bridge Shield** (9 blocks, x -1..1, y 1..3, z 15) over the stair-hall door; side doors to the armory / barracks |
| Stern | z 16..31 | stair hall (stairs climb north from z 30 to the bridge floor at z 23); armory (anvil, smithing table, a Tier 3 barrel, a trooper); barracks (bunks, a gunner) |
| Command Bridge | x -9..9, z 18..32, on the flight deck | long front window, consoles, map tables, the captain's chair, **Panel #4**, two gunners, the **Deck Officer** (home `OFFICER_HOME`), a **sniper on the roof** behind a parapet, radar mast; the **locked keycard Blast Door** (x -1..1, z 18) onto the flight deck |
| Flight deck | y 8 (standing 9), z -44..17 | runway markings, landing ring round `KESTREL_HOME` (0, 9, -12), Kestrel's orange chevron, open edges with bollards, **four Wind Vents** gusting outward; **Admiral Kestrel** |
| Admiral's cabin | x -6..6, z -43..-36 on the deck | the **sealed Fortress Vault** (0, 9, -42), desk, map, a Tier 3 barrel |

Public constants for tests / the harness: `HATCH`, `KESTREL_HOME`, `KESTREL_SPAWN`, `OFFICER_HOME`, `VAULT`, `TURBINES`,
`SHIELD`, `BRIDGE_DOOR`, `PANELS`. `SkyFortressPiece.world(local)`, `buildAll(level)`.

**Ground post** (`LaunchPadBuilder`, local (0,0,0) = the pad's centre): 21 x 21 fenced platform, the 3 x 3 Launch Pad in a
hazard ring, guide lights, a guard shack (Alarm Panel, camera, Tier 2 barrel, ladder to the roof sniper), floodlight posts,
radio mast, windsock. Guards: the **Pad Sergeant** (`SkyTrooper` variant SERGEANT, drops the Fortress Pass), a trooper,
an Alarm Runner, the roof sniper. The column over the pad is cleared 44 up.

## Progression

1. Kill the Pad Sergeant (or sneak and pickpocket nothing: the pass only drops). Hold the **Fortress Pass**, step on the pad.
   `SkyFling`: every tick the server sets the player's upward velocity (`riseSpeed` = clamp((target + 3 - y) x 0.18,
   0.9, 2.6) b/t, centred on the pad, sent like knockback), until above the hangar floor, then a sideways nudge
   (0.42 b/t along the player's facing), 3 s Slow Falling, fall distance reset. The target is the first solid block straight
   above the pad (the hangar ceiling over the hatch) minus 6. Timeout 260 ticks (then 10 s Slow Falling). Fliers just fly up.
2. Hangar -> engine room (unlocked Blast Doors that shut on Alert).
3. **Turbines** (`SkyTurbines`): hold right-click on a running core for 40 ticks (any alarm state). It bursts (running=false
   for good). The fortress's turbines / shield blocks are found in its zone's box within 12 below / 16 above (no zone: 40
   round). The last one down: every `bridge_shield` block in that region goes, everyone aboard gets a camera shake
   (`TitanShakePayload` 0.9 / 70 ticks) and a shove to one side ("The fortress lurches and groans!").
4. Cell block: free prisoners (button, walk in, right-click: `chests/sky_prisoner_reward` straight into the inventory, a line
   of thanks, then they bail out with a parachute 2 s later) or leave them. Kill the **Captain** for the keycard.
5. Stair hall up to the Command Bridge; the Deck Officer.
6. The keycard opens the bridge's flight-deck door (or kill a whole reinforcement wave during an Alert).
7. Admiral Kestrel on the flight deck. Her death opens the cabin's Fortress Vault (Tier 4 Hero Vault, per-player loot,
   Ghost bonus if the zone never went Alert).

## People (`SkyFortressEntityTypes`; humans drawn by `SyndicateRenderer` with `SkyHuman.skyTexture()`)

| Mob | Base | HP / armour | Notes |
|---|---|---|---|
| `sky_trooper` | `SyndicateThug` | 36 / 8 | navy suit, gunmetal plates, orange visor helmet; baton or cutlass. Variant SERGEANT = Pad Sergeant (high-vis deck vest, orange helmet, ear defenders) |
| `sky_gunner` | `SyndicateGunman` | 30 / 6 | dark flak vest, red stripes; pistol, or a rifle (`Kind.SNIPER`) on perches |
| `sky_prisoner` | `PathfinderMob` | 20 | harmless; pilot / engineer skins (`HumanoidMobRenderer` + player model) |
| `deck_officer` | `SyndicateThug`, x1.15 | 320 / 12 (+4) | the mid-boss |
| `admiral_kestrel` | `Monster` + GeckoLib | 1100 / 14 (+6) | the boss |
| `kestrel_missile` | `Projectile` | — | homing micro-missile (rocket item renderer, smoke trail) |

Guards (`SkyFortressGuards`): tag `projecthero.sky_guard`, alarm guards, persistent, `restrictTo` their post, roles set
before they join the level. Reinforcements: two troopers to every gunner, arriving in a burst of jet wash.

Roles aboard: Captain (cell block), Runners (hangar, cell block, ground post), Medic (engine room), Shield-bearers (hangar,
engine room, cell block), Snipers (hangar catwalk, bridge roof, ground-post shack).

## The Deck Officer (`DeckOfficerBoss`)

Navy dress tunic, gold braid and epaulettes, white gloves, peaked cap with goggles, a netherite sabre. Shared `BossThreat`
(HurtByTarget removed), boss bar within 32, leash 12 / tether 30 from the bridge.

| Move | When | Effect |
|---|---|---|
| Sidearm Volley | 6-28, sight | 12-tick aim, 3 pistol bullets 6 ticks apart, 5 each |
| Flashbang | 4-20 | 20-tick white ring (r 4.5) at the target's feet, then 4 + Blindness + Slowness II 3 s |
| Sabre Lunge | 4-12, sight | 8-tick crouch, 10 ticks at 0.95 b/t, 11 + throw |
| Rally the Deck | at 60% and 30% health | 20-tick radio, 2 crew drop in, Strength I 10 s to crew within 12 |

Drops (`entities/deck_officer`): 2-3 Hero Alloy Ingots, 1-2 Vibranium Shards, 3-6 XP bottles, 50% golden apple.
Hooks: `startVolley/startFlash/startLunge/startRally`, `flash(level, at)`, `rally(level)`, `threat()`, `home()`.

## Admiral Kestrel (`AdmiralKestrelBoss`)

An original villain: an ace pilot in a heavy gunmetal-and-navy flight suit, crested visor helmet, layered chest armour with
a glowing core, pauldrons with orange trim, forearm missile pods, a twin-nozzle jet pack, folding two-segment wing-blades
with glowing edges, jet boots. GeckoLib, about 2.7 blocks tall (19 bones, 61 cubes, 128 x 64 texture + glowmask).

Shared `BossThreat` (range 48), boss bar within 56 (blue, red "(airborne)" in phase 2), bound to `home` (the deck ring
centre): put back on the deck if she ends up more than 44 away or 10 below it. No fall damage, fire immune, knockback 0.9.

| Move | Phase | Effect |
|---|---|---|
| Jet Dash | 1 (6-20, sight) | 12-tick crouch with an orange line on the path, 12 ticks at 1.3 b/t, 12 + throw |
| Missile Barrage | both (8-36 / 4-36, sight) | 16-tick lock-on (red rings under up to 3 players), then 6 (9 airborne) homing missiles one per 3 ticks; 6 in a 2.5 burst each |
| Strafing Run | both (cooldown ~23-28 s, ~15-20 s airborne) | lifts off, flies to the lane start while a red lane (4 wide, 36 long, through her target) is painted for 30 ticks, then rakes it at 1.5 b/t: 7 in a 2.2 circle under her every other tick |
| Fighter Drones | both | 20-tick radio, 2 drones (+1 per extra player), at most 3 (+1 per extra player, max 5) near; existing `ultron_drone` mobs renamed "Fighter Drone", tagged `projecthero.kestrel_drone` (killed when she dies) |
| Transformation | at 50% | 40 ticks, untouchable, rises, wings unfold |
| Air phase | 2 | circles the deck 11 out and ~7 up, facing her target |
| Afterburner Dive | 2 | 20-tick ring (r 3.5) at the target's spot, plunge at 1.6 b/t, 14 + throw within 3.5, then **grounded** 50 ticks (+30% damage taken) |

Animations: idle / walk / hover (each also `_open`: wings spread), dash, missiles, strafe, radio, dive, recover, transform,
death. One GeckoLib controller picks the clip from the synced move / phase / flying flags.
Drops (`entities/admiral_kestrel`): 2-4 Celestial Cores, 3-5 Vibranium Shards, 8-12 XP bottles, 2 golden apples, 35% netherite
scrap, 15% Elytra. Hooks: `startDash/startMissiles/startStrafe/startDrones/startDive/startTransform`, `fireMissile`,
`callDrones`, `laneStart/laneEnd`, `phase()`, `flying()`, `move()`.

## Blocks (`SkyFortressBlocks`)

| Block | Notes |
|---|---|
| `launch_pad` | step on with a Fortress Pass in either hand -> fling (`SkyFling`); right-click explains. Pickaxe, drops itself |
| `sky_turbine` (`running`) | unbreakable; hold right-click 2 s to sabotage; animated fan texture, smoke / embers once wrecked |
| `bridge_shield` | unbreakable, solid, translucent animated hex field; right-click says how many turbines still run |
| `sky_vault_seal` (`facing`) | unbreakable; opens into a Tier 4 Hero Vault when Kestrel dies (search 44 round her home), or when used with no Kestrel within 96 |
| `parachute_crate` | right-click: a Parachute (refused while you carry one); endless; breaks for nothing |
| `wind_vent` (`facing`, block entity) | 170-tick cycle offset per vent: 30 ticks of hissing puffs (the tell), 34 ticks of gust: players in the 15 x 5 lane get +0.075 b/t per tick along the facing (halved sneaking) up to 0.75. Only runs with a player within 24 |

## Items (`SkyFortressItems`)

| Item | Rarity | Notes |
|---|---|---|
| `fortress_pass` | Rare | the Launch Pad key; never used up |
| `parachute` | Common, stacks to 4 | right-click in the air, or it opens itself after a 10-block fall (anywhere in the inventory): 20 s Slow Falling, fall forgiven, one used |
| `kestrel_thruster` (artifact) | Legendary | right-click: thrust 1.55 b/t along your look (+0.35 up), fall forgiven, 3.5 s Slow Falling glide; 15 s cooldown. Movement only |

**Artifact roll:** `SkyFortressMod` adds a pool to `chests/vault_tier4_artifacts` in code (`LootTableEvents.MODIFY`, so the
shared JSON is untouched): 20% Kestrel's Thruster, gated by `{"condition": "projecthero:in_sky_fortress"}`
(`InSkyFortressCondition`: the origin is in a `sky_fortress` alarm zone and no Admiral Kestrel lives within 96).

## Static state

`SkyFling.ACTIVE` (player UUID -> pad position, target y, landing direction, ticks) and `SkyTurbines.PROGRESS` (player UUID ->
position / last tick / progress): numbers only, cleared by `SkyFortressMod.clearSessionState()` from
`ServerStateReset.clearAll`. Everything else is world state (blocks, entities, the alarm zone's SavedData).

## Guidebook / JEI

Structures chapter: a "Sky Fortress (Tier 4)" entry. Loot & Progression: a Tier 4 dungeon line. Villain Hideouts chapter:
a "The Sky Fortress (Tier 4)" sub-section after The Vault (getting aboard, wings, turbines, cells, the Deck Officer, Kestrel,
the long drop, the vault, the artifact) — no new chapter, counts unchanged. JEI info pages for the three items and six
blocks. Creative tab: all of them.
