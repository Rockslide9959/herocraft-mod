# Power Armour (v0.17.5) — reference

Design: `docs/POWER_ARMOUR_DESIGN.md` (approved 2026-11-16). A **Secondary power** you only have while sealed in a suit.

Code: `com.projecthero.mod.powerarmour` (common: data, frame entity, items, charger, kit), `com.projecthero.mod.client.powerarmour`
(voxel model, renderers, HUD, editor, effects), mixins `PowerArmourInputMixin`, `PowerArmourPoseMixin`,
`PowerArmourFirstPersonArmMixin`, `PowerArmourVisorGlowMixin`. Numbers live in `ArmourTier`, `CoreType`, `ModuleType` and
`PowerArmourConfig`. Tests: `src/gametest/.../PowerArmourGameTests`. Assets/data: `scratchpad/gen_power_armour_v0175.js`;
lang: `scratchpad/lang_power_armour_v0175.js` (key by key). Films/screens: `scratchpad/PowerArmourHarness.v0175.java.txt` +
`scratchpad/power_armour_v0175_films.js` -> `docs/screenshots/v0175/power_armour/`. Round 2 (proportions + side-aware opening):
`scratchpad/PowerArmourHarness2.v0175.java.txt` (before / after modes, same camera mixin) + `scratchpad/power_armour2_v0175_films.js`
-> `docs/screenshots/v0175/power_armour2/`.

## How it fits the power system

- **Secondary**, like the Symbiote: never replaces and is never replaced by a Primary (`PowerArmour.POWER_CLASS`). Any hero
  can own frames and step in. Gaining a Primary does not touch it.
- **Keys:** `AbilityRouter.handleInput` hands every key to `PowerArmourAbilityManager` while `PowerArmour.ownsKeys` (in a suit
  or stepping in / out) — right after the Erased check, ahead of every Primary. H / N presses are swallowed. Client side,
  `ProjectHeroModClient` skips the hero key handlers (H power wheel, N, double-jump gestures, charged punch, firearm reload)
  while in a suit (`PowerArmourClient.swallowHeroKeys`). Primary passives keep running.
- **HUD:** `VanillaHud.muteOthers` draws every other power's bottom-right column off-screen while you are in a suit; the
  suit's HUD (`PowerArmourHud`) uses `Stack.bottomRightOwned`. The Left-Alt move list shows the armour kit.
- **Erased (Eraser Head):** the suit is a machine (`PowerSuppression.isTech`), it keeps working.
- **Static state:** `PowerArmourAbilities` keeps three maps of ids (charge runs, thrown mobs, rail beams), cleared via
  `PowerArmourMod.clearSessionState()` from `ServerStateReset`.

## Tiers (frame material)

| Tier | Armour | Tough. | KB res. | Speed | Integrity | Damage x | Drain x | Cooldown x | Slots | Special |
|---|---|---|---|---|---|---|---|---|---|---|
| Iron | 15 | 1 | 0.1 | -10% | 300 | 1.00 | 1.00 | 1.15 | 3 | sturdy, slow |
| Gold | 11 | 0 | 0 | -5% | 220 | 0.95 | **0.75** | 1.10 | 3 | cores drain 25% slower |
| Hero Alloy | 17 | 2 | 0.1 | 0 | 450 | 1.15 | 1.00 | 1.00 | 4 | |
| Diamond | 20 | 5 | 0.15 | 0 | 650 | 1.30 | 1.00 | 0.95 | 5 | |
| Netherite | 22 | 8 | **1.0** | -5% | 900 | 1.45 | 1.00 | 0.95 | 6 | fire / lava proof, no knockback |
| Vibranium | 24 | 10 | 0.3 | +5% | 1200 | 1.60 | 1.00 | 0.90 | 7 | falls charge 2 E/pt, blasts -50% and charge 3 E/pt |
| Celestial | 26 | 12 | 0.4 | +10% | 1600 | 1.80 | 1.00 | 0.80 | 8 | faint glow, fastest servos |

The suit's armour **replaces** worn armour: what you wore is stored (`PowerArmourAttachments.STASH`) and handed back when you
step out (anything put on while sealed drops into the inventory). Step-up +0.45 while powered.

**Slots by body part** (`ArmourTier.SLOT_ORDER`, first N): Helmet, Arms, Back, Legs, Chest, Arms, Helmet, Chest —
Iron/Gold H1 A1 B1; Hero Alloy +L1; Diamond +C1; Netherite +A2; Vibranium +H2; Celestial +C2. A smithing upgrade only adds
slots at the end, so every module keeps its place.

**Integrity:** every point of incoming damage while sealed costs 1 integrity (before armour). Below 20% the frame is a
**wreck** and will not seal. At 0 the suit ejects you and stands as a wreck. **Dying inside:** the suit ejects you and stands
where you fell with 30% of its integrity gone (your stored armour goes back on first, so it drops / keeps like normal).
**Repair:** right-click a parked frame with its material: Iron/Gold ingot 10%, Hero Alloy ingot 12.5%, Diamond / Netherite
Scrap / Vibranium Shard 15%, Celestial Core 50%, or any tier with a Suit Repair Kit 30%. The Repair Drone repairs 5/s.

## Power cores

| Core | Capacity | Output | Recipe |
|---|---|---|---|
| Fusion Cell | 1000 E | 6 | `IRI / RGR / IRI` (iron, redstone, glowstone dust) |
| Arc Core | 2500 E | 10 | `AQA / QFQ / AQA` (Hero Alloy, quartz, a Fusion Cell) |
| Star Core | 6000 E | 16 | `VGV / GCG / VAV` (Vibranium Shard, glowstone, Celestial Core, an Arc Core) |

Charge is the `projecthero:power_core_charge` component (crafted cores start full; shown as the item bar).
**Drain:** 1 E/s sealed (x tier drain, x1.25 with Overclock), Visor +1 E/s, Shield Projector +6 E/s, Thruster Pack 8 E/s.
Falls cost 4 E per point of fall damage instead of health (if the core cannot pay, the fall hurts).
**Unpowered** (core and bank empty): walk speed -70%, no step-up, no moves, no passives; Shift+V still opens it.
**Core Charger** (`III / RFR / ICI`: iron, redstone, furnace, Circuit Board): a core on top, fuel below; burns furnace fuel for
its furnace time (redstone dust 200 ticks, redstone block 1800) only while the core is not full, +2 E per burning tick.
**Solar Plate:** +0.5 E/s in daylight under open sky, worn or parked. **Capacitor Bank:** a 600 E reserve used after the core;
while the core is above half it moves 2 E/s into the bank.

## Getting it

- **Redstone Circuit Board:** `RGR / CIC / RGR` (redstone, gold nugget, copper ingot, iron ingot).
- **Platings** (24 material per frame = 6 platings): `MM / MM` of iron / gold / Hero Alloy ingots / diamonds / Vibranium
  Shards; Netherite = netherite ingot + 2 diamonds (shapeless); Celestial = Celestial Core + 2 Vibranium Shards (shapeless).
- **Frame:** `PBP / P P / P P` (6 platings + board). Place it: stands facing you (45° steps), never takes damage
  (explosions, fire, lava included), falls like an armour stand.
- **Smithing upgrade:** template = Circuit Board, base = frame of tier N, addition = one Plating of tier N+1. Keeps the whole
  suit (vanilla smithing copies the `power_armour_suit` component; the item's tier wins — `PowerArmourFrameItem.suitOf`),
  integrity keeps its share. No skipping tiers.

## Frame handling

- Right-click: step in. Sneak + right-click with an empty hand: the **Armour Editor** (slots, core bay, paint, stats, Pick Up).
- Right-click with its repair material / a Suit Repair Kit: repair. Right-click with a core while it has none: core goes in.
- **Pick Up** (editor button): back to its item with everything on it (core, modules, paint, integrity, custom name).
- Refused while: another suit or transformation is on (any Iron Man piece, Hulk, Titan form, Omnitrix alien, Moon Knight,
  Black Panther suit, Max Steel, Shazam, a Miraculous, the Symbiote suit, Thor's armour), riding, sleeping, spectating, or the
  frame is a wreck. One of those starting while sealed (the Hulk's rage) bursts the suit open.

## Sequence (ticks)

Frame opens 18 / closes 22. **Enter:** 20 (walk to 1.15 blocks from the frame on the side you clicked) + 22 (step in) + 40
sealing (legs 0-30%, arms 18-48%, back 34-60%, chest 46-72%, helmet 64-86%, faceplate 76-95%, visor lights at the end).
**Exit:** hold Shift+V 16, unseal 18 (reverse order), the frame stands, step out 18; the empty frame closes itself.

**One side opens (round 2).** The click's side is `PowerArmour.sideFrom`: the player's position against the frame's facing
(front half-plane = front, behind = back); within 0.15 blocks of the plane the look decides (looking the way the frame faces =
behind it). It is stored on the frame (`PowerArmourFrameEntity.side()`, synced entity data, saved) and on the player's state
(`PowerArmourState.side`, in the synced attachment, saved), so every viewer sees the same plates move.
- **Front:** chest doors, abdomen flap, the helmet's front half (swings up and forward like a visor), arm fronts, both halves
  of each leg front open; you walk to the front, turn round and step back in.
- **Back:** back doors, the helmet's back half (swings up and back), arm backs and leg backs open; you walk round behind it,
  facing the way it faces the whole time (no turn), and step forward in.
- The outer arm / leg side plates slide out a little either way. Pistons and steam only on the opening side.
- **Exit** opens the side you came in by (front if unknown) and you step out that way: forward out of the front, backward
  out of the back. The frame stands facing the way the suit faced.
The walk is driven client-side as velocity (`PowerArmourClient.drive`, so the legs walk); the server snaps you into the frame
at the seal. A logout mid-sequence is tidied on join (sealing completes, entering is dropped, leaving completes).

## Kit (sealed)

Damage below is the Iron base x the tier's damage multiplier (x1.15 with Overclock); cooldowns x the tier's cooldown multiplier.
Bosses (max health >= 100 or `TitanCombat.isBoss`) are never knocked about or grabbed.

| Key | Move | Numbers |
|---|---|---|
| R | **Servo Punch** — hold to charge (1 s) | 8 dmg (up to x2 charged), 4-block reach, knockback 1.2-2.5, 8-20 E, 0.5 s. Charged >= 50% breaks the block it lands on if hardness <= 1.5 (and you could break it). **Power Fist:** x1.5 and charged punches send a 4-block cone shockwave at half damage |
| Shift+R | **Arm weapon** | see below |
| G | **Ground Stomp** | r4, 7 dmg (falloff to 60%), throws up/out, 40 E, 6 s. **Seismic Plates:** r6, x1.2, Slowness II 3 s |
| Shift+G | **Servo Grip** (press again to throw) | 3.5-block grab (**Magnetic Clamp:** 14), held up to 4 s, throw 8 dmg on impact (+50% splash r2), 20 E, 3 s |
| Z | **Shoulder Charge** | 8 ticks at 1.1 b/t, 9 dmg per mob (once each), 30 E, 4 s; stops on a wall. **Breacher Plating:** breaks blocks with hardness <= 3 (2 high) |
| Shift+Z | **Core Overload** | 1 s wind-up (rooted), r6 blast 24 dmg (falloff to 50%), costs max(250, 35% of capacity), 45 s |
| X | **Jump Jets** | leap (1.0 forward, 0.95 up; 0.72 in the air), 20 E, 2 s. **Thruster Pack:** hold to fly (WASD steers, Sneak sinks, up to 0.55 b/t climb), 8 E/s |
| C | **Back module** | see below |
| V | **Visor** | outlines living things within 32 blocks (your view only), 1 E/s. **Ore Scanner:** ores within 10 outlined through walls (rescans every 2 s). **Target Lock:** aims the gatling / tesla / rail / missiles at the nearest threat in a 30° cone (32 blocks) |
| Shift+V (hold 0.8 s) | **Open up** | works unpowered |

**Arm weapons (Shift+R)** — one per suit: **Gatling** (hold; 0.3 s spin-up then a round every 3 ticks: 2.5 dmg, 32 blocks,
slight spread, 1.5 E/round), **Flamer** (hold; every 2 ticks a 6-block 28° cone: 1.5 fire dmg + 4 s burning, 1 E),
**Tesla Coil** (chain to up to 4 targets 6 blocks apart, 9 dmg each, 35 E, 2 s), **Rail Cannon** (hold 1.5 s, release: a
piercing 48-block beam, 28 dmg to everything on the line, 110 E, 6 s; reports to `BeamClash` every tick of its 8-tick beam,
the renderer hides it via `BeamClashClient.hides`), **Power Fist** (Piledriver: 5-block cone slam, 14 dmg, launches up, 45 E, 5 s).

**Back modules (C):** **Missile Pod** (4 homing missiles at the nearest threats in sight, 6 dmg r2.2 burst each, no block
damage, 80 E, 8 s), **Shield Projector** (toggle up to 8 s: projectiles bounce off, other damage -60%, 6 E/s, 10 s cooldown
after), **Repair Drone** (10 s: +5 integrity and +1 health a second, 100 E, 30 s), **Sentry Turret** (30 s, shoots the nearest
threat in 16 blocks every 8 ticks for 3 dmg x tier, one per owner, 150 E, 40 s), **Thruster Pack** (C = afterburner dash
along your look, 2.0 b/t, 60 E, 3 s).

**Passives:** armour from the tier, no fall damage while powered (drains the core), heavy footsteps, step-up, no
sprint-swimming. **Night Visor** (night vision), **Rebreather** (air topped up, poison cleared), **Reactive Plating** (hit by
something within 4 blocks: r3 burst 4 dmg + knockback, 10 E, 3 s), **Servo Boosters** (+20% speed), **Stabilisers** (no
knockback), **Overclock** (+3 output, +15% damage, +25% drain).

## Modules

| Module | Part | Output | Recipe (B = Circuit Board, T = Salvaged Tech, A = Hero Alloy ingot) |
|---|---|---|---|
| Night Visor | Helmet | 1 | ` G / TBT` golden carrot |
| Ore Scanner | Helmet | 1 | ` C / TBT` compass |
| Target Lock | Helmet | 2 | ` S / ABA` spyglass |
| Rebreather | Helmet | 1 | ` P / TBT` pufferfish |
| Reactive Plating | Chest | 2 | `AGA / TBT` gunpowder |
| Capacitor Bank | Chest | 0 | `CRC / CBC` copper ingot, redstone block |
| Overclock | Chest | 0 | `RGR / ABA` redstone block, glowstone |
| Solar Plate | Chest | 0 | `DDD / TBT` daylight detector |
| Breacher Plating | Chest | 1 | `AAA / IBI` iron block |
| Gatling | Arms (weapon) | 3 | `III / ABA` iron ingot |
| Flamer | Arms (weapon) | 3 | `FFF / ABA` fire charge |
| Tesla Coil | Arms (weapon) | 4 | `CLC / ABA` copper block, lightning rod |
| Rail Cannon | Arms (weapon) | 6 | `VRV / ABA` Vibranium Shard, powered rail |
| Power Fist | Arms (weapon) | 2 | `PPP / ABA` piston |
| Magnetic Clamp | Arms | 2 | `IRI / TBT` iron block, redstone |
| Missile Pod | Back | 4 | `FFF / ABA` firework rocket |
| Shield Projector | Back | 4 | `SGS / ABA` shield, glass |
| Repair Drone | Back | 3 | `PTP / ABA` phantom membrane |
| Sentry Turret | Back | 5 | `DTD / ABA` dispenser |
| Thruster Pack | Back | 5 | `RFR / ABA` blaze rod, firework rocket |
| Servo Boosters | Legs | 2 | `PSP / TBT` piston, sugar |
| Seismic Plates | Legs | 2 | `OOO / TBT` obsidian |
| Stabilisers | Legs | 1 | `IHI / TBT` iron ingot, heavy pressure plate |

**Fitting rules** (`SuitData.whyNot`): right body part, no duplicates, one arm weapon, and the modules' total output must not
exceed the core's (+3 with Overclock). Without a core anything that fits a slot goes in, but a core that cannot run what is
fitted is refused (`whyNotCore`), and taking Overclock out is refused if that leaves the load over budget.

## Balance notes

A Celestial suit with a Star Core and Overclock hits x2.07 (punch 16.6, charged 33; rail 58; overload 50 at the centre) with
26 armour / 12 toughness — a strong secondary, but it runs on a battery (a Star Core is ~100 s of continuous gatling fire), its
integrity drains with every hit, and Core Overload costs 2100 E. Top Primaries (Kryptonian, Thor, Hulk) still out-damage and
out-last it.

## Look (client)

- `SuitMesh`: the voxel mesher. Each layer is a 128x128 texture in the player-skin layout (2x: a texel is half a model pixel).
  Every opaque texel becomes a block standing `depth` proud of the body box grown by `inflate`; faces merge into rectangles
  per panel, side walls into runs (a wall samples the texel row it borders). Cached per (texture, inflate, depth), cleared on
  resource reload.
- **Proportions (round 2):** a suit is exactly a player scaled up by `PowerArmourConfig.SUIT_SCALE` = **1.1** on every axis
  (head 8x8x8, body 8x12x4, arms and legs 4x12x4, same pivots). The layers are a thin shell inside the player's boxes, so the
  silhouette is the scaled box itself: under-suit -0.18..-0.08 px, plates -0.10..+0.02, trim and glow 0..+0.10 (model px before
  scaling). The cavity is (4 - 2 x 0.18) x 1.1 = 4.0 px, so a player's 4-px limbs fit inside. Sealed, the wearer gets a
  `SCALE` attribute modifier of the same factor (`power_armour_scale`): hitbox 0.66 x 1.98 (still under a 2-block door; the
  drawn helmet top, 2.06 blocks, brushes a 2-block lintel), eye height 1.78. The standing frame draws at 0.9375 x 1.1 (the player
  renderer's own 0.9375 times the suit's), the same size as a worn suit. 1.1 is the largest factor that keeps the hitbox under 2
  blocks; 1.15+ would stop suited players walking through doors.
- **The wearer inside:** the wearer's own model is hidden from stepping into the open frame until stepping out
  (`PowerArmourHideBodyMixin`, also the first-person hand). While the suit is open round them `PowerArmourLayer` draws their
  body (skin + overlays) inside it (`SuitRenderer.renderInner`): as they stand during the first half of the step in, rising into
  the suit's joints (each part at the scaled pivot, shrunk 0.3 px to sit inside the shell) over the second half, fitted while the
  plates close / open, settling back over the first half of the step out. The suit is drawn at `SUIT_SCALE / currentScale`, so
  the attribute changing at the seal (applied when sealed, removed when it starts to open) never makes it jump.
- Layers (`SuitRenderer`): frame (shared `frame.png`, a solid woven under-suit with ribs and joints, tinted per tier), plates
  (`<tier>_plates.png` greyscale x primary paint), trim (`<tier>_trim.png` x secondary), glow (same depth as trim,
  `<tier>_glow.png` x glow colour, `RenderType.eyes`, dimmed when unpowered / a faint standby on a parked frame with charge).
- Panel breakup (generator, round 2): pecs with a lower edge sloping up to the armpits, chest core between them, two rows of
  abs on the abdomen flap, belt; shoulder blades over a lower back with a 2-px spine channel (spine lights beside it); a shoulder
  plate on each arm (within the 4-px arm) over an elbow and a wrist joint; thigh, knee cap, shin and boot plates; a 2-px visor
  slit, brow ridge and mouth grille. Every plate is bevelled on its own borders (lit top / left, shaded bottom / right).
- `SuitPanel`: every texel of every layer rides the first panel whose 3D region holds it: chest doors, abdomen flap (from y 6),
  back doors, the helmet's front half (`VISOR`) and back half (`HELMET`, split at z 0 on the sides / top), arm and leg clamshells
  (leg fronts split down the middle), outer side plates slide. `SuitPanel.side()` says which side each opens on and
  `Look.open(panel)` keeps the other side shut. Hinges sit 0.1 px outside the box edges. Pistons stretch from the frame to the
  opening plates. Seams in the textures line up with the panel regions (keep `gen_power_armour_v0175.js` and `SuitPanel` in
  step).
- Per tier: plate shapes (Iron riveted slabs, Gold filigree with cut-outs, Hero Alloy chamfered chevrons, Diamond facets,
  Netherite layered bands, Vibranium hex grid, Celestial pearl with sparkles), trims (Iron rims, Gold double lines, Hero Alloy
  racing stripes, Diamond crystal ridges, Netherite spikes, Vibranium silver edges, Celestial gold filigree) and glow shapes
  (visor and core differ per tier; Netherite molten cracks, Celestial stars).
- `SuitModules`: pixel-box modules from `modules.png` (8x4 swatch cells); gatling barrels spin while firing, thrusters and
  calf jets flame, face modules ride the helmet's front half (the Ore Scanner the back half), back modules lift out of the way
  when the back opens. The boxes were laid out on a 1.45 px shell; `SuitModules.fit` moves each onto the round-2 shell when it is
  built (a box wholly outside a face slides in keeping its size, a box that wraps a part has its outer ends pulled in).
- Poses (`PowerArmourPose`, via `PowerArmourPoseMixin`): punch wind-up / jab, stomp, grip / throw, charge, overload, aimed
  weapon arm, piledriver, jets, thrust.
- Paint: the editor's three swatches cycle the 16 dye colours and back to the tier's own colour (-1).
