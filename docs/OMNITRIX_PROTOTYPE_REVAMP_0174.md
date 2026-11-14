# Prototype aliens ability revamp (v0.17.4)

User (2026-11-14): "improve their abilities, take out any abilities that aren't good or don't make sense, add new ones,
and give all the abilities shifted variants and hold abilities if it makes sense."

Keys: R = SLOT_1, G = SLOT_2, X = SLOT_3 (movement), Z = SLOT_4, V = SLOT_5 (utility / mode), C = SLOT_6. H/N untouched.
Every key gets a tap and a Shift move; a hold only where holding means something (charge, channel, sustain).
Damage tiers (v0.17.2 scale): light 5-8, medium 12-17, heavy 17-20, ultimate 21-36. Cooldowns in seconds are targets;
tune in play. Passives and weaknesses stay unless listed.

## Snare-oh
Removed: **Detach** (V) -- a second "grab and drag back" next to Grapple Pull, and an attack on the utility key.
Fixed: **Bandage Swing** said "Hold X" but was registered as a tap; it is now a real hold.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| R | Bandage Whip: 7, 8 blocks, knockback (0.6 s) | Bandage Bind: 6, bound 3 s (boss 0.75 s) (8 s) | **Lash Flurry** (new): whips at the crosshair while held, 4 every 0.25 s for up to 2 s, you're slowed (6 s) |
| G | Grapple Pull: 8, yanked to you (boss pulls you) (5 s) | **Bandage Slam** (new): catch the creature you look at (12 blocks), lift it and slam it down: 15, and 6 to things within 2.5 blocks of the impact (10 s) | **Tether Throw** (new): catch a creature and whirl it round you while held (up to 2 s, hitting others for 4); let go to fling it where you look: 12 + 6 on impact (12 s) |
| X | Bandage Zip: reel to the block under the crosshair (40 blocks) (2 s) | **Recoil** (new): a bandage to a block behind you snaps you 10 blocks backwards, out of trouble (4 s) | Bandage Swing: swing while held, Jump reels in, Sneak pays out, let go to fling (0.5 s) |
| Z | Mummy Wrap: 16 within 7 blocks, all bound 2.5 s (25 s) | **Sand Coffin** (new): every bound creature within 12 blocks is crushed: 20, and the binding ends (18 s) | **Cocoon** (new): wrap yourself while held (up to 4 s): you can't move or be hurt, heal 1 heart a second; let go to burst out: 6 within 3 blocks (30 s) |
| V | Scatter: 1.5 s of loose bandages, untouchable, fast, mobs lose you (12 s) | **Bandage Net** (new): a 5-wide net of bandages strung at the crosshair (16 blocks) for 6 s: creatures that touch it are bound 1.5 s (16 s) | -- |
| C | Unravel Spin: three lashes of 5 round you (6 s) | **Sand Gust** (new): breathe a gust of tomb sand (8-block cone): 9, blinded 2 s (10 s) | **Whirlwind** (new): spin while held and keep walking (up to 3 s): 4 every 0.5 s round you (12 s) |

## Shock Rock
Removed: none -- the kit made sense; it just had no Shift/hold options.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| R | Energy Blast: 8 (0.6 s) | Rapid Bolts: five bolts of 5 (3.5 s) | **Charged Bolt** (new): charge up to 1.5 s, release a piercing bolt: 10 uncharged to 20 full (4 s) |
| G | Energy Blade: 10 s / 6 hits of 13-damage slashes (12 s) | **Blade Wave** (new): throw a crescent of energy (16 blocks, pierces): 14; with the blade up it spends 2 of its hits and hits for 18 (6 s) | **Energy Lash** (new): an energy tether on the creature you look at while held (up to 2 s): 3 every 0.25 s and it's dragged toward you (10 s) |
| X | Energy Dash: 6 to things passed, no fall damage (2.5 s) | **Updraft Jet** (new): blast yourself 10 blocks straight up, no fall damage (5 s) | **Power Glide** (new): hold to keep jetting forward low (up to 2 s); things you pass take 6 once (6 s) |
| Z | Rock Burst: 18 within 6 blocks, thrown back (18 s) | **Rock Barrage** (new): fling your armour plates in a cone (12 blocks): six shards of 4 (12 s) | **Core Overload Burst** (new): charge up to 2 s, then burst: 18 within 6 blocks uncharged to 28 within 9 full (25 s) |
| V | Overload Mode: +33% damage while Energy drains (toggle) | **Armour Reform** (new): rebuild your plates: 8 absorption health for 10 s (30 s) | **Energy Channel** (new): hold to draw energy in, +10 Energy a second, slowed (no cooldown) |
| C | Shield Construct: 5 s front shield (10 s) | **Energy Dome** (new): 4 s dome (4 blocks) that stops shots and blasts from outside, for you and allies inside (20 s) | **Reflect** (new): hold the shield up (up to 3 s): shots that hit it fly back at the shooter (12 s) |

## Frankenstrike
Removed: **Quick Recharge** (tap V, +8 Charge) -- pointless beside Hold V Recharge, which does the same job better.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| R | Shock Punch: 8 + a 6 jump to the next creature, +6 Charge (0.8 s) | Lightning Bolt: 15 for 15 Charge (weak 7 without) (1.5 s) | **Arc Stream** (new): a crackling arc at the crosshair while held (14 blocks, up to 3 s): 3 every 0.25 s, jumps to one more creature, 4 Charge a second (6 s) |
| G | Conductor Slam: 17 within 5.5 blocks for 20 Charge (7 s) | **Tesla Field** (new): plant two electrodes: a 4-block field for 5 s, 3 every 0.5 s to creatures inside, 20 Charge (16 s) | **Charged Slam** (new): wind up to 1.5 s: 17 within 5.5 blocks uncharged to 22 within 8 full, 25 Charge (9 s) |
| X | Electro Leap: huge leap, shock landing with 10+ Charge (5 s) | **Lightning Ride** (new): become a bolt and strike to a spot up to 12 blocks away along the crosshair: 8 to things on the line, 15 Charge (6 s) | -- |
| Z | Overcharge Storm: ultimate, 50 Charge (45 s) | **Defibrillate** (new): 30 Charge: heal 8 and shake off Slowness/Weakness/Poison; allies within 4 blocks heal 4 (25 s) | -- |
| V | **Lightning Rod** (moved from Shift V): call down a bolt: +50 Charge, +4 health, 8 within 3 blocks (45 s) | **Static Armour** (new): 6 s of crackling skin, 15 Charge: anything that hits you up close takes 4 and is knocked back (14 s) | Recharge: +6 Charge a second (x2 near power, x3 in storms), slowed (no cooldown) |
| C | Grab & Shock: lift, 4 every 0.5 s, throw (12 s) | **Monster Roar** (new): a thunderous roar (10-block cone): 6, Weakness and Slowness 3 s (14 s) | -- |

## Eye Guy
Removed: none.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| R | Eye Laser: 7, 32 blocks (0.5 s) | Laser Volley: six eyes, 6 each (4 s) | **Sweeping Laser** (new): a beam from your eyes that follows your aim while held (up to 3 s): 2 every 0.2 s to what it touches (8 s) |
| G | Freeze Beam: 12, Slowness IV 4 s (6 s) | Fire Beam: 12, burning 5 s (6 s) | **Thermal Shock** (new): both beams at once while held (up to 2.5 s): 4 every 0.25 s, slowed and burning; a creature caught the whole time shatters for 10 more (14 s) |
| X | Eye Dodge: hop, untouchable 0.4 s (1.5 s) | **Retreat Volley** (new): hop back 6 blocks and fire three lasers at the nearest creature: 5 each (6 s) | -- |
| Z | Chest Eye Beam: ultimate, 32 through a line (35 s) | **Optic Lockdown** (new): your eyes lock onto up to 6 creatures within 16 blocks for 3 s: 2 every 0.5 s, Slowness II (18 s) | -- |
| V | All-Seeing: 12 s of creatures through walls + night vision (25 s) | **Weak Spot** (new): mark the creature you look at for 8 s: it takes 30% more from you (12 s) | **Far Sight** (new): zoom your view in while held, like a spyglass (no cooldown) |
| C | Eye Flash: 5 within 9, blinded 3 s, mobs lose you (13 s) | **Hypno Gaze** (new): stare down the creature you look at: for 5 s it forgets you and attacks the nearest other mob (not bosses) (20 s) | -- |

## Benwolf (Blitzwolfer)
Removed: **Focused Howl** (Shift G) -- the third howl in one kit (G cone, Shift G beam, Z shockwave), all doing "sound damage + knockback".

| Key | Tap | Shift | Hold |
|---|---|---|---|
| R | Claw Swipe: 8, wide (0.6 s) | Pounce: leap on a target 16 blocks away: 14, pinned 1 s (6 s) | -- |
| G | Sonic Howl: 12 in a 14-block cone, thrown back (5 s) | **Disorienting Howl** (new): a low warbling howl (10 blocks round you): 4, Nausea, and mobs hit by it attack each other for 4 s (18 s) | -- |
| X | Wolf Leap: long bound, no fall damage (2 s) | **Wall Bound** (new): kick off the wall you're facing (or next to) for a high bound away from it, chainable 3 times before landing (1 s) | **Hunt Sprint** (new): drop to all fours while held: Speed III, step up blocks, can't attack (no cooldown) |
| Z | Howl Shockwave (hold to charge): 14 within 4 to 22 within 9 (18 s) | **Moon Howl** (new): 10 s of Strength II and Speed I for you, and Strength I for your tamed wolves within 16 blocks (40 s) | (Howl Shockwave is the hold) |
| V | Tracking Senses: 15 s of outlines within 32 + night vision (25 s) | **Scent Mark** (new): mark the creature you look at for 30 s: it stays outlined for you at any range and leaves a scent trail of particles (15 s) | -- |
| C | Frenzy: six slashes of 5 in 1.2 s (10 s) | **Maul** (new): seize the creature ahead and slam it down: 16, pinned 1.5 s (bosses: 16, no pin) (12 s) | -- |

## Arctiguana
Removed: none, but **Ice Breath moved to Hold R** and **Ice Shard Spit to tap R**: the basic attack was a 6 s-cooldown breath while the spammable spit sat on Shift.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| R | Ice Shard Spit: 7, frosts; freezes a frosted creature 2 s (0.75 s) | **Shard Fan** (new): five shards in a spread: 5 each, frosting (4 s) | Ice Breath (now a hold): breathe while held, up to 3 s, 7-block cone, 3 every 0.25 s, frosts then freezes (6 s after release) |
| G | Frost Wall: 5x3 ice wall, 8 s (12 s) | **Ice Pillar** (new): a pillar of ice bursts up under the creature you look at (16 blocks): 13, thrown up (8 s) | **Ice Path** (new): while held, a strip of ice forms under and ahead of you as you move, even over water and gaps (melts after 10 s) (no cooldown, up to 6 s) |
| X | Ice Slide: frictionless 0.8 s slide, 4 to things passed (3 s) | **Wall Cling** (new): grip the wall you're facing for up to 5 s and climb it (Jump to leap off) (4 s) | **Long Skate** (new): keep sliding while held (up to 3 s), frost trail, 4 to things passed (6 s) |
| Z | Deep Freeze: 17 within 6, frozen 3 s (20 s) | **Shatter** (new): every frozen creature within 12 blocks shatters: 14 each, the ice ends (12 s) | **Blizzard** (new): channel a snowstorm round you while held (8 blocks, up to 3 s): 2 every 0.5 s, slowed, frosted (24 s) |
| V | Frost Armour: 10 s, 40% less damage, attackers frosted (20 s) | **Ice Decoy** (new): leave an ice statue of yourself; mobs within 16 target it for 5 s, and it bursts into frost (6 within 3) when broken or expired (18 s) | -- |
| C | Tail Whip: 8 round you, knockback (1.5 s) | **Frozen Tail Spike** (new): an ice-coated tail thrust (4 blocks): 12, frosted (6 s) | -- |
