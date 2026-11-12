# Vision -- design (v0.17.3)

Requested by the user 2026-11-12: "add Vision to the game, make his abilities good and his powers good. Use this skin for the
power. Give him a power to project a hologram on himself that makes his skin the player's skin, similar to Cyborg. Make X a dash
and Shift+X a mobility attack move. Remove the cape from the back of the skin and add a cape model to the suit."

Drawn from the MCU (Avengers: Age of Ultron, Captain America: Civil War, Avengers: Infinity War, WandaVision) and the comics
(the synthezoid built by Ultron, density control, the solar jewel on his brow). Hero-Tier **Primary** power in the Guidebook's
**Marvel** section. House key rules: H = power selector only, no N, X = movement, V = utility/mode, the rest attacks. Flight is
**double-tap Space** like Starfire, Raven, Shazam and Doctor Fate.

## The skin (borrowed -- licence to check)

- "Vision" by **Doctor_Aurus** on PlanetMinecraft: https://www.planetminecraft.com/skin/vision-4192664/ (wide / Steve model,
  tagged Avengers: Age of Ultron). Original kept untouched in `docs/reference/vision/vision_doctor_aurus.png`, see `SOURCE.txt`.
- **No licence is stated on the page: ask the author / credit them before a public release.**
- `scratchpad/vision/make_vision_suit.js` builds, into `textures/entity/vision/`:
  - `suit.png` (wide arms) and `suit_slim.png` (3-px arms: each 4-wide arm face drops the middle column most like its
    neighbour, the Shazam suit's method, so bands and seams still line up);
  - the painted cape taken off: it is only on the outer layer (jacket back, the back edges of the jacket sides, the jacket's
    top and bottom faces, the backs, back edges and tops of both trouser legs). Those texels are cleared, so the suit's own
    green-and-maroon back (the base layer, which has no cape on it) shows;
  - `cape.png`, the real cape's cloth (64x48, the `FlowingCapeLayer` layout): the skin's mustard gold, darker toward the
    shoulders and lighter toward the hem, with the darker fold the painted cape had down its middle and a deeper gold edge.
- **The head is part of the suit** (Vision's face is the synthezoid's: maroon and green with the Mind Stone on the brow).
  The suit layer draws the whole body, head and hat layer included, a hair over the wearer's skin; the wearer's own outer layer
  is hidden while it shows (`PlayerModelMixin`, the Doctor Fate rule). The cape is `VisionCapeLayer` on the shared Superman
  cloth (full length: the painted cape reached the ankles).

## Getting it -- the Cradle

Ultron's last body, the one Helen Cho's Regeneration Cradle grew from vibranium for the Mind Stone.

- **Synthezoid Cradle** (new block): crafted from 3 Glass, 2 Vibranium Plating, an Ultron Core and 3 Iron Blocks -- all of it
  from purging Ultron (every clear drops plating and a core).
- **Right-click the Cradle holding the Mind Stone** (the first Ultron clear's reward): the stone sets into the synthetic body,
  lightning strikes the Cradle (harmless, it's the spark Thor gave it) and you wake up as **Vision**. The stone is used up
  (it's in your forehead now); the Cradle stays and can make another Vision with another stone.
- Like every Hero-Tier power it replaces your Primary. With the unique-powers gamerule on, a second player can't become Vision
  while someone is (the stone is not used).
- **Random serums never give it** (`PowerGrants.NOT_FROM_SERUMS`), like Shazam and Doctor Fate.
- Testing: `/projecthero power grant vision <player>` and `/projecthero power tune vision (energy <0-100> | cooldowns)`.

## Passives -- the synthezoid body

| | |
|---|---|
| Health | 45 (+25) |
| Damage resistance | 30% off every hit |
| Melee | +6 |
| Hunger | none: food stays at 18 with no saturation, so he self-repairs slowly like a well-fed player but never starves |
| Breathing | never drowns, never runs out of air |
| Immune | poison, wither, falls (density control lets him land softly), suffocation in a wall |

## Mind Stone Energy -- a Meter, Mind Stone gold

0..100. Pays for the moves. Regenerates **2.5 a second**, **4 a second in direct sunlight** (the comics' solar jewel), 1.5 in
the dark (caves, the Nether, the End). It doesn't regenerate while the beam, the Unleashed beam, Intangibility or Diamond Density
are draining it. Flight is free (it is his own density control, not the stone).

## Flight -- double-tap Space

20 blocks/s cruising, 36 sprinting, Kryptonian handling (shared directional flight). The cape streams out behind him. Diamond
Density is too heavy to fly: turning it on drops him out of the sky, and flight won't start while it's on.

## Keys

| Key | Move | Cost | Cooldown | What it does |
|---|---|---|---|---|
| R (hold) | **Mind Stone Beam** | 12/s (6 to start) | 1.5 s after | A golden-yellow beam from the stone on his brow, 32 blocks: 7 damage every 5 ticks (28/s) and a small push; up to 8 s. BeamClash-aware. |
| Shift+R | **Solar Lance** | 30 | 6 s | 0.6 s charge (gold gathers on the brow), then a piercing bolt down his look: 20 damage to every creature on a 48-block line. |
| G | **Density Punch** | 10 | 1.5 s | Max-density fist, 5 blocks: 18 damage and a launch; a creature hurled into a wall takes 6 more. |
| Shift+G | **Phase Strike** | 15 | 4 s | Phases his hand into the target (5 blocks) and re-solidifies: 14 damage that ignores armour, and a 2 s stun (Slowness VII, Weakness). |
| Z | **Mind Stone Pulse** | 25 | 8 s | A golden burst round him, 7 blocks: 12 damage, a hard shove outward and Glowing for 5 s. |
| Shift+Z | **Mind Stone Unleashed** | 80 | 2 min | The ultimate: he hovers and channels the stone for 3 s -- a wide golden beam (48 blocks) that hits everything along it for 7 every 4 ticks (up to 105); sweep it with your look. BeamClash-aware. |
| X | **Phase Dash** | 8 | 1 s | 10 blocks along the look in a quarter second, intangible the whole way: through creatures, nothing hurts him. |
| Shift+X | **Density Dive** | 25 | 5 s | With a creature under the crosshair (24 blocks): he phases straight through it and re-solidifies inside -- 22 damage, a launch along his path, a 1 s stun -- and ends 2.5 blocks behind it, facing it. With no target and 4+ blocks of air below: a max-density meteor drop straight down, 14 damage + 0.6 per block fallen (up to +16) in 4.5 blocks, knocking everything up. With neither: a short forward density rush that hits the first creature for 22. |
| C | **Synthezoid Grip** | 12 | 3 s after the throw | Grabs a creature (6 blocks, up to 120 health, no bosses) and holds it out in front for up to 4 s; C again hurls it along his look. It takes 10 when it hits a wall, the ground or another creature. |
| Shift+C | **Diamond Density** (toggle) | 3/s | 1 s | Max density: another 50% off every hit (65% with the passive), knockback immunity, +6 melee, 35% slower, no flight. |
| V | **Human Hologram** (toggle) | free | 1.5 s | Projects a hologram of your own skin over him from the Mind Stone, built top-down a texel at a time with a gold scan line (Cyborg's hologram); off, it drains away feet-first. While it's up the suit and the cape aren't drawn: you look like yourself. Every power still works and nothing turns it off but V. |
| Shift+V | **Intangibility** (toggle) | 8/s (10 to start) | 1 s | Attacks, arrows and other projectiles pass through him and no hit lands; he can't attack (melee or moves; Phase Dash and the toggles still work). He walks through walls sideways (floors and ceilings stay solid). It can't be switched off inside a wall; if the energy runs out inside one he is pushed out to the nearest open space. |

Hold Left-Alt for the move list (the shared overlay).

## Decided details

- **HUD**: bottom right, the shared layout -- status tags (Flying, Hologram, Intangible, Diamond Density, Unleashed), the title,
  the Mind Stone **Meter** (gold), then the six key boxes; a move he can't pay for is greyed; yellow while Shift shows the Shift
  moves.
- **Intangible look**: the suit gets a flickering gold-white ghost shell (an additive copy just outside it) and drifts golden
  sparks; with the hologram up only the sparks show.
- **Diamond Density look**: the suit is drawn darker and heavier-grey, with stone dust at his feet.
- **Wall phasing** uses the Silver Surfer / Ghostfreak approach (horizontal moves ignore blocks, vertical collision kept, the
  server's "moved wrongly" check waved through) so it's robust in Minecraft's collision; suffocation damage is cancelled while
  phased; leaving the phase inside a block is refused, and an empty bar inside one pushes him out to the nearest free 2-high
  space (searching 6 blocks).
- **The hologram doesn't drop on a move** (unlike Cyborg's): the user asked that powers still work with it on.
- **Mind Stone Unleashed** roots him in the air (no movement, he can look around to sweep it) and can't be cast while intangible.
- **Phase Strike's armour-ignoring damage** is indirect magic damage credited to him (so boss threat and squad rules see it).
- **Threat**: every hit goes through the mod's shared hit path (`AbilityHelpers.hurtBurst` with his own damage source), so every
  boss's threat table counts it; he has no taunt.
- **Balance**: between Doctor Fate (40 health, 30%) and Shazam (50 health, 35%, +10 melee): Vision's 45 / 30% / +6 melee
  with the strongest defensive toggles (Diamond Density, Intangibility) but no damage-boosting form.
