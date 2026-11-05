# Omnitrix: Upchuck (v0.17.2)

User: "heres upchucks skin please build him: https://www.planetminecraft.com/skin/upchuck/ ... remove the pixel omnitrix from his
skin model and replace it with our omnitrix badge please. do research on his powers and look at what he does in the show ... Make
this another alien you unlock on the 11th mistransformation ... make it an even chance to unlock [any of the hidden aliens] if you
dont unlock any of them"

Upchuck belongs to the **prototype** Omnitrix (dial glyph **U+E018**; U+E019 is reserved for Buzzshock, built in parallel). He is
not on the Recalibrated Omnitrix.

## Found by accident

- He is a **hidden** alien (`Aliens.HIDDEN`), like Cannonbolt, Wildvine and Ditto. He is not on the dial until a mistransform finds
  him.
- Every **11th** mistransform (11th, 22nd, 33rd, 44th...) lands on one hidden alien, **picked at random with an even chance** from
  the ones the player has not found yet (`Aliens.pickHidden`). With all four unfound, each has a quarter. A found one never comes up
  again. Way Big (item-only, the To'kustar Shard) is never in the draw.
- Test tools: `/projecthero power tune omnitrix found on|off`, `mistransforms <count>`, `status`.

## In the show

Upchuck is a Gourmand from Peptos XI: a small, round, green alien with a huge mouth. Ben uses the Perk Upchuck form. In the original
series he wears a black open-front suit with white over-sleeves and white leg bands, and the Omnitrix symbol on his **left hand**.

What he does in the show:
- **Three long, sticky, prehensile tongues** (the brief said four; the show and the wiki say three). They stretch a long way, latch
  onto big things (an elephant, objects of 10 tons) and reel them in with ease.
- He eats almost anything: solid matter (metal, machines), liquids, energy (energy beams, even the Sub-Energy), and living
  creatures (tigers, a Crabdozer).
- What he eats becomes **explosive balls of green vomit** ("loogies"), spat with great accuracy. How big the blast is depends on
  what he ate and how much: a loogie hits like a hand grenade. He can curve them like a frisbee.
- He can **fly by propelling himself with his vomit**.
- He can **swallow something whole and spit it back out** unharmed (a container, a Galvan).
- Diamond-hard teeth, tough for his size (takes a car hit), a good jumper, quick reflexes. He spits slime that is slippery and melts
  ice.

His weaknesses in the show:
- There is a limit to how much he can fit in his mouth.
- Eating too much (or too strong an energy) inflates him and badly slows him down ("full cheeks").
- Living things squirm in his stomach and can make him cough them back up.
- His appetite: he is easily distracted by food.

Sources: the Ben 10 Wiki's Upchuck page (https://ben10.fandom.com/wiki/Upchuck), and the show itself (his debut, "The Visitor",
season 3 of the original series).

## Body

The user's planetminecraft skin (https://www.planetminecraft.com/skin/upchuck/), the classic look: green face and belly, the black
suit with white sleeves, on the player body at **0.6** size (Ditto is 0.55).

The skin painted its own Omnitrix as a 2x2 grey hourglass on the **outside of his left wrist**, on both layers (arm texels 41..42 x
60..61, sleeve 57..58 x 60..61). Those texels now take the wrist band's own colours (white above, the black cuff below) in
`scratchpad/ben10_v0171/gen_upchuck.js`. The mod's 3D badge sits there instead: left arm bone, facing out of its side,
`{3.3, 7.0, 0, 3.0, 1}`. That matches the show, where he wears the symbol on his left hand.

| | |
|---|---|
| Health | 24 |
| Armour | 8 + 1 toughness |
| Knockback resistance | 0.2 |
| Speed | x1.05 |
| Size | x0.6 |
| Bite | +4 |
| Falls | 5 blocks safe |

His belly swells as his Gut fills (from 30 up), drawn from the skin's own belly green, and it squirms while a creature is inside.

The dial icon is a generated **placeholder** (`scratchpad/ben10_v0171/upchuck_dl/dial_src/upchuck.png`, run through
`scratchpad/gen_omnitrix_dial_icons.js` with `IDS=upchuck`). The user's real icon never reached this machine as a file: when it
arrives, crop it to the disc (white out any neighbouring icon, as in `scratchpad/ditto_icon_src`) and run the same script on it.

## The Gut

His meter, 0 to 100, shown on the HUD ("Gut", lime turning gold the richer it is; "FULL" flashes over 75).

- **Filling it:** everything he eats.
  - Blocks (Devour): 2 for most, 3 for leaves, flowers, crops and wool, 10 for obsidian, 8 for ore and 15 for metal blocks (rich).
  - Dropped items (Gobble): food by its nutrition, anything else 1 an item (12 a stack at most).
  - Shots (Gobble): 5 for an arrow, 10 for a fireball or other energy (rich).
  - XP orbs (Gobble): 1 (rich), and the XP is still his.
  - Bites: 3 a bite, 6 for a Tongue Grab chomp, 4 a second while digesting a swallowed creature, and up to 20 if it doesn't
    survive.
  - A Feeding Frenzy doubles all of it.
- **Rich food** is ore, metal and energy (anything named iron, gold, copper, diamond, netherite, redstone, glowstone, TNT, blaze...).
  The rich share of his Gut makes his loogies up to **+20%** stronger.
- **Spending it:** loogies, the Vomit Rocket and Digest. The rich share is spent in proportion.
- It digests on its own: -1 every 5 s.
- Turning back empties it.

## Keys

| Key | Move | What it does | Cooldown |
|---|---|---|---|
| R | **Gut Bomb** | An explosive loogie (15 Gut): 9 to 22 by how full he is (x1.2 rich at most), a blast of 2 to 3.5 blocks at 70%. With under 15 Gut it is a free **Slime Spit**: 5, slowed. | 1 s |
| Shift+R | **Curveball** | A frisbee loogie (20 Gut): it leaves to one side and curves onto the creature he looks at (24). 12 to 20 by Gut, blast 1.8 at half. | 3 s |
| G | **Tongue Grab** | Three tongues latch onto the creature he looks at (14) and reel it in over 0.5 s for a chomp: 10, +6 Gut. A boss or anything over 100 health reels **him** in instead. | 4 s |
| Shift+G | **Swallow Whole** | Gulps a small creature (within 5, or the one on his tongues; 40 health at most, never a boss or a player). It is held inside him for 4 s (unseen, harmless, 2 a second, +4 Gut a second), then spat out where he looks: 8, thrown. If it doesn't survive, up to 20 Gut. | 15 s |
| X | **Vomit Rocket** | A blast of vomit launches him where he looks (8 Gut): 5 to everything just behind him. No fall damage for 2 s. With an empty Gut only a hop. | 2 s |
| Shift+X | **Tongue Zip** | The tongues stick to the block he looks at (24) and haul him to it (1.5 s at most). | 3 s |
| Z | **Devour** | The tongues snatch the block he looks at and the four round it on that face (6): eaten whole, nothing drops, Gut by what it was. | 0.5 s |
| Shift+Z | **Mega Loogie** (ultimate) | He swells up for 0.7 s, then everything in his Gut comes out at once (40 at least, spends it all): one huge loogie, 18 to 32 by Gut (x1.2 rich at most), a blast of 3 to 6 blocks at 80%. A little recoil. | 60 s |
| V | **Digest** | 20 Gut into 6 health; poison, hunger and nausea cleared. | 5 s |
| Shift+V | **Slime Slick** | A gob of slime (about 12 blocks): 4 where it lands, then a 2.5-block slick for 5 s. Everything in it is slowed (III) and slides about. It melts snow and ice (only where he could build). | 10 s |
| C (hold) | **Gobble** | Mouth wide for as long as C is held, no time limit: dropped items, XP and every shot but a trident within 5 blocks are sucked to his mouth and eaten. Shots can't hurt him meanwhile. | 6 s, from when C comes up |
| Shift+C | **Feeding Frenzy** | 10 s: double Gut from everything, and Devour and Tongue Grab need no wait (Grab 0.5 s). | 45 s |
| Attack | **Chomp** | His bite, +4. Every bite adds 3 Gut. | |

H is never used; N is the Omnitrix's (turn back).

Damage is on the prototype's scale (quick ~9-14, normal 14-22, big 22-28): a full-Gut Gut Bomb is a normal hit, a rich one at the
top of it; the Mega Loogie is the big one.

## Passives and weaknesses

- **Iron Stomach:** poison, hunger and nausea never stick.
- **Full Cheeks:** over 75 Gut, or with a creature inside him, he is slowed (Slowness I) and waddles.
- An empty Gut leaves him only a weak Slime Spit and his bite.
- Not on the dial until a mistransform finds him.

## What he may eat

Devour follows Upgrade's merge refusals (`UpgradeMachines.mayTake`):
- never where he couldn't break a block (claims and other mods' break checks, spawn protection, adventure mode);
- never anything unbreakable (bedrock, barriers...);
- never fluids;
- never a block with a block entity (chests, furnaces, shulker boxes...), so nobody's things are ever swallowed.

The Slime Slick's melting goes through the same check. Swallow Whole never takes a player or a boss, and whatever is inside when he
turns back comes back out alive.

## Build notes

- `UpchuckKit` (main, `omnitrix/alien/upchuck`) and `UpchuckClient` (client).
- His tongues are three held `AlienProjectileEntity`s (`upchuck_tongue`, `size()` = which of the three, `colour()` = the creature's
  id + 1, or 0 for a point). The client draws each as a flat pink tube from his mouth that lashes out over 3 ticks and pulls taut.
- The loogies are `upchuck_spit` / `upchuck_curve` / `upchuck_mega` / `upchuck_slime_spit` / `upchuck_slime` projectiles, drawn by
  `UpchuckFx` (blasts break no blocks).
  - Flight: a smooth teardrop mesh shaded by the view (a hot core through the spit's colour, lime or gold when rich, to a saturated edge), two soft
    halos, a tail, and a few drops strung behind. It's on the flat unlit render type, so the colour holds in daylight.
  - Bursts and splats are held projectiles the server spawns (`UpchuckKit.fx`: `upchuck_burst`, `upchuck_puddle`), so every player sees them. A
    burst is a flash, a swelling hollow shell and gobs on arcs; the puddle is a glossy, wavy-edged pool with a shine and bubbles. The Slime Slick is a
    puddle too.
  - Round the player (`UpchuckFx.world`): Gobble's suction streams, the Rocket's jet, the Mega Loogie gathering in his mouth, and the Frenzy's
    lashing tongues. All read synced flags.
- His mouth really opens on his face (`UpchuckClient.mouth`): lip, throat, top teeth and tongue, sized by the pose. The belly is a smooth half ellipsoid.
- Every move has its own pose, third and first person. Gobble's holds as long as the flag does; a move played over it hands back to the open stance.
- Swallowed creatures are kept exactly on his spot each tick (so neither pushes the other), invisible, weakened and slowed; the
  digest damage is his own `alien_strike` (the framework's `hurt` skips a creature standing inside him).
- Gut and rich are kit timers (`upchuck.gut`, `upchuck.rich`), so the client's HUD and belly read them from the synced state.
- Tests: `OmnitrixUpchuckGameTests`; the four-alien discovery tests are in `OmnitrixCannonboltGameTests`. Screenshots and the spit
  video: `docs/screenshots/v0172/upchuck/`. Harness: `scratchpad/UpchuckHarness.v0172.java.txt` (needs `run/upchuck.flag`).
- Round 3 screenshots: `docs/screenshots/v0172/round3_upchuck/`.
