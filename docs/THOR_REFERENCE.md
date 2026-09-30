# Thor (Hero-Tier power)

Power logic `com.projecthero.mod.power` (`ThorPowers`, `ThorPassives`, `StormEnergy`), the hammer
`entity/MjolnirEntity` + `com.projecthero.mod.hammer`, Thor's Armour `com.projecthero.mod.thorarmor`, key routing
`hero/ThorAbilityAdapter`, client `client/thor`, flight pose `client/FlightPoseHelper` + `client/mixin/HumanoidModelMixin`,
HUD `client/gui/ThorHud`. Player-facing text: the Guidebook's Thor chapter (`hero/guide/HeroPackGuide`, keys
`projecthero.guide.thor.*`, which the power info screen shows too).

Keys: R Call Mjolnir (right-click throws), G Lightning Strike, X Lightning Beam (hold), Z God of Thunder's Wrath (hold 5 s),
V Hammer Volley, Shift+V Thunderclap, C Chain Lightning, H Thor's Armour, double-tap Jump flight.
Storm Call (`ThorPowers.stormCall`) is still in the code but not on any key since v0.6.22. There is no Thor Parry.

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
