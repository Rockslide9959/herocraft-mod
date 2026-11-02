# Omnitrix revamp proposal: Humungousaur

Status: **BUILT (v0.17.2)** with the user's decisions ("Yes do all this stuff but dont add ultimate aliens yet please"): no
Ultimate Humungousaur (no Bone Missile Barrage, shell or missile fingers) -- Shift+Z is the **Earthshaker** instead (rear up, three
stomps 0.6 s apart: 10 and 10 in 6, then 55 in 8, +1 block a step, damage x the size bonus, rooted, cd 30 s); Titanic Slam lives on
as the landing of a full-size Dino Leap (16 in 7); size bonus +10% a step; three sizes, no Colossal step. Skin: the user's Planet
Minecraft skin 5136728. Screenshots: docs/screenshots/v0172/humungousaur/. The original proposal follows. Same format as the Wildmutt / Ghostfreak revamp:
R G Z X C V, each with a tap and a Shift variant (hold moves allowed); X = movement, V = utility/mode, the rest attacks;
Z / Shift+Z the big moves. No H or N moves. Effects are posed models and rendered shapes (rings, arcs, model parts), few particles.

Tags: **KEEP** = unchanged · **TUNE** = same move, new numbers or extra behaviour · **REWORK** = same slot, different move · **NEW**

Research note: ben10.fandom blocks fetching, so this is from fan-wiki search snippets (other fandoms, Giant Bomb) plus memory of the
show. Solid: he debuts in the Alien Force premiere *Ben 10 Returns*; Vaxasaurians are ~12 ft and can grow to ~60 ft; growing
adds armour plates and spikes; **Ultimate Humungousaur** (Ultimate Alien) cannot change size, has a spiked shell and a mace tail,
and his hands turn into launchers that fire bone "missiles" (unstable bone fragments in AF/UA; proper missiles in Omniverse).
Everything else below is marked *(recurring)* and is from memory of many episodes, not a specific citation.

---

## Humungousaur (Vaxasaurian)

**In the show.** A huge tan dinosaur-man with a cream belly, a heavy tail and back plates. Ben's go-to heavy hitter in Alien Force
from *Ben 10 Returns* on, mostly against DNAliens and Highbreed. *(recurring)* He grows on demand, from roughly 12 ft up to about
60 ft, and gains more spikes and plates the bigger he gets. He fights with raw strength: ground pounds, shoulder and head-down
charges, tail swipes, picking up cars and trucks (and enemy ships and robots) and throwing them. He is tough enough to take blasts,
and he braces to hold up or catch huge things. *(recurring)* His weak side is that he's slow and an easy target, and big tight
spaces don't suit him. **Ultimate Humungousaur** (Ultimate Alien) is a fixed-size evolved form with a spiked turtle-like shell and
a mace tail. His fingers fire bone missiles in a rapid barrage.

### Current kit (v0.17.2, basic)
- R Haymaker (8, knocked away) · Shift+R Uppercut (14, launched)
- G Ground Pound (15 in r 4.5, +1.5 a step) · Shift+G Tail Sweep (12 in r 3.5, +1 a step)
- X Stampede (1 s charge, 12) · Shift+X Dino Leap (landing 10 in r 4)
- Z Boulder Toss (17 + 8 splash) · Shift+Z Grab and Hurl (12, not bosses)
- C Primal Roar (cone 10: 12, weak/slow) · Shift+C Titanic Slam ULT (55 centre / 40 edge, r 8 +2 a step)
- V Grow one step · Shift+V Shrink one step (x1.5 → x2.2 → x3.0; each step +20% damage, +3 armour, reach, KB res, -8% speed)
- Passive: 44 HP, armour 10 (+3 toughness), 60% KB resistance, safe fall 6

**What's wrong with it.** The ultimate sits on Shift+C instead of Z, and Z (Boulder Toss / Grab and Hurl) is a split version of
what Four Arms already does on Shift+G and V. Titanic Slam is basically Four Arms' Meteor Slam. Size scaling of +20% per step
pushes normal moves past the agreed scale (Boulder Toss 17 → 23.8 at full size). The revamp makes him **the size alien**: size
changes how every move works (range, splash, what he can lift), and he gets the spikes, the brace and Ultimate Humungousaur's
bone missiles, which Four Arms has nothing like.

### How he differs from Four Arms
| | Four Arms | Humungousaur |
|---|---|---|
| Identity | fast combos, four-fist technique, sonic clap | slow, huge, **size-scaling**: more reach, radius and lift as he grows |
| Unique tools | Thunder Clap, Four-Arm Guard, Giant Swing | tail club, bone spikes, Brace, Bone Missile Barrage |
| Throwing | hold-and-carry Grab, Giant Swing | one-press Heavy Hurl; the size of what he can lift scales with his own size |
| Ultimate | Meteor Slam (a sky drop) | Bone Missile Barrage (ranged, aimed) |

### Proposed kit

Damage below is at base size. Size bonus is **+10% a step** (see passives), so x1.2 at full size.

| Key | Move | What it does | Numbers | Tag / source |
|---|---|---|---|---|
| R | Haymaker | heavy straight punch; small mobs are knocked flat | 7, cd 0.7 s | TUNE (8 → 7, faster) · *(recurring)* |
| Shift+R | Uppercut | launches the target skyward | 14, cd 4 s | KEEP · *(recurring)* |
| G | Ground Pound | both fists into the ground: shockwave ring, thrown up | 15, r 4.5 (+1.5 a step), cd 6 s | KEEP · his best-known move *(recurring)* |
| Shift+G | Tail Club | full tail spin. At full size (x3.0) the tail has its club: +3 damage and everything hit is knocked down 1 s | 13 (16 with club), r 3.5 (+1 a step), cd 5 s | TUNE · tail swipes; club from his spiked big form and Ultimate's mace tail |
| X | Stampede | head-down ram. **Hold X to keep running up to 2.5 s** and steer with the camera; carries the first target along; hitting a wall ends it with a crash (target pinned: +6, stunned 1 s); smashes glass, leaves, fences and wood planks | 12 (+6 wall crash), cd 6 s | TUNE · his charges and shoulder rams *(recurring)* |
| Shift+X | Dino Leap | great bound, landing quake, no fall damage | 10 in r 4 (+1 a step), cd 6 s | KEEP |
| Z | Heavy Hurl | one press: grabs what is in front and throws it where you look. A creature if one is there (base: up to player size; x2.2: up to iron golem; x3.0: up to ravager; never bosses), otherwise he **tears up a slab of the ground** (a real 3x1x2 block chunk model). The thrown thing bowls over whatever it passes | 18 to the thrown / hit target, 8 splash r 2.5, cd 8 s | **REWORK** (merges Boulder Toss + Grab and Hurl) · throwing cars and trucks *(recurring)* |
| Shift+Z | Bone Missile Barrage | **ULT.** Ultimate Humungousaur's shell and spikes rise out of his back (model layer, 0.5 s), he plants his feet, and his fingers fire bone missiles where you look for 3 s (20 missiles, each a small blast). Can't move while firing. Size doesn't change the damage | 3 each + 1 splash r 1.5 (up to 60 on one target), cd 60 s | **NEW** (replaces Titanic Slam) · Ultimate Humungousaur's finger missiles (UA; Omniverse) |
| C | Primal Roar | 10-block cone (+2 a step): weakened and slowed; **new: mobs under 20 HP flee for 3 s** | 12, cd 8 s | TUNE · his roar *(recurring)* |
| Shift+C | Spike Burst | **NEW.** Bone spikes jut out of his back, shoulders and tail (model parts): r 3 (+1 a step) hit around him, and for 8 s they stay out: melee attackers take 3 thorns, +3 armour | 14, thorns 3, cd 15 s | **NEW** · spikes and plates grow on him as he grows; Ultimate's spiked shell |
| V | Grow | one size step bigger (x1.5 → x2.2 → x3.0); each grow pushes nearby creatures aside (no damage). **Hold V 1 s**: straight to full size | as now, cd 2 s | TUNE · growing on demand |
| Shift+V | Shrink | one step smaller. **Hold Shift+V**: straight back to base size | as now, cd 0.5 s | TUNE |
| Left-click | Heavy Fists | normal punch with his attack stat; from x2.2 on it cleaves (hits up to 2 creatures in front) | base attack 5 (+2 / +5 at steps) | TUNE |
| Right-click (empty hand) | Brace | hold: plants his feet, arms up. Frontal damage -60%, no knockback, frontal projectiles are stopped. Walking speed halved while held | max 4 s, then 3 s cd | **NEW** · holding up and catching huge things, taking blasts head-on *(recurring)* |

### Passives
- **Vaxasaurian Hide (KEEP):** 44 HP, armour 10 (+3 toughness) plus the hidden Resistance I, 60% knockback resistance, falls up to
  9 blocks shrugged off.
- **Size-Shift (TUNE):** each step: **+10% move damage** (was +20%, which broke the damage scale), +3 armour, +0.75 reach, +15% KB
  resistance, -8% speed, and **larger areas** (Ground Pound, Tail Club, Dino Leap, Roar and Spike Burst ranges above). At full
  size, Heavy Hurl lifts ravager-sized mobs, and the back plates show as bone spikes with a tail club (the current x3.0 model).
- **Giant's Footsteps (NEW):** at full size, sprinting gives a tiny camera shake to nearby players and tramples crops and tall
  grass. Flavour only, no damage.

### Weaknesses
- **Big and slow (KEEP):** x0.85 speed, -8% each step.
- **Huge target (TUNE):** at x3.0 he can't fit through doors and tunnels. **New:** Grow is refused if there isn't room above him
  (message "No room to grow"), so he can't suffocate himself inside a roof.
- **Hungry giant (NEW):** at x2.2 hunger drains 1.5x as fast, at x3.0 2x. Staying huge has a cost, so he shrinks between fights.
- **Rooted barrage:** he can't move during the Bone Missile Barrage, so he is easy to flank.

### Visuals / animation
- **Growth:** keep the current scale tween. Add a rendered expanding dust ring at the feet (one quad, not particles) and a short
  plate "click" animation as the back plates lengthen.
- **Spike Burst:** spikes are extra model cubes on the back/shoulders/tail that scale up from 0 in 4 ticks and back down when it
  ends. A single bone-white ring at the burst.
- **Ultimate shell (Barrage):** a separate model layer (grey-blue shell plates + spikes) that rises over 0.5 s and sinks back after.
  The fingers fold back into tube shapes (the hand cubes swap to a gatling-hand cube). Missiles are small bone-white projectile
  models with a short rendered trail. Small rendered blast rings on impact, no smoke clouds.
- **Brace:** a held pose, forearms crossed in front, feet wide. Blocked hits give a dull thud and a tiny screen shake.
- **Stampede:** head-down run pose. On a wall crash: a camera kick and one ring of block chips at the wall.
- **Heavy Hurl:** the thrown ground slab is a real block chunk model (matches the slab's blocks), spinning slowly in flight.

---

## Open questions
- **H1.** Titanic Slam goes (it duplicates Four Arms' Meteor Slam) and the Bone Missile Barrage becomes the ultimate. OK, or keep
  Titanic Slam somewhere (e.g. as the landing of Dino Leap at full size)?
- **H2.** Ultimate Humungousaur: one 3 s barrage (as above), or a **timed Ultimate form** (15 s, locked at x2.6 size, can't
  grow, left-click fires bone missiles at 5 each, ends with the barrage)? The timed form is closer to the show but bigger to build.
- **H3.** Size bonus to damage: +10% a step (keeps every move in the agreed bands) or keep +20% (bigger feels bigger, but normal
  moves reach ~21 at full size)?
- **H4.** A fourth "Colossal" step (x4.5, about 8 blocks tall, 20 s timer, then he shrinks back) to nod at the 60 ft giant, or keep
  the max at x3.0 so he still fits in normal terrain?

Sources (search snippets): [ben10fanfiction: Ultimate Humungousaur](https://ben10fanfiction.fandom.com/wiki/Ultimate_Humungousaur_(Jon%27s_Dimension)),
[Ben 10 Wiki: Evolved Vaxasaurian](https://ben10.fandom.com/wiki/Evolved_Vaxasaurian), [Giant Bomb: Humungousaur](https://giantbomb.com/wiki/Characters/Humungousaur).
