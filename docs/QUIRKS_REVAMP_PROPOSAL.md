# Quirks revamp proposal (v0.17.x) -- for sign-off, no code yet

The user (2026-10-27): "we need to revamp most of the quirks, they kinda suck rn, come up with revamped quirks with better
animations and vfx please... show me what you want to do before you do it, i want the quirks to be at the same level as
the other abilities."

The bar is the Speed Force Speedster, Chlorokinesis (v0.17.1), Green Lantern and the Miraculous kits. One For All is out of
scope (it was reworked on its own); it is only used here as a reference point.

Rules kept throughout:
- H = power selector only. N only where the user has already assigned it (Dark Shadow hide). No H/N moves or HUD boxes.
- X = movement, V = utility / mode, R G Z C = attacks. Every key: a tap move + a Shift move; some add a hold.
- Bosses: every taunt, summon and big hit feeds the shared threat table; crowd control is capped on bosses.
- All new HUD / guide text goes through `TooltipWrap`.

---

## 1. Diagnosis: where the seven Quirks are now

The v0.16.7 pass (`docs/QUIRK_REVAMP_V0167.md`) gave every Quirk 12 moves and a meter. The skeleton is fine. What is missing
is the *feel*: the polish layer the newer kits have.

### Problems shared by all seven

| Problem | Evidence |
|---|---|
| **No first-person animation.** Every pose is third-person only. In first person you see vanilla hands with the costume sleeve on. The one exception is Engine's legs. | `client/mixin/QuirkFirstPersonArmMixin.java` only draws the sleeve; `EngineFirstPersonLegs` is the lone exception. The `fpbody/FirstPersonBody` system is not used by any Quirk. |
| **Mostly vanilla particles.** Flame, smoke, explosion, cloud, crit and sweep make up most of the VFX. | Rough counts: Hellflame 18x `FLAME`; Explosion 8x `EXPLOSION` + 10x `FLAME`; Engine 11x `SOUL_FIRE_FLAME`; Overhaul 11x `BLOCK`. |
| **Zero custom sounds.** Every Quirk SFX is a vanilla event. | `quirk/**` uses `GENERIC_EXPLODE`, `BLAZE_SHOOT`, `FIRECHARGE_USE`, `PLAYER_ATTACK_*`. No `quirk_*` entry in `sounds.json` (122 entries for other powers). |
| **No ultimate and no combo layer.** Every move is a standalone button press. Nothing chains. Only Hellflame has a power-up (Plus Ultra on Shift+V). | Compare `GreenLanternCombos` and the Speed Force momentum system. |
| **Thin survival use.** Most kits are combat-only. | Overhaul's Reassemble / Restore and Zero Gravity's Catch are the only real survival tools. Chlorokinesis has 8+. |
| **The meters are mostly a cooldown in disguise.** They fill, you get locked out, they drain. They rarely change *how* a move plays. | Explosion Sweat (power scaling) and Dark Shadow's light are the exceptions. |
| **Generic HUD.** The same six boxes and one bar for everyone. No hold-charge ring, no Shift strip, no ultimate gauge. | `client/quirk/QuirkHud.java`. Only Explosion and Zero Gravity draw their own status block. |
| **Poses with few frames and no leg detail.** Only two kits use the full 16-column `QuirkKeyframes`. | `EnginePose` and `ExplosionPose` use `QuirkKeyframes`. `HellflamePose`, `OverhaulPose`, `ErasurePose` and `ZeroGravityPose` use the older 12-column format (no leg yaw or roll). `DarkShadowPose` is mostly code-driven angles. |

### Per Quirk

| Quirk | What works | What's weak |
|---|---|---|
| **Hellflame** | Real beam and string geometry (`HellflameRender`, `BeamDraw`); Body Heat with overheat burnout. | Damage is small next to the cost: Spider 9, Barrage 3.5 a jab, Wall 3, Cloak aura 2 (`HellflameConfig`). The heat meter is only a penalty. Plus Ultra is a stat buff on Shift+V with no moment. The Fire Wall / Pillar are vanilla fire blocks plus `FLAME` particles. Flight has no body FX beyond particles. |
| **Overhaul** | The temp-terrain ledger (`OverhaulTerrain`, `GeoTerrain`); Restore; the Fusion layer (`OverhaulFusionLayer`). | The spikes are real blocks (pointed dripstone and stone columns): blocky, not jagged. The VFX are `BLOCK` crumbs only (no disassembly "burst into fragments" look). Focus is a generic stamina bar. The Gloves toggle spends a whole slot (Shift+C) on making melee safe. Disassemble has no visual on the victim. |
| **Engine** | The best Quirk: gears, momentum, Turbo, real calf nozzles, FOV mixin, sound loop (`EngineSound`). | VFX are `SOUL_FIRE_FLAME` / `CLOUD` particle trails, not ribbons. There are no first-person kick poses, so kicks look like nothing in first person. There is no ultimate: Turbo is a mode. Heat matters little now that Turbo freezes it. |
| **Dark Shadow** | A GeckoLib creature (`DarkShadowCreature`); the light/darkness meter has real identity; Berserk. | Most attacks are the same reach-and-claw (Strike, Fang, Grab). Z (Scout = Glowing, Sunshine Ankh) is weak. **C = Berserk on purpose** mostly hurts you. Tokoyami's own body barely moves (`DarkShadowPose` is procedural). There is no Abyssal Black Body moment. |
| **Erasure** | The scarf ribbon renderer (`ErasureWorld`); `PowerSuppression` reaches every power system; the swing. | **Against plain mobs the Quirk does nothing** (zombies have no powers). Damage is tiny: Whip 5, Combo 3/3/4/7, Pull 3 (`ErasureConfig`). The meter only touches the stare. Eye Drops and Goggles take two of the twelve slots. No ultimate. |
| **Explosion** | Propulsion flight; Sweat power scaling; the Howitzer spin (`QuirkBodyTurnMixin`). | **Every blast is vanilla `EXPLOSION` + `FLAME` + `GENERIC_EXPLODE`.** There is no custom blast flash, no shock ring, no palm crackle (`ExplosionBlast`, `ExplosionClient` has no renderer). Arm Strain is a pure lockout. The gauntlets are drawn on the costume but do nothing in play. Two meters, one of which never changes a move. |
| **Zero Gravity** | `ZeroGravityDebris` (BlockDisplay rubble); Float Self; the pink field dome. | Damage is thin and indirect: Touch 2, Shower 6 a block, Comet 8 (`ZeroGravityConfig`). **Catch / Settle take all of C** (no attack on C). Gunhead Martial Arts is a short grapple. No fingertip-pad visual on what you float. Nausea is shown but has no screen effect. |

---

## 2. Shared framework upgrades (build once, lift all seven)

### 2.1 Quirk VFX toolkit: `quirk/fx/`
Modelled on `ChloroFxEntity` / `HardLightDraw`: short-lived, unsaved effect entities drawn from code geometry in the
additive buffer, synced once at spawn.

| Piece | What it draws | Used by |
|---|---|---|
| `QuirkFxEntity` + `QuirkFxRenderer` | One entity, many `Kind`s, params (colour ramp, size, life) in a compact payload | all |
| `BLAST` | White flash sphere, then an expanding fire-ball shell (noise-wobbled), a ground shock ring and a smoke crown | Explosion, Hellflame |
| `RING` | Flat expanding ring (shock, gravity, steam) | all |
| `RIBBON` | Tapered trail ribbon from recorded points (exhaust, flame jets, scarf snaps, shadow tendrils) | Engine, Hellflame, Erasure, Dark Shadow |
| `CONE` | Cone sheet of flame or debris along the aim | Hellflame, Explosion, Overhaul |
| `SPIKE` | Jagged faceted spike mesh with a crack decal at its base, rising out of the ground; textured from the ground block | Overhaul |
| `SHARDS` | A burst of block-textured fragments that fly out, hang, then (optionally) fly back and reassemble | Overhaul, Zero Gravity |
| `DECAL` | Ground scorch, crack web, shadow pool or pink handprint, fading over 5-20 s | all |
| `SPRITE_FLIPBOOK` | Animated billboard (8-frame fireball, smoke puff, ink splash) | Explosion, Hellflame, Dark Shadow |

Custom particles (full-bright, coloured, real textures): `quirk_ember`, `quirk_spark`, `quirk_smoke_puff`,
`quirk_shadow_mote`, `quirk_steam`, `quirk_pink_sparkle`, `quirk_dust_chunk`.

**Camera "juice"** (`QuirkCameraFx`, client): screen shake scaled by distance, a 2-4 tick hit-stop on heavy hits, an FOV
punch on dashes, and a brief vignette (heat orange, erased red, nausea green wobble). One shared helper; each move passes a
preset. It respects a "reduce camera shake" client config.

### 2.2 First- and third-person animation pass
- **One keyframe format.** Move every pose onto `QuirkKeyframes` (16 columns, full leg rotation). Retire the 12-column
  copies and procedural angles in `HellflamePose`, `OverhaulPose`, `ErasurePose`, `ZeroGravityPose` and `DarkShadowPose`.
- **First-person arms.** Add `QuirkFirstPersonPose`, a new mixin on `ItemInHandRenderer`. It plays the move's arm frames
  (punch, palm thrust, finger snap, scarf throw) on the first-person arms. Today's first-person arm mixins (Cryo, Ladybug,
  the Quirk sleeve) only draw sleeves on `PlayerRenderer.renderHand`; they never move the arm.
- **First-person full body for the big moments.** Register Quirk ultimates and whole-body moves (Howitzer spin, Prominence
  Burn, Fusion, kicks) with `FirstPersonBody`, so in first person you see your own legs and arms do the move.
  `EngineFirstPersonLegs` becomes a special case of this.
- **Whole-body rotation.** Generalise `QuirkBodyTurnMixin` into per-move spin / flip / tilt curves (flips, spins, dives,
  the comet).
- **Anticipation, contact, recovery.** Every attack gets a wind-up frame, a hit frame matched to the server damage tick
  (the One For All rule) and a follow-through. Target: 4-8 keyframes a move instead of the current 3-5.

### 2.3 Plus Ultra: a shared ultimate gauge
- A second, thin gauge per Quirk, **Plus Ultra** (0-100). It is separate from the kit's meter.
- **It fills from fighting:** +1 a landed hit, +3 a move hit, +0.5 per HP of damage taken, +10 for saving an ally from
  death or a fall. It decays slowly out of combat (after 30 s).
- **Hold C (1 s) at 100 = the Quirk's signature ultimate.** The hold shows a charge ring on the C box, a pose, a quote on
  the action bar ("PLUS ULTRA!") and a full-body first-person view.
- The ultimate spends the whole gauge. Each one ends with its own drawback (burnout, stall, sick, arm damage).
- This replaces Hellflame's Shift+V "Plus Ultra: Full Power" and gives the other six their missing moment. C tap and
  Shift+C stay normal moves.

### 2.4 Drawbacks that matter
Each meter gets **three bands** that change how moves play, not just a lockout at 100:
- **Low:** weaker, but safe.
- **Sweet spot:** full power plus a perk.
- **Red zone:** strongest, with a real cost. You can push past 100 for a few seconds at an HP cost (the "go beyond"
  fantasy), then you crash.

Each Quirk's bands are in its section below.

### 2.5 Costume and "Quirk tells"
- The seven craftable suits stay (`QuirkSuits`). **The suit should matter in play.** Each full set gives one kit perk:
  - Bakugo's gauntlets store Sweat for the Gauntlet Grenade.
  - Endeavor's suit: +15% heat headroom.
  - Ingenium: half heat (already built).
  - Uravity's suit: -25% Nausea.
  - Aizawa's goggles: the Goggles perk passive.
  - Overhaul's mask: immune to his own spike shrapnel and poison gas.
  - Tokoyami's cloak: hides Dark Shadow's light penalty by 2 levels.
- **Tells without a suit.** Small visuals that say "this player has a Quirk" while it works, drawn as a player layer:
  - Endeavor: flame beard and brows when heat is above 40.
  - Bakugo: palms crackle with sparks as Sweat builds.
  - Uraraka: pink fingertip pads glow.
  - Overhaul: gloves (drawn) come off for moves.
  - Ingenium: calves (done).
  - Tokoyami: bird head (done).
  - Aizawa: red eyes only (the user removed the risen hair; keep it removed).

### 2.6 HUD
- **A per-Quirk meter widget instead of the plain bar**, each with the band ticks drawn on it:
  - Hellflame: a thermometer.
  - Explosion: a sweat-droplet bar plus two arm icons.
  - Zero Gravity: a stomach-wobble gauge.
  - Dark Shadow: a light / dark dial.
  - Erasure: an eye that closes as it dries.
  - Engine: gears (done).
  - Overhaul: a Matter bar (see Overhaul).
- **On the key boxes:** a Shift-move strip (reuse `AbilityHud.SHIFT_STRIPS`), a hold-charge ring, and a combo-window
  flash.
- **Plus Ultra:** a hairline gauge under the meter. It pulses gold when full and lights the C box.
- **Combo popup** (reuse the `GreenLanternComboPopup` style) when a route lands.
- Alt move list and guide pages regenerate from the kit tables (`QuirkKits` + lang).

### 2.7 Combos and synergy
- **Per-kit routes:** move A then move B within ~1 s gives B a bonus. Examples: Explosion "Stun Grenade then AP Shot" =
  crit; Erasure "Bind then Spin" = extra fling. Each route is listed in the guide and shown in the popup.
- **Squad synergy (small, optional):**
  - Erasure: erased targets take +20% from squadmates.
  - Zero Gravity: floated targets take +25% from the next hit.
  - Hellflame + Explosion fire hits on the same target within 2 s = a "Flashover" bonus burst.

### 2.8 Sounds
About 6-8 custom sound events per Quirk (charge, release, impact, loop, overload, ultimate), registered as `quirk_*` in
`sounds.json`. Vanilla blends stay as layers underneath. Examples: Bakugo's crackle and boom with a sub-thump; the Hellflame
roar loop; Overhaul's crack-and-reform; Aizawa's cloth snap; Uraraka's soft "pip" touch and the "release" clap.

---

## 3. Per-Quirk kits

Damage is in half-hearts (vanilla HP). "cd" = cooldown. Numbers are first-pass and will be tuned in a client.

### 3.1 Hellflame -- Enji Todoroki, Endeavor

**Identity:** the No. 1 hero's raw, ever-hotter fire. Every flame he throws heats his body, and his strongest moves risk
cooking him from the inside.

**Meter: Body Heat**, banded:

| Band | Heat | Effect |
|---|---|---|
| Kindle | 0-39 | Orange flames, x0.85 damage |
| Blaze | 40-79 | Yellow-white flames, x1.0, the flame beard tell |
| Inferno | 80-100 | White-hot core, x1.3, he takes 0.5/s |
| Overheat | push past 100 | Up to 5 s at x1.5 and 1 HP/s, then burnout: 8 s with no fire, Slowness |

Water and rain cool him. V vents heat as an attack.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | **Hell Spider**: five cutting fire strings sweep across the aim, 9 each (one hit a target), 14 blocks. *Anim:* fingers splayed, a right-to-left flick. *VFX:* `HellflameRender` strings plus a `DECAL` scorch line. | **Hell Spider: Mesh**: a dome cage of strings at the aim point, 4 s; trapped targets take 2.5 a half-second and can't leave. Boss: Slowness only. *Anim:* both hands weave. *VFX:* string dome, embers. | -- |
| **G** | **Flashfire Fist**: a three-punch fire combo, 6 / 6 / 10, the third a jet-assisted straight. *Anim:* jab, cross, lunge. *VFX:* `CONE` flame burst on each fist, `RING` on the third. | **Vanishing Fist**: a flame decoy stays behind, he blinks behind the target and strikes, 16. *Anim:* fade, reappear mid-swing. *VFX:* a man-shaped flame decoy (exists), a `RIBBON` streak. | **Flashfire Fist: Hellblast**: charge 0.5-2 s, then a sustained beam, 7 every 0.25 s, heat +12/s. *Anim:* fist drawn to the hip, arm locked out. *VFX:* the existing beam, plus heat shimmer and a `BLAST` at the impact point. |
| **X** | **Jet Burst**: a flame-thrust dash, 10 blocks, works in the air, no damage. *Anim:* palms back, body flat. *VFX:* twin `RIBBON` jets from the palms. | **Flashfire Fist: Jet Burn**: a dash punch through everything in a 14-block line, 16. *Anim:* a fist-first rocket. *VFX:* a `CONE` fireball at the front, a scorch decal trail. | **Fire Jet Flight**: sustained flight, 18 b/s (sprint 26), heat +3/s. *Anim:* palms down to hover, arms back like rudders in flight. *VFX:* feet and palm jets as ribbons, smoke trail. |
| **Z** | **Hellflame Barrage**: 8 rapid fire jabs, 3.5 each, the last one launches. *Anim:* alternating fast jabs. *VFX:* a flipbook fireball per jab. | **Hellflame Pillar**: the ground glows, then a pillar erupts under the target, 14 plus a 3 s linger at 2/s. *Anim:* fist to the ground. *VFX:* crack `DECAL`, then a column of flame sheets. | -- |
| **C** | **Flame Wave**: a wide 90° cone wave of fire, 10 blocks, 11 plus 4 s of burning, knockback. *Anim:* a two-handed sweep. *VFX:* a rolling `CONE` sheet with a flipbook edge. | **Flame Cloak**: a 12 s burning aura; 3/s to anything within 3 blocks; melee attackers ignite; +2 armour. Heat +2/s. *Anim:* fists clenched, chest out. *VFX:* body flames, beard and shoulders (exists) plus a heat ring. | **Ultimate** (Plus Ultra full) |
| **V** | **Heat Vent**: dumps up to 60 heat in a steam-and-flame burst around him. Damage 4 + 0.18 per heat vented, radius 4, knockback. Works while overheated. *Anim:* arms flung wide, head back. *VFX:* a `RING` of steam, a white flash. | **Fire Wall**: a 7x3 wall of flame for 8 s; blocks projectiles, burns anything crossing it (4). It sits on solid ground and never lights flammables. *Anim:* palm swept along the ground. *VFX:* flame sheets in place of vanilla fire blocks. | -- |

**Passives**
- Fire and burning immunity (not lava).
- The heat bands above.
- **Hot hands:** melee sets things on fire at Blaze or higher.
- Immune to freezing and powder snow; nearby snow and ice melt while he is in Inferno.

**Survival / utility**
- **Forge Hand:** with an empty main hand and raw food or ore in the off hand, Sneak + use cooks or smelts it. 4 heat each.
- **Flight** and the Jet Burst escape.
- **Heat Vent** puts out fires on him and allies, and evaporates a 3x3 area of water (for builds and getting out).

**Ultimate: Plus Ultra -- Prominence Burn.**
- 1.5 s ignition: he becomes a white-hot sun, heat pins at 100, arms flung out.
- Then a comet charge: he flies up to 40 blocks along the aim, burning through every target (20 each), and detonates at the
  end for 45 in radius 8. Boss cap applies.
- Afterwards: 10 s burnout.
- *Anim:* full-body first person, dive pose.
- *VFX:* a sun sphere with a corona, a white-orange `RIBBON` tail, a huge `BLAST`, a scorched crater decal.

### 3.2 Overhaul -- Kai Chisaki

**Identity:** a touch takes anything apart (people, the ground, himself) and puts it back together, as a weapon or as a
cure. He hates being touched.

**Meter: Matter** (replaces Focus, 0-100). Disassembled matter is what he rebuilds with:
- Refills 10/s while he stands on stone or earth (his feet draw from the ground), 3/s on wood or in the air.
- +15 for each mob a Disassemble kills.
- Spikes, walls and Fusion **spend** Matter; Disassemble moves **gain** it.

Bands:

| Band | Matter | Effect |
|---|---|---|
| Starved | 0-24 | Constructs are small |
| Stocked | 25-79 | Normal |
| Overflowing | 80-100 | Spikes and walls are 1.5x size |

**Gloves:** drawn on by default. A move tugs a glove off for 8 s, and bare-hand melee then disassembles (+4). The Gloves
toggle and its slot are gone.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | **Disassemble**: a bare-hand touch, 18; a kill bursts on everything within 3.5 (6). +5 Matter. *Anim:* glove tug, two-finger touch. *VFX:* the victim cracks with red lines, then bursts into `SHARDS`. | **Disarm**: the held item or an armour piece is destroyed (a player's is knocked loose). Not bosses. *Anim:* a quick wrist grab. *VFX:* the item `SHARDS` apart. | -- |
| **G** | **Ground Spikes**: a jagged line of spikes erupts toward the target, 14 blocks, 10 and a toss. 12 Matter. *Anim:* palm slapped to the floor. *VFX:* `SPIKE` meshes rising in sequence, a crack decal. | **Spike Field**: two rings of spikes round him, 9 to everything within 5. 20 Matter. *Anim:* both palms down. *VFX:* ring of `SPIKE`s. | **Thorn Forest**: aim a marker, release: a 7x7 forest of 6-block spikes rises in waves at the point, 16 an impale, then stands 4 s as cover / walls. 40 Matter. *Anim:* kneel, palms pressed, rising slowly. |
| **X** | **Ground Shift**: the floor slides him 8 blocks the way he looks, as a rippling wave. *Anim:* standing still, carried. *VFX:* a ripple of lifted blocks (`SHARDS` that settle). | **Pillar Launch**: a 3-block pillar flings him high, no fall damage for 5 s. 10 Matter. | **Terrain Surf**: kept as built (rides while held, step assist, wake ridge). 8 Matter/s. |
| **Z** | **Overhaul Strike**: a low dash-touch, 14 and a throw. *Anim:* lunge, hand out. *VFX:* `SHARDS` on hit. | **Disassemble Terrain**: the floor opens under up to 6 creatures near the aim; each drops into a pit that closes, 4 s. Not bosses. +10 Matter. | -- |
| **C** | **Shrapnel Burst**: the ground ahead is taken apart and flung as a cone of stone shards, 3 x 5, 8 blocks. +8 Matter. *Anim:* palm swept upward. *VFX:* a `CONE` of block-textured `SHARDS`. | **Wall Burst**: a 5x3 wall bursts up ahead for 10 s, throwing back what stood there (5). 16 Matter. *VFX:* the wall assembles from flying fragments. | **Ultimate** |
| **V** | **Reassemble**: you, or the ally you look at, healed to full with debuffs gone. 30 Matter, cd 45 s. *Anim:* palm on chest / on the ally. *VFX:* a body outline breaks to `SHARDS` and snaps back, white-teal. | **Chronostasis Fusion** (mode, kept): 20 s, x1.5 size, stone plates, two extra spiked arms, +8 damage, +12 armour. 45 Matter. *VFX:* fusion `SHARDS` swirl in, then plates. | **Restore**: hold 1 s: every block an explosion broke within 12 blocks in the last 5 minutes rebuilds as a wave (the existing `OverhaulRestore`). |

**Passives**
- Matter from the ground.
- The glove tell.
- **Germophobe:** after a mob hits him, his next move within 3 s is +20% damage.

**Survival / utility**
- **Mend:** Sneak + V with a damaged tool or armour piece in the off hand reassembles it, +100 durability for 20 Matter.
- **Disassembly mining:** a bare-hand Sneak + punch breaks a 3x3 of stone or earth into its drops. 6 Matter, needs
  terrain rights.
- **Restore** (creeper repair) and **Reassemble** (heal).

**Ultimate: Overhaul -- Total Disassembly.**
- Both palms on the ground. A 16-block radius breaks apart and rises as a sea of spikes rolling outward in three waves
  (12 each, toss), then reassembles perfectly behind them (ledger-restored).
- During it he is fused (x1.5) and can't be knocked back.
- *VFX:* a crack web decal out to 16 blocks, `SPIKE` waves, everything reforming in a white-teal ripple.

### 3.3 Engine -- Tenya Iida, Ingenium

**Identity:** engines in the calves. Speed, discipline and kicks; Recipro Burst trades a short overdrive for a stall.

This is the strongest Quirk and was tuned by the user very recently. **The kit stays as built**
(`QUIRK_REVAMP_V0167.md`). The proposal only adds polish, hold moves and the ultimate.

**Meter: Engine Heat** (kept), banded:

| Band | Heat | Effect |
|---|---|---|
| Cold | 0-29 | -10% momentum gain |
| Warm | 30-79 | Normal |
| Redline | 80-99 | +15% speed-scaled damage, a red exhaust glow |
| Stall | 100 | Stalls, as now |

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | Recipro Kick (kept: 5 + 0.55 per b/s) | Spin Kick (kept) | **Charged Kick** (new): hold up to 1 s while running, release for 1.5x damage and launch. *VFX:* nozzle flare builds. |
| **G** | Kick Barrage (kept) | Recipro Extend Kick (kept) | -- |
| **X** | Recipro Dash (kept; the Turbo Strike in Turbo) | Rising Kick (kept) | **Engine Brake** (new): hold while running for a controlled skid that keeps 85% momentum and drifts the turn. *VFX:* a skid `DECAL` and sparks. |
| **Z** | Axe Kick (kept) | Sweep Kick (kept) | -- |
| **C** | Recipro Turbo (kept, the mode) | Ingenium Finisher (kept) | **Ultimate** |
| **V** | Gear Up (kept) | Gear Down (kept) | -- |

**Polish**
- **First-person kicks:** every kick plays in first person through `FirstPersonBody` (your leg swings into view). This is
  today's biggest gap for this kit.
- **Exhaust as ribbons:** the `SOUL_FIRE_FLAME` / `CLOUD` trails become `RIBBON`s out of the twelve nozzles (blue, white
  in Turbo, orange when stalled), with a speed afterimage at Gear 3 (Speed Force echo style).
- **Kick impacts:** a `RING` plus hit-stop on every connecting kick; the Finisher gets a camera punch.

**Passives:** step assist and passive cooling (kept); 60% less fall damage in gear (kept).

**Survival / utility**
- **Water skim:** at Gear 3 with 70% momentum he runs across water.
- **Wall kick:** sprinting into a wall in gear runs up it, up to 4 blocks.
- **Rescue Carry** (returning from the first draft): Sneak + use on an ally carries them while you run.

**Ultimate: Recipro Burst -- Extend.**
- Hold C: a 1 s rev (the nozzles go white-hot, the pose crouches into a sprinter's stance).
- Then 6 s of Recipro Burst: the speed cap doubles past Turbo and every kick chains to the next target within 10 blocks
  automatically (up to 6 kicks, 12 + 0.4 per b/s each), finishing with a mach kick (35).
- Then a 30 s stall.
- *VFX:* blue-white afterimage chain, `RING` per kick, a sonic-boom `RING` at the start.

### 3.4 Dark Shadow -- Fumikage Tokoyami, Tsukuyomi

**Identity:** a living shadow bird that is timid in light and terrifying in darkness. The deeper the dark, the stronger and
the less controllable it is.

**Meter: Shadow** (kept; the light rules are good), with clearer bands:

| Band | Shadow | Effect |
|---|---|---|
| Restrained (bright) | 0-29 | x0.6, small, timid |
| Hunting | 30-74 | x1.0 |
| Ravenous | 75-99 | x1.4; its hits heal you 1 |
| Berserk | 100 | As now, until light calms it |

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | **Shadow Strike**: a long-reach claw, 8 (5-10 blocks, by the dark). *Anim (Tokoyami):* points, then commands. *VFX:* a shadow `RIBBON` tendril plus after-image smears (exists). | **Dark Fang**: a lunging 4-bite flurry, 4 each. *VFX:* an ink-splash flipbook per bite. | -- |
| **G** | **Shadow Grab**: wraps a mob, lifts it, slams it (10). *Anim:* Tokoyami's fist closes as it squeezes. | **Shadow Shield**: (hold) it coils round you and soaks 70% from every side, Shadow +4/s. *VFX:* a wing cocoon. | **Shadow Toss** (new): hold after a grab to carry the mob, release to throw it along your aim (12 + impact splash). |
| **X** | **Shadow Lunge** (new): it grabs a point 10 blocks ahead and yanks you there. *VFX:* a tendril, a black streak. | **Fetch**: carries you to the look point, or snatches items there (kept). | **Black Fallen Angel**: Abyssal Ankh gliding flight with wings (kept, now a hold). Boosted in the dark. |
| **Z** | **Shadow Scout**: it flies your look path and marks every mob near it for 10 s; **marked mobs take +15% from you** (new). *VFX:* violet eye glyph above marked mobs. | **Sunshine Ankh**: a nova of shadow feathers, 6 to everything within 6, knockback (buffed from flavour-only). *VFX:* black feather flipbook burst, a dark `RING`. | -- |
| **C** | **Abyssal Rampage** (new attack): three sweeping claw arcs in front, 7 / 7 / 11, 5 blocks. *Anim:* Tokoyami braced, Dark Shadow swings over his head. *VFX:* black crescent `RIBBON`s. | **Unleash**: a controlled Berserk in darkness only. 8 s of Berserk that never hits you or your squad, then the meter drops to 40. Replaces "Berserk on purpose". | **Ultimate** |
| **V** | **Black Ankh** (mode, kept): it coats you as armour, claws, reach. | **Light Flare** (moved here): needs a light source or daylight; blinds nearby mobs and calms or ends Berserk. N stays Hide, as the user assigned. | -- |

**Passives**
- Light/dark scaling.
- **Night owl:** night vision while in Black Ankh.
- **Shadow catch:** if you fall more than 6 blocks with Shadow above 30, it catches you (no fall damage, cd 20 s).

**Survival / utility**
- **Fetch** (items, gaps) and **Black Fallen Angel** gliding.
- **Shadow Scout** as cave recon: it marks mobs through walls.
- **Shadow catch** stops falls.

**Ultimate: Abyssal Black Body.**
- Dark Shadow fully merges with him: a towering winged black-bird armour (x1.6 scale, the Ankh layer upgraded, glowing
  eyes).
- 12 s of fast flight with a claw dive on attack (14 each, through targets).
- Shadow is frozen at Ravenous and can't go berserk while it lasts.
- Afterwards: 5 s Hidden.
- *VFX:* black feather storm, violet eye trails, a dive `RIBBON`.

### 3.5 Erasure -- Shota Aizawa, Eraser Head

**Identity:** an underground pro who switches off Quirks with a stare and wins with a capture weapon, close combat and
nerve. He gets weaker the longer he goes without blinking.

**Meter: Dry Eyes** (kept). The fix is that **erasure itself becomes a combat tool against everything**:
- **Erased targets are Exposed:** +20% damage taken, no sprint, no jump boost. On mobs, ranged and special attacks are
  already cancelled (exists).
- **Every scarf hit on an erased target ties it** for 1 s.

Bands:

| Band | Dry Eyes | Effect |
|---|---|---|
| Fresh | 0-49 | Normal |
| Strained | 50-89 | The stare reaches 32 blocks (from 24) and the screen edge reddens |
| Burning | 90-100 | The stare hits two targets but you take 0.5/s |
| Forced shut | 100 | As now |

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | **Capture Bind**: scarf ties the target, rooted 4 s (players: softened). Not bosses. | **Capture Pull**: yank the bound or looked-at target to you, 6 (buffed from 3), into a free follow-up. | **Reel Lock** (new): hold to keep a bound target strung up in place as long as you hold (Dry Eyes +5/s), 2/s. |
| **G** | **Close Combat**: jab, elbow, knee, spinning kick, 5 / 5 / 6 / 10 (buffed). | **Counter**: 0.6 s parry; caught hit = 0, attacker takes 10 and is tied 2 s. | -- |
| **X** | **Scarf Pull**: zip to a block or creature (6 on arrival). | **Evade Flip** (new): backflip 5 blocks with 0.3 s of i-frames. *Anim:* full body flip (spin curve). | **Scarf Swing**: kept. |
| **Z** | **Bind and Spin**: kept, 8 a hit, 10 on the fling. | **Disarm Throw**: kept. | -- |
| **C** | **Scarf Whip**: a cracking lash, 10 blocks, 7 and a stagger. *VFX:* cloth snap `RIBBON` plus a crack ring. | **Multi-Bind** (new): the scarf fans out and ties up to 3 targets in a cone for 2.5 s. | **Ultimate** |
| **V** | **Erasure Stare** (hold, kept). *VFX:* red eye glow plus a faint red sight-line `RIBBON` only you see. | **Goggles** (moved from Shift+C, kept). | -- |

**Eye Drops** leave the key layout: the item is used by right-click (it already works that way). That frees a slot for a
real attack.

**Passives**
- Exposed on erased targets.
- **Underground:** while sneaking in the dark, mobs detect you at half range.
- **Catnap:** see survival.

**Survival / utility**
- **Scarf Swing** and the Evade Flip.
- **Pull to safety:** Capture Pull on an ally drags them out of lava, water or a fall.
- **Aizawa's Sleeping Bag** (new item, Erasure-flavoured, anyone can use it): sleep anywhere to skip the night without
  moving your spawn, and wake with Dry Eyes at 0.

**Ultimate: Underground Takedown** (his USJ entrance).
- Goggles snap down. Everything in a 20-block cone is erased for 6 s.
- Then he chains through them automatically: zip, kick (10), bind; up to 6 targets.
- It ends with all of them strung up for 2 s.
- Bosses: erased (special moves off) for 4 s, not bound.
- *VFX:* red stare flash across the cone, scarf `RIBBON`s linking every target, cloth snap SFX per hit.

### 3.6 Explosion -- Katsuki Bakugo, Ground Zero

**Identity:** nitroglycerin sweat from his palms. Point-blank power, propulsion flight and pure aggression; the more he
sweats, the bigger the boom.

**Meters: Sweat** (fuel, kept) **plus Arm Strain** (now a real drawback).
- Sweat bands:

  | Band | Sweat | Effect |
  |---|---|---|
  | Dry | 0-29 | x0.6 sputter |
  | Primed | 30-79 | x1.0 |
  | Soaked | 80-100 | x1.4 plus palm sparks |

- **Arm Strain** now does more than lock you out:
  - Above 70, blasts gain +15% but cost 0.5 HP each.
  - At 100: "arms shot", 6 s with no blasts and Weakness II.
  - The Ground Zero gauntlets bank 50 Sweat for the Gauntlet Grenade.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | **AP Shot**: a compressed piercing palm blast, 10, 32 blocks. *Anim:* left hand braces the right wrist, palm forward. *VFX:* a tight `CONE` flash plus a `BLAST` at the hit. | **AP Shot: Auto-Cannon**: 8 rapid shots, 4 each. *Anim:* palm stays out, recoil each shot. | **AP Shot: Pierce** (new): charge up to 1.5 s, a thin piercing beam to 48 blocks, up to 22. |
| **G** | **Explosive Punch**: lunge, point-blank blast, 14, heavy knockback. | **Blast Rush**: 6 blasts march along the ground, 8 each. | **Crush Grip** (new): grab the target's face, three detonations (6 / 6 / 12), then throw. Not bosses (they take the hits, no grab). |
| **X** | **Explosion Dash**: a directional back-blast, about 12 blocks (the direction comes from WASD). | **Spin Dodge** (new): a sideways blast spin, 5 blocks, i-frames 0.3 s, ends facing the attacker. | **Explosion Propulsion**: flight, kept (22 b/s, sprint 33). |
| **Z** | **Cluster**: two rings of blasts round you, 9. | **Explosive Uppercut** (new): a rising blast uppercut, 12, launches the target and you after it for an air follow-up. | -- |
| **C** | **Howitzer Impact**: spin-rise, corkscrew dive, 30 in radius 6. Needs 30 Sweat. | **Gauntlet Grenade**: pin pull, all banked Sweat down one cannon line, 28 (needs 50 Sweat or the gauntlets' bank). | **Ultimate** |
| **V** | **Stun Grenade**: blinds and slows everything within 8 for 5 s; mobs drop their target. | **Cool Off**: -40 Sweat, -50 Arm Strain. Works while strained. | -- |

**The explosion overhaul** (the headline fix):
- Every blast becomes a `BLAST` fx: a white flash frame, an orange flipbook fireball, a ground shock ring, a smoke crown,
  sparks.
- Scaled by Sweat band, with camera shake and hit-stop.
- A custom `quirk_explosion_*` sound set with a sub-bass thump; no more `GENERIC_EXPLODE`.

**Passives**
- Sweat builds with heat and exertion (kept).
- **Palm crackle tell:** sparks and small pops at Soaked.
- **Landing blast:** a fall over 8 blocks with 10+ Sweat auto-blasts under him, no fall damage, 10 Sweat.

**Survival / utility**
- **Propulsion flight** and Spin Dodge.
- **Blast mining:** Sneak + punch blasts a 3x3 of soft stone or earth into drops. 5 Sweat, needs terrain rights.
- **Stun Grenade** as the escape button. The **landing blast** saves falls.

**Ultimate: Howitzer Impact -- Cluster.**
- A spinning rise that marks up to 5 targets in a 20-block radius.
- Then a corkscrew chain-dive through each, 15 a target, finishing with a 40 blast in radius 8.
- Afterwards: Arm Strain set to 100 (arms shot).
- *Anim:* full-body first person through the spin.
- *VFX:* a spiral fire `RIBBON` vortex, a `BLAST` per target, a giant final blast and a crater decal.

### 3.7 Zero Gravity -- Ochaco Uraraka, Uravity

**Identity:** five fingertip pads make what she touches weightless. Release brings it all down. It is a rescue Quirk that
hits hard with whatever she floats, and pushing it makes her sick.

**Meter: Nausea** (kept), banded:

| Band | Nausea | Effect |
|---|---|---|
| Steady | 0-49 | Normal |
| Queasy | 50-84 | Screen sway, +25% float capacity |
| Pushing it | 85-99 | Strong sway, x1.3 drop damage |
| Sick | 100 | 5 s, can't touch, everything floated drops |

Floating more mass costs more Nausea per second. Uravity's suit gives -25%.

**Touch marks:** everything she floats gets glowing pink fingertip prints (a `DECAL` on the entity) and slowly rotates. The
whole kit revolves around touch, then release.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | **Comet Toss**: throw the floated thing you look at (block, mob or item) hard, 10 plus 4 splash. Empty: throws a chunk of ground. *VFX:* pink motion `RIBBON`. | **Debris Barrage** (new): every orbiting debris piece fires as a shotgun along the aim, 4 each. | -- |
| **G** | **Gunhead Martial Arts**: palm strike, touch, arm-lock throw, 4 / 4 / 8; the throw floats the target. *Anim:* Gunhead stance, a proper 3-hit set. | **Zero Gravity Slam**: float the target up 8 blocks, then release, 8 plus its fall damage. | -- |
| **X** | **Zero-G Leap**: a huge weightless leap, no fall damage. | **Weightless Push** (new): an air dash off nothing, 8 blocks, usable twice in the air. | **Float Self**: hold to stay weightless (glide, drift); Nausea +4/s. |
| **Z** | **Zero Gravity Field**: everything in radius 5 floats for 4 s. | **Debris Shield**: orbiting floated blocks block projectiles (kept). | -- |
| **C** | **Meteor Shower**: tear up a field of rubble into a cloud overhead; C again or Shift+V rains it on the aim, 6 a block (x band). *VFX:* existing debris plus pink rings and a dust burst on every impact. | **Gravity Bomb** (new): float a 5x3x5 slab of ground overhead, then drop it on the aim, 20 in radius 4; the slab is restored after. | **Ultimate** |
| **V** | **Zero Gravity Touch**: touch a mob, item or block and it floats. | **Release**: fingertips together, everything floated drops (damage from height). Sneak + hold = **Settle**: a safe drop with no damage, then a Nausea vent. | **Catch**: hold to float everyone falling within 12 blocks (allies, mobs you choose not to drop). Rescue first. |

**Passives**
- Touch marks.
- **Light on her feet:** no fall damage below Queasy.
- **Float pickup:** dropped items near her drift to her.

**Survival / utility**
- **Crane:** Touch a placed block to float it; Release drops it where you point. Move blocks without breaking them (no
  block entities).
- **Herding:** floated animals follow her for 20 s (move livestock).
- **Catch / Settle:** rescue and safe landings.

**Ultimate: Zero Gravity -- Full Release.**
- Fingertips spread: every mob, loose block and item within 16 floats up for 4 s (bosses: rooted and lifted 3 blocks).
- Then she claps her fingertips together ("Release!"): everything crashes down, fall damage by height plus a Meteor Shower
  of the floated rubble.
- Afterwards: Nausea 100, sick.
- *VFX:* a pink dome burst, fingertip prints on everything, rings, a dust shockwave on the crash.

---

## 4. Build order, effort and open questions

### Order (one framework build, then three parallel waves)

| Wave | Work | Why first | Effort |
|---|---|---|---|
| **0. Framework** | `quirk/fx` toolkit (`QuirkFxEntity` + kinds, particles), `QuirkCameraFx`, `QuirkFirstPersonPose` + `FirstPersonBody` hook, 16-col keyframe migration helper, Plus Ultra gauge + hold-C ultimate plumbing, HUD widget slots / strips / combo popup, `quirk_*` sound registry | Everything depends on it | Large: about one full agent build |
| **1. Explosion + Hellflame** (parallel) | Both lean on `BLAST` / `CONE` / `RIBBON` | The worst vanilla-particle look; the most iconic kits | Medium-large each |
| **2. Erasure + Zero Gravity** (parallel) | Kit changes (Exposed, Multi-Bind, crane, Gravity Bomb), poses | The weakest kits in play | Medium each |
| **3. Overhaul + Dark Shadow + Engine** (parallel) | Overhaul: Matter, `SPIKE` / `SHARDS`, Total Disassembly. Dark Shadow: new C / Z, ultimate form. Engine: FP kicks, ribbons, ultimate | Already the most built; mostly polish | Overhaul medium-large; Dark Shadow medium; Engine small-medium |

- Each kit: its own worktree branch.
- Each build ends with lang + guide regeneration, `QuirkKits` tables, versioned configs migrated, gametests updated, and
  `copyToModpack` (not while the game runs).
- Gametests run in CI, not locally.
- Total: about 4 build sessions (the framework, then three waves of 2-3 parallel agents).

### Open questions (for the user)

1. **Plus Ultra gauge + hold-C ultimate for all seven:** OK, or do you want each Quirk's ultimate on a different trigger?
2. **Overhaul:** replace Focus with ground-fed **Matter** and drop the Gloves toggle (gloves come off automatically on a
   move)?
3. **Erasure:** should erased targets take **+20% damage** (so Aizawa is strong against normal mobs, not only powered
   foes)?
4. **Engine:** leave the kit as you tuned it and only add the FP kicks, ribbon VFX, two hold moves and the ultimate?
5. **Dark Shadow:** move Light Flare to Shift+V (N stays Hide) and make C an attack (Abyssal Rampage) with Berserk on
   Shift+C as "Unleash"?
6. **Sounds:** OK to add about 6-8 custom sound files per Quirk, or stay with vanilla blends?
