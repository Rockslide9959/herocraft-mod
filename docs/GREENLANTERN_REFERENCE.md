# Green Lantern Hero-Tier power (v0.11.6)

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
| X (Movement) | Ring Grapple (v0.11.5) | — |
| Z (Ultimate) | Directional Shield (held) | Protective Dome |
| V (Utility/Control) | Suit Up/Down | Ring Scan |
| C (Special Mode) | Deploy selected construct (Rescue Tether: grab, or throw if already holding) | hold ≥0.35s, release = cycle to next construct; **Shift+C** = dismiss all, or set down a Rescue Tether hold safely if one is active |

**v0.11.5: Ring Flight moved off the X slot** to a double-tap of the vanilla jump key while airborne
(mirroring Thor/Iron Man's own double-tap-jump gesture -- see `ProjectHeroModClient#handleDoubleJump`
and the C2S `GreenLanternActionPayload`), toggling on or off either way; boost is still read live from
sprint-holding while flying, no separate key. X now fires the new Ring Grapple ability instead (see
`GreenLanternGrapple`) so the slot isn't wasted.

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
| Ring Grapple (v0.11.5, X slot) | `GreenLanternGrapple` |
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
