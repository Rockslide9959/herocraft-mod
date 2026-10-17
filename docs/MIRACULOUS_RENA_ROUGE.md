# Rena Rouge (Trixx, the Fox Miraculous) — v0.16.6

The Rena Rouge kit for the Miraculous core (`docs/MIRACULOUS_REFERENCE.md`). The core owns the box, the Necklace, the
Kwami, the transformation, the 5-minute countdown and the shared transformed stats (+12 armour, +6 toughness, +10 max
health, +5 melee, +25% speed, +35% jump, no fall damage). This kit owns the six keys, the Flute, the illusions, the suit.

Code: `com.projecthero.mod.miraculous.renarouge` (server), `com.projecthero.mod.client.miraculous.renarouge` (client).
Every number is a static final in `RenaRougeConfig`.

## The keys

| Key | Move | Numbers | Shift version | Numbers |
|---|---|---|---|---|
| R | **Flute Strike** — 3-hit staff combo (forehand, backhand, overhead) | 9 dmg a hit, reach 4, the 3rd knocks back 0.8; 0.5 s cooldown; a press within 1.5 s carries the combo on | **Flute Sweep** — a 360 spin | 7 dmg + knockback 1.3 to everything within 4; 3 s |
| G | **Flute Throw** — a boomerang along a curving arc | 16 blocks out (bows 3.5 to her right), 10 dmg to each thing it passes on the way out and again on the way back; a wall turns it back; can't throw while it is out; 2 s cooldown from the catch | **Melody Lure** — a few flute notes | mobs within 15 walk to where she looks for 5 s and take no target meanwhile; 15 s |
| X | **Fox Dash** — dart along the look | 10 blocks (stops at walls); a decoy stands where she was for 1 s and the mobs after her turn on it; 5 s | **Illusion Swap** | trade places with her Double (first choice) or nearest illusion within 48; 3 s |
| Z | **Illusion Double** | fights for 15 s at half her melee damage, mobs after her turn on it, takes 6 mob hits; 30 s | **MIRAGE** (special) | plays 1.2 s, an orange bubble floats 0.8 s and pops: 5 copies (3 mob hits each) drawing all aggro, a 9x4 fake wall 6 ahead, monsters within 20 confused for 30 s (each rethinks every 1.5 s: another monster 50%, an illusion 35%, wander 15%); **the countdown starts at the pop**; 90 s |
| V | **Fox Camouflage** | invisible to everyone (body, suit, armour, held items, Flute, name, shadow; no particles) up to 20 s; monsters after her lose her; ends on any attack / damage she deals or V again; 15 s cooldown from the end | **Fake Wall** | a 5x3 wall of the ground's own block 3 ahead, not solid, mobs can't see through it (and drop targets behind it); 10 s; 15 s |
| C | **Fox Pounce** | a high leap (1.15 up, 1.0 forward); landing stuns everything she may hit within 3 for 2 s (bosses: Slowness III instead); 7 s | **Fox Feint** | 3 s: every attack on her is cancelled, an illusion pops in where she stood and dissolves, she slips 1.5 blocks aside; 12 s |

Bosses (300+ max health or `TitanCombat.isBoss`) take at most 8% of their max health a hit and are never knocked back.

## The illusions (`RenaIllusionEntity`)

Player-shaped: the holder's own skin with the suit, fox ears, tail and Flute over it. Kinds: decoy, Double, Mirage copy,
feint. A player's hit (not hers or a squadmate's) dissolves one at once; a mob's hit costs one of its hits. Dissolving:
0.6 s of orange light (the reveal frames run out, tinted orange, fading) + orange dust / wax sparkles. Never saved
(`noSave`), no loot / XP, `OwnableEntity` (so `HeroTargets` treats it as her summon). They go when she detransforms,
logs out, dies or changes dimension (`onDetransform` → `RenaRougeMoves.endAll`).

**Aggro:** every 5 ticks an illusion turns monsters within 24 that are after her (or idle within 10) onto itself. Bosses
are fooled through their **threat table** (`RenaRougeIllusions.taunt`): the shared `BossThreat` (found by reflection on
the boss's `threat()`), or the Oathbreaker's own table (`debugThreat()`), gets more threat for the illusion than the
boss's current target, applied at once. When the illusion dissolves the table drops it and the boss returns to the real
fighters. The Titan's table counts players only, so it is never fooled.

## Shared-file hooks this kit added

- `projecthero.mixins.json`: `RenaRougeFakeWallSightMixin` (LivingEntity#hasLineOfSight false across a live fake wall,
  mobs only; free while no wall stands — `FakeWallEntity.ACTIVE`).
- `projecthero.client.mixins.json`: `RenaRougeFirstPersonArmMixin` (suit sleeve in first person),
  `RenaRougeFluteHandMixin` (first-person Flute during moves / at the lips), `RenaRougeCamouflageMixin` (cancels her
  whole player render while camouflaged), `RenaRougeHeldItemMixin` (no held item drawn over the Flute in her right hand),
  `RenaRougeArmorMixin` (no worn armour over the suit).
- `KwamiClientKit#hidesSkinOverlay` is deliberately **false**: the shared hook also hides the hat (her own hair). The kit
  hides the jacket / sleeve / trouser layers itself in `applyPose` and armour through `RenaRougeArmorMixin`.
- `KwamiKit#modifyIncomingDamage` is not used: Fox Feint is its own `ALLOW_DAMAGE` veto in `RenaRougeMod`.

## The suit and the Flute

- Suit: the user's cut-out of minecraftskins.com 17505838 ("rena rogue edit"), `suit.png` (slim arms) and
  `suit_wide.png` (wide arms), picked by the wearer's skin model. Both layers, on a player model inflated 0.04 over the
  wearer, so her own face, hair (hat layer kept) and bare skin show through the transparent texels.
- The reveal (`RenaRougeRender.cover` / `order`): over `MiraculousConfig.TRANSFORM_TICKS` texel by texel from the
  throat up and down, the leading edge a band of hot orange light; the wearer's jacket / sleeves / trousers go texel by
  texel in step (`OverlayStrips`, hat excluded). Detransforming runs it backwards.
- 3D: the fox ears (orange, cream inside, dark tips; follow the head) and a five-segment bushy tail with a white tip
  that sways and lifts as she walks — both grow in over the last 30% of the reveal.
- The Flute: a code-built model (orange body, white mouth band and tip, gold rings, finger holes), 24 px long. On her
  back (diagonal), in her right hand during a move, at her lips (played sideways) for the Lure / the Mirage, gone while
  thrown. First person: in place of the main-hand item during a move, level across the view while playing.
- Textures are written by `scratchpad/gen_rena_rouge_v0166.js` (also in the main checkout's `scratchpad/`): it copies the
  two suit skins and paints `flute.png`, `fox_parts.png` and `bubble.png`.

## Poses (ids 60..79, `RenaRougePose`)

60/61/62 strike combo, 63 sweep (the whole body turns a circle), 64 throw, 65 catch, 66 lure, 67 dash, 68 swap,
69 Double, 70 Mirage (plays, then the arm raised after the bubble), 71 camouflage, 72 wall, 73 pounce crouch (plus an
in-air dive pose while `rr_pounce`), 74 feint. The Flute-at-the-lips pose runs off `rr_play_until`.

## Synced values (`MiraculousState.vals`)

`rr_camo_until`, `rr_feint_until`, `rr_lure_until`, `rr_play_until`, `rr_flute_out`, `rr_combo`, `rr_combo_at`,
`rr_pounce`, `rr_double_until`, `rr_mirage_until`, `rr_wall_until`. Static per-player sessions (`RenaRougeMoves.SESSIONS`,
entity ids only) and `FakeWallEntity.ACTIVE` are dropped by `clearSessionState` / `forget`.

## Tests

`RenaRougeGameTests` (14): combo 9 x3, sweep 7 all round + knockback, throw hits and returns, lure walks a zombie to the
look point, dash decoy targeted, swap trades places, Double half damage + targeted + fights, Mirage 5 copies + wall +
countdown + confusion into in-fighting, camouflage + ends on attack, fake wall blocks sight but is not solid, pounce
stuns near not far, feint cancels a hit into an illusion, a player's hit dissolves / a mob's chips, detransform clears
everything.
