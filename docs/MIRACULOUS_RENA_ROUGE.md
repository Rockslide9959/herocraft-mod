# Rena Rouge (Trixx, the Fox Miraculous) — v0.16.6 (Mirage wheel v0.16.7)

The Rena Rouge kit for the Miraculous core (`docs/MIRACULOUS_REFERENCE.md`). The core owns the box, the Necklace, the
Kwami, the transformation, the 3-minute countdown and the shared transformed stats (+12 armour, +6 toughness, +10 max
health, +7 melee, +25% speed, +35% jump, no fall damage). This kit owns the six keys, the Flute, the illusions, the suit.

Code: `com.projecthero.mod.miraculous.renarouge` (server), `com.projecthero.mod.client.miraculous.renarouge` (client).
Every number is a static final in `RenaRougeConfig`.

## The keys

| Key | Move | Numbers | Shift version | Numbers |
|---|---|---|---|---|
| R | **Flute Strike** — 3-hit staff combo (forehand, backhand, overhead) | 9 dmg a hit, reach 4, the 3rd knocks back 0.8; 0.5 s cooldown; a press within 1.5 s carries the combo on | **Flute Sweep** — a 360 spin | 7 dmg + knockback 1.3 to everything within 4; 3 s |
| G | **Flute Throw** — a boomerang along a curving arc | 16 blocks out (bows 3.5 to her right), 10 dmg to each thing it passes on the way out and again on the way back; a wall turns it back; can't throw while it is out; 2 s cooldown from the catch | **Melody Lure** — a few flute notes | mobs within 15 walk to where she looks for 5 s and take no target meanwhile; 15 s |
| X | **Fox Dash** — dart along the look | 10 blocks (stops at walls); a decoy stands where she was for 1 s and the mobs after her turn on it; 5 s | **Illusion Swap** | trade places with her Double (first choice) or nearest illusion within 48; 3 s |
| Z | **Illusion Double** | fights for 15 s at half her melee damage, mobs after her turn on it, takes 6 mob hits; 30 s | **MIRAGE** (special, v0.16.7: a wheel of 8 illusions, see below) | pick one: the Flute plays 1.2 s, the orange bubble floats 0.8 s and pops into it for **30 s**; **the countdown starts at the pop**; **2 min** cooldown from the pick (twice a transformation); Escape / no pick spends nothing |
| V | **Fox Camouflage** | invisible to everyone (body, suit, armour, held items, Flute, name, shadow; no particles) up to 20 s; monsters after her lose her; ends on any attack / damage she deals or V again; 15 s cooldown from the end | **Fake Wall** | a 5x3 wall of the ground's own block 3 ahead, not solid, mobs can't see through it (and drop targets behind it); 10 s; 15 s |
| C | **Fox Pounce** | a high leap (1.15 up, 1.0 forward); landing stuns everything she may hit within 3 for 2 s (bosses: Slowness III instead); 7 s | **Fox Feint** | 3 s: every attack on her is cancelled, an illusion pops in where she stood and dissolves, she slips 1.5 blocks aside; 12 s |

Bosses (300+ max health or `TitanCombat.isBoss`) take at most 8% of their max health a hit and are never knocked back.


## The Mirage wheel (v0.16.7, `RenaMirage`, `MirageIllusion`)

Shift+Z (when ready) makes the server send `RenaMirageMenuPayload`; the client opens `RenaMirageWheelScreen` (an orange
ring of 8 wedges, item icons, name in the middle, a wrapped hint under the ring). Mouse direction + click, or releasing Z
over a wedge, sends `RenaMirageChoicePayload(ordinal)`; Escape / closing sends -1 and spends nothing. The server only
accepts a pick within `MIRAGE_MENU_TICKS` of the offer (`RenaRougeMoves.chooseMirage`). The aim of the aimed ones is
taken at the pick (`RenaMirage.aimPoint`, 32 blocks). Every illusion lasts `MIRAGE_TICKS` (30 s); when it ends it
shatters in an orange flash and every mob it fooled is dazed 3 s (Slowness II + no target).

**Bosses are never immune** (user). A boss that can't really walk (movement speed <= 0.01, e.g. Galactus) stops attacking
instead (its target is refused for as long). Target refusals: `PunisherControl.suppressTarget` and
`RenaMirage.refusesTarget`, both asked from `MobMindLockMixin` at `Mob#setTarget` (a Mirage refusal keeps the mob's
current target if that one is allowed, so a mirrored mob stays on the mob it fights).

| # | Illusion | Server effect | Visual |
|---|---|---|---|
| 1 | **Army of Foxes** | the classic Mirage (5 copies that swing when she swings, a 9x4 fake wall, monsters within 20 confused) + 8 `RenaFoxEntity` that chase the nearest enemy within 16, bite for 6 (`RenaRougeCombat.strike`, never her / squad / pets), pull the bitten onto themselves, take 3 mob hits (a player's at once). A copy hit by anything bursts: Blindness 3 s + Nausea 5 s (a player) or 5 s no target + wander (a mob) | vanilla fox model, orange, see-through, shimmering |
| 2 | **Monster** | every mob within 24 of the beast: no target, runs 16 blocks away (every 0.5 s) | `MirageFxEntity.MONSTER`: the fox model x8, rearing, translucent orange |
| 3 | **Vanishing Act** | mobs within 32 that were after her / her squad drop them and can't pick them again unless one of them hit that mob within 5 s; 3 RUNNER copies sprint off (behind her and to the sides) and take the aggro | runners + a puff |
| 4 | **Mirage Zone** | radius 12 at the aim: mobs inside drop targets and walk out; mobs outside have a path / chase into it cut and are nudged back | `ZONE`: glowing disc, cracks, 34 flame sprites, client smoke / flame / lava particles |
| 5 | **Golden Lure** | every mob within 30 of the prize: no target, walks to it, stares | `LURE`: a rotating heap of gold items in a halo |
| 6 | **Pitfall** | a real 4x4 pit, 3 deep, at the aim, through `GeoTerrain.pocket` (natural ground only, `GeoTerrain.canEdit` build rules, never under her / a squadmate); mobs within 16 walk for it and are nudged in at the edge; she / squadmates who fall in are lifted to the rim; filled back bottom-up with `restoreNow` at the end (what fell in is lifted out); the ledger also restores on server stop | `PIT`: the ground block drawn over the open cells + a faint orange shimmer |
| 7 | **Eclipse** | a 10-radius night following her: enemies inside drop and can't pick anything farther than 4 blocks; their projectiles (`ENTITY_LOAD`) fly up to 25 degrees off | `ECLIPSE`: a dark dome with stars |
| 8 | **Hall of Mirrors** | enemies within 20 at the cast are mirrored: each one is kept on the nearest other mirrored mob (bosses through `taunt`), they can't pick her / her squad; a lone one just stays off her | `MIRRORS`: 8 turning glass panels with orange frames |

Performance: the work runs every 5 / 10 ticks over one box query (the mirrors reassert every tick, but only search for a
mob whose fight broke off); the big visuals are one client-drawn `MirageFxEntity`; server particles are count + spread.


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

- v0.16.7: `MobMindLockMixin` asks `RenaMirage.refusesTarget`; `HeroPackGuide` adds the `mirage*` sections to her chapter.
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
`rr_pounce`, `rr_double_until`, `rr_mirage_until`, `rr_mirage_kind` (v0.16.7), `rr_wall_until`. `RenaMirage.RUNNING` (v0.16.7) is dropped the same way. Static per-player sessions (`RenaRougeMoves.SESSIONS`,
entity ids only) and `FakeWallEntity.ACTIVE` are dropped by `clearSessionState` / `forget`.

## Tests

`RenaRougeGameTests` (14): combo 9 x3, sweep 7 all round + knockback, throw hits and returns, lure walks a zombie to the
look point, dash decoy targeted, swap trades places, Double half damage + targeted + fights, Mirage 5 copies + wall +
countdown + confusion into in-fighting, camouflage + ends on attack, fake wall blocks sight but is not solid, pounce
stuns near not far, feint cancels a hit into an illusion, a player's hit dissolves / a mob's chips, detransform clears
everything.

`RenaMirageGameTests` (12, v0.16.7, each in its own batch): Escape spends nothing, a pick costs 2 min and the pop starts the
30 s and the countdown, one per illusion with a boss among the mobs (Army of Foxes: 8 foxes, 6 a bite, never the squad,
a copy bursts and blinds; Pitfall dug and filled back exactly, never under a squadmate), the end dazes, detransforming ends it.
