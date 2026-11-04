# Omnitrix: Cannonbolt (v0.17.2)

User: "build canonbolt alien for the prototype omnitrix please. Use this skin:
https://www.planetminecraft.com/skin/cannonbolt-original-series/ do research on his powers and look at what he does in the show
aswell when building his kit please, change his abilities if hes in ball mode or not please"

Cannonbolt belongs to the **prototype** Omnitrix, after Grey Matter (dial glyph U+E014). He is not on the Recalibrated Omnitrix.

## Found by accident

User: "for the canonbolt alien i want ben to accidentally transform into the alien like a mistransformation"

- At first he is **not on the dial**. The scroll wheel, the alien wheel and dialling all skip him.
- The prototype's mistransform glitch fires on 50% of slaps (never under Master Control, never on the Recalibrated Omnitrix).
  User (later): "make it so that on the 11th mistransformation the player unlocks a new alien which for now is just canonbolt".
  Every mistransform is counted (persisted `omnitrix.mistransforms`). The **11th always lands on Cannonbolt** (the 22nd on the
  next hidden alien, when there is one). That turns Ben into him, and puts him on the dial for good (a
  persisted Omnitrix flag `omnitrix.found.cannonbolt`). The dial itself stays on the alien Ben picked.
- **Since Wildvine** (user: "make it an even chance to unlock either cannonbolt or wildvine if you dont unlock any of them"): the
  11th lands on Cannonbolt or Wildvine at random, 50/50, and the 22nd on the other. See `docs/OMNITRIX_REVAMP_WILDVINE.md`.
- That one discovery shows a line on the action bar: "The Omnitrix glitched into a new alien: Cannonbolt! He is on your dial
  now." Ordinary mistransforms stay silent, as the user asked earlier.
- Every other mistransform picks any alien on the dial at random, as before.
- The flag is lost only when the watch comes off (the Containment Pod).
- Test tools: `/projecthero power tune omnitrix found on|off` puts him on the dial or hides him again;
  `mistransforms <count>` sets the count (10 = the next mistransform finds him). `status` shows the count.
- Code: `Aliens.HIDDEN`, `found`, `discover`, `nextHidden`. `playable(p)` and `available` skip unfound aliens. The glitch
  is in `Omnitrix.tick`.

## In the show (Ben 10, original series)

Cannonbolt is an Arburian Pelarota. He is a big, white, top-heavy alien. He has no head: his face is on his chest. Thick gold armour
plates cover his back, shoulders and forearms.

What he does in the show:
- He curls up so the plates close into a near-indestructible ball. Then he rolls at high speed, like a cannonball.
- He rams through enemies, robots, doors and walls.
- In a closed space he ricochets off every surface like a pinball, gaining speed as he goes.
- He bounces and rolls up walls and ramps.
- Lasers and projectiles glance off his shell.

His weaknesses in the show:
- Standing up he is slow, clumsy and off-balance.
- Rolled up he cannot fight with his hands, and he can't stop or steer well.

Sources: the show itself, and TV Tropes' original-series alien page
(https://www.tvtropes.org/pmwiki/pmwiki.php/Characters/Ben10OmnitrixAliensTheOriginalSeries). The fan-wiki pages were paywalled or
fan fiction, so they were not used.

## Body

The skin is the user's second planetminecraft skin (https://www.planetminecraft.com/skin/cannonbolt-ben-10-original-series/;
the user: "The body, arms and legs should be used for the alien model and the head should be used for the ball form").

- **Standing:** the skin's body, arms and legs on the player body at 1.3 size. The head and hat layer are cleared, so he is
  headless, with his face painted on his chest, as in the show. The badge sits at the top of his chest.
- **Ball:** the skin's HEAD, painted as his shell (amber plates, dark seams, a circle panel on each side), rounded into a ball.

| | Standing | Ball Mode |
|---|---|---|
| Health | 40 | 40 |
| Armour | 16 + 3 toughness | 16 + 3, and half damage on top |
| Speed | 0.8 | x2.0, rising to x3.2 as Momentum builds |
| Hitbox | 0.78 x 2.0 (headless: ends at his shoulders) | a 1.75-block ball |
| View | from his chest (eye 1.63) | low (eye 1.05) |
| Hands | fists +9 | none (no punching, no items) |

## Keys

V switches between the two modes. Every other key changes with the mode (`liveMoveOn`), and the HUD shows the live move.

| Key | Standing | Ball Mode |
|---|---|---|
| R | **Shoulder Bash**: lunge, 14 to everything just ahead, knockback (1.2 s) | **Cannonball Charge**: straight-line burst, 20, bursts weak blocks, stops at walls (5 s) |
| Shift+R | **Shell Slam**: topple onto the shell, 20 in 2.5 blocks ahead, slowed (8 s) | **Pinball Ricochet**: bounces off walls and creatures, faster each bounce, 6 bounces or 5 s, 16 per hit (12 s) |
| G | **Curl Smash**: jump, curl, drop, 22 in 3.5 blocks (10 s) | **Big Bounce**: about 8 blocks up, landing 18 in 4 blocks (4 s) |
| Shift+G | **Shell Guard**: 3 s, front hits -75%, projectiles sent back (12 s) | **Meteor Drop**: up high, then straight down, 28 in 5 blocks and a quake (20 s) |
| X | **Tumble Roll**: about 6-block curled dodge, 9 to anything hit, half damage (4 s) | **Roll Boost**: 5 s at top speed, Momentum full (12 s) |
| Z | **Ground Pound**: 16 in 4 blocks, knocked up (7 s) | **Shell Spin**: 2 s spin, 8 every 0.4 s in 3 blocks, projectiles flung (8 s) |
| C | **Cannonball Launch** (ultimate): fires himself where he looks, 26, bursts weak blocks, lands in Ball Mode (25 s) | **Unstoppable** (ultimate): 8 s, faster, -80% damage, no knockback, rams do 24 and burst weak blocks (45 s) |
| V | **Curl Up** | **Uncurl** (needs room to stand) |

## Passives

- **Pelarota Fists** (standing): +9 punch damage.
- **Shell Back** (standing): hits from behind lose 40%.
- **Armoured Shell** (ball): half damage, and projectiles glance off. Any fall bounces him back up instead of hurting.
- **Momentum** (ball): the longer he keeps rolling, the faster he goes, up to 3 s of rolling. Stopping resets it.
- **Ram** (ball): rolling fast (0.3+ blocks/tick) into a creature hits it for 8 to 20, scaled by speed, and knocks it flying.
  Each creature can be hit once per 0.5 s.
- **Wall Roll** (ball): pressed into a wall, he rolls straight up it.

## Size, view and nametag

- Ball (user: "make canonbolts ball form 1,75 blocks tall please"): the hitbox and the drawn ball are 1.75 x 1.75 blocks, so he no
  longer fits one-block gaps rolled up.
- View (user: "make the players view come out of their chest please since they dont have a head"): standing, the eye is at his
  chest (1.25 before the 1.3 scale, 1.0 crouching).
- Nametag (user: "make the nametag appear above the body instead of above the chest please"): the standing box ends at the top of
  his headless body (1.55 before scale), and the NAME_TAG attachment sits 0.4 under it. Vanilla adds 0.5, so the tag hangs just
  over his shoulders (about 2.0 blocks). You never see your own tag on your own client; other players do.
- Badge (user: "remove the pixel omnitrix from the cannonbolt skin please and raise our badge by like 0.1 pixel please"): the
  skin's painted symbol (outer layer 21..26 x 36..37, base 23..24 x 21) is painted out in gen_cannonbolt.js. The badge sits at
  y 1.3 px (was 1.4).

## Weaknesses

- Slow and clumsy standing up, and a big target.
- Rolled up he cannot punch or use items.
- He needs headroom to uncurl.

## Build notes

- `CannonboltKit` (main) and `CannonboltClient` (client).
- The ball is drawn in place of the player model (a per-player `AlienClientKit.customBody(AbstractClientPlayer)` hook). It is a
  cube-sphere: each of the head's six 8x8 faces is a 12x12 grid pushed out onto the sphere, so every texel keeps its place and
  the faces meet without seams. The hat layer is a second shell 7% larger.
- **Facing and rolling:**
  - The head's front face is the ball's front. Standing still, it turns to where he looks. Rolling, it swings round to where he
    rolls.
  - The ball turns over forward by the distance it covers (angle = distance / radius).
  - When he stops, it rocks back until his face is at the front again.
  - Shell Spin spins it on the spot.
- This ball replaced the amber-seams ball made from the user's earlier reference picture
  (`scratchpad/ben10_v0171/cannonbolt_ball_ref.png`).
- Textures are generated by `scratchpad/ben10_v0171/gen_cannonbolt.js` from `scratchpad/ben10_v0171/cannonbolt_v2/cannonbolt2_src.png`:
  - `cannonbolt.png`: the body, head cleared.
  - `cannonbolt_ball.png`: the whole skin; the renderer reads only its head.
- The dial icons are the user's own art (`scratchpad/gen_omnitrix_dial_icons.js`). The Cannonbolt script no longer writes them.
- In-game roll video: `docs/screenshots/v0172/cannonbolt/cannonbolt_ball_roll.mp4`.
- Tests: `OmnitrixCannonboltGameTests`. Screenshots: `docs/screenshots/v0172/cannonbolt/`. Harness:
  `scratchpad/CannonboltHarness.v0172.java.txt`. It needs a `cannonbolt.flag` file in its run dir. It was run through a Gradle init
  script that adds a `runCbClient` run with its own run dir, so it doesn't share `run/` with other sessions.

## Sprint roll, sprint launch, fall bounces

User: "make cannonbolts ball form move at 25 blocks per second while sprinting, and when he sprint jumps its more like a forward
launch kind of thing, if he falls from a great distance the ball form should bounce a bit first"

- **Sprint roll:** sprinting in Ball Mode, his roll bonus builds with Momentum to `SPRINT_TOP_BONUS` (4.57). Measured in game on flat
  ground, that is 1.25 blocks a tick (25 blocks a second) after about 3 s of rolling (4.5 measured 24.7 b/s).
  - Walking rolls are unchanged. Roll Boost and Unstoppable fill his Momentum but never take a sprint past 25 b/s.
  - He is waved through the server's "moved too quickly" check while rolling (`SpeedForceMoveCheckMixin`).
  - The Ram (8 to 20, from 0.3 b/t) already reaches its 20 cap at 0.7 b/t, so it was not retuned.
- **Sprint Launch:** a sprint jump in Ball Mode (on the ground last tick, off it now, rising, sprinting) fires him where he looks.
  - The arc: at least 1.7 b/t forward (1.2x his roll if faster) with 0.5 lift. Measured: about 18 blocks in a 2.5-block-high arc,
    landing at about 1.25 b/t and rolling on.
  - Takeoff: a ring behind him, dust, a cloud puff and a crack.
  - Landing: 12 within 2.5 blocks, a ground ring, dust, a thud and a small camera shake.
  - Momentum stays full. There is a 16-tick gap between launches. No launch during his other moves or the Shell Spin.
- **Fall bounces:** a fall that would hurt (about 8+ blocks) does no damage rolled up. Instead he bounces up to 3 times, each lift
  0.55x the last, and then settles and rolls on.
  - The first lift is 0.3 + 0.04 x the damage, clamped 0.45..1.1. A 30-block drop measured about 6 blocks, then 1.4, then a hop.
  - The run ends when the bounces run out, when a lift is under 0.22, on uncurling, in water, when any other airborne move starts,
    or after 6 s, so it never sticks.
  - Meteor Drop and Big Bounce keep their own landings: their falls never start a run.
- **Motion:** bounces and launches use his measured per-tick motion, since the server's own velocity for a player isn't his. Big
  Bounce now keeps his rolling speed too.
- Videos: `docs/screenshots/v0172/cannonbolt/cannonbolt_sprint_launch.mp4` and `cannonbolt_fall_bounces.mp4`. The camera follows
  him (one client cannot film its own player from outside).
