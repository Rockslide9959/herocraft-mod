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
- has 6 health, falls, steps up blocks, climbs anything it runs into, and has **a spot of its own**: ten places in a two-row fan
  behind Ditto (`CloneSpacing`, shared with Echo Echo), rings round whatever it fights, and a push that keeps any two apart on the
  way (user: "give them their own position instead of just standing inside each other");
- **swarms**: it chases its target (the kit's order, else whatever Ditto just hit, whatever just hit him, or a monster hunting him)
  and punches it for 3 once a second. The pack's punches are staggered, and they reset the target's hurt-immunity, so they all land;
- hits back at whatever hurts it;
- is drawn in his skin at his size, with the badge, running, jabbing with either fist and spinning with his Pinwheel Kick;
- is never saved and pops the moment he turns back.

### Splitting and merging

**Coming out** (`EMERGING`, 0.9 s; 0.55 s for Split Burst, the Ditto Army and a move that brings its own copy): the copy starts small
inside him and pulls away toward a spot in front of him, off to one side, so he sees it from first person too. It strains forward,
stretched long, and a tube of his own skin (`DittoStrand`) runs from his middle to the copy's, thick at both ends and necking in the
middle like a cell dividing. The neck pinches through, the two halves snap back into the bodies, and the copy stumbles clear and
bounces back into shape. Then it walks to its own spot in the fan. He braces, strains and jolts upright as it parts. It cannot be hit
while it comes out.

**Going back** (Shift+V): one copy at a time, the nearest. He beckons, it **walks** back (it runs only if it is over 8 blocks out),
and from about a block away it reaches for him and sinks in along the same strand while he hugs himself. The +2 health arrives then, and
the **0.5 s cooldown runs from that moment**. A second press while one is walking does nothing. Each press calls one copy; holding
Shift+V calls them all in turn. A copy can still be hit while it walks.

**Felt by All:** half the damage a copy takes (never more than the health it had left) reaches Ditto. It is magic damage (armour
doesn't help), and it never gives him hurt-immunity, so a real hit right after still lands.

Mobs cannot target a non-living entity, so the copies are no decoy on their own. Shell Game (Shift+Z) is the decoy move.

## Keys

H is never used and N belongs to the Omnitrix. A move that needs a pack and finds none splits the copies it needs off for free
(Swarm Strike two, Dogpile one, Pile-Up two, Switcheroo one).

| Key | Move |
|---|---|
| R | **Swarm Strike**: every copy rushes the creature you look at (20 blocks) and fights it for 8 s (3 a punch, once a second each) (5 s) |
| Shift+R | **Dogpile**: every copy leaps onto the creature you look at (16) and hangs on: 4 each (6 at most) on landing, pinned (Slowness IV, Weakness) 2.5 s (10 s) |
| G | **Pinwheel Kick**: he throws himself flat and spins two whole turns on one leg, 10 within 2.5; every copy spins too, 4 within 2 of each (6 copies at most per creature) (1.5 s) |
| Shift+G | **Split Burst**: three copies burst out at once, 8 within 3 and thrown back (12 s) |
| X | **Splixson Leap**: a long agile leap; the copies bound after him; no fall damage 1.5 s (1 s) |
| Shift+X | **Copy Toss**: the nearest copy (6 blocks) hops to his back and hurls him where he looks; the landing hits 9 within 2.5; no fall damage (6 s) |
| Z | **Pile-Up**: the copies leap up and dive onto the spot you look at (16), each to its own place: 14 within 3, +3 per copy landed there (6 at most), slowed (8 s) |
| Shift+Z | **Ditto Army** (ultimate): up to all ten copies pour out of him, one every 0.1 s; for 10 s each goes for its own creature within 12 and the pack punches twice as fast. They stay when it ends (60 s) |
| V | **Split**: one copy pulls itself out of him, 10 at most (0.5 s) |
| Shift+V | **Merge**: one copy walks back into him, +2 health; one at a time; hold to call them in turn (0.5 s, from when it is in) |
| C | **Shell Game**: swaps places with a random copy while the others hop round; every mob hunting him loses him; invisible 3 s (30 s) |
| Shift+C | **Switcheroo**: he hoists his nearest copy (a new one if he has none) and throws it where he looks; where it lands, or on the creature it hits, he and it swap places: 6 within 2 of the landing (8 s) |
| Attack | **Splixson Scrap**: fists +3, and every copy piles on whatever he hits |

Damage is on the prototype scale (Cannonbolt: quick 9-14, normal 14-22, big 22-28). Ditto's own hits are light: 9-16. The pack makes
up the rest: a full Dogpile is 24, a full Pile-Up 32, ten copies on one target about 30 a second (60 in the Ditto Army).

## Passives

- **Felt by All**: half of a copy's damage reaches him.
- **Wall Crawl**: press into a wall to climb it. His copies climb too.
- **Pack**: the copies follow him, climb, and fight whatever he hits or whatever hits him.
- **Merge heal**: +2 health for each copy that walks back in.

## Weaknesses

- Weak alone: 18 health and light fists (+3).
- Hurt one, hurt them all, and a copy breaks at 6 health.
- Not on the dial until a mistransform finds him.

## Choices made (no questions asked, per the user's "keep going")

- Ten copies at most, the Ditto Army included (user: "make the limit to dittos clones 10 please"; it was six, twelve in the army).
  Since the army can no longer go over the limit, what makes it an ultimate is ten at once and the pack punching twice as fast, and the
  copies stay when it ends.
- Merge is one copy per press, as the 0.5 s cooldown implies; holding Shift+V walks them all in, so a full pack is not ten presses.
- Switcheroo throws the nearest free copy; the throw is roughly 30 blocks at most, and a copy thrown off the world just pops, with no swap.
- Feedback is 50%, capped by the health the copy had left. At 100% every area attack would hit him seven times over.
- Copies are plain entities, not mobs, so they don't fight the AI system, despawn, drop loot or need pathfinding. They steer
  straight at their target and climb what they run into.
- The badge goes on his forehead, not his chest, because that is where the skin and the show have it.
- Placeholder dial icon until the user sends one.

## Animations (v0.17.2 round 3)

Every move has its own third-person clip and first-person clip (`DittoClient`), and the big ones move the whole body through
`AlienBodyTurn` / `OmnitrixBodyTurnMixin`, because a model pose only turns the torso.

- **Pinwheel Kick**: a tuck, then he tips nearly flat and his whole body spins two turns, one leg driven straight, the other knee tucked,
  arms wide, a white crescent trailing the foot (world pass). In first person his arms whip across.
- **Swarm Strike** a thrown arm, **Dogpile** a two-handed drive, **Pile-Up** an arm that goes up with the pack and chops down with it.
- **Split Burst** balls up and explodes; **Ditto Army** arms up, jolting as each copy tears out; **Splixson Leap** and **Copy Toss** a
  crouch, then flat out in flight; **Shell Game** a duck and a spin; **Switcheroo** a hoist, a lean back, a heave, and a crouched arrival.
- Copies: thrown ones tumble spread like a star, leaps climb with knees tucked and dive head first, a Dogpile hangs on kicking, a Copy
  Toss heaves from the knees, and they climb hand over hand.

## Code

- `omnitrix/alien/CloneSpacing.java`: the fan, the rings and the push apart, shared with Echo Echo.
- `omnitrix/alien/ditto/DittoKit.java`: the moves, the copy list, feedback and `foe`.
- `omnitrix/alien/ditto/DittoCopyEntity.java`: the copy and its state machine.
- `client/omnitrix/alien/DittoClient.java`: skin, badge, poses, whole-body turns, the kick's crescent.
- `client/omnitrix/alien/ditto/DittoCopyRenderer.java`: how a copy is drawn in each state; `DittoStrand.java`: the strand.
- `Aliens.HIDDEN` / `unfound` / `pickHidden` / `mistransformFinds` (shared with Wildvine's build): the unlock pick. The glitch itself is in
  `Omnitrix.tick`.
- Tests: `OmnitrixDittoGameTests`, which covers the dial, every key, the even pick, the 11th/22nd/33rd unlocks with no repeats,
  copies, spacing, the one-at-a-time merge, feedback, Swarm, Kick, Dogpile, Switcheroo, Shell Game, Leap, Toss and Army.
