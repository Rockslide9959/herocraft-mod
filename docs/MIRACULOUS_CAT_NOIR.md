# Cat Noir (Plagg's Miraculous, the Ring) -- v0.16.6

Cat Noir is one of the three Miraculous kits. The Miraculous core (`com.projecthero.mod.miraculous`) handles the
Kwami, the jewel, the transformation, the shared transformed stats (armour, health, melee, speed, jump, no fall
damage), the 5-minute countdown and the HUD. This kit adds the six keys, the suit and the Baton.

All numbers are static finals in `CatNoirConfig`. Ability ids start with `cat_noir_`, and their lang keys are
`projecthero.miraculous.ability.<id>` / `.desc`.

## Moves

| Key | Move | Numbers | Cooldown |
|---|---|---|---|
| R | **Baton Strike** (`cat_noir_baton_strike`) | 3 swings (forehand, backhand, overhead smash) on ticks 3 / 8 / 13. Each does 9 damage to everything in a cone in front (reach 4, about 60 degrees each side). The last swing knocks back harder. | 1.5 s |
| Shift+R | **Low Sweep** (`cat_noir_low_sweep`) | 6 damage in a 3-block arc at leg height. Trips what it hits: a small pop, its path cleared, Slowness IV for 3 s. | 3 s |
| G | **Baton Extend** (`cat_noir_baton_extend`) | The staff shoots out up to 15 blocks along your look over 4 ticks and stops at the first creature or block. 14 damage, knockback 2.6, lift 0.45. | 4 s |
| Shift+G | **Baton Throw** (`cat_noir_baton_throw`) | Flies spinning at 1.5 blocks a tick for up to 22 blocks. 12 damage to the first creature it hits, then it comes back to your hand. A wall or the max range turns it back too. It is lost after 5 s and simply returns. | 5 s |
| X | **Pole Vault** (`cat_noir_pole_vault`) | The baton is planted (it needs ground within 4 blocks below). After 4 ticks you are launched 15 blocks straight up, with a little forward carry. The pole is drawn from the ground to your hands as it extends. | 6 s |
| Shift+X | **Rooftop Run** (`cat_noir_rooftop_run`, toggle) | +35% speed while sprinting. Against a wall you count as on a ladder, so you walk into the wall to climb and Sneak to cling (`CatNoirClimbMixin`). | 1 s toggle |
| Z | **Baton Spin** (`cat_noir_baton_spin`) | Lasts 2 s. 8 damage to everything within 4 blocks, and the same creature can be hit again every 0.75 s. Projectiles within 4 blocks that are coming at you are batted back and become yours. | 5 s |
| Shift+Z | **CATACLYSM** (`cat_noir_cataclysm`) | See below. | 20 s, only if it fizzled |
| V | **Cat Leap** (`cat_noir_cat_leap`) | A ballistic pounce of 12 blocks along your look. | 3 s |
| Shift+V | **Pounce Strike** (`cat_noir_pounce_strike`) | Leaps at the creature you look at (up to 24 blocks). 12 damage when you come within 2.2 blocks of it. Without a target it does nothing and has no cooldown. | 5 s |
| C | **Night Vision** (`cat_noir_night_vision`, toggle) | The Night Vision effect, kept topped up. Every creature within 20 blocks gets a green outline, which only you can see (the client glow mixins, `IronManHighlight.mayDecide`). | 1 s toggle |
| Shift+C | **Cat Reflexes** (`cat_noir_cat_reflexes`) | For 5 s, the next 3 hits that something deals to you are cancelled. Each one triggers a sidestep across the line of attack. This works through a `ServerLivingEntityEvents.ALLOW_DAMAGE` veto. Blows, shots and blasts count. Falls, fire, hunger and anything that bypasses invulnerability do not. | 15 s |

Bosses (300+ max health, or `TitanCombat.isBoss`) take at most 8% of their max health from one baton hit, and at most
15% from Cataclysm. They are never knocked about. Every hit follows `HeroTargets.canHarm`: no squadmates, no friendly
pets, and players only when PvP is on.

## CATACLYSM

- **The call.** The main hand crackles with dark destruction energy for 10 s (see **CATACLYSM visuals** below). The core's 5-minute countdown starts **when it is called**, because
  in the show calling the power is what spends the Miraculous.
- **One touch.** A punch on a block, a right-click on a block, or an attack on a creature.
  - **On a block:** the block and every block joined to it face-to-face within 4 blocks crumble to dust in a spreading
    wave. Each ring cracks and darkens over 6 ticks and then falls, one block further out every 5 ticks (about 1 s to
    the edge). The limit is 300 blocks, and nothing drops.
    - It follows the mod's block-damage rules: the `abilityTerrainDamage` config, the `mobGriefing` gamerule,
      `mayInteract` (spawn protection and claims) and loaded chunks only.
    - It never breaks fluids, unbreakables (hardness -1 or `#wither_immune`), or block entities, so a chest is never
      voided with its contents.
  - **On a creature:** its worn armour shatters first (mobs only; a player's armour is spared). Then it takes 40 damage
    and Wither III for 8 s.
- **Once per transformation.** After a touch Cataclysm is spent for the rest of the transformation (state flag
  `cn_cataclysm_spent`; v0.16.6, user: the baton no longer rots black). If the charge runs out untouched it fizzles, and it can be called again after 20 s. The countdown keeps
  running either way.

## CATACLYSM visuals (`CataclysmFx`, v0.16.6 rework)

Real geometry, not an enchantment-style arm overlay (the old `cataclysm_dark` / `cataclysm_swirl` wrap is gone).

- **The hand** (third person for every viewer, and first person). Churning dark matter clings to the fist only (an
  animated box over the last 4 pixels of the arm). At the open palm sits a ball of crackling darkness, billboarded
  to the camera:
  - a sickly green-black edge and a soft dark halo, with swirling near-black smoke wisps (8-frame animated sheet)
    over them;
  - black lightning arcs with a dark violet glow, drawn as segmented ribbons and re-forked every 2 ticks;
  - green and violet sparks, plus faint green-black motes drifting off (particles).
  - **Forming:** over the call animation the sparks pull in, the ball swells and it throws off a ring of smoke.
  - First person uses a compact version above the fist, so it never blots out the view.
- **On a block.** The server sends `CataclysmFxPayload` (the crumbling blocks). Each block blackens from the side
  facing the touch: dark veins race ahead of a blotchy black front, then grey fracture lines split it. These are
  decals on its open faces (8 stages of `cataclysm_corrode.png`), timed to the server's rings. When it falls it
  breaks into tumbling charcoal chunks (voxel debris) and black dust. The server no longer sends vanilla crack
  stages, and the block is removed without the vanilla stone-chip particles.
- **On a creature.** It turns dark and cracks for 3.5 s (`CataclysmTouchLayer`, on every living renderer). Its
  shattered armour bursts into grey chunks that blacken as they fall, and a dark surge bursts from the point of contact.
- **Sound:** a low sculk bloom surge, then the deepslate rumble and sand-like crumbling.
- Textures are made by `scratchpad/make_cataclysm_textures.js` (Node, no Python).

## Suit and visuals (`client/miraculous/catnoir`)

- **Suit.** The user's chosen skin (minecraftskins.com 23799210) cut out by `scratchpad/make_suit_skins.js`: the
  bodysuit, belt, bell, gloves, boots and the mask with green lenses. There are two versions, `suit_wide.png` and
  `suit_slim.png`, and each wearer gets the one matching their arm width.
  - It is drawn as an overlay a hair larger than the wearer, so the wearer's own face, hair and bare skin show through.
  - The wearer's jacket, sleeve and trouser layers are hidden. Their hat layer (their hair) is kept, redrawn by
    `OverlayStrips`.
- **Reveal.** The suit spreads texel by texel from the Ring hand over `MiraculousConfig.TRANSFORM_TICKS`, with the
  newest texels flashing green, and runs backwards on detransform. The ring itself is drawn by the core.
- **3D parts** (`parts.png`): cat ears that follow the head, a belt-tail of four segments that sways with walking and
  idle motion, and a gold bell.
- **The Baton** (`CatNoirBaton`). A vertex-built octagonal staff: a grip with the green paw, and four nested tubes per
  side with collars and caps.
  - It really telescopes: the thickest tube comes out first, then each thinner one slides out of the one before.
  - It sits collapsed on the lower back. It is in the hand for the strike, sweep, throw, pounce and catch, and whirling
    for the spin.
  - It is drawn in the world for Extend (up to 15 blocks) and the Vault pole (`CatNoirWorldFx`).
  - It has its own entity renderer when thrown, and a rotted texture after Cataclysm.
- **First person.** The suit sleeve is drawn on the arm. During baton moves and the Cataclysm charge, the main hand
  shows the bare gloved arm with the baton or the Cataclysm energy (`CatNoirItemInHandMixin`). In third person, the held
  item is hidden at those times.
- **Poses** (`CatNoirPose`, animation ids 40-55): three strike swings, the sweep crouch, the extend thrust, the
  throw and catch, the vault plant and ride, the spin, the leap, the pounce, a hand to the mask, the reflex guard,
  left and right dodges, the hand flung up for the Cataclysm call and then held up while charged, the touch thrust,
  and hand-over-hand wall climbing.

## Files

- Server: `miraculous/catnoir/` -- `CatNoir` (ids, client-safe state reads), `CatNoirKit`, `CatNoirAbilities`,
  `CatNoirCataclysm`, `CatNoirCombat`, `CatNoirConfig`, `CatNoirMod` (registration, damage veto, touch hooks, crumble
  clock), `entity/CatBatonEntity`, `entity/CatNoirEntityTypes`, and `mixin/CatNoirClimbMixin`.
- Client: `client/miraculous/catnoir/` plus five `client/mixin/CatNoir*Mixin` classes.
- Textures: `textures/entity/miraculous/cat_noir/`, regenerated by `scratchpad/gen_cat_noir_v0166.js`.
- Tests: `CatNoirGameTests` (14 tests).
