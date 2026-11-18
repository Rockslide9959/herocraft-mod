# Power Armour (v0.17.5) — APPROVED 2026-11-16

A **Secondary power**: you only get its abilities while sealed inside a suit. Original design (no Fallout/Iron Man
copying): a slim, layered exo-suit built from 3D pixels.

## User's decisions
- **Tiers:** vanilla + mod alloys. Build a frame directly from any of them; stronger alloy = stronger suit.
- **Entering:** a standing frame you place. Right-click: it splits open, you step in backwards, it seals. Hold a key to
  open it and step out, leaving it standing.
- **Power cores:** swappable batteries that drain; better cores hold and output more; spent cores recharge.
- **Keys:** inside the suit the armour's kit takes R/G/Z/X/C/V; your Primary's passives still work, its keys return when
  you step out. H stays the power selector only.

## Tiers (frame material)
| Tier | Material | Armour feel | Upgrade slots |
|---|---|---|---|
| 1 | Iron | sturdy, slow | 3 |
| 1+ | Gold | weaker plating, but cores drain 25% slower | 3 |
| 2 | Hero Alloy | better plating | 4 |
| 3 | Diamond | strong | 5 |
| 4 | Netherite | very strong, fire/lava proof, no knockback | 6 |
| 5 | Vibranium | absorbs impacts (falls/explosions charge the core a little) | 7 |
| 6 | Celestial | best; faint glow, fastest servos | 8 |

Frames upgrade tier to tier at a smithing table (keeps upgrades, paint and core). The look changes per material
(colour, trims, plate shapes). The suit has its own integrity (repairs with its material).

## Power cores
Fusion Cell (basic) → Arc Core → Star Core (from Celestial Core). Capacity and max output rise per core; high-power
upgrades need a strong enough core. Recharge spent cores in a furnace-like **Core Charger** block, or slowly from a
Solar Plate upgrade. An empty core leaves the suit **unpowered**: very slow, no abilities, but you can still open it.

## Kit (inside the suit)
| Key | Base move | Changed by upgrades |
|---|---|---|
| R | **Servo Punch:** heavy hydraulic punch, big knockback; hold to charge and break weak blocks | Power Fist (more damage, shockwave) |
| Shift+R | **Arm weapon** (whatever's mounted on the arms) | Gatling, Flamer, Tesla Coil, Rail Cannon |
| G | **Ground Stomp:** hydraulic stomp shockwave around you | Seismic Plates (bigger, slows) |
| Shift+G | **Servo Grip:** grab a mob and throw it | Magnetic Clamp (pulls from range) |
| Z | **Shoulder Charge:** dash forward, ramming mobs | Breacher Plating (breaks through blocks) |
| Shift+Z | **Core Overload:** vent the core in a burst of energy (huge damage, drains a lot) | — |
| C | **Back module** (whatever's on the back) | Missile Pod, Shield Projector, Repair Drone, Sentry Turret |
| X | **Jump Jets:** boosted leap, soft landing | Thruster Pack (hover and short flight) |
| V | **Visor:** highlight mobs through walls | Ore Scanner, Target Lock (auto-aim the arm weapon) |
| Shift+V (hold) | **Open up:** the suit opens and you step out | — |

Passive: no fall damage (it drains the core instead), heavier steps, can't sprint-swim, armour from the tier.

## Upgrades (crafted modules, socketed into slots)
- **Helmet:** Night Visor, Ore Scanner, Target Lock, Rebreather (water/gas).
- **Chest:** Reactive Plating (thorns-like burst when hit), Capacitor Bank (+core capacity), Overclock (+output).
- **Arms (pick one weapon):** Gatling, Flamer, Tesla Coil, Rail Cannon, Power Fist.
- **Back (pick one module):** Missile Pod, Shield Projector, Repair Drone, Sentry Turret, Thruster Pack.
- **Legs:** Servo Boosters (speed), Seismic Plates, Stabilisers (no knockback below Netherite).
- Plus **paint** (dye the primary/secondary/glow colours).
Each module costs slots and core output; the edit screen shows what fits.

## Editing ("at any point")
Shift + right-click a parked frame opens the **Armour Editor**: a rotating 3D view of the suit, the slots, the core
bay, paint, and stats (armour, energy use, output budget). No station needed. Modules, cores and paint can be swapped
there anywhere. (Inside the suit you can't edit; step out first.)

## Getting it
Craft a frame from 24 of a material + a redstone circuit board (new crafted part) at a crafting table; place it;
put a core in; step in. The Secondary power is granted while you own/are inside a frame (follows the mod's
Primary/Secondary rules).

## Look & animation
- Built from 3D pixels (voxel geometry per texel) in **layers**: an under-suit frame, plates over it, trim and glow
  strips on top. Slim, close to the player's silhouette (a little taller and broader, not bulky).
- Opening: the chest plates hinge out, the back opens, the leg plates split, the helmet tilts up and back, with pistons
  and steam. Entering: the player turns, steps back in, then the panels close in sequence (legs, arms, chest,
  helmet), the visor lights up.
- Each tier's material re-skins the plates; modules are visible on the suit (gatling on the arm, missile pod on the
  back, thrusters on the calves).

## Round 2 (user, 2026-11-18)
- "Make the power armour model look better ... it should have the same scale as a regular player ... the proportions are
  basically just a scaled up player": the suit is a player scaled up 1.1x on every axis, its layers a thin shell inside that
  silhouette (no extra-wide shoulders or chunky limbs). The sealed wearer grows to the same size (hitbox, eye height).
- "Only make it so that the side you click on opens up and then you enter on that side": click from the front and the front
  opens (turn round, step back in); click from behind and the back opens (walk straight in). Stepping out opens the side you
  came in by. Details in `POWER_ARMOUR_REFERENCE.md`.
