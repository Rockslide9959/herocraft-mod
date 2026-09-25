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
| Z | Ability 3 | Claw Dash | 18 to each enemy cut through, 21-block launch; grabs the first enemy hit and drags it in front of you until you land; lunge pose | 6 s |
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
- **Physique:** melee damage 4 (claws out and empty hand: +8 = 12), +20% speed (+30% more in Rage), +25% jump, mining
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

## v0.12.10 kit changes

- **Keys:** R Claw Slash (18) · G Cross Slash (12 + 12) · X Claw Dash (18) · Z Adamantium Execution · V Frenzy · C Berserker Rage · N Sniff · H claws.
- **Z Execution:** hold 5 s to charge (Slowness II while charging), release to fire. Early release cancels with no cooldown. Fires the usual wind-up / lunge / 60 damage; 30 s cooldown.
- **C Rage:** gated by a rage bar (0-100), no cooldown. +2.5 per HP taken, +1.5 per HP dealt (`WolverineConfig`), not gained while raging. Full bar -> 30 s of rage, bar empties. Bar resets on respawn.
- **N Sniff:** every living thing within 40 blocks highlighted for 20 s, no cooldown (10-tick anti-spam), sniffer sound. Client-only render via `WolverineSensePayload`.
- **Hunters:** any mob targeting the Wolverine within 40 blocks glows orange at all times (replaces the old 12-block hostile glow). Only the Wolverine's client draws it.

## v0.12.12

- **Left hand = right hand:** the right-click off-hand strike now deals a fixed 12 (+50% in Rage) at full
  strength (`WolverineSense.offHandStrike`), no longer scaled by the swing cooldown or a held item.
- **Claw Dash grab:** the first enemy struck is pinned 1.3 blocks ahead and carried with the player's velocity
  until the dash ends (landing; 30-tick safety cap), never through walls (`WolverineAbilities.dragGrabbed`).
- **Lunge pose:** `WolverineDashPose` + `PlayerRendererMixin` / `HumanoidModelMixin` lean the body 55° forward
  with both arms thrust ahead while `dashUntil` is active (visible to every viewer).

## v0.12.13

- Claw Dash range 7 -> 21 blocks. The grabbed enemy is now placed ahead of the player by 2 ticks of their velocity
  (1.6 blocks in front) so it stays in front instead of trailing.
- Claw moves alternate right / left hand (`WolverineAbilities.swing`); the right-click strike resets the attack cooldown.
- Symbiote suit growth is now base-host only (`Symbiote.tickGrowth`); Symbiote Spider-Man keeps his size.

## v0.12.14

- **Claws mine like swords:** `WolverineBareHands` (hooked in `PlayerMixin`) gives claws-out + empty-hand a netherite
  sword's destroy speed and drop rules (cobweb near-instant + drops, sword-efficient blocks 1.5x), on top of the 1.5x.
- **Wolverine Suit:** 4 craftable costume pieces (`WolverineItems.SUIT_*`, set id `wolverine`, `geo/wolverine.geo.json`,
  `textures/armor/wolverine.png`, recipes: leather + yellow/blue dye). Built from `WolverinArmour.bbmodel`.
- **Death flesh model:** `PlayerRendererFleshMixin` swaps the skin to `textures/entity/wolverine_flesh.png`
  (from `Flesh.bbmodel`) while a Wolverine `isDeadOrDying()`.
- **Symbiote Spider-Man** no longer gets the base host's passives: `Symbiote.isNormalHost` now gates the Biomass bar,
  damage rules, Resistance I, tool-hands, raw-food stomach and Predator Vision.

## v0.12.15

- **Emergency resurrection cooldown 60 s -> 180 s.** It now stamps `WolverineState.fleshStartedAt`; every client
  (`WolverineFlesh`) shows full flesh for 10 s then fades the real skin back over the flesh for 10 s
  (`PlayerRendererFleshMixin` picks the flesh texture, `WolverineFleshLayer` draws the skin at rising alpha).
- **Rage bar:** fills 50% slower (0.375 / 0.225 per damage taken / dealt) and drains 2/s after 10 s with no damage
  dealt or taken (`WolverineState.lastCombatAt`, `WolverinePassives.drainRage`).
- **Symbiote:** Resistance I removed; wearing the suit cuts all damage taken by 10%.

## v0.12.16

- **Death resurrection** (replaces the <15% emergency heal): a lethal hit no longer kills a Wolverine whose
  resurrection is ready (3 min cooldown). He rises at 30% health for 10 s of total invulnerability (only `/kill` and
  the void get through), Slowness III + Blindness + Weakness I (re-applied every 5 ticks so the debuff-halving
  mixin cannot shorten them, removed at the end), in the flesh model; then the skin fades back over the next 10 s.
  On cooldown he can die normally. `WolverinePassives.tryEmergency`, `Wolverine.resurrecting`.
- **R (Claw Slash, radius 4.5), G (Cross Slash, radius 4) and V (Frenzy, every strike hits everything within 4 blocks)
  are now area attacks** around him, not frontal.
- **Rage bar:** +1% per hit dealt or taken (flat, `RAGE_PER_HIT`), -5%/s after 10 s out of combat.
- **No held items with the claws out:** both hands are cleared each tick into free inventory slots (claws retract if
  there is no room). `WolverinePassives.clearHands`.
- **Healing factor nerfed:** 3 / 6 / 9 HP/s (was 4 / 8 / 12) at full / below half / below quarter health.
- **Suit:** leather-level (1/3/2/1); `SuperheroArmorModel` swaps to `wolverine_damaged_1..3.png` as the wearer's
  health falls below 75 / 50 / 25%.
- **Fix:** the boots recipe listed an unused blue-dye key, so it never loaded in 0.12.14/0.12.15.
- **Super Regeneration:** Cellular Surge now adds +8 HP/s (was +4). Base 4 HP/s and Regeneration Mode +4 unchanged.
- **Commands:** `/projecthero` is now only `power grant|remove|stack`, `locate <structure>` and
  `raid start|end|removetimer|advancetimer <supervillain|gravebound>` (op-only). `/squad` stays.
- **Thor:** releasing the hammer marks the player (`HAMMER_RELEASED`); gaining any other power afterwards zeroes
  their worthiness so they cannot lift Mjolnir and stack it.

## v0.12.17

- Death Surge visuals: red screen-edge shadow for the 10 s window (WolverineSurgeOverlay); first-person hand uses the flesh texture and the skin fades back over it (PlayerRendererFleshHandMixin); HUD line is now "Death Surge: <s>".
- R / G are frontal again but hit a box 3 blocks wide (inBox, reach 3.5 / 3.0); V (Frenzy) is back to its old nearest-target spread.
- Suit: chainmail protection (1/4/5/2), iron durability (multiplier 15); four damage textures (below 90 / 65 / 40% health, plus a near-fully-torn-off one during the Death Surge) with whole chunks removed to bare the skin.

## v0.12.18

- Suit damage lingers: the shown tear stage jumps up with damage but repairs one stage every 5 s (20 s from fully shredded) - WolverineSuitWear.
- Death Surge: debuffs / red border / torn suit last 20 s, the flesh->skin fade another 20 s; damage immunity stays the first 10 s; healing factor halved for all 40 s.
- Deploying the claws costs 4 armour-bypassing damage (never lethal, not in creative).
- Claw Dash sweeps a box 3 blocks wide along its line of travel instead of a 2-block-radius sphere.

## v0.12.19

- Death Surge bursts into a spray of blood (red dust + redstone-block chips) and keeps bleeding through the first ~15 s.
- Claws deploy with the retract sound. Suit durability is now diamond level (multiplier 33).

## v0.12.20

- Healing factor is a flat **1.5 HP every 5 ticks** (no health tiers, no Rage bonus, no Death Surge halving).
- Death Surge invulnerability is **5 s** (the surge itself, debuffs and the bleed are unchanged).
- Deploying the claws still costs 4 HP but is not combat: `Wolverine.deployingClaws()` keeps it out of the Rage bar /
  combat timer, and `WolverineSuitWear` ignores that wound for 3 s so the suit never tears from it.
- Claw Dash damage 18 -> 12.
