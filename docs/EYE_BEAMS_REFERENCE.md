# Eye beams (v0.17.5)

User: "change how heat vision in the mod looks, like laser vision in general, i want it to look better in third person and in
first person". Every player eye beam now shares one look, drawn by `client/eyebeam/EyeBeamFx`. Client only: no ray, damage,
range or clash numbers changed. Clashing (start rule, lock, no drain, Escape forfeit) is in `BEAM_CLASH_REFERENCE.md`.

## Who uses it

| Beam | Renderer (decides when it is live, ray-casts the end) | Style |
|---|---|---|
| Kryptonian Heat Vision (+ server-sent `KIND_HEAT_VISION`) | `kryptonian/KryptonianBeamRenderer` | `HEAT` red-orange, flames + lava embers |
| Laser Vision (all kinds; Maximum Output adds the centre halo) + server-sent beams (untracked shooters, Laser Vision boss) | `mutation/v0145/LaserBeamRenderer` | `LASER` red |
| Sentry Solar Gaze (keeps his golden ring at the hit) | `sentry/SentryFxClient.gaze` | `SOLAR`, `SOLAR_HOT` once hot |
| Starfire Eye Beams | `starfire/StarfireBeamRenderer` | `STARFIRE` green |
| Vision Mind Stone Beam / Unleashed (one beam from the brow) | `vision/VisionBeamRenderer` | `MIND` gold |
| Doomsday heat vision (boss, from his own eye points) | `doomsday/DoomsdayRenderer.heatBeam` | `DOOMSDAY` deep red |

Not converted (own identities): Eye Guy's multi-eye projectile beams (`EyeGuyFx`), Galactus's Power Cosmic eye beams,
Darkseid's homing Omega Beams.

## How it works

- Renderers call `EyeBeamFx.twin / brow / twinAt / twinFrom` every frame from their own `AFTER_ENTITIES` listener, after their
  usual `BeamClashClient.hides(...)` / `hidesFrom(...)` check. `EyeBeamFx.init()` is called **last** in
  `ProjectHeroModClient.onInitializeClient` so its `AFTER_ENTITIES` listener runs after all of them and draws everything in one pass.
- `EyeBeamFx.ray(...)` is the shared ray (block collider, then the first living non-armour-stand creature unless piercing) and
  returns the block face for the scorch.
- **Eyes**: `mixin/EyeBeamHeadCaptureMixin` stores each player model's head matrix just before `renderToBuffer`, so beams leave
  the drawn eyes (head pixels x +-1.5, y -3.5, z -4.4; brow y -6.2) in every pose: crouching, flying, swimming, scaled. If the model
  was not drawn this frame a head-pivot estimate from the eye position and look is used.
- **Layers** (soft-edged ribbons: bright centre line, alpha 0 at the edges): haze, glow (`EyeBeamRenderType.GLOW`, alpha blend), core
  and white-hot centre (`ADD`, additive); none write depth. Flicker, two pulses running out along each beam, a faint shimmer at
  the eyes, glowing eye discs on the face.
- **Impact**: layered soft flare + 6 rotating rays, a molten disc on the block face; scorch decals (max 96, 4 s, cool from glow to
  black). Particles: at most one spray per beam spot per tick, 18 per tick across all beams.
- **Ramp/fade**: per beam state (entity id + channel): shoots out over 2 ticks and swells over `rampTicks`; stopping, it thins
  and pulls away from the eyes toward the target over `fadeTicks` (hidden at once if the shooter enters a clash).
- **First person**: beams start just below the bottom edge of the screen (angle from the FOV setting) either side of the centre,
  fade in at the edge and thin out near the target; a warm screen-edge glow (`hud`, about 20% alpha at the very edge) eases in and
  out while you fire.
- **Your own beam, third person**: back view draws the first 7 blocks over your head (`OVER`, no depth test); front view stops it
  0.8 blocks short of the camera.
- Static maps are client-only and cleared on disconnect (no server worlds held).

Shots: `docs/screenshots/v0175/eye_beams/` (`before/`, `after/`, `compare/`, MP4s + frame sheets; `solar_gaze_ray/` = the Solar Gaze aimed at, beside and over a husk, before and after the feet-snapping fix). Harness kept in
`scratchpad/EyeBeamHarness.v0175.java.txt`, films by `scratchpad/eye_beams_film_v0175.js`.
