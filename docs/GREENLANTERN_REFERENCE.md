# Green Lantern Hero-Tier power (v0.11.2)

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
| X (Movement) | Flight toggle | — (boost is read live from sprint-holding while flying, no separate key) |
| Z (Ultimate) | Directional Shield (held) | Protective Dome |
| V (Utility/Control) | Suit Up/Down | Ring Scan |
| C (Special Mode) | Deploy selected construct | hold ≥0.35s, release = cycle to next unlocked construct; **Shift+C** = dismiss all |

## Core classes

| Role | Class |
|---|---|
| Static API (bond/revoke/cooldowns/lifecycle) | `GreenLantern` |
| Balance constants | `GreenLanternConfig` |
| Persistent state (15-field codec, under the 16-field ceiling) | `data/GreenLanternState` |
| Ring Charge resource | `GreenLanternEnergy` |
| Slot dispatch + per-player tick | `GreenLanternAbilityManager` |
| Flight (cloned from `MaxSteelFlight`'s velocity model) | `GreenLanternFlight` |
| Ring Bolt / Beam / Fist / Hammer | `GreenLanternCombat` |
| Directional Shield / Protective Dome | `GreenLanternShield` (state) + `GreenLanternDamage` (absorption hook) |
| Ring Scan | `GreenLanternScan` |
| Suit Up/Down animation | `GreenLanternSuit` |
| Willpower Mastery I-IV | `GreenLanternMastery` |
| Power Battery recharge channel | `GreenLanternBattery` |
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
  (modelled on `PowerWheelScreen`) listing every Mastery-unlocked `ConstructType`; picking a wedge
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
  gap either. **The supplied texture's head UV region (both the `[0,0]` and `[32,0]` 32x16 blocks) is
  fully transparent** -- inherited byte-for-byte from the source art, not a conversion mistake -- so the
  `helmet` bone currently renders nothing at all while worn (armour points/toughness still apply; it is
  purely a visual gap). Needs either a repaint of that texture region or the `helmet` cubes dropped
  entirely, at the user's call.
- **Mining Drill mines through the normal survival block-break path**
  (`ServerPlayerGameMode.destroyBlock`), so it fully respects claims/protections and can never touch
  bedrock/unbreakable blocks — but it inherits the currently-held item's harvest tier rather than
  always granting diamond-equivalent drops, and it does not implement the per-material break-speed
  timing table from the spec (it breaks on the normal per-tick check interval instead).
- **Battering Ram** is an instant dash-and-open-vanilla-doors ability, not a persisted, slot-costing
  construct (avoids any block-breaking/claim-bypass risk).
- **Carry Platform** is a larger stationary platform; it does not yet follow its owner or carry
  passengers along with it.
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
