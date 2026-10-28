# Hero Landing (v0.17.1)

User request: "add a superhero landing to green lantern please. Add a cool landing animation and pose to all the flight
landings that dont have one please".

One shared system in `com.projecthero.mod.flight.landing` (server) and `com.projecthero.mod.client.flight` (client). A
power only registers a style; detection, the hold, the sync, the effects and the pose are shared.

## Trigger (`HeroLanding`)

Every player, every server tick (`END_SERVER_TICK`):

- While airborne, the first registered style whose "flying under me" test passes **claims** the player; the peak descent
  and speed of the last few ticks are remembered (both the client-reported known movement and the position delta; the
  peak fades by 0.12 / 0.15 b/t per tick so a dive that levelled out stops counting).
- On touchdown, within `CLAIM_WINDOW` = 10 ticks of a claim and after `MIN_AIR_TICKS` = 3 airborne ticks, it is a hero
  landing when it was fast:
  - a descent of `FAST_DESCENT` = 0.55 b/t (11 blocks/s), or
  - a dive: `DIVE_SPEED` = 0.9 b/t total (18 blocks/s) with at least `DIVE_DESCENT` = 0.25 b/t (5 blocks/s) downward.
- A gentle touchdown just lands (no pose, no effects).
- Drops without flying: only the fall-immune Kryptonian and Thor (`registerDrop`), at `DROP_DESCENT` = 0.95 b/t (about an
  8-block fall).
- Never while riding, in water / lava, swimming, on an elytra, in spectator, asleep or dead (`excluded`).
- Strength 0..1 = `(max(descent, 0.7 * speed) - 0.4) / 1.4`, clamped to 0.15..1; it scales the ring, the decal, the
  shake and the sound.

**No fall damage**: an `ALLOW_DAMAGE` hook cancels fall damage within `FALL_SAFE_TICKS` = 12 of a claim and during the hold.

**The hold**: the style's `holdTicks` (14-24, about 0.7-1.2 s). After its `lockTicks` (4; the Surfer 11 for his skid),
walking (`MOVE_CANCEL` = 0.1 b/t horizontal), jumping, riding or water ends it early. Start and end go to the lander and
everyone tracking them (`HeroLandingPayload`).

Static state (`TRACK`, `ACTIVE`) is cleared by `ServerStateReset`, and per player on logout, death / respawn and
dimension change.

## Styles (`HeroLandingStyle`, `HeroLandingStyles`)

| Style | Flight | Pose | Signature |
|---|---|---|---|
| Green Lantern | Ring Flight | three-point, ring fist planted | ring flare at the fist, green hard-light shockwave + second wave, Lantern **emblem** glowing in the crack (`QuirkFx.DECAL_EMBLEM`), hard-light hum |
| Kryptonian | flight, or a long drop | three-point | crater: scorched bowl, wide crack web, small debris, heavy shake |
| Thor | flight, or a long drop | hammer-first (arm comes down from overhead) | a cosmetic beam onto the hammer head, a ring of crackling arcs, thunder crack |
| Nova | Nova flight | three-point | gold ring + red ring, an upright flare ring, starlight |
| Silver Surfer | surfing | crouched on the board, then steps off (the board stays under him and slides back) | silver ripple, a short skid with spray |
| Max Steel | Turbo Flight | three-point | blue Turbo burst (always blue) |
| Cyborg | jets / hover | two-foot jet crouch | red tech ring, sparks |
| Blue Beetle | flight | three-point | blue tech ring, sparks |
| Hellflame | Fire Jet Flight | jet crouch | fire ring, scorch |
| Dark Shadow | Black Fallen Angel (Abyssal) wings | wings fold | shadow pool |
| Heatblast | Fire Jet (read-only: `Omnitrix.active` + kit flag) | jet crouch | fire ring, scorch |
| Hawk Moth | Swarm Flight | wings fold | purple ring |
| Moon Knight | Cape Glide | cape folds | white gust ring |
| Pyro / Cryo / Hydro / Rock | Flame Jets / Ice Wings / Hydro Jets / rock surf + timed self-flight | crouch / wings / crouch / three-point | fire / frost crack / splash / crater |
| Flight | the Flight power's hero flight | three-point | gust ring |

Already had their own landing, not registered: Falcon (`FalconFlight` kneel + dust ring), Iron Man and the Repulsor
Boots / Thruster Pack (`IronManFallLanding`, `IronManLandingSlam`), Super Strength's Power Leap (`StrengthLandingPose`),
Spider-Man (`SpiderAnim`). No flight to land from: Ghost Rider, the Speed Force / Flash.

## Effects (`HeroLandingFx`)

Shared: the shockwave ring (style colours), a soft dust ring in the ground block's colour, a crack decal (cosmetic, no
block is touched), block dust flung out, a few puffs, `hero_landing_impact`, a camera shake for everyone within
10-22 blocks, a 2-tick hit-stop on strong landings and a small FOV punch for the lander. Then the style signature above.

Sounds (`HeroLandingSounds`, `scratchpad/synth_hero_landing_sounds.js`, mono OGG via the libvorbis ffmpeg):
`hero_landing_impact`, `hero_landing_hardlight`, `hero_landing_thunder`, `hero_landing_cosmic`. The emblem texture is
`scratchpad/gen_hero_landing_textures.js`.

## Client

- `HeroLandingPose` (last `HumanoidModelMixin` injection): FIST / HAMMER / WINGS kneel (hips drop 6 px, lean from the hips
  with the neck / head / shoulders following, an impact bounce, head bowed then looking up before he rises), CROUCH and
  BOARD lunges. Mirrored for left-handed players. Blends in over 2.5 ticks and out over 5 after the hold ends.
- `FlightPoseHelper.lean` / `armRaise` are scaled by `HeroLandingClient.flightPoseFactor`, so the dive straightens out
  within the first tick of the landing.
- First person: `HeroLandingCameraMixin` dips the view toward the ground and nods it down (scaled by Distortion
  Effects); bare-handed, the planted fist pushes into view (`QuirkFirstPersonPose` clip).
- The Silver Surfer's board is drawn under him during the landing (`SilverSurferLayer`).

## Tests

`HeroLandingGameTests`: GL / Thor fast landings and a Kryptonian long drop trigger; a gentle touchdown and a powerless
fall do not; no fall damage; the hold clears on its own and early when walking; never in water or while riding.

Screenshots: `docs/screenshots/v0171/landing_*.png` (harness: `scratchpad/LandingHarness.v0171.java.txt` +
`LandingHarnessCameraMixin.v0171.java.txt`; the camera mixin keeps the player as the camera entity so the client keeps
sending its position -- an orbit camera entity stops the real dive from ever reaching the server).
