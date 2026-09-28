# The Hulk (Hero-Tier power)

Package `com.projecthero.mod.hulk`. Hero-Tier key `hulk` (the spec's "Gamma power"). Built in five phases:

| Phase | Scope | Release |
|---|---|---|
| 1 | Core: synced data, rage loop, transform / revert, stats, rage HUD bar, test commands | v0.13.11 |
| 2 | Abilities: Thunderclap, Ground Smash, Super Leap, Sprint Smash, keys, cooldowns + HUD, JSON config | v0.13.12 |
| 3 | Looks: the user's Hulk model (GeckoLib, drawn in place of the player), animations, hidden armour, roar + shake | v0.13.12 |
| 4 | Origin: Gamma Serum (loot only), rare Gamma Lab ruin with a glowing Gamma Reactor block | v0.13.12 |
| 5 | Balance + polish: Thor / Mjolnir rules, lifecycle, conflicts with other growing powers | v0.13.12 |

The spec asked for Fabric 1.21.11 + GeckoLib 5; the project is (and stays) on **Fabric 1.21.1 + GeckoLib 4.9**.

## Files

| Role | Where |
|---|---|
| State (persistent, `copyOnDeath`, synced to all) | `hulk/data/HulkState` -> `ModAttachments.HULK_STATE` |
| API: grant / revoke, rage, the change, stats, tick, lifecycle | `hulk/Hulk` |
| Numbers | `hulk/HulkConfig`: core loop = static finals; abilities + world rules = `config/projecthero_hulk.json` |
| The four abilities (task queue, cooldowns, landing, Sprint Smash) | `hulk/HulkAbilities` |
| Keys -> abilities | `hulk/HulkAbilityManager` (routed from `AbilityRouter`; while he is out the Hulk takes the keys first) |
| Rage hooks, fists-only, no fall damage | `hulk/HulkDamage` |
| H key | `network/HulkActionPayload` + `ProjectHeroModClient#handlePowerSelect` |
| Guns refused | `ModNetworking` firearm-fire receiver |
| HUD | `client/gui/HulkHud` -- R / G / X cooldown boxes + C Sprint Smash state, leap charge hairline, rage bar in Thor's meter spot |
| Model | `client/hulk/HulkAnimatable` (GeoReplacedEntity), `HulkModel`, `HulkRenderer`, `client/mixin/PlayerRendererHulkMixin`; first-person arm skin in `PlayerRendererFleshHandMixin` |
| Assets | `geo/hulk.geo.json`, `animations/hulk.animation.json`, `textures/entity/hulk.png` -- `scratchpad/gen_hulk.js` from `3d minecraft models/hulk/hulk.bbmodel` |
| Items / block | `hulk/item/HulkItems` (`gamma_serum`, `gamma_reactor`), `hulk/item/GammaSerumItem`, `hulk/block/GammaReactorBlock`; textures `scratchpad/gen_gamma.js` |
| Structure | `hulk/worldgen/GammaLabStructure` + `GammaLabPiece`; `worldgen/structure(_set)/gamma_lab.json`; loot `chests/gamma_lab.json` (always one serum) |
| Commands | `command/HulkCommand` -- `/hulk grant|revoke|setrage` (op 2, kept as testing helpers); `/projecthero power grant hulk` |
| Tests | `gametest/HulkGameTests` (18) |

## Replacing the model in Blockbench

Keep these names and the files drop straight in (re-run nothing):

- **Geometry** `geo/hulk.geo.json`, identifier `geometry.hulk`, 64x64 texture. Bones: `root` > `body` > `head`,
  `right_arm`, `left_arm`; `root` > `right_leg`, `left_leg`. Player-sized -- the 1.8x comes from the scale attribute.
  `head` is turned to the look direction on top of the animation.
- **Animations** `animations/hulk.animation.json`: `animation.hulk.idle` (loop), `walk` (loop), `run` (loop, while
  sprinting), `clap` (Thunderclap; impact at 0.3 s), `smash` (Ground Smash; impact at 0.5 s), `leap_charge` (hold),
  `leap` (hold, in the air), `transform` (1.5 s), `punch` (melee swing, right arm only -- layered over the rest).
- **Texture** `textures/entity/hulk.png`.

## Rules

- **Rage (0-100)**, Gamma players only. Banner: +2.5 per damage taken, +0.8 per damage dealt, -0.5/s after 15 s calm.
  A Gamma Reactor within 4 blocks: +3/s. Exhausted Banner builds none.
- **H** at 75+ lets the Hulk out; 100 forces it. Hulk: -1 rage/s, +1.5 per damage taken. 0 -> Banner, Weakness +
  Slowness 8 s.
- **Stats:** scale 1.8x (eased over 30 ticks), +12 attack, +40 max health (+20 HP on the way in), 0.9 knockback
  resistance, +8 toughness, +0.5 step, +1.5 reach, 2 HP/s regeneration, no fall damage.
- **Abilities** (Hulk only): R Thunderclap (12 dmg cone, 12 blocks, 70 degrees, shatters glass / ice / leaves /
  plants, 8 s), G Ground Smash (16 dmg, 7 blocks, 10 s), X Super Leap (hold 1.5 s: 10-45 blocks; landing 8 dmg 4.5
  blocks; 6 s), C Sprint Smash toggle (sprinting breaks blocks up to hardness 3 in front; no block entities).
  Bosses (`TitanCombat.isBoss`) take damage but are never shoved.
- **World switches** (json): `blockBreaking`, `sprintSmashEnabled`, `maxBreakableHardness`, `dropBrokenBlocks`,
  `respectMobGriefing`, `screenShake`.
- **Thor:** never both -- claiming Hulk revokes Thor (worthy or bound), claiming Thor revokes Hulk. The Hulk form can
  never lift Mjolnir (`Worthiness.canLift` / `wouldAscend`, `ItemEntityMixin`, `WorthinessEnforcer` ejects it), even a
  Hero of the Village. Creative still bypasses worthiness, as for everyone.
- **Other growing powers:** no Hulk inside a Titan or in All Might's Power Form, and no Titan shift / All Might form
  change while he is out (the scale bonuses would stack).
- **Lifecycle:** death -> respawn is a calm Banner; logout -> join comes back as Banner with rage kept (a relog cannot
  restore health above 20); dimension change keeps the Hulk.

## Not covered by tests / known limits
- Hulk + Wolverine held together: plain H goes to the Hulk (Wolverine's claw toggle unreachable).
- Another client's Hulk model and animations only checked in single player (the harness), not on a server.
- Sprint Smash measures speed from position change on the server; a slow walk into a wall does nothing by design.
