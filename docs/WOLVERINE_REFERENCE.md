# Wolverine — Hero-Tier ascension of Super Regeneration (v0.12.1)

Package `com.projecthero.mod.wolverine` (+ `client.wolverine`, `command.WolverineCommand`,
`network.WolverineActionPayload`). Wolverine is a **Primary** power that *ascends* the Super Regeneration
mutation the same way Spider-Man ascends Spider Adhesion. It is not a second regeneration system: it
reuses Super Regeneration's base heal tick and debuff-shortening mixin and scales them.

## Getting it

1. Own **Super Regeneration** (power 12). Without it the serum does nothing and is not consumed.
2. Craft an **Adamantium Serum** (`DND / BGB / DND`: D diamond, N netherite ingot, B blaze powder, G golden
   apple) and right-click it. It consumes Super Regeneration and, like every Primary power, replaces the
   mutation group (`HeroTiers.claimPrimary(player, "wolverine")`) — the oldest hero is replaced if two are held.
3. Admin: `/wolverine power grant|revoke [player]`, `/wolverine claws deploy|retract`, `/wolverine status`
   (also `/heropower grant hero wolverine`). `grant` gives Super Regeneration first if it is missing.

## Controls

| Key | Slot | Move | Damage | Cooldown |
|---|---|---|---|---|
| R | Ability 1 | Claw Slash | 18, 3.5-block arc | 1.5 s |
| G | Ability 2 | Cross Slash | 15 + 15, 3 blocks | 4 s |
| Z | Ability 3 | Claw Dash | 24 to each enemy cut through, 7-block launch | 6 s |
| X | Ability 4 | Berserker Rage | 12 s: +50% dmg, +30% speed, 2x regen, -20% dmg taken, ~100% KB resist | 45 s |
| C | Ability 5 | Frenzy | 5 strikes x 8 (0.2 s apart), enemies within 4 blocks | 15 s |
| V | Ability 6 | Adamantium Execution | 60, 4 blocks, wind-up + lunge, needs a target | 30 s (8 s on a whiff) |
| H | Utility 1 | Claws deploy / retract | — | 0.4 s spam guard |

There are **no new keybinds**. R/G/Z/X/C/V are the mod's existing Ability 1–6 keys; `AbilityRouter` hands
them to `WolverineAbilityManager` only while the player holds Wolverine and has no mutation selected (same
terms as every other Hero-Tier power), so every other power keeps its own meaning for those keys. H is
Utility 1 (it already toggles Iron Man's faceplate, Max Steel's helmet, the Symbiote); as Wolverine it toggles
the claws, and **Shift+H** still opens the power wheel.

Claw moves deploy the claws for you if they are retracted; Rage does not need them.

## Passives (all in `WolverineConfig`)

- **Healing factor:** Super Regeneration's base heal (`SuperRegenerationHandlers.tickBaseRegen`) at 1x / 2x / 3x
  → 4 / 8 / 12 HP/s above 50% / below 50% / below 25% health, ignoring combat; doubled in Rage.
- **Survivability:** 35% less physical damage (fire, magic, poison, wither, hunger are not reduced), a further
  20% in Rage (multiplicative, 0.65 x 0.8), 75% less fall damage, 75% knockback resistance (~100% in Rage / mid-dash),
  Poison and Wither last a quarter as long, the other Super Regeneration debuff cuts (50%) still apply.
- **Physique:** +8 melee damage (12 with the claws out), +20% speed (+30% more in Rage), +25% jump, mining
  1.5x with claws out (1.2x passive from strength). All are fixed-id attribute modifiers, reconciled every tick
  and removed with the power.
- **Enhanced senses:** hostile mobs within 12 blocks are outlined *for the Wolverine's own client only*
  (`EntityGlowMixin`; nothing is set on the mob).
- **Emergency heal:** below 15% health (or on a would-be-lethal hit) restore 30% of max health over 2 s, then a
  60 s internal cooldown. `/kill` and the void still kill.
- No bleeding mechanic exists in the mod, so none was added and there is no bleed chance / immunity.

## Architecture

| Piece | File |
|---|---|
| All numbers | `WolverineConfig` |
| Attachment (persistent, copy-on-death, synced to all) | `data/WolverineState`, `ModAttachments.WOLVERINE_STATE` |
| Server API: state, ascension, claws, cooldowns, lifecycle | `Wolverine` |
| Stat modifiers, healing factor, emergency heal, timer expiry | `WolverinePassives` |
| Damage reduction, fall reduction, emergency triggers | `WolverineDamage` (`ALLOW_DAMAGE` cancel-and-reapply with a re-entrancy guard, like `PunisherDamage`) |
| The six abilities | `WolverineAbilities` (delayed hits via `WolverineScheduler`) |
| Router bridge, per-tick upkeep | `WolverineAbilityManager` |
| Ascension item | `item/AdamantiumSerumItem`, `item/WolverineItems` |
| H toggle packet | `network/WolverineActionPayload` |

Everything gameplay-critical is server-side: the client only sends "slot N pressed" (`AbilityInputPayload`) and
the H toggle; damage, healing, cooldowns, targeting and Rage state are never taken from the client. Cooldowns
live in `WolverineState.abilityReadyAt` (same scheme as the Punisher / Spider-Man) and are synced so the HUD can
draw them. `WolverineScheduler` is a static cache and is emptied by `ServerStateReset` and `Wolverine.clearTransient`.

## Client

- `WolverineClawsModel` — code-built model: 3 curved blades per hand, each a chain of four tapering segments on
  nested child parts (12 boxes a hand), texture `textures/entity/wolverine_claws.png`. Extension animates over
  6 ticks from `WolverineState.clawsChangedAt`, so every viewer sees the same deploy / retract.
- `WolverineClawsLayer` — third person and other players (registered as a `PlayerRenderer` feature layer).
- `PlayerRendererClawsMixin` + `WolverineClawsRenderer` — first-person hand.
- `WolverineHud` — the six keys + cooldowns (Left-Alt shows names), `WOLVERINE` label, claw state, Rage timer.
- Guide chapter (`HeroPackGuide.wolverineChapter`), "Your Power" (I) screen, squad identity.

Sounds and particles are vanilla (sweep attack, crit, enchanted hit, angry villager, crimson spore, wolf growl,
chain, anvil, phantom swoop). No animation files: the swing uses the vanilla arm swing.

## v0.12.1 companion changes

- **Symbiote** suit growth is now +50% (1.8 → 2.7 blocks, Iron Golem height).
- **Symbiote Lunge** is a single ballistic launch (`AbilityHelpers.ballisticLaunch`) sized to carry the host
  20 blocks, instead of a held push.
- **Size Manipulation** forms ease in and out over 1 second (`SizeHandlers.SCALE_ANIM`).

## Tests

`gametest/WolverineGameTests`: prerequisite gate, stat apply/remove/idempotence, claws toggle, Rage (12 s,
no stacking, cleanup), R routing + cooldown gate, Frenzy target rule, emergency heal + cooldown, other powers
untouched.
