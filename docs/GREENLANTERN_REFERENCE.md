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
| Lantern Light | a floating hard-light orb (no collision, light 15) on the aimed air cell, 16-block range (`LANTERN_LIGHT_RANGE`) -- was a Sea Lantern block, two blocks above an aimed floor. |
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
