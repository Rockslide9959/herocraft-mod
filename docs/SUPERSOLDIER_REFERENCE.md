# Super Soldier (Hero-Tier power)

Package `com.projecthero.mod.supersoldier` (client: `com.projecthero.mod.client.supersoldier`). Hero-Tier key
`super_soldier`. Added in v0.14.8. A Captain-America-style peak human: always on (no transformation, H stays the power
selector), strong but deliberately below the Hulk / Thor / All Might.

## Files

| File | What |
|---|---|
| `SuperSoldier` | The API: state access, grant / revoke, `reconcile` (every attribute modifier), the passive tick, lifecycle hooks |
| `SuperSoldierAbilities` | The ten moves, targeting (`canTarget`: no self / squadmates / own pets / creative players; players only with PvP), `strike` (Onslaught x1.25, Focus crit x1.5, boss cap), the per-player move tables |
| `SuperSoldierAbilityManager` | Slot dispatch (Shift read server-side), per-tick upkeep, cooldown lookups for the HUD |
| `SuperSoldierDamage` | ALLOW_DAMAGE: 30% reduction, roll i-frames, no fall damage from his leaps, Focus crits on punches; AFTER_DAMAGE: Onslaught punch shock; the `projecthero:serum_rejection` damage type key |
| `SuperSoldierSerum` | Drink outcome with an injected roll (`drink(player, refined, roll)`) |
| `SuperSoldierConfig` | Every number |
| `SuperSoldierSetup` | One init call: items, recipe serializer, entity type, damage rules |
| `data/SuperSoldierState` | The synced, persistent, copy-on-death attachment `projecthero:super_soldier_state` |
| `entity/SoldierShieldEntity` | The thrown ricochet shield (server-driven, never saved) |
| `item/SuperSoldierItems`, `item/SuperSoldierSerumItem` | Unrefined / refined serum (drinkable, 32 ticks, leaves a bottle); `soldier_shield` (display-only model for the thrown shield) |
| `recipe/SuperSoldierSerumRecipe` | `CustomRecipe` + `SimpleCraftingRecipeSerializer`, type `projecthero:super_soldier_serum` |
| client `SuperSoldierHud`, `SoldierShieldRenderer`, `SuperSoldierClient` | Mono HUD (R G Z X V, Hairline bars), the spinning shield, registration |

## Obtaining

- **Unrefined Super Soldier Serum**: crafting table, shapeless special recipe
  (`data/projecthero/recipe/unrefined_super_soldier_serum.json`): exactly one drinkable Potion of Strength, one of
  Swiftness and one of Leaping (long / strong variants accepted; splash, lingering, custom-effect and other potions
  rejected). Special recipes are not in the recipe book -- the guide says so.
- Drinking it: **10%** grant, **90%** death (`projecthero:serum_rejection`, tagged `bypasses_armor`, `bypasses_effects`,
  `bypasses_enchantments`, `bypasses_invulnerability`, `bypasses_resistance`, `no_knockback`; message "X's body
  rejected the unrefined serum"). Creative included. A power's own death save (Symbiote, Khonshu) may still intervene.
  The roll is the server level's RNG.
- **Refined**: blasting recipe, `cookingtime` 12000 (10 minutes), `refined_super_soldier_serum_blasting.json`. Always
  grants.
- Granting calls `HeroTiers.claimPrimary(player, "super_soldier")` (one Primary power: the old one is replaced) and
  shows a title + chat message. Also: `/projecthero power grant super_soldier [player]`, and the Heroic random serum.

## Passives (fixed-id transient modifiers, reconciled every tick)

| | Value |
|---|---|
| Movement speed | +50% (ADD_MULTIPLIED_BASE 0.5) |
| Max health | +10 (+5 hearts) |
| Unarmed melee | +7 while the main hand is empty |
| Damage taken | x0.7 (cancel-and-reissue; not for /kill, the void, bypass-invulnerability) |
| Jump | apex 2.25 blocks (JUMP_STRENGTH x(v/0.42 - 1), v solved by simulation) |
| Safe fall | +3 blocks |
| Knockback resistance | +0.2 |
| Attack speed | +15% |
| Regeneration | 1 HP / 2 s after 5 s out of combat (food > 6) |
| Immunities | Poison, Nausea |

## Moves (R / G / Z / X / V, each with a Shift variant; C unused; H / N never)

| Key | Move | Numbers | Cooldown |
|---|---|---|---|
| R | Combo Strike | 3 punches, 5 ticks apart: 5 + 5 + 7 (finisher knockback 1.0). Needs a target within 4 blocks | 5 s |
| Shift+R | Uppercut Launcher | 10 damage, target launched ~5 blocks up (vy 1.05) | 8 s |
| G | Shield Throw | 8 per hit, up to 3 hits (ricochets to the nearest visible hostile within 10 blocks, also off walls), range 24, returns through blocks | 7 s |
| Shift+G | Shield Bash Charge | 8 ticks at 1.1 b/t (~9 blocks), 7 damage + knockback 1.6 + Slowness I 2 s to everything in the path | 9 s |
| Z | Leaping Slam | leap 4 blocks up / 0.8 forward, lands (or after 3 s) for 12 damage in 5 blocks (60% at the edge), knockback 1.2, lift 0.4 | 12 s |
| Shift+Z | **Super Soldier Onslaught** (ultimate) | 8-damage shockwave in 6 blocks, then 10 s: +5 melee, Resistance I, Speed I, moves x1.25, each punch shocks enemies within 3 blocks of the target for 4 | 100 s |
| X | Tactical Roll | 1.35 b/t dash in the movement (or look) direction, 10 ticks of invulnerability | 4 s |
| Shift+X | High Leap | 5 blocks up, 1.25 forward, no fall damage for 5 s | 8 s |
| V | Battle Cry | self + squadmates in 16: Strength I + Speed I 10 s; hostiles (and enemy players) in 12: Weakness 8 s + Slowness I 4 s | 30 s |
| Shift+V | Tactical Focus | every hostile / enemy player in 30 blocks glows 10 s; 8 s of x1.5 crits (punches and moves) on the marked | 25 s |

Bosses (max health >= 300, or `TitanCombat.isBoss`) take at most 6% of their max health per hit and are never knocked
back or launched. A 4-tick global lock stops moves stacking; the combo locks for 12 ticks, the bash for 10.

## HUD

`SuperSoldierHud`: bottom right, mono (black / gray) boxes in the order R G Z X V; each box shows the longer of its two
moves' cooldowns. Hairline bars (3 px, no border, no text) above the keys: the ultimate (blue = ready, gray = recharging,
white = running) and Tactical Focus (amber, only while it runs). Left-Alt shows short move names.

## State / lifecycle

`SuperSoldierState`: `hasPower`, `busyUntil`, `iframeUntil`, `noFallUntil`, `onslaughtUntil`, `focusUntil`,
`lastCombatTick`, `abilityReadyAt`. Join / respawn clear the timers (game time is per-world) and re-reconcile; death,
logout and dimension change call `clearTransient`. Static maps (move tasks, bash, slam, Focus marks, message throttle)
are cleared by `ServerStateReset` via `SuperSoldierAbilityManager.clearSessionState`.

## Tests

`SuperSoldierGameTests` (listed in `src/gametest/resources/fabric.mod.json`).

## Not done / untested in a real client

- No custom player poses (moves use arm swings, particles and sounds only).
- The HUD and the shield renderer are unverified visually in-client.
