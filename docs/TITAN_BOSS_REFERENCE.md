# The Titan (world boss) — reference (v0.14.4)

Not the Titan Shifter hero power (see `TITANSHIFTER_REFERENCE.md`). A very rare Overworld wilderness boss.
Package `com.projecthero.mod.titan` (`TitanConfig`, `TitanThreat`, `TitanSpawner`, `TitanTerrain`,
`TitanHealthCap`) and `com.projecthero.mod.titan.entity` (`TitanEntity`, `DisguisedTitanEntity`,
`TitanBoulderEntity`, `TitanEntityTypes`). Client: `client.render.TitanRenderer` / `TitanModel` /
`TitanAnimations` (registered from `TitanEntityRenderers`). Config: `config/projecthero_titan.json`
(re-written with any new keys on every start; missing keys/sections take the code defaults).

## Encounter

A `DisguisedTitanEntity` ("Wanderer") spawns on open surface ground, looks like a plain zombie except for a
yellow spark every ~1.5 s, and on death is struck by (visual-only) lightning and replaced by the real
`TitanEntity`: 18 blocks tall (`SCALE = 18 / 1.95`), 1800 HP, armour 10 / toughness 8, full knockback
resistance, no fall damage, detection 150 blocks, follow range 176. It spawns roaring (INTRO animation) and
waits ~2.5 s before its first attack. Loot: `data/projecthero/loot_table/entities/titan.json`.

Movement never runs an A* search (the v0.10.10 lag fix): it steers straight at its target with the move
control, steps up to 10 blocks (STEP_HEIGHT), and `tickUnstick` carves a corridor through anything that
holds it still for half a second. Block drops from destruction are throttled (`blockDropChance`,
`maxBlocksPerDestruction`).

## Moves

Every telegraphed move winds up for **2 s** (`WINDUP_TICKS = 40`) with a distinct animation, sound and
particle tell, then resolves; per-move cooldowns plus a global 16-tick gap between moves. Damage to a
player is capped at 80 % of their max health per hit (`scaleForPlayer`). All area moves hit **every**
valid player in range, not only the target.

| Move | Tell (animation) | Effect | Chosen when | Cooldown |
|---|---|---|---|---|
| Punch | right fist cocked back, shoulder turned | 20 to the target within 7 (+1) blocks, knockback | target ≤ 7 | 1 s |
| Backhand Sweep | arm wound far out to the side, then swept low across the front | 22 + hard knockback to everyone in the front 180° within 10 + half-width | target ≤ 12 | 3.5 s |
| Stomp | knee raised high, arms out for balance | 30 + knock-up within 4.5, breaks nearby blocks (never the floor) | target underfoot / ≤ 6 | 4 s |
| Ground Slam | both fists raised overhead, leaning back, then a hammer-fist | 26 within 6, crater | target ≤ 9 | 5.5 s |
| Shockwave | arms spread wide and raised, then a crouching double-fist blast | up to 30 (falls off) + knock-up within 14 | target ≤ 14 | 7.5 s |
| Grab | open hand drawn back, then a lunging snatch | 6, then held 1.5 s in the right fist (4), then hurled 15–25 blocks (10) | target ≤ 6, grounded player | 8 s |
| Boulder | scoops a rock (visible in its hands), heaves it overhead, throws | 28 direct + 7-block AoE with falloff, impact crater | target 6–48 | 6 s |
| Charge | head down, arms back, pawing the ground; then a sprint cycle | 34 + launched, carves through terrain, up to 40 blocks | target 6–40, line of sight | 9 s |
| **Leaping Slam** (v0.14.4) | deep crouch, arms back; red ring on the target's spot | scripted jump onto the target (arc apex 7 + 0.2 × distance); 30 to everyone within half-width + 4 of the landing, then a ground ring travels out to 18 blocks at 0.9 blocks/tick hitting grounded players for 16 + knock-up. **Jumping as the ring passes dodges it.** | target 10–32 (favoured when they run) | 10 s |
| **Grave Roar** (v0.14.4) | rears back inhaling souls, arms spread; then a roaring lunge with a body shudder | everyone within 24: 6 damage + Slowness II 4 s; raises up to 3 Husks beside the group (players it is *not* chasing first; max 6 alive, cleared when the Titan dies); drag pulse toward the Titan every 5 ticks for 1.5 s | target ≤ 24 (favoured vs. groups and kiters) | 16 s |
| Swat (filler melee) | quick raised-arm chop (10-tick wind-up since v0.14.4) | 16 + knockback to every player in reach (8 + half-width) in a wide frontal arc | any player in reach, 0.8 s cadence | — |

## Aggro (v0.14.4, `TitanThreat`)

All vanilla target goals are stripped (including `HurtByTargetGoal`, which was the cause of the one-target
lock). Instead:

- **Damage** a player deals adds `damage × damageThreatMultiplier` threat; standing within
  `proximityRange` (14) of its edge adds up to `proximityThreatPerSecond` (3/s). Threat halves every
  `threatHalfLifeTicks` (10 s).
- Every `retargetIntervalTicks` (2 s), not mid-attack/grab/swat, it scores every valid player within
  detection range as `threat + nearbyWeight × (1 − distance / nearbyRange)` and switches when the best beats
  the current target by `switchMargin` (×1.25). With `randomSwitchChance` (20 %) it instead picks at random
  among players scoring ≥ `randomScoreFloor` (60 %) of the best.
- An invalid target (dead, logged out, creative/spectator, other dimension, past follow range) is replaced
  immediately. Creative/spectator players are never targeted or hit (knockback included).
- A switch is announced with a low growl and angry-villager sparks over the new target's head.

## Animation system (v0.14.4)

Still the vanilla zombie rig and `textures/entity/titan.png` (swap the texture freely), but posed by
`TitanModel`:

- `TitanEntity.Anim` is synced as one `SynchedEntityData` int (`DATA_ANIM`: ordinal + a bump counter so a
  repeat still registers). The client stamps its own `tickCount` when it changes and plays the clip from
  there, cross-fading ~5 ticks from the previous clip (1–2 ticks for impacts).
- `TitanAnimations` holds eased keyframe clips (degrees; torso `BEND`/`TWIST`/`ROLL` pivot the upper body
  at the hips, `DROP` lowers it). Attack clips strike at tick 40 to match the server; CHARGE's pawing and
  sprint, the roar's shudder and the airborne flail are procedural.
- Walking uses a slowed gait (`GAIT_SCALE 0.4`) with sway, bob and arm counter-swing.
- `TitanRenderer.HeldBoulderLayer` renders the rock (sandstone / dirt / cobblestone by the ground under it)
  in its hands during the Boulder wind-up.

## Config keys added in v0.14.4

`attacks`: `meleeWindupTicks`, `leapMinDistance`, `leapMaxDistance`, `leapDamage`, `leapCrushRadius`,
`leapRingDamage`, `leapRingRadius`, `leapRingSpeed`, `roarRadius`, `roarDamage`, `roarPullStrength`,
`roarDurationTicks`, `roarSlownessTicks`, `roarSummonCount`, `roarMaxMinions`. `cooldowns`: `leap`, `roar`.
New section `aggro`: `damageThreatMultiplier`, `proximityThreatPerSecond`, `proximityRange`,
`threatHalfLifeTicks`, `retargetIntervalTicks`, `nearbyWeight`, `nearbyRange`, `switchMargin`,
`randomSwitchChance`, `randomScoreFloor`. Existing config files pick these up with their defaults (Gson
keeps field initialisers for missing keys); no existing value changed, so no migration is needed.

## v0.14.4

- Full body animations for every move, the walk, the spawn roar, the grab-and-throw and the leap.
- New moves: Leaping Slam and Grave Roar.
- Threat-based aggro; area moves and the swat hit everyone; creative/spectator players ignored.
- Swat now has a 10-tick wind-up and hits every player in reach (was: instant, current target only).
- Grab now holds the victim in its right fist in front of it (was inside its own body); boulders leave
  from above its head.
- The Titan no longer starts attacks while collapsing after death.
- Tests: `TitanBossV0144GameTests` (threat switch, creative exclusion, live retarget, swat on a non-target,
  Grave Roar effects + Husk cap, Leaping Slam landing + ring). They run on a temporary deck above the
  GameTest barrier cages, since an 18-block Titan cannot move inside one.
