# Omnitrix revamp proposal: Wildmutt + Ghostfreak

Status: **PROPOSAL, awaiting approval.** No code written. Same format as the Heatblast / Diamondhead / Four Arms revamps:
R G Z X C V, each with a tap and a Shift variant; X = movement, V = utility/mode, the rest attacks; Z / Shift+Z the big moves.
No H or N moves. Effects are posed models and rendered shapes (rings, outlines, model parts), with few particles.

Tags: **KEEP** = unchanged · **TUNE** = same move, new numbers or extra behaviour · **REWORK** = same slot, different move · **NEW**

Research note: the fan wikis are the main source and they disagree in places. Episode citations are the best-supported moments.
One finding matters for the design: Wildmutt **firing** quills comes from the comics and Ultimate Wildmutt, not the 2005 cartoon
(there his back quills are only for show). The kit keeps Quill Volley anyway; see open question W1.

---

## 1. Wildmutt (Vulpimancer)

**In the show.** A big orange, dog/ape-like beast with **no eyes**. He senses the world through the gill-like nostrils on his neck
(smell, hearing, vibration) and fights by leaping, climbing, pouncing and clawing. He can only growl and roar. He debuts in
*And Then There Were 10*, where he senses Gwen coming without seeing her. In *Lucky Girl*, Ben's head cold takes away his smell,
which leaves him effectively blind. In *Camp Fear* he tracks Max by scent into the pit tunnels. In Omniverse (*Of Predators and
Prey*) he is the prey in Khyber's hunt and wins by agility. Ultimate Wildmutt (UA) is bigger and has launchable spikes and a
bladed tail.

### Current kit (v0.17.2)
- R Pounce (12, pins) · Shift+R Maul (3 rakes + slam = 18)
- G Quill Volley (7 x 3) · Shift+G Savage Roar (cone 9: 6, slow/weak/stagger)
- X Leap · Hold X Coiled Leap (up to 1.8x) · Shift+X Wall Climb toggle
- Z Ground Slam (r 4.5, 16/10) · Shift+Z Quill Roll ULT (1.5 s roll 12/hit, -40% taken, burst 14)
- C Feral Frenzy (8 s buff) · Shift+C Scent Track (48 r, 30 s trail)
- V Dig (3x3 tunnel) · Shift+V Sniff (sense range 64, shows ores/chests)
- Attack: cleaving claw swipe · Use: eat/drink · Passive Scent Sense 24 (outlines, invisible too), gallop +20%,
  immune to Blindness/Darkness; underwater sense 6 and air runs out 2x as fast; a nearby explosion deafens him for 3 s

The kit is already solid. The revamp leans harder into **blind hunter**: what he senses depends on movement and smell, he chains
attacks between targets, and the one move with no basis in the show (Ground Slam) goes.

### Proposed kit

| Key | Move | What it does | Numbers | Tag / source |
|---|---|---|---|---|
| R | Pounce | lunge onto the creature ahead and pin it | 12, range 12, pin 1.25 s, cd 3 s | KEEP · his signature move from the pilot onwards |
| Shift+R | Maul | rear up: rakes then a double slam | 3 x 3.8 + 6.6 = 18, cd 7 s | KEEP |
| G | Quill Volley | 7 quills from the back in a cone | 3 each (21), cd 5 s | KEEP · comics / Ultimate Wildmutt (see W1) |
| Shift+G | Savage Roar | cone roar, **plus a sound "ping"**: a rendered ring spreads out to 24 blocks, and everything it touches is outlined for him **and his squad** for 6 s | cone 9: 6 + slow/weak/stagger 3.5 s; ping r 24, 6 s; cd 15 s | TUNE · his roar is his only "voice"; the ping uses his hearing |
| X | Leap | huge bound, no fall damage, landing shock | as now, cd 1.5 s | KEEP |
| Hold X | Coiled Leap | coil up to 1.5 s, up to 1.8x as far | as now | KEEP |
| Shift+X | Wall Climb | toggle; **new: jump while climbing = wall-kick** off the wall the way you look (a 6-block bound, no cooldown) | wall-kick 6 blocks | TUNE · bouncing off walls and trees in his fights |
| Z | Ricochet Hunt | pounce on the nearest enemy within 10, then bounce on to the next one within 8 of it, up to 4 targets (each hit once). With a single target he hits it twice. Uses the Pounce clip, chained | 8 a hit (up to 32), cd 10 s | **REWORK** (replaces Ground Slam) · his ricochet fighting style against groups (Vilgax drones, Khyber's hunt) |
| Shift+Z | Quill Roll | ULT: 1.5 s spiked roll, then a quill burst | 12 a hit, -40% taken, burst 14 r 4, cd 25 s | KEEP |
| C | Feral Frenzy | 8 s: +25% speed, +4 claws, +1 attack speed, heals | as now, cd 30 s | KEEP |
| Shift+C | Scent Track | mark a creature within 48 for 30 s: a scent trail to it | as now, cd 15 s | KEEP · *Camp Fear* (tracking Max) |
| V | Dig | 3x3 tunnel 3 deep, wherever you look | as now | KEEP |
| Shift+V | Deep Sniff | sense range 64 for 6 s, shows ores/chests; **new: also shows the last 20 s of footsteps** of players/mobs nearby as faint paw/foot prints | as now + trails 20 s | TUNE · scent tracking |
| Attack | Claw Swipe | cleaving swipe; **new: every 3rd hit is a Bite** (+3, short slow) | +3 every 3rd | TUNE · he bites constantly in the show |
| Use | eat / drink | unchanged | – | KEEP |

### Passives
- **Scent Sense (TUNE):** creatures within 24 are outlined, including invisible ones (as now). **New: motion matters.** A moving
  creature gets a bright outline. One standing still or sneaking gets a faint outline, and beyond 8 blocks it gets none. Sneaking
  becomes the counterplay against him, which fits a blind hunter.
- **Gallop (KEEP):** +20% speed while sprinting.
- **Eyeless (KEEP):** immune to Blindness and Darkness.
- **Predator's Appetite (NEW):** a kill restores 2 hunger and 1 heart (5 s internal cooldown). Fits since he can't hold food
  well and eating is awkward for him.

### Weaknesses
- **Underwater (KEEP):** senses shrink to 6 and air runs out 2x as fast.
- **Deafened (KEEP):** an explosion nearby shuts his senses for 3 s.
- **Choked Nose (NEW, from *Lucky Girl*):** smoke and stench shut off his scent: campfire smoke, Stinkfly's gas, lingering
  potion clouds, and freezing in powder snow. For 5 s his sense shrinks to 4 blocks and his screen edge goes dark and blurred
  (a client vignette, no particles). He sneezes on the way out, which is an animation.

### Visuals / animation
- **Roar ping:** one flat translucent orange ring quad that expands outward, rendered rather than particles. Outlines use the
  existing WildmuttSense glow mixins, in amber.
- **Ricochet Hunt:** reuse the pounce clip with a short in-air twist between targets. The camera gets a small FOV kick on each hit.
- **Wall-kick:** a new short GeckoLib clip (push off with all fours).
- **Footprints:** decal quads on the ground that fade over 20 s, shown only to him.
- **Sneeze:** a head-jerk clip and a small sound cue. No particles.

---

## 2. Ghostfreak (Ectonurite)

**In the show.** A floating grey-white phantom with black stripes, one eye and a wispy tail. He is intangible, invisible, can fly,
and can **possess** people. His skin is a protective layer: in *Ghostfreaked Out*, Zs'Skayr breaks out of the Omnitrix, **peels
the skin back** to reveal his true form (black-and-white tentacles from the chest, a skull-like face), tries to take over Ben,
and is beaten by **sunlight** (he bursts into flame). In *Permanent Retirement* (his debut) Ben uses him to scare Gwen. In *Be
Afraid of the Dark*, Zs'Skayr plans to plunge Earth into **darkness**. The wikis also say his true form is stronger in total
darkness.

### Current kit (v0.17.2)
- R Spectral Claw (10, 10, Reaping Rake 18) · Shift+R Wail (8, knockback, fear)
- G Possess (ride in a mob, it fights for you, burst out 14) · Shift+G Soul Siphon (2 s tether, 20, heals)
- X Phase (intangible up to 5 s, chills 4) · Shift+X Ecto Rush (dash through walls, 10)
- Z Chest Tentacles (grab 3, 6 + 3x4) · Shift+Z Haunt ULT (r 10: 8 + 3 every 0.5 s = 26)
- C Fright Aura (10 s panic, 2/s) · Shift+C Fade (10 s invisible, Ambush +10)
- V Spirit Sight toggle · Shift+V (empty)
- Attack Chilling Touch (+2, slow) · Use (empty hand) Spectral Bolt (5) · double-tap jump: fly
- Passive: physical blows deal half, slow fall, never drowns; sunlight burns him for 1 every 2 s

The pieces from the show are mostly there already. What's missing is **the skin peel / true form**, his best-known moment and
the source of his sunlight weakness. The revamp makes that his ultimate, adds the **darkness** theme from *Be Afraid of the
Dark*, and lets him actually **steer** what he possesses.

### Proposed kit

| Key | Move | What it does | Numbers | Tag / source |
|---|---|---|---|---|
| R | Spectral Claw | three-swipe combo, ending in the Reaping Rake | 10, 10, 18; cd 0.4 s / 2 s after the rake | KEEP |
| Shift+R | Wail | shriek cone: knockback, and what hears it flees | 8, fear 5 s, cd 12 s | KEEP · scaring Gwen, *Permanent Retirement* |
| G | Possess | dive INTO a mob. **New: you steer it** (WASD moves it, it looks where you look, left-click makes it attack at +50% of its own damage). G again, sneak, or 15 s bursts you out (14). Bosses throw you out after 3 s | 15 s max, burst 14 + 6 splash, cd 15 s after exit | TUNE · possession is his core power; in the show he controls the body, not just rides it |
| Shift+G | Soul Siphon | 2 s drain tether, heals him | 2.5 x 8 = 20, heal 1/pulse, range 12, cd 10 s | KEEP (see G2) |
| X | Phase | intangible up to 5 s; chills what you pass through | 4 + slow, cd 10 s | KEEP · phasing through walls and floors |
| Shift+X | Ecto Rush | intangible dash through walls and bodies | ~11 blocks, 10 + weakness, cd 6 s | KEEP |
| Z | Chest Tentacles | chest opens: up to 3 creatures in front yanked in and constricted | 6 + 3x4, range 8, cd 11 s | KEEP · true-form tentacles, *Ghostfreaked Out* |
| Shift+Z | **True Form** | ULT. He grabs his chest and **peels the skin open** (1 s). The reveal is a terror burst: r 10, 10 damage, everything flees 4 s. For **20 s**: four tentacles stay out and lash the nearest 3 enemies within 6 every second; claws +30%; Chest Tentacles has half its cooldown; his look causes fear (a mob he stares at for 1 s flees 3 s). The cost: physical-damage resistance is lost, and the **sun burns 3/s**. Ends with the skin wrapping back | burst 10 r 10; lash 4/s x3; 20 s; cd 45 s (starts at end) | **REWORK** (replaces Haunt) · *Ghostfreaked Out* skin peel + sunlight |
| C | Fright Aura | 10 s: everything near panics, weakened, 2/s | as now, cd 30 s | KEEP |
| Shift+C | Fade | invisible 10 s; first blow out is an Ambush | +10, blinds, cd 20 s | KEEP |
| V | Spirit Sight | toggle: see in the dark, living things within 32 outlined | as now | KEEP |
| Shift+V | **Lights Out** | NEW. Darkness falls within 12 for 8 s: enemies there get the vanilla Darkness effect and lose their outline/name tags, and lit torches/lanterns in range are visually snuffed (client render only, no block changes). Counts as "dark" for Creature of the Dark | r 12, 8 s, cd 25 s | **NEW** · *Be Afraid of the Dark* (Zs'Skayr's darkness plan) |
| Attack | Chilling Touch | +2, slows | as now | KEEP |
| Use (empty hand) | Spectral Bolt | ecto bolt | 5, cd 0.6 s | KEEP |
| Double-tap jump | Fly | always ready | as now | KEEP |

### Passives
- **Ectonurite Body (KEEP):** physical blows deal half, slow fall, never drowns.
- **Protective Skin (NEW name for the existing rule):** the skin is why normal-form sunlight only stings (1 every 2 s, as now).
  True Form removes it (see weaknesses).
- **Creature of the Dark (NEW):** where the light level is 4 or below (night outdoors, caves, inside Lights Out), +20% damage and
  move cooldowns tick 25% faster. A faint purple rim on the eye shows it is active.
- **Cold Presence (KEEP, renamed):** Chilling Touch, plus Phase's chill.

### Weaknesses
- **Sunlight (TUNE):** normal form 1 every 2 s (as now). **True Form: 3 per second** and he can't use Fade. This mirrors his defeat
  in *Ghostfreaked Out*. Being inside a host shields him (as now).
- **Daylight dulls him:** in open daylight his cooldowns run 15% slower. This is the counterpart to Creature of the Dark.

### Visuals / animation
- **Skin peel (the centrepiece):** the chest skin is a separate model layer split down the middle. Two flaps rotate outward over
  1 s, revealing an underlying true-form texture: black and white stripes, a skull-like face. The tentacles are segmented model
  cubes (as Chest Tentacles already uses) with a slow sway. The reverse plays at the end. No particles, just a single dark burst
  ring at the reveal.
- **Fear on screen:** a scared player gets a pulsing dark vignette and a slight camera shake (client side) instead of particle clouds.
- **Lights Out:** a darkening dome shader / translucent black sphere; the torches' flames are not drawn while inside.
- **Possession steering:** the host gets a faint purple outline and its eyes glow purple. Ghostfreak's model is hidden while inside.

---

### Round 3 (v0.17.2, as built)

User: "Upgrade ghostfreaks flying animations please, make it look good and make it look like how he flies in the show please" / "edit and
update animations on all moves please".

**Flight** is a pose of its own, read each tick from how he is actually moving (`GhostfreakClient.Drift`, per player on every client):
- **Hover:** upright, a slight stoop, claws raised before him drifting unevenly, a slow bob: the stalking float of the show.
- **Moving:** he tips head-first along his motion from the hips (level flight flat, a dive past flat, a climb upright, a retreat leaned
  back), lifted and centred in his hitbox, head still on the look, claws reaching ahead with a long stroke passing between them.
- **Flat out (sprinting):** the arms sweep back along his sides and he streaks.
- **Turning:** he banks into the turn. A thin wake of ash comes off him at speed.

GhostfreakTail's simulation is unchanged: the tail hangs off the leaning body bone and streams behind by itself.

**Every move** got a new clip with a wind-up, a hit and a settle (the claw combo's three swings, Wail, Possess, Grasp, Phase, take-off,
Fade, Fright Aura, the True Form peel and wrap, Lights Out, Tendril Snatch, Ecto Rush, the burst from a host, Chest Tentacles, Chilling
Touch, Spectral Bolt, Spirit Sight) and its own first-person clip; all play over the flight pose, and a clip's end hands back to the
base pose instead of snapping.

## Open questions

**Wildmutt**
- **W1.** Quill Volley isn't in the 2005 cartoon (comics / Ultimate Wildmutt only). Keep it, or swap G for something else, e.g.
  **Tail Lash** (Ultimate Wildmutt's bladed tail: a 270-degree sweep, 10, knockback)?
- **W2.** Ground Slam → Ricochet Hunt: OK to drop the Slam, or keep it somewhere (e.g. as the landing of Coiled Leap)?
- **W3.** Should his screen actually look **blind**? An opt-in "blind vision" filter would darken and desaturate the world, with
  only scent outlines and nearby blocks shown in orange. It would be a client toggle in config, not a key.
- **W4.** Sneaking hiding from his Scent Sense beyond 8 blocks: right for PvP, or too much of a nerf?

**Ghostfreak**
- **G1.** Can he **possess players**? Suggestion: a 3 s light version in PvP. Their camera wobbles, they're slowed, and they can't
  use items, with no movement control. Or keep possession mob-only?
- **G2.** Soul Siphon isn't from the show. Keep it, or replace Shift+G with **Phantom Grasp**: his claw passes inside a body for
  12 that ignores armour, plus 2 s of slowness and mining fatigue?
- **G3.** Haunt goes away (folded into True Form's opening burst). OK, or keep Haunt elsewhere?
- **G4.** True Form sun damage of 3/s: harsh enough to be a real risk at noon (a full 20 s would be 60). Intended, or cap it
  (e.g. auto-revert to normal form when below 6 HP in sunlight)?
