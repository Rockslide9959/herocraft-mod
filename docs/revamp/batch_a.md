# v0.13.22 mutation revamp — batch A

Super Strength (01), Laser Vision (02), Flight (03), Super Speed (04), Super Regeneration (12), Super Durability (13).

Every power now has 8 abilities (R G X Z V C + H N). Cooldowns below are base values (scaled at runtime by the
config cooldown multiplier). General balance pass vs. v0.13.21: roughly +20% damage, −15% cooldowns, resource bars
+15% (heat 500→575, guard 500→575, new bars sized 115).

Code map:

| What | Where |
|---|---|
| ability data (ids, slots, activation, base cooldowns) | `hero/PowerCatalog.java` (the six batch-A methods only) |
| handlers | `hero/power/p01`, `p02`, `p03`, `p04`, `p12`, `p13` |
| shared helpers (no-op-skipping resource writes, stance-pose rule, ally check, particle rings) | `hero/revamp/batcha/BatchA.java` |
| thrown block / boulder entity | `hero/revamp/batcha/ThrownChunkEntity.java`, `BatchAEntities.java` |
| overlay flags, visual values, HUD meters, Barrel Roll damage veto | `hero/revamp/RevampBatchA.java` |
| poses, overlay renderers, chunk renderer, Flight double-tap, Haymaker pips | `client/mutation/RevampClientA.java`, `client/revamp/batcha/ThrownChunkRenderer.java` |
| Durability Impact banking | 3 lines in the `power_13_super_durability` case of `hero/power/HeroDamageRules.java` |
| Flight speed-tier clamp | 3 lines in `projecthero$flightControl` of `client/mixin/LocalPlayerMixin.java` |
| textures | `textures/entity/mutation/p01_veins, p03_streaks, p04_crackle_a/b, p12_veins, p13_plating, p13_glint` (generator: `scratchpad/tex_v01322_a.js`) |
| lang | `scratchpad/lang_v01322_a.js` (also deletes the dropped abilities' keys) |
| tests | `gametest/RevampBatchAGameTests.java` (25 tests) |

---

## 01 Super Strength — throw anything + hero landings

Passives: unarmed 14 (was 12), +150% attack knockback, ~2-block jump, 15% damage reduction (explosions 32%,
falls 65%), 70% knockback resistance, stone-tool hands. **Charged Punch** (hold LMB 2 s, client-timed flow kept):
24 (was 20), 2.5 s cooldown.

| Key | Ability (id) | Numbers | Pose |
|---|---|---|---|
| R | Haymaker (`haymaker`, new) | jab 10 → cross 12 → haymaker 22 + launch + shield disable. Follow-ups within 1.5 s, 0.4 s between hits; finisher 5.1 s cd; dropping the combo half way 2.5 s cd. Aimed target, else nearest enemy within 3 blocks not behind you. Combo pips under the crosshair. | punch_right / punch_left / haymaker |
| G | Ground Slam (`ground_slam`, moved from R) | 17 dmg, r 5, 2 s slow, launch. Airborne = dive: 17/19/24/29/34 by fall height, ends in a hero landing. 6.8 s. | slam_two_hand; dive `p01.dive` → hero_landing |
| X | Power Leap (`power_leap`, kept, client flow untouched) | 11–38 blocks by charge tier; **new hero landing** on touchdown: 7 + 3/tier dmg (7…22) in r 2.5 + 0.6/tier, debris-ring crater, smoke. 2.5 s. | crouch_charge (wind-up), leap, hero_landing |
| Z | Maximum Effort (`maximum_effort`, moved from C) | 15 s (was 22): every Strength move and melee ×2 (Bull Rush / Impact Smash ×1.25), slams 1.5× radius, knockback immune, 30% less damage, Speed II / Jump II / Haste II, cooldowns halved. 51 s. | power_up; red vein overlay |
| V | Grab & Throw (`grab_throw`, replaces Grab & Carry) | Grab a mob/player (5 blocks) and hoist it overhead, else rip the aimed block out (hardness < 10, no block entities; copied instead of removed when terrain damage is off). Press again: creature thrown at 2.8 — takes 12 (and 9 to what it hits) when it slams into a wall/floor/mob; block flies as a `ThrownChunkEntity` for 19 direct + 8 splash r 2.5. Sneak+V sets a creature down. 15 s hold cap. 3.4 s. | grab_pull, carry_overhead (loop), throw_right |
| C | Bull Rush / Impact Smash (`bull_rush`, moved from Z) | 5 s charge; rush 8 s, 24 per hit; sneak = Impact Smash 72 r 20 (soft blocks break with terrain damage). Now on the ordinary ability cooldown: 34 s rush / 76 s smash. | crouch_charge, `p01.rush` loop, ground_pound |
| H | Thunderclap (`thunderclap`, new) | 120° cone, 9 blocks: 9 dmg, knockback 2.4, Slowness V + Weakness II 2.5 s, projectiles in the cone turned around, fire extinguished on creatures, you and blocks (always, even with terrain damage off). 8 s. | clap |
| N | Rip & Hurl (`rip_hurl`, new) | Tears the ground block 1.8 blocks ahead (cobblestone if it can't be ripped) out as a 1.8-scale boulder that rises overhead in 12 ticks and auto-throws along your aim: 26 direct + 14 splash r 3.2. 9.4 s. | summon_ground → throw_right |

Dropped: Air Punch.

## 02 Laser Vision — heat gauge, hotter = sharper beam

Heat 0–575. Every beam multiplies damage by `1 + 0.6 × heat/575` (×1.6 at full); past 60% heat a white-hot
END_ROD core runs down every beam. Full gauge = **overheat**: 3 s lockout, channels cut, venting at 2.5× speed.
Heat vents 4.5/tick starting 1 s after the last shot (not while Thermal Vision is on). Passives: blindness/darkness
immunity, permanent night vision.

| Key | Ability | Numbers | Pose |
|---|---|---|---|
| R | Heat Vision (hold) | 21 blocks, 4.8 per half-second × ramp (1 → 2.5 over 3 s held) × heat. +2 heat/tick. Shift+R utility mining kept (+3/tick). | beam_eyes loop |
| G | Piercing Lance (`piercing_lance`, CHARGE, new) | 2 s full charge; 40 blocks; pierces 2–4 targets for 18–29 × heat; cuts leaves / glass / panes (≤12 blocks, terrain damage on). +90 heat, 7.5 s. | beam_eyes → `p02.glare` |
| X | Recoil Blast (`recoil_blast`, new) | Blast at the aimed point (≤6 blocks) — you are launched opposite your look at 1.9 (+0.25 up); 9.6 × heat + fire r 2.5 at the blast; 4 s fall immunity. +50 heat, 4.25 s. | `p02.recoil` |
| Z | Maximum Output (hold) | 4 s charge (was 5), then 3 s, 48 blocks: 9.6/tick × heat, 41-dmg burst every 10 ticks r 5, cuts soft blocks. +250 heat, 55 s. | beam_eyes |
| V | Ricochet Shot (`ricochet_shot`, new) | Hitscan bolt, 48 blocks total, reflects off up to 3 block faces; 11 × heat, +25% per bounce; ignites TNT. +40 heat, 3.5 s. | `p02.glare` |
| C | Thermal Vision (toggle, kept) | Client glow mixin unchanged; +0.4 heat/tick, blocks venting. | `p02.glare` on toggle |
| H | Sweeping Arc (`sweeping_arc`, new) | 150° horizontal sweep over 10 ticks, 14 blocks, 14.4 × heat + fire (i-frames stop double hits). +80 heat, 8.5 s. | `p02.sweep` (body yaw swing) |
| N | Cauterize (`cauterize`, new) | Needs ≥60 heat; heals 3 + 13 × heat/575 (≤16), clears fire, Wither, Poison, empties the gauge and any overheat. 14 s. | `p02.cauterize` |

Visuals: `p02.eyes` flag (always while owned) + synced value `p02.eye_glow` (0.35–0.8 by heat, 1.0 while any beam
fires) → `MutationRender.eyes` red, flickering when firing. Removed the old per-tick eye flame particles.
Dropped: Focused Beam, Heat Burst, Precision Vision.

## 03 Flight — speed tiers + sonic boom

Passive: full fall immunity (unchanged). Flight never runs out.

| Key | Ability | Numbers | Pose |
|---|---|---|---|
| R | Air Dash | impulse 2.1 flying / 1.25 grounded; 10 dmg + 1.4 knockback on contact for 6 ticks. 2.5 s. | dash_forward |
| G | Dive Bomb | straight-down plunge; 18 + 1.9/block (cap 80) + Meteor combo, r up to 12. 6.8 s. | `p03.dive` loop → hero_landing |
| X | Flight (`flight_toggle`) | also **double-tap jump** (client, `RevampClientA`, only when Flight is the selected mutation, not creative, no Mjolnir, no Iron Man suit). **Speed tiers**: sprinting while flying adds a tier every 2 s (0–3): flying-speed ability 0.06/0.075/0.09/0.105 and client clamp 25/32/39/46 blocks/s. Reaching tier 3 fires a sonic boom ring (6 dmg r 5, knockback, SONIC_BOOM ring, flash). Tiers bleed off 4× as fast when you stop sprinting. | float_arms (hover) / `p03.superman` (sprint) |
| Z | Orbital Drop (`orbital_drop`, new) | rocket up at 3.0/tick for ≤14 ticks (~30 blocks, stops at ceilings), 6-tick apex hover, then 3.4/tick dive: 24 + 1.2/block fallen (cap 72) + Meteor combo, r 7, plus a sonic boom. 45 s. | `p03.rise` → float_arms → `p03.dive` → hero_landing |
| V | Carry (toggle, kept) | unchanged. | `p03.carry` loop |
| C | Sonic Flight (moved from Z) | 25 s at ~50 b/s, 11-dmg shockwaves every 0.5 s, now opens with a sonic boom. 25.5 s cooldown after it ends. | power_up, `p03.superman` |
| H | Slipstream (`slipstream`, new) | 12 s: allies (squad-mates, your tamed pets, villagers, iron golems) within 16 blocks are pulled toward a point 2.5 blocks behind you (≤1.6/tick) and kept on Slow Falling. 16 s. | `p03.beckon` |
| N | Barrel Roll (`barrel_roll`, new) | sideways impulse 1.7 (+0.3 forward), alternating left/right, 10 ticks of full damage immunity (void and /kill excepted, via an `ALLOW_DAMAGE` listener in `RevampBatchA`). 3.5 s. | `p03.roll_right` / `p03.roll_left` |

Visuals: `p03.wind` flag at tier ≥2 or Sonic Flight → thick emissive wind-streak shell, brightness by synced
`p03.speed`. Dropped: Aerial Burst.

## 04 Super Speed — momentum builds while running

Momentum 0–115: +0.5/tick while sprinting (on the ground, or anywhere in Speed Mode/Overdrive), ×2 in Speed Mode,
×3 in Overdrive; drains 1.2/tick a second after you stop. Wall running, water running, step assist kept.

| Key | Ability | Numbers | Pose |
|---|---|---|---|
| R | Rapid Assault (moved from G) | 4 + ⌊momentum/25⌋ hits (≤8) of 2.4 (×2 Overdrive) to everything within 3.5 in front — each hit now actually lands (`hurtBurst`; the old version lost all but one hit to i-frames). 1.5 s. | `p04.flurry` |
| G | Speed Carry (moved from R) | unchanged carry/drop. 2.5 s. | grab_pull, `p04.carry` loop |
| X | Momentum Dash | distance × (1 + 0.8 × momentum/115); afterimage sparks. 1.7 s. | dash_forward |
| Z | Overdrive | 30 s; ~64 b/s tier, +100% melee damage attribute ("attacks ×2"), Super Speed moves ×2, **time slows**: enemies within 12 get Slowness IV (and non-players have their velocity halved) every 5 ticks. 42.5 s. | power_up |
| V | Vortex (`vortex`, replaces Whirlwind) | hold ≤8 s: you run a 3.5-radius circle (eye starts 3.5 ahead); enemies within 10 are pulled/spiralled into the eye, lifted within 4, 3.6 dmg every 10 ticks within 4.5; projectiles reversed; fire cleared. 10.2 s on release. | spin_arms |
| C | Speed Mode (toggle, kept) | unchanged numbers; Momentum ×2. | dash_forward on toggle |
| H | Phase Vibrate (`phase_vibrate`, new) | teleports through up to 2 solid block columns directly ahead (needs a wall within 1.5 blocks and a free spot within 4); never through unbreakable blocks. 5.1 s (no cooldown on failure). | `p04.vibrate` |
| N | Lightning Throw (`lightning_throw`, new) | needs ≥30 Momentum, spends all: 8 + 0.14 × momentum (≤24.1, ×2 Overdrive) to the aimed target (32 blocks) + Slowness III 1.5 s, arcs to one more enemy within 5 for half. 4 s. | throw_right |

Visuals: `p04.crackle` flag in Speed Mode / Overdrive → thin emissive yellow lightning shell swapping between two
frames every 2 ticks; `p04.intensity` (1/2) brightens it in Overdrive. Dropped: Whirlwind (Vortex replaces it).

## 12 Super Regeneration — Adrenaline

Adrenaline 0–115: +4 per point of damage taken (`AFTER_DAMAGE`), drains 0.15/tick out of combat (3 s window).
Passive regen unchanged (4 HP/s, `tickBaseRegen` public signature kept for Wolverine).

| Key | Ability | Numbers | Pose |
|---|---|---|---|
| R | Rapid Heal | 7.2 HP; +6 if 20 Adrenaline is available (spent). 5.1 s. | `p12.heal` |
| G | Purge | removes every harmful effect **except `projecthero:unstable_mutation`**. 10.2 s. | `p12.purge` |
| X | Adrenal Rush (`adrenal_rush`, replaces Recovery Burst) | lunge + Speed II / Jump II 6 s; spending 25 Adrenaline: Speed III + Resistance I. 8.5 s. | dash_forward |
| Z | Resurrection (automatic, kept) | totem rescue; now **keeps the unstable mutation effect** and fills Adrenaline. 51 s. | `p12.roar` |
| V | Cellular Surge | +2 HP / 5 ticks for 30 s (now a `surge_left` countdown). 38 s. | power_up |
| C | Regeneration Mode (toggle) | unchanged. | flex on toggle |
| H | Blood Rage (`blood_rage`, new) | needs ≥40 Adrenaline, spends all: 10 s of ATTACK_DAMAGE ×(1 + min(1, 0.25 + missing-health fraction)), re-evaluated every 10 ticks. 20 s. | `p12.roar` |
| N | Mend (`mend`, new) | allies within 8 (squad-mates, own pets, villagers, iron golems): +8 HP; spending 20 Adrenaline: +12 and Regeneration I 5 s. No allies = no cooldown. 12 s. | cast_two_hand |

Visuals: `p12.regen` (Regeneration Mode or Surge) → green emissive veins, brighter during a Surge (`p12.surge`);
`p12.rage` → red veins. Wolverine: the power key, `tickBaseRegen` and `Wolverine.hasSuperRegeneration` are
untouched. Dropped: Recovery Burst.

## 13 Super Durability — blocked damage fills Impact

Impact 0–115: every point of damage the power's layers prevent (passive 40%, Block, Tank Mode, explosion/fall
extra, Unbreakable and projectile immunity in full) banks 3 Impact (`HeroDamageRules` → `gainImpact`); each
reflected projectile banks 3. Fades 0.05/tick when not guarding. Guard bar 575 (regen 2.3/tick).

| Key | Ability | Numbers | Pose |
|---|---|---|---|
| R | Heavy Strike | 12 dmg (was 8) + Slowness IV 2 s; aimed or nearest in front. 2.5 s. | haymaker |
| G | Shoulder Charge | launch 1.8, 10 dmg per contact for 12 ticks. 6.8 s. | `p13.shoulder` |
| X | Block (hold) | unchanged mitigation; still shoves projectiles away. | guard loop |
| Z | Unbreakable | 15 s total immunity + Resistance V + knockback immunity. 42.5 s. | power_up |
| V | Deflection (`projectile_deflection`) | hold: projectiles within 3.5 are aimed back at their shooter (or reversed) at max(1.6, 1.2× speed), re-owned by you (arrows become crits). Drains guard. | shield_brace loop |
| C | Tank Mode (toggle) | unchanged. | flex on toggle |
| H | Impact Release (`impact_release`, new) | needs ≥20: 8 + 0.3 × impact (≤42.5) in r 4–8, knockback, slow; empties Impact. 8 s. | ground_pound |
| N | Taunt (`taunt`, new) | every non-ally, non-tame mob within 16 targets you (neutral mobs get persistent anger), you get Resistance I 6 s. 15 s. | `p13.taunt` |

Visuals: `p13.steel` (Tank Mode or Unbreakable) → thin metallic plating shell (face left mostly clear);
`p13.gold` (Unbreakable) tints it gold and adds a pulsing emissive glint shell.

---

## HUD meters registered (`MutationMeters`)

Laser: heat (GAUGE, always, %), overheat (METER), lance_charge (HAIRLINE), ult_charge (SLAB). Flight: speed_tier
(METER), sonic_ticks, slip_ticks (HAIRLINE). Speed: momentum (GAUGE, always, %), overdrive_ticks (SLAB), vortex_ticks.
Regen: adrenaline (GAUGE, always, %), surge_left, rage_left. Durability: impact (GAUGE, always, %), guard (METER,
max 575), unbreakable_left.

**Super Strength has no registered meters**: `AbilityHud.collectMeters` returns early for
`power_01_super_strength` and draws its own `renderStrengthExtras` rows (Maximum Effort / rush charge / Charged
Punch / Power Leap). Those still work with the kept resource names (`effort_left`, `z_charge`, `z_smash`,
`z_run_end`, `charged_cd`). Framework caveats (not fixed, AbilityHud is off-limits): the Maximum Effort bar divides
by the old 440-tick duration, so the 15 s bar starts ~68% full; and the Z box read the old shared `z_cd` — the
passive now zeroes that stale resource on join so it never sits on the Maximum Effort box. The Haymaker combo is
shown as three pips under the crosshair by `RevampClientA`.

## Unverified in-game

Everything was verified by `./gradlew build` + gametests only (no client run here):
- all poses, overlays (shell textures, eye glow, crackle frame swap, gold glint) and the chunk renderer's look;
- Flight tier feel: acceleration via `setFlyingSpeed` + the mixin clamp, the double-tap jump racing vanilla's own
  double-tap-to-stop-flying (both end up "off"), and the sonic boom ring;
- Vortex steering the player on a circle with server-set velocity (may feel rubber-bandy at high ping);
- Orbital Drop / Dive Bomb vertical velocity fighting creative-flight drag client-side (same technique as the old
  Dive Bomb);
- Slipstream pulling squad-mate *players* (server velocity on another player relies on `hurtMarked`);
- Barrel Roll direction alternates (the server can't see strafe input).
