# The Hulk (Hero-Tier power, built in phases)

Package `com.projecthero.mod.hulk`. Hero-Tier key `hulk` (the spec's "Gamma power"). Built one phase at a time;
each phase ships as its own release and waits for an in-game test before the next starts.

| Phase | Scope | Status |
|---|---|---|
| 1 | Core: synced data, rage loop, transform / revert, stats, rage HUD bar, test commands, basic effect | **v0.13.11** |
| 2 | Abilities: Thunderclap, Ground Smash, Super Leap, Sprint Smash, keybinds, packets, cooldowns + HUD, JSON config | next |
| 3 | Looks: the user's Hulk skin (`3d minecraft models/hulk/hulk.bbmodel`, a 64x64 player skin) on the Hulk, GeckoLib model + animations, hidden armour, roar + growth effect | |
| 4 | Origin: Gamma Serum (loot only), rare overworld Gamma Lab ruin with a glowing gamma reactor block; lock down `/hulk` | |
| 5 | Balance + polish: Thor / Mjolnir conflict (Hulk cannot lift Mjolnir), death / respawn / logout / dimension audit, cleanup | |

The spec asked for Fabric 1.21.11 + GeckoLib 5; the project is (and stays) on **Fabric 1.21.1 + GeckoLib 4.9**.

## Phase 1 (v0.13.11)

### Files
| Role | Where |
|---|---|
| State (persistent, `copyOnDeath`, synced to all) | `hulk/data/HulkState` -> `ModAttachments.HULK_STATE` (`projecthero:hulk_state`) |
| API: grant / revoke, rage, the change, stats, tick, lifecycle | `hulk/Hulk` |
| Every number | `hulk/HulkConfig` (static finals; Phase 2 adds a JSON config for the ability numbers) |
| Rage hooks + fists-only rule | `hulk/HulkDamage` (AFTER_DAMAGE, AttackEntityCallback, UseItemCallback, PlayerBlockBreakEvents.BEFORE) |
| H key | `network/HulkActionPayload` (C2S `TRANSFORM`), receiver in `ModNetworking`, client branch in `ProjectHeroModClient#handlePowerSelect` (after All Might) |
| Guns refused | `ModNetworking` firearm-fire receiver drops a press from a Hulk |
| HUD | `client/gui/HulkHud` -- Thor's Storm Energy spot: label + 3 px Hairline bar, white tick at 75 |
| Test commands | `command/HulkCommand` -- `/hulk grant|revoke [players]`, `/hulk setrage <0-100> [players]`, op level 2, registered from `ProjectHeroCommand` |
| Guide | `HeroPackGuide` chapter `CH_HULK = 21` (`CHAPTER_POWER_BASE` is now 22); "Your Power" (I) screen; squad identity Hulk / Banner |
| Tests | `gametest/HulkGameTests` |

Wiring follows the All Might checklist: `HeroTiers` (hasHeroTier, HERO_KEYS, holdsHero, revokeHero,
hasIncompatibleWith), `AbilityRouter.serverTick` -> `Hulk.tick`, `HeroCommand` (grant / revoke / error texts),
`ProjectHeroMod` (damage init, death, join, respawn, world change, disconnect), `ServerStateReset`,
`HeroIdentity`, `PowerInfoScreen`, `HeroPackGuide`, HUD registration, lang.

### Rules
- **Rage (0-100)**, only with the Gamma power. As Banner: +2.5 per point of damage taken, +0.8 per point dealt;
  after 15 s without combat it bleeds off 0.5/s. Exhausted Banner builds none.
- **H** at 75+ lets the Hulk out; at 100 he comes out on the next tick. 10-tick debounce.
- **As the Hulk:** -1 rage/s; +1.5 per point of damage taken (dealing damage adds nothing). At 0 he reverts with
  **Weakness I + Slowness I for 8 s** and no rage / no change for those 8 s.
- **Stats** (fixed-id transient modifiers, re-applied every second by `reconcile`, idempotent): scale +0.8
  (1.8x, eased over 30 ticks by `tickScale`, which waits if the bigger body would not fit), attack +12, max health
  +40 (health % carried over, +20 HP on the way in), knockback resistance +0.9, armour toughness +8, step height
  +0.5, entity + block reach +1.5, regeneration 1 HP every 10 ticks.
- **Fists only:** `HulkDamage.isForbidden` = `TieredItem` (swords, tools), `ProjectileWeaponItem` (bow, crossbow),
  trident, mace, the mod's firearms.
- **Lifecycle:** respawn = calm Banner (no rage, no Hulk, size reset at once); join re-applies the saved form's
  stats and clears world-relative timers; revoke drops everything at once. Dimension change keeps the Hulk (same
  entity, attributes carried).

### Known Phase 1 simplifications (for Phase 5)
- No invulnerability window while growing.
- A Hulk can still lift Mjolnir / hold Thor at the same time -- Phase 5.
- Hulk + Wolverine held together: plain H goes to the Hulk, so Wolverine's claw toggle is unreachable -- revisit in
  Phase 5 with the other two-power conflicts.
- Armour stays visible and is scaled with him -- Phase 3 hides it.
