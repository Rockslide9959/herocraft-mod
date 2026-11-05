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

Round 3 user requests are quoted under "Round 3" below.

| Key | Move |
|---|---|
| R | **Vine Lash**: Ladybug's Yo-yo Strike with a vine. Whipped 12 blocks along the look and straight back (3 ticks out, 4 back): 12 to the first creature, knocked away, never pulled in. Her slight auto-aim: about 15 degrees off the crosshair, or within 1 block of the aim line (0.8 s) |
| Shift+R | **Vine Slash**: both arms sweep across and 28 tendrils tear along the ground, following the land, across a 20-block 70-degree cone. 18 to everything they reach, as they reach it (10 s) |
| G | **Seed Bomb**: plucks a pod off his back, throws it. It arcs and bursts where it lands: up to 18 within 3 blocks. Takes 1 pod (1 s) |
| Shift+G | **Seed Mines**: plants 3 pods from his back in front of him (fewer if he has fewer). Each arms after 1 s and bursts when an enemy steps within 1.6 blocks: 16 within 2.5. They last 20 s. Shift+G again blows them all up at once, armed or not, with no cooldown (15 s) |
| X (hold) | **Vine Swing**: Ladybug's Yo-yo Swing. A vine to the block under the crosshair up to 45 blocks away, for as long as X is held (Jump reels in, Sneak pays out). Let go to fling on. The line snaps 14 blocks past its length (0.3 s, from letting go) |
| Shift+X | **Root Burrow**: no time limit. He dives and moves as a low mound. Faster, untouchable (except by fire), heals 2 a second, enemies hunting him lose track. He slips through fences, bars, walls, doors and anything else not a full block. Shift held: he phases through earth and rock too. X or Shift+X bursts him up: 14 within 3 blocks, thrown up (15 s, from coming up) |
| Z | **Constrict**: coils round the creature he aims at (12 blocks, the same slight auto-aim). It is rooted 3 s and squeezed for 4 every 0.5 s (24 in all). A burning enemy burns free (10 s) |
| Shift+Z | **Overgrowth** (ultimate): he drives his arms into the ground and a thicket erupts. 22 to everything within 7 blocks, thrown up, rooted 3 s (40 s) |
| V | **Regrowth**: 10 health back at once, and slowness, poison, wither, weakness, frost and fire are shed (20 s) |
| Shift+V | **Thicket Camouflage**: he folds up, then is invisible for 6 s from the start of the move, and enemies hunting him lose track. Any move ends it (25 s) |
| C | **Vine Snare**: both palms slam down, vines whip out of the ground. 8 to everything within 5 blocks, rooted 2 s (13 s) |
| Shift+C | **Vine Slam**: a vine snatches the creature he aims at (16 blocks, the same slight auto-aim), hoists it overhead for 9 ticks and smashes it down 3 blocks in front of him in 4, the blow landing 2 ticks later: 20, and 8 to everything within 3 blocks of the impact. A boss is not lifted: the blow lands where it stands. No target, no cooldown (12 s) |

H is never used. N is the Omnitrix's.

Roots are the shared `AlienStatus.root` (a boss is held a quarter as long).

## Passives

- **Stretch Arms**: 2 blocks of extra reach, +5 punch damage.
- **Creeper Climb**: walking into a wall, he climbs it like a ladder.
- **Green Walk**: he walks straight through trees, leaves and big plants (block tag `projecthero:wildvine_plants`). A block whose top is at his feet is still his floor, so he stands on a canopy. Hold Jump inside one to climb it, Sneak to drop down through it. Cactus cannot hurt him and he cannot suffocate in a tree.
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
- **Swing on X (held).** This keeps the house rule (X = movement) and reuses the tested Spidermonkey / Ladybug rope. Shift+X is the
  burrow (also movement).
- **Damage** matches the current prototype scale: quick ~9-14, normal 14-22, big 22-28 (as in CannonboltKit and HumungousaurKit).
- **No mode switch.** Unlike Cannonbolt, Wildvine has one body, so V is utility (Regrowth / Camouflage).

## Round 3 (user's requests)

- "make vine lash work similar to ladybugs yoyo R ability please, dont make it pull enemies closer and make it autotrack slightly please"
- "Shift+R - vine slash, make it so that the player spawns a bunch of tendril in a 20 block cone in front of them and attacks enemies in that range to 18 damage"
- "reduce seed bomb cooldown to 1 second"
- "Make seed mines able to manually explode by reactivating the ability"
- "make it so that the player regenerates 1 seed bomb every 3 seconds please"
- "Make vine swing work similar to ladybugs yoyo swing ability please but with widlvines vfx and stuff"
- "remove the time limit on root burrow, make player pop up by pressing x again, make player able to phase through half blocks like fences and iron bars in this form please, if player shifts in his form they can phase through nature blocks please, releasing shift stops the phasing"
- "Make wildvine able to walk through nature blocks like wood, leaves and stuff please"
- "Make C the vine snare ability and shift Z the overgrowth ability also give shift+C an attack move please"
- "edit and update animations on all moves please"

Choices made in round 3 (not asked):

- **The stock is 6**, one pod for each on his back, so the pods he shows are exactly what he has. Bombs take 1, mines take 3 (the same stock). Full again on every transformation. The HUD shows **Seed Pods n/6**, because in first person he cannot see his back.
- **Vine Grab is gone** (Shift+R went to the Vine Slash, as asked), and the zip with it. Vine Slam is the new grab.
- **Vine Lash** keeps its 12 damage (Ladybug's is 10) and takes her range, cooldown and timing.
- **Nature blocks, on foot** (`wildvine_plants`): logs and wood of every kind, leaves, wart blocks, mushroom blocks, bamboo, cactus, melons, pumpkins, azalea, big dripleaf, mangrove roots, chorus, shroomlight. Not planks or anything built.
- **Nature blocks, Shift in the burrow** (`wildvine_earth`): dirt, grass, sand, gravel, clay, snow, stone and the rest of the natural rock, ores, terracotta, sandstone. Bricks and anything built stay solid. The ground under his feet stays his floor: he phases level, and Jump takes him up.
- **Letting go of Shift inside the ground** lifts him straight up onto the surface above (phasing stops, as asked, and this way he is never buried).
- **Half blocks** are anything not a full block and over half a block tall. Slabs and stairs stay solid so that the mound still walks up them, and low things such as carpet stay his floor.
- **Burrowed he is a low mound for real**: hitbox and camera drop to the ground. If there is no room to stand when he surfaces, he comes up on top of what is over him.
- **The burrow's cooldown starts when he comes back up**, so a long burrow does not hide it.
- **Moves wait for their pose.** The pod leaves his hand 3 ticks in, the mines land at 5, the slash starts at 3, the snare at 4, Overgrowth at 7 and the invisibility at 6.

## Build notes

- `WildvineKit` (main), `WildvineClient` + `wildvine/WildvineFx` + `wildvine/WildvineLineClient` (client).
- The vines are held `AlienProjectileEntity`s drawn by `WildvineFx`:
  - `wildvine_vine`: lit, ribbed green tubes with leaves from his right hand, shooting out over 3 ticks
  - `wildvine_lash`: the whip, a wave running down it, a thorn bud on the tip, 3 ticks out and 4 back
  - `wildvine_slash`: the fan of tendrils along the ground, heads reared and thrashing; `wildvine_stab`: three spears at each creature cut
  - `wildvine_wrap` / `wildvine_regrow`: a vine spiralling round a creature / up him
  - `wildvine_mine`: half-buried pulsing pods
  - `wildvine_thicket`: tendrils rearing out of the ground in rings from the middle out (Vine Snare, Overgrowth; colour = its life)
- Passing blocks: `AlienKit.passesBlock` + `mixin/OmnitrixAlienBlockShapeMixin` (the block has no collision shape for that player, on both
  sides). Rays he casts use an empty collision context, so a vine still hooks a tree. `AlienKit.clearInBlocks` +
  `OmnitrixAlienScreenEffectMixin` take the block texture off the screen.
- The thrown pod is `wildvine_seed`. Burrowed, the player model is replaced by an earth mound with shoots (`customBody`).
- Textures: `scratchpad/ben10_v0171/gen_wildvine.js` from `scratchpad/ben10_v0171/wildvine/wildvine_src.png`.
- The dial icon is a **placeholder** (grey disc, lime diamond, a black vine-armed silhouette with a lime eye), made by the same
  script and processed with `IDS=wildvine node scratchpad/gen_omnitrix_dial_icons.js scratchpad/ben10_v0171/wildvine/icon_src`.
  Swap in the user's art the same way when they send it.
- Tests: `OmnitrixWildvineGameTests`. The discovery tests (11th, then 22nd, no repeats; the even pick) are in
  `OmnitrixCannonboltGameTests`.

## Seed pods on his back

User: "can you add little seed bomb models to wildvines back please"

- The skin's flat dark pods on his back are painted out in `gen_wildvine.js`: base back face 33..34 / 37..38 rows 23..25,
  34 / 37 rows 27..29 and 31, and the jacket layer's copies.
- `WildvineFx.backPods` draws 3D pods there, on the body bone (`WildvineClient.renderExtras`):
  - three even pairs, all one size
  - each pod is a glossy black oval in the skin's own pod colour (0x07030F; user: "wildvines bombs are black ... make it match the colour of the bombs on the skin model"), a soft grey highlight on its shoulder, a darker cap and a short dark stem
  - each hangs from its cap and sways a little, more as he walks
- They are his seed bombs, and they show the stock (round 3): six pods, six bombs.
  - The top pair goes first (he plucks them over his shoulder), the bottom pair last.
  - One regrows every 3 s, swelling from nothing over those 3 s (`WildvineKit.pods`, `podRegrowth`, synced, so every viewer sees the same).
  - The thrown and planted pods share the same black look, and a pod shows in his hand on the way.
- Screenshots: `docs/screenshots/v0172/wildvine/wildvine_pods_*.png`.
