# The Oathbreaker — reference (v0.14.0, tuned in v0.13.9 and v0.13.10, extended in v0.13.19)

> **v0.13.19**: two new attacks, 50-block tracking, and a threat system so he actually turns on whoever is
> hurting him. All numbers in `OathbreakerTuning` (sections "threat / target switching", "Oathbound Whirlwind",
> "Grave Geysers"); damage goes through `strike()`, so the phase multipliers (x1.1 / x1.25) and the 10%-of-max-HP
> cap against other bosses apply as usual.
>
> - **Oathbound Whirlwind** (`Attack.WHIRLWIND`, all phases). 0.7 s wind-up, sword drawn low behind him (low
>   glint, grindstone scrape) -> **two full spins** in 0.8 s (hyper armor), each cutting **everyone within 5 blocks
>   all the way round** (horizontal, from his feet -- `radiusTargets`) for **11** on the contact frames at 0.20 s
>   and 0.60 s: the first barely shoves (0.35), the second throws you out (1.1) -> **1.5 s dizzy recovery**, no
>   hyper armor, soul wisps circling his helm (the punish window). Own 8 s cooldown. Favoured whenever he's
>   **surrounded**: a valid player within 5 blocks more than 100 degrees off his facing (behind him), or 2+ players
>   within 5 blocks. While surrounded and off cooldown he rolls 50% every 0.5 s *before any other pick* (whoever
>   his target is and wherever they stand), and in the melee pool its weight jumps from 12/10/10 to 50. Phase 2+
>   adds a soul-fire ring on each contact.
>   - *Animation*: `whirlwind_windup` (14 ticks) and `whirlwind_strike` (46 ticks = spins 16 + recovery 30, one
>     clip across two Java steps). The spin is a **root-bone Y rotation 0 -> 719 degrees**, linear, held at 719 to
>     the end of the clip. Why 719 and why one clip: GeckoLib's bone reset (`AnimationProcessor`) snaps a
>     "suspected completed rotation" back to 0 when no clip animates the bone any more -- but only for values at or
>     just *below* a whole number of turns -- whereas a following clip keyed at 0 would lerp him 719 degrees
>     backwards over the controller's 1-tick transition. The build script fails if any other clip keys root
>     rotation, if the root isn't 719 from the end of the spins, or if SPIN + RECOVER != STRIKE.
> - **Grave Geysers** (`Attack.GRAVE_GEYSERS`, phase 2+). 0.8 s: both hands on the hilt overhead, reverse grip
>   (glint up high, respawn-anchor charge) -> plunge, down on one knee; the blade goes in 0.15 s into
>   `geyser_plunge` (small shake, mace slam, anchor deplete) and **a ring of soul particles appears under every
>   valid player within 30 blocks** (plus his target if it isn't a player; nearest 8). The rings **follow their
>   player for 0.5 s, then lock** (anchor-charge ping, ring turns to soul fire), and **0.75 s later** a 4.5-block
>   soul-fire column erupts on each: **15** damage, launched ~3.5 blocks up (y velocity 0.75), 2 s of fire, once
>   per cast even where columns overlap. He stays **bowed over the planted sword for 2.2 s** (`geyser_bowed`, no
>   hyper armor); the columns go off 0.9 s into it, leaving ~1.3 s to punish. Own 12 s cooldown. At range (6-30
>   blocks) it's rolled alongside the Chains once a second -- 25%, or 45% with 2+ players within 30 blocks -- and
>   it's in the phase 2/3 melee pools at weight 8/10 (doubled with 2+ players). The casts are hazards (like Soul
>   Rend's lines): a stagger after the plunge doesn't stop columns that are already coming.
> - **Tracking from 50 blocks.** `FOLLOW_RANGE` and the boss-bar radius 48 -> 50. The only target goal left is a
>   `NearestAttackableTargetGoal` (priority 2) with `mustSee = false` and its `TargetingConditions` rebuilt with
>   `ignoreLineOfSight()` -- in 1.21.1 `mustSee` only affects *keeping* a target; *acquiring* one checked line of
>   sight regardless. So he picks up the nearest valid player within 50 blocks through walls and never drops one
>   for stepping out of view -- only for leaving 50 blocks, dying, going creative/spectator or changing dimension.
>   Its `canUse` is overridden to stand down while he already has a target, so it never overwrites a target the
>   threat system chose. **Saved worlds:** vanilla saves attribute base values, so `readAdditionalSaveData` now
>   re-applies `FOLLOW_RANGE` (a boss saved by an older version would otherwise keep 48 forever).
> - **Threat / target switching** (`entity/OathbreakerThreat.java`). User report: "if my friend is just luring him
>   in 1 direction I'm able to just spam hit him and he doesn't change priority to me". Root cause: vanilla
>   `HurtByTargetGoal` only retargets in `start()`; once it was running with the friend as target, other players'
>   hits did nothing, and the lower-priority nearest-player goal couldn't interrupt it. **`HurtByTargetGoal` is
>   gone.** Every landed hit (`OathbreakerEntity#hurt` -> `OathbreakerCombat#noteDamageTaken`) adds its raw
>   damage (min 1) as threat for the attacker (projectiles count for the shooter); threat halves every 5 s. On
>   every hit from someone who isn't his target, and every 1 s for the strongest non-target attacker, he
>   **switches** when: he has no target; or -- outside a **1.75 s lockout** after the last switch -- the attacker's
>   threat is **more than 1.25x** the target's (a target who has never hit him has 0, which is exactly the lure
>   case), or the target **hasn't hurt him for 4 s** while the attacker has and is **closer**. Candidates must be
>   valid (`isValidTarget`: never creative/spectator), attackable (`Mob#canAttack`: not invulnerable, not
>   peaceful), in his dimension and within 50 blocks. **Never** while holding an Execution victim (hold, and the
>   impale up to its contact frame). A switch mid-wind-up re-aims naturally: every wind-up tracks
>   `boss.getTarget()`. Oath Guard eligibility ("the target hit him in the last 3 s") now reads the threat table,
>   so it follows the current target. Because the threat system sets targets outside any goal, combat also drops a
>   target that's left the dimension or gone past 54 blocks. Parried hits add no threat (the riposte answers them).
> - **GameTests** (`src/gametest/.../OathbreakerGameTests.java`, each boss test in its own batch): the switching
>   rule directly (lure case, margin, lockout, idle rule, half-life); the rule on the real entity via `hurt()` from
>   NoAI husks (the mock player is always "creative"); the follow range surviving a save/load from 48; both new
>   attacks run every step with the right hyper-armor state, the whirlwind hits a husk in front AND behind, the
>   geysers hit (and ignite) a target 10 blocks away and not a bystander. Test hooks: `OathbreakerEntity`
>   `debugBeginAttack / debugActiveAttack / debugAttackStep / debugHoldAttacks / debugThreat`.
> - **Not verified in-client:** how the two new clips actually look (the spin direction relative to the blade edge,
>   the dizzy sway, the reverse-grip raise and plunge), the particle telegraphs, and real multiplayer switching.
>   Known edge: a **stagger mid-spin** interrupts `whirlwind_strike` at a non-whole rotation, so GeckoLib lerps the
>   root back to 0 over one tick (a brief reverse whip) -- accepted, it's already a violent interruption.

> **v0.13.10**: every damage number roughly **halved** -- once v0.13.9 fixed his aim his hits finally landed,
> and on top of the +40% pass he was overwhelming. Now: dash 20, combo 7/hit, riposte 13, leap 14, rend 9,
> Judgement 24/7 + 3/s ring, Execution 28, spear 10, shockwave 6 (all `mobAttack`, so Hard still x1.5).

> **v0.13.9** (released after 0.14.0 on purpose -- the user wanted the patch number, not a minor bump):
> - **Aim fix.** Every damage cone is measured along `forward()` = his *yaw*, and vanilla only moves a mob's yaw
>   while it walks -- the look control turns just the head. Against a player who stood still, his swings went
>   out along a stale heading up to ~75 degrees off and whiffed. `OathbreakerCombat.faceTarget` now turns
>   yaw/body/head together: snapped at every attack start, 20 degrees/tick through wind-ups, 30 degrees/tick
>   between attacks. Harness: a target parked 90 degrees off his heading took 10 of 10 strikes.
> - **Damage +40%** across the board (dash 42, combo 14, riposte 26, leap 28, rend 17, Judgement 48/14 + 6/s
>   ring, Execution 55, spear 21, shockwave 12). Combo reach 3.5 -> 4.
> - **More aggressive.** Shared cooldowns 50/40/30 -> 24/18/12 ticks. The Stance Dash is also a gap-closer: a
>   target 5-10 blocks away with a clear line gets a 50% roll every 10 ticks.
> - **Longer dash.** The lunge goes to where the target stood as it committed (1 short), 3-9 blocks (was a
>   fixed 4), and cuts everyone within 2 blocks of its whole path, once each (`dashSweep`), plus the 6-block
>   end cone. Harness: connected from 7 blocks.
> - **Faster:** 0.19 / 0.22 / 0.26 (~1.6 / 2.1 / 3.0 blocks/s).
> - **Walk rebuilt.** The free arm swung with the same-side leg (pacing), the torso roll splayed the legs, and a
>   1 s stride covered ~3 blocks while he moved ~1 block/s (feet skated). Now +-24 degree strides on a 1.4 s
>   cycle with a heel-strike "load" beat, opposite-arm swing, slight lean; phase 2 plays it 1.25x; the run is
>   +-32 degrees on 1.0 s. The stride maths is in the build script's comments.
> - **No glow.** `AutoGlowingGeoLayer` removed, glowmask PNGs deleted and no longer generated, and
>   `EntityGlowMixin` never outlines him (senses, highlights, the Glowing effect).
> - **"Invisible" right arm.** It was never missing: harness shots with the arms painted green/blue show both
>   in every pose. The source skin paints each arm's banded plate only on its outer face; the front face was
>   ~95% 0x0d black, exactly like his chest, so from the front the sword arm vanished and the blade looked
>   like it floated. `wrapArmPlates` in the build script now wraps the outer plate pattern around the front
>   and back faces of both arms (all three phase textures).

A 4-block-tall fallen death knight, summoned on demand by using a **Knight's Soul** on a **Respawn
Anchor**. v0.14.0 rebuilt him from "a knight that does two attacks" into a three-phase, Elden-Ring-style
duel boss: every attack has a readable wind-up, a dodge window and a punishable recovery, a hidden poise
meter can break him, and he gets more desperate as his health drops.

Every tunable number (HP, damage, ranges, arcs, cooldowns, tick lengths, chances, weights) lives in
**`com.projecthero.mod.oathbreaker.OathbreakerTuning`**. Balance there; nothing else hard-codes a number.

## Files

| What | Where |
| --- | --- |
| Tunables | `oathbreaker/OathbreakerTuning.java` |
| Entity: lifecycle, phases + sync, poise/stagger, transitions, death, GeckoLib controllers, riding overrides | `oathbreaker/entity/OathbreakerEntity.java` |
| Every attack (state machine, parry, scripted movement, hazards, anti-cheese) | `oathbreaker/entity/OathbreakerCombat.java` |
| Threat table / target-switching rule (v0.13.19) | `oathbreaker/entity/OathbreakerThreat.java` |
| Shared shake/zoom/particle-shape cues | `oathbreaker/OathbreakerFx.java` |
| Summon delay + multiplayer HP scaling | `oathbreaker/OathbreakerSummon.java` |
| Model (texture per phase) / renderer (glow layer, sword item, death fade) | `client/oathbreaker/OathbreakerModel.java`, `OathbreakerRenderer.java` |
| **All generated assets** (geo, animations, 3 textures + 3 glowmasks, Broken Oath icon) | `scratchpad/build_oathbreaker_assets.js` (single source of truth -- never hand-edit its outputs) |
| Source skin (vanilla 64x64 player layout, base + second layer) | `scratchpad/oathbreaker/deathknight_source.png` (copied out of a session temp dir in v0.14.0 so it can't vanish) |
| Unblockable damage type | `data/projecthero/damage_type/oathbreaker_execution.json` + `data/minecraft/tags/damage_type/bypasses_shield.json` |
| Loot | `data/projecthero/loot_table/entities/oathbreaker.json` |
| Guidebook chapter | `hero/guide/HeroPackGuide.java` (`CH_OATHBREAKER`), lang `projecthero.guide.oathbreaker.*` |

## Summoning

Unchanged from v0.13.7: `KnightsSoulItem` -> `OathbreakerSummon.begin` -> 5 s of escalating rumble/zoom ->
`spawnNow` -> `OathbreakerEntity.spawnIn` (a 1.25 s crouch-to-stand, invulnerable, no AI).

**Multiplayer HP scaling** (new): in `spawnNow`, before the entity is added, every player within 48 blocks of
the spawn point is counted; each extra player beyond the first adds 40% max health, capped at 3 extra (4
players). Written once into the `MAX_HEALTH` attribute's base value and never touched again.

## Stats

| | |
| --- | --- |
| Max health | 4,000 (+40% per extra nearby player at spawn, max 4 players) |
| Armor / toughness | 10 / 4 |
| Knockback resistance | 0.8; **1.0 during any wind-up or active strike** ("hyper armor", `setHyperArmor`). Dropped back to 0.8 during every punish window (post-dash stance, chain miss, grab whiff) |
| Movement speed | 0.19 / 0.22 / 0.26 by phase (v0.13.9; was 0.15 / 0.18 / 0.22) |
| Follow range / boss-bar radius | 50 / 50 (v0.13.19; was 48 / 48) -- acquired without line of sight |
| XP | 500 (actually dropped now -- see "Death") |

`TitanCombat.isBoss()` still trips (it's a `getMaxHealth() >= config threshold` check; 4,000 clears it --
verified in the harness).

## Poise and stagger

Hidden meter, 400. `hurt()` (not `actuallyHurt`, which only sees armor-reduced damage) drains it one-for-one
with the **raw** incoming amount, mirroring vanilla's i-frame rule (a hit inside the window only counts by
how much it beats the last one). Hyper armor does **not** protect poise. 80 ticks with no damage -> poise
snaps back to full. At 0: the current attack is cancelled, he kneels (`stagger`, 2.5 s, no AI), takes +30%
damage, then gets up with full poise. Never during spawn, a phase transition, or death. The flinch clip only
plays when he's idle.

## Phases

`Phase { KNIGHT, FORSWORN, OATHLESS }`, synced in one `EntityDataAccessor<Byte>`; the model picks the
texture from it. Never regresses.

- **Oath Shattered** (at 60%): 3 s scripted, invulnerable, no AI, no stagger. `phase_transition` clip: reels,
  kneels, raises the sword in a reverse grip, **drives it into the ground at exactly 1.5 s** -- on that tick the
  phase flips (texture swap), a soul-fire ring expands to 8 blocks over 8 ticks hitting each player once as
  it passes (8 damage, big outward shove), plus a strong shake + zoom to everyone within 32 and respawn-anchor-
  deplete / soul-escape sounds. Rips the sword out and stands. Boss bar becomes "The Oathbreaker — Forsworn".
- **Enrage** (at 25%): 1.5 s roaring stance. Per spec **not** invulnerable (verified: hits land), but no AI and
  no stagger. Phase 3 texture swaps in at the roar's peak (0.5 s) with a big shake. From then on he uses a
  `run` loop instead of `walk` when chasing. Boss bar "The Oathbreaker — Oathless" (the spec only named phase
  2's bar; phase 3's follows the same pattern).
- A single huge hit that skips past 25% still plays Oath Shattered first; the enrage follows next tick.

## Attacks (`OathbreakerCombat`)

Every attack is a list of timed steps; every step length is a tuning constant that the build script checks
against its clip length, and every damaging strike resolves on the clip's **contact keyframe** (also checked
by the build script), never on the clip's first frame. Wind-ups track the target; committed strikes and
flights don't -- that's the dodge. Movement during attacks (dash lunge, backstep, leaps, Judgement, the
Execution lunge) is a **scripted move**: a start and end locked when it begins, driven by moving the entity
along the path each tick (collisions still apply), gravity off.

Damage cones (`arcTargets`) are measured on the **horizontal plane from his feet**, with a vertical band from
just below his feet to just above his head; anyone inside his footprint counts as hit.

| Attack | Phases | Beats | Damage | Counterplay |
| --- | --- | --- | --- | --- |
| Stance Dash | all | 1.5 s wind-up (glint) -> 4-block lunge, contact 0.10 s -> 2 s committed stance | 30, 5-block 70° cone | dodge the lunge; punish the stance |
| Dash feint | 2+ (25%) | weight shifts as if to go, settles back; the real dash fires 0.5 s later | -- | don't roll on the first twitch |
| Combo | all | 4 hits (5 from phase 2), each wind-up -> strike (contact 0.10 s), wind-ups chain from the previous hit's follow-through | 10 each; the 5th is a thrust (4.5 range, 40°) | -- |
| Delayed combo | 2+ | before each strike, a random 0-0.6 s extra hold (looping `combo_hold_N`) | | can't rhythm-roll it |
| Oath Guard | all (only if the target hit him in the last 3 s) | 1.5 s guard; a **melee** hit from within 6 blocks inside his front 100° is negated (anvil ping) -> instant **Riposte** | riposte 18 + strong knockback | hit from behind, or at range |
| Backstep | all | after any attack, target within 2.5 blocks, 30%: hops 3 blocks back | -- | spacing |
| Leaping Cleave | all | target 8-20 blocks away for 3 s: 0.6 s crouch -> 0.8 s parabola to where you **were** at takeoff (landing ring on the ground the whole flight) -> 0.8 s chop + settle | 20 at centre -> 10 at 4 blocks | move out of the ring |
| Soul Rend | 2+ | 0.8 s drag; the line **locks** for the last 0.5 s and soul particles flicker along all 12 blocks -> rising slash, eruptions walk out 1 block / 2 ticks | 12 per eruption (1.2 radius), 3 s fire | step off the line |
| Chains of the Forsworn | 2+ (range 6-16, 35%/s roll) | 0.5 s off-hand wind-up -> a visible chain flies at where you were, 1.6 blocks/tick, 18 reach -> caught: dragged in over 0.5 s, then combo strike 1; missed: 1 s open recovery | strike 10 | sidestep the throw |
| Judgement | 3 (own 20 s cooldown, 50% when up) | rises 8 blocks; hangs 1 s tracking you with a soul beam + ground ring; landing **locks**; 5-tick slam | 35 at centre -> 10 at 6 blocks; then a 6-block soul-fire circle, 4/s for 5 s | get out of the ring before the lock, then out of the circle |
| Execution | 3 (own 25 s cooldown, 50% when up) | **red** flash + warden charge, 0.8 s wind-up, short lunge; a player in the 2.5-block 60° cone is grabbed and held up in front of him for 1.5 s, then impaled and thrown | 40, **unblockable** (bypasses shields; armor still counts) | leave the cone; allies deal 150 during the hold to free you (he staggers) |
| Oathbound Whirlwind (v0.13.19) | all (own 8 s cooldown; favoured when someone is behind him or 2+ crowd him) | 0.7 s low draw -> two spins in 0.8 s, contacts 0.20 / 0.60 s -> 1.5 s dizzy | 11 per spin, everyone within 5 blocks all round | back off or jump; punish the dizzy |
| Grave Geysers (v0.13.19) | 2+ (own 12 s cooldown; at 6-30 blocks with the Chains roll, and in the melee pools) | 0.8 s reverse-grip raise -> plunge (contact 0.15 s): a ring under every player within 30 tracks 0.5 s, locks, erupts 0.75 s later -> 2.2 s bowed | 15, launch up, 2 s fire | step off your ring after it locks; punish the bow |
| Phantom Echo | 3 (passive) | every Combo/Stance Dash contact is repeated by a soul-fire ghost from where he stood, 1 s later | 60% of the original | don't dodge once and walk straight back in |

**Weights.** Phase 1 melee pool: Stance Dash 35 / Combo 45 / Oath Guard 20. Phase 2: 25 / 35 / Soul Rend 20 /
Oath Guard 10. Phase 3: 20 / 30 / 20 / 5, with Judgement and Execution offered first whenever off their own
cooldowns (50% each). v0.13.19 adds Whirlwind 12 / 10 / 10 (50 when surrounded) and Grave Geysers - / 8 / 10
(doubled with 2+ players within 30), each only while off its own cooldown. Shared cooldown 1.2 s / 0.9 s / 0.6 s (v0.13.9; was 2.5 / 2 / 1.5). The damage column
below is v0.14.0's -- v0.13.9 raised every number ~40% and v0.13.10 then halved them; see the top of this
file and `OathbreakerTuning` for the live values.

**Chains decision (differs from the literal spec, on purpose).** The spec lists Chains at weight 10 "only when
in range", but its range (6-16) never overlaps the 5-block trigger every melee attack uses, so a weighted pick
would make it the *only* candidate out there and he'd throw it after every single cooldown. Instead, while
the target sits in 6-16 blocks and he's off cooldown, he rolls `CHAIN_RANGED_CHANCE` (35%) once a second. In
phase 3 the same roll can also offer Judgement.

**Execution grab mechanics.** The victim rides him (`startRiding(boss, true)`); `positionRider` holds them
1.4 blocks in front at 42% of his height, and a Monster's travel ignores rider input, so they can't move.
Release paths: impale, the escape rule, his stagger/death/any cancel (`releaseVictim`), and a per-tick
validity check (dead, removed, other dimension, creative/spectator, disconnected -> hold ends). A victim who
sneak-dismounts is put straight back. `hasExactlyOnePlayerPassenger()` is overridden to `false`: otherwise
vanilla would treat him like a horse and, on logout or world save, write him **into the player's save file**
and remove him from the world. The Fabric disconnect hook also releases a logging-out victim first.

## Anti-cheese

- **Unreachable for 3 s** (pillared > 3 blocks above him, in water/lava, or no complete path -- pathfinding
  re-checked every 10 ticks): phase 1 throws a **Soul Spear** (off-hand, chain-throw wind-up, then a fast
  visible bolt, 2.5 blocks/tick, 15 damage, dodgeable); phase 2+ throws the **Chains**, which drag them down.
- **Stuck for 5 s** (not attacking, target out of reach, barely moved or inside a block): a short Leaping
  Cleave (max 6 blocks) toward the target.
- Creative/spectator players are never valid targets or strike victims; a target that switches mid-fight is
  dropped.

## Death

`die()` now calls `super.die()` -- **the old override skipped it**, so the boss never set vanilla's dead flag,
never credited the kill (advancements/kill score) and **never dropped its XP**; it hand-rolled only the loot.
Vanilla's death path rolls the entity type's default loot table, which is the same
`projecthero:entities/oathbreaker`. `tickDeath` is replaced: 3 s instead of vanilla's 20-tick poof; the
`death` clip drops him to both knees with the sword planted and his head bowed, soul particles stream off him
thicker every tick from 1.2 s, the renderer fades him out (translucent render type + alpha, no vanilla
tip-over rotation, the sword item disappears halfway through since the item renderer can't fade), and
everyone within 48 blocks gets "The oath... is fulfilled." on their action bar.

Loot: netherite sword, 25-40 Grave Essence, a guaranteed Abyssal Core, and the new **Broken Oath** (crafting
material, no recipe/use yet; icon generated by the build script).

## Model and assets

- **Second skin layer is back.** v0.13.7's attempt built the layer as separate *child bones* and corrupted
  the model. v0.14.0 does what the source Blockbench file itself does: each layer cube lives **inside the same
  bone as its base cube** (inflate 0.5 hat, 0.25 elsewhere). No new bones. Screenshotted from all sides: reads
  as a crested helm, pauldrons and plate, no corruption.
- **The sword is the real vanilla netherite sword item**, drawn at the (now empty) `sword` bone by a
  `BlockAndItemGeoLayer`. GeckoLib's stock layer applies the bone's rotation a *second* time on top of the
  entity renderer's own bone transform, so the renderer overrides `renderForBone` to only move to the pivot
  and orient the item itself (grip on the wrist, blade down the arm, edge leading).
- **No new bones anywhere.** Kneels/crouches (stagger, transitions, death, landings) are faked by the
  build script's `pose()` solver: give it a torso lean and the *world* angle each leg/arm/sword should end at,
  and it returns local rotations plus the `root` offset that keeps a foot on the ground (the legs hang off
  `body`, whose pivot is at the shoulders, so leaning the torso swings the hips).
- **Glow (removed in v0.13.9):** `AutoGlowingGeoLayer` + a `_glowmask` twin per texture. Phase 1: the four eye pixels (they sit
  behind eye slits cut into the helm layer's visor). Phases 2/3: plus the soul-fire crack cores. Phase 3's
  cracks are phase 2's, widened, with a few extra forks (same seed).
- **Build-script checks.** Every run prints every clip's length in ticks and fails on any mismatch with its
  `OathbreakerTuning` constant, and checks every strike clip has a sword-arm keyframe on its contact tick.

### Bugs found and fixed along the way (all harness-verified)

- **Walk/idle overrode every attack clip on the client.** The client never saw the server's attack state,
  so the looping controller kept playing underneath triggered clips and won on shared bones -- the v0.13.7
  wind-up screenshot shows the arm at the hip instead of overhead. A synced "busy" flag now stops it. Almost
  certainly the real reason v0.13.7's attacks "didn't really work".
- The melee-chase goal kept re-pathing mid-attack, so he crept forward during wind-ups. Now it stands down.
- The glow layer rendered at 4x (floating crack shapes above his head): `preRender` scaled again on GeckoLib's
  layer re-render pass. Now scales on the main pass only.
- 3D damage cones from mid-height: a player hugging his legs couldn't be hit, and the Execution whiffed on a
  player right in front of him. Now horizontal.
- A held victim changing dimension crashed the server (an ended attack was advanced). Fixed.

## Verification (TEMPORARY client harness, deleted before release as always)

The harness (`scratchpad/OathbreakerDebugHarness.java.txt`, run with `PROJECTHERO_OATHBREAKER_DEBUG=1`,
`Difficulty.NORMAL`, mob spawning off -- superflat slimes photobomb otherwise) was run after every stage.

- **Screenshot-verified:** spawn; idle/walk; stagger kneel; the busy-flag fix; Stance Dash, all combo
  hits, guard, riposte, backstep, leap (with its ground ring); Oath Shattered sequence and the texture swap;
  phase 2/3 textures by day and at night (glowing cracks, no floating layer); eyes glowing through the visor
  at night; Soul Rend telegraph + line; chain flight, pull and miss; the delayed-combo hold and the thrust;
  the feint; enrage; run; Judgement rise/beam/impact/ring; Execution red flash, hold and impale; Phantom Echo;
  Soul Spear knocking a pillared player off; the second skin layer from all sides; the netherite sword in
  every pose; the death kneel and fade; the Broken Oath icon; the guidebook index.
- **Verified by harness numbers:** HP/armor/toughness/knockback/speed per phase; `isBoss`; poise maths,
  regen, stagger through hyper armor, +30%; hyper armor 1.0/0.8; parry vs rear hit vs arrow; backstep distance;
  leap trigger and landing accuracy; Soul Rend on-line vs sidestep; chain catch/pull/miss; the 5-hit combo's
  random holds; a feint in phase-2 dashes; transitions (invulnerability, phase flip timing, shockwave shove);
  enrage hittable and stagger-proof; Judgement height/centre damage/ring ticks; Execution hold height, impale
  damage through Resistance III, escape at 160 (not 80), victim's own damage not counting, sneak re-grab,
  release on dimension change / victim death / boss death; Phantom Echo timing and 60%; face-hug combo hit;
  Soul Spear and Chains on pillaring players; creative switch; unsticking from a 3-deep pit (phase 1: the
  short unstick leap; phase 2: a kite leap straight out -- the stuck timer keeps counting through ranged
  attacks, and the move control is parked at attack start, which is what made an earlier pit leap fail);
  the death message; loot and XP; the two Thor fixes (R swap, full-inventory recall).
- **Code-read only:** multiplayer HP scaling with real extra players (single player can't simulate them);
  release on the victim **logging out** (the disconnect hook + the `hasExactlyOnePlayerPassenger` override);
  the Execution bypassing a raised shield (data tag).
- **Not verified:** the camera shake/zoom cues themselves (they're existing payloads; a static screenshot
  can't show them), and real multiplayer overall.

## Known simplifications

- The Phantom Echo is particles only (no ghost entity/model), as the spec allowed.
- Chains and the Soul Spear are server-side point-and-line math with particles, not projectile entities.
- The phase is saved (`OathbreakerPhase` NBT byte, restored with its movement speed on load), so a reloaded
  phase-2/3 boss doesn't replay its transition. Attack/stagger/transition timers aren't saved; instead the
  vanilla flags they drive (NoAI, Invulnerable, NoGravity, the hyper-armor knockback base) are all reset on
  load -- without that, a world saved mid-stagger or mid-transition would reload a permanently frozen,
  invulnerable boss. A reload mid-attack simply resumes at idle; one saved before Oath Shattered's plunge
  replays the transition. (Code-read only -- the harness never saved and reloaded a world.)
- The glowing eyes/cracks don't fade with the body during death (the glow layer has its own render type);
  they vanish when he's removed.
- The whole model is mirrored relative to the skin (the `right_arm` bone renders on his left) -- inherited
  from the original model and harmless, but camera angles in the harness use his *left* side as the sword side.
