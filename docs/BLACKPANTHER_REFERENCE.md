# Black Panther reference

Hero-Tier Primary power, **Street tier** (melee 7-10, abilities 8-10, ultimate 15-20, `MAX_HIT` 20). Package
`com.projecthero.mod.blackpanther`, client `client/blackpanther`. Numbers live in `BlackPantherConfig`; state in the
`projecthero:black_panther_state` attachment (`BlackPantherState`, synced, copy-on-death). Tests: `BlackPantherGameTests`,
`BlackPantherV0177GameTests`.

## Getting it

Heart-Shaped Herbs grow in jungles. 3 herbs + a bowl = Heart-Shaped Herb Soup. Drinking it runs the 10 s Ancestral Plane, then
grants the power and the silver necklace (cosmetic, always drawn). It replaces the Primary power.

## The suit (v0.17.7 rework)

The user: "make the suit craftable instead please like the flash suit and you need to equip it, when the suit is worn you get the
new moveset". Four armour pieces (`BlackPantherItems.SUIT_HELMET/CHESTPLATE/LEGGINGS/BOOTS`, `PantherSuitItem`, material
`projecthero:black_panther_suit`): 2/5/3/2 armour, 1 toughness each (12 / 4 over the set), no durability, repair item Vibranium
Ingot. Anyone can wear them; only the Black Panther wearing all four gets the suited kit.

| Piece | Recipe (shaped) |
|---|---|
| Helmet | `BVB` / `A A` |
| Chestplate | `B B` / `VHV` / `BAB` |
| Leggings | `BVB` / `A A` / `B B` |
| Boots | `B B` / `V V` |

B = Black Wool, V = Vibranium Ingot, A = Amethyst Shard, H = Heart-Shaped Herb. 6 Vibranium Ingots for the set.

- `BlackPanther.syncSuit` runs every tick (and on join, respawn, grant): the full set on runs `suitUp` (the texel-by-texel build
  from the necklace, the head last, `SUIT_UP_TICKS`), any piece off runs `suitDown`. Suited moves wait for the build (`changing`).
- Look: the armour layer texture (`textures/models/armor/black_panther_suit_layer_1/2.png`) is the top half of the suit skin, so
  a piece worn alone looks like the suit; once suited, `BlackPantherLayer` draws the skin and `HumanoidArmorLayerMixin` hides the
  armour (`BlackPantherRender.hidesSkinOverlay`). Asset script: `scratchpad/bp_v0177_suit_textures.js` (also the item icons).
- Removed: H tap suit-up, hold-H necklace off, the Panther Habit recipes, the N claw toggle and the claw model.
- **Migration** (`BlackPanther.migrate`, flag `v177`): a Black Panther who had the suit before (habit bound in 0.17.4-0.17.6, or
  any 0.17.3 power) gets the four pieces once: worn if he was suited and the slot is free, otherwise into the inventory (dropped if
  full). Old `habit`, `necklaceOff`, `clawsOut` flags are cleared. A leftover Panther Habit item unfolds into the four pieces on use.

## Passives

| Passive | Unsuited | Suited |
|---|---|---|
| Melee (bare fist) | 5 | 10 (claws always out) |
| Regeneration | I | II |
| Herb | Speed I, Jump Boost I | same |
| Falls | half (Cat's Landing) | none |
| Night Eyes | in the dark | always |
| Hunter's Instinct | creatures hunting him within 20 outlined | same |
| Kinetic Absorption | -- | 40% of a hit soaked, 1 damage = 4 Kinetic |
| Vibranium Weave | -- | the pieces' 12 armour / 4 toughness; shots under 15 bounce off |
| Silent Paws | -- | no vibrations |

The melee is one attribute modifier (`black_panther_herb`): `UNSUITED_MELEE - 1` or `SUITED_MELEE - 1`. Regeneration is an
infinite `PowerToggles.effect` refreshed in `reconcile`.

## Unsuited kit (T'Challa)

| Key | Move | Damage / effect | Cooldown |
|---|---|---|---|
| R | Warrior's Combo | 5 / 7 / 10 (jab on the press, kick shoves) | 1.5 s |
| Shift+R | Leg Sweep | 7 within 2.6, trip 1.5 s | 6 s |
| G | Takedown | grab within 3 blocks, slam 10, daze 2 s | 8 s |
| Shift+G | Ground Pound (new) | hop, dive, slam: 9 within 3.5, knock-up, Slowness II 1.5 s | 8 s |
| X | Vault | front flip, no fall damage | 2.5 s |
| Shift+X | Flying Kick (new) | dash ~8 blocks, 10 to the first creature reached, rebound | 6 s |
| Z | Throwing Knife | 8, returns | 4 s |
| Shift+Z | Kimoyo Scan | mark 10 s, +25% from him | 15 s |
| V | Rising Uppercut (new) | 9 within 3, launched + Slowness III 1.5 s | 6 s |
| Shift+V | Hunter's Tracking | toggle, outlines + footprints, 24 | -- |
| C (hold) | Block (new) | every hit from the front -60%, up to 5 s | 1 s |
| Shift+C | Ancestral Resolve | Regeneration II 4 s, Strength I 6 s | 30 s |

Bosses only take the damage of the control moves. The new moves were designed for the user's requests: Ground Pound replaces
Disarm, Flying Kick replaces Backflip, Block replaces Parry (no perfect parry / riposte), Rising Uppercut replaces Shadow Step.

## Suited kit

| Key | Move | Damage / effect | Cooldown |
|---|---|---|---|
| R | Warrior's Combo | 8 / 9 / 10 | 1.5 s |
| Shift+R | Kinetic Pulse | 10 within 5, 30 Kinetic | 2.5 s |
| Shift+R, full bar | Kinetic Overload | 20 -> 15 at the edge, 10 blocks, whole bar | 5 s |
| G | Claw Combo | 7 / 7 / 9, bleed 4 s | 2 s |
| Shift+G | Pounce | 8 landing, pin 2 s, 10 strike | 12 s |
| X | Panther Leap | ~12 blocks up, ledge catch | 3 s |
| Shift+X | Drop Strike | 10 within 4 | 8 s |
| Z | Vibranium Dagger | 8, 3 hops | 7 s |
| Shift+Z | Kimoyo Flash | blind + slow within 6 | 10 s |
| V | Stalk | lose mobs past 6 while sneaking; backstab x3 (x1.75 players), max 20 | 10 s |
| Shift+V | Panther Senses | toggle, 40 | -- |
| C (hold) | Absorb Stance | frontal hits into Kinetic, 40 a stance | 4 s |
| Shift+C | Kinetic Return | max(8, shot x1.25) + 0.25 per Kinetic spent (40 max), max 20 | 6 s |
| Shift+N | Mask | toggle | -- |

Kinetic Overload lost its own key when the user moved the Kinetic Pulse to Shift+R; it fires from Shift+R when the bar is full.

## HUD

Bottom-right `VanillaHud.Stack`: title always "Black Panther" (no T'Challa title, no passive status words, user v0.17.7), the
Kinetic bar in the **Meter** style (suited), the Absorb bar (Hairline) while absorbing, the six key boxes for the live kit. Left Alt
lists both moves per key, Shift+N Mask and "Shift+R (full)" Kinetic Overload in the suit.

## Animations (BlackPantherPose)

Anim ids keep their numbers: 13 = Ground Pound (was Disarm), 15 = Flying Kick (was Backflip), 18 = Rising Uppercut (was the
riposte). Block uses the held stance `BLOCK` (forearms crossed in front of the face) and `F_BLOCK` in first person. The Ground
Pound's slam also sets the shockwave ring (`pulseStart/pulseRadius/pulsePos`, drawn by `BlackPantherEffectsRenderer`).
