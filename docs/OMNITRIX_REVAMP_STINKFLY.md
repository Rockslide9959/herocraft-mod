# Omnitrix revamp proposal: Stinkfly

Status: **PROPOSAL, awaiting approval.** No code written. Same format as the Heatblast / Diamondhead / Wildmutt / Ghostfreak revamps:
R G Z X C V, each with a tap and a Shift variant; X = movement, V = utility/mode, the rest attacks; Z / Shift+Z the big moves.
No H or N moves. Flight stays on **double-tap jump** (no key for flying), with this session's auto take-off when transformed
mid-air and the new lean / bank / hover-bob flight poses (StinkflyFlightPoseMixin). Effects are posed models and rendered shapes,
with few particles.

Tags: **KEEP** = unchanged · **TUNE** = same move, new numbers or extra behaviour · **REWORK** = same slot, different move · **NEW**

Research note: the Ben 10 fandom wiki (and its Lepidopterran page on the aliens wiki) returned HTTP 402 and the BreezeWiki mirror
was blocked, so I could not fetch the canon pages. A web search of fan sites backed up the basics: flight with fast direction
changes, sticky goo, a stench / chemical spray, and **fragile wings** (with fire implied as a weakness). The episode details below
are **from memory** and marked *(memory)*. Moments I can't pin to an episode are marked *(recurring)*.

---

**In the show.** Stinkfly is a green insect from Lepidopterra with **four eyes on stalks**, two pairs of thin wings and a
**stinger** on his tail. He is Ben's first flier. In the 2005 series he fires **sticky goo from his eye stalks**; later designs
(Alien Force / Omniverse / the reboot) spit it from the mouth. The goo **glues enemies in place, to walls or to the ground** and
**gums up machines and drones** *(recurring)*. He gives off a foul **stench** and can release a toxic / stink gas *(recurring)*.
He **carries Gwen and Grandpa Max** out of danger and drops things on enemies from above *(recurring)*. His tail stinger jabs
*(recurring)*. His weak points are the **wings**: they're thin and fragile, and wet or damaged wings ground him *(memory: wet
wings grounding him is the fan-wiki weakness I remember; I can't name the episode)*. He backs away from fire, and in the reboot
his goo is **flammable** and goes up when lit *(memory, reboot)*. Episode moments I'm fairly sure of: his debut is in
*Washington B.C.* *(memory)*, and Kevin's mutant form in season 2 carries Stinkfly's wings *(memory, recurring)*. Lepidopterrans
also have natural predators whose fog neutralises their goo (Buglizards, Spitworms; fan-wiki lore, not used here).

### Current kit (v0.17.2)
- R Slime Shot (10, root 1.5 s, cd 0.8 s) · Shift+R Slime Net (6, roots r 2.75 for 3 s, cd 8 s)
- G Toxic Gas (cloud r 3.25 for 5 s, 2/pulse = 20, poison + nausea, cd 11 s) · Shift+G Stinger Stab (12 + Poison II, cd 2.5 s)
- X Wing Dash (cd 3 s) · Shift+X Stinger Lunge (14 to all in 8 blocks, poisons, cd 5 s)
- Z Dive Bomb (18 falling to 11, r 3.5, slows, cd 7 s) · Shift+Z Goo Storm ULT (r 6, 6 s, 4/s = 24, slows, cd 30 s)
- C Wing Gust (6, shoves, deflects projectiles, cd 6 s) · Shift+C Slime Barrage (5 globs, 4 each, cd 12 s)
- V Lift (carry the creature in front, < 150 max HP; V again drops) · Shift+V (empty)
- Attack: stinger jab (poisons) · Use (empty hand): goo spit · Double-tap jump: fly (auto take-off mid-air)
- Passive: 20 HP, no fall damage or fly-into-wall damage

The kit already covers flight, goo, gas, stinger and carrying. What's missing is the **sticky** side of the goo (it only roots
for a moment and never leaves anything behind) and the two show weaknesses (**wet / fragile wings, fire**). There's also no move
that fully **encases** someone, his signature "glued to the wall" image. Two moves overlap: Slime Barrage is a wider Slime Shot,
and Goo Storm is a bigger Toxic Gas. The revamp makes the goo **persist and combo**: it glues, it burns, and the gust pushes the
gas around. It gives him an encasing ultimate and fills Shift+V.

### Proposed kit

| Key | Move | What it does | Numbers | Tag / source |
|---|---|---|---|---|
| R | Slime Shot | goo glob: roots for 1.5 s, then slows. **New: a miss leaves a goo patch** on the block it hits (floor, wall or ceiling, 1.5 x 1.5): walking into it is like honey (heavy slow, no jumping), and a mob landing in it is stuck 1 s | 10, root 1.5 s, patch 8 s (max 6 patches), cd 0.8 s | TUNE · goo gluing feet to the ground *(recurring)* |
| Shift+R | Slime Net | a wide net of goo strands; everything under it is rooted. **New: a net that hits a wall pins the target to it** (it can't move or turn for the root time) | 6, r 2.75, root 3 s (boss 1 s), cd 8 s | TUNE · enemies glued to walls *(recurring)* |
| G | Stench Cloud | the toxic gas cloud ahead, **plus: mobs inside lose their target** and wander for 2 s after leaving it. Blown by Wing Gust (see C) | r 3.25, 5 s, 2/pulse = 20, poison + nausea; cd 11 s | TUNE · the stink *(recurring)* |
| Shift+G | Stinger Stab | tail jab under the body | 12 + Poison II 5 s, cd 2.5 s | KEEP · the stinger |
| X | Wing Dash | burst of speed where you look (in the air or on the ground) | as now, cd 3 s | KEEP |
| Shift+X | Stinger Lunge | dart forward stinger-first through everything in the path | 14, 8 blocks, poisons, cd 5 s | KEEP |
| Z | Dive Bomb | plunge and splash down; **new: leaves a 3-block goo pool** for 5 s (the honey-slow of R's patch) | 18 falling to 11, r 3.5, pool 5 s, cd 7 s | TUNE |
| Shift+Z | **Goo Cocoon** | ULT. He hovers and pumps a thick stream at the creature he looks at (up to 20 blocks) for 1.5 s, which **wraps it in a goo cocoon**. A cocooned mob can't move, attack or use items for 5 s (boss 1.5 s) and takes +25% damage. The stream's spray glues everything within 4 of it for 2 s. When the cocoon bursts it lets out a gas puff (8 + nausea 3 s, r 3) | stream 1.5 s; cocoon 5 s / boss 1.5 s; +25% taken; burst 8; cd 30 s | **REWORK** (replaces Goo Storm, which moves to Shift+C) · encasing foes in slime *(recurring)* |
| C | Wing Gust | blast of wind: shoves, deflects projectiles. **New: a gust that hits his own Stench Cloud or Goo Rain carries it 8 blocks** the way he looks | 6, cd 6 s | TUNE · his wings as a weapon *(recurring)* |
| Shift+C | **Goo Rain** | the old Goo Storm, now a smaller regular move: a green cloud above the spot you look at rains globs; what's under it is slowed, and patches (as R) are left where globs land | r 5, 4 s, 3/s = 12, slows, cd 16 s | **REWORK** (replaces Slime Barrage, which moves to held Use) |
| V | Lift | grab the creature in front in your legs and fly with it; V again drops it. **New: allies (players in your squad) can be carried too**, and a drop from 6+ blocks onto an enemy deals the fall as damage to both of them (the ally takes none) | < 150 max HP as now; drop-on 2 per block fallen, max 16; cd 3 s | TUNE · carrying Gwen and Max; dropping things on enemies *(recurring)* |
| Shift+V | **Goo Wall** | NEW. Spit a sheet of goo across the space in front (3 wide x 3 high, standing on the ground or spanning a gap). Projectiles stick in it. Mobs that walk in are glued 2 s, and it has 30 HP. Wing Gust doesn't break it; fire burns it away at once | 3 x 3, 12 s, 30 HP, glue 2 s, cd 15 s | **NEW** · sealing doors / blocking with goo *(recurring)* |
| Attack | Stinger Jab | stinger-tipped jab | as now (poison 2 s) | KEEP |
| Use (empty hand) | Goo Spit | tap: one quick spit. **New: hold = rapid spit** (Slime Barrage folded in): 4 globs a second while held, slight spread | tap 5; held 3 each, up to 3 s, then 6 s cd | TUNE · reboot rapid-fire spitting |
| Double-tap jump | Fly | as now (auto take-off mid-air, new lean / bank / bob poses) | – | KEEP |

### Passives
- **Light Flyer (KEEP):** no fall damage and no fly-into-wall damage.
- **Sticky Goo (NEW rule across the kit):** a creature standing in any goo patch or pool counts as **Gooed**. Gooed creatures
  can't sprint or jump. **Fire on a Gooed creature or a goo patch ignites it**: one burst of 6 in r 2 and 4 s burning, after
  which the goo is gone. Heatblast players, fire aspect and lava all trigger it. *(reboot: flammable goo)*
- **Stench (NEW):** passive animals (cows, pigs and so on) and villagers keep 6 blocks away from him. Wildmutt's *Choked Nose*
  (from that proposal) also triggers inside his gas. Cosmetic plus a little crowd control.
- **Compound Eyes (NEW, small):** four eyes mean nothing sneaks up on him. Hostile mobs **behind** him within 10 get a faint
  green outline (re-uses the existing glow-outline mixins). *(design: four eyes on stalks)*

### Weaknesses
- **Wet Wings (NEW):** in water he can't take off. After leaving water his wings are soaked for 5 s, so double-tap jump does
  nothing and he only glides. In **rain**, flight speed is -30% and Wing Dash goes half as far. Goo shot in rain roots for
  half as long. *(memory / fan wiki: wet wings ground him)*
- **Fragile Wings (NEW):** a single hit of 8+ damage while flying **clips his wings**: 2 s of forced gliding (no climbing), with
  a wing-flutter pose. 10 s internal cooldown so it can't be chained.
- **Fire (NEW):** +25% fire damage, and being on fire while flying works like Fragile Wings (glide only). *(fan wiki: backs
  away from fire)*

### Visuals / animation
- **Fewer particles.** The gas cloud and goo storm currently spray many dust / swirl particles per tick. Replace them with
  **rendered volumes**: 6-10 large soft-edged translucent green billboard puffs that slowly roll and swell, plus a dark core
  sphere. Only a handful of drips stay as particles.
- **Goo patches / pools:** flat glossy decal quads on floor, wall or ceiling (StinkflyGooFx's slime texture) with a slight
  wobble, fading at the end. A glued creature's feet get a stretched-goo string model.
- **Goo Cocoon:** a lumpy translucent shell model scaled to the target's hitbox, with the darker core texture showing the mob
  inside. It bulges in the last second, then splits into 3-4 shell pieces that fall away, with a single gas puff.
- **Goo Wall:** one translucent goo sheet (strands + knots, re-using the Slime Net's strand rendering) stretched across the gap,
  sagging in the middle. Stuck arrows stay visible in it.
- **Goo Cocoon cast pose:** hovering upright, head pushed forward, wings at full spread with a hard beat. The stream is a
  tapered tube model from his face to the target.
- **Wet Wings / clipped:** the wings drawn darker and drooping (half spread, slow twitch), plus a few drip particles. The
  flutter for a clipped wing is a quick uneven beat.
- **Hold-Use rapid spit:** a short repeating head-jerk clip. Each glob is the existing small slime blob.

---

## Open questions
- **S1.** Goo Storm moves from the ultimate down to Shift+C (as the smaller Goo Rain), Slime Barrage folds into held Use, and
  the new ultimate is **Goo Cocoon**. OK, or keep Goo Storm as the ultimate and put Goo Cocoon somewhere else?
- **S2.** Where does the goo come out: his **eyes** (2005 original; the current skin has his eyes on the face, no stalks) or
  his **mouth** (later series, and where the Slime Net comes out now)?
- **S3.** **Wet Wings**: should rain count (as -30% speed), or only actually being in water? Is no take-off for 5 s after
  swimming too harsh?
- **S4.** Lift on **players**: squad allies only (as proposed), or also enemy players in PvP (with a struggle-free after 3 s)?
