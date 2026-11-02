# Omnitrix revamp proposal: Upgrade + Grey Matter + XLR8

Status: **PROPOSAL, awaiting approval.** No code written. Same format as the Wildmutt / Ghostfreak revamp:
R G Z X C V, each with a tap and a Shift variant; X = movement, V = utility/mode, the rest attacks; Z / Shift+Z the big moves.
No H or N moves. Effects are posed models and rendered shapes (ribbons, rings, outlines, model parts), with few particles.

Tags: **KEEP** = unchanged · **TUNE** = same move, new numbers or extra behaviour · **REWORK** = same slot, different move · **NEW**

Research note: the Ben 10 Fandom wiki and its mirrors refused automated reads this time (HTTP 402 / bot wall), and the search
results were mostly fan pages. The show facts below are the well-established ones (species powers, recurring behaviour). Episode
citations are given only where I'm confident; anything marked *(recurring)* is a habit across many episodes rather than one scene.
Please spot-check before any of it goes into the Guidebook text.

---

## 1. XLR8 (Kineceleran)

**In the show.** A blue-and-black, raptor-like speedster with a helmet whose visor slides down when he runs, a long balancing tail
and ball-shaped "wheel" feet he skates on. He's fast enough for anything around him to look frozen, and runs up walls and across
water *(recurring)*. He makes **tornadoes by running in circles** and lands dozens of punches in a blink. Kinecelerans run on
**friction**, so slick ground throws them off, and pinning the tail holds one in place. In *Kevin 11*, Kevin absorbs his speed. Rush
(2016 reboot) is the same species.

### Current kit (v0.17.2)
- R Rapid Assault (5 x 2.5) · Shift+R Kick Barrage (8 x 2, last launches)
- G Blitz (zip 24, 14 + 8 round) · Shift+G Tornado Run (3 s: 1.5 / 0.5 s, throw-out 4, r 6)
- X Blur Dash (10 blocks, 6) · Hold X Whirlwind (r 6, 8) · Shift+X Phase (5 s through walls, untouchable)
- Z Mach Punch (20 + shock 8) · Shift+Z Speed Sweep ULT (every foe within 50, 26 each, cd 30 s)
- V Speed Mode (visor down, +100% speed, wall/water run, step +1, Regen I, mining +50%) · Hold V Overdrive (20 s, more speed,
  blows x2, cd 45 s) · Shift+V Afterimage Decoys (6 s)
- C Time Burst (r 12 slowed 5 s) · Shift+C Speed Mine (1x2 tunnel 16 ahead)
- Attack double jab (+1.5) · Use (empty) item sweep r 14 · Use on a mob: snatch its item
- Passive: 22 HP, speed 1.9, +50% sprinting, +2 attack speed, step 0.5, safe fall 6. Speed Mode's trail is the plain Speedster's
  see-through **after-image copies** (`SuperSpeedClientV0145`), plus CLOUD / ELECTRIC_SPARK particles from `Xlr8Kit.tick`

What you asked for: he keeps everything the mod's speedsters can do (the Super Speed set already sits under these keys, borrowed
from `SuperSpeedMoves` / `SuperSpeedHandlers`), but each move becomes **his**: visor-down tells, tail and ball-feet animation, a
show moment behind each one. He gets **his own meter** (Kinetic Charge, in place of the Speed Force's momentum), and his speed
mode gets the **blue-and-black streak lines**.

### Proposed kit

| Key | Move | What it does | Numbers | Tag / source |
|---|---|---|---|---|
| R | Blur Punches | the visor snaps down, a blur of punches into everything in a 3.5-block cone; the arms render as 3 overlapping ghost copies | 6 x 2.5 = 15, cd 2 s | TUNE (Rapid Assault, +1 hit) · hundreds of punches in a blink *(recurring)* |
| Shift+R | Kick Barrage | 8 skating kicks in 0.8 s, then a **tail-whip finisher**: a 360-degree sweep, r 3 | 8 x 2 + tail 5 (knockback 1.2) = 21, cd 7 s | TUNE · the tail as a weapon |
| G | Raptor Strike | zip to the foe you aim at (24 blocks) and **slide-tackle** it off its feet on his ball feet. It's tripped (can't move or jump) for 1 s; foes within 3 take the splash | 14 + 8 splash, trip 1 s, cd 5 s | TUNE (Blitz) · his hit-and-gone style |
| Shift+G | Tornado Run | 3 s of circle-running: a twister lifts what is near, then throws it out. **New: Shift+G again while it spins** lets the twister go, and it travels 12 blocks the way you look over 4 s, picking things up | 1.5 / 0.5 s, throw-out 4; loose twister 2 / 0.5 s, r 3; cd 15 s | TUNE · *(recurring)*, his signature trick |
| X | Blur Dash | a 10-block dash where you aim, through bodies | 6 to each, cd 2 s | KEEP |
| Hold X | Tail Spin | spin on one ball-foot with the tail out flat: everything within 6 is blown away | 8, knockback 2, cd 10 s | TUNE (Whirlwind, renamed and re-posed around the tail) |
| Shift+X | Phase | vibrate through walls on his level for up to 5 s (tap again to stop); nothing physical touches him | 5 s, cd 5 s | KEEP (see X1) |
| Z | Sonic Punch | one huge punch with a sound-barrier ring. **New: spends Kinetic Charge** for more damage and a bigger ring | 20 + shock 8 r 4; at full charge 35 + shock 12 r 6; cd 10 s | TUNE (Mach Punch + the Speed Force's Infinite Mass Punch, folded into one) |
| Shift+Z | Raptor Rush | ULT: blink through every foe within 50, then home. Each hop draws a **blue-and-black streak line** between targets (see Visuals). Untouchable while it runs | 26 each, cd 30 s | TUNE (Speed Sweep, renamed, new lines) |
| V | Speed Mode | toggle: visor down, flat-out speed, wall and water running, step assist. **New: the blue-and-black streak trail** (below) | as now | TUNE · visor down / wheel feet |
| Hold V | Overdrive | 20 s of more speed, blows twice as hard, Kinetic Charge fills 2x as fast; the trail runs longer and gets a white-hot core | as now, cd 45 s | KEEP (+ trail) |
| Shift+V | Afterimage Decoys | three after-images draw every foe off him | 6 s, cd 20 s | KEEP |
| C | Time Burst | everything within 12 slows to a crawl, the way the world looks to him in the show. **New:** his screen gets a thin blue visor-glass tint and the edges stretch | r 12, 5 s, cd 30 s | TUNE · his slow-motion POV shots *(recurring)* |
| Shift+C | Speed Mine | strip-mine a 1x2 tunnel 16 blocks ahead | cd 15 s | KEEP (see X3) |
| Attack | Double Jab | two jabs a swing | +1.5 | KEEP |
| Use (empty hand) | Item Sweep | sweep up every dropped item within 14 | cd 3 s | KEEP |
| Use on a creature | Snatch | take what it holds | cd 8 s | KEEP · he snatches weapons out of hands *(recurring)* |
| Use on a player | **Rescue Carry** | NEW. Scoop an ally onto your back (the Grey Matter shoulder-ride code, `OmnitrixRiding`). They come along at his speed; Use again, Shift, or any of his attacks sets them down. They take no fall damage while carried | – | **NEW** · he rescues people by grabbing and running *(recurring)*; the Speed Force's Rescue Run, his way |

### Passives
- **Kineceleran Speed (KEEP):** speed 1.9, +50% sprinting, +2 attack speed, step 0.5.
- **Kinetic Charge (NEW, his own meter):** a thin blue Hairline bar under the hotbar. It fills while he moves fast in Speed Mode
  (0 to 100 in 6 s flat out, 3 s in Overdrive) and drains 10/s standing still. Spent by Sonic Punch (full bar = the top numbers
  above). At 100 the visor's edge glows white. This is his version of the Speed Force momentum, smaller and simpler.
- **Tail Balance (NEW):** in Speed Mode he takes no knockback while moving, and turns at full speed without sliding wide. The tail
  swings out to the side on hard turns.
- **Reflexes (NEW):** in Speed Mode, arrows and other projectiles fired at him have a 30% chance to miss (he side-steps; a
  1-frame blur copy shows where he was). Beams and explosions still hit.

### Weaknesses
- **Friction (NEW, from the species' friction control):** on ice, packed/blue ice, slime and honey blocks, Speed Mode loses grip.
  He skids (momentum keeps going 1 s after you let go) and wall running stops. The ball feet render spinning in place.
- **Tail Pin (NEW, from the pinned-tail weakness):** anything that roots him (cobwebs, Grey Matter's snare, a powered stun, Slowness
  III+) drops him out of Speed Mode and empties Kinetic Charge. He can't toggle it back for 3 s.
- **Glass armour (KEEP):** 22 HP, armour 2.

### Visuals / animation
- **The streak trail (what you asked for):** in Speed Mode, while he moves faster than a walk, **three ribbons stream back from
  him: one from each ball foot at ankle height and one from the tail tip.** Each is a flat, camera-facing ribbon. A **blue band**
  (sheet `0x3A7BD5`, core `0x9FD8FF`) runs with a **thin black line** (`0x0A0E14`) on both edges, so it reads as blue-and-black
  speed lines. The lines last 0.8 s (16 ticks); in Overdrive 1.4 s with a white core.
  - **Reuse:** `src/client/java/com/projecthero/mod/client/flight/LightTrail.java` already does this for Green Lantern and Nova.
    It has a `History` of recent points (clears on teleport, drops old points), tapered fading camera-facing `ribbon(...)`s, twisting
    `strands(...)` and a `Palette` record. `GreenLanternFlightFx.java` shows how a power records an emitter point each client tick and
    calls `LightTrail.draw`. `src/client/java/com/projecthero/mod/client/quirk/engine/EngineRibbons.java` is the same idea with
    several emitters per body (twelve calf nozzles), which is the closer match for feet + tail.
  - **The one catch:** LightTrail's glow pass is additive (`HardLightRibbon.additive`), and additive **black is invisible**. The black
    edge lines have to go through `HardLightRibbon.translucent` (normal alpha blend), drawn after the blue band. Plan: add a
    `LightTrail.drawBanded(...)` (or an `edge` colour on `Palette`) that draws the blue sheet + core as now, then two narrow
    translucent black ribbons offset to each side, and leaves the additive glow off when the edge colour is dark.
  - **Where:** a new `Xlr8StreakTrail` in `client/omnitrix/alien/`, ticked from `Xlr8Client`. It keeps three
    `LightTrail.History`s per visible XLR8, fed from the posed foot and tail-tip world positions (`Xlr8Client` already poses the
    tail). In first person only the two foot lines draw, so nothing crosses the camera.
  - **Zips too:** Raptor Strike, Blur Dash and Raptor Rush send `SpeedStreakPayload` already (`SuperSpeedMoves.streak`, received in
    `SuperSpeedClientV0145.acceptStreak`). For XLR8, the client also lays the same blue/black band along that from→to line,
    fading over 0.5 s, so his zips leave lines as well as after-images.
  - **Cleanup:** `Xlr8Kit.tick`'s CLOUD and ELECTRIC_SPARK particles while running go. The lines replace them.
- **Visor:** already done (`Xlr8Kit.visorDown` + the mask skin). Add a 3-tick slide-down clip so the visor visibly drops on
  every speed move instead of swapping.
- **Ball-foot skating:** the run pose becomes a skate: legs push sideways in long strides, and the feet roll (a spinning-disc model
  part under each foot, visible only in Speed Mode). Wall run: body flat to the wall, tail streaming down.
- **Tail Spin / Kick Barrage finisher:** the tail model part rotates flat out, with one rendered blue arc quad for the sweep.

---

## 2. Upgrade (Galvanic Mechamorph)

**In the show.** Living liquid technology: a black body with green circuit lines and one round green eye. His real power is
**merging with machines**, which he spreads over, takes control of and **upgrades** (better guns, faster engines). He also fires
an **optic beam**, stretches and reshapes like liquid metal, and wraps around enemies *(recurring)*. Against a purely organic
fight he is the weakest of the original ten. In Ultimate Alien, Ultimate Upgrade adds a "weapon boost". In Omniverse, Malware
is a corrupted Mechamorph who **infects and takes over** technology (*Malefactor*, *Showdown*).

### Current kit (v0.17.2)
- R Optic Beam (12, 24 blocks) · Shift+R Overload (8, golems 16)
- G Tech Grab (6, stun 3 s) · Shift+G Circuit Lash (8, yanks to you)
- X Liquid Form toggle (fast puddle, through soft/half blocks, climbs) · Shift+X Stretch Grapple (22 blocks)
- Z Charged Beam (1 s charge, 18 + burst 6) · Shift+Z EMP Burst ULT (r 8, 26 → 16, golems x2, jams redstone, cd 30 s)
- V Overclock (held tool 15 s: +100% mining, +4 damage, mends) · Shift+V (empty)
- C Merge (furnace/dispenser/dropper/piston supercharged; iron/snow golem, minecart, boat piloted; 20 s, half damage) ·
  Shift+C Upgrade Tool (repair half; whole → +1 Unbreaking to III)
- Attack Circuit Jolt (+2, shorts golems) · Use on an iron door/trapdoor: it opens
- Passive: 28 HP, armour 6, speed 1.05, knockback resist 0.2

The kit already covers beam, merge, stretch and liquid. What's missing is the **tech takeover** (Malware's infecting, Ben's habit of
turning enemy robots on their owners) and **upgrading other heroes' gear**. The mod is full of robots now (Sentinels, Ultron
drones, Iron Man suits), so he finally has things worth taking over.

### Proposed kit

| Key | Move | What it does | Numbers | Tag / source |
|---|---|---|---|---|
| R | Optic Beam | a green beam from the eye | 12, 24 blocks, cd 1 s | KEEP · *(recurring)* |
| Shift+R | Stretch Fist | an arm shoots out up to 8 blocks as a liquid-metal fist and slams the target back | 10, knockback 1.5, cd 5 s | **REWORK** (Overload moves into Tech Takeover) · stretching *(recurring)* |
| G | Liquid Engulf | liquid metal pours over the target and sets hard. Stunned 3 s; **new:** a mechanical target is **merged** instead (as C, from range) | 6, stun 3 s, cd 6 s | TUNE (Tech Grab) · he wraps round foes *(recurring)* |
| Shift+G | Circuit Lash | a stretched whip that yanks the target to you | 8, cd 5 s | KEEP |
| X | Liquid Form | toggle: a fast puddle that seeps through soft/half blocks and climbs walls. **New:** also through iron bars, fences and under doors (1-block gaps at floor level). **New: Geyser**: any attack key while in it rises him up through the foe above the puddle | Geyser 8 + launch, cd 4 s | TUNE |
| Shift+X | Stretch Grapple | stretch to a block up to 22 away and pull there | cd 3 s | KEEP |
| Z | Charged Beam | 1 s charge, then a piercing beam and a burst | 18 + 6, cd 11 s | KEEP |
| Shift+Z | EMP Burst | ULT: everything within 8 shorts out, redstone jams. **New:** mechanical mobs (golems, Sentinels, Ultron drones, Iron Man drones) within 8 are also **taken over** for 10 s (as Shift+C, no cap) | 26 → 16, golems/robots x2; takeover 10 s; cd 30 s | TUNE |
| V | Overclock | merge into the held item 15 s: +100% mining, +4 damage, it mends. **New (weapon boost):** bows/crossbows: arrows pierce 1 and fly 50% faster; the mod's firearms: +50% fire rate, no reload; shields: no cooldown after a disabling hit | 15 s, cd 30 s | TUNE · Ultimate Upgrade's weapon boost |
| Shift+V | Upgrade Tool | repair half the held tool; already whole: +1 Unbreaking (to III). **New:** also works on armour pieces (the one you look down at / in the offhand) | cd 30 s | KEEP (moved from Shift+C) |
| C | Merge | merge into a furnace / dispenser / dropper / piston (supercharged) or an iron/snow golem, minecart or boat (piloted), unseen; C again leaves. **New:** merge onto an **ally player** as living armour (Ally Upgrade): they get +8 armour and +2 toughness, and their attacks fire your Optic Beam (6) every 2 s while you ride; you take no damage and can still use R | 20 s, cd 15 s | TUNE · merging to make things better *(recurring)*; Ben merging with his team's gear |
| Shift+C | **Tech Takeover** | NEW. Fire a nanite bolt (16 blocks). A **mechanical** target (iron/snow golem, Sentinel, Sentinel drone, Ultron drone, Master Mold minions, hostile Iron Man drones) turns green-lined and **fights for you** for 15 s. Bosses are only stunned 2 s. A **living** target is shorted out instead: the old Overload (8, slowed and weakened 5 s) | 15 s takeover (max 2 at a time), cd 20 s | **NEW** · Malware infecting tech (*Malefactor*); Upgrade turning robots on their owners *(recurring)* |
| Attack | Circuit Jolt | +2, shorts golems | as now | KEEP |
| Use on an iron door / trapdoor | Interface | it opens for you | as now | KEEP |

### Passives
- **Liquid Metal Body (TUNE):** knockback resist 0.2 (as now). **New:** arrows and thrown things pass through him 25% of the time
  (a green ripple where they go in).
- **Tech Sense (NEW):** mechanical mobs and powered redstone within 16 get a faint green outline (the existing mob-highlight
  glow, in his colour), so you can see what's worth merging with or taking over.
- **Merged Shell (KEEP):** half damage while merged into a block or vehicle; inside a golem, the golem takes the hits.

### Weaknesses
- **Electric Shock (NEW):** lightning and electric damage (Thor, a trident storm, the mod's electric attacks) deal +50% to him,
  and a hit while merged throws him out and stuns him 1 s.
- **Organic Fights (KEEP, made explicit):** slow on foot (speed 1.05); his merge and takeover tricks do nothing to most animals
  and players, so his raw damage against them is the beams.
- **Water (NEW, mild):** in water, Liquid Form is 40% slower (the puddle disperses) and Charged Beam's burst shorts out (no splash).

### Visuals / animation
- **Takeover:** green circuit lines crawl over the target's model (an emissive overlay layer, animated UV scroll, no particles), and
  its eyes or lights turn green. A thin green tether line runs back to Upgrade for 0.5 s.
- **Stretch Fist:** the arm is a chain of stretched model cubes (the Stretch Grapple's arm), with a fist that balloons on impact.
- **Ally Upgrade:** the ally's model gets Upgrade's black-and-green circuit skin as an armour layer (like a merge overlay), and his
  eye sits on their chest plate and fires the beams. Upgrade's own body is hidden.
- **Geyser:** a liquid-metal column model shoots up out of the puddle and reforms into his body (a 6-tick scale clip).
- **Overclock weapon boost:** the held item gets the green circuit overlay (as now) plus a green muzzle ring on bows and guns.

---

## 3. Grey Matter (Galvan)

**In the show.** A tiny grey, frog-like Galvan, the smartest species in the galaxy (Azmuth, who built the Omnitrix, is one; *Secret of
the Omnitrix*). He's physically very weak and wins by brains: **crawling inside machines to rewire them**, slipping through gaps,
pointing out weak spots, and quickly **building and fixing gadgets** *(recurring)*. He climbs walls, has a strong grip, breathes
underwater, and often **rides on someone's shoulder** giving instructions *(recurring)*. Being stepped on, grabbed or swatted is
the running joke.

### Current kit (v0.17.2)
- R Nerve Pinch (4, stun 3 s) · Shift+R Weak-Spot Strike (18; 24 on a marked target)
- G Analyze (mark: +35% damage from everyone 10 s) · Shift+G Decoy (ticking gadget 5 s)
- X Big Leap (no fall) · Shift+X Scurry (5 s top speed, hunters lose you)
- Z Snare Trap (10, root 4 s, max 3) · Shift+Z Smoke Charge ULT (22 r 4, blinds, everyone loses track, cd 30 s)
- V Pocket Workshop (crafting grid anywhere) · Shift+V (empty)
- C Genius Repair (blocks / boost furnace / repair gear) · Shift+C Mind Map (ores/chests/spawners through walls, r 20, 8 s)
- Attack precision jab (+2 on analyzed) · Use on a creature: quick read · Use on a player: shoulder ride
- Passive: 0.33 scale (fits 1-block gaps), 20 HP, speed 1.35; mobs keep losing track of him (30% a second at > 2.5 blocks)

The weak-but-smart identity is there. What it lacks is the show's best Grey Matter moment, **getting inside the machine (or the
monster) and pulling the right wire**. Smoke Charge is a generic ultimate. The revamp makes that his ultimate, gives him a
gadget he builds, a remote **rewire** utility, and wall crawling.

### Proposed kit

| Key | Move | What it does | Numbers | Tag / source |
|---|---|---|---|---|
| R | Nerve Pinch | pinch a nerve: the creature in front is stunned | 4, stun 3 s, cd 6 s | KEEP |
| Shift+R | Weak-Spot Strike | a precise crit | 18; 24 on a marked target; cd 5 s | KEEP · pointing out weak spots *(recurring)* |
| G | Analyze | reads a creature and marks it: +35% damage from everyone. **New:** a marked boss's attacks telegraph 0.5 s earlier (its wind-up outline turns grey for you and your squad) | 10 s, cd 10 s | TUNE |
| Shift+G | **Gizmo Turret** | NEW. Builds a fist-sized turret from pocket scraps where you look (6 blocks). It beeps and blinks, so mobs within 10 go after it (the old Decoy), and it fires darts at the nearest foe, or the analyzed one first. 6 HP | 2 dmg x 2/s for 10 s (40 max), taunt r 10, cd 15 s | **REWORK** (Decoy folded in) · building gadgets on the spot *(recurring)* |
| X | Big Leap | a huge leap for something so small, no fall damage | cd 1.5 s | KEEP |
| Shift+X | Scurry | 5 s top speed; hunters lose you. **New:** starts with a smoke puff (r 3, blind 2 s, no damage), which is what is left of Smoke Charge | 5 s, cd 12 s | TUNE |
| Z | Snare Trap | quick-craft a tripwire snare | 10, root 4 s, max 3, cd 8 s | KEEP |
| Shift+Z | **Inside Job** | ULT. Leap at a creature within 6 and **climb inside** (ear, vent, armour seam). For 3 s you're gone and it thrashes: 6 every 0.5 s that **ignores armour**, then you pop out the far side and it's stunned 2 s. On a **mechanical** target (golem, Sentinel, Ultron drone, robot bosses) you rewire it instead: it shuts down for 5 s (bosses 2 s) and takes the same damage x1.5 | 36 (54 on machines), stun 2 s / shutdown 5 s, cd 30 s | **REWORK** (replaces Smoke Charge) · crawling inside machines to rewire them *(recurring)* |
| V | Pocket Workshop | a crafting grid anywhere | as now | KEEP |
| Shift+V | **Rewire** | NEW. Remote-tinker with the block you look at (16 blocks): open/close an iron door or trapdoor; flip a lever / power a block for 10 s; **disable a spawner for 60 s**; defuse primed TNT (drops back as an item); disarm a tripwire | cd 5 s (spawner 30 s) | **NEW** · rewiring machines and traps *(recurring)* |
| C | Genius Repair | fix a block (anvils, cracked bricks), boost a furnace / brewing stand, or repair held gear | cd 30 s | KEEP |
| Shift+C | Mind Map | ores, chests and spawners outlined through walls | r 20, 8 s, cd 45 s | KEEP |
| Attack | Precision Jab | +2 on an analyzed target | as now | KEEP |
| Use on a creature | Quick Read | a quick read of its health | as now | KEEP |
| Use on a player | Shoulder Ride | ride on their left shoulder. **New: Backseat Genius**: while he rides, whatever the carrier hits is Analyzed for free (no cooldown, 5 s mark), and his R / Shift+R / Shift+G still work from the shoulder | – | TUNE · riding on Gwen's / Max's shoulder giving orders *(recurring)* |

### Passives
- **Too Small to Track (KEEP):** mobs keep losing sight of him.
- **Sticky Climber (NEW):** pressed into a wall, he climbs it like a spider (vanilla climbable rule through the kit's
  `climbs(...)` hook, as Upgrade's Liquid Form uses), and Shift holds him in place on it.
- **Amphibian (NEW):** breathes underwater and swims 50% faster.
- **Galvan Intellect (NEW):** an enchanting table shows him all three offers' enchantments, and anvil repairs cost 30% less XP.
  A quiet perk for the "smartest being in three galaxies".

### Weaknesses
- **Swatted (NEW):** he takes 2x knockback, and a hit from a creature larger than a player stuns him 0.5 s (being flicked away is
  the show's running joke).
- **Too Small for Big Weapons (NEW):** with a sword, axe or trident in hand his attack speed is halved. He fights with gadgets.
- **Fragile (KEEP):** small and light; falling blocks (anvils, gravel, sand) do double damage to him.

### Visuals / animation
- **Inside Job:** he dives and shrinks into the target (a 4-tick scale-to-zero at the contact point). The target's model shakes and
  bulges in small pulses (render-scale jitter), with a grey eye glint at a seam. He pops out with a little spin. No particles beyond
  one small spark puff on machines.
- **Gizmo Turret:** a tiny block-model entity (a cobbled box, a dish and a barrel) with a blinking red light; darts are thin
  rendered lines.
- **Rewire:** a thin grey line from his hand to the block, and a brief grey outline on the block. The spawner's flame model stops
  spinning while disabled.
- **Wall crawl:** a flat-to-wall pose with splayed limbs.

---

## Open questions

**XLR8**
- **X1.** Phase (through walls) is the Flash's trick, not XLR8's. Keep it as is, or swap Shift+X for **Friction Zero**: 4 s of
  frictionless ball-foot skating, low to the ground, through 1-block gaps, knocking over what he hits (6)?
- **X2.** Speed Mode trail: should the **blue/black lines replace** the see-through after-image copies, or run **alongside** them?
  Proposal: lines always, copies only in Overdrive.
- **X3.** Speed Mine isn't from the show. Keep it on Shift+C, or swap it for **Speed Heal** (the Speed Force's: 3 s still and
  vibrating, heals 8, cd 40 s)?
- **X4.** Kinetic Charge as a visible bar: OK, or keep it hidden (just the white visor glow at full)?

**Upgrade**
- **U1.** Ally Upgrade (merging onto a player as armour): OK in PvP squads, or allies only (same squad)?
- **U2.** Should Tech Takeover work on **other players' Iron Man suits** (a 3 s lock: no flight or repulsors)? Fits Malware; may be
  frustrating in PvP.
- **U3.** Ultimate: keep EMP Burst (+ mass takeover), or a bigger one: **Mech Merge**, where he pulls in nearby iron blocks and
  machines to form a 3-block-tall mech for 20 s?
- **U4.** Overload moves into Shift+C (on living targets). OK, or keep Overload separate?

**Grey Matter**
- **GM1.** Inside Job on **players**: allowed (a 3 s version, damage halved), or creatures only?
- **GM2.** Smoke Charge drops to a puff on Scurry. OK, or keep it as its own move somewhere?
- **GM3.** Is Galvan Intellect (enchanting/anvil perks) welcome, or should he stay purely a combat/utility kit?
- **GM4.** Swatted's 2x knockback: right for the joke, or too punishing on a 20-HP alien?
