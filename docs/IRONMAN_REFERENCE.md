# Iron Man / Tony Stark — Content Reference

Hero-Tier power built around the permanent **Tony Stark** power. A normal player cannot craft Iron
Man armour and wear it — they must first become Tony Stark by creating and activating an **Arc
Reactor**. After that they gain access to the **Stark Fabricator** and the Iron Man suit progression.

All power/energy/progression checks are **server-authoritative** (`com.projecthero.mod.ironman.*`). A
modified client cannot tell the server it has Tony Stark or unlimited suit energy.

---

## 1. The Tony Stark power

| | |
|---|---|
| Display name | Tony Stark |
| Internal id | `tony_stark` (stored as a flag, not a registry entry) |
| Tier | Hero Tier (peer of Thor — **not** one of the 27 experimental mutations) |
| Storage | `TonyStarkState` attachment `projecthero:tony_stark_state` — `persistent` + `copyOnDeath` + synced target-only, mirroring `ExperimentalState` |
| Survives | logout, server restart, world reload, dimension change, and death (like Thor's worthiness) |

`TonyStarkState` fields: `hasPower`, `techLevel` (0–5), `builtSuits`, `activeSuit`, per-suit
`suitEnergy` / `suitIntegrity`, Iron Man `abilityReadyAt` cooldowns, transient suit-up transition
state.

## 2. Arc Reactor activation

Right-click the Arc Reactor item (`com.projecthero.mod.ironman.item.ArcReactorItem`):

* **server checks** `TonyStark.hasPower(player)`;
* if not owned → `TonyStark.grant()`: sets `hasPower`, tech level 0, gives a **Blank Blueprint** ("changes 21"),
  plays `BEACON_ACTIVATE` + `CONDUIT_ACTIVATE`, blue-white `END_ROD` / `ELECTRIC_SPARK` particles,
  3-second Glowing (illumination), message **"Tony Stark power acquired."**, and consumes **one** Arc
  Reactor;
* if already owned → **"You already possess the Tony Stark power."**, reactor **not** consumed.

## 3. Recipes

**Arc Reactor** (normal crafting table):

```
A C A     A = Amethyst Shard   C = Copper Ingot
G D G     G = Gold Ingot       D = Diamond
A R A     R = Redstone Block           -> 1x Arc Reactor
```

**Reactor Core** — the machine power core (so the player's original Arc Reactor stays specifically
the thing that unlocked Tony Stark):

```
G R G     G = Glowstone
R D R     R = Redstone Block
G R G     D = Diamond                  -> 1x Reactor Core
```

**Stark Fabricator**:

```
I D I     I = Iron Block   D = Diamond
P C P     P = Piston       C = Crafting Table
I R I     R = Reactor Core              -> 1x Stark Fabricator
```

**Iron Man Suit Platform**: `III / R C R / III` (I = Iron Block, R = Reactor Core, C = Chain).

**Basic components** (normal table): `copper_wiring`, `metal_plating`, `basic_circuit`,
`mechanical_parts` — shapeless from copper/iron/redstone/quartz.

**Blank Blueprint** (`2 paper + basic_circuit + redstone`) is the only craftable blueprint — see
"changes 21". Right-click it to stamp it into a specific mark's blueprint, gated on the linear
build-the-previous-suit progression. (Pre-"changes 21": each mark had its own blueprint recipe.)

### 3a. v0.14.21 recipe cost pass (current recipes)

User request: "redo the Iron Man crafting recipes ... so that it's not that expensive." Every suit from
the Mark 2 up now costs roughly **half** the raw materials (46-55% less), and the order of the build
ladder is now also the order of cost. Every component's crafting-table JSON and its Fabricator recipe
are identical (same inputs, same yield) — gametest `componentTableRecipesMirrorTheFabricator` enforces it.

**Components** (table *and* Fabricator; bold = changed):

| component | inputs | yield |
|---|---|---|
| `copper_wiring` (table) | 1 copper + 1 redstone | 6 |
| `metal_plating` (table) | 2 iron | **4** (was 3) |
| `basic_circuit` (table) | 1 copper_wiring + 1 redstone + 1 quartz | **4** (was 3) |
| `mechanical_parts` (table) | 1 iron + 1 redstone + 1 copper_wiring | **4** (was 3) |
| `titanium_gold_alloy` | 3 gold + 3 iron + 1 metal_plating | **3** (was 2) |
| `titanium_gold_plate` | 2 titanium_gold_alloy + 1 metal_plating | 3 |
| `servo_motor` | **1** mechanical_parts (was 2) + 1 copper_wiring + 1 redstone | 2 |
| `micro_thruster` | 1 servo_motor + **1** metal_plating (was 2) + 1 blaze_powder | 2 |
| `stark_circuit` | 2 basic_circuit + 1 gold + 1 redstone | **3** (was 2) |
| `repulsor` | 1 stark_circuit + **1** redstone_block (was 2) + 1 diamond | 2 |
| `flight_stabilizer` | **1** servo_motor (was 2) + 1 micro_thruster + 1 amethyst_shard | 2 |
| `targeting_module` | 1 basic_circuit + 1 stark_circuit + 1 ender_eye | 2 |
| `suit_computer` | **1** stark_circuit (was 2) + 1 targeting_module + 1 amethyst_shard | 1 |
| `advanced_arc_reactor` | **2** titanium_gold_plate (was 3) + 2 stark_circuit + **4 diamonds (was a diamond block)** + **1** glowstone (Fabricator had 2, table 3) | 1 |
| `missile_module` | **1** stark_circuit (was 2) + **2** gunpowder (was 4) + 1 servo_motor + 2 metal_plating | 2 |

Several table recipes changed shape (`repulsor` is now a D/R/C column, `flight_stabilizer` and
`suit_computer` shapeless, `advanced_arc_reactor` `PGP / DSD / DSD`, `missile_module` `GCG / MSM`).
Fabricator energy/time eased on the dearest parts (`advanced_arc_reactor` 4 000 → 2 500 energy,
160 → 120 t; `suit_computer` 1 200 → 800, 100 → 80 t; `repulsor` 800 → 600; `flight_stabilizer`
600 → 500; `missile_module` 700 → 600).

**Mark 1** (table): built from Metal Plating instead of iron ingots — same shapes. Helmet `PBP / P P`
(+ basic_circuit), chestplate `P P / PMP / PPP` (+ mechanical_parts), leggings `PCP / P P / P P`
(+ copper_wiring), boots `P P / P P`. ~11 iron for the suit (was ~22).

**Mark 2** (Fabricator, metal plating): helmet 5 plating, 1 servo, 1 targeting module, 2 Stark
circuits (Suit Computer dropped); chestplate 6 plating, 2 servos, 1 repulsor, 1 flight stabiliser, 2
circuits; leggings 6 plating, 2 servos, 1 circuit, 1 stabiliser; boots 5 plating, 1 micro thruster, 1
repulsor, 1 stabiliser, 1 servo.

**Mark III → VII** (Fabricator, `FabricatorRecipes.armorSet(suit, timeTier, costTier, signature)`),
with `e = (costTier + 1) / 2` and `S` = the mark's signature component:

| piece | inputs |
|---|---|
| helmet | (4+e) Ti-Au plate, 1 servo, 1 targeting module, 1 Stark circuit, 1 suit computer, 1 S |
| chestplate | (6+costTier) plate, 2 servo, 1 repulsor, 1 flight stabiliser, 1 advanced arc reactor, 2 Stark circuit, 2 S |
| leggings | (5+e) plate, 2 servo, 1 Stark circuit, 1 flight stabiliser, 1 S |
| boots | (4+e) plate, 1 micro thruster, 1 repulsor, 1 flight stabiliser, 1 servo, 1 S |

| mark | costTier | signature S |
|---|---|---|
| III | 0 | Stark circuit |
| 4 | 1 | servo motor |
| V | 2 | micro thruster |
| 6 | 3 | flight stabiliser |
| VII | 4 | missile module |

Duplicate inputs (S = an item the piece already lists) are folded into one input by
`FabricatorRecipes.merged` — previously the Mark III/4/6 pieces listed Stark circuits twice, which
`FabricationRecipe.matches` checked independently against the whole tray (so a tray one short still
matched).

**Raw cost per suit** (components fully expanded; "weight" = iron 1, copper 0.5, redstone 0.25, gold 2,
quartz 0.5, diamond 8, blaze powder 2, amethyst 1, ender eye 4, glowstone 1.5, gunpowder 0.5):

| mark | before | after | cut |
|---|---|---|---|
| Mark 1 | 22 iron, ~1 redstone — **22.5** | 11 iron, ~1 redstone — **11.2** | 50% |
| Mark 2 | 24 iron, 7 gold, 57 redstone, 1.5 diamond, 1 ender eye … — **78.7** | 13 iron, 2 gold, 19 redstone, 1 diamond, 0.5 ender eye … — **37.0** | 53% |
| Mark III | 56 iron, 43 gold, 61 redstone, 10.5 diamond, 2 glowstone … — **258.5** | 22 iron, 19 gold, 23 redstone, 5 diamond, 1 glowstone … — **117.1** | 55% |
| Mark 4 | same as III — **258.5** | 26 iron, 20 gold, 24 redstone, 5 diamond — **123.5** | 52% |
| Mark V | 60 iron, 40 gold, 59 redstone, 10.5 diamond — **261.3** | 28 iron, 20 gold, 22 redstone, 5 diamond — **131.2** | 50% |
| Mark 6 | same as III — **258.5** | 31 iron, 23 gold, 23 redstone, 5 diamond, 5 amethyst — **140.1** | 46% |
| Mark VII | 60 iron, 43 gold, 63 redstone, 10.5 diamond, 10 gunpowder — **268.4** | 34 iron, 25 gold, 24 redstone, 5 diamond, 5 gunpowder — **143.5** | 47% |

The biggest single cut is the chestplate (~143 → ~65-75): its Advanced Arc Reactor used to cost a
whole diamond block. Gametest `suitRawCostRisesWithTheMarkAndStaysCheap` recomputes this from the live
recipes and asserts Mark 1 ≤ 2 ≤ III ≤ 4 ≤ V ≤ 6 ≤ VII and every Fabricator suit < 160.

**Fabricator pacing:** an armour piece still costs the whole 50 000 buffer, but
`StarkFabricatorBlockEntity.SELF_RECHARGE_SECONDS` is now **150** (was 300) — 10 minutes of waiting per
suit instead of 20.

## 4. Stark Fabricator

`StarkFabricatorBlock` (BaseEntityBlock) + `StarkFabricatorBlockEntity` + `StarkFabricatorMenu` +
`StarkFabricatorScreen`. Slots: 9 component inputs, 1 blueprint slot, 1 output slot.

* **Access:** right-click without the Tony Stark power → **"You don't understand Stark technology."**
  and the interface does not open. `StarkFabricatorMenu.stillValid()` re-checks the power every tick,
  so it refuses a non-Tony-Stark player even if the screen were forced open.
* **Automation guard:** the Fabricator only makes fabrication progress while a player **with the Tony
  Stark power is within 6 blocks** — this blocks hopper/other automation by a non-Stark player.
* **Energy:** internal buffer, `50,000` max. Charge by right-clicking the block with a **Reactor
  Core** (+8,000) or an **Arc Reactor** (+25,000) — explicit, the item is consumed and a message
  confirms the new total. Nothing consumes an Arc Reactor by surprise. (Since "changes 22" it also
  refills itself — 150 s from empty since v0.14.21, see §3a.)
* **Fabrication time:** every recipe has a `timeTicks`; higher-tier = longer (servo motor 40t,
  repulsor 80t, helmet ~160t, chestplate ~240t). The FABRICATE bar and ENERGY gauge sync via 4
  `ContainerData` ints.
* **Blueprint gating ("changes 21"):** armour is only produced via the selected-piece path, which
  needs the matching mark's blueprint in the slot. Recipes are all `requiredTechLevel 0` now — the
  Blank Blueprint progression is the sole gate (previously also gated on operator tech level + blueprint tier).

## 5. Stark components

Basic (normal table): Copper Wiring, Metal Plating, Basic Circuit, Mechanical Parts.

Advanced (Stark Fabricator **or**, since "changes 22", a normal table — see §17p): Titanium-Gold
Alloy, Titanium-Gold Plate, Servo Motor, Micro Thruster, Repulsor, Flight Stabilizer, Targeting
Module, Stark Circuit, Suit Computer, Advanced Arc Reactor, Missile Module. Plus the Reactor Core
(table only). Modular Armor Controller / Nanotech Matrix are registered but recipe-less (no consumer).

## 6. Fabricator recipes

All in code — `com.projecthero.mod.ironman.fabricator.FabricatorRecipes` (call `all()` for the full
list). Categories: 13 advanced components, 4 later-mark blueprints (`mark_v/vii/42/50_blueprint`),
and 20 armour pieces (helmet/chest/legs/boots × 5 marks), tech-gated + blueprint-gated. Building a
mark's **chestplate** calls `TonyStark.markBuilt()` which advances the technology tree.

## 7. Iron Man armour restriction

`com.projecthero.mod.ironman.IronManArmor`:

* `canOperate(player)` = `TonyStark.hasPower(player)` — the single gate for every capability;
* `enforce(player)` runs every server tick: any Iron Man piece worn by a player **without** the power
  is moved back to their inventory (or dropped if full) with **"Stark armor rejects unauthorized
  user."** + a rejection sound. The item is never destroyed. Covers shift-click, right-click equip,
  inventory drag, and anything an external mod does to armour slots — the next tick undoes it;
* abilities/flight/HUD/energy all re-check `canOperate` server-side regardless, so even a force-held
  piece grants nothing;
* `wornSuitId`, `wornPieces`, `wearingFullSuit`, `hasHelmet`, `hasChestplate` support partial-armour
  logic (Mark 42).

## 8. Technology progression storage

"changes 21": the linear gate is now **build the whole previous mark's suit**, tracked in
`TonyStarkState.builtSuits` (bare suit id once complete + `suitId/piece` markers per piece; use
`TonyStark.builtSuitIds` / `hasFabricatedFullSuit`). `TonyStark.recordSuitPiece` records each piece
(Fabricator completion or `IronManArmorItem.onCraftedBy` for the Mark 1); the fourth promotes the
mark. `techLevel` (0 = Tony Stark, 1 = Mark III, 2 = Mark V, 3 = Mark VII) still advances alongside it
for the suit-up / summon gates, but no longer gates the Fabricator. Blueprints come only from stamping
a Blank Blueprint (`TonyStark.stampBlueprint`).

## 9. Suit summoning

v0.14.21: `IronManSuitCall` is the one call path (the legacy `IronManSuitSummonManager` is gone; `/ironman suit` →
`IronManSuitCall.commandCall`, `/ironman part` → `IronManSuitCall.callPiece`). Behaviour by `SummonType`:

* `FLYING_SET` — each missing piece (the **real stack**) leaves the platform / pack in an `IronManSuitPartEntity`
  courier that curves in and clamps on (`IronManSuitUpManager.receivePart(player, stack)`);
* `TRACKING_POD` (Mark VII) — an `IronManDeliveryPodEntity` carries the set, lands behind the player and fires the
  pieces out as couriers;
* `SUITCASE_ITEM` (Mark V) — no entities, the suit unfolds from the Mark V Suitcase.

`IronManSuitPartEntity` carries the real `ItemStack` (synced + saved in NBT; the type is no longer `noSave`). On owner
logout / dimension change / timeout it drops that stack, never destroys it. See §17u.

## 10. Suit-up / suit-down

`IronManSuitUpManager` — one implementation, `SuitUpType` selects the stage order + timing:
`MECHANICAL_REMOTE` (Marks 1-6 except V), `SUITCASE_MOVIE` (V), `REMOTE_AUTOMATED` (VII). (v0.14.21: the unused
`SUITCASE` / `MODULAR` / `NANOTECH` types are gone.) Equipping moves the real stacks between inventory / case and armour
slots server-side, one piece per stage tick; every viewer animates it from the synced `IronManSuitFx` attachment
(piece lock-on / release clocks, body pose, faceplate swing). Suit-down folds the suit back into the inventory (or the
Mark V Suitcase). See §17u.

## 11. Suit energy

`IronManEnergy` over `TonyStarkState.suitEnergy` / `suitIntegrity`, keyed by suit id. Capacity /
recharge / per-ability cost numbers come from the `IronManSuit` definition. Powers flight,
repulsors, Unibeam, missiles, HUD. At zero energy those shut down; the `ArmorMaterial` defense
remains.

The suit's own Arc Reactor tops the pool up **every tick** (`energyRecharge` is per-tick) — it is a
generator, never a battery, and never fully runs out. It does not keep up with sustained use, so a
hard fight or a long flight still drains the suit and you land / dock at a Suit Platform to recover.

| Mark | Pool | Regen | Full refill (idle) | Flight net drain | Flight time (from full) |
|---|---|---|---|---|---|
| III | 10,000 | 40 /s | ~4 min | −20 /s | ~8 min |
| V | 7,000 | 32 /s | ~4 min | −38 /s | ~3 min |
| VII | 14,000 | 52 /s | ~4.5 min | −16 /s | ~15 min |
| XLII | 15,000 | 52 /s | ~5 min | −16 /s | ~16 min |
| L | 20,000 | 68 /s | ~5 min | −8 /s | ~41 min |

Repulsor 120 · Charged Repulsor up to ~360 · Unibeam 1,500 · missile volley ~250 · each hit taken
drains `damage × 18`.

## 12. Iron Man HUD

`IronManHud` (client) — shown only with the Tony Stark power **and** a valid Iron Man helmet worn.
Removing the helmet drops the readout (unarmoured you get the CALL ARMOUR chip + the Phoenix timer).

**v0.14.21 helmet-HUD redesign** (was ~20-28 stacked text lines from (6,24) that could reach the chat):

* **Visor** — while the faceplate is *closed* (`IRON_MAN_FACEPLATE_OPEN` false): a faint edge vignette,
  thin cyan corner brackets (blink red while offline) and two tiny reticle ticks. Faceplate open = no
  frame + a VISOR OPEN chip.
* **Top-left block** (`IronManUiLayout.HUD_X/HUD_Y` = 6,6, 150 px wide): suit name (bold) + small
  `ALT / SPD` right-aligned; **EN** and **INT** Gauge bars (label + exact values on the line above a 3 px
  bar) — values keep the house format `76,67% (7667 / 10000)` (`IronManUiLayout.pct/amount`, comma
  decimals); one dim clock / day / X Z line; then *contextual* Gauges only while relevant: AIR (tank not
  full), HEAT (> 0.5), FLIGHT burst, OVERLOADED SYSTEMS / Wrist Laser timer, MISSILES reload; the Mark II
  ceiling warning / SYSTEMS FROZEN, a blinking **SUIT CRITICAL** banner (< 35%) and the wrapped SUIT
  OFFLINE line.
* **Status chips** (flow-wrapped inside the block, `IronManUiLayout.flowChips`): mob highlight ON/OFF,
  entity SCAN range (Mark VII), weapon-wheel binding (`X: …`), BLADES, MISSILES xN (ready), VISOR OPEN,
  PHOENIX mm:ss.
* **Ability strip** (`IronManUiLayout.abilityStrip`): six 26×22 slots R G X Z V C centred above the hotbar
  (bottom edge 52 px above the screen bottom, clear of hotbar / XP / hearts / armour). Each reads the worn
  suit's `abilityInSlot()`: an item icon per ability (`IronManHud.icon`), the bound key, a cooldown sweep
  (dark cover shrinking from the top; total = the remaining time the first time a new ready-at was seen)
  + whole seconds. Gold rim = active toggle (highlight, blades, flight burst); gold corner notch = the
  Mark VII weapon-wheel slot (shows the bound ability's icon); red tint = systems offline. **Hold Left Alt**
  to list the slot names above the strip.
* Target name + distance sits just under the crosshair (full HUD, visor closed). The dead
  `drawReticle` (needed TARGETING_MODE, which no suit binds any more) is gone.
* **Mark I** keeps its stripped-down retro HUD (`minimalHud`): amber palette, ten-cell segmented bars, a
  heavier eye-slit visor band, no telemetry / chips except its mob-highlight timer, plus the flamethrower HEAT.

Shared drawing kit: `client/gui/IronManGui` (palette, Slab / Gauge / Hairline bars, panels with corner
brackets, chips, ring sectors, `StarkButton`, rotating 3D suit preview on a client-only invisible armour
stand). All layout numbers are in the common `ironman/ui/IronManUiLayout` so `IronManUiV01421GameTests`
can prove every string and card fits a 320×240 scaled screen (GUI scale 4).

## 12b. Iron Man screens (v0.14.21 redesign)

| Screen | Layout |
|---|---|
| **Call Armour** `IronManSuitCallScreen` | Scrollable grid of 150×72 cards (2 columns at 320 wide). Card = rotating 3D preview of the mark, name, CHARGE / INTEGRITY Slab bars with the exact values inside and % on the label line, location (in your pack / platform N m away / on its way). Built suits the server did not list are shown greyed ("out of reach here") and cannot be picked. Mouse wheel scrolls, arrows move focus, Enter / Space calls, Esc cancels. Same `IronManCallSuitPayload`. |
| **Suit Platform** `IronManSuitPlatformScreen` | Textured 176×202 panel (`textures/gui/iron_man_suit_platform.png`). Left: holo well with the stored suit turning in 3D. Stored mark name over the four armour slots (empty ones show the vanilla armour silhouettes); RES plate (exact reserve, hover for x / 50000). Full-width CHARGE and INTEGRITY Slabs with exact values; styled Deploy (gold) / Retrieve buttons. Slot coordinates unchanged. |
| **Stark Fabricator** `StarkFabricatorScreen` | Textured 200×236 panel (`textures/gui/stark_fabricator.png`, replaces the unused 176×186 placeholder; the old 248 px panel overflowed a 240 px GUI-scale-4 screen, so the player inventory moved up: `StarkFabricatorMenu` rows y 156, hotbar y 214). Vertical energy gauge (gradient, lit top edge), progress arrow + full-width bar with a travelling glow, one status line (Fabricating n% / Ready / Not enough energy / Components missing / Idle) + exact energy. Real piece tabs (44×18 hit areas showing the suit's own piece icon; the name is in the tooltip and the checklist header). Checklist box: each input of the selected piece as a small icon + green ✓ count or red have/need (hover for the item name). View more modal restyled (bracket panel, ✓/✗ per component, still scrollable, Esc closes). Empty blueprint / output slots have tooltips. |
| **Blank Blueprint** `BlankBlueprintScreen` | Progression track Mark I → Mark VII: helmet-icon nodes on one line, lit up to the last unlocked mark, labels alternating above / below. Unlocked = click to stamp; locked = greyed + padlock, tooltip "… build the whole Mark X first". Arrows + Enter work. Same payloads. |
| **Mark VII weapon wheel** `IronManWeaponWheelScreen` | Six real ring wedges (triangle-fan sectors), hover lifts + brightens, bound option gold rim, an item icon per wedge (+ BOUND / glow ON-OFF), centre disc reads the hovered (else bound) option's name + a one-line description. Hit-test `IronManUiLayout.wheelSector` keeps the "changes 17" half-sector offset. |

Lang: `scratchpad/lang_v01421_ironman_ui.js`. Textures: `scratchpad/gen_v01421_ironman_gui.js`
(hand-rolled PNG via `scratchpad/pnglib.js`).

Notes from the in-client check (`scratchpad/IronManUiDebugHarness.v01421.java.txt`, GUI scale 4 at
1280×960 and GUI scale 2; harness removed from the source tree afterwards):

* The HUD text block is drawn in two passes (measure, then a soft dark backdrop + the text) — without
  the backdrop the dim labels vanished against a bright sky.
* The ability strip sits where vanilla shows the held-item name for ~2 s after a hotbar switch and, while
  chat is fresh, under the chat box — both are vanilla overlays and only transient.
* Found and fixed while testing: `FabricatorRecipes.all()` built its shared list lazily from whichever
  thread asked first; the integrated server and the Fabricator screen racing on it crashed the client
  with a `ConcurrentModificationException`. It is now built once under a lock and published read-only.
* Mark IV / VI / II nodes and cards use each suit's own item icons; empty Suit Platform slots show the
  vanilla armour silhouettes (block-atlas sprites).

## 13. Abilities

Router priority (`AbilityRouter`): **Thor → Iron Man → experimental power**. Iron Man takes the six
universal slots when `TonyStark.hasPower && wearing an Iron Man suit`. The ability that fires is
`activeSuit + slot -> ability id` from the suit definition, so each mark can have its own layout.

Default Mark set (all five marks, tuned per suit):

| Slot | Key | Ability |
|---|---|---|
| 1 | R | Repulsor Blast — tap for a raycast shot; **hold ≥ 2 s then release** for a Charged Repulsor (1.6× dmg, 2.5× energy, breaks ~3 blocks in a line only when shot *at* a block, nothing when shot at a mob) |
| 2 | G | Repulsor Barrier — hold to keep a frontal shield up (absorbs ~80% of damage + deflects projectiles), drains energy while held, release for a 6 s cooldown |
| 3 | X | Micro-Missiles — homing volley at nearby hostiles |
| 4 | Z | Unibeam — Arc Reactor chest beam, 5 s channel, heavy damage, light block-break (Laser-Vision-ultimate style: ≤1 soft block / ~0.5 s), reduced particles, **30 s cd** |
| 5 | V | Targeting Mode — highlights nearby hostiles, HUD assist, **2 s cd** |
| 6 | C | Suit Toggle — suit-down when worn; **Call Armour picker** when unarmoured |

The helmet also passively highlights nearby hostiles (client-only outline) whenever the worn suit has energy.

**Flight:** double-tap jump in the air while wearing a suit with boots (mirrors Thor). **Sneak +**
double-tap jump on the ground with the power but no suit worn → summon your most-developed suit (the
sneak is "changes 22": without it, two ordinary jumps inside 7 ticks called your armour in).
Double-tap jump in the air wearing a bare **Repulsor** in the boots slot flies at half the Mark 2's
speed — no suit and no Tony Stark power needed ("changes 22").

## 14. Suit definitions

`com.projecthero.mod.ironman.suit.IronManSuits`: `MARK_III`, `MARK_V`, `MARK_VII`, `MARK_42`,
`MARK_50`. Each `IronManSuit` carries id, tech level, mark number, energy capacity/recharge/flight
cost, flight speed/accel, repulsor & Unibeam & missile numbers, the 6 ability ids, `SuitUpType`,
`SummonType`, required blueprint. Adding Mark I/II/IV/VI/VIII/Hulkbuster/War Machine/… later is one
new `IronManSuits` entry + a recipe set.

## 15. Suit Platform

`IronManSuitPlatformBlock` + BE: right-click with an Iron Man piece to store it; right-click empty-handed (Tony
Stark) to open the GUI and DEPLOY the stored suit onto you; sneak-right-click to retrieve your worn suit back onto the
platform. v0.14.21: deploy and retrieve are animated, server-timed ~1.5 s sequences (§17u).
`IronManSuitPlatformBlock` + BE + `IronManSuitPlatformMenu` / `IronManSuitPlatformScreen`: right-click
opens the platform screen (four armour slots, charge / integrity / reserve readouts, Deploy and Retrieve
buttons — see §12b for the v0.14.21 layout); the block renderer shows the stored suit turning on the pad.
Deploy puts the stored suit on you; Retrieve pulls your worn suit back onto the platform. The platform
recharges / repairs the stored suit from its own reserve (Reactor Cores + trickle, max 50,000).

## 15b. Stark Sorting Station (v0.14.16, Tidy v0.14.20, repack / supplies v0.14.21)

`com.projecthero.mod.ironman.sorter`: a 54-slot station block + the Sorter Bot (non-Mob flying GeckoLib entity,
never saved). Containers = every chest / trapped chest / barrel within 10 blocks (a double chest is one target).
`SortPlan` decides what each container holds (1-3 containers: 4 coarse groups; 4+: up to 13 fine categories; a
chest keeps the theme it mostly already has). Items in transit always belong to the station BE (`carried`), so a
lost/unloaded bot or a broken station never loses or duplicates anything. The screen has two buttons (vanilla
`clickMenuButton` ids):

| Button | Id | What it does |
|---|---|---|
| **Sort** | 0 | Empties the station: files its stacks into the containers (up to 3 stacks a trip, station -> chest -> station), only ever into the stack's own category's containers (v0.14.21: no "anywhere with room" fallback). A full category adopts a spare container, else gets a chest placed from the supply; otherwise the stack stays in the station and the chest shortfall is reported. When the station is done the bot runs the same chest-to-chest pass as Tidy (`beginFinishingPass`) with the Sort's plan, so a Sort leaves the room tidy. |
| **Tidy** | 1 | Re-sorts the containers themselves (for jumbled chests). The plan is built from the containers' current contents only; every stack in a container that is not one of its category's containers is carried chest -> chest (up to 4 stacks a trip, no trip home in between) to one that is, the identical item's chest first. Split stacks are merged in every chest the bot opens, plus one compact-only visit to any other chest with split stacks. The station's own store is untouched. |

Tidy rules: only ever moves a stack to one of its **own** category's containers (no "anywhere with room" fallback);
if those are full, or its category has no container, it stays put and the end report counts it ("N stacks stayed
put"). The pickup is simulated against the destination first, so a full destination leaves the items in the source
chest. A destination broken mid-trip -> the load goes home into the station (dropped on top if the station is full).
Only one job at a time: Tidy during a Sort (or Sort during a Tidy) just reports the running job. Nothing to do ->
"The chests are already tidy." (bot not launched); only full destinations -> "N stacks are in the wrong chest, but
their category's chests are full". A trip cap (2 x stacks + 2 x containers + 16) ends the job if players keep
reshuffling chests while it runs. GameTests: `StarkSorterGameTests` (`sorterTidy*`).

### v0.14.21: repack, strict homes, spares, signs and chests

- **Repack** (`Stash.repack`): every container the bot opens (pickup, deposit, or a "service" visit) is rewritten:
  partial stacks of the identical item (`isSameItemSameComponents`) merged, everything moved to the front with no
  gaps, then a stable sort -- stacks that belong in that container first, then `SortCategory` ordinal, then
  registry id, then fullest first (`SortPlan.orderFor` / `Stash.ORDER`). A packed container repacks to itself,
  so Tidy is idempotent. `Stash.Sim` simulates against the repacked layout and deposits repack before inserting.
- **Weights** are packed slots per category (merged per distinct item), invariant under moving/merging stacks, so
  re-planning the same items always merges buckets the same way.
- **Assignment**: each bucket first gets the container holding the most of it (`hits`, then share). A leftover
  container only joins a bucket whose items need more slots than its containers have (majority bucket, then last
  designation, then biggest shortfall); otherwise it is a **spare** (`SortPlan.spares`): its contents are carried
  home and it is adopted (`adoptSpare`) by the first category that fills up. So a chest holding one lone stack of
  iron is consolidated into the Ores chest, while a genuine overflow chest keeps its share.
- **Designations** (container key -> categories) are saved in the station NBT when a job ends and passed to the next
  `SortPlan.build` as hints: they decide which *empty* container a bucket gets, so emptied chests keep their job.
- **Supply**: 6 slots (`SUPPLY_SLOTS`) beside the store -- 3 for signs (`#minecraft:signs` with a wall-sign block;
  no hanging signs), 3 for chests (`minecraft:chest`). Saved under `Supply`, dropped when the station breaks,
  shift-click routes signs/chests there first.
- **Labels** (`SorterSupply`): a waxed wall sign, front text `[Stark]` + the bucket's short sign names
  (`sort_category.*.sign` / `sort_group.*.sign`, max 3 lines, "+N more"). Placed in an air block on the
  container's front, then outer sides, then back (`canSurvive` checked); one per double chest. A chest with any
  wall sign attached (or a standing sign on top) counts as labelled; only signs whose first line is `[Stark]` are
  re-written (category changed, or "Spare"). Player signs are never touched.
- **New chests** (`SorterSupply.chestSpot`): air, within 10 blocks and the scan filter, solid ground (or on top of
  a container), nothing solid above (lid), not in front of a container or the station, not the dock. Preference:
  continuing the row of the category's own chests, any managed row, on top of a container, beside the station
  (also used when Sort finds no containers at all). Always a single chest facing like its neighbour.
- **Needs** (`computeNeeds` / `refreshNeeds`): chests = overflow slots (station stacks + misplaced stacks
  simulated into their homes) minus spare free slots, /27; signs = unlabelled non-spare containers with a free face
  + needed chests; plus containers with no free face. Recomputed on menu open, when the store/supply changes and
  every 40 ticks while viewed; synced in data slots 5-9; shown in the supply column ("Add 3 signs") and sent in
  chat on every button press and at the end of a job.
- GameTests: `sorterTidyRepacksAGappyChest` (the user's screenshot), `sorterRepackIsStableAndConserves`,
  `sorterTidyConsolidatesALoneStack`, `sorterOverflowChestIsAllowed`, `sorterNeverFilesIntoAnotherCategorysChest`,
  `sorterTidyIsIdempotent`, `sorterSortThenTidyIsStable`, `sorterLabelsChestsWithSigns`,
  `sorterReportsMissingSigns`, `sorterPlacesAChestFromTheSupply`, `sorterPlacesAFirstChestBesideTheStation`.
## 15c. Block models: Fabricator + Suit Platform (v0.14.21)

All assets come from `scratchpad/gen_v01421_ironman_blocks.js` (re-run it after any change; with a directory argument
it also renders orthographic previews). Models are authored in block pixels with the **front on the north side**;
both blocks now have a `facing` blockstate (front faces the player who placed it) and model-matched, facing-rotated
hit boxes (`IronManBlockShapes`). Old placed blocks load as `facing=north`.

* **Stark Fabricator** — hybrid. The static workbench is a JSON model (`models/block/stark_fabricator.json`, one 64x64
  atlas `textures/block/stark_fabricator.png`, also the particle texture): gunmetal cabinet with panel seams, a red
  Stark plate with an arc-reactor eye on the front, red/gold side bands, a cyan light strip under the gold-edged
  worktop, a dark-glass work plate, a parts tray, an arm turret, and a rear gantry with a tilted display. On top, a
  **GeckoLib rig** (`StarkFabricatorBlockEntity` is a `GeoBlockEntity`, client `StarkFabricatorRenderer`;
  `geo/stark_fabricator.geo.json`, `animations/stark_fabricator.animation.json`,
  `textures/machine/stark_fabricator_rig.png` + `_glowmask`) draws the red/gold robotic welding arm, a holographic
  Iron Man helmet with scan rings over the plate, and emissive overlays for the light strips and display.
  * `working` blockstate: set by the server while a fabrication is in progress (10-tick linger so queued pieces do
    not flicker). Idle = arm parked over the tray, slow hologram; working = arm swings over the plate and welds in
    strokes (IK-solved in the generator), hologram spins, scan ring sweeps, a pulse runs along the front strip,
    electric sparks at the emitter (`animateTick`), light level 7 -> 10.
  * The item model is a separate JSON (`models/item/stark_fabricator.json`) = the body plus the arm in its parked
    pose, so the inventory icon shows the arm too.
* **Iron Man Suit Platform** — JSON only (`textures/block/iron_man_suit_platform.png`): an octagonal floor plate (pixel
  octagon) with a cyan light ring and core on a gold-trimmed base, and a 1.75-block rear gantry (posts with cyan
  inner strips, a light-striped back spine with a Stark crest, shoulder clamps, a red/gold header with an overhead
  lift emitter). The racked suit stands at `IronManSuitPlatformRenderer.DISPLAY_Y_OFFSET` (0.2 blocks, clear of the
  plate); the generator's `SUIT_Y_OFFSET` must match. The hit box is the plate + the gantry's lower block; the gantry
  above one block high is visual only.

## 16. Commands (op 2)

```
/ironman power grant|revoke [player]
/ironman tech <0-3> [player]                                 # v0.14.21: 3 = Mark VII, the highest any suit needs
/ironman energy <suitId> <amount> [player]
/ironman suit <suitId> [player]                              # put a suit on through the real call / suit-up path
/ironman part <suitId> <helmet|chestplate|leggings|boots>    # fly one piece in (pack, else nearest loaded platform)
/ironman status [player]
```

## 17. Creative mode

All Iron Man items are in the existing **Superheroes** creative tab (Arc Reactor, Stark Fabricator,
Suit Platform, Reactor Core, Mark V Suitcase, blueprints, all components, all 5 marks × 4 pieces).
Grabbing armour from creative does **not** grant Tony Stark — use `/ironman power grant`.

## Calling / recalling a suit — the exact steps

**Requirements:** the Tony Stark power, and the suit's four pieces existing somewhere you can reach —
your inventory, or an Iron Man Suit Platform within ~60 blocks in a loaded chunk. (You also need to
have developed the suit — fabricate its **chestplate** at least once — OR simply be holding its
pieces.)

**Store a suit on a platform**
1. Place an **Iron Man Suit Platform**.
2. Right-click it with each armour piece in hand (or right-click empty-handed to open its GUI and
   drag the four pieces into the H / C / L / B slots).

**Recall it to your body — two ways:**
- **From anywhere nearby:** press **C** (the suit-toggle key) with an empty-ish context, *or*
  double-tap the jump key while standing on the ground. The pieces launch off the platform / out of
  your inventory and fly to you like Mjolnir, assembling boots→legs→chest→helmet. The HUD shows
  `CALL ARMOUR [C]` when this is available.
- **Standing at the platform:** right-click it empty-handed → the GUI opens → click **DEPLOY**. This
  also tops the suit's energy up from the platform's charge and repairs it to 100% integrity.

**Take it back off:** press **C** while wearing it (folds back into your inventory), or sneak +
right-click the platform (pulls the worn suit straight onto it).

If **C does nothing:** you either don't have the Tony Stark power, haven't fabricated/obtained the
suit, or the pieces aren't within ~60 blocks. Check with `/superhero status`.

## 17u. v0.14.21 flight revamp (look and feel only)

Speeds and energy drain are **unchanged** (`flight.DirectionalFlightModel#ironMan`, `IronManFlight.flightCostPerTick`;
`DirectionalFlightGameTests.everyFlightKeepsItsSpeeds` + the new `ironManFlightTickKeepsItsDrain`). Everything below is
client-side, driven only by state every client already has for every player it can see (synced `IRON_MAN_FLYING` /
`REPULSOR_BOOTS_FLYING`, the synced sprint flag, worn boots, the synced `TonyStarkState.supersonicUntil`, and the
interpolated position), so other players in multiplayer see the same thing. The pure numbers live in common code,
`ironman/IronManFlightLook` (jet profile per state, hard/soft landing rule, timings, sound curves), and are gametested
(`DirectionalFlightGameTests.ironManFlightLookJetsAndLandings`).

**Server.** `IronManFlight.tick` no longer sends the feet-centre FLAME / END_ROD particles every tick nor the supersonic
CLOUD puff; `setFlying` no longer plays BREEZE_JUMP / BREEZE_LAND. `RepulsorBoots` likewise. All of it is client FX now.

**Poses** (`client/ironman/IronManFlightPose`, hooked into `HumanoidModelMixin#projecthero$ironManFlightPose` right after
the generic flight pose, before the move poses). The body lean still comes from `FlightPoseHelper` (hover 0 / slow 25 /
sprint 90 / back -12 deg); Iron Man is no longer in its `heroOnly` arms-pinned branch nor Thor's arm raise. Limbs are
eased per tick by tier weights (0.22/tick) and an in/out blend (0.25/tick), interpolated across the partial tick:

| State | Arms | Legs |
|---|---|---|
| hover | a little out from the body, hands (repulsors) down; small stabilising micro-motion | slightly apart, gentle sway |
| slow forward | angled back, palms back | trailing |
| sprint (with the 90 deg lean) | tight back along the sides, palms back -- the classic pose | together, fine flutter |
| backward | thrown forward, palms out, braking | forward |
| take-off (first 8 ticks) | brief crouch: knees tucked, arms pulled back, released into the flight pose | |
| hard landing | three-point superhero landing: right knee + right fist down, left foot planted, left arm swept back, head bowed then lifting; ~0.55 s hold (`LANDING_IN 2 / HOLD_END 13 / END 19` ticks) | |

Hard vs soft landing (`IronManFlightLook.isHardLanding`, evaluated when the flight flag drops, over the last 8 ticks of
position deltas because the flag arrives after touch-down): grounded **and** (descent >= 0.45 b/t **or** speed >= 0.7
b/t with descent >= 0.12 b/t). A plain Sneak descent (0.375 b/t) is soft; switching off mid-air is never a landing.
During the landing `FlightPoseHelper` snaps the lean upright (x0.35 per tick) so the kneeling pose isn't tipped over.
**Mark 1** keeps no lean and gets its own clunky upright stance (arms stiffly out for balance, heavy sway + servo
jitter). **Repulsor Boots** wearers only get straight legs (so the boot jets line up) -- no arm pose, no landing pose.

**Thruster FX** (`client/IronManFlightFxClient`). Nozzle positions are rederived from the model each tick / frame: yaw
`180 - bodyYaw`, entity scale (Mark 1 1.25), the lean, `scale(-1,-1,1)`, 0.9375, `translate(0,-1.501,0)`, then the limb
pivot + the exact rotations `IronManFlightPose.targets` poses it with, then the hand end (px (-/+1, 11), the suit's
dilated arm) / boot sole (px (0, 13); 12.3 for bare boots). The jet points along the limb's +Y. Per nozzle:
* particles every tick (boots FLAME, palms ELECTRIC_SPARK; sprint / supersonic add END_ROD sparkle / SMOKE; Mark 1 boots
  are smoky orange rockets and it has no palm jets), spread back along the tick's path at speed so trails stay
  continuous;
* at render (`WorldRenderEvents.AFTER_ENTITIES`, `RenderType.lightning()` additive, fullbright): a camera-facing glow
  billboard + a wider halo, and a tapering jet ribbon (`BeamDraw.segment`) whose length follows
  `IronManFlightLook.jetLength` (hover 0.32 / forward 0.55 / back 0.45 / sprint 1.05 / supersonic 1.6 blocks x scale).
Your own palm particles are skipped in first person (they sit under the camera), and nothing is billboarded for yourself
in first person.

**Take-off burst**: a downward blast from every nozzle, a POOF + block-dust ring on the ground below (if within 4
blocks), `ironman_takeoff` (breeze wind burst, pitched down) + `ironman_takeoff_kick` (firework launch). **Hard
landing**: POOF + block-debris ring, an EXPLOSION puff, a ground shock ring (expanding additive annulus, 3.4 blocks),
`ironman_land_impact` (mace heavy smash) + `ironman_land_boom` -- no block damage. **Soft landing / switched off
mid-air**: `ironman_power_down`. **Supersonic engage**: a shockwave ring across the flight direction (5 blocks), a CLOUD
ring, `ironman_sonic_boom` (warden sonic boom, pitched down) + boom; then while supersonic a CLOUD vapour collar every
tick and a faint additive vapour-cone mesh around the body.

**Thruster loop** (`client/sound/IronManThrusterSoundInstance`): three looping instances per flier (own and others),
started by `IronManFlightFxClient` once it sees the flier airborne, each following the player, fading in over 8 ticks and
out over 6 (then stopping itself): `ironman_thruster_roar` (fire loop, volume 0.35-0.9 / pitch 0.7-1.35 with speed),
`ironman_thruster_whine` (breeze whirl, strongest hovering; silent on the Mark 1) and `ironman_thruster_wind` (elytra
loop, silent hovering, swelling in with speed). Bare Repulsor Boots play at 60 %. No ffmpeg / oggenc on the dev machine,
so every event re-pitches vanilla sound files in `sounds.json` (added by `scratchpad/lang_v01421_ironman_flight.js`,
which also adds the five `subtitles.projecthero.ironman_*` keys). The events are client-only (never registered) -- the
SoundManager resolves them by id.
## 17u. v0.14.21 revamp — suit-up & fixes

### The synced clock: `IronManSuitFx`

`com.projecthero.mod.ironman.suit.IronManSuitFx` — attachment `projecthero:iron_man_suit_fx`, synced to **every**
viewer, never persisted (`ModAttachments.IRON_MAN_SUIT_FX`). The persisted `TonyStarkState.CODEC` is untouched (it was
at the 16-field record limit; nested since round two, see 17v). Fields: one start tick per armour slot + an "assembling" bit mask (lock-on vs release),
a reveal `style` (plates / from-case), the body-pose clock (`poseStart`, `poseTicks`, `poseKind`) and `faceplateAt`.
Written only server-side (`markPiece`, `startPose`, `endPose`, `faceplateMoved`). A stale clock is ignored outside its
window, so it can never hide a piece equipped some other way later.

### Timeline (ticks)

| Sequence | Stages (piece reaches / leaves the body) | Per-piece animation | Total |
|---|---|---|---|
| Inventory suit-up, Marks 1-6 / VII (`MECHANICAL_REMOTE` 50, `REMOTE_AUTOMATED` 45) | boots 15%, legs 35%, chest 60%, helmet 85% of the stage timeline | lock-on 12 | timeline + 12, then the 10-tick faceplate beat |
| Mark V from the case (`SUITCASE_MOVIE` 80) | chest 10%, legs 45%, boots 60%, helmet 85% | lock-on 12, radial from the case in the right hand | 92 + beat |
| Suit-down (any) | helmet 10%, chest 35%, legs 60%, boots 80% (Mark V: helmet, boots, legs, chest) | release 10, **then** the piece leaves the slot | timeline + 10 |
| Suit Platform deploy | lift-off 2 + 6·i, on the body 8 later (boots, legs, chest, helmet) | flight 8 (BER) + lock-on 12 | 28 (~1.4 s) |
| Suit Platform retrieve | release 6·i, onto the rack 10 later (helmet first) | release 10 + flight home 8 (BER) | 36 (~1.8 s) |
| Courier | launch stagger 16 per piece; curved flight, decelerating over the last 3.5 blocks | lock-on 12 on arrival | distance-dependent |
| Mark VII pod | descend 40 → open 10 → one piece every 8 (as couriers) → close 10 → ascend 30 | lock-on 12 per piece | ~125 for 4 pieces |

### What each animation does

* **Build-on reveal** (`client.ironman.IronManSuitReveal`, hooked in `SuperheroArmorRenderer.getRenderType`): while a
  piece's lock-on clock runs it is drawn with an `ArmorSweepReveal` frame restricted to that piece's bones — the plates
  sweep up the limb behind a white-hot edge (`0xFFFFF4D6`) and an orange spark trail (`0xFFFF9A2E`); the release plays it
  backwards. The Mark V case style spreads each piece outward from the right hand instead (radial sweep). Built lazily
  per (mark geometry, texture, piece).
* **GeckoLib clips** (`IronManArmorItem.registerControllers`, controller `suit`): `suit_lock_on` (0.6 s — helmet drops
  in and the faceplate swings shut, chest/back plates slide in from front/back, shoulders and gauntlets swing in, thigh
  plates/knees/boots snap home, the arc reactor pops on) and `suit_release` (0.5 s — the reverse, plates popping outward)
  are new clips in `crimson_vanguard.animation.json` (generated by `scratchpad/gen_v01421_ironman_suitup.js`). The old
  `assemble` / `disassemble` clips were authored as one 1.8 s / 1.4 s whole-suit shot with 14-28 px travel (and
  `disassemble`'s helmet keyframes are out of order), which reads wrong per piece, so they stay unused. `helmet_open` /
  `helmet_close` are wired as-is for the H faceplate (verified: every mark's `faceplate` bone has the same pivot and
  cube as crimson_vanguard's). The controller is driven purely by the synced clock (no trigger packets, no stack ids):
  armour without a GeckoLib stack id is keyed per (wearer entity id, slot), so each wearer animates independently.
* **Faceplate** (H): the helmet stays visible while the visor swings (`helmet_open`, 10 ticks), then retracts as
  before; closing shows the helmet and swings the visor down (`helmet_close`). The suit-up's last beat closes it too.
* **Body pose** (`client.ironman.IronManSuitUpPose`, one self-contained `HumanoidModelMixin` TAIL injection): suit-up —
  arms out and slightly raised, chest lifted; receive (couriers / pod inbound) — arms wider, chin up; Mark V — right arm
  holds the case forward, left arm out; suit-down — arms ease out then drop; faceplate close — head dips and returns
  (open — tips back). Eased in 6 / out 10 ticks.
* **Couriers** (`IronManSuitPartEntity` + `IronManSuitPartRenderer`): quadratic Bezier bowed up and to one side
  (alternating per entity id), accelerating out of launch and decelerating to a crawl; over the last 40% of the curve
  the tumble stops, the piece turns to the owner's body yaw and grows from 85% to full size, arriving exactly on its
  slot; then the clamp (sparks + `ironman_clamp`) and the lock-on reveal.
* **Suit Platform** (`IronManSuitPlatformBlockEntity` sequence + `IronManSuitPlatformRenderer`): the rack stops
  spinning and turns to face the player; each piece gets its own armour stand that travels on an arc between rack (62%
  size) and body (full size, body yaw), then locks on — or breaks away and flies home.
* **Mark VII pod** (`IronManDeliveryPodEntity`, GeckoLib `geo/iron_man_delivery_pod.geo.json`, clips `fly` / `open` /
  `close`): red-and-gold capsule with a clamshell front, glowing interior ribs and thrusters (glowmask). Comes in from
  26 blocks up (or straight off a loaded platform), tracks and lands 2.4 blocks behind the player (mid-air ok), opens,
  fires the pieces onto the player as couriers, closes and flies away.
* **Mark V suitcase** (`MarkVSuitcaseItem` is now a `GeoItem`; `client.ironman.MarkVSuitcaseRenderer` /
  `MarkVSuitcaseLayer`; `ItemInHandLayerSuitcaseMixin`): a red / silver / gold 3D briefcase when held or dropped (the
  inventory keeps the flat sprite via the `item/mark_v_suitcase_icon` model). During a case suit-up it springs open in
  the right hand (front shell drops, side panels swing out, handle folds, core glows), the suit spreads out of it, and
  it shrinks into the gauntlet at the end; folding, it grows open out of the gauntlet and snaps shut. Posed
  procedurally from the synced clock; the ordinary right-hand held-item draw is skipped for that window.

### Item identity (bug fix)

The real `ItemStack` (enchantments, names, every component, charge stamps) now travels end to end: inventory suit-up /
suit-down; couriers (`receivePart(player, stack)`; the stack is synced, saved in the entity's NBT, and the stack dropped
on failure); platform deploy / retrieve / calls (`takePieceStack`); death recovery (dock or `StarkSuitReturnQueue`,
whose `Pending` record now carries `stacks`, encoded with registry ops; older records still rebuild fresh pieces);
Protocol Phoenix (it uses the same paths); and the Mark V case, which now stores the four real stacks in the vanilla
`minecraft:container` component (`SuitcaseContents`). A case with no component (crafted / creative / pre-0.14.21) still
unfolds a fresh Mark V; an *emptied* modern case never does. The fold hands the (empty) case over first and puts each
piece into it in the tick it leaves the body; the unfold takes each piece out of the case in its stage tick and uses
the case up when it is empty — so case + body always hold the whole suit (no loss on logout, no dupe).

### Suit Platform safety

Server-timed; a piece moves between rack and body in exactly one tick. The sequence stops where it is if the player
goes more than 6.5 blocks from the rack, logs out, dies, loses the power, or the block is broken (`onRemove` aborts
it, then the contents drop as before). While it runs the player's suit-up state is held (no other suit-up / -down /
call can start over it) and calls cannot take pieces off the rack. The sequence itself is never written to disk.

### Sounds

No OGG encoder exists in the build environment (`ffmpeg` / `oggenc` / `sox` not found), so like `DarkseidSounds` the
new events (`IronManSounds`: `ironman_servo`, `_clamp`, `_release`, `_faceplate_seal`, `_faceplate_open`, `_power_up`,
`_thruster`, `_pod_land`, `_case_unfold`) are layered, re-pitched vanilla files in `sounds.json` (piston, crafter,
netherite armour-equip, lodestone lock, vault eject/insert, iron trapdoor, beacon power, firework launch, breeze charge,
mace smash, heavy core). Swap in real audio by pointing an event at `projecthero:ironman/<name>`.

### Other fixes / cleanup

* Client: the airborne double-tap that toggles Iron Man flight now `return`s instead of also falling through to the
  Green Lantern ring-flight check.
* Beams: `IronManAbilities.broadcastBeam` sends to `IronManBeamRecipients.recipients` — the shooter, their trackers,
  and every player whose eyes are within 128 blocks of the beam segment (a far target used to never see the beam).
* Removed: `IronManSuitSummonManager`; `SuitUpType.SUITCASE/MODULAR/NANOTECH` and the nanotech branches;
  `SummonType.NANOTECH_ONBOARD/FLYING_MODULAR`; the unbound `TARGETING_MODE` ability (the `targetingUntil` field stayed
  only because `IronManHud` still reads it); the vestigial `IronManSuit` fields `energyRecharge`, `repulsorEnergyCost`,
  `unibeamEnergyCost`, `damageReduction` (builder: `energy(capacity, flightCost)`, `repulsor(damage)`,
  `unibeam(damage)`); stale Mark 42 / 50 comments; `/ironman tech` is now 0-3.
* `modular_armor_controller` / `nanotech_matrix` stay registered (old worlds) but are off the creative tab and tagged
  `c:hidden_from_recipe_viewers`.
* GameTests: `IronManSuitUpV01421GameTests`.

## 17v. v0.14.21 round two — glow, blades, gauntlets, boots, Homing Missiles, save format

Assets: `scratchpad/gen_v01421_ironman_round2.js` (idempotent; re-run after repainting a mark). Lang:
`scratchpad/lang_v01421_ironman_round2.js`. GameTests: `IronManRound2GameTests`.

**Glowing suit details** (`client/ironman/IronManSuitGlowLayer`, added to `SuperheroArmorRenderer`). Every mark has
`textures/armor/<mark>_glowmask.png` (64x64), generated from the mark's own cyan texels (eye slits, arc reactor, soles,
Mark V palms) plus white texels inside a cyan cluster (Mark 2's eye cores), plus the palm repulsor (centre 2x2 of the
base-arm "down" rect: right (49-50, 17-18), left (41-42, 49-50)) for marks 2..VII. Mark 1 has no cyan: only its white
reactor dot glows, at 55%. The layer re-renders the piece in `RenderType.eyes(glowmask)` (additive, fullbright, so the
mask's transparent texels are written as rgb 0). It is deliberately **not** GeckoLib's `AutoGlowingGeoLayer`, which cuts
the glow texels out of the base texture in GPU memory -- the same `mark_*.png` is sampled by the first-person arm, the
`ArmorSweepReveal` copies and item previews. Reveal: a piece whose lock-on / release clock is running does not glow;
its lights come on once it is locked in. Open faceplate / slot visibility apply to the glow pass too (same bone pass).

**Mark V blades.** `geo/mark_v.geo.json` has `right_blade` / `left_blade` bones (children of the arm bones: red housing
on the outer side of the gauntlet, a silver blade, a tapered tip; pivot at the top) textured from a blade swatch painted
into the unused top-left 8x8 of `mark_v.png`. `SuperheroArmorRenderer#renderRecursively` hides / Y-scales them each
frame from `client/ironman/IronManBladeClient` -- one value per player, stepped per client tick from the synced
`IRON_MAN_BLADES` flag by `ironman/IronManBladeLook` (6 ticks each way, smoothstep, interpolated over the partial
tick). The first-person gauntlet draws the same blade. Gameplay (`IronManBlade`: +4 melee, no block placing, retract on
shutdown) is unchanged; the old server particle line along the look vector is gone.

**Per-mark first-person gauntlets** (`client/ironman/IronManFirstPersonGauntlets`, called from
`SuperheroFirstPersonArm` for the seven marks; other sets keep the shared arm). Drawn in the vanilla arm's animated
space with `client/ironman/IronManBoxes` (vanilla box-UV layout, UV size independent of geometry) from the mark's own
texture panels (base arm, arm shell, shoulder, gauntlet, a trim strip). Mark 1: oversized boxy gauntlet, thick cuff,
slab shoulder, rivets, knuckle bar. Mark 2: slim smooth gauntlet with an end cap, domed shoulder. Mark III: outer
forearm plate, knuckle plate, two-tier shoulder; Mark 4 adds a wrist ring and splits the forearm plate. Mark V: slim
with the blade-housing ridge (+ blade). Mark 6: forearm fins, stepped shoulder. Mark VII: three bands, wrist missile
pod, swept shoulder flap. Marks 2..VII: fullbright palm repulsor (`textures/misc/repulsor_palm_glow.png`) on the palm
and the fist end, lit once the chestplate's reveal is done. The FP arm follows the chest build-on reveal texture.

**Repulsor Boots worn model** (`client/ironman/RepulsorBootsLayer`, a player feature layer). A Repulsor in the boots
slot shows a silver shell, red ankle ring and side pods, toe cap with a gold stripe, heel thruster and dark nozzle sole
on each foot (`textures/armor/repulsor_boots.png`, 8x8 material swatches). While `REPULSOR_BOOTS_FLYING` the soles
light up (flickering fullbright glow). The sole bottom is leg-local y `RepulsorBootsLayer.SOLE_Y` = 12.6 px and
`IronManFlightFxClient` now puts the bare-boots jet nozzles at `SOLE_Y + 0.1` (was 12.3), so the jets leave the soles.

**Homing Missiles** (Mark VII weapon wheel, **added** as a 7th wedge after Micro-Missiles -- nothing replaced; the wheel
screen and hit-test are generic over the sector count). `IronManAbilities.HOMING_MISSILES`: 4 missiles
(`HOMING_MISSILE_COUNT`), launched one per 4 ticks through the same volley ticker as Micro-Missiles, same energy
(`missileEnergyCost` x the suit multiplier), own 8 s cooldown (`mark_vii/homing_missiles`, which the HUD strip reads;
icon = target block). Lock-on (`homingTarget`): the living thing directly under the crosshair within 30 blocks with a
clear line, if `HeroTargets.canHarm` allows it (an aimed shot); otherwise the `HeroTargets.isHostile` mob closest to the
crosshair inside a 30-degree cone within 30 blocks (`HOMING_RANGE`, `HOMING_CONE_DEG`). Each missile fans out a little,
then steers hard onto the lock (`IronManMissileEntity.withTarget`, turn 0.35/tick after 4 ticks, speed >= 1.1); if the
lock dies it falls back to the old mild nearest-hostile homing. With no lock it still fires (action bar says so).
Micro-Missiles stay dumb-fire (their wheel description, which wrongly said "homing", now says so). Wedge labels fit a
1/7 wedge at every tested GUI size (`IronManRound2GameTests.homingMissilesIsAWheelOption`).

**`TonyStarkState` save format.** The persisted `CODEC` is now
`Codec.withAlternative(NESTED_CODEC, LEGACY_FLAT_CODEC)`: it always writes
`{core:{has_power, tech_level, built_suits, active_suit}, suit:{energy, integrity, air, mob_highlight_on,
weapon_wheel_choice}, cooldowns:{ability_ready_at, timed_flight_until, wrist_laser_until, overload_until,
phoenix_ready_at}, misc:{flamethrower_heat, wrist_laser_spent}}` and reads that or, failing it, the old flat 16-field
shape. `core` is the one required group -- that is what makes an old flat save fail the nested decoder and fall back.
Add new persisted fields to a group with room (each is far below the 16-field limit) or a new optional group.
`SYNC_CODEC` wraps `CODEC` unchanged; `IronManSuitFx` is untouched. The dead `targetingUntil` / `targetingActive`
(and the two resets in `IronManSuitTicker`) are removed.

Not verifiable by gametest (rendering): the glow at night, the blade geometry in third / first person, the per-mark
gauntlet shapes and palm glow, and the boots model + jet alignment.

## 17t. v0.11.13 (Mark 1 / Mark 2 rebalance)

*(Note: this reference doc's changelog sections skip a large gap between v0.6.2 below and here --
several released versions of Iron Man changes through v0.11.12 were never backfilled into this file.
The per-mark stat table in §17l is stale for the same reason; treat it as a historical snapshot, not
current numbers. `IronManSuits.java` is the source of truth for current per-mark stats.)*

**Mark 2 rebuilt into a real second-tier suit, not just a cheap Mark 1 clone.** All explicit user
request:
* **Diamond-level armour** (`IronManArmorMaterials.MARK_2`): defense/toughness now identical to Mark 1
  / vanilla Diamond (boots 3, leggings 6, chestplate 8, helmet 3, toughness 2.0), still iron-repaired.
* **Integrity 420 → 1500**, **worn energy regen 1.0 → 12/s**, **new worn armour regen 5/s** (Mark 2 had
  none before), **Suit Platform regen now its own flat override (24 energy/s + 15 integrity/s)**
  instead of the generic 0.1%-of-pool formula every non-Mark-1 suit still uses.
* **New standing Resistance I** (`IronManSuit.resistanceAmplifier`) while a full, powered Mark 2 suit
  is worn -- refreshed every tick in `IronManPassives`, same pattern as the helmet's Night Vision.
* **Damage model matches Mark 1's now** (`IronManDamage.mitigateMark2`, new): the wearer takes the
  **full** raw hit, and the plating separately bleeds **80%** of that same amount off integrity, in
  parallel -- not a 100-vs-80 split of one pool. No fire/projectile immunity carve-out (that stayed
  Mark-1-only).
* **A real Charged Repulsor** added to R (`repulsorSlot`, `chargedRepulsor`): a quick tap still does
  the existing forced 1 s spin-up then an ordinary blast (now **20** energy, down from the shared 80),
  but holding the key >= 2 s before releasing instead fires a Charged Repulsor directly (**50** energy)
  -- no separate windup needed, the hold itself is the charge. The windup also gained a periodic
  rising-pitch hum (`tickRepulsorWindup`), not just a single blip when it starts.
* **Rocket (G)** now costs its own **50** energy (was the shared 300) and its blast **actually breaks
  blocks** (see below).
* **Unibeam (Z)** now costs its own flat **300** total per channel (was the shared 700).
* **Mob Highlight (V)** now drains **1 energy/sec** while toggled on (`tickMark2MobHighlightDrain`,
  ticked from `IronManSuitTicker`) -- auto-clears if the helmet comes off or energy runs dry. Was
  free and indefinite before, like every non-Mark-1 suit.
* **Custom Fabricator recipe** (`FabricatorRecipes.mark2ArmorSet`, replacing the shared `armorSet`
  generator entirely): built from the basic `METAL_PLATING` instead of the advanced
  `TITANIUM_GOLD_PLATE` every other mark uses -- Helmet (6 plate/2 servo motor/1 targeting module/3
  Stark circuit/1 suit computer), Chestplate (6 plate/4 servo motor/1 repulsor/1 flight
  stabilizer/4 Stark circuit), Leggings (6 plate/4 servo motor/2 Stark circuit/1 flight stabilizer),
  Boots (6 plate/2 micro thruster/2 repulsor/1 flight stabilizer/2 servo motor/1 Stark circuit). The
  Stark Fabricator's "View more" info panel and the ingredient list it shows are both fully dynamic off
  these definitions, so no separate menu/UI text needed updating.

**Mark 1 changes**, also explicit user request:
* **Damage model changed from a 50/50 reduction to a full-hit-plus-parallel-integrity-cost model**
  (`IronManDamage.mitigateMark1`): the wearer now takes the **full** raw hit (was halved), and the
  plating separately bleeds **80%** of that same amount off integrity (was 50%), in parallel. Fire/
  projectile immunity is unchanged.
* **Rocket (Z)**: damage 15 → **25**, energy cost 300 → **100**, cooldown 20 s → **25 s**, and the
  blast now **breaks blocks** (see below). The flamethrower's 2.5-dmg-every-half-second cadence was
  already exactly that (vanilla hit-invulnerability naturally throttles a per-tick `hurt()` call to
  roughly twice a second) -- no code change needed there, just confirmed by reading `AbilityHelpers.hurt`.
* **HUD heat-bar bug fixed**: the HEAT gauge had silently stopped showing for Mark 1 -- root cause was
  the v0.11.12 "minimal HUD" strip-down (`IronManHud`'s `minimalHud = "mark_1".equals(suitId)`) gating
  the heat bar behind `!minimalHud`, which hides it specifically for the ONE suit that has the
  Flamethrower bound to a slot at all. `minimalHud` is meant to hide altitude/speed/clock/target-
  readout/Phoenix status, not this suit's own core ability feedback -- the gate was simply removed from
  that one bar. It still only shows while heat is actually above ~0 (building up while firing, or
  venting back down afterwards), same as before.

**Shared change**: `IronManMissileEntity` gained an opt-in `withBreaksBlocks()` (a TNT-style
`Level.ExplosionInteraction.MOB` blast, gated on `HeroConfig.abilityTerrainDamage` like every other
terrain effect in the mod) -- used by the Mark 1/2 Rocket only; Micro-Missiles is untouched and still
never damages terrain.

`./gradlew build` green, **288** gametests pass (6 new: Mark 2 diamond armour, Mark 2 Resistance,
Mark 2 Unibeam cost, Mark 2 Charged Repulsor cost, Mark 2 mob-highlight drain, Mark 2 rocket cost; the
old `rocketAbilityDealsFifteenDamage` was renamed `mark1RocketCostsOneHundredEnergy` and now also
checks the energy spent). NOT in-world playtested. The damage-split arithmetic itself is verified by
code review only, same long-standing limitation as every other Iron Man mitigation change in this
file -- see the comment block above the "---- Mark 1 / Mark 2 ----" gametest section for why a mock
`ServerPlayer` never reaches `player.hurt()` through Fabric's `ALLOW_DAMAGE` event.

## 17s. v0.6.2 (platform regen → flat 0.1%/s)

**Suit Platform regen is now a flat 0.1% of the mark's pool per second**, for both energy and armour.
`IronManEnergy.PLATFORM_FRACTION_PER_SECOND` = 0.001. `platformEnergyPerSecond(suit)` =
`suit.energyCapacity() * 0.001`; `platformIntegrityPerSecond(suit)` = `suit.maxIntegrity() * 0.001`.
A full refill or repair of any mark takes ~1000 s (~16.7 min) regardless of pool size. This replaces
the "changes 22" / v0.6.1 model (3x → 5x the worn Arc Reactor).

`IronManEnergy.PLATFORM_REGEN_MULTIPLIER` and `IronManSuit.platformArmorRegenPerSecond()` /
`.platformArmorRegen(...)` (the Mark 1/2 platform-only fallback) are **removed** — the flat fraction
gives every mark, prototypes included, a positive rate directly.

## 17r. v0.6.1 (platform + early-mark recharge buff)

**Suit Platform regen bumped from 3x to 5x the worn passive regen** — superseded by 17s above.

**Mark 1 / Mark 2 worn recharge nudged up.** `energyRegen` Mark 1 `0.5 → 0.7`/s, Mark 2 `0.75 → 1.0`/s
(`IronManSuits`). Small quality-of-life buff for the prototype suits; the per-mark progression is
unchanged in shape (III still 1.5/s, VII still 3.0/s).

## 17q. v0.6.0 ("changes 22" follow-up)

**Suit Platform regen is now 3x the worn Arc Reactor.** `IronManEnergy.PLATFORM_REGEN_MULTIPLIER` =
3.0. `platformEnergyPerSecond(suit)` = `suit.energyRegenPerSecond() * 3`;
`platformIntegrityPerSecond(suit)` = `suit.armorRegenPerSecond() * 3`, falling back to a new per-mark
`IronManSuit.platformArmorRegenPerSecond()` when the mark has no worn self-repair at all (Mark 1 =
0.05/s, Mark 2 = 0.07/s — otherwise they would have come out at 3 × 0). The old flat
`PLATFORM_{ENERGY,INTEGRITY}_FRACTION_PER_SECOND = 1.5%/s` constants are gone.

*Deploy no longer force-repairs.* `IronManSuitPlatformBlockEntity.deployTo` used to
`setIntegrity(max)` on the way out, which made the repair rate — whatever it was set to — purely
cosmetic: any wrecked suit came off the rack in perfect condition. It now hands over the integrity
the rack has actually restored.

**Flamethrowers set fire to what they are sprayed at.** The surface-fire code had been present and
dead the whole time: it was gated on `HeroConfig.abilityFireSpread`, which defaults to `false`. That
flag exists for *incidental* fire (an Inferno fireball's trail, a Flame Dash wake), so both
flamethrowers — `IronManAbilities.flamethrowerFireOk` and Pyrokinesis's new `streamFireOk` /
`placeStreamFire` — now depend only on `abilityTerrainDamage` (`AbilityHelpers.canGrief`). The fire
is still a `TempBlocks` placement, so it is temporary and only ever replaces air.

**Mark 1 flamethrower heat capacity = 300** — `flamethrowerHeat(0.6f)` against the shared 500 base,
about 7.9 s of stream before it overheats. The Mark VII's 1.5× bar is untouched.

**Fire barely wears the armour.** `IronManDamage` special-cases `DamageTypeTags.IS_FIRE`: integrity
bleed ×0.05 and energy cost ×0.10. The wearer's own damage share is unchanged, so the suit protects
them exactly as well as before. Burning is a 1-damage tick twice a second that never stops until you
leave the flames, so the flat 90%-to-integrity rule was charging ~1.8 integrity/second for standing
in a campfire — thirty seconds alight cost a Mark 1 nearly a fifth of its entire pool.

**Stark Fabricator recharges itself, and one armour piece costs the whole buffer.**
`StarkFabricatorBlockEntity.selfRecharge` refills `FabricatorRecipes.MAX_ENERGY` (50 000) over
`SELF_RECHARGE_SECONDS` = 300 s, every tick, with no operator and no fuel. Every armour recipe's
`energyCost` is now `MAX_ENERGY`, so building a piece drains the machine flat and the next one cannot
start until it has charged all the way back up. Reactor Cores / Arc Reactors in an input slot still
work — they are now a way to skip part of that wait rather than the only power source. Component
recipes are unchanged and still cheap, so the whole tree is not paced by the recharge.

**Component costs eased.** Ingredient lists are untouched; the yields went up, on *both* routes (the
§17p table recipes and the Fabricator), so neither is cheaper than the other:

| component | was | now |
|---|---|---|
| `titanium_gold_alloy` | 1 | **2** |
| `titanium_gold_plate` | 2 | **3** |
| `servo_motor`, `micro_thruster`, `stark_circuit`, `repulsor`, `flight_stabilizer`, `targeting_module`, `missile_module` | 1 | **2** |
| `suit_computer` | 1 | 1, but 3 → **2** stark circuits |
| `advanced_arc_reactor` | 1 | 1, but 4 → **3** plates and 4 → **2** glowstone |
| `metal_plating` / `copper_wiring` / `basic_circuit` / `mechanical_parts` (table) | 2 / 4 / 2 / 2 | **3 / 6 / 3 / 3** |

A full suit needs 30 plates, 12 servo motors and 10 Stark circuits, so at one-per-run the components,
not the suit, were the entire build.

**Component textures redrawn** — `scratchpad/gen_changes22_textures.js`. The originals were all
~110-byte flat rounded squares in slightly different greys and golds, i.e. genuinely
indistinguishable in a nine-slot Fabricator tray. All 18 are now separated on three axes at once:
silhouette (disc / cylinder / flat plate / stacked sheets / board / missile rack / cone / gyroscope
…), hue, and the placement of a single bright accent.

**The accidental suit summon is fixed.** The *ground* half of the double-tap-jump gesture
(`Project HeroModClient.handleDoubleJump`) fired on any two jump presses inside 7 ticks — which is just
running, or mashing jump to get moving after a knockback. That is the "an armour gets called to me
when I get hit" report. It now requires **sneak + double-tap jump**. (A double-tap that fires nothing
also leaves the timer at 0, so in a rapid string of jumps *every* tap counted as a double-tap.) The
airborne flight half was always safe — ordinary movement never puts two jump presses in the air
inside the window — and is unchanged. `C` remains the ordinary way to call the armour.

**The Repulsor is wearable and fireable.** `IronManItems.REPULSOR` is now a
`RepulsorItem extends ArmorItem` (`Type.BOOTS`, material `IronManArmorMaterials.REPULSOR_BOOTS` —
1 armour point, **empty layer list** so nothing is drawn over the player). Same item id, same stack
size, same recipes, same role as a Fabricator component.

* *Flight* — `RepulsorBoots`, deliberately its own system with its own `REPULSOR_BOOTS_FLYING`
  attachment rather than another branch inside `IronManFlight`. Suit flight is defined entirely in
  terms of a worn `IronManSuit` (its speed, acceleration, drain multiplier, altitude ceiling,
  manual-flight flag, per-tick energy spend, integrity failure and systems lockout); none of that
  exists for a pair of boots. Exactly half the Mark 2's numbers: speed 0.5, acceleration 0.04, capped
  at 15 m/s. Airborne double-tap jump toggles it, it lands itself on ground contact, and
  `clearStale` takes the ability bit back if the boots come off mid-air. No energy cost and no Tony
  Stark power required.
* *Blast* — right-click and hold. After `WINDUP_TICKS` = 20 (the Mark 2's own `repulsorWindup(20)`,
  with the same spark-and-rising-hum spin-up) it calls `IronManAbilities.fireHandRepulsor`, which
  routes into the very same `fireRepulsor` the suits use at the Mark 2's damage — so it is literally
  the Mark 2's shot, beam, knockback and glass-breaking. 8-tick item cooldown afterwards, matching
  the Mark 2's post-shot cooldown. Deliberately **no durability**: durability would force the max
  stack size to 1, and the armour recipes consume Repulsors two and four at a time.

**Zombie Raid / Gravebound HUD moved to the bottom-right** (`RaidHud`) — see
`docs/ZOMBIE_RAID_REFERENCE.md`.

## 17o. v0.4.13 ("changes 21")

**Blank Blueprint progression.** Mark blueprints are no longer crafted or fabricated individually.
The one craftable blueprint is `IronManItems.BLANK_BLUEPRINT` (`projecthero:blank_blueprint`,
`2 paper + basic_circuit + redstone`, no Tony Stark gate). Right-click it (`BlankBlueprintItem.use`,
sneak or not) → server sends `IronManBlueprintPickerPayload` → client opens `BlankBlueprintScreen`
listing every mark with a blueprint in progression order. Pick → `IronManBlueprintChoicePayload` →
`TonyStark.stampBlueprint` re-validates, consumes one blank, hands over that mark's blueprint.

*Linear gate:* `IronManItems.MARK_ORDER` = `mark_1, mark_2, mark_iii, mark_4, mark_v, mark_6,
mark_vii`. A mark's blueprint unlocks only once **every piece of the previous mark** is built
(`IronManItems.prerequisiteSuit`). Piece tracking piggybacks on `TonyStarkState.builtSuits`: it now
holds both the bare suit id (a fully-built mark, what `hasBuilt` / suit-up / summon read — filter with
`TonyStark.builtSuitIds`) **and** per-piece markers `suitId + "/" + pieceName`. `TonyStark.recordSuitPiece`
adds a marker on every Stark Fabricator armour completion and (for the table-built Mark 1)
`IronManArmorItem.onCraftedBy`; the fourth marker promotes the bare id in and advances `techLevel`.
Kept as one set so the persistence codec stays at its 16-field ceiling. `markBuilt` still exists as
the "whole suit at once" convenience for commands/tests.

*Tech-level gate removed from the Fabricator.* Every component and armour recipe in
`FabricatorRecipes` is now `requiredTechLevel 0` — the Blank Blueprint chain (which mark's blueprint
sits in the slot) is the sole progression gate. This also fixes a latent dead-end: components were
tech-1 and armour tech-1..3, but the only way to raise tech was to fabricate a chestplate, which
needed those very components. Armour is produced **only** via the selected-piece path (which requires
the matching mark's blueprint in the slot); the fallback loop in `find()` never returns armour.
`armorSet`'s per-mark int is now `timeTier` (fabrication-time scaling only). Removed:
`mark_v_blueprint` / `mark_vii_blueprint` Fabricator recipes, `modular_armor_controller` /
`nanotech_matrix` recipes (their Mark 42/50 consumers are gone), all `data/projecthero/recipe/
mark_{1,2,4,6,iii}_blueprint.json`.

`TonyStark.grant` now hands a **Blank Blueprint** instead of the Mark III Blueprint.

**Mob highlight follows the helmet.** `IronManSuitTicker.tick` drops the `mobHighlightOn` toggle the
instant an Iron Man helmet is no longer worn (`suitId == null || !IronManArmor.hasHelmet`), not just
when the whole suit powers down — so docking a suit into a Suit Platform, or pulling only the helmet,
turns it off immediately.

**Stark Fabricator screen widened + wraps.** `imageWidth 176 → 200`, `imageHeight 222 → 248`, player
inventory pushed to `y+164` (menu slot coords match). The status line (`idle_hint` / `pick_piece` /
`fabricating`) is now `font.split`-wrapped to the panel width instead of overflowing the right edge;
the energy gauge moved to the right margin and the piece/`View more` button band moved down so it no
longer collides with the inventory label.

## 17p. v0.4.14 ("changes 22")

**Every advanced Stark component now has a normal crafting-table recipe**, in addition to its Stark
Fabricator recipe. Goal: every Iron Man armour set is fully survival-obtainable *and* discoverable in
a recipe viewer (JEI/EMI never saw the in-code `FabricatorRecipes`, so servo motors / suit computers
/ etc. looked uncraftable). New `data/projecthero/recipe/*.json` (all `category: misc`, no Tony Stark
gate — components were always "deliberately open" per §on `IronManCrafting`):

| component | table recipe |
|---|---|
| `titanium_gold_alloy` | shaped: 3 gold + 3 iron + 1 metal_plating |
| `titanium_gold_plate` (×2) | shapeless: 2 titanium_gold_alloy + 1 metal_plating |
| `servo_motor` | shapeless: 2 mechanical_parts + 1 copper_wiring + 1 redstone |
| `micro_thruster` | shapeless: 1 servo_motor + 2 metal_plating + 1 blaze_powder |
| `stark_circuit` | shapeless: 2 basic_circuit + 1 gold_ingot + 1 redstone |
| `repulsor` | shaped: 2 redstone_block + 1 diamond + 1 stark_circuit |
| `flight_stabilizer` | shaped: 1 amethyst_shard + 2 servo_motor + 1 micro_thruster |
| `targeting_module` | shapeless: 1 basic_circuit + 1 stark_circuit + 1 ender_eye |
| `suit_computer` | shaped: 3 stark_circuit + 1 targeting_module + 1 amethyst_shard |
| `advanced_arc_reactor` | shaped: 4 titanium_gold_plate + 2 glowstone + 2 stark_circuit + 1 diamond_block |
| `missile_module` | shaped: 2 stark_circuit + 4 gunpowder + 1 servo_motor + 2 metal_plating |

Ingredient lists mirror the `FabricatorRecipes` entries (with `advanced_arc_reactor` trimmed from
`4 plate + 2 circuit + 1 diamond_block + 4 glowstone` = 11 items to fit a 3×3). The Fabricator
recipes are unchanged and still valid — the table is just a second route.

*What still needs the Stark Fabricator:* the Mark 2 / III / 4 / V / 6 / VII **armour pieces**
themselves (only the Mark 1 is table-craftable, by design — the Fabricator + Blank Blueprint chain is
the intended progression gate). `modular_armor_controller` / `nanotech_matrix` stay recipe-less —
dead items with no consumer since the Mark 42/50 removal.

**Full survival chain (verified):** Arc Reactor (table) → Tony Stark power → Reactor Core (table) →
Stark Fabricator + Suit Platform (table) → charge the Fabricator with Reactor Cores/Arc Reactors →
basic + advanced components (table) → Blank Blueprint (table), stamp Mark 1 → build all 4 Mark 1
pieces (table) → Mark 2 blueprint unlocks → fabricate Mark 2 … linear through Mark VII.

## 17n. v0.4.12 ("changes 20")

**No suit is craftable without the Tony Stark power.** The Fabricator and the suit platform already
refused to run for a player failing `TonyStark.hasPower`, but the four Mark 1 pieces ship as plain
`minecraft:crafting_shaped` recipes, so anyone with iron and a basic circuit could build the starter
suit at a workbench — and then only discover it was useless when `IronManArmor.enforce` ejected it.
`com.projecthero.mod.ironman.IronManCrafting` is the policy (`requiresTonyStark` = any
`IronManArmorItem` or the Mark V suitcase) and `mixin.CraftingMenuMixin` enforces it by blanking the
result slot.

The mixin targets `CraftingMenu.slotChangedCraftingGrid`, the single static helper vanilla routes
**both** grids through — the 3×3 table and the player's 2×2 inventory grid — so one injection covers
hand-placed, shift-click and recipe-book crafts. It is server-only, so the gate is authoritative. It
injects at TAIL (vanilla has already resolved the recipe, so the check is a plain "is this a suit?"
question about a finished stack) and therefore has to repeat vanilla's client resync —
`setRemoteSlot` + a fresh `ClientboundContainerSetSlotPacket` — or the client would keep showing a
phantom suit in a slot the server considers empty. A throttled action-bar message
(`message.projecthero.ironman.craft_requires_tony_stark`, once per 60 ticks) explains the empty slot.

**Deliberately still open:** the Arc Reactor (it is what *grants* the power — gating it would make the
whole tree unreachable), the components, the blueprints and the suit platform block.

**Faceplate now actually reveals the wearer's skin.** "changes 19" hid only the geo's `faceplate`
bone, but `crimson_vanguard`'s `helmet` bone is two complete 8×8×8 boxes around the head (inflate 0.2
and 0.45), each with its own north face, while `faceplate` is just a thin 0.45-deep slab in front of
them. Hiding the slab peeled off the outer visor and left two solid helmet walls over the face, so
pressing **H** looked like it did nothing. `SuperheroArmorRenderer.setHelmetHidden` now hides the
`helmet` shell *and* the `faceplate` visor together, i.e. the helmet retracts.

Both bones are named explicitly rather than hiding their shared `armorHead` parent: GeckoLib's
`GeoBone.setHidden` only suppresses that bone's *own* cubes (children need `setChildrenHidden`), and
`armorHead` is a cubeless container bone, so hiding it would have done nothing.

`PlayerModelMixin` also stops suppressing the skin's second layer (`model.hat`) while the helmet is
retracted — that suppression exists to stop the overlay fringing through the fitted suit shell, and
with no shell on the head it was stripping the wearer's hair/hat off the face the toggle just exposed.

**Tests:** `ironManSuitIsNotCraftableWithoutTonyStark` (drives a real `CraftingMenu` through the
mixin, before and after `TonyStark.grant`), `ironManCraftingGateCoversSuitsOnly`,
`ironManFaceplateOnlyOpensWhileArmored`. 107 gametests total.

---

## 17m. v0.4.10 ("changes 19")

**Protocol Phoenix cooldown 5 min → 20 min** (`ProtocolPhoenix.COOLDOWN_TICKS = 1200 * 20`).

**Helmet faceplate** (`com.projecthero.mod.ironman.IronManFaceplate`) — pressing **H** while wearing any
Iron Man armour opens/closes the visor, revealing the pilot's face. `ModAttachments.IRON_MAN_FACEPLATE_OPEN`
(synced to all, non-persistent). Client H handler (`Project HeroModClient.handlePowerSelect`) sends
`IronManActionPayload.TOGGLE_FACEPLATE` instead of opening the power wheel when an IM piece is worn.
`SuperheroArmorRenderer.applyBoneVisibilityBySlot(HEAD)` hides the geo's helmet bones while open (see
"changes 20" -- hiding `faceplate` alone left the helmet shell covering the face).
`IronManSuitTicker` calls `IronManFaceplate.reconcile` each tick so it closes when the armour comes off.

**C auto-equips an inventory suit** — `IronManSuitCall.autoEquipInventorySuit`: plain **C** while
unarmoured, when a whole suit's four pieces are in your inventory, puts it straight on (prefers the
last active suit, then highest tech). **Sneak + C** (or no complete suit in the pack) still opens the
call-armour picker. Wired in `AbilityRouter` (the SLOT_6 intercept) and `IronManAbilityManager`.

**Stark Fabricator "View more" is a proper modal** (`StarkFabricatorScreen.renderInfoOverlay`) — the
screenshot bug was `GuiGraphics.fill` drawing *behind* the item icons (which render at a high Z). Now:
pushed to Z 400 (above icons), a **fully opaque** frame, the screen dimmed behind it, header + `[x]`,
and the body is **scissored + scrollable** (mouse wheel; a scrollbar appears when needed) so content
can never spill past the frame. Any click closes it. `imageHeight` was already bumped to 222 in
"changes 18" so the piece buttons no longer overlap the output slot.

**Only the Mark 1 is table-craftable** — deleted `data/projecthero/recipe/iron_man_mark_{2,iii,4,6}_*.json`;
added Fabricator armour recipes for `mark_2` / `mark_4` / `mark_6` in `FabricatorRecipes.armorSet` (all
tech 0, so their existing tier-0 blueprints gate them, not a tech level). New
`FabricatorRecipes.hasArmorRecipes(suitId)` (true for everything except `mark_1`) drives the
Fabricator's per-piece picker visibility.

**Mark 5 Blade ability** (`com.projecthero.mod.ironman.IronManBlade`, `IronManAbilities.BLADE`) —
replaces Flare in slot 3 (X). Toggle: press X to extend energy blades from both gauntlets, X again to
retract. While extended: **+4 melee** (fixed-id transient `ATTACK_DAMAGE` modifier, reconciled each
tick in `IronManBlade.tick`), **cannot place blocks** (`Project HeroMod`'s `UseBlockCallback` vetoes a
`BlockItem` use, read via the synced `ModAttachments.IRON_MAN_BLADES` so it fires client-side too),
and bright `ENCHANTED_HIT`/`END_ROD` blade FX from both gauntlets. Retracts on suit shutdown / power
loss / overload / zero energy. HUD shows `GAUNTLET BLADES ENGAGED (+4 melee)`. (Blades are an FX
representation, not new model geometry — a follow-up if the user wants real cubes.)

**Mark 7 texture** rebuilt cleanly (`scratchpad/gen_changes19.js`): reconstructed from the untouched
`mark_iii.png` + the silver-forearm pixels from `mark_6.png`, then **only true-red pixels** dimmed
(× 0.55 R, red clearly dominant + both other channels low). Gold, silver and blue are exactly as
before — the "changes 18" script had over-dimmed the gold.

`./gradlew build` green (**104** gametests), `runClient` boot clean. New tests:
`changes19FaceplateAndBlades`, `changes19CallAndFabricatorRecipes`; updated `markVRedefinedAsMovieMarkFive`,
`mark1And2AreRegisteredAndCraftableAtTechZero`.

## 17l. v0.4.9 ("changes 18")

**Per-mark stat rebalance** (`IronManSuits`). New `IronManSuit` fields + builders:
`energyRegen(perSecond)` (worn Arc Reactor trickle, now a flat per-mark figure via
`IronManEnergy.tickRecharge` — the old 0.1%-of-capacity fraction is gone), `armorRegen(perSecond)`
(worn self-repair of integrity — **new**: Mark III+ now slowly self-repair while worn, via
`IronManEnergy.tickArmorRegen`, called next to every `tickRecharge`; Mark 1/2 = 0, platform only),
`flightDrain(multiplier)` (scales the new tiered flight cost).

| Mark | Integrity | Energy cap | Energy regen /s | Armour regen /s | Melee (`strength`) | Flight drain × |
|---|---|---|---|---|---|---|
| 1 (`mark_1`)   | 300 | 3000  | 0.5 | – | 4 | 0.55 (also `flight(0.75,0.055)` = 25% slower) |
| 2 (`mark_2`)   | 420 | 4500  | 0.75 | – | 5 | 0.9 |
| 3 (`mark_iii`) | 600 | 7500  | 1.5 | 0.03 | 6 | 1.0 |
| 4 (`mark_4`)   | 700 | 8500  | 1.8 | 0.04 | 6 | 1.05 |
| 5 (`mark_v`)   | 550 | 8000  | 1.6 | 0.03 | 5 | 0.90 |
| 6 (`mark_6`)   | 800 | 10500 | 2.6 | 0.05 | 7 | 1.1 |
| 7 (`mark_vii`) | 950 | 12000 | 3.0 | 0.06 | 7 | 1.15 |

**Tiered flight energy cost** (`IronManFlight.flightCostPerTick`): hover **10/s**, walk-flight (holding
a movement key) **20/s**, sprint-flight **30/s**, supersonic **45/s** — each × the mark's
`flightDrainMultiplier()`. This *replaces* `flightEnergyCost` + `energyCostMultiplier` for flight
(`SUPERSONIC_FLIGHT_COST_MULTIPLIER` is now vestigial).

**Suit Platform regen ×5**: `IronManEnergy.PLATFORM_ENERGY/INTEGRITY_FRACTION_PER_SECOND` 0.003 →
**0.015** (1.5%/s each).

**Ability energy costs** (`IronManAbilities`): repulsor **80** (was 50), charged repulsor **250** (was
100) and now **3× the damage** (`CHARGED_REPULSOR_DAMAGE_MULTIPLIER`, was ×1.6), unibeam **700**
total (was 500), rocket **300** (was 400), repulsor shield **40/s** (`BARRIER_ENERGY_PER_TICK` 8 → 2),
wrist laser **500** (was 400).

**Micro-Missiles fire one at a time** (`IronManAbilities.tickMicroMissiles`, ticked from
`IronManSuitTicker`): one press pays the whole volley up front and arms
`TonyStarkState.pendingMissiles` (transient, in `copy()`); a missile launches every
`MICRO_MISSILE_STAGGER_TICKS` (4) along the *current* look vector, so each lands and blasts on its own
i-frame window instead of the salvo hitting once for 8 damage. Cleared by `shutDownAllSystems`.

**Night Vision now clears the instant the helmet comes off** — `IronManSuitTicker.clearHelmetNightVision`
(called from `applyHelmetOptics` when the helmet is missing, and from the no-suit / no-power early
returns). Our optic is the only ambient, no-icon, ≤400-tick Night Vision so it never nukes a potion/beacon one.

**Mob highlight** (`EntityGlowMixin.projecthero$ironManHighlightDecision`): while the viewer wears an Iron
Man helmet the mixin now takes an **authoritative yes/no** for every enemy (or, for Mark 6/7's
coloured glow, every living entity) inside the suit's `targetScanRange` — returning `false` kills a
stale outline the frame the toggle goes off. Still purely per-viewer (client-only, no server GLOWING).

**HUD** shows the in-game clock + day count (`TIME hh:mm   DAY n`) under the coordinate line.

**Arc reactor** on the bare chest sits ~0.08 higher (`ArcReactorLayer` translate 0.30 → 0.22).

**Flying suit pieces render the 3D model** — `IronManSuitPartRenderer` now equips the piece on a
reusable invisible client `ArmorStand` and renders that (same trick as `IronManSuitPlatformRenderer`),
so a courier looks exactly like the worn GeckoLib suit instead of a spinning inventory sprite.

**Stark Fabricator per-piece picker** — a suit blueprint (`BlueprintItem.suitId()`, wired for every
mark) in the blueprint slot shows a button column (Helmet / Chest / Legs / Boots) on the right of the
`StarkFabricatorScreen`; picking one (`StarkFabricatorMenu.clickMenuButton` 1–4 →
`StarkFabricatorBlockEntity.setSelectedPiece`, synced as ContainerData index 4) makes that piece's
armour recipe the only armour recipe `FabricatorRecipes.find(container, tech, selectedPiece)`
considers, and the 3×3 grid shows its components as greyed **ghost** stacks in the blanks. A **View
more** button opens a panel listing every component in words plus the finished suit's stats. (Only
`SUIT_IDS` — mark_iii/v/vii — have Fabricator armour recipes, so the picker only shows for those.)

**Power Suppressor** (`com.projecthero.mod.item.PowerSuppressorItem`, `ModItems.POWER_SUPPRESSOR`) — a
survival-craftable item (`AAA / ESE / AAA` = 6 amethyst shard + 2 echo shard + fermented spider eye).
**Sneak + use** permanently strips **every** power at once: `TonyStark.revoke`, the full
`/heropower revoke all` teardown for experimental powers, and `Worthiness.setScore(0)`. Refuses while
an Iron Man suit is worn; a plain (non-sneak) use just prints the confirm hint. In the Superheroes tab.

**Mark 7 texture** (`textures/armor/mark_vii.png`) regenerated with the red pulled down (× 0.60 R on
red-dominant pixels) — `scratchpad/gen_changes18.js` (hand-rolled PNG codec; also emits
`power_suppressor.png`).

`./gradlew build` green (102 gametests), `runClient` resource reload clean. New/updated tests:
`microMissilesFireOneAtATime`, `changes18SuitTuning`, `powerSuppressorStripsEverything`;
`markThreeIntegrityPoolIsFifteenHundred`, `ironManSuitGrantsStrengthProRata`,
`markFourIsRegisteredWithWristLaser`, `markVRedefinedAsMovieMarkFive`,
`wornReactorTrickleIsTenthOfAPercent`, `markSixAndSevenAreConfigured`,
`repulsorEnergyCostsAreFlatFiftyAndHundred`, `supersonicFlightRework`,
`suitAutoRecoversToPlatformOnDeath`, `carriedOnlySuitIsRecoveredOnDeathToo`.

## 17k. v0.4.8 ("changes 17")

**Protocol Phoenix** — a passive emergency-resurrection ability on the Tony Stark power
(`com.projecthero.mod.ironman.ProtocolPhoenix`). Fired from `ServerLivingEntityEvents.ALLOW_DEATH`
(before `recoverSuitOnDeath`): if a Tony Stark player would die, is **not** already in a complete
suit, and Phoenix is off its **5-minute** cooldown (`TonyStarkState.phoenixReadyAt`, in the synced
CODEC), the death is cancelled Totem-of-Undying style (`level.broadcastEntityEvent(player,(byte)35)`
+ `TOTEM_USE`). The player enters an incapacitated state (`TonyStarkState.phoenixEmergencyUntil`,
**transient** — server-authoritative, client driven by action-bar messages): invulnerable
(`IronManDamage` returns false), Slowness 251 / Blindness / Weakness / mining-fatigue refreshed every
tick, pinned in place, jump/attack/break/place/use/interact all vetoed via Fabric interaction
callbacks + an `AbilityRouter` guard (`ProtocolPhoenix.incapacitated`). `selectBestSuit` reuses
`IronManSuitCall.assemblableSuits` (= the C-key picker's `gather`) filtered to ≥25% energy / ≥10%
integrity and ranked mark → integrity% → energy% → sum; inventory suit → `beginSuitUp`, platform suit
→ `IronManSuitCall.execute`. On the full suit equipping: end the state, restore to **5 hearts**, drain
**20% of max energy** + **10% of max integrity**. 30 s failsafe (extends while a suit-up / pending
call is genuinely in progress) → `failSafe` releases the player alive at ≤3 hearts. Cooldown starts on
activation and survives death/relog/dimension/armour-drop. HUD shows `PHOENIX: mm:ss` / `PHOENIX:
READY`. `clearEmergency` on JOIN / AFTER_RESPAWN.

**Mark XLII & Mark L removed** — suit defs, armour items, materials, blueprints, Fabricator recipes,
creative entries, textures and lang all gone. `SUIT_IDS` = `mark_iii/mark_v/mark_vii`; tech tree tops
at Mark VII (tech 3). 7 suits total.

**New `IronManSuit` fields ("changes 17"):** `airTankSeconds` (0 = none), `helmetNightVision`
(default true, `.noHelmetNightVision()`), `fallDamageFraction` (0 = immune; Mark 1 = 0.20),
`waterMoveMultiplier` (Mark 1 = 0.5), `ceilingFreeze` (Mark 2), `flamethrowerHeatMultiplier`
(Mark 7 = 1.5), `coloredEntityGlow` (Mark 6 & 7).

* **Night vision** — every helmet **except Mark 1's** grants ambient Night Vision while worn
  (`IronManSuitTicker.applyHelmetOptics`).
* **Air tanks** (`IronManAirTank`, ticked from `IronManSuitTicker`; `TonyStarkState.suitAir` 0..1 in
  the CODEC): Mark 2 = 120 s, Mark III/IV = 180 s, Mark VI = 300 s, Mark VII = 420 s. Drains while
  `isUnderWater()` (holds `setAirSupply(max)`), refills **3×** as fast out of water. HUD `AIR` bar
  when below full.
* **Mark 1** — heavy prototype: **80% (not 100%)** fall-damage reduction with boots
  (`IronManDamage` fall branch reads `fallDamageFraction()`), **can't breathe underwater** (no
  tank), **50% slower in water** (Slowness III while a full suit is worn + `isInWater()`).
* **Mark 2** — hitting the Y150 ceiling now also gives **Freeze for 4 s** + a hard systems lockout
  (`IronManSuitTicker`, `TonyStarkState.ceilingFreezeUntil` / `wasAboveCeiling`, `setTicksFrozen`).
* **Mark 6 / Mark 7 "coloured entity glow"** — the highlight toggle now outlines **every** nearby
  entity, coloured by type in `EntityGlowMixin` (new `getTeamColor` `@Inject`): hostile **red**,
  player **yellow**, else **blue**. Every other mark's toggle stays hostiles-only, default colour.
  Mark 7's old always-on `passiveHighlight(50)` is gone — it's a **weapon-wheel toggle** now.
* **Mark 7 model** — `textures/armor/mark_vii.png` regenerated = `mark_iii` + silver forearms only
  (no knee line, no triangular reactor — unlike Mark 6). `scratchpad/imglib.js` (hand-rolled PNG
  RGBA codec) + a 2-rect blit from `mark_6.png`.
* **Mark 7 weapon wheel** — 6 sectors now: the 5 X-bindings **+ an "Entity Glow" toggle**
  (`IronManAbilities.ENTITY_GLOW_TOGGLE` / `WEAPON_WHEEL_SECTORS`; `ModNetworking` special-cases it →
  `toggleEntityGlowFromWheel`). **Mouse hit-test fixed** — `IronManWeaponWheelScreen` adds
  `+ π/sectors` to the hover angle so each drawn box sits in the middle of its own wedge (was half a
  wedge off).
* **Mark 7 supersonic flight reworked** — `SUPERSONIC_TICKS` 5 s → **20 s**; **early cancel** on a
  second press; **10 s cooldown starts on end** not activation (`IronManAbilities.endSupersonic`,
  called on re-press / 20 s timer in `IronManFlight.tick` / `shutDownAllSystems`); burns **3×** flight
  energy (`SUPERSONIC_FLIGHT_COST_MULTIPLIER`); disengage message `supersonic_offline`.
* **Mark 7 flamethrower heat bar** — the weapon-wheel flamethrower now shows the HUD `HEAT` bar
  (gated on `suit.hasWeaponWheel()` too), and its ceiling is **1.5×** the base
  (`IronManAbilities.flamethrowerMaxHeat(suit)`).
* **Sprint-fly hand particles** — `IronManFlightFxClient` reworked with an end-to-end derivation of
  where the hand actually renders (arms-straight-down + `PlayerRendererMixin`'s `Axis.XP(-lean)` about
  the feet): `hand = feet + right·±0.32 + up·(0.74·cos lean) + forward·(0.74·sin lean)`, ×
  `player.getScale()`.
* **Low-suit alert** — `IronManHud` blinks `SUIT CRITICAL — FIND COVER AND REPAIR THE ARMOUR` while
  energy% or integrity% is under **35%**.
* **Creative** — a dedicated **Iron Man** tab (`ModCreativeTab.IRON_MAN`, key `projecthero:iron_man`)
  holding *everything* Iron Man: Arc Reactor, the Stark Fabricator + Suit Platform blocks, the
  reactor core + Mark V Suitcase, every blueprint, every component (basic → advanced), and all the
  armour pieces Mark I → II → III → … via `IronManItems.armorPiecesByMark()` (sorts
  `IronManSuits.all()` by `markNumber()`). **v0.6.19:** none of this appears in the main Superheroes
  tab any more — the Iron Man tab is the only place to find it. (Superseded the old armour-only "Iron
  Man Armour" tab.) The C-key call picker (`IronManSuitCall.gather`) still uses the same mark order.

`./gradlew build` green (**104** gametests), `runClient` boot clean. New tests:
`markOneIsAHeavyPrototype`, `airTanksAreConfigured`, `supersonicFlightRework`,
`protocolPhoenixCooldownState`; updated `ironManSuitsAndRecipesRegistered` (9→7),
`markThreeIntegrityPoolIsFifteenHundred`, `advancedMarksCarryMobHighlightInsteadOfTargeting`,
`markSixAndSevenAreConfigured`.

## 17j. v0.4.7 ("changes 16")

**Suit Platform display is authoritative now.** `IronManSuitPlatformBlockEntity` implements
`getUpdateTag` + `getUpdatePacket`, and `afterContentsChanged()` calls `sendBlockUpdated`, so the
Hall-of-Armor render always matches what is really racked -- it used to only refresh when the GUI was
opened (menu slot sync), so a called-away suit stayed on show and a full platform read empty after a
relog. `store()` routes through `afterContentsChanged()` too.

**Suit Platform renders the armour model, not item icons.** `IronManSuitPlatformRenderer` now puts
the four stored pieces on an invisible, slowly-rotating client `ArmorStand` above the platform (one
reusable stand per BE in a `WeakHashMap`), so the GeckoLib suit shows exactly as it does when worn.

**Charge rates are percentage-based ("changes 16"):**
* Worn Arc Reactor trickle = **0.1% of the suit's energy capacity / second**
  (`IronManEnergy.WORN_TRICKLE_FRACTION_PER_SECOND`); never touches integrity.
* Suit Platform = **0.3% of energy capacity / second** + **0.3% of max integrity / second**
  (`SUIT_ENERGY_FRACTION_PER_TICK` / `SUIT_INTEGRITY_FRACTION_PER_TICK`, mirroring
  `IronManEnergy.PLATFORM_*_FRACTION_PER_SECOND`). Integrity still repairs **only** on a platform.

**Per-suit energy cost multiplier.** `IronManSuit.energyCostMultiplier()` (default 1.0) scales every
ability + flight spend, via new `IronManAbilities.pay()` / `canPay()` helpers and inline factors in
the per-tick drains (barrier / unibeam / flamethrower / flight). Mark 6 & 7 = 0.6.

**Per-suit flight speed cap.** `IronManSuit.maxFlightSpeedMps()` (0 = uncapped) -- `IronManFlight.tick`
clamps horizontal velocity to it. Mark 6 & 7 = 30 m/s (with `flight(1.7, 0.14)` so they can reach it).

**Mark 4:**
* Overload lockout **15 s &rarr; 30 s** (`OVERLOAD_TICKS`).
* Overloaded suit can no longer fire a repulsor: the overload guard in `IronManAbilities.trigger`
  now blocks **both** key edges (a blocked press used to still leave a release that fired the shot).
* HUD overload readout is a label line + a full-width `wideBar` below it -- the long
  "OVERLOADED SYSTEMS" text and the bar no longer overlap. Same treatment for the wrist-laser timer.

**New suit -- Mark VI (`mark_6`):** tech-0 craftable primitive (in `PRIMITIVE_SUIT_IDS`). Mark 4's
loadout **minus the wrist laser** (= Mark III's ability set). Integrity **450**, energy **14,000**,
360-degree Repulsor Shield (`IronManSuit.fullBodyShield()` -- `IronManDamage` skips the frontal-arc
gate, `tickBarrier` draws a sphere), 30 m/s flight, 0.6x energy cost. Grey-accented + silver-forearm +
knee-line + **triangular arc reactor** texture (repaint at the geo's reactor UV [22,21]). New material
`IronManArmorMaterials.MARK_6`, blueprint `MARK_6_BLUEPRINT`, four normal-table recipes.

**Mark VII redefined as the movie Mark 7** (kept id `mark_vii` + `MARK_VII_BLUEPRINT` + Fabricator
tech-3 slot, so the III&rarr;V&rarr;VII&rarr;XLII&rarr;L tree is intact). Now: integrity **550**,
energy **15,000**, 7 total unarmed damage (`strength(6)`), full-body shield, 30 m/s flight, 0.6x
energy cost, and:
* **Passive entity highlight** -- `IronManSuit.passiveHighlight(50.0)`. `EntityGlowMixin` outlines
  *every* living entity within 50 blocks whenever the suit is powered -- no V toggle, no hostile-only
  filter.
* **Weapon wheel** -- slot 5 (V) = `IronManAbilities.WEAPON_WHEEL` (sends `IronManWeaponWheelPayload`
  S2C with `""` &rarr; opens `IronManWeaponWheelScreen`, a radial menu); slot 3 (X) =
  `WEAPON_WHEEL_SLOT`, which dispatches to whatever `TonyStarkState.weaponWheelChoice` (synced,
  default `micro_missiles`) holds. Options: **micro missiles / flamethrower / wrist laser / rocket /
  supersonic flight** (`WEAPON_WHEEL_OPTIONS`). Picking sends `IronManWeaponWheelPayload(id)` C2S,
  re-validated in `ModNetworking`.
* **Supersonic flight** (`SUPERSONIC_FLIGHT`) -- a 5 s burst: strong forward drive along the look
  vector, speed cap raised to 80 m/s (`supersonicSpeedCap` read in `IronManFlight.tick`), 300 energy,
  12 s cooldown. `TonyStarkState.supersonicUntil` (transient).

The one-shot wrist laser now reloads on **any** platform dock (was gated on `hasWristLaser()`), so a
Mark 7 that bound it on the wheel reloads too.

Gametests updated: suit count 8&rarr;9, `markThreeIntegrityPoolIsFifteenHundred` (mark_6=450,
mark_vii=550), `advancedMarksCarryMobHighlight...` drops mark_vii, `wornReactorTrickle...` renamed to
`...IsTenthOfAPercent`. New: `markSixAndSevenAreConfigured`, `cheapSuitSpendsLessEnergy`,
`overloadedSuitCannotFireRepulsor`. **95 gametests pass**, `./gradlew build` green, `runClient`
resource reload clean (BER + weapon-wheel screen not eyeballed in a running client).

## 17i. v0.4.6 ("changes 15")

**Charge rates rebalanced.** Worn Arc Reactor trickle is now **0.5 energy/second** (`IronManEnergy
.WORN_TRICKLE_PER_TICK` = 0.025/tick) and never touches integrity. A Suit Platform now gives a docked
suit **5 energy/second** (`SUIT_ENERGY_REFILL_PER_TICK` 0.25/tick) and **2 integrity/second**
(`SUIT_INTEGRITY_REFILL_PER_TICK` 0.10/tick). `IronManEnergy.PLATFORM_REFILL_PER_TICK` mirrors the
platform energy figure (0.25).

**"Not enough energy" now names the cost.** `IronManAbilities.noEnergy(player, required)` shows
`message.projecthero.ironman.not_enough_energy` -- "Not enough suit energy -- this ability needs N" --
wherever a spend is refused for a known cost (repulsor, charged repulsor, shield, missiles, unibeam,
punch, rocket, flare, timed flight, wrist laser).

**Calling a suit over other armour** now visibly stows that armour: `IronManSuitUpManager.evictSlot`
routes a non-Iron-Man piece to the main inventory (hotbar only as a fallback, ground only if truly
full) and messages `armour_stowed` / `armour_dropped` as each Iron Man piece arrives.

**Blueprints for every craftable mark.** New `MARK_1_BLUEPRINT` / `MARK_2_BLUEPRINT` /
`MARK_4_BLUEPRINT` items (tier 0 -- collectible + Fabricator-enabling, **not** a gate on the
normal-table armour recipe), craftable `2 paper + basic_circuit + redstone` like the Mark III
blueprint. All in the creative tab. Pattern to keep: **every new mark gets a blueprint item + a
blueprint recipe + (for craftable marks) four normal-table armour recipes**, wired through
`IronManItems` + `data/projecthero/recipe/`.

**Mark 1 flight burst (X):** ends on a **13 s cooldown** (`IronManAbilities.TIMED_FLIGHT_COOLDOWN_TICKS`
+ new `endTimedFlight`, called from `IronManFlight` on both timer-expiry and early landing, and from
`IronManSuitTicker.shutDownAllSystems`). `TonyStarkState.timedFlightUntil` is now **synced** (moved
out of the transient block, into the CODEC) so the HUD draws a `FLIGHT` remaining bar.

**Mark 2 texture** regenerated brighter -- blacks -> mid grey, greys -> cool silver, crimson dulled
to steel; the movie Mark II silver look. (`scratchpad/gen_changes15.js` over `armor/mark_iii.png`.)

**Mark 3 (mark_iii):** `strength(9 -> 5)` -- **6 total unarmed damage** (5 + vanilla's base 1).

**New suit -- Mark IV (`mark_4`):** tech 0 craftable primitive (in `PRIMITIVE_SUIT_IDS`), integrity
**400**, energy **13,000**, Mark III's exact ability layout. Grey-accented recolour of the Mark III
armour + item textures (`gen_changes15.js`). New `IronManArmorMaterials.MARK_4`. Recipes:
`data/projecthero/recipe/iron_man_mark_4_*` (iron + basic circuits, diamond in the chest) + a blueprint.
* **Wrist laser (sneak + V):** `IronManSuit.wristLaser()` flag + `IronManAbilities.WRIST_LASER`.
  Sneaking and pressing the V (mob-highlight) slot fires a thin, terrain-carving **red** beam (beam
  `kind 3` in `IronManBeamClient`) -- `WRIST_LASER_DAMAGE_PER_TICK` 10 gated by i-frames to ~20/s --
  for `WRIST_LASER_TICKS` (4 s). Plain V still toggles the highlight. **One shot per charge:**
  `TonyStarkState.wristLaserSpent` (a synced set of suit ids), cleared only when that suit is docked
  back on a Suit Platform (`IronManSuitPlatformBlockEntity.serverTick`). Firing it **overloads** the
  armour: `TonyStarkState.overloadUntil` = now + `OVERLOAD_TICKS` (15 s), during which
  `IronManSuitTicker` treats the suit as depleted (everything offline) and the HUD shows an
  `OVERLOADED SYSTEMS` bar. HUD slot-5 label becomes `Mob Highlight / Wrist Laser`.

**Mark V redefined as the movie Mark 5.** Same id (`mark_v`), same `MARK_V_BLUEPRINT` /
`MARK_V_SUITCASE` items, still Fabricator-buildable at tech 2 -- but the suit itself is now: integrity
**250**, energy **6,000**, flight drains 10/s, **Mark 2's ability set** (R windup-repulsor / G Rocket
/ X Flare / Z Unibeam 0.7x / V highlight / C store), and `SuitUpType.SUITCASE_MOVIE`. Texture is the
Mark III recoloured -- **gold/yellow -> silver, reds kept**.
* **Sneak + C (or right-click the Mark V Suitcase while wearing it):** `beginSuitDownToCase` -- folds
  the four pieces away and hands over one `MARK_V_SUITCASE` stamped with the suit's live charge
  (`TonyStarkState.transitionToCase`).
* **Right-click the Mark V Suitcase unarmoured:** `beginSuitUpFromCase` -- consumes the case, loads
  its charge, and builds the suit around the player over ~4 s **chest -> legs -> feet -> helmet**
  (`SuitUpType.SUITCASE_MOVIE` thresholds in `IronManSuitUpManager.stageThreshold`;
  `TonyStarkState.transitionFromCase` materialises the pieces since there is nothing in the pack).

Gametests: `ironManSuitsAndRecipesRegistered` (7 -> 8), `markThreeIntegrityPoolIsFifteenHundred`
(mark_4 = 400, mark_v = 250), `ironManSuitGrantsStrengthProRata` (+9 -> +5),
`repulsorEnergyCostsAreFlatFiftyAndHundred` (mark_v -> mark_iii, since mark_v now has the windup).
New: `markFourIsRegisteredWithWristLaser`, `markVRedefinedAsMovieMarkFive`,
`markOneFlightBurstGoesOnCooldown`, `abilityWithoutEnergyIsRefusedCleanly`,
`wornReactorTrickleIsHalfPerSecond`. **92 gametests pass**, `./gradlew build` green, `runClient`
resource reload clean.

## 17h. v0.4.5 ("changes 14")

**Integrity split now protects a partial suit.** `IronManDamage` gated the 90%-to-integrity /
10%-to-player split on `wearingFullSuit`; it now only needs the **chestplate** (the core that houses
the arc reactor + plating). A 10-damage hit still puts 9 on integrity and 1 through to the player.
The split math was already correct -- the change is that it now engages with the common
helmet+chest loadout, not just all four pieces.

**HUD shows the real numbers.** ENERGY / INTEGRITY (and the new Mark 1 HEAT bar) now read
`76,67%  (7667 / 10000)` -- percentage *and* the true current/max values.

**Per-suit target-scan range.** New `IronManSuit.targetScanRange()` (default 34). Mark 1 / Mark 2 =
**30 blocks**, Mark III = **70 blocks**. Drives the mob-highlight outline in `EntityGlowMixin`.

**Worn Arc Reactor recharge is now a trickle.** `IronManEnergy.tickRecharge` = a flat **0.25
energy/tick (5/s)** -- exactly 10% of the Suit Platform's 50/s -- so you have to dock at a platform
for a real recharge. (`IronManEnergy.PLATFORM_REFILL_PER_TICK` = 2.5 mirrors the platform constant.)

**Per-mark condition pools:** Mark 1 = **200**, Mark 2 = **200**, Mark III = **300** (was 1500).
Death-crash integrity damage is now **50% of that suit's own max** (was a flat 250), so a small suit
isn't near-totalled by one death.

**Marks 1-3 are all survival-craftable at a normal table.** Mark 1 / Mark 2 recipes already
existed; added `data/projecthero/recipe/iron_man_mark_iii_{helmet,chestplate,leggings,boots}.json`
(metal plating + basic circuits + diamonds, netherite ingot in the chestplate). The Fabricator
path still exists and is still the only thing that advances the tech tree.

**Mark 1:**
* Integrity 200.
* **Flight burst (X) fixed.** `IronManFlight.tick` checked "touched the ground -> land" *before* the
  timed-flight branch, so activating it while standing on the floor ended it the next tick. The
  timed burst now runs before that rule (like Pyrokinesis / Geokinesis timed self-flight), survives
  ground contact, force-lands only on the timer, and pops the player up ~0.6 on activation.
* **Rocket (Z)** no longer auto-tracks -- fires straight down the crosshair, 3.0-radius AoE blast,
  70% splash.
* **Flamethrower (G)** gained a heat gauge (`TonyStarkState.flamethrowerHeat`, 0..500, synced),
  identical model to Pyrokinesis's flamethrower: climbs ~1.9/tick while held (~13 s to overheat),
  vents ~1/tick idle, overheat cuts it out until it drops below 480. Still drains suit energy too.
  Shown as a `HEAT` bar on the HUD.

**Mark 2:**
* Integrity 200.
* Flight now drains **10 energy/second** in the air (`flightEnergyCost` 3.0 -> 0.5/tick).
* **Rocket (G)** is dumb-fire + AoE, same as Mark 1's.

**Mark 3:**
* Integrity 300.
* **Micro-Missiles (X)** fire **4 dumb-fire missiles** in a tight spread, 8 direct damage each,
  2.0-radius AoE blast, 60% splash -- no auto-tracking. (`IronManMissileEntity` homing is now
  opt-in via `withHoming()`, which nothing currently calls.)

## 17g. v0.4.4 ("changes 13")

**Armour render bug fixed for good — z-fighting, not culling.** The shared `crimson_vanguard.geo.json`
shipped most of its cubes (torso, arms, head shell, ...) at the *exact* dimensions of the vanilla
player skin box with **zero `inflate`**, so the GeckoLib shell and the player's own body surface were
coincident. Which of the two won the depth test is view-angle dependent, so plates flickered out at
certain camera angles in both the inventory doll and third person. Fix: a flat **`inflate += 0.2`** on
every one of the 28 cubes (a Node script over the JSON), lifting the shell ~0.0125 blocks clear of the
skin while preserving the artist's relative plate/shell layering (every cube moved by the same amount).
Rule now in `docs/ARMOR_MODELS.md`: **a suit cube must never sit coincident with the vanilla skin box
(inflate 0) — it will z-fight and vanish at angles.**

**Suit Platform refill rates are now flat 50/s.** `IronManSuitPlatformBlockEntity.SUIT_REFILL_PER_TICK`
= 2.5 (both energy and integrity), replacing the old "fill in 5 minutes" derivation. The Reactor-Core
buffer is still drawn on first as fuel for the energy share.

**Flat ability energy costs (every mark).** `IronManAbilities` constants replace the per-suit fields:
`REPULSOR_ENERGY` 50, `CHARGED_REPULSOR_ENERGY` 100, `UNIBEAM_ENERGY` 500 (spread over the 5 s channel).
`IronManSuit.repulsorEnergyCost` / `unibeamEnergyCost` are now vestigial. Unibeam damage dropped to
**10/tick** (`UNIBEAM_DAMAGE_PER_TICK`), still ×`unibeamDamageMultiplier` (Mark 2 = 0.7).

**Mob highlight is a per-viewer toggle on every mark — no passive highlight, no Targeting Mode.**
`EntityGlowMixin` now paints hostiles only while `TonyStarkState.mobHighlightOn` is set (client-only
outline, the wearer's screen only). All five advanced marks swapped slot 5 `TARGETING_MODE` →
`MOB_HIGHLIGHT_TOGGLE`. `IronManSuitTicker.shutDownAllSystems` calls `IronManAbilities.clearMobHighlight`
so the toggle can't stay latched with the suit off / unpowered — the wearer must deliberately re-enable
it. `toggleMobHighlight` now requires the helmet.

**Repulsor Shield (slot 2 / G, formerly "Repulsor Barrier"):** `BARRIER_DAMAGE_MULT` 0.2 → **0.1**
(blocks 90%), and `IronManDamage.isFrontal` gates it to the **180° arc the player faces** — a hit from
behind passes straight through to the ordinary suit mitigation. Sourceless damage counts as frontal.

**Per-suit max integrity.** `IronManSuit.maxIntegrity()` (default 500) — **Mark III = 1500**. Resolve a
specific suit's ceiling with `IronManEnergy.maxIntegrity(suitId)`; the `MAX_INTEGRITY` constant is now
only the fallback for no-suit-id contexts (codec defaults). `stackIntegrity` gained a `suitId` param.
The platform menu's ContainerData now sends integrity as a **0..100 %** (the screen can't divide by a
fixed constant any more). Death-crash damage stays a flat 250 (`MAX_INTEGRITY * 0.5`).

**Mark 1:** `IronManSuit.manualFlight()` = false — `IronManFlight.toggle` refuses to start flight from
the double-tap-jump gesture (message `no_manual_flight`); the Mark 1 flies **only** via its X
timed-flight ability, which calls `setFlying` directly.

**Mark 2:** no Charged Repulsor variant and no hold semantics — `repulsorSlot` short-circuits for any
suit with `repulsorWindupTicks() > 0`: a single press commits to the 1 s spin-up
(`tickRepulsorWindup`) then an ordinary blast. HUD shows "Repulsor (1s charge)".

**HUD:** added a `MOB HIGHLIGHT ON/OFF` line and a `MISSILES x{n}` / `MISSILES reload {s}s` line.

`./gradlew build` green, gametests pass. New tests: `mark1CannotStartRepulsorFlightFromDoubleJump`,
`repulsorEnergyCostsAreFlatFiftyAndHundred`, `markThreeIntegrityPoolIsFifteenHundred`,
`advancedMarksCarryMobHighlightInsteadOfTargeting`, `mobHighlightClearsWhenSuitLosesPower`.

## 17f. v0.4.3 ("changes 12")

**Render bug fixed: GeckoLib armour rendered helmet/chest/boots but never the leg plates, and the
inventory-doll preview looked half-armoured too.** Root cause: `GeoArmorRenderer.applyBoneVisibilityBySlot`
decides which bones to show by copying the visibility flag off whichever vanilla `HumanoidModel`
instance it was handed as `original` for that render pass -- and for the LEGS slot specifically,
vanilla hands it a *different* model instance (the slimmer "inner" armour model leggings use, distinct
from the one HEAD/CHEST/FEET get), whose own flags aren't reliably the "worn and visible" state
GeckoLib assumes. `SuperheroArmorRenderer` now overrides `applyBoneVisibilityBySlot` to show each
slot's own bones unconditionally instead of trusting a borrowed model's flags -- fixes LEGS (and the
inventory doll, which goes through the same code) without touching the three slots that already worked.

**Suit storage now goes to the main inventory, never the hotbar.** `IronManSuitUpManager.beginSuitDown`
counts free main-inventory slots (9..35) up front and refuses -- with `"No space to store suit"`, no
side effects at all -- if there isn't one free slot per piece being stored. The staged removal in
`tick()` places each piece via a new `addToMainInventoryOnly` instead of `Inventory.add` (which also
fills the hotbar).

**Recharge/repair timing overhauled.** A worn suit's own Arc Reactor now takes a flat **10 minutes**
to trickle-charge it from empty to full (`IronManEnergy.tickRecharge` derives the per-tick amount from
`energyCapacity / 12000 ticks` instead of the old per-suit `energyRecharge` constant -- that field is
now vestigial). Integrity still never repairs while worn, only on a Suit Platform. A **Suit Platform**
now fills **both** energy and integrity in a flat **5 minutes** (`IronManSuitPlatformBlockEntity`'s
`FULL_REPAIR_TICKS` drives both; the Reactor-Core buffer is still drawn on as "fuel" first, but running
it dry no longer slows the guaranteed rate below target).

**Integrity damage model replaced.** `IronManDamage` no longer scales incoming damage by a per-suit
`damageReduction` multiplier (that field is now vestigial). While integrity is intact, the suit absorbs
**90%** of every hit (the wearer takes 10%); absorbing bleeds integrity by 90% of the raw hit and
drains a little energy. Once integrity fails, there's nothing left to absorb with, but the raw plating
still helps a little -- the wearer now takes a flat **80%** instead of the old "100%, raw defense
only". A suit with failed integrity also refreshes Slowness II + Weakness II on its wearer every tick
(`IronManSuitTicker.tickIntegrityFailure`) -- "life-support and stabilisers failing" -- clearing on its
own the instant integrity is repaired or the suit comes off, since it's a short refreshed duration
rather than an explicit add/remove that could clobber an unrelated effect.

**Death recovery now also reaches a suit that was only ever carried, never worn.** `IronManSuitCall
.recoverSuitOnDeath` was rewritten to scan every Iron Man piece the dying player has -- worn *or*
sitting in the pack -- grouped by suit id, and runs the platform-recovery flow independently per suit
(so carrying two different marks at once recovers both, each to whichever platform actually holds
that mark). The crash damage is now a flat **50% of MAX integrity** (250 points) subtracted, not a
proportional 35% of whatever integrity was left -- consistent with how integrity damage is expressed
everywhere else in this system (an absolute amount, not a fraction of current). No platform for a given
mark -- its pieces are stamped in place and left exactly where they are, dropping the same way the rest
of a dead player's stuff always does.

**Iron Man HUD gained a coordinate readout**, and (Mark 2 only) an altitude-ceiling warning band and a
"SYSTEMS FROZEN" readout once actually locked out.

**Two new suits, both non-Fabricator** (`IronManItems.PRIMITIVE_SUIT_IDS`, tech level 0 -- craftable at
a normal table the moment a player has the Tony Stark power, same as the Arc Reactor itself; see
`data/projecthero/recipe/iron_man_mark_{1,2}_*.json`):

* **Mark I** -- grey texture (`textures/armor/mark_1.png`, a desaturated `crimson_vanguard.png`,
  generated the same way Mjolnir's textures were verified this session -- no hand-painted art), 25%
  bigger (`Attributes.SCALE` while a full suit is worn -- `IronManSuit.scale()`, applied in
  `IronManPassives`), never applies the sprint lean pose (`IronManSuit.noFlightLean()`, checked in
  `FlightPoseHelper`). Integrity 500 (already the shared global cap, no new plumbing needed), energy
  3000, +4 unarmed damage (on top of vanilla's base 1 = 5 total, via the existing `strengthBonus`
  mechanic). Abilities: **R** Strong Punch (new, melee-range, 12 dmg + knockback) / **G** Flamethrower
  (new -- the same stream/fire-catching/hit-cone mechanic as Pyrokinesis's flamethrower ability, ported
  into `IronManAbilities.tickFlamethrower`, but fuelled by suit energy instead of a heat gauge) /
  **X** a one-press 20-second guaranteed flight burst (new `TIMED_FLIGHT` ability -- flat activation
  cost, no per-tick drain, force-lands exactly at the timer; the normal double-tap-jump toggle still
  works too for ordinary energy-metered flight) / **Z** Rocket (new -- single `IronManMissileEntity`,
  15 dmg, 20 s cooldown) / **V** a real mob-highlight on/off toggle / **C** Suit Store.
* **Mark II** -- grey texture, normal size, +4 unarmed damage, integrity 500, energy 4500. Freezes all
  systems and force-lands above **Y150** (`IronManSuit.altitudeCeiling()`, enforced in
  `IronManSuitTicker.tick`, `IronManAbilities.trigger` and `IronManFlight.toggle`), with a HUD warning
  band starting 20 blocks below the ceiling. Abilities: **R** Repulsor Blast with a forced 1-second
  spin-up before an ordinary tap fires (new `IronManSuit.repulsorWindupTicks()` +
  `IronManAbilities.tickRepulsorWindup`; the existing hold-2s-for-Charged-Repulsor upgrade is
  untouched) / **G** Rocket / **X** Flare (new -- a bright burst that blinds nearby hostiles for 3 s,
  10 s cooldown) / **Z** Unibeam at 70% of Mark III's per-tick damage (new
  `IronManSuit.unibeamDamageMultiplier()`) / **V** mob-highlight toggle / **C** Suit Store.

**Two bugs fixed in passing while touching this code:**
* The Iron Man HUD's six ability lines were reading a **hardcoded** id list
  (repulsor/barrier/missiles/unibeam/targeting/toggle) instead of the worn suit's own
  `abilityInSlot(...)` table -- harmless for the original five marks (which all share that layout) but
  would have shown the wrong names/cooldowns entirely for Mark 1 / Mark 2.
* Micro-Missiles never actually used each suit's own `missileDamage()` -- every mark's volley dealt the
  entity's hardcoded default (8 direct / 4 splash) regardless of the builder value (7..12 across marks).
  `IronManMissileEntity` now takes a `withDamage(direct, splash)` override (also used by Rocket), and
  Micro-Missiles passes `suit.missileDamage()` through.

Regression coverage: 12 new gametests covering suit-store space-checking, the depleted-integrity
debuff, carried-only-suit death recovery, both new suits' registration/stats/scale, the windup
repulsor, the altitude lockout, Rocket, and the mob-highlight toggle. The 90%/10% and 80%/20% damage
split itself is **not** gametest-covered -- confirmed by direct instrumentation that
`player.hurt(...)` never reaches Fabric's `ALLOW_DAMAGE` event at all for a GameTest mock
`ServerPlayer` (it fires reliably for every other entity in the suite), a pre-existing harness gap the
old `damageReduction` mitigation was never covered by either. 82 game tests pass.

## 17e. v0.4.2 — launch fix + Iron Man durability pass

The client would not reach the title screen at all: `PlayerModelMixin` `@Shadow`-ed `hat`, which is
declared on the SUPERclass `HumanoidModel`, and Mixin resolves `@Shadow` fields only against the
target class itself — so the mixin threw `InvalidMixinException` while `LayerDefinitions.createRoots`
was loading `PlayerModel` during the very first resource reload, taking the whole game down. The
overlay parts are all public, so the mixin now reaches them with a plain cast instead. Nothing else
about the GeckoLib armour system changed.

Alongside that, a pass over the Iron Man system for the ways it could silently eat a player's items:

* **Suit-up no longer destroys the suit if it is interrupted.** `beginSuitUp` used to pull all four
  pieces out of the inventory up front and re-create them per stage. The transition fields are
  transient, so a logout / death / crash during those ~2 seconds left the armour in neither the
  inventory nor a slot — gone. It now only *reserves* the pieces; each one moves from the pack onto
  the body in the tick its stage fires. As a bonus the stack you carried is the stack you wear, so
  its charge, enchantments and custom name survive being put on.
* **Suiting up no longer deletes the armour you were already wearing.** `setItemSlot` overwrites; a
  new `evictSlot` hands the old piece back to the inventory (dropping only if it will not fit).
* **Dying in a partial suit no longer deletes your other armour.** The "fly itself home to an
  unloaded platform" path blanked all four armour slots; it now clears only the slots the Iron Man
  suit actually occupies.
* **Breaking a Suit Platform or Stark Fabricator drops its contents.** Their loot tables only ever
  dropped the block, so mining a platform holding a finished Mark 50 destroyed the suit. Both blocks
  now override `onRemove`.
* **A Suit Platform holds one mark.** Mixing marks made every "the stored suit" reading (energy,
  integrity, the registry entry the call system reads) describe whichever piece sat in the lowest
  slot, and deploying deleted the odd one out. `canPlaceItem` / `store` now refuse a foreign mark,
  and `deployTo` empties only the slots it actually handed over — so an older mixed rack from a
  previous save still can't lose anything.
* **Micro-missiles respect the mod's PvP rule.** In-flight homing and the blast targeted any living
  thing, so a volley on a peaceful server steered itself into the nearest bystander. Both now use
  the same enemy test as every other ability (`AbilityHelpers.enemiesAround`).
* **Repulsor muzzle / barrier disc survive looking straight up or down.** `look.cross(UP)` collapses
  to the zero vector there, which put the muzzle back on the eye position and flattened the shield
  disc to a single point — exactly the angles a flying Iron Man looks at most. A new `rightOf` falls
  back to body yaw.
* **`beginSuitDown`** no longer NPEs on an unknown suit id, and `IronManSuitCall`'s static
  pending-call map is cleared on `SERVER_STOPPED`, so a single-player session cannot carry a
  countdown — pointed at a `GlobalPos` in the world you just left — into the next world you open.

Regression coverage added: `interruptedSuitUpKeepsThePiecesInTheInventory`,
`suitUpEvictsOrdinaryArmourInsteadOfDeletingIt`, `deathRecoveryOnlyTakesTheIronManPieces`,
`breakingASuitPlatformDropsTheSuitItHeld`, `aSuitPlatformRefusesToMixMarks`. 71 game tests pass.

**Still outstanding (unchanged this pass, deliberately):** every armour set — Thor and all five
marks — still renders with the same `crimson_vanguard` placeholder texture, because the six files in
`assets/projecthero/textures/armor/` are byte-identical copies of it. See §18 and `docs/ARMOR_MODELS.md`.

## 17d. v0.3.7 ("changes 9" + follow-ups)

* **Ability layout** — Charged Repulsor moved onto **hold-R** (2 s), freeing slot 2 for the new
  **Repulsor Barrier** (`G`). Unibeam nerfed (30 s cd, ≤1 block/0.5 s, fewer particles, beam VFX every
  other tick). Targeting Mode cd → 2 s. Charged Repulsor block-break is now a ≤3-block line, only when
  shot at a block, never at a mob.
* **Helmet threat highlight** — `EntityGlowMixin` gains an Iron Man branch: a powered helmet paints
  nearby `Enemy` mobs with a client-only outline (no server GLOWING effect).
* **Flight animation** — `IRON_MAN_FLYING` now feeds `FlightPoseHelper` as an arms-at-sides lean pose,
  same as experimental Hero Flight.
* **Suit charge travels with the armour** — new `ModDataComponents.SUIT_ENERGY` / `SUIT_INTEGRITY`
  float components, stamped on unequip / store, read on equip / deploy. Absent = full (a fresh suit
  ships charged). The Suit Platform actively pours its Reactor-Core buffer + free trickle into the
  stored suit's carried charge and repairs its integrity, and the (now taller) Platform screen shows
  the armour's **real** SUIT CHARGE + INTEGRITY bars, not zero.
* **Call Armour picker** (`C` unarmoured) — server sends `IronManSuitListPayload` of every
  assemblable suit (fully in inventory, or on a bound Suit Platform of yours in this dimension — even
  an unloaded chunk, via `StarkPlatformRegistry` SavedData), the client opens `IronManSuitCallScreen`,
  the choice comes back as `IronManCallSuitPayload` to `IronManSuitCall.execute`. Inventory suit →
  immediate staged suit-up. Platform suit → the platform's chunk is force-loaded once (packet-handler
  context) so its block entity removes the pieces, charge transfers, couriers fly in near the player.
  Platforms auto-bind to their placer (and adopt a nearby Tony Stark player if placed by command).
  Double-tap-jump-on-ground → `IronManSuitCall.callBest` (same gather, no picker). This replaces the
  old `summonBest`-on-C path and its "Mark L is not developed yet" misfire.
* **Mark III** — repainted `mark_iii_layer_{1,2}` as the classic MCU Mark III (deep red / brushed
  gold faceplate·hands·boots·hips, blue eyes + arc reactor), plus a slim custom armour model via
  Fabric `ArmorRenderer` (`IronManArmorModels`, `CubeDeformation` 0.55/0.4 vs vanilla 1.0/0.5) so it
  stops reading as bulky. Mark III only; the other suits keep the standard model.

**Follow-ups (same version line):**

* Suit Platform screen redrawn with fixed non-overlapping bands (no more RESERVE-over-DEPLOY).
* **Call Armour on `C` always works unarmoured** — `AbilityRouter` routes `C` → the picker for any
  Tony Stark player who is unarmoured and not holding Mjolnir, ahead of Thor's context and regardless
  of whether a suit has been "developed" or a piece is carried. (Hold Mjolnir and `C` stays Thor's.)
* **Repulsor Barrier is hold-to-maintain** — hold `G` and the shield stays up as long as you hold it,
  draining energy; release (or run dry) and it drops with a 6 s cooldown.
* **All suit systems cut out the moment the suit comes off or the power drops** — targeting mode,
  the barrier, the Unibeam channel, the charged-repulsor spin-up, flight: one switch.
* **The suit only makes you wait when it's coming from an unloaded chunk.** A loaded Suit Platform =
  the armour flies straight off it to you. An unloaded one = a travel delay scaled to the real
  distance ("ETA 12s") *then* it flies in and self-assembles at the usual pace. Pieces stay on the
  platform until it actually launches, so nothing is lost if the server restarts mid-wait.
* **Suit worn on death auto-recovers** — the onboard AI flies the armour to your nearest Suit
  Platform, crash-damaged (integrity drops to ~35% of what it was). Repair it there. No platform to
  reach → the pieces just drop (with their charge intact). Off under keepInventory.
* Sprint-flying vents repulsor exhaust from the **hands** (drawn client-side so it tracks the pose).
* **First person shows the gauntlet** — a chunky armoured forearm is drawn over the first-person hand
  while you wear an Iron Man chestplate, tracking every swing/place animation.

## 17c. v0.3.3 ("changes 8")

* **Slot layout** (all marks): `R` Repulsor Blast (10 dmg, breaks glass) · `G` Charged Repulsor
  (16 dmg Mk III, breaks soft blocks) · `X` Micro-Missiles (8 dmg each, homing, own no-terrain
  explosion) · `Z` Unibeam (channels 5 s, 25 dmg/tick, carves a tunnel) · `V` Targeting Mode ·
  `C` Store Suit / **Call Armour**.
* **Calling the armour** — with the Tony Stark power and a developed suit, press `C` (or double-tap
  jump on the ground) while unarmoured: the pieces fly to you like Mjolnir and assemble. The HUD
  shows a `CALL ARMOUR [C]` prompt. Wearing a suit, `C` stores it.
* **Suit strength** (`IronManPassives`) — a worn suit adds a large ATTACK_DAMAGE bonus, pro-rata to
  how many pieces you have on: Mark III +9, V +7, VII +13, XLII +15, L +20 for a full suit. Full
  suits also add attack knockback, knockback resistance, reach and mining speed.
* **Arc Reactor on the model** — a glowing reactor renders on your chest while you have the power and
  aren't wearing an Iron Man chestplate.
* **Remove the reactor** — `/superhero arcreactor remove` (survival, no op needed): lose the Tony
  Stark power, get an Arc Reactor item back. Also `/superhero worthiness` and `/superhero status`.
* HUD energy/integrity now show a 2-decimal percentage (`76,67%`); the Fabricator and Suit Platform
  GUIs show it too, and the Platform GUI was relaid out so the charge line no longer overlaps the
  slots. Both blocks are `noOcclusion` (fixed neighbouring blocks rendering invisible).

## 17b. v0.3.1 additions

* **Charging is now discoverable.** Stark Fabricator: drop a **Reactor Core** (+8 000) or **Arc
  Reactor** (+25 000) into *any* input slot and it is consumed into the buffer next tick — or
  right-click the block with one in hand. Suit Platform: right-click it with a Reactor Core to fill
  its charge buffer; deploying the stored suit transfers that charge to the suit **and repairs
  integrity to 100**. Suits also trickle-charge from their own Arc Reactor while worn. GUI energy
  readout fixed (it was overflowing 16-bit sync → `-15536`).
* **Suit-up / suit-down is staged.** Pieces equip/remove one body region at a time from a per-player
  timer (`IronManSuitUpManager.tick`) with a particle burst + clunk per stage — boots → legs → torso
  → helmet → faceplate-close. Mark 50 spreads from the chest outward with nanite particles and is
  faster. Suit-down retracts helmet-first. No custom rendering needed — vanilla armour rendering
  shows the pieces appearing.
* **Repulsor / Unibeam beams render in first *and* third person** via a server→client
  `IronManBeamPayload` that draws a client particle line (`IronManBeamClient`). Repulsors fire from a
  gauntlet offset, the Unibeam from the chest.
* **Charged Repulsor is a real HOLD ability** — hold slot 2 to spin up (visible charge particles),
  release to fire; damage and energy cost scale with hold time up to ~3.4×.
* **Suit integrity + damage mitigation** (`IronManDamage`): a full powered suit scales incoming
  damage by the suit's `damageReduction` (Mark III ×0.72 … Mark 50 ×0.45), each hit bleeds integrity
  and drains energy, and at zero integrity mitigation stops (raw `ArmorMaterial` defense only). Iron
  Man **boots alone** give strong fall-damage protection (partial armour).
* **HUD polish** — crosshair `TARGET name + distance`, a corner-bracket reticle while Targeting Mode
  is active, live `MISSILES` count / reload timer, `ALT` / `SPD` readout, "SUIT OFFLINE" at zero
  energy or integrity.
* **Suit Platform GUI** (`IronManSuitPlatformMenu` / `Screen`) — 4 armour slots, charge readout,
  **DEPLOY** / **RETRIEVE** buttons. Right-click the platform empty-handed to open it. A
  `BlockEntityRenderer` displays the stored pieces as a slowly rotating floating stack (a basic Hall
  of Armor — full mannequin is still a follow-up).
* **Partial / modular summons** — `/ironman part <suit> <helmet|chestplate|leggings|boots>` flies a
  single piece in as its own courier entity (Mark 42 modular calling).
* **Custom block models** — the Stark Fabricator and Suit Platform are custom models (v0.14.21 remodel: see
  section 15c), not `cube_all`. Item/armour textures are proper red/gold procedural art.

## 18. Placeholder assets to replace

Generated by `scratchpad/gen_ironman.js` (flat solid-colour PNGs):

* `assets/projecthero/textures/item/` — `reactor_core.png`, `arc_reactor.png`, `repulsor.png`,
  `mark_v_suitcase.png` (v0.14.21: all Iron Man item sprites, one sprite per blueprint, the missile entity
  texture and the Mark III-VII skin repaint now come from `scratchpad/gen_v01421_ironman_textures.js`;
  the unused `stark_component.png`, `blueprint.png` and `iron_man_mark_{iii,v,vii}.png` were deleted)
* `assets/projecthero/textures/block/` — `stark_fabricator.png`, `iron_man_suit_platform.png`
* `assets/projecthero/textures/models/armor/mark_{iii,v,vii,42,50}_layer_{1,2}.png`
* ~~`assets/projecthero/textures/gui/stark_fabricator.png` (176×186)~~ — replaced in v0.14.21 by real
  256×256 GUI sheets `stark_fabricator.png` (200×236 panel) and `iron_man_suit_platform.png` (176×202),
  generated by `scratchpad/gen_v01421_ironman_gui.js`
* Models are all `item/generated` / `block/cube_all`. Replace freely; no code change needed.
* Custom sounds: none added — suit-up/abilities layer real vanilla sounds.
* Custom entity/block models and staged nanotech formation rendering: not yet — see the roadmap in
  the implementation report.
