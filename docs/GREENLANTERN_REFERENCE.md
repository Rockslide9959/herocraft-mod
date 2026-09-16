# Green Lantern Hero-Tier power (v0.10.22)

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
- **Construct selection is cycle-on-hold, not a radial wheel.** Holding C ≥0.35s and releasing
  cycles `selectedConstruct` to the next Mastery-unlocked type (server-authoritative, one existing
  network payload, no new Screen/packet). The spec's fallback explicitly allows this
  ("functional selection UI... architecture ready for radial visuals" later). A real
  `GreenLanternConstructScreen` + dedicated select payload is the natural follow-up.
- **Directional Shield / Protective Dome do not physically block entity movement.** They intercept
  damage (redirecting it into the barrier's HP pool via the same cancel-and-reapply-smaller pattern
  every Hero-Tier power in this mod uses for `ALLOW_DAMAGE`) rather than giving hostiles a real
  collision shape to bounce off. A player standing inside a dome can still be melee-reached by
  something that clips in, even though the hit itself is absorbed. True collision would need a
  custom `VoxelShape`/entity-pushback system.
- **Suit renders on the shared `crimson_vanguard` GeckoLib geometry**, retextured black/green/white
  (`textures/armor/green_lantern.png`, a first-pass colour-banded placeholder, not UV-accurate per
  body part). A bespoke `geo/green_lantern.geo.json` is the natural next step, exactly like several
  Iron Man marks originally shipped on the shared geometry before getting their own model.
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
- **Textures are placeholder, programmatically generated** (`scratchpad/gen_greenlantern_textures.js`,
  the project's established hand-rolled-PNG-via-Node-zlib approach) — flat colour blocks, not final
  art.
