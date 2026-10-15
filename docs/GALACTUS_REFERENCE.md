# Galactus: The Devourer (v0.16.1)

The endgame world event. A cosmic signal reaches the Devourer of Worlds: his Herald comes first, then his Worldship, then
Galactus himself -- sixty blocks tall and impossible to kill. The only way to win is the **Ultimate Nullifier**.

**Progression / threat ladder:** the top rung -- after the Frost Giant Assault and the Ultron Contagion (the cosmic signal
only finds a world that has beaten one of them). Bring a party, flight and ranged damage.

Code: `com.projecthero.mod.galactus` (server) and `com.projecthero.mod.client.galactus` (client). Tuning:
`config/projecthero_galactus.json` (`GalactusConfig`, VersionedConfig v1). Gametests: `GalactusGameTests`.
Timing contract: `GalactusAnims`. Placeholder assets: `scratchpad/gen_galactus_placeholders.js`. Surfer skin:
`scratchpad/gen_galactus_surfer_skin.js`. Lang: `scratchpad/lang_v0161_galactus.js`. Screenshot harness:
`scratchpad/V0161GalactusHarness.java.txt` + `V0161GalactusCamMixin.java.txt`.

## 1. Starting it
- **Herald's Beacon** (block, shaped recipe `heralds_beacon`, late-game cost):
  ```
  E N E      E = end crystal, N = netherite ingot, A = amethyst block,
  A S A      S = nether star, O = crying obsidian, B = beacon
  O B O
  ```
  Place it, right-click **at night** (`trigger.beaconNightOnly`), in Survival, never on Peaceful. It becomes the arena's
  centre (`ACTIVE=true`, light 15, unminable while it runs), the reward chest on a win, and burns out on a loss.
- **The cosmic signal** (`trigger.naturalTriggerEnabled`, **on** by default): once per in-game day (overworld day
  counter, `GalactusWorldState.lastRollDay`) a `trigger.chancePerDay` (0.05) roll, only if the world is **unlocked**
  (`ContagionState.omegaBeaten`, or a Frost Giant Assault won -- `GalactusWorldState.frostBeaten`, set by the assault's
  win), never on Peaceful, never while one runs. It picks a random eligible online player (survival, not already in an
  event), finds natural ground 12-28 blocks away (no block entity within 6), places a Herald's Beacon and starts.
- `/projecthero raid start|end|advancetimer|advance galactus` (op; `advance` is a new alias of `advancetimer` for every raid).
  `start` places a beacon at your feet (any time of day). `end` aborts every Galactus event (everything put back).
  `advance`: arrival -> the Herald -> he turns -> the Worldship -> one converter falls (repeat) -> Galactus -> the pylons
  fall -> the weak points break -> the Nullifier is put in your hands -> it is used -> the chest.
- `trigger.enabled = false` switches the whole event off (beacon, signal and command).
- One at a time per `minDistanceBetweenEvents` (256).

## 2. The arena
- Radius **80** (`arena.radius`), edged every half second by violet / silver dust and full-bright end-rod points (forced,
  long range, `arena.borderPoints` cap). Fighters are kept in (pulled back up to 48 past the edge; a rider is left alone);
  anyone who walks in joins; a fighter who dies is **down** until they walk back in; all down at once = a loss.
- **The sky**: the shared raid tint (`ZombieRaidNetworking.pushSky`, deep violet `0x2B0B4F`; near-white while the world is
  consumed) plus `GalactusSkyPayload` -> `GalactusSkyClient`: the **stars shine in daylight** inside the arena
  (`GalactusStarsMixin` on `ClientLevel.getStarBrightness`), fading over 24 blocks past the edge. Unlike the raid sky's
  90-block murk, the fog stays at the render distance (start pulled in to 45%) so the 60-block Galactus and the Worldship
  stay in sight (`FogRendererMixin`).
- Bars: the event bar (purple, notched: arrival countdown / the Herald / World Consumption % + converters destroyed / +
  pylons and weak points / the Nullifier) and a boss bar (the Silver Surfer in Act 1, Galactus in Act 3).

## 3. Act 1: the Herald (the Silver Surfer)
A **player model** (user decision: the standard player rig, wide arms, hat/jacket/sleeve/pants layers) wearing the user's
chosen skin -- **Planet Minecraft skin 6740557, "Silver Surfer"** (credit to its author on Planet Minecraft), at
`textures/entity/galactus/silver_surfer.png` with a 5-pixel eye glowmask (`silver_surfer_glowmask.png`). He stands on his
board (the GeckoLib `surfboard_mount` model, drawn under his feet). Hit-box 0.8 x 2.2. Health 600 + 150 per extra fighter,
8 armour. Code-driven poses (`GalactusRenderers.SurferModel`): the surf stance (feet apart along the board, arms out),
blast (right arm thrown at the target), dash (leaning, arms swept back), shield (forearms crossed), lift (both arms up),
the turn (head bowed, then the right hand raised) and the ally's easy stance; synced as a pose index
(`GalactusAnims.SURFER_POSES`).

He circles his target (`herald.preferredRange` 14, `hoverHeight` 7), one move at a time every ~2 s, targets from the shared
`BossThreat` table (else the nearest fighter):
| Move | Timing (ticks: length / hit) | Effect |
|---|---|---|
| Power Cosmic blast | 24 / 14 | a dodgeable bolt (`CosmicBolts`, 1.4 blocks/tick, 40 range) for 9 |
| Light-speed dash | 34 / 24 | a silver line is drawn for 1 s, then he crosses 28 blocks in 6 ticks: 14 to anything on it, knockback |
| Reflective shield | 70 / 8 | 3 s: projectiles bounce back (50% of their damage to the shooter), melee does 30% |
| Cosmic lift | 40 / 20 | a violet ring under the target, then Levitation X for 24 ticks (and the fall) |

**He can't be killed in the duel**: his health never drops below 10% (`turnAtFraction`). There he stops and **turns**
(`TURNING`, 6 s, three lines of dialogue), then is an **ALLY**: protected (only `/kill` hurts him), allied to players.
As an ally he orbits the nearest fighter, bolts the nearest Punisher every 2 s (16), bubbles a fighter under 30% health
with Resistance II + Absorption II (every 30 s), and in the Nullifier step hovers over the beacon with a pillar of light.

## 4. Act 2: the Worldship and the Element Converters
- **Taa II, the Worldship** (render-only `WorldshipEntity`, 110 blocks up, never saved -- re-summoned).
- **Element Converters**: 3, +1 per two extra fighters, at most 5 (`converterCountFor`), on a ring of 46, crashing down
  from 70 blocks over 2 s. 400 health (+100 per extra fighter), 10 armour. Each has **2 Punisher guards** and is
  **shielded** (blue sparks, no damage) while any of its guards lives.
- **The consumption**: a landed converter's radius grows 1 block every 1.5 s up to **16**; it walks the columns nearest
  first (6 columns a tick each, 48 blocks a tick all together) and turns natural ground into grey **Consumed Ground** --
  see section 6 for the rules. `arena.terrainDamage = false`: particles only.
- **World Consumption**: +5%/min per landed converter (Act 3: +1.5%/min per standing Power Pylon). Titles at 50 / 75 / 90%.
  **100% = the planet is consumed**: a 7 s cinematic (white-violet sky, shakes, flashes, Galactus' line) then a **loss**:
  everything reverts and the event ends.
- A destroyed converter drops an **Ultimate Nullifier Part** (invulnerable, never despawns) and the ground it ate comes
  back (its ledger group is reverted). A converter that vanishes some other way counts as destroyed after 30 s.

### Punishers (~8 blocks)
220 health (+40 per extra fighter), 10 armour, speed 0.24. Punch up close (12, knockback), a blaster-gauntlet bolt at 6-28
blocks (8, 3.5 s cooldown; clip 30 / 18), and a ground slam telegraphed by a violet ring (14 in 5.5 blocks, clip 40 / 28).
Guards stay within 18 blocks of their converter unless a fighter is right there. Act 3 summons: 2 a cast, 6 alive at most.

## 5. Act 3: Galactus
- **60 blocks from the top of his head to his toes** (user decision -- the helmet prongs / antennae are not counted:
  bones starting `crest`, `prong`, `antenna`, `horn` are left out of the fit, `GalactusModelFit`). Render scale only: his
  body hit-box is **10 x 40**. He arrives 70 blocks north of the centre (on the arena's edge) and turns to face his
  target (`BossThreat`, range 140). **v0.16.4: he walks** -- 0.06 blocks/tick, 4-9 block legs with 4-10 s pauses, turning
  on the spot first, always 52-74 blocks from the centre (`galactusWalkBand`, clear of the pylon ring, inside the border),
  never into unloaded chunks; the `walk` clip (4 s loop, two heavy steps, a ground shake + footfall every 40 ticks). A move
  stops him; his weak points ride on him.
- **He cannot be killed**: health never below 1 (`setHealth` clamp; even `/kill`). 4000 health (+1000 per extra fighter).
  **v0.16.4:** while any Power Pylon stands **nothing gets through** (blue sparks, a shield ring, an action-bar line naming
  the pylons left); after that his **body takes full damage** (`bodyDamageMultiplier` 1.0) and weak points take **x2**
  (`weakPoints.damageMultiplier`). The grabbing hand can always be hit to break a grip.
- **Weak points** -- separate damageable part entities (`GalactusWeakPointEntity`, 4 x 4) he places on himself every tick:
  the **Chest Emblem** (y 39, 600 hp), the **Helmet Crest** (y 58, 500), the **Left / Right Gauntlets** (y 22, 450 each;
  +120 per extra fighter). Damage to them comes off Galactus in full (`passThrough` 1.0) and doubles as threat; breaking
  one takes another 8% of his max health. During Matter Transmutation the left gauntlet comes down to the ground; during
  the Hand Grab the right one rides the hand. Their health lives in the event (saved); they are re-made on load.
- **Power Pylons**: 4 on a ring of 32 (300 hp +80): while **any** stands, he regenerates (6 hp/s each) and the weak points
  are **sealed**. Ground fighters break them.

| Move | Clip (length / hit) | Effect |
|---|---|---|
| Power Cosmic Eye Beams | `eye_beam` 90 / 44 | a 44-block line across the ground glows for 2 s, then the beam sweeps it: 16 + fire within 2.5 |
| Hand Grab | `reach` 40 / 34, `grab_lift` hold, `fling` 30 / 16 | a giant hand (`GalactusHandEntity`, the `galactus_hand` model) comes down over a growing 1.5 s shadow; the nearest target under it is lifted to his face over 2 s (3/s squeeze), held 1.5 s, then flung toward the arena's middle (10). Allies break the grip by hitting the hand (80 hp + 30 per extra fighter; the victim can't); they drop with Slow Falling |
| Matter Transmutation | `transmute` 60 / 40 | under up to 3 fighters a ring is marked, then a radius-3 patch turns to Cosmic Hazard for 10 s: 3 dmg/s to anyone standing in it (the zone does the damage; the blocks are the look) |
| Disintegration Wave | `disintegrate` 50 / 30 | a ring leaves his feet at 0.9 blocks/tick out to 150: 12 to anyone on the ground at it -- jump (1.25 high) or fly over |
| Punisher Summons | `summon` 50 / 30 | 2 Punishers drop from the Worldship (cap 6) |

Other clips: `idle` (100, loop), `weakpoint_hurt` (30), `retreat` (160 hold, hit 60).

## 6. Terrain: what changes, and how it comes back (`GalactusTerrain`)
One ledger, overworld saved data (`projecthero_galactus_terrain`), keyed by event id and group (a converter's index, or
`-1` for Matter Transmutation with an expiry time). It is both the event's record and the orphan guard.
- Only the `projecthero:galactus_consumable` tag (dirt-family, overworld stone, sand, terracotta, gravel, snow, clay,
  sandstones, mud, netherrack, soul sand/soil, end stone...) becomes Consumed Ground / Cosmic Hazard; plants on top
  (replaceables, flowers, saplings, cane, cactus, berry bushes) are cleared to air with them. Never a block entity; nothing
  within 3 blocks of one (a base's chests and furnaces keep the base clean); a column stops at its first block that is
  neither (a player's floor or wall shields what's under it); 2 ground blocks deep.
- Loaded chunks only (never force-loads); set with UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE (no neighbour updates).
- Every original state is recorded first (palette-compressed). Revert: a destroyed converter's group, an expired hazard,
  or the whole ledger (win, loss, abort, an event that no longer exists -- swept every 10 s) is put back 160 blocks a tick,
  nearest first, only where the block is still Galactus' own (a block a player changed since is left alone), unloaded
  chunks waiting for their load (retried every 5 s). Both event blocks never drop anything and can't be pushed.

## 7. The ending: the Ultimate Nullifier
1. **The Nullifier moment**: every weak point broken, **or** his health at/under 20% (`nullifierAtFraction`), **or** 1 hp.
   Title + the Surfer's call; he hovers over the beacon with a pillar of light.
2. **Assembly** (the clean flow chosen): once fighters' inventories hold every part earned (one per destroyed converter),
   the Surfer takes them and hands the **Ultimate Nullifier** to the fighter holding the most. Parts lost to lava / the void
   reappear near the Surfer after 10 s (`nullifier.lostPartTicks`). No parts earned: it is handed over at once.
3. **Use**: hold right-click 3 s (`nullifier.useTicks`) aimed at Galactus (his body or a weak point, within 160) -> he
   **retreats**: everything stops, Punishers fall, he raises a hand (`retreat` hit, line 2) and at the clip's end vanishes
   in a burst of Power Cosmic; the Worldship plays `leave`; the whole ledger reverts; the Surfer says farewell; the chest.
- The parts and the Nullifier never outlive the event (removed from every fighter at the end).

## 8. Rewards (`GalactusRewards`)
- **Chest** at the centre (the beacon becomes it): Galactus' Helmet Crest (trophy block), diamonds 10-18, netherite scrap
  2-4, emeralds 16-28, amethyst 12-24, bottles o' enchanting 8-16, 3 enchanted books (+1 for 3+ fighters), scaled by
  `Valuables.partyScale`.
- **First clear per player** (`projecthero:galactus_clears` attachment, persistent): the **Silver Surfer's Board** and **the
  Power Cosmic**, straight into the pack (`rewards.firstClearItems`).
- **Silver Surfer's Board** (`SurferBoardEntity`, item `silver_surfer_board`): right-click the item to summon it under you
  and ride; fly where you look holding forward (1.1 b/t), **sprint = Power Cosmic boost** (2.4 b/t, silver trail), jump
  climbs, sneak dismounts; sneak + right-click the board to store it (owner only). Invulnerable, never despawns. The rider's
  client simulates it (a local-player vehicle).
- **The Power Cosmic** (`power_cosmic`): right-click a cosmic blast along your look (10, range 32, 2 s cooldown; never your
  pets); sneak + right-click a 3 s shield (Resistance II + Absorption II, 20 s cooldown). Works in either hand.

## 9. Model contract (the real models are exported here by the model agent)
`geo/galactus/<name>.geo.json`, `textures/entity/galactus/<name>.png` + `<name>_glowmask.png`,
`animations/galactus/<name>.animation.json` for **galactus, punisher, element_converter, worldship, galactus_hand,
surfboard_mount, nullifier** (the Silver Surfer is a player model -- section 3). Bones `neck` / `head` (look) and `flinch`
(hurt) are posed by code and must never be keyed. Every clip with a hit keys its strike at `(hit - 4) / 20` s; play-once
clips are `(length - 4) / 20` s long; loops `length / 20`. Sizing: each model is measured from its rest pose
(`GalactusModelFit`): Galactus to 60 (head top, prongs excluded), Punisher 8, converter 10, hand 9, Worldship 40; the
board and the Nullifier at raw scale (16 px = 1 block).

| Model | Clip | Kind | Length | Hit |
|---|---|---|---|---|
| galactus | idle | loop | 100 | - |
| galactus | eye_beam | once | 90 | 44 |
| galactus | reach | once | 40 | 34 |
| galactus | grab_lift | hold | 50 | - |
| galactus | fling | once | 30 | 16 |
| galactus | transmute | once | 60 | 40 |
| galactus | disintegrate | once | 50 | 30 |
| galactus | summon | once | 50 | 30 |
| galactus | weakpoint_hurt | once | 30 | - |
| galactus | retreat | hold | 160 | 60 |
| punisher | idle / walk | loop | 60 / 30 | - |
| punisher | blast | once | 30 | 18 |
| punisher | slam | once | 40 | 28 |
| punisher | death | hold | 40 | - |
| element_converter | idle / consume / shielded | loop | 40 | - |
| element_converter | destroyed | hold | 40 | - |
| worldship | idle | loop | 200 | - |
| worldship | leave | hold | 100 | - |
| galactus_hand | idle / grip | loop | 40 / 20 | - |
| surfboard_mount | idle | loop | 40 | - |
| nullifier | idle | loop | 40 | - |
| silver_surfer (pose timings, not GeckoLib) | idle / fly / blast / dash / shield / lift / turn_ally / ally_idle | - | 40 / 20 / 24 / 34 / 70 / 40 / 120 / 40 | - / - / 14 / 24 / 8 / 20 / - / - |

## 10. Persistence and performance
- Saved with the event: phase + its start, fighters / down, the Surfer, Galactus, converter ids + total, pylons, each
  weak point's health / broken, consumption, parts earned + dropped part ids, the Nullifier moment / assembled / user,
  hazard zones, the dialogue step. Saved in the ledger: every recorded block. Entities: the Surfer's state (a mid-turn save
  loads as an ally), converters' landed / radius / spiral cursor, Punishers' guard post.
- Orphan guards: every unit discards itself within 2 s if its event is gone; the ledger reverts any event that no longer
  exists; weak points / the hand / the Worldship are never saved and are re-made.
- The framework tick (every 0.5 s) does the arena, bars, sky and phases; the per-tick work is the converters' budgeted
  bite, zones every 0.5 s, consumption / regen / part watch every second, the revert budget. Particles are capped per call
  (lines <= ~120 points, rings <= 160). Never force-loads a chunk.

## 11. Config keys (`config/projecthero_galactus.json`)
`trigger` (enabled, naturalTriggerEnabled, chancePerDay, naturalOffsetMin/Max, beaconNightOnly) - `arena` (radius,
arrivalSeconds, worldshipHeight, borderPoints, terrainDamage) - `herald` (health, healthPerExtraFighter, armor, flySpeed,
turnAtFraction, moveCooldownTicks, blastDamage, blastSpeed, blastRange, dashDamage, dashLength, dashTicks, shieldTicks,
shieldMeleeReduction, shieldReflectFraction, liftAmplifier, liftTicks, preferredRange, hoverHeight, turnTicks) - `ally`
(blastIntervalTicks, blastDamage, shieldCooldownTicks, shieldBelowHealth, shieldTicks, orbitRadius) - `converters` (base,
perTwoExtraFighters, max, ring, health, healthPerExtraFighter, armor, fallTicks, guards, maxRadius, growTicks,
columnsPerTick, blocksPerTick, revertPerTick, blockEntityClearance, depth) - `punisher` (health, healthPerExtraFighter,
armor, speed, punchDamage, blastDamage, blastRange, blastCooldownTicks, slamDamage, slamRadius, slamCooldownTicks, leash) -
`consumption` (perConverterPerMinute, perPylonPerMinute, lossCinematicTicks) - `galactus` (health, healthPerExtraFighter,
bodyDamageMultiplier, renderHeight, standDistance, moveCooldownTicks, nullifierAtFraction, regenPerPylonPerSecond, range) -
`eyeBeam` (telegraphTicks, damage, width, length, setFireSeconds) - `grab` (radius, gripHealth, gripHealthPerExtraFighter,
liftTicks, holdTicks, squeezeDamagePerSecond, flingDamage, flingSpeed) - `transmute` (maxPatches, radius, lifeTicks,
damagePerSecond) - `wave` (damage, speed, reach, clearHeight) - `summons` (perCast, cap) - `pylons` (count, ring, health,
healthPerExtraFighter) - `weakPoints` (emblemHealth, crestHealth, gauntletHealth, healthPerExtraFighter, passThrough,
breakDamageFraction) - `nullifier` (useTicks, range, lostPartTicks) - `rewards` (valuablesMultiplier, netheriteMin/Max,
books, firstClearItems) - `board` (flySpeed, boostSpeed, acceleration) - `powerCosmic` (blastDamage, blastRange,
blastCooldownTicks, shieldTicks, shieldCooldownTicks).

## 12. Gametests (`GalactusGameTests`)
Registered + recipe + clip contract + config defaults; starts / refuses a second nearby / aborts clean; the Surfer stops at
10%, turns on his own, is protected and allied after; a converter records and reverts its bite exactly (block-for-block
snapshot) and never touches a chest or anything within 3 of it; consumption fills (5%/min per converter) and the loss path
reverts converter + hazard blocks; hazard blocks are cleaned on abort; Galactus holds at 1 hp (even `/kill`) and calls for
the Nullifier; weak points are sealed by pylons, then take full damage (passed through to him) while the body takes 20%,
and breaking one costs more; the Nullifier wins, the beacon becomes the chest, first-clear items are paid once.
