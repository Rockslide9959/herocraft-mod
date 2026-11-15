# Gear tiers I-IV (v0.17.5) — reference

Gear for anyone, power or not. Design: `docs/STRUCTURES_PROGRESSION_DESIGN.md` §3; materials and vaults:
`docs/LOOT_PROGRESSION_REFERENCE.md`.

Code: `com.projecthero.mod.gear` (common), `com.projecthero.mod.client.gear` (renderers), `mixin/CelestialSightMixin`.
Tests: `src/gametest/.../GearTierGameTests`. Assets/data: `scratchpad/gen_gear_v0175.js` (icons, armour layers, models,
recipes, vault gear tables, vanilla equipment tags merged); lang: `scratchpad/lang_gear_v0175.js` (key by key).
Films/screens: `scratchpad/GearHarness.v0175.java.txt` + `scratchpad/gear_v0175_films.js` -> `docs/screenshots/v0175/gear/`.

Gadgets are used with **right-click from the hotbar**, never the power keys.

## Items

| Item | Tier / rarity | Stats | Recipe |
|---|---|---|---|
| Grapple Line | I, Common | 24-block hook, reels at 1.1 b/t, 64 uses, 1 s cooldown, repair: Salvaged Tech | ` TH / ST / I  ` (T tech, H tripwire hook, S string, I iron) |
| Smoke Bomb | I, Common, stack 16 | cloud r4 for 7 s: mobs blinded + lose target, 5 s cooldown | ` S / TGT / C ` (string, tech, gunpowder, any coal) -> 4 |
| Flashbang | I, Uncommon, stack 16 | r5: blind 5 s, lose target, stun (Slowness VII + Weakness V) 2 s, 7 s cooldown | ` S / TGT / D ` (glowstone dust) -> 4 |
| Hero Alloy armour | II, Uncommon | 2/6/5/2, toughness 1/piece, durability x25 (iron 15, diamond 33), enchant 12 | iron-armour shapes in Hero Alloy Ingots |
| Hero Alloy Sword | II, Uncommon | 6.5 dmg, 1.6/s, 900 uses | `A / A / stick` |
| Alloy Baton | II, Uncommon | 4.5 dmg, 2.2/s, every hit: Slowness II 1.25 s | `  A /  A  / T  ` |
| Utility Belt | II, Rare | carried anywhere: +1 spare charge per gadget kind, cooldowns x0.7 | `LLL / TAT` (leather, tech, alloy) |
| Vibranium armour | III, Rare | 3/8/6/3, toughness 3, KB 0.1/piece, durability x40, enchant 15, fall damage -12.5%/piece (additive: set = half) | smithing: Hero Alloy piece + Vibranium Ingot (no template) |
| Vibranium Blade | III, Rare | 7.5 dmg, 1.8/s, 2200 uses | smithing: Hero Alloy Sword + Vibranium Ingot |
| Vibranium Grapple | III, Rare | 40 blocks, 1.5 b/t, 160 uses, 0.6 s cooldown, repair: Vibranium Shard | smithing: Grapple Line + Vibranium Shard |
| Vibranium Smoke Bomb | III, Rare | cloud r6, 11 s | smithing: Smoke Bomb + Shard; or 4 bombs around a Shard -> 4 |
| Vibranium Flashbang | III, Rare | r8, blind 8 s, stun 3.5 s | smithing: Flashbang + Shard; or 4 around a Shard -> 4 |
| Celestial Upgrade | IV, Epic | smithing template; **only from tier 4 vaults** (35% a roll) | copy: `SCS / STS / SSS` (S shard, C core, T template) -> 2 |
| Celestial armour | IV, Epic | 3/8/6/3, toughness 4, KB 0.15/piece, durability x45, enchant 20, fall cut like Vibranium, glowing trims | smithing: template + Vibranium piece + Celestial Core |
| Celestial Warhammer | IV, Legendary | 11 dmg, 0.9/s, 2600 uses; hold right-click >= 0.6 s, release: slam r5, 8-14 dmg (falloff to 60% at the rim), knock-back, 8 s cooldown, 2 durability | smithing: template + Vibranium Blade + Celestial Core |

**Celestial set bonus** (all four worn): +4 max health (transient modifier `projecthero:celestial_set_health`, checked
every 10 ticks by `CelestialSet`), and Blindness / Darkness are refused (`CelestialSightMixin` on `canBeAffected`).

**Bosses** (max health > 100, or `#c:bosses`) are blinded by gadgets but keep their target; a Flashbang stuns them
for a third as long.

## Mechanics

- `GrappleHookEntity` sticks where the ray hit (block colliders only); its server tick sets the owner's velocity
  towards it (`pullStep`, `hurtMarked` pushes it to the client) and clears fall distance. The reel ends on arrival
  (1.6 blocks), no progress for 6 ticks, or 70 ticks: a 0.6 hop, then up to 12 ticks of a gentle push towards the
  hook (`released`, cable hidden) so you land on the ledge -- one push is not enough, the client's wall collision
  zeroes sideways speed. Sneak lets go at once. Firing again replaces the hook.
- `GadgetProjectile` (one entity type for both bombs, rendered as its item) -> `SmokeCloudEntity` (client particles,
  server re-applies every 3 ticks) or `Flashbang.detonate`.
- `GadgetCooldowns`: cooldown is applied to both items of a kind (basic + Vibranium). Belt spare charge state is a
  static `UUID -> EnumMap<Kind, Long>`, cleared via `GearMod.clearSessionState()` from `ServerStateReset`.
- Fall cut = `fall_damage_multiplier` modifiers on the armour items' default attributes (`GearArmorItem`).
- Celestial look: `CelestialArmorRenderer` (Fabric `ArmorRenderer`) draws the normal layer, then
  `celestial_layer_N_glow.png` with `RenderType.eyes` (full-bright).

## Data

- Vault gear sub-tables (`chests/vault_tier<N>_gear`), one roll each per vault open; found armour/weapons come worn:
  - tier 1: 80 empty / grapple 6 / smoke 1-2 x7 / flash 1-2 x7
  - tier 2: 55 empty / smoke, flash 1-3 x10 / grapple 6 / alloy pieces 3 (chest 2) / sword 3 / baton 3 / belt 2
  - tier 3: 50 empty / alloy pieces 4 (chest 3) / vibranium pieces 2 (chest 1) / blade 1 / vibranium gadgets 3-4 / belt 3
  - tier 4: template 35% (own pool) + 40 empty / vibranium pieces 6 (chest 5) / blade 4 / vibranium gadgets 4-5
- Tags added (merged): `minecraft:head_armor`, `chest_armor`, `leg_armor`, `foot_armor`, `trimmable_armor`, `swords`
  (sword, baton, blade, warhammer), `enchantable/durability` (both grapples).
- Guidebook: "Gear Tiers" section at the end of the Loot & Progression chapter (no new chapter, counts unchanged).
  JEI: an info page per gear item.
