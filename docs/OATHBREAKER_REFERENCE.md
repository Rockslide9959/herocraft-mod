# The Oathbreaker — reference (v0.13.6)

A 4-block-tall knight boss, summoned on demand (not a natural/random spawn) by right-clicking a
**Knight's Soul** on a Lodestone. Package `com.projecthero.mod.oathbreaker.entity`; the summoning item
(`KnightsSoulItem`) lives in `com.projecthero.mod.grave.item` since it is crafted from Gravebound
materials. Client half in `com.projecthero.mod.client.oathbreaker`.

## Summoning

**Knight's Soul** (`GraveItems.KNIGHTS_SOUL`) is crafted from an Abyssal Core (dropped by The Abyssal
Behemoth) surrounded by 8 Grave Essence — a 3x3 shaped recipe, `data/projecthero/recipe/knights_soul.json`.
Right-clicking it on a Lodestone (`KnightsSoulItem.useOn`) consumes it and spawns the Oathbreaker on an
open patch of ground beside the stone. Two server-side guards, the same shape as
`GraveRitualTotemItem`/`BehemothSpawner`:

- **Dedup**: refuses if another live `OathbreakerEntity` is within 48 blocks (`activeNearby`).
- **Room**: `findSpot` scans outward from the lodestone (then straight up as a last resort) for a spot
  with 4 blocks of clear air over solid ground; refuses with a message if nothing qualifies.

## Model

The geometry and texture started from a Blockbench file the project owner supplied
(`3d minecraft models/knight/deathknight.bbmodel`) — a 64x64 skin using vanilla's own player-model box-UV
layout (head/body/arms/legs at the exact vanilla coordinates) painted as a dark, red-eyed knight, but with
**unnamed** bone groups (no `name`/`pivot`/`rotation` on any group node), which GeckoLib's geo format
cannot use directly. `scratchpad/build_oathbreaker_assets.js` rebuilds the usable assets from it:

- Decodes the embedded base64 texture (`pnglib.js`, the same hand-rolled PNG decode/encode this project
  uses everywhere — no Python in this dev environment), extends the canvas from 64x64 to 64x80, and paints
  three flat sword swatches (blade/guard/grip) into the new strip.
- Builds `geo/oathbreaker.geo.json` by hand: six named bones (`head`/`body`/`right_arm`/`left_arm`/
  `right_leg`/`left_leg`) at the model's own cuboid coordinates, with explicit per-face `{uv, uv_size}`
  computed from Minecraft's standard box-UV packing formula (the exported geo format wants faces spelled
  out, not an implicit box-UV origin — confirmed against how `abyssal_behemoth.geo.json` is shaped) so the
  supplied texture drops straight on with no distortion. A seventh bone, `sword`, is a child of
  `right_arm` (three cuboids: grip, guard, and — "make the sword longer" — a blade running from the hand
  down past the knees), textured from the new swatch strip.
- Builds `animations/oathbreaker.animation.json` (15 clips) the same way: idle/walk/hit/death, plus the
  two named attacks' wind-up and strike beats (see below).

The model is authored at vanilla-player proportions (`OathbreakerEntity.MODEL_HEIGHT = 2.0f`); the
renderer (`OathbreakerRenderer.preRender`) scales it up to the entity's real 4-block bounding box
(`OathbreakerEntity.HEIGHT`) — the same `getBbHeight() / MODEL_HEIGHT` trick `TitanFormRenderer` and
`BehemothRenderer` already use.

## Movement and AI

Unlike the Titan/Behemoth family, the Oathbreaker uses **ordinary vanilla goals** for movement — it is
grounded and single-target, so there is no need for the hand-rolled navigation those flying/giant bosses
need: `FloatGoal`, `MeleeAttackGoal` (chase-into-range only), `WaterAvoidingRandomStrollGoal`,
`HurtByTargetGoal`, `NearestAttackableTargetGoal<Player>`. `doHurtTarget` always returns `false` so
`MeleeAttackGoal`'s own automatic attack call is harmless — every point of real damage comes from the
timed state machine in `tickCombat`, called from `aiStep`, the same "goals only move it, a hand-timed
state machine attacks" split `AbyssalBehemothEntity`/`TitanEntity` use.

Stats: 500 HP, movement speed 0.11 (10% over vanilla's 0.1 player walk speed, per spec), knockback
resistance 0.6. `TitanCombat.isBoss()` (a `getMaxHealth()` threshold check) picks it up automatically at
500 HP, so every other power's "don't one-shot a boss" damage cap already applies to it for free.

## Combat state machine

Two named attacks, an `Attack` enum (`STANCE_DASH` / `COMBO`) plus a shared cooldown
(`ATTACK_COOLDOWN_TICKS`, 2.5s) so they never overlap — `pickAttack()` rolls 40% Stance Dash / 60% Combo
once in range (`ATTACK_TRIGGER_RANGE`, 5 blocks) and off cooldown.

**Stance Dash** (30 damage) — `StancePhase.WINDUP -> DASH -> POST`:
1. 2s wind-up (`windup_dash`): sword drawn back overhead, stance held.
2. `dashSlice`: a short forward lunge (cosmetic velocity only) plus one instant forward-cone damage sweep
   (`arcTargets`, 5 blocks / 70°) — resolved in one shot, not a physically-simulated collision, the same
   pattern `TitanEntity.doPunch`/`doSweep` etc. already use.
3. 2s held stance (`post_dash`) before easing back to idle/walk, per spec ("stays in that stance for
   another 2 seconds").

**Four-Strike Combo** (10 damage per hit) — four hits, each its own short wind-up (`combo_windup_N`,
0.4s) then a strike (`combo_strike_N`, resolved via `arcTargets`, 3.5 blocks / 80°) from a different
direction: upper-right slash, upper-left slash, a horizontal sweep, an overhead slam — both the pose data
(`comboPoses` in the build script) and the actual attack direction vary per hit, "like an Elden Ring
boss." `progressCombo` alternates a wind-up sub-phase and a strike-then-hold sub-phase via one
`strikePhasePending` flag rather than a second enum, since there are only two sub-states to track.

Both attacks lock `getNavigation()` and hold the look control on the target for the whole sequence, so a
windup can be genuinely dodged by moving out of the eventual strike's arc.

## Damage taken

No armor/resistance overrides — a plain `actuallyHurt` override only refreshes the boss bar immediately
and occasionally triggers the `hit` flinch animation, the same shape `AbyssalBehemothEntity` uses.

## Boss bar, loot, death

`EventBossBar` (red, no darken-screen), 48-block radius, updates every tick and immediately after any hit
lands. `die()` stops AI, plays `death`, clears the boss bar, and rolls
`data/projecthero/loot_table/entities/oathbreaker.json` (a netherite sword, 15-25 Grave Essence, a chance
at another Abyssal Core) before a 1s collapse (`deathTicks`) removes the entity — shorter than the
Behemoth's 3s cinematic since this is a grounded humanoid, not a giant collapsing creature. `xpReward =
250`.

## Multiplayer

Server-authoritative throughout: the state machine, targeting and movement all run only in
`aiStep`/goal ticks on the logical server. Animation reaches every client through GeckoLib's networked
`triggerAnim` (the whole attack sequence, one-shot per beat) and a synced `getLimbSwingAmount()` read in
`mainPredicate` for the looping idle/walk state — the same two-controller convention every other GeoEntity
boss in this mod uses.

## Known simplifications

- The attack-arc damage sweep is a single instant AABB/cone check timed to the strike beat, not a
  frame-by-frame hitbox on the actual swinging sword geometry — consistent with how every other boss in
  this mod resolves its telegraphed attacks.
- Attack-state fields (`activeAttack`, `comboHitIndex`, ...) are not persisted to NBT; a save/reload
  mid-attack just resets to idle, which only ever costs the player a few seconds of a wasted windup.
- Not playtested in a running client — visuals (the rebuilt model/animations) are unverified in-game, same
  caveat as other recent additions built without a `runClient` session available here.
