# Omnitrix damage pass -- Alien Force (Recalibrated Omnitrix), v0.17.2

User: "can you make the damage of all of ben 10s alien stronger, i want each alien to feel like its own entire ability and i want
them to be strong aswell please ... make each alien stronger accordingly please? Especially humungosaur and alien X".

Scale (Heatblast notes as the yardstick): quick/spammable 8-12, normal 20-28, big 28-35, damage-over-time 7-10 a pulse, ultimates
70-110 (old: 5-8 / 12-17 / 17-20 / 40-70, so roughly x1.6). DoTs that pulse 16 times (Big Chill's Frost Field, Goop's acid puddle)
stay a little under 7 a pulse. Unarmed melee ("bare fists") bonuses are the user's own numbers, set as each alien's `attack` stat.
Alien X, Heatblast, XLR8 and the prototype aliens are other passes.

## Unarmed melee bonus (user-set)

| Alien | old | new |
|---|---|---|
| Swampfire | +3 | +9 |
| Echo Echo | +1 | +4 |
| Humungousaur | +5 / +7 / +10 by size | +15 / +20 / +25 by size |
| Jetray | +3 | +5 |
| Big Chill | +3 | +6 |
| Chromastone | +4 | +7 |
| Brainstorm | +3 | +4 |
| Spidermonkey | +2 | +5 |
| Goop | +2 | +5 |

Conditional fist extras stay on top: Swampfire vs burning 2 -> 4, Chromastone charged 3 -> 5, Brainstorm pincers 2 -> 4,
Spidermonkey four fists 4 -> 6 / sticky 1 -> 2, Goop corrosive 2 -> 4, Echo Echo jab 1.5 -> 3 a clone (5 clones at most).

## Humungousaur (base 12 / 24 / 28.8 blocks tall)

Size bonus +10% -> **+25% a step** (x1.0 / x1.25 / x1.5). Every area is now the larger of its old radius and a share of his height,
centred on his feet (Spike Burst used to sit at his chest, 5 blocks up, and Stampede's hit box 4.8 blocks up -- both missed things
on the ground); Primal Roar comes from low on his chest and never aims up; Haymaker / Uppercut / Heavy Hurl reach is
2.5 + scale x 2.0 (was x 1.2) so a creature at his feet is in reach of his eyes.

| Move | old (base / mid / full) | new (base / mid / full) | area old -> new (base / mid / full) |
|---|---|---|---|
| Haymaker | 7 / 7.7 / 8.4 | 24 / 30 / 36 | reach 10.5 -> 15.8 / 29 / 34.5 |
| Uppercut | 14 / 15.4 / 16.8 | 36 / 45 / 54 | as Haymaker |
| Ground Pound | 15 / 16.5 / 18 | 40 / 50 / 60 | r 4.5/6/7.5 -> 6 / 12 / 14.4 |
| Tail Club (tail / club) | 13 / 14.3 / club 19.2 | 32 / 40 / club 63 | r 3.5/4.5/5.5 -> 5 / 10 / 12.1 |
| Stampede (hit / crash) | 12 / 6 | 32 / 14 (x size) | hit box at his feet, 1 + 0.8 x width |
| Dino Leap landing | 10 / 11 | 28 / 35 | r 4/5 -> 5.4 / 10.8 |
| Titanic Slam (full size) | 19.2 | 75 | r 7 -> 17.3 |
| Heavy Hurl (hit / splash) | 18 / 8 | 45 / 20 (x size: 68 / 30 at full) | splash r 2.5 -> 2.5 / 4.8 / 5.8 |
| Earthshaker stomps 1-2 | 10 / 11 / 12 | 24 / 30 / 36 | r 6/7/8 -> 6.6 / 13.2 / 15.8 |
| Earthshaker final stomp | 55 / 60.5 / 66 | **95 / 118.75 / 142.5** | r 8/9/10 -> 9 / 18 / 21.6 |
| Primal Roar | 12 / 13.2 / 14.4 | 28 / 35 / 42 | cone 10/12/14 -> 10 / 19.2 / 23 |
| Spike Burst (hit / thorns) | 14 / 3 | 34 / 6 (hit x size) | r 3/4/5 -> 4.8 / 9.6 / 11.5 |
| Heavy Fists | +5 / +7 / +10 | +15 / +20 / +25 | |

## Swampfire

| Move | old | new |
|---|---|---|
| Fire Blast | 7 | 11 |
| Methane Torch (a pulse, 4 pulses) | 3.5 | 9 |
| Seed Bomb (hit / burst) | 4 / 15 | 8 / 25 |
| Vine Snare | 6 | 10 |
| Methane Cloud (a second) | 2 | 7 |
| Ignite | 19 | 32 |
| Methane Inferno (centre / edge) | 60 / 40 | 100 / 70 |
| Root Eruption | 16 | 26 |
| Seed Scatter mine | 8 | 14 |
| Vine Lash | 6 | 10 |
| Burning Fist vs burning | +2 | +4 |

## Echo Echo (up to 10 clones)

New `CLONE_HIT_CAP = 5`: at most 5 clones add their hit to any one creature per move (scream, wall, ring, surround, cannon,
chorus rings, jab), so a full chorus of 10 can never one-shot a boss.

| Move | old | new |
|---|---|---|
| Sonic Scream (+ a clone) | 7 (+3) | 12 (+4, max +20) |
| Wall of Sound (a pulse, + a clone) | 3 (+1) | 7 (+2) |
| Echo Ring (+ a clone) | 12 (+4) | 22 (+6) |
| Surround Sound (a clone) | 5 | 7 (5 clones max: 35) |
| Sonic Leap | 4 | 10 |
| Sonic Cannon (+ a clone) | 16 (+3) | 26 (+4) |
| Ultimate Chorus (centre / edge, + a clone ring) | 55 / 40 (+8) | 85 / 60 (+6, max +30) |
| Shatter Note (x1.5 shells) | 15 | 24 |
| Chorus Jab (a clone) | 1.5 | 3 (max 5 clones) |

## Jetray

| Move | old | new |
|---|---|---|
| Neuroshock Eyes | 7 | 11 |
| Tail Blast | 14 | 24 |
| Dive Strike | 15 | 26 |
| Tail Grab | 4 | 9 |
| Supersonic Burst boom | 6 | 12 |
| Neuroshock Barrage (a beam) | 6 | 10 |
| Neuroshock Overload (ultimate) | 60 | 100 |
| Dive Bomb | 18 | 30 |
| Neuroshock Pulse | 13 | 22 |
| Wing Ram (a step / cap) | +1 / +5 | +1.6 / +8 |
| Wing Slash | 6 | 10 |

Also fixed: his flight sound "plays constantly even if he isnt flying" -- the whoosh / streamline / boom cues layered vanilla's
ELYTRA_FLYING, a ~10 s drone, so every whoosh (including the one on transforming) droned on long after; it is gone (short wind
sounds instead), the transform whoosh only plays if he transforms in mid-air, and the in-flight whoosh needs him airborne.

## Big Chill

| Move | old | new |
|---|---|---|
| Ice Shard | 7 | 11 |
| Freezing Breath (a pulse) | 3 | 8 |
| Frost Grip | 13 | 22 |
| Shatter (burst / flying ice) | 16 / 5 | 28 / 10 |
| Phantom Chill | 8 | 13 |
| Ice Dive | 17 | 28 |
| Blizzard (a pulse, 8 pulses) | 2 | 7 |
| Frost Field (forms / a pulse, 16 pulses) | 6 / 1 | 10 / 4 |
| Absolute Zero (centre / edge) | 55 / 40 | 95 / 70 |
| Cloak Ambush | +6 | +10 |

## Chromastone

| Move | old | new |
|---|---|---|
| Prism Bolt | 6 | 10 |
| Prism Beam | 15 | 26 |
| Redirect (1.5x the hit, clamped) | 8-17 | 12-28 |
| Refraction (each) | 12 | 20 |
| Light Dash | 6 | 10 |
| Radiant Burst (plain / charged) | 13 / 17 | 22 / 30 |
| Full Spectrum | 40 + 0.6 a charge over 50 (70) | 70 + 0.8 a charge over 50 (110) |
| Charge Release | 8 + 0.1 a charge (18) | 14 + 0.15 a charge (29) |
| UV Flare (undead) | 6 | 10 |
| Charged Fists | +3 | +5 |
| Overload burst (round him) | 10 | 16 |

## Brainstorm

| Move | old | new |
|---|---|---|
| Lightning Bolt | 7 | 11 |
| Chain Lightning (first / each jump) | 14 / 12 | 24 / 20 |
| Arc Pincer | 13 | 22 |
| Ball Lightning (burst / arc) | 16 / 4 | 26 / 7 |
| Bolt Dash | 6 | 10 |
| Discharge | 17 | 28 |
| Mega Bolt | 19 | 32 |
| Electric Storm (a strike x12 / finale) | 4 / 22 (70) | 7 / 26 (110) |
| Static Cage | 12 | 20 |
| Static Overload | +6 | +10 |
| Shocking Pincers | +2 | +4 |

## Spidermonkey

| Move | old | new |
|---|---|---|
| Web Shot | 6 | 10 |
| Web Net | 12 | 20 |
| Quad Flurry (a fist x4) | 3.5 | 6 |
| Web Yank / Swing Kick | 12 / 15 | 20 / 26 |
| Tail Whip | 13 | 22 |
| Web Trap | 5 | 9 |
| Cocoon Crush (a squeeze x10 / slam) | 5 / 14 (64) | 8 / 25 (105) |
| Web Barrage (each) | 5 | 8 |
| Four Fists / Sticky | +4 / +1 | +6 / +2 |

## Goop

| Move | old | new |
|---|---|---|
| Acid Spit | 7 | 11 |
| Acid Glob (burst / puddle pulse) | 12 / 2 | 20 / 5 |
| Slime Whip | 13 | 22 |
| Engulf (a pulse / spit out) | 4 / 6 | 8 / 10 |
| Slime Slam | 17 | 28 |
| Acid Spray (a pulse) | 3.5 | 8 |
| Acid Tide (wave / lake pulse) | 45 / 5 | 85 / 8 |
| Goo Cannon | 18 | 30 |
| Corrosive Touch / Acid Skin | +2 / 2 | +4 / 4 |
