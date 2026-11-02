# Queen Bee -- the Bee Miraculous (v0.17.2)

Kwami **Pollen** (Kwami of Subjection; `Kwami.POLLEN`, id `pollen`, colour `0xF5C518`), jewel the **Bee Comb**
(`bee_comb`, in the Miraculous Box and the first-spawn Miraculous calling), hero key `queen_bee`. The core is
`MIRACULOUS_REFERENCE.md`; this is the kit. Layout approved by the user 2026-11-02 (damage on the v0.17.2 scale).

- Transform: `Pollen, buzz on!` (or just `Buzz on!`); change back: `Buzz off!` (or `Pollen, buzz off!`). The normal short
  transformation (no long sequence yet: `MiraculousSequence.of(POLLEN)` is null, so the full phrase plays the short one).
- Food: **honeycomb** (vanilla); 10 of any other food also works.
- Transformed: the shared Miraculous stats, plus the buzzing glide.

## Code

Server `com.projecthero.mod.miraculous.queenbee`: `QueenBee` (synced `qb_*` keys, pose ids 180..194, client-safe
reads), `QueenBeeConfig` (every number), `QueenBeeKit`, `QueenBeeMoves` (the twelve moves, Venom, the glide, the tick),
`QueenBeeMod` (the kit + Venom on a plain punch through `AttackEntityCallback`).

Client `com.projecthero.mod.client.miraculous.queenbee`: `QueenBeeClient` (layer, world FX, the glide's fall cap),
`QueenBeeLayer` (suit + the top at her hip / in hand / overhead / before her), `QueenBeeRender` (suit + reveal frames from
the comb downward, the spinning top and the Bee Comb as lit boxes), `QueenBeePose`, `QueenBeeWorldFx` (the string, the
thrown top, the hovering top and its stingers, the tangle wraps, Venom's paralysis rings), `QueenBeeHiveSense` (gold
outlines through `ViperionHeatGlowMixin` / `ViperionHeatColorMixin`); mixins `QueenBeeFirstPersonArmMixin`,
`QueenBeeArmorMixin`.

## The suit

The user's skin (minecraftskins.com 23083151, "Queen Bee From Miraculous LB", a SLIM skin) cut to the suit only by
`scratchpad/queen_bee/gen_queen_bee_suit.js` (user: "remove the hair from the suit and the regular skin so the player
skin shows"): the whole head goes except a yellow mask from the skin's own palette on face rows 11-14 with eye holes on
rows 12-13; the ponytail down the back goes (body back rows 20-24 re-drawn from the front, jacket strands cleared).
`suit_slim.png` is as drawn, `suit_wide.png` is `T.convertArms` 3 -> 4 px. The wearer's hat layer (their hair) stays,
their jacket / sleeves / trousers hide under the suit (as Viperion).

## The twelve moves

| Key | Move | Shift version |
|---|---|---|
| R | **Top Strike**: three-press combo on the string, 7 / 7 / 9, 5-block cone, the third knocks back; 1.5 s | **Top Throw**: 20 blocks, 14, ricochets to the next within 8 (3 in all), flies home; 6 s |
| G | **Spinning Shield**: 4 s, projectiles turned back, 4 every 0.5 s within 3; 10 s | **Top Tangle**: aimed 14, 6 + rooted / no attacking 3 s (bosses slowed); 10 s |
| X | **Top Swing**: hook the aimed block (30), pull / swing there, safe landing; 3 s | **Top Copter**: flight up to 15 s (toggle, landing ends it, safe landing); 8 s after |
| Z | **Stinger Dive**: leap, dive, 17 within 4 + knock-up, safe landing; 9 s | **VENOM**: armed 10 s, next hit (move or punch) 40 + paralysed 10 s; again = touch in front; bosses 8% + slowed 3 s; starts the countdown; 2 min |
| V | **Hive Sense** (toggle): hostiles within 32 glow gold through walls, her client only | **Queen's Command**: hostile mobs within 10 drop their target 5 s, allies (her pets, players she can't hurt) Strength I 10 s; 20 s |
| C | **Sting**: lunge, first creature in 2.6 takes 13 + Poison 3 s, safe landing; 4 s | **Stinger Volley**: the top hovers over her shoulder, 6 stingers at the aim, 6 + Poison 2 s each; 12 s |

Bosses (300+ max health or `TitanCombat.isBoss`) take at most 8% of their max health a hit and are never rooted or
paralysed.

## Passive: the buzzing glide

Holding jump in the air (not flying, no elytra, not in water) her own client caps the fall at 0.12 blocks a tick; the
server forgives fall distance while she drops slower than 0.2 a tick.

## Assets

Suit `textures/entity/miraculous/queen_bee/suit_{slim,wide}.png`, Pollen `geo/miraculous/pollen.geo.json` +
`textures/entity/miraculous/pollen.png` (`scratchpad/queen_bee/gen_pollen.js`: Nooroo's bone set, so his six clips drive
her, appended as `animation.kwami.pollen.*` by `scratchpad/queen_bee/anim_pollen.js`), icon `textures/item/bee_comb.png`.
Lang inserted by `scratchpad/queen_bee/lang_queen_bee.js`. Guide chapter `CH_QUEEN_BEE` (63, after the Stands;
`CHAPTER_POWER_BASE` 64) with `glide` and `venom` sections. Tests: `QueenBeeGameTests`.
