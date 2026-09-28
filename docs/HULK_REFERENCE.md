# The Hulk (Hero-Tier power)

Package `com.projecthero.mod.hulk`. Hero-Tier key `hulk` (the spec's "Gamma power"). Built in five phases:

| Phase | Scope | Release |
|---|---|---|
| 1 | Core: synced data, rage loop, transform / revert, stats, rage HUD bar, test commands | v0.13.11 |
| 2 | Abilities: Thunderclap, Ground Smash, Super Leap, Sprint Smash, keys, cooldowns + HUD, JSON config | v0.13.12 |
| 3 | Looks: the user's Hulk model (GeckoLib, drawn in place of the player), animations, hidden armour, roar + shake | v0.13.12 |
| 4 | Origin: Gamma Serum (loot only), rare Gamma Lab ruin with a glowing Gamma Reactor block | v0.13.12 |
| 5 | Balance + polish: Thor / Mjolnir rules, lifecycle, conflicts with other growing powers | v0.13.12 |
| - | Overhaul: new kit, passives, death save, riding, calm minigame, control / rampage, HUD | v0.13.14 |

The spec asked for Fabric 1.21.11 + GeckoLib 5; the project is (and stays) on **Fabric 1.21.1 + GeckoLib 4.9**.

## v0.13.14 overhaul

New kit (keys -> `HulkAbilityManager`): **R Power Punch** (3 wide x 6 long, 30, 5 s), **G Ground Smash** (30 in 7 blocks +
crater, 5 s), **Z Thunderclap** (22, 8 s) / **Shift+Z HULK SMASH** (hold 5 s, 100 forward 25 + round 30, crater, 60 s),
**X Super Leap** (15-70 blocks, 2 s), **C Charge** (8 s forced run, 20 to everything run through, breaks soft blocks, 12 s
after), **V Grab** (`HulkGrab`: throw / Shift+V crush 26 / Shift+V empty-handed = `HulkBoulderEntity` earth chunk, 30
in 4 on impact; 8 s). Sprint Smash is now a passive (config switch). All numbers: `config/projecthero_hulk.json` (config
version 2 resets a 0.13.13 file's abilities section).

Passives: +19 attack (20 punches), +1.5 attack knockback, +50% speed, +20 armour / +8 toughness, 0.75 HP per 5 ticks,
fire / arrow / fall immune, lava x0.25, explosions x0.5 (`HulkDamage.allowDamage`), stone-tool hands (`HulkBareHands`
via `PlayerMixin`), armour torn off on the change (-50 durability, dropped) and bounced while out.

Systems:
- **Death save** (`Hulk.tryDeathSave`, ALLOW_DEATH): every 3 min a fatal hit brings the Hulk out (or back) at full HP and
  100 rage; the 3 px dot left of the rage bar shows it. /kill and the void still kill.
- **Riding** (`HulkRiding`, `mixin/EntityHulkRideMixin`): a squad-mate right-clicks to ride his back, one at a time.
- **Calm down** (`HulkCalm` + `client/hulk/HulkCalmScreen`): hold N 2 s after 3 s out of combat; a breathing exercise
  (hold Space as the guide ring swells, let go as it shrinks). The client reports in/out-of-rhythm ticks once a second;
  the server caps them. Hurt = broken. Calmed to 0 as the Hulk = revert without exhaustion.
- **Control / rampage** (`HulkControl`): 8 s without dealing damage -> control drains 6/s, W/A/S/D prompts every 2.5 s
  (1.5 s window, +25 / -15). 0 -> 15 s rampage (targets nearest living thing, attacks, leaps, stomps, smashes blocks;
  abilities, riders and calm locked out), then 60 control back.
- State: `HulkState.Combat` (nested codec -- the record codec caps at 16 fields).
- HUD (`HulkHud`): Banner = "Rage N%" + bar; Hulk = keys, "Hulk Form", "Rage N%" + bar, control bar only while slipping;
  prompts / rampage / HULK SMASH / calm-hold above the crosshair.
- Animations added: power_punch, hulk_smash_charge, hulk_smash, charge, hold (arms layer), pickup, throw, crush.
- Tests: `HulkGameTests` (28).

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
