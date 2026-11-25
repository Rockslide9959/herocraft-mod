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
