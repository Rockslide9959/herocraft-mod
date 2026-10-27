# Erasure + Zero Gravity revamp (v0.17.1, wave 2 of the Quirks revamp)

Built from `docs/QUIRKS_REVAMP_PROPOSAL.md` 3.5 and 3.7 (user-approved; every open question resolved to the doc's
recommendation: Erasure's +20% Exposed, the Sleeping Bag item, Zero Gravity's C as Gravity Bomb with Catch on a V hold,
custom sounds), on the shared framework (`docs/QUIRK_FRAMEWORK_0171.md`).

User rule kept: **"i dont want like heat or darkness or whatever to build up too quick, make it work well please."** Both
meters pass the framework's pacing test with the new moves in their heavy-use scripts (numbers below).

Key rules: X = movement, V = utility / mode, R G Z C attacks, hold C at a full Plus Ultra gauge = the ultimate (a C tap is
still the C move). H is the power selector only; no N for either Quirk; no H/N HUD boxes.

## Erasure (Shota Aizawa, Eraser Head)

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | Capture Bind: rooted 4 s (players softened), not bosses | Capture Pull: 6, yanked into reach; on an ally: Pull to safety | **Reel Lock**: a bound target strung up 1.2 blocks, 2/s, Dry Eyes +5/s (raw) |
| **G** | Close Combat 5 / 5 / 6 / 10 | Counter: 0.6 s parry, caught hit = 0, attacker 10 + tied 2 s | -- |
| **X** | Scarf Pull: zip to a block / creature, 6 on arrival | **Evade Flip**: backflip ~5 blocks, 6 ticks of i-frames | Scarf Swing |
| **Z** | Bind and Spin: 8 a hit, 10 on the fling | Disarm Throw | -- |
| **C** | Scarf Whip: 10 blocks, 7 + a 1 s stagger | **Multi-Bind**: up to 3 in a 35-degree cone, 2.5 s | **Underground Takedown** (Plus Ultra) |
| **V** | Erasure Stare (held) | Goggles (moved from Shift+C) | -- |

Eye Drops left the keys: the item is right-clicked.

- **Exposed** (`ErasureExposed`, `ErasureExposedMixin`): anything erased takes x1.2 from every hit, cannot sprint, gets no
  jump boost. Every scarf hit on an erased creature ties it for 1 s (`ErasureMoves.scarfStrike`). This is what makes him
  strong against plain mobs.
- **Bands** (Dry Eyes): Fresh 0-49; Strained 50-89 (the stare reaches 32 blocks, a red screen edge); Burning 90-99 (the stare
  holds two targets, 0.5 HP/s); forced shut at 100.
- **Underground**: sneaking in the dark (light <= 7), mobs see him at half range (`getVisibilityPercent`).
- **Catnap**: Aizawa's Sleeping Bag (`projecthero:sleeping_bag`; 5 yellow wool + 1 string, shaped `YYY / YSY`). Anyone can sleep
  anywhere at night on the ground; the night is skipped by the usual vote; the spawn point never moves; an Eraser wakes with
  Dry Eyes at 0. Drawn round the sleeper (`SleepingBagLayer`).
- **Goggles perk**: the full Eraser Head suit makes the Goggles' stare perks passive (lock-on, -25% drying).
- **Combos**: Strung Up (Bind -> Spin, same target, 1.5 s, x1.5 fling), Crack and Yank (Whip -> Pull, same target, 1.2 s, x1.4).
- **Ultimate, Underground Takedown** (`ErasureUltimate`): goggles snap down; a 20-block, 40-degree cone is erased for 6 s
  (bosses 4 s, never bound, threat credited); he chains through up to six (one every 7 ticks: the scarf zips him there, a
  10-damage kick, a bind; i-frames while he chains); all of them strung up for 2 s with the scarf linking them; then the
  drawback: Dry Eyes 100, forced shut.
- **Pacing**: build x0.15 (the stare 10/s raw and Reel Lock 5/s raw); the heavy-use script (a held stare + a 3 s Reel Lock
  whenever Capture Bind is ready) reaches Burning after ~49 s; Burning -> Fresh at rest in 3.3 s.

## Zero Gravity (Ochaco Uraraka, Uravity)

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | Comet Toss: 10 + 4 splash; nothing afloat: a chunk of ground | **Debris Barrage**: every piece of debris up fires as a shotgun, 4 each | -- |
| **G** | Gunhead Martial Arts 4 / 4 / 8, the throw floats it | Zero Gravity Slam: 8 blocks up, 8 + its fall | -- |
| **X** | Zero-G Leap | **Weightless Push**: an 8-block air dash, twice before landing | Float Self (while held) |
| **Z** | Zero Gravity Field: radius 5, 4 s | Debris Shield | -- |
| **C** | Meteor Shower (C again rains it) | **Gravity Bomb**: 5x3x5 slab overhead, Shift+C again drops it, 20 in radius 4 | **Full Release** (Plus Ultra) |
| **V** | Touch | Release | Catch (while held); Shift + hold: Settle (works while sick) |

A tap is a press let go within 5 ticks (`ZeroGravityConfig.HOLD_TICKS`).

- **Touch marks**: everything she floats wears glowing pink fingertip prints (`ZeroGravityPrints`, from the synced
  `ZeroGravityMarksPayload`) and slowly turns.
- **Bands** (Nausea): Steady 0-49; Queasy 50-84 (screen sway, +25% float capacity); Pushing it 85-99 (strong sway, a green
  wobble at the edge, x1.3 drop damage); Sick at 100. The Nausea bar's edge sloshes once Queasy.
- **Passives**: Light on her feet (no fall damage below Queasy); Float pickup (dropped items within 6 drift to her).
- **Survival**: Crane (touch a placed block, Release sets it down where you point -- placed, never broken; the old cell stays
  empty: `GeoTerrain.commit`); Herding (an animal she touches follows her for 20 s, then is set down softly); Catch / Settle.
- **Ultimate, Full Release** (`ZeroGravityUltimate`): every mob, loose block and item within 16 floats up for 4 s (bosses:
  rooted and lifted 3 blocks, gravity always restored); "Release!" -- they are slammed down (6 + their fall), the rubble rains
  on them; then Nausea 100, sick. Nausea does not build while it runs.
- **Pacing**: build x0.20 (suit x0.75) unchanged; the new costed moves (Gravity Bomb 6, Push 1.5, the Gunhead throw 3) join the
  heavy-use rotation: Pushing it after ~51.5 s; 85 -> 50 at rest in 4.4 s.

## Sounds (`scratchpad/synth_quirk_erasure_zerogravity_sounds.js`, mono OGG)

Erasure: `quirk_erasure_scarf_whip` (2), `quirk_erasure_scarf_snap` (2), `quirk_erasure_glare` (the stare's hum),
`quirk_erasure_bind`, `quirk_erasure_goggles`. Zero Gravity: `quirk_zerogravity_touch` (2), `quirk_zerogravity_float` (the soft
chime), `quirk_zerogravity_release` (the clap and heavy thud), `quirk_zerogravity_meteor` (the rumble), `quirk_zerogravity_push`.

## Animation

Every move has a third-person pose (`ErasurePose`, `ZeroGravityPose`, 16/17-column `QuirkKeyframes`) and a first-person clip
(`ErasureFirstPerson`, `ZeroGravityFirstPerson`); held moves have long clips the kit ends on release. Whole-body turns: the
Evade Flip's backflip and Multi-Bind's sweep (`ErasureSpinMixin`), the Weightless Push's dive and Float Self's tilt
(`ZeroGravityBodyTurnMixin`). The capture scarf stays real rendered cloth (`ErasureWorld`: the line, the coils, the Multi-Bind
fan, the takedown's chain, the stare's red sight line only the Eraser sees) plus the toolkit's cloth ribbons on each throw.

## Generators (worktree `scratchpad/`)

`synth_quirk_erasure_zerogravity_sounds.js`, `gen_quirk_erasure_zerogravity_textures.js` (the bag's icon and cloth),
`add_sounds_erasure_zerogravity.js`, `lang_erasure_zerogravity.js`, `pace_sim_erasure_zerogravity.js` (an offline copy of
the pacing test's script), `QuirkEzgHarness.v0171.java.txt` (the screenshot harness, enabled by `run/quirkezg.flag`).

## Screenshots

`docs/screenshots/v0171/quirk_erasure_*.png` (scarf whip, Multi-Bind, bind and Reel Lock, the stare, the Evade Flip, Bind and Spin,
Underground Takedown: cone / chain / strung up, the Sleeping Bag, first person: whip, Multi-Bind, the Strained stare with its HUD,
Reel Lock, the takedown, the Burning HUD) and `quirk_zerogravity_*.png` (touch and the fingertip prints, Release, Meteor Shower
cloud and rain, Gravity Bomb lift / overhead / impact, Debris Barrage, the Gunhead throw, the Weightless Push, the Crane, Full
Release burst / float / clap / crash, first person: touch, Release with the Queasy HUD, Gravity Bomb with Pushing it, Catch, Full
Release). Harness: `scratchpad/QuirkEzgHarness.v0171.java.txt` (enabled by `run/quirkezg.flag`; the file's text picks the scenes:
`er`, `zg` or both).
