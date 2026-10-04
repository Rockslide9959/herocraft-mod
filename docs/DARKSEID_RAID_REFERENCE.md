# Apokolips Invasion — the Darkseid Raid (v0.13.18)

An endgame co-op raid for up to 8 players. It never starts on its own: a player uses a **Boom Tube Beacon**.
Every number below is a default in `config/projecthero_darkseid.json` (`DarkseidConfig`).

## Flow

```
Boom Tube Beacon -> PREPARATION (12 s) -> INVASION_WAVE_1 -> _2 -> _3 -> _4 -> _5 -> DARKSEID_ENTRANCE (10 s)
  -> MOTHER_BOX_PHASE -> DARKSEID_PHASE_1 -(60%)-> DARKSEID_PHASE_2 -(25%)-> DARKSEID_PHASE_3 -> VICTORY
  (every online participant dead / waiting to re-enter at the same moment, at any point) -> DEFEAT
```

| Stage | What happens |
| --- | --- |
| Preparation | Titles "APOKOLIPS INVASION / THE LORD OF APOKOLIPS APPROACHES", red sky, raid bar countdown. |
| Waves 1-5 | Parademons through 2-9 Boom Tubes: W1 11 standard; W2 8 standard + 7 ranged; W3 5 elite + 2 brute + 6 standard + 7 ranged; W4 6 elite + 3 brute + 6 standard + 8 ranged; W5 8 elite + 4 brute + 6 standard + 9 ranged (solo counts, +35% per extra participant). Enemy cap 34. (v0.13.19; was 3 waves of 8/11/15, cap 26.) |
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
| Omega Beams | Target glows + "OMEGA MARK", 1.6 s charge | v0.13.19: 4 sharp zig-zag legs (~1 s), then home at 6.5-9.5 deg/tick for 3 s: sharp turns, cover (blocks stop them) |
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
Omega Core (guaranteed), 1-3 Omega Shards (60%), Mother Box (10%), Omega Relic (3%), 1500 XP.
v0.14.21 Apokolips plunder (`DarkseidRaidRewards.valuables`, one chat line): 32-48 lapis, 2-4 lapis blocks, 6-10 diamonds,
12-24 gold, 24-40 iron, 24-40 redstone, 12-20 amethyst, 10-20 emeralds, 3-5 golden apples, two enchanted books; 25% netherite
scrap (1-2), 10% ancient debris, 10% enchanted golden apple. The common part is scaled by `rewards.valuablesMultiplier`
(config v4, 0 = off) and by the configured invasion waves (60% for one wave .. 100% for five). Advancements
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
`/projecthero raid start|end|advancetimer darkseid`, `/projecthero raid darkseid status|enrage|stagger|attack <name>|boxes`.
`advancetimer` clears whichever of the five waves is running (after the last configured wave, Darkseid is next); `boxes`
(v0.13.19) makes the fight's Mother Box return happen now.

## v0.13.19 -- more invasion, winged Parademons, zig-zag Omega Beams, Mother Boxes that come back

Config `projecthero_darkseid.json` is now **configVersion 2**. `DarkseidConfig.load` migrates a v1 file: every value this
pass changed is moved to its new default (listed below); keys new in v2 arrive at their defaults; untouched keys keep what
the file says. (A file with no `configVersion` is still reset to defaults, as before.)

**Invasion.** Five waves instead of three (`raid.invasionWaves`, 1-5, default 5) with ~35% more Parademons in each old
wave, then two bigger ones (compositions in the table above; `raid.wave4*` / `raid.wave5*`). Enemy cap 26 -> 34. Waves 3-5
come through Elite tubes; wave 5 uses 6 ground + 3 air tubes. New stages `INVASION_WAVE_4` / `INVASION_WAVE_5` sit
between wave 3 and the entrance; the stage is saved by name, so a v0.13.18 save carries on (a save in wave 3 continues into
waves 4 and 5). The raid bar reads "Wave n/5", `status` shows "(n/5)", and `advancetimer` clears any of the five.
`DarkseidRaid.waveComposition(n)` / `waveCount()` are the single source of the wave table.

**Parademons.** v0.13.21: back to the v0.13.18 stats (standard 30 HP / 6, ranged 24 HP / 5 bolt, elite 60 / 10, brute
100 / 15; v0.13.19-20 ran +20% health / +15% damage, config v3 migrates a v2 file). Gunners never stand still to shoot: on the ground they strafe around their target at
`parademons.rangedPreferredRange` (10) using vanilla's strafe move-control (backing off inside 6.5 blocks, closing in
beyond 13.5, pathing in when out of sight or beyond 18); in the air they orbit it with a vertical bob. The strafe direction
flips every 1-3 s and on bumping into something; they fire on the move.
**Wings:** every variant (all four textures are recolours of one sheet) has a pair of bat-like leathery wings --
`left_wing`/`right_wing` (+ `_tip`) bones on the torso, each part a 1x1 bone strut plus a zero-thickness membrane. The
texture sheet grew to 64x128 (the skin and every old UV untouched in rows 0-63; the wings in 64-127; the glowmasks
extended to match). Their own GeckoLib `wings` controller plays `wings_flap` (0.5 s beat) while flying or falling and
`wings_fold` (span squeezed by bone scale, swept back, tips hanging) on the ground. Controller order is now main -> action ->
wings, and the attack/shoot clips key only the bones they move, so a gunner's shot shows while its legs keep strafing.

**Darkseid.** Shared attack cooldown `boss.globalCooldownTicks` 30 -> 19 (all phases scale it as before). Omega Beams
cooldown 11 s -> 7 s and weight 4.5 / 6 (ground / air) -> `abilities.omegaBeamWeight` 7 / `omegaBeamAirWeight` 8.5. Boom
Tube Reinforcements cooldown 26 s -> 20 s (`abilities.reinforcementCooldown` 400) and weight 2 -> 2.5
(`abilities.reinforcementWeight`, still +1 from phase 2, still only while fewer than half the cap are alive).

**Zig-zag Omega Beams.** Each beam first snakes through `omegaBeamZigZagTurns` (4) sharp legs of 4-7 ticks: each leg breaks
35-70 degrees off the straight line to the target, alternating sides in any plane, and snaps to its new heading (a small
spark at the kink). The legs always make progress toward the target, run 1.3x faster, never kink down into the floor next
to a grounded target, and pick another heading if the next ~4 blocks are solid. Within 6 blocks of the target, or after
the last leg, the beam homes exactly as before (turn-limited, blocks stop it, same damage). The whole bent path is drawn by
the Omega Beam's own renderer (`EnergyTrailRenderer` + `BeamDraw`): the beam keeps a 30-tick trail (bolts keep 14) and is
culled on its whole trail, not just its head. `OmegaBeamEntity.fire(..., zigzagTurns)`; the Omega Relic still fires
plain homing beams.

**Mother Boxes come back.** During phases 1-3, once every box is dark, a clock of `motherBoxes.fightReactivateMinSeconds`-
`fightReactivateMaxSeconds` (70-100 s, random) runs -- only while all four stay dark. When it expires:
`fightReactivateWarningSeconds` (5 s) of warning -- title "BOXES AWAKEN", chat, the Mother Box awaken sound, and the chosen
boxes rise, spin up, flicker and spark (synced `MotherBoxEntity.waking`, name "REAWAKENING") with a red line from Darkseid --
then 1 box (phase 1), 1-2 (phase 2) or 2 (phase 3) power back up ("BOXES ONLINE"). They are ordinary active boxes again:
each takes 25% off the damage he takes (the existing rule outside the shield phase), each runs the 90 s neglect/overload
clock, and players channel them down the same way. Darkseid's boss bar shows "MOTHER BOX SHIELD n%", the raid bar
"n Mother Box(es) online (-n% dmg)" (and a countdown during the warning), and he gets one orbiting shield mote per
active box. The soft enrage's own reactivation (every 75 s) is unchanged and skips boxes already picked. Saved: `BoxReturn`,
`BoxWarn`, `BoxWarnTotal`, `BoxPicks` (absent in old saves = clock starts fresh). `fightReactivateEnabled` turns it off.

New lang keys: `title.projecthero.darkseid_raid.wave4.sub`, `.wave5.sub`, `.boxes_stir(.sub)`, `.boxes_online(.sub)`;
`message.projecthero.darkseid_raid.boxes_stir`, `.boxes_online`; `bar.projecthero.darkseid_raid.fight_boxes`,
`.enraged_boxes`, `.boxes_stir`; `entity.projecthero.mother_box.waking`. `bar.projecthero.darkseid_raid.wave` now takes
(wave, total, count). The guide's waves / Mother Boxes / phase 1 pages mention the changes.

Tests (`DarkseidRaidGameTests`): five waves each bigger than before; new wave stages save/load by name; a v1 config file
migrates; Parademons +20%/+15%; a zig-zag beam snaps heading at least twice while closing in; a gunner fires while it
keeps moving; Mother Boxes come back after the delay, warning first, 2 in phase 3 and 1 in phase 1, and the clock stops
while a box is on.
