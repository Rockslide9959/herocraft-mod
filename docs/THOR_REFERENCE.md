# Thor (Hero-Tier power)

Power logic `com.projecthero.mod.power` (`ThorPowers`, `ThorPassives`, `StormEnergy`), the hammer
`entity/MjolnirEntity` + `com.projecthero.mod.hammer`, Thor's Armour `com.projecthero.mod.thorarmor`, key routing
`hero/ThorAbilityAdapter`, client `client/thor`, flight pose `client/FlightPoseHelper` + `client/mixin/HumanoidModelMixin`,
HUD `client/gui/ThorHud`. Player-facing text: the Guidebook's Thor chapter (`hero/guide/HeroPackGuide`, keys
`projecthero.guide.thor.*`, which the power info screen shows too).

Keys: R Call Mjolnir (right-click throws), G Lightning Strike, X Lightning Beam (hold), Z God of Thunder's Wrath (hold 5 s),
V Hammer Volley, Shift+V Thunderclap, C Chain Lightning, H Thor's Armour, double-tap Jump flight.
Storm Call (`ThorPowers.stormCall`) is still in the code but not on any key since v0.6.22. There is no Thor Parry.

## v0.14.20: Mjolnir / Stormbreaker 3-hit melee combo, Stormbreaker hold fix

### Combo -- `power/WeaponCombo` (+ `WeaponComboState`, `mixin/PlayerAttackComboMixin`)
- Any wielder (no power gate), main hand only. A melee hit counts when it LANDS (confirmed through `AFTER_DAMAGE`,
  not blocked) at attack strength >= 0.9 (`MIN_STRENGTH`, read at `Player.attack` HEAD before vanilla resets it).
  Steps 1 -> 2 -> 3 -> 1. Weak hits neither advance nor reset; misses are ignored; switching weapon starts over.
- Window: the next step must land within `windowTicks` = the weapon's swing recharge (ceil of
  `getCurrentItemAttackStrengthDelay`, 19 ticks Mjolnir / 23 Stormbreaker) + 25 ticks (1.25 s), else it starts at 1.
- Step 3 (finisher): +50% (`FINISHER_BONUS`) of the hit's own damage on the target -- dealt by re-hurting it at 1.5x
  inside its hurt cooldown, so vanilla applies exactly the difference (works on players too). Mjolnir: shockwave of
  4 damage + knockback 0.8 / lift 0.25 within 3 blocks of the target, sparks + thunder crack. Stormbreaker: cleave of
  6 damage + knockback 0.6 within 4.5 blocks in a 150-degree arc in front of the wielder. The area hit skips the
  target, the wielder, their pets (`OwnableEntity`) and squad allies (`Squads.areAllies`); bosses are never knocked
  back. Base weapon damage is unchanged.
- State = the synced, non-persistent `ModAttachments.WEAPON_COMBO` (`step, weapon, start`): both the combo's own state
  (no static map) and what every viewer animates from. `WeaponCombo.setForTests` for tests.

### Swing animations -- client `thor/WeaponComboPose`, `mixin/ItemInHandRendererComboMixin`
- Third person: keyframe tables (ThorPose technique) per weapon per step -- Mjolnir one-handed (flat swing, high-left
  to low-right backhand, overhead slam with the free arm thrown wide), Stormbreaker two-handed (flat cleave, rising
  backhand, two-handed overhead chop). Applied in `HumanoidModelMixin` just before `ThorPose` (a Thor move wins).
  `weaponGrip` re-grips the held weapon along the arm in `ItemInHandLayerMixin` (the same -90 degree turn as ThorPose).
- First person: the weapon is moved about the hand before it is drawn; vanilla's swing and post-hit lowering of the
  main-hand item are scaled out by the swing's weight (`@ModifyArg` on the main-hand `renderArmWithItem` call).
- The local player's step is predicted on click (client `AttackEntityCallback`), so it starts with the click; the
  server's stamp of the same step takes over without a jump. Other players see the synced state.

### Stormbreaker hold
- v0.14.19 copied Mjolnir's handheld display but had the axe blade on +X, which the -45 degree icon-diagonal bake puts
  on the side a vanilla axe sprite does NOT have its blade: held blade-up ("upside down"). The generator
  (`scratchpad/gen_v01419_stormbreaker_assets.js`) now mirrors the geometry in X (blade -X, hammer +X) and uses the
  vanilla `item/handheld` rotations ([0,-90,55] third person, [0,-90,25] first person) with its own translation /
  scale (first person bigger and moved so the whole head shows), gui = the plain tool diagonal. Verified side by side
  with Mjolnir and an iron axe in the client screenshot harness.
- Thrown (`StormbreakerEntityRenderer`): the model is turned a quarter about Y before the tumble so it spins in its own
  blade plane (it used to spin about an axis through the blade, the blade sticking out sideways).
- Tests: `WeaponComboGameTests`.

## v0.14.4: squad safety, piece-by-piece armour, move animations, new effects

### Squad safety -- `power/ThorTargets`
- `ThorTargets.canAffect(caster, target)` is the single rule every Thor power goes through: never the caster, a
  squadmate (`Squads.areAllies`), a pet owned by either (`OwnableEntity`), another player while PvP is off, or an armour
  stand. It gates damage **and** knockback, Slowness and the animal singe-fire, not just the hit.
- Wired into: Lightning Strike (primary + auto-chain), Chain Lightning (`chainLightningTargets`), Lightning Beam,
  God of Thunder's Wrath (`wrathTargets`), Thunderclap, Storm Call, the thrown hammer (`MjolnirEntity#canHitEntity` -- it
  flies straight past allies instead of stopping on them) and Hammer Volley (target pick + strike).
- Aim rays (`ThorPowers.aimable`) treat spared entities as see-through, so a bolt/beam goes past a squadmate standing in
  the way onto the enemy behind.
- Thor's lightning damage is now `ThorTargets.lightning(level, caster)`: still the `lightning_bolt` damage type (Thor's
  lightning immunity and every other lightning rule unchanged) but with the caster as attacker, so the squad
  friendly-fire veto, PvP rules, kill credit/XP and mob retaliation all see who threw it.
- Storm Call no longer spawns a real vanilla `LightningBolt` (which hit everything in 3 blocks, lit fires and converted
  villagers/pigs/creepers): a visual-only bolt + 8 damage to what `canAffect` allows within 3 blocks.
- Every other Thor bolt was already visual-only (`setVisualOnly(true)`), so it never damages or ignites anything itself.

### Synced move state -- `power/ThorFx` + `ThorVisuals`
`ModAttachments.THOR_FX` (synced to all, never persisted): last one-shot anim (id, start tick, world point), channel bits
(`CH_BEAM`, `CH_WRATH` + `chargeStart`) and the armour suit clock (`suitDir`, `suitStart`). Only `ThorVisuals` writes it,
and only on change (the per-tick channel calls are free on the wire).

### Thor's Armour suit-up -- `thorarmor/ThorArmor`, client `thor/ThorSuitReveal`
- H equips all three pieces on the first tick (the protection is instant and nothing can be interrupted), and starts
  the `SUIT_UP` clock *before* equipping so no viewer sees a frame of the full set.
- The look is piece by piece over `SUIT_UP_TICKS` (36): boots 0-12, greaves 10-24, chestplate+cape 22-36
  (`ThorArmor.pieceProgress`). Each piece sweeps up the body (`ArmorSweepReveal` now takes a `Sweep`: bone filter,
  bottom-up, edge + trail colours; per-piece frames are cached per bone set) with a white-hot edge row and an
  electric-blue trail row. The boots get the vanilla visual bolt; the greaves and chest get a sparks+flash+sound burst
  server-side (`pieceArrives`) and a client bolt from the sky (`ThorFxRenderer.suitBolts`).
- H again starts `SUIT_DOWN` (14 ticks, chest -> greaves -> boots dissolve); `ThorArmor.tick` strips the pieces when it
  ends and clears the clock 20 ticks later (clearing at once could flash the suit back on for a frame).
- Toggle debounce: `SUIT_UP_TICKS + 4` after summoning, 20 after dismissing. Death / the once-a-second audit still
  strip instantly.
- Hooked in `SuperheroArmorRenderer#getRenderType` by `getCurrentSlot()`.

### Move animations -- client `thor/ThorPose`
Hooked as its own `HumanoidModelMixin` TAIL injection declared **after** the flight pose, so a move made mid-flight
overrides the flight arm pose. Keyframes (smoothstep, fade-out over the last 4 ticks), written for a right-hand hammer
and mirrored when Mjolnir is in the left hand. Strike (hammer to the sky, then down), Thunderclap (two-handed overhead
slam, slam lands ~tick 3), Chain Lightning (aimed thrust), throw (aimed follow-through), Hammer Volley (fling + direct),
Wrath release (aimed chop), Storm Call and suit-up (hammer to the sky); held: Beam (braced along the aim, free hand on
the wrist) and the Wrath charge (hammer straight up, other arm wide, trembling as it builds).

Each frame also carries a `grip` weight: `ItemInHandLayerMixin` turns the held hammer `-90 deg * grip` about the hand's X
axis, which lays the hammer's long axis along the arm (head to the sky when raised, at the target when thrust out)
whatever the arm angle. The old HOVER flight correction is scaled by `1 - ThorPose.armWeight`.

### Effects -- client `thor/ThorDraw`, `ThorLightningArcRenderer`, `ThorFxRenderer`
- `ThorDraw.bolt`: a jagged node path drawn as three camera-facing ribbons (halo `0x2F5BFF`, glow `0x7FC4FF`, core
  `0xF4FAFF`) with `entityTranslucentEmissive` over the shared white texture. Replaces the 1-px `RenderType.lines()`
  bundles.
- Lightning Beam / Chain Lightning arcs: main channel + a second twisting channel + 2-3 forks + flares at both ends,
  re-shaped ~7-9x a second with a brightness flicker; the Beam is drawn 1.35x heavier. While the Beam / Chain pose is up
  the arc leaves the hammer head at arm's length (`ThorLightningArcClient.hammerHandPosition`) -- except in your own
  first-person view, where it keeps the old hip start and skips the start flare so nothing sits across the crosshair.
- `ThorDraw.flare`: a small orb core with six jagged sparks whose directions jump every couple of ticks.
- `ThorFxRenderer` (per player, within 128 blocks): Thunderclap shockwave ring + light wall + ground lightning; strike
  impact ring; Wrath double ring + ground lightning + central blaze; Wrath charge orb, lashing arcs, foot ring and sky
  bolts past half charge; suit-up sky bolts.

### Tests
`ThorSquadGameTests` (target rule incl. pets, Thunderclap no shove/slow on the mate, Chain Lightning and Wrath target
lists, Lightning Strike through a mate, piece timeline); `ThorArmorGameTests#hTogglesTheArmourOnAndOff` now waits for
the dissolve. Lang: `scratchpad/lang_v0144_thor.js`.

## v0.14.19-0.14.20: Stormbreaker and the Bifrost -- `stormbreaker/*`

Stormbreaker (v0.14.19): `StormbreakerItem` (14 melee, worthy-only powers), `StormbreakerEntity` (piercing returning
throw), `StormbreakerForge` (10 s in Nether lava), `ThorPowers.isHoldingThorWeapon` (counts as Thor's weapon).

### Bifrost (reworked v0.14.20) -- `stormbreaker/Bifrost`, `BifrostWaypoints`, client `gui/BifrostScreen`
- **Sneak + right-click** (worthy, main hand) sends `BifrostScreenPayload(open=true)`: the three waypoints, the
  cooldown ticks left and the player's dimension. The only exception: sneak-clicking a block within reach with
  something in the off hand returns PASS, so off-hand block placement still works.
- **Screen:** X / Y / Z fields (prefilled with the player's block position) + *Open Bifrost*; three waypoint rows
  (name field, coordinates + dimension, *Save here*, *Go*, clear); a rainbow cooldown bar. *Go* is greyed out with a
  tooltip for a waypoint saved in another dimension. Buttons send `BifrostActionPayload` (TRAVEL_COORDS /
  TRAVEL_WAYPOINT / SAVE / CLEAR); save/clear/failures answer with a refresh (`open=false`).
- **Waypoints:** `ModAttachments.BIFROST_WAYPOINTS` -- persistent + `copyOnDeath`, exactly 3 slots, names cleaned and
  capped at 20 chars (blank = "Waypoint N"). Saved server-side from the player's own position; travel to a waypoint
  uses the server's copy, never client coordinates.
- **Validation (`Bifrost.travelTo`)**, in order: worthy + Stormbreaker in either hand + alive + not spectator
  (`NOT_HOLDING`); cooldown (`COOLDOWN`); same dimension for waypoints (`WRONG_DIMENSION`); Y within build height and
  X/Z inside the world border (`OUT_OF_BOUNDS`); a safe landing (`NO_LANDING`). Any failure spends no cooldown and
  shows `message.projecthero.bifrost.fail.<result>`.
- **No distance limit** beyond the world border. **Same dimension only** (cross-dimension was left out on purpose:
  portals, the End fight and Nether-roof landings make it more than a teleport call).
- **Safe landing (`findSafeLanding`)**, same column only: from Y, if open air, drop to the first floor; otherwise (or
  if the drop hits something unsafe) climb to the first gap. A spot needs two free blocks (no collision, fluid, fire,
  powder snow, berry bush or cobweb) over a solid floor that is not lava, magma, a campfire, cactus or powder snow --
  or a water surface. A column with nothing safe (the void) is refused.
- **Riders:** the user + every `Squads.areAllies` squadmate within **8 blocks** (3D), same level, alive, not a
  spectator and not sneaking (sneak = stay behind). Each keeps their X/Z offset and gets their own safe landing in
  their column if it is within 6 blocks up/down of the user's; otherwise they share the user's spot. Non-squad
  players are never carried. Everyone is dismounted (`stopRiding` / `ejectPassengers`), teleported with
  `teleportTo(level, ...)`, zeroed momentum and fall distance, with the rainbow column at both ends.
- **Chunks:** the destination chunk (and each ally's column chunk) gets a `POST_TELEPORT` ticket and is loaded
  (generated if need be) before the landing search.
- **Cooldown:** 60 s in `ModAttachments.BIFROST_READY_AT` (overworld game time, persistent + `copyOnDeath`). No
  vanilla item cooldown any more, so the throw is never held. Shown in the screen; the action bar names the seconds
  left on a refused attempt and after each trip.

Tests: `StormbreakerGameTests` (Bifrost batch) -- waypoints save / persist through an NBT save-load / clean names;
travel carries a nearby squadmate but not a distant one or a non-squad player; second trip blocked for 60 s; out of
bounds / other-dimension waypoint / empty slot / unworthy refused; landing climbs out of stone and drops through air.
Lang: `scratchpad/lang_v01420_bifrost.js`.
