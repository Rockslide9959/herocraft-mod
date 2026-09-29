# Apokolips Invasion — the Darkseid Raid (v0.13.18)

An endgame co-op raid for up to 8 players. It never starts on its own: a player uses a **Boom Tube Beacon**.
Every number below is a default in `config/projecthero_darkseid.json` (`DarkseidConfig`).

## Flow

```
Boom Tube Beacon -> PREPARATION (12 s) -> INVASION_WAVE_1 -> _2 -> _3 -> DARKSEID_ENTRANCE (10 s)
  -> MOTHER_BOX_PHASE -> DARKSEID_PHASE_1 -(60%)-> DARKSEID_PHASE_2 -(25%)-> DARKSEID_PHASE_3 -> VICTORY
  (every online participant dead / waiting to re-enter at the same moment, at any point) -> DEFEAT
```

| Stage | What happens |
| --- | --- |
| Preparation | Titles "APOKOLIPS INVASION / THE LORD OF APOKOLIPS APPROACHES", red sky, raid bar countdown. |
| Waves 1-3 | Parademons through 2-5 Boom Tubes: W1 8 standard; W2 6 standard + 5 ranged; W3 4 elite + 2 brute + 4 standard + 5 ranged (solo counts, +35% per extra participant). Enemy cap 26. |
| Entrance | Lightning, a giant Boom Tube, Darkseid steps out (invulnerable during it), four Mother Boxes spawn 24 blocks N/E/S/W. |
| Mother Box phase | Darkseid immune while any box is active; shield = active boxes / 4. Limited kit (fists, slam, barrage, grip -- grip prefers channellers). |
| Phase 1 | Full kit. |
| Phase 2 (60%) | "OMEGA EFFECT": x1.2 damage, x0.8 cooldowns, faster, Omega Beam Sweep, 2 tubes per reinforcement. |
| Phase 3 (25%) | "OMEGA RAGE": x1.4 damage, x0.62 cooldowns, x1.35 knockback, two sweep beams, Omega Annihilation every 45 s, End boss music. |
| Victory | Parademons recalled, 5.5 s death (kneel, energy tears loose, burst, Boom Tube, fade), "VICTORY / APOKOLIPS HAS FALLEN", rewards. |
| Defeat | "DEFEAT / APOKOLIPS HAS CLAIMED THIS WORLD"; Darkseid leaves through a Boom Tube. |

## Participants, arena, death
- Roster: survival players within 48 blocks of the beacon (nearest first, max 8). Anyone entering the 64-block arena joins
  until Darkseid reaches **50% health**; then the roster is sealed (late arrivals can fight, no rewards).
- Creative/spectator players are never participants or targets.
- Leaving the arena: warned, then pulled back after 6 s (flight is never disabled). Beyond 140 blocks you have left on
  purpose and are no longer pulled or chased.
- Death: respawn normally; you cannot re-enter for 25 s (you are set down at the edge). Roster growth before the seal
  rescales Darkseid's health (fraction kept).
- Health: 3000 + 600 per participant beyond the first (above vanilla's 1024 cap via `TitanHealthCap`).

## Mother Boxes
- Right-click, stay within 4.5 blocks for 10 s. Hits don't interrupt; walking away does. Progress holds 3 s then bleeds.
- 90 s without being channelled -> **overload**: Darkseid heals 6% max HP, 12-damage blast (7 blocks), a Boom Tube with 3
  Parademons, a 12 s burning zone (4 dmg/s). Never a wipe. Any overload forfeits the *Apokolips Falls* advancement.

## Darkseid's attacks (`DarkseidCombat`)
| Attack | Tell | Counterplay |
| --- | --- | --- |
| Melee hook / backhand / 3-hit combo | Wind-up clip | Back off; hits land on the clip's impact frame |
| Godly Ground Slam | Orange ring = its reach, 26-tick wind-up | Leave the ring; reaches 10 blocks up (60% damage airborne) |
| Omega Beams | Target glows + "OMEGA MARK", 1.6 s charge | Beams turn 6.5-9.5 deg/tick for 3 s: sharp turns, cover (blocks stop them) |
| Omega Barrage | Warning circles on you and where you're heading | Leave both circles (air positions too) |
| Darkseid's Grip | Purple line, 1 s | Break line of sight; team deals 2% max HP to free the victim |
| Omega Teleport | Portal particles | He comes to far/high targets (never beyond 96 blocks of the arena) |
| Apokoliptian Charge | Lane drawn on the ground, 1.2 s | Step out; lane is locked; he flinches if he hits a wall |
| Boom Tube Reinforcements | Arm raised | Kill the Parademons; respects the cap |
| Omega Beam Sweep (P2+) | Warning lines, 1.5 s | Knee-high beam: jump, fly, or hide behind blocks |
| Omega Annihilation (P3) | Title + marked target, 5 s charge, boss bar shows interrupt % | Deal 4% max HP to interrupt (6 s stagger, +30% damage taken); else arena blast (40 to target, 60% falloff to others, halved behind cover) |

Soft enrage after 15 min with Darkseid: +15% damage and x0.88 cooldowns per step (a step every 150 s), faster, a disabled
Mother Box reactivates every 75 s (each active box = 25% damage reduction outside the shield phase), a reinforcement tube
every 30 s.

## Rewards (per rewarded participant)
Omega Core (guaranteed), 1-3 Omega Shards (60%), Mother Box (10%), Omega Relic (3%), 1500 XP. Advancements
`projecthero:darkseid/anti_life` and `darkseid/apokolips_falls` (no overloads).
- **Mother Box**: sneak-use to attune, use to Boom Tube there (any dimension), 2 min cooldown.
- **Omega Relic**: 24 charges of paired homing Omega Beams (12 damage), 8 s cooldown; unenchantable, unrepairable.
- Recipes: beacon (tokens or shards), Mother Box (Omega Core + 4 shards + 4 netherite scrap).

## Architecture
- `darkseid.raid.DarkseidRaid` -- the manager, an `EventInstance` (framework persistence, owned-mob cleanup, pause/abandon).
  Registered as `darkseid_raid` in `EventTypes`. Owns stages, `DarkseidRoster`, boundary, waves/Boom Tubes, Mother Box
  neglect/overload, shield value, phase changes, enrage, bars, sky, music, rewards, cleanup.
- `darkseid.entity.DarkseidEntity` -- body, shield/stagger damage rules, synced phase/shield/busy/omega/sweep, boss bar,
  entrance, cinematic death; tells the raid when he dies. `DarkseidCombat` -- targeting (raid participants only), weighted
  attack choice, every attack script. `DarkseidAnims` -- clip names and tick timings (checked by the asset generator).
- `ParademonEntity` (4 variants), `MotherBoxEntity`, `BoomTubeEntity` (visual only, never saved), `OmegaBeamEntity` /
  `ParademonBoltEntity` (`EnergyProjectile`, client-drawn trails).
- Cleanup: victory/defeat/abort/`/projecthero raid end darkseid`/server stop all go through `DarkseidRaid.cleanup`; a server
  stop ends every invasion and refunds the beacon. Every raid entity has an orphan guard (removes itself if its raid is gone).
- Models: the user's `3d minecraft models/darkseid/{darkseid,parademon}.bbmodel` skins, split at elbows/knees
  (`scratchpad/darkseid/gen_darkseid_assets.js`). Sounds are vanilla files re-pitched (`gen_sounds.js`). Data/lang:
  `gen_darkseid_data.js`.

## Admin / test commands (op 2)
`/projecthero raid start|end|advancetimer darkseid`, `/projecthero raid darkseid status|enrage|stagger|attack <name>`.
