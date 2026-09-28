# The Oathbreaker — reference (v0.13.7)

A 4-block-tall knight boss, summoned on demand (not a natural/random spawn) by right-clicking a
**Knight's Soul** on a **Respawn Anchor**. Package `com.projecthero.mod.oathbreaker` (+ `.entity`); the
summoning item (`KnightsSoulItem`) lives in `com.projecthero.mod.grave.item` since it is crafted from
Gravebound materials. Client half in `com.projecthero.mod.client.oathbreaker`.

## Summoning

**Knight's Soul** (`GraveItems.KNIGHTS_SOUL`) is crafted from an Abyssal Core (dropped by The Abyssal
Behemoth) surrounded by 8 Grave Essence — a 3x3 shaped recipe, `data/projecthero/recipe/knights_soul.json`.

Using it (`KnightsSoulItem.useOn`) on a Respawn Anchor (changed from a Lodestone in v0.13.7 — a better
thematic fit for a death-knight-flavoured boss) is a two-step hand-off, not an instant spawn:

1. **Item side** (`KnightsSoulItem`): the usual dedup/room guards (refuses if another live
   `OathbreakerEntity` is within 48 blocks, or if `OathbreakerSummon.findSpot` can't find 4 blocks of
   clear air over solid ground near the anchor), then the item is consumed immediately and
   `OathbreakerSummon.begin(level, anchorPos)` takes over. Same shape as `GraveRitualTotemItem`/
   `BehemothSpawner`.
2. **`OathbreakerSummon`** (v0.13.7, new): a transient in-memory queue (ticked from the same
   `ServerTickEvents.END_SERVER_TICK` hook as `BehemothSpawner`, cleared by `ServerStateReset` like every
   other static cache this mod keeps) that waits **5 real seconds** before the boss actually appears —
   "make world rumble a bit and make the players screen zoom in slightly and after 5 seconds the
   oathbreaker spawns in in a crouched animation." During the wait it sends an escalating rumble
   (`TitanShakePayload`, reused from Titan Shifter — despite the name it's already a generic camera-shake
   packet) and ambient Soul particles to everyone within 32 blocks, plus a one-shot camera zoom-in cue
   (`WorldEventZoomPayload`, new, same shape as the shake payload) at the start and again at the moment of
   the actual spawn. `findSpot` is recomputed at spawn time in case the ground changed during the wait; if
   it no longer qualifies, the summon just fizzles (the Knight's Soul is already spent, same as any other
   spell that whiffs).

## Camera cues (`WorldEventZoomPayload` / `WorldEventZoomClient` / `WorldEventZoomMixin`)

A small new generic system, deliberately named for reuse beyond just this boss (matching how
`TitanShakePayload` outgrew its own name): the server sends `(amount, ticks)`, the client eases a
multiplier into `GameRenderer#getFov` (a second `@Inject` at the same point `GunFovMixin` already uses,
composed multiplicatively) that decays linearly back to 1.0, exactly mirroring `TitanShakeClient`'s decay
shape for camera shake.

## Model

The geometry and texture started from a Blockbench file the project owner supplied
(`3d minecraft models/knight/deathknight.bbmodel`) — a 64x64 skin using vanilla's own player-model box-UV
layout (head/body/arms/legs at the exact vanilla coordinates) painted as a dark, red-eyed knight, but with
**unnamed** bone groups (no `name`/`pivot`/`rotation` on any group node), which GeckoLib's geo format
cannot use directly. `scratchpad/build_oathbreaker_assets.js` rebuilds the usable assets from it, and is
the single source of truth for all three generated files below — never hand-edit the JSON/PNG outputs.

- Decodes the embedded base64 texture (`pnglib.js`, the same hand-rolled PNG decode/encode this project
  uses everywhere — no Python in this dev environment), extends the canvas from 64x64 to 64x80, and paints
  three flat sword swatches (blade/guard/grip) into the new strip. v0.13.7: the blade swatch recoloured
  from a plain steel silver to netherite's own dark, slightly-purple grey ("make the sword a netherite
  sword").
- Builds `geo/oathbreaker.geo.json` by hand: six named bones (`head`/`body`/`right_arm`/`left_arm`/
  `right_leg`/`left_leg`) at the model's own cuboid coordinates, with explicit per-face `{uv, uv_size}`
  computed from Minecraft's standard box-UV packing formula (the exported geo format wants faces spelled
  out, not an implicit box-UV origin — confirmed against how `abyssal_behemoth.geo.json` is shaped) so the
  supplied texture drops straight on with no distortion. A seventh bone, `sword`, is a child of
  `right_arm` (three cuboids: grip, guard, blade), textured from the new swatch strip.
- Builds `animations/oathbreaker.animation.json` (16 clips): idle/walk/hit/death/**spawn** (new), plus the
  two named attacks' wind-up and strike beats (see below).

The model is authored at vanilla-player proportions (`OathbreakerEntity.MODEL_HEIGHT = 2.0f`); the
renderer (`OathbreakerRenderer.preRender`) scales it up to the entity's real 4-block bounding box
(`OathbreakerEntity.HEIGHT`) — the same `getBbHeight() / MODEL_HEIGHT` trick `TitanFormRenderer` and
`BehemothRenderer` already use.

### The second-layer attempt, and why it isn't here

v0.13.7 tried restoring the vanilla-skin "layer" overlay (hat/jacket/sleeves/trousers) as a set of thin
inflated child bones, one per base bone, reusing the source skin's own layer UV offsets — the user had
flagged the base-only v0.13.6 model as missing it. Verified with the client debug harness (below):
**adding the layer bones corrupted the whole model into an unreadable vertical blob**, badly enough that
head/body/arms/legs stopped reading as separate parts at all — screenshotted before and after. Deleting
just the layer bones (keeping everything else identical) restored a correctly-proportioned humanoid, so
the layer shell was dropped again rather than shipping something worse than no second layer. This reads
like a real GeckoLib child-bone quirk (the geo data itself, checked by hand, looked correct — every
cube's origin/size/pivot matched the intended absolute position), but the root cause wasn't tracked down
further; revisit with fresh eyes before trying again, and verify with the harness before assuming a fix
works.

### Sword: the "dragging on the ground" bug

The very first version's blade ran from the hand (absolute model y=12, since vanilla-proportioned arms
only reach hip height) down 22 units — but the ground is at y=0, so roughly 10 units of blade (a good
1.25 blocks at the render-time 2x scale) rendered *through the floor* at rest. v0.13.7 shortened the blade
to 7 units, which happens to land the tip exactly at y=0 hanging straight down (a deliberate "point
planted on the ground" rest, not a clipping bug) — total visible length below the hand (guard + blade) is
about 9 units, proportionate on a 4-block frame without ever going negative. Confirmed clear of the
ground via the harness screenshots below.

## Movement and AI

Unlike the Titan/Behemoth family, the Oathbreaker uses **ordinary vanilla goals** for movement — it is
grounded and single-target, so there is no need for the hand-rolled navigation those flying/giant bosses
need: `FloatGoal`, `MeleeAttackGoal` (chase-into-range only), `WaterAvoidingRandomStrollGoal`,
`HurtByTargetGoal`, `NearestAttackableTargetGoal<Player>`. `doHurtTarget` always returns `false` so
`MeleeAttackGoal`'s own automatic attack call is harmless — every point of real damage comes from the
timed state machine in `tickCombat`, called from `aiStep`, the same "goals only move it, a hand-timed
state machine attacks" split `AbyssalBehemothEntity`/`TitanEntity` use.

Stats (v0.13.7): **3,000 HP** (up from 500), movement speed 0.15 (**50%** over vanilla's 0.1 player walk
speed, up from 10%), knockback resistance 0.6. `TitanCombat.isBoss()` (a `getMaxHealth()` threshold check)
picks it up automatically, so every other power's "don't one-shot a boss" damage cap already applies to
it for free.

## Spawn sequence (`spawnIn`, v0.13.7)

Called by `OathbreakerSummon` the instant the entity is added to the world: sets `spawnTicksLeft =
SPAWN_TICKS` (25 ticks, matched exactly to the `spawn` animation clip's own 1.25s length so AI waking up
lines up with the moment he's actually finished standing), `setNoAi(true)` and `setInvulnerable(true)` for
the duration, and triggers the `spawn` action animation (a held crouch that rises to standing over the
clip). `aiStep` branches to `tickSpawn` while any ticks remain — ambient Soul Fire particles every 4
ticks, then on the last tick: AI/invulnerability lift, an impact sound + particle burst, and the boss bar
opens for the first time. Before this the boss bar does not exist yet, so nothing shows on-screen during
the crouch.

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

**Four-Strike Combo** (10 damage per hit) — four hits, each its own wind-up (`combo_windup_N`) then a
strike (`combo_strike_N`, resolved via `arcTargets`, 3.5 blocks / 80°) from a different direction:
upper-right slash, upper-left slash, a horizontal sweep, an overhead slam — both the pose data
(`comboPoses` in the build script) and the actual attack direction vary per hit, "like an Elden Ring
boss." `progressCombo` alternates a wind-up sub-phase and a strike-then-hold sub-phase via one
`strikePhasePending` flag rather than a second enum, since there are only two sub-states to track.

**v0.13.7 timing/animation fix**: the user reported the attack animations as not really working. Two real
causes, both fixed:
- The `AnimationController` transition time was 4 ticks, blending between two triggered clips — for the
  original 0.2s combo strikes that ate a third or more of the entire beat, smearing one pose into the
  next before it ever fully showed. Cut to 1 tick (and the "main" idle/walk controller's own transition
  2, down from 6).
- The beats themselves were too short to read even without blending: combo wind-up lengthened 0.4s -> 0.5s
  and the strike hold 0.2s -> 0.35s (`COMBO_WINDUP_TICKS`/`COMBO_STRIKE_HOLD_TICKS`, matched exactly by
  the animation clip lengths in the build script).

Both attacks lock `getNavigation()` and hold the look control on the target for the whole sequence, so a
windup can be genuinely dodged by moving out of the eventual strike's arc.

## Damage taken

No armor/resistance overrides — a plain `actuallyHurt` override only refreshes the boss bar immediately
and occasionally triggers the `hit` flinch animation, the same shape `AbyssalBehemothEntity` uses.

## Boss bar, loot, death

`EventBossBar` (red, no darken-screen), 48-block radius, updates every tick and immediately after any hit
lands (once it exists — see the spawn sequence above). `die()` stops AI, plays `death`, clears the boss
bar, and rolls `data/projecthero/loot_table/entities/oathbreaker.json` (a netherite sword, 15-25 Grave
Essence, a chance at another Abyssal Core) before a 1s collapse (`deathTicks`) removes the entity —
shorter than the Behemoth's 3s cinematic since this is a grounded humanoid, not a giant collapsing
creature. `xpReward = 250`.

## Multiplayer

Server-authoritative throughout: the state machine, targeting and movement all run only in
`aiStep`/goal ticks on the logical server. Animation reaches every client through GeckoLib's networked
`triggerAnim` (the whole attack sequence, one-shot per beat) and a synced `getLimbSwingAmount()` read in
`mainPredicate` for the looping idle/walk state — the same two-controller convention every other GeoEntity
boss in this mod uses. The rumble/zoom cues and the spawn sequence are likewise plain server -> client
cosmetic payloads, no different from Titan Shifter's own shake packet.

## Verifying visuals without a human (v0.13.7)

Actually playtested this time, via the same TEMPORARY client debug harness
`docs/TITANSHIFTER_REFERENCE.md`'s own notes and `project-titan-shifter` memory describe (env var
`PROJECTHERO_OATHBREAKER_DEBUG=1 ./gradlew runClient --offline`, background, screenshots read back as
images). Source saved at `scratchpad/OathbreakerDebugHarness.java.txt` for reuse; copy into
`src/client/.../client/oathbreaker/`, add the guarded `init()` call, and delete both before committing,
same discipline as every other harness use.

**One gotcha specific to a `Monster`-tagged boss** (the Titan/All Might harnesses never hit this, since
neither of those entities extends `Monster`): the harness world must be `Difficulty.NORMAL`, not
`PEACEFUL`. On Peaceful, vanilla silently removes every `Monster`-category entity a few ticks after it
spawns, with no exception or log line — the first run of this harness used Peaceful (copied from
`TitanDebugHarness`) and both the Oathbreaker and a test Abyssal Behemoth vanished mid-script, which cost
a full extra debug cycle to diagnose before the fix was obvious.

Confirmed via the harness: the spawn sequence (crouch -> particle burst -> rise), both attacks with the
new timing, the sword no longer touching the ground, the corrected (layer-less) model reading as a proper
humanoid, and real damage (a forced hit reduced `getHealth()` by exactly the amount dealt, no resistance).
Not covered: the rumble/zoom camera cues themselves (hard to verify from a static screenshot) and real
multiplayer.

## Known simplifications

- No second texture layer (see above) — base skin only.
- The attack-arc damage sweep is a single instant AABB/cone check timed to the strike beat, not a
  frame-by-frame hitbox on the actual swinging sword geometry — consistent with how every other boss in
  this mod resolves its telegraphed attacks.
- Attack-state and spawn-state fields are not persisted to NBT; a save/reload mid-attack or mid-spawn just
  resets to idle/finishes spawning instantly, which only ever costs a few seconds of a wasted windup.
