# Thor (Hero-Tier power)

Power logic `com.projecthero.mod.power` (`ThorPowers`, `ThorPassives`, `StormEnergy`), the hammer
`entity/MjolnirEntity` + `com.projecthero.mod.hammer`, Thor's Armour `com.projecthero.mod.thorarmor`, key routing
`hero/ThorAbilityAdapter`, client `client/thor`, flight pose `client/FlightPoseHelper` + `client/mixin/HumanoidModelMixin`,
HUD `client/gui/ThorHud`. Player-facing text: the Guidebook's Thor chapter (`hero/guide/HeroPackGuide`, keys
`projecthero.guide.thor.*`, which the power info screen shows too).

Keys: R Call Mjolnir (right-click throws), G Lightning Strike, Shift+G Thunder Field (v0.16.1), X Lightning Beam (hold),
Z God of Thunder's Wrath (hold 5 s), V Hammer Volley, Shift+V Thunderclap, C Chain Lightning, H Thor's Armour, N weapon
selector (v0.15.3), double-tap Jump flight.
Storm Call (`ThorPowers.stormCall`) is still in the code but not on any key since v0.6.22. There is no Thor Parry.

## v0.16.1: storm buff, dual-wield combo, Twin Thunder Strike, Thunder Field

Config `config/projecthero_thor.json` (`power/ThorConfig`, versioned, sections `storm` / `dualCombo` / `twinThunder` /
`thunderField` all balance-owned).

### Storm-Charged -- `power/ThorStorm`, `mixin/LivingEntityThorStormMixin`
- Condition: `level.isThundering()` and the biome at his feet has precipitation (rain or snow). Sky NOT required by
  default (`storm.requireSky = false`): the god of thunder feels the storm under a roof. "Thor" = worthy + (holding
  Mjolnir / Stormbreaker or bound) -- the same test as his ability keys.
- Effect: +`storm.damageBonus` (0.30) on every hit whose `DamageSource.getEntity()` is that player. ONE hook: a
  `@ModifyVariable` at `LivingEntity#hurt` HEAD (like the Punisher mark), paired with a RETURN inject. Double-count
  guards: (1) a per-thread stack of victims being hurt by a scaled hit -- a hit re-issued from inside the same `hurt`
  (Thor's 30% reduction + built-in armour, Nova, Moon Knight...) on that victim is not scaled again; (2) the single-weapon combo
  finisher's re-hurt (whose amount comes from Fabric's `AFTER_DAMAGE` base, which already carries the bonus) runs in
  `ThorStorm.unscaled`. The stack is cleared every player tick (nothing is inside a hurt then).
- Tell: `ThorFx.stormPct` (synced) -- the client draws little arcs crackling over him (`ThorFxRenderer.stormCrackle`),
  the server sends sparks, and the HUD shows "Storm-Charged +30%" above the keys.

### Dual-wield combo -- `power/WeaponCombo` (`WEAPON_DUAL`), client `thor/WeaponComboPose`
- Mjolnir in one hand and Stormbreaker in the other (either way round): `WeaponCombo.comboWeapon` = `WEAPON_DUAL`, 4
  steps (`DUAL_HITS`). 1 Mjolnir overhead blow, 2 Stormbreaker flat sweep, 3 cross-body double swing (X), 4 leaping
  double slam (the server hops him: `dualCombo.slamHop` 0.42, ground only). Window = swing recharge +
  `dualCombo.windowTicks` (20 = 1 s). Damage = the vanilla hit with the main-hand weapon, untouched.
- Poses: third-person tables `DUAL_1..4` written for Mjolnir on the right (mirrored when it is on the left), with
  anticipation (ticks 0-2), the snap (4), follow-through (7), recovery (13 / 16); hips twist (bodyY) and HipBend.
  First person: per-hand tables (`FP_D*`), `ItemInHandRendererComboMixin` now also calls the off hand while dual.
- 4th hit in the window -> `ThorTwinThunder.queue`.

### Twin Thunder Strike -- `power/ThorTwinThunder`, client `thor/ThorTwinFrames` + `ThorFxRenderer.twinThunder`
- Only while dual wielding, cooldown `twinThunder.cooldownTicks` 400 (20 s, `ThorAbility.TWIN_THUNDER`, started when it
  queues; the action bar says when it is still recharging). Starts `DELAY` 12 ticks after the 4th hit (the hop lands),
  synced as `ThorFx.ANIM_TWIN_THUNDER`: tick 0 raise, 8 a visual bolt onto each weapon head + arcs between them, 16 the
  slam (`twinThunder.damage` 20 within `radius` 6, knock-up 0.7 / back 0.9, bosses not moved, debris rings, screen shake
  via `TitanShakePayload` within 24 blocks, glowing cracks + shockwave client-side), 19 a ring of `ringBolts` 8 bolts
  at `ringRadius` 6.5, each hitting hostiles within `ringSplash` 2 for `ringDamage` 8. Never the wielder, pets, squad
  allies or their pets (`WeaponCombo.friendly`); the storm buff applies via the hook. Static `PENDING` map, ticked on
  END_SERVER_TICK, cleared by `ServerStateReset`.

### Thunder Field (Shift+G) -- `power/ThunderField`
- Binding: Shift+G (plain G stays Lightning Strike; the Shift variant was free). Throws the main-hand Thor weapon (else
  the off-hand one) at the aimed ground within `thunderField.range` 24 (a block-clip along the look; a wall / ceiling hit
  drops to the ground at its foot; nothing in range = action-bar message, no cooldown). Re-uses the real
  `MjolnirEntity` / `StormbreakerEntity` throw, re-aimed at the spot.
- Landing (Mjolnir leaves THROWN / Stormbreaker turns home, or 40 ticks): the weapon is pinned there for the field's
  life (`pinAt`, a synced `DATA_PINNED` on both entities; Stormbreaker renders blade-down in the ground), a visual bolt
  hits it, and the field forms: radius 5, 80 ticks; every 10 ticks hostiles (rule 2) within it (and within 2 blocks
  vertically) take `pulseDamage` 4 (+ storm) and Slowness II. No fire, no block damage. Then the weapon flies home as a
  thrown one always does; the call key pulls it out at once. Cooldown 300 (15 s, `ThorAbility.THUNDER_FIELD`), shown as
  an amber bar along the bottom of the G box.
- Look: `ThorFx.field*` (separate from the one-shot move slot) -> `ThorFxRenderer.thunderField`: glowing disc + rim,
  arcs crawling over the ground, arcs from the weapon to the rim, short bolts out of the air.

### Stormbreaker model rebuild -- `scratchpad/gen_v01419_stormbreaker_assets.js` (edit the generator, not the outputs)
- 76 boxes instead of 12: a crescent blade built row by row (half-pixel rows, each a dark Uru body + lighter ground
  bevel + thin bright cutting edge, so the edge arcs, the horn sweeps up, the beard droops and the back curves away
  from the haft), a rune strip on the blade and a rune band on the head, a stepped hammer poll with a polished face,
  a wandering, knotted Groot-branch haft, alternating leather wraps, a collar and a capped pommel. Block-projected UVs
  so grain / wraps / brushing run continuously across the stepped boxes. Textures now 32x32 (new
  `stormbreaker_bevel`). Same bounding convention and display transforms as before; `stormbreaker_thrown.json` is the
  same geometry unrotated. A pinned (Thunder Field) axe renders blade-first in the ground (`PINNED_LIFT` 0.66).

### HUD -- `client/gui/ThorHud.renderStatusRow`
- Above the keys: 4 combo pips while dual wielding (gold up to the landed step; grey + seconds while Twin Thunder
  recharges) and "Storm-Charged +30%" on the right (a line higher if it would overlap the pips).

### Tests -- `ThorV0161GameTests`
- storm buff only while thundering and only once (ability hit, melee hit, finisher ratio, non-Thor unaffected); dual
  combo 4 steps / window / reset / back to single; Twin Thunder only on the 4th hit in the window and never with one
  weapon; it hits an enemy for 20 but never a squad ally or pet; Thunder Field shocks + slows an enemy, not a pet or a
  squad ally, then expires; the Shift+G throw pins Mjolnir, forms a field and comes home.

## v0.15.3: N weapon selector, Stormbreaker on the ground

### N weapon selector -- `hammer/ThorWeaponSelection`, client `gui/ThorWeaponScreen`
- N opens a small screen (gold on night blue, like the Bifrost menu): one row per weapon -- icon, name, "Bound to you" /
  "None bound to you", and an Active / Inactive toggle (tooltip says what a click does). Intro and the both-off warning
  are word-wrapped to the panel (`font.split`). N again (or Done / Esc) closes it.
- Client routing: an `else if` in `ProjectHeroModClient.handleMaxSteelTransform`, just before the mutation Utility 2
  fallback, gated on `ThorWeaponSelection.ownsSelector` = worthy and (holding Mjolnir/Stormbreaker, or bound to
  Mjolnir, or bound to a Stormbreaker) -- the same rule on both sides. Nothing else owned N for Thor: the server's
  `AbilityRouter` gives Thor's context slots 1-6 only and dropped slot 8, and `mutationHasUtility` is already off while
  a Thor weapon is held.
- State: `ModAttachments.THOR_WEAPONS_INACTIVE` -- an int, bit `1 << ThorWeapon.ordinal()` set = that weapon INACTIVE;
  0 (default) = both active = the v0.15.1 rule. Persistent, `copyOnDeath`, synced to the owner (the screen reads it,
  so it always shows the server's state). No static state.
- Writes: the toggle sends `ThorWeaponTogglePayload(weapon ordinal, active)`; `ThorWeaponSelection.handleToggle`
  re-validates (index in range, `ownsSelector`) and answers on the action bar.
- Recall (`MjolnirRecall.call` / `chooseWeapon`): both inactive -> nothing is called, action bar
  `message.projecthero.recall.none_active`, `call` returns false. One inactive -> it is never called (not even when it
  is the closer or the only bound one); the other is treated as the only weapon (v0.15.1 rule 3: already in hand /
  swap from the pack / called home). R is the only weapon-calling path -- Thor's Armour (H) calls no weapon.

### Stormbreaker on the ground -- `StormbreakerEntity` resting state
- Like Mjolnir: a loose Stormbreaker `ItemEntity` is promoted on its first tick (`ItemEntityMixin
  #promoteToStormbreakerEntity`, replacing v0.15.1's "stay an item but track it") into a RESTING
  `StormbreakerEntity` carrying the real stack and the item's toss velocity -- Q, dragging it out of the inventory, death
  drops, dispensers, and a freshly forged axe (the forge runs earlier in the same tick). The /give decoration
  (`NEVER_PICK_UP`) is left alone; a stale ghost is just discarded.
- Also resting, as the SAME entity: a throw whose thrower is gone (offline / dead / another dimension; it used to become
  an item) and a non-recall catch with a full pack (waits at the owner's feet).
- Resting (`DATA_RESTING`, synced, saved as `Resting` + `PickupDelay`): gravity 0.055 (Mjolnir's), stops dead on the
  floor (`landOn` sets it exactly on the hit surface; walls only kill sideways speed), floats up out of lava (negative
  gravity, rise capped at 0.04/tick) so a forged axe can be fished out; heading held steady; no flight effects; hits
  nothing. Never despawns (a projectile) or burns (fire-immune type).
- Pickup: right-click only (`interact`, `isPickable` while resting), after a 40-tick grace, and only for
  `StormbreakerEntity.canLift` = creative, or worthy and not the Hulk (Mjolnir's rule minus Hero of the Village
  ascension); anyone else gets Mjolnir's throttled clang. Main hand if free, else the pack, else it stays. An unworthy
  player still can't grab the item form during the one tick before promotion (`ItemEntityMixin#blockUnworthyPickup`).
  Holding/swinging it is unchanged: anyone may still carry Stormbreaker that reaches them some other way (a chest).
- Tracking/anti-dupe: registry status `RESTING`, entity id recorded on its first tick; stale copies discard themselves
  before registering (as before); R (`MjolnirRecall.answerLoaded`) calls `recall` on the resting entity itself, which
  clears resting and flies the same entity home.
- Render (`StormbreakerEntityRenderer`): resting = lying flat on its side along the landing yaw (model +Y along the yaw,
  blade face up, lifted by half the model's 2.8 px thickness at the 1.15 FIXED scale).
- Tests: `ThorWeaponSelectV0153GameTests` (toggle default/persist/validation; inactive never called even when closer;
  inactive only-bound weapon stays put; both inactive -> nothing; Q-drop becomes the entity, registry RESTING, unworthy
  can't lift, save/load, worthy can; dropped axe recalled home as the same entity; ghost resting axe deletes itself).
  `StormbreakerGameTests#anOrphanedAxeLiesDownAsItselfAndStaysPut` and `ThorRecallV0151GameTests` updated for the
  entity. Lang: `scratchpad/lang_v0153_thor.js`.

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
