# Omnitrix revamp proposal: Swampfire

Status: **PROPOSAL, awaiting approval.** No code written. Same format as the Wildmutt / Ghostfreak revamp:
R G Z X C V, each with a tap and a Shift variant (some held); X = movement, V = utility/mode, the rest attacks; Z / Shift+Z the
big moves. No H or N moves. Effects are rendered shapes (cones, vines, rings, model parts), with few particles; fire that lands
on the ground is **real temporary fire blocks** (`PyrokinesisHandlers.groundFire / fireRing / fireScatter`).

Tags: **KEEP** = unchanged · **TUNE** = same move, new numbers or extra behaviour · **REWORK** = same slot, different move · **NEW**

Research note: ben10.fandom wasn't usable, so the other sources were fan wikis (omnipediav10, giantbomb) and a respect-thread
summary. They agree on the core powers: fire from the hands, methane he sweats and lights, plant and vine control, explosive or
vine-growing seeds, fast regrowth and reattaching limbs, fire immunity, and the smell. **Episode citations are from memory.**
Only *Ben 10 Returns* (his Alien Force debut, fighting DNAliens) is cited with confidence. Everything else is marked
*(recurring)*. That means it happens across Alien Force / Ultimate Alien, but I can't tie it to one episode reliably. The cold
weakness is *not* well sourced (see S4).

---

## Swampfire (Methanosian)

**In the show.** A walking swamp plant, green and black, with rock-like plates and a rotten-swamp smell that Kevin and Gwen keep
complaining about *(recurring)*. He is Ben's go-to Alien Force heavy from his debut in *Ben 10 Returns*, where he burns through
DNAliens. He shoots **fire from his palms** and sweats **methane**, which he lights to make explosions *(recurring)*. He controls
**plants**: vines burst from the ground to bind enemies, and he throws **seeds** that either sprout into grabbing vines or
explode *(recurring)*. He **regenerates**: torn or blown apart, he pulls himself back together, and cut limbs reattach or regrow
*(recurring)*. Fire and heat don't hurt him. **Ultimate Swampfire** (Ultimate Alien) burns with **blue fire**, which is hotter.
He is bulkier, and he fires explosive seed pods *(recurring, from memory)*.

### Current kit (v0.17.2, basic)
- R Fire Blast (7, alight 4 s) · Shift+R Methane Torch (cone 8, 14, alight 5 s)
- G Seed Bomb (lobbed, 15 in r 3.5, roots 1 s) · Shift+G Vine Snare (6, rooted 3 s, range 20)
- X Methane Jet (launch, no fall damage 1.5 s) · Shift+X Vine Swing (haul to block, range 22)
- Z Methane Cloud (r 4, 6 s, 2/s + nausea) · Shift+Z Ignite (cloud explodes 18, r 5)
- V Regrowth (3 s rooted, +12, clears poison/wither) · Shift+V Bloom (bone meal r 4)
- C Root Eruption (10-block line, 16, launch) · Shift+C Inferno Bloom ULT (55 centre / 40 edge, r 8)
- Attack Burning Fist (alight 3 s) · Passive: fire/lava immune, regrows 1 HP/s (2 while burning), freezing damage x2

The basic kit already covers fire, gas, seeds and vines. The problems: the ultimate sits on Shift+C instead of Shift+Z, and
Ignite takes up a whole key when it should be the payoff of the gas. The show's **reassembly** isn't in the kit, and his fire
never touches the world. The revamp makes **gas + fire** his combo engine, moves the ultimate to Shift+Z as an Ultimate Swampfire
blue-fire nod, adds seed mines, and makes being "killed" a regrowth moment.

### Proposed kit

| Key | Move | What it does | Numbers | Tag / source |
|---|---|---|---|---|
| R | Fire Blast | methane fireball from the palm; **new: passing through his gas cloud detonates it** (see Z) | 7, alight 4 s, cd 0.75 s | TUNE · palm fire, *Ben 10 Returns* onwards |
| Shift+R (hold) | Methane Torch | **hold up to 2 s**: a continuous cone of burning gas from both palms. The rendered cone grows while held. Where it hits the ground it leaves real fire (`groundFire`, 4 s) | 3.5 per 0.5 s (up to 14), cone 8 / 60°, alight 5 s, cd 6 s from release | TUNE · flamethrower-style streams *(recurring)* |
| G | Seed Bomb | lobbed seed pod that bursts on impact; **new: it leaves a 2 s vine tangle** (rooted) where it burst | 15 in r 3.5, root 2 s (bosses 0.5 s), cd 5 s | TUNE · explosive seeds *(recurring)* |
| Shift+G | Vine Snare | vines spiral up round the creature you look at; **new: tap Shift+G again while it's held to yank it** 4 blocks toward you | 6 + yank, rooted 3 s, range 20, cd 8 s | TUNE · vines binding DNAliens *(recurring)* |
| X | Methane Jet | a burst of burning gas launches him; no fall damage | as now, cd 2 s | KEEP · gas-propelled leaps *(recurring)* |
| Shift+X (hold) | Vine Swing | vine to the block you look at. **New: hold to stay tethered and swing in an arc** (pendulum), release to let go with the momentum; tap = the current haul | range 22, cd 4 s | TUNE |
| Z | Methane Cloud / Ignite | tap: vent a cloud of gas round you (r 4, 6 s, 2/s + nausea). **Tap Z again while it hangs = Ignite.** Any of his fire passing through it (Fire Blast, Torch, Root Eruption's embers) also ignites it. The blast leaves a ring of real fire (`fireRing` r 4, 5 s) | cloud 2/s; Ignite **19** in r 5, alight 5 s; cd 10 s (starts after the blast) | **REWORK** (Cloud + Ignite merged) · sweating methane and lighting it, his signature combo *(recurring)* |
| Shift+Z | Blue Inferno | ULT. A 1 s build-up while his fire turns **blue**, then a blue firestorm round him. Afterwards he stays **Blue Flame for 12 s**: every fire move is blue, deals +25%, and Fire Blast leaves fire where it lands | 60 centre / 40 edge, r 8, `fireScatter` r 7 (6 s); Blue Flame +25% 12 s; cd 60 s | **REWORK** (Inferno Bloom moved from Shift+C) · Ultimate Swampfire's blue fire |
| C | Root Eruption | a line of root spikes bursts up ahead, 10 blocks, launching what it hits | 16, cd 7 s | KEEP · vines from the ground *(recurring)* |
| Shift+C | Seed Scatter | NEW. Flings 5 seeds in a fan. They plant where they land (20 s). An enemy coming within 2 blocks makes a seed **burst into snapping vines** | 8 per seed, root 1.5 s, max 5 live, cd 12 s | **NEW** · seeds sprouting vines *(recurring)* |
| V | Regrowth | 3 s rooted heal; clears poison/wither; **new: also reattaches. Clears Slowness/Weakness, and the Reassembly cooldown ticks 2x as fast while channelling** | +12 over 3 s, cd 20 s | TUNE · regrowing limbs *(recurring)* |
| Shift+V | Overgrowth | Bloom (bone meal r 4), **plus: on the wall you look at, grows a climbable vine ladder** (real vine blocks, gone after 30 s) | r 4; ladder up to 8 tall; cd 10 s | TUNE · plant control |
| Attack | Burning Fist | sets alight 3 s; **new: +2 against a target already burning** | +2 | TUNE |
| Use (empty hand) | Vine Lash | NEW. A vine whips from his arm, range 6. It hits and pulls small mobs a little toward him | 6, cd 0.8 s | **NEW** · vine whips / stretching limbs *(recurring)* |

Damage check: quick 6-8 (Blast, Lash, seeds), normal 14-16 (Torch, Seed Bomb, Roots), big 19 (Ignite), ult 60/40.

### Passives
- **Methanosian Body (KEEP):** fire and lava never hurt him; his own fire blocks don't either.
- **Regrowth (KEEP):** 1 HP/s, 2 HP/s while burning or standing in fire. That makes his own fire rings a healing zone.
- **Reassembly (NEW, the show's best Swampfire trick):** lethal damage doesn't kill him. He **bursts into a heap of swamp mulch**
  for 3 s, untargetable and unable to act, then pulls himself back together at **10 HP** with 2 s of Resistance II. Cooldown
  150 s, shown on the HUD. Void damage and /kill still kill him.
- **Swamp Stench (NEW):** he smells *(recurring joke)*. A creature standing within 2 blocks of him for 2 s gets 3 s of Nausea.
  Wildmutt's "Choked Nose" weakness counts his gas and stench. No particles: a faint green haze shader on the victim's screen.
- Hidden Resistance I and his own armour (6) as now.

### Weaknesses
- **Cold (KEEP, flagged):** freezing damage x2. **New: while frozen** (powder snow, Big Chill, ice attacks), his regrowth stops
  and Reassembly can't trigger. A frozen Swampfire stays dead.
- **Doused (NEW):** underwater, his fire moves fizzle (Fire Blast, Torch, Ignite and Blue Inferno are refused; Cloud bubbles up
  harmlessly). Rain halves fire-block lifetime. Methane fire needs air, so this is logic rather than an episode.
- **Flammable cloud:** Ignite hits **everything** in the cloud except him, so it can hurt his own squad's armour stands and pets.
  Squad members are spared (friendly fire rules).

### Visuals / animation
- **Gas:** the existing churning yellow-green haze, made denser. At Ignite it flashes to an orange shell that swells and
  vanishes (the existing `burst` renderer). The fire ring is real blocks, not particles.
- **Torch (hold):** the existing cone renderer, its length driven by hold time; both palms forward, slight lean back.
- **Blue Flame:** recolour every fire renderer (torch cone, blast, burst) to white-blue/cyan; a blue glow layer on his head and
  shoulders; the fire blocks he leaves are vanilla **soul fire**, which is blue for free.
- **Reassembly:** his model collapses: limbs drop and the body sinks into a mulch mound (a simple model with vine tendrils). Then
  the parts slide back together in reverse over 0.6 s, with a single green ring. No particle shower.
- **Seed Scatter:** small knobbly pod models sitting in the ground with a slow pulse; vine-snap reuses the Snare vines.
- **Vine Lash / ladder / swing tether:** the existing `vine()` segment renderer.

---

## Open questions
- **S1.** Shift+Z: a **damage burst + 12 s Blue Flame buff** (as proposed), or a full **Ultimate Swampfire form** for 20 s (bigger,
  armoured model, blue fire, explosive seed pods from the shoulders), with the firestorm only as the opening?
- **S2.** Reassembly as a death save (10 HP, 150 s cooldown): right strength, or too strong next to the other aliens? An
  alternative is "survive at 1 HP once per transformation".
- **S3.** Cloud + Ignite merged onto Z (tap twice, or shoot it with fire). OK, or keep Ignite on its own key?
- **S4.** The cold weakness isn't solidly backed by the show (I can't cite an episode). Keep it as a gameplay counter, as is
  common in fan sources, or drop it and rely on Doused (water) as his only weakness?

Sources used (fan wikis, unverified): https://omnipediav10.fandom.com/wiki/Swampfire , https://giantbomb.com/wiki/Characters/Swampfire ,
https://myanimethoughts.com/swampfire-powers--why-he-replaced-heatblast
