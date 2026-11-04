# Omnitrix: Wildvine (v0.17.2)

User: "also create a moveset for wildvine please. Use this skin: https://www.planetminecraft.com/skin/wildvine-ben-10-4704618/
remove the pixel omnitrix from his skin model and replace it with our omnitrix badge please. do research on his powers and look at
what he does in the show ... Make this another alien you unlock on the 11th mistransformation. When it comes to unlocking new aliens
make it an even chance to unlock either cannonbolt or wildvine if you dont unlock any of them, do you understand? im gonna add more
new laiens still"

Wildvine belongs to the **prototype** Omnitrix, after Cannonbolt (dial glyph U+E016; U+E015 is kept for Way Big). He is not on the
Recalibrated Omnitrix. He is his own alien: Wildmutt is untouched.

## Found by accident (with Cannonbolt)

- Like Cannonbolt, he is **not on the dial** at first (`Aliens.HIDDEN`).
- Every **11th** mistransform (then the 22nd, 33rd...) finds a hidden alien. Which one is an **even random pick among every hidden
  alien the player has not found yet** (`Aliens.pickHidden`). Today that is Cannonbolt or Wildvine, 50/50. The 22nd then finds
  the other one, so there are never repeats. Once both are found, mistransforms just land on dial aliens.
- To add another hidden alien later, add its id to `Aliens.HIDDEN`; it joins the draw. An alien that must never come from a
  mistransform (Way Big: an item unlocks him) is filtered out in one place, `Aliens.byMistransform`.
- `/projecthero power tune omnitrix found on|off` puts every hidden mistransform alien on the dial, or hides them all again.
  `status` lists the ones still unfound and the next unlocking mistransform number.

## In the show

Wildvine is a Florauna: a tall, thin, green plant with a bulb for a head and one eye, and limbs like vines.

What he does in the show:
- His arms and legs **stretch** a long way like vines. He grabs things and people far away, and whips enemies.
- He **swings** on his vines and pulls himself up to ledges.
- He throws the **black seed pods** on his back. They are explosive.
- He can sink **into the ground** and come back up somewhere else, and he **regrows** lost parts.
- He **climbs** walls and **wraps** enemies up in his vines.

His weaknesses:
- **Fire**: he is a plant.
- **Cold**: frost stiffens him.
- He is light and thin, not a brawler.

Sources: the show itself, and TV Tropes' Omnitrix alien pages. The fan-wiki pages (Ben 10 Fan Fiction wiki, reboot wiki) were
fan fiction or other versions and disagree with each other, so they were not used for numbers. The weakness to electricity some list
was left out.

## Body

- The user's planetminecraft skin on the player body at **1.1 size**: a lanky plant with his one eye in the hat layer's bulb.
- The skin's own painted Omnitrix is painted out in `gen_wildvine.js`. It was a 2x2 grey block at the top-middle of his chest
  (base 23..24 x 21..22) under a grey/white diagonal on the jacket layer (23..24 x 37..38). Both now take the chest stripe's
  green, row by row. The mod's 3D badge sits there instead: `{0, 2.0, -2.3, 2.6}` (just in front of the jacket layer).
- Idle: his arms hang a little out from his body and sway like stems. Every move has a pose. Hanging on a vine, the right arm
  points straight up the line.

| | Wildvine |
|---|---|
| Health | 30 |
| Armour | 4 |
| Speed | 1.05 |
| Size | 1.1 |
| Reach | +2 blocks |
| Fists | +5 |

## Keys

| Key | Move |
|---|---|
| R | **Vine Lash**: an arm whips out as a vine. 12 to the first creature he looks at within 9 blocks, jerked toward him (1 s) |
| Shift+R | **Vine Grab**: the arm stretches up to 16 blocks. A creature takes 10 and is reeled in (a boss only takes the hit). Aimed at a block up to 24 blocks away instead, he pulls himself along the vine to it (a zip) (6 s) |
| G | **Seed Bomb**: throws a seed pod off his back. It arcs and bursts where it lands: up to 18 within 3 blocks (3 s) |
| Shift+G | **Seed Mines**: plants 3 pods in front of him. Each arms after 1 s and bursts when an enemy steps within 1.6 blocks: 16 within 2.5. They last 20 s (15 s) |
| X (hold) | **Vine Swing**: a vine to a block up to 30 blocks away, and he swings on it while X is held (Jump reels in, Sneak pays out). Let go to fling on. Same rope as Spidermonkey and Ladybug (1.5 s) |
| Shift+X | **Root Burrow**: sinks into the ground and moves as a mound for 3 s. He is faster, untouchable (except by fire), heals 2 a second, and enemies hunting him lose track (he is not invisible: the mound shows where he is). Then he bursts up: 14 within 3 blocks, thrown up. X again comes up early (15 s) |
| Z | **Constrict**: coils round the creature he looks at (12 blocks). It is rooted 3 s and squeezed for 4 every 0.5 s (24 in all). A burning enemy burns free (10 s) |
| Shift+Z | **Vine Snare**: vines whip out of the ground all round him. 8 to everything within 5 blocks, rooted 2 s (13 s) |
| V | **Regrowth**: 10 health back at once, and slowness, poison, wither, weakness, frost and fire are shed (20 s) |
| Shift+V | **Thicket Camouflage**: invisible for 6 s, and enemies hunting him lose track. Any move ends it (25 s) |
| C | **Overgrowth** (ultimate): a thicket of vines bursts up all round him. 22 to everything within 7 blocks, thrown up, rooted 3 s (40 s) |

H is never used. N is the Omnitrix's. Shift+C has no move: one ultimate is enough.

Roots are the shared `AlienStatus.root` (a boss is held a quarter as long).

## Passives

- **Stretch Arms**: 2 blocks of extra reach, +5 punch damage.
- **Creeper Climb**: walking into a wall, he climbs it like a ladder.
- **Photosynthesis**: in daylight under open sky, or standing on grass, moss or leaves, he heals 1 every 3 s.
- **Pliant body**: falls hurt half as much, and not at all off his own vines.

## Weaknesses

- **Fire**: +75% fire damage. Fire still reaches him underground, and a burning enemy burns out of his coils.
- **Frost**: +75% freezing damage, and frost slows him for 3 s.
- Light and thin: 30 health and little armour.
- Not on the dial until a mistransform finds him.

## Choices made (not asked)

- **Seed pods explode on impact.** The show's pods burst shortly after landing, but a fuse made them too easy to dodge and too
  slow to read.
- **Swing on X (held) and zip on Shift+R at a block.** This keeps the house rule (X = movement) and reuses the tested
  Spidermonkey / Ladybug rope, so all the vine moves feel the same. Shift+X is the burrow (also movement).
- **Damage** matches the current prototype scale: quick ~9-14, normal 14-22, big 22-28 (as in CannonboltKit and HumungousaurKit).
- **No mode switch.** Unlike Cannonbolt, Wildvine has one body, so V is utility (Regrowth / Camouflage).

## Build notes

- `WildvineKit` (main), `WildvineClient` + `wildvine/WildvineFx` + `wildvine/WildvineLineClient` (client).
- The vines are held `AlienProjectileEntity`s (`wildvine_vine`, `wildvine_wrap`, `wildvine_thicket`, `wildvine_mine`) drawn by
  `WildvineFx`:
  - lit, ribbed green tubes with leaves from his right hand, shooting out over 3 ticks
  - a vine spiralling round a wrapped creature
  - half-buried pulsing pods for mines
  - tendrils rearing out of the ground for Overgrowth
- The thrown pod is `wildvine_seed`. Burrowed, the player model is replaced by an earth mound with shoots (`customBody`).
- Textures: `scratchpad/ben10_v0171/gen_wildvine.js` from `scratchpad/ben10_v0171/wildvine/wildvine_src.png`.
- The dial icon is a **placeholder** (grey disc, lime diamond, a black vine-armed silhouette with a lime eye), made by the same
  script and processed with `IDS=wildvine node scratchpad/gen_omnitrix_dial_icons.js scratchpad/ben10_v0171/wildvine/icon_src`.
  Swap in the user's art the same way when they send it.
- Tests: `OmnitrixWildvineGameTests`. The discovery tests (11th, then 22nd, no repeats; the even pick) are in
  `OmnitrixCannonboltGameTests`.
