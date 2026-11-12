# the Spin (v0.17.3) -- approved design

The user approved this build on 2026-11-11 ("I like this build"), with X and Shift+X swapped from the first draft.
References: `docs/reference/spin/steel_ball_ref_1..5.png` (the ball), `docs/reference/spin/spin_sfx_source.mp4` (the user's
sound clip: "take the sfx from this video"). The clip is a fan-made sound; note the source before a public release.

## Power
- Hero-Tier **Primary** power, Guidebook: Other Heroes, next to Stands. Package `com.projecthero.mod.spin`, client `client/spin`.
- Spin isn't a Stand user: the power is the **Spin** through two **Steel Balls**. **Ball Breaker** (his late Stand) only appears for the
  Shift+Z ultimate, as a ghostly figure.
- **Getting it:** the **Zeppeli Steel Balls** item, rare loot in desert pyramid chests and trail ruins (archaeology), like the
  Stand Arrow. Right-click to learn the Spin; it replaces the Primary power. Random serums never give it.
- **Look:** a player skin of Spin (hat, "GO!GO!" teeth, two balls on the belt).

## Passives
- 20 health (no extra hearts, user 2026-11-12), passive Regeneration I, +15% speed, +4 melee, 15% damage resistance. No skin change.
- **Spin bar** (100), refills 4/s. Moves cost Spin.
- **Two returning balls.** They always fly back. While a ball is out, moves that need it are greyed out.
- **Curving (2026-11-12).** An outgoing R / Shift+R ball bends after the thrower's look (capped turn rate, 120 degrees total, no
  steering when he looks away) and homes onto a creature under the crosshair only if the curve can reach it. Golden Spin stays straight.
  Numbers: `SpinConfig.CURVE_*`.
- **The balls are items (2026-11-12, user's pick).** Two `projecthero:steel_ball` items bound to their owner (`SpinBalls.BallId`:
  owner, index, generation). One in the main hand for R, G, Shift+G, Shift+Z, Z, C, V, Shift+V; one in each hand for Shift+R and
  Shift+C; none for X / Shift+X. Throwing empties the hand; the catch refills the same hand (else a free slot, else the hand,
  dropping what's there). Scan / Quake / Shield take them out while they run. Kept on death, owner-only pickup, R with an empty
  main hand recalls a dropped / chested ball, a destroyed ball respawns after 30 s. The old automatic hand / belt balls are gone.
- **Golden Rectangle.** Holding a Golden Spin move shrinks a gold rectangle onto the crosshair; release while it lines up = **Perfect
  Spin** (double damage, ignores armour).
- **Riding:** on a horse every Spin move hits 30% harder and Golden Spin charges twice as fast.

## Keys (H = power selector only; no N ability)
| Key | Move | Spec |
|---|---|---|
| R | Steel Ball | throw one ball, 30 blocks, 10 dmg, returns; target spins 3 s (skin twists, stumbles off course, can't aim). 15 Spin |
| Shift+R | Twin Balls | both balls, 2 x 10, curving in from either side. 30 Spin |
| G | Spin Touch | melee: joints lock, can't move 2 s; a player drops their main-hand item. 20 Spin |
| Shift+G (hold) | Golden Spin | Golden Rectangle charge; 35 dmg (70 Perfect), pierces blocks and creatures in a line. 50 Spin, 12 s cd |
| Z | Scan | ball spins on the ground; creatures/players within 40 blocks glow through walls 8 s. 15 Spin |
| Shift+Z (hold) | Ball Breaker | needs a Perfect Spin; the figure bursts out and strikes: 60 dmg + aging (Weakness II, Slowness III, Wither 10 s; skin greys and cracks). 100 Spin, 3 min cd |
| **X** | **Spin Leap** | spin your leg muscles: ~12 blocks forward, 5 up, no fall damage after. 10 Spin |
| **Shift+X** | **Valkyrie** | whistle: your horse Valkyrie gallops to you and you mount. Tamed, very fast, armoured, comes when called. 60 s cd if she dies |
| C | Spin Quake | ball hammers the ground: 14 dmg within 6 blocks, knockdown + slow 2 s. 25 Spin |
| Shift+C | Spin Shield | both balls orbit you 4 s, knock away arrows/bullets/fireballs, hit anything close for 4 a tick. 30 Spin |
| V | Healing Spin | tap: heal self 6 hearts over 3 s, clear poison/slowness; aim at a squadmate to heal them. 25 Spin, 15 s cd |
| Shift+V | Face Spin | disguise 20 s: name tag hidden, mobs that haven't seen you attack ignore you. 20 Spin |

## Visuals and sound
- **VFX trails (2026-11-12, user: "replace the spin particles with a spin vfx trail").** `client/spin/SpinTrails`: one render type
  (`RenderType.lightning()`, additive). Thrown balls: a tapering ribbon through their last 14 positions with 3 helix strands turning
  with the ball (green-white; gold for Golden Spin / Ball Breaker). Golden Spin: a gold spiral ribbon fading over 0.5 s. Spin Shield:
  ribbon arcs behind the orbiting balls. Spun creatures: a rendered gold swirl. Spin Quake: a spinning shock ring (block dust kept).
- Steel balls: green metal sphere, a raised hexagon plate on each side, curved grooves wrapping round, glossy highlights
  (see the references). Spiralling golden trail in flight; twisting skin rings on spun targets; gold rectangle + spiral flash on
  Golden Spin; the lime, stud-covered Ball Breaker figure rushing out of the ball (see "Ball Breaker look" below).
- Sounds from the user's clip (quiet). The ids are fixed in `SpinSounds`.

## Shared contract (two build agents)
- `spin/SpinSounds.java`: the sound ids (ART supplies OGGs + `sounds.json`; POWER plays them).
- `client/spin/model/SteelBallRender.render(pose, buffers, light, radius, spinDeg, golden)`: ART implements; POWER calls it everywhere a
  ball is drawn.

## Ball Breaker look (2026-11-12)
The user picked the custom model out of two candidates (the other, a borrowed PlanetMinecraft skin, was dropped) and asked for the face
to match the references. Shift+Z now draws it in `SpinFx.renderBreaker` (solid, growing out of the ball, golden glow on the burst and
the strike, faint golden afterimages, fades at the end).
- **Model:** `client/spin/model/BallBreakerModel` (procedural ellipsoids/frustums/boxes in a bone hierarchy, floating crouch,
  `strike` 0..1 drives the right hand forward) + `textures/entity/spin/power/ball_breaker_model.png` (swatch atlas from
  `scratchpad/spin/gen_ball_breaker_model_tex.js`). Our own work, no licence issue.
- **Face:** smooth lime egg dome; a raised face shield outlined by a dark seam (rounded top, U-shaped bottom) over a dark recessed jaw;
  seven magenta oval studs on thin silver stems running down the shield (All-Star Battle render; the manga's pink bulbs on stalks).
- References: the user's `docs/reference/spin/ball_breaker_face_34.png` / `_35.png` and the jojowiki Ball Breaker page.
- Films: `docs/screenshots/v0173/spin/ball_breaker_*`. Harness: `scratchpad/spin/ball_breaker_harness_hook.js` +
  `BallBreakerHarness.v0173.java.txt` (write `face` into `run/bb.flag` for only the face close-ups); films: `ball_breaker_films.sh`.
