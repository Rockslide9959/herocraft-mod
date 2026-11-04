# Omnitrix: Ditto (v0.17.2)

User: "also create a moveset for ditto please. Use this skin: https://www.planetminecraft.com/skin/ditto-ben-10-5303343/ remove the
pixel omnitrix from his skin model and replace it with our omnitrix badge please. do research on his powers and look at what he does
in the show ... Make this another alien you unlock on the 11th mistransformation. When it comes to unlocking new aliens make it an
even chance to unlock either cannonbolt or wildvine if you dont unlock any of them ... im gonna add more new aliens still"

Ditto belongs to the **prototype** Omnitrix, after Cannonbolt (dial glyph U+E017; U+E015 is Way Big's and U+E016 Wildvine's, both
built in parallel). He is not on the Recalibrated Omnitrix.

## Found by accident

- Like Cannonbolt he is a **hidden** alien (`Aliens.HIDDEN`): not on the dial until a mistransform finds him.
- Every mistransform is counted (persisted `omnitrix.mistransforms`). **Every 11th** (11th, 22nd, 33rd...) lands on one hidden
  alien, **picked at random with an even chance** from the hidden aliens the player has not found yet (`Aliens.pickHidden`). With
  Cannonbolt, Wildvine and Ditto all unfound, each is a third; once one is found the other two are a half each, and so on.
- Hidden aliens unlocked by an item (e.g. Way Big) are never in the pool (filtered in `Aliens.byMistransform`).
- To add another hidden alien later: put its id in `Aliens.HIDDEN`. Nothing else changes.
- Test tools: `/projecthero power tune omnitrix found on|off` puts every mistransform-found alien on the dial or hides them all
  again; `mistransforms <count>` sets the count; `status` lists the ones still to find and the ones found.

## In the show (Ben 10, original series)

Ditto is a Splixson: a small, big-headed, white-and-black alien with a fin on his head and the Omnitrix on his forehead.

What he does in the show:
- He splits into any number of identical copies of himself. Each copy thinks and acts on its own.
- The copies swarm an enemy, pile on top of it, gang up on it from every side, and carry or lift things together.
- He is quick and agile and a good climber.
- The copies merge back into one Ditto when he is done.

His weaknesses in the show:
- On his own he is weak: no strength or real power, just numbers.
- **Damage to one copy is felt by all of them.** Hurting one hurts Ditto.
- The copies squabble and get in each other's way.

Sources: the show itself (his debut, "Divided We Stand", season 2 of the original series), and TV Tropes' original-series alien
page (https://www.tvtropes.org/pmwiki/pmwiki.php/Characters/Ben10OmnitrixAliensTheOriginalSeries).

## Body

The user's planetminecraft skin (https://www.planetminecraft.com/skin/ditto-ben-10-5303343/) on the player body at **0.55** size
(Grey Matter is ~0.3, Echo Echo 0.6). The skin painted its own Omnitrix as a 2x2 white mark on the fin on his forehead (the hat
layer's front, texels 43..44 x 9..10). Those texels now take the fin's dark colour (`scratchpad/ben10_v0171/gen_ditto.js`), and the
mod's 3D badge sits there instead (head bone, `{0, -6, -4.56, 3}`), so it sits on his forehead as in the show. Every copy wears it too.

| | |
|---|---|
| Health | 18 |
| Armour | 6 |
| Speed | x1.3 |
| Size | x0.55 |
| Fists | +3 |
| Falls | 6 blocks safe |

The dial icon is a generated placeholder (`ditto_dl/dial_src/ditto.png` -> `gen_omnitrix_dial_icons.js` with `IDS=ditto`). If the
user sends real art, run the same script on it.

## The copies

The copies are real entities (`DittoCopyEntity`), not living mobs, like Echo Echo's clones. Each one:
- has 6 health, falls, steps up blocks, climbs anything it runs into, and keeps a loose pack round Ditto;
- **swarms**: it chases its target (the kit's order, else whatever Ditto just hit, whatever just hit him, or a monster hunting him)
  and punches it for 3 once a second. The pack's punches are staggered, and they reset the target's hurt-immunity, so they all land;
- hits back at whatever hurts it;
- is drawn in his skin at his size, with the badge, running, jabbing and copying his big poses (split, spin, army);
- is never saved and pops the moment he turns back.

**Felt by All:** half the damage a copy takes (never more than the health it had left) reaches Ditto. It is magic damage (armour
doesn't help), and it never gives him hurt-immunity, so a real hit right after still lands.

Mobs cannot target a non-living entity, so the copies are no decoy on their own. Shell Game (Shift+Z) is the decoy move.

## Keys

H is never used and N belongs to the Omnitrix. A move that needs a pack and finds none splits the copies it needs off for free
(Swarm Strike two, Dogpile one, Pile-Up two).

| Key | Move |
|---|---|
| R | **Swarm Strike**: every copy rushes the creature you look at (20 blocks) and fights it for 8 s (3 a punch, once a second each) (5 s) |
| Shift+R | **Dogpile**: every copy leaps onto the creature you look at (16): 4 each (6 at most), pinned (Slowness IV, Weakness) 2.5 s (10 s) |
| G | **Pinwheel Kick**: a spin kick, 10 within 2.5; every copy spins too, 4 within 2 of each (6 copies at most per creature) (1.5 s) |
| Shift+G | **Split Burst**: three copies burst out at once, 8 within 3 and thrown back (12 s) |
| X | **Splixson Leap**: a long agile leap; the copies bound after him; no fall damage 1.5 s (1 s) |
| Shift+X | **Copy Toss**: the nearest copy (6 blocks) hurls him where he looks; the landing hits 9 within 2.5; no fall damage (6 s) |
| Z | **Pile-Up**: the copies dive onto the spot you look at (16): 14 within 3, +3 per copy landed there (6 at most), slowed (8 s) |
| Shift+Z | **Shell Game**: swaps places with a random copy; every mob hunting him loses him; invisible 3 s (30 s) |
| V | **Split**: one copy splits off, 6 at most (0.5 s) |
| Shift+V | **Merge**: the copies run back into him, +2 health each (2 s) |
| C | **Ditto Army** (ultimate): 12 copies at once for 10 s, each going for its own creature within 12; when it ends he keeps six (60 s) |
| Shift+C | **Copy Carry**: two copies lift the creature you look at (12) over their heads for 1 s and hurl it where he looks: 16. Anything over 100 max health is too big and is slammed instead: 10 (12 s) |
| Attack | **Splixson Scrap**: fists +3, and every copy piles on whatever he hits |

Damage is on the prototype scale (Cannonbolt: quick 9-14, normal 14-22, big 22-28). Ditto's own hits are light: 9-16. The pack makes
up the rest: a full Dogpile is 24, a full Pile-Up 32, six copies on one target about 18 a second.

## Passives

- **Felt by All**: half of a copy's damage reaches him.
- **Wall Crawl**: press into a wall to climb it. His copies climb too.
- **Pack**: the copies follow him, climb, and fight whatever he hits or whatever hits him.
- **Merge heal**: +2 health for each copy merged back.

## Weaknesses

- Weak alone: 18 health and light fists (+3).
- Hurt one, hurt them all, and a copy breaks at 6 health.
- Not on the dial until a mistransform finds him.

## Choices made (no questions asked, per the user's "keep going")

- Six copies normally, twelve during the Ditto Army. Unlimited copies would make the swarm damage unbounded.
- Feedback is 50%, capped by the health the copy had left. At 100% every area attack would hit him seven times over.
- Copies are plain entities, not mobs, so they don't fight the AI system, despawn, drop loot or need pathfinding. They steer
  straight at their target and climb what they run into.
- The badge goes on his forehead, not his chest, because that is where the skin and the show have it.
- Placeholder dial icon until the user sends one.

## Code

- `omnitrix/alien/ditto/DittoKit.java`: the moves, the copy list, feedback and `foe`.
- `omnitrix/alien/ditto/DittoCopyEntity.java`: the copy.
- `client/omnitrix/alien/DittoClient.java`: skin, badge, poses.
- `client/omnitrix/alien/ditto/DittoCopyRenderer.java`: how a copy is drawn.
- `Aliens.HIDDEN` / `unfound` / `pickHidden` / `mistransformFinds` (shared with Wildvine's build): the unlock pick. The glitch itself is in
  `Omnitrix.tick`.
- Tests: `OmnitrixDittoGameTests`, which covers the dial, every key, the even pick, the 11th/22nd/33rd unlocks with no repeats,
  copies, feedback, Swarm, Kick, Dogpile, Carry, Leap, Toss and Army.
