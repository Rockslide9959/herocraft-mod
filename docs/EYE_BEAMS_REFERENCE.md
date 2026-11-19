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
- **First person** (reworked after "it looks like the beam is coming from the players chest"): the beams leave your eyes. Seen
  from your own eyes, a beam from each eye is a line coming in from the side of the view at eye level, so each starts
  `FP_EYE_FORWARD` (0.12) blocks ahead of the camera, `FP_EYE_SIDE` (0.86) of the way to the side edge and `FP_EYE_DOWN` (0.07)
  of the way down (shares of the half-screen from the FOV setting and window shape, so it looks the same at every FOV); the brow
  beam comes down from `FP_BROW_UP` (0.8) of the way to the top. Out to `FP_FULL_DIST` (3 blocks) the width grows with distance
  from the camera, so on screen it is one even thin line, never a slab; it fades in from nothing at the edge and thins and turns
  see-through over the last stretch so the crosshair stays visible. Screen glow (`hud`): a soft warm bloom at each side edge at
  eye level plus a faint tint along the top (no bottom glow), easing in and out while you fire.
- **Your own beam, third person**: back view draws the first 7 blocks over your head (`OVER`, no depth test); front view stops it
  0.8 blocks short of the camera.
- Static maps are client-only and cleared on disconnect (no server worlds held).

Shots: `docs/screenshots/v0175/eye_beams/` (`before/`, `after/`, `compare/`, MP4s + frame sheets; `solar_gaze_ray/` = the Solar Gaze aimed at, beside and over a husk, before and after the feet-snapping fix). Harness kept in
`scratchpad/EyeBeamHarness.v0175.java.txt`, films by `scratchpad/eye_beams_film_v0175.js`.
