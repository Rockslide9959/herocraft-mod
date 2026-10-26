# Chlorokinesis revamp (v0.17.1)

Power 22, `power_22_plant_manipulation_chlorokinesis` (Plant Manipulation / Chlorokinesis). The user asked for a
revamp that is fun to play, has real survival use, its own VFX, and a Shift variant on every move.

## Shape of the kit

A green-thumb nature controller. It follows the newer mutation reworks (Elasticity, Wind, Super Speed):

- **Six keys, no H and no N.** H stays the power wheel. N is dropped: Spore Cloud moves to Shift+G, and the
  Thorn Sentry (the old H) becomes G's hold move. Because there is no `SLOT_7`, the HUD shows the six core boxes only.
- **All six slots are `HOLD`**, so the handlers own tap / Shift / release. The tap move uses the slot's normal
  cooldown, which shows in its HUD box. Each Shift move has its own ready-at resource (`<move>_ready`, a game time),
  which shows as a thin strip along the bottom of the box. This is the Super Speed strip, made general so other
  powers can register their own Shift strips (`AbilityHud.SHIFT_STRIPS`).
- One hold move, on G (hold 0.6 s): **Thorn Sentry**.
- **Chlorophyll** (0-100) is the resource. Every move needs its full cost. The meter is an always-on green Hairline,
  shown with the existing `MutationMeters` style.

## Chlorophyll

| Where you are | Refill per second |
|---|---|
| Open sky, daytime, clear weather | 8 |
| Open sky, daytime, rain | 5 |
| Open sky, night | 3 |
| Covered or underground, block light 12 or more (a "grow lamp") | 4 |
| Covered or underground, darker than that | 2 |

These multipliers stack on top:

- ×1.5 standing on living ground (grass, moss, leaves, farmland, podzol, mycelium, rooted dirt).
- ×1.25 in a lush biome (any forest, jungle, taiga, swamp, mangrove swamp, cherry grove or flower forest).

Eating fruit (sweet berries, glow berries, apples, melon slices) gives +12 Chlorophyll, +2 hunger and +3 saturation.
Fruit is a chlorokinetic's battery.

## Moveset

Damage gets +20% on living ground (the old Nature's Blessing bonus, now part of the kit). Cooldowns go through the
global `cooldownMultiplier`.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| **R** | **Thorn Volley**: three thorns in a narrow fan, 5 dmg each + Poison I 2.5 s. Cost 5, cd 0.5 s. | **Vine Lash**: a thorned vine whips out up to 14 blocks. 12 dmg, and it yanks the creature to 2 blocks in front of you. A boss isn't moved but takes +4. Cost 12, cd 3 s. | — |
| **G** | **Root Snare**: roots erupt under the target (up to 20 blocks; aimed at the ground, every enemy within 2.5 of the spot). 6 dmg on eruption, then held in place 4 s (no walking, no jumping) at 2 dmg/s. A boss only gets Slowness III. Cost 18, cd 6 s. | **Spore Cloud**: a 4.5-block cloud 3 blocks ahead, 8 s. Every 0.5 s, enemies inside get Poison I, Blindness and Slowness II, and non-boss mobs lose their target ("lulled"). You and your squad heal 1.5 and get Regeneration I. Cost 22, cd 14 s. | **Thorn Sentry**: plant a living turret for 15 s that fires thorns twice a second at hostiles (5 dmg + short Poison). One at a time. Cost 30, cd 20 s. |
| **X** | **Vine Grapple**: a vine shoots up to 48 blocks to a block and reels you in (no fall damage for 10 s). Aimed at a creature within 30, it reels the creature to you instead. Cost 6, cd 0.75 s. | **Leaf Glide** (toggle): a canopy of leaves opens over you for elytra-style gliding (steer with your look). It gives a small hop if you start on the ground. Drains 2/s. Closes on landing, in water, at 0 Chlorophyll, or on Shift+X. Cost 5 to open. | — |
| **Z** | **Overgrowth**: 1.5 s gather (you're rooted while roots coil up your body), then a 12-block eruption. 30 dmg, rooted 4 s (a boss gets Slowness IV), Poison II 6 s. **Survival:** every crop and sapling in the radius matures, bare dirt turns to grass, and flowers sprout. Cost 60, cd 40 s. | **Treant**: a walking tree ally fights for you for 45 s. 80 HP, a 10-dmg slam with knockback. It taunts hostiles within 8 so they turn on it, and heals 1 HP/s on living ground. One at a time. Cost 50, cd 60 s. | — |
| **V** | **Bloom Burst**: a ring of glowing flowers blooms around you for 6 s (radius 6). You and your squad get +2 HP/s, and Poison, Wither and Hunger are cleansed at once. Crops in the ring get two growth steps. **Survival:** as the flowers wilt they drop 2 sweet berries, plus an apple 35% of the time. Cost 25, cd 14 s. | **Living Architecture**, picked by where you look: **at a wall** = Vine Ladder (real vines up the wall face, up to 16 high, cost 8); **down** = Root Bridge (10 blocks of mangrove roots ahead, permanent, cost 12); **up** = Grow Shelter (a permanent 5×5 leaf hut with log posts, a moss floor, a doorway and a shroomlight in the roof, cost 40, cd 30 s); **level at nothing** = Bramble Wall (a temporary 7×3 thorn wall for 12 s; mobs touching it take 3/s and are slowed, cost 15). cd 1.5 s. | — |
| **C** | **Verdant Form** (toggle): your skin turns to bark and leaves. +6 armour, +2 toughness, +25% knockback resist; melee attackers take 3 thorns damage. **Photosynthesis:** in sunlight you heal 1 HP every 2 s and get 1 hunger back every 8 s. Out of the sun it costs 1 Chlorophyll/s (and ends at 0). | **Root Sense**: roots spread 24 blocks through the ground. The action bar reports ripe and unripe crops, saplings, and ores by kind, plus the nearest valuable ore's direction and distance. Glowing sap trails, which only you see, run to the 8 nearest ores and ripe crops for 6 s. Cost 15, cd 8 s. | — |

Cost and damage numbers are close to the other reworked mutations. Wind Blade is 12 for 8 Gust, the Hurricane is
30, Elasticity's Hammer Fist is 20-34, and Downburst is 18. Chlorokinesis does a little less burst damage and makes
up for it with roots, healing and a summon.

## Survival features

Passives, always on while you own the power:

- **Green Thumb** (empty-hand right-click):
  - ripe crop: harvested and auto-replanted;
  - any other growable plant: bone-mealed twice (1 Chlorophyll);
  - a flower: a copy of it drops, which is infinite dye (2 Chlorophyll);
  - an animal: fed into love mode, or a baby grows up a step (3 Chlorophyll);
  - Sneak on dirt, coarse dirt or rooted dirt: a 3×3 patch turns to grass. Sneak on stone, cobblestone or deepslate:
    the 3×3 turns to moss (4 Chlorophyll).
- **Tree Feller:** Sneak while breaking a log and the whole tree comes down (up to 96 connected logs, items dropped
  at the stump). A matching sapling is replanted. Costs 8.
- **Growth Aura:** crops and saplings within 24 blocks get random extra growth ticks.
- **Fruit Battery:** see Chlorophyll.
- **Photosynthesis** (Verdant Form), **Overgrowth** harvest growth, **Bloom Burst** fruit, **Vine Ladder**, **Root
  Bridge** and **Grow Shelter** are all covered in the moveset table.
- Kept from before: no fall damage on living ground, attacks pass through grass and ferns, and lush ground gives a
  little Regeneration.

## VFX

- `ChloroFxEntity`: short-lived, unsaved effect entities drawn by `ChloroFxRenderer` from code geometry
  (`ChloroMesh`):
  - **VINE** (grapple and lash): a segmented, tapering vine tube from the hand to the target. It grows out, stays
    attached, then snaps back. Leaves along its length, thorns on the lash.
  - **ROOTS** (snare): six twisting root tubes erupt out of the ground and coil up the victim. They follow it and
    sink back when it's released.
  - **SPORES** (cloud): soft translucent puff spheres drifting and turning, with custom spore particles.
  - **BLOOM** (bloom burst): a ring of flowers on stems. The petals open, glow faintly, then wilt and fold.
  - **OVERGROWTH**: roots coil up the caster during the gather. On release a ring of root spikes erupts outward in a
    wave across the radius.
  - **SENSE** (root sense): a green ring runs out along the ground.
- **Custom particles** (textures generated by `scratchpad/chlorokinesis_v0171/gen_chloro_v0171.js`):
  - `chloro_leaf`: falling, swaying, spinning leaf;
  - `chloro_petal`: pink and white petal;
  - `chloro_spore`: glowing yellow-green mote;
  - `chloro_sap`: glowing green drop, used for the Root Sense trails, full-bright.
- **Leaf Glide canopy:** a dome of big leaf quads over the player, drawn as an overlay on the `p22.glide` flag.
- **Verdant Form:** the bark-and-leaf shell (existing `p22_bark` texture), a leafy crown, green eyes in the dark, and
  leaves falling off you.
- **Treant:** the player model in a bark skin (generated), scaled 1.35×, with a leafy crown of leaf cubes on the head
  and glowing sap eyes. A regular summon, so the player-model rule applies.
- **Poses:** `p22.volley`, `p22.lash`, `p22.snare`, `p22.spore_blow` (kept), `p22.grapple`, `p22.glide` (loop),
  `p22.gather` (kept, loop), `p22.bloom`, `p22.shape`, `p22.sense`, `p22.summon`.

## Not changed

- `PlantManipulationBoss` keeps its own kit. It's independent of the player handlers and already uses the shared
  threat table through `BossPowerController`.
- `ClipContextMixin`'s swing-through-grass and `HeroDamageRules`' soft landing still call
  `PlantManipulationHandlers.isActiveFor` / `onNatureGround`.
- Thorn Sentry and its thorn entity are reused as is.

## Tests

`ChlorokinesisGameTests`:

- the volley's thorns hit and poison;
- Vine Lash damages and pulls;
- Root Snare holds a mob in place and damages it;
- Spore Cloud poisons enemies and heals the owner;
- Thorn Sentry is planted by holding G;
- Vine Grapple launches you;
- Leaf Glide toggles;
- Overgrowth matures a crop and damages;
- Treant spawns and is one at a time;
- Bloom Burst heals;
- Vine Ladder places climbable vines;
- Root Bridge is walkable;
- Grow Shelter builds;
- Verdant Form adds armour;
- Root Sense finds ore;
- Green Thumb harvests and replants;
- Tree Feller fells and replants;
- move costs come off the meter, the meter refills, and a move is refused when the meter is short.

The old power-22 tests in `RevampBatchEGameTests` and `HeroPackGameTests` are moved onto the new keys.
