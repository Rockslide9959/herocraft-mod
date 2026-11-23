# Ancient Temple (v0.17.7, Tier 4 dungeon) — reference

Design: `docs/STRUCTURES_V0177_DESIGN.md` §3. Alarm framework: `docs/ALARM_REFERENCE.md`. Patterns from
`docs/VAULT_DUNGEON_REFERENCE.md` (one code-built piece on the hideouts' `HideoutCanvas`, sealed vaults, SavedData, loot gate).
Code: `com.projecthero.mod.temple` (+ `.block`, `.entity`, `.item`, `.worldgen`), client `com.projecthero.mod.client.temple`.
Tests: `AncientTempleGameTests`. Assets: `node scratchpad/v0177/gen_temple_v0177.js` (skins, blocks, items, loot, structure JSON,
tags) and `node scratchpad/v0177/gen_ankhatep_v0177.js` (boss model, textures, clips; checks clip timings against
`AnkhatepBoss`). Lang: `scratchpad/v0177/temple_lang.json` via `lang_add.js`. Harness: `scratchpad/TempleHarness.v0177.java.txt`,
films: `scratchpad/v0177/temple_films_v0177.js` -> `docs/screenshots/v0177/temple/`.

## Placement

Structure `projecthero:ancient_temple` (type `projecthero:ancient_temple`, `TempleStructure`), biomes
`#projecthero:ancient_temple_biomes` (desert, jungle, sparse jungle, bamboo jungle), step `top_layer_modification`,
`random_spread` spacing 240 / separation 90 (rarer than The Vault's 200 / 80), salt 1705301, 4 chunks clear of villages.
Jungle biomes at the start get the mossy stone-brick look, everything else (the desert) sandstone (`TemplePalette`). Skipped on
water, below sea level - 1, where the surface varies more than 10 blocks over the platform's corners, or where the throne room
would sink under `minBuildHeight + 8`. In `#projecthero:locator/tier4` (the Dungeon Locator).

## Layout (`TempleBuilder`, local coords, entrance local north, y 0 = surface, runs +z and down)

Box x -17..17, y -24..20, z -20..196. Everything public on `TempleBuilder` for tests / the harness; `TemplePiece.world(local)`.

| Part | Floor y | z | What |
|---|---|---|---|
| Surface | 0 | -19..10 | platform (footing down to 14), 3-tier ziggurat: hypostyle hall (pillars, glyph frieze, the **Pilgrim's Warning** lectern), solid 2nd tier, a shrine with a soul brazier on top; 5-wide doorway, braziers, two obelisks; vines + moss (jungle) / sand drifts (desert); a guardian and an acolyte inside |
| Stairwell | 0..-12 | -4..7 | 3 wide, lights |
| Vestibule | -12 | 7..16 | ward stone #1, Shield-bearer guardian, Runner acolyte, guardian, 2 spawn points |
| Dart Hall | -12 | 16..36 | 5 wide; 6 dart traps (knee: jump / head: crouch), 5 ward plates, a gong tripwire at z 34, a revenant |
| Collapsing Floor | -12 over a pit at -18 | 36..50 | crumbling tiles, solid east ledge (x 6) + both end rows, a head-high dart across the ledge, 2 revenants in the pit, a ladder back up |
| Hall of Trials | -12 | 50..81 | glyph plates + candle mural (west), Sun Lens + 3 mirrors + a decoy (east), weight altar + 7 candles + 6 urns (centre), the three seals before the **trials gate**; ward stone #2, gong posts at the entrance, 2 ward plates, a sniper's dais (NE), Shield-bearer, Medic, 2 revenants, a guardian; the **Three Trials** lectern |
| Crushing Corridor | -12 | 82..111 | 5 wide x 30 long x 4 high, trigger stone in row 0, a hurdle slab (row 9) and a crouch slab (rows 19-20), then a stair down (z 112..119) |
| Flooded Crypt | -20 (pool -21, water at -20) | 119..143 | knee-deep pool, pillars, 8 sarcophagi, soul lanterns, 2 revenants, the **Tomb Sentinel** at (0, -20, 131); the **crypt gate** south |
| Hall of the Bound | -20 | 143..156 | the Captain (seal key), Medic, Runner, Shield-bearer, a sniper on the east ledge, ward stone #3, a ward plate, a Tier 3 barrel, 4 spawn points; the locked ancient **Blast Door** (keycard) |
| Throne of the Bound King | -22 | 156..187 | 27 x 30, pillars with bands + braziers (ancient alarm lamps), hanging chains, shackle anvils, a 2-step dais with the gold-and-lapis throne, 4 **canopic jars** on pedestals, **Ankhatep** at (0, -21, 175) |
| Treasury | -22 | 187..194 | the **vault gate**, gold floor, three sealed Treasury Vaults (-2/0/2, -21, 192) |

## Temple Wards (the alarm framework, ancient style)

Profile `ancient_temple` (tier 4 -> waves of 6 + 1 per extra player, max 10), colour `0xE8C060`, siren `BELL_BLOCK` at 0.5 (a deep
gong), reinforcer `TempleGuards.reinforcement` (guardians 2 in 3, revenants, rising out of the sand). Structure registered with
`AlarmProfiles.registerStructure` (the zone = the whole box); `TemplePiece.buildAll` registers it by hand.

| Block | What it does |
|---|---|
| `ward_gong` (`facing`, `powered`) | Sensor. A tripwire hooked to it, a redstone signal, or a survival player striking / using it -> `AlarmDirector.raise(SENSOR)` (wired to the nearest ward stone; dead once that is sabotaged). Bell + sonic-boom puff. |
| `ward_plate` (`powered`) | Sensor. A survival player walking onto it (not sneaking, on the ground) raises the alarm; clicks back up after 1 s. |
| Alarm Panel / Lamp / Blast Door, style `ancient` | The ward stones (3), braziers (lamps) and the keycard door into the throne room. |

Guards (`TempleGuards`, tag `projecthero.temple_guard` + the framework's guard tag): Captain (guardian, the zone keycard = the
"seal key"), 2 Runners (acolytes), 2 Medics (acolytes), 3 Shield-bearers (guardians), 2 Snipers (acolytes with bows; the framework
fires the arrows). Ghost bonus: the treasury vaults are `HeroVault`s, so `AlarmGhost.claim` runs on open (one extra roll +
`projecthero:ghost` if the zone never went Alert).

## Traps

| Block | Behaviour |
|---|---|
| `dart_trap` (`facing`, `high`) | BE scans every 4 ticks along its facing (to the first collision block, max 10) at `y + 0.35` (knee, `high=false`) or `y + 0.95` (head, `high=true`, in the block above the feet = 1.95 over the feet). A survival player whose box (padded 0.3 up/down, as an arrow's hit test is) crosses it -> click, 5 ticks, a no-gravity dart (arrow, 1.2 base, Poison I 3 s, no pickup), 30-tick rest. Jumping clears a knee line, crouching (1.5 + 0.3 = 1.8) clears a head line. Mineable. |
| `crumbling_floor` (`stage` 0-2) | Unbreakable. A survival player standing on it -> stage 1 (dust) -> 14 ticks -> stage 2 (no shape, not drawn) -> 400 ticks -> back to 0 (waits while something is in its space). |
| `crusher_core` (+BE) / `temple_crusher` | The Crushing Corridor. Idle: a survival player in rows 0-2 starts it. Every 70 ticks the walls advance a column from each side (crusher blocks fill the air of column ±2, then ±1, then 0, along the whole length; occupied cells are skipped and the occupant takes 3 + Slowness). After the 3rd step: 40-tick hold (3 every 10 ticks to anyone inside), retract, 60-tick rest. About 10.5 s to run it. Only the geometry is saved; leftovers are retracted on the first tick after loading. |

## The Hall of Trials

| Trial | Blocks | Rule |
|---|---|---|
| Glyphs | `glyph_plate` (`glyph` 0 Sun / 1 Eye / 2 Scarab / 3 Ankh, `lit`, `pressed`) x4, `glyph_carving` mural x4 with 1-4 candles under each | The order is a permutation from the piece's seed (`TempleBuilder.glyphOrder`, never 0-1-2-3), written into the Seal of Glyphs' BE and carved on the mural; which plate shows which glyph is a second permutation. A right step lights the plate, a wrong one puts all out (starting again if it was the first glyph). |
| Light | `sun_lens` (BE), `temple_mirror` x3 + decoy | The beam (traced every 5 ticks, drawn as gold dust while a player is within 40) runs straight, reflects off a mirror's face (`facing` and `facing.cw` are its two open sides), stops at anything else solid. Ending on the Seal of Light breaks it. Right-click a mirror to turn it 90°. Solution (local): EAST, WEST, EAST; they start NORTH, SOUTH, WEST. Not a combat beam (no `BeamClash`). |
| Weight | `weight_altar` (BE, `load`, `solved`), 4 weight items in the urns (decorated pots: Jackal, Feather, Lion, Ibis, bones, gold nuggets) | Right-click with a weight lays it; exactly 7 balances (Lion + Jackal, or Lion + Ibis + Feather) and breaks the Seal of Weight; over 7 tips the scale and throws every weight back; sneak + empty hand takes them back. |

`temple_seal` (`kind`, `broken`, BE) breaks -> `TempleClearing.trialsCheck`: when every seal within 16 is broken, the `trials`
`temple_gate`s within 24 open. Using a seal or gate tells you what it wants.

## People

All player-model humans in skins painted by `gen_temple_v0177.js` (`textures/entity/temple/`, base + full overlay layer), extending
`SyndicateThug` through `TempleMob` (own synced skin byte; drawn by `TempleClient.TempleMobRenderer`, a `SyndicateRenderer`
with the temple texture — the Syndicate skin list is untouched).

| Mob | HP / armour | Notes |
|---|---|---|
| `temple_guardian` | 40 / 8 | jackal mask (A) or nemes (B), bronze scale, khopesh (iron sword) |
| `wrapped_revenant` | 36 / 4 | slow, 0.4 knockback resistance, hits add Hunger + Slowness, fire x1.5 |
| `cult_acolyte` | 26 / 2 | ochre hood (A) / black robe + skull paint (B), golden dagger; snipers hold a bow |
| `tomb_sentinel` | 480 / 12 (x1.6) | mid-boss (below) |

### The Tomb Sentinel (`TombSentinelBoss`)

Shared `BossThreat` (HurtByTarget removed), boss bar within 40 (gold, red after the rise), leashed to the crypt (teleported home
past 32). **Crypt Slam** (target within 6): 20-tick gold ring (r 5), then 10 + throw to grounded players. **Riptide Charge**
(4-16, sight): 12-tick turn, 18 ticks at 0.85 b/t, 9 + shove per player, stops on walls. **Rise** (once at half health): 2
revenants, cooldowns x0.7. Death -> `TempleClearing.sentinelDefeated` (the `crypt` gate). Test seams: `startSlam`, `startCharge`,
`rise`, `threat()`.

## Ankhatep, the Bound King (`AnkhatepBoss`, GeckoLib)

1100 HP, 14 armour / 8 toughness, full knockback resistance, fire / wither / poison immune, 1.1 x 3.3 box (the geo model is authored
at full size, 18 bones, 88 cubes, 2 texels per model pixel: gold death-mask, striped nemes, white atef crown + uraeus, braided
beard, bead collar, scarab pectoral, wrappings, kilt + apron, tattered lapis cloak, shackles + chains, crook staff with a soul
gem, green curse flame). Two textures (`ankhatep` / `ankhatep_unbound`: torn wraps, cracked mask, awakened runes) with
`_glowmask` twins (`AutoGlowingGeoLayer`). The chain bones (`chain_r/l`) show while bound, the stubs (`broken_r/l`) after; every
clip keeps the chains hanging straight down.

| Move | Timing | Effect |
|---|---|---|
| Curse of the Bound King | on waking (first target within 20, or hurt): 20-tick cast | while any jar on his jar spots stands: `bound_curse` (80 ticks, renewed every second) to every survival player within 30; drains 1 HP every 40 ticks (20 in phase 2), never below 1; every 2 s he heals 1 per cursed player (x2 in phase 2). All jars broken -> curse lifted, effect removed, **stagger** 100 ticks (+50% damage taken). |
| Sand Wave | 24-tick wind-up, 24-tick ring to r 14 (phase 2: a second ring 10 ticks behind) | 9 + throw + Blindness to grounded players within 0.8 of the front. Jump it. |
| Sand Lances | 16-tick wind-up, 18 ticks | 3 lines (5 in phase 2) fanned 20° (16°) march 1 block/tick; 8 + launch per player; lines stop at walls |
| Raise the Guardians | 30-tick cast | 2 (3) temple guardians from the sand, max 4 alive; they crumble when he dies |
| Staff strike | target within 3.6, 10 ticks | 12 + shove |
| Breaking the chains | at half health, 50 ticks (immune) | at tick 30: phase 2 (synced), shockwave (6 + throw within 8), speed 0.24 -> 0.30, reach 6 -> 22 blocks from home, bar red "Ankhatep, Unbound", 2 broken jars re-form, the curse resumes |

Phase 1 he never moves more than 6 blocks from his home (the chains); he walks home when idle. Shared `BossThreat` (range 48,
held while a move is in flight), boss bar within 48. Death: 60-tick collapse into sand; minions crumble, curse removed,
`TempleClearing.bossDefeated` (TempleState cleared, `vault` gate, treasury seals -> Tier 4 Hero Vaults, title + chat within 64).
Test seams: `startMove`, `stepForTest`, `rescanJars`, `liftCurse`, `summon`, `jarsLeft`, `waveRadius`.

`canopic_jar` (`head` 0-3): hardness 0.6, any projectile shatters it, no drops. `temple_vault_seal`: unbreakable; used with no
Ankhatep within 96 -> opens anyway (never stuck).

## Loot

- Treasury: three Tier 4 Hero Vaults (`chests/vault_tier4`, per-player, Ghost bonus).
- Artifact: **the Bound King's Ankh** (Legendary, `bound_kings_ankh`) — added in code (`TempleMod`, `LootTableEvents.MODIFY` on
  `chests/vault_tier4_artifacts`) as one pool gated by `projecthero:near_ancient_temple` (radius 32, `NearTempleCondition`) + 18%.
  Carried: a death (not void / `/kill`) is refused once -> 8 HP, Regeneration II 5 s, Resistance II 3 s, sand burst (6 + throw to
  hostiles within 5); dormant 12000 ticks, kept on the item (`AnkhReadyAt` custom data) + an item cooldown for the bar.
- Drops (`loot_table/entities/`): Ankhatep 2-3 Celestial Cores, 3-5 Vibranium Shards, 6-10 XP bottles, 1-2 golden apples, 2-4 gold
  blocks, 25% a notched apple; the Sentinel 2-4 Vibranium Shards, 3-6 XP bottles, 4-9 gold, 2-4 alloy ingots; guards gold nuggets etc.
- A Tier 3 barrel in the Hall of the Bound.

## Effects, items, statics

- `projecthero:bound_curse` (`TempleEffects`). Items: 4 weights (`weight_feather/ibis/jackal/lion`, Uncommon), the Ankh.
- `TempleState` (per-dimension SavedData `projecthero_ancient_temples`, keyed by Ankhatep's home). No static per-world state
  anywhere (boss viewers, minions, jar spots are instance fields; nothing for `ServerStateReset`).

## Guidebook / JEI

Villain Hideouts chapter: "The Ancient Temple (Tier 4)" sub-section after The Vault (wings, wards, trials, guardians, Ankhatep,
clearing, the artifact). Loot & Progression: an "Ancient Temple" paragraph. Rare Structures: a pointer line. Chapter counts
unchanged. JEI info pages for 13 temple blocks, the 4 weights and the Ankh. Lecterns in the temple: the Pilgrim's Warning (surface)
and the Three Trials (hall).
