# Green Lantern Hero-Tier power (v0.11.7)

A bonded Power Ring, Ring Charge (0-10,000), controlled flight, hard-light constructs, ranged
combat and shielding. Package: `com.projecthero.mod.greenlantern` (+ `.data`, `.item`, `.block`,
`.construct`, `.worldgen`). Modelled on Max Steel's file layout (the most structurally complete
existing Hero-Tier power) — see `MAXSTEEL_REFERENCE.md` for the sibling doc.

## Slot mapping (deviates from a literal R/G/H/Z/X/C reading)

The mod has exactly six universal ability slots — `AbilitySlot`: R (Primary/slot 1), G
(Secondary/2), **X** (Movement/3), **Z** (Ultimate/4), **V** (Utility-Control/5), C (Special
Mode/6). `H` is reserved mod-wide for the experimental power-select wheel and is not one of the
six slots, so it is not available to any Hero-Tier power. Green Lantern's kit is remapped onto the
real six slots by role:

| Slot | Tap | Shift |
|---|---|---|
| R | Ring Bolt | Continuous Beam (held) |
| G | Construct Fist | War Hammer Slam |
| X (Movement) | hold: recite the Oath -- "Green Lantern's Light!" empowerment mode (v0.11.7) | — |
| Z (Ultimate) | Directional Shield (held) | Protective Dome |
| V (Utility/Control) | Suit Up/Down | Ring Scan |
| C (Special Mode) | Deploy selected construct (Rescue Tether: grab, or throw if already holding; Mining Drill/Energy Blade/Carry Platform: toggle on/off if already equipped) | hold ≥0.35s, release = cycle to next construct; **Shift+C** = dismiss all, or set down a Rescue Tether hold safely if one is active |

**v0.11.5: Ring Flight moved off the X slot** to a double-tap of the vanilla jump key while airborne
(mirroring Thor/Iron Man's own double-tap-jump gesture -- see `ProjectHeroModClient#handleDoubleJump`
and the C2S `GreenLanternActionPayload`), toggling on or off either way; boost is still read live from
sprint-holding while flying, no separate key. **v0.11.7 replaced X's Ring Grapple outright** with a new
Oath empowerment mode (`GreenLanternOath` -- the `GreenLanternGrapple` class is deleted) -- see the
changelog entry below for the full mechanic.

## Core classes

| Role | Class |
|---|---|
| Static API (bond/revoke/cooldowns/lifecycle) | `GreenLantern` |
| Balance constants | `GreenLanternConfig` |
| Persistent state (7-field codec, under the 16-field ceiling) | `data/GreenLanternState` |
| Ring Charge resource | `GreenLanternEnergy` |
| Slot dispatch + per-player tick (+ suit upkeep drain, v0.11.5) | `GreenLanternAbilityManager` |
| Flight (cloned from `MaxSteelFlight`'s velocity model; toggled via double-tap-jump, v0.11.5) | `GreenLanternFlight` |
| Ring Bolt / Beam / Fist / Hammer | `GreenLanternCombat` |
| "Green Lantern's Light!" Oath empowerment mode (v0.11.7, X slot, replaces Ring Grapple) | `GreenLanternOath` |
| Directional Shield / Protective Dome | `GreenLanternShield` (state) + `GreenLanternDamage` (absorption hook) |
| Ring Scan | `GreenLanternScan` |
| Suit Up/Down animation | `GreenLanternSuit` |
| Power Battery Oath recitation | `GreenLanternBattery` |
| Will Trial (waves/fail/cooldown) | `GreenLanternTrial` |
| Construct framework | `construct/Construct`, `construct/ConstructType`, `construct/GreenLanternConstructs` |
| Suit items/armour | `item/GreenLanternItems`, `item/GreenLanternArmorItem`, `item/GreenLanternSuitArmor` |
| Battery/pedestal blocks | `block/GreenLanternBlocks`, `block/PowerBatteryBlock`, `block/FallenLanternPedestalBlock` |
| Fallen Lantern Site worldgen | `worldgen/FallenLanternSiteStructure`, `worldgen/FallenLanternSitePiece` |
| Admin/testing command | `command/GreenLanternCommand` (`/projecthero greenlantern ...`) |
| HUD | `client/gui/GreenLanternHud` |
| Gametest coverage | `gametest/GreenLanternGameTests` |

Wired into `HeroTiers` (cross-tier exclusivity), `HeroCommand.HERO_TIER_KEYS`, `AbilityRouter`,
`ProjectHeroMod` (init + death/respawn/join/world-change/disconnect lifecycle + tick loop),
`ServerStateReset` (session-state clears), `ModAttachments`, `ModCreativeTab`, `HeroPackGuide`
(chapter `CH_GREEN_LANTERN`), `PowerInfoScreen`.

## v0.11.7 changelog

A large single-message batch covering flight, the X slot, the dome, low-charge feedback, the Power
Battery model and every construct's cost/behaviour. Full detail belongs here rather than repeated per
change below:

- **Flight**: a green hard-light trail now shows any time Ring Flight is engaged and moving (was
  Boost-only; Boost still thickens it), and flight now ends automatically on landing -- `GreenLanternFlight`
  tracks a per-player liftoff-grace timestamp (`FLIGHT_START`, 10-tick grace, same idea Thor's own flight
  uses) and auto-`forceStop`s once `player.onGround()` reports true past that grace window.
- **New passive: Resistance I**, flat and permanent for any bonded Green Lantern, suited or not (reapplied
  every 5s on a short hidden effect from `GreenLanternAbilityManager#serverTick` so it never actually
  expires) -- not suit-gated, matching the existing unsuited-still-works convention (fall immunity).
- **X slot fully replaced**: Ring Grapple is gone (`GreenLanternGrapple` deleted) in favour of
  **"Green Lantern's Light!"** (`GreenLanternOath`) -- hold X to recite the same four Oath lines/cadence
  the Power Battery's own recharge ritual uses (reused, not duplicated text); release before the fourth
  line and it's cancelled outright, free, no cooldown. A completed recitation empowers the caster for
  `OATH_MODE_DURATION_TICKS` (22s): double melee (a transient `ADD_MULTIPLIED_TOTAL` attribute modifier),
  double ability damage (`GreenLanternCombat`'s Bolt/Beam/Fist/Hammer and the constructs' Turret/Battering
  Ram damage all read `GreenLanternOath.multiplier`), and **every Ring Charge cost doubled** -- done at the
  single choke point (`GreenLanternEnergy#spend` multiplies by `GreenLanternOath.multiplier` itself, so no
  individual ability/construct call site needed touching), plus its own flat `OATH_MODE_UPKEEP_PER_SEC`
  (10/sec, paid via the new `GreenLanternEnergy#spendRaw` so it isn't itself doubled by the multiplier it
  causes). Ends on its own timer or the instant its own upkeep can't be paid, either way applying
  `OATH_MODE_COOLDOWN_TICKS` (80s). Green particles surround the caster the whole time; the X HUD box shows
  a live countdown while active (green overlay, not the ordinary dark cooldown tint) or "..." while
  reciting. State lives in two new synced, non-persisted attachments (`GREEN_LANTERN_OATH_UNTIL`/
  `_RECITING_SINCE`) -- same live-source-of-truth pattern the barrier HP attachment already used, so the
  client-side HUD needs no extra payload.
- **Protective Dome reworked**: radius 5 -> 10, now animates outward from the caster over
  `DOME_EXPAND_TICKS` (1.5s) instead of appearing at full size instantly (`GreenLanternShield#currentDomeRadius`,
  driven by a new per-owner `DOME_DEPLOY_TICK` map), upkeep cut 20 -> 5/sec, and the outline now draws with
  24 points per ring instead of 14. **New: it's a real exclusion zone** -- every tick,
  `GreenLanternShield#pushOutNonSquad` knocks anyone within the current (possibly still-expanding) radius
  outward unless they're the owner or a squadmate (`SquadManager#squadOf`/`Squad#has`), which is "push out
  nearby entities as it expands" during the growth window and keeps outsiders from walking back in for the
  rest of the dome's lifetime. Damage-absorption behaviour itself is unchanged (still caster-only).
- **Low Ring Charge feedback overhauled**: eight escalating thresholds (40/35/30/25/20/15/10/5%, was three)
  in `GreenLanternConfig#LOW_CHARGE_WARN_THRESHOLDS`; `GreenLanternEnergy#triggerLowChargeFeedback` finds
  the most severe one crossed since the last check and fires a colour-ramped (yellow -> gold -> bold red)
  action-bar message plus a pitch/volume-ramped notification sound. The HUD's own charge-bar pulse (was a
  single hardcoded <=10% cutoff) now reacts to `GreenLanternEnergy#severityTier` and speeds up as charge
  drops further -- that continuous pulse is the "flash above the hotbar," rather than the server trying to
  schedule a multi-tick blink itself.
- **Power Battery model reshaped** into an actual lantern silhouette (base/glowing core/cap/carrying-handle
  loop, 6 elements, ~20 units tall vs. a normal block's 16) instead of a plain `cube_all`, per an explicit
  "just a green lantern but slightly bigger" request -- new `power_battery_frame.png` texture for the
  base/cap/handle, core texture cleaned up to a plain glow (the bordered-square look moved to being a
  separate element instead of baked into one flat texture). Purely visual; no collision-shape change (the
  handle loop overhangs above the block's ordinary full-cube hitbox, the same simplification vanilla
  lanterns/chains/campfires accept for their own overhangs).
- **Every construct renumbered again** (a full pass, not a proportional cut like v0.11.4's):
  - **The generic weighted-slot cap is gone outright** (`CONSTRUCT_MAX_SLOTS` deleted) -- explicit "remove
    construct limit" request. `ConstructType#slotWeight()` is still tracked/reported, nothing compares it
    to a ceiling any more.
  - **Mining Drill, Energy Blade and Carry Platform are now toggle constructs**: deploying equips them for
    free (0 cost) with no effect yet; a second C-press on the caster's own already-active instance
    (`GreenLanternConstructs#toggleConstruct`, dispatched via a `TOGGLE_TYPES` check at the top of
    `#deploy`) flips `Construct#toggledOn`, a third press flips it back. Only while on do they actually cost
    anything (`#tickUpkeep` skips Drill/Blade upkeep while off) or do anything (Drill only mines while on,
    with green particles spiralling the mining hand; Blade only grants its melee attribute bonus while on,
    with a periodic green arm-glow particle; Carry Platform only steers toward the owner while on --
    `#tickCarryPlatform` early-returns otherwise, leaving it parked). Upkeep while on: Drill/Blade 2/sec each.
  - **Sentry Turret**: cost 170->20, upkeep 9->1/sec, a new explicit **`TURRET_MAX_LIVE`=10** hard per-player
    cap (separate from the now-deleted generic cap -- checked in `#deploy` before the cooldown check), and a
    standing green particle "rod" now marks its anchor at all times (`#tickTurret`, every 4 ticks) rather
    than only showing a beam when it actually fires. (Investigated the "shouldn't deploy from the wheel"
    part of this request -- static analysis found the v0.11.2 wheel-hold race this would describe is
    already fully closed by `ProjectHeroModClient`'s `glWheelOpenedThisHold` guard; no reproducible bug
    found, so nothing was changed there.)
  - **Containment Cage**: cost 120->20, upkeep 5->1/sec, range 18->30. **Now sized to the target**
    (`GreenLanternConstructs#spawnCage`) -- anything spider/cow/sheep-sized or smaller (bounding-box width
    <=1.5, i.e. essentially every overworld mob) gets a tight 2x2 footprint of solid walls at body height
    instead of the old fixed 3x3; bigger targets keep the original 3x3 perimeter-with-open-corners shape.
    **Flying targets get fully sealed top and bottom** (`#isFlyingMob`: no-gravity entities, Vex, Ghast,
    Phantom, Bat, Parrot, Bee) instead of the small open gap a ground-bound mob doesn't need closed.
  - **Battering Ram**: cost 130->20.
  - **Hard-Light Wall**: cost 100->20, upkeep 8->1/sec, height 3->4 blocks (`WALL_HEIGHT`).
  - **Platform**: cost 60->20, upkeep 4->1/sec, footprint 3x3->4x4 (`PLATFORM_SIZE`), and its own
    30-block placement range (`PLATFORM_RANGE`, was the generic 24) -- landing on the ground within range
    (the existing raycast-hit-a-block fallback of `placementPoint`) already made it spawn flush on terrain
    rather than floating, so no separate "spawn on the ground" branch was needed.
  - **Bridge**: cost/upkeep flattened to a fixed 20/1/sec (was a variable-length, per-segment-scaling cost)
    at a fixed 20 long x 3 wide (`BRIDGE_LENGTH`/`BRIDGE_WIDTH`) -- the old aim-at-a-target length is gone.
  - **Stair/Ramp**: cost/upkeep flattened to 20/1/sec, 3 blocks wide (`RAMP_WIDTH`).
  - **Lantern Light**: cost 20->1, upkeep 1/sec->1 every 5 seconds (`LANTERN_LIGHT_UPKEEP_PER_SEC`=0.2 --
    Ring Charge is a float pool with no truncation risk, unlike the mod's integer XP systems, so a
    sub-1-per-tick upkeep needed no special carried-remainder handling here).
  - **Atmosphere Bubble**: radius 2.5->8 ("covers a small room"); its particle outline was already there
    from the general deploy-glow pass, unchanged.
  - **Rescue Tether**: cost 40->20, range 24->30, and **the hold itself now costs 1/sec to maintain**
    (`TETHER_UPKEEP_PER_SEC`, drained in `#tickRescueHeld`) -- an unpayable upkeep releases the target the
    same safe way Shift+C does, rather than dropping them outright.
  - **Carry Platform**: cost 120->20, upkeep 6->1/sec (unconditional -- it's still occupying world blocks
    whether or not "drive" is toggled on), and **now spawns level with the owner's own Y** instead of one
    block below (both `#spawnPlatform`'s carry branch and `#tickCarryPlatform`'s re-centring dropped their
    `.below()`).
  - **Hard-Light Tool Kit**: gained a fourth piece, a Hard-Light Flint and Steel (`TOOL_KIT_BASES`/
    `TOOL_KIT_NAME_KEYS` both grew by one; the piece-count-ends-the-kit check already read
    `TOOL_KIT_BASES.length` generically, so no separate "4" needed hardcoding anywhere else).
- Gametest coverage: `GreenLanternGameTests` gained oath-mode press/release/activation/cost-doubling
  coverage, a toggle-construct test (Mining Drill's `toggledOn` flips across three presses, never spawning
  a second instance), an Energy Blade melee-bonus-only-while-on test, a dome-radius-grows-over-time test,
  and a Sentry Turret live-cap-refusal test; the old fixed weighted-slot-cap test was replaced with one
  proving deploys past the old 20-slot ceiling now succeed, and every tool-kit test/helper updated for 4
  pieces instead of 3. Build green, 282 gametests pass. **UNVERIFIED in-game** (usual standing limitation,
  no live server here) -- the Oath mode's feel/balance, the dome's push-out radius and squad-filtering in a
  real multi-player scenario, every construct's new dimensions/costs, the lantern block model's actual
  in-game silhouette, and the escalating low-charge audio/visual cues.

## Deliberate simplifications (v1)

Each of these is a considered scope cut, not an oversight — flagged here so a future pass knows
exactly what to revisit:

- **No custom projectile/turret entities.** Ring Bolt, Continuous Beam and Construct Fist are
  server-authoritative hit-scans (raycast + instant damage + a particle trail), the same pattern
  most of the mod's existing ranged abilities already use (e.g. Shadow Bolt). Sentry Turret is a
  ticked anchor point (no spawned entity, no AI, no renderer) that raycasts and fires on an
  interval. This avoids needing a new `EntityType` + client renderer registration for a first pass;
  gameplay-wise the only loss is a dodgeable travel time.
- **~~Construct selection is cycle-on-hold, not a radial wheel.~~ Superseded in v0.11.2**: holding C
  ≥0.5s (`CONSTRUCT_WHEEL_HOLD_TICKS`) now opens `GreenLanternConstructWheelScreen`, a real radial menu
  (modelled on `PowerWheelScreen`) listing every `ConstructType` (all of them, unconditionally since
  v0.11.5 removed the Mastery unlock gate); picking a wedge
  sends the dedicated `GreenLanternConstructSelectPayload` C2S packet, re-validated server-side in
  `GreenLanternAbilityManager#selectConstruct`. A quick tap of C still deploys the current selection
  directly, unchanged. The server-side hold-then-cycle fallback this replaced has been removed --
  `handleAbilitySix` now just deploys on a short release and no-ops on a long one (the wheel owns that
  interaction). `CONSTRUCT_MAX_SLOTS` raised 6 -> 20 in the same pass.
- **Directional Shield / Protective Dome do not physically block entity movement.** They intercept
  damage (redirecting it into the barrier's HP pool via the same cancel-and-reapply-smaller pattern
  every Hero-Tier power in this mod uses for `ALLOW_DAMAGE`) rather than giving hostiles a real
  collision shape to bounce off. A player standing inside a dome can still be melee-reached by
  something that clips in, even though the hit itself is absorbed. True collision would need a
  custom `VoxelShape`/entity-pushback system.
- **~~Suit renders on the shared `crimson_vanguard` GeckoLib geometry~~ Superseded in v0.11.2**: ships
  its own `geo/green_lantern.geo.json` (converted from the user-supplied model -- bones renamed to
  GeckoLib's armour convention, baked rest rotations zeroed like every other set in the mod, two empty
  `armorRightBoot`/`armorLeftBoot` placeholder bones added since the source model has none) with its
  own real, hand-painted `textures/armor/green_lantern.png`, exactly like the Iron Man marks did when
  they moved off the shared geometry. Still points at `SHARED_ANIMATION` since the geo reuses
  `crimson_vanguard`'s top-level bone names. No dedicated boot geometry -- the leg cubes already reach
  the floor, so a Green Lantern wearing only boots (no leggings) shows nothing extra, just no visible
  gap either. **No helmet geometry either (v0.11.3, by design)** -- the suit has no helmet, so
  `armorHead` is an empty top-level bone with no cubes (same pattern as the boot bones), and
  `PlayerModelMixin#helmetRetracted` treats any `GreenLanternArmorItem` in the HEAD slot as permanently
  "retracted" so the wearer's own hat/hair second-layer shows through instead of the usual
  hide-the-skin-overlay-under-armour behaviour every other set gets.
- **Mining Drill mines through the normal survival block-break path**
  (`ServerPlayerGameMode.destroyBlock`), so it fully respects claims/protections and can never touch
  bedrock/unbreakable blocks — but it inherits the currently-held item's harvest tier rather than
  always granting diamond-equivalent drops, and it does not implement the per-material break-speed
  timing table from the spec (it breaks on the normal per-tick check interval instead).
- **Battering Ram and Rescue Tether are both `ConstructType.Kind.INSTANT`**, not persisted,
  slot-costing constructs -- dispatched from `GreenLanternConstructs#deploy` before the normal construct
  pipeline, never entering `BY_OWNER`. Battering Ram avoids any block-breaking/claim-bypass risk and
  stays a true one-shot. Rescue Tether went through two reworks: v0.11.5 turned it from a standing,
  upkeep-costing pull-over-time construct into a single instant grab (mirroring
  `SymbioteTendrils#pull`'s heavy-vs-light handling); **v0.11.6 turned that instant grab into an actual
  hold**, per an explicit "press c and pick up the target, they can press c again to throw the target or
  shift+C to let them down safely" request. The state now lives in `GreenLanternConstructs#RESCUE_HELD`
  (a `Map<UUID, Integer>` of owner -> held entity id, separate from `BY_OWNER` since a hold still isn't a
  standing construct): the first C press raycasts and validates the target with the same
  `AbilityHelpers#isValidGrabTarget` filter every other Hero-Tier grab move uses (no armour stands, no
  boss-tier health, real players gated behind `HeroConfig#abilityHardCrowdControlOnPlayers` -- a
  persisted carry is a much stronger hold on a player than the old one-tick yank ever was, so it now
  gets the same hard-CC gate Super Strength/Telekinesis/Elasticity's grabs already respect) and glues it
  in front of the caster's eyes every tick (`#tickRescueHeld`, called from
  `GreenLanternAbilityManager#serverTick`, re-implementing `GrabHelper#tick`'s reposition-every-tick
  approach since Green Lantern keeps its own state rather than an experimental power's `AbilityContext`
  resource slots); a second C press throws it (`#throwRescueHeld`); Shift+C sets it down safely instead
  of the ordinary dismiss-all (`GreenLanternAbilityManager#handleAbilitySix` checks
  `#releaseRescueHeldSafely` first) -- zero velocity, `fallDistance` reset, and a brief Slow Falling if
  still airborne, so "safely" holds even released off a ledge. A hold that outlives its owner (death,
  respawn, logout, dimension change, power loss) is released the same safe way from
  `GreenLantern#clearTransient`, and a held target too far away or that dies is dropped automatically
  by `#tickRescueHeld`.
- **Carry Platform follows its owner and can carry passengers (v0.11.5)**, resolving the prior
  limitation here. It's a fixed 3x3 footprint (not rotated to the owner's facing -- a symmetric square
  is rotation-invariant, so `GreenLanternConstructs#carryPlatformCells` just offsets ±1 on both
  horizontal axes from a moving center) spawned 5 blocks in front of the owner at their own foot height.
  Each tick it steers a continuous `Construct#platformCenter` toward a live "5 blocks ahead of the
  owner's look" target, capped at `CARRY_PLATFORM_SPEED_CAP_BPS`; the actual blocks (and any riders)
  only move when that continuous position crosses into a new block, via a restore-then-replace of all
  nine cells rather than any real sliding-block physics. Passengers ride one invisible, unkillable
  `ArmorStand` "seat" per cell (`spawnCarryPlatformSeats`) -- right-clicking an unoccupied cell
  (`UseBlockCallback`, `GreenLanternConstructs#trySeat`) mounts the clicking player on that cell's seat,
  and the seat is simply teleported by the same delta as the platform moves; passenger repositioning
  itself is entirely vanilla's own generic "a rider follows whatever it's riding" behaviour, not
  anything this mod implements. Two known edge cases from this simplicity: (1) a standing-but-not-
  seated player on top of the platform is NOT carried along (the blocks teleport out from under them
  the instant the platform moves, same as any other block-based construct in this mod) -- only a seated
  rider is guaranteed to travel with it; (2) if a cell's new position is ever blocked (another player,
  a container, bedrock, etc.), `add()` silently skips that one cell, which can leave `Construct#cells`
  shorter than `Construct#seatEntityIds` until the platform moves somewhere unobstructed again -- a
  minor, self-healing index mismatch, not a crash risk (`trySeat` bounds-checks the index).
- **Will Trial enemies are vanilla mobs** (Zombie/Skeleton, boosted health) plus a boosted, renamed
  Vex as the "Fear Echo" — not bespoke models.
- **Fallen Lantern Site** is a procedurally-carved single `StructurePiece` (clone of
  `SteelCrashSitePiece`'s crater approach), not a hand-built NBT template — it follows real terrain
  everywhere but is visually simple (a blackstone/deepslate bowl with a small dais and loot chest).
- **Item/block textures are placeholder, programmatically generated** (`scratchpad/gen_greenlantern_textures.js`,
  the project's established hand-rolled-PNG-via-Node-zlib approach) — flat colour blocks, not final
  art. The suit texture is the one exception (see above): real, hand-painted art supplied by the user
  in v0.11.2, used byte-for-byte.
- **Ring Bolt/Continuous Beam originate from an offset hand point, not the eye** (v0.11.2,
  `GreenLanternCombat#handOrigin`) — a purely cosmetic change to where the drawn particle line starts;
  the underlying raycast (and therefore what the bolt/beam actually hits) is still eye-based, so it
  keeps landing exactly on the crosshair. Construct Fist / War Hammer Slam gained a shaped green
  particle "model" (a wireframe cube / mallet silhouette via `AbilityHelpers#line`) the same pass,
  still no spawned entity or GeckoLib mesh -- see the "no custom projectile entities" simplification
  above, which this stays consistent with. The Directional Shield is a client-only rendered
  green-tinted vanilla shield tracking the wearer's look direction (`GreenLanternShieldRenderer`, no
  entity); the Protective Dome instead gets a green particle boundary outline that re-emits itself
  while the dome is up, and can now be dismissed early with a second Shift+Z -- which applies half of
  `DOME_COOLDOWN_TICKS` (`GreenLanternShield#dismissDomeVoluntarily`) so a dome can't be soaked down and
  redeployed at full HP for free; the lifecycle-cleanup path (`GreenLanternShield#dismissAll`, used on
  death/power-loss) deliberately does not apply that cooldown.
- **The Oath's "any input cancels it" is a heuristic, not real key-press detection** (v0.11.4,
  `GreenLanternBattery#movedOrTurned`) -- no raw server-side input event exists for this, so the oath
  instead cancels on a position/look-angle drift past a small epsilon each tick, plus the existing
  per-ability `onAbilityUsed`/`onDamaged` hooks every Green Lantern ability already calls. This mirrors
  the old channel's own interrupt conditions (damage/movement/ability-use/logout), just re-purposed for
  a fixed recitation instead of a walk-and-wait loop. Passive Ring Charge regen is gone entirely --
  `GreenLanternEnergy#addCharge` (fired once, instantly, on oath completion) is now the only source of
  charge.
- **The oath text is the classic Green Lantern Oath** (`message.projecthero.green_lantern.oath.line1-4`),
  per an explicit user request -- the opening line was already shipped as `message...welcome` since an
  earlier version.
- **Hard-Light Tool Kit dismissal is tick-polled, not event-driven** (v0.11.4,
  `GreenLanternConstructs#tickKind`'s `TOOL_KIT` case/`countToolKitPieces`) -- rather than a new
  drop-event/mixin, the granted diamond pickaxe/axe/shovel are tagged with a `CustomData` marker and
  the construct checks each server tick whether all three are still somewhere in the owner's inventory
  (including the container-menu cursor, so mid-drag inventory rearranging isn't misread as a drop);
  losing any one ends the whole kit and calls `deleteLooseToolKitPieces`, which also runs unconditionally
  every player-tick from `GreenLanternAbilityManager` whenever that player has no live Tool Kit -- so a
  piece dropped, stored in a chest, or left over from an ended kit is deleted the moment it re-enters the
  player's inventory, rather than becoming a free permanent diamond tool. At most one Tool Kit may be
  live per player at a time (a second deploy is refused outright) specifically so this per-player piece
  count is never ambiguous between two simultaneous kits. Deploying with fewer than 3 free inventory
  slots is refused and refunded up front instead of dropping the overflow on the ground.
- **The Willpower Mastery I-IV progression system was removed outright (v0.11.5)**, per an explicit
  user request ("remove the progression mastery system make all constructs available from the start").
  `GreenLanternMastery` is deleted; `GreenLanternState` dropped `masteryLevel`, `totalEnergySpent`,
  `totalDamageBlocked`, `totalFlightDistance`, `nightStartTick`, `nightSurvived` and
  `bossDefeatedWhileBonded` (7 fields, down from 14 -- the codec's `optionalFieldOf` means an old save
  still holding those keys in its NBT just has them silently ignored, the same forward-compatible
  pattern the removed `last_ability_use_tick` field already established). `ConstructType#unlockedFor`
  and `#requiredMastery` are gone entirely rather than left as an always-true stub -- every construct is
  simply available the moment the ring bonds. Mastery IV's Efficient Focus (10% upkeep discount) went
  with it; no replacement discount was added.
- **Power Battery is now as breakable as dirt (v0.11.5)** (`strength(0.5f)`, no
  `requiresCorrectToolForDrops()`), per an explicit user request -- this deliberately reverses v0.11.4's
  blast-resistance raise to 1200, which existed only to guard against losing an unrecoverable recharge
  point to a creeper. That tradeoff (a placed battery is trivial to destroy, by explosion or otherwise)
  is now the player's own to manage.
- **Suit Up now costs 10 charge (was 100) plus 1 charge every 5 seconds while worn (v0.11.5)** --
  `GreenLanternAbilityManager#serverTick` drains it on a `player.tickCount % SUIT_UPKEEP_INTERVAL_TICKS`
  cadence and calls `GreenLanternSuit#forceSuitDown` (bypassing the mid-animation debounce and the
  oath-recharging refusal `toggle()` normally enforces) if the charge can't be paid, so an unpayable
  debt can't keep the suit on indefinitely.
- **Directional Shield / Protective Dome cost far less (v0.11.5)** -- Shield 250→45 initial /
  60→10 per-sec upkeep, Dome 900→160 initial / 120→20 per-sec upkeep. The Directional Shield is now
  also rendered for its own wielder, not just everyone else (`GreenLanternShieldRenderer` dropped the
  first-person skip that used to exist to avoid the shield rendering on top of the camera at the old,
  closer distance -- the shield already floats a full block out, `DISTANCE = 1.0`, so that concern
  didn't actually apply and the skip just meant a player couldn't see their own shield at all).
- **Ability-key HUD glow and cooldown display fixes (v0.11.5)** -- `GreenLanternHud`'s six boxes now
  glow (`BORDER_ACTIVE`, the same convention `MaxSteelHud`/`ThorHud` already use) while Z's Shield/Dome
  is up or V's suit is worn; R/G/Z each now show whichever of their tap/shift abilities' two separate
  cooldown keys (`ring_bolt`/`continuous_beam`, `construct_fist`/`war_hammer_slam`,
  `directional_shield`/`protective_dome`) is actually counting down, via `Math.max` of both -- previously
  only the tap half's key was checked, so e.g. War Hammer Slam's cooldown never showed on the G box at
  all; and C now shows the currently *selected* construct's own cooldown
  (`GreenLanternConstructs#cooldownRemainingFor`) instead of nothing. Deploying any construct now also
  emits a burst of green particles at the caster's hand (`GreenLanternConstructs#emitDeployGlow`) so
  marker-only constructs with no blocks of their own (turret/bubble/drill/energy blade/tool kit) still
  give some visible feedback, and every block-based construct's blocks are green stained glass instead
  of light-blue (`lightBlockState`) to actually read as this power's own hard light. A construct refused
  for being on cooldown now names the actual seconds remaining
  (`message.projecthero.green_lantern.construct_cooldown`) instead of a bare "COOLDOWN" string.
- **Ring Flight's double-tap-jump gesture was dead for every Green Lantern who wasn't also Tony
  Stark (v0.11.6 bug fix)** -- `ProjectHeroModClient#handleDoubleJump`'s Iron Man branch used to
  `return` outright when the player had no Tony Stark power, which skipped every check below it in the
  method, including Green Lantern's, further down. A Green Lantern with no Iron Man power (i.e. almost
  all of them) could therefore never trigger flight by double-tapping Space at all. Fixed by guarding
  the Iron Man branch in an `if` instead of an early `return`, so execution always falls through to the
  Green Lantern check. The gesture itself is unchanged: airborne, double-tap Space within
  `DOUBLE_JUMP_WINDOW_TICKS` (7 ticks) of the first press, toggles flight on or off either way.
- **No fall damage at all while the ring has any charge (v0.11.6)**, per an explicit user request --
  replaces the old suited-only, cost-and-cooldown-gated Emergency Catch passive outright (`emergency_
  catch`'s ability id, `EMERGENCY_CATCH_COST`/`EMERGENCY_CATCH_COOLDOWN_TICKS` are gone;
  `GreenLanternDamage#onAllowDamage` now just checks `GreenLanternEnergy#get(player) > 0f` against
  `DamageTypeTags#IS_FALL` and vetoes the damage, free and not suit-gated like every other ring power).
  `GreenLanternConfig#EMERGENCY_RESERVE` and `GreenLanternEnergy#spendEmergency` stay -- they're still
  the emergency flight descent's own reserved pool, unrelated to this.
- **War Hammer Slam's AoE radius raised 4.5 -> 10 blocks (v0.11.6)**, per an explicit user request --
  `GreenLanternConfig#HAMMER_RADIUS`, measured from the impact point 2 blocks in front of the caster
  exactly as before; damage/knockup/knockback figures are untouched.
- **Power Battery now has a loot table and actually drops itself when broken (v0.11.6 bug fix)** --
  it never had one, so breaking a placed battery (trivial since v0.11.5's strength drop to 0.5) simply
  destroyed it with nothing to pick back up. `data/projecthero/loot_table/blocks/power_battery.json`
  fixes that with a plain self-drop, the same pattern every other simple block in this mod uses (e.g.
  `stark_fabricator.json`).

## v0.11.14 -- Will Trial fixes, ring consumed, Primary power

- **Dome removed.** The green glass dome (`buildDome`/`restoreDome`) is gone: waves 2 and 3 were spawning on
  top of it (the dome sat on the heightmap the spawn point is snapped to), so the trial appeared to end after
  wave 1 and then jump straight to the prompt. The boundary is now purely particles
  (`GreenLanternTrial#boundaryParticles`), while `expelOutsiders` still pushes every entity except the
  attempting player and the trial's own mobs out of the 32-block radius, every 10 ticks. Trial mobs are
  also `setPersistenceRequired` so they can't despawn mid-wave.
- **"Are you afraid?"** is the only text on the prompt. **No** = not afraid = pedestal breaks, ring + core
  granted. **Yes** = afraid = the trial cancels (no ring, no cooldown, site open at once). The prompt times
  out after 3 minutes and counts as "afraid" so a dismissed screen can't hold the pedestal forever.
- **The ring is consumed** when right-clicked (`GreenLantern.bond` succeeds -> `stack.shrink(1)`).
- **Primary power.** Green Lantern is a Primary power: `GreenLantern.bond` calls `HeroTiers.claimPrimary`,
  which replaces any mutation / other hero power and removes a bonded Symbiote. The old
  `trial_ineligible` refusal is gone. Green Lantern now also appears in the squad roster
  (`HeroIdentity`).

## v0.12.20

- The Ring Charge readout under the ability row is a percentage (was `charge / 10000`).

## v0.13.21 -- constructs overhaul, suit sweep, directional flight, ring & battery models

Four explicit user requests in one pass.

### 1. Constructs look and work better

**Shared (every construct):**

- **New hard-light blocks** (`block/HardLightBlock`, `HardLightStairBlock`, `HardLightLampBlock`, registered in
  `GreenLanternBlocks`): translucent Lantern-green with a bright rim on every face, drawn full-bright
  (`emissiveRendering`), light level 7 (orb 15), occasional green motes. Never obtainable (no item, `noLootTable`),
  unbreakable by normal means (strength -1, so creepers can't hole a Wall either), piston-immovable, no mob spawns, no
  suffocation. `HardLightBlock.BRIGHT` is the paler Carry Platform variant. Replaces green/lime stained glass and the
  Sea Lantern. Translucent render layer set in the new `client/greenlantern/GreenLanternClient` (also sets it for the
  Power Battery).
- **Orphan cleanup**: each placed hard-light block schedules a tick every 40 ticks
  (`HardLightBlock.ORPHAN_CHECK_TICKS`) and removes itself unless `GreenLanternConstructs#isTrackedCell` says a live
  construct owns it -- so a crash/restart mid-construct, or a construct ending while its chunk was unloaded, can no
  longer leave blocks in the world forever (the stained glass could).
- `GreenLanternConstructs#CELL_INDEX` (dimension -> cell -> construct) replaces the full scans in the punch / break /
  seat callbacks; `placeCells` indexes a cell before placing it, `restore` un-indexes.
- **Build-out**: block constructs appear over a few ticks (`Construct#buildPerTick`, `#tickBuild`) with a spark per
  cell -- Wall 5 cells/tick (rises row by row, 4 ticks), Platform 4/tick from its middle outward, Bridge 6/tick (runs out
  from you, 10 ticks), Stair/Ramp 3/tick (one step per tick). Cage, Carry Platform and Lantern Light place instantly. A
  cell whose spot changed or that a player stepped into mid-build is skipped, not overwritten.
- **Deploy FX**: a spark beam traces from the ring hand to aimed constructs (Wall, Platform, Lantern Light, Turret,
  Cage) plus an amethyst chime at the construct.
- **Despawn FX**: every construct now dissolves in green sparks with an amethyst-break sound whenever it ends (expiry,
  upkeep, Shift+C, death...) -- it used to vanish silently unless punched down (`#end` / `#emitDissolve`).
- **Expiry warning**: timed constructs flicker over their last 3 s (`CONSTRUCT_EXPIRY_WARN_TICKS` = 60) with one soft
  chime when it starts.
- **Placement**: aimed constructs use `#targetCell` -- the air cell in front of the face you point at (or the cell at
  full range) instead of the raw hit point, which often resolved to the solid block itself. Wall and Turret then drop up
  to 4 blocks (`CONSTRUCT_GROUND_SNAP_BLOCKS`) onto the ground. `#add` now refuses any cell inside ANY player, the caster
  included (the caster used to be exempt -- a Wall aimed at your feet could entomb you).
- Punching a Wall/Cage cell now gives feedback (sparks + an amethyst hit whose pitch drops as its HP does).
- Carry Platform seat entities are removed if its placement fails (they used to leak).

**Per construct:**

| Construct | v0.13.21 change |
|---|---|
| Hard-Light Wall | hard-light blocks; on the ground under the aimed spot; rises row by row; hit feedback. |
| Platform | **fixed**: aimed at the ground it used to fail ("invalid target", every cell was inside the ground) -- it now forms on the aimed air cell's layer (a raised floor), spreading from its middle. **New catch**: falling and looking down past 40 degrees (`PLATFORM_CATCH_PITCH`) forms it right under your feet. |
| Bridge | hard light; runs out from your feet two rows a tick; cast in the air it starts directly under you. |
| Stair/Ramp | **real stair steps** (`hard_light_stairs`, facing away from you) you can walk up -- the old full-block staircase needed a jump every block; first step now at your feet one block ahead (it used to start inside the ground); builds one step a tick. |
| Lantern Light | v0.15.9: light from the ring itself (`GreenLanternRingLight`) -- an invisible `minecraft:light` (15) moved to the caster's head/feet cell as they move, ambient Night Vision (15 s, topped up), green glow at the ring hand; toggled (press again to put it out), no time limit, 1 + 0.2/s. Was a floating hard-light orb on the aimed air cell. |
| Containment Cage | a **closed** box sized to the target -- hollow 1-3 wide x 1-4 tall from its bounding box (`CAGE_MAX_INTERIOR_*`), floor/roof tried with the walls (solid ground skipped by `#add`), target snapped to the middle first so it never blocks a wall cell. Big mobs used to be shoved straight out of the fixed 1-wide hollow. Pulls an escaped target back in (e.g. enderman teleport) unless it is 12+ blocks away (cage ends); ends the moment the target dies (was ~20 ticks later). Target resolved before charging. |
| Sentry Turret | visible at last: a spinning hard-light core balanced on a corner, floating 1.25 blocks up (`TURRET_HOVER_HEIGHT`) on a stalk of light -- two vanilla `block_display` entities built from NBT (setters are private), tagged `projecthero_hard_light`; `#LIVE_DISPLAYS` + a `ServerEntityEvents.ENTITY_LOAD` hook discard orphans. **Only fires at targets it can see** (it shot through walls); green bolt with flashes both ends instead of a white end-rod line. |
| Battering Ram | a visible hard-light ram head (wire cube + trail) now **travels** from the ring hand along your aim at 2.5 blocks/tick (`RAM_SPEED_PER_TICK`) out to the same 16 blocks, smashing the first creature it passes within 0.9 blocks (`RAM_HIT_RADIUS`; same 12 damage / knockback) or bursting on the first solid block, opening a door there. Was an invisible instant 16-block hit-scan. `#RAM_ACTIVE` / `#tickRams`. Lunge unchanged. |
| Rescue Tether | a visible tether line from the hand to the held target every 2 ticks; the hold point is pulled in short of walls (targets used to be pushed into blocks and suffocate when you looked at a wall); sparks on grab and throw. |
| Atmosphere Bubble | **centred on the caster** (it was centred up to 24 blocks away on the aim point and often didn't cover you at all); also gives air to squadmates inside; shows its boundary as a shimmering Fibonacci-sphere shell every 0.5 s. |
| Energy Blade | a translucent full-bright blade (crossguard + 14 px blade) drawn on the fist in first and third person while on (`client/greenlantern/GreenLanternHandRenderer`), synced via the new `ModAttachments.GREEN_LANTERN_HAND_CONSTRUCTS` bitmask; the particle swirl is now an occasional glint. |
| Mining Drill | a stepped, spinning drill bit drawn on the fist while on (same path); sparks + whir when it actually mines. |
| Carry Platform | bright hard-light variant (was lime glass). |
| Hard-Light Tool Kit | pieces are unbreakable (a worn-out piece used to count as dropped and end the whole kit) and shimmer (enchantment glint). |

### 2. Suit-up: a top-to-bottom pixel-row sweep

- New shared helper `client/render/ArmorSweepReveal` (sibling of `SymbioteDissolve`, which is untouched -- Moon Knight's
  and the Symbiote's paths are unchanged). It parses the suit's Bedrock geometry (box UV and per-face UV, inflate, cube
  rotation about its pivot, parent-bone rest rotations), maps every texel of every face back onto the 3D face to get its
  height on the body, buckets heights into model-pixel rows counted down from the top of the suit, and builds one
  `DynamicTexture` per row (27 for the Green Lantern geo): frame k shows rows 0..k-1, with the newest row painted as a
  bright green scan line (`EDGE_ARGB`). Reusable for any `SuperheroArmorItem` set.
- `client/greenlantern/GreenLanternSuitReveal` reads the synced suit clock: 0 -> 1 over a suit-up, 1 -> 0 over a
  suit-down (dissolves feet -> shoulders), 1 suited, and 0 when worn but neither suited nor animating (so the suit never
  flashes on between the equipment and state packets). Hooked into `SuperheroArmorRenderer#getRenderType` and the
  first-person sleeve (`SuperheroFirstPersonArm#render`).
- Server (`GreenLanternSuit`): the armour is now equipped at the **start** of a suit-up (the renderer hides unreached
  rows) instead of popping on at the end; suit-down still strips at the end. `SUIT_UP_TICKS` 16 -> 30 (1.5 s) so the
  sweep is watchable. The particle ring now rides the sweep's edge (shoulders -> feet on, feet -> shoulders off).

### 3. Directional flight

- (v0.14.16: generalised into `client/flight/DirectionalFlight` + `mixin/DirectionalFlightTravelMixin`, which every
  flight in the mod now uses; GL's numbers are `flight/DirectionalFlightModel#greenLantern`.)
  `client/greenlantern/GreenLanternFlightClient` via the new client mixin `GreenLanternFlightTravelMixin` (HEAD of
  `Player#travel`, local player only -- Player's own creative-flight branch overwrites vertical velocity after the move,
  so it is replaced whole). Forward/back fly along the full 3D look vector; strafe is horizontal; Space/Sneak add
  straight up/down; no input eases to a dead hover. Velocity eases toward the wanted one (22% of the gap per tick
  steering, 18% braking -- `FLIGHT_ACCELERATION` / `FLIGHT_BRAKING`) from the class's own record of last tick's velocity,
  not `getDeltaMovement()` (vanilla's Space/Sneak nudges are already added to it), so there is no jitter.
- Speeds unchanged: `flyingSpeed x 10` blocks/tick (x2 sprinting) -- the server still sets `flyingSpeed` (0.06 cruise,
  Boost ratio above it) -- vertical 8 / 15 (boost) blocks/s from the config. Activation (double-tap Space), auto-land and
  every cost unchanged. **Judgment call**: Boost stays Sneak+Sprint (server rule and cost untouched), but while boosting
  Sneak no longer also descends (it used to -- every boost sank), since you now dive by looking down.

### 4. Ring on the hand + new battery model

- `client/greenlantern/GreenLanternHandRenderer`: a real ring model on the **right** hand (band round the knuckles, a
  raised bezel and a full-bright gem on the back of the hand; slim-arm and over-the-gauntlet variants), in third person
  via `PowerRingLayer` (rewritten; was a flat item-icon fleck on the main arm) and first person via the new mixin
  `GreenLanternRingHandMixin` (TAIL of `renderHand`). New textures `textures/entity/green_lantern/power_ring_worn.png`,
  `hard_light_construct.png`; new ring item icon `textures/item/power_ring.png`.
- Personal Power Battery reshaped (14 elements, `parent: block/block` for proper inventory display): base plates, a
  translucent glass barrel with a glowing core carrying the lantern emblem, four corner posts, top ring, cap, dome and a
  carrying handle. New `power_battery_core.png`, redrawn `power_battery.png` (glass) and `power_battery_frame.png`;
  real outline shape (`PowerBatteryBlock#SHAPE`) and rising green motes.
- All assets generated by `scratchpad/gen_greenlantern_v01321.js` (pngkit), lang by
  `scratchpad/lang_v01321_greenlantern.js`, CurseForge bullets by `scratchpad/docs_v01321_greenlantern.js`.

Gametests added: suit armour on at the start of the sweep / off at the end of suit-down, platform catch (hard light,
indexed, restored on dismiss), ramp steps are facing stairs, orphaned hard light removes itself, ram head hits the first
creature on its path, bubble centred on the caster, blade toggle syncs the hand-construct flag, tool-kit pieces
unbreakable.

## v0.14.3 -- revamp: models, animations, new attacks & constructs, wheel, HUD, ring

Explicit user request: "revamp green lantern, give him animations, add a few more useful constructs, make construct wheel
look better, give him more attacks and construct attacks, make N remove constructs instead of Shift+C, make shift+hold n
able to remove ring (5 seconds), make his HUD look better, give his moves models if needed, make the green lantern power
stronger, change ring model again and make it look cooler".

### Keys (new layout)

| Key | Tap | Shift |
|---|---|---|
| R | Ring Bolt (streak of light) | Continuous Beam (solid beam, free hand grips the wrist) |
| G | Construct Fist -- a flying fist model | War Hammer Slam -- a hammer model swung down 3 blocks ahead |
| X | hold: Oath (fist before the face; ring punched skyward on completion) | **hold: Emerald Gatling** |
| Z | Directional Shield | Protective Dome |
| V | Suit Up/Down | Ring Scan |
| C | deploy construct / hold: wheel | **Missile Barrage** (was: dismiss all) |
| H | **Giant Hand** -- grab / hurl (Shift+H = power wheel) | -- |
| N | **dismiss all constructs** (first press sets a tethered creature down) | **hold 5 s: take the ring off** |

### Architecture

- `entity/HardLightConstructEntity` (+ `GreenLanternEntityTypes.HARD_LIGHT_CONSTRUCT`): ONE entity type, the shape in
  synced data (`BOLT, BEAM, FIST, HAMMER, MISSILE, BUZZSAW, ANVIL, HAND, CHAINS, LAUNCH_PAD, WARRIOR`) plus scale / end
  point / target id / life / action tick. Never saved. All gameplay runs server-side in
  `GreenLanternConstructAttacks#tickEntity`; the client renderer `client/greenlantern/HardLightConstructRenderer` builds
  every model from `HardLightDraw` boxes (translucent full-bright green core + pale rim shell, over `TurboDraw`).
- `GreenLanternConstructAttacks`: the fist / hammer / streak visuals for the old moves, Gatling, Missile Barrage, Giant
  Hand, and the five new wheel constructs. Lasting ones (pads, warrior, chains) are tracked per owner in `LIVE` and end
  with N, death, logout, dimension change (`onCleanup` from `GreenLantern#clearTransient`).
- `data/GreenLanternFx` (+ `ModAttachments.GREEN_LANTERN_FX`, synced to all, never persisted, written only through
  `GreenLanternVisuals`): last move animation + start tick, channel bits (beam / gatling / hand / ring removal), ring
  removal start. Read by `client/greenlantern/GreenLanternPose` (keyframe poses, hooked in `HumanoidModelMixin`), the
  hand renderer (Gatling barrels on the fist, ring halo flare) and the HUD.
- New constructs are appended to `ConstructType` (ordinals of saved selections unchanged): `BUZZSAW, ANVIL_DROP,
  CHAIN_SNARE` (`Kind.ATTACK`), `LAUNCH_PAD, EMERALD_WARRIOR` (`Kind.SUMMON`); `ConstructType#category()` groups the
  wheel; `#descriptionKey()` = `<name>.desc`. `GreenLanternConstructs#deploy` hands those kinds to
  `GreenLanternConstructAttacks#deploy` first.
- N / H / Shift+N go through `GreenLanternActionPayload` (`GIANT_HAND, CLEAR_CONSTRUCTS, RING_REMOVE_START/STOP`);
  client dispatch sits in `ProjectHeroModClient`'s H and N chains just before the mutation-utility fallbacks.
  `GreenLantern#removeRing` = revoke + the Power Ring item back to the inventory (right-click re-bonds).
- Launch Pad fall safety (owner + squad) is a one-shot `FALL_SAFE` checked at the top of `GreenLanternDamage`.

### Balance (stronger)

Resistance I -> II; suit melee +8 -> +10; bolt 13 -> 18; beam 4 per 10 t -> 5 per 6 t; fist 16 -> 24 (flies 20 blocks,
half-damage splash, 1.5 s); hammer 17 -> 26 (4 s); Oath 22 -> 30 s; shield HP 80 -> 140; dome 250 -> 400; wall 160 ->
260; cage 75 -> 120; turret 4 -> 7; ram 12 -> 20; energy blade 9 -> 13. New: Gatling 5 x 10/s; missiles 6 x 12 (2.5
radius); Giant Hand 6 per 0.5 s squeeze + 22 throw; Buzzsaw 14 x 5 bounces; Anvil 34 / 18; Chains 5 s pin; Warrior 12
per swing for 30 s. All numbers in `GreenLanternConfig`'s "v0.14.3" block.

### Look

- Worn ring (`GreenLanternHandRenderer#ring`): band round the ring finger + dark bezel + white-hot gem + a breathing
  halo that flares while any move / channel is running. Still finger-sized (the v0.14.1 "not a bracelet" rule).
- Power Ring item: a real 3D model (`models/item/power_ring.json`, octagonal band + bezel + gem, texture
  `item/power_ring_model.png`), generated by `scratchpad/gen_ring_model_v0143.js`.
- Construct wheel (`GreenLanternConstructWheelScreen`): a donut of wedges drawn as GUI quads, category colour band per
  wedge, hovered wedge lifts out, selected wedge has a bright edge, icon atlas `textures/gui/green_lantern/constructs.png`
  (cell = ordinal, cell 31 = emblem, `scratchpad/gen_greenlantern_v0143.js`), centre = icon / name / group / cost,
  description under the ring. Sizes itself to the window.
- HUD (`GreenLanternHud`): framed panel -- emblem + title + status tags (Oath / Flying / Boost / Suited, dropped from the
  end if they would hit the title), a segmented Gauge for Ring Charge with the reserve marked, 8 key boxes (R G X Z V C
  H N) with top-down cooldown shade and lit outlines, the selected construct with icon + cost. Stacked above: ring
  removal Gauge, shield/dome meter, construct cooldowns with icons + Hairline bars. Hidden while the wheel is open.

Verified in-client with `scratchpad/GreenLanternDebugHarness.v0143.java.txt` + `HarnessCameraMixin.gl.java.txt` (env
`PROJECTHERO_GL_DEBUG`; both removed from the source tree afterwards). Gametests: `V0143GameTests`. Gotcha: GameTest
mock players are placed at world spawn -- move them into the test area (`helper.absoluteVec`) before testing anything
that is a ticking entity, and never use a NoAI mob to test a push (NoAI mobs ignore velocity).

## v0.14.4 -- simpler HUD again

User: "Change the HUD to make it look more similar to how it looked before, I preferred the simpler design. You can keep
the construct wheel the way it looks, it's nice. I don't like the border around his menu though."

- `GreenLanternHud` rebuilt on the pre-v0.14.3 layout (`git show ffe1912:.../GreenLanternHud.java`): **no framed panel,
  no panel backgrounds, no bordered bars** -- just the key boxes, text rows and thin 3 px bars, bottom-right. Top to
  bottom: (Alt) move names as plain shadowed lines, constructs on cooldown ("■ Buzzsaw 12s"), the shield / dome /
  barrier meter (centred label + thin bar), the ring-removal bar (Sneak + hold N), the selected construct + its cost /
  cooldown, "Green Lantern" + one status (OATH 18s / RECITING / BOOST / FLYING), the eight keys, the Ring Charge bar
  with the reserve mark, and the %.
- Kept from v0.14.3 in the old style: H and N boxes (8 keys, `BOX` back to 20 = 174 px wide), the Oath countdown in the
  X box (green overlay) and "..." while reciting, the Gatling / beam / Giant Hand / ring-removal glows, the flash when a
  move goes off, the Missile Barrage / Gatling / Giant Hand cooldowns, the ring-removal progress, hidden while the
  construct wheel is open. Dropped: the emblem icon, the SUITED tag (the V box already glows), the segmented Gauge,
  the construct icons and per-construct cooldown bars.
- Text is drawn with a shadow and the selected construct in a mid green (`SELECTED` 0xFF5FD08A) -- the old dark
  green with no shadow disappeared over grass.
- Construct wheel: unchanged except the thin dark-green **border ring round the centre disc** is gone (the only
  border it drew). `icon`, `ICONS`, `EMBLEM_ICON` stay on `GreenLanternHud` because the wheel uses them.
- `hud.projecthero.green_lantern.ring_charge` / `tag.suited` are now unused (left in the lang file).
- Verified in-client with `scratchpad/V0144HudDebugHarness.java.txt` (same install as the v0.14.3 harness, removed
  afterwards): idle, busy (shield + cooldowns), low charge + barrier refill, reciting, ring removal, wheel.


### v0.14.4 -- Carry Platform seats
Riders used to float about two blocks above the Carry Platform: each cell's invisible seat was a full-size armour
stand, and a stand seats its rider at its own 2-block height. The seats are now zero-size MARKER stands on the cell's
top surface (`GreenLanternConstructs#newCarrySeat`), so a rider sits on the platform itself. Test:
`GreenLanternGameTests#carryPlatformRidersSitOnThePlatform`.

## v0.14.22: Rescue Tether bubble, no time limit

- Whatever the tether carries sits inside a bubble of hard light: a `HardLightConstructEntity` with the new
  `Shape.BUBBLE` (radius = half the target's bounding-box diagonal + 0.3, in its scale), kept on the target by
  `GreenLanternConstructs.tickRescueHeld` and drawn by `HardLightConstructRenderer#bubble` on the target's own
  interpolated position (two `TurboDraw.sphere` shells). Throwing, setting down or losing the target pops it with
  the usual 5-tick fade.
- No time limit: `TETHER_UPKEEP_PER_SEC` 1 -> 0, so an empty ring no longer drops the target. Only C (throw) and
  N / Shift+C (set down) end the carry, plus the existing 20-block leash.

## v0.15.15 -- suit styles (N) and the suit pouring out of the ring

User requests: "Players can press N to choose between different suits. Turn these green lantern skins into suits." and
"make the suitup not go from the top of the players chest to their feet, make it spread outwards from the hand with the
ring on it, ring glows as the suit forms on users body". Then (same release) the key layout: "SHIFT+N opens the suit
selection menu, and that menu also has a Remove Ring option; plain N stays Clear Constructs; suit-up/down moves to H; the
Giant Hand moves to V".

| Key | Tap | Shift |
|---|---|---|
| H | **Suit Up / Down** (was V) | power wheel (unchanged) |
| V | **Giant Hand** (was H) | Ring Scan (unchanged) |
| N | dismiss all constructs (unchanged) | **open the suit screen** (`client/gui/GreenLanternSuitScreen`): 4 suit cards + a two-click **Remove Ring** button (replaces Shift + hold N) |

- `GreenLanternSuitStyle` (DEFAULT, CORPS, STEWART, CLASSIC) -> texture. Default = `textures/armor/green_lantern.png`;
  the three new ones are the user's skins with skin/hair stripped (`textures/entity/green_lantern/suits/*.png`, 64x64
  player-skin layout). Stored in `GreenLanternState#suitStyle` (codec field `suit_style`, default 0, so old saves load
  as Default), synced with the rest of the state.
- Rendering: all styles share `geo/green_lantern.geo.json`, which now has a `mask` bone under `armorHead` (head cube at
  inflate 0.55 -- over the player's own hat layer -- and hat cube at 0.8). The default texture's head is empty, so the
  default suit looks as before; Corps/Classic show their domino mask there. `GreenLanternSuitReveal#texture` swaps in the
  style texture for the 3rd-person armour (`SuperheroArmorRenderer#getRenderType`) and the first-person sleeve
  (`SuperheroFirstPersonArm`). Transparent texels show the wearer's own skin (face, Stewart's bare hands).
- Picking (`GreenLanternActionPayload` actions `SUIT_STYLE_*`, appended -> `GreenLanternSuit#selectStyle`): stored at
  once; if the suit is on and settled, a free suit-up transition replays (armour stays on) so the new suit re-forms out
  of the ring. `SUIT_TOGGLE` (H) -> `GreenLanternAbilityManager#suitToggle`; `REMOVE_RING` -> `#removeRingFromMenu`
  (`GreenLantern#removeRing`). The old RING_REMOVE_START/STOP hold path is still on the server but no client sends it.
- Suit-up / suit-down: `ArmorSweepReveal.Sweep.radialVia("gl_ring_arm", ring fist -> right shoulder)` -- up the ring arm,
  then out over the body and head behind a white-green edge; suit-down plays it backwards (recedes into the ring). Pose:
  ring fist held forward and a little up, the head turned to watch it, for the whole transition, then back to normal
  (`GreenLanternPose.SUIT`, both directions). Ring blaze:
  `GreenLanternSuitReveal#ringGlow` -> `GreenLanternHandRenderer` draws the ring in a bright palette with a white core,
  additive (lightning-buffer) glow spheres and a pulsing light shell (both views); server dust bursts at
  `GreenLanternSuit#ringHand` and rides the sweep front -- sent to everyone but the Lantern (in first person it buried
  the view).

## v0.15.15 batch 2 (gl2): R tap/hold + Blast Wave, Z bubble, off-hand battery charging, no construct limits, flight look

| Key | Tap | Hold | Shift |
|---|---|---|---|
| R | Ring Bolt (fires on release) | Continuous Beam (after `BEAM_HOLD_TICKS` = 6) | **Blast Wave** |
| Z | -- | **hard-light bubble** (`ForceBubble`, `Style.GREEN_LANTERN`) | Protective Dome (unchanged) |

- **R**: `GreenLanternAbilityManager` keeps `R_PRESSED` (press tick, or `R_HELD` once it became a hold); `serverTick`
  starts the beam past the threshold, the release fires the bolt only for a short press. Shift+R ->
  `GreenLanternCombat#blastWave` (18 dmg, Slowness III 4 s, 7 s cd `blast_wave`, 150 charge) ->
  `GreenLanternConstructAttacks#blastWave`: a `HardLightConstructEntity` of the new `Shape.WAVE` (appended) that rolls
  out over a 120-degree forward cone to 8 blocks in 8 ticks, hitting each creature once as the front reaches it
  (`canHit` = HeroTargets/squad rules, knockback except bosses). Drawn by `HardLightConstructRenderer#wave` with the new
  `HardLightRibbon` quads (leaning wall, white crest, ground seam, wash, additive glow). Pose `ANIM_BLAST`.
- **Z** (user-confirmed): held bubble, 40 charge/s (`BUBBLE_SHIELD_DRAIN_PER_SEC`), no up-front cost / HP / uptime meter,
  drops when the ring can't pay. Still the barrier attachment (HP > 0, not a dome) so HUD/pose see it.
  `GreenLanternDamage`: not a dome -> `ForceBubble.blocks` cancels projectiles and in-person blows from any side
  (`ForceBubble.absorb`), everything else passes. `GreenLanternShieldRenderer` draws `ForceBubbleRenderer` (the floating
  vanilla shield is no longer drawn). From inside in first person the shared renderer's shell is barely visible.
- **Charging** (`GreenLanternBattery`, rewritten): `PowerBatteryItem` (the battery's BlockItem) -- off hand + Sneak +
  use (block or air) -> `beginCharge`. Rooted (MOVEMENT_SPEED / JUMP_STRENGTH -100% modifiers + drift pulled back),
  the four Oath lines on the action bar every 30 ticks (`CHARGE_TICKS` = 120), cancelled by any damage > 0
  (`GreenLanternDamage`), any ability (`onAbilityUsed`) or the battery leaving the off hand; completion = full ring +
  `ANIM_CHARGED` (ring fist to the sky) + flash. Particles go to everyone but the charger. Synced through
  `GreenLanternFx.CH_CHARGE` + the new `chargeStart` field. A placed battery block only explains the gesture.
- **Battery look** (`client/greenlantern/PowerBatteryHeldRenderer` + `ItemInHandLayerPowerBatteryMixin` /
  `ItemInHandRendererPowerBatteryMixin`): drawn from its handle at the fist with the arm rotation undone (always hangs
  down), a damped pendulum per holder driven by hand acceleration / drag / stride. First person: arm lifted
  (`HOLD_UP`), grip moved to the fist's inner edge (else the arm hides it). Charging: 3rd-person pose in
  `GreenLanternPose` (`CH_*`), first-person arm offsets `BAT_*` / `RING_*`, full-bright battery + white core + additive
  halos growing over the Oath, flare on completion.
- **Constructs**: `ConstructType#maxDurationTicks` = 0 for all but the Sentry Turret; only the turret keeps a cooldown
  (`cooldownIdFor`); ram / tether / buzzsaw / anvil / chains / warrior cooldowns gone; Chains / Pad / Warrior entities
  live until N (`life` 0); Chains now cost 4/s (`CHAINS_UPKEEP_PER_SEC`); one ram head in flight at a time.
- **Flight look** (`client/greenlantern/GreenLanternFlightFx`): additive + translucent ellipsoid aura round the leaning
  body whenever `GREEN_LANTERN_FLYING` (not for yourself in first person); while sprint-flying (`GREEN_LANTERN_BOOSTING`
  or sprinting) a trail sampled every tick from the legs, drawn as camera-facing tapered ribbons + white core + two
  twisting strands + additive glow, fading over 16 ticks. The server dust line is skipped while boosting.
- **Ring on the suit**: `HandRing#innerFace` -- the 90-degree placements put the band on the outer face (normal -x), so
  a suit shell now moves the pivot outward (it used to sink the ring into the gauntlet); suited hands use wide-arm
  placement (gauntlet / first-person sleeve are always 4 px).
- **HUD**: six keys (24 px), no H / N boxes; the box = tap-move cooldown, a 2 px bar along its bottom = the Shift move
  (refills while cooling, bright when ready; peak-tracked). CHARGING tag; the barrier label reads BUBBLE.
- I-key page fix: the N row showed the construct text (a `case "N"` hidden behind a comment).
- Gametests: `V01515Gl2GameTests`. Screenshots: main repo `scratchpad/shots_v01515/gl2/` (harness sources there too).

## v0.16.1 -- Sniper Rifle construct

A new wheel construct (`ConstructType.SNIPER_RIFLE`, appended so saved selections keep their ordinals; new
`Kind.HELD_WEAPON`; Attack arc; icon = cell 19 of `textures/gui/green_lantern/constructs.png`).

- **Forming / putting away**: C forms it (`SNIPER_FORM_COST` 20, then `SNIPER_UPKEEP_PER_SEC` 1/s); C again dismisses it
  for free. It is an ordinary tracked `Construct` (marker, no cells), so N, death, logout, dimension change and an empty
  ring end it like everything else. `GreenLanternConstructs.HAND_SNIPER` (4) in `GREEN_LANTERN_HAND_CONSTRUCTS` tells
  every client to draw it; `end()` calls `GreenLanternSniper.onPutAway` (drops a charge in progress + the scope channel).
- **Input** (`client/greenlantern/GreenLanternSniperClient` + `GreenLanternSniperInputMixin`): while the rifle is out
  (and no real firearm is in the hand) vanilla `startAttack` / `continueAttack` / `startUseItem` are cancelled; attack
  down / up and use held / released go to the server as `GreenLanternSniperPayload` (CHARGE_START / FIRE / SCOPE_ON /
  SCOPE_OFF).
- **Shot** (`GreenLanternSniper.fire`): needs the rifle out, the `construct_sniper` cooldown ready and
  `SNIPER_SHOT_COST` (25, through `GreenLanternEnergy.spend`, so the emergency reserve is untouchable and the Oath
  doubles it). Hit-scan along the look out to `SNIPER_RANGE` (120): `SNIPER_DAMAGE` (22) at a full
  `SNIPER_FULL_CHARGE_TICKS` (15 = 0.75 s) charge, `SNIPER_MIN_CHARGE_FRACTION` (35%) tapped, times the Oath multiplier,
  via `AbilityHelpers.hurtBurst` (`HeroTargets` rules: no squadmates / pets). Knockback scales with charge.
  `SNIPER_COOLDOWN_TICKS` (20) between shots. A refused shot spends nothing and starts no cooldown.
- **Look**: one `HardLightConstructEntity.Shape.SNIPER_BOLT` at the muzzle (tracked by the shooter even when the impact
  is 120 blocks off) -- a white-hot head racing to the impact over 2.5 ticks, a thick tracer that thins and fades, and
  an impact flash (white core, additive + translucent expanding shells, a shock ring); plus a forced particle burst at
  the impact for every player within 200 blocks. The rifle model is drawn in the arm's pixel space by
  `GreenLanternHandRenderer.sniper` (stock under the forearm, grip in the fist, receiver, magazine, shrouded barrel,
  muzzle brake, scope on top; energy cells and muzzle brighten over `ANIM_SNIPER_CHARGE`, a shot kicks it back via
  `ANIM_SNIPER_SHOT`). `GreenLanternPose`: ring arm along the aim, free hand under the barrel; scoped
  (`CH_SNIPER_SCOPE` = 32) braces the stance.
- **Scope** (`client/gui/GreenLanternScopeOverlay`): first person only -- a deep translucent green tube, glowing rim,
  green duplex reticle with a hollow diamond centre, a 24-point charge ring that fills and pulses at full charge,
  READY / CHARGING / FULL CHARGE / cooldown, 5x. Zoom (`SNIPER_SCOPE_FOV` 0.2) and slower look
  (`SNIPER_SCOPE_SENSITIVITY` 0.35) ride the existing `GunFovMixin` / `MouseHandlerMixin`; the crosshair is hidden
  (`GuiGunCrosshairMixin`).
- **HUD**: while the rifle is out, the selected-construct row becomes the rifle's row (READY / CHARGING n% / FULL
  CHARGE / seconds to the next shot) -- one line, no extra rows. The scope overlay is registered in
  `ProjectHeroModClient` before the HUDs, so the Lantern HUD draws over the scope's surround; the 5x label sits in the
  upper-left of the lens.
- Gametests: `V0161PunisherGlGameTests` (forms / puts away, long-range hit + cost + cooldown, tapped shot weaker,
  refused without charge, charge-and-release input path; shots go straight up with `skyAccess = true`).

## v0.17.1 -- survival constructs (chest, scaffold tower, item net, tunneller)

Four new `ConstructType`s with their own `Kind.SURVIVAL`, appended after `MED_BAY`, run by
`greenlantern/GreenLanternSurvival` (dispatched from `GreenLanternConstructAttacks#deploy`; ticked, dismissed and cleared
through `GreenLanternArsenal`). All in the Construct Menu's Building & Utility section, icons in
`textures/gui/green_lantern/survival.png` (`GreenLanternBinding.ATLAS_SURVIVAL`). Numbers in `GreenLanternConfig`
next to the Tool Kit; every cost goes through `GreenLanternEnergy#spend` (reserve, rank discount, Oath x2).

| Construct | Cost | Upkeep | Notes |
|-----------|------|--------|-------|
| Hard-Light Chest | 40 | 1 / s | 27 slots, one at a time, where you aim (6 blocks). Owner + squad (`Squads.areAllies`) open it. Ends on N, an empty ring, 5 punches, the block being replaced, logout / death / dimension change: the items ALWAYS drop (at the chest; at the owner if the chest's chunk is unloaded). |
| Scaffold Tower | 30 | 1.5 / s | Aimed at the ground (24 range): a column up to 24 high + a 3x3 deck. Press again to let it go. |
| Item Net | 25 | -- | 12-block sweep, 3 s, pulls items + XP orbs and touches them into the inventory. 10 s cooldown (`construct_item_net`). |
| Tunneller | 10 + 1.5 / block | -- | 3x3 x 48 along the aim snapped to the nearest axis, one slice per 3 ticks (7.2 s for the full bore), drawn as a hard-light drill car gliding down the bore; stops at bedrock / hard light / an empty ring. |

- Blocks (`block/GreenLanternSurvivalBlocks`): `hard_light_scaffold` (`platform` property: false = column with the vanilla
  scaffolding collision -- solid only from above and not while sneaking; true = solid deck) and `hard_light_chest`
  (horizontal facing). Unbreakable, no item / loot, push-proof, self-removing orphans (`GreenLanternSurvival#isTrackedCell`).
  The column is in `#minecraft:climbable`; `HardLightScaffoldClimbMixin` makes sneaking sink through it instead of
  clinging (vanilla only exempts `Blocks.SCAFFOLDING`).
- The chest's items live in `GreenLanternSurvival.Chest#items` (a `SimpleContainer`, opened as a vanilla 9x3
  `ChestMenu`), not a block entity: anyone viewing is closed out before the contents drop.
- The tunneller breaks every block with `ServerPlayerGameMode#destroyBlock` -- the Mining Drill's path, so Fabric break
  events, claim mods, spawn protection and adventure mode apply -- with an unbreakable diamond pickaxe swapped into the
  selected slot for that one call (`GreenLanternSurvival#mine`).
- Entities: `Shape.ITEM_NET` and `Shape.TUNNELLER`, drawn by `HardLightSurvivalRenderer` (the Tunneller's drill car is
  `HardLightTunnellerModel`: tracked armoured body, canopy, stacks, a meshed 16-sided cone with three spiral flutes that
  spins up while the block ahead is solid; the server glides the entity 1/3 block a tick with the car's middle on the
  last slice cut, so the client lerp is smooth, and sprays block-crack particles of each block it mines).
- Gametests: `GreenLanternSurvivalGameTests`.
## v0.17.1 -- Carry Platform -> Car -> Jet

Explicit user request: the Carry Platform switches into a car or a jet.

- **Input**: pressing the Carry Platform's key again while it stands (any bound key, Quick Deploy on C, or a menu
  spawn -- all reach `GreenLanternConstructs#deploy`) now cycles **Platform -> Car -> Jet -> Platform**
  (`construct/GreenLanternVehicles#cycle`) instead of toggling follow / park. Follow / park moved to the owner's
  **Sneak + right-click on a platform cell** (the `UseBlockCallback` that seats riders; a plain right-click still seats).
  A Shift+key variant was rejected: Shift selects a different binding slot in the Construct Menu model, so it could
  never reach the same entry. **Superseded in round 2 below**: the key cycles only while you look at the form, follow /
  park is back on the key, and Sneak + right-click is gone. Everyone aboard is carried into the new form's seats, the owner first (so they drive);
  a reshaped platform starts parked. No room for the new form = the charge is refunded and the platform comes back.
- **Costs** (`GreenLanternConfig.VEHICLE_*`, next to `CARRY_PLATFORM_*`): Platform 20 + 1/s, Car 40 + 2/s, Jet 60 + 3/s
  -- `GreenLanternVehicles#upkeepPerSec` is what `tickUpkeep` charges for the Carry Platform now. All through
  `GreenLanternEnergy.spend`, so the Oath doubles and ranks cut them.
- **State**: still ONE `Construct` (`CARRY_PLATFORM`); `Construct#vehicleForm` + `Construct#vehicle` hold the form and
  the entity. In Car / Jet form it has no cells; `tickKind` hands it to `GreenLanternVehicles#tick` (false once the
  vehicle is destroyed / unloaded / gone, which ends the construct), `end()` and the logout path call `#onEnd` (riders
  out, a burst of green sparks + amethyst break, discard). Statics (`LIVE`, `FIRING`, `BOOST_READY`) are cleared from
  `GreenLanternConstructs#clearSessionState` and per player from `GreenLanternAbilityManager#onCleanup`.
- **Entities** (`entity/HardLightVehicleEntity` base, `HardLightCarEntity`, `HardLightJetEntity`; types
  `hard_light_car` 2.2x1.25, `hard_light_jet` 2.6x1.3): Mobs, `noSave`, never change dimension, and one that no live
  construct owns (`HardLightVehicleEntity.tracked` -> `GreenLanternVehicles.LIVE`) discards itself. Client-simulated
  like a boat / horse: the controlling passenger's client runs `simulate` from `travel` and the vanilla vehicle-move
  packet carries it; the server only simulates a parked vehicle. The velocity is kept in `vel`, not the delta movement
  (the client damps a non-AI mob's delta movement by 0.98 a tick). Riders' view yaw follows the car's turn
  (`positionRider`, boat-style). HP: Car 120, Jet 90; immune to fall / in-wall / drowning / cramming and to its own
  riders' and owner's hits; it flares when hit and crackles below a third.
- **Getting out**: Shift is the Shift+R modifier in these vehicles, so `PlayerMixin#wantsToStopRiding` asks
  `HardLightVehicleEntity#passengerWantsOut`: Shift held `VEHICLE_DISMOUNT_HOLD_TICKS` (10) with no Shift+R in that hold.
  Dismounting leaves it parked (a car stops, a jet sinks gently). Riders of a vehicle that dissolves in mid-air get
  6 s of Slow Falling (a ringless passenger would otherwise fall); Lanterns already take no fall damage with charge.
- **Car**: 4 seats (driver front-left). W accelerates `VEHICLE_CAR_ACCEL_BPS2` (14) to `VEHICLE_CAR_TOP_BPS` (50), S
  brakes (45) then reverses (12 b/s), A/D steer with eased input (rate falls from 5 to 1.8 deg/tick with speed, 0 at a
  stand), step height 1.1, floats on water. **Shift+R**: horn (two notes) + x3 acceleration for 1.5 s, 15 charge, 3 s
  cooldown (`GreenLanternVehicles#ringKey`, called first in `GreenLanternAbilityManager`'s SLOT_1). Plain R is still the
  Ring Bolt.
- **Jet**: pilot + 1. The nose swings toward the pilot's view at 6 deg/tick (banking into turns, A/D add a little yaw);
  W throttles at 22 b/s per s to 90, S brakes. Below `VEHICLE_JET_STALL_BPS` (18) it loses lift: the nose drops and it
  sinks at up to 5 b/s; on the ground it taxis level and lifts off past stall speed. `noGravity` (own lift model, and a
  floating vehicle must not trip the server's anti-fly kick). **R (hold)**: twin wingtip cannons, a pair every 4 ticks
  converging on the pilot's crosshair (64 blocks), 4 damage a bolt x `GreenLanternRanks.power` via
  `AbilityHelpers.hurtBurst`, 3 charge a pair, drawn as `boltStreak`s. **Shift+R**: afterburner, x3 thrust for 2 s,
  20 charge, 4 s cooldown.
- **Look** (`client/greenlantern/HardLightVehicleRenderer`, code-built `HardLightDraw` boxes like every GL construct
  model; golden at Corps Legend): an open-top sports car (wedge nose + splitter, striped hood with the emblem, raked
  windscreen, four bucket seats, roll hoops, rear wing, rolling + steering wheels, head / tail lights) and a fighter
  (pointed nose, tandem bubble canopy, intakes, swept wings with tip cannons, tailplanes, twin canted fins with the
  emblem, an exhaust that lengthens with speed and the afterburner). Both are swept into being nose-to-tail over
  `VEHICLE_BUILD_TICKS` (20) behind a bright plane of light, with a spark line from the ring.
- Gametests: `GreenLanternVehicleGameTests` (the cycle + costs, riders carried over, N removes it / never saved, a
  destroyed car ends the construct, the Shift-hold dismount rule vs Shift+R, Jet cannons damage + cost + R hold, a
  stalled jet sinks gently).

## v0.17.1 round 2 -- the key looks before it reshapes; easier Car and Jet

User: "if you press the carry platform button again after spawning it in and not looking at it then the platform follows
you like how it used to, make it so that it only shifts between its forms when you press the carry platform button while
looking at the construct itself, also make the jet and car easier to control please".

- **The key** (`GreenLanternVehicles#keyPress`, from `GreenLanternConstructs#deploy`):
  - **Looking at it** -> `#cycle`, Platform -> Car -> Jet -> Platform. "Looking" (`#lookingAt`): the eye ray out to
    `VEHICLE_LOOK_REACH` (6) blocks, stopped by any other block, meets the form's box (the platform's cells, or the
    vehicle's bounding box) padded `VEHICLE_LOOK_PAD` (0.5) sideways and down and `VEHICLE_LOOK_PAD_UP` (0.3) up -- the
    small top pad is so a level gaze over a platform at your feet (eye 1.62 over its base) does not count. A ray that
    lands on one of the platform's own blocks always counts; standing inside the padding only the form itself counts.
  - **Not looking at it** -> `#toggleFollow`: `GreenLanternConstructs#toggleConstruct` exactly as before the vehicles
    (Beacon Activate / Deactivate chime + sparks or dust at the ring hand; free), plus the action-bar line ("Carry
    Platform: following your aim" / "<form>: following you" / "Carry Platform: parked"). A following Platform chases a
    point 5 blocks ahead of your aim at 24 b/s (unchanged `tickCarryPlatform`). A following Car / Jet:
    `GreenLanternVehicles#tick` sets `HardLightVehicleEntity#setFollowTarget`(owner) while nobody rides it, and the
    server-side `simulate(null)` drives it -- the Car steers at you and stops `VEHICLE_FOLLOW_DISTANCE` (4.5) away, at up
    to `VEHICLE_FOLLOW_CAR_BPS` (20; a slow tight turn first when you are behind it); the Jet hovers 2.5 up and 4.5
    behind you, facing your way, at up to `VEHICLE_FOLLOW_JET_BPS` (30). Anyone getting in parks it.
  - **Riding it**: on the **Platform** the look rule still holds (look down at it to reshape, ahead to fly it -- the old
    flying-carpet feel). In the **Car / Jet** you are inside it, so the key always reshapes.
  - **No mid-air drops**: an airborne Platform (`#platformAirborne`: nothing solid in the 3x3x2 under it) skips the Car
    and goes straight to the Jet; a Jet in mid-air becomes a **parked Platform hovering where it was** with everyone
    seated on it; a form that cannot be built rebuilds the OLD form in place (charge refunded; was: always a platform,
    which could fail too and leave riders unseated); and `#cushion` gives anyone still unseated in mid-air 6 s of Slow
    Falling. A reshape sets `HardLightVehicleEntity#reshaping` so the riders it re-seats get no needless Slow Falling.
  - Every new form starts parked. **Sneak + right-click** on the platform is removed (the key does it again); a plain
    right-click still seats you.
- **Controls** (`HardLightVehicleEntity.Controls`: strafe, forward, Space, Sneak, view yaw / pitch, built in `travel` from
  the driver's client; `stepForTest` runs one tick of it for the gametests). Before -> after:

| | Before | After |
|---|---|---|
| Car acceleration | 14 b/s per s (0-50 in 3.6 s) | 18 (0-50 in 2.8 s) |
| Car brake | 45 b/s per s (50-0 in 1.1 s, 23 ticks) | 70 (50-0 in 0.75 s, 15 ticks; 20-0 in 6 ticks) |
| Car reverse | 12 b/s top, 11.2 b/s per s | 16 b/s top, 30 b/s per s (0 to full reverse in 11 ticks) |
| Car steering, deg/tick | 2.1 at 3 b/s, 4.2 at 12, 3.4 at 25, 1.8 at 50 (linear) | 4.9 at 3, 3.9 at 12, 2.9 at 25, 1.6 at 50 (sqrt curve, `VEHICLE_CAR_STEER_SLOW/FAST_DEG` 6 / 1.6) |
| Car steering input | eased 0.3 both ways | eased in 0.35, back to centre 0.5; 40% in the air |
| Car mouse steering | none | holding W with A/D free: eases toward the view (within 75 deg), the view does not turn with it, never overshoots |
| Car coast | x0.975 a tick | x0.965 |
| Car step-up | 1.1 | 1.25 (a 1-block bump with a slab on it); a wall it cannot climb halves the speed instead of grinding along |
| Car boost | x3 acceleration, capped at 50 | x3 acceleration up to 60, easing back to 50 after |
| Jet nose steering | toward the view at a flat 6 deg/tick (snaps on small corrections) | 22% of the gap a tick, at most 5 deg (`VEHICLE_JET_TURN_EASE` / `_TURN_DEG`) |
| Jet A/D | +-2.5 deg/tick of yaw the mouse then pulled back | a 3 deg/tick rudder that turns the pilot's view with it |
| Jet roll | banked with the turn input | banks into the turn, rolls level by itself as the nose reaches the view |
| Jet throttle | W accelerates while held, release = coast, S brakes | a lever: W up 60%/s, S down 90%/s, it holds; airspeed follows it (accel 26, brake 40 b/s per s; were 22 / 30) |
| Under 18 b/s | stall: nose drops, sinks up to 5 b/s | hover on VTOL jets: no sink, nose levels (pitch scaled by lift), HOVER on the HUD |
| Space / Sneak | -- | rise / descend at 7 b/s at any speed; Space also eases the throttle off into a hover |
| Take-off / landing | taxi past 18 b/s to lift off | Space lifts straight off; within 3 blocks a descent flares to 2.5 b/s and it settles on its skids |
| Parked jet | sinks at up to 5 b/s | lets itself down at 4 b/s, flared at the bottom |
| Vertical motion | instant | eased (35% a tick) -- no jolts between climb / hover / descend |

- **Getting out vs descend**: Sneak descends in the Jet, so the hold that gets you out is split
  (`HardLightVehicleEntity#passengerWantsOut`): near the ground (`#nearGround`: anything solid within a block under it)
  Shift held `VEHICLE_DISMOUNT_HOLD_TICKS` (10) **counted while near the ground** gets you out -- so holding Shift lands
  the Jet and then climbs you out half a second after touchdown; in the air the hold is `#airExitHoldTicks` (Jet:
  `VEHICLE_JET_AIR_EXIT_HOLD_TICKS` 60, a 3 s bail-out; Car: 10). Leaving any vehicle in mid-air (bail-out, N, an empty
  ring) gives 6 s of Slow Falling (`removePassenger`). Shift+R still cancels the hold.
- **Both**: no fall damage to the vehicle or riders (unchanged: `causeFallDamage` false, riders' fall distance reset every
  tick); client prediction unchanged (the driver's client simulates, the server accepts the move packet and never pushes
  the driver's own vehicle back, so it does not jitter).
- **HUD** (`client/greenlantern/HardLightVehicleHud`): Car rows W/S drive / brake, reverse; A/D steer; Mouse (hold W)
  steer toward your view; R Ring Bolt; Shift+R horn boost; hold Shift get out. Jet: Mouse steer; W/S throttle up / down
  (it holds); A/D rudder; Space rise / slow to a hover; Shift descend / land; hold R wing cannons; Shift+R afterburner;
  hold Shift land and get out. The Jet header shows speed + throttle % and a HOVER tag (was a flashing red STALL).
- Gametests (`GreenLanternVehicleGameTests`): the cycle now looks at the form first; not looking toggles follow (free)
  and looking reshapes, for the platform and the car; a following platform chases the aim; a following car drives up to
  its owner and boarding parks it; a mid-air jet becomes a seated hovering platform and an airborne platform skips the car;
  the jet hovers (no airspeed / 8 b/s hold their height, Space climbs and eases the throttle, Sneak descends); a parked jet
  lets itself down gently; the car brake / reverse / steering numbers; the car's mouse assist. Screenshots:
  `docs/screenshots/v0171/gl_vehicle2_*.png`.
## v0.17.1 -- Construct Combos, durability shown in-world, the ring's voice

- **Combos** (`GreenLanternCombos`): hit sites call `strike(owner, target, Move)` (a finisher: returns that hit's damage
  multiplier) or `mark(...)` (an opener; held / ticking constructs re-mark every pulse). Marks are per attacker -> target,
  expire after `COMBO_WINDOW_TICKS` (3 s), cleared on server stop and with the Lantern's transient state. Table: Crush
  (Chain Snare -> Anvil, x2 + shockwave), Slingshot (Giant Hand -> Fist, x1.5, hand lets go, launch), Overcharge (3 Ring
  Bolts -> Beam, x5 pulse), Detonation (Dome push -> Blast Wave, x2 + burst), Shatter (Buzzsaw -> War Hammer, x1.25,
  armour + toughness 0 for 8 s via transient modifiers), Pin (Battering Ram -> the rammed target touching one of your
  Hard-Light Walls within 1.5 s, +10 + 2 s stun; `#tick`), Bullseye (Bear Trap -> Sniper, x2), Home Run (Fly Swatter ->
  Boxing Glove, x1.5 + sky-high). Each: burst + chord at the target, +25 Willpower XP, a `GreenLanternCuePayload` (COMBO)
  -> `client/greenlantern/GreenLanternComboPopup` (name in bold green above the crosshair + one wrapped effect line).
  Numbers in `GreenLanternConfig`'s "Construct Combos" block.
- **Durability** (`construct/GreenLanternConstructHealth`): only the Z bubble, the dome, the Wall and the Cage have HP.
  Wall / Cage: under 50% every cell gets vanilla's crack overlay (`destroyBlockProgress`, one breaker id per cell,
  re-sent every 2 s); the HP fraction goes to players within 96 blocks (`GreenLanternConstructHealthPayload`) and
  `GreenLanternConstructHealthClient` draws a darker-green skin on the construct's outer faces (thicker / murkier as HP
  falls, flickering with drop-outs under 20%). The dome (`GreenLanternDomeRenderer`) and the bubble
  (`ForceBubbleRenderer`, GL style only) dim, darken toward deep green, crack under 50% and stutter under 20%.
- **Ring voice** (`GreenLanternRingVoice`, JARVIS's delivery style): lines by edge (charge 25% / 10%, Willpower Surge,
  3+ hostiles in 16 blocks, a barrier under 20%) or event (greeting on bond, Oath accepted, rank achieved, recharge
  recommended on a low-charge refusal, a wall / cage under 20%, combo callout). Per-line cooldowns + an 80-tick gap
  (urgent lines skip the gap). Client: `GreenLanternRingVoiceClient` -- typed-out green box in the top-left corner
  with a soft chime; muted by the Ring Voice switch in the Shift+N suits screen (`config/projecthero_ring_voice.json`).
- Gametests: `GreenLanternCombosGameTests`.
## v0.17.1 -- the Hard-Light Mech (ultimate construct)

User: "build it, build it well, give it a really cool looking mech model". A Construct Menu entry
(`ConstructType.HARD_LIGHT_MECH`, appended, `Kind.ARSENAL` -> `GreenLanternArsenal#deploy` -> `mech/GreenLanternMech#deploy`;
Attack section, its own icon file `textures/gui/green_lantern/mech_icon.png` via `GreenLanternBinding.ATLAS_MECH`).

- **Forming**: 1500 Ring Charge (through `GreenLanternEnergy.spend`: the Oath doubles it, the rank cuts it), then 15 a second.
  Needs a clear 3.2 x 6.5 space and not riding anything. A `mech/HardLightMechEntity` (a `Mob` + GeckoLib, never saved)
  spawns on the player and they ride it; it ASSEMBLES for 2 s (the `assemble` clip: core first, then every plate snaps out of
  it with an overshoot, light streaming from the ring hand), then is ACTIVE. Pressing the entry again from inside = N.
- **Piloting**: the pilot is the controlling passenger, so their client simulates the mech like a horse (vanilla vehicle-move
  packet, server checks). ~10 blocks a second (`WALK_SPEED` 0.23), 2-block step, Space = thruster jump (client side, in
  `tickRidden`). Sneak does not dismount (`mixin/GreenLanternMechSeatMixin`).
- **Keys** (`GreenLanternAbilityManager#handle` hands every slot to `GreenLanternMech#handleKey` while piloting; H is ignored
  in `GreenLanternBindings#key`; N = `clearConstructs` -> `GreenLanternMech#exit`; the client does not open the Construct
  Menu on a held C or the suit screen on Shift+N while piloting; double-jump flight is refused):

| Key | Move | Numbers (before Oath / rank / Surge) |
|---|---|---|
| R | punch combo, alternating fists, hold to keep swinging | 20, cd 0.5 s, 8 charge, big knockback |
| Shift+R | Rocket Fist: a FIST construct (phase 1 = moved by the mech) flies 24 blocks, pierces, returns | 30, cd 6 s, 60 |
| G | Ground Slam: shockwave ring to 9 blocks over 0.5 s | 28, cd 8 s, 80 |
| X | Thruster Dash: a velocity sent to the pilot's client, rams for 0.5 s | 14, cd 2.5 s, 30 |
| Z (hold) | Chest Lantern Beam, 48 blocks | 8 every 5 ticks, 50 charge/s |
| V (hold) | Hard-Light Shield wall: projectiles bounce back (owner -> pilot), frontal hits x0.2, half speed | 40 + 20/s |
| C | Missile Pods: 12 homing MISSILE constructs from the shoulder pods | 9 each, cd 10 s, 120 |

- **Integrity**: 300 HP (+10% a Lantern Rank), armour 8, no knockback, immune to fall / suffocation / drowning. Every hit on
  the pilot is moved onto the mech (`ServerLivingEntityEvents.ALLOW_DAMAGE`, void / kill excepted). At 0 it shatters, the
  pilot is set down where it stood, 60 s before another; leaving with N = 1 s dissolve, 10 s cooldown. An empty ring
  dissolves it. Death / logout / dimension change / ring loss remove it at once (`GreenLantern#clearTransient`). Static state
  (`GreenLanternMech.MECHS`) is cleared through `GreenLanternAbilityManager#clearSessionState`.
- **Look**: `geo/green_lantern_mech.geo.json` + `textures/entity/green_lantern/mech.png` (translucent plates, alpha 150, bright
  edges, dark inner frame, glass canopy alpha 64 so the seated pilot shows) + `mech_glow.png` (additive `eyes` pass) +
  `animations/green_lantern_mech.animation.json` (idle, walk, punch_r / punch_l, rocket_fist, ground_slam, beam, shield, dash,
  missiles, jump, assemble, dissolve), all generated by `scratchpad/gl_mech_v0171/gen_gl_mech.js` (preview sheets in
  `docs/screenshots/v0171/gl_mech_sheet.png` / `gl_mech_poses*.png`). `client/greenlantern/mech/HardLightMechRenderer` draws it
  full-bright translucent, golden at rank 5, plus the beam, the shield's honeycomb wall and the slam ring with `HardLightDraw`.
  First person hides the torso / head / backpack (you look out of the cockpit, arms and fists in view) and the player's hands
  (`GreenLanternMechHandsMixin`). Third person pulls back 6.5 blocks (`GreenLanternMechCameraMixin`, which also shakes the
  camera with every footfall / landing / slam from `GreenLanternMechClient`).
- **HUD** (`GreenLanternMechHud`, the GL HUD steps aside): title + state, INTEGRITY bar, six boxes R G X Z V C (never H / N)
  with cooldowns and a Rocket Fist bar on R, the Ring Charge bar; Alt lists the moves; first person adds cockpit brackets.
  The vanilla mount hearts and the "Press Shift to Dismount" hint are dropped (`GreenLanternMechGuiMixin`).
- Gametests: `GreenLanternMechGameTests`.

## v0.17.1 -- Hard-Light Mech construct combos (Giga Drill, Hard-Light Katana)

User: "combo moves with the mech construct ... using the drill construct while in the mech activates a giant drill attack
like in gurren lagann and using energy blade while in the mech spawns in a giant katana for the mech to wield". Server:
`mech/GreenLanternMechCombos` (all state on the `HardLightMechEntity` -- no static maps, nothing for the reset hook);
keys: `mech/MechComboKeys` (client-safe); numbers: `GreenLanternMechConfig` (the "construct combos" block); sounds:
`mech/GreenLanternMechSounds` (`gl_mech_*`, `sounds/green_lantern/mech_*.ogg`, synthesised mono by
`scratchpad/synth_gl_mech_combo_sounds.js`).

- **Trigger rule** (`MechComboKeys`): inside the mech a combo is **Shift + the key its construct is bound to outside**. The
  construct's Construct Menu bindings are read in menu order (G, H, Z, X, C, V, then the Shift keys); the first that is not H
  gives the base letter (bound to G or Shift+G -> Shift+G in the mech). Not bound, bound only to H / Shift+H, or the letter is
  already taken by an earlier combo (`Combo` order: Giga Drill, then Katana) -> the combo's fallback (Giga Drill **Shift+G**,
  Katana **Shift+X**), else the first free of Shift+G / X / Z / V / C. Never H; N stays climb-out; Shift+R stays the Rocket
  Fist (the iaido while the katana is drawn). A Shift + key with no combo on it does that key's plain move, as before.
  `GreenLanternMech#handleKey` asks `GreenLanternMechCombos#key` first.
- **Giga Drill** (Mining Drill): hold the combo key -- the right forearm becomes a spiral hard-light drill (the fist is hidden,
  `MechWeaponDraw#drill` on the forearm bone) that spins up over `DRILL_SPIN_TICKS` 40 (cost 120, then 40/s; the mech walks at
  35%; the drill arm follows the camera pitch, `HardLightMechModel#setCustomAnimations`; a crosshair gauge in first person).
  Let go (`DRILL_MIN_SPIN_TICKS` 6, else it fizzles: 2 s cooldown) -> `DRILL_CHARGE`: the mech charges 10..26 blocks (by
  spin) along the aim (pitch clamped to +/-0.55) at 1.15 blocks/tick. The pilot's client moves the mech (its input is locked
  and its body held on the line, `tickRidden`); the server sends the velocity every tick (`impulse`), keeps a bore cursor at
  most `DRILL_LEAD` 5 ahead of the mech and cuts a round tunnel (radius 3.7 round the axis 3.25 up -- clears the 3.2 x 6.5
  body) `DRILL_BORE_AHEAD` 4.5 in front of it. Boring = `canBore`: the server's ability-terrain switch, loaded / in-bounds /
  inside the border, no block entities, not `WITHER_IMMUNE`, hardness 0..25 (no obsidian), no hard light, and the pilot's own
  break rights (`mayBuild`, `mayInteract` = spawn protection, adventure mode, every `PlayerBlockBreakEvents.BEFORE` guard =
  claims / GL constructs / conjured structures). 15% of bored blocks drop. A solid block it may not bore within 2.4 of the axis
  ends the charge 2 blocks short of it. Enemies within 3.4 of the line take 11 x power x (0.6 + 0.4 spin) every 3 ticks (8 hits
  each at most) and are carried along. The charge ends at the line's end, when the mech stalls, or after 50 ticks. A full spin
  finishes in **GIGA DRILL BREAK** (`gigaBreak`): 70 x power to everything 5 round the 11 blocks ahead, knockback 3.2, a crater
  of radius 4.6, the ghost drill + shockwave rings + flash (`MechComboRender`), screen shake, the popup. Cooldown 15 s; the drill
  shrinks back over 1 s. While it spins / charges every other mech key waits.
- **Hard-Light Katana** (Energy Blade): tap the combo key -- the 12-tick draw clip (the blade forms out of the tsuba); tap again
  -- the 10-tick sheathe (the blade slides back into light). 60 to draw, 6/s upkeep (sheathes itself when the ring cannot pay),
  sheathed at once on climb-out / dissolve / shatter (`stopChannels` -> `stopAll`) and when the Giga Drill starts. While drawn:

| Key | Move | Damage (x power) | Cost | Cooldown |
|---|---|---|---|---|
| R (hold to chain) | 3-cut chain: kesa, rising reverse, wide horizontal (arc +/-70 / 70 / 100 deg, reach 7.2) | 24 / 24 / 38 | 10 / 10 / 16 | 9 / 9 / 16 t |
| Shift+R | Iaido: a crescent (half-width 3.6) flies 32 blocks at 1.8/t through enemies (each hit once), cuts grass / flowers / saplings / vines / leaves (`cuttable`, break-permission checked), stopped by terrain | 34 | 90 | 5 s |
| G | Spinning sweep: one full turn, everything within 7.5 | 30 | 70 | 6 s |

  A cut that connects sets `HIT_AT`: the action clip's speed drops to 0.08 for 3 ticks (hit-stop) and nearby cameras shake.
  Slash trails are sampled per frame from the blade's pose (`MechComboRender#trailSample`, world space, kept 3.2 ticks).
- **Model** (`MechWeaponDraw`, code-built meshes, `entityTranslucentEmissive`): the katana (5.7 blocks of curved single-edged
  blade, sori 0.30, shinogi ridge, iori-mune spine, wavy notare/gunome hamon on both flats and a boshi, kissaki with a yokote
  line, a sheen band sweeping along it, a white cutting edge, a glow shell; habaki, seppa, a lobed oval tsuba with the Lantern
  emblem cut through it, a diamond-wrapped tsuka with menuki, fuchi and kashira); the drill (a cone with three spiral flutes
  over 2.3 turns, an armoured collar with glowing teeth); the crescent. The renderer catches the right fist / forearm bone poses
  as GeckoLib draws them, so both follow every clip.
- **Clips** (`scratchpad/gl_mech_v0171/anims.js`, keyframed poses; check them with `preview_combos.js`): katana_idle /
  katana_walk (main loops while it is out), katana_draw, katana_sheath, katana_slash_1..3, katana_iaido, katana_spin (a full
  root turn), drill_spin / drill_charge (main loops), drill_break.
- **HUD**: keys holding a combo get a corner tag; Shift flips the boxes to the Shift layer (drill / katana icons + their
  cooldowns, Rocket Fist or iaido on R, the rest dimmed); with the katana drawn R / G show its cuts and the R bar tracks the
  iaido; the state reads DRILL n% / DRILLING / KATANA; the Alt list adds the Shift+R move and both combos with their keys.
- **Popup / XP**: the Construct Combo popup (`GreenLanternCuePayload.COMBO`, `projecthero.green_lantern.combo.mech_*`) on
  release / draw / GIGA DRILL BREAK; Willpower XP 25 for a connecting charge or the first connecting katana move per draw, 60
  for a GIGA DRILL BREAK that hits. Damage is a player attack, so boss threat tables credit the pilot.
- Gametests: `GreenLanternMechComboGameTests`. Screenshots: `docs/screenshots/v0171/gl_mech_combo_*.png` (harness kept as
  `scratchpad/MechComboHarness.v0171.java.txt`).

## v0.17.1 -- Ring Auto-Protection (the ring shields its bearer on its own)

User-approved: no key, five automatic saves in `GreenLanternAutoProtect` (registered on `ALLOW_DAMAGE` from
`GreenLanternDamage#initialize`, straight after GL's own handler, so the Z bubble / Dome soak first and the Death Save only
sees what would really land). Runs for any bonded Lantern (`GreenLantern.hasPower`) -- an active mutation does not turn it
off -- but not while piloting the Hard-Light Mech (the mech takes the hits), in creative or spectator. The void, `/kill` and
anything `BYPASSES_INVULNERABILITY` always go through. Numbers: `GreenLanternConfig`'s "Ring Auto-Protection" block.

| Save | Trigger | Effect | Cost / cooldown |
|---|---|---|---|
| Death Save | a hit that kills or leaves < 6 HP (after armour, Resistance, protection, absorption: `wouldDropBelow`) | hit cancelled; burst shell blocks every hit for 20 ticks | 300 flat (`spendRaw`, spendable pool only -- never fires on a short ring), 60 s (`abilityReadyAt["gl_auto_death_save"]`, synced) |
| Fall Cushion | owns the v0.11.6 free fall immunity (charge > 0); plus a pad when falling past the safe fall distance within 2.5 blocks (+ speed) of solid ground | fall damage cancelled; pad flashes on the ground, fall braked to 0.3 b/t | pad: 20 + 2 per block fallen, 10 s (the immunity itself has no cost or cooldown); not while flying / elytra / in water |
| Air Bubble | `DROWN` or `IN_WALL` damage | hit cancelled, air refilled, bubble over the head while still needed (+2 s) | 3 / s |
| Heat Shell | `IS_FIRE` damage, or standing in lava / on fire | fire and lava damage x0.25 (cancel + reissue) | 2 / s while up (+1.5 s linger) |
| Blind-Side Guard | melee (direct = attacker, a living entity) or projectile (direction = against its flight) from outside the 100 degree view cone (horizontal) | x0.6 (cancel + reissue), spark + chime at that side | 3 s |

- **Design decision**: the Fall Cushion switch now gates the old free fall immunity (switching it off = you take falls); the
  pad's 20 + 2/block is paid only when the pad flashes, so the v0.11.6 promise ("no fall damage while the ring has charge")
  still holds with the switch on. The automatic Air Tank (v0.11.10) still keeps a swimming Lantern's air topped up from half
  air, so underwater the Air Bubble is the backstop; it matters most for suffocation in walls.
- **Death-save interplay**: the Death Save cancels the hit outright, so Hulk / Wolverine / Black Flash style saves (which act
  on a hit that lands) never see it -- no double fire. It is GL-only, so it is fine for it to go first.
- **Switches**: `GreenLanternState#autoProtectOff` (OFF bits, 0 = all on; codec field `auto_protect_off`, persisted and
  synced). The Shift+N suit screen's AUTO-PROTECTION section: master + five, two rows of three, short labels when narrow, the
  five grey out while the master is off; the heading shows "Death Save ready / in N s". Sent as
  `GreenLanternActionPayload.Action.TOGGLE_AUTO_*` (appended) -> `GreenLanternAutoProtect#toggle`.
- **Voice**: `GreenLanternRingVoice.Line` SHIELD_ENGAGED ("Lethal threat detected. Shield engaged.", urgent), CUSHION,
  AIR_BUBBLE, HEAT_SHELL, BLIND_SIDE (45-60 s cooldowns + the 80-tick gap, so never spammy).
- **Look**: synced `ModAttachments.GREEN_LANTERN_AUTO_PROTECT_FX` (`data/GreenLanternAutoProtectFx`: shell start, cushion start +
  ground Y, BUBBLE / HEAT flags) -> `client/greenlantern/GreenLanternAutoProtectRenderer`: the Death Save pops the
  `ForceBubbleRenderer` bubble out to 1.35 blocks behind a white flash in 3 ticks, holds, fades; the pad is a flat octagon of
  `HardLightDraw` parts with a stud rim and the emblem, squashing under the landing; the Air Bubble is the Atmosphere Bubble
  construct's two soft spheres round the head; the Heat Shell a faint orange-green sphere with an amber rim. The lasting
  shells are skipped for the wearer in first person.
- **HUD**: a thin 7 x 8 shield outline under the right end of the charge bar -- green with a bright spine when the Death Save
  is ready, grey with "Ns" beside it while cooling down; hidden when the save or the master is off. Not a key box.
- **State**: static maps (shell, cushion / blind cooldowns, bubble / heat linger, last save) cleared through
  `GreenLanternAbilityManager#clearSessionState` (server stop) and `GreenLantern#clearTransient` (death, respawn, logout,
  dimension change, ring removal), which also clears the fx attachment.
- Gametests: `GreenLanternAutoProtectGameTests`. Screenshots: `docs/screenshots/v0171/gl_autoprotect_*.png`.

## v0.17.1 -- the Hard-Light Drone and the Hard-Light Gateway (ring waypoints)

Two "ring tech" constructs (`ConstructType.HARD_LIGHT_DRONE` / `HARD_LIGHT_GATEWAY`, appended, `Kind.RING_TECH`), all in
`greenlantern/recon/`: `GreenLanternRecon` (entity types, payloads, key router, per-player tick, clean-up),
`GreenLanternDrone` + `HardLightDroneEntity`, `GreenLanternGateway` + `HardLightGatewayEntity`, `GreenLanternWaypoints`
(the data) and `GreenLanternReconConfig` (every number). Menu tabs: Drone = Utility, Gateway = Mobility (section Building &
Utility). HUD key-box icons: `textures/gui/green_lantern/ring_tech.png` (2 x 16 px, `GreenLanternBinding.ATLAS_RING_TECH`);
menu icons `icons/c_hard_light_drone.png`, `icons/c_hard_light_gateway.png` (`scratchpad/gl_icons_v0171/drone_gateway_icons.js`
in the main checkout).

**Keys -- a key and its Shift twin.** Both are "pair" entries (`GreenLanternBinding#isPair`): binding one to G (or Shift+G)
binds both G and Shift+G. The key is the main action, Shift + the key the second one (`GreenLanternBindings#key` passes
`second` only while both keys hold it; an empty Shift key under a pair key falls through to its second action too). Neither
can go on H or Shift+H (`GreenLanternBinding#refusalKey` -> `menu.no_h`). Menu "spawn once" = the main action.

| | Drone | Gateway |
|---|---|---|
| Key | deploy (80) / dismiss (free); 2 a second while out | the waypoint picker; mid-channel: cancel; doors open: close them early |
| Shift + key | scout run (deploys one first if none is out); again = come home | the Waypoints screen |

**Drone** (`HardLightDroneEntity`, 0.7 x 0.5, never saved, server-driven, client lerp-smoothed). Follows over the right
shoulder (`GreenLanternDrone#shoulder`, eased `flyTo`, blinks home past 28 blocks). Every 60 ticks it sweeps 32 blocks and
sends `GreenLanternReconPayload.MARKS` to its Lantern alone -> `GreenLanternRingScanClient#acceptDrone` -> `EntityGlowMixin`
outlines them red (a set of its own, so it never wipes a Ring Scan). Fetch: the nearest `ItemEntity` / `ExperienceOrb` within 8
blocks of the bearer (never one the bearer threw in the last 2 min) -- it flies over it, carries it under a tractor beam and
hands it over (`playerTouch`; a full inventory gets it set down at the feet). Zap: a `Mob` whose target is the bearer, within
16, in sight, `HeroTargets.canHarm` -> 3 damage every 30 ticks. Scout run: to the crosshair block (64 max) pulled back 1.2 and
up 2.5, hover 200 ticks circling, a sweep of 24 every 40 ticks, and on arrival `SCOUT_REPORT` (hostiles, containers
(`Container` block entities but furnaces, in loaded chunks), other creatures) -> the Scout Report popup
(`GreenLanternReconClient`). 40 HP (`hurt` -> `GreenLanternDrone#onHurt`; the owner and their squad can't hurt it); pickable,
projectiles hit it. Ends on: key, N, an empty ring, being shot down, death / logout / world change.
Model (`client/greenlantern/recon/HardLightDroneRenderer`): teardrop hull of stacked slices with a spine, keel and 45-degree
shell, swept fins + stabiliser, flank seams, tail light, belly emitter; a lens with a bezel ring, dark glass, bright core and the
Lantern emblem (white flash on a zap); two spinning rotor rings on pylons that tip with speed; a two-shell halo; a 14-sample
light ribbon; zap streak + impact orb; tractor beam; a sweeping scan ring under it while it hovers; banks into turns; flickers
under 30% HP.

**Waypoints**: attachment `ModAttachments.GREEN_LANTERN_WAYPOINTS` (persistent, `copyOnDeath`, synced to the owner only):
up to 5 `(name, dimension, x, y, z, yaw)`. Names are cleaned (`GreenLanternWaypoints#clean`, 24 chars). C2S
`GreenLanternGatewayPayload` ADD / RENAME / DELETE / OPEN; S2C `GreenLanternReconPayload.OPEN_PICKER` / `OPEN_WAYPOINTS`
(carries the cooldown seconds). Screens: `GreenLanternWaypointsScreen` (rows with coordinates + distance; other-world rows greyed
with the reason; inline rename; "Mark here" with a name box) and `GreenLanternGatewayPickerScreen` (compact rows: distance +
cost, or the reason it is greyed: other world / too close (< 8) / recharging; click or press 1-5; "Waypoints..." button). Both use
`ReconUi` (the Construct Menu's panel look), every line wraps or is cut with an ellipsis.

**Gateway** (`GreenLanternGateway#start`): refused (nothing spent, no cooldown) when busy, on cooldown, the waypoint is in another
dimension (`gateway.other_dimension` -- cross-dimension is deliberately not allowed), under 8 blocks, the ring can't pay, or there
is no room 2.6 ahead. Otherwise a `HardLightGatewayEntity` in state TRACE stands there facing the bearer, `GreenLanternFx.CH_GATEWAY`
raises the ring arm (`GreenLanternPose`), a rising resonance hums every 10 ticks, and the HUD shows "Opening gateway" with a bar.
`AFTER_DAMAGE` on the bearer -> `onDamaged` -> cancel (no cost, no cooldown). After 60 ticks `complete`: the far end is loaded
(`getChunk`), a safe landing found (`PegasusTeleport.landing`, the Kaalki / Pegasus portal helpers), the cost paid (100 + 0.2 a
block, 600 max, through `GreenLanternEnergy#spend`), the near door goes OPEN and its twin opens at the waypoint facing the way
the Lantern faced when saving it, both closing after 200 ticks; 5-minute cooldown (`construct_gateway`); the ring voice says
"Gateway open: <name>." (`GreenLanternRingVoice.Line.GATEWAY`). Each open door keeps its chunk with a `TicketType.PORTAL`
ticket (refreshed every 100 ticks). Travel (`passThrough` / `travel`, like `PegasusPortalEntity`): a player crossing the door
plane within the opening goes out 1.3 in front of the other door, lateral offset mirrored, yaw turned by
`to.yaw - (from.yaw + 180)` and horizontal velocity with it; 30-tick re-entry guard. Only the owner and `Squads.areAllies`
players; anyone else gets a "not yours" spark; mobs are never moved. N / death / logout cancel the channel and fold the doors.
Door model (`HardLightGatewayRenderer`): stepped pillars with fluted faces and climbing rune dashes, capitals, a squared lintel,
an arched crown with a keystone carrying the emblem, tympanum, threshold step, bright inner edge; the opening is three layered
swirl planes at depths 0.04 / 0.28 / 0.55 (per-vertex spiral colour, alternating directions, deeper = darker and slower), a rim
glow and 46 motes flowing in from round the frame and spiralling into the middle. TRACE draws the outline as two lines of light
climbing from the threshold to the crown (ghost outline + drawing sparks, emblem at the end); OPEN unfolds a centre seam, then
widens out; CLOSING folds back.

Static state (`GreenLanternDrone.DRONES`, `GreenLanternGateway.CHANNELS / OPEN / PASSED / DENIED`) is cleared by
`GreenLanternRecon#clearSessionState` (via `GreenLanternArsenal#clearSessionState` <- `GreenLanternAbilityManager` <-
`ServerStateReset`) and per player by `GreenLanternRecon#dismissAll` (via `GreenLanternArsenal#dismissAll` on N, death, respawn,
logout and world change).
Gametests: `GreenLanternReconGameTests`. Screenshots: `docs/screenshots/v0171/gl_drone_*.png`, `gl_gateway_*.png`.
