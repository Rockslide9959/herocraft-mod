# Speed Force Speedster reference

Package `com.projecthero.mod.speedforce` (server), `client/speedforce` (client). The player-facing rules live in the
Guidebook (`projecthero.guide.speed_force_speedster.*`) and the move descriptions in `en_us.json`. This file collects the
implementation notes that are not obvious from those.

## Hold C: Speed Force Charge (the set position)

- **Start:** hold C (0.6 s) while standing still (ground speed at most `SpeedForceConfig.CHARGE_STILL_BPS`). On the move the same
  hold is the Momentum Lock instead. `SpeedForceAbilities.startCharge` sets `SpeedForceState.chargeStart` (synced).
- **Full:** after `CHARGE_TICKS` (3 s) a chime and "Charged -- let go!". Letting go then launches (`releaseCharge`): Speed Mode
  on at 100% momentum, held 2 s. Letting go earlier fizzles. 10 s cooldown on a launch.
- **Rooted (v0.17.7, user):** while `chargeStart != 0` the runner can look around but not walk, strafe, jump, sprint or crouch.
  - Owning client: `SpeedForceChargeInputMixin` (on `KeyboardInput.tick`, TAIL) zeroes the movement impulses and the
    jump/crouch flags and calls `SpeedForceSpeedster.holdStill`, every tick before the move.
  - Server: `SpeedForceAbilities.chargeTick` calls `holdStill` every tick (zero input, no jump, horizontal velocity killed,
    a fall still falls) and syncs it (`hurtMarked`). The stance's spot is kept in `CHARGE_ANCHOR`; a runner more than
    0.75 blocks off it (lag into the stance, a client that slipped the lock) is teleported back, keeping their look.
  - Not a movement-speed modifier: vanilla's FOV follows the speed, so a -100% modifier zooms the camera in (Super Soldier's
    Barrage hit that).
  - The launch or a fizzle clears `chargeStart`, which frees the runner at once.
- **Pose (v0.17.7, user's reference photo: a sprinter at "set" in the blocks):** `SpeedForcePose.chargeStance`. Both hands
  flat on the ground just ahead of the shoulders with the arms straight, the torso tipped ~104 degrees so the hips ride
  above the shoulders, the front leg forward under the chest, the rear leg back nearly straight onto the (raised) back
  block, the head down. Built in model space: the shoulder is placed, the neck and hip follow from the torso pitch, each limb
  is aimed so its end lands on the floor. Eases in over 6 ticks; a tremble builds with the charge (the hands stay planted).
  The front leg is the off-hand side, mirrored for left-handed players.
- **First person:** the hands are hidden during the charge (registered with `CutsceneHands`); the camera stays at standing
  eye height, so hands on the ground would not line up with the view.
- **VFX:** unchanged -- gold dust spiralling in and electric sparks, growing with the charge, bell notes rising in pitch.
- **Test:** `SpeedForceSpeedsterGameTests.theChargeStanceRootsYou` (per-tick steps ~0 while charging, free after the launch).

## After-images: strictly 40% (v0.17.7, user)

- **Rule:** every after-image (the Speed Force trail and its streak/decoy copies, the plain Super Speed trail, zips and decoys,
  XLR8) is exactly 40% opaque (`GhostCopies.ALPHA`) and never denser -- not where the hat/jacket/sleeve/trouser layers overlap
  the base skin, not with armour on, not through the model's own inner faces, not where two copies overlap. A copy starts at
  40% and only fades below it. Colour unchanged: full-bright, plain white tint (Super Speed keeps its `s.rgb()`), no glow.
- **How:** `client/mutation/v0145/GhostCopies`. The renderers queue each copy (skin + `GhostArmour`) and a later
  `AFTER_ENTITIES` phase draws all of them twice through its own immediate buffer: first a depth-only pass (colour writes off,
  back faces culled, the entity-translucent shader's `a < 0.1` cutout keeps empty hat pixels out, polygon offset pushes it back
  a hair), then the colour pass at the copy's alpha with a strict `LESS` test that writes depth. Only the frontmost surface
  of all the copies passes, once; anything behind or coplanar with it fails. Never draw a copy straight with
  `entityTranslucent` again: that blends every overlapping layer.
- Known trade-off: on two exactly coplanar textures (leather armour's dye layer + overlay, trims) only the first drawn shows
  on the copy.

## Flash Time / Time Slow: per dimension (v0.17.7)

User: "can you change all the time stops and time slows so that they still work worldwide but not across dimensions".
Flash Time (and the Super Speed mutation's Time Slow, both `hero.power.p04.SuperSpeedTimeSlow`) no longer drop the
server's tick rate. `time.LevelTimeScale` scales just the caster's dimension:

- The server runs at 20 tick/s. The scaled dimension runs one tick in 20 (`TICK_DIVISOR`) and is held on the others:
  `LevelTimeScaleServerLevelMixin` turns that level's own `runsNormally` off (weather, world border, block / fluid / random
  ticks, spawning, raids, block events, day time) and skips `tickNonPassenger` for every entity but the caster's root-vehicle
  group; `LevelTimeScaleLevelMixin` holds block entities, `LevelTimeScaleMobMixin` the despawn roll,
  `LevelTimeScaleChunkCacheMixin` spawning / random ticks / lightning, `LevelTimeScalePlayerMixin` a held player's connection
  tick (living tick and the floating-kick counter), and `ProjectHeroMod` skips their per-player mod systems. Chunk loading and
  entity tracking carry on every tick, so the caster's movement reaches every client in real time.
- The game clock is shared by every dimension, so it never slows (the Overworld's held ticks still count it; only its day
  time holds). The caster's old catch-up (extra ticks from `pollTask`, cooldown / animation / clock advances) is gone:
  they are simply never held. Items near them still count pickup delays and the Black Flash keeps their pace on held ticks.
- Clients: players in the dimension get vanilla's ticking-state packet at 1 tick/s, everyone else the server's real state;
  re-sent on join, dimension change, respawn and after vanilla `/tick` broadcasts (`LevelTimeScaleTickRateMixin`).
  `TimeSlowStatePayload` carries the dimension; `TimeSlowClient` only applies it while the client is in that dimension.
- One slow per dimension. It stays in the dimension it was cast in (the caster can walk out; it runs on to its end) and
  ends on its timer, Z / letting go, or the caster's death, logout or power loss.
- Known approximation: scheduled block ticks are due on the shared clock, so in a slowed dimension a scheduled chain
  (redstone, flowing water) advances at most one step per run tick instead of exactly 5%; random ticks are exact.
