# The Ultron Contagion (v0.16.1)

"You can't kill an idea." A few in-game days after Ultron's first defeat in a world he comes back as an **infection that
spreads through the world**, and it ends in a sky-citadel fight against **Ultron Omega**. Overworld only, never on
Peaceful.

Code: `com.projecthero.mod.ultron.contagion` (server) and `com.projecthero.mod.client.ultron.contagion` (client).
Tuning: `config/projecthero_contagion.json` (`ContagionConfig`, VersionedConfig v1). Gametests:
`UltronContagionV0161GameTests`. World state: `data/projecthero_contagion.dat` (`ContagionState`) and
`data/projecthero_ultron_world.dat` (`UltronWorldState.firstDefeatTime`).

## 1. How it starts (`Contagion`)
| Stage | What happens |
|---|---|
| `IDLE` | Waiting for `UltronWorldState.firstDefeatTime >= 0` (Part A records it via `recordDefeat` on the first Sentry kill). |
| `SCHEDULED` | Starts `delayDaysMin..delayDaysMax` (3-6) days + a random part of a day after the first defeat. |
| `PATIENT_ZERO` | 20 s of red flicker at: a placed **Ultron Core** trophy, else a **Stark machine** (Fabricator, suit platform, Gantry floor, Sorting Station, Stark Furnace/Smelter/Smoker), else a ground spot 80-140 blocks from a player who has cleared an Uprising (else any online player). Only loaded chunks are searched. Then the first Nexus grows 2-6 blocks away and machines within 6 blocks are Compromised. |
| `ACTIVE` | The infection (sections 2-6). |
| `FINALE` | The Sokovia Protocol (section 7). |
| `DORMANT` | Omega was beaten. Resurgence (section 9). |

Announcement: every Tony Stark gets JARVIS `contagion_start` (red, glitched text); everyone else the title
**THE CONTAGION**; Ultron in chat to everyone: "Did you really think deleting a body would delete me?".

## 2. Saturation (one value per world, 0-100%)
- **+1%** per standing, grown Nexus in an entity-ticking chunk, every 2 min (`saturation.risePerNexus` / `riseIntervalTicks`).
  A Nexus in an unloaded chunk does not count.
- **-5%** when a Nexus falls; **-1%** per fuelled Firewall Beacon every 2 min while under 90%.
- **25%**: a new Nexus every 15 min instead of 25. **50%**: iron golems in the corruption become Ultron Golems; newly
  Assimilated mobs get +25% max health. **75%**: a Hunter-Killer squad (3 drones + 1 Heavy, 18-28 blocks out, loaded spots
  only) every 10 min at a random player. **100%**: the Sokovia Protocol starts on its own.
- Shown on a red **Hairline** across the top of every Overworld player's screen ("37% | 3 Nexus | Trace 1/3").

## 3. Ultron Nexus (`UltronNexusEntity`)
| Stat | Value |
|---|---|
| Size | 2.2 x 9 blocks, GeckoLib `geo/ultron/ultron_nexus.geo.json` (+ glowmask, idle clip: rotating ring, pulsing eye) |
| Health | 800 (x version scale), 8 armour, shield blocks all damage until Overridden |
| Growth | 2 min out of the ground (the renderer sinks it), red beam up to y 260 while growing |
| Spawns | every 25 min (15 at 25%+), 64-160 blocks from a random online player, max 4 (+1 per version), loaded + entity-ticking ground only, never in a fuelled Firewall Beacon's radius (the dome flickers), never within spawn protection + 32, never within 48 of another Nexus |

**Corruption** (`NaniteCorruption`): radius grows 1 block every 10 s (`radiusGrowTicks`) to 32. Each tick a Nexus
examines 6 columns (nearest first, a precomputed spiral) and changes at most `blocksPerTick / nexusCount` blocks (24
total). In a column, from the top down: air / corruption skipped; tagged natural blocks converted; see-through
non-solids (grass tufts, flowers, snow layers) looked under; anything else stops the column (a player's floor shields
what is below). Two ground blocks per column. Rules:
- only `#projecthero:contagion_corruptible` (grass, dirt variants, moss, mud, stone family, deepslate, tuff, calcite,
  sand, red sand, gravel, sandstone, terracotta, snow block, the 8 natural logs, `#minecraft:leaves`);
- never a block entity; never `persistent=true` leaves (player-placed); never within 4 blocks of any block entity
  (`blockEntityClearance`: bases stay clean); never inside a fuelled Firewall Beacon's radius;
- blocks set with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` (no neighbour updates: nothing falls, pops or decays);
- the original state is recorded (`ContagionState` palette + `long` position -> palette index) **before** the change.

Corrupted blocks: `corrupted_circuitry` (ground), `corrupted_circuitry_stone`, `corrupted_circuitry_log` (keeps the
axis), `corrupted_circuitry_leaves` -- animated red veins, emissive rendering, light 4, no drops.
`terrainCorruption: false` = particles only.

**Revert**: the record is queued nearest-first (a blue wave); `revertPerTick` (300) per tick, loaded chunks only, only
where the block is still the corruption's own (a block a player broke or replaced stays as it is). Unloaded entries wait
in the persisted queue. A Purge Charge reverts its radius immediately.

## 4. Inside the corruption (`Assimilation`, `SuitHijack`)
- **Assimilated** (synced attachment `projecthero:assimilated`): zombies (not zombified piglins), skeletons, spiders,
  creepers, pillagers that spawn/load inside, plus a sweep every 2 s. +4 armour, poison immunity
  (`ContagionPoisonImmunityMixin`), a 4-damage red bolt at 16 blocks (about every 4 s), archers' arrows become red bolts,
  creepers get a 15-tick fuse and explode in an EMP that drains 25% of a suit's energy within 5 blocks. 20% drop Ultron
  Scrap. Client: plating overlay + emissive eyes (`AssimilatedLayer`, `textures/entity/ultron/assimilated/*`).
- **Ultron Golems** (`projecthero:ultron_golem`): from 50% Saturation an iron golem converts after 10 s inside: hostile,
  150 health, a magnetic slam (pull + 10 damage + knock-up, every ~6 s when a player is within 5.5). Reverted when the
  Nexus falls or by a Purge Charge (keeps its health, capped).
- **Compromised Stark machines**: right-click refused ("COMPROMISED"), red particles, an Ultron Drone every 60 s (max 3
  alive) while the chunk is ticking. Freed when their Nexus falls or by a Purge Charge.
- **Villagers**: `HEARD_BELL_TIME` every sweep (they hide as if a bell rang); trading refused.
- **Iron Man**: inside, a 1 s red static flash every 30-60 s, JARVIS sometimes speaks with Ultron's voice
  (`contagion_ultron_1..4`). Once per Nexus visit (after 5 s inside) a **Hijack**: a red "OVERRIDE ATTEMPT" Hairline fills
  over 4 s. Leaving the corruption, 6 suit-key presses / melee hits, a Firewall Chip, the JARVIS Firewall protocol or a
  fuelled beacon breaks it. Completed: the suit locks for 5 s (no flight, no abilities -- hooks in
  `IronManAbilityManager.handle` and `IronManFlight.toggle`), then JARVIS recovers it.

## 5. The Override capture
Override Spike on a grown Nexus (`startCapture`): a 10-block circle (cyan particle ring, the lit part shows progress).
The timer (60 s) runs only while a living survival player stands inside (horizontal 10, +-8 vertical): x1 for one, x2 for
two or more, x1.4 more with a Mind Stone in anyone's off hand (`UltronNexusEntity.rate`). Waves every 15 s while a player
is within 48 (2 drones, a Sentinel Drone, a Lancer and a Hijacker -- see section 11 -- up to 14 alive), a Heavy at 50%, an
**Ultron Prime echo** (600 health, no pylons to jump to) at 80%. Done: the shield flips blue (`SHIELD_BLUE`), then break it.
Its fall (`Contagion.onNexusDestroyed`): Assimilated mobs inside die (scrap), golems revert, machines come back, the blue
wave, -5% Saturation, +1 Trace (JARVIS `contagion_trace` / `_trace_3`), drops 3-6 Ultron Scrap + 1 Nanite Sample.
The Mind Stone's existing off-hand outline already reveals cloaked Hijackers (outlines draw through invisibility).

## 6. Items and blocks
| Item | Recipe | Use |
|---|---|---|
| Ultron Scrap | drop | material |
| Nanite Sample | Nexus drop | material |
| Purge Charge (x4) | `SCS / RER / SCS` scrap, copper ingot, redstone, sculk sensor | thrown EMP, 6 blocks: reverts recorded blocks, stuns robots 3 s, frees machines, strips Assimilated, reverts golems, powers down Hijacked Marks |
| Firewall Beacon | `NAN / IVI / III` nanite, arc reactor, vibranium plating, iron blocks | redstone block = 1 day (Sneak = whole stack, cap 64 days); 48-block radius immune to corruption, Nexus spawns and hijacks; -1% Saturation / 2 min; blocks Resurgence; blue hex dome flicker when probed (`FirewallBeaconRenderer`) |
| Override Spike | `SLS / SNS / _R_` scrap, lightning rod, nanite, reactor core (or redstone block) | starts a capture (consumed only if it starts) |
| Firewall Chip | shapeless: nanite, stark circuit, 2 scrap, quartz | smithing (no template) into an Iron Man helmet: that suit is hijack-immune (`projecthero:firewall_chip` component; tooltip + I-key spec sheet) |
| Vibranium Ingot | Omega reward | material |
| Vibranium Plating II | `_V_ / VPV / _V_` ingots round Vibranium Plating | smithing on an Iron Man piece: +3 armour / +2 toughness, **replaces** plating I (modifiers and component swap; plating I can't go back on) |
| Vibranium Shield | `VVV / VSV / _V_` ingots round an Adamantium Shield | extends `AdamantiumShieldItem`: blocks, unbreakable, Super Soldier C throw 12 dmg / 5 bounces / 30 blocks (Adamantium 9 / 4 / 25); drawn by `AdamantiumShieldRenderer` with `textures/entity/vibranium_shield.png` |
| Omega Core (`ultron_omega_core`) | Omega reward | trophy: Omega's head on a pedestal, eyes slowly dimming (`OmegaCoreRenderer`). (`omega_core` was taken by Darkseid's item.) |

## 7. The Sokovia Protocol (`SokoviaFinale`, `SkyCitadel`)
- **Trigger**: 3 Traces or 100% Saturation. JARVIS `contagion_found`, title, chat coordinates. Site: 300-600 blocks from
  the online players' average position (column only -- nothing is loaded to look).
- **MARKED**: a red diamond + distance on everyone's HUD compass strip. Starts when a player is within 64 (and the site
  is loaded) or after 1 in-game day. If the day runs out with the site unloaded: **AWAY** -- the Core charges with nothing
  built; 100% = loss.
- **BUILDING**: ground = the heightmap at the site (clamped so the citadel fits under the build limit); floor at
  ground + 90. The disc (radius 25, ragged broken edge, plating centre + 8 spokes, rock outside, dais, parapet bits, 8
  pillars, inverted hanging-rock cone up to 16 deep) goes up 500 blocks a tick, only into air, every placed block recorded.
  The rising-rock path (2x1 steps, +1 up per step, 30 degrees round a 4-block spiral 32 blocks east of the centre, then a
  bridge) builds 2 blocks a tick. The red marker beam (`CitadelMarkerEntity`) stands on the ground: right-click once for
  the **Stark Lift** (blue beam, carried up then onto the edge).
- **FIGHT**: Vibranium Core (`VibraniumCoreEntity`, invulnerable, GeckoLib), 4 Lift Engines (500 health, shielded while their
  guard Heavy lives, -15% charge each), Ultron Omega after 3 s. Core charge 0 -> 100% in 12 min (boss bar "Vibranium Core:
  N%"), frozen during the Vision moment. Fighters: anyone within 64 joins; a fighter who dies is down until they come back
  within 64; all down = loss.
- **Safety net**: a non-flier (no `mayfly`/`flying`/elytra) falling below the floor for 2 s is put back on the edge at
  the same angle, 6 fall damage, Slow Falling 5 s.
- **ENDING**: every Contagion unit near the citadel is removed, fighters get Slow Falling 30 s, placed blocks are removed
  400 a tick (only blocks still ours, loaded chunks only; the finale stays until the list is empty). Loss: debris column,
  impact, a **cosmetic crater** (radius 12, natural top blocks -> Citadel Rock / blackstone, recorded, reverted after 5 min;
  `finale.craterDecal: false` = particles only), Saturation 50%, Traces 0. Escape: Saturation 50%, Traces 2. Win: below.

## 8. Ultron Omega (`UltronOmegaEntity`, `OmegaAnims`)
4 blocks, flies, 4000 + 900 per extra fighter (cap 12000) x (1 + 10% per version above 1), 16 armour / 10 toughness, no
adaptation, `BossThreat` (range 80, holds while a move runs), own boss bar (decoys have none), lines
`boss.projecthero.ultron_omega.say.*`. Kept over the citadel (radius 22, floor +1..+14).
| Phase | Moves |
|---|---|
| 1 (100-75%) | Vibranium Strike (dash, hits at 12/22/36 for 8/8/14 in a 3.8-block front arc), Concussive Blast (cone 10 blocks, 12 dmg, thrown away from the citadel centre), Encephalo Lattice (20-tick telegraph, then 3 beams at 120 degrees rotating 90 deg/s for 4 s, 4 dmg / 5 ticks), Core Siphon (every 60 s: flies to the Core top, channels 5 s, +5% charge; 8% of his max health dealt in the channel interrupts and stuns him 2 s) |
| 2 (75%) | Split: the real one and 3 decoys appear on a 7-block ring round the centre. Decoys: same model/health bar-less, 50% damage, never take a hit, every hit on one heals the real one 0.5%. Tells (client only): Mind Stone holders see only the real one outlined gold (`clientGoldOutline` hook; decoys never outlined for them), the Iron Man scanner names decoys "Signal Echo", the real one's glowmask pulses with the Core (once a second), decoys burn steady. 10% of his max health dealt to the real one collapses them. |
| 3 (50%) | Iron Legion: 3 + fighters Hijacked Marks (150 hp, repulsor 5, unibeam 2 s telegraph then 14) using the real Mark armour models (Mark 2/III/4/5/6 random; one copy of each suited fighter's own Mark); glowmasks tinted red (`HijackedMarkRenderer.legion` -> `IronManSuitGlowLayer`). Shielded while 3+ stand. A Purge Charge powers one down (1 scrap, never the armour -- the entity carries no items). |
| 4 (25%) | Upload: 3 Uplink Antennae (300 + 100/extra fighter) rise; 35 s UPLOAD bar; he hovers above the centre shielded, 2 drones every 3 s (max 8 near), a blast every 4.5 s. All antennae down: **stranded** (no shield, x1.5 damage taken, 45% shorter gaps). Timer runs out: he escapes (partial loss). |
| 0 hp | **Vision moment**: he kneels (invulnerable, gold screen tint). A fighter holding a Mind Stone within 32 holds right-click 3 s (`MindStoneBeam`, client reports the hold with `ContagionNet.MindHold`): the golden beam, he disintegrates (60 ticks). No Mind Stone: after 10 s he self-destructs (3 s red ring, 6-block blast up to 20 damage). Both are wins. |

### OmegaAnims timing table (TRANSITION = 4)
| clip | length | loop | server tick(s) | clip key tick(s) |
|---|---|---|---|---|
| idle | 40 | loop | - | - |
| fly | 20 | loop | - | - |
| walk | 20 | loop | - | - |
| vibranium_strike | 44 | once | 12, 22, 36 | 8, 18, 32 |
| concussive_blast | 30 | once | 18 | 14 |
| lattice | 100 | once | beams 20..100 | windup 0..16 |
| siphon | 100 | loop | +charge at 100 | - |
| split | 30 | once | 20 | 16 |
| override | 40 | once | 24 | 20 |
| upload | 40 | loop | - | - |
| hurt | 10 | once | - | 2 |
| stagger_kneel | 30 | hold | - | - |
| disintegrate | 60 | hold | dies at 60 | - |
| self_destruct | 60 | hold | blast at 60 | 56 |
Bones `neck` and `flinch` are never keyed. The model is a **placeholder** (`scratchpad/gen_ultron_omega_placeholder.js`)
until the concept model is approved; any replacement must keep the file names, bone names and this table.

## 9. Rewards and Resurgence
- **Win** (`ContagionRewards`): a chest on the ground under the Core: Vibranium Ingots x (2 + fighters), the Omega Core,
  Ultron Scrap 6-12, netherite scrap 2-4, diamonds 8-16, emeralds 16-28 (party scaled), 2 level-30 books.
  **First ever win** (`projecthero:omega_wins`, persistent): the Mind Stone is empowered (`projecthero:mind_stone_empowered`
  component; a fresh empowered one if none): Sneak + hold right-click = Mind Stone Beam (2 s, 6 dmg / 5 ticks, 32 blocks,
  60 s cooldown), the charm still works without Sneak. Tony Starks also get the JARVIS Firewall protocol
  (`projecthero:jarvis_firewall`, synced to the owner, every suit hijack-immune). The world: every Nexus collapses, every
  record reverts, golems and machines are restored, Saturation 0, stage `DORMANT`.
- **Resurgence** (`resurgence.*`): every 20 days a 15% roll -- never while any Firewall Beacon in the world is fuelled.
  It brings "Ultron v2" (up to v5): +20% Contagion unit health, +1 max Nexus, +10% Omega health and damage per version;
  the Contagion restarts 30 s later ("Version N.0. Faster. Stronger. Still me.").

## 10. Commands (op)
`/projecthero contagion start | stop | status | saturation <0-100> | trace | nexus | finale | resurge` (registered on
its own and merged into the `/projecthero` root). `stop`: the finale is torn down (every placed block), every Nexus record
queued for revert, every loaded Contagion unit removed (the rest remove themselves when they load: the `projecthero_contagion`
tag orphan guard), machines freed, stage `DORMANT` (beaten before) or `IDLE` (and the first-defeat time cleared, so it
does not reschedule itself).

## 11. Engineering notes
- **Never force-loads**: spread waits on unloaded columns; reverts / citadel removal skip unloaded blocks and come back;
  spawns check `isLoaded` / `isPositionEntityTicking`; the finale's site is only built when loaded.
- **Never touches player blocks**: tag + no block entity + 4-block block-entity clearance + persistent leaves +
  first-solid stop; reverts only change blocks that are still the corruption's / citadel's own.
- **Lancers / Hijackers** (Part A) are used through `ContagionUnits.lancerOrDrone()` / `hijackerOrDrone()`: registry lookups
  of `projecthero:ultron_lancer` / `ultron_hijacker`, drones if unregistered.
- **Static state** (all in `ServerStateReset.clearAll`): `SuitHijack.STATES`, `MindStoneBeam.HOLDS`, the
  `NaniteCorruption` revert cursor. Everything else lives in `ContagionState` or on entities.
- **Private highlight rule**: the Mind Stone gold outline is `UltronOmegaEntity.isCurrentlyGlowing/getTeamColor`
  overridden client-side only (server side returns vanilla).
- Packets: `contagion_hud` (S2C every 10 ticks), `contagion_beam` (S2C coloured beams), `mind_stone_hold` (C2S).
- Guide chapter `CH_ULTRON_CONTAGION` = 34 (`CHAPTER_POWER_BASE` = 35; 33 = Frost Giants).
