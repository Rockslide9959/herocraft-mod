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
| X | Wing Beat | Round 4, replaces Flight (flight stays on double-tap jump). The wings go up (0.15 s) and one stroke throws him along the look; off the ground it lifts him into flight; a swimming burst in water. In the show he goes from still to flat out in a stroke of those wings *(recurring, from memory)* | speed 2.4 b/t (48 b/s); downwash 6 in 4, shoved; cd 3 s |
| Shift+X | Supersonic Flight | Round 4, replaces Supersonic Burst. Hold while flying: 3 s charge (see below), sonic boom, then 200 b/s along the look with a green trail until the keys come up | boom 12 in 3, shoved; cd 5 s from the release, 1 s after a dropped charge |
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

## Supersonic Flight (round 4)

User: "dont make x his flight, he already flies by double pressing space bar, put another move in x" and "for supersonic flight make it so that
while hes holding shift+X while flying he gets a green trail and flies at 200 blocks per second please, player needs to hold shift+X for 3
seconds tho and there must be a chargeup animation aswell".

- **Charge:** he drifts along the look at 4 b/s. His wings are thrown wide, then drawn in, shuddering, as he levels out along his aim; rings of green
  light close in faster and faster, his eyes and stinger gather light, neuroshock crackles over him; a Supersonic meter fills on the HUD.
  Shift is half of the key chord, so it does not sink him (`heldThrust`). Letting go of either key drops the charge.
- **Past the barrier:** `DirectionalFlightModel.jetraySupersonic`, a forced drive of 10 blocks a tick. Arms tight to his sides, wings a narrow dart.
  `JetraySonicTrail` lays the green streamer (2 s, 400 blocks at full speed), every viewer drawing it from the synced flag.
- **Terrain:** `Entity.move` sweeps the player's whole box along the step, so 10 blocks a tick cannot tunnel through a loaded block
  (`jetraySupersonicCannotTunnelThroughAWall`). He stops or slides.
- **Chunks:** that sweep sees only loaded chunks. So the client checks 96 blocks along the look (`DirectionalFlight.clearAhead`) and
  `supersonicLimit` caps the drive: full speed with 84+ blocks clear, easing down, and all but stopped 24 blocks short of the first missing chunk. The
  world generates far slower than he flies: in a fresh normal world at render distance 8 the test run held about 30 b/s, on the chunk frontier and
  never past it. 200 b/s holds over ground already generated, and over the build limit, where the brake is off (nothing unloaded can be in the
  way up there, and the moment he drops below it the brake is back). The HUD shows his real speed.
- **Server:** 10 blocks a tick is vanilla's "moved too quickly" limit, so `SpeedForceMoveCheckMixin` waves that one check through for `JetrayKit.fastMove`, and
  for 1 s after he lets go, while his last fast moves are still arriving. "Moved wrongly" still applies.
- While it runs his eyes, tail, Barrage, Pulse and a Tail Grab drop still work; moves that would move him or hold him still wait. A clipped-wing hit ends it.

## Model
Removed: the manta wings, the whip tail with its lit stinger, the head crest, the glowing eye cubes, and `jetray_extras.png`.
Jetray is now the wide player model in the user's skin. The skin file is a wide-format (4 px arm) 64x64 sheet, checked with
`scratchpad/af_echojet/pngcheck.js`, so it is also correct for slim-arm players. The Omnitrix badge stays. Move VFX: the client world
pass keeps the eye beams, tail bolt, barrage fan, pulse ring, overload charge and beam, and dive streak. Added: a crackling neuroshock
tether to whatever the Tail Grab carries (no tail model). New moves also draw QuirkFx shapes: the sonic boom ring, the dive trail,
paralysis rings, the overload impact ring, and the Dive Bomb shock ring with its crack decal.

Screenshots: `docs/screenshots/v0172/jetray/`.
