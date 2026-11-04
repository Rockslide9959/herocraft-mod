# Omnitrix: Buzzshock (v0.17.2)

User: "Also build buzzshock please heres the skin: https://www.planetminecraft.com/skin/buzzshock-ben-10-5321154/". Said earlier
about the new aliens: "remove the pixel omnitrix from his skin model and replace it with our omnitrix badge please. do research on
his powers and look at what he does in the show ... Make this another alien you unlock on the 11th mistransformation ... make it an
even chance to unlock [any of the hidden aliens] if you dont unlock any of them".

Buzzshock belongs to the **prototype** Omnitrix. His dial glyph is U+E019, next to Upchuck's U+E018 (built in parallel). He is not
on the Recalibrated Omnitrix.

## Found by accident

- He is a hidden alien (`Aliens.HIDDEN`), like Cannonbolt, Wildvine and Ditto. At first he is **not on the dial**.
- Every 11th mistransform (22nd, 33rd...) finds one hidden alien a mistransform can find. The pick is random and even among the ones
  still unfound (`Aliens.pickHidden` / `unfound`), so he joins that draw. Way Big is `ITEM_ONLY` and stays out of it.
- Once found, he is on the dial for good. The unlock belongs to the player, so taking the watch off and putting it back on keeps
  him (`Omnitrix.keepPlayerData`).
- Test tools: `/projecthero power tune omnitrix found on|off` and `mistransforms <count>`, as for Cannonbolt.

## In the show (Ben 10, original series)

Buzzshock is a Nosedeenian (also called a Megawhatt), from the Nosedeen Quasar. He is a tiny creature of living electricity. He is
black, with green eyes, a wide white grin, a white stripe down his front and a vent on top of his head that his electricity spits
from. In the original series he wears the Omnitrix on his chest.

What he does in the show:
- He shoots lightning bolts and blasts of electricity.
- He zips about at high speed as a streak of electricity, and flies.
- He passes into power lines, wires and electronics, travels through them and comes out somewhere else.
- He takes control of machines and overloads them. Robots and electronics are his natural prey.
- He absorbs electricity to grow stronger.
- He splits into copies of himself. Nosedeenians multiply by splitting, and the duplication is a big part of the character.

His weaknesses:
- Water shorts him out, and grounding drains him.
- He is tiny and physically weak.

Sources: the show itself; TV Tropes' original-series alien page
(https://www.tvtropes.org/pmwiki/pmwiki.php/Characters/Ben10OmnitrixAliensTheOriginalSeries); and the Ben 10 wiki file pages
search results (the wiki article itself was paywalled). They agree on the chest symbol in the classic series, the duplication and
the electricity powers.

## Body

The skin is the user's planetminecraft skin. It was downloaded through the built-in browser, since planetminecraft blocks curl.

| | |
|---|---|
| Health | 18 |
| Armour | 6 |
| Speed | 1.35 |
| Size | x0.4 (about shin high; Ditto is x0.55) |
| Fists | +1 (the attack stat); the punch is the kit's own shock (see Static Fists) |
| Safe fall | 8 blocks |

- **Badge:** the skin paints the Omnitrix as a 2x2 dark-blue and white mark in the middle of the jacket layer's white stripe
  (texels 23..24 x 37..38). `gen_buzzshock.js` paints those four texels in the stripe's white. The mod's badge sits on his chest
  there (body bone, y 2.0, just in front of the jacket layer).
- **Glow:** `buzzshock_glow.png` is drawn full-bright over the skin and flickers (a steady hum, quick crackles). At full strength:
  the green vent on his head, his eyes and the green lights on his limbs. At about 40%: his white stripe and grin. Water dims it
  to a third.
- **Crackle:** sparks flick off him now and then, and constantly while he flies.

## Keys

| Key | Move | What it does | Cooldown |
|---|---|---|---|
| R | **Lightning Bolt** | a bolt at the crosshair (24 blocks): 10 | 0.75 s |
| Shift+R | **Chain Lightning** | 14 to the creature you look at (20), then it jumps to 4 more within 7 of each other: 10 each | 8 s |
| G | **Static Touch** | a point-blank discharge ahead (3 blocks, 120 degrees): 14, stunned 1 s | 2.5 s |
| Shift+G | **Spark Bomb** | a lobbed ball of electricity; it bursts where it lands, up to 18 within 3.5 | 10 s |
| X | **Spark Zip** | a spark streak up to 12 blocks where you look, through creatures (8 each), stopping short of walls; no fall damage for 1.5 s | 1.5 s |
| Shift+X | **Spark Flight** | flight on / off | 0.5 s |
| Z | **Overload** | a pulse round him, 12 within 6, stunned 1 s; machines take 24 and go offline for 3 s | 12 s |
| Shift+Z | **Power Line Ride** | into the conductor you look at (within 48) and out of it, with a jolt of 8 within 2.5 | 8 s |
| V | **Split** | a copy splits off (4 at most, 4 health each) | 0.5 s |
| Shift+V | **Merge** | the copies arc back into you, +2 health each | 2 s |
| C | **Megawatt Swarm** (ultimate) | 8 copies at once for 10 s, each shocking its own creature within 12; then the extras fizzle and four stay | 60 s |
| Shift+C | **Megavolt** | every copy dives onto the creature you look at (16) and they discharge as one: 16, +3 a copy (6 at most), stunned 2 s; the copies are spent (with none, two split off first) | 15 s |

H is the power selector and N is the Omnitrix's, as for every alien.

**Conductors** (Power Line Ride): anything that gives a redstone signal (wire, torches, levers, buttons, repeaters, observers, a
redstone block...), and anything copper, redstone, rails, lightning rods, iron bars and chains. The ride looks along the crosshair
for the first one within a block and a half of the ray. He comes out on it (a wire, a rail) or on top of it (a copper block), where
there is room. With no conductor in view, nothing happens and no cooldown is spent. His copies ride the line with him.

## Copies

User (the task): "For duplicates, reuse Ditto's copy entity approach where it fits".

- `BuzzshockCopyEntity` is built like `DittoCopyEntity`: a plain, never-saved entity that fizzles as soon as its owner is not a
  transformed Buzzshock.
- Unlike Ditto's copies, his **fly**. They hover in a ring round his shoulders, bobbing.
- They fight whatever he fights, using Ditto's foe rule (`DittoKit.foe`: what he just hit, what just hit him, or a monster hunting
  him), or the creature a move sends them at. They hover about 2 blocks off it and shock it once a second (3, with line of sight,
  within 6 blocks).
- Each has 4 health. Unlike Ditto's, their damage is **not** fed back to him: in the show Nosedeenian copies are separate beings.
- Water fizzles a copy at once.
- The renderer is the player model in his skin at his size, with his glow pass and the chest badge. Arms are out, legs dangling, and
  an arm thrusts on each shock. The copies join his Split, Merge, Swarm and Overload poses.

## Passives

- **Static Fists** (attack): every punch is a shock, 4 (scaled by the attack charge like a normal swing), with sparks.
- **Living Electricity:** lightning never hurts him. A strike heals him 8 (once a second) and puts out the fire.
- **Machine Killer:** every shock does x1.5 to a machine. That is Upgrade's `UpgradeKit.machine`: golems, and mobs that are
  mechanical. Overload does x2 and knocks a machine offline for 3 s.

## Weaknesses

- **Short Circuit (water):** in water he takes 1 damage a second and sparks. His electric moves fizzle with a "Fzzt!" and no
  cooldown is spent (R, Shift+R, G, Shift+G, Z, Shift+Z, C, Shift+C). Flight drops, and his punches are plain. Spark Zip, Split and
  Merge still work.
- Tiny and fragile: 18 health, and a copy breaks at 4.
- Not on the dial until a mistransform finds him.

## Build notes

- Server: `omnitrix/alien/buzzshock/BuzzshockKit` and `BuzzshockCopyEntity` (entity `projecthero:buzzshock_copy`).
- Client: `client/omnitrix/alien/BuzzshockClient` (skin, badge, glow, poses, first-person clips, the flying pose),
  `alien/buzzshock/BuzzshockFx` and `BuzzshockCopyRenderer`.
- **Bolts:** short-lived held `AlienProjectileEntity` carriers, as Brainstorm's are. `buzzshock_arc` is a normal bolt and
  `buzzshock_big` is Megavolt's. The carrier sits at the bolt's end. Its colour field is 0 to draw the bolt from his hand, or
  `id + 1` to draw it from that entity (a copy, the Spark Bomb, the last link in a chain). The drawing is `BrainstormFx.bolt` in his
  lime (0x9CFF30) with a paler second strand. `buzzshock_ball` is the Spark Bomb: a real projectile with gravity and a 3.5 blast.
  Its burst adds a flash, sparks, a shock ring and arcs to everything it hit.
- **Registration:** `Aliens.BUZZSHOCK`, `Aliens.HIDDEN`, `OmnitrixMod` (register and `clearSessionState`, which
  `ServerStateReset` calls), `OmnitrixClient`, the `OmnitrixInfo.DIAL_ORDER` glyph list (after `upchuck`), and
  `font/omnitrix_dial.json` (U+E019).
- **Textures:** `scratchpad/ben10_v0171/gen_buzzshock.js`, from `buzzshock_dl/buzzshock_skin.png`. The dial icons come from
  the user's icon (`buzzshock_dl/dial_src/buzzshock.png`) through `IDS=buzzshock node scratchpad/gen_omnitrix_dial_icons.js`.
- **Tests:**
  - `OmnitrixBuzzshockGameTests`: the dial and glyph; every key has a move; the bolt and Overload against machines; the copies;
    Megawatt Swarm and Megavolt; Spark Zip, flight and Power Line Ride; water; lightning.
  - The discovery draw tests in `OmnitrixCannonboltGameTests` now read the pool from `Aliens.mistransformFinds()`, so the next
    hidden alien needs no test edits. They are: every 11th finds a new one with no repeats until all are found; even odds at every
    pool size.
  - The prototype dial count is 16, with Upchuck.
- **In game:** `docs/screenshots/v0172/buzzshock/`.
  - Stills: front, copies, back, the swarm, flight and first person.
  - `buzzshock_lightning.mp4` (6 s, 20 fps): two bolts at an iron golem, Chain Lightning through the zombies, the Spark Bomb,
    Megavolt and Overload. `buzzshock_lightning_frames.png` is a frame sheet from it.
  - The harness is `scratchpad/BuzzshockHarness.v0172.java.txt`. It needs a `buzzshock.flag` file in its run dir.
  - The in-game check led to three changes:
    - The copies hover beside and ahead of him, never behind, where the third-person camera is.
    - Overload has no blast sphere, which swallowed the close-in camera of a tiny alien.
    - His own sparks never spawn in his first-person view.
