# Viperion -- the Snake Miraculous (v0.16.7)

Kwami **Sass** (Kwami of Intuition; `Kwami.SASS`, id `sass`, colour `0x2FB7B0`), jewel the **Snake Bracelet**
(`snake_bracelet`, in the Miraculous Box and the first-spawn Miraculous calling), hero key `viperion`. The core
(grant, the spoken phrases, the countdown, the hungry Kwami, the shared passives, the HUD) is
`MIRACULOUS_REFERENCE.md`; this is the kit.

- Transform: `Sass, scales slither!` (or just `Scales slither!`); change back: `Scales rest!` (or `Sass, scales rest!`).
- Food: **eggs** (`Items.EGG`; right-clicking with an egg while Sass is hungry feeds him, it is not thrown); 10 of any
  other food also works (22 s, the egg 10 s).
- Transformed: the shared Miraculous stats, plus **immunity to poison** (the effect is removed every tick).
- The bracelet: round the RIGHT wrist, green and black scales, the head biting the tail, gold eyes (third person in
  `MiraculousJewelLayer` -> `ViperionRender.renderBracelet`, first person `ViperionFirstPersonArmMixin`).
- The Lyre (a harp that is also a club, lit boxes in `ViperionRender.renderLyre`): across his back, in his right hand
  during a move (first person too: `ViperionItemInHandMixin` draws the bare arm, the mixin puts the Lyre in the fist),
  spinning before him for the String Shield.

## Code

Server `com.projecthero.mod.miraculous.viperion`: `Viperion` (synced `vp_*` keys, pose ids 140..152, client-safe reads),
`ViperionConfig` (every number), `ViperionKit`, `ViperionMoves` (11 moves + the tick), `SecondChance` (the special), `TimeRewind` (its timer snapshots),
`ViperionMod` (kit, `ALLOW_DEATH` auto-rewind, server tick). `mixin/ViperionDashPoseMixin` holds the swim / crawl pose
while slithering (both sides, from the synced state). `CityLevelChunkMixin` also offers block changes to
`SecondChance.onBlockChanged`.

Client `com.projecthero.mod.client.miraculous.viperion`: `ViperionClient`, `ViperionLayer` (suit + Lyre), `ViperionRender`
(suit, reveal frames, Lyre, bracelet), `ViperionPose`, `ViperionHud` (Second Chance ring + flashes), `ViperionHeatSense`,
`ViperionFirstPerson`; mixins `ViperionFirstPersonArmMixin`, `ViperionArmorMixin`, `ViperionItemInHandMixin`,
`ViperionItemInHandLayerMixin`, `ViperionHeatGlowMixin`, `ViperionHeatColorMixin`.

## The twelve moves

| Key | Move | Shift version |
|---|---|---|
| R | **Lyre Strike** (user: as Cat Noir's Baton Strike, pressed 3 times): each press one swing -- forehand, backhand, overhead finisher (poses 140 / 153 / 154) -- landing 3 ticks after the press on everything in a 4-block cone (cos 0.5): 9 a swing, knockback 0.4, finisher 1.0 + lift; next press within 20 ticks (sooner than 5 is queued) or it starts over; 1.5 s cooldown from the third | **Lyre Sweep**: 4.5-block wide arc, 12 damage + knockback; 5 s |
| G | **Sonic Chord**: 12-block cone (about 32 degrees), 10 damage, knockback 1.8; 6 s | **Resonance**: 6-block ring, 4 damage + 2 s stun (bosses slowed); 15 s |
| X | **Serpent Dash**: 18 blocks (user, was 12) at 1.2 b/t, swim / crawl pose (0.6 high) while it runs; when it ends nothing brakes him (user: the last push carries on); safe landing; 5 s | **Coil Leap**: forward ~17.5 blocks (user, was 14) and up (0.95); never fall damage on the landing (the core's safe landing, re-armed while airborne, plus the fall distance reset every tick until he is down); 6 s |
| Z | **Venom Strike**: 4.5-block jab, 6 damage + Poison III 8 s; 5 s | **SECOND CHANCE** (below; starts the countdown); 120 s |
| V | **Constrict**: aimed (14 blocks, 7-degree assist): rooted 4 s, 2.5 damage every 10 ticks (bosses: slowed, capped); 12 s | **String Shield**: 6 s, front hits cancelled, projectiles from the front reversed; 15 s |
| C | **Heat Sense** (toggle): everything living within 32 blocks glows through walls for him only (hostile red, players yellow, others orange) | **Hiss**: mobs within 10 flee 5 s (never bosses, players or his pets); 20 s |

Bosses (300+ max health or `TitanCombat.isBoss`) take at most 8% of their max health a hit and are never knocked back,
rooted, stunned or scared. Both X moves are in `MiraculousConfig.SAFE_LANDING_MOVES`.

## Second Chance

First Shift+Z: a checkpoint (green clock-face ripple, bell + chime, HUD ring) and the Miraculous countdown. For **90 s**
(user) Shift+Z again rewinds (never on the cooldown, 20 ticks between presses); **3 rewinds**, then it is spent.
Rewound, within **32 blocks** of the checkpoint:

- him: position, rotation, health, absorption, hunger / saturation, effects, fire, air (not the inventory);
- squadmates who were there: the same -- other players are never moved;
- **time** (v0.16.7, user: "as if time were rewound", `TimeRewind`): the checkpoint stores its game time and copies of
  his and those squadmates' `MiraculousState` / `QuirkState`; a rewind shifts every absolute time in them by
  `now - checkpointTime`: the Miraculous countdown (`countdownEnd`), every cooldown (`abilityReadyAt`; ready then = ready
  now), every kit timer in `vals` named `*_until` / `*_at` / `*_since` / `*_end` (one started since is removed; toggles,
  counts and ids stay), the Kwami's hunger + meal, a Quirk's cooldowns / overload / meter. The window restarts (a full
  90 s from the rewind); the special's own cooldown goes back to its checkpoint value too. His running move sessions end;
- every creature that was there (up to 96, nearest first): position, rotation, health, effects, fire, air (no deeper AI
  state / boss move timers); one killed since is
  rebuilt from its saved NBT (never a boss); hostile mobs that appeared since, and new item drops (no thrower) / XP orbs,
  are removed;
- blocks broken / placed / changed since (first old state per spot, up to 8192) are put back at once.

A **killing blow** while a checkpoint is live rewinds instead (`ALLOW_DEATH`, one rewind). `/kill` (`GENERIC_KILL`) and
other invulnerability-bypassing damage still kill, except falling out of the world (that is rewound).
The checkpoint lives in `SecondChance` (server); the HUD reads `vp_cp_until`, `vp_cp_left`, `vp_rewind_at`,
`vp_checkpoint_at`. Detransforming, death, logout or a dimension change drop it.

## Assets

- Suit: `scratchpad/viperion/gen_viperion_suit.js` cuts the user's skin (`scratchpad/user_skins_v0167/viperion.png`,
  minecraftskins.com 20294512, wide) to the suit: head and skin texels go, the skin's own visor (face rows 13-15,
  including the two yellow texels under the eyes, user) is kept, a brow row and eye holes on the wearer's eye row (12)
  are painted from the visor's palette; `suit_wide.png` as drawn, `suit_slim.png` via `convertArms`.
- Sass: `scratchpad/viperion/gen_sass.js` (copy of `lib_concept_models.js` beside it) writes `geo/miraculous/sass.geo.json`,
  `textures/entity/miraculous/sass.png`, the six `animation.kwami.sass.*` clips (with a swaying `tail` bone; the leg bones
  are empty) and the Snake Bracelet item model / texture.

## Tests

`ViperionGameTests` (24): one per move (Lyre Strike: three presses with Cat Noir's numbers, the window lapsing; the dash
keeping its momentum; Coil Leap's fall forgotten + no fall damage), the checkpoint + manual rewind (him + a mob), the automatic rewind on a lethal
hit, three rewinds then spent, the 90 s window closing, the timers turned back (countdown, a cooldown, a kit timer, the
window restarting) and a squadmate's countdown + cooldown turned back, the dead / blocks / newcomers rewind, poison immunity, the
phrases + eggs, box / jewel / guide chapter. The hero-voice phrase line is in `MiraculousCoreGameTests`. The Second Chance tests each run in their own batch (their 32-block reach
would rewind the neighbouring tests' mobs).
