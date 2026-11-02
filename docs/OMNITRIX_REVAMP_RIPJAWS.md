# Omnitrix revamp proposal: Ripjaws

Status: **APPROVED and BUILT (v0.17.2).** User's answers: R1 "Drop tidal roar" (Megamouth replaces it), R2 "Do lure light i
suppose" (Shift+V Lure Light), R3 "Draw jaws as an effect" (rendered toothed jaw arcs; a 4-block version for Megamouth), R4
"keep drag under the same" (3 s, players too). Same format as the Heatblast / Diamondhead / Four Arms / Wildmutt /
Ghostfreak revamps: R G Z X C V, each with a tap and a Shift variant; X = movement, V = utility/mode, the rest attacks; Z / Shift+Z
the big moves. No H or N moves. Effects are posed models and rendered shapes (jaw arcs, rings, model parts), with few particles.

Tags: **KEEP** = unchanged · **TUNE** = same move, new numbers or extra behaviour · **REWORK** = same slot, different move · **NEW**

Research note: ben10.fandom returned HTTP 402 and the other fan pages either refused (403/404) or were fan-fiction wikis, so
**most of the "in the show" section is from memory**. Only *The Krakken* (season 1, episode 3, his debut) was confirmed by an
episode guide. Things marked *(recurring)* are general behaviour across his appearances. Fan-wiki search snippets list
"steel-bending jaws" and a "luminescent lure" for the species, but I can't place the lure in an episode; see open question R2.

---

## Ripjaws (Piscciss Volann)

**In the show.** A grey-blue fish-man with a pale belly, gills, fins and an enormous jaw full of teeth, which opens far wider than
his head. In water his legs are a fish tail and he is a fast, strong predator. On land he drags himself along and **dries out**
quickly, gasping until he gets wet again or times out. He debuts in *The Krakken*: Ben uses him in the lake against the creature
and against the poachers chasing her eggs, and the dry-out is the main problem. *(recurring)* His jaws **bite through metal**
(hulls, cables, robot plating) and he **grabs prey and drags it under**. He is used less as the series goes on because he is
so limited out of water (from memory). Later designs (Omniverse cameo in *A New Dawn* per a search snippet; Reboot) give him
**legs on land** that become the tail in water, and the Reboot version wears a **water-pack suit** for dry land. The mod already
does the legs/tail swap.

### Current kit (v0.17.2)
- R Bite (12, heals 1, cd 0.7 s) · Shift+R Jaw Lunge (16, first creature in the way, cd 3.5 s)
- G Tail Slam (ring, 12, knockback, cd 4.5 s) · Shift+G Fin Slash (spin, 10, slows, cd 6 s)
- X Water Dash (burst in water / hop on land, cd 1.5 s) · Shift+X Breach (leap out of water / back-flip on land, cd 5 s)
- Z Whirlpool in water (r 6, 5 s, 2 a pulse = 20, drains air) / Mud Splash on land (3, blinds) (cd 16 s) · Shift+Z Tidal Roar
  ULT (r 8, 24, weak/slow/shove, cd 30 s)
- C Feeding Frenzy (10 s: x1.3 damage, faster, bites heal 3, cd 30 s) · Shift+C Drench (half the hydration back, cd 45 s)
- V Water Bubble (30 s: dry land counts as rain, cd 60 s) · Shift+V (empty)
- Attack jaw punch (+3 in water) · Use on sand/gravel/dirt/clay/mud/snow: dig through it
- Passives: in water Dolphin's Grace + Conduit Power, never drowns, every move x1.25. **Drying out:** 2 min of dry land free,
  then 1 damage every 2 s with Slowness/Weakness; water refills in 15 s, rain refills slowly and stops the drying.

The drying-out mechanic and the water/land split are good and stay. What's thin: the **jaws** (his whole identity) are only R and
Shift+R; Tail Slam and Fin Slash overlap (two spin AoEs, and the v0.17.2 model lost its forearm fins); Mud Splash and Tidal Roar
aren't from the show; Shift+V is empty. The revamp puts the **jaws** in the centre: biting through metal, grabbing and dragging
prey under, and a giant unhinged chomp as the ultimate.

### Proposed kit

| Key | Move | What it does | Numbers | Tag / source |
|---|---|---|---|---|
| R | Bite | huge chomp at the creature ahead; heals. **New: Steel Jaws**, ignores 50% of armour and takes 8 durability off what the target wears | 12, heal 1 (3 in Frenzy), reach 3.5, cd 0.7 s | TUNE · *(recurring)* biting through metal |
| Shift+R | Jaw Lunge | launch jaws-first; the first creature in the way is bitten. In water it goes 2x as far | 16, ~6 blocks (12 in water), cd 3.5 s | KEEP |
| G | Drag Under | clamp onto the creature you look at (reach 4) and **hold it 3 s**. In water it is towed with you as you swim and drowns (air drained, 3 a second). On land he thrashes it side to side (3 x 4) then flings it 6 blocks. Bosses: 1 s hold, no tow | water 9 + drowning; land 12 + fling; cd 10 s | **REWORK** (replaces Tail Slam) · *(recurring)* grab-and-drag-under; *The Krakken* lake fights |
| Shift+G | Tail Sweep | 270-degree sweep with the tail (legs on land). Water: a shove wave that pushes 5 blocks. Land: 10 + knockback + slow 2 s | 12 water / 10 land, r 3.5, cd 6 s | **REWORK** (merges Tail Slam + Fin Slash) · the tail is the one fin he still has on the model |
| X | Water Dash | burst along your look in water; a lunging hop on land | as now, cd 1.5 s | KEEP |
| Shift+X | Breach | leap out of the water (on land a back-flip). **New: look down while airborne and press X again = Dive Strike**, a jaws-first plunge onto the spot below | strike 14 r 2.5, cd 5 s | TUNE · shark-style attack from below |
| Z | Whirlpool / Gnash | **water:** Whirlpool as now (pull + drown). **land:** Gnash replaces Mud Splash, a 1 s flurry of 5 bites in a short cone | whirl r 6, 5 s, 20; gnash 5 x 4 = 20, cone 3.5, cd 16 s | TUNE + **REWORK** (Mud Splash had no show basis) |
| Shift+Z | **Megamouth** | ULT. 0.75 s wind-up as his jaw unhinges, then one colossal chomp in a 4-block cone in front: ignores all armour; heals 1/4 of the damage dealt (max 8). In water it also **swallows the current** with a 6-block pull into the bite first. Each kill refills 30 s of hydration | 30 (x1.25 in water), cone 4 (pull 6 in water), heal 25% max 8, each kill +30 s hydration, cd 30 s | **REWORK** (replaces Tidal Roar) · his giant-jaw gape, the defining visual |
| C | Feeding Frenzy | 10 s: x1.3 damage, faster, bites heal 3. **New: Blood Scent**, creatures below 50% health within 24 are outlined red for him | as now, cd 30 s | TUNE · shark frenzy |
| Shift+C | Drench | splash yourself: half the hydration back | cd 45 s | KEEP |
| V | Water Bubble | 30 s: a water bubble round his head, dry land counts as rain | 30 s, cd 60 s | KEEP · the Reboot water-pack idea |
| Shift+V | **Lure Light** | NEW toggle: a glowing lure sprouts from his head. He gets Night Vision; within 12, fish/squid/axolotls swim to him, and hostile mobs that see the light **target him** (a taunt). While on, he dries 25% faster on land | r 12; toggle, 3 s cd after off | **NEW** · species lure from the fan wikis (unconfirmed, see R2) |
| Attack | Jaw Punch | as now (+3 in water) | as now | KEEP |
| Use (block) | Dig / **Bite Through** | as now on sand, gravel, dirt, clay, mud, snow. **New:** also bites through iron bars, chains, iron doors/trapdoors (drops them) | 1 block per use, 0.15 s | TUNE · *(recurring)* biting through metal |

Megamouth numbers, plainly: 30 damage in a 4-block cone (37.5 in water), armour ignored, heals 25% of the damage dealt up to
8 health, each kill refills 30 s of hydration, cooldown 30 s.

### Passives
- **Aquatic Predator (KEEP):** in water Dolphin's Grace, Conduit Power, never drowns, every move x1.25.
- **Tail and Legs (KEEP):** the existing visual swap: legs on land, the long tail in water.
- **Steel Jaws (NEW):** every bite (R, Lunge, Drag Under, Gnash, Megamouth) ignores 50% of armour; Megamouth all of it.
- **Blood in the Water (NEW):** while he is in water, any creature in water within 24 that is below full health is faintly
  outlined (Frenzy upgrades this to the red Blood Scent outline on land as well).

### Weaknesses
- **Drying Out (KEEP):** 2 min free on land, then 1 every 2 s with Slowness/Weakness; water refills in 15 s, rain slowly.
- **Heat dries faster (NEW):** in hot biomes (desert, badlands, savanna) he dries 1.5x as fast, in the Nether 3x; standing in
  fire adds 10 s of dryness a second.
- **Fish out of water (KEEP, made visible):** speed 0.9 on land as now; on land his X hop is shorter (as now).

### Visuals / animation
- **The jaw problem:** v0.17.1 replaced the model jaw with the user's skin face. Rather than bring the model back, bites draw a
  **rendered jaw arc**: two toothed crescent quads (upper and lower) that open in front of his face and snap shut, 6 ticks.
  Megamouth uses the same quads scaled to 4 blocks, opening over the 0.75 s wind-up. No particles beyond one splash.
- **Drag Under:** the held creature is pinned to a point in front of his face; on land a side-to-side thrash clip (head and torso
  yaw), then a throw.
- **Dive Strike:** a straight-down dive pose (arms forward, tail up), a flat splash ring on impact.
- **Lure Light:** a small model part (a stalk with a glowing bulb, emissive texture) on the head, gently bobbing.
- **Blood Scent:** the existing glow-outline path in red; no particles.
- **Gnash:** a fast head-snap loop, the jaw arcs firing 5 times.

---

## Open questions
- **R1.** Tidal Roar goes away (replaced by Megamouth). OK, or keep a roar somewhere (e.g. fold its weaken/slow into
  Megamouth's wind-up)?
- **R2.** The lure light isn't confirmed in an episode (fan-wiki species trait only). Keep Lure Light on Shift+V, or use the slot
  for something else, e.g. **Geyser**: shoot up a water column under you (refills hydration, launches nearby foes, 8)?
- **R3.** Should the bite show the rendered jaw arcs, or would you rather bring back a real hinged jaw on the model?
- **R4.** Drag Under in PvP: a 3 s hold underwater is strong. Shorten to 1.5 s against players?
