# Omnitrix revamp: Jetray

Status: **BUILT (v0.17.2).** Code: `omnitrix/alien/jetray/JetrayKit.java` (uses the shared `RecalKitMath` without changing it),
client `client/omnitrix/alien/JetrayClient.java` (uses `jetray/RecalFx`, unchanged). Tests: `OmnitrixJetrayGameTests` (6), plus the
older `OmnitrixRecalBGameTests` Jetray tests (unchanged, still pass).

The user asked: "can you work on revamps for the rest of the alien force aliens please, look at their powers and abilities in the
show and do research and make them good please". Under "dont add ultimate aliens yet" and "only use the skins i provided", the
skin is now the user's Planet Minecraft Jetray skin
(https://www.planetminecraft.com/skin/jetray-ben-10-alien-force-5144258/).

Research note: ben10.fandom returned 402, so the sources were fan wikis and summaries. They agree on these points. Jetray is an
Aerophibian who flies and swims at very high speed, even through space. He fires **green neuroshock blasts from his eyes and tail**
that temporarily shut down an organic nervous system (paralysis). His tail is **prehensile**: he carries Gwen with it. He is
vulnerable to **strong winds**, and his own neuroshock hurt him once when it was deflected back at him. **Episode citations are
from memory** and marked *(recurring)*.

## In the show
A crimson manta ray with a black underside and a whip tail. He is Ben's fastest flier in Alien Force *(recurring)*. He strafes
DNAliens and Highbreed ships with eye and tail blasts, which stun or paralyse *(recurring)*, dives at targets, and carries people
out of danger in his tail *(recurring)*.

## Kit (built)

| Key | Move | What it does | Numbers |
|---|---|---|---|
| R | Neuroshock Eyes | twin eye beam to the first creature in line; leaves a Neuroshock stack | 7, 24 blocks; cd 0.8 s |
| Shift+R | Tail Blast | neuroshock bolt from the tail; paralyses | 14 + paralysed 1.5 s (bosses 1/4), 28 blocks; cd 4 s |
| G | Dive Strike | streak along the look, hitting all you pass | 15 each; cd 6 s |
| Shift+G | Tail Grab | NEW. The tail grabs the creature you look at and carries it under you; Shift+G again drops it. A squad ally is carried unhurt; not bosses or big mobs | 4 on the grab, carry up to 5 s, range 5; cd 10 s from the drop |
| X | Flight | take off / land (fast flight; double-tap jump works too) | cd 0.5 s |
| Shift+X | Supersonic Burst | the old Afterburner, plus a sonic boom ring behind him | boost 2.5 (3.0 streamlined); boom 6 in 3, shoved; cd 3 s |
| Z | Neuroshock Barrage | five eye beams in a fan, each leaving a stack | 6 each, 16 blocks; cd 5 s |
| Shift+Z | Neuroshock Overload | ULT, moved from Shift+C. 1 s held still, then a 30-block, 3-wide beam | 60 + paralysed 3 s; cd 60 s |
| C | Dive Bomb | the old Thunder Dive: climb, then plunge; the landing bursts | 18 in 4, paralysed 1 s; cd 12 s |
| Shift+C | Neuroshock Pulse | ring round him | 13 in 5, Slowness II 3 s, a stack; cd 8 s |
| V | Streamline | toggle: faster flight, Dolphin's Grace II in water, Speed II on the ground | cd 0.5 s |
| Shift+V | Barrel Roll | NEW. Sideways roll (the way he drifts, else right): shots slip past, other hits do half | 0.75 s; cd 6 s |
| Attack (in flight) | Wing Ram | NEW. Hits gain +1 per 0.4 blocks/tick of speed and knock back | up to +5 |
| Use (empty hand) | Wing Slash | moved from Shift+G. The wing edge sweeps in front | 6 in a 3.5-block cone, shoved; cd 0.8 s |

Removed: Neural Scan (an echolocation-style ping now belongs to Echo Echo, which keeps the two aliens distinct).

Damage check: quick 6-7 (eyes, barrage, slash, boom), normal 13-18 (tail, dive, pulse, dive bomb), ult 60.

### Passives
- **Aerophibian:** no fall damage and no fly-into-wall damage; never drowns; Dolphin's Grace in water.
- **Neuroshock (NEW):** eye beams, Barrage and Pulse each leave a stack. The **third stack within 4 s paralyses** the creature for
  1 s and clears the stacks. Tail Blast, Dive Bomb and the Overload paralyse outright.
- **Insulated (NEW):** lightning does him half damage.
- Hidden Resistance I and his own 3 armour, as before.

### Weaknesses
- **Strong winds (NEW, from the show):** wind charges and explosions do +50% and knock him out of the air. The stall lasts 1.5 s:
  no flight, though he still takes no fall damage.
- **Clipped wings (NEW):** a single hit of 8 or more while flying stalls him the same way.
- **Light:** 24 HP and 3 armour. His beams stop at the first block.

## Model
Removed: the manta wings, the whip tail with its lit stinger, the head crest, the glowing eye cubes, and `jetray_extras.png`.
Jetray is now the wide player model in the user's skin. The skin file is a wide-format (4 px arm) 64x64 sheet, checked with
`scratchpad/af_echojet/pngcheck.js`, so it is also correct for slim-arm players. The Omnitrix badge stays. Move VFX: the client world
pass keeps the eye beams, tail bolt, barrage fan, pulse ring, overload charge and beam, and dive streak. Added: a crackling neuroshock
tether to whatever the Tail Grab carries (no tail model). New moves also draw QuirkFx shapes: the sonic boom ring, the dive trail,
paralysis rings, the overload impact ring, and the Dive Bomb shock ring with its crack decal.

Screenshots: `docs/screenshots/v0172/jetray/`.
