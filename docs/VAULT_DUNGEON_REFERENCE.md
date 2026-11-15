# The Vault (v0.17.5, Tier 4 dungeon) — reference

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
