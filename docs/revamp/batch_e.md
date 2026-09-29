# v0.13.22 mutation revamp: batch E

Powers 16 Spider Climbing / Adhesion, 17 Elasticity, 18 Density Manipulation, 22 Plant Manipulation /
Chlorokinesis and 27 Size Manipulation. Every power now has 8 abilities (R G X Z V C + the H / N utility
slots), every ability plays a body animation (`MutationVisuals`), each power has overlays other players can see
and HUD bars registered with `MutationMeters`.

General balance pass across all five: about +20% damage, about -15% cooldowns, and about +15% capacity on the
existing resource bars (Density's Phase reserve goes from 100 to 115, Size Strain from 500 to 575). The Hero-Tier
powers are still stronger.

Where the code lives:

| What | Where |
|---|---|
| Ability handlers | `hero/power/p16`, `p17`, `p18`, `p22`, `p27` (registered by `HeroPowerHandlers`) |
| New effects | `p18/CrushingDensityEffect`, `p27/ShrunkenEffect` (real `MobEffect`s, so vanilla removes their attribute modifiers) |
| New entities | `p22/ThornSentryEntity`, `p22/ThornProjectile`, `p22/PlantEntities` |
| Flags, synced values, HUD bars | `hero/revamp/RevampBatchE` |
| Poses, overlays, renderers, client tick | `client/mutation/RevampClientE`, `client/mutation/BatchERenderers` |
| Textures | `scratchpad/gen_v01322_e_textures.js` writes `textures/entity/mutation/p16_web, p17_rubber, p18_shell, p22_bark` and `textures/mob_effect/crushing_density, shrunken` |
| Lang | `scratchpad/lang_v01322_e.js` |
| Tests | `gametest/RevampBatchEGameTests` (23 tests, all passing) |

---

## 16 Spider Climbing / Adhesion: the lead-in to Spider-Man

**Signature mechanic:** real surface adhesion through the shared `spider/SpiderClimb` engine, plus a predator's
kit and a spider-sense. It still evolves into the Spider-Man Hero Class (`SpiderMan.evolveFromAdhesion`). The
`wall_grip` and `adhesion_mode` toggle ids are unchanged, so the engine and existing saves work as before.

| Key | Move | Numbers |
|---|---|---|
| R | Adhesive Strike | 4.5-block palm strike, 8.5 dmg (was 7), roots and weakens for 7 s. 3.4 s cooldown (was 4) |
| G | Pounce | Leap along your aim. **New:** the first enemy you land on within 1.5 s takes 7 dmg and Slowness II for 2 s. 4.25 s cooldown (was 5) |
| X | Wall Leap | Push off the gripped surface (`SpiderClimbActions.leap`), or kick backwards in mid-air. 2.55 s cooldown (was 3) |
| Z | Predator Rush | 35 s of Speed II, Haste II and Jump III, **plus Strength I**, with red eyes. 30 s cooldown (was 35) |
| V | Wall Grip | Toggle: arm the grip. Unchanged |
| C | Adhesion Mode | Toggle: arm the grip permanently. Unchanged |
| H | **Spider-Sense Dodge** | For 24 ticks (about 1.2 s), the next attack that would hit you (anything with an attacker or direct entity; environmental damage is ignored) is cancelled. You sidestep perpendicular to it and get Speed II for 2 s. 6 s cooldown |
| N | **Venom Bite** | 3.8-block lunging bite: 7 dmg, Poison II for 6 s and Slowness II for 4 s. A miss costs nothing. 7 s cooldown |

Passive (new): owners see a faint red dust shimmer over every `Enemy` within 14 blocks. While Spider-Sense is primed
it reaches 20 blocks and is brighter. This runs on the client, so only the owner sees it.

**Visuals:** `p16.grip` draws a thin web-lined shell on the palms and soles. `p16.rush` makes the eyes glow red.
`p16.sense` draws red eyes and two red halo rings that expand above the head. Poses: `p16.palm_strike`,
`p16.pounce`, `p16.crouch_roar`, `p16.cling`, `p16.sense`, `p16.dodge`, `p16.bite`, plus the shared `leap`.
**HUD:** Predator Rush and Spider-Sense timers (Hairline).

## 17 Elasticity: light pass plus the stretching arm

**Signature mechanic:** the arm visibly **stretches** to the target. Every stretch move calls
`ElasticityHandlers.stretchArm(player, blocks, ticks, mode)`, which writes a length, a start tick
(game time mod 8192) and a draw mode. These are synced as the visual values `p17.arm_len`, `p17.arm_t` and
`p17.arm_mode` under the flag `p17.arm`. `RevampClientE.stretchedArm` draws the player's **own skin**: one row of
the forearm stretched along the arm's local +Y from the hand out to that length, with a fist on the end. The arm
extends over 3 ticks, holds, and retracts by tick 14. Held grabs and slingshots keep it extended. The pose puts
the arm on `MutationPose.AIM`, so the stretch runs along the crosshair. Hammer Fist draws both arms with fists
that grow to 2.6 times their size.

| Key | Move | Numbers |
|---|---|---|
| R | Stretch Punch (charge) | 14.5 dmg (was 12), +6 per second held up to +12 (was +5 and +10), range 15 blocks +2 per second. Cooldown 1.7 s +0.85 s per second held (was 2 s +1 s). Arm stretches to the aim point |
| G | Double-Fist Slam | Ground: both arms stretch 6.5 blocks, 20.5 dmg in a 4-block ball (was 17). From more than 5 blocks up: dive slam, 24 dmg (was 20). 6 s cooldown (was 7) |
| X | Slingshot | Arm latches onto a mob (45 blocks) and hauls you in, 17 dmg on impact (was 14). Or reels you to a block. 2.55 s cooldown |
| Z | Giant Hammer Fist | Both arms plus giant fists, 41 dmg in 4.5 blocks (was 34). 30 s cooldown (was 35) |
| V | Elastic Grab | Arm stretches out (15 blocks), wraps the target and holds it at 2.5 blocks. The arm stays drawn to it. Throw for 8.5 dmg (was 7). 5.1 s cooldown |
| C | Elastic Form (wheel) | Unchanged three shapes. Form ability bonus 9.5 (was 8). Adds a rubbery sheen overlay |
| H | **Rubber Shield** (hold) | Inflate into a balloon. Projectiles within 3.5 blocks that are heading at you are turned around (re-aimed at their shooter within 48 blocks) and become yours. Melee attackers are flung about 3 blocks per tick. You take 65% less damage, have 100% knockback resistance and -50% speed. Drains the **Rubber** bar (115, 5 s from full, refills in about 9 s, needs 20 to start). 8 s cooldown on release |
| N | **Parachute Glide** | Flatten into a canopy: slow fall plus a forward glide steered by your look (about 10 blocks/s, or a faster dive when looking steeply down). Used on the ground, it hops you up first. At most 8 s, ends on landing, or press N again. 5 s cooldown when it ends |

**Glide implementation:** the client steers (`RevampClientE.clientTick` blends horizontal velocity toward the look
direction and clamps the sink rate), following the adhesion engine's rule that the client simulates and the
server authorises. The server keeps a hidden short Slow Falling running as a fallback and resets fall distance.
Elasticity already has full fall immunity.

**Visuals:** `p17.arm` (stretched arm), `p17.shield` (thick rubber shell plus a breathing translucent balloon on
the torso), `p17.glide` (a rubbery membrane between the spread arms), `p17.form` (thin sheen). Poses:
`p17.windup` (loop while charging), `p17.stretch_right`, `p17.stretch_both`, `p17.hammer`, `p17.grab_hold`
(loop), `p17.sling` (loop), `p17.form_stretch`, `p17.compress`, `p17.inflate` (loop), `p17.glide` (loop).
**HUD:** Stretch Charge (Meter, %), Rubber (Meter), Parachute Glide (Hairline timer).

## 18 Density Manipulation: light pass

**Signature mechanic:** the continuous 25-300% density dial is unchanged (R +5%, G -5%, C zero density to 25%,
X anchor to 300%). A **thin translucent shell** shows it: blue when light, orange when heavy, more opaque the
further from 100%. It is drawn from the synced value `p18.density` under the flag `p18.shell`, shows once density
is at least 5% off 100% or while Anchor runs, and uses the white hex-lattice `p18_shell` texture tinted at
runtime.

| Key | Move | Numbers |
|---|---|---|
| R / G | Increase / Decrease Density | ±5%, no cooldown. Unchanged |
| X | Density Anchor | 300% and rooted for 10 s. Cooldown after it ends: 25.5 s (was 30) |
| Z | Heavy Impact | Up to 54 dmg in a 20-block radius (was 45). 8.5 s cooldown (was 10) |
| V | Phase | Unchanged, but the reserve is 115 (was 100). Same drain, so it lasts 15% longer. Regen 0.23/tick |
| C | Zero Density | To 25%. 1.7 s cooldown (was 2) |
| H | **Intangible Dodge** | 10 ticks (0.5 s) immune to everything except void and `/kill` (added to the density case in `HeroDamageRules`), plus a 1.25 blocks/tick horizontal dash. 4 s cooldown |
| N | **Crushing Touch** | Arms the fist for 8 s. The next hit that lands (any damage whose direct source is you, including melee) applies **Crushing Density** for 5 s: -70% speed, -80% flying speed, -100% jump, +200% gravity, +0.6 knockback resistance. While airborne, fliers (`FlyingMob`, `FlyingAnimal`, no-gravity mobs, elytra players) are forced down at 0.55 or more blocks/tick. Bosses over 200 HP keep their flight. Duration is halved on players (`applyControl`). 12 s cooldown |

**Visuals:** `p18.shell`; `p18.intangible` (pulsing emissive cyan shell); `p18.crush` (an emissive orange glow
on the right fist while armed). Poses: `p18.dense`, `p18.light`, `p18.anchor` (loop), `p18.plunge`,
`p18.charge_fist`, plus `float_arms` while phasing and `ground_pound` on Heavy Impact's landing.
**HUD:** Density (Gauge, always shown, seeded to 100), Phase (Meter, 115), Crushing Touch (Hairline timer).

## 22 Plant Manipulation / Chlorokinesis: growth and plant minions

**Signature mechanic:** growth you can fight with. The **Thorn Sentry** is a planted turret, and Nature's Blessing
turns your skin to bark.

| Key | Move | Numbers |
|---|---|---|
| R | Thorn Shot | 9.5 dmg plus Poison III 8 s (was 8), 1.7 s cooldown. Shift: Branch Thrust, 18 dmg (was 15), 8.5 s cooldown (was 10) |
| G | Thorn Snare | Root 8 s, 3.6 dmg, with a vine particle lash. Shift: 10-block cone. 6 s cooldown (was 7) |
| X | Vine Swing | Unchanged. 0.85 s cooldown |
| Z | Overgrowth (charge 5 s) | 54 dmg in 20 blocks (was 45), root, Poison X. 51 s cooldown (was 60). Green eyes and a pulsing bark shell while charging |
| V | Living Wall | Unchanged. 6.8 s cooldown (was 8) |
| C | Nature's Blessing | Toggle. Nature bonus +12 (was +10). **Bark-and-leaf THIN shell** while on |
| H | **Thorn Sentry** | Plants a turret where you look (on a top face within 6 blocks, otherwise 1.5 blocks ahead). For 15 s (300 ticks) it tracks the nearest visible hostile within 14 blocks and fires a `ThornProjectile` every 10 ticks: 5 dmg plus Poison I 2.5 s. Targets only `Enemy` mobs or mobs targeting the owner. Never players, never the owner, never squad-mates (`Squads.areAllies`). Its thorns cannot hit them either. One per player (a new one withers the old). Not saved. Withers if the owner leaves, dies or goes more than 64 blocks away. 25 s cooldown |
| N | **Spore Cloud** | A 4.5-block cloud centred 2.5 blocks ahead, lasting 8 s. Every 10 ticks, enemies inside get Poison II and Blindness for 3 s (players only with PvP on). You and squad-mates inside get +1.5 HP and Regeneration I. Stored as a marker, so negative coordinates are fine. 16 s cooldown |

The model is built from block models (moss base, cactus stem, flowering-azalea head, cactus barrel) through
`BlockRenderDispatcher.renderSingleBlock`. It grows in over 10 ticks, sways, turns its head to its target,
swells slightly on each shot and withers over the last 20 ticks. Thorns are a small baked green spike oriented
along their velocity.

Performance fix: the always-on growth aura used to walk every position in a 61×13×61 box (about 48k positions)
every 5 s. It now takes 900 random samples with the same 25-plant budget.

**Visuals:** `p22.blessing` (bark shell), `p22.overgrowth` (green eyes and a pulsing bark shell). Poses:
`p22.swing`, `p22.gather` (loop while charging), `p22.spore_blow`, plus `cast_right`, `grab_pull`,
`summon_ground`, `cast_two_hand`, `cast_raise_both`, `power_up` and `flex`.
**HUD:** Overgrowth (Slab, %), Thorn Sentry and Spore Cloud timers (Hairline).

## 27 Size Manipulation: forms

**Signature mechanic:** three forms, and every change eases geometrically over 1 s in both directions (the
existing v0.12.1 `SCALE_ANIM` easing; growth pauses while the bigger body would not fit). Removing the power
still snaps back instantly.

| Key | Move | Numbers |
|---|---|---|
| R | Giant Punch | 12 dmg (was 10); +6 Large / +18 Giant (were +5 / +15); ×0.3 in Tiny. 2.55 s cooldown |
| G | Stomp | 9.6 dmg (was 8) plus the form bonus. 6.8 s cooldown (was 8) |
| X | Tiny Form | Toggle, unchanged apart from the pose |
| Z | Giant Form | Toggle. Size Strain bar is 575 (was 500) at the old drain, so about 26 s (was 23). Attack bonus +18. Trample 9.6 |
| V | Tiny Dash | Unchanged. 4.25 s cooldown (was 5) |
| C | Large Form | Toggle. Attack bonus +6 |
| H | **Shrink Punch** | Raycast at 4.5 × your size's reach factor. 8 dmg (×0.3 in Tiny) plus **Shrunken** for 8 s: SCALE -50% (a smaller hitbox and shorter mob reach), -40% attack damage, -40% interaction range, -15% speed. It is a real mob effect, so the transient modifiers always come off. Bosses over 200 HP take the damage but keep their size. A miss costs nothing. 10 s cooldown |
| N | **Mount** | **Tiny:** `startRiding` the mob you look at (4 blocks) for up to 60 s. **Large / Giant:** pick up a mob less than 60% of your height and hold it in the right hand for up to 20 s. N again dismounts, or throws the carried mob along your look. Released automatically and safely on form change, death (`AFTER_DEATH`), power removal (the passive teardown), timeout, distance over 24 blocks, or the mob dying. A released carried mob gets Slow Falling for 5 s. No players, armour stands or bosses. Normal form does nothing and costs nothing. 2 s cooldown after a release |

**Visuals:** size itself, plus poses `p27.shrink`, `p27.ride` (loop), `p27.carry` (loop), with `haymaker`,
`stomp`, `dash_forward`, `punch_right`, `power_up`, `flex` and `throw_right`.
**HUD:** Size Strain (Meter, always shown, 575, seeded full), Mount (Hairline timer).

---

## Design calls

- **Sneak + N**: none of the N abilities read sneak.
- **Reused ids**: all old ability ids survive (saves keep cooldowns). New ids: `spider_sense`, `venom_bite`,
  `rubber_shield`, `parachute_glide`, `intangible_dodge`, `crushing_touch`, `thorn_sentry`, `spore_cloud`,
  `shrink_punch`, `mount`. No ability was dropped.
- **Intangible Dodge** is immunity plus a dash. It does **not** noclip through blocks: the Phase noclip needs
  flight, and a 0.5 s noclip without it can drop you through the floor or leave you inside a wall.
- **Mount carry** is also allowed in Large form, not just Giant (the brief said Giant), limited by the 60%-height
  rule.
- **Crushing Touch** triggers on the next landed hit whose direct source is you. That includes ability hits that
  use the player-attack damage source, not only fist swings.
- `PowerCatalog` uses `AbilitySlot.SLOT_7` / `SLOT_8` fully qualified, so the import block stays untouched
  (fewer merge conflicts with the other batches).
- The `HeroDamageRules` changes are confined to the density case (Intangible Dodge) and a comment in the
  elasticity case. The Rubber Shield's reduction goes through the existing `ElasticityHandlers.damageTakenFactor`
  and `deflectsProjectiles`. Spider-Sense uses its own `ALLOW_DAMAGE` listener, so the shared
  `power_03_flight` / `power_16` case line is untouched.
- No new static world caches (no `ServerStateReset` changes). Transient per-player state lives in the power's
  resources, which `forget()` wipes.

## Unverified in-game (no client run here)

- **Stretched arm**: skin UV rows, fist alignment on slim models, how it looks at very long lengths (a 48-block
  cap), and that it only shows in third person. First person only shows the slime particle line.
- The Rubber Shield balloon and glide membrane proportions, and the density and bark shell looks.
- How Parachute Glide steering feels (0.5 cruise, 0.75 dive, -0.11 sink), and any rubber-banding against the
  server.
- Thorn Sentry block-model proportions and head-yaw direction, and the thorn spike orientation.
- Mount: riding pose on different mobs, whether carried mobs visibly jitter (the server repositions them every
  tick), and a Giant's throw distance.
- Pose frame values in general (all hand-authored).
- The Spider-Sense threat glow's particle density.
