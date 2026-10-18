# Carapace (Wayzz, the Turtle Miraculous) — v0.16.6

The Carapace kit for the Miraculous core (`docs/MIRACULOUS_REFERENCE.md`). The core owns the box, the Turtle Bracelet,
the Kwami, the transformation, the 3-minute countdown and the shared transformed stats (+12 armour, +6 toughness, +10 max
health, +7 melee, Regeneration I, +25% speed, +35% jump, no fall damage). This kit owns the six keys, the Shield, the
green energy shell and the suit.

Code: `com.projecthero.mod.miraculous.carapace` (server), `com.projecthero.mod.client.miraculous.carapace` (client).
Every number is a static final in `CarapaceConfig`. Ability ids start `carapace_`; pose ids 100..111.

## The keys

| Key | Move | Numbers | Shift version | Numbers |
|---|---|---|---|---|
| R | **Shield Bash** — charge behind the Shield | 6 blocks along the look (1 block/tick); the first thing within 1.6 of the shield's face takes 12 dmg, knockback 2.2 + lift 0.45, and the charge stops; 5 s | **Shield Throw** — spinning, ricochets | first target under the crosshair (7° aim assist, 20 range), then the nearest unhit one in sight within 10, up to 3 targets, 10 dmg each, 1.5 blocks/tick; a wall sends it home; refused while it is out; 6 s cooldown from the catch |
| G | **Shield Slam** — into the ground | 5-block shockwave: 10 dmg, lift 0.35, 1.5 s stun (bosses: Slowness III 1.5 s); 8 s | **Taunt** | monsters within 16 (not bosses) target him for 6 s (re-applied every 0.5 s); he takes 30% less damage meanwhile; 20 s |
| X | **Shield Surf** (toggle) — ride the Shield like a sled | client-simulated: hold forward to glide along the look at 0.6 blocks/tick (ice 0.85), floats on water (Shift sinks), +0.6 step height; moves that need the shield take it off the ground; 2 s cooldown after it ends | **Shell Roll** | tuck and roll 10 blocks (0.9 blocks/tick); no damage at all while rolling (+0.2 s); 8 s |
| Z | **Shell Guard** (held) | all damage from the front (within ~66° of the look) is blocked, melee attackers within 5 shoved back 0.8, projectiles in front within 2.6 sent back at their shooter (now his); drops by itself after 10 s; 3 s cooldown from the release | **SHELTER** (special) | a dome, radius 6, round where he stands for 30 s: creatures he may harm (never him or a squadmate) are pushed out (bosses and players only shoved), projectiles from outside stop at the shell, he and squadmates inside get Regeneration II; **starts the countdown**; 2 min |
| V | **Shell Barrier** | a 5x3 energy wall 3 blocks ahead across the look for 10 s; creatures he may harm can't cross it (pushed back to the side they came from), projectiles stop at it; 20 s | **Protect** | a bubble on the player he looks at within 24 (a squadmate or anyone not hostile), else himself: Absorption 20 (10 hearts) for 15 s; the bubble pops when the absorption is used up; 30 s |
| C | **Steadfast** | 8 s of knockback immunity (+1.0 knockback resistance) and Resistance II; 20 s | **Tidal Swim** | 60 s of Water Breathing, Dolphin's Grace and +1.0 water movement efficiency; 90 s |

Bosses (300+ max health or `TitanCombat.isBoss`) take at most 8% of their max health a hit, are never knocked back,
stunned (a short slow instead) or taunted. Every aimed hit follows `HeroTargets.canHarm`.

## Synced state

All in `MiraculousState.vals` / `vecs` with the `cp_` prefix (`Carapace`): the dome centre and end, the wall's bottom
centre / facing / end, the Protect target id and end, the guard / surf / roll / bash / taunt / buff timers, the thrown
shield flag. Every client draws the dome, the wall and the bubble from these (`CarapaceWorldFx`, hex texture
`hex.png`, emissive translucent). `endAll` (detransform, logout, death, dimension change) drops all `cp_` values, the
attribute modifiers (surf step, Steadfast, Tidal), the Protect absorption on another player, and the thrown shield.

## The Shield

`ShieldThrowEntity` (never saved) while thrown; otherwise drawn by `CarapaceLayer`: on his back, on his left forearm
during moves, square in front of the chest for Shell Guard / the Bash, flat under his feet while surfing, big over the
curled back while rolling. It is a real mesh (`CarapaceRender.renderShieldModel`): a disc of radius 7.5 px, domed 2.6 px
in front (rings of quads, the turtle-shell texture projected flat), a rim band and a flat back with a strap.
`shield.png` and `hex.png` are painted by `scratchpad/gen_carapace_v0166.js`.

## The suit

The user's chosen skin (minecraftskins.com 19407493 "carapace"), cut to suit-only (`scratchpad/make_suit_skins.js`): the
face's skin, cap and hair are transparent; the dark mask with yellow-green lenses, the green shell hood (hat layer) and
the suit stay. `suit.png` (slim) / `suit_wide.png`, picked by `PlayerSkin.Model`, drawn over the wearer's own skin with
the per-texel reveal (from the chest, a green light at its edge). **Not bulky** (user): the base layer is +0.02 off the
body, the second layer at vanilla's own +0.25 / +0.5 (a second model, nothing stacked). The wearer's own second layer,
hat included, is hidden while the suit shows (the reveal redraws it texel by texel).

## Shared-file hooks this kit added

- `projecthero.client.mixins.json`: `CarapaceFirstPersonArmMixin` (the sleeve in first person),
  `CarapaceShieldHandMixin` (the Shield in the off hand's place in first person), `CarapaceArmorMixin` (no worn armour
  over the suit), `CarapaceHeldItemMixin` (no off-hand item over the Shield on his arm).
- `en_us.json`: the block after `projecthero.guide.blue_beetle`.
- gametest `fabric.mod.json`: `CarapaceGameTests` (13 tests).
