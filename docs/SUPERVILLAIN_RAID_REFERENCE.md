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
2. **v0.14.21 — the Spy marks the player, like Bad Omen.** If the Spy's bolt (or melee) **hits a
   player — anywhere —** or a player **kills the Spy**, that player gets the **Supervillain's Mark**
   status effect (100 minutes, harmful, milk clears it). A miss does nothing; an ordinary Pillager
   does nothing. When a marked player is **inside a village** (within ~64 blocks of a bell, or a
   vanilla occupied village) on a non-Peaceful world, the mark turns into a **30-second Supervillain
   Omen** (title + horn, last-5-seconds countdown) — the Raid Omen step — and when that runs out the
   raid starts at that spot and the village is **Marked for Attack**. The mark is only consumed when
   a raid really starts: if one is already running there, the mark is kept (and an omen that cannot
   fire hands it back); on Peaceful nothing ever converts. See *v0.14.21* below.
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
| Spy hit / kill -> player mark; raid start | `SupervillainRaidStarter` |
| Supervillain's Mark + Omen effects, mark -> omen -> raid tick | `SupervillainMark` (v0.14.21) |
| Top-of-screen raid bar | `EventBossBar` (shared with the Zombie Raid) |
| Post-raid 3-day cooldowns (SavedData) | `SupervillainVillages` |
| Rare natural spawn | `PillagerSpySpawner` (ticked from `Project HeroMod` server tick) |
| Spy entity + village-seek AI | `PillagerSpy extends Pillager` |
| Boss | `EmpoweredZombie` + `DATA_VARIANT` (`configureAsSupervillain`) — full AI reused |
| Boss appearance enum | `SupervillainVariant` (Chimera / Arsenal / Omega Mage) |
| Random power (AI) | `BossPowers.randomSupervillainKey` over the existing `BossPowerController` registry |
| Rewards | `SupervillainRaidRewards` (v0.14.21: + `victoryValuables` -- 16-28 lapis, redstone, iron, gold, 2-5 diamonds, amethyst, golden apples, a book, 30% lapis block, 5% enchanted golden apple; a third more per extra participant, up to double) |
| Items | `SupervillainRaidItems` |
| Damage trigger hook | `SupervillainRaidEvents` (`AFTER_DAMAGE`) |
| Death / boss-defeat hook | shared with the Zombie Raid in `GraveboundEvents.onAfterDeath` |
| Client rendering | `SupervillainBossRenderer`, `RaidEntityRenderers` |
| Commands | `/supervillainraid start|mark|boss|spy|clear|status` (`SupervillainRaidCommand`, op 2) |
| Config | `EventConfig.SupervillainRaid` → `config/projecthero_events.json` `supervillainRaid` block |

## Boss

The Supervillain is an `EmpoweredZombie` with a cosmetic `variant` set. It runs with the same AI as
the Zombie Raid's final boss (target scoring, rotation -- v0.15.19: overridden by the shared `BossThreat` table whenever someone clearly out-threatens the target --, airborne/ranged response, preferred-range
movement, cluster-aware AoE) and one of the **26 boss-capable Experimental Powers** (v0.14.21: every mutation)
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

v0.14.21: `BossPowers` registers **all 26 mutations**, each running several of its revamped abilities (the full
table is in `docs/ZOMBIE_RAID_REFERENCE.md`), including mutations players cannot currently obtain. The pre-v0.14.21
exclusion list is gone. `supervillainAllowedPowers` /
`supervillainBlockedPowers` in the config further narrow the pool.

## Commands (op 2)

| Command | Effect |
|---|---|
| `/supervillainraid start` | Mark the nearest village and skip the countdown straight to Wave 1 |
| `/supervillainraid mark [targets]` | Give the Supervillain's Mark to you (or the targets) |
| `/supervillainraid omen` | Turn your mark into the omen here, or make a running omen fire now |
| `/supervillainraid markvillage` | Mark the nearest village directly and run the full 10-minute timer (the pre-v0.14.21 trigger) |
| `/supervillainraid boss` | Skip to Wave 6 (the Supervillain), starting a raid first if needed |
| `/supervillainraid spy` | Spawn a Pillager Spy in front of you |
| `/supervillainraid clear` | Abort every active Supervillain Raid, clear this village's cooldown and your mark / omen |
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

## v0.14.21 -- the Spy marks the player (Bad Omen model)

User request: make the Pillager Spy mark the player and work like vanilla Bad Omen.

- **Mark** (`SupervillainMark.MARK`, `projecthero:supervillain_mark`): harmful, dark purple, 100 minutes
  (`markDurationMinutes`), its own 18x18 icon (`scratchpad/gen_v01421_spy_mark_icons.js`). Given by a Spy's hit
  on a player anywhere (`AFTER_DAMAGE`; a shield block does not count, a hit soaked to 0 damage does) **and** by
  killing a Spy (`PillagerSpy#die`, projectile kills credit the owner) -- both, so a power that dodges every
  bolt still has a way to be marked, which is why the v0.12.23 "firing counts" `performRangedAttack` hook is gone.
  Milk, death and anything else that clears effects clear it. Re-marking refreshes the duration.
- **Omen** (`SupervillainMark.OMEN`, `projecthero:supervillain_omen`): crimson, `markOmenSeconds` (30). A marked
  player standing in a village (`PillagerSpy.insideVillage`, checked every 10 ticks), non-Peaceful, with no
  Supervillain Raid within `minDistanceBetweenEvents`, swaps the mark for the omen; the spot and the fire time
  are kept in the `supervillain_omen_state` player attachment (vanilla keeps `raidOmenPosition`). When it runs
  out the raid starts there through `SupervillainRaidStarter.startFromOmen` -> `markVillage` (the same
  `EventManager.start` path, so the one-raid-per-area rule and every raid config value still apply, including
  the 10-minute preparation). If it cannot start (Peaceful by then, or a raid got there first) the mark is
  handed back. Milk/death during the omen drop it with no raid.
- Both effects are pure markers (`shouldApplyEffectTickThisTick` false); the logic runs from
  `END_SERVER_TICK` (`SupervillainMark.tick`) because mutating effects from `applyEffectTick` happens while
  vanilla iterates the effect map.
- **Spy AI**: targets any player not already marked / omened (still never villagers, still retaliates, still
  fights golems); after marking someone it drops them as a target. The spawner skips marked players.
- **Inventory tooltip**: `EffectDescriptionTooltipMixin` shows name + `effect.projecthero.<id>.desc` when you
  hover a Project Hero effect in the wide inventory effect list (vanilla already tooltips the narrow layout).
- **Migration**: a village already Marked for Attack is just a `SupervillainRaid` in its `COUNTDOWN` phase in
  `EventSavedData`; nothing about it changed, so it still runs its countdown and gets its raid.
- `/heroraid stop` clears every mark/omen; `/heroraid cleartimers` clears running omens.
- Tests: `V01421SpyMarkGameTests` (hit marks anywhere, kill marks, ordinary Pillager does nothing, milk clears
  mark and omen, village -> omen -> raid with the mark consumed and kept while a raid runs, Peaceful) and the
  `RaidRepeatGameTests` lifecycle now goes hit -> mark -> omen -> raid.
