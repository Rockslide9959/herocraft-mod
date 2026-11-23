# The Vault (v0.17.5, two new floors + alarm v0.17.7, Tier 4 dungeon) — reference

## v0.17.7: Maximum Security, the Warden's Control Room, the alarm, Kross phase 2

Design: `docs/STRUCTURES_V0177_DESIGN.md` §2. Tests: `VaultSitesGameTests` (+ `VaultDungeonGameTests` updated for layout 2).
Assets: `node scratchpad/v0177/gen_vault_sites_v0177.js`; lang: `scratchpad/v0177/vault_sites_lang.json` via `lang_add.js`.
Harness: `scratchpad/VaultSitesHarness.v0177.java.txt` + `scratchpad/v0177/vault_sites_films_v0177.js` -> `docs/screenshots/v0177/vault_sites/`.

**Layouts.** `VaultPiece` saves `Layout` (2 = v0.17.7). A piece saved before the tag existed loads as layout 1 and keeps
drawing the v0.17.5 plan (a half-generated old Vault finishes the way it started); its saved box is kept too. New pieces:
layout 2, box min z -66 (was -21). Structure id, placement, biomes and the tier-4 Locator tag are unchanged.
`VaultBuilder.build(canvas, layout)` makes a fresh builder object per call (worldgen draws chunks on several threads, so
the "layout 2" flag is a final field, never shared state).

**The route (layout 2).** Surface -> Cell Block -> Security Floor (console opens the gate) -> gate landing -> **keycard
Blast Door** (the guard Captain in the security office carries the card; a cleared alarm wave also unlocks it) ->
Maximum Security (corridor, laser grid, the hall with the Riot Captain) -> **red field** (drops when the Riot Captain dies)
-> Warden's Control Room -> **blue field** (drops when the cell-block breaker is thrown) -> passage west, 16-step stair
south, a tunnel on the Warden's Level into the processing room's north wall -> Kross's arena -> the vault room. The v0.17.5
flights from the gate landing to the processing room are not drawn in layout 2.

| Part (local, y) | What |
|---|---|
| Laser corridor x -3..2, z -24..-12, y F2 | 5 framework laser tripwires set in the walls (knee/head, both sides), hazard stripes, cameras at both ends, Alarm Panel #4 |
| Laser grid x -7..6, z -34..-24, y F2 | 3 rows of floor lasers pointing up (rows z -27/-30/-32, one gap each at x 4/-5/0, marked with a lime floor block), 2 cameras, a shield-bearer |
| Hall x -12..11, z -52..-34, y F2 (8 high) | 8 pods (x -11..-8 and 7..10, z centres -49/-45/-41/-37) fronted by blue fields, an inmate each (tag `projecthero.vault_pod`); iron catwalks on the pod roofs (ladder at the south-west), snipers on them; the Riot Captain at (0, F2+1, -44) with a shield-bearer, a medic, a runner; Alarm Panel #5; 3 cameras; 4 spawn points; red field on the far door (x -1..0, z -52) |
| Control Room x -9..8, z -64..-52, y F2 | monitor wall, desks, the **cell-block breaker** `vault_breaker` at (0, F2+1, -62), Alarm Panel #6 (spared by the power cut), a Tier 3 chest, a shield-bearer + gunner; blue field on the west exit (x -9, z -58..-57) |
| Descent | passage x -25..-9 z -59..-56 (F2), stair x -25..-21 z -56..-41 (F2 -> F3), tunnel x -25..-21 z -41..-12 (F3) with a camera, a gunner, a spawn point |

Public positions: `VaultBuilder.LAYOUT`, `MAXSEC_DOOR`, `RIOT_CAPTAIN`, `CAPTAIN_FIELD`, `BREAKER`, `POWER_FIELD`, `PODS`.

**Alarm.** The Vault is an alarm zone (`VaultSecurity.PROFILE` = `the_vault`, tier 4, colour 0xC0392B, siren bell pitch 0.5;
waves are riot wardens / gunners via `VaultGuards.spawn`). `VaultPiece.buildAll` registers the zone by hand. Layout 2 has an
Alarm Panel on every floor (7: post, guard tower foot, turret hall, corridor, hall, Control Room, processing room), 16
cameras, 40 laser tripwires (the Security Floor's two `vault_laser_alarm`s became framework lasers in the west wall), 23
lamps and 22 spawn points. Every non-inmate guard spawned by a layout-2 build is an alarm guard (`VaultGuards.spawn(...,
alarmGuard, role)`), roles: Captain (security office, keycard), runners (post, cell block, turret hall, hall), medics,
shield-bearers, snipers (cell-block gallery, turret hall, hall catwalk). Inmates are never guards. Ghost bonus: automatic
on the Tier 4 Hero Vaults (`HeroVault.open` -> `AlarmGhost.claim`). Kross's Lockdown does not raise the zone alarm (it
would cost every Ghost run); it keeps its own squad.

**Blocks.**

| Block | Notes |
|---|---|
| `vault_barrier` (`kind` = power / captain) | Security Field. Unbreakable, see-through (translucent render layer), light 7, full collision. Right-click explains it. `VaultSecurity.dropFields(level, centre, r, dy, kind)` |
| `vault_breaker` (`facing`, `powered`) | Cell-block breaker. Right-click while powered -> `VaultSecurity.shutDown`: off for good; blue fields within 34 (dy 8) drop; panels on its floor (dy <= 3) within 50 except within 10 of it are sabotaged (the Maximum Security wing's sensors go dark); pod inmates get a 24 leash and target the nearest player; chat line. |

**The Riot Captain** (`riot_captain`, `RiotCaptainBoss` extends `SyndicateEnforcer`, x1.25, 380 HP, armour 12 / 4,
player model, skin `SyndicateSkin.VAULT_RIOT_CAPTAIN` painted by the generator over `thug_c`). Shared `BossThreat`
(hurt-by removed), yellow boss bar within 40 (red after the Rally), leash 14 / tether 32 from his post. Shield: frontal hits
-40% (`RiotWarden.fromFront`), dazed = +50%. Moves: Shield Rush (4-12, sight; 10-tick brace, 14 ticks at 0.75 b/t, 9 dmg,
shove, Slowness II 1 s; wall = dazed 30), Flashbang (5-16, sight; 8-tick wind, 16-tick arc, pop: Blindness 3 s + Slowness
2 s + 3 dmg to everyone within 6 with a clear line), Rally (once at half health: 2 shield-bearer wardens, Strength 10 s to
guards within 12, Resistance 5 s to him, cooldowns x0.75). Death -> `VaultSecurity.captainDown` (red fields within 30).
Drops (`entities/riot_captain`): 2-4 Hero Alloy Ingots, 1-2 Vibranium Shards, 3-6 XP bottles, 50% Energy Cell, 35% golden
apple. Test hooks: `startRush`, `startFlash`, `pop(level, at)`, `rally(level)`, `rallied()`, `threat()`.

**Warden Kross, phase 2** (from half health, on top of the v0.17.5 enrage + Lockdown): chat "phase 2".
- Riot Gas Volley (`MOVE_GAS`, 4-16 sight): 14-tick windup, three canisters (target + 3.5 either side) on 14-tick arcs;
  each leaves a cloud for 160 ticks, r 2.8: every 10 ticks 2 (indirect magic) + Blindness 1.5 s + Slowness 1.5 s to
  everyone but Syndicate mobs. Cooldown 240-300 (x0.65 enraged). Clouds are transient fields on the boss.
- Breach Slam (`MOVE_SLAM`, 6-18 sight, 50%): 14-tick crouch, a ballistic leap onto the target's spot (~18 ticks,
  horizontal speed capped), landing (or 34 ticks) = shockwave r 6: max(6, 16 x (1 - d/7)) + throw + Slowness II 1.5 s.
  Cooldown 220-280 (x0.65).
Test hooks: `startGas`, `startSlam`, `addGasCloud`, `gasClouds()`, `slamImpact(level)`.

No new static state (nothing for `ServerStateReset`).

Design: `docs/STRUCTURES_PROGRESSION_DESIGN.md` §2 ("Tier 4 Dungeons"). Loot hooks: `docs/LOOT_PROGRESSION_REFERENCE.md`.
Patterns copied from `docs/VILLAIN_HIDEOUTS_REFERENCE.md` (canvas, guards, sealed vault, threat table, SavedData, loot gate).
Code: `com.projecthero.mod.vaultdungeon` (server/common), `com.projecthero.mod.client.vaultdungeon` (renderers).
Tests: `VaultDungeonGameTests`. Assets: `node scratchpad/gen_vault_v0175.js`; lang: `node scratchpad/lang_vault_v0175.js`
(key by key). Films/screens: `scratchpad/VaultDungeonHarness.v0175.java.txt` + `scratchpad/vault_v0175_films.js`
-> `docs/screenshots/v0175/vault/`.

## Placement

Structure `projecthero:the_vault` (type `projecthero:vault_dungeon`, `VaultStructure`), biomes `#projecthero:vault_dungeon_biomes`
(= `#projecthero:finds_land_biomes`), step `top_layer_modification`, `random_spread` spacing 200 / separation 80, salt 1705201,
4 chunks clear of villages. Skipped on water (world-surface vs ocean-floor heightmaps differ), below sea level - 1, or where
the bottom floor would sink under `minBuildHeight + 8`. In `#projecthero:locator/tier4` (the Dungeon Locator). The vanilla
random-spread search counts its radius in placement cells, so the Locator's 100 reaches far past the 200-chunk spacing.

## Layout (`VaultBuilder`, local coords, entrance local north, y 0 = surface)

One code-built piece (`VaultPiece`) drawn on the hideouts' `HideoutCanvas` (no faction): rotated + clipped per chunk, mobs
spawned only by the chunk holding their feet. Box x -28..13, y -47..9, z -21..26.

| Part | y | What |
|---|---|---|
| Surface | 0 | fenced pad, gray-concrete guard post (iron blast doorway, barred windows, floodlights), shaft + ladder, 2 riot wardens |
| Cell Block | -14 | 25x37 hall, 28 barred cells on two tiers (galleries + stairs), many doors broken, glass guard tower round the shaft; 8 inmates, 2 wardens, 1 gunner; tier 1 barrels |
| Stair 1 | -14..-28 | tunnel from the hall's south-east corner |
| Security Floor | -28 | checkpoint corridor with 2 laser tripwires, turret hall (4 ceiling turrets, cover), security office (console, tier 2 barrels), lockdown gate (2 iron doors); 5 wardens, 2 gunners |
| Stair 2 | -28..-44 | from behind the gate |
| Warden's Level | -44 | processing room (2 wardens, gunner, tier 3 chest), arena (blackstone, red ring, 4 pillars, dais), vault room (3 sealed Dungeon Vaults) |

`VaultBuilder.ANCHOR` (0, -43, 5) = arena centre = Kross's home = clearing anchor. `VAULTS`, `CONSOLE`, `GATE`, `TURRETS`, `LASERS`
are public for tests / the harness; `VaultPiece.anchor()`, `vaults()`, `world(local)`.

## Blocks (`VaultBlocks`)

| Block | Notes |
|---|---|
| `vault_seal` (facing) | Sealed Dungeon Vault. Unbreakable. Kross dead -> Tier 4 Hero Vault, same facing (`VaultClearing.unsealVaults`, 24 round the anchor, dy ±8). Used while no Kross within 96 -> opens anyway. |
| `lockdown_shutter` | Unbreakable blast plate placed by the Lockdown. Self-removes (scheduled tick every 100) when no Kross in Lockdown is within 40. |
| `lockdown_track` | Hazard floor. Lockdown fills the 3 blocks above every track within 20 (dy -4..1) of the anchor. Arena: west passage + vault-room door (6 columns). |
| `vault_laser_alarm` (facing, spent) | Tripwire hook hangs on its front; client red-dust laser along the wire. First power change **with a non-creative player within 16** -> spent, sirens, 3 wardens/gunner (+1 per extra player, max 5) via `VaultGuards.alarm`. |
| `vault_turret` (+BE) | Scans every 5 ticks for a player within 14 in line of sight; 3-dart burst (tipped arrows, 1.5 base dmg, Slowness II 2 s, no pickup), 36-tick rest. Pickaxe to remove. Nothing saved. |
| `vault_console` (facing) | Use: toggles every iron door within 32 (opens if any is closed). |

## People (`VaultEntityTypes`, all player models via `SyndicateRenderer`, skins `SyndicateSkin.VAULT_*`)

| Mob | Base | HP / armour | Notes |
|---|---|---|---|
| `vault_inmate` | `SyndicateThug` | 30 / 2 | orange jumpsuit (2 skins), shiv / pipe / fists |
| `riot_warden` | `SyndicateThug` | 44 / 10 | baton (Slowness on hit), riot shield: -40% from the front |
| `riot_gunner` | `SyndicateGunman` (shotgun) | 34 / 7 | grey vest |
| `warden_kross` | `SyndicateEnforcer`, x1.7 | 900 / 14 (+6 toughness) | the boss |

Guards: persistent, tagged `projecthero.vault_guard`, `restrictTo` their post. Skins are painted over the shipped Syndicate
skins `thug_a` / `thug_c` (Skindex, see `docs/SYNDICATE_REFERENCE.md` for those credits) by `gen_vault_v0175.js`.

## Warden Kross (`WardenKrossBoss`)

Shared `BossThreat` (HurtByTarget removed), boss bar within 48 (purple + "LOCKDOWN PROTOCOL" once enraged), leash 18 / tether 40
from home, shield: frontal hits -25% (-70% while bracing / charging), dazed = +50% damage taken.

| Move | When | Effect |
|---|---|---|
| Riot Shield Charge | 5-16, sight | 16-tick brace, 20 ticks at 0.9 b/t; 12 (14) + throw + Slowness III 1.5 s; wall = dazed 36 ticks |
| Stun Baton Shockwave | within radius | 20-tick spark ring (r 7, 8.5 enraged), 10 (13) to grounded + Slowness IV + Weakness 2 s |
| Taser Line | 6-18, sight | 10-tick aim, 30 (40) ticks latched: 2.5 per 5 ticks, Slowness II, reel-in; breaks on lost sight / 20 blocks. `BeamClash.report` every live tick; renderer hides it when `BeamClashClient.hides` |
| Lockdown | enraged | 30-tick siren, shutters (`VaultClearing.seal`), 3-5 guards; lasts 600 ticks, repeats every 900 |

Phase change at half health: speed 0.27 -> 0.31, cooldowns x0.65, first Lockdown at once. Test hooks:
`startCharge/startShock/startTaser/startLockdown`, `threat()`, `inLockdown()`, `enraged()`, `taserTargetId()`.
Drops (`loot_table/entities/warden_kross.json`): 2-3 Celestial Cores, 2-4 Vibranium Shards, 6-10 XP bottles, 1-2 golden apples,
30% netherite scrap.

## Clearing (`VaultClearing`, `VaultDungeonState`)

Kross dies -> `bossDefeated(level, home)`: `VaultDungeonState` (per-dimension SavedData `projecthero_vault_dungeons`, keyed by
anchor) marks it cleared for good, seals open into Tier 4 Hero Vaults (per-player loot, Celestial Upgrade template from the
tier 4 gear table), title + chat within 64. A Lockdown is lifted on death. No static state anywhere (nothing for
`ServerStateReset`).

## Artifacts (`vaultdungeon.item`)

`chests/vault_tier4_artifacts`: one pool each, gated by `{"condition": "projecthero:near_vault_dungeon", "radius": 32}`
(`NearVaultCondition`) + random chance. A Tier 4 vault anywhere else rolls none.

| Item | Rarity | Chance | Effect |
|---|---|---|---|
| `wardens_keyring` | Epic | 22% | use: hostiles within 10 shackled 80 ticks (Slowness VI + Weakness II; bosses a third), 40 s cooldown, nothing spent with nobody near; use on iron door / trapdoor toggles it |
| `cracked_mjolnir_shard` | Legendary | 15% | hold >= 1 s, release: show-only lightning at the look point (32), 10 to non-players within 3 + Slowness II, 25 s cooldown |
| `riot_capacitor` | Epic | 22% | carried: hurt by something within 6 -> 6 to it and hostiles within 3.5 + shove, 15 s item cooldown (`ServerLivingEntityEvents.AFTER_DAMAGE`) |

## Guidebook / JEI

"The Vault (Tier 4)" sub-section at the end of the Villain Hideouts chapter (no new chapter, counts unchanged). JEI info pages
for the three artifacts and the turret / alarm / console / track. Creative tab: artifacts + those four blocks.
