# Supervillain Village Raid — Reference

A rare, superhero-themed village assault added in **v0.6.23**. It is **not** a vanilla raid and does
not replace one. Package: `com.projecthero.mod.event.raid` (+ `com.projecthero.mod.event.entity` for the
Pillager Spy and the boss variant). Built on the existing `com.projecthero.mod.event` world-event
framework and reuses the Zombie Raid's `EmpoweredZombie` boss and `BossPowers` AI registry wholesale.

## v0.9.10 changes

- **Wave sizes doubled** (`SupervillainRaidWaves`, waves 1–5), the same treatment the Zombie Raid got
  in v0.9.9. Solo base counts: wave 1 = 16, wave 5 peaks at 20 Pillagers / 16 Vindicators / 6 Evokers /
  4 Witches / 4 Ravagers. Ravager fixed counts doubled too (still opt out of per-player scaling).
- **The Supervillain is more aggressive and powerful** (`EmpoweredZombie.configureAsSupervillain` +
  `EventConfig.SupervillainRaid`): real configured melee damage (`bossMeleeDamage` = 16, a new config
  key that lands even on an old config file — was the bare entity default of 10), a small
  movement-speed bump matching the Zombie Raid's final boss, `bossAbilityDamageScale` 0.7 → **1.0**
  (its power hits as hard as the Zombie Raid boss's now — still fair, every ability is telegraphed and
  dodgeable), base health 350 → **450** and +125 → **+150** per extra player, knockback resistance
  0.35 → **0.45**. All the telegraphed-cast / close-range-shove / SuperSpeed-blitz / Flight-aerial AI
  from v0.9.9 was already shared `BossPowerController` / `EmpoweredZombie` behaviour and applied
  automatically.

## Player-facing summary

1. **A Pillager Spy** appears rarely in the wild (darker robe, faint purple particle, name on the
   crosshair), sometimes with a 1–2 Pillager escort, and heads for the nearest village.
2. If the Spy **damages a player who is standing inside a village**, the village is **Marked for
   Attack**. A miss does nothing; an ordinary Pillager does nothing; a hit outside the village does
   nothing.
3. A **10-minute preparation timer** runs (warnings at 10 / 5 / 1 min and a final 10-second
   countdown). Leaving the village does not cancel it — the mark belongs to the village. While you
   are within ~96 blocks of the marked village a **vanilla-style raid bar** appears at the top of
   the screen (red, ten notches) showing the `M:SS` time left until the first wave.
4. **Five escalating raider waves** (Pillagers → + Vindicators → + Witches → + Evokers + Ravager →
   the big wave), each cleared before the next, 15 s between them. The same raid bar now reads
   **"Wave X/5 · N enemies left"** and fills as the wave is cleared. It hides once the Supervillain
   arrives (that fight uses the boss's own health bar). Raiders that get kited more than ~60 blocks
   from the village are walked back; ones dragged much further are pulled straight back — the fight
   stays at the village (`EventInstance#tetherOwnedMobs` + `Mob#restrictTo` at spawn).
5. After wave 5, ~20 s of quiet, then **SOMETHING POWERFUL IS APPROACHING** and one of three
   **Supervillains** arrives with a random superhero power and a small escort.
6. Defeat the Supervillain → **THE VILLAGE IS SAFE**, rewards drop, remaining raiders flee, and the
   raid is over. It is **repeatable**: the next Pillager Spy can mark the same village (or any other)
   straight away -- see v0.12.23 / v0.14.4 below. (The old 3-day village immunity is gone.)

## Architecture

| Concern | Where |
|---|---|
| Event lifecycle / persistence / abandon | `SupervillainRaid extends EventInstance` |
| Wave table (data) | `SupervillainRaidWaves` |
| Village marking + starting | `SupervillainRaidStarter` |
| Top-of-screen raid bar | `EventBossBar` (shared with the Zombie Raid) |
| Post-raid 3-day cooldowns (SavedData) | `SupervillainVillages` |
| Rare natural spawn | `PillagerSpySpawner` (ticked from `Project HeroMod` server tick) |
| Spy entity + village-seek AI | `PillagerSpy extends Pillager` |
| Boss | `EmpoweredZombie` + `DATA_VARIANT` (`configureAsSupervillain`) — full AI reused |
| Boss appearance enum | `SupervillainVariant` (Chimera / Arsenal / Omega Mage) |
| Random power (AI) | `BossPowers.randomSupervillainKey` over the existing `BossPowerController` registry |
| Rewards | `SupervillainRaidRewards` |
| Items | `SupervillainRaidItems` |
| Damage trigger hook | `SupervillainRaidEvents` (`AFTER_DAMAGE`) |
| Death / boss-defeat hook | shared with the Zombie Raid in `GraveboundEvents.onAfterDeath` |
| Client rendering | `SupervillainBossRenderer`, `RaidEntityRenderers` |
| Commands | `/supervillainraid start|mark|boss|spy|clear|status` (`SupervillainRaidCommand`, op 2) |
| Config | `EventConfig.SupervillainRaid` → `config/projecthero_events.json` `supervillainRaid` block |

## Boss

The Supervillain is an `EmpoweredZombie` with a cosmetic `variant` set. It runs with the same AI as
the Zombie Raid's final boss (target scoring, rotation, airborne/ranged response, preferred-range
movement, cluster-aware AoE) and one of the **14 boss-capable Experimental Powers**
(`BossPowerController` subclasses). Appearance and power are rolled independently.

* **Health**: `bossBaseHealth` (350) + `bossHealthPerAdditionalPlayer` (125) × (players − 1), capped
  at `bossHealthPlayerCap` (8) participants.
* **Knockback resistance**: `bossKnockbackResistance` (0.35).
* **Ability damage**: every boss-power ability is multiplied by `bossAbilityDamageScale` (0.7) so a
  reused player power stays "dangerous but fair". Only touches the Supervillain — the Zombie Raid
  boss keeps 1.0.
* **Hitbox**: much smaller than the visual model (Chimera 1.55×, Arsenal 1.45×, Omega Mage 1.30×,
  vs. render scales of 2.15 / 1.95 / 1.75) so it does not wedge in village buildings.
* **Boss bar**: `THE CHIMERA — GEOKINESIS` etc.; title reveal on spawn shows `POWER: …`.

### Model status

The three variants currently render on the vanilla zombie mesh with flat tinted placeholder textures
(`textures/entity/supervillain_{chimera,arsenal,omega_mage}.png`) plus a per-variant scale. All the
gameplay, AI, boss bar, rewards and model-selection are wired to `SupervillainVariant`, so dropping
in the bespoke voxel models from the reference art is texture / GeckoLib work with **no logic
change**.

## Compatible powers

`BossPowers` registers the 14 Experimental Powers whose fantasy works on an AI mob: Super Strength,
Laser Vision, Flight, Super Speed, Geokinesis, Electrokinesis, Pyrokinesis, Cryokinesis,
Teleportation, Super Durability, Sonic Scream, Shockwave, Gravity, Magnetism. Excluded (and why):
Invisibility/Light, Spider Climbing, Size, Density, Elasticity, Telekinesis, Super Regeneration,
Plant/Water/Wind, Energy Absorption, Shadow, Crystalkinesis — each is built around player inputs, a
GUI, building, or a mechanic that does nothing visible on a mob. `supervillainAllowedPowers` /
`supervillainBlockedPowers` in the config further narrow the pool.

## Commands (op 2)

| Command | Effect |
|---|---|
| `/supervillainraid start` | Mark the nearest village and skip the countdown straight to Wave 1 |
| `/supervillainraid mark` | Mark the nearest village and run the full 10-minute timer |
| `/supervillainraid boss` | Skip to Wave 6 (the Supervillain), starting a raid first if needed |
| `/supervillainraid spy` | Spawn a Pillager Spy in front of you |
| `/supervillainraid clear` | Abort every active Supervillain Raid and clear this village's cooldown |
| `/supervillainraid status` | Village / phase / wave / countdown / villain / power / participant count |
| `/heroraid start supervillain` | Start it here, countdown skipped (shared start/stop command — also `gravebound`) |
| `/heroraid stop` | Force-end **every** active Project Hero world event and clear all curses |
| `/heroraid cleartimers` | Clear every curse countdown + any raid still in its pre-wave countdown; leave running raids alone |

## Persistence

`SupervillainRaid` persists phase, phase timer, the preparation countdown's total length, wave, owed
spawn counts, escort counts, the boss UUID, the rolled variant + power, whether the boss is defeated,
whether victory rewards were granted, and whether the post-raid cooldown was started. The preparation
countdown bar itself is **not** persisted — it is a transient `ServerBossEvent` rebuilt lazily each
tick and its membership is recomputed from the level's player list, so a restart just re-creates it. A restart mid-raid resumes exactly where it was and
can never duplicate a boss or a reward. Village cooldowns live in the `SupervillainVillages`
SavedData on the overworld.

## Known limitations / follow-ups

* Bespoke Chimera / Arsenal / Omega Mage voxel models + animations (currently tinted zombie mesh).
* Pillager Spy natural spawn is a standalone rare tick (5% roll per player every 5 min near a
  village), not a `PatrolSpawner` mixin — tune `pillagerSpySpawnChance` to taste.
* "Champion of the Village" is Hero of the Village III for 30 min (re-applied fresh, not stacked),
  not a bespoke registered MobEffect.
* Villager panic / bell-ringing relies on vanilla raider-proximity behaviour rather than the vanilla
  Raid system's explicit hooks.
* Not yet playtested in a live world (no `runServer` in the dev environment) — build + 182 gametests
  green, dev-client boot clean.

## v0.12.23

- The Pillager Spy marks a village when it *attacks* a player inside it (`performRangedAttack`), not only when the arrow
  deals damage, and the per-village post-raid cooldown no longer blocks marking (an active raid still does).

## v0.14.4 -- repeatable raids

Player report: after beating a Supervillain Raid, Pillager Spies never marked a village again.

**Root cause** (confirmed against the user's real playthrough logs + saves and vanilla bytecode):
`PillagerSpy.insideVillage` was vanilla `ServerLevel.isVillage`, which only counts village POIs that are
*claimed by a living villager* (`PoiManager.isVillageCenter` filters on `Occupancy.IS_OCCUPIED`, and
`Villager.die` calls `releaseAllPois`). The raid's Vindicators / Evokers / Ravagers kill villagers, so a
village that had been raided was often no longer a "village": the spawner (which finds the bell with
`Occupancy.ANY`) kept producing spies -- the logs show four natural spies at the user's raided village
over three days, zero marks -- but no hit could ever mark it. Aggravating it, the spy (a) fired at the
first player it saw, and it spawns 32-56 blocks from a player who is by construction *outside* the
village, so natural spies spent themselves in the fields; (b) took vanilla's random despawn roll as
soon as it was 32+ blocks from everyone, i.e. usually before reaching the village; (c) shot villagers,
emptying the village itself. Nothing in raid / player state lingers after a victory (the user's
`projecthero_world_events.dat` was empty, no stuck flags on any player; the `SupervillainVillages`
cooldown has not been consulted since v0.12.23).

**Fixes**
- `PillagerSpy.insideVillage` = vanilla `isVillage` **or** within `VILLAGE_BELL_RADIUS` (64) blocks of a
  bell (`PoiTypes.MEETING`, any occupancy). Used by the trigger, the spy's targeting and the spawner's
  "player is not already in a village" check.
- Spy targeting: vanilla Pillager's `NearestAttackableTargetGoal`s (player / villager / golem) are replaced
  by one that only targets a **player standing in a village**, plus iron golems. `HurtByTargetGoal` is
  kept, so it still shoots back when attacked. It no longer shoots villagers.
- `removeWhenFarAway(d)` = `d > 96²` (`LINGER_RANGE`): the spy lingers while a player is within 96 blocks
  and still despawns like any rare monster once everyone has gone.
- `SeekVillageGoal` "arrived" = within 24 blocks of its bell (or in a vanilla village) -- it used to circle
  a villager-less village forever.
- `PillagerSpySpawner.villageToScout(level, pos)` split out: pure world-side eligibility, no memory of past
  raids. Spawn chance left at 5% per 5 min (each spy is now far more likely to actually mark a village).

**Tests** (`RaidRepeatGameTests`): the full lifecycle twice -- a spy hits a player in a bell-only
(villager-less) village -> raid starts -> skip to the boss -> kill the Supervillain -> raid COMPLETED and
removed (Champion effect granted, legacy cooldown record written) -> a second spy hit marks the same
village again. Spawner eligibility is unchanged after a completion count, a village cooldown, Champion of
the Village and a Gravebound record. `removeWhenFarAway` near / far (`SupervillainRaidGameTests`).
