# HeroPack Content Reference

The readable source of truth for what is actually implemented in HeroPack (mod id `projecthero`,
Fabric 1.21.1). Keep this file updated in the **same change** as any content addition/modification.

The bottom "Experimental Power Encyclopedia" section is generated from the code + language file by
`scratchpad/gen_docs.js`; edit `PowerCatalog.java` / `gen_lang.js` and regenerate, don't hand-edit
below the AUTO marker.

---

## Implementation status

| Batch | Scope | Status |
|---|---|---|
| 1 | Six-slot input router, experimental player data, power definition registry (all 27), power selection, persistent cooldowns, server-authoritative networking, minimal HUD, config | **done** |
| 2 | Mutation framework: experimental brewing, Unstable Mutation serum effect, exposure triggers, permanent unlock, capacity, research/discovery state, 4-step advancement chain | **done** |
| 3 | Powers 01–05 mechanics (Super Strength, Laser Vision, Flight, Super Speed, Geokinesis) | **done** |
| 4 | Powers 06–10 mechanics (Crystalkinesis, Electrokinesis, Pyrokinesis, Cryokinesis, Telekinesis) | **done** |
| 5 | Powers 11–15 mechanics (Teleportation, Super Regeneration, Super Durability, Sonic Scream, Invisibility/Light) | **done** |
| 6 | Powers 16–20 mechanics (Spider Climbing/Adhesion, Elasticity, Density, Shadow, Energy Absorption) | **done** |
| — | v0.6.3: Spider Adhesion rebuilt on a real adhesion engine; Spider-Man Hero Class | **done** |
| 7 | Powers 21–27 mechanics (Shockwave, Plant/Chlorokinesis, Gravity, Wind, Water, Magnetic, Size) | **done** |
| — | 6 rare surface structures, 9 laboratory device blocks, power combos, HeroPack Guide book + GUI, HUD resource meter | **done** |
| — | Per-ability HUD icon art (currently key-letter tiles), radial power wheel (currently a screen) | polish backlog |

When a power's abilities are wired into `AbilityHandlers`, its row here moves to "done" and its
encyclopedia entry stops showing the "not implemented yet" behaviour.

### Ability implementation infrastructure (batch 3)

- `com.projecthero.mod.hero.power.AbilityHelpers` — server-side raycast, config-gated `hurt` (PvP off
  → no player damage), `applyControl` (halves CC vs players; skips if `abilityHardCrowdControlOnPlayers`
  off), knockback, self-launch (`ClientboundSetEntityMotionPacket`), particle lines.
- `Handlers` — terse factories (`instant`, `instantTicking`, `hold`, `charge`, `toggle`, `cycle`).
- `PowerToggles` — infinite hidden effects + fixed-id transient attribute modifiers, applied
  idempotently every toggle tick (so they survive respawn via `ExperimentalPowers.reconcileToggles`).
- `HeroFlight` — shared experimental flight, **fully independent of Thor flight**: own attachment
  `projecthero:hero_flying`, own `flight` stamina resource (drains airborne, regens grounded, cuts out
  cleanly with fall distance reset), refuses to engage while Thor flight is active.
- `TempBlocks` — conjured walls/spikes: places only over replaceable blocks, restores after a TTL,
  bounded deque ticked once per server tick, skipped entirely when `abilityTerrainDamage` is off.
- `HeroDamageRules` — one `ALLOW_DAMAGE` listener for passive resistances keyed off the active power
  (fall immunity for Flight/Spider/Elasticity/Wind/Strength, fire immunity for Pyrokinesis).
- `AbilityHandler.onServerTick` runs every tick for all 6 abilities of the active power (grab/charge/
  marker upkeep).
- `GrabHelper` (batch 4) — shared grab / hold-in-front / throw / slam, state in the ability's own
  resource slots so it clears on power switch and self-heals if the held entity vanishes.

### Safe movement (batch 5)

`SafeTeleport` validates every blink/phase/mark destination: loaded chunk, inside world border,
build-height bounds, collision-free for the player's current pose, no lava in the body column.
`blink` walks the ray in 0.5-block steps and stops at the last safe point before an obstacle (never
punches through walls); `phaseThrough` only succeeds if a safe spot exists within a short distance;
`teleport_mark` re-validates on recall and drops the marker if the spot is now blocked. Gametest
`teleportBlinkRespectsWalls` asserts a blink into obsidian does not pass through it.

### Hero hierarchy — Electrokinesis vs Thor (spec 10)

Generic Electrokinesis deliberately uses `DamageTypes.PLAYER_ATTACK` (armour applies; Thor is **not**
immune to it, unlike real lightning), chains to **3** targets (Thor: 5) at 5/4/3 damage (Thor:
~9 + falloff), and never touches Mjolnir / worthiness / Storm Energy. Its lightning-damage passive is
a *reduction* (×0.4 via the re-entrancy-guarded path in `HeroDamageRules`), not immunity — Thor's is
full immunity.

---

## Controls

HeroPack uses **exactly six universal ability keybindings**, shown in Options → Controls under the
category **`Heropack Abilties`** (exact spelling per design spec):

| Slot | Physical key | Controls-screen name | Design role |
|---|---|---|---|
| 1 | `R` | Primary | Primary attack |
| 2 | `G` | Secondary | Secondary attack |
| 3 | `X` | Movement | Movement / defence |
| 4 | `Z` | Ultimate | Ultimate |
| 5 | `V` | Utility / Control | Utility / control |
| 6 | `C` | Special Mode | Special mode / toggle |

Plus one non-slot key: **`H` — HeroPack: Select Power** (opens the power-selection screen). Never
uses any of R/G/X/Z/V/C.

There is one logical six-input system, **not** 162 keybindings. What each slot does is resolved
server-side by `AbilityRouter` from the player's current context.

### Context routing (`com.projecthero.mod.hero.AbilityRouter`)

1. **Thor** — if the player is worthy **and** (holding Mjolnir **or** is Mjolnir's bound owner), the
   six slots route to Thor's existing ability handlers with **no behaviour change**:
   R = Call Mjolnir, G = Lightning Strike, X = Lightning Beam (hold), Z = Thunderclap,
   V = Storm Call, C = Chain Lightning. Thor flight is unchanged — still a double-tap of the jump
   key while holding Mjolnir. The experimental system never takes the slots while Thor holds them.
2. **Active experimental power** — otherwise, the six slots map to the six abilities of whichever
   mutation the player has selected via the power wheel.
3. **Nothing** — input ignored.

Client sends only a request (`AbilityInputPayload` — slot 1–6, pressed/released edge). All
validation (ownership, active power, cooldown, resources, world state, damage, CC limits) is
server-side.

---

## Famous hero content already in the mod

### Thor / Mjolnir (production content — unchanged by HeroPack)

Implemented in `com.projecthero.mod` under `item/` (`MjolnirItem`, armor), `entity/MjolnirEntity`,
`hammer/` (`MjolnirRegistry`, `MjolnirRecall`, `HammerRecord`, `MjolnirStatus`), `power/`
(`ThorPowers`, `ThorPassives`, `ThorFeedback`, `StormEnergy`, `ThorAbility`), `worthiness/`
(`Worthiness`, `WorthinessEnforcer`), `worldgen/` (Mjolnir crater structure + `CraterAmbience`),
client mixins and `ThorHud`.

**Items:** Mjolnir (`projecthero:mjolnir`), Helmet/Chestplate/Leggings/Boots of Asgard. All in the
`projecthero:superheroes` ("Superheroes") creative tab.

**Mjolnir interactions:**
- Right-click — throw Mjolnir (straight-line flight; returns on recall).
- Sneak + right-click — bind / release Mjolnir (binding grants the Power of Thor).
- `R` (Primary slot, via router) — Call Mjolnir (recall from anywhere, incl. unloaded chunks / other
  dimensions, with a generation-counter duplication guard).
- Double-tap jump while holding Mjolnir — toggle flight (drains Storm Energy; 15 s hammerless-flight
  grace if thrown mid-flight).

**Thor abilities (worthy + holding Mjolnir):**
| Key | Ability | Notes |
|---|---|---|
| G | Lightning Strike | 30-block ranged bolt, auto-chains to 2 nearby monsters, costs Storm Energy, 2.5 s CD |
| X | Lightning Beam | held/continuous, drains Storm Energy per tick |
| Z | Thunderclap | 5-block radial knockback + slow, 5 s CD |
| V | Storm Call | 18 s follow-the-player storm that strikes nearby hostiles, 2 min CD |
| C | Chain Lightning | crosshair target + up to 4 arcs, damage falloff, 8 s CD |

**Power of Thor (passive, from being the bound owner + worthy):** flat melee bonus, +18 % move
speed, 0.35 knockback resistance, hidden Resistance I, mild out-of-combat regen, immunity to fall
and lightning damage. Transient attribute modifiers with fixed ids — never accumulate.

**Worthiness:** hidden score, threshold 50. `/thor worthy|unworthy|status`.

**Worldgen:** `projecthero:mjolnir_crater` rare surface structure spawns a naturally-occurring Mjolnir
with ambient crater lightning. Unchanged.

HeroPack changes to Thor were limited to a **transport-only** input reroute (the six Thor keybinds
became the universal Ability 1–6 keys; each routes to the identical `ThorPowers.*` method). No Thor
gameplay constant, registry id, structure, sound, particle, data key, or handler was changed.

### Spider-Man (v0.6.3)

A Hero Class in `com.projecthero.mod.spider`, reached by evolving experimental power 16 with an
**Arachnid Mutagen**. It takes the six universal slots whenever no experimental power is selected in
the wheel, sitting after Thor and Iron Man in the router priority.

| Key | Ability |
|---|---|
| R | Web Swing -- hybrid real/fabricated anchors, momentum-preserving rope physics |
| G | Web Zip -- short-range precision pull toward the aimed surface |
| Z | Web Shot -- stacking slow that pins an ordinary mob after three hits; puts out fires |
| X | Web Yank -- pull items and light mobs to you, heavy ones pull you to them |
| C | Web Cocoon -- 5 s restraint (2 s and much weaker on a boss) |
| V | Web Net -- a 25 s cobweb platform through `TempBlocks` |

**Passives:** Spider Sense (50 % server-side dodge, arrow catching, a threat cue), wall and ceiling
crawling, a 2 s-cooldown double jump, +3 attack / +18 % speed / +20 % jump / +0.30 knockback
resistance / much softer landings, and a 100-point organic Web Reserve.

Full writeup: [SPIDERMAN_REFERENCE.md](SPIDERMAN_REFERENCE.md).

---

## Experimental mutation system

**Standard flow** (spec section 2): research a clue → brew an unstable serum → drink it (temporary
"Unstable Mutation" effect) → perform the required exposure event before it expires → power is
permanently unlocked with an `UNUSUAL MUTATION DETECTED` title → advancement + full recipe revealed.

### Serums & brewing (batch 2)

- Each power has a **reagent** item (`projecthero:<power>_reagent`), crafted shapeless from that
  power's obscure additives + its thematic "fuel" ingredient (see `data/projecthero/recipe/`).
  *Deviation:* vanilla brewing takes one ingredient and always uses blaze powder as fuel, so the
  multi-additive list + non-standard fuel are folded into this crafted reagent.
- Brewing: `<base vanilla potion> + <reagent>` in a brewing stand → that power's **serum**, a custom
  potion (`projecthero:serum_<power>`) that applies **Unstable Mutation** for
  `unstableMutationDurationTicks` (default 60 s). *Deviation:* the target power is carried in the
  effect's amplifier (power index), so one effect covers all 27 serums.
- Drinking the serum sets a pending mutation and advances research to `SERUM_STABILIZED`. It never
  grants a power on its own. Re-drinking a serum for an owned power does nothing.

### Exposure events (`MutationManager`)

Natural/ambient detectors implemented now: fire dwell, powder-snow dwell, direct sunlight, true
darkness at night, plant surroundings, long submersion, standing on natural stone, amethyst-geode
proximity, enchanting-setup proximity, metal-block proximity, long-fall survival (gravity), surface
thunderstorm at altitude (pressure), looking at the sun (light). Event-driven: explosion / fire /
lightning / near-death damage survived, cave-spider hit, goat-horn use, ender-pearl use, and Energy
Absorption's "two different energy sources".

`MutationManager.triggerExposure(player, Kind)` is the entry point laboratory device blocks will
call (devices batch). Powers whose only trigger is a device — **Density (18), Size (27)** — and to a
lesser extent Laser (02), Flight (03), Gravity (23), Wind (24), Magnetic (26) which have generous
natural fallbacks — become fully reliable once devices land. All 27 are already brewable & researchable.

### Research notes (batch 2)

`projecthero:research_note` (carries a `research_power` component) — right-click to study, advances
that power's research to `RESEARCH_FOUND` and fires the first advancement. Structure loot in the
structures batch; for now obtainable in Creative or via `/heropower research <power>`.

### Advancement chain

`projecthero:mutation/research_found → serum_stabilized → exposure_survived → mutation_confirmed`
(`data/projecthero/advancement/mutation/`), awarded from code at each stage.

### Persistent power stacking (v0.9.3)

Experimental Tier powers (`PowerTier.EXPERIMENTAL` — all 27 mutations) **stack**. A player may own
up to `mutationCapacity` (default 3) at once, via natural mutation or `/heropower stack <power>`.

- **All owned powers' passive buffs are live at all times, simultaneously.** Super Strength's attack
  bonus, Super Durability's damage reduction, Super Speed's step assist, etc. all apply together, and
  never clear each other. Reconciled every tick (`ExperimentalPowers.serverTick` → `PowerPassives`)
  and on join/respawn (`PowerPassives.reconcileActive`, which now reconciles *every owned* power).
- **All owned powers' toggled modes / timed states keep running in the background.** Selecting a
  different power does not turn off another power's armour stance, aura, cloak, phase, mode meter,
  timed self-flight, etc. A mode ends only when: you toggle it off, its timer/cooldown expires, or
  its resource runs dry.
- **`ExperimentalPowers.setActive` is now purely "which power do my six hotbar keys cast from".** It
  does not touch toggles, passives or cooldowns. The one thing it ends is a HOLD/CHARGE *channel* on
  the power you just left (you are no longer holding its key).
- **`HeroDamageRules`** applies every owned power's passive damage rule at once — immunities from any
  power cancel the hit; partial reductions from several powers multiply together.
- **Abilities still come from the selected power only** (`AbilityRouter` → `getActive`). An owned but
  unselected power's six abilities do not fire.
- Handler "is my mode active" checks were switched from `power == getActive()` to
  `ExperimentalPowers.owns(player, power)` across ~15 handlers + the client mode mixins
  (`EntityGlowMixin`, `LocalPlayerMixin`, `MagneticSenseClient`, `SpiderClimb`).
- Teardown (`/heropower clear`, `revoke all`, `HeroTiers.wipeAll`, `forget`) runs
  `ExperimentalPowers.tearDownAll` first so non-attribute mode effects are removed, not orphaned.

---

## Rare structures (`projecthero:research_site`)

One code structure (`ResearchSiteStructure` + `ResearchSitePiece`, mirroring the proven
`MjolnirCraterStructure` pattern — code `StructureType`, datapack JSON, procedural piece that sits
on real terrain; **no recursion, no structure-near-structure logic, bounded loops clipped to the
chunk box**). The kind is a datapack field (`site_type`), so all six share the code:

| Structure | `site_type` | Houses device | Themed loot / linked powers |
|---|---|---|---|
| Abandoned Research Facility | `research_facility` | Experimental Light Projector | Flight, Laser Vision, Telekinesis, Wind, Gravity |
| Meteor Impact Site | `meteor_impact` | — (amethyst/meteor fragment) | Energy Absorption, Flight, Laser Vision, Density |
| Abandoned Power Station | `power_station` | Overloaded Redstone Coil | Electrokinesis, Super Speed, Magnetism |
| Collapsed Geological Research Site | `geological_site` | Geological Resonance Chamber | Geokinesis, Crystalkinesis, Density |
| Government Experiment Site | `government_site` | Molecular Compression Chamber | Density, Size, Gravity, Super Regeneration (rarest — spacing 112) |
| Hydrostatic Test Facility | `hydrostatic_facility` | Hydrostatic Test Tank | Water Manipulation (coastal biomes) |

Each `structure_set` JSON has its own salt + `random_spread` spacing/separation (retune in
`data/projecthero/worldgen/structure_set/*` without code). Biome tags `#projecthero:hp_surface_biomes`
and `#projecthero:hp_coastal_biomes`. Loot in `data/projecthero/loot_table/chests/*` — research notes
(blank → reveal a random undiscovered power), thematic reagents, materials, and a rare themed serum.
`/locate structure projecthero:<id>` works.

## Laboratory devices (`projecthero:*` blocks)

One `LabDeviceBlock` class param'd by `MutationTrigger.Kind[]` + activation style. Fires
`MutationManager.triggerExposure` for every player within 4 blocks who has the matching unstable
serum active — a **small local `getEntitiesOfClass`**, only when the device activates (redstone
rising edge, or right-click), never a per-tick scan.

| Block | Kind(s) | Activation |
|---|---|---|
| `overloaded_redstone_coil` | ELECTRICAL_DISCHARGE | redstone pulse |
| `experimental_light_projector` | HIGH_INTENSITY_LIGHT | redstone pulse |
| `unstable_gravity_plate` | GRAVITY_DISTORTION | redstone pulse |
| `geological_resonance_chamber` | GEOLOGICAL_RESONANCE, AMETHYST_GEODE | right-click |
| `molecular_compression_chamber` | MOLECULAR_COMPRESSION | redstone pulse |
| `mass_compression_chamber` | MASS_COMPRESSION | redstone pulse |
| `pressure_chamber` | PRESSURE_CHAMBER | redstone pulse |
| `hydrostatic_test_tank` | SUBMERSION | right-click |
| `electromagnetic_coil` | MAGNETIC_FIELD | redstone pulse |

All craftable at iron-tier (`data/projecthero/recipe/*`), in the Superheroes creative tab.

## Power combos (`PowerCombos`)

Applied whenever the player **owns both** powers (active or not); Thor is never involved.
Implemented: Meteor Slam (Strength+Flight — Dive Bomb / Ground Slam scale with fall speed/distance),
Water+Electrokinesis (wet targets ×1.6 electrical damage), Super Speed+Electrokinesis (sprinting
builds `static_charge` for the next Electric Bolt), Geokinesis+Super Strength (bigger Boulder Lift),
Pyrokinesis+Flight (flame trail at speed), Durability+Size (`sizeStabilityFactor`), Energy
Absorption+Laser (`energyRefillsLaser`). Others from spec §14 are documented here as intent; the
listed ones are wired.

## HeroPack Guide

`projecthero:heropack_guide` — a book item (Uncommon), in the Superheroes creative tab, crafted from
book + amethyst + paper. Right-click opens `HeroPackGuideScreen`: a dependency-free chapter book
(scrollable clickable index + word-wrapped scrollable content). Content is built by
`HeroPackGuide.chapters()` **from the power registry + shared translation keys** — 6 framing
chapters (Overview/Controls, Mutation System, Rare Structures, Laboratory Devices, Power Combos,
Thor & Mjolnir) + one chapter per power (abilities/keys, passives, serum recipe, trigger, lab
device). Adding the guide has zero effect on Thor.

**Mutation capacity:** default **3** permanently-owned powers per player, configurable
(`config/projecthero.json` → `mutationCapacity`). A player may own several but only **one**
experimental power occupies the six ability slots at a time. Switching the active power (power wheel / `H`)
never resets cooldowns, never duplicates attributes/passives, and turns off the previous power's
toggles.

**Data storage:** all experimental state lives in one isolated attachment
`projecthero:experimental_state` (`ExperimentalState`) — owned powers, active power, ability cooldowns
(absolute ready-at game time, so they survive relog / death / dimension change / power switch),
toggle/cycle states, power resources, per-power research stage. Persistent + copy-on-death. Never
shares a key with any Thor attachment.

**Research progression:** `UNKNOWN → RESEARCH_FOUND → SERUM_STABILIZED → EXPOSURE_SURVIVED →
MUTATION_CONFIRMED`. Once confirmed, the recipe + trigger are permanently readable and reproducible
(not RNG-locked).

---

## Configuration (`config/projecthero.json`)

| Key | Default | Effect |
|---|---|---|
| `mutationCapacity` | 3 | Max permanently-owned experimental powers per player |
| `unstableMutationDurationTicks` | 1200 | Exposure window after drinking a serum |
| `cooldownMultiplier` | 1.0 | Global multiplier on every experimental ability cooldown |
| `abilityTerrainDamage` | true | Whether abilities may break/place real blocks |
| `abilityPvpDamage` | true | Whether experimental abilities damage other players |
| `abilityFireSpread` | false | Whether fire abilities spread fire to the world |
| `abilityHardCrowdControlOnPlayers` | true | Whether prisons/grabs/stuns can affect players |
| `largeAbilityDestruction` | 1.0 | Scale of world-changing done by large ultimates |
| `structureRarityMultiplier` | 1.0 | Structure spacing multiplier (>1 = rarer) |

Thor is not configured here and is unaffected by any of these.

---

## HUD

Six compact ability boxes near the lower-right (`AbilityHud`), shown for experimental-power context:
slot key letter, cooldown shading + remaining seconds, ACTIVE edge for toggles. Hold **Left Alt** to
show full ability names. Thor keeps its own Storm Energy bar (`ThorHud`). *(Icons, resource meters,
flight stamina and the radial power wheel are a later polish pass.)*

---

## Admin / testing commands

`/heropower grant <power> [player]` · `revoke` · `active <power|none> [player]` · `list [player]` ·
`clear [player]` · `serum <power> [player]` (give the brewed serum) · `research <power> [player]` ·
`status [player]` (show pending-mutation state) — op level 2. Power argument is the short key, e.g.
`power_01_super_strength`.

**v0.6.19 — categories on `grant` / `revoke`.** Both now also take an explicit category:

* `/heropower grant experimental <key> [player]` — the bare form above, spelled out.
* `/heropower grant hero <thor|iron_man|spider_man|max_steel> [player]` — grant a Hero-Tier power the
  same way that hero's own command does (Thor → worthy; Tony Stark → granted; Spider-Man → evolves
  from Spider Adhesion, granting that first if missing; Max Steel → bonds).
* `/heropower revoke hero <…> [player]` — tear a Hero-Tier power down (Thor → score 0; the other
  three call their own `revoke`).

---

## Recent tuning — v0.10.10

- **HUD meters are an allow-list now.** `AbilityHud` used to draw a bar for any resource a
  hand-maintained deny-list did not name, defaulting the unknown to "reserve, max 500" — so every flag,
  entity id and mode number a power ever stored became a permanent bar stuck at 1/500 (Water's
  `spraying` flag was the second, phantom Water Beam bar; Crystalkinesis' `skating` was another).
  A resource must now be listed in `AbilityHud.KIND` to be drawn at all, and its kind decides when.
- **Telekinesis** rebuilt around the Psi reserve — see its entry above. Force Pull moved to Sneak+R and
  G became the Telekinetic Barrier; the ultimate is a 5 s hold on 90 s.
- **Elasticity:** damage raised across the kit, Elastic Form is projectile-proof, the bounce damps out,
  and Slingshot at a creature is a dash-strike.
- **Density:** Phase capped 2 blocks above ground and rendered translucent (`LivingEntityPhaseMixin`);
  Singularity is a 5 s hold, capped at 5 blocks, endable early, cooldown on end.
- **Geokinesis:** Colossal Rock detonates with a real power-6 explosion (blocks only — its entity damage
  is still the hand-rolled pass) on top of a widened earth-only dig; Boulder Lift is seven real
  no-gravity falling blocks made of the local ground; Earth Swim is fast inside earth and slow outside it.
- **Cryokinesis:** the ice tool is shaped on the Sneak+R press (the old 2 s hold essentially never paid
  out); powder snow no longer slows a cryokinetic (`PowderSnowSlowMixin`).
- **Titan (performance):** every A*-pathfinding goal stripped and the chase replaced with direct
  `MoveControl` steering. Path cost scales with entity footprint — the node evaluator samples a
  ceil(w)×ceil(h)×ceil(w) box **per visited node**, so on an 18-block frame one search is thousands of
  times an ordinary mob's. Passive block destruction now only scans the slab it is walking through, and
  only while it is actually moving.
- **Punisher:** grenades stick to the first surface they touch instead of bouncing; bullet holes fade
  after 10 s instead of a minute.
- **Thor:** R throws Mjolnir, Sneak+R recalls it.
- **Squads:** new. See the Squads section below.

## Squads (v0.10.10)

`com.projecthero.mod.squad`. A player-created group whose members cannot damage each other, persisted
as `SavedData` on the overworld (`SquadManager`). Friendly fire is suppressed on
`ServerLivingEntityEvents.ALLOW_DAMAGE` rather than in `HeroDamageRules` — that class only runs for a
player with an experimental power selected, whereas a squad has to cover ordinary swords and every hero
ability for members with no powers at all. The attacker is resolved through `DamageSource.getEntity()`,
so a squadmate's arrow, grenade or repulsor beam is as harmless as their sword; self-harm and
`GENERIC_KILL`/`FELL_OUT_OF_WORLD` are untouched.

`/squad` is the mod's only top-level command root besides `/projecthero` (and is mirrored under it).
The roster screen is **P** (`SquadScreen`), fed by `SquadInfoPayload` pushed every 10 ticks — health,
hero identity (`HeroIdentity`, ordered to match `AbilityRouter`'s own context priority), coordinates,
and distance + compass bearing, with cross-dimension members showing the dimension instead.

## Recent tuning — "changes 6" (v0.2.5–0.2.6)

New shared helper `com.projecthero.mod.hero.power.ModeMeter`: a stance bar that drains while a mode
toggle is on and recharges while off (like flight stamina), forcing the toggle off when it empties.
Its `regen()` seeds an untouched meter to full (fresh mutation = full bar). Used by Crystal Armor /
Earth Armor, Charged Mode, Tailwind, Repulsion Field, Giant Form, and (v0.2.6) each of the Pyro and
Cryo channels/stances independently — Flamethrower, Flame Body, Freeze Beam and Frozen Armor each
have their **own** reserve now (no shared heat/cold).

**HUD (v0.2.6):** `AbilityHud` draws one bar per meter that is *in play* — a reserve while it is
below full (draining or refilling), a build-up/timer meter while it has anything on it. Full-and-idle
meters are hidden. The keybind row and power name are pushed up to fit however many bars show. Each
mode/armor meter resource is now named after its ability id, so bars are labelled with the ability
name ("Frozen Armor", "Charged Mode", "Repulsion Field", …). Telekinesis flight is `HeroFlight.infinite`
now (gated by the psi bar only, no flight-stamina bar).

- **Telekinesis:** Psychic Flight and held grabs/blocks run purely off the psi bar (no separate flight stamina).
- **Size:** Giant/Large move ranges scale with body size (~4× reach in Giant). The size-strain bar is no longer reset on toggling Giant on/off — once you shrink back it slowly recharges on its own.
- **Flight:** Sonic Flight keeps a continuous vapour trail, can be ended early (press again), and only goes on cooldown once it ends.
- **Crystalkinesis:** Crystal Prison = 7 s amethyst cage + Slowness X + jump lock (total lockdown). Crystal Eruption scatters ground amethyst shards across an 8-block radius. Crystal Armor = +10 to every move + strain bar.
- **Elasticity:** Elastic Form now rebounds off the ground on every landing, scaled to fall speed.
- **Electrokinesis:** Electric Bolt 8 dmg + static shock; Chain Lightning 8 dmg, unlimited hops within 8 blocks; Charged Mode = +10 all moves, Speed III, 25 s charge bar, 5-damage backlash when hit; Electromagnetic Pull → **Power Surge** (plant a redstone block up to 50 blocks away); Electrical Storm → one 60-damage bolt + small crater + 8 s hard slow. Every hit leaves a visible static-shock spark aura.
- **Sonic Scream:** Sonic Blast 10 dmg / 5 s cd; Focused Scream 16 dmg / 10 s cd; Sonic Jump negates landing fall damage; Supersonic Scream is a 20-block, 35-damage cone; Echolocation highlight is viewer-only.
- **Shadow Manipulation:** Shadow Bolt 8 dmg + 7 s blind; Shadow Tendrils 10 dmg + 7 s blind; Shadow Step is a plain 25-block blink (4 s cd); Shadow Clone → **Shadow Bind** (blind + total lockdown for 7 s); Total Darkness lasts 25 s with a bar, ends early, 50 s cd on end.
- **Density:** immune to all damage while phasing; phasing holds position on entry (only sinks on sneak).
- **Geokinesis:** Earth Armor = +8 to every move + strain bar.
- **Pyrokinesis:** Flame Body lays a fire trail and ignites everything within 3 blocks; bleeds the heat reserve.
- **Cryokinesis:** Frozen Armor slows enemies within 5 blocks and leaves a snow trail; bleeds the cold reserve.
- **Wind:** Tailwind shoves anything within 2 blocks back ~1 block; drains a wind bank.
- **Energy Absorption:** Energy bar → 500. Energy Drain is now HOLD (siphon powered blocks + acts as shield, 3 s cd on release). Energy Blast 8 dmg / cheaper. Absorption Shield → **Energy Dash** (3 s cd). Absorption Mode trickle-drains the meter and charges melee hits. Overload = 1 damage per percent charged, with a blast/crater that grows with charge.
- **Shockwave:** Shockwave Punch 8 / Ground Wave 12 / Kinetic Detonation 35. Recoil Jump launches up + forward with no landing fall damage (and further with built Charge). Repulsion Field is now HOLD with a repulsion bank (~13 s).

## Recent tuning — "changes 7" (v0.2.7)

- **Freeze Beam / Flamethrower are now overheat gauges, not reserves.** Their meter (`freeze_beam` /
  `flamethrower`) starts at **zero** and *climbs* while the channel is held — the cryokinetic is
  freezing themselves, the pyrokinetic is overheating. It hits max in ~4 s (beam) / ~13 s (stream),
  at which point the channel cuts out with the "too cold" / "overheating" message, and it bleeds back
  toward zero while not channelling (locked out until it drops ~20 below max). `ModeMeter.cool()` is
  the new inverse of `regen()` for this. Flame Body and Frozen Armor are unchanged (still drain-down
  reserves). In `AbilityHud` these two are `Kind.BUILD` (amber; frost-blue for Freeze Beam) — shown
  only while non-zero, so the bar appears when you start firing and disappears once it has cooled.
- **Timed self-flight bars fixed** (`flameflight`, `rockflight` from `TimedSelfFlight`). They were
  read as 0..500 reserves, so the bar showed ~20 % full on cast and never disappeared when spent.
  `AbilityHud` now treats any `*flight` meter (except `flight` stamina) as a 0..100 `Kind.TIMER`:
  full on cast, counts down, hidden at zero.

## Recent tuning — "changes 8" (v0.2.8)

**HUD (`AbilityHud`):** bar row height 12 → 16 so stacked meter bars have a gap; the Left-Alt
expanded ability-name column is anchored to end just above the keybind boxes (`y0 + i*10 - 52`) so
the bottom name never falls off screen. `sparkle` added to the 100-max meter list + labelled
"Sparkling Flight"; `sparkling` / `holy_ticks`-style flags excluded from bars.

**Shockwave — Charge:** a `WARDEN_SONIC_CHARGE` whine on press; the charge tick now emits a fuller
(still restrained) swirl — 4 `ELECTRIC_SPARK` + 3 `CRIT` every 2 t, a `SONIC_BOOM` pulse every 8 t.

**Gravity Manipulation:**
- Gravity Push 3 → **9** damage.
- Gravity Crush rebuilt: 60 t → **200 t (10 s)**, **4 damage/second** (was 2/0.5 s), Slowness IV
  refreshed every tick for the whole duration, still pins the target down.
- Gravity Well → **black hole** (same model as Density's Singularity, minus the lift): player-centred,
  `setInvulnerable(true)` re-asserted each tick, 12 s, hauls creatures within **10 blocks** inward,
  15 dmg/s within 4.5. Drops invulnerability on end / power switch. Removed the old `well_x/y/z` aim
  point.
- Levitate → a telekinetic-style **grab** (`Handlers.instantTicking` + `GrabHelper`, range 16,
  hold 160 t, press again to hurl for 4 dmg). Replaces the old Levitation-effect version.

**Plant Manipulation / Chlorokinesis:**
- Thorn Shot 5 → **8** dmg + **Poison II / 8 s**.
- Vine Grab 3 → **12** dmg + roots the target (Slowness VIII + Weakness II, 8 s) and zeroes its
  velocity.
- Vine Swing anchor range 22 → **100** blocks.
- Overgrowth 6 → **27** dmg, Poison → **Poison V / 10 s**, radius 7 → **10**, 20 → 44 growth blocks
  (some 2 high) at TTL 120 so the thicket recedes after ~6 s.
- Living Wall → `ConjuredStructures` wall / dome (look up) / bridge (look down) of oak leaves, like the
  other wall powers, **no flight**. Sneak + activate instead sets `bonemeal_until` for 30 s; a
  `UseBlockCallback` bone-meals (`BoneMealItem.growCrop` / `growWaterPlant`) any block you right-click
  in that window.
- Nature's Blessing rebuilt: while standing on grass/moss/podzol/mycelium **or** within 5 blocks of
  any plant/leaf (tag + `BushBlock` check), grants **Regeneration II** and a **+8 `ATTACK_DAMAGE`**
  modifier; `natureBonus()` adds the same +8 to Thorn Shot / Vine Grab / Overgrowth. Clears on
  toggle-off / leaving vegetation / power switch (`natures_blessing_atk` modifier id).

**Invisibility / Light Manipulation:**
- Light Blast 5 → **10** dmg.
- Flash now also deals **8** damage; blind + slow **100 t → 200 t (10 s)**.
- Mirage Dash → **Sparkling Flight** (id kept `mirage_dash`; catalog INSTANT 5 s → **HOLD 3 s**). A
  hold-to-fly ability: creative-fly flags + a look-direction impulse each tick, body dissolved via
  re-applied hidden `INVISIBILITY` + `END_ROD`/`GLOW` column particles + the new armour-hide mixin.
  `sparkle` meter (0..100, drain ~25 s, regen ~18 s via `PowerPassives.registerTick`); `endSparkle()`
  drops flight, restores the body, adds brief Slow Falling, starts the 3 s cooldown.
  `PowerPassives.register` clears a stuck `sparkling` + mayfly on join/respawn/switch.
- Cloaking Toggle: unchanged server-side, but the new `HumanoidArmorLayerMixin` cancels the armour
  layer while `InvisibilityLightHandlers.hideArmor(player)` (cloaked **or** sparkle-flying), so the
  player is fully invisible, armour included.
- Perfect Cloak → **Holy Light** (id kept `perfect_cloak`): a 60 t `instantTicking` channel — one
  thick `END_ROD`/`GLOW` beam (12 dmg/tick to the aimed target) with a radiant burst every 10 t
  (24 dmg + Glowing + Blindness in a 5-block radius, `FLASH` particle). No longer applies
  invisibility (gametest `invisibilityPerfectCloakAppliesInvisibility` → `invisibilityHolyLightChannels`).

**New client mixin:** `client.mixin.HumanoidArmorLayerMixin` (HEAD of `HumanoidArmorLayer.render`,
cancellable) — registered in `projecthero.client.mixins.json`.

`./gradlew build` green, 44 gametests.

## Full rework — "changes 9" (v0.2.9): Magnetic Manipulation

Power 26 rebuilt from the ground up so it manipulates **actual magnetic metal**, not invisible force.

**Material system** (`hero.power.p26.MagneticMaterials`) — the single authority:
- Tags `#projecthero:magnetic` (block) and `#projecthero:magnetic` (item) hold the concrete vanilla list
  (iron block/ore/raw-iron, bars, doors, trapdoors, chain, anvils, hopper, cauldron, heavy pressure
  plate, rails, lanterns, lodestone, netherite block; iron/chainmail/netherite gear, iron nuggets/
  ingots/raw iron, shears, shield, buckets, flint & steel, compass, minecarts, tripwire hook, …).
- Java adds: iron/netherite `TieredItem` tier check (covers modded gear), netherite detection,
  lodestone, `canDisplace` (magnetic + no block-entity + not door/bed + breakable), entity checks
  (minecart, iron golem, magnetic `ItemEntity`, magnetic `FallingBlockEntity`, metal-equipped
  `LivingEntity`), and `loadout(entity)` → `(pieces, netherite)` for Crush/Sense.
- **Excluded everywhere:** copper, gold, and **Mjolnir** (`isMjolnir` short-circuits every check;
  `MjolnirEntity` and a dropped-Mjolnir `ItemEntity` are never magnetic).

**Mass system** (`MagneticMass` enum LIGHT / MEDIUM / HEAVY / EXTREME) — one weight class per object,
carrying `launchSpeed`, `accel`, `damage` (6 / 9 / 18 / 28), `knockback`, `gripFollow`. `of(ItemStack)`,
`of(BlockState)`, `of(Entity)`. Netherite objects resolve HEAVY/EXTREME.

**Abilities** (ids renamed, catalog updated; `everyAbilityHasAHandler` still green):
- **R `ferrous_shot`** (INSTANT 3 s) — source priority looked-at block → nearest magnetic drop →
  nearest magnetic block (r6). Blocks fly as `FallingBlockEntity.fall` (removed from world, no dupe,
  places/drops on landing); items as a 1-count `ItemEntity` split off the drop. Tracked in
  `PROJECTILES`, mass-scaled damage on first living contact, owner never hit. No metal → "Requires
  magnetic metal", no cooldown.
- **G `magnetic_grip`** (INSTANT, ~1.5 s between grips) — grab block/item/minecart/iron-golem, hold
  on aim with mass-scaled follow lag, press again to hurl (tracked like Ferrous Shot). Golem/entity
  grips capped at 3 s. Released on power switch.
- **H `polarity_leap`** (INSTANT 2 s) — was Magnetic Flight (removed). Impulse toward a looked-at
  magnetic block/entity ≤24 blocks (`addImpulse`, preserves momentum), `resetFallDistance` + 20-tick
  `no_fall_until`. No target → "Target magnetic metal", no cooldown.
- **Z `metal_storm`** (INSTANT 18 s) — gather ≤8 magnetic drops + displaceable blocks (r12 / r6),
  need ≥2. 30 t orbit, then one-shot hurl toward aim. Per-target damage cap 30/storm (`dealt` map).
  Static `STORMS` map, ticked from `MagneticHandlers.tick`.
- **V `magnetic_crush`** (INSTANT 9 s) — `loadout(target)`; `dmg = 3 + pieces·4` (7 → 27), ×0.6 for
  netherite (still full CC), Slowness + Weakness, hard immobilise at 3+ pieces, drags loose metal in.
  No magnetic gear on target → no effect, no cooldown.
- **C `magnetic_sense`** (TOGGLE) — was Magnetic Vision (server GLOWING scan removed). Entities:
  client-only glow via extended `EntityGlowMixin` (magnetic entities ≤20). Blocks: new
  `client.MagneticSenseClient` — every 6 t, r10, ≤36 marks, subtle particles (iron spark / lodestone
  soul-flame / netherite smoke). Zero server scanning.

**Passives** (`passive.metal_attraction`, `passive.projectile_immunity`): magnetic drops within 4
blocks drift to the player (magnetic only); controlled objects exclude their owner from all damage;
faint spark on the metal you look at.

**Wiring:** `MagneticHandlers.tick(server)` added to `Project HeroMod`'s `END_SERVER_TICK` (projectiles +
storms). `client.mixin.EntityGlowMixin` extended; `client.MagneticSenseClient` registered in
`Project HeroModClient`. Serum / trigger / Electromagnetic Coil Pair unchanged.

`./gradlew build` green, **46 gametests** (2 new: `magneticCrushNeedsMetalOnTarget`,
`ferrousShotConsumesNearbyMetalDrop`).

<!-- AUTO-GENERATED BELOW THIS LINE by scratchpad/gen_docs.js — edit PowerCatalog.java / gen_lang.js, not here -->

## Experimental Power Encyclopedia

27 powers, 162 abilities. Slot → key: 1→R, 2→G, 3→H, 4→Z, 5→X, 6→C.

### 1. Super Strength  
**ID:** `projecthero:power_01_super_strength` · **Category:** Physical

Enhanced physiology: devastating melee force, mobility, grabbing and tanking damage.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Power Punch | Heavy melee strike with high damage and knockback. |
| 2 | G | Ground Slam | Smash the ground for a radial shockwave that damages and launches nearby entities. |
| 3 | X | Super Leap | Charge briefly, then launch toward where you are looking. |
| 4 | Z | Thunderous Impact | Massive charged ground punch with a large shockwave and cosmetic terrain cracking. |
| 5 | V | Grab & Throw | Grab a valid mob/player/object; activate again to hurl it where you aim. |
| 6 | C | Brace | Toggle a defensive stance: high knockback resistance and damage reduction, less speed. |

**Passives:** Unarmed hits deal 7 damage; a held tool adds its own damage on top; Slightly higher jump height; Faster breaking of stone-like blocks; Reduced fall damage

**Serum:** Experimental Adrenal Serum  
**Mutation trigger:** Drink the serum, then take an electrical surge from an Overloaded Redstone Coil (or a natural lightning strike).

### 2. Laser Vision  
**ID:** `projecthero:power_02_laser_vision` · **Category:** Energy

A photonic mutation turning the eyes into precision tools, combat beams and high-output weapons.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Heat Vision | Hold to fire two continuous energy streams from your eyes that damage and ignite targets. |
| 2 | G | Focused Beam | Narrow, high-damage beam with longer range and limited block-cutting. |
| 3 | X | Heat Burst | Instant short-range eye blast that knocks nearby enemies away. |
| 4 | Z | Maximum Output | Sustained full-power beam with extreme damage, fire and an impact burst. |
| 5 | V | Precision Vision | Low-power utility beam: light TNT, melt ice/snow, break glass. |
| 6 | C | Thermal Vision | Toggle thermal highlighting of nearby living entities, through limited cover. |

**Passives:** Reduced blindness duration; Subtle red eye glow while charged

**Serum:** Photon Sensitivity Serum  
**Mutation trigger:** Drink the serum and look into an active Experimental Light Projector / Beacon Lens for several seconds.

### 3. Flight  
**ID:** `projecthero:power_03_flight` · **Category:** Movement

True superhero aerial movement: dashes, hovering and high-speed bursts.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Air Dash | Rapid forward aerial dash that preserves momentum. |
| 2 | G | Dive Bomb | Accelerate downward; hitting the ground creates an impact shockwave scaled to dive speed. |
| 3 | X | Flight Toggle | Enable or disable superhero flight. |
| 4 | Z | Sonic Flight | Temporary maximum-speed flight with a sonic boom and wake knockback. |
| 5 | V | Hover | Lock altitude and greatly reduce drift for precise aerial combat. |
| 6 | C | Aerial Burst | Powerful vertical launch that immediately gains altitude. |

**Passives:** Improved air control; Reduced fall damage while flight is unlocked

**Serum:** Gravitational Instability Serum  
**Mutation trigger:** Drink the serum on an activated Unstable Gravity Plate or inside a Gravity Distortion Rig.

### 4. Super Speed  
**ID:** `projecthero:power_04_super_speed` · **Category:** Movement

A speedster mutation: traversal, combat bursts, evasion and momentum.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Speed Blitz | Dash to the targeted enemy and strike at high speed. |
| 2 | G | Rapid Assault | A burst of multiple rapid strikes to nearby valid targets. |
| 3 | X | Momentum Dash | Instant directional dodge based on current movement. In speed modes, jumps also keep their momentum for long leaps. |
| 4 | Z | Overdrive | For 25s: +500% speed, +150% attack speed, fall immunity, and every Super Speed move hits twice as hard. Stacks with Speed Mode (50s cooldown). |
| 5 | V | Whirlwind | Run a tight circle to push enemies/projectiles away and extinguish nearby fire. |
| 6 | C | Speed Mode | Toggle: +200% speed, +50% attack speed, run on water, 2-block step, +100% swim speed, -80% fall damage. Stacks with Overdrive. |

**Passives:** Faster sprinting; Higher step-up capability at speed; Reduced collision slowdown

**Serum:** Hypermetabolic Serum  
**Mutation trigger:** Drink the serum and sprint across a run of powered/charged copper plates.

### 5. Geokinesis  
**ID:** `projecthero:power_05_geokinesis` · **Category:** Elemental

Control stone, dirt and earth as a battlefield weapon and defensive tool.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Rock Shot | Rip a small rock from the ground and fire it as a projectile. |
| 2 | G | Earth Spike | Cause stone spikes to erupt beneath the targeted location. |
| 3 | X | Stone Wall | Raise a temporary wall from suitable terrain. |
| 4 | Z | Earthquake | Large seismic pulse that damages, disrupts and launches nearby entities. |
| 5 | V | Boulder Lift | Pull a large temporary boulder from the ground and hold it for an aimed throw. |
| 6 | C | Earth Armor | Toggle stone plating: defense and stronger melee, reduced speed. |

**Passives:** Improved mining speed on stone/earth while ability energy is available

**Serum:** Geological Resonance Serum  
**Mutation trigger:** Drink while standing on natural stone/deepslate, ideally in a Geological Resonance Chamber.

### 6. Crystalkinesis  
**ID:** `projecthero:power_06_crystalkinesis` · **Category:** Elemental

An amethyst/crystal power set: sharp projectiles, prisons, barriers and reflective armor.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Crystal Shard | Fire a fast, sharp crystal projectile. |
| 2 | G | Crystal Spikes | Grow a cluster of crystals beneath targeted enemies. |
| 3 | X | Crystal Barrier | Create a temporary translucent crystal shield. |
| 4 | Z | Crystal Eruption | A large field of crystals erupts around you. |
| 5 | V | Crystal Prison | Encase a target in crystal temporarily, heavily restricting movement. |
| 6 | C | Crystal Armor | Toggle crystal plating: defense, minor damage reflection, enhanced melee. |

**Passives:** Reduced damage from amethyst/crystal hazards you create

**Serum:** Crystalline Resonance Serum  
**Mutation trigger:** Drink inside a natural Amethyst Geode or a functioning crystal chamber.

### 7. Electrokinesis  
**ID:** `projecthero:power_07_electrokinesis` · **Category:** Energy

A general electricity mutation - intentionally weaker than Thor’s true lightning.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Electric Bolt | Fire a quick electrical projectile. |
| 2 | G | Chain Lightning | Strike one target and arc to nearby valid targets. |
| 3 | X | Electric Dash | Short high-speed electrical dash with brief collision immunity. |
| 4 | Z | Electrical Storm | A temporary storm field that repeatedly shocks nearby enemies. |
| 5 | V | Electromagnetic Pull | Pull metallic items, minecarts and metal-linked entities toward you. |
| 6 | C | Charged Mode | Electrify your body: melee attackers are shocked, punches deal bonus electrical damage. |

**Passives:** Reduced lightning damage; Can briefly power compatible redstone targets with some abilities

**Serum:** Electrochemical Serum  
**Mutation trigger:** Drink the serum and activate an Overloaded Redstone Coil while inside its discharge radius.

### 8. Pyrokinesis  
**ID:** `projecthero:power_08_pyrokinesis` · **Category:** Elemental

Direct heat and flame control: ranged fire, mobility and an aggressive flame-body stance.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Fireball | Launch a compact explosive fire projectile. |
| 2 | G | Flamethrower | Hold to project continuous close/medium-range flame. |
| 3 | X | Flame Dash | Use a fire burst to launch rapidly where you aim. |
| 4 | Z | Inferno | A large fiery eruption around you with heavy damage and ignition. |
| 5 | V | Flame Wall | Create a temporary line of fire at the targeted ground area. |
| 6 | C | Flame Body | Toggle a flame aura granting fire immunity and burning melee attackers. |

**Passives:** Strong or permanent fire resistance depending on balance mode

**Serum:** Thermal Mutation Serum  
**Mutation trigger:** Drink the serum and remain in ordinary fire for several seconds while resistance protects you.

### 9. Cryokinesis  
**ID:** `projecthero:power_09_cryokinesis` · **Category:** Elemental

Ice control: freezing, terrain creation, defensive walls and fast ice traversal.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Ice Bolt | Fire a chilling projectile that builds freeze on targets. |
| 2 | G | Freeze Beam | Hold a beam on enemies to progressively freeze and slow them. |
| 3 | X | Ice Slide | Create temporary ice beneath your movement for fast skating. |
| 4 | Z | Absolute Zero | Freeze entities and water in a large radius and extinguish fire. |
| 5 | V | Ice Wall | Create a large temporary wall of ice. |
| 6 | C | Frozen Armor | Toggle ice plating for defense; melee attackers gain freeze stacks. |

**Passives:** Powder snow immunity; Reduced freezing damage

**Serum:** Cryogenic Serum  
**Mutation trigger:** Drink the serum while submerged in Powder Snow for several seconds.

### 10. Telekinesis  
**ID:** `projecthero:power_10_telekinesis` · **Category:** Mental

Psionic force manipulation, every ability paid for out of one shared **Psi** reserve (500). Empty the
reserve and the whole power locks out for 10 seconds ("burnout"): every toggle drops, every ability
refuses, and Psi does not even begin to refill until the lockout is over. Continuous drains (flight)
cut out at a soft floor rather than emptying the bar; deliberate spends and blows soaked by the
Barrier are what can actually burn you out. Psi regeneration is parked for 1 s after anything spends
it, so no channel can be outrun by the trickle.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Force Push | 10 damage + a hard shove in a 4.5-block ball ahead. 1 s cooldown. |
| 1 | Sneak+R | Force Pull | Reel in the aimed target **and** every loose item within 20 blocks (they are handed straight over inside 1.5 blocks). 1 s cooldown. |
| 2 | G | Telekinetic Barrier | Toggle. Nothing gets through while it holds; drains Psi steadily and charges 4× the blocked damage on top, so a heavy hit can collapse it into a burnout. |
| 3 | X | Psychic Flight | Low, steady drain (~1 min from full). Cuts out at the soft floor so it can never drop you AND lock you out. |
| 4 | Z | Psychic Detonation | Hold 5 s: everything within 20 blocks is lifted and hauled toward you, held at a 2-block standoff, then blown apart for 55. 90 s cooldown, huge Psi cost, and Psi regenerates at 20% for 10 s afterwards. |
| 5 | V | Telekinetic Grab | Press to grab, press again to throw. |
| 5 | Sneak+V (holding) | Set Down | Release the victim gently — Slow Falling, no throw, no damage. |
| 5 | Sneak+V (empty) | Force Crush | Reel a victim in and squeeze: 10 damage/second, drains Psi hard, and roots you (−85% speed) while it runs. |
| 6 | C | Block Manipulation | Lift a block and steer it with the crosshair (a teleport packet per tick, so it tracks smoothly rather than in 1-second steps); release to throw. |
| 6 | Sneak+C | Chunk Manipulation | Tear a 3×3 slab out of the ground and hurl it: 26 damage to the first thing it reaches, then it bursts. |

**Passives:** Nearby dropped items drift slightly toward you while charged; everything runs off the Psi bar

**Serum:** Psionic Serum  
**Mutation trigger:** Drink the serum near an active Enchanting Table with enough bookshelves to trigger a psionic resonance.

### 11. Teleportation  
**ID:** `projecthero:power_11_teleportation` · **Category:** Movement

Short-range spatial distortion: combat blinks, marks and limited wall phasing.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Blink | Short teleport toward the crosshair, stopping safely before invalid blocks. |
| 2 | G | Target Teleport | Teleport to a safe position behind a targeted enemy. |
| 3 | X | Escape Blink | Instant short teleport backward from your facing. |
| 4 | Z | Spatial Frenzy | Rapidly blink between nearby enemies and strike each valid target. |
| 5 | V | Teleport Mark | Place one return marker; activate again to return if the spot is still valid. |
| 6 | C | Phase Jump | Short teleport through a thin wall with strict distance and safety checks. |

**Passives:** Reduced Ender Pearl damage

**Serum:** Spatial Distortion Serum  
**Mutation trigger:** Drink the serum and throw/use an Ender Pearl while the effect is active.

### 12. Super Regeneration  
**ID:** `projecthero:power_12_super_regeneration` · **Category:** Physical

Aggressive cellular regeneration: constant passive healing that hunger and combat can't stop.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Rapid Heal | Instantly restore a burst of health. |
| 2 | G | Purge | Remove selected negative effects such as Poison, Weakness and Slowness. |
| 3 | X | Recovery Burst | Immediate recovery and short resistance after heavy recent damage. |
| 4 | Z | Second Wind | Briefly, lethal damage cannot drop you below half a heart. |
| 5 | V | Cellular Surge | +1 HP/second for 30s on top of your normal regeneration, plus a movement and mining boost (45s cooldown). |
| 6 | C | Regeneration Mode | Toggle +1 HP/second healing; the only Super Regeneration ability that burns hunger (saturation every 5s). |

**Passives:** Passive regeneration of 1 HP per second, unaffected by hunger or combat

**Serum:** Cellular Regeneration Serum  
**Mutation trigger:** While the serum is active, survive being reduced below 3 hearts without dying.

### 13. Super Durability  
**ID:** `projecthero:power_13_super_durability` · **Category:** Physical

A tank mutation hardening the body against damage, knockback, projectiles and explosions.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Heavy Strike | Reinforced melee hit with extra stagger. |
| 2 | G | Shoulder Charge | Rush forward through enemies, dealing collision damage and knockback. |
| 3 | X | Block | Hold to cut damage from the frontal 180° arc by a further 60%; drains the guard bar. |
| 4 | Z | Unbreakable | Ignore 100% of all damage for 15 seconds. |
| 5 | V | Projectile Deflection | Hold to swat away nearby projectiles and be immune to them; drains the guard bar. |
| 6 | C | Tank Mode | Toggle a reinforced stance: a further 30% damage reduction and knockback resistance, lower speed. |

**Passives:** 30% less damage from all sources, with extra reduction vs fall and explosions

**Serum:** Dermal Reinforcement Serum  
**Mutation trigger:** Drink the serum and survive an explosion from TNT or a controlled blast chamber.

### 14. Sonic Scream  
**ID:** `projecthero:power_14_sonic_scream` · **Category:** Energy

Manipulate destructive sound: cones, focused blasts, mobility and sonar-like utility.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Sonic Blast | A quick cone-shaped sound attack with knockback. |
| 2 | G | Focused Scream | A long-range concentrated sonic beam. |
| 3 | X | Sonic Jump | Blast sound downward to launch into the air. |
| 4 | Z | Supersonic Scream | A huge area scream with severe knockback and glass-breaking. |
| 5 | V | Resonance | Vibrate selected fragile/compatible blocks until they break. |
| 6 | C | Echolocation | Toggle periodic sonar pulses that outline nearby living entities. |

**Passives:** Reduced self-damage from sonic effects

**Serum:** Resonance Serum  
**Mutation trigger:** Use a Goat Horn while the serum is active, ideally inside a resonant room/chamber.

### 15. Invisibility / Light Manipulation  
**ID:** `projecthero:power_15_invisibility_light_manipulation` · **Category:** Light

Bend light for stealth, flashes, mirages and limited offensive photonic attacks.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Light Blast | Fire a compact burst of concentrated light. |
| 2 | G | Flash | Blind/disorient nearby enemies for a short duration. |
| 3 | X | Mirage Dash | Briefly vanish while performing a fast evasive dash. |
| 4 | Z | Perfect Cloak | Full invisibility including armor, held items and particles for a limited time. |
| 5 | V | Decoy | Create a temporary visual duplicate that attracts hostile attention. |
| 6 | C | Cloaking Toggle | Sustained invisibility; attacking or heavy damage briefly reveals you. |

**Passives:** Reduced detection range while crouching

**Serum:** Photonic Refraction Serum  
**Mutation trigger:** Drink under direct sunlight with an unobstructed sky for several seconds.

### 16. Spider Climbing / Adhesion  
**ID:** `projecthero:power_16_spider_climbing_adhesion` · **Category:** Movement

Real surface adhesion -- walls, ceilings and the corners between them. Rebuilt in v0.6.3 (see
[Spider-Man](SPIDERMAN_REFERENCE.md) section 3 for why the old version could only do what a ladder
can). While either toggle is on, movement is expressed relative to whatever surface you are holding:
look where you want to go and walk. Stop and you stay put; sneak and you hold tighter; jump and you
push off along the real surface normal.

Vertical climbing, descent, holding position, horizontal crawling, full upside-down ceiling crawling,
and transitions in both directions between wall and ceiling all work. It grips anything with
collision geometry, so slabs, stairs, fences, logs and irregular cliff faces are all climbable, and an
established hold survives five ticks with nothing in reach so corners and block seams do not drop you.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Adhesive Strike | Hit a target and briefly stick/root it against a nearby surface. |
| 2 | G | Pounce | Launch toward the aimed target or surface. |
| 3 | X | Wall Leap | Launch away along the held surface normal (falls back to a backward leap when not adhered). |
| 4 | Z | Predator Rush | Temporary enhanced agility: rapid climbing, wall-running, attack speed and leaps. |
| 5 | V | Wall Grip | Hold on: stick to whatever surface you touch and move relative to it. |
| 6 | C | Adhesion Mode | The same grip, left on permanently. |

**Passives:** Reduced fall damage; Improved jump height; can be evolved into the Spider-Man Hero Class

**Serum:** Adhesive Mutation Serum  
**Mutation trigger:** Get hit by a Cave Spider while the serum is active and survive the poison.

**Evolution:** an **Arachnid Mutagen** turns this power into the Spider-Man Hero Class, consuming it
and freeing its mutation slot. Spider-Man uses the same adhesion engine with a faster climb speed and
a longer grace period, and needs no toggle -- it is always on. See
[SPIDERMAN_REFERENCE.md](SPIDERMAN_REFERENCE.md).

### 17. Elasticity  
**ID:** `projecthero:power_17_elasticity` · **Category:** Molecular

Rubber-like body manipulation: ranged punches, bouncing, grabs and slingshot movement.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Stretch Punch | Long-reach melee strike — 12 damage at up to 15 blocks. |
| 2 | G | Double-Fist Slam | Heavy forward smash — 17 damage in a 4-block ball, 7 blocks out. |
| 3 | X | Slingshot | Anchor and launch. At terrain: reel yourself to it. At a **creature**: anchor onto them, get hauled in at speed, and slam into them for 14. |
| 4 | Z | Giant Hammer Fist | 34 damage in a 4.5-block area, plus Slowness. |
| 5 | V | Elastic Grab | Pull a mob, player or item in; press again to hurl for 7. |
| 6 | C | Elastic Form | Toggle: +reach, +speed, Jump Boost, **projectile immunity**, melee attackers bounce off, and a slime-block rebound on landing that returns half the impact (so it always damps out). |

**Passives:** Reduced fall and collision damage

**Serum:** Elastic Polymer Serum  
**Mutation trigger:** While affected, fall at least 10 blocks onto a Slime Block and survive the rebound.

### 18. Density Manipulation  
**ID:** `projecthero:power_18_density_manipulation` · **Category:** Molecular

Switch between low and high density for mobility, heavy impacts, defense and limited phasing.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Heavy Punch | Momentarily increase fist density for a powerful strike. |
| 2 | G | Density Slam | Become extremely heavy and crash downward. |
| 3 | X | Intangible Dash | Lower density and dash through entities and very thin obstacles. |
| 4 | Z | Singularity | Hold 5 s to gather. Hangs ≤5 blocks off the ground, invulnerable, dragging everything in; 15 damage/s inside 6 blocks. 25 s, or press Z again to drop out early. 60 s cooldown, started only when it **ends**. |
| 5 | V | Phase | Toggle: walk through solid blocks, immune to damage, rendered see-through. Capped at 2 blocks above the ground — intangibility, not flight. |
| 6 | C | Density Mode | Cycle Light → Normal → Heavy: mobility vs damage/defense. |

**Passives:** Mode-dependent jump, fall speed and knockback modifiers

**Serum:** Molecular Density Serum  
**Mutation trigger:** Activate a Molecular Compression Chamber while the serum is active.

### 19. Shadow Manipulation  
**ID:** `projecthero:power_19_shadow_manipulation` · **Category:** Energy

Use darkness as a resource for bolts, tendrils, movement, clones and concealment.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Shadow Bolt | Fire a compact dark-energy projectile. |
| 2 | G | Shadow Tendrils | Bind and pull nearby enemies with shadow tendrils. |
| 3 | X | Shadow Step | Teleport to a nearby valid dark location. |
| 4 | Z | Total Darkness | Create a large darkness field where you gain mobility and offensive bonuses. |
| 5 | V | Shadow Clone | Create a temporary shadow duplicate to distract or attack lightly. |
| 6 | C | Shadow Form | Toggle stealth bonuses in darkness: partial invisibility, speed, reduced mob detection. |

**Passives:** Stronger ability regeneration in darkness; weaker in bright sunlight

**Serum:** Umbral Serum  
**Mutation trigger:** Drink at night while standing in true darkness (light level 0) for several seconds.

### 20. Energy Absorption  
**ID:** `projecthero:power_20_energy_absorption` · **Category:** Energy

Convert incoming heat, explosions and electricity into a stored meter for offensive discharge.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Energy Blast | Spend stored energy on a ranged projectile. |
| 2 | G | Energy Beam | Spend stored energy on a sustained beam. |
| 3 | X | Absorption Shield | Hold a shield converting a share of incoming damage into stored energy. |
| 4 | Z | Overload | Release all stored energy in a detonation scaled to meter level. |
| 5 | V | Energy Drain | Drain compatible powered blocks, redstone devices or energy entities into the meter. |
| 6 | C | Absorption Mode | Toggle passive partial absorption of fire, lightning, explosions and energy attacks. |

**Passives:** Visible Energy Meter; high charge produces a cosmetic aura

**Serum:** Energy Conversion Serum  
**Mutation trigger:** While the serum is active, survive at least two different energy-like damage sources.

### 21. Shockwave Manipulation  
**ID:** `projecthero:power_21_shockwave_manipulation` · **Category:** Kinetic

Amplify kinetic force into pressure waves, repulsion and charged impacts.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Shockwave Punch | Punch to send a short pressure wave forward. |
| 2 | G | Ground Wave | Strike the ground and send a traveling shockwave along the surface. |
| 3 | X | Recoil Jump | Blast a shockwave downward to launch yourself. |
| 4 | Z | Kinetic Detonation | Release a huge spherical shockwave around you. |
| 5 | V | Repulsion Field | Immediately shove nearby entities and projectiles away. |
| 6 | C | Charge | Hold to build kinetic energy; the next Primary, Secondary or Ultimate gains extra strength and range. |

**Passives:** Slight knockback resistance from internal kinetic control

**Serum:** Kinetic Amplification Serum  
**Mutation trigger:** Drink the serum and survive a TNT explosion or controlled blast chamber.

### 22. Plant Manipulation / Chlorokinesis  
**ID:** `projecthero:power_22_plant_manipulation_chlorokinesis` · **Category:** Nature

Control vines, roots and rapid growth for crowd control, movement, protection and regeneration.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Thorn Shot | Fire sharp plant projectiles. |
| 2 | G | Vine Grab | Grow vines that restrain and pull a target. |
| 3 | X | Vine Swing | Attach a temporary vine line to suitable blocks and swing. |
| 4 | Z | Overgrowth | Explosive plant growth across a large area, trapping and damaging enemies. |
| 5 | V | Living Wall | Grow a temporary root/vine barrier. |
| 6 | C | Nature’s Blessing | Toggle enhanced regeneration and energy recovery near healthy vegetation. |

**Passives:** Bone meal interactions are more efficient; Minor regeneration in lush areas

**Serum:** Chlorokinetic Serum  
**Mutation trigger:** Drink beneath open sky while surrounded by natural plant blocks, flowers or saplings.

### 23. Gravity Manipulation  
**ID:** `projecthero:power_23_gravity_manipulation` · **Category:** Spatial / Force

Control local gravity to push, crush, levitate and create dangerous gravity wells.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Gravity Push | A focused gravity pulse that throws targets away. |
| 2 | G | Gravity Crush | Increase gravity on a target/area, pinning enemies and dealing sustained pressure damage. |
| 3 | X | Zero-G | Temporarily reduce personal gravity for floating and aerial repositioning. |
| 4 | Z | Gravity Well | A strong localized gravity field that drags entities toward its center. |
| 5 | V | Levitate | Lift a targeted mob/object and hold it suspended. |
| 6 | C | Gravity Field | Cycle personal Low / Normal / High gravity with different mobility and combat effects. |

**Passives:** Reduced fall damage in Low gravity mode

**Serum:** Gravitic Distortion Serum  
**Mutation trigger:** Drink inside an active Gravity Distortion Rig or complete a controlled low-gravity drop test.

### 24. Wind Manipulation  
**ID:** `projecthero:power_24_wind_manipulation` · **Category:** Elemental

Air-pressure control: blades, tornadoes, flight-like movement and projectile defense.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Wind Blade | Fire a compressed blade of air. |
| 2 | G | Tornado | Create a small moving tornado that lifts and carries entities. |
| 3 | X | Wind Flight | Use controlled air currents for limited flight/propulsion. |
| 4 | Z | Hurricane | Create a large violent wind field around you. |
| 5 | V | Wind Push | A powerful defensive gust that repels mobs and projectiles. |
| 6 | C | Tailwind | Toggle a supporting wind current for faster movement, jumps and gliding. |

**Passives:** Reduced fall damage when using wind movement

**Serum:** Atmospheric Pressure Serum  
**Mutation trigger:** Drink inside an active Pressure Chamber / Industrial Fan Array.

### 25. Water Manipulation  
**ID:** `projecthero:power_25_water_manipulation` · **Category:** Elemental

Control water into projectiles, whips, prisons and movement; be extremely capable underwater.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Water Shot | Fire a high-pressure water projectile. |
| 2 | G | Water Whip | A sweeping water lash that damages and pulls targets. |
| 3 | X | Riptide | Fire yourself along a jet of water in a fast forward dash; further while wet, bowls over anything in the way. |
| 4 | Z | Tidal Wave | Launch a massive advancing wall of water that knocks back and displaces enemies. |
| 5 | V | Water Prison | Trap a target in a suspended water sphere for a short duration. |
| 6 | C | Aquatic Form | Toggle an enhanced underwater state: water breathing, fast swimming, clear vision. |

**Passives:** Improved swimming; No drowning while Aquatic Form is active

**Serum:** Hydrokinetic Serum  
**Mutation trigger:** Drink the serum and remain fully submerged for roughly a minute, or use a Hydrostatic Test Tank.

### 26. Magnetic Manipulation  
**ID:** `projecthero:power_26_magnetic_manipulation` · **Category:** Spatial / Force

Manipulate magnetically reactive metal (iron family + lodestone; netherite resists; **not** copper or
gold) for combat and movement. Every major ability needs a real magnetic block, item or metal-equipped
enemy in reach — otherwise it fails with subtle feedback and spends no cooldown. Mjolnir is immune.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Ferrous Shot | Launch a nearby magnetic object (looked-at block → dropped metal item → nearby metal block) at the crosshair; heavier objects hit harder. |
| 2 | G | Magnetic Grip | Grab a magnetic block / item / minecart / iron golem and float it on your aim; activate again to hurl it. |
| 3 | X | Polarity Leap | Pull yourself toward a magnetic object you are looking at, up to 24 blocks; preserves momentum, brief landing cushion. |
| 4 | Z | Metal Storm | Gather up to 8 nearby magnetic objects into orbit, then launch them at your aim; per-target damage capped. |
| 5 | V | Magnetic Crush | Crush an enemy with their own worn/held metal — damage scales with how much they carry, plus Slowness / immobilise. |
| 6 | C | Magnetic Sense | Toggle: highlight only magnetically reactive blocks and metal-equipped entities nearby (client-side, bounded). |

**Passives:** Nearby dropped magnetic items drift toward you; objects you control never damage you.

**Serum:** Magnetoreactive Serum  
**Mutation trigger:** Drink between two powered Electromagnetic Coils or inside a Magnetic Test Rig.

### 27. Size Manipulation  
**ID:** `projecthero:power_27_size_manipulation` · **Category:** Molecular

Change body scale for stealth, mobility or giant strength - neither form universally superior.

| Slot | Key | Ability | Effect |
|---|---|---|---|
| 1 | R | Giant Punch | Briefly enlarge the striking arm for extended reach and damage. |
| 2 | G | Stomp | Increase size and stomp, creating a short-radius ground shockwave. |
| 3 | X | Shrink | Instantly shift to Tiny form for evasion and access to small spaces. |
| 4 | Z | Giant Form | Become roughly 4-6 blocks tall briefly with major strength and reach. |
| 5 | V | Tiny Dash | While small or normal, rapidly shrink/dash through a narrow gap or under attacks. |
| 6 | C | Size Cycle | Cycle Tiny → Normal → Large, each with distinct movement, hitbox, damage and defense. |

**Passives:** Form-dependent reach, speed, step height and hitbox changes

**Serum:** Mass Compression Serum  
**Mutation trigger:** Drink inside a Mass Compression Chamber (Government Experiment Sites) or a craftable late-iron-tier version.

## Documented Power Combos

- Super Strength + Flight: Ground Slam / Dive Bomb becomes a Meteor Slam scaling with fall speed.
- Super Speed + Electrokinesis: high-speed movement builds charge for the next electrical ability.
- Water + Electrokinesis: wet targets take extra electrical damage; Chain Lightning spreads better.
- Geokinesis + Super Strength: larger boulders; stronger earth-armor melee.
- Cryokinesis + Water: water constructs can be instantly frozen into walls, spears or platforms.
- Pyrokinesis + Flight: flame propulsion visuals and a temporary fiery trail at high speed.
- Energy Absorption + Laser Vision: stored energy can refill or amplify Maximum Output.
- Super Durability + Size Manipulation: Large/Giant form gains stability and reduced self-impact damage.
- Wind + Fire: wind abilities extend flame range or create a fire vortex.
- Shadow + Teleportation: Shadow Step / Phase cost less energy where the destination is dark.
- Flying Brick build: Flight + Super Strength + Super Durability.
- Speedster build: Super Speed + Super Regeneration + Electrokinesis.
- Crystal Hero build: Crystalkinesis + Super Durability + Shockwave Manipulation.
- Psychic build: Telekinesis + Teleportation + Invisibility / Light Manipulation.

## Laboratory Devices (reference names)

- Overloaded Redstone Coil
- Charged Copper Plates
- Experimental Light Projector / Beacon Lens
- Unstable Gravity Plate
- Gravity Distortion Rig
- Geological Resonance Chamber
- Crystal Test Chamber
- Molecular Compression Chamber
- Mass Compression Chamber
- Controlled Blast Chamber
- Resonant Chamber
- Enchanting Resonance Setup
- Pressure Chamber / Industrial Fan Array
- Hydrostatic Test Tank
- Electromagnetic Coil Pair

