# Iron Man Era I -- The Gantry Age (v0.16.7, user-approved 2026-10-20)

The user's 20-suit Iron Man plan has four eras. **This build is Era I + the Stark Workbench only**; Eras II-IV come later.
Concept art (approved): `scratchpad/concepts_ironman_v2/` (generator `scratchpad/gen_ironman_era1_concepts.js`, built by
`scratchpad/make_era1_concepts.js` from `scratchpad/ironman_era1_main.js.txt`), workbench `10_stark_workbench.png`
(`scratchpad/gen_stark_workbench_concept.js`). Rules from the user that apply everywhere: H is the power selector only (no
invented H / N moves; the existing H faceplate / gantry uses stay), every key R / G / X / Z / V / C has a tap move AND a
Shift move, tooltips / UI text must wrap, armour must look THIN (no bulk), keep everything in 0.16.7 (do not bump the
version), never release.

## The suits (existing item ids are kept, so worlds keep their suits)

| Era I | id | Display name | Integrity | Absorb | Energy cap | Regen /s | Movement | Melee |
|---|---|---|---|---|---|---|---|---|
| Mk I | `mark_1` | Mk I Cave-Forged | 700 | 30% | 600 | 1.0 | no flight: rocket jumps; walk -10%; scale 1.0 (was 1.25) | +8 |
| Mk II | `mark_1_v2` | Mk II Golden Avenger | 850 | 32% | 800 | 1.5 | no sustained flight: the X boot-jet hover (5 s) | +7 |
| Mk III | `mark_iii` | Mk III Classic | 1400 | 38% | 1200 | 2.0 | full flight (drains 2 energy/s, sprint-flight 4/s; v0.16.7 (user): was 6 / 12) | +8 |
| Mk IV | `mark_4` | Mk IV Hydro | 1600 | 40% | 1200 | 2.0 | swims ~3x + water breathing + clear vision underwater; v0.16.7 (user): full flight like the Mk III (2/s, sprint 4/s; water-jet thrusters), Slowness I only while WALKING on land | +8 (x1.5 in water) |
| Mk V | `mark_v` | Mk V Stealth | 500 | 15% | 1000 | 2.5 | v0.16.7 (user): full flight like the Mk III (2/s, sprint 4/s) on near-silent clear air jets; Shift+X Active Camo; silent (no steps / vibrations, kept) | +6 |

Progression (Workbench order): Mk I -> Mk II (upgrade: a full Mk I + gold, as the current Mark 1 V2 upgrade) -> Mk III ->
Mk IV -> Mk V. The later suits keep working exactly as they are for now and are renamed to their future-era names:
`mark_2` -> "Mk VI Suitcase" (moves after `mark_v` in the order), `mark_6` -> "Mk VII Silver Centurion", `mark_vii` ->
"Mk VIII Space", `mark_8` -> "Hulkbuster I Bruiser". Era II+ suits keep the no-energy behaviour (the shim) for now.

**Arc Reactor Mk1**: every Era I suit needs one (the existing Arc Reactor item becomes "Arc Reactor Mk1": iron, redstone,
copper). Keep it simple: if the recipe already uses those, just rename.

## The Gantry rules (Era I only)

* Suit up and take off ONLY at a Stark Gantry (the existing 5 x 5 floor multiblock + its robotic arms + Suit Platforms
  within 20 blocks). Era I suits refuse every other way on / off (manual C build, calls, crafting-grid wear, dispensers,
  shift-click into the armour slots -- whatever paths exist), with a clear wrapped message ("Era I suits suit up in a
  Stark Gantry").
* Repairs ONLY at the Gantry: racked on a Suit Platform within range of a complete Gantry (or worn while standing on the
  Gantry floor). A Suit Platform with no Gantry nearby does not repair an Era I suit (its screen says why).
* Energy refills fast in the Gantry (+50/s racked or standing on the floor), slowly anywhere else (the Regen column).
* **Emergency eject**: hold Shift+C for 3 s (a bar fills, like the Miraculous take-off bar): the worn suit comes off into a
  **Suit Crate** item (carries the four pieces with their integrity / energy, heavy: Slowness I while it is in your
  inventory). Use the Crate while standing on a Stark Gantry floor -> the arms put the suit on you; or right-click a Suit
  Platform in a Gantry's range to rack it. The Crate can't be worn or opened anywhere else.

## Integrity + energy (Era I)

* Integrity: as now (`IronManDamage.absorb` / `IronManIntegrity`) with the absorb shares above; 0 = wrecked (no moves, no
  flight, heavy) until repaired at the Gantry.
* Energy is back for Era I: per-suit capacity / regen above; every move costs the energy in its row below (paid when it
  goes off; not enough = "LOW POWER", the move does not fire); flight / hover drain per second; at 0 the suit **browns
  out** (no moves, no flight / hover, armour + absorb still work) until it has 10% again. Stored on the pieces like the old
  energy (the stack component) so racking / the Crate keep it.
* HUD: two thin bars above the move boxes -- INTEGRITY (red-gold) and REACTOR (cyan), in the existing Iron Man HUD style.
  The spec sheet / Workbench / platform screens show Energy + Regen again for Era I suits.

## The kits (60 moves). Damage per hit; cd = cooldown s; en = energy cost

C is a kit key now for Era I (slot 6, `comic_c`); **Shift+C is always the emergency eject (hold 3 s)** -- it is not in the
tables. H stays the faceplate / gantry key.

### Mk I Cave-Forged (`mark_1`, ids `e1mk1_*`)
| Key | Tap | Shift |
|---|---|---|
| R | `flamethrower` hold: 6-block cone, 8/s + fire, 15 en/s | `fire_wall` 5-wide fire line ahead, 4 s, 3/s; cd 14, en 80 |
| G | `iron_fist` 18, big knockback; cd 1.5, en 20 | `haymaker` charge 1 s, 36 + stagger; cd 8, en 60 |
| X | `rocket_jump` ~8 blocks up (fall-safe); cd 3, en 40 | `rocket_leap` long forward arc ~14 blocks; cd 6, en 60 |
| Z | `smoke_screen` 6 s cloud: mobs inside lose you; cd 14, en 50 | `scrap_burst` close shotgun cone 4 x 7; cd 6, en 40 |
| V | `magnetic_repel` shove everything 7 blocks, 4; cd 8, en 40 | `brace` 4 s: -60% damage, no knockback, rooted; cd 16, en 50 |
| C | `reactor_overload` next hit / 3 s of flame x2; cd 20, en 120 | (eject) |

### Mk II Golden Avenger (`mark_1_v2`, ids `e1mk2_*`)
| Key | Tap | Shift |
|---|---|---|
| R | `repulsor` 12; cd 1, en 20 | `charged_repulsor` hold 1 s, 26; cd 6, en 70 |
| G | `gold_uppercut` 14 + launches the mob up; cd 5, en 40 | `ground_pound` from the air, 16 in 4 blocks (fall-safe); cd 8, en 60 |
| X | `boot_hover` 5 s hover (Space up / Shift down), 10 en/s; cd 2 | `jet_dash` 14 + dash; cd 7, en 50 |
| Z | `golden_glare` blinds mobs in a 12-block cone 4 s; cd 12, en 50 | `jet_skates` 8 s fast ground glide (the comic roller skates); cd 14, en 60 |
| V | `magnetic_pull` items + mobs in 14 blocks; cd 6, en 30 | `magnetic_shield` 5 s, deflects arrows / projectiles; cd 15, en 70 |
| C | `reactor_overload` as the Mk I; cd 20, en 120 | (eject) |

### Mk III Classic (`mark_iii`, ids `e1mk3_*`)
| Key | Tap | Shift |
|---|---|---|
| R | `repulsor` 14; cd 0.8, en 20 | `twin_volley` 2 x 12 both hands; cd 3, en 45 |
| G | `unibeam` hold to charge (max 1.5 s), up to 36 along 40 blocks; cd 10, en 150 | `unibeam_sweep` 24 sweeping a 90° arc; cd 14, en 160 |
| X | `flight_boost` 3 s burst x1.8 speed; cd 8, en 60 | `barrel_roll` sideways dodge + 0.6 s i-frames; cd 5, en 40 |
| Z | `mini_missiles` lock 3 targets, 18 each; cd 10, en 100 | `missile_barrage` 6 x 14 at the crosshair area; cd 18, en 160 |
| V | `tractor_beam` hold: pull a mob / item in 20 blocks; cd 6, en 15 en/s | `tractor_throw` fling what you hold (or nearest in front) 16; cd 8, en 60 |
| C | `targeting_hud` toggle: marks mobs within 32 (private glow), +20% crit; 4 en/s while on | (eject) |

### Mk IV Hydro (`mark_4`, ids `e1mk4_*`; x1.5 in water, central)
| Key | Tap | Shift |
|---|---|---|
| R | `torpedo` 30, straight (fast in water, sinks on land); cd 4, en 50 | `homing_torpedo` 26, homes the nearest target; cd 8, en 80 |
| G | `sonar_ping` mobs through walls in 48 for 10 s (private glow); cd 12, en 40 | `sonic_pulse` 10 + stun 2 s, 8 blocks (Guardians / water mobs 4 s); cd 14, en 70 |
| X | `hydro_surge` jet dash in the look direction (3x in water); cd 4, en 40 | `ballast_drop` sink fast / on land a stomp 20 in 4; cd 10, en 60 |
| Z | `depth_charge` thrown, sinks, 28 in 4 blocks; cd 10, en 80 | `mine_field` 3 proximity mines 22 each, last 60 s; cd 20, en 120 |
| V | `whirlpool` pulls mobs in 10 blocks for 4 s, 3/s; cd 16, en 100 | `bubble_shield` 8 s air bubble r4: you + allies breathe, mobs pushed out; cd 20, en 90 |
| C | `headlamps` toggle: strong light (light blocks follow you) | (eject) |

### Mk V Stealth (`mark_v`, ids `e1mk5_*`)
| Key | Tap | Shift |
|---|---|---|
| R | `silenced_dart` 6 + Poison II 4 s + Slowness II 4 s; cd 1.5, en 20 | `sleeper_dart` mob sleeps 8 s (no AI; never bosses / players, a hit wakes it); cd 12, en 60 |
| G | `silent_takedown` 15, x3 from behind / unaware; cd 4, en 40 | `shadow_strike` blink behind the target in 16 blocks + takedown hit; cd 10, en 80 |
| X | `light_bend_blink` 8-block invisible dash; cd 5, en 50 | `active_camo` (v0.16.7 (user), replaced `silent_glide` once the Mk V flew) toggle: you AND the suit invisible (armour / GeckoLib suit hidden, faint first-person arm), mobs lose track and cannot target you; any hit given or taken breaks it for 5 s; 3 en/s; not while browned out; cd 1 |
| Z | `emp` 10-block pulse: mobs stunned 3 s, drones / Ultron / sentries 6 s; cd 18, en 120 | `decoy` a hologram of you for 10 s that mobs target; cd 18, en 80 |
| V | `radar` marks mobs within 32 for 8 s (private glow); cd 10, en 30 | `thermal_vision` see living things through walls 10 s (private); cd 16, en 60 |
| C | `silent_running` toggle: mobs cannot pick you as a target until you attack (breaks for 5 s on attack), 5 en/s | (eject) |

Reuse the existing comic move code wherever a move is the same idea (flamethrower, smoke, repel, brace, repulsor,
charged repulsor, ground pound, dash, magnet pull, rocket skates, unibeam + sweep, missiles, tractor, torpedoes, depth
charge, whirlpool, sonar, EMP, decoy, blink, takedown, private marks, cloak / jammer logic...). Private highlights only
ever go to the wearer (`IronManComicMarkPayload`), never glow flags on entities.

## Stark Workbench (replaces the Stark Fabricator)

The `stark_fabricator` block id is kept (worlds convert automatically); display name "Stark Workbench". A TWO-BLOCK-wide
desk (a head + a side half, like a bed: placing it claims the block to the player's right; breaking either drops one;
the half blocks share one block entity on the main half). Dark metal top, cyan edge light, red/gold Stark stripe, a
keyboard strip, a small arc reactor on a stand.

* **In-world holograms** (block entity renderer, client-only, only within ~12 blocks): a cyan scan-lined hologram of
  the suit being worked on stands on a projector disc in the middle, slowly rotating -- the pieces already built drawn
  bright, the rest as a dim wireframe; a ring rotates round it at chest height; two floating screens beside it (stats
  bars on the left, recipe slots + a progress bar on the right). Idle (nothing selected): a slowly turning arc reactor.
* **Screen** (opened with right-click), styled like the concept mockup: era tabs at the top (Era I live; II-IV shown
  locked "coming soon"); suit list on the left (owned / buildable / locked -- unlocked by owning the previous suit's
  blueprint as now); a rotating hologram preview of the suit in the middle (drag to spin) with the four pieces as
  toggles (built / not built); stats bars on the right (Armour, Integrity, Reactor energy, Flight/Mobility), the selected
  piece's recipe slots (missing items red, have / need counts) and a Build button with a progress bar. It drives the
  existing Fabricator crafting logic (`FabricatorRecipes`, machine energy etc.) -- a new front end, same recipes.
* Everything wraps / fits (`TooltipWrap` rule).

## Ownership (parallel worktree agents)

| Agent | Owns | Lang anchor (add keys right after it) |
|---|---|---|
| CORE | `IronManEnergy`, `IronManIntegrity`, `IronManDamage` (absorb shares), `IronManSuits` (stats, names, order), `IronManItems` (order, Suit Crate, Arc Reactor Mk1), gantry / platform / suit-up / removal / call refusal paths, the eject + Suit Crate, the router energy spend + brownout + flight drain, `IronManHud` bars, spec sheet / platform screen energy rows, the cost test | `__anchor_ironman_era1_core` |
| MK12 | the Mk I + Mk II moves (`ability/era1/EraOneMk1Moves`, `EraOneMk2Moves`) + their poses / FX / tests / lang | `__anchor_ironman_era1_mk12` |
| MK34 | the Mk III + Mk IV moves (`EraOneMk3Moves`, `EraOneMk4Moves`) | `__anchor_ironman_era1_mk34` |
| MK5 | the Mk V moves (`EraOneMk5Moves`) | `__anchor_ironman_era1_mk5` |
| ART | the five suits' textures + glowmasks + THIN geo (from the concept skins), Hydro dome, item icons | `__anchor_ironman_era1_art` |
| BENCH | the Stark Workbench block (two halves), renderer holograms, screen, model, item icon | `__anchor_ironman_era1_bench` |

The kit DATA (`IronManComicKit`, all ids / cooldowns / damage / energy) and the move registry
(`ability/era1/IronManEraOne`) are written first by the lead and are shared read-only: a moves agent registers its
handlers with `IronManEraOne.register("e1mk1_iron_fist", handler)` from its own class and must not edit the table (tell
the lead if a number has to change). Each agent writes its own gametest class.
