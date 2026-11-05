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
- v0.17.2 (user: "save the data to the player and not the omnitrix"): the unlocks and the mistransform count belong to the player -- `Omnitrix.keepPlayerData` carries them through taking the watch off and putting one back on (either watch); the state is persistent and copied on death.
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

- **Standing:** the skin's body, arms and legs on the player body, 2 blocks tall to the shoulders (scale 1.42: the headless body
  is 24 px and vanilla draws players at 0.9375, so `2 / (1.5 x 0.9375)`). The head and hat layer are cleared, so he is headless,
  with his face painted on his chest, as in the show. The badge sits at the top of his chest.
- **Ball:** the skin's HEAD, painted as his shell (amber plates, dark seams, a circle panel on each side), rounded into a ball.

| | Standing | Ball Mode |
|---|---|---|
| Health | 40 | 40 |
| Armour | 16 + 3 toughness | 16 + 3, and half damage on top |
| Speed | 0.8 | 10 blocks a second, 25 at full Momentum |
| Hitbox | 0.85 x 2.0 (headless: ends at his shoulders) | a 2-block ball |
| View | from his chest (eye 1.62) | low (eye 1.16) |
| Hands | fists +9 | none (no punching, no tools; he can eat and drink) |

## Keys

V switches between the two modes. R, G, X and V change with the mode (`liveMoveOn`), and the HUD shows the live move. Z, Shift+Z, C
and Shift+C are the same in both forms; those that need the ball curl him up and leave him in it.

| Key | Standing | Ball Mode |
|---|---|---|
| R | **Shoulder Bash**: lunge, 14 to everything just ahead, knockback (1.2 s) | **Cannonball Charge**: straight-line burst, 20, bursts weak blocks, stops at walls (5 s) |
| Shift+R | **Shell Slam**: topple onto the shell, 20 in 2.5 blocks ahead, slowed (8 s) | **Rolling Rampage**: rolls to each of the 20 nearest enemies within 50 blocks and 5 above or below, 20 each (20 s from the end) |
| G | **Cannonball Launch**: fires himself where he looks, 20, bursts weak blocks, lands in Ball Mode (12 s) | **Big Bounce**: about 8 blocks up, landing 18 in 4 blocks (4 s) |
| Shift+G | **Ground Pound**: 16 in 4 blocks, knocked up (7 s) | **Meteor Drop**: up high, then straight down, 20 in 5 blocks and a quake (20 s) |
| X | **Tumble Roll**: about 6-block curled dodge, 8 to anything hit, half damage (4 s) | **Roll Boost**: 5 s with Momentum held full (12 s) |
| Z | **Rebound Strike**: fires himself at the mark and rebounds off it, three hits, 12, 12, 17 (8 s) | the same |
| Shift+Z | **Unstoppable Cannonball** (ultimate): see below (60 s) | the same |
| C | **Spin Charge** (hold): curls and spins on the spot, WASD dead, full Momentum in 1.5 s; let go with W held to shoot off (3 s) | the same |
| Shift+C | **Curl Smash**: jump, curl, drop, 20 in 3.5 blocks (10 s) | the same |
| V | **Curl Up** | **Uncurl** (needs room to stand) |
| Shift+V | **Shell Guard** (hold): as long as it is held, front hits -75%, projectiles sent back, slowed (4 s after) | **Shell Spin**: 2 s spin, 8 every 0.4 s in 3 blocks, projectiles flung (8 s) |

Where the old moves went (a displaced move goes to the key vacated): C's Cannonball Launch to G (vacated by the
Curl Smash), Z's Ground Pound to Shift+G (vacated by the Shell Guard), ball Z's Shell Spin to ball Shift+V. The Pinball Ricochet became the
Rolling Rampage. Unstoppable became the middle of the ultimate. The Launch and Drop came down to the damage scale now that neither is an
ultimate (Launch 26 to 20, Drop 28 to 20, Curl Smash 22 to 20, Tumble 9 to 8).

## Passives

- **Pelarota Fists** (standing): +9 punch damage.
- **Shell Back** (standing): hits from behind lose 40%.
- **Armoured Shell** (ball): half damage, and projectiles glance off. Any fall bounces him back up instead of hurting.
- **Momentum** (ball): see "The roll" below. No bar.
- **Ram** (ball): rolling into a creature hits it for 10 at no Momentum up to 20 at full, and knocks it flying. Each creature can
  be hit once per 0.5 s.
- **Ledge Roll** (ball): he rolls up ledges up to 4 blocks high and through leaves, as speedsters do (`SpeedLeaves`). No wall climbing.
- **Shell Shuttle** (ball): see below.

## The roll

User: "change his ball form speed, make him charge up momentum to go faster, at 0 momentum he moves at 10 blocks per second, at max
momentum he moves at 25 blocks per second, he doesn't slow down when going up blocks or fall in the air or turning, don't add a bar for
this, increase his ball collision damage based on his momentum, make it 10 at 0 momentum and 20 at max momentum, it should take him like
3 seconds of sprinting in a straight line to get to max momentum"

- `CannonboltRoll` replaces vanilla's walking on the owner's client (`CannonboltRollMixin`, the head of `Player.travel`). His horizontal
  speed is **set** each tick from Momentum, never accelerated, so ledges, air time and turns have nothing to bleed. Turning swings his
  heading (55% a tick) and costs no speed.
- A sprint builds Momentum 0 to 1 in 3 s. Rolling on without sprinting keeps it. Letting go of the keys on the ground bleeds it in 0.75 s and
  friction stops him. A wall (not a step) ends it. In the air, with or without keys, it holds.
- The server never sees his keys, so it reads Momentum back off his real speed. The Ram hits with that.
- Water and lava drag at him as normal, and creative flight is its own thing: the roll stands aside there.
- Eating costs no speed: the roll uses the direction of the keys, not their size.
- **F_DRIVEN:** while a move is moving him (dash, Rampage, Rebound Strike, the wind-up, the launches and drops) the server's velocity is what
  counts and his keys are dead.

## Moves

- **Spin Charge** (user: "player holds c and goes into ball form and begins spinning on the ground, theyre charging up their momentum, the
  player cant move with wasd while in this mode, upon releasing C while holding W they charge forward at max momentum"): the Momentum is
  wound up on his client; whether W is down at the release is only known there too. A hold shorter than 1.5 s leaves with what it wound up (decision: the
  user said "charging up"). With no W he just stops. Held 10 s it lets go by itself.
- **Rolling Rampage** (user: "autotrack the 20 nearest enemies on a similar Y level of the player within 50 blocks and then roll and attack
  them dealing 20 damage please, player doesn't return to original position afterwards, player doesn't teleport between enemies"):
  the order is nearest-next. He is moved only by velocity, at 25 blocks a second. "Enemies" is `HeroTargets.isHostile`, so
  villagers and farm animals are safe. "Similar Y" is 5 blocks up or down. A wall that the 4-block step can't take is steered round (30 to 125
  degrees either way). If he stops getting closer to one for 1 s, it is skipped. He has no true pathfinding, so a maze can defeat him.
- **Rebound Strike** (Z, my design from the show's habit of slamming into a robot and bouncing straight back): the creature under the
  crosshair within 24 blocks, or the nearest enemy within about 20 degrees of it. He flies at 32 blocks a second, hits, hops back and comes
  again, 12, 12, 17. With no mark he flies along his look and rebounds off the first thing. A wall between them ends it.
- **Unstoppable Cannonball** (Shift+Z, my design: the show's unstoppable ball, wearing Ultimate Cannonbolt's spikes): 1.5 s rising and
  spinning while spikes grow out of the shell and everything within 12 blocks is dragged in; 8 s under his own control at full Momentum,
  30 a ram, -80% damage, no knockback, bursting weak blocks; then (or on a second Shift+Z) straight up and down for 36 in 9 blocks.
- **Shell Guard** (user: "Make holding shift+V a block ability when not in ball form"): up for as long as Shift+V is held. Letting go of
  either key drops it.

## Shell Shuttle

User: "if player is in ball form then up to 2 squad mates can right click the player to climb inside them, they are only released when
player uncurls and get nausea from the experience, dont combine hitboxes and model just make the squadmates disappear so you cant see them"

- `CannonboltStow`. A mate inside is a passenger of the ball: vanilla carries them on every client. The ride is the only state, so
  nothing can go stale, and if it ever ends any way at all they are an ordinary player again.
- Hidden from every client (`CannonboltHideStowedMixin`, no body, tag or shadow). No hitbox (`CannonboltStowMixin`). No damage, except what
  bypasses invulnerability (/kill). No attacks, items or power keys. Mobs hunting them turn on the ball.
- Sneak does nothing. Uncurling, turning back for any reason (death, disconnect and timeout all come through `onRevert`) sets them
  down beside him, clear of walls, with 7 s of Nausea. A mate's own disconnect or death just ends their ride (`OmnitrixRiding`'s disconnect hook).
- While inside in first person, a mate looks out through the ball (it isn't drawn for them).

## Size, view and nametag

- Ball (user: "Make his ball form 2 blocks tall aswell please"): the hitbox and the drawn ball are 2 x 2 blocks, the hat layer's
  outside being the ball's edge, so he needs two-block gaps rolled up.
- View (user: "make the players view come out of their chest please since they dont have a head"): standing, the eye is at his
  chest (1.14 before the 1.42 scale, 0.92 crouching).
- Nametag (user: "make the nametag appear above the body instead of above the chest please"): the standing box ends at the top of
  his headless body (1.41 before scale), and the NAME_TAG attachment sits just over it, the same height off the ground as before.
  You never see your own tag on your own client; other players do.
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
- **The curl** (user: "when player goes into ball form make an animation for them rolling and curling into the ball form please, make it
  smooth and make sense"): `drawCurl`. 0.55 s, driven only by the synced `F_ROUND` flag, so every curl and uncurl of every move plays it on
  every client. He tucks, somersaults forward as he shrinks inside, and the plates sweep round from his back to close over his face. The
  body is a `PlayerModel` drawn by hand so it can turn over as one piece; the plates are the ball's own quads, cut by angle.
- **Spikes** on the ball for the ultimate: 34 four-sided cones on a Fibonacci sphere, growing over 0.5 s.
- **First person:** his arms play `FP_CURL` / `FP_UNCURL`, then rolled up he sees the top of his own shell turning over under his view at
  the speed of the roll, which is how every ball move reads from inside.
- **Facing and rolling:**
  - The head's front face is the ball's front. Standing still, it turns to where he looks. Rolling, it swings round to where he
    rolls.
  - The ball turns over forward by the distance it covers (angle = distance / radius).
  - When he stops, it rocks back until his face is at the front again.
  - Shell Spin spins it flat on the spot; the Spin Charge and the ultimate's wind-up spin it forward like wheels on ice, shaking harder as it
    winds up.
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

- **Sprint roll:** replaced by "The roll" above. He is still waved through the server's "moved too quickly" check while rolling
  (`SpeedForceMoveCheckMixin`).
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
