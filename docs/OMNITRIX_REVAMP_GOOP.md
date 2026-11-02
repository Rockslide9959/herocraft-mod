# Omnitrix revamp: Goop (v0.17.2)

Status: **BUILT** (v0.17.2). Code: `omnitrix/alien/goop/GoopKit.java`, `client/omnitrix/alien/GoopClient.java`,
`client/omnitrix/alien/goop/GoopFx.java`. Tests: `OmnitrixGoopGameTests` (+ the basic ones in `OmnitrixRecalCGameTests`).

User: "can you work on revamps for the rest of the alien force aliens please, look at their powers and abilities in the show and
do research and make them good please" / "delete all the alien models and only use the skins i provided please no extra things
untill i say". No Ultimate form ("dont add ultimate aliens yet").

## In the show

A Polymorph from Viscosia (Alien Force debut). His body is acidic green slime, held in shape by an **anti-gravity projector**, the
little saucer floating over him. He stretches limbs, **engulfs** enemies, sprays and spits **acid** that eats through metal
(locks, bars, doors), **squeezes** through vents and pipes and under doors, and reforms after being split or splattered. Knock the
projector away and he is a **helpless puddle** until he gets back to it. Ben has also thrown the projector ahead and poured after
it. Physical blows mostly pass through him. *(Fan wikis and episode memory; ben10.fandom was not reachable. Treat episode
details as unverified.)*

Upgrade is the other liquid alien: he merges with machines. Goop is **acid and shape** instead.

## Kit

| Key | Move | What it does | Numbers |
|---|---|---|---|
| R | Acid Spit | a glob of acid | 7, cd 1 s |
| Shift+R | Acid Glob | lobbed glob, splash + an acid puddle | 12 in r 2.5; puddle 8 s, 2 / 0.5 s, slowed; cd 10 s |
| G | Slime Whip | the arm stretches 8 blocks and lashes; small creatures dragged in | 13, drag 3 blocks, cd 4 s |
| Shift+G | Engulf / Spit Out | swallows a creature (not bosses / big things) for 3 s; Shift+G again spits it out early | 4 / 0.75 s (16); spit: thrown ~6 blocks, +6; cd 15 s |
| X | Slime Bounce | springy leap, then two automatic rebounds, each lower; sneak to stop | cd 1.5 s |
| Shift+X (hold) | Squeeze | while held (6 s max): 0.35x, through one-block gaps, climbs walls, +20% speed | cd 3 s from the release |
| Z | Acid Spray | 1.5 s of acid from both hands, steered by the look, 6-block 50 deg cone | 3.5 / 0.5 s (14), Weakness 5 s; cd 7 s |
| Shift+Z | **Acid Tide** (ULT) | becomes a wave: surges 10 blocks in 0.5 s, untouchable, carries what it hits; an acid lake where it breaks | 45 to each creature; lake r 6, 6 s, 5 / 0.5 s, slowed; cd 60 s |
| C | Slime Slam | splash-down round him | 17 in r 4, pushed, slowed 3 s; cd 10 s |
| Shift+C | Goo Cannon | huge glob that bursts | up to 18 in r 3; cd 13 s |
| V | Reform | pulls himself together; refused at full health with nothing to clear | +8 HP, harmful effects removed; cd 20 s |
| Shift+V | Projector Fling | throws the projector up to 16 blocks; his body pours after it as a puddle (untouchable) and reforms there | cd 8 s |
| Attack | Corrosive Touch | every punch burns | +2 |
| Use (empty hand, on a block) | Dissolve | acid eats the block for 10 s, then it comes back | hardness 5 or less (iron bars yes, obsidian no), no block entities / doors / beds; abilityTerrainDamage + build rights; cd 1 s |
| Sneak (in the air) | Slime Chute | flattens into a sheet: slow fall while held | fall capped at 0.14 / tick |

Damage check: quick 7 (Spit), 2 (Touch); normal 12-17 (Glob, Whip, Spray 14, Slam); big 18 (Cannon); ult 45 + lake.

### Passives
- **Polymorph body:** physical blows deal half; no fall damage; never suffocates in walls.
- **Acid Skin:** whatever hits him in melee takes 2 acid back.
- Hidden Resistance I (every alien); armour 2 as before. 26 health.

### Weaknesses
- **The projector:** a hit of 10+ (before resistances) or any lightning knocks it off. He is a puddle (0.45x, -70% speed, no moves)
  until he flows back over it; it floats back by itself after **10 s**. HUD row "Projector" shows the countdown.
- **Heat:** fire damage x1.5.
- **Squeezed thin:** physical blows land in full while squeezed.

## Look

Only the player model in the user's Goop skin (`textures/entity/omnitrix/goop.png`, converted from the 4-bit palette PNG to RGBA;
its clear pixels stay clear). The body is drawn on the translucent entity type (alpha 225, 150 as a puddle). **Removed:** the
floating projector saucer and its anti-gravity cone over his head, the stretched-arm boxes of Slime Whip, the drip particles. The
only projector drawn is the one lying on the ground (knocked off / flung), since that is the thing you have to reach.
Moves are rendered effects: QuirkFx ribbons (whip, fling), cones (spray), rings / pool decals (slam, splashes, tide, reform),
the acid blobs and puddles in `GoopFx`, QuirkCamera shakes on the slam, cannon and tide.
