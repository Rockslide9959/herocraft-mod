# Multimouse -- the Mouse Miraculous (v0.17.1)

Kwami **Mullo** (Kwami of Multiplication; `Kwami.MULLO`, id `mullo`, colour `0xE0559E`), jewel the **Mouse Pendant**
(`mouse_pendant`, in the Miraculous Box and the first-spawn Miraculous calling), hero key `multimouse`. The core is
`MIRACULOUS_REFERENCE.md`; this is the kit. The approved draft is `MULTIMOUSE_DRAFT.md`, with the user's three answers:
10 copies (not 7), a new Cheese Wedge food, and Gnaw chews stone too.

- Transform: `Mullo, multiply!` (or just `Multiply!`); change back: `Divide!` (or `Mullo, divide!`). The normal short
  transformation; the suit's reveal reads `Multimouse.transformTicks` (= `Miraculous.formTicks`), the hook a long
  show-style sequence plugs into later.
- Food: the **Cheese Wedge** (`cheese_wedge`, shapeless milk bucket + sugar -> 4, the bucket comes back; players can eat
  it: 3 hunger, fast); 10 of any other food also works.
- Transformed: the shared Miraculous stats, plus the passives below.

## Code

Server `com.projecthero.mod.miraculous.multimouse`: `Multimouse` (synced `mm_*` keys, pose ids 160..174, client-safe
reads), `MultimouseConfig` (every number), `MultimouseKit`, `MultimouseMoves` (11 moves, the passives, her size, the
tick), `Multitude` (the special), `MultimouseMod`; `entity/MiniMouseEntity` (the copies), `entity/MultimouseEntityTypes`.
Core hook added: `KwamiKit.modifyFallDamage` (default the core's half).

Client `com.projecthero.mod.client.miraculous.multimouse`: `MultimouseClient`, `MultimouseLayer`, `MultimouseRender`
(suit + reveal frames, the ears / rope belt / tail as lit boxes, the worn pendant), `MultimousePose`,
`MultimouseWorldFx` (the rope, the snares, Mouse Hearing's x-ray rings), `MultimouseHud` (the Multitude / Scurry card),
`MiniMouseRenderer`; mixins `MultimouseArmorMixin`, `MultimouseFirstPersonArmMixin`.

Size (Scurry, Multitude) is the vanilla `generic.scale` attribute: one transient modifier reconciled every tick from
the synced state and cleared on detransform, death, logout, dimension change and revoke (`onDetransform`). Growing back
waits until there is room for her full size.

## The twelve moves

| Key | Move | Shift version |
|---|---|---|
| R | **Rope Lash**: Cat Noir's three-press combo, 8 a swing, 5-block cone, the third knocks back; 1.5 s | **Lasso Yank**: aimed 16 blocks, 6 + 1.5 s stun, yanked to her feet (bosses slowed); 8 s |
| G | **Rope Twirl**: 4 s, 4 every 0.5 s within 3.5 blocks, projectiles from any side reversed; 10 s | **Skipping Trip**: 6-block front arc, 6 + tripped 2 s (a stun; bosses slowed); 8 s |
| X | **Rope Swing**: hooks the aimed block (30), pulls / swings her there, safe landing; 3 s | **Scurry**: a third of her size, +40% speed, up to 20 s (toggle); 5 s after |
| Z | **Mouse Swarm**: 3 copies dart at the target for 6 s, 2 a bite; 14 s | **MULTITUDE** (below; starts the countdown); 2 min |
| V | **Mouse Hearing** (toggle): creatures within 32 as x-ray rings pulsing with their footsteps, her client only | **Gnaw**: 1x2 hole up to 3 deep through any breakable block, stone included; 4 s |
| C | **Rope Bind**: aimed 14, rooted + no attacking 4 s (bosses slowed); 12 s | **Mouse Trap**: a snare (max 3, 2 min life), first mob in: snared 5 s + 8; 1 s |

Gnaw follows CATACLYSM's block rules: the terrain-damage config and `mobGriefing`, `mayBuild` / `mayInteract` (spawn
protection, claims), the world border; never air, fluids, block entities, `#wither_immune` (bedrock, barriers, portals...)
or anything with negative hardness. A column stops at the first block it cannot chew. The blocks drop as if mined.

Bosses (300+ max health or `TitanCombat.isBoss`) take at most 8% of their max health a hit and are never yanked, rooted,
tripped or stunned.

## Passives

- **Light on her feet**: an ordinary fall hurts a quarter (75% less) and never kills (`modifyFallDamage`).
- **Quiet as a mouse**: crouching, mobs targeting her more than 6 blocks away lose track (target suppressed).
- **Small** (Scurry / Multitude): 35% of mob hits and projectiles miss her.

## Multitude

Shift+Z: 10 `MiniMouseEntity` copies (6 HP, 3 a bite, scale 0.25, her skin + the suit + the 3D extras) and she shrinks
to 0.25. 60 s. The copies follow her and go for what she hits (last hurt mob within 5 s), what she looks at (if hostile),
else the nearest threat within 12. Her own moves do half damage. Bites are `playerAttack(owner)`, so a boss's threat
table counts them for her. Killed, a copy pops in pink sparkle; the HUD shows the count. Shift+Z again (never on the
cooldown) or the timer: the survivors run back (up to 1 s), then a shockwave within 6 blocks: knockback + 2 per copy back.
Detransforming / death / logout drop the copies at once (no shockwave).

## Assets

Suit `textures/entity/miraculous/multimouse/suit_{wide,slim}.png`, Mullo `geo/miraculous/mullo.geo.json` +
`textures/entity/miraculous/mullo.png`, clips `animation.kwami.mullo.*` (Sass's, re-timed for a mouse tail:
`scratchpad/multimouse/anim_mullo.js`), icons `textures/item/mouse_pendant.png` and `cheese_wedge.png`
(`scratchpad/multimouse/gen_cheese_wedge.js`). Lang inserted by `scratchpad/multimouse/lang_multimouse.js`.
Screenshots `docs/screenshots/v0171/multimouse_*.png` (harness `scratchpad/MultimouseHarness.v0171.java.txt`).
