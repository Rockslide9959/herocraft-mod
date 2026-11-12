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
- Steel balls: green metal sphere, a raised hexagon plate on each side, curved grooves wrapping round, glossy highlights
  (see the references). Spiralling golden trail in flight; twisting skin rings on spun targets; gold rectangle + spiral flash on
  Golden Spin; a pale, armour-plated Ball Breaker figure rushing out of the ball.
- Sounds from the user's clip (quiet). The ids are fixed in `SpinSounds`.

## Shared contract (two build agents)
- `spin/SpinSounds.java`: the sound ids (ART supplies OGGs + `sounds.json`; POWER plays them).
- `client/spin/model/SteelBallRender.render(pose, buffers, light, radius, spinDeg, golden)`: ART implements; POWER calls it everywhere a
  ball is drawn.

## Ball Breaker look: candidates (2026-11-12, preview only, NOT wired)
The user asked for Ball Breaker to match the manga look and will pick one. The live Shift+Z effect still draws the old pale
`ball_breaker.png` figure in `SpinFx.renderBreaker`.
- **A, custom model:** `client/spin/model/BallBreakerModel` (procedural ellipsoids/frustums/boxes in a bone hierarchy, floating crouch,
  `strike` 0..1 drives the right hand forward, optional golden glow and fade) + `textures/entity/spin/power/ball_breaker_model.png`
  (swatch atlas from `scratchpad/spin/gen_ball_breaker_model_tex.js`). Our own work, no licence issue.
- **B, PlanetMinecraft skin:** `client/spin/model/BallBreakerSkinFigure` (slim player model, same crouch) +
  `textures/entity/spin/power/ball_breaker_pmc.png`. Source: "Ball Breaker" by **Kiryutojo**,
  https://www.planetminecraft.com/skin/ball-breaker-6348233/ (Alex/slim arms, uploaded 2024-07-22). **No licence is stated on the page**
  (PMC default = all rights reserved): get the author's permission, or drop it, before a public release.
- References used: the jojowiki Ball Breaker page (All-Star Battle render, manga infobox, ASB colour alts, concept sketches).
- Previews: `docs/screenshots/v0173/spin/ball_breaker_candidates.mp4`, `ball_breaker_candidates_sheet.png`, `ball_breaker_vs_refs.png`.
  Harness: `scratchpad/spin/ball_breaker_harness_hook.js` + `BallBreakerHarness.v0173.java.txt`; films: `ball_breaker_films.sh`.
